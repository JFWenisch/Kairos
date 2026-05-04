package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.wenisch.kairos.entity.OutageNotificationRef;

import java.util.Optional;

public interface OutageNotificationRefRepository extends JpaRepository<OutageNotificationRef, Long> {

    Optional<OutageNotificationRef> findByOutageIdAndProviderId(Long outageId, Long providerId);

    void deleteByOutageIdAndProviderId(Long outageId, Long providerId);
}
