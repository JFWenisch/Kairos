package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.wenisch.kairos.entity.CustomHeaderSettings;

public interface CustomHeaderSettingsRepository extends JpaRepository<CustomHeaderSettings, Long> {
}
