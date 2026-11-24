package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skin_trending")
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SkinTrendingRow {
    @Id
    @Column(name = "skin_id")
    private long skinId;

    @Column(nullable = false, name = "heat")
    private int heat;
}
