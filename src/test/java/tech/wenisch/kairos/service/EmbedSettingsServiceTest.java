package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.EmbedAllowedOrigin;
import tech.wenisch.kairos.entity.EmbedPolicy;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.EmbedAllowedOriginRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbedSettingsServiceTest {

    @Mock private ResourceTypeConfigRepository resourceTypeConfigRepository;
    @Mock private EmbedAllowedOriginRepository embedAllowedOriginRepository;

    private EmbedSettingsService service;

    @BeforeEach
    void setUp() {
        service = new EmbedSettingsService(resourceTypeConfigRepository, embedAllowedOriginRepository);
    }

    // ── getPolicy ──────────────────────────────────────────────────────────────

    @Test
    void getPolicyReturnsAllowAllWhenNoConfigs() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of());
        assertThat(service.getPolicy()).isEqualTo(EmbedPolicy.ALLOW_ALL);
    }

    @Test
    void getPolicyReturnsConfiguredPolicy() {
        ResourceTypeConfig config = ResourceTypeConfig.builder().embedPolicy("DISABLED").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        assertThat(service.getPolicy()).isEqualTo(EmbedPolicy.DISABLED);
    }

    @Test
    void getPolicyReturnsAllowAllForInvalidValue() {
        ResourceTypeConfig config = ResourceTypeConfig.builder().embedPolicy("GARBAGE").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        assertThat(service.getPolicy()).isEqualTo(EmbedPolicy.ALLOW_ALL);
    }

    // ── setPolicyForAllResourceTypes ───────────────────────────────────────────

    @Test
    void setPolicySavesEachConfig() {
        ResourceTypeConfig c1 = ResourceTypeConfig.builder().typeName("HTTP").embedPolicy("ALLOW_ALL").build();
        ResourceTypeConfig c2 = ResourceTypeConfig.builder().typeName("DOCKER").embedPolicy("ALLOW_ALL").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(c1, c2));

        service.setPolicyForAllResourceTypes(EmbedPolicy.DISABLED);

        verify(resourceTypeConfigRepository).save(c1);
        verify(resourceTypeConfigRepository).save(c2);
        assertThat(c1.getEmbedPolicy()).isEqualTo("DISABLED");
        assertThat(c2.getEmbedPolicy()).isEqualTo("DISABLED");
    }

    // ── normalizeOrigin ────────────────────────────────────────────────────────

    @Test
    void normalizeOriginThrowsForNull() {
        assertThatThrownBy(() -> service.normalizeOrigin(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeOriginThrowsForBlank() {
        assertThatThrownBy(() -> service.normalizeOrigin("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeOriginThrowsForNoScheme() {
        assertThatThrownBy(() -> service.normalizeOrigin("example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeOriginThrowsForFtpScheme() {
        assertThatThrownBy(() -> service.normalizeOrigin("ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeOriginAcceptsHttps() {
        assertThat(service.normalizeOrigin("https://example.com")).isEqualTo("https://example.com");
    }

    @Test
    void normalizeOriginAcceptsHttp() {
        assertThat(service.normalizeOrigin("http://example.com")).isEqualTo("http://example.com");
    }

    @Test
    void normalizeOriginPreservesPort() {
        assertThat(service.normalizeOrigin("https://example.com:8443")).isEqualTo("https://example.com:8443");
    }

    @Test
    void normalizeOriginStripsPath() {
        assertThat(service.normalizeOrigin("https://example.com/some/path")).isEqualTo("https://example.com");
    }

    // ── addAllowedOrigin ───────────────────────────────────────────────────────

    @Test
    void addAllowedOriginReturnsFalseWhenDuplicate() {
        when(embedAllowedOriginRepository.existsByOrigin("https://example.com")).thenReturn(true);
        assertThat(service.addAllowedOrigin("https://example.com")).isFalse();
    }

    @Test
    void addAllowedOriginSavesAndReturnsTrue() {
        when(embedAllowedOriginRepository.existsByOrigin("https://example.com")).thenReturn(false);
        assertThat(service.addAllowedOrigin("https://example.com")).isTrue();
        verify(embedAllowedOriginRepository).save(any(EmbedAllowedOrigin.class));
    }

    // ── removeAllowedOrigin ────────────────────────────────────────────────────

    @Test
    void removeAllowedOriginCallsDeleteById() {
        service.removeAllowedOrigin(5L);
        verify(embedAllowedOriginRepository).deleteById(5L);
    }

    // ── listAllowedOrigins ─────────────────────────────────────────────────────

    @Test
    void listAllowedOriginsSortsCaseInsensitive() {
        EmbedAllowedOrigin b = EmbedAllowedOrigin.builder().origin("https://beta.example.com").build();
        EmbedAllowedOrigin a = EmbedAllowedOrigin.builder().origin("https://Alpha.example.com").build();
        when(embedAllowedOriginRepository.findAll()).thenReturn(List.of(b, a));

        List<EmbedAllowedOrigin> result = service.listAllowedOrigins();
        assertThat(result.get(0).getOrigin()).isEqualTo("https://Alpha.example.com");
    }

    // ── frameAncestorsDirective ────────────────────────────────────────────────

    @Test
    void frameAncestorsDirectiveReturnsNoneForDisabled() {
        ResourceTypeConfig config = ResourceTypeConfig.builder().embedPolicy("DISABLED").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        assertThat(service.frameAncestorsDirective()).isEqualTo("'none'");
    }

    @Test
    void frameAncestorsDirectiveReturnsWildcardForAllowAll() {
        ResourceTypeConfig config = ResourceTypeConfig.builder().embedPolicy("ALLOW_ALL").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        assertThat(service.frameAncestorsDirective()).isEqualTo("*");
    }

    @Test
    void frameAncestorsDirectiveReturnsOriginsForAllowList() {
        ResourceTypeConfig config = ResourceTypeConfig.builder().embedPolicy("ALLOW_LIST").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        EmbedAllowedOrigin o = EmbedAllowedOrigin.builder().origin("https://example.com").build();
        when(embedAllowedOriginRepository.findAll()).thenReturn(List.of(o));

        String directive = service.frameAncestorsDirective();
        assertThat(directive).contains("'self'").contains("https://example.com");
    }

    @Test
    void frameAncestorsDirectiveReturnsNoneForAllowListWithNoOrigins() {
        ResourceTypeConfig config = ResourceTypeConfig.builder().embedPolicy("ALLOW_LIST").build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        when(embedAllowedOriginRepository.findAll()).thenReturn(List.of());

        assertThat(service.frameAncestorsDirective()).isEqualTo("'none'");
    }
}
