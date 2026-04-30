package tech.wenisch.kairos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.DiscoveryServiceType;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.DiscoveryServiceConfigRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.ResourceDiscoveryRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckExecutorServiceTest {

    @Mock
    private HttpCheckService httpCheckService;

    @Mock
    private DockerCheckService dockerCheckService;

    @Mock
    private DockerRepositorySyncService dockerRepositorySyncService;

    @Mock
    private OpenshiftRouteSyncService openshiftRouteSyncService;

    @Mock
    private ResourceStatusStreamService resourceStatusStreamService;

    @Mock
    private MonitoredResourceRepository resourceRepository;

    @Mock
    private ResourceTypeConfigRepository configRepository;

    @Mock
    private ResourceDiscoveryRepository resourceDiscoveryRepository;

    @Mock
    private DiscoveryServiceConfigRepository discoveryConfigRepository;

    private CheckExecutorService checkExecutorService;

    @BeforeEach
    void setUp() {
        checkExecutorService = new CheckExecutorService(
                httpCheckService,
                dockerCheckService,
                dockerRepositorySyncService,
                openshiftRouteSyncService,
                resourceStatusStreamService,
                resourceRepository,
                configRepository,
                resourceDiscoveryRepository,
                discoveryConfigRepository
        );
    }

    @AfterEach
    void tearDown() {
        checkExecutorService.shutdown();
    }

    @Test
    void runImmediateCheckByIdReturnsFalseWhenNotFound() {
        when(resourceRepository.findById(123L)).thenReturn(Optional.empty());

        boolean result = checkExecutorService.runImmediateCheck(123L);

        assertThat(result).isFalse();
        verifyNoInteractions(httpCheckService, dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService, resourceStatusStreamService);
    }

    @Test
    void runImmediateCheckReturnsFalseForInactiveResource() {
        MonitoredResource inactive = MonitoredResource.builder().id(1L).active(false).resourceType(ResourceType.HTTP).build();

        boolean result = checkExecutorService.runImmediateCheck(inactive);

        assertThat(result).isFalse();
        verifyNoInteractions(httpCheckService, dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService, resourceStatusStreamService);
    }

    @Test
    void runImmediateCheckReturnsFalseWhenTypeMissing() {
        MonitoredResource resource = MonitoredResource.builder().id(2L).active(true).resourceType(null).build();

        boolean result = checkExecutorService.runImmediateCheck(resource);

        assertThat(result).isFalse();
        verifyNoInteractions(httpCheckService, dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService, resourceStatusStreamService);
    }

    @Test
    void runImmediateCheckTriggersHttpCheckAsynchronously() {
        MonitoredResource resource = MonitoredResource.builder()
                .id(3L)
                .name("web")
                .active(true)
                .resourceType(ResourceType.HTTP)
                .build();

        when(configRepository.findByTypeName("HTTP"))
                .thenReturn(Optional.of(ResourceTypeConfig.builder().typeName("HTTP").parallelism(1).build()));

        boolean triggered = checkExecutorService.runImmediateCheck(resource);

        assertThat(triggered).isTrue();
        verify(resourceStatusStreamService, org.mockito.Mockito.timeout(1000)).publishResourceChecking(resource);
        verify(httpCheckService, org.mockito.Mockito.timeout(1000)).check(resource);
        verifyNoInteractions(dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService);
    }

    @Test
    void runImmediateCheckTriggersDockerCheckAsynchronously() {
        MonitoredResource resource = MonitoredResource.builder()
                .id(4L)
                .name("image")
                .active(true)
                .resourceType(ResourceType.DOCKER)
                .build();

        when(configRepository.findByTypeName("DOCKER"))
                .thenReturn(Optional.of(ResourceTypeConfig.builder().typeName("DOCKER").parallelism(1).build()));

        boolean triggered = checkExecutorService.runImmediateCheck(resource);

        assertThat(triggered).isTrue();
        verify(resourceStatusStreamService, org.mockito.Mockito.timeout(1000)).publishResourceChecking(resource);
        verify(dockerCheckService, org.mockito.Mockito.timeout(1000)).check(resource);
        verifyNoInteractions(httpCheckService, dockerRepositorySyncService, openshiftRouteSyncService);
    }

    @Test
    void dispatchSkipsTypesWithoutConfig() {
        when(configRepository.findByTypeName("HTTP")).thenReturn(Optional.empty());
        when(configRepository.findByTypeName("DOCKER")).thenReturn(Optional.empty());
        when(discoveryConfigRepository.findByTypeName(DiscoveryServiceType.DOCKER_REPOSITORY.name())).thenReturn(Optional.empty());

        checkExecutorService.dispatch();

        verify(resourceRepository, never()).findByResourceTypeAndActiveTrue(any());
        verifyNoInteractions(httpCheckService, dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService);
    }

    @Test
    void dispatchRunsChecksForDueHttpResources() {
        MonitoredResource resource = MonitoredResource.builder()
                .id(8L)
                .name("api")
                .active(true)
                .resourceType(ResourceType.HTTP)
                .build();

        when(configRepository.findByTypeName("HTTP"))
                .thenReturn(Optional.of(ResourceTypeConfig.builder()
                        .typeName("HTTP")
                        .checkIntervalMinutes(0)
                        .parallelism(1)
                        .build()));
        when(configRepository.findByTypeName("DOCKER")).thenReturn(Optional.empty());
        when(discoveryConfigRepository.findByTypeName(DiscoveryServiceType.DOCKER_REPOSITORY.name())).thenReturn(Optional.empty());
        when(resourceRepository.findByResourceTypeAndActiveTrue(ResourceType.HTTP)).thenReturn(List.of(resource));

        checkExecutorService.dispatch();

        verify(resourceStatusStreamService, org.mockito.Mockito.timeout(1000)).publishResourceChecking(resource);
        verify(httpCheckService, org.mockito.Mockito.timeout(1000)).check(resource);
        verifyNoInteractions(dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService);
    }

    @Test
    void runChecksOnStartupDelegatesToDispatch() {
        when(configRepository.findByTypeName("HTTP")).thenReturn(Optional.empty());
        when(configRepository.findByTypeName("DOCKER")).thenReturn(Optional.empty());
        when(discoveryConfigRepository.findByTypeName(DiscoveryServiceType.DOCKER_REPOSITORY.name())).thenReturn(Optional.empty());

        checkExecutorService.runChecksOnStartup();

        verify(configRepository).findByTypeName(eq("HTTP"));
        verify(configRepository).findByTypeName(eq("DOCKER"));
        verify(discoveryConfigRepository).findByTypeName(eq(DiscoveryServiceType.DOCKER_REPOSITORY.name()));
    }

    @Test
    void shutdownHandlesEmptyExecutorState() {
        checkExecutorService.shutdown();

        verifyNoInteractions(httpCheckService, dockerCheckService, dockerRepositorySyncService, openshiftRouteSyncService, resourceStatusStreamService);
    }
}
