package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.IPUtils;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.model.dto.request.PlayerViewRequest;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.PlayerViewEventRow;
import xyz.mcutils.backend.model.token.turnstile.TurnstileResponse;
import xyz.mcutils.backend.repository.postgres.PlayerMonthlyViewsRepository;
import xyz.mcutils.backend.repository.postgres.PlayerViewEventRepository;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class PlayerViewService {

    @Value("${mc-utils.player-views.auth-token}")
    public String authToken;

    @Value("${mc-utils.player-views.ip-salt}")
    public String ipSalt;

    private static final Duration VIEW_WINDOW = Duration.ofDays(30);

    private final PlayerService playerService;
    private final TurnstileService turnstileService;
    private final PlayerMonthlyViewsRepository playerMonthlyViewsRepository;
    private final PlayerViewEventRepository playerViewEventRepository;

    public PlayerViewService(PlayerService playerService, TurnstileService turnstileService, PlayerMonthlyViewsRepository playerMonthlyViewsRepository,
                             PlayerViewEventRepository playerViewEventRepository) {
        this.playerService = playerService;
        this.turnstileService = turnstileService;
        this.playerMonthlyViewsRepository = playerMonthlyViewsRepository;
        this.playerViewEventRepository = playerViewEventRepository;
    }

    public void rebuildMonthlyViews() {
        long before = System.currentTimeMillis();
        this.playerMonthlyViewsRepository.rebuild();
        int pruned = this.playerViewEventRepository.deleteViewedBefore(Instant.now().minus(VIEW_WINDOW));
        log.info("Updated monthly views for players in {}ms (pruned {} expired events)", System.currentTimeMillis() - before, pruned);
    }

    public void countView(PlayerViewRequest request, String ip) {
        if (authToken == null) {
            throw new IllegalStateException("Player view auth token has not been set");
        }

        String ipHash = IPUtils.hashIp(ip, ipSalt);
        TurnstileResponse turnstileResponse = this.turnstileService.validateToken(request.turnstileToken(), ip);
        if (!turnstileResponse.isSuccess()) {
            throw new BadRequestException("Invalid Turnstile Token");
        }
        PlayerRow player = this.playerService.getPlayer(request.playerQuery());
        boolean alreadyViewed = playerViewEventRepository.existsByPlayerIdAndIpAddressAndViewedAtAfter(player.getId(), ipHash,
                Instant.now().minus(VIEW_WINDOW));
        if (!alreadyViewed) {
            this.playerViewEventRepository.save(new PlayerViewEventRow(player.getId(), ipHash, Instant.now()));
        }
    }
}
