package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.SkinCurrentOwnersRow;

public interface SkinCurrentOwnersRepository extends JpaRepository<SkinCurrentOwnersRow, Long> {
}
