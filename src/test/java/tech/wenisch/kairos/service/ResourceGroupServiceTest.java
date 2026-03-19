package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.repository.ResourceGroupRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceGroupServiceTest {

    @Mock
    private ResourceGroupRepository resourceGroupRepository;

    private ResourceGroupService resourceGroupService;

    @BeforeEach
    void setUp() {
        resourceGroupService = new ResourceGroupService(resourceGroupRepository);
    }

    @Test
    void findAllOrderedDelegatesToRepository() {
        ResourceGroup g = new ResourceGroup();
        g.setName("Infra");
        when(resourceGroupRepository.findAllByOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(g));

        List<ResourceGroup> result = resourceGroupService.findAllOrdered();

        assertThat(result).containsExactly(g);
    }

    @Test
    void findByIdReturnsFromRepository() {
        ResourceGroup g = new ResourceGroup();
        g.setName("Apps");
        when(resourceGroupRepository.findById(1L)).thenReturn(Optional.of(g));

        Optional<ResourceGroup> result = resourceGroupService.findById(1L);

        assertThat(result).contains(g);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(resourceGroupRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<ResourceGroup> result = resourceGroupService.findById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void saveTrimsGroupName() {
        ResourceGroup g = new ResourceGroup();
        g.setName("  Infra  ");
        when(resourceGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceGroup saved = resourceGroupService.save(g);

        assertThat(saved.getName()).isEqualTo("Infra");
    }

    @Test
    void saveWithNullNameDoesNotThrow() {
        ResourceGroup g = new ResourceGroup();
        g.setName(null);
        when(resourceGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceGroup saved = resourceGroupService.save(g);

        assertThat(saved.getName()).isNull();
    }

    @Test
    void deleteDelegatesToRepository() {
        resourceGroupService.delete(5L);

        verify(resourceGroupRepository).deleteById(5L);
    }
}
