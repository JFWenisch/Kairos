package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.Announcement;
import tech.wenisch.kairos.entity.AnnouncementKind;
import tech.wenisch.kairos.repository.AnnouncementRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;

    private AnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        announcementService = new AnnouncementService(announcementRepository);
    }

    private Announcement announcement(Long id, boolean active) {
        return Announcement.builder()
                .id(id)
                .kind(AnnouncementKind.INFORMATION)
                .content("Test content")
                .active(active)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    // ── findAllOrderedByCreatedAtDesc ───────────────────────────────────────

    @Test
    void findAllOrderedDelegatesToRepository() {
        List<Announcement> expected = List.of(announcement(1L, true));
        when(announcementRepository.findAllByOrderByCreatedAtDesc()).thenReturn(expected);

        List<Announcement> result = announcementService.findAllOrderedByCreatedAtDesc();

        assertThat(result).isEqualTo(expected);
    }

    // ── findAllActiveForPublicView ──────────────────────────────────────────

    @Test
    void findAllActiveForPublicViewMergesAndSortsBothLists() {
        Announcement older = Announcement.builder()
                .id(1L).active(true).createdAt(LocalDateTime.now().minusDays(2)).build();
        Announcement newer = Announcement.builder()
                .id(2L).active(true).createdAt(LocalDateTime.now().minusDays(1)).build();

        when(announcementRepository.findByActiveTrueAndActiveUntilIsNullOrderByCreatedAtDesc())
                .thenReturn(List.of(older));
        when(announcementRepository.findByActiveTrueAndActiveUntilAfterOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(newer));

        List<Announcement> result = announcementService.findAllActiveForPublicView();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L); // newer first
    }

    @Test
    void findAllActiveForPublicViewReturnsEmptyWhenBothListsEmpty() {
        when(announcementRepository.findByActiveTrueAndActiveUntilIsNullOrderByCreatedAtDesc())
                .thenReturn(List.of());
        when(announcementRepository.findByActiveTrueAndActiveUntilAfterOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        List<Announcement> result = announcementService.findAllActiveForPublicView();

        assertThat(result).isEmpty();
    }

    // ── findById ───────────────────────────────────────────────────────────

    @Test
    void findByIdDelegatesToRepository() {
        Announcement a = announcement(42L, true);
        when(announcementRepository.findById(42L)).thenReturn(Optional.of(a));

        Optional<Announcement> result = announcementService.findById(42L);

        assertThat(result).contains(a);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(announcementRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Announcement> result = announcementService.findById(99L);

        assertThat(result).isEmpty();
    }

    // ── save ───────────────────────────────────────────────────────────────

    @Test
    void saveSetsCreatedAtWhenMissing() {
        Announcement a = Announcement.builder().content("Hi").build();
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Announcement saved = announcementService.save(a);

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void saveKeepsExistingCreatedAt() {
        LocalDateTime existing = LocalDateTime.of(2024, 1, 15, 10, 0);
        Announcement a = Announcement.builder().content("Hi").createdAt(existing).build();
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Announcement saved = announcementService.save(a);

        assertThat(saved.getCreatedAt()).isEqualTo(existing);
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    void deleteDelegatesToRepository() {
        announcementService.delete(7L);

        verify(announcementRepository).deleteById(7L);
    }

    // ── deactivateExpiredAnnouncements ─────────────────────────────────────

    @Test
    void deactivateExpiredSetsActiveToFalseAndSaves() {
        Announcement expired = announcement(1L, true);
        when(announcementRepository.findByActiveTrueAndActiveUntilLessThanEqual(any()))
                .thenReturn(List.of(expired));

        announcementService.deactivateExpiredAnnouncements();

        assertThat(expired.isActive()).isFalse();
        verify(announcementRepository).saveAll(List.of(expired));
    }

    @Test
    void deactivateExpiredDoesNothingWhenNoneExpired() {
        when(announcementRepository.findByActiveTrueAndActiveUntilLessThanEqual(any()))
                .thenReturn(List.of());

        announcementService.deactivateExpiredAnnouncements();

        verify(announcementRepository, never()).saveAll(any());
    }

    @Test
    void deactivateExpiredDeactivatesMultipleAnnouncements() {
        Announcement a1 = announcement(1L, true);
        Announcement a2 = announcement(2L, true);
        when(announcementRepository.findByActiveTrueAndActiveUntilLessThanEqual(any()))
                .thenReturn(List.of(a1, a2));

        announcementService.deactivateExpiredAnnouncements();

        assertThat(a1.isActive()).isFalse();
        assertThat(a2.isActive()).isFalse();
        ArgumentCaptor<List<Announcement>> captor = ArgumentCaptor.forClass(List.class);
        verify(announcementRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(a1, a2);
    }
}
