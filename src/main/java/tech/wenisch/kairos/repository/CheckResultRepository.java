package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    long deleteByCheckedAtBefore(LocalDateTime cutoff);

        @Query("""
            select cr from CheckResult cr
            where cr.resource = :resource
              and (:status is null or cr.status = :status)
              and (:errorCode is null or :errorCode = '' or lower(coalesce(cr.errorCode, '')) like lower(concat('%', :errorCode, '%')))
              and (:message is null or :message = '' or lower(coalesce(cr.message, '')) like lower(concat('%', :message, '%')))
            order by cr.checkedAt desc
            """)
        Page<CheckResult> findHistoryFiltered(
            @Param("resource") MonitoredResource resource,
            @Param("status") CheckStatus status,
            @Param("errorCode") String errorCode,
            @Param("message") String message,
            Pageable pageable
        );
}
