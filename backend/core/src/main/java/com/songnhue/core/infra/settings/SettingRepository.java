package com.songnhue.core.infra.settings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.settings.Setting;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long> {

    Optional<Setting> findBySettingKey(String settingKey);
}
