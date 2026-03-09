package com.jfwendisch.kairos.repository;

import com.jfwendisch.kairos.entity.CheckResult;
import com.jfwendisch.kairos.entity.MonitoredResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
    List<CheckResult> findByResourceOrderByCheckedAtDesc(MonitoredResource resource);
    List<CheckResult> findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(MonitoredResource resource, LocalDateTime after);
    Optional<CheckResult> findTopByResourceOrderByCheckedAtDesc(MonitoredResource resource);
    List<CheckResult> findByResourceInAndCheckedAtAfterOrderByCheckedAtAsc(List<MonitoredResource> resources, LocalDateTime after);
    Page<CheckResult> findByResourceOrderByCheckedAtDesc(MonitoredResource resource, Pageable pageable);
}
