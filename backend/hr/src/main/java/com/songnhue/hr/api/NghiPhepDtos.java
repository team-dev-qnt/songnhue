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
            Instant decidedAt,
            Boolean toiDuyetDuoc) {

        /**
         * @param noHo đơn này do <b>người khác nộp hộ</b> (chốt C3) — giao diện hiện nhãn, vì một
         *     đơn nộp hộ và một đơn tự nộp mang hai mức tin cậy khác nhau khi đối chiếu về sau
         */
        /**
         * ⚠⚠ {@code toiDuyetDuoc = null} là một trạng thái THỨ BA, ⛔ phải "false cho gọn" — T80.7.
         *
         * <p>Ba câu trả lời khác nhau, và gộp hai cái đầu là nói dối: {@code true} = bấm được ·
         * {@code false} = <b>thấy mà ⛔ bấm được</b> (⛔ giữ chức vụ, ⛔ được uỷ quyền) ·
         * {@code null} = <i>endpoint này ⛔ trả lời câu ấy</i> (danh sách đơn của chính mình, lịch sử
         * của một CBNV — hỏi *"tôi duyệt được ⛔"* ở đó là vô nghĩa). Trả {@code false} ở ca thứ ba
         * thì giao diện hiện *"⛔ duyệt được"* trên chính đơn của mình — một câu đúng mà vô duyên, và
         * nó làm lượt rà sau tưởng cờ đã được tính ở mọi nơi (cùng lý lẽ ba trạng thái của T59.0).
         */
        public static DonView of(LeaveRequest r, UUID employeePublicId, String code, String name) {
            return of(r, employeePublicId, code, name, null);
        }

        public static DonView of(
                LeaveRequest r, UUID employeePublicId, String code, String name, Boolean toiDuyetDuoc) {
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
                    r.getDecidedAt(),
                    toiDuyetDuoc);
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

    // === Lịch nghỉ đơn vị (T57.18 vế b) ======================================

    /**
     * Một ô ngày trên lịch.
     *
     * @param tyLePhanTram {@code null} = đơn vị ⛔ có quân số ⇒ ⛔ có mẫu số để chia. Giao diện phải
     *     nói *"chưa có quân số"* chứ ⛔ vẽ một vòng 0% — 0% nghĩa là <i>⛔ ai nghỉ</i>
     * @param vuotNguong cảnh báo bố trí ca trực, ⛔ phải một lệnh cấm
     */
    public record LichNgayView(LocalDate ngay, long soNguoiNghi, Integer tyLePhanTram, boolean vuotNguong) {}

    /**
     * Lịch nghỉ của một đơn vị trong một tháng.
     *
     * <p>⚠ {@code thang} là chuỗi {@code yyyy-MM}: nó là một <b>tháng dương lịch</b>, ⛔ phải một
     * khoảnh khắc. Trả {@code Instant} ở đây là mời giao diện đổi múi giờ rồi rơi sang tháng khác —
     * đúng lớp lỗi T63.18, chỉ là ở cỡ tháng thay vì cỡ ngày.
     *
     * @param don đơn THÔ, dựng bằng cùng {@code toView} mà hộp chờ duyệt dùng — ⛔ có bản mô tả thứ
     *     hai của một lá đơn (luật 14)
     * @param nguongPhanTram ngưỡng {@code hr.leave.overlap-warning-percent} đang áp dụng — gửi kèm
     *     để màn hình giải thích được vì sao một ô đỏ, thay vì bắt người đọc đi tra {@code settings}
     */
    public record LichView(
            UUID donViPublicId,
            String tenDonVi,
            String thang,
            long quanSo,
            int nguongPhanTram,
            List<DonView> don,
            List<LichNgayView> ngay) {}
}
