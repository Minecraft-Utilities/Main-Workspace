package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.util.Optional;

public interface SkinRepository extends JpaRepository<SkinRow, Long> {
    Optional<SkinRow> findByTextureId(String textureId);

    @Query("SELECT s FROM SkinRow s")
    Slice<SkinRow> findAllSkins(Pageable pageable);

    @Query("SELECT s FROM SkinRow s JOIN SkinTrendingRow t ON t.skinId = s.id ORDER BY t.heat DESC, s.id ASC")
    Slice<SkinRow> findTrendingSkins(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE SkinRow s SET s.legacy = :legacy WHERE s.textureId = :textureId")
    int updateLegacyByTextureId(@Param("textureId") String textureId, @Param("legacy") boolean legacy);
}
