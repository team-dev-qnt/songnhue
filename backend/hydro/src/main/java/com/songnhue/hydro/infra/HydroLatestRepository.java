package com.songnhue.hydro.infra;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.songnhue.hydro.domain.HydroLatest;

/**
 * Đọc bảng "mực nước hiện tại".
 *
 * <p>⭐ Đây là điểm tích hợp chính giữa {@code hydro} và phần còn lại của hệ: widget cổng công khai,
 * lớp GIS và dashboard đọc bảng này. ⛔ Không nơi nào trong số đó được quét {@code hydro_readings}
 * (quy tắc 8 — báo cáo/dashboard đọc bảng tổng hợp, không scan raw).
 */
public interface HydroLatestRepository extends JpaRepository<HydroLatest, Long> {

    /**
     * Mốc đo gần nhất của <b>toàn hệ</b>, bất kể chất lượng — nuôi chỉ số độ tươi dữ liệu (T29.8).
     *
     * <p>⚠ Cố ý dùng {@code last_seen_at} chứ không {@code valid_measured_at}: câu hỏi ở đây là
     * <i>"poller còn sống không"</i>, không phải <i>"số liệu có dùng được không"</i>. Một nguồn chỉ
     * trả toàn số nghi ngờ vẫn đang phát tín hiệu, và báo động sai nguyên nhân thì huy động sai
     * người.
     *
     * <p>@return rỗng khi <b>chưa từng có</b> bản ghi nào — ⛔ khác hẳn "dữ liệu tươi". Với một
     * nguồn không lấy lại được thì "chưa từng có" là tình trạng đáng báo động, không phải trung tính
     * ({@code DataFreshnessRegistry.ageOf} nói đúng điều này).
     */
    @Query("select max(l.lastSeenAt) from HydroLatest l")
    Optional<Instant> mocDoGanNhat();
}
