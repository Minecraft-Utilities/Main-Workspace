package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

public interface CapeRepository extends JpaRepository<CapeRow, Long> {
    Optional<CapeRow> findByTextureId(String textureId);

    List<CapeRow> findByTextureIdIn(Collection<String> textureIds);

    @Query("SELECT c FROM CapeRow c ORDER BY c.uniqueOwners DESC, c.id ASC")
    List<CapeRow> findAllOrderByUniqueOwnersDescIdAsc();
}
