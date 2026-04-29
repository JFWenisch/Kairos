package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutageRepository extends JpaRepository<Outage, Long> {

    List<Outage> findByResourceAndActiveTrueOrderByStartDateDesc(MonitoredResource resource);

    List<Outage> findByResourceOrderByStartDateDesc(MonitoredResource resource);

    List<Outage> findAllByOrderByStartDateDesc();

    /** Fetch all active outages with their resources in a single JOIN FETCH query. */
    @Query("SELECT o FROM Outage o JOIN FETCH o.resource WHERE o.active = true ORDER BY o.startDate DESC")
    List<Outage> findAllActiveWithResource();

    long deleteByActiveFalseAndEndDateBefore(LocalDateTime cutoff);

    long countByActiveTrue();
}
