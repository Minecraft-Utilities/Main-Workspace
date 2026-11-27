package xyz.mcutils.backend.repository.redis;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import xyz.mcutils.backend.common.redis.RedisBlobCache;
import xyz.mcutils.backend.model.domain.asn.AsnLookup;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.dns.impl.ARecord;
import xyz.mcutils.backend.model.domain.dns.impl.SRVRecord;
import xyz.mcutils.backend.model.domain.geo.GeoLocation;
import xyz.mcutils.backend.model.domain.server.Favicon;
import xyz.mcutils.backend.model.domain.server.MOTD;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;
import xyz.mcutils.backend.model.domain.server.Platform;
import xyz.mcutils.backend.model.domain.server.Players;
import xyz.mcutils.backend.model.domain.server.bedrock.BedrockEdition;
import xyz.mcutils.backend.model.domain.server.bedrock.BedrockGameMode;
import xyz.mcutils.backend.model.domain.server.bedrock.BedrockMinecraftServer;
import xyz.mcutils.backend.model.domain.server.bedrock.BedrockVersion;
import xyz.mcutils.backend.model.domain.server.java.ForgeData;
import xyz.mcutils.backend.model.domain.server.java.ForgeModInfo;
import xyz.mcutils.backend.model.domain.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.domain.server.java.JavaVersion;
import xyz.mcutils.backend.model.domain.serverregistry.ServerRegistryEntry;
import xyz.mcutils.backend.model.persistence.redis.CachedMinecraftServer;
import org.xbill.DNS.Name;
import org.xbill.DNS.DClass;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinecraftServerCacheRepositoryTest {

    private static final JsonMapper API_MAPPER = JsonMapper.builder().build();

    @Test
    void javaServerSurvivesRoundTripWithEveryApiField() throws Exception {
        JavaMinecraftServer original = javaServer();
        CachedMinecraftServer restored = roundTrip(Platform.JAVA, original);

        assertInstanceOf(JavaMinecraftServer.class, restored.getServer());
        assertEquals(API_MAPPER.readTree(API_MAPPER.writeValueAsString(original)), API_MAPPER.readTree(API_MAPPER.writeValueAsString(restored.getServer())));
        JavaMinecraftServer server = (JavaMinecraftServer) restored.getServer();
        assertInstanceOf(SRVRecord.class, server.getRecords()[0]);
        assertInstanceOf(ARecord.class, server.getRecords()[1]);
        assertTrue(server.isModded());
        assertTrue(server.isMojangBlocked());
        assertEquals("iVBOR", server.getFavicon().getBase64());
    }

    @Test
    void bedrockServerSurvivesRoundTripWithEveryApiField() throws Exception {
        BedrockMinecraftServer original = BedrockMinecraftServer.builder()
                .id("1234").edition(BedrockEdition.MCPE).version(new BedrockVersion(819, "1.21.50")).gamemode(new BedrockGameMode("Survival", 1))
                .hostname("play.example.net").ip("203.0.113.9").port(19132).records(new DNSRecord[0])
                .motd(new MOTD(new String[]{"§aHi"}, new String[]{"Hi"}, new String[]{"<span>Hi</span>"}, "https://x/preview.png"))
                .players(new Players(3, 20, null)).build();
        CachedMinecraftServer restored = roundTrip(Platform.BEDROCK, original);

        assertInstanceOf(BedrockMinecraftServer.class, restored.getServer());
        assertEquals(API_MAPPER.readTree(API_MAPPER.writeValueAsString(original)), API_MAPPER.readTree(API_MAPPER.writeValueAsString(restored.getServer())));
    }

    @Test
    void restoredServerIsMarkedCachedWithItsSaveTime() throws Exception {
        long before = System.currentTimeMillis();
        CachedMinecraftServer restored = roundTrip(Platform.JAVA, javaServer());

        assertTrue(restored.isCached());
        assertTrue(restored.getCachedTime() >= before && restored.getCachedTime() <= System.currentTimeMillis());
    }

    @Test
    void keysDifferingOnlyInCaseShareOneEntry() {
        RedisBlobCache cache = mock(RedisBlobCache.class);
        MinecraftServerCacheRepository repository = new MinecraftServerCacheRepository(cache);
        repository.save(Platform.JAVA, "Mc.Example.NET", 25565, javaServer());

        verify(cache).set(eq("server:java:mc.example.net:25565"), any(byte[].class), eq(MinecraftServerCacheRepository.TTL));
    }

    @Test
    void unreadablePayloadIsAMiss() {
        RedisBlobCache cache = mock(RedisBlobCache.class);
        when(cache.get("server:java:mc.example.net:25565")).thenReturn(Optional.of("not json".getBytes()));

        assertTrue(new MinecraftServerCacheRepository(cache).find(Platform.JAVA, "mc.example.net", 25565).isEmpty());
    }

    private static CachedMinecraftServer roundTrip(Platform platform, MinecraftServer server) {
        RedisBlobCache cache = mock(RedisBlobCache.class);
        MinecraftServerCacheRepository repository = new MinecraftServerCacheRepository(cache);
        repository.save(platform, "host.example.net", 25565, server);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache).set(key.capture(), stored.capture(), eq(MinecraftServerCacheRepository.TTL));
        when(cache.get(key.getValue())).thenReturn(Optional.of(stored.getValue()));
        return repository.find(platform, "host.example.net", 25565).orElseThrow();
    }

    private static JavaMinecraftServer javaServer() {
        try {
            SRVRecord srv = new SRVRecord(new org.xbill.DNS.SRVRecord(Name.fromString("_minecraft._tcp.example.net."), DClass.IN, 300, 0, 5, 25565, Name.fromString("mc.example.net.")));
            ARecord a = new ARecord(new org.xbill.DNS.ARecord(Name.fromString("mc.example.net."), DClass.IN, 60, InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 7})));
            return JavaMinecraftServer.builder()
                    .hostname("mc.example.net").ip("203.0.113.7").port(25565).records(new DNSRecord[]{srv, a})
                    .motd(new MOTD(new String[]{"§aExample", "§7Net"}, new String[]{"Example", "Net"}, new String[]{"<span>Example</span>", "<span>Net</span>"}, "https://x/preview.png"))
                    .players(new Players(12, 100, new Players.Sample[]{new Players.Sample(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), new Players.Sample.Name("Notch", "Notch", "Notch"), "https://x/players/069a79f4")}))
                    .version(new JavaVersion("Paper 1.21.4", 769, "1.21.4", "Paper"))
                    .modInfo(new ForgeModInfo("FML", null))
                    .forgeData(new ForgeData(new ForgeData.Channel[]{new ForgeData.Channel("fml:handshake", "1", true)}, new ForgeData.Mod[]{new ForgeData.Mod("create", "0.5")}, false, 3))
                    .favicon(new Favicon("iVBOR", "https://x/icon.png"))
                    .preventsChatReports(true).enforcesSecureChat(true).previewsChat(true).mojangBlocked(true).isModded(true)
                    .location(new GeoLocation("United States", "US", "Virginia", "Ashburn", "America/New_York", "20149", 39.03, -77.5, "https://x/us.png"))
                    .asn(new AsnLookup("AS14618", "Amazon.com, Inc.", "203.0.113.0/24"))
                    .registryEntry(new ServerRegistryEntry("example", "Example", List.of("mc.example.net"), List.of("*.example.net"), "https://x/bg.png", Platform.JAVA))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
