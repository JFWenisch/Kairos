package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.ResourceGroupRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private MonitoredResourceRepository resourceRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private ResourceGroupRepository resourceGroupRepository;

    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService(resourceRepository, checkResultRepository, resourceGroupRepository);
    }

    @Test
    void findAllActiveReturnsRepositoryResult() {
        MonitoredResource resource = MonitoredResource.builder().id(1L).name("A").active(true).build();
        when(resourceRepository.findAllActiveForLanding()).thenReturn(List.of(resource));

        List<MonitoredResource> result = resourceService.findAllActive();

        assertThat(result).containsExactly(resource);
    }

    @Test
    void saveSetsCreatedAtWhenMissing() {
        MonitoredResource input = MonitoredResource.builder().name("api").active(true).build();
        when(resourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredResource saved = resourceService.save(input);

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void saveKeepsExistingCreatedAt() {
        LocalDateTime existing = LocalDateTime.now().minusDays(1);
        MonitoredResource input = MonitoredResource.builder().name("api").createdAt(existing).active(true).build();
        when(resourceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredResource saved = resourceService.save(input);

        assertThat(saved.getCreatedAt()).isEqualTo(existing);
    }

    @Test
    void deleteRemovesCheckHistoryAndResource() {
        MonitoredResource resource = MonitoredResource.builder().id(7L).name("to-delete").active(true).build();
        CheckResult resultOne = CheckResult.builder().id(1L).resource(resource).build();
        CheckResult resultTwo = CheckResult.builder().id(2L).resource(resource).build();

        when(resourceRepository.findById(7L)).thenReturn(Optional.of(resource));
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(resource)).thenReturn(List.of(resultOne, resultTwo));

        resourceService.delete(7L);

        verify(checkResultRepository).delete(resultOne);
        verify(checkResultRepository).delete(resultTwo);
        verify(resourceRepository).delete(resource);
    }

    @Test
    void getHistoryPageReturnsEmptyWhenResourceMissing() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        Page<CheckResult> result = resourceService.getHistoryPage(99L, 1, 20, CheckStatus.AVAILABLE, "500", "error");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        verify(checkResultRepository, never()).findHistoryFiltered(any(), any(), any(), any(), any());
    }

    @Test
    void getHistoryPageAppliesTrimmedFilters() {
        MonitoredResource resource = MonitoredResource.builder().id(2L).name("api").build();
        PageImpl<CheckResult> page = new PageImpl<>(List.of());

        when(resourceRepository.findById(2L)).thenReturn(Optional.of(resource));
        when(checkResultRepository.findHistoryFiltered(eq(resource), eq(CheckStatus.NOT_AVAILABLE), eq("500"), eq("timeout"), any(PageRequest.class)))
                .thenReturn(page);

        Page<CheckResult> result = resourceService.getHistoryPage(2L, 0, 10, CheckStatus.NOT_AVAILABLE, " 500 ", " timeout ");

        assertThat(result).isSameAs(page);
    }

    @Test
    void getUptimePercentageReturnsZeroWhenNoResults() {
        MonitoredResource resource = MonitoredResource.builder().id(3L).build();
        when(checkResultRepository.findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(eq(resource), any(LocalDateTime.class)))
                .thenReturn(List.of());

        double uptime = resourceService.getUptimePercentage(resource, 24);

        assertThat(uptime).isEqualTo(0.0);
    }

    @Test
    void getUptimePercentageIgnoresUnknownInDenominator() {
        MonitoredResource resource = MonitoredResource.builder().id(4L).build();
        List<CheckResult> results = List.of(
                CheckResult.builder().status(CheckStatus.AVAILABLE).checkedAt(LocalDateTime.now().minusHours(1)).build(),
                CheckResult.builder().status(CheckStatus.NOT_AVAILABLE).checkedAt(LocalDateTime.now().minusMinutes(20)).build(),
                CheckResult.builder().status(CheckStatus.UNKNOWN).checkedAt(LocalDateTime.now().minusMinutes(5)).build()
        );
        when(checkResultRepository.findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(eq(resource), any(LocalDateTime.class)))
                .thenReturn(results);

        double uptime = resourceService.getUptimePercentage(resource, 24);

        assertThat(uptime).isEqualTo(50.0);
    }

    @Test
    void getCurrentStatusReturnsUnknownWhenNoChecksExist() {
        MonitoredResource resource = MonitoredResource.builder().id(5L).build();
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource)).thenReturn(Optional.empty());

        String status = resourceService.getCurrentStatus(resource);

        assertThat(status).isEqualTo("unknown");
    }

    @Test
    void getCurrentStatusMapsStatuses() {
        MonitoredResource resource = MonitoredResource.builder().id(6L).build();
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource))
                .thenReturn(Optional.of(CheckResult.builder().status(CheckStatus.NOT_AVAILABLE).build()));

        String status = resourceService.getCurrentStatus(resource);

        assertThat(status).isEqualTo("not-available");
    }

    @Test
    void getTimelineBlocksAlwaysReturnsNinetyBuckets() {
        MonitoredResource resource = MonitoredResource.builder().id(10L).build();
        when(checkResultRepository.findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(eq(resource), any(LocalDateTime.class)))
                .thenReturn(List.of());

        List<String> blocks = resourceService.getTimelineBlocks(resource);

        assertThat(blocks).hasSize(90);
        assertThat(blocks).allMatch("unknown"::equals);
    }

    @Test
    void clearGroupAssignmentUnsetsGroupAndSavesAll() {
        ResourceGroup group = ResourceGroup.builder().id(1L).name("backend").build();
        MonitoredResource one = MonitoredResource.builder().id(1L).group(group).build();
        MonitoredResource two = MonitoredResource.builder().id(2L).group(group).build();

        when(resourceRepository.findByGroup_Id(1L)).thenReturn(List.of(one, two));

        int count = resourceService.clearGroupAssignment(1L);

        assertThat(count).isEqualTo(2);
        assertThat(one.getGroup()).isNull();
        assertThat(two.getGroup()).isNull();
        verify(resourceRepository).saveAll(List.of(one, two));
    }

    @Test
    void findOrCreateGroupByNameReturnsExistingWhenPresent() {
        ResourceGroup existing = ResourceGroup.builder().id(10L).name("core").build();
        when(resourceGroupRepository.findByNameIgnoreCase("core")).thenReturn(Optional.of(existing));

        ResourceGroup group = resourceService.findOrCreateGroupByName("core");

        assertThat(group).isSameAs(existing);
        verify(resourceGroupRepository, never()).save(any());
    }

    @Test
    void findOrCreateGroupByNameCreatesWhenMissing() {
        when(resourceGroupRepository.findByNameIgnoreCase("new-group")).thenReturn(Optional.empty());
        when(resourceGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResourceGroup created = resourceService.findOrCreateGroupByName("new-group");

        assertThat(created.getName()).isEqualTo("new-group");

        ArgumentCaptor<ResourceGroup> captor = ArgumentCaptor.forClass(ResourceGroup.class);
        verify(resourceGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(0);
    }
}
