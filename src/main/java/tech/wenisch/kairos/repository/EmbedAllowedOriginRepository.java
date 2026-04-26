package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.wenisch.kairos.entity.EmbedAllowedOrigin;

@Repository
public interface EmbedAllowedOriginRepository extends JpaRepository<EmbedAllowedOrigin, Long> {
    boolean existsByOrigin(String origin);
}
