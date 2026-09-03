package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.hydro.domain.AlertLevel;
import com.songnhue.hydro.infra.AlertLevelRepository;
import com.songnhue.hydro.infra.AlertRuleRepository;

/**
 * Danh mục mức cảnh báo — <b>T33.1</b>.
 *
 * <p>⛔ Danh mục có CRUD, ⛔ cấm enum (quy tắc 16): bộ mức thật là <b>G9-a</b>, Công ty chưa chốt, và
 * ngày họ chốt thì thêm một mức ⛔ không được đòi một lượt deploy.
 *
 * <p>⛔ Migration ⛔ không seed dòng nào. Danh mục rỗng là trạng thái <b>hợp lệ</b>, không phải lỗi
 * cấu hình — nó nghĩa là <i>chưa có ngưỡng nào, nên chưa có cảnh báo nào</i>, đúng hành vi T33.6.
 */
@Service
public class AlertLevelService {

    private static final Logger log = LoggerFactory.getLogger(AlertLevelService.class);

    /**
     * ⛔ Khoá của {@code design-tokens}, ⛔ không phải mã hex.
     *
     * <p>Ràng buộc {@code ck_alert_levels_color_token} đã chặn ở tầng CSDL; mẫu này chặn <b>sớm
     * hơn</b> để người dùng nhận một dòng đỏ dưới đúng ô thay vì một lỗi ràng buộc thô. Hai tầng
     * cùng một luật ở đây là cố ý: tầng CSDL là thứ ⛔ không lối nào lách được, tầng này là thứ nói
     * được <i>vì sao</i>.
     */
    private static final Pattern MAU_KHOA_MAU = Pattern.compile("^[a-z][a-z0-9-]*$");

    private final AlertLevelRepository levels;
    private final AlertRuleRepository rules;

    public AlertLevelService(AlertLevelRepository levels, AlertRuleRepository rules) {
        this.levels = levels;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<AlertLevel> list() {
        return levels.findByDeletedAtIsNullOrderBySeverityRankAsc();
    }

    @Transactional(readOnly = true)
    public AlertLevel get(UUID publicId) {
        return tim(publicId);
    }

    @Transactional
    public AlertLevel create(
            String code, String name, String colorToken, Integer severityRank, Boolean active, String description) {
        String ma = chuanHoaMa(code);
        if (levels.existsByCodeIgnoreCaseAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        int hang = hang(severityRank);
        if (levels.existsBySeverityRankAndDeletedAtIsNull(hang)) {
            // ⚠ Hạng trùng ⛔ không phải chuyện thẩm mỹ: nó làm câu "mức nào nặng hơn" không có câu
            //   trả lời, và mức của một cảnh báo rơi vào thứ tự DB trả về (luật 13).
            throw new ConflictException(ErrorCode.HYD_1002, "severityRank=" + hang);
        }
        AlertLevel muc = new AlertLevel(ma, batBuoc(name, "name"), khoaMau(colorToken), hang);
        muc.setActive(active == null || active);
        muc.setDescription(description);
        AlertLevel daLuu = levels.save(muc);
        log.info("Thêm mức cảnh báo {} (hạng {})", ma, hang);
        return daLuu;
    }

    @Transactional
    public AlertLevel update(
            UUID publicId,
            String code,
            String name,
            String colorToken,
            Integer severityRank,
            Boolean active,
            String description) {
        AlertLevel muc = tim(publicId);
        String ma = chuanHoaMa(code);
        if (!ma.equalsIgnoreCase(muc.getCode()) && levels.existsByCodeIgnoreCaseAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        int hang = hang(severityRank);
        if (!Integer.valueOf(hang).equals(muc.getSeverityRank())
                && levels.existsBySeverityRankAndDeletedAtIsNull(hang)) {
            throw new ConflictException(ErrorCode.HYD_1002, "severityRank=" + hang);
        }
        muc.setCode(ma);
        muc.setName(batBuoc(name, "name"));
        muc.setColorToken(khoaMau(colorToken));
        muc.setSeverityRank(hang);
        // ⚠ `active == null` đọc là TRUE, cùng quy ước với MeasurementTypeService.update — ⛔ đừng
        //   đọc là "giữ nguyên": một PUT là mô tả TRẠNG THÁI ĐẦY ĐỦ, và "giữ nguyên" biến nó thành
        //   PATCH ngầm, tức một cờ mà màn hình bật/tắt được nhưng API thì không.
        muc.setActive(active == null || active);
        muc.setDescription(description);
        return muc;
    }

    /**
     * ⛔ Xoá mềm, và ⛔ <b>chặn khi còn ngưỡng trỏ vào</b> ({@code HYD-2010}).
     *
     * <p>⛔ Không xoá lan sang {@code alert_rules}: mức cảnh báo là danh mục của Công ty, và xoá nó
     * âm thầm tắt một loạt ngưỡng ai đó đã cấu hình bằng số liệu thật. Buộc gỡ ngưỡng trước là buộc
     * người dùng <b>nhìn thấy</b> cái mình sắp tắt.
     */
    @Transactional
    public void delete(UUID publicId) {
        AlertLevel muc = tim(publicId);
        if (rules.existsByAlertLevelIdAndDeletedAtIsNull(muc.getId())) {
            long dem = rules.findByDeletedAtIsNullOrderByIdAsc().stream()
                    .filter(r -> r.getAlertLevel().getId().equals(muc.getId()))
                    .count();
            throw new ConflictException(ErrorCode.HYD_2010, String.valueOf(dem));
        }
        muc.markDeleted(Instant.now());
        log.info("Xoá mềm mức cảnh báo {}", muc.getCode());
    }

    private AlertLevel tim(UUID publicId) {
        return levels.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static String chuanHoaMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw loiTruong("code", "BAT_BUOC", ma);
        }
        return ma.trim().toUpperCase(Locale.ROOT);
    }

    private static String batBuoc(String text, String truong) {
        if (text == null || text.isBlank()) {
            throw loiTruong(truong, "BAT_BUOC", text);
        }
        return text.trim();
    }

    private static String khoaMau(String colorToken) {
        String v = batBuoc(colorToken, "colorToken");
        if (!MAU_KHOA_MAU.matcher(v).matches()) {
            // ⚠ Tên vi phạm nói thẳng cái sai thường gặp nhất: người dùng dán một mã hex vào đây.
            throw loiTruong("colorToken", "PHAI_LA_KHOA_DESIGN_TOKEN", v);
        }
        return v;
    }

    private static int hang(Integer severityRank) {
        if (severityRank == null || severityRank < 1 || severityRank > 999) {
            throw loiTruong("severityRank", "NGOAI_KHOANG", String.valueOf(severityRank));
        }
        return severityRank;
    }

    /**
     * ⭐ Lỗi kèm <b>tên trường</b> — F1 đã trả giá cho việc thiếu nó.
     *
     * <p>{@code SYS-0003} trần làm {@code datLoiTheoTruong} ở FE trả {@code false}, và người dùng
     * nhận một toast chung chung thay vì một dòng đỏ dưới đúng ô.
     */
    private static ValidationException loiTruong(String truong, String maViPham, String giaTri) {
        return (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail(truong, maViPham, giaTri);
    }
}
