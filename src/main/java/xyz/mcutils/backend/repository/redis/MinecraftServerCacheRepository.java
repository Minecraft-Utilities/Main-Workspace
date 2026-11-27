package xyz.mcutils.backend.repository.redis;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import xyz.mcutils.backend.common.redis.RedisBlobCache;
import xyz.mcutils.backend.common.redis.ServerCacheKeys;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.dns.impl.ARecord;
import xyz.mcutils.backend.model.domain.dns.impl.SRVRecord;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;
import xyz.mcutils.backend.model.domain.server.Platform;
import xyz.mcutils.backend.model.domain.server.bedrock.BedrockMinecraftServer;
import xyz.mcutils.backend.model.domain.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.persistence.redis.CachedMinecraftServer;

import java.time.Duration;
import java.util.Optional;

/**
 * Caches pinged servers as one JSON value per server.
 * <p>
 * The platform is part of the key, so the payload carries no type information; it selects the concrete
 * {@link MinecraftServer} class when reading.
 *
 * @author Braydon
 */
@Repository
@Slf4j
public class MinecraftServerCacheRepository {

    public static final Duration TTL = Duration.ofMinutes(10);

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addMixIn(DNSRecord.class, DnsRecordMixin.class)
            .build();

    private final RedisBlobCache cache;

    public MinecraftServerCacheRepository(RedisBlobCache cache) {
        this.cache = cache;
    }

    public Optional<CachedMinecraftServer> find(Platform platform, String hostname, int port) {
        Optional<byte[]> bytes = cache.get(ServerCacheKeys.server(platform, hostname, port));
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        try {
            JavaType type = MAPPER.getTypeFactory().constructParametricType(Entry.class, serverType(platform));
            Entry<MinecraftServer> entry = MAPPER.readValue(bytes.get(), type);
            CachedMinecraftServer server = new CachedMinecraftServer(entry.server());
            server.setCached(true);
            server.setCachedTime(entry.cachedTime());
            return Optional.of(server);
        } catch (JacksonException e) {
            log.warn("Discarding unreadable cached server {}:{}: {}", hostname, port, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Platform platform, String hostname, int port, MinecraftServer server) {
        byte[] bytes = MAPPER.writeValueAsBytes(new Entry<>(System.currentTimeMillis(), server));
        cache.set(ServerCacheKeys.server(platform, hostname, port), bytes, TTL);
    }

    private static Class<? extends MinecraftServer> serverType(Platform platform) {
        return switch (platform) {
            case JAVA -> JavaMinecraftServer.class;
            case BEDROCK -> BedrockMinecraftServer.class;
        };
    }

    private record Entry<T>(long cachedTime, T server) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ARecord.class, name = "A"),
            @JsonSubTypes.Type(value = SRVRecord.class, name = "SRV")
    })
    private interface DnsRecordMixin {
    }
}
