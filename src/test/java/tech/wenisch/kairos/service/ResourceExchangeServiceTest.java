package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceExchangeServiceTest {

    @Mock
    private ResourceService resourceService;

    private ResourceExchangeService resourceExchangeService;

    @BeforeEach
    void setUp() {
        resourceExchangeService = new ResourceExchangeService(resourceService);
    }

    // ── exportResourcesAsYaml ──────────────────────────────────────────────

    @Test
    void exportProducesYamlWithSchemaHeader() throws IOException {
        when(resourceService.findAll()).thenReturn(List.of());

        String yaml = resourceExchangeService.exportResourcesAsYaml();

        assertThat(yaml).containsAnyOf("format: kairos-resources", "format: \"kairos-resources\"");
        assertThat(yaml).contains("schemaVersion: 1");
    }

    @Test
    void exportContainsResourceFields() throws IOException {
        MonitoredResource r = MonitoredResource.builder()
                .name("My Service")
                .resourceType(ResourceType.HTTP)
                .target("https://example.com/health")
                .active(true)
                .displayOrder(0)
                .build();
        when(resourceService.findAll()).thenReturn(List.of(r));

        String yaml = resourceExchangeService.exportResourcesAsYaml();

        assertThat(yaml).contains("My Service");
        assertThat(yaml).contains("https://example.com/health");
        assertThat(yaml).contains("HTTP");
    }

    @Test
    void exportIncludesGroupName() throws IOException {
        ResourceGroup group = new ResourceGroup();
        group.setName("Infrastructure");

        MonitoredResource r = MonitoredResource.builder()
                .name("DB")
                .resourceType(ResourceType.HTTP)
                .target("https://db.example.com")
                .build();
        r.getGroups().add(group);
        when(resourceService.findAll()).thenReturn(List.of(r));

        String yaml = resourceExchangeService.exportResourcesAsYaml();

        assertThat(yaml).contains("Infrastructure");
    }

    @Test
    void exportReturnsEmptyResourcesListWhenNoneExist() throws IOException {
        when(resourceService.findAll()).thenReturn(List.of());

        String yaml = resourceExchangeService.exportResourcesAsYaml();

        assertThat(yaml).contains("resourceCount: 0");
    }

    // ── importResourcesFromYaml ────────────────────────────────────────────

    @Test
    void importThrowsForNullFile() {
        assertThatThrownBy(() -> resourceExchangeService.importResourcesFromYaml(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importThrowsForEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> resourceExchangeService.importResourcesFromYaml(empty))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importThrowsForYamlWithNoResourcesKey() throws IOException {
        // Valid YAML but no 'resources' array anywhere
        byte[] content = "format: some-other-tool\ncount: 5\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml", content);

        assertThatThrownBy(() -> resourceExchangeService.importResourcesFromYaml(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid YAML format");
    }

    @Test
    void importCreatesNewResources() throws IOException {
        String yaml = """
                format: kairos-resources
                schemaVersion: 1
                resources:
                  - name: My API
                    resourceType: HTTP
                    target: https://api.example.com
                    active: true
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));
        when(resourceService.findAll()).thenReturn(List.of());
        when(resourceService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(0);
        assertThat(result.getSkipped()).isEqualTo(0);
    }

    @Test
    void importUpdatesExistingResourceByTypeAndTarget() throws IOException {
        String yaml = """
                resources:
                  - name: Updated Name
                    resourceType: HTTP
                    target: https://api.example.com
                    active: true
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));

        MonitoredResource existing = MonitoredResource.builder()
                .name("Old Name")
                .resourceType(ResourceType.HTTP)
                .target("https://api.example.com")
                .active(true)
                .build();
        when(resourceService.findAll()).thenReturn(List.of(existing));
        when(resourceService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);

        assertThat(result.getImported()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(1);
    }

    @Test
    void importSkipsEntriesWithMissingTarget() throws IOException {
        String yaml = """
                resources:
                  - name: No Target
                    resourceType: HTTP
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));
        when(resourceService.findAll()).thenReturn(List.of());

        ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);

        assertThat(result.getSkipped()).isEqualTo(1);
        verify(resourceService, never()).save(any());
    }

    @Test
    void importSkipsEntriesWithUnknownResourceType() throws IOException {
        String yaml = """
                resources:
                  - name: Unknown Type
                    resourceType: FOOBAR
                    target: https://example.com
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));
        when(resourceService.findAll()).thenReturn(List.of());

        ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getNotes()).anyMatch(n -> n.contains("FOOBAR"));
    }

    @Test
    void importAcceptsPlainArrayFormat() throws IOException {
        String yaml = """
                - name: Array API
                  resourceType: HTTP
                  target: https://array.example.com
                  active: true
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));
        when(resourceService.findAll()).thenReturn(List.of());
        when(resourceService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);

        assertThat(result.getImported()).isEqualTo(1);
    }

    @Test
    void importUsesTargetAsNameWhenNameIsMissing() throws IOException {
        String yaml = """
                resources:
                  - resourceType: HTTP
                    target: https://noname.example.com
                    active: true
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));
        when(resourceService.findAll()).thenReturn(List.of());
        when(resourceService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        resourceExchangeService.importResourcesFromYaml(file);

        verify(resourceService).save(argThat(r -> "https://noname.example.com".equals(r.getName())));
    }

    @Test
    void importAssignsGroupWhenGroupNameProvided() throws IOException {
        String yaml = """
                resources:
                  - name: Service
                    resourceType: HTTP
                    target: https://example.com
                    groupName: MyGroup
                """;
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                yaml.getBytes(StandardCharsets.UTF_8));
        ResourceGroup group = new ResourceGroup();
        group.setName("MyGroup");
        when(resourceService.findAll()).thenReturn(List.of());
        when(resourceService.findOrCreateGroupByName("MyGroup")).thenReturn(group);
        when(resourceService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        resourceExchangeService.importResourcesFromYaml(file);

        verify(resourceService).save(argThat(r -> r.getGroups().contains(group)));
    }

    @Test
    void importRoundtrip() throws IOException {
        MonitoredResource original = MonitoredResource.builder()
                .name("Round Trip")
                .resourceType(ResourceType.HTTP)
                .target("https://roundtrip.example.com")
                .active(true)
                .displayOrder(5)
                .createdAt(LocalDateTime.of(2024, 6, 1, 12, 0))
                .build();
        when(resourceService.findAll()).thenReturn(List.of(original));

        String exported = resourceExchangeService.exportResourcesAsYaml();

        // now import it back
        MockMultipartFile file = new MockMultipartFile("file", "import.yml", "text/yaml",
                exported.getBytes(StandardCharsets.UTF_8));
        when(resourceService.findAll()).thenReturn(List.of()); // simulate empty DB
        when(resourceService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(0);
    }
}
