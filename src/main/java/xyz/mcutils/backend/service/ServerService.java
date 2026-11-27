package xyz.mcutils.backend.service;

import com.google.common.net.InetAddresses;
import io.minio.Utils.InetAddressValidator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.EnumUtils;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.redis.RedisBlobCache;
import xyz.mcutils.backend.common.redis.ServerCacheKeys;
import xyz.mcutils.backend.common.renderer.impl.server.ServerPreviewRenderer;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.server.ServerLookupMetric;
import xyz.mcutils.backend.model.domain.IpLookup;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.dns.impl.ARecord;
import xyz.mcutils.backend.model.domain.dns.impl.SRVRecord;
import xyz.mcutils.backend.model.domain.server.Favicon;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;
import xyz.mcutils.backend.model.domain.server.Platform;
import xyz.mcutils.backend.model.domain.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.persistence.redis.CachedMinecraftServer;
import xyz.mcutils.backend.repository.redis.MinecraftServerCacheRepository;

import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ServerService {
    public static final String DEFAULT_SERVER_ICON = "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";
    private static final Duration PREVIEW_TTL = Duration.ofHours(6);
    private static final InetAddressValidator IP_VALIDATOR = InetAddressValidator.getInstance();
    private final MojangService mojangService;
    private final MaxMindService maxMindService;
    private final DNSService dnsService;
    private final ServerRegistryService serverRegistryService;
    private final MinecraftServerCacheRepository serverCacheRepository;
    private final RedisBlobCache previewCache;
    private final CoalescingLoader<String, CachedMinecraftServer> serverLoader = new CoalescingLoader<>(Main.EXECUTOR);
    private final CoalescingLoader<String, byte[]> previewLoader = new CoalescingLoader<>(Main.EXECUTOR);
    @Value("${mc-utils.cache.servers.enabled}")
    private boolean cacheEnabled;
    @Value("${mc-utils.renderer.server-preview.cache}")
    private boolean previewCacheEnabled;
    @Value("${mc-utils.renderer.server-preview.enabled}")
    private boolean renderingEnabled;
    @Value("${mc-utils.renderer.server-preview.limits.min_size}")
    private int minPreviewSize;
    @Value("${mc-utils.renderer.server-preview.limits.max_size}")
    private int maxPreviewSize;
    @Value("${mc-utils.server-pinger.java.timeout}")
    private int javaPingerTimeout;
    @Value("${mc-utils.server-pinger.bedrock.timeout}")
    private int bedrockPingerTimeout;
    @Value("#{'${mc-utils.server-pinger.blacklisted-subnets}'.split(',')}")
    private List<String> blacklistedSubnetCidrs;
    private List<Subnet> blacklistedSubnets;

    public ServerService(MojangService mojangService, MaxMindService maxMindService, DNSService dnsService, ServerRegistryService serverRegistryService, MinecraftServerCacheRepository serverCacheRepository, RedisBlobCache previewCache) {
        this.mojangService = mojangService;
        this.maxMindService = maxMindService;
        this.dnsService = dnsService;
        this.serverRegistryService = serverRegistryService;
        this.serverCacheRepository = serverCacheRepository;
        this.previewCache = previewCache;
    }

    @PostConstruct
    void parseBlacklistedSubnets() {
        this.blacklistedSubnets = parseSubnets(this.blacklistedSubnetCidrs);
    }

    /**
     * Ping a server to get the server information.
     *
     * @param platformName the name of the platform
     * @param hostname     the hostname of the server
     * @return the server
     */
    public CachedMinecraftServer getServer(String platformName, String hostname) {
        if (IP_VALIDATOR.isValid(hostname)) {
            this.checkSubnet(hostname);
        }

        Platform platform = EnumUtils.getEnumConstant(Platform.class, platformName.toUpperCase());
        if (platform == null) {
            log.debug("Invalid platform: {} for server {}", platformName, hostname);
            throw new BadRequestException("Invalid platform: '%s'".formatted(platformName));
        }
        int port = platform.getDefaultPort();
        if (hostname.contains(":")) {
            String[] parts = hostname.split(":");
            hostname = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.debug("Invalid port: {} for server {}", parts[1], hostname);
                throw new BadRequestException("Invalid port: '%s'".formatted(parts[1]));
            }
            if (port < 1 || port > 65535) {
                log.debug("Invalid port: {} for server {}", port, hostname);
                throw new BadRequestException("Invalid port: '%s'".formatted(port));
            }
        }
        String key = ServerCacheKeys.server(platform, hostname, port);
        log.debug("Getting server: {}:{}", hostname, port);

        long cacheStart = System.currentTimeMillis();
        if (cacheEnabled) {
            Optional<CachedMinecraftServer> cached = serverCacheRepository.find(platform, hostname, port);
            if (cached.isPresent()) {
                CachedMinecraftServer server = cached.get();
                log.debug("Got server {}:{} from cache in {}ms", hostname, port, System.currentTimeMillis() - cacheStart);
                MetricService.getMetric(ServerLookupMetric.class).recordHit();
                return server;
            }
        }

        String resolvedHostname = hostname;
        int resolvedPort = port;
        // A popular server can miss the cache in bursts, so one ping serves every caller waiting on this key.
        return serverLoader.get(key, () -> this.loadServer(platform, resolvedHostname, resolvedPort));
    }

    private CachedMinecraftServer loadServer(Platform platform, String hostname, int port) {
        String requestedHostname = hostname;
        int requestedPort = port;
        List<DNSRecord> dnsRecords = new ArrayList<>();

        SRVRecord srvRecord = platform == Platform.JAVA ? dnsService.resolveSRV(hostname) : null; // Resolve the SRV record
        if (srvRecord != null) { // SRV was resolved, use the hostname and port
            dnsRecords.add(srvRecord); // Going to need this for later
            InetSocketAddress socketAddress = srvRecord.getSocketAddress();
            hostname = socketAddress.getHostName();
            port = socketAddress.getPort();
        }

        ARecord aRecord = dnsService.resolveA(hostname); // Resolve the A record so we can get the IPv4 address
        String ip = aRecord == null ? null : aRecord.getAddress(); // Get the IP address
        if (ip != null) { // Was the IP resolved?
            dnsRecords.add(aRecord); // Going to need this for later
            log.debug("Resolved hostname: {} -> {}", hostname, ip);
        }

        if (ip == null) {
            throw new BadRequestException("Hostname returned an invalid ip: '%s'".formatted(hostname));
        }

        if (IP_VALIDATOR.isValid(ip)) {
            this.checkSubnet(ip);
        }

        long pingStart = System.currentTimeMillis();
        CachedMinecraftServer cachedServer = new CachedMinecraftServer(platform.getPinger().ping(hostname, ip, port, dnsRecords.toArray(new DNSRecord[0]), platform == Platform.JAVA ? javaPingerTimeout : bedrockPingerTimeout));
        long pingDurationMs = System.currentTimeMillis() - pingStart;
        log.debug("Successfully pinged server: {}:{} in {}ms", hostname, port, pingDurationMs);
        MetricService.getMetric(ServerLookupMetric.class).recordMiss(pingDurationMs);

        // Populate the server's ip lookup data
        IpLookup ipLookup = maxMindService.lookupIp(ip);
        cachedServer.getServer().setLocation(ipLookup.location());
        cachedServer.getServer().setAsn(ipLookup.asn());

        this.serverRegistryService.getEntryByHostname(hostname).ifPresent(serverRegistryEntry -> cachedServer.getServer().setRegistryEntry(serverRegistryEntry));

        if (platform == Platform.JAVA) {
            ((JavaMinecraftServer) cachedServer.getServer()).setMojangBlocked(mojangService.isServerBlocked(hostname));
        }

        if (cacheEnabled) {
            CompletableFuture.runAsync(() -> this.serverCacheRepository.save(platform, requestedHostname, requestedPort, cachedServer.getServer()), Main.EXECUTOR).exceptionally(ex -> {
                log.warn("Save failed for server {}: {}", requestedHostname, ex.getMessage());
                return null;
            });
        }
        return cachedServer;
    }

    /**
     * Gets the server favicon.
     *
     * @param hostname the hostname of the server
     * @return the server favicon, null if not found
     */
    public byte[] getServerFavicon(String hostname) {
        String icon = null; // The server base64 icon
        try {
            Favicon favicon = ((JavaMinecraftServer) getServer(Platform.JAVA.name(), hostname).getServer()).getFavicon();
            if (favicon != null) { // Use the server's favicon
                icon = favicon.getBase64();
                icon = icon.substring(icon.indexOf(",") + 1); // Remove the data type from the server icon
            }
        } catch (BadRequestException | NotFoundException ignored) {
            // Safely ignore these, we will use the default server icon
        }
        if (icon == null) { // Use the default server icon
            icon = DEFAULT_SERVER_ICON;
        }
        return Base64.getDecoder().decode(icon); // Return the decoded favicon
    }

    /**
     * Gets the server list preview image.
     *
     * @param cachedServer the server to get the preview of
     * @param platform     the platform of the server
     * @param size         the size of the preview
     * @return the server preview
     */
    public byte[] getServerPreview(CachedMinecraftServer cachedServer, String platform, int size) {
        if (!renderingEnabled) {
            throw new BadRequestException("Skin rendering is currently disabled");
        }
        if (size < minPreviewSize || size > maxPreviewSize) {
            throw new BadRequestException("Invalid server preview size. Must be between " + minPreviewSize + " and " + maxPreviewSize);
        }

        MinecraftServer server = cachedServer.getServer();
        log.debug("Getting preview for server: {}:{} (size {})", server.getHostname(), server.getPort(), size);
        String key = ServerCacheKeys.preview(EnumUtils.getEnumConstant(Platform.class, platform.toUpperCase(Locale.ROOT)), server.getHostname(), server.getPort());

        byte[] canonical = getCanonicalPreview(key, server);
        if (size == ServerPreviewRenderer.ROW_WIDTH) {
            return canonical;
        }
        BufferedImage resized = ServerPreviewRenderer.resize(ImageUtils.decodeImage(canonical), size);
        return ImageUtils.imageToBytes(resized);
    }

    /**
     * Gets the preview at its native size. The artwork does not depend on the requested size, so one stored image
     * serves every size.
     */
    private byte[] getCanonicalPreview(String key, MinecraftServer server) {
        long cacheStart = System.currentTimeMillis();
        if (previewCacheEnabled) {
            Optional<byte[]> cached = previewCache.get(key);
            if (cached.isPresent()) {
                log.debug("Got server preview {}:{} from cache in {}ms", server.getHostname(), server.getPort(), System.currentTimeMillis() - cacheStart);
                return cached.get();
            }
        }

        // Rendering is CPU-bound, so a burst of requests for one preview must share a single render.
        return previewLoader.get(key, () -> {
            long renderStart = System.currentTimeMillis();
            byte[] preview = ImageUtils.imageToBytes(ServerPreviewRenderer.INSTANCE.renderCanonical(server));
            log.debug("Took {}ms to render preview for server: {}:{}", System.currentTimeMillis() - renderStart, server.getHostname(), server.getPort());

            // don't save to cache in development
            if (previewCacheEnabled) {
                CompletableFuture.runAsync(() -> previewCache.set(key, preview, PREVIEW_TTL), Main.EXECUTOR).exceptionally(ex -> {
                    log.warn("Save failed for server preview {}: {}", key, ex.getMessage());
                    return null;
                });
            }
            return preview;
        });
    }

    /**
     * Checks if the IP is in a blacklisted subnet.
     *
     * @param ip the ip to check
     */
    private void checkSubnet(String ip) {
        InetAddress address;
        try {
            address = InetAddresses.forString(ip);
        } catch (Exception e) {
            throw new BadRequestException("Invalid IP address");
        }

        for (Subnet subnet : this.blacklistedSubnets) {
            if (isInSubnet(address, subnet)) {
                throw new BadRequestException("IP address is in a blacklisted subnet");
            }
        }
    }

    static List<Subnet> parseSubnets(List<String> cidrs) {
        List<Subnet> subnets = new ArrayList<>(cidrs.size());
        for (String cidr : cidrs) {
            String trimmed = cidr.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int slash = trimmed.indexOf('/');
            if (slash < 1) {
                throw new IllegalStateException("Invalid blacklisted subnet: '%s'".formatted(trimmed));
            }
            byte[] network;
            try {
                network = InetAddresses.forString(trimmed.substring(0, slash)).getAddress();
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid blacklisted subnet: '%s'".formatted(trimmed), e);
            }
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(trimmed.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid blacklisted subnet: '%s'".formatted(trimmed), e);
            }
            if (prefixLength < 0 || prefixLength > network.length * 8) {
                throw new IllegalStateException("Invalid blacklisted subnet prefix: '%s'".formatted(trimmed));
            }
            subnets.add(new Subnet(network, prefixLength));
        }
        return List.copyOf(subnets);
    }

    /**
     * Checks if the given address is inside the subnet.
     *
     * @param address the address to check
     * @param subnet  the pre-parsed subnet to check
     * @return if the given address is inside the subnet
     */
    static boolean isInSubnet(InetAddress address, Subnet subnet) {
        byte[] addressBytes = address.getAddress();
        byte[] subnetBytes = subnet.network();
        if (addressBytes.length != subnetBytes.length) {
            return false;
        }

        int prefixLength = subnet.prefixLength();
        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addressBytes[i] != subnetBytes[i]) {
                return false;
            }
        }

        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF00 >> remainingBits;
        return (addressBytes[fullBytes] & mask) == (subnetBytes[fullBytes] & mask);
    }

    record Subnet(byte[] network, int prefixLength) {
    }
}
