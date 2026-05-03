package tech.wenisch.kairos.mcp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
import tech.wenisch.kairos.entity.Announcement;
import tech.wenisch.kairos.entity.AnnouncementKind;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.service.AnnouncementService;
import tech.wenisch.kairos.service.CheckAuditEntry;
import tech.wenisch.kairos.service.CheckAuditService;
import tech.wenisch.kairos.service.CheckExecutorService;
import tech.wenisch.kairos.service.InstantCheckService;
import tech.wenisch.kairos.service.OutageService;
import tech.wenisch.kairos.service.ResourceService;

@ExtendWith(MockitoExtension.class)
class KairosMcpToolsTest {

    @Mock private ResourceService resourceService;
    @Mock private CheckResultRepository checkResultRepository;
    @Mock private AnnouncementService announcementService;
    @Mock private OutageService outageService;
    @Mock private CheckAuditService checkAuditService;
    @Mock private CheckExecutorService checkExecutorService;
    @Mock private InstantCheckService instantCheckService;

    private KairosMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new KairosMcpTools(resourceService, checkResultRepository, announcementService,
                outageService, checkAuditService, checkExecutorService, instantCheckService);
    }

    // ── listResources ──────────────────────────────────────────────────────────

    @Test
    void listResourcesReturnsEmptyWhenNoResources() {
        when(resourceService.findAllActive()).thenReturn(List.of());
        assertThat(tools.listResources()).isEmpty();
    }

    @Test
    void listResourcesReturnsResourceWithStatus() {
        MonitoredResource r = MonitoredResource.builder()
                .id(1L).name("Test").resourceType(ResourceType.HTTP).target("https://example.com").active(true).build();
        CheckResult cr = CheckResult.builder()
                .status(CheckStatus.AVAILABLE).checkedAt(LocalDateTime.now()).message("HTTP 200").build();
        when(resourceService.findAllActive()).thenReturn(List.of(r));
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(r)).thenReturn(Optional.of(cr));

        List<Map<String, Object>> result = tools.listResources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "Test")
                .containsEntry("type", "HTTP")
                .containsEntry("currentStatus", "AVAILABLE");
    }

    @Test
    void listResourcesHandlesResourceWithNoCheckResult() {
        MonitoredResource r = MonitoredResource.builder()
                .id(2L).name("Unchecked").resourceType(ResourceType.DOCKER).target("nginx:latest").active(true).build();
        when(resourceService.findAllActive()).thenReturn(List.of(r));
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(r)).thenReturn(Optional.empty());

        List<Map<String, Object>> result = tools.listResources();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).doesNotContainKey("currentStatus");
    }

    // ── getResource ────────────────────────────────────────────────────────────

    @Test
    void getResourceReturnsErrorWhenNotFound() {
        when(resourceService.findById(99L)).thenReturn(Optional.empty());
        Map<String, Object> result = tools.getResource(99L);
        assertThat(result).containsKey("error");
    }

    @Test
    void getResourceReturnsDetails() {
        MonitoredResource r = MonitoredResource.builder()
                .id(1L).name("API").resourceType(ResourceType.HTTP).target("https://api.example.com")
                .active(true).createdAt(LocalDateTime.now()).build();
        CheckResult cr = CheckResult.builder()
                .status(CheckStatus.NOT_AVAILABLE).checkedAt(LocalDateTime.now())
                .message("Connection refused").errorCode("CONNECTION_ERROR").latencyMs(0L).build();
        when(resourceService.findById(1L)).thenReturn(Optional.of(r));
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(r)).thenReturn(Optional.of(cr));

        Map<String, Object> result = tools.getResource(1L);
        assertThat(result).containsEntry("name", "API")
                .containsEntry("currentStatus", "NOT_AVAILABLE")
                .containsEntry("lastErrorCode", "CONNECTION_ERROR");
    }

    @Test
    void getResourceWithNoCheckResult() {
        MonitoredResource r = MonitoredResource.builder()
                .id(5L).name("New").resourceType(ResourceType.HTTP).target("https://new.example.com")
                .active(true).build();
        when(resourceService.findById(5L)).thenReturn(Optional.of(r));
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(r)).thenReturn(Optional.empty());

        Map<String, Object> result = tools.getResource(5L);
        assertThat(result).containsEntry("name", "New").doesNotContainKey("currentStatus");
    }

    // ── triggerCheck ───────────────────────────────────────────────────────────

    @Test
    void triggerCheckReturnsSubmittedWhenSuccessful() {
        when(checkExecutorService.runImmediateCheck(1L, "MCP")).thenReturn(true);
        Map<String, Object> result = tools.triggerCheck(1L);
        assertThat(result).containsEntry("status", "submitted");
    }

    @Test
    void triggerCheckReturnsFailedWhenNotSubmitted() {
        when(checkExecutorService.runImmediateCheck(2L, "MCP")).thenReturn(false);
        Map<String, Object> result = tools.triggerCheck(2L);
        assertThat(result).containsEntry("status", "failed");
    }

    // ── getCheckHistory ────────────────────────────────────────────────────────

    @Test
    void getCheckHistoryReturnsErrorWhenResourceNotFound() {
        when(resourceService.findById(99L)).thenReturn(Optional.empty());
        List<Map<String, Object>> result = tools.getCheckHistory(99L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKey("error");
    }

    @Test
    void getCheckHistoryReturnsEntries() {
        MonitoredResource r = MonitoredResource.builder().id(1L).build();
        CheckResult cr = CheckResult.builder()
                .status(CheckStatus.AVAILABLE).checkedAt(LocalDateTime.now()).message("OK").errorCode("200").latencyMs(50L).build();
        when(resourceService.findById(1L)).thenReturn(Optional.of(r));
        when(checkResultRepository.findByResourceOrderByCheckedAtDesc(r)).thenReturn(List.of(cr));

        List<Map<String, Object>> result = tools.getCheckHistory(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("status", "AVAILABLE").containsEntry("latencyMs", 50L);
    }

    // ── listAnnouncements ──────────────────────────────────────────────────────

    @Test
    void listAnnouncementsReturnsEmptyList() {
        when(announcementService.findAllActiveForPublicView()).thenReturn(List.of());
        assertThat(tools.listAnnouncements()).isEmpty();
    }

    @Test
    void listAnnouncementsReturnsMappedEntries() {
        Announcement a = Announcement.builder()
                .id(1L).kind(AnnouncementKind.WARNING).content("<p>Maintenance</p>")
                .active(true).activeUntil(LocalDateTime.now().plusDays(1)).createdAt(LocalDateTime.now()).build();
        when(announcementService.findAllActiveForPublicView()).thenReturn(List.of(a));

        List<Map<String, Object>> result = tools.listAnnouncements();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("kind", "WARNING").containsEntry("active", true);
    }

    // ── listOutages ────────────────────────────────────────────────────────────

    @Test
    void listOutagesFiltersByActiveOnly() {
        MonitoredResource r = MonitoredResource.builder().id(1L).name("DB").build();
        Outage active = Outage.builder().id(1L).resource(r).startDate(LocalDateTime.now()).active(true).build();
        Outage closed = Outage.builder().id(2L).resource(r).startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now()).active(false).build();
        when(outageService.findAllForApi()).thenReturn(List.of(active, closed));

        List<Map<String, Object>> onlyActive = tools.listOutages(true);
        assertThat(onlyActive).hasSize(1);
        assertThat(onlyActive.get(0)).containsEntry("active", true);

        List<Map<String, Object>> all = tools.listOutages(false);
        assertThat(all).hasSize(2);
    }

    @Test
    void listOutagesWithNullResource() {
        Outage o = Outage.builder().id(3L).resource(null).startDate(LocalDateTime.now()).active(true).build();
        when(outageService.findAllForApi()).thenReturn(List.of(o));

        List<Map<String, Object>> result = tools.listOutages(false);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).doesNotContainKey("resourceId");
    }

    // ── getCheckAuditLog ───────────────────────────────────────────────────────

    @Test
    void getCheckAuditLogReturnsEmptyList() {
        when(checkAuditService.getEntries()).thenReturn(List.of());
        assertThat(tools.getCheckAuditLog()).isEmpty();
    }

    @Test
    void getCheckAuditLogReturnsMappedEntries() {
        CheckAuditEntry entry = new CheckAuditEntry(
                LocalDateTime.now(), "Scheduled", "MyService", "https://example.com", "System", "AVAILABLE");
        when(checkAuditService.getEntries()).thenReturn(List.of(entry));

        List<Map<String, Object>> result = tools.getCheckAuditLog();
        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("kind", "Scheduled")
                .containsEntry("resourceName", "MyService")
                .containsEntry("result", "AVAILABLE");
    }

    // ── runInstantCheck ────────────────────────────────────────────────────────

    @Test
    void runInstantCheckReturnsErrorForInvalidType() {
        Map<String, Object> result = tools.runInstantCheck("https://example.com", "INVALID");
        assertThat(result).containsKey("error");
    }

    @Test
    void runInstantCheckReturnsResultForHttp() {
        InstantCheckExecutionResult execResult = InstantCheckExecutionResult.builder()
                .status(CheckStatus.AVAILABLE).message("HTTP 200").errorCode("200").latencyMs(100L).build();
        when(instantCheckService.runInstantCheck(eq(ResourceType.HTTP), eq("https://example.com"), eq(false), eq(false)))
                .thenReturn(execResult);

        Map<String, Object> result = tools.runInstantCheck("https://example.com", "HTTP");
        assertThat(result).containsEntry("status", "AVAILABLE").containsEntry("latencyMs", 100L);
        verify(checkAuditService).record("Instant Check", null, "https://example.com", "MCP", "AVAILABLE");
    }

    @Test
    void runInstantCheckHandlesException() {
        when(instantCheckService.runInstantCheck(any(), anyString(), eq(false), eq(false)))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> result = tools.runInstantCheck("https://broken.example.com", "HTTP");
        assertThat(result).containsEntry("status", "ERROR").containsKey("error");
    }

    // ── createResource ─────────────────────────────────────────────────────────

    @Test
    void createResourceReturnsErrorForInvalidType() {
        Map<String, Object> result = tools.createResource("Test", "INVALID", "target", false);
        assertThat(result).containsKey("error");
    }

    @Test
    void createResourceSavesAndTriggersCheck() {
        MonitoredResource saved = MonitoredResource.builder()
                .id(10L).name("New Service").resourceType(ResourceType.HTTP)
                .target("https://new.example.com").active(true).createdAt(LocalDateTime.now()).build();
        when(resourceService.save(any(MonitoredResource.class))).thenReturn(saved);

        Map<String, Object> result = tools.createResource("New Service", "HTTP", "https://new.example.com", false);

        assertThat(result).containsEntry("id", 10L).containsEntry("name", "New Service").containsEntry("type", "HTTP");
        verify(checkExecutorService).runImmediateCheck(10L, "MCP");
    }

    @Test
    void createResourceWorksForDocker() {
        MonitoredResource saved = MonitoredResource.builder()
                .id(11L).name("My Image").resourceType(ResourceType.DOCKER)
                .target("nginx:latest").active(true).createdAt(LocalDateTime.now()).build();
        when(resourceService.save(any())).thenReturn(saved);

        Map<String, Object> result = tools.createResource("My Image", "docker", "nginx:latest", false);
        assertThat(result).containsEntry("type", "DOCKER");
    }

    // ── deleteResource ─────────────────────────────────────────────────────────

    @Test
    void deleteResourceReturnsErrorWhenNotFound() {
        when(resourceService.findById(99L)).thenReturn(Optional.empty());
        Map<String, Object> result = tools.deleteResource(99L);
        assertThat(result).containsKey("error");
    }

    @Test
    void deleteResourceDeletesAndReturnsStatus() {
        MonitoredResource r = MonitoredResource.builder().id(5L).build();
        when(resourceService.findById(5L)).thenReturn(Optional.of(r));

        Map<String, Object> result = tools.deleteResource(5L);
        assertThat(result).containsEntry("status", "deleted").containsEntry("resourceId", 5L);
        verify(resourceService).delete(5L);
    }

    // ── createAnnouncement ─────────────────────────────────────────────────────

    @Test
    void createAnnouncementReturnsErrorForInvalidKind() {
        Map<String, Object> result = tools.createAnnouncement("INVALID", "content", null);
        assertThat(result).containsKey("error");
    }

    @Test
    void createAnnouncementReturnsErrorForInvalidActiveUntil() {
        Map<String, Object> result = tools.createAnnouncement("WARNING", "content", "not-a-date");
        assertThat(result).containsKey("error");
    }

    @Test
    void createAnnouncementWithNoExpiry() {
        Announcement saved = Announcement.builder()
                .id(1L).kind(AnnouncementKind.INFORMATION).content("<p>Hello</p>")
                .active(true).activeUntil(null).createdAt(LocalDateTime.now()).build();
        when(announcementService.save(any())).thenReturn(saved);

        Map<String, Object> result = tools.createAnnouncement("INFORMATION", "<p>Hello</p>", null);
        assertThat(result).containsEntry("kind", "INFORMATION").containsEntry("active", true);
        assertThat(result.get("activeUntil")).isNull();
    }

    @Test
    void createAnnouncementWithValidExpiry() {
        Announcement saved = Announcement.builder()
                .id(2L).kind(AnnouncementKind.PROBLEM).content("<p>Down</p>")
                .active(true).activeUntil(LocalDateTime.of(2026, 6, 1, 8, 0)).createdAt(LocalDateTime.now()).build();
        when(announcementService.save(any())).thenReturn(saved);

        Map<String, Object> result = tools.createAnnouncement("PROBLEM", "<p>Down</p>", "2026-06-01T08:00:00");
        assertThat(result).containsEntry("kind", "PROBLEM");
    }

    @Test
    void createAnnouncementTreatsNullStringAsNoExpiry() {
        Announcement saved = Announcement.builder()
                .id(3L).kind(AnnouncementKind.WARNING).content("<p>Warning</p>")
                .active(true).createdAt(LocalDateTime.now()).build();
        when(announcementService.save(any())).thenReturn(saved);

        Map<String, Object> result = tools.createAnnouncement("WARNING", "<p>Warning</p>", "null");
        assertThat(result).containsEntry("kind", "WARNING");
    }

    // ── deleteAnnouncement ─────────────────────────────────────────────────────

    @Test
    void deleteAnnouncementReturnsErrorWhenNotFound() {
        when(announcementService.findById(99L)).thenReturn(Optional.empty());
        Map<String, Object> result = tools.deleteAnnouncement(99L);
        assertThat(result).containsKey("error");
    }

    @Test
    void deleteAnnouncementDeletesAndReturnsStatus() {
        Announcement a = Announcement.builder().id(4L).build();
        when(announcementService.findById(4L)).thenReturn(Optional.of(a));

        Map<String, Object> result = tools.deleteAnnouncement(4L);
        assertThat(result).containsEntry("status", "deleted").containsEntry("announcementId", 4L);
        verify(announcementService).delete(4L);
    }
}
