package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "player_monthly_views")
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerMonthlyViewsRow {
    @Id
    @Column(name = "player_id")
    private UUID playerId;

    @Column(nullable = false, name = "views")
    private long views;
}
