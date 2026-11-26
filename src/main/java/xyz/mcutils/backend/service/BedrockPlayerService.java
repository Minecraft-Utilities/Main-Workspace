package xyz.mcutils.backend.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.player.BedrockPlayer;
import xyz.mcutils.backend.model.domain.skin.BedrockSkin;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.token.geyser.GeyserSkinToken;
import xyz.mcutils.backend.model.token.mojang.DecodedTexturesPropertyToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves Bedrock players by gamertag or by their Floodgate UUID. Every Floodgate UUID has a
 * zero most-significant half, so a real Java UUID is rejected instead of being read as an xuid.
 */
@Service
public class BedrockPlayerService {
    private static final int MAX_GAMERTAG_LENGTH = 16;

    private final GeyserService geyserService;

    public BedrockPlayerService(GeyserService geyserService) {
        this.geyserService = geyserService;
    }

    /**
     * Resolves a player by gamertag or Floodgate UUID.
     *
     * @param query the gamertag or Floodgate UUID, with or without dashes
     * @return the player, with a null skin when Geyser has none for them
     * @throws BadRequestException when the query is neither a valid gamertag nor a Floodgate UUID
     * @throws NotFoundException   when Geyser does not know the player
     */
    public BedrockPlayer getPlayer(String query) {
        ResolvedQuery resolved = resolve(query);
        String gamertag = resolved.gamertag();
        if (gamertag == null) {
            gamertag = this.geyserService.getGamertag(resolved.xuid());
        }
        if (gamertag == null) {
            throw new NotFoundException("Player with xuid '%s' was not found".formatted(resolved.xuid()));
        }

        GeyserSkinToken token = this.geyserService.getSkin(resolved.xuid());
        if (token == null || token.textureId() == null || token.textureId().isBlank()) {
            return new BedrockPlayer(resolved.uniqueId(), Long.toString(resolved.xuid()), gamertag, null, null);
        }
        return new BedrockPlayer(
                resolved.uniqueId(),
                Long.toString(resolved.xuid()),
                gamertag,
                toBedrockSkin(resolved.uniqueId(), token),
                token.lastUpdate() == null ? null : Instant.ofEpochMilli(token.lastUpdate())
        );
    }

    /**
     * Resolves a player's skin for rendering. UUID queries only touch the skin endpoint, not the
     * gamertag endpoint, so they stay a single upstream call.
     *
     * @param query the gamertag or Floodgate UUID, with or without dashes
     * @return the skin, carrying the raw textures.minecraft.net URL for the renderer
     * @throws BadRequestException when the query is neither a valid gamertag nor a Floodgate UUID
     * @throws NotFoundException   when the player or their skin cannot be found
     */
    public Skin getSkin(String query) {
        long xuid = resolve(query).xuid();
        GeyserSkinToken token = this.geyserService.getSkin(xuid);
        if (token == null || token.textureId() == null || token.textureId().isBlank()) {
            throw new NotFoundException("Skin not found for player with xuid '%s'".formatted(xuid));
        }
        return new Skin(0, token.textureId(), resolveModel(token.value()), 0, false, Instant.EPOCH);
    }

    private ResolvedQuery resolve(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (isUuid(trimmed)) {
            UUID uniqueId = UUIDUtils.parseUuid(trimmed);
            if (uniqueId.getMostSignificantBits() != 0L) {
                throw new BadRequestException("'%s' is not a Bedrock player".formatted(query));
            }
            return new ResolvedQuery(uniqueId, uniqueId.getLeastSignificantBits(), null);
        }

        String gamertag = normalizeGamertag(trimmed);
        Long xuid = this.geyserService.getXuid(gamertag);
        if (xuid == null) {
            throw new NotFoundException("Player '%s' was not found".formatted(gamertag));
        }
        return new ResolvedQuery(new UUID(0L, xuid), xuid, gamertag);
    }

    private static String normalizeGamertag(String query) {
        String gamertag = query.startsWith(".") ? query.substring(1) : query;
        // Floodgate replaces spaces with underscores because a gamertag may not contain one.
        gamertag = gamertag.replace('_', ' ').trim();
        if (gamertag.isEmpty() || gamertag.length() > MAX_GAMERTAG_LENGTH) {
            throw new BadRequestException("Gamertags must be between 1 and %s characters".formatted(MAX_GAMERTAG_LENGTH));
        }
        return gamertag;
    }

    private static boolean isUuid(String query) {
        if (query.length() != 32 && query.length() != 36) {
            return false;
        }
        String hex = query.replace("-", "");
        if (hex.length() != 32) {
            return false;
        }
        return hex.chars().allMatch(character -> (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F'));
    }

    private static BedrockSkin toBedrockSkin(UUID uniqueId, GeyserSkinToken token) {
        String base = AppConfig.INSTANCE.getWebPublicUrl() + "/bedrock/players/" + uniqueId + "/skin";
        Map<String, String> parts = new HashMap<>();
        for (Skin.SkinPart part : Skin.SkinPart.values()) {
            parts.put(part.name(), base + "/" + part.name().toLowerCase() + ".png");
        }
        return new BedrockSkin(token.textureId(), base + "/texture.png", resolveModel(token.value()), parts);
    }

    private static Skin.Model resolveModel(String value) {
        if (value == null || value.isBlank()) {
            return Skin.Model.DEFAULT;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            DecodedTexturesPropertyToken payload = Constants.GSON.fromJson(new String(decoded, StandardCharsets.UTF_8), DecodedTexturesPropertyToken.class);
            if (payload == null || payload.textures() == null || payload.textures().skin() == null
                    || payload.textures().skin().metadata() == null) {
                return Skin.Model.DEFAULT;
            }
            return "slim".equalsIgnoreCase(payload.textures().skin().metadata().model())
                    ? Skin.Model.SLIM
                    : Skin.Model.DEFAULT;
        } catch (RuntimeException ignored) {
            return Skin.Model.DEFAULT;
        }
    }

    private record ResolvedQuery(UUID uniqueId, long xuid, @Nullable String gamertag) {}
}
