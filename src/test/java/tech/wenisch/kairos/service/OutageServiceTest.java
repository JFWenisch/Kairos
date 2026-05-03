package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.OutageRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutageServiceTest {

    @Mock private OutageRepository outageRepository;
    @Mock private CheckResultRepository checkResultRepository;
    @Mock private ResourceTypeConfigRepository resourceTypeConfigRepository;

    private OutageService outageService;

    private MonitoredResource resource;

    @BeforeEach
    void setUp() {
        outageService = new OutageService(outageRepository, checkResultRepository, resourceTypeConfigRepository);
        resource = MonitoredResource.builder()
                .id(1L).name("TestService").resourceType(ResourceType.HTTP).active(true).build();
    }

    // ── evaluate: no config ────────────────────────────────────────────────────

    @Test
    void evaluateDoesNothingWhenNoConfig() {
        when(resourceTypeConfigRepository.findByTypeName("HTTP")).thenReturn(Optional.empty());
        outageService.evaluate(resource);
        verify(outageRepository, never()).save(any());
    }

    // ── evaluate: open outage ──────────────────────────────────────────────────

    @Test
    void evaluateOpensOutageWhenThresholdReached() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .outageThreshold(2).recoveryThreshold(2).build();
        when(resourceTypeConfigRepository.findByTypeName("HTTP")).thenReturn(Optional.of(config));
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource)).thenReturn(List.of());

        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);
        CheckResult r1 = CheckResult.builder().status(CheckStatus.NOT_AVAILABLE).checkedAt(t1).build();
        CheckResult r2 = CheckResult.builder().status(CheckStatus.NOT_AVAILABLE).checkedAt(t2).build();
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r2, r1)));

        outageService.evaluate(resource);

        ArgumentCaptor<Outage> captor = ArgumentCaptor.forClass(Outage.class);
        verify(outageRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getStartDate()).isEqualTo(t1);
    }

    @Test
    void evaluateDoesNotOpenOutageWhenNotEnoughFailures() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .outageThreshold(3).recoveryThreshold(2).build();
        when(resourceTypeConfigRepository.findByTypeName("HTTP")).thenReturn(Optional.of(config));
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource)).thenReturn(List.of());

        CheckResult r1 = CheckResult.builder().status(CheckStatus.NOT_AVAILABLE).checkedAt(LocalDateTime.now()).build();
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r1)));

        outageService.evaluate(resource);
        verify(outageRepository, never()).save(any());
    }

    @Test
    void evaluateDoesNotOpenOutageWhenMixedResults() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .outageThreshold(2).recoveryThreshold(2).build();
        when(resourceTypeConfigRepository.findByTypeName("HTTP")).thenReturn(Optional.of(config));
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource)).thenReturn(List.of());

        CheckResult ok = CheckResult.builder().status(CheckStatus.AVAILABLE).checkedAt(LocalDateTime.now()).build();
        CheckResult fail = CheckResult.builder().status(CheckStatus.NOT_AVAILABLE).checkedAt(LocalDateTime.now().minusMinutes(1)).build();
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ok, fail)));

        outageService.evaluate(resource);
        verify(outageRepository, never()).save(any());
    }

    // ── evaluate: close outage ─────────────────────────────────────────────────

    @Test
    void evaluateClosesOutageWhenRecoveryThresholdReached() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .outageThreshold(2).recoveryThreshold(2).build();
        when(resourceTypeConfigRepository.findByTypeName("HTTP")).thenReturn(Optional.of(config));

        Outage activeOutage = Outage.builder().id(1L).resource(resource)
                .startDate(LocalDateTime.now().minusHours(1)).active(true).build();
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource))
                .thenReturn(List.of(activeOutage));

        LocalDateTime t1 = LocalDateTime.now().minusMinutes(1);
        LocalDateTime t2 = LocalDateTime.now();
        CheckResult r1 = CheckResult.builder().status(CheckStatus.AVAILABLE).checkedAt(t2).build();
        CheckResult r2 = CheckResult.builder().status(CheckStatus.AVAILABLE).checkedAt(t1).build();
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r1, r2)));

        outageService.evaluate(resource);

        ArgumentCaptor<Outage> captor = ArgumentCaptor.forClass(Outage.class);
        verify(outageRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().getEndDate()).isEqualTo(t2);
    }

    @Test
    void evaluateDoesNotCloseOutageWhenNotEnoughRecovery() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .outageThreshold(2).recoveryThreshold(3).build();
        when(resourceTypeConfigRepository.findByTypeName("HTTP")).thenReturn(Optional.of(config));

        Outage activeOutage = Outage.builder().id(1L).resource(resource)
                .startDate(LocalDateTime.now().minusHours(1)).active(true).build();
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource))
                .thenReturn(List.of(activeOutage));

        CheckResult r1 = CheckResult.builder().status(CheckStatus.AVAILABLE).checkedAt(LocalDateTime.now()).build();
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r1)));

        outageService.evaluate(resource);
        verify(outageRepository, never()).save(any());
    }

    // ── query methods ──────────────────────────────────────────────────────────

    @Test
    void findAllActiveSinceByResourceIdReturnsMappedResult() {
        MonitoredResource r = MonitoredResource.builder().id(42L).name("svc").build();
        Outage o = Outage.builder().resource(r).startDate(LocalDateTime.of(2026, 5, 1, 10, 0)).active(true).build();
        when(outageRepository.findAllActiveWithResource()).thenReturn(List.of(o));

        Map<Long, String> result = outageService.findAllActiveSinceByResourceId();
        assertThat(result).containsKey(42L);
        assertThat(result.get(42L)).startsWith("2026-05-01");
    }

    @Test
    void countActiveOutagesDelegatesToRepository() {
        when(outageRepository.countByActiveTrue()).thenReturn(3L);
        assertThat(outageService.countActiveOutages()).isEqualTo(3L);
    }

    @Test
    void hasActiveOutagesReturnsTrueWhenNonZero() {
        when(outageRepository.countByActiveTrue()).thenReturn(1L);
        assertThat(outageService.hasActiveOutages()).isTrue();
    }

    @Test
    void hasActiveOutagesReturnsFalseWhenZero() {
        when(outageRepository.countByActiveTrue()).thenReturn(0L);
        assertThat(outageService.hasActiveOutages()).isFalse();
    }

    @Test
    void findByResourceDelegatesToRepository() {
        when(outageRepository.findByResourceOrderByStartDateDesc(resource)).thenReturn(List.of());
        assertThat(outageService.findByResource(resource)).isEmpty();
    }

    @Test
    void findAllReturnsAllOutages() {
        Outage o = Outage.builder().id(1L).startDate(LocalDateTime.now()).active(false).build();
        when(outageRepository.findAllByOrderByStartDateDesc()).thenReturn(List.of(o));
        assertThat(outageService.findAll()).hasSize(1);
    }

    @Test
    void findAllForApiDelegatesToRepository() {
        when(outageRepository.findAllWithResourceOrderByStartDateDesc()).thenReturn(List.of());
        assertThat(outageService.findAllForApi()).isEmpty();
    }

    @Test
    void findActiveOutageReturnsEmptyWhenNone() {
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource)).thenReturn(List.of());
        assertThat(outageService.findActiveOutage(resource)).isEmpty();
    }

    @Test
    void findActiveOutageReturnsPrimaryWhenPresent() {
        Outage o = Outage.builder().id(1L).resource(resource).startDate(LocalDateTime.now()).active(true).build();
        when(outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource)).thenReturn(List.of(o));
        assertThat(outageService.findActiveOutage(resource)).contains(o);
    }
}
