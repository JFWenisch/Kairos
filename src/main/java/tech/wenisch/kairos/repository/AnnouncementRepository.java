package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByActiveTrueAndActiveUntilIsNullOrderByCreatedAtDesc();

    List<Announcement> findByActiveTrueAndActiveUntilAfterOrderByCreatedAtDesc(LocalDateTime dateTime);

    List<Announcement> findByActiveTrueAndActiveUntilLessThanEqual(LocalDateTime dateTime);

    List<Announcement> findAllByOrderByCreatedAtDesc();
}
