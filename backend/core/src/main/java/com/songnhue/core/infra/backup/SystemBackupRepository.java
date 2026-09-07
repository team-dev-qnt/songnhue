package com.songnhue.core.infra.backup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.backup.BackupStatus;
import com.songnhue.core.domain.backup.SystemBackup;

@Repository
public interface SystemBackupRepository extends JpaRepository<SystemBackup, Long> {

    Optional<SystemBackup> findByPublicId(UUID publicId);

    /**
     * Lượt sao lưu <b>thành công</b> gần nhất — nguồn của metric {@code backup_last_success_timestamp}
     * (T7.3) và của dòng trạng thái trên màn hình M5.10.
     *
     * <p>⚠ Cố ý lọc {@code SUCCEEDED}. "Lượt gần nhất" và "lượt thành công gần nhất" chỉ khác nhau
     * đúng vào lúc sao lưu đang hỏng — tức là đúng lúc con số này được đọc để ra quyết định.
     */
    Optional<SystemBackup> findFirstByStatusOrderByFinishedAtDesc(BackupStatus status);

    /** Toàn bộ lịch sử, mới nhất trước — màn hình M5.10 và danh sách nguồn khôi phục M5.11. */
    List<SystemBackup> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * Bản thành công quá hạn giữ — ứng viên để xoá file.
     *
     * <p>Chỉ trả về bản {@code SUCCEEDED} có đường dẫn: bản hỏng không có file để xoá, nhưng dòng ghi
     * nhận thì <b>giữ lại</b> — lịch sử "đêm nào sao lưu hỏng" là thứ cần khi điều tra mất dữ liệu.
     */
    @Query(
            """
            SELECT b FROM SystemBackup b
             WHERE b.status = com.songnhue.core.domain.backup.BackupStatus.SUCCEEDED
               AND b.filePath IS NOT NULL
               AND b.finishedAt < :cutoff
             ORDER BY b.finishedAt ASC
            """)
    List<SystemBackup> findExpired(@Param("cutoff") Instant cutoff);

    /** Lượt đang chạy — chặn hai lượt sao lưu chồng nhau (đọc đĩa gấp đôi, không được gì). */
    boolean existsByStatus(BackupStatus status);
}
