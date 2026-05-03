package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.CustomHeaderSettings;
import tech.wenisch.kairos.repository.CustomHeaderSettingsRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomHeaderServiceTest {

    @Mock private CustomHeaderSettingsRepository repository;

    private CustomHeaderService service;

    @BeforeEach
    void setUp() {
        service = new CustomHeaderService(repository);
    }

    // ── getSettings ────────────────────────────────────────────────────────────

    @Test
    void getSettingsReturnsDefaultWhenNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        CustomHeaderSettings settings = service.getSettings();
        assertThat(settings.getContent()).isEmpty();
        assertThat(settings.isApplyToAdmin()).isFalse();
    }

    @Test
    void getSettingsReturnsStoredEntity() {
        CustomHeaderSettings stored = CustomHeaderSettings.builder()
                .id(1L).content("<script>alert('x')</script>").applyToAdmin(true).build();
        when(repository.findById(1L)).thenReturn(Optional.of(stored));

        CustomHeaderSettings result = service.getSettings();
        assertThat(result.getContent()).isEqualTo("<script>alert('x')</script>");
        assertThat(result.isApplyToAdmin()).isTrue();
    }

    // ── saveSettings ───────────────────────────────────────────────────────────

    @Test
    void saveSettingsConvertsNullContentToEmpty() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        service.saveSettings(null, false);
        verify(repository).save(any(CustomHeaderSettings.class));
    }

    @Test
    void saveSettingsStoresContent() {
        CustomHeaderSettings existing = CustomHeaderSettings.builder().id(1L).content("").applyToAdmin(false).build();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.saveSettings("<p>Custom</p>", true);

        assertThat(existing.getContent()).isEqualTo("<p>Custom</p>");
        assertThat(existing.isApplyToAdmin()).isTrue();
        verify(repository).save(existing);
    }

    // ── getHtmlForPage ─────────────────────────────────────────────────────────

    @Test
    void getHtmlForPageReturnsEmptyWhenContentBlank() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.getHtmlForPage(false)).isEmpty();
    }

    @Test
    void getHtmlForPageReturnsEmptyForAdminPageWhenNotApplied() {
        CustomHeaderSettings settings = CustomHeaderSettings.builder()
                .id(1L).content("<p>Header</p>").applyToAdmin(false).build();
        when(repository.findById(1L)).thenReturn(Optional.of(settings));

        assertThat(service.getHtmlForPage(true)).isEmpty();
    }

    @Test
    void getHtmlForPageReturnsContentForPublicPage() {
        CustomHeaderSettings settings = CustomHeaderSettings.builder()
                .id(1L).content("<p>Header</p>").applyToAdmin(false).build();
        when(repository.findById(1L)).thenReturn(Optional.of(settings));

        assertThat(service.getHtmlForPage(false)).isEqualTo("<p>Header</p>");
    }

    @Test
    void getHtmlForPageReturnsContentForAdminPageWhenApplied() {
        CustomHeaderSettings settings = CustomHeaderSettings.builder()
                .id(1L).content("<p>AdminHeader</p>").applyToAdmin(true).build();
        when(repository.findById(1L)).thenReturn(Optional.of(settings));

        assertThat(service.getHtmlForPage(true)).isEqualTo("<p>AdminHeader</p>");
    }
}
