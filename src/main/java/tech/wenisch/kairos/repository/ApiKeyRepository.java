package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyId(String keyId);
    List<ApiKey> findAllByOrderByCreatedAtDesc();
}
