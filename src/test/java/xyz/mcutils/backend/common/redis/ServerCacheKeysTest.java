package xyz.mcutils.backend.common.redis;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.model.domain.server.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerCacheKeysTest {

    @Test
    void hostnameCaseDoesNotSplitEntries() {
        assertEquals(ServerCacheKeys.server(Platform.JAVA, "Mc.Example.NET", 25565), ServerCacheKeys.server(Platform.JAVA, "mc.example.net", 25565));
    }

    @Test
    void trailingDotDoesNotSplitEntries() {
        assertEquals("server:java:mc.example.net:25565", ServerCacheKeys.server(Platform.JAVA, "mc.example.net.", 25565));
    }

    @Test
    void platformsAndPortsStaySeparate() {
        assertEquals("server:bedrock:mc.example.net:19132", ServerCacheKeys.server(Platform.BEDROCK, "MC.example.net", 19132));
        assertEquals("server:java:mc.example.net:25566", ServerCacheKeys.server(Platform.JAVA, "mc.example.net", 25566));
    }

    @Test
    void previewKeyUsesItsOwnNamespace() {
        assertEquals("serverPreview:java:mc.example.net:25565", ServerCacheKeys.preview(Platform.JAVA, "Mc.Example.Net.", 25565));
    }
}
