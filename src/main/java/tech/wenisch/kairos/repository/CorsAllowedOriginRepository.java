package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.CorsAllowedOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CorsAllowedOriginRepository extends JpaRepository<CorsAllowedOrigin, Long> {
    boolean existsByOrigin(String origin);
}
