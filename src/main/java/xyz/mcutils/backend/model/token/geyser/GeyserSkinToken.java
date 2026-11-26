package xyz.mcutils.backend.model.token.geyser;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A Geyser skin lookup response. An unknown player is answered with an empty JSON object,
 * which deserializes to a token whose texture id is null.
 *
 * @param textureId  the textures.minecraft.net texture id
 * @param value      the base64 Mojang textures payload, used for the skin model
 * @param lastUpdate epoch milliseconds of the last skin update, null when absent
 */
public record GeyserSkinToken(
        @JsonProperty("texture_id") String textureId,
        @JsonProperty("value") String value,
        @JsonProperty("last_update") @Nullable Long lastUpdate
) {}
