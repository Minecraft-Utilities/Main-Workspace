package xyz.mcutils.backend.model.persistence.redis;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import xyz.mcutils.backend.common.CachedResponse;
import xyz.mcutils.backend.model.domain.server.MinecraftServer;

/**
 * @author Braydon
 */
@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
public class CachedMinecraftServer extends CachedResponse {
    /**
     * The cached server.
     */
    @NonNull
    @JsonUnwrapped
    private MinecraftServer server;

    public CachedMinecraftServer(@NonNull MinecraftServer server) {
        super(false, System.currentTimeMillis());
        this.server = server;
    }
}
