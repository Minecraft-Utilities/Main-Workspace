package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerViewEventRow;

import java.time.Instant;
import java.util.UUID;

public interface PlayerViewEventRepository extends JpaRepository<PlayerViewEventRow, Long> {
    boolean existsByPlayerIdAndIpAddressAndViewedAtAfter(UUID playerId, String ipAddress, Instant after);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlayerViewEventRow e WHERE e.viewedAt < :cutoff")
    int deleteViewedBefore(@Param("cutoff") Instant cutoff);
}
