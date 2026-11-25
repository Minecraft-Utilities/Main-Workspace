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
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface SkinRepository extends JpaRepository<SkinRow, Long> {
    Optional<SkinRow> findByTextureId(String textureId);

    List<SkinRow> findByTextureIdIn(Collection<String> textureIds);

    @Query("SELECT s.id FROM SkinRow s")
    List<Long> findSkinIds(Pageable pageable);

    /**
     * Walks only the sort index to find the page's ids, then loads those rows. Skipping rows through
     * the heap makes deep OFFSETs cost seconds; an index-only skip is orders of magnitude cheaper.
     */
    default List<SkinRow> findAllSkins(Pageable pageable) {
        List<Long> ids = findSkinIds(pageable);
        Map<Long, SkinRow> byId = findAllById(ids).stream().collect(Collectors.toMap(SkinRow::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Query("SELECT s FROM SkinRow s JOIN SkinTrendingRow t ON t.skinId = s.id ORDER BY t.heat DESC, s.id ASC")
    Slice<SkinRow> findTrendingSkins(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE SkinRow s SET s.legacy = :legacy WHERE s.textureId IN :textureIds AND s.legacy IS DISTINCT FROM :legacy")
    int updateLegacyByTextureIds(@Param("textureIds") Collection<String> textureIds, @Param("legacy") boolean legacy);
}
