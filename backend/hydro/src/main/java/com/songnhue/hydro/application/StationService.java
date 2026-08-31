package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.domain.PositionRole;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.StationConstruction;
import com.songnhue.hydro.infra.ApiSourceRepository;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationConstructionRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Danh mục điểm đo — T28.3 / T28.8 / T28.9.
 *
 * <h2>⛔ Mã API là bất biến, và đó là luật nghiệp vụ chứ không phải thói quen</h2>
 *
 * <p>{@code api_code} là khoá nối duy nhất giữa response của nguồn và điểm đo. Đổi nó trên một điểm
 * đo đang chạy là <b>gán toàn bộ số liệu lịch sử của trạm này sang trạm khác</b> — không ràng buộc
 * nào bắt được, không màn hình nào đổi màu, biểu đồ vẫn vẽ đẹp. Nên {@link #update} từ chối bằng
 * {@code HYD-2006} thay vì lặng lẽ bỏ qua trường ấy: bỏ qua thì người dùng tưởng đã đổi.
 *
 * <h2>⚠ Phạm vi đơn vị: mọi tra cứu theo {@code public_id} phải qua {@link ScopeGuard}</h2>
 *
 * <p>{@code findByPublicIdAndDeletedAtIsNull} đi qua bộ lọc phạm vi, nên bản ghi ngoài phạm vi trả
 * về rỗng và nếu ném thẳng "không tìm thấy" thì một lần dò dữ liệu đơn vị khác trông y hệt gõ nhầm
 * đường dẫn — {@code conventions.md} §4.2.
 */
@Service
public class StationService {

    private static final Logger log = LoggerFactory.getLogger(StationService.class);

    private final StationRepository stations;
    private final StationConstructionRepository links;
    private final ApiSourceRepository sources;
    private final MeasurementTypeRepository types;
    private final OrgUnitPort orgUnits;
    private final ScopeGuard scopeGuard;

    public StationService(
            StationRepository stations,
            StationConstructionRepository links,
            ApiSourceRepository sources,
            MeasurementTypeRepository types,
            OrgUnitPort orgUnits,
            ScopeGuard scopeGuard) {
        this.stations = stations;
        this.links = links;
        this.sources = sources;
        this.types = types;
        this.orgUnits = orgUnits;
        this.scopeGuard = scopeGuard;
    }

    @Transactional(readOnly = true)
    public List<Station> list() {
        return stations.findByDeletedAtIsNullOrderByCodeAsc();
    }

    /**
     * Điểm đo <b>chưa gán đơn vị phụ trách</b> — T28.9, hệ quả trực tiếp của OI-05.
     *
     * <p>⚠ Đây không phải một bộ lọc tiện tay: cho tới khi danh sách này rỗng, resolver người nhận
     * cảnh báo (G11 tập 2) không tìm được ai để gửi. Một cảnh báo không có người nhận là một cảnh
     * báo không tồn tại, và nó không báo lỗi ở đâu cả — nên việc còn thiếu phải hiện thành một con
     * số trên màn hình.
     */
    @Transactional(readOnly = true)
    public List<Station> chuaGanDonVi() {
        return stations.findByOrgUnitIdIsNullAndDeletedAtIsNullOrderByCodeAsc();
    }

    /**
     * Số điểm đo đang trỏ vào một nguồn — để màn hình Nguồn dữ liệu hiện "19 điểm đo".
     *
     * <p>⚠ Con số này đi qua bộ lọc phạm vi như mọi truy vấn khác, nên với người dùng cấp Xí nghiệp
     * nó là "số điểm đo <i>của bạn</i> dùng nguồn này", không phải tổng toàn hệ thống. Đúng như vậy:
     * một con số vượt phạm vi hiển thị trên màn hình cũng là một dạng rò rỉ.
     */
    @Transactional(readOnly = true)
    public int soDiemDoCuaNguon(Long apiSourceId) {
        return stations.findByApiSourceIdAndDeletedAtIsNullOrderByCodeAsc(apiSourceId)
                .size();
    }

    @Transactional(readOnly = true)
    public Station get(UUID publicId) {
        return scopeGuard.require(stations.findByPublicIdAndDeletedAtIsNull(publicId), Station.class, publicId);
    }

    /** Nguồn dữ liệu của một điểm đo — để giao diện hiện mã nguồn thay vì khoá số. */
    @Transactional(readOnly = true)
    public ApiSource nguonCua(Station diemDo) {
        return sources.findById(diemDo.getApiSourceId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StationConstruction> lienKetCua(Station diemDo) {
        return links.findByStationIdAndDeletedAtIsNull(diemDo.getId());
    }

    @Transactional
    public Station create(
            String code,
            String name,
            String apiCode,
            UUID apiSourcePublicId,
            PositionRole positionRole,
            UUID orgUnitPublicId,
            Set<UUID> measurementTypePublicIds) {
        String ma = chuanHoaMa(code);
        String maApi = chuanHoaMaApi(apiCode);
        if (stations.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        if (stations.existsByApiCodeAndDeletedAtIsNull(maApi)) {
            throw new ConflictException(ErrorCode.HYD_1002, maApi);
        }
        Station diemDo = new Station(
                ma, batBuocTen(name), maApi, nguon(apiSourcePublicId).getId(), batBuoc(positionRole));
        diemDo.setOrgUnitId(donViId(orgUnitPublicId));
        diemDo.setMeasurementTypes(loaiChiSo(measurementTypePublicIds));
        log.info("Thêm điểm đo {} (mã API {})", ma, maApi);
        return stations.save(diemDo);
    }

    /**
     * Sửa hồ sơ điểm đo.
     *
     * <p>⛔ {@code apiCode} chỉ được gửi lên đúng giá trị đang có. Xem javadoc của lớp.
     *
     * <p>⚠ {@code riverName} / {@code chainage} / toạ độ nhận {@code null} là bình thường: G8 chưa
     * có dữ liệu cho 19 điểm đo. Không tự suy, không tự điền.
     */
    @Transactional
    public Station update(UUID publicId, StationForm form) {
        Station diemDo = get(publicId);
        String maApi = chuanHoaMaApi(form.apiCode());
        if (!diemDo.getApiCode().equals(maApi)) {
            throw new BusinessRuleException(ErrorCode.HYD_2006, diemDo.getApiCode(), maApi);
        }
        String ma = chuanHoaMa(form.code());
        if (stations.existsByCodeAndDeletedAtIsNullAndIdNot(ma, diemDo.getId())) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        PositionRole vaiTro = batBuoc(form.positionRole());
        kiemVaiTroKhopLienKetChinh(diemDo, vaiTro);

        diemDo.setCode(ma);
        diemDo.setName(batBuocTen(form.name()));
        // ⚠ `nguon()` ném SYS-0004 khi mã nguồn không có thật — đổi sang một nguồn không tồn tại
        // phải đỏ, không được lặng lẽ giữ nguồn cũ (đó chính là lỗi vừa vá).
        diemDo.setApiSourceId(nguon(form.apiSourcePublicId()).getId());
        diemDo.setPositionRole(vaiTro);
        diemDo.setOrgUnitId(donViId(form.orgUnitPublicId()));
        diemDo.setRiverName(rong(form.riverName()));
        diemDo.setChainage(lyTrinh(form.chainage()));
        datToaDo(diemDo, form.latitude(), form.longitude());
        diemDo.setInterpolated(form.interpolated());
        diemDo.setActive(form.active());
        diemDo.setDescription(form.description());
        diemDo.setMeasurementTypes(loaiChiSo(form.measurementTypePublicIds()));
        return stations.save(diemDo);
    }

    @Transactional
    public void delete(UUID publicId) {
        Station diemDo = get(publicId);
        diemDo.markDeleted(Instant.now());
        stations.save(diemDo);
        log.info("Xoá điểm đo {} (mã API {})", diemDo.getCode(), diemDo.getApiCode());
    }

    /**
     * Ràng buộc A2b: vai trò của liên kết <b>chính</b> phải trùng vai trò chính thức của điểm đo.
     *
     * <p>CSDL không ép được ràng buộc liên bảng này (chỉ ép nổi "mỗi điểm đo tối đa một liên kết
     * chính"), nên nó phải nằm ở đây — và phải chạy cả khi người dùng đổi {@code position_role} của
     * điểm đo, không chỉ khi sửa liên kết. Bỏ nhánh này thì hai giá trị lệch nhau lặng lẽ và biểu
     * tổng hợp xếp điểm đo vào nhầm cột TL/HL.
     */
    private void kiemVaiTroKhopLienKetChinh(Station diemDo, PositionRole vaiTroMoi) {
        links.findByStationIdAndPrimaryTrueAndDeletedAtIsNull(diemDo.getId()).ifPresent(chinh -> {
            if (chinh.getRole() != vaiTroMoi) {
                throw new BusinessRuleException(ErrorCode.HYD_2005);
            }
        });
    }

    private ApiSource nguon(UUID publicId) {
        if (publicId == null) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return sources.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /** ⚠ {@code null} hợp lệ: OI-05 chưa chốt, điểm đo chưa gán đơn vị là trạng thái có thật. */
    private Long donViId(UUID orgUnitPublicId) {
        if (orgUnitPublicId == null) {
            return null;
        }
        return orgUnits.findRef(orgUnitPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004))
                .id();
    }

    private Set<MeasurementType> loaiChiSo(Set<UUID> publicIds) {
        Set<MeasurementType> ket = new LinkedHashSet<>();
        if (publicIds == null) {
            return ket;
        }
        for (UUID id : publicIds) {
            ket.add(types.findByPublicIdAndDeletedAtIsNull(id)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004)));
        }
        return ket;
    }

    /**
     * Toạ độ đi theo cặp.
     *
     * <p>Một nửa toạ độ là một điểm sai trên bản đồ, tệ hơn hẳn việc chưa số hoá — chưa số hoá thì
     * còn nằm trong danh sách nhắc việc. CSDL có {@code ck_stations_coords_paired} canh nốt.
     */
    private static void datToaDo(Station diemDo, BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        diemDo.setLatitude(latitude);
        diemDo.setLongitude(longitude);
    }

    private static String lyTrinh(String chainage) {
        String rut = rong(chainage);
        if (rut != null && !rut.matches("^K[0-9]+\\+[0-9]{1,3}$")) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return rut;
    }

    private static String rong(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String chuanHoaMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ma.trim().toUpperCase(Locale.ROOT);
    }

    /** Dạng {@code F} + 5 chữ số — khớp {@code ck_stations_api_code_format}. */
    private static String chuanHoaMaApi(String maApi) {
        if (maApi == null || !maApi.trim().toUpperCase(Locale.ROOT).matches("^F[0-9]{5}$")) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return maApi.trim().toUpperCase(Locale.ROOT);
    }

    private static String batBuocTen(String ten) {
        if (ten == null || ten.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ten.trim();
    }

    private static <T> T batBuoc(T value) {
        if (value == null) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return value;
    }
}
