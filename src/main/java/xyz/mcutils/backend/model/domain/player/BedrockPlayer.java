package xyz.mcutils.backend.model.domain.player;

import org.jspecify.annotations.Nullable;
import xyz.mcutils.backend.model.domain.skin.BedrockSkin;

import java.time.Instant;
import java.util.UUID;

/**
 * A Bedrock player resolved through Geyser. The UUID is the Floodgate UUID the player has
 * on Java servers, so it does not collide with Java account UUIDs.
 *
 * @param uniqueId      the Floodgate UUID
 * @param xuid          the Xbox user id
 * @param username      the gamertag
 * @param skin          the player's skin, null when Geyser has none for them
 * @param skinUpdatedAt when Geyser last saw the skin change, null when unknown
 */
public record BedrockPlayer(
        UUID uniqueId,
        String xuid,
        String username,
        @Nullable BedrockSkin skin,
        @Nullable Instant skinUpdatedAt
) {}
