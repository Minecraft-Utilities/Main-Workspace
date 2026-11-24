package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRefreshStateRow;

import java.util.UUID;

public interface PlayerRefreshStateRepository extends JpaRepository<PlayerRefreshStateRow, UUID> {
}
