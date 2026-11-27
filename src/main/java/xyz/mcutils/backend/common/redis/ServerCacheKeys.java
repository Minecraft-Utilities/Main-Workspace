package xyz.mcutils.backend.common.redis;

import xyz.mcutils.backend.model.domain.server.Platform;

import java.util.Locale;

/**
 * Redis keys for cached server data. Hostnames are case-insensitive and may carry a trailing root dot, so both are
 * normalised away to keep one entry per server.
 */
public final class ServerCacheKeys {

    public static String server(Platform platform, String hostname, int port) {
        return "server:" + identity(platform, hostname, port);
    }

    public static String preview(Platform platform, String hostname, int port) {
        return "serverPreview:" + identity(platform, hostname, port);
    }

    private static String identity(Platform platform, String hostname, int port) {
        String host = hostname.toLowerCase(Locale.ROOT);
        int end = host.length();
        while (end > 0 && host.charAt(end - 1) == '.') {
            end--;
        }
        return platform.name().toLowerCase(Locale.ROOT) + ":" + host.substring(0, end) + ":" + port;
    }
}
