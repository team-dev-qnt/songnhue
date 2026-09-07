package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.hydro.domain.AlertConditionType;
import com.songnhue.hydro.domain.AlertLevel;
import com.songnhue.hydro.domain.AlertRule;
import com.songnhue.hydro.domain.DieuKienNguong;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.infra.AlertLevelRepository;
import com.songnhue.hydro.infra.AlertRuleRepository;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Cấu hình ngưỡng cảnh báo — <b>T33.2 · T33.11</b> (hạng mục nghiệm thu <b>G9</b>).
 *
 * <h2>⭐ Bất biến của điều kiện ép ở HÀM DỰNG của {@link DieuKienNguong}, ⛔ không ở đây</h2>
 *
 * <p>{@code OUT_OF_RANGE} thiếu cận trên, khoảng đảo ngược, tốc độ đổi âm — cả ba đều nổ khi dựng
 * {@code DieuKienNguong}, và lớp này chỉ việc gọi nó một lần trước khi lưu. Nghĩa là <b>không có
 * đường nào</b> để một dòng {@code alert_rules} hỏng đi vào CSDL, kể cả nếu ai đó thêm một đường ghi
 * thứ hai sau này — quy tắc 16 ở dạng áp cho mã: <i>ràng buộc ép ở hàm dựng, ⛔ không ở lời dặn</i>.
 *
 * <p>Ba tầng cùng canh một luật, và ba tầng ấy ⛔ không thừa: CHECK của CSDL là thứ không lối nào
 * lách được · {@code DieuKienNguong} là thứ nổ sớm với một câu đọc được · lớp này là thứ gắn được
 * <b>tên trường</b> vào lỗi để màn hình chỉ đúng ô sai.
 */
@Service
public class AlertRuleService {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleService.class);

    private final AlertRuleRepository rules;
    private final AlertLevelRepository levels;
    private final StationRepository stations;
    private final MeasurementTypeRepository types;

    public AlertRuleService(
            AlertRuleRepository rules,
            AlertLevelRepository levels,
            StationRepository stations,
            MeasurementTypeRepository types) {
        this.rules = rules;
        this.levels = levels;
        this.stations = stations;
        this.types = types;
    }

    @Transactional(readOnly = true)
    public List<AlertRule> list(UUID diemDoPublicId) {
        if (diemDoPublicId == null) {
            return rules.findByDeletedAtIsNullOrderByIdAsc();
        }
        Station diemDo = timDiemDo(diemDoPublicId);
        return rules.findByStationIdAndDeletedAtIsNullOrderByIdAsc(diemDo.getId());
    }

    /**
     * Điểm đo <b>chưa cấu hình ngưỡng nào</b> — T33.6.
     *
     * <p>⭐ Đây là nửa <b>đọc</b> của {@code HYD-2003}. Không có màn hình này thì <i>"chưa cấu hình
     * ngưỡng"</i> là một trạng thái đúng mà ⛔ không ai nhìn thấy, và ngày Công ty đưa bộ mức thật
     * (G9-a) sẽ không ai biết còn thiếu những điểm nào — cho tới lúc một trận lũ đi qua trong im
     * lặng.
     *
     * <p>⚠ Chỉ tính điểm đo {@code active}: một điểm đã ngừng dùng ⛔ không phải một việc còn nợ.
     */
    @Transactional(readOnly = true)
    public List<Station> diemDoChuaCauHinh() {
        Set<Long> daCo = Set.copyOf(rules.diemDoDaCauHinh());
        return stations.findByDeletedAtIsNullOrderByCodeAsc().stream()
                .filter(Station::isActive)
                .filter(s -> !daCo.contains(s.getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public AlertRule create(AlertRuleForm form) {
        Station diemDo = timDiemDo(form.diemDoPublicId());
        MeasurementType loaiChiSo = timLoaiChiSo(form.maLoaiChiSo());
        AlertLevel muc = timMuc(form.mucPublicId());

        if (rules.existsByStationIdAndMeasurementTypeIdAndAlertLevelIdAndDeletedAtIsNull(
                diemDo.getId(), loaiChiSo.getId(), muc.getId())) {
            throw new ConflictException(ErrorCode.HYD_2009);
        }
        kiemDieuKien(form.loai(), form.nguong(), form.nguongCao());

        AlertRule quyTac = new AlertRule(diemDo, loaiChiSo, muc, form.loai(), form.nguong());
        apDung(quyTac, form);
        AlertRule daLuu = rules.save(quyTac);
        log.info(
                "Thêm ngưỡng {} cho {} · {} · mức {}",
                form.loai(),
                diemDo.getCode(),
                loaiChiSo.getCode(),
                muc.getCode());
        return daLuu;
    }

    @Transactional
    public AlertRule update(
            UUID publicId,
            AlertConditionType loai,
            BigDecimal nguong,
            BigDecimal nguongCao,
            Integer treTrongPhut,
            Boolean active,
            String note) {
        AlertRule quyTac = tim(publicId);
        kiemDieuKien(loai, nguong, nguongCao);
        // ⛔ Bộ ba (điểm đo × loại chỉ số × mức) là BẤT BIẾN của một dòng ngưỡng — sửa nó là gán
        //   lịch sử cảnh báo của bộ ba này sang một bộ ba khác, đúng họ với HYD-2006 của `api_code`.
        //   Muốn đổi thì xoá rồi khai lại, và lúc ấy lịch sử ở lại với dòng cũ như nó phải thế.
        // ⭐ Đường TẠO và đường SỬA dùng CHUNG `apDụng` — hai khối setter song song là cách chắc
        //   chắn để một trường mới chỉ được thêm vào một nửa (N1/T28.33 đã trả giá đúng hình dạng ấy).
        quyTac.setConditionType(loai);
        quyTac.setThresholdValue(nguong);
        apDung(quyTac, new AlertRuleForm(null, null, null, loai, nguong, nguongCao, treTrongPhut, active, note));
        return quyTac;
    }

    /** ⭐ Dùng chung cho cả hai đường ghi — xem ghi chú ở {@link #update}. */
    private static void apDung(AlertRule quyTac, AlertRuleForm form) {
        quyTac.setConditionType(form.loai());
        quyTac.setThresholdValue(form.nguong());
        quyTac.setThresholdValueHigh(form.loai() == AlertConditionType.OUT_OF_RANGE ? form.nguongCao() : null);
        quyTac.setDelayMinutes(tre(form.treTrongPhut()));
        quyTac.setActive(form.active() == null || form.active());
        quyTac.setNote(form.note());
    }

    /**
     * Xoá mềm một ngưỡng.
     *
     * <p>⚠ Cảnh báo đang mở của ngưỡng ấy ⛔ <b>không</b> bị đóng theo, và đó là chủ ý: một sự kiện
     * đã xảy ra thì đã xảy ra. Nhưng nó cũng ⛔ không còn được đánh giá lại (câu {@code SQL_NGUONG}
     * lọc {@code deleted_at IS NULL}), nên nó ở lại "đang xảy ra" cho tới khi có người đóng tay.
     * Màn hình lịch sử vì thế phải cho đóng tay — xem {@code AlertEventService.dong}.
     */
    @Transactional
    public void delete(UUID publicId) {
        AlertRule quyTac = tim(publicId);
        quyTac.markDeleted(Instant.now());
        log.info("Xoá mềm ngưỡng #{}", quyTac.getId());
    }

    /**
     * ⭐ Kiểm bằng cách <b>dựng</b> {@link DieuKienNguong} — ⛔ không chép lại luật của nó ở đây.
     *
     * <p>Chép lại là tạo ra hai bản của một luật, và luật 14 nói thẳng chuyện gì xảy ra sau đó. Ở
     * đây chỉ làm đúng một việc thêm: đổi {@code IllegalArgumentException} (thông điệp cho lập trình
     * viên) thành {@code SYS-0003} <b>kèm tên trường</b> (thông điệp cho người đang nhập).
     */
    private static void kiemDieuKien(AlertConditionType loai, BigDecimal nguong, BigDecimal nguongCao) {
        if (loai == null) {
            throw loiTruong("conditionType", "BAT_BUOC", null);
        }
        if (nguong == null) {
            throw loiTruong("thresholdValue", "BAT_BUOC", null);
        }
        try {
            new DieuKienNguong(loai, nguong, loai == AlertConditionType.OUT_OF_RANGE ? nguongCao : null);
        } catch (IllegalArgumentException e) {
            String truong = loai == AlertConditionType.OUT_OF_RANGE ? "thresholdValueHigh" : "thresholdValue";
            throw loiTruong(truong, "SAI_DIEU_KIEN", String.valueOf(nguongCao));
        }
    }

    private static int tre(Integer treTrongPhut) {
        int v = treTrongPhut == null ? 0 : treTrongPhut;
        if (v < 0 || v > 1440) {
            throw loiTruong("delayMinutes", "NGOAI_KHOANG", String.valueOf(treTrongPhut));
        }
        return v;
    }

    private AlertRule tim(UUID publicId) {
        return rules.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private Station timDiemDo(UUID publicId) {
        return stations.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private MeasurementType timLoaiChiSo(String ma) {
        if (ma == null || ma.isBlank()) {
            throw loiTruong("measurementTypeCode", "BAT_BUOC", ma);
        }
        return types.findByCodeAndDeletedAtIsNull(ma.trim())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private AlertLevel timMuc(UUID publicId) {
        if (publicId == null) {
            throw loiTruong("alertLevelId", "BAT_BUOC", null);
        }
        return levels.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static ValidationException loiTruong(String truong, String maViPham, String giaTri) {
        return (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail(truong, maViPham, giaTri);
    }
}
