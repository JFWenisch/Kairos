package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;

import java.util.List;

@Repository
public interface OutageRepository extends JpaRepository<Outage, Long> {

    List<Outage> findByResourceAndActiveTrueOrderByStartDateDesc(MonitoredResource resource);

    List<Outage> findByResourceOrderByStartDateDesc(MonitoredResource resource);

    List<Outage> findAllByOrderByStartDateDesc();
}
