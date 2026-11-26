package xyz.mcutils.backend.model.domain.skin;

import java.util.Map;

/**
 * The skin of a Bedrock player, addressed through the player's Floodgate UUID.
 *
 * @param textureId  the textures.minecraft.net texture id
 * @param textureUrl the public URL of the raw texture
 * @param model      the skin model taken from the texture metadata
 * @param parts      the rendered parts, keyed by {@link Skin.SkinPart} name
 */
public record BedrockSkin(String textureId, String textureUrl, Skin.Model model, Map<String, String> parts) {}
