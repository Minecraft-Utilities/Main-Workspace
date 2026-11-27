package xyz.mcutils.backend.common.renderer;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.impl.server.ServerPreviewRenderer;
import xyz.mcutils.backend.model.domain.dns.DNSRecord;
import xyz.mcutils.backend.model.domain.server.MOTD;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;
import xyz.mcutils.backend.model.domain.server.Players;
import xyz.mcutils.backend.model.domain.server.java.JavaMinecraftServer;
import xyz.mcutils.backend.model.domain.server.java.JavaVersion;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ServerPreviewRendererTest {

    private static MinecraftServer server() {
        return JavaMinecraftServer.builder()
                .hostname("mc.example.net").ip("203.0.113.7").port(25565).records(new DNSRecord[0])
                .motd(new MOTD(new String[]{"§aExample", "§7Net"}, new String[]{"Example", "Net"}, new String[]{"", ""}, "https://x/preview.png"))
                .players(new Players(12, 100, null))
                .version(new JavaVersion("Paper 1.21.4", 769, "1.21.4", "Paper"))
                .build();
    }

    @Test
    void canonicalPreviewHasNativeWidthRegardlessOfRequestedSize() {
        BufferedImage canonical = ServerPreviewRenderer.INSTANCE.renderCanonical(server());

        assertEquals(ServerPreviewRenderer.ROW_WIDTH, canonical.getWidth());
        assertEquals(ServerPreviewRenderer.ROW_WIDTH, ServerPreviewRenderer.resize(canonical, ServerPreviewRenderer.ROW_WIDTH).getWidth());
        assertSame(canonical, ServerPreviewRenderer.resize(canonical, ServerPreviewRenderer.ROW_WIDTH));
    }

    @Test
    void resizingAStoredCanonicalPngMatchesRenderingAtThatSize() {
        MinecraftServer server = server();
        BufferedImage direct = ServerPreviewRenderer.INSTANCE.render(server, 256);
        byte[] stored = ImageUtils.imageToBytes(ServerPreviewRenderer.INSTANCE.renderCanonical(server));
        BufferedImage fromStored = ServerPreviewRenderer.resize(ImageUtils.decodeImage(stored), 256);

        assertEquals(256, fromStored.getWidth());
        assertEquals(direct.getHeight(), fromStored.getHeight());
        assertArrayEquals(direct.getRGB(0, 0, 256, direct.getHeight(), null, 0, 256), fromStored.getRGB(0, 0, 256, fromStored.getHeight(), null, 0, 256));
    }
}
