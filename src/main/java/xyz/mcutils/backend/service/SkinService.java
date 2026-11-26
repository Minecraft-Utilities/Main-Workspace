package xyz.mcutils.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.*;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.skin.SkinRenderMetric;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.domain.skin.SkinLookupSort;
import xyz.mcutils.backend.model.domain.skin.VanillaSkinTextureIds;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinCurrentOwnersRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinCurrentOwnersRepository;
import xyz.mcutils.backend.repository.postgres.SkinRepository;
import xyz.mcutils.backend.repository.postgres.SkinTrendingRepository;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SkinService {
    public static final String DEFAULT_SKIN = "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb"; // Steve
    public static final int SKINS_PER_PAGE = 20;
    public static SkinService INSTANCE;

    private final SkinRepository skinRepository;
    private final SkinCurrentOwnersRepository skinCurrentOwnersRepository;
    private final SkinTrendingRepository skinTrendingRepository;
    private final PlayerRepository playerRepository;
    private final StorageService storageService;
    private final StatisticsService statisticsService;
    private final WebRequest webRequest;
    /**
     * Rendered part cache. Weight-bounded (256MB) instead of entry-count-bounded so a few
     * 768px PNGs cannot pin the whole heap; per-size variants are cached under {@code key-size}.
     */
    private final Cache<String, byte[]> renderedSkinCache = CacheBuilder.newBuilder()
            .expireAfterAccess(6, TimeUnit.HOURS)
            .maximumWeight(256L * 1024 * 1024)
            .weigher((String key, byte[] value) -> value.length)
            .build();
    /**
     * Upgraded/fixed texture bytes are content-addressed per textureId and never change.
     */
    private final Cache<String, byte[]> processedTextureCache = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumWeight(64L * 1024 * 1024)
            .weigher((String key, byte[] value) -> value.length)
            .build();
    private final CoalescingLoader<String, byte[]> processedLoader = new CoalescingLoader<>(Main.EXECUTOR);
    private final CoalescingLoader<String, byte[]> textureLoader = new CoalescingLoader<>(Main.EXECUTOR);
    private final CoalescingLoader<String, SkinRow> skinCreationLoader = new CoalescingLoader<>(Runnable::run);
    private final TransactionTemplate transactionTemplate;
    private final LegacySkinCheckService legacySkinCheckService;

    @Value("${mc-utils.renderer.skin.cache}")
    private boolean cacheEnabled;

    @Value("${mc-utils.renderer.skin.enabled}")
    private boolean renderingEnabled;

    @Value("${mc-utils.renderer.skin.limits.min_size}")
    private int minPartSize;

    @Value("${mc-utils.renderer.skin.limits.max_size}")
    private int maxPartSize;

    public SkinService(SkinRepository skinRepository, SkinCurrentOwnersRepository skinCurrentOwnersRepository, SkinTrendingRepository skinTrendingRepository, PlayerRepository playerRepository,
                       StorageService storageService, WebRequest webRequest, StatisticsService statisticsService,
                       PlatformTransactionManager transactionManager,
                       @Lazy LegacySkinCheckService legacySkinCheckService) {
        this.skinRepository = skinRepository;
        this.skinCurrentOwnersRepository = skinCurrentOwnersRepository;
        this.skinTrendingRepository = skinTrendingRepository;
        this.playerRepository = playerRepository;
        this.storageService = storageService;
        this.webRequest = webRequest;
        this.statisticsService = statisticsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.legacySkinCheckService = legacySkinCheckService;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    public void updateTrendingHeat() {
        long before = System.currentTimeMillis();
        this.skinTrendingRepository.rebuild(VanillaSkinTextureIds.ALL);
        this.statisticsService.refreshTrendingSkinCount();
        log.info("Updated trending heat for skins in {}ms ({} trending)", System.currentTimeMillis() - before, this.statisticsService.getTrendingSkinCount());
    }

    public Skin getSkin(String query) {
        SkinRow skinRow = this.getSkinByQuery(query);
        Skin skin = Skin.fromRow(skinRow);

        if (skinRow.getFirstSeenUsingPlayerId() != null) {
            this.playerRepository.findById(skinRow.getFirstSeenUsingPlayerId())
                    .ifPresent(p -> skin.setFirstSeenUsing(p.getUsername()));
        }

        List<String> usersUsing = this.playerRepository.findUsernamesBySkinId(skinRow.getId(), PageRequest.of(0, 250));
        skin.setAccountsSeenUsing(usersUsing);
        skin.setCurrentOwners(this.skinCurrentOwnersRepository.findById(skinRow.getId()).map(SkinCurrentOwnersRow::getOwners).orElse(0L));

        return skin;
    }

    public SkinRow getSkinByQuery(String query) {
        if (!query.isEmpty() && query.chars().allMatch(Character::isDigit)) {
            // All-digit names are valid usernames, so an ID miss falls through to a player lookup.
            Optional<SkinRow> bySkinId = query.length() <= 18 ? this.skinRepository.findById(Long.parseLong(query)) : Optional.empty();
            if (bySkinId.isPresent()) {
                return bySkinId.get();
            }
            if (query.length() <= 16) {
                Optional<SkinRow> byPlayer = this.playerRepository.findSkinByUsernameIgnoreCase(query);
                if (byPlayer.isPresent()) {
                    return byPlayer.get();
                }
            }
            throw new NotFoundException("Skin not found");
        }
        // By player (name or UUID)
        if (query.length() <= 36) {
            return this.getPlayerSkinRow(query);
        }
        // By texture ID
        Optional<SkinRow> optionalSkinRow = this.skinRepository.findByTextureId(query);
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found");
        }
        return optionalSkinRow.get();
    }

    public SkinRow getPlayerSkinRow(String playerQuery) {
        boolean isUsername = playerQuery.length() <= 16;
        Optional<SkinRow> optionalSkinRow = isUsername
                ? this.playerRepository.findSkinByUsernameIgnoreCase(playerQuery)
                : this.playerRepository.findSkinById(UUIDUtils.parseUuid(playerQuery));
        if (optionalSkinRow.isEmpty()) {
            throw new NotFoundException("Skin not found for player '%s'".formatted(playerQuery));
        }
        return optionalSkinRow.get();
    }

    /**
     * When {@code query} is a player username or UUID, returns that player's current username
     * for Content-Disposition filenames on rendered skin parts.
     */
    public Optional<String> findPlayerUsernameForSkinQuery(String query) {
        if (query.isEmpty() || query.length() > 36) {
            return Optional.empty();
        }
        boolean isUsername = query.length() <= 16;
        Optional<PlayerRow> player = isUsername
                ? this.playerRepository.findByUsernameIgnoreCase(query)
                : this.playerRepository.findById(UUIDUtils.parseUuid(query));
        return player.map(PlayerRow::getUsername);
    }

    /**
     * Cached variant of {@link #getOrCreateSkin(SkinTextureToken, UUID)}.
     * Cache key is the texture ID only; {@code playerId} is only relevant on first insert.
     */
    @Cacheable(value = "skinByTextureId", key = "#token.textureId")
    public SkinRow getOrCreateSkinCached(SkinTextureToken token, UUID playerId) {
        return getOrCreateSkin(token, playerId);
    }

    public Map<String, SkinRow> findByTextureIds(Collection<String> textureIds) {
        return this.skinRepository.findByTextureIdIn(textureIds).stream()
                .collect(Collectors.toMap(SkinRow::getTextureId, Function.identity()));
    }

    public SkinRow getOrCreateSkin(SkinTextureToken token, UUID playerId) {
        String textureId = token.getTextureId();
        return this.skinCreationLoader.get(textureId, () -> this.transactionTemplate.execute(_ -> {
            Optional<SkinRow> existing = this.skinRepository.findByTextureId(textureId);
            if (existing.isPresent()) {
                return existing.get();
            }
            Skin.Model model = token.metadata() == null
                    ? Skin.Model.DEFAULT
                    : Skin.Model.valueOf(token.metadata().model().toUpperCase());
            SkinRow row = new SkinRow(textureId, model, false, 0, Instant.now());
            row.setFirstSeenUsingPlayerId(playerId);
            SkinRow saved = this.skinRepository.save(row);
            StatisticsService.addTrackedSkinCount(1);
            this.legacySkinCheckService.enqueue(textureId);
            return saved;
        }));
    }

    public Pagination.Page<Skin> getPaginatedSkins(int page, SkinLookupSort sort) {
        if (sort == SkinLookupSort.TRENDING) {
            Pageable trendingPage = PageRequest.of(page - 1, SKINS_PER_PAGE);
            Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.statisticsService.getTrendingSkinCount());
            return pagination.getPage(page, (_) ->
                this.skinRepository.findTrendingSkins(trendingPage).map(Skin::fromRow).stream().toList()
            );
        }

        Sort pageSort = Sort.by(Sort.Direction.DESC, sort.getFieldName()).and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable pageable = PageRequest.of(page - 1, SKINS_PER_PAGE, pageSort);
        Pagination<Skin> pagination = new Pagination<Skin>().setItemsPerPage(SKINS_PER_PAGE).setTotalItems(this.statisticsService.getTrackedSkinCount());
        return pagination.getPage(page, (_) ->
            this.skinRepository.findAllSkins(pageable).stream().map(Skin::fromRow).toList()
        );
    }

    /**
     * Gets the skin image for the given skin.
     *
     * @param textureId  the texture id of the skin to get
     * @param textureUrl the texture url of the skin to get
     * @param upgrade    whether to upgrade legacy 64×32 skins to 64×64
     * @return the skin image
     */
    public byte[] getSkinTexture(String textureId, String textureUrl, boolean upgrade) {
        String cacheKey = textureId + ":" + upgrade;
        byte[] processed = this.processedTextureCache.getIfPresent(cacheKey);
        if (processed != null) {
            return processed;
        }
        return processedLoader.get(cacheKey, () -> {
            byte[] cached = this.processedTextureCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
            byte[] skin = this.getRawSkinTexture(textureId, textureUrl);
            byte[] result = SkinUtils.fixTransparentSkin(upgrade ? SkinUtils.upgradeLegacySkin(textureId, skin) : skin);
            this.processedTextureCache.put(cacheKey, result);
            return result;
        });
    }

    private byte[] getRawSkinTexture(String textureId, String textureUrl) {
        return textureLoader.get(textureId, () -> {
            long start = System.currentTimeMillis();

            byte[] skinBytes = this.storageService.get(StorageService.Bucket.SKINS, textureId + ".png");
            if (skinBytes == null) {
                log.debug("Downloading skin image for skin {}", textureId);
                byte[] bytes = webRequest.request(textureUrl).asBytes();
                boolean fallback = bytes == null;
                if (fallback) {
                    bytes = webRequest.request("https://textures.minecraft.net/texture/%s".formatted(DEFAULT_SKIN)).asBytes();
                    log.info("Skin not found for the texture {}, using fallback skin", textureId);
                    if (bytes == null) {
                        throw new IllegalStateException("Skin image for skin '%s' was not found".formatted(textureId));
                    }
                }
                log.debug("Downloaded skin image for skin {} in {}ms", textureId, System.currentTimeMillis() - start);

                if (!fallback) {
                    byte[] finalBytes = bytes;
                    Main.EXECUTOR.submit(() -> this.storageService.upload(StorageService.Bucket.SKINS, textureId + ".png", MediaType.IMAGE_PNG_VALUE, finalBytes));
                }
                skinBytes = bytes;
            }
            return skinBytes;
        });
    }

    /**
     * Renders a skin part, optionally with a cape.
     * Canonical image is stored at max size; smaller requested sizes are produced by downscaling.
     * Cape rendering is only supported for {@code FULLBODY_ISO_FRONT} and {@code FULLBODY_ISO_BACK} parts.
     *
     * @param skin     the skin to render
     * @param typeName the name of the part
     * @param options  render options (overlay flag and optional cape)
     * @param size     the output size (height; width derived per type)
     * @return the rendered image as PNG bytes
     */
    public byte[] renderSkin(Skin skin, String typeName, RenderOptions options, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Skin rendering is currently disabled");
        }
        if (size < minPartSize || size > maxPartSize) {
            throw new BadRequestException("Invalid skin part size. Must be between " + minPartSize + " and " + maxPartSize);
        }

        Skin.SkinPart part = EnumUtils.getEnumConstant(Skin.SkinPart.class, typeName);
        if (part == null || !skin.supportsPart(part)) {
            throw new BadRequestException("Invalid or unsupported skin part: '%s'".formatted(typeName));
        }

        String baseKey = "%s-%s-%s".formatted(skin.getTextureId(), part.name(), options.renderOverlays());
        String sizeKey = options.cape() != null ? baseKey + "-" + options.cape().getTextureId() + "-" + size : baseKey + "-" + size;
        byte[] cached = cacheEnabled ? this.renderedSkinCache.getIfPresent(sizeKey) : null;
        if (cached != null) {
            MetricService.getMetric(SkinRenderMetric.class).recordHit();
            return cached;
        }

        long renderStart = System.currentTimeMillis();
        byte[] bytes = ImageUtils.imageToBytes(skin.render(part, size, options), 1);
        if (cacheEnabled) {
            this.renderedSkinCache.put(sizeKey, bytes);
        }
        MetricService.getMetric(SkinRenderMetric.class).recordMiss(System.currentTimeMillis() - renderStart);
        return bytes;
    }
}
