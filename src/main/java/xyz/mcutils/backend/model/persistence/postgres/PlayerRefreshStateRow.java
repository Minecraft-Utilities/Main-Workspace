package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_refresh_state")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerRefreshStateRow {
    @Id
    @Column(name = "player_id")
    private UUID playerId;

    @Column(nullable = false, name = "next_refresh_at")
    private Instant nextRefreshAt;

    @Column(nullable = false, name = "change_velocity")
    private double changeVelocity;

    @Column(nullable = false, name = "last_updated")
    private Instant lastUpdated;
}
