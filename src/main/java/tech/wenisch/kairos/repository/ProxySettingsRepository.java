package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.wenisch.kairos.entity.ProxySettings;

public interface ProxySettingsRepository extends JpaRepository<ProxySettings, Long> {
}
