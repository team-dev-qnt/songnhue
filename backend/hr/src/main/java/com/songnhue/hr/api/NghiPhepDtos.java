package com.songnhue.hr.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.hr.application.SoDuPhepService;
import com.songnhue.hr.domain.Holiday;
import com.songnhue.hr.domain.LeaveRequest;
import com.songnhue.hr.domain.LeaveType;

/** Kiểu vào/ra của API nghỉ phép — CN-04.9. */
public final class NghiPhepDtos {

    private NghiPhepDtos() {}

    // === Ngày lễ =============================================================

    public record NgayLeRequest(
            @NotNull LocalDate holidayDate, @NotNull @Size(max = 255) String name, @Size(max = 500) String note) {}

    public record NgayLeView(UUID publicId, LocalDate holidayDate, String name, String note) {

        public static NgayLeView of(Holiday h) {
            return new NgayLeView(h.getPublicId(), h.getHolidayDate(), h.getName(), h.getNote());
        }
    }

    // === Đơn nghỉ phép =======================================================

    /**
     * @param employeePublicId {@code null} = nộp cho <b>chính mình</b>; khác null = <b>nộp hộ</b>
     *     (chốt C3), đòi thêm {@code hr:leave:view-all}
     */
    public record DonRequest(
            UUID employeePublicId,
            @NotNull LeaveType leaveType,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @Size(max = 1000) String reason) {}

    /** Thân của một bước chuyển. {@code reason} bắt buộc với bước có {@code requires_reason}. */
    public record HanhDongRequest(@NotNull String action, @Size(max = 1000) String reason) {}

    public record DonView(
            UUID publicId,
            UUID employeePublicId,
            String employeeCode,
            String employeeName,
            LeaveType leaveType,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal workingDays,
            String reason,
            String state,
            boolean noHo,
            Instant decidedAt) {

        /**
         * @param noHo đơn này do <b>người khác nộp hộ</b> (chốt C3) — giao diện hiện nhãn, vì một
         *     đơn nộp hộ và một đơn tự nộp mang hai mức tin cậy khác nhau khi đối chiếu về sau
         */
        public static DonView of(LeaveRequest r, UUID employeePublicId, String code, String name) {
            return new DonView(
                    r.getPublicId(),
                    employeePublicId,
                    code,
                    name,
                    r.getLeaveType(),
                    r.getFromDate(),
                    r.getToDate(),
                    r.getWorkingDays(),
                    r.getReason(),
                    r.currentState(),
                    r.getCreatedForBy() != null,
                    r.getDecidedAt());
        }
    }

    /**
     * @param soNgayLeDaKhai số ngày lễ Công ty đã khai cho năm ít đầy đủ nhất mà đơn chạm tới
     * @param duNgayLeTheoLuat {@code soNgayLeDaKhai >= 11} (Điều 112 BLLĐ). ⛔ <b>false</b> ⇒ giao
     *     diện PHẢI nói *"năm N mới khai X/11 ngày lễ"*. ⚠ Seed đặt sẵn 4 ngày dương lịch cố định
     *     mỗi năm, nên một cờ *"đã có ngày lễ nào chưa"* sẽ nói CÓ trong khi **Tết vẫn thiếu** —
     *     đúng ca nguy hiểm nhất (luật 9)
     * @param canhBaoTrungLich {@code null} = dưới ngưỡng. Là <b>cảnh báo</b>, ⛔ không phải chặn
     */
    public record XemTruocView(
            BigDecimal soNgayCong,
            int soNgayLeTru,
            int soNgayLeDaKhai,
            boolean duNgayLeTheoLuat,
            SoDuView soDu,
            boolean vuotPhep,
            boolean canCapHaiDuyet,
            long soNguoiNghiCungLuc,
            String canhBaoTrungLich) {}

    /**
     * @param namTruocCoDuLieu hệ có đơn của năm trước ⛔ không. {@code false} ⇒ {@code chuyenTuNamTruoc}
     *     là <b>0 vì CHƯA BIẾT</b>, ⛔ không phải vì đã dùng hết — giao diện phải nói ra khác biệt ấy
     * @param conLai <b>âm được</b>, và cố ý ⛔ không kẹp về 0
     */
    public record SoDuView(
            int nam,
            BigDecimal duocHuong,
            BigDecimal theoThamNien,
            BigDecimal chuyenTuNamTruoc,
            BigDecimal daDung,
            BigDecimal dangChoDuyet,
            BigDecimal conLai,
            boolean namTruocCoDuLieu) {

        public static SoDuView of(SoDuPhepService.SoDu d) {
            return new SoDuView(
                    d.nam(),
                    d.duocHuong(),
                    d.theoThamNien(),
                    d.chuyenTuNamTruoc(),
                    d.daDung(),
                    d.dangChoDuyet(),
                    d.conLai(),
                    d.namTruocCoDuLieu());
        }
    }

    /** Trang đơn kèm tổng — giao diện cần tổng để nói *"N đơn"*, ⛔ suy được từ một trang. */
    public record DonTrangView(List<DonView> muc, long tong, int trang, int co) {}
}
