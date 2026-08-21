package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionCluster;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.infra.ConstructionClusterRepository;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Hồ sơ công trình — CN-02.1, CN-02.6.
 *
 * <h2>⭐ Module nghiệp vụ đầu tiên đi qua cửa SPI</h2>
 *
 * Lớp này chỉ được import {@code core.spi.*} và {@code core.common.*} — luật ArchUnit từ WS-10.
 * Suốt Phase 0 luật đó chạy qua tập rỗng vì bốn module nghiệp vụ chưa có dòng mã nào; đây là nơi nó
 * lần đầu chạy thật, gồm cả {@code NotificationPort} — cửa mà lỗi giao dịch {@code readOnly} từng
 * nằm im sau bốn WS ({@code architecture-review.md} §10.21).
 *
 * <h2>Ranh giới trách nhiệm</h2>
 *
 * <ul>
 *   <li><b>Phạm vi đơn vị</b> — không có một dòng {@code WHERE org_unit_id} nào ở đây.
 *       {@code ScopeFilterAspect} lo, và {@link ScopeGuard} phân biệt "không có" với "có nhưng thuộc
 *       đơn vị khác" ({@code AUTH-3002} + ghi {@code security_events}).
 *   <li><b>Trạng thái vận hành</b> — {@link ConstructionStatusService} là nơi duy nhất ghi.
 *   <li><b>Tài liệu đính kèm</b> — {@code ConstructionDocumentService}, đi qua {@code AttachmentPort}.
 * </ul>
 */
@Service
public class ConstructionService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionService.class);

    /** Định dạng lý trình {@code K<km>+<m>} — trùng với CHECK ở CSDL, cố ý viết hai nơi. */
    private static final Pattern LY_TRINH = Pattern.compile("^K\\d+\\+\\d{1,3}$");

    /**
     * Trường được phép sắp xếp trên danh sách.
     *
     * <p>Bảng trắng chứ không nhận tên cột tự do: {@code Pageable} dịch thẳng tên trường sang HQL,
     * nên tên tự do là đường đọc dữ liệu ngoài ý muốn — kể cả trường không hiện trên giao diện.
     */
    private static final Set<String> SAP_XEP_CHO_PHEP =
            Set.of("code", "name", "constructionType", "operationalStatus", "riverName", "chainageM", "createdAt");

    /** Tiền tố mã gợi ý theo loại công trình — CN-02.1 nêu ví dụ {@code TB-SN-001}, {@code CG-SN-001}. */
    private static final java.util.Map<ConstructionType, String> TIEN_TO_MA = java.util.Map.of(
            ConstructionType.TRAM_BOM, "TB",
            ConstructionType.CONG, "CG",
            ConstructionType.KENH_MUONG, "KM",
            ConstructionType.DE_DIEU, "DD",
            ConstructionType.KHAC, "CT");

    private final ConstructionRepository constructions;
    private final ConstructionClusterRepository clusters;
    private final ConstructionStatusService trangThai;
    private final ScopeGuard scopeGuard;
    private final OrgUnitPort orgUnits;
    private final NotificationPort notifications;

    public ConstructionService(
            ConstructionRepository constructions,
            ConstructionClusterRepository clusters,
            ConstructionStatusService trangThai,
            ScopeGuard scopeGuard,
            OrgUnitPort orgUnits,
            NotificationPort notifications) {
        this.constructions = constructions;
        this.clusters = clusters;
        this.trangThai = trangThai;
        this.scopeGuard = scopeGuard;
        this.orgUnits = orgUnits;
        this.notifications = notifications;
    }

    // === Đọc =================================================================

    @Transactional(readOnly = true)
    public Construction get(UUID publicId) {
        return trongPhamVi(publicId);
    }

    /**
     * Tra một công trình trong phạm vi của người đăng nhập.
     *
     * <p>⚠ Tách riêng khỏi {@link #get} vì các hàm ghi bên dưới cũng cần nó, mà <b>gọi thẳng
     * {@code get()} bằng {@code this} thì không đi qua proxy Spring</b> — chú thích giao dịch mất tác
     * dụng, và ở đây còn tệ hơn thế: nếu nó <i>có</i> tác dụng thì lượt ghi sẽ chạy trong giao dịch
     * {@code readOnly}. Dự án đã sập bẫy tự gọi hàm {@code @Transactional} hai lần
     * ({@code BackupService} ở WS-7, {@code ViewCountService} ở WS-16) nên nay có luật ArchUnit cấm.
     */
    private Construction trongPhamVi(UUID publicId) {
        return scopeGuard.require(
                constructions.findByPublicIdAndDeletedAtIsNull(publicId), Construction.class, publicId);
    }

    @Transactional(readOnly = true)
    public Page<Construction> search(ConstructionFilter filter, Pageable pageable) {
        return constructions.search(
                filter.tuKhoaLike(),
                filter.loai(),
                filter.trangThai(),
                filter.vongDoi(),
                filter.capQuanLy(),
                filter.donViId(),
                donViCumId(filter.cumPublicId()),
                filter.tuyenSong(),
                filter.chuaSoHoa(),
                pageable);
    }

    /** Điểm để vẽ bản đồ — chỉ công trình đã số hoá toạ độ (M2.9). */
    @Transactional(readOnly = true)
    public List<Construction> mapPoints(ConstructionType loai) {
        return constructions.mapPoints(loai);
    }

    @Transactional(readOnly = true)
    public List<String> rivers() {
        return constructions.distinctRivers();
    }

    public static Set<String> allowedSortFields() {
        return SAP_XEP_CHO_PHEP;
    }

    // === Ghi =================================================================

    @Transactional
    public Construction create(ConstructionForm form) {
        String code = chuanHoaMa(form.code());
        if (constructions.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ConflictException(ErrorCode.OPS_2008, code);
        }
        OrgUnitRef donVi = donVi(form.orgUnitPublicId());

        Construction ct = new Construction(code, chuanHoaTen(form.name()), form.constructionType(), donVi.id());
        apDung(ct, form);
        trangThai.recompute(ct);

        Construction saved = constructions.save(ct);
        log.info("Thêm công trình {} ({}) thuộc đơn vị {}", saved.getCode(), saved.getConstructionType(), donVi.code());
        return saved;
    }

    @Transactional
    public Construction update(UUID publicId, ConstructionForm form) {
        Construction ct = trongPhamVi(publicId);
        String code = chuanHoaMa(form.code());
        if (constructions.existsByCodeAndDeletedAtIsNullAndIdNot(code, ct.getId())) {
            throw new ConflictException(ErrorCode.OPS_2008, code);
        }

        Long donViCu = ct.getOrgUnitId();
        OrgUnitRef donViMoi = donVi(form.orgUnitPublicId());

        ct.setCode(code);
        ct.setName(chuanHoaTen(form.name()));
        ct.setConstructionType(form.constructionType());
        ct.setOrgUnitId(donViMoi.id());
        apDung(ct, form);
        trangThai.recompute(ct);

        Construction saved = constructions.save(ct);
        if (!donViMoi.id().equals(donViCu)) {
            baoBanGiao(saved, donViCu, donViMoi);
        }
        return saved;
    }

    /**
     * Đổi vòng đời — endpoint riêng, có lý do.
     *
     * <p>Không gộp vào {@link #update}: thanh lý một công trình kéo theo ngừng nhận bản ghi bảo trì
     * ({@code OPS-2002}), đổi màu trên bản đồ điều hành và gửi thông báo. Một quyết định như vậy
     * không nên xảy ra như tác dụng phụ của lượt sửa số điện thoại.
     */
    @Transactional
    public Construction changeLifecycle(UUID publicId, LifecycleState state, String lyDo) {
        Construction ct = trongPhamVi(publicId);
        LifecycleState truoc = ct.getLifecycleState();
        if (truoc == state) {
            return ct;
        }
        ct.setLifecycleState(state);
        trangThai.recompute(ct);
        Construction saved = constructions.save(ct);

        log.info("Công trình {} đổi vòng đời {} → {}: {}", saved.getCode(), truoc, state, lyDo);
        if (state == LifecycleState.DA_THANH_LY) {
            notifications.notify(NotifyRequest.alert(
                    "CONSTRUCTION_DECOMMISSIONED",
                    "Thanh lý công trình %s".formatted(saved.getName()),
                    "Công trình %s (%s) đã chuyển sang trạng thái Đã thanh lý. Lý do: %s"
                            .formatted(saved.getName(), saved.getCode(), lyDo == null ? "(không ghi)" : lyDo),
                    NotifySeverity.WARNING,
                    List.of(saved.getOrgUnitId())));
        }
        return saved;
    }

    /** Xoá mềm. Hồ sơ đã có lịch sử thao tác không được xoá thật — nhật ký sẽ trỏ vào khoảng không. */
    @Transactional
    public void delete(UUID publicId) {
        Construction ct = trongPhamVi(publicId);
        ct.markDeleted(Instant.now());
        constructions.save(ct);
        log.info("Xoá mềm công trình {}", ct.getCode());
    }

    /**
     * Mã gợi ý dạng {@code <LOẠI>-<MÃ ĐƠN VỊ>-<số>} — điểm nghiệp vụ 13.
     *
     * <p><b>Gợi ý, không phải áp đặt.</b> Công ty đã có mã in trên hồ sơ giấy; ép tự sinh là buộc họ
     * đổi thứ đang dùng. Chốt chặn trùng lặp không nằm ở hàm này mà ở chỉ mục duy nhất của CSDL cộng
     * với {@code OPS-2008} — một gợi ý sai thì người dùng thấy ngay, còn một chỗ kiểm trùng bị bỏ sót
     * thì không ai thấy.
     */
    @Transactional(readOnly = true)
    public String suggestCode(ConstructionType loai, UUID orgUnitPublicId) {
        OrgUnitRef donVi = donVi(orgUnitPublicId);
        String tienTo = "%s-%s-".formatted(TIEN_TO_MA.getOrDefault(loai, "CT"), donVi.code());
        int lonNhat = 0;
        for (String ma : constructions.codesStartingWith(tienTo)) {
            String duoi = ma.substring(tienTo.length());
            if (duoi.chars().allMatch(Character::isDigit) && !duoi.isEmpty()) {
                lonNhat = Math.max(lonNhat, Integer.parseInt(duoi));
            }
        }
        return "%s%03d".formatted(tienTo, lonNhat + 1);
    }

    // === Nội bộ ==============================================================

    /** Gán các trường sửa được. Cố ý không đụng tới mã, tên, loại, đơn vị — nơi gọi đã xử lý. */
    private void apDung(Construction ct, ConstructionForm form) {
        kiemToaDo(form);
        kiemLyTrinh(form.chainage());

        ct.setPurpose(form.purpose());
        ct.setManagementLevel(form.managementLevel() == null ? ManagementLevel.XI_NGHIEP : form.managementLevel());
        ct.setClusterId(cum(ct, form));
        ct.setAddress(form.address());
        ct.datToaDo(form.latitude(), form.longitude());
        ct.setRiverName(form.riverName());
        ct.setChainage(form.chainage());
        ct.setBasinNote(form.basinNote());
        ct.setBuiltYear(form.builtYear());
        ct.setCommissionedYear(form.commissionedYear());
        ct.setDesigner(form.designer());
        ct.setContractor(form.contractor());
        ct.setTotalInvestment(form.totalInvestment());
        ct.setDescription(form.description());

        apDungThongSo(ct, form);
    }

    /**
     * Gán thông số kỹ thuật đúng loại, và <b>xoá sạch thông số của loại khác</b>.
     *
     * <p>Bước xoá mới là phần dễ quên: đổi một hồ sơ từ Trạm bơm sang Cống mà không dọn thì bảng
     * {@code pump_station_specs} còn lại một dòng mồ côi, biểu mẫu không hiện nó nữa nhưng báo cáo
     * "tổng công suất trạm bơm" vẫn cộng vào. Sai số kiểu đó không có triệu chứng nào ngoài một con
     * số lớn hơn thực tế.
     */
    private void apDungThongSo(Construction ct, ConstructionForm form) {
        ConstructionType loai = form.constructionType();
        if (form.pump() != null && loai != ConstructionType.TRAM_BOM) {
            throw new BusinessRuleException(ErrorCode.OPS_2009, "trạm bơm");
        }
        if (form.sluice() != null && loai != ConstructionType.CONG) {
            throw new BusinessRuleException(ErrorCode.OPS_2009, "cống");
        }
        if (form.linear() != null && !loai.laCongTrinhTuyen()) {
            throw new BusinessRuleException(ErrorCode.OPS_2009, "kênh mương / đê điều");
        }

        ganThongSoBom(ct, loai == ConstructionType.TRAM_BOM ? form.pump() : null);
        ganThongSoCong(ct, loai == ConstructionType.CONG ? form.sluice() : null);
        ganThongSoTuyen(ct, loai.laCongTrinhTuyen() ? form.linear() : null);
    }

    private static void ganThongSoBom(Construction ct, ConstructionForm.PumpSpec spec) {
        ct.setTotalPowerKw(spec == null ? null : spec.totalPowerKw());
        ct.setPumpCount(spec == null ? null : spec.pumpCount());
        ct.setStandbyPumpCount(spec == null ? null : spec.standbyPumpCount());
        ct.setFlowPerPumpM3s(spec == null ? null : spec.flowPerPumpM3s());
        ct.setHeadM(spec == null ? null : spec.headM());
        ct.setPowerSource(spec == null ? null : spec.powerSource());
        ct.setVoltageKv(spec == null ? null : spec.voltageKv());
        ct.setOperatingLevelMinM(spec == null ? null : spec.operatingLevelMinM());
        ct.setOperatingLevelMaxM(spec == null ? null : spec.operatingLevelMaxM());
    }

    private static void ganThongSoCong(Construction ct, ConstructionForm.SluiceSpec spec) {
        ct.setSluiceType(spec == null ? null : spec.sluiceType());
        ct.setBayCount(spec == null ? null : spec.bayCount());
        ct.setBayWidthM(spec == null ? null : spec.bayWidthM());
        ct.setSillElevationM(spec == null ? null : spec.sillElevationM());
        ct.setSluiceCrestElevationM(spec == null ? null : spec.crestElevationM());
        ct.setSluiceDesignFlowM3s(spec == null ? null : spec.designFlowM3s());
        ct.setGateOperation(spec == null ? null : spec.gateOperation());
        ct.setUpstreamWarningLevelM(spec == null ? null : spec.upstreamWarningLevelM());
        ct.setUpstreamDangerLevelM(spec == null ? null : spec.upstreamDangerLevelM());
    }

    private static void ganThongSoTuyen(Construction ct, ConstructionForm.LinearSpec spec) {
        ct.setLengthKm(spec == null ? null : spec.lengthKm());
        ct.setStartChainage(spec == null ? null : spec.startChainage());
        ct.setEndChainage(spec == null ? null : spec.endChainage());
        ct.setLinearDesignFlowM3s(spec == null ? null : spec.designFlowM3s());
        ct.setLinearCrestElevationM(spec == null ? null : spec.crestElevationM());
        ct.setTechnicalGrade(spec == null ? null : spec.technicalGrade());
        ct.setCrossSection(spec == null ? null : spec.crossSection());
        ct.setSpecNote(spec == null ? null : spec.specNote());
    }

    private Long cum(Construction ct, ConstructionForm form) {
        ManagementLevel cap = form.managementLevel();
        if (form.clusterPublicId() == null) {
            if (cap == ManagementLevel.CUM) {
                throw new ValidationException(ErrorCode.OPS_2013);
            }
            return null;
        }
        ConstructionCluster cum = clusters.findByPublicIdAndDeletedAtIsNull(form.clusterPublicId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        log.debug("Công trình {} gắn cụm {}", ct.getCode(), cum.getCode());
        return cum.getId();
    }

    private Long donViCumId(UUID cumPublicId) {
        return cumPublicId == null
                ? null
                : clusters.findByPublicIdAndDeletedAtIsNull(cumPublicId)
                        .map(ConstructionCluster::getId)
                        .orElse(-1L); // lọc theo một cụm không tồn tại → không ra bản ghi nào
    }

    private OrgUnitRef donVi(UUID publicId) {
        if (publicId == null) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return orgUnits.findRef(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static void kiemToaDo(ConstructionForm form) {
        BigDecimal lat = form.latitude();
        BigDecimal lng = form.longitude();
        if ((lat == null) != (lng == null)) {
            throw new ValidationException(ErrorCode.OPS_2010);
        }
    }

    private static void kiemLyTrinh(String chainage) {
        if (chainage != null
                && !chainage.isBlank()
                && !LY_TRINH.matcher(chainage).matches()) {
            throw new ValidationException(ErrorCode.OPS_2011, chainage);
        }
    }

    /**
     * Bàn giao công trình sang đơn vị khác.
     *
     * <p>Gửi cho cả hai đơn vị: bên nhận cần biết mình có thêm việc, bên giao cần biết hồ sơ đã rời
     * danh sách của mình. Đây là sự kiện <b>hiếm</b> — vài lần một năm — nên gửi cả nhóm Ban điều
     * hành là hợp lý; nếu là việc xảy ra hằng ngày thì đúng cách phải là {@code targeted}, vì thông
     * báo gửi tràn lan sẽ dẫn tới không ai đọc thông báo nữa và cảnh báo sự cố thật chết theo
     * ({@code architecture-review.md} §10.10).
     */
    private void baoBanGiao(Construction ct, Long donViCu, OrgUnitRef donViMoi) {
        log.info("Công trình {} chuyển đơn vị phụ trách {} → {}", ct.getCode(), donViCu, donViMoi.code());
        notifications.notify(NotifyRequest.alert(
                "CONSTRUCTION_TRANSFERRED",
                "Bàn giao công trình %s".formatted(ct.getName()),
                "Công trình %s (%s) chuyển sang đơn vị phụ trách %s."
                        .formatted(ct.getName(), ct.getCode(), donViMoi.name()),
                NotifySeverity.INFO,
                List.of(donViCu, donViMoi.id())));
    }

    private static String chuanHoaMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ma.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String chuanHoaTen(String ten) {
        if (ten == null || ten.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ten.trim();
    }
}
