package xyz.mcutils.backend.service;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.metric.impl.api.ExternalApiRequestsMetric;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@Getter
public class MojangService {
    /**
     * The splitter and joiner for dots.
     */
    private static final Splitter DOT_SPLITTER = Splitter.on('.');
    private static final Joiner DOT_JOINER = Joiner.on('.');

    private static final String API_MOJANG = "mojang";

    private final String sessionServerEndpoint;
    private final String apiEndpoint;

    /**
     * A list of banned server hashes provided by Mojang.
     * <p>
     * This is periodically fetched from Mojang, see
     * {@link #updateBlockedServers()} for more info.
     * </p>
     */
    private final Set<String> blockedServerHashes = ConcurrentHashMap.newKeySet();

    private final WebRequest webRequest;

    public MojangService(
            WebRequest webRequest,
            @Value("${mc-utils.mojang.session-server-url:https://sessionserver.mojang.com}") String sessionServerEndpoint,
            @Value("${mc-utils.mojang.api-url:https://api.mojang.com}") String apiEndpoint
    ) {
        this.webRequest = webRequest;
        this.sessionServerEndpoint = sessionServerEndpoint;
        this.apiEndpoint = apiEndpoint;
        updateBlockedServers();
    }

    /**
     * Gets the Session Server profile of the
     * player with the given UUID.
     *
     * @param id the uuid or name of the player
     * @return the profile
     */
    public MojangProfileToken getProfile(String id) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            MojangProfileToken result = webRequest.mojangProfileRequest(sessionServerEndpoint + "/session/minecraft/profile/" + id).useProxy().as(MojangProfileToken.class);
            success = result != null;
            return result;
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_MOJANG, "player_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Gets the UUID of the player using
     * the name of the player.
     *
     * @param id the name of the player
     * @return the profile
     */
    public MojangUsernameToUuidToken getUuidFromUsername(String id) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            MojangUsernameToUuidToken result = webRequest.mojangProfileRequest(apiEndpoint + "/users/profiles/minecraft/" + id).useProxy().as(MojangUsernameToUuidToken.class);
            success = result != null;
            return result;
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_MOJANG, "username_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Looks up up to 10 usernames at once. Names Mojang does not know (or that are currently
     * unclaimed) are absent from the result rather than mapped to a placeholder.
     *
     * @param names the usernames to look up, at most 10
     * @return the profiles Mojang returned, possibly empty
     */
    public List<MojangUsernameToUuidToken> getUuidsFromUsernames(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            MojangUsernameToUuidToken[] result = webRequest.mojangProfileRequest(apiEndpoint + "/profiles/minecraft").useProxy()
                    .post(names)
                    .asRequired(MojangUsernameToUuidToken[].class);
            success = true;
            return result == null ? List.of() : List.of(result);
        } finally {
            MetricService.getMetric(ExternalApiRequestsMetric.class).record(API_MOJANG, "bulk_username_lookup", success, System.currentTimeMillis() - start);
        }
    }

    /**
     * Check if the hash for the given
     * hostname is in the blocked server list.
     *
     * @param hostname the hostname to check
     * @return whether the hostname is blocked
     */
    @SuppressWarnings("deprecation")
    private boolean isServerHostnameBlocked(@NonNull String hostname) {
        return blockedServerHashes.contains(Hashing.sha1().hashBytes(hostname.toLowerCase().getBytes(StandardCharsets.ISO_8859_1)).toString());
    }

    /**
     * Check if the server with the
     * given hostname is blocked by Mojang.
     *
     * @param hostname the server hostname to check
     * @return whether the hostname is blocked
     */
    public boolean isServerBlocked(@NonNull String hostname) {
        if (hostname.isEmpty()) {
            return false;
        }

        while (hostname.charAt(hostname.length() - 1) == '.') {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        // Is the hostname banned?
        if (isServerHostnameBlocked(hostname)) {
            return true;
        }
        List<String> splitDots = Lists.newArrayList(DOT_SPLITTER.split(hostname)); // Split the hostname by dots
        boolean isIp = splitDots.size() == 4; // Is it an IP address?
        if (isIp) {
            for (String element : splitDots) {
                try {
                    int part = Integer.parseInt(element);
                    if (part >= 0 && part <= 255) { // Ensure the part is within the valid range
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                    // Safely ignore, not a number
                }
                isIp = false;
                break;
            }
        }
        if (!isIp && isServerHostnameBlocked("*." + hostname)) {
            return true;
        }
        // Additional checks for the hostname
        while (splitDots.size() > 1) {
            splitDots.remove(isIp ? splitDots.size() - 1 : 0);
            String starredPart = isIp ? DOT_JOINER.join(splitDots) + ".*" : "*." + DOT_JOINER.join(splitDots);
            if (isServerHostnameBlocked(starredPart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fetches the current list of banned server hashes from Mojang and updates {@link #blockedServerHashes}.
     * Uses the shared HTTP client (connection pooling, proxy if configured).
     */
    public void updateBlockedServers() {
        log.info("Fetching blocked servers from Mojang");
        byte[] bytes = webRequest.request(sessionServerEndpoint + "/blockedservers").asBytes();
        if (bytes == null) {
            log.error("Failed to fetch blocked servers from Mojang");
            return;
        }
        List<String> hashes = Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split("\n"));
        blockedServerHashes.clear();
        blockedServerHashes.addAll(hashes);
        log.info("Fetched {} blocked server hashes", blockedServerHashes.size());
    }
}
