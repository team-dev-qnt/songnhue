package com.songnhue.hr.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.hr.infra.HolidayRepository;

/**
 * Đếm số <b>ngày công</b> của một khoảng nghỉ — trừ cuối tuần và ngày lễ (CN-04.9).
 *
 * <h2>⭐ Phép đếm ⛔ KHÔNG viết ở đây — nó đã có sẵn từ Phase 0</h2>
 *
 * <p>{@code DateTimeUtils.countWorkingDays(from, to, holidays)} nằm trong kho từ WS-4 với javadoc
 * ghi thẳng <i>"Dùng cho tính số ngày nghỉ phép (CN-04.9)"</i> — và <b>0 nơi gọi production</b>
 * suốt một tháng (hai lời gọi duy nhất nằm trong {@code UtilsTest}). Bản nháp đầu của lớp này chép
 * lại vòng lặp ấy; chép là dựng bản sao <b>thứ hai</b> của định nghĩa *"ngày công"*, và hai bản sẽ
 * lệch đúng vào ngày Công ty đổi quy ước cuối tuần (luật 14).
 *
 * <p>⇒ Lớp này chỉ làm phần {@code DateTimeUtils} ⛔ không làm được: <b>lấy ngày lễ từ CSDL</b> và
 * <b>nói ra mức độ tin cậy</b> của con số.
 *
 * <h2>⛔⛔ "Đã có ngày lễ" ⛔ KHÔNG bằng "đã có ĐỦ ngày lễ"</h2>
 *
 * <p>Seed {@code V202608131008} đặt sẵn <b>4 ngày</b> mỗi năm — đúng những ngày lễ có <b>ngày dương
 * lịch cố định</b> mà Điều 112 BLLĐ 2019 ấn định (1/1 · 30/4 · 1/5 · 2/9), và <b>cố ý ⛔ không</b>
 * seed Tết Nguyên đán với Giỗ Tổ vì chúng theo âm lịch.
 *
 * <p>⇒ Một phép hỏi <i>"năm này có ngày lễ nào chưa"</i> sẽ trả <b>CÓ</b> cho mọi năm đã seed —
 * trong khi <b>Tết, kỳ nghỉ dài nhất năm, vẫn đang thiếu</b>. Đó đúng là ca nguy hiểm nhất: một đơn
 * nghỉ trùng Tết bị trừ cả tuần Tết vào phép năm, và cờ *"đã cấu hình"* nói dối.
 *
 * <p>⇒ Lớp này trả <b>số ngày đã khai</b> và so với {@link #SO_NGAY_LE_THEO_LUAT} — một con số
 * <b>pháp định</b> (Điều 112: Tết dương 1 + Tết âm 5 + Giỗ Tổ 1 + 30/4 1 + 1/5 1 + Quốc khánh 2 =
 * 11), ⛔ không phải một phỏng đoán. Giao diện nói *"năm N mới khai 4/11 ngày"* thay vì một cờ
 * xanh/đỏ giấu mất con số.
 */
@Service
public class DemNgayCongService {

    /**
     * Số ngày nghỉ lễ, tết <b>tối thiểu</b> trong một năm — Điều 112 BLLĐ 2019.
     *
     * <p>1 (Tết Dương lịch) + 5 (Tết Âm lịch) + 1 (Giỗ Tổ Hùng Vương) + 1 (30/4) + 1 (1/5) +
     * 2 (Quốc khánh) = <b>11</b>.
     *
     * <p>⛔ Đây là một con số <b>trích luật</b>, ⛔ không phải tham số vận hành — cùng lý do
     * {@code EducationLevel} là enum chứ ⛔ không phải bảng CRUD (quy tắc 16 nói về danh mục <i>do
     * khách vận hành</i>, và Công ty ⛔ không có thẩm quyền đổi số ngày lễ quốc gia). ⚠ Nó là
     * <b>cận dưới</b>: ngày nghỉ bù và lễ riêng làm con số thật lớn hơn, nên ⛔ không dùng nó để
     * <i>chặn</i>, chỉ để <i>nói ra</i>.
     */
    public static final int SO_NGAY_LE_THEO_LUAT = 11;

    private final HolidayRepository holidays;

    public DemNgayCongService(HolidayRepository holidays) {
        this.holidays = holidays;
    }

    /**
     * Kết quả đếm — <b>con số ⛔ không đi một mình</b> (quy tắc 16).
     *
     * @param soNgayLeTru số ngày lễ đã trừ được trong chính khoảng này
     * @param soNgayLeDaKhai số ngày lễ Công ty đã khai cho <b>năm ít đầy đủ nhất</b> mà khoảng này
     *     chạm tới
     * @param duNgayLeTheoLuat {@code soNgayLeDaKhai >= }{@link #SO_NGAY_LE_THEO_LUAT}. ⛔ <b>false</b>
     *     ⇒ giao diện phải nói *"năm N mới khai X/11 ngày lễ"*, ⛔ không được hiện
     *     {@code soNgayCong} như một sự thật đã kiểm
     */
    public record KetQua(BigDecimal soNgayCong, int soNgayLeTru, int soNgayLeDaKhai, boolean duNgayLeTheoLuat) {}

    @Transactional(readOnly = true)
    public KetQua dem(LocalDate tu, LocalDate den) {
        List<LocalDate> ngayLe = holidays.ngayLeTrongKhoang(tu, den);

        // ⭐ Phép đếm dùng lại `DateTimeUtils` — MỘT định nghĩa "ngày công" cho cả kho.
        long ngayCong = DateTimeUtils.countWorkingDays(tu, den, ngayLe);

        int itNhat = soNgayLeItNhatTrongCacNam(tu, den);
        return new KetQua(BigDecimal.valueOf(ngayCong), ngayLe.size(), itNhat, itNhat >= SO_NGAY_LE_THEO_LUAT);
    }

    /**
     * Năm <b>ít đầy đủ nhất</b> mà khoảng này chạm tới.
     *
     * <p>⚠ Lấy <b>min</b> chứ ⛔ không lấy năm bắt đầu: một đơn vắt qua giao thừa dương lịch chạm
     * hai năm, và năm sau thường là năm <b>chưa ai khai</b>. Hỏi năm bắt đầu sẽ im lặng đúng ca ấy.
     */
    private int soNgayLeItNhatTrongCacNam(LocalDate tu, LocalDate den) {
        int itNhat = Integer.MAX_VALUE;
        for (int nam = tu.getYear(); nam <= den.getYear(); nam++) {
            itNhat = Math.min(itNhat, holidays.demTrongNam(LocalDate.of(nam, 1, 1), LocalDate.of(nam, 12, 31)));
        }
        return itNhat == Integer.MAX_VALUE ? 0 : itNhat;
    }
}
