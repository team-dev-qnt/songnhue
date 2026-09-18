package com.songnhue.hr.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.SettingPort;
import com.songnhue.hr.domain.HoSoThuMuc;

/**
 * Đọc tham số nhân sự từ bảng {@code settings} — <b>nửa còn thiếu của một cặp đọc–ghi</b>.
 *
 * <h2>⛔⛔ Vì sao lớp này là một BẢN VÁ, ⛔ không phải một tiện ích</h2>
 *
 * <p>Đo ngày 10/09/2026: <b>15</b> khoá nhóm {@code hr.*} đã được seed từ <b>13/08/2026</b>
 * ({@code V202608131009}) và <b>cả 15 có 0 nơi đọc trong toàn kho</b>. Đó là luật 15 treo suốt
 * <b>28 ngày</b>: người vận hành thấy 15 ô nhập trên màn hình <i>Cấu hình hệ thống</i>, sửa chúng,
 * và ⛔ không có gì đổi. Cùng hình dạng với tám khoá {@code HYDRO} mà {@code HydroSettings} đã vá.
 *
 * <p>⚠⚠ Lớp này đóng <b>2 trong 15</b>. Mười ba khoá còn lại đều là {@code hr.leave.*} — chúng
 * thuộc CN-04.9 (nghỉ phép) và <b>vẫn đang mồ côi</b>. Con số ấy được ghi ra ở đây chứ ⛔ không để
 * lượt rà sau tự phát hiện lại: nợ có số đo thì trả được, nợ ⛔ không có số đo thì ⛔ không.
 *
 * <h2>Hai khoá lớp này đọc</h2>
 *
 * <ul>
 *   <li>{@code hr.contract.expiry-warning-days} (mặc định 30) — M4.9, hợp đồng sắp hết hạn;
 *   <li>{@code hr.certificate.expiry-warning-days} (mặc định 90) — M4.9, chứng chỉ hết hiệu lực.
 * </ul>
 *
 * <p>Cộng thêm bảy khoá hạn mức thư mục và một khoá danh sách thư mục bắt buộc do WS-53 seed —
 * chúng ra đời <b>cùng lượt</b> với người đọc, đúng thứ quy tắc 15 đòi.
 *
 * <p>⛔ Giá trị dự phòng ở đây <b>phải khác</b> giá trị seed thì bài kiểm mới phân biệt được
 * <i>"đọc từ settings"</i> với <i>"rơi về dự phòng"</i> — T48.7/T48.9 đã trả giá cho đúng chuyện
 * ấy. Nên bài {@code HrSettingsTest} đổi giá trị trong CSDL rồi đo, ⛔ không so với hằng số ở đây.
 */
@Component
public class HrSettings {

    private static final Logger log = LoggerFactory.getLogger(HrSettings.class);

    static final String KHOA_HD = "hr.contract.expiry-warning-days";
    static final String KHOA_CHUNG_CHI = "hr.certificate.expiry-warning-days";
    static final String KHOA_THU_MUC_BAT_BUOC = "hr.document.required-folders";

    private static final int MAC_DINH_HD = 30;
    private static final int MAC_DINH_CHUNG_CHI = 90;
    private static final int MAC_DINH_HAN_MUC_MB = 10;

    private final SettingPort settings;

    public HrSettings(SettingPort settings) {
        this.settings = settings;
    }

    /** Báo trước bao nhiêu ngày với hợp đồng sắp hết hạn (M4.9). */
    public int soNgayBaoTruocHopDong() {
        return settings.getInt(KHOA_HD, MAC_DINH_HD);
    }

    /** Báo trước bao nhiêu ngày với chứng chỉ sắp hết hiệu lực (M4.9). */
    public int soNgayBaoTruocChungChi() {
        return settings.getInt(KHOA_CHUNG_CHI, MAC_DINH_CHUNG_CHI);
    }

    /** Dung lượng tối đa mỗi tệp của một thư mục hồ sơ (CN-04.5). */
    public int hanMucMb(HoSoThuMuc thuMuc) {
        return settings.getInt(thuMuc.khoaHanMuc(), MAC_DINH_HAN_MUC_MB);
    }

    /**
     * Những thư mục <b>bắt buộc</b> phải có ít nhất một tệp thì hồ sơ mới coi là hoàn thiện.
     *
     * <h2>⬜ Đây là một điểm nghiệp vụ CHƯA CHỐT, và nó được khai ra</h2>
     *
     * <p>CN-04.5 đòi <i>"% hoàn thiện hồ sơ + danh sách tài liệu thiếu"</i> nhưng ⛔ <b>không</b>
     * nói thư mục nào bắt buộc. Ghi cứng một danh sách trong mã là bịa một luật nhân sự rồi in tỉ lệ
     * % của nó lên màn hình Ban giám đốc.
     *
     * <p>⇒ Danh sách là tham số. <b>Rỗng là hợp lệ</b> và có nghĩa <i>"⛔ không tính %"</i> — ⛔
     * không phải <i>"0%"</i>. Hai trạng thái ấy khác nhau và quy tắc 16 nói số 0 là một khẳng định.
     *
     * <p>⚠ Tên ⛔ không giải được thì <b>bỏ qua kèm một dòng WARN</b>, ⛔ không ném: một ký tự thừa
     * trong ô cấu hình ⛔ không được phép làm chết màn hình hồ sơ. Nhưng nó cũng ⛔ không được im —
     * im thì người sửa cấu hình tưởng mình vừa khai đúng.
     */
    public Set<HoSoThuMuc> thuMucBatBuoc() {
        String tho = settings.getString(KHOA_THU_MUC_BAT_BUOC).orElse("");
        Set<HoSoThuMuc> ket = new LinkedHashSet<>();
        for (String phan : tho.split(",")) {
            String ten = phan.trim().toUpperCase(Locale.ROOT);
            if (ten.isEmpty()) {
                continue;
            }
            Arrays.stream(HoSoThuMuc.values())
                    .filter(tm -> tm.name().equals(ten))
                    .findFirst()
                    .ifPresentOrElse(
                            ket::add,
                            () -> log.warn(
                                    "Khoá {} có giá trị '{}' ⛔ không phải tên thư mục hồ sơ nào — bỏ qua. "
                                            + "Tên hợp lệ: {}",
                                    KHOA_THU_MUC_BAT_BUOC,
                                    ten,
                                    Arrays.toString(HoSoThuMuc.values())));
        }
        return ket;
    }
}
