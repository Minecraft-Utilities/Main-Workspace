package xyz.mcutils.backend.repository.postgres;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.util.*;

public interface PlayerRepository extends JpaRepository<PlayerRow, UUID> {
    @Query("SELECT p.id FROM PlayerRow p WHERE UPPER(p.username) = UPPER(:username)")
    Optional<UUID> findIdByUsernameIgnoreCase(@Param("username") String username);

    /** Resolves the id first: a joined username predicate makes the planner pick a parallel plan that costs far more than two index lookups. */
    default Optional<PlayerRow> findByUsernameIgnoreCase(String username) {
        return findIdByUsernameIgnoreCase(username).flatMap(this::findById);
    }

    @Override
    @EntityGraph(attributePaths = {"skin", "cape"})
    Optional<PlayerRow> findById(UUID id);


    /**
     * Case-insensitive prefix search, written as a byte-order range so the {@code text_pattern_ops} index serves it
     * with a bind parameter. {@code chr(1114111)} is the highest code point, so the upper bound sits past every
     * username that starts with the prefix.
     */
    @Query(value = """
            SELECT * FROM players
            WHERE upper(username) ~>=~ upper(:prefix) AND upper(username) ~<~ (upper(:prefix) || chr(1114111))
            ORDER BY upper(username) USING ~<~
            LIMIT :limit
            """, nativeQuery = true)
    List<PlayerRow> searchByUsernamePrefix(@Param("prefix") String prefix, @Param("limit") int limit);
    @EntityGraph(attributePaths = {"skin", "cape"})
    List<PlayerRow> findAllByOrderBySubmittedUuidsDesc(Pageable pageable);

    @Query("SELECT p.username FROM PlayerRow p WHERE p.skin.id = :skinId")
    List<String> findUsernamesBySkinId(long skinId, Pageable pageable);

    @Query("SELECT p.username FROM PlayerRow p WHERE p.cape.id = :capeId")
    List<String> findUsernamesByCapeId(long capeId, Pageable pageable);

    @Query("SELECT p.id FROM PlayerRow p WHERE p.id IN :ids")
    Set<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlayerRow p WHERE p.id = :id")
    Optional<PlayerRow> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p.skin FROM PlayerRow p WHERE p.id = :id")
    Optional<SkinRow> findSkinById(@Param("id") UUID id);

    @Query("SELECT p.skin FROM PlayerRow p WHERE UPPER(p.username) = UPPER(:username)")
    Optional<SkinRow> findSkinByUsernameIgnoreCase(@Param("username") String username);

    @Query("SELECT p.cape FROM PlayerRow p WHERE UPPER(p.username) = UPPER(:username) AND p.cape IS NOT NULL")
    Optional<CapeRow> findCapeByUsernameIgnoreCase(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("UPDATE PlayerRow p SET p.submittedUuids = p.submittedUuids + :count WHERE p.id = :id")
    void incrementSubmittedUuids(@Param("id") UUID id, @Param("count") long count);
}
