package xyz.mcutils.backend.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.exception.impl.BadRequestException;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.player.BedrockPlayer;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.token.geyser.GeyserSkinToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BedrockPlayerServiceTest {
    private static final long XUID = 2535448955017314L;
    private static final String TEXTURE_ID = "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb";
    private static final String PUBLIC_URL = "https://mcutils.xyz";

    private GeyserService geyserService;
    private BedrockPlayerService bedrockPlayerService;

    @BeforeAll
    static void configureApp() {
        AppConfig config = new AppConfig();
        ReflectionTestUtils.setField(config, "webPublicUrl", PUBLIC_URL);
        AppConfig.INSTANCE = config;
    }

    @BeforeEach
    void setUp() {
        this.geyserService = mock(GeyserService.class);
        this.bedrockPlayerService = new BedrockPlayerService(this.geyserService);
    }

    @Test
    void resolvesGamertagWithFloodgatePrefixAndUnderscores() {
        when(this.geyserService.getXuid("Fascinated 4250")).thenReturn(XUID);
        when(this.geyserService.getSkin(XUID)).thenReturn(skinToken("slim"));

        BedrockPlayer player = this.bedrockPlayerService.getPlayer(".Fascinated_4250");

        assertEquals("Fascinated 4250", player.username());
        assertEquals(Long.toString(XUID), player.xuid());
        assertEquals(new UUID(0L, XUID), player.uniqueId());
        assertNotNull(player.skin());
        assertEquals(Skin.Model.SLIM, player.skin().model());
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), player.skinUpdatedAt());
        assertEquals(PUBLIC_URL + "/bedrock/players/" + player.uniqueId() + "/skin/texture.png", player.skin().textureUrl());
        assertEquals(PUBLIC_URL + "/bedrock/players/" + player.uniqueId() + "/skin/face.png", player.skin().parts().get("FACE"));
        verify(this.geyserService).getXuid("Fascinated 4250");
        verify(this.geyserService, never()).getGamertag(anyLong());
    }

    @Test
    void resolvesFloodgateUuidWithAndWithoutDashes() {
        UUID uniqueId = new UUID(0L, XUID);
        when(this.geyserService.getGamertag(XUID)).thenReturn("Fascinated4250");
        when(this.geyserService.getSkin(XUID)).thenReturn(skinToken("default"));

        for (String query : List.of(uniqueId.toString(), uniqueId.toString().replace("-", ""))) {
            BedrockPlayer player = this.bedrockPlayerService.getPlayer(query);
            assertEquals("Fascinated4250", player.username());
            assertEquals(uniqueId, player.uniqueId());
        }
        verify(this.geyserService, times(2)).getGamertag(XUID);
        verify(this.geyserService, never()).getXuid(anyString());
    }

    @Test
    void rejectsNonFloodgateUuid() {
        assertThrows(BadRequestException.class,
                () -> this.bedrockPlayerService.getPlayer("123e4567-e89b-12d3-a456-426614174000"));
        verifyNoInteractions(this.geyserService);
    }

    @Test
    void rejectsOverlongGamertag() {
        assertThrows(BadRequestException.class, () -> this.bedrockPlayerService.getPlayer("abcdefghijklmnopq"));
        verifyNoInteractions(this.geyserService);
    }

    @Test
    void throwsNotFoundForUnknownGamertag() {
        when(this.geyserService.getXuid("Unknown")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> this.bedrockPlayerService.getPlayer("Unknown"));
    }

    @Test
    void returnsPlayerWithoutSkin() {
        when(this.geyserService.getXuid("Fascinated4250")).thenReturn(XUID);
        when(this.geyserService.getSkin(XUID)).thenReturn(null);

        BedrockPlayer player = this.bedrockPlayerService.getPlayer("Fascinated4250");

        assertNotNull(player);
        assertNull(player.skin());
        assertNull(player.skinUpdatedAt());
    }

    @Test
    void uuidSkinLookupDoesNotCallGamertag() {
        when(this.geyserService.getSkin(XUID)).thenReturn(skinToken("default"));

        Skin skin = this.bedrockPlayerService.getSkin(new UUID(0L, XUID).toString());

        assertEquals(TEXTURE_ID, skin.getTextureId());
        assertEquals(Skin.Model.DEFAULT, skin.getModel());
        assertEquals(Skin.CDN_URL.formatted(TEXTURE_ID), skin.getRawTextureUrl());
        verify(this.geyserService, never()).getGamertag(anyLong());
        verify(this.geyserService, never()).getXuid(anyString());
    }

    @Test
    void throwsNotFoundWhenPlayerHasNoSkin() {
        when(this.geyserService.getSkin(XUID)).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> this.bedrockPlayerService.getSkin(new UUID(0L, XUID).toString()));
    }

    private static GeyserSkinToken skinToken(String model) {
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + TEXTURE_ID
                + "\",\"metadata\":{\"model\":\"" + model + "\"}}}}";
        String value = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new GeyserSkinToken(TEXTURE_ID, value, 1_700_000_000_000L);
    }
}
