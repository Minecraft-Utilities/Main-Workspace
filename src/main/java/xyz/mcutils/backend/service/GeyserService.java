package xyz.mcutils.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.metric.impl.api.ExternalApiRequestsMetric;
import xyz.mcutils.backend.model.token.geyser.GeyserGamertagToken;
import xyz.mcutils.backend.model.token.geyser.GeyserSkinToken;
import xyz.mcutils.backend.model.token.geyser.GeyserXuidToken;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Reads the public GeyserMC Global API. Misses are cached as nulls too, so a gamertag Geyser does
 * not know does not hit the API again on every request.
 */
@Service
@Slf4j
public class GeyserService {
    private static final String API_GEYSER = "geyser";

    private final WebRequest webRequest;
    private final String apiUrl;

    public GeyserService(
            WebRequest webRequest,
            @Value("${mc-utils.geyser.api-url:https://api.geysermc.org}") String apiUrl
    ) {
        this.webRequest = webRequest;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    /**
     * Resolves the xuid of a gamertag.
     *
     * @param gamertag the gamertag to look up
     * @return the xuid, or null when Geyser does not know the gamertag
     */
    @Cacheable(value = "geyserXuid", key = "#gamertag.toLowerCase()", sync = true)
    public Long getXuid(String gamertag) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            GeyserXuidToken result = this.webRequest
                    .request(this.apiUrl + "/v2/xbox/xuid/" + encodePathSegment(gamertag))
                    .as(GeyserXuidToken.class);
            success = result != null;
            return result == null ? null : result.xuid();
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_GEYSER, "xuid_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Resolves the gamertag of an xuid.
     *
     * @param xuid the xuid to look up
     * @return the gamertag, or null when Geyser does not know the xuid
     */
    @Cacheable(value = "geyserGamertag", key = "#xuid", sync = true)
    public String getGamertag(long xuid) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            GeyserGamertagToken result = this.webRequest
                    .request(this.apiUrl + "/v2/xbox/gamertag/" + xuid)
                    .as(GeyserGamertagToken.class);
            success = result != null;
            return result == null ? null : result.gamertag();
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_GEYSER, "gamertag_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Resolves the skin of an xuid.
     *
     * @param xuid the xuid to look up
     * @return the skin token, or null when the player has no skin or is unknown
     */
    @Cacheable(value = "geyserSkin", key = "#xuid", sync = true)
    public GeyserSkinToken getSkin(long xuid) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            GeyserSkinToken result = this.webRequest
                    .request(this.apiUrl + "/v2/skin/" + xuid)
                    .as(GeyserSkinToken.class);
            if (result == null || result.textureId() == null || result.textureId().isBlank()) {
                return null;
            }
            success = true;
            return result;
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_GEYSER, "skin_lookup", success, System.currentTimeMillis() - start);
        }
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
