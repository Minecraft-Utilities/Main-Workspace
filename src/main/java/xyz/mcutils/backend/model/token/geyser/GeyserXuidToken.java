package xyz.mcutils.backend.model.token.geyser;

/**
 * A Geyser xuid lookup response.
 *
 * @param xuid the Xbox user id of the gamertag
 */
public record GeyserXuidToken(long xuid) {}
