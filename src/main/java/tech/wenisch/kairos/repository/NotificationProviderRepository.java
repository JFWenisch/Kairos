package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.wenisch.kairos.entity.NotificationProvider;

@Repository
public interface NotificationProviderRepository extends JpaRepository<NotificationProvider, Long> {
}
