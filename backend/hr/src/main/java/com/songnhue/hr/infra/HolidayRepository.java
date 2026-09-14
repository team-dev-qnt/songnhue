package com.songnhue.hr.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.hr.domain.Holiday;

/** Danh mục ngày nghỉ lễ — CN-04.9. */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<Holiday> findByDeletedAtIsNullOrderByHolidayDateAsc();

    /** Ngày lễ rơi trong một khoảng — nguồn của phép đếm ngày công. */
    @Query(
            """
            SELECT h.holidayDate FROM Holiday h
            WHERE h.deletedAt IS NULL AND h.holidayDate BETWEEN :tu AND :den
            """)
    List<LocalDate> ngayLeTrongKhoang(@Param("tu") LocalDate tu, @Param("den") LocalDate den);

    /**
     * <b>Số</b> ngày lễ đã khai cho một năm — ⛔ không phải một cờ có/⛔ không.
     *
     * <p>⛔⛔ Bản nháp đầu hỏi {@code count(h) > 0}. Nó **sai** theo hướng nguy hiểm nhất: seed
     * {@code V202608131008} đặt sẵn <b>4 ngày dương lịch cố định</b> mỗi năm, nên câu hỏi ấy trả
     * <b>CÓ</b> cho mọi năm đã seed — trong khi <b>Tết Nguyên đán, kỳ nghỉ dài nhất năm, vẫn đang
     * thiếu</b>. Một cờ xanh nói dối đúng ở ca nguy hiểm nhất.
     *
     * <p>⇒ Trả con số, để nơi gọi so với {@code SO_NGAY_LE_THEO_LUAT} (Điều 112 BLLĐ = 11) và nói
     * ra *"mới khai 4/11"* — người dùng tự phán xử, ⛔ không ai đoán hộ.
     */
    @Query(
            """
            SELECT count(h) FROM Holiday h
            WHERE h.deletedAt IS NULL
              AND h.holidayDate BETWEEN :dauNam AND :cuoiNam
            """)
    int demTrongNam(@Param("dauNam") LocalDate dauNam, @Param("cuoiNam") LocalDate cuoiNam);

    /** Chặn trùng ngày ở tầng service để có mã lỗi đọc được — chỉ mục duy nhất là lưới cuối. */
    Optional<Holiday> findByHolidayDateAndDeletedAtIsNull(LocalDate holidayDate);
}
