package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.wenisch.kairos.entity.NotificationPolicy;
import tech.wenisch.kairos.entity.NotificationProvider;

import java.util.List;

@Repository
public interface NotificationPolicyRepository extends JpaRepository<NotificationPolicy, Long> {

    List<NotificationPolicy> findAllByNotifyOnOutageStartedTrue();

    List<NotificationPolicy> findAllByNotifyOnOutageEndedTrue();

    List<NotificationPolicy> findAllByProvider(NotificationProvider provider);

    void deleteByProvider(NotificationProvider provider);
}
