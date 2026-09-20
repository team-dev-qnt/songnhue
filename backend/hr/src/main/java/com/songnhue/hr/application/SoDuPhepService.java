package com.songnhue.hr.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.LeaveRequest;
import com.songnhue.hr.domain.LeaveState;
import com.songnhue.hr.domain.LeaveType;
import com.songnhue.hr.infra.LeaveRequestRepository;

/**
 * Số dư phép năm — CN-04.9, <b>tính lại từ đơn mỗi lượt đọc</b>.
 *
 * <h2>⛔⛔ Vì sao ⛔ KHÔNG có bảng `leave_balances`</h2>
 *
 * <p>Đặc tả chốt thẳng: <i>"Còn lại = Được hưởng − Đã nghỉ − Đang chờ duyệt (<b>tính lại từ đơn, ⛔
 * không cộng trừ tay</b>)"</i>. Một bảng số dư là một giá trị <b>dẫn xuất</b> phải giữ đồng bộ bằng
 * tay, và dự án đã trả giá cho đúng hình dạng ấy ở quy tắc 13: {@code ConstructionStatusService}
 * trộn hai nguồn khác chiều lọc, và trạng thái công trình thành ra phụ thuộc <i>ai bấm F5 sau
 * cùng</i>. Ở đây số dư SINH từ {@code leave_requests} — ⛔ không có gì để lệch.
 *
 * <p>⚠ Cái giá phải khai ra: mỗi lượt mở màn hình là hai câu {@code SUM}. Với ~200 CBNV và vài
 * chục đơn mỗi người mỗi năm, đó là một phép cộng trên vài nghìn hàng có chỉ mục — rẻ hơn hẳn một
 * cột lệch mà ⛔ không ai đối chiếu được với cái gì.
 *
 * <h2>⛔⛔ "Chuyển từ năm trước" — con số mà hệ CHƯA BIẾT, và nói thẳng ra</h2>
 *
 * <p>Số ngày chuyển sang = phần phép năm ngoái ⛔ không dùng hết, tối đa
 * {@code hr.leave.carry-over-max-days}. Nó suy được <b>chỉ khi</b> hệ có dữ liệu đơn của năm ngoái.
 * Hệ này mới chạy: với năm vận hành đầu tiên, năm trước <b>⛔ không có một đơn nào</b> — và khi ấy
 * công thức cho ra <i>"⛔ không dùng ngày nào ⇒ chuyển tối đa"</i>, tức <b>cấp thừa</b> phép cho mọi
 * người.
 *
 * <p>⇒ {@link SoDu#namTruocCoDuLieu} khai rằng hệ <b>chưa biết</b>, và khi ấy
 * {@link SoDu#chuyenTuNamTruoc} là <b>0</b> chứ ⛔ không phải mức tối đa. Giao diện nói *"chưa có
 * dữ liệu năm trước"* thay vì in một con số ⛔ không ai kiểm được. ⬜ Ngày Công ty cần nhập số dư
 * đầu kỳ thật thì thêm một đường ghi — nợ <b>T57.16</b> (câu hỏi G16-a), ⛔ dựng bảng trên phỏng đoán.
 *
 * <h2>⛔⛔ "Biết năm trước" là một THAM SỐ, ⛔ phải một phép đoán từ đơn — T57.16 (20/09/2026)</h2>
 *
 * <p>Bản trước đoán bằng <i>"người ấy có ≥ 1 đơn năm trước"</i>, và phép đoán sai cả hai chiều. Hệ lên
 * production giữa năm 2026, đơn giấy tháng 01–09 ⛔ nằm trong hệ ⇒ từ 01/01/2027 ai có một đơn 2026 được
 * chuyển = quỹ 2026 − số ngày <i>nhập trong hệ</i> ⇒ <b>cấp thừa</b> tới trần chuyển năm. Chiều ngược: năm
 * đã ghi đủ mà một người ⛔ nghỉ ngày nào thì bị coi là <i>chưa biết</i> ⇒ <b>mất</b> số chuyển — đúng người
 * chăm chỉ nhất. Nay: {@code hr.leave.first-fully-recorded-year} (mặc định 2027) —
 * {@code NghiPhepHttpTest#chuyenPhepChiTinhTuNamGhiNhanDu} canh cả ba ca.
 */
@Service
public class SoDuPhepService {

    private final LeaveRequestRepository donNghi;
    private final ChinhSachPhep chinhSach;

    public SoDuPhepService(LeaveRequestRepository donNghi, ChinhSachPhep chinhSach) {
        this.donNghi = donNghi;
        this.chinhSach = chinhSach;
    }

    /**
     * @param duocHuong tổng quỹ phép năm nay = theo thâm niên (đã pro-rata nếu vào làm giữa năm) +
     *     chuyển từ năm trước
     * @param daDung tổng ngày của đơn <b>đã duyệt</b> — phần chắc chắn đã tiêu
     * @param dangChoDuyet tổng ngày của đơn <b>đang chờ</b> — vẫn giữ chỗ, xem
     *     {@code LeaveState.conChiemSoDu()}
     * @param conLai {@code duocHuong − daDung − dangChoDuyet}. <b>Âm được</b>, và cố ý ⛔ không kẹp
     *     về 0: một người đã nghỉ quá phép là một sự thật nhân sự cần nhìn thấy, ⛔ không phải một
     *     con số cần giấu
     * @param namTruocCoDuLieu hệ ghi nhận ĐỦ năm trước ⛔ không ({@code hr.leave.first-fully-recorded-year}) —
     *     xem javadoc lớp
     */
    public record SoDu(
            int nam,
            BigDecimal duocHuong,
            BigDecimal theoThamNien,
            BigDecimal chuyenTuNamTruoc,
            BigDecimal daDung,
            BigDecimal dangChoDuyet,
            BigDecimal conLai,
            boolean namTruocCoDuLieu) {}

    @Transactional(readOnly = true)
    public SoDu tinh(Employee hoSo, int nam) {
        LocalDate dauNam = LocalDate.of(nam, 1, 1);
        LocalDate cuoiNam = LocalDate.of(nam, 12, 31);

        BigDecimal theoThamNien = quyPhepNam(hoSo, nam);

        // ⛔⛔ Chuyển từ năm trước: chỉ tính khi hệ ghi nhận ĐỦ năm ấy (T57.16) — ⛔ đoán từ "có đơn".
        boolean namTruocCoDuLieu = nam - 1 >= chinhSach.namGhiNhanDuDauTien();
        BigDecimal chuyen = namTruocCoDuLieu ? chuyenTuNamTruoc(hoSo, nam - 1) : BigDecimal.ZERO;

        BigDecimal duocHuong = theoThamNien.add(chuyen);

        // ⛔⛔ MỘT câu truy vấn, MỘT vị từ, chia nhóm ở đây. Bản đầu lấy *đã tiêu* bằng một câu
        //    `SUM` lọc `BETWEEN` rồi trừ đi *đang chờ* lấy bằng một câu lọc theo **chồng khoảng** —
        //    hai vị từ khác nhau, và hiệu của chúng là một con số ⛔ không ai định nghĩa được. Đúng
        //    hình dạng quy tắc 13, và nó suýt lọt vào chính lớp viết javadoc cảnh báo nó.
        List<LeaveRequest> don = donNghi.donChiemSoDuTrongNam(hoSo.getId(), LeaveType.PHEP_NAM, dauNam, cuoiNam);
        BigDecimal dangCho = cong(don.stream().filter(d -> d.trangThai().dangChoDuyet()));
        BigDecimal daDuyet = cong(don.stream().filter(d -> d.trangThai() == LeaveState.DA_DUYET));

        return new SoDu(
                nam,
                duocHuong,
                theoThamNien,
                chuyen,
                daDuyet,
                dangCho,
                duocHuong.subtract(daDuyet).subtract(dangCho),
                namTruocCoDuLieu);
    }

    /**
     * Quỹ phép năm theo thâm niên, <b>pro-rata</b> nếu vào làm giữa năm.
     *
     * <p>Công thức chốt C1: {@code 12 × số tháng / 12}, làm tròn theo
     * {@code hr.leave.prorata-rounding-step}. Ở đây "12" là <b>số ngày theo thâm niên</b> chứ ⛔
     * không phải hằng 12 — đọc công thức thành hằng số là ghi cứng đúng thứ chốt C1 cấm.
     *
     * <p>⚠ Người vào làm <b>trước</b> năm đang xét thì hưởng trọn quỹ — ⛔ không pro-rata.
     */
    private BigDecimal quyPhepNam(Employee hoSo, int nam) {
        LocalDate moc = mocThamNien(hoSo);
        if (moc == null) {
            // ⛔ Chưa có ngày vào làm ⇒ ⛔ không suy được thâm niên. Trả 0 và để giao diện nói
            //   "thiếu ngày vào làm" — ⛔ không đoán một con số rồi in lên như sự thật.
            return BigDecimal.ZERO;
        }
        int namThamNien = (int) ChronoUnit.YEARS.between(moc, LocalDate.of(nam, 12, 31));
        int quy = chinhSach.soNgayPhepNam(Math.max(namThamNien, 0));

        if (moc.getYear() < nam) {
            return BigDecimal.valueOf(quy);
        }
        if (moc.getYear() > nam) {
            return BigDecimal.ZERO;
        }
        // Vào làm GIỮA năm đang xét ⇒ pro-rata theo số tháng còn lại, tính cả tháng vào làm.
        int soThang = 13 - moc.getMonthValue();
        BigDecimal tho = BigDecimal.valueOf(quy)
                .multiply(BigDecimal.valueOf(soThang))
                .divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP);
        return chinhSach.lamTron(tho);
    }

    private LocalDate mocThamNien(Employee hoSo) {
        return chinhSach.mocThamNien() == ChinhSachPhep.MocThamNien.CONTRACT_DATE
                ? hoSo.getContractSignedAt()
                : hoSo.getHiredAt();
    }

    /** Phần ⛔ không dùng hết của năm {@code nam}, kẹp trần theo {@code carry-over-max-days}. */
    private BigDecimal chuyenTuNamTruoc(Employee hoSo, int nam) {
        BigDecimal quy = quyPhepNam(hoSo, nam);
        BigDecimal daTieu = cong(donNghi
                .donChiemSoDuTrongNam(
                        hoSo.getId(), LeaveType.PHEP_NAM, LocalDate.of(nam, 1, 1), LocalDate.of(nam, 12, 31))
                .stream());
        BigDecimal con = quy.subtract(daTieu);
        if (con.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return con.min(BigDecimal.valueOf(chinhSach.soNgayChuyenToiDa()));
    }

    private static BigDecimal cong(java.util.stream.Stream<LeaveRequest> don) {
        return don.map(LeaveRequest::getWorkingDays).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Năm hiện tại theo giờ Việt Nam — ⛔ không đọc đồng hồ máy chủ trần (luật T51.11). */
    public int namHienTai() {
        return LocalDate.now(DateTimeUtils.ZONE_VN).getYear();
    }
}
