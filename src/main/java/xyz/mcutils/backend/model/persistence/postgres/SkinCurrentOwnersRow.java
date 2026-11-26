package xyz.mcutils.backend.model.persistence.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skin_current_owners")
@Getter
@NoArgsConstructor
public class SkinCurrentOwnersRow {
    @Id
    @Column(name = "skin_id")
    private long skinId;

    @Column(nullable = false, name = "owners", insertable = false, updatable = false)
    private long owners;
}
