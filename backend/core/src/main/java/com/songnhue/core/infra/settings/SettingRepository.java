package com.songnhue.core.infra.settings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.settings.Setting;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long> {

    Optional<Setting> findBySettingKey(String settingKey);

    /** Màn hình cấu hình gom theo nhóm rồi mới tới thứ tự trong nhóm. */
    List<Setting> findAllByOrderByGroupCodeAscSortOrderAscSettingKeyAsc();

    List<Setting> findByGroupCodeOrderBySortOrderAscSettingKeyAsc(String groupCode);

    /**
     * Chỉ tham số được phép xuất (M5.17).
     *
     * <p>Lọc ở truy vấn chứ không lọc trong Java sau khi tải hết — credential không cần đi vào bộ
     * nhớ tiến trình rồi mới bị bỏ đi (conventions.md §4.7).
     */
    List<Setting> findByExportableTrueOrderBySettingKeyAsc();
}
