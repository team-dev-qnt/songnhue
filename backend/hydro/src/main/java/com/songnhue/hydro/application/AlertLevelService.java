package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import com.songnhue.core.spi.PortalCachePort;
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

    /**
     * ⭐⭐ Danh sách khoá màu <b>thật sự vẽ được</b> — T35.14.
     *
     * <h2>Vì sao regex một mình là chưa đủ</h2>
     *
     * <p>{@link #MAU_KHOA_MAU} chỉ kiểm <b>hình dạng</b>. Cho tới 04/09/2026 nó là tầng chặn duy
     * nhất, và hệ quả đo được: {@code color_token = "banana"} đi lọt service, lọt CHECK của CSDL,
     * lọt mọi bài kiểm — rồi hiện lên màn hình đúng chữ "banana", vì ⛔ <b>không bảng ánh xạ nào
     * trong toàn kho</b> đổi khoá thành màu. Nửa <i>ghi</i> hoàn chỉnh, nửa <i>đọc</i> không tồn
     * tại (luật 27).
     *
     * <p>⇒ Từ nay khoá phải nằm trong <b>bảng màu có thật</b>.
     *
     * <h2>⛔⛔ HAI NƠI PHẢI NHỚ — và phép kiểm nhớ hộ</h2>
     *
     * <p>Danh sách này phải trùng khít với {@code alertLevelColors} ở
     * {@code frontend/design-tokens/src/index.ts}. Java ⛔ không import được TypeScript, nên đây là
     * đúng hình dạng luật 14. Phép kiểm nhớ hộ: {@code alertLevelColors.test.ts} <b>đọc thẳng tệp
     * Java này</b> và so hai tập hợp, kèm khẳng định về <b>số lượng</b> để nó ⛔ không xanh trên tập
     * rỗng (cùng khuôn {@code error-map.test.ts} đang dùng cho mã lỗi BE ↔ FE).
     *
     * <p>⚠ Thêm một slot thì phải sửa <b>cả hai</b> tệp — bài kiểm sẽ đỏ nếu chỉ sửa một.
     *
     * <h2>⛔ Vì sao là SLOT, không phải "mỗi mức một màu"</h2>
     *
     * <p>Quy tắc 16: mức ngưỡng là <b>danh mục có CRUD</b>, thêm mức mới ⛔ không được đòi deploy
     * (G9-a). Nên danh sách này là tập <i>slot</i> để danh mục chọn vào — Công ty thêm mức thứ sáu
     * vẫn dùng lại được một slot có sẵn.
     */
    static final Set<String> KHOA_MAU_CHO_PHEP =
            Set.of("alert-level-1", "alert-level-2", "alert-level-3", "alert-level-4", "alert-level-5");

    private final AlertLevelRepository levels;
    private final AlertRuleRepository rules;
    private final PortalCachePort portalCache;

    public AlertLevelService(AlertLevelRepository levels, AlertRuleRepository rules, PortalCachePort portalCache) {
        this.levels = levels;
        this.rules = rules;
        this.portalCache = portalCache;
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
        // ⚠ Mức cảnh báo là DANH MỤC, và từ T35.14 `colorToken` + `name` của nó đi thẳng ra marker
        //   GIS lẫn bảng mực nước trên cổng. Đổi màu/tên mà ⛔ không xoá đệm là đúng lỗi T27.7 —
        //   xem javadoc PortalCachePort.hydroStationsChanged().
        portalCache.hydroStationsChanged();
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
        portalCache.hydroStationsChanged();
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
        portalCache.hydroStationsChanged();
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
        // ⭐ T35.14 — hình dạng đúng vẫn chưa đủ: khoá phải VẼ ĐƯỢC. Xem KHOA_MAU_CHO_PHEP.
        if (!KHOA_MAU_CHO_PHEP.contains(v)) {
            throw loiTruong("colorToken", "KHOA_MAU_KHONG_CO_TRONG_BANG_MAU", v);
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
