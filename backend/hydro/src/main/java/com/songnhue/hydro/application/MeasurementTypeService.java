package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Danh mục loại chỉ số quan trắc — T28.1 / T28.8.
 *
 * <p>⚠ Ba loại đã seed ở {@code V202608311049}. Riêng <b>Lượng mưa</b> giữ lại dù v1 chưa có nguồn
 * tự động (G3-a: {@code bhh40.net} chỉ có {@code getmn.aspx}) — xoá nó đi thì màn hình nhập tay cũng
 * mất chỗ đứng, và cột "lượng mưa" của biểu §5.2 không còn nơi nào để nhập.
 */
@Service
public class MeasurementTypeService {

    private static final Logger log = LoggerFactory.getLogger(MeasurementTypeService.class);

    private final MeasurementTypeRepository types;
    private final StationRepository stations;

    public MeasurementTypeService(MeasurementTypeRepository types, StationRepository stations) {
        this.types = types;
        this.stations = stations;
    }

    @Transactional(readOnly = true)
    public List<MeasurementType> list() {
        return types.findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public MeasurementType get(UUID publicId) {
        return tim(publicId);
    }

    @Transactional
    public MeasurementType create(
            String code,
            String name,
            String unit,
            Short valueScale,
            Integer sortOrder,
            Boolean active,
            String description) {
        String ma = chuanHoaMa(code);
        if (types.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        MeasurementType loai = new MeasurementType(ma, chuanHoaTen(name), chuanHoaTen(unit), thangDo(valueScale));
        loai.setSortOrder(sortOrder == null ? 0 : sortOrder);
        // ⚠ Thêm 01/09/2026: `active` được DTO nhận và validate rồi bị bỏ rơi ở controller — bỏ tick
        // "Đang dùng" lúc tạo vẫn ra một bản ghi đang dùng, và màn hình báo thành công. Không gửi
        // trường thì mặc định là đang dùng (thêm một loại chỉ số để dùng là trường hợp thường).
        loai.setActive(active == null || active);
        loai.setDescription(description);
        log.info("Thêm loại chỉ số quan trắc {}", ma);
        return types.save(loai);
    }

    @Transactional
    public MeasurementType update(
            UUID publicId,
            String code,
            String name,
            String unit,
            Short valueScale,
            Integer sortOrder,
            boolean active,
            String description) {
        MeasurementType loai = tim(publicId);
        String ma = chuanHoaMa(code);
        if (types.existsByCodeAndDeletedAtIsNullAndIdNot(ma, loai.getId())) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        loai.setCode(ma);
        loai.setName(chuanHoaTen(name));
        loai.setUnit(chuanHoaTen(unit));
        loai.setValueScale(thangDo(valueScale));
        loai.setSortOrder(sortOrder == null ? 0 : sortOrder);
        loai.setActive(active);
        loai.setDescription(description);
        return types.save(loai);
    }

    /**
     * Xoá mềm một loại chỉ số.
     *
     * <p>Chặn khi còn điểm đo đang gắn, thay vì tự gỡ liên kết: gỡ tự động thì một cú bấm nhầm làm
     * các điểm đo mất loại chỉ số, và <b>không có gì hoàn tác được</b> — bảng nối không lưu lịch sử,
     * nên không nơi nào ghi lại "trước đó điểm đo này đo cái gì".
     */
    @Transactional
    public void delete(UUID publicId) {
        MeasurementType loai = tim(publicId);
        long dangDung = stations.findByDeletedAtIsNullOrderByCodeAsc().stream()
                .filter(s ->
                        s.getMeasurementTypes().stream().anyMatch(t -> t.getId().equals(loai.getId())))
                .count();
        if (dangDung > 0) {
            throw new ConflictException(ErrorCode.HYD_1002, loai.getCode());
        }
        loai.markDeleted(Instant.now());
        types.save(loai);
        log.info("Xoá loại chỉ số quan trắc {}", loai.getCode());
    }

    private MeasurementType tim(UUID publicId) {
        return types.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static String chuanHoaMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ma.trim().toUpperCase(Locale.ROOT);
    }

    private static String chuanHoaTen(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return text.trim();
    }

    /** Khớp {@code ck_measurement_types_scale} — chặn ở tầng dịch vụ để lỗi có câu chữ, không phải SQLState. */
    private static short thangDo(Short valueScale) {
        short scale = valueScale == null ? 3 : valueScale;
        if (scale < 0 || scale > 6) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return scale;
    }
}
