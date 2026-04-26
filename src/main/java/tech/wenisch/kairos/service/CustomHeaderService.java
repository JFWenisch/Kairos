package tech.wenisch.kairos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tech.wenisch.kairos.entity.CustomHeaderSettings;
import tech.wenisch.kairos.repository.CustomHeaderSettingsRepository;

@Service
@RequiredArgsConstructor
public class CustomHeaderService {

    private static final long SETTINGS_ID = 1L;

    private final CustomHeaderSettingsRepository repository;

    public CustomHeaderSettings getSettings() {
        return repository.findById(SETTINGS_ID)
                .orElseGet(() -> CustomHeaderSettings.builder()
                        .id(SETTINGS_ID)
                        .content("")
                        .applyToAdmin(false)
                        .build());
    }

    @Transactional
    public void saveSettings(String content, boolean applyToAdmin) {
        CustomHeaderSettings settings = getSettings();
        settings.setId(SETTINGS_ID);
        settings.setContent(content != null ? content : "");
        settings.setApplyToAdmin(applyToAdmin);
        repository.save(settings);
    }

    public String getHtmlForPage(boolean isAdminPage) {
        CustomHeaderSettings settings = getSettings();
        String content = settings.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        if (isAdminPage && !settings.isApplyToAdmin()) {
            return "";
        }
        return content;
    }
}
