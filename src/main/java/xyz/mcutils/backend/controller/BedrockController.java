package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.model.domain.player.BedrockPlayer;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.service.BedrockPlayerService;
import xyz.mcutils.backend.service.SkinService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/bedrock")
@Tag(name = "Bedrock Controller", description = "The Bedrock Controller is used to get information about Bedrock players.")
public class BedrockController {
    private final BedrockPlayerService bedrockPlayerService;
    private final SkinService skinService;

    public BedrockController(BedrockPlayerService bedrockPlayerService, SkinService skinService) {
        this.bedrockPlayerService = bedrockPlayerService;
        this.skinService = skinService;
    }

    @GetMapping(value = "/players/{query}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BedrockPlayer> getBedrockPlayer(
            @Parameter(description = "The gamertag or Floodgate UUID of the player", example = "Fascinated4250") @PathVariable String query) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(this.bedrockPlayerService.getPlayer(query));
    }

    @GetMapping(value = "/players/{query}/skin/{type}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getBedrockPlayerSkin(
            @Parameter(description = "The gamertag or Floodgate UUID of the player", example = "Fascinated4250") @PathVariable String query,
            @Parameter(description = "The part of the skin", schema = @Schema(implementation = Skin.SkinPart.class)) @PathVariable String type,
            @Parameter(description = "The size of the image (height; width derived per part)", example = "768") @RequestParam(required = false, defaultValue = "768") int size,
            @Parameter(description = "Whether to render the skin overlay (skin layers)", example = "true") @RequestParam(required = false, defaultValue = "true") boolean overlays) {
        Skin skin = this.bedrockPlayerService.getSkin(query);
        byte[] bytes = this.skinService.renderSkin(skin, type, new RenderOptions(overlays, null), size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }

    @GetMapping(value = "/players/{query}/skin/texture.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getBedrockPlayerSkinTexture(
            @Parameter(description = "The gamertag or Floodgate UUID of the player", example = "Fascinated4250") @PathVariable String query) {
        Skin skin = this.bedrockPlayerService.getSkin(query);
        byte[] texture = this.skinService.getSkinTexture(skin.getTextureId(), skin.getRawTextureUrl(), true);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(texture);
    }
}
