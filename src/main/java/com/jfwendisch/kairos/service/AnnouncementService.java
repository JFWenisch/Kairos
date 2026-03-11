package com.jfwendisch.kairos.service;

import com.jfwendisch.kairos.entity.Announcement;
import com.jfwendisch.kairos.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public List<Announcement> findAllOrderedByCreatedAtDesc() {
        return announcementRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Announcement> findAllActiveForPublicView() {
        LocalDateTime now = LocalDateTime.now();
        List<Announcement> activeWithoutEnd = announcementRepository
                .findByActiveTrueAndActiveUntilIsNullOrderByCreatedAtDesc();
        List<Announcement> activeWithFutureEnd = announcementRepository
                .findByActiveTrueAndActiveUntilAfterOrderByCreatedAtDesc(now);

        List<Announcement> result = new ArrayList<>(activeWithoutEnd.size() + activeWithFutureEnd.size());
        result.addAll(activeWithoutEnd);
        result.addAll(activeWithFutureEnd);
        result.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return result;
    }

    public Optional<Announcement> findById(Long id) {
        return announcementRepository.findById(id);
    }

    public Announcement save(Announcement announcement) {
        if (announcement.getCreatedAt() == null) {
            announcement.setCreatedAt(LocalDateTime.now());
        }
        return announcementRepository.save(announcement);
    }

    public void delete(Long id) {
        announcementRepository.deleteById(id);
    }

    @Scheduled(fixedDelay = 60000)
    public void deactivateExpiredAnnouncements() {
        LocalDateTime now = LocalDateTime.now();
        List<Announcement> expired = announcementRepository.findByActiveTrueAndActiveUntilLessThanEqual(now);
        if (expired.isEmpty()) {
            return;
        }

        for (Announcement announcement : expired) {
            announcement.setActive(false);
        }
        announcementRepository.saveAll(expired);
        log.debug("Deactivated {} expired announcements", expired.size());
    }
}
