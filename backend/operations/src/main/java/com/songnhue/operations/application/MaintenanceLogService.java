package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.CodeGenerator;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.core.spi.SettingPort;
import com.songnhue.core.spi.UserDirectoryPort;
import com.songnhue.core.spi.WorkflowPort;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.MaintenanceLog;
import com.songnhue.operations.domain.MaintenanceState;
import com.songnhue.operations.domain.MaintenanceType;
import com.songnhue.operations.infra.MaintenanceLogRepository;

/**
 * Lịch sử sửa chữa / bảo trì / khắc phục sự cố — CN-02.2, WS-18.
 *
 * <h2>⭐ Đây là nơi chuỗi suy ra trạng thái công trình có đầu vào đầu tiên</h2>
 *
 * Suốt WS-17, {@link ConstructionStatusService} chỉ có mắt xích cuối (vòng đời) vì bốn mắt xích trên
 * cần dữ liệu chưa tồn tại. Lớp này sinh ra dữ liệu của hai mắt xích đầu — sự cố đang mở và bảo trì
 * đang thực hiện. Mọi lượt ghi ở đây <b>đều gọi lại</b>
 * {@link ConstructionStatusService#recompute(Construction)}: bỏ sót một đường thì cột trạng thái
 * lệch khỏi dữ liệu sinh ra nó, và sai lệch đó không có triệu chứng nào ngoài màu marker trên bản
 * đồ điều hành.
 *
 * <h2>⛔ Sự cố không phải entity riêng — quy tắc 15</h2>
 *
 * Không có {@code IncidentService} song song. Cùng một lớp, cùng một bảng, phân biệt bằng
 * {@link MaintenanceType#KHAC_PHUC_SU_CO}. Hai đường vào khác nhau ở tầng controller (quyền khác
 * nhau), không phải ở tầng này.
 */
@Service
public class MaintenanceLogService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceLogService.class);

    /** Tiền tố mã bản ghi theo CN-02.2: {@code BT-2026-0001}. */
    private static final String MA_TIEN_TO = "BT";

    private static final int MA_SO_CHU_SO = 4;

    /** Xem {@code ops.maintenance.author-edit-window-minutes} ở migration WS-18. */
    public static final String KEY_AUTHOR_EDIT_WINDOW = "ops.maintenance.author-edit-window-minutes";

    private static final Set<String> SAP_XEP_CHO_PHEP =
            Set.of("code", "startedOn", "completedOn", "workType", "severity", "status", "cost", "createdAt");

    private final MaintenanceLogRepository logs;
    private final ConstructionService constructions;
    private final ConstructionStatusService trangThai;
    private final WorkflowPort workflow;
    private final CodeGenerator codes;
    private final OrgUnitPort orgUnits;
    private final UserDirectoryPort users;
    private final NotificationPort notifications;
    private final SettingPort settings;
    private final ScopeGuard scopeGuard;

    // CHECKSTYLE.OFF: ParameterNumber - hàm dựng tiêm phụ thuộc; gói lại thành một "context" object
    // chỉ giấu số phụ thuộc đi chứ không giảm nó, và làm mất luôn khả năng thấy lớp nào đang phình.
    public MaintenanceLogService(
            MaintenanceLogRepository logs,
            ConstructionService constructions,
            ConstructionStatusService trangThai,
            WorkflowPort workflow,
            CodeGenerator codes,
            OrgUnitPort orgUnits,
            UserDirectoryPort users,
            NotificationPort notifications,
            SettingPort settings,
            ScopeGuard scopeGuard) {
        this.logs = logs;
        this.constructions = constructions;
        this.trangThai = trangThai;
        this.workflow = workflow;
        this.codes = codes;
        this.orgUnits = orgUnits;
        this.users = users;
        this.notifications = notifications;
        this.settings = settings;
        this.scopeGuard = scopeGuard;
    }
    // CHECKSTYLE.ON: ParameterNumber

    public static Set<String> allowedSortFields() {
        return SAP_XEP_CHO_PHEP;
    }

    // === Đọc =================================================================

    @Transactional(readOnly = true)
    public MaintenanceLog get(UUID publicId) {
        return trongPhamVi(publicId);
    }

    /** Xem chú thích cùng tên ở {@code ConstructionService}: tự gọi hàm {@code @Transactional} là bẫy. */
    private MaintenanceLog trongPhamVi(UUID publicId) {
        return scopeGuard.require(logs.findByPublicIdAndDeletedAtIsNull(publicId), MaintenanceLog.class, publicId);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceLog> search(MaintenanceFilter filter, Pageable pageable) {
        return logs.search(
                congTrinhId(filter.constructionPublicId()),
                filter.workType(),
                filter.severity(),
                filter.status(),
                donViId(filter.performerOrgUnitPublicId()),
                filter.from(),
                filter.to(),
                filter.keywordLike(),
                pageable);
    }

    /** Nút giao diện được phép bấm — FE không tự suy ra từ trạng thái (conventions.md §3). */
    @Transactional(readOnly = true)
    public List<AllowedAction> allowedActions(UUID publicId) {
        return workflow.allowedActions(trongPhamVi(publicId));
    }

    /** Các đường vào đời của một loại công việc — giao diện dựng ô "Trạng thái ban đầu" từ đây. */
    @Transactional(readOnly = true)
    public List<AllowedAction> initialActions(MaintenanceType workType) {
        return workflow.initialActions(quyTrinhCua(workType));
    }

    /**
     * Tổng chi phí và số lượng công việc theo kỳ — <b>tính ở BE</b> (quy tắc 3, T18.7).
     *
     * <p>⛔ FE không cộng. Không phải vì không cộng được, mà vì cộng ở hai nơi thì sẽ có ngày hai nơi
     * ra hai số: màn hình chi tiết cộng những dòng đang hiển thị (một trang), báo cáo BC-09 cộng cả
     * kỳ, và cả hai đều gọi kết quả là "tổng chi phí".
     */
    @Transactional(readOnly = true)
    public CostSummary costSummary(MaintenanceFilter filter) {
        Long ct = congTrinhId(filter.constructionPublicId());
        BigDecimal tong = logs.sumCost(ct, filter.workType(), filter.from(), filter.to());
        long soBanGhi = logs.countInPeriod(ct, filter.workType(), filter.from(), filter.to());
        return new CostSummary(tong, soBanGhi, filter.from(), filter.to());
    }

    /**
     * Tổng chi phí theo kỳ.
     *
     * @param total {@code null} khi không bản ghi nào trong kỳ có chi phí — <b>khác 0 đồng</b>. Giữ
     *     nguyên {@code null} thay vì quy về {@code BigDecimal.ZERO} vì "chưa ai điền chi phí" và
     *     "đã làm mà không tốn tiền" là hai câu khác nhau, và trên một bảng quyết toán thì chọn nhầm
     *     câu là đưa ra một con số không có thật.
     */
    // ⚠⚠ ALWAYS đè cấu hình NON_NULL chung của Jackson — cùng lý do với ô KPI ở DashboardService, và
    // bài kiểm đã bắt được nó ngay lượt chạy đầu. Bỏ hẳn khoá `total` khỏi JSON thì phía nhận đọc ra
    // `undefined`, không phân biệt được với "API đổi tên trường"; mà cả thiết kế của record này dựa
    // trên việc nói RÕ rằng không có số.
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record CostSummary(BigDecimal total, long recordCount, LocalDate from, LocalDate to) {}

    /** Danh sách "Sự cố chưa xử lý" (T18.8) — đã lọc theo phạm vi đơn vị của người đăng nhập. */
    @Transactional(readOnly = true)
    public List<MaintenanceLog> openIncidents(int limit) {
        return logs.openIncidents(PageRequest.of(0, Math.clamp(limit, 1, 200)));
    }

    // === Ghi =================================================================

    /**
     * Ghi nhận một công việc hoặc một sự cố.
     *
     * <p>Thứ tự các bước có chủ đích: kiểm công trình (và phạm vi) → kiểm quy tắc nghiệp vụ → xin
     * trạng thái khởi tạo từ engine → mới sinh mã. Sinh mã sau cùng vì {@link CodeGenerator} chạy
     * trong <b>giao dịch riêng</b>: một lượt tạo bị từ chối ở bước kiểm mà đã trót lấy mã thì mã đó
     * mất luôn, để lại một lỗ trong dãy số mà kế toán sẽ hỏi.
     */
    @Transactional
    public MaintenanceLog create(MaintenanceLogForm form) {
        Construction ct = congTrinh(form.constructionPublicId());
        chanCongTrinhDaDong(ct);

        MaintenanceType loai = yeuCau(form.workType());
        IncidentSeverity mucDo = kiemMucDo(loai, form.severity());
        kiemNgay(form.startedOn(), form.completedOn());

        // ⛔ Trạng thái khởi tạo do ENGINE quyết định và kiểm quyền, không phải service này —
        // điểm nghiệp vụ 15. Tạo ở MOI rồi chạy transition cho tới DA_XU_LY là bịa vào nhật ký
        // (có chuỗi băm) một chuỗi sự việc chưa từng xảy ra.
        String trangThaiDau = workflow.resolveInitialState(quyTrinhCua(loai), form.initialState());
        if (MaintenanceState.DA_XU_LY.equals(trangThaiDau) && form.completedOn() == null) {
            throw new BusinessRuleException(ErrorCode.OPS_2004);
        }

        MaintenanceLog banGhi = new MaintenanceLog(
                codes.next(MA_TIEN_TO, MA_SO_CHU_SO),
                ct.getId(),
                // T18.2 — SAO CHÉP đơn vị của công trình tại thời điểm phát sinh.
                ct.getOrgUnitId(),
                loai,
                trangThaiDau,
                yeuCau(form.startedOn()),
                chuanHoaNoiDung(form.content()),
                nguoiPhuTrach(form.assigneeUserPublicId()));
        banGhi.setSeverity(mucDo);
        apDung(banGhi, form);

        MaintenanceLog saved = logs.save(banGhi);
        capNhatTrangThaiCongTrinh(ct);

        log.info(
                "Ghi nhận {} {} cho công trình {} — trạng thái ban đầu {}",
                loai.laSuCo() ? "sự cố" : "công việc",
                saved.getCode(),
                ct.getCode(),
                trangThaiDau);

        if (loai.laSuCo() && saved.dangMo()) {
            baoSuCoMoi(saved, ct);
        }
        return saved;
    }

    /**
     * Sửa một bản ghi đã lưu — T18.9.
     *
     * <p>Quyền theo ma trận §6: {@code ops:maintenance:update} = Admin + Quản lý XN. Controller chỉ
     * đòi {@code ops:maintenance:create} vì luật thật phụ thuộc <b>dữ liệu</b> (ai tạo, tạo lúc nào)
     * chứ không chỉ phụ thuộc vai trò, mà annotation thì không đọc được dữ liệu. Chốt chặn nằm ở
     * {@link #chanSuaTraiPhep}.
     */
    @Transactional
    public MaintenanceLog update(UUID publicId, MaintenanceLogForm form) {
        MaintenanceLog banGhi = trongPhamVi(publicId);
        chanSuaTraiPhep(banGhi);

        Construction ct = congTrinh(form.constructionPublicId());
        // ⛔ Chuyển bản ghi sang công trình khác là chuyển cả phạm vi đơn vị của nó — một đường ghi
        //    dữ liệu vào phạm vi của Xí nghiệp khác, đội lốt một lượt sửa. Muốn vậy thì xoá rồi lập
        //    lại, để nhật ký có đủ hai vết.
        if (!ct.getId().equals(banGhi.getConstructionId())) {
            throw new BusinessRuleException(ErrorCode.SYS_0008, "đổi công trình", banGhi.getStatus());
        }

        MaintenanceType loai = yeuCau(form.workType());
        IncidentSeverity mucDo = kiemMucDo(loai, form.severity());
        kiemNgay(form.startedOn(), form.completedOn());
        if (MaintenanceState.DA_XU_LY.equals(banGhi.getStatus()) && form.completedOn() == null) {
            throw new BusinessRuleException(ErrorCode.OPS_2004);
        }

        boolean doiLoai = loai != banGhi.getWorkType();
        banGhi.setWorkType(loai);
        banGhi.setSeverity(mucDo);
        banGhi.setStartedOn(yeuCau(form.startedOn()));
        banGhi.setContent(chuanHoaNoiDung(form.content()));
        banGhi.setAssigneeUserId(nguoiPhuTrach(form.assigneeUserPublicId()));
        apDung(banGhi, form);

        MaintenanceLog saved = logs.save(banGhi);
        // Đổi loại giữa "sự cố" và "không sự cố" là đổi màu cờ của công trình (đỏ ↔ vàng), nên phải
        // tính lại. Không đổi loại thì vẫn tính lại — rẻ, và một nhánh "khi nào cần tính" là một
        // nhánh sẽ có ngày thiếu trường hợp.
        capNhatTrangThaiCongTrinh(ct);
        if (doiLoai) {
            log.info("Bản ghi {} đổi loại công việc → {} (quy trình {})", saved.getCode(), loai, quyTrinhCua(loai));
        }
        return saved;
    }

    /**
     * Chuyển trạng thái xử lý — <b>đường duy nhất</b> (quy tắc 4).
     *
     * @param completedOn ngày hoàn thành đi kèm khi bước chuyển dẫn tới "Đã xử lý"; {@code null} thì
     *     giữ nguyên giá trị đang có
     */
    @Transactional
    public MaintenanceLog execute(UUID publicId, String action, LocalDate completedOn, String acceptNote) {
        MaintenanceLog banGhi = trongPhamVi(publicId);
        if (completedOn != null) {
            kiemNgay(banGhi.getStartedOn(), completedOn);
            banGhi.setCompletedOn(completedOn);
        }
        if (acceptNote != null && !acceptNote.isBlank()) {
            banGhi.setAcceptanceNote(acceptNote.trim());
        }

        chanDongKhiThieuNgayHoanThanh(banGhi, action);
        workflow.execute(banGhi, action, null);

        MaintenanceLog saved = logs.save(banGhi);
        trangThai.recomputeFor(saved.getConstructionId());
        return saved;
    }

    /**
     * Xoá mềm.
     *
     * <p>⚠ Phải tính lại trạng thái công trình: xoá bản ghi sự cố cuối cùng đang mở mà không tính
     * lại thì công trình mang cờ đỏ vĩnh viễn, và không còn bản ghi nào để giải thích vì sao.
     */
    @Transactional
    public void delete(UUID publicId) {
        MaintenanceLog banGhi = trongPhamVi(publicId);
        banGhi.markDeleted(Instant.now());
        logs.save(banGhi);
        trangThai.recomputeFor(banGhi.getConstructionId());
        log.info("Xoá mềm bản ghi {} ({})", banGhi.getCode(), banGhi.getWorkType());
    }

    // === Nội bộ ==============================================================

    /**
     * ⚠⚠ Chặn "đóng bản ghi mà chưa có ngày hoàn thành" <b>TRƯỚC</b> khi engine chuyển trạng thái.
     *
     * <p>Bản đầu kiểm <i>sau</i>, với lập luận nghe rất hợp lý: engine sở hữu máy trạng thái, hỏi nó
     * "bước này dẫn tới đâu" là chép lại một nửa máy trạng thái ra chỗ khác, và ném ngoại lệ sau thì
     * giao dịch quay lui hết. <b>Lập luận đó sai ở một chỗ:</b> lượt kiểm sau không bao giờ chạy tới.
     * {@code WorkflowEngine.execute} ghi một dòng thông báo, lượt ghi đó <b>flush</b> cả entity đang
     * bẩn, và ràng buộc {@code ck_maintenance_logs_completed_when_done} của CSDL bắn trước — người
     * dùng nhận một lỗi ràng buộc thô thay vì {@code OPS-2004}.
     *
     * <p>Cách tra đích đến vẫn <b>không</b> chép lại máy trạng thái: đọc chính
     * {@link WorkflowPort#allowedActions} — dữ liệu của engine, không phải bản sao. Hành động không
     * có trong danh sách thì để {@code execute} trả về đúng mã lỗi của nó ({@code SYS-0008} hoặc
     * {@code AUTH-3001}); ở đây không đoán thay.
     */
    private void chanDongKhiThieuNgayHoanThanh(MaintenanceLog banGhi, String action) {
        if (banGhi.getCompletedOn() != null) {
            return;
        }
        boolean dongBanGhi = workflow.allowedActions(banGhi).stream()
                .anyMatch(a -> a.action().equals(action) && MaintenanceState.DA_XU_LY.equals(a.toState()));
        if (dongBanGhi) {
            throw new BusinessRuleException(ErrorCode.OPS_2004);
        }
    }

    private static String quyTrinhCua(MaintenanceType loai) {
        return loai.laSuCo() ? MaintenanceLog.WORKFLOW_INCIDENT : MaintenanceLog.WORKFLOW_WORK;
    }

    /** Gán các trường tuỳ chọn. Cố ý không đụng tới mã, công trình, đơn vị — bất biến sau khi tạo. */
    private void apDung(MaintenanceLog banGhi, MaintenanceLogForm form) {
        banGhi.setCompletedOn(form.completedOn());
        banGhi.setItemOrEquipment(form.itemOrEquipment());
        datDonViThucHien(banGhi, form);
        banGhi.setCost(form.cost());
        banGhi.setFundingSource(form.fundingSource());
        banGhi.setAcceptanceResult(form.acceptanceResult());
        banGhi.setAcceptanceNote(form.acceptanceNote());
        banGhi.setAlertEventPublicId(form.alertEventPublicId());
    }

    /**
     * Đơn vị thực hiện — đúng một trong hai, điểm nghiệp vụ 17.
     *
     * <p>Kiểm ở đây <b>và</b> ở CSDL. Trùng lặp có chủ đích: CHECK của CSDL là thứ không đường ghi
     * nào lách được, còn kiểm ở đây là thứ trả về một mã lỗi người dùng đọc hiểu thay vì một lỗi
     * ràng buộc thô.
     */
    private void datDonViThucHien(MaintenanceLog banGhi, MaintenanceLogForm form) {
        boolean coDonVi = form.performerOrgUnitPublicId() != null;
        boolean coTen = form.performerName() != null && !form.performerName().isBlank();
        if (coDonVi == coTen) {
            throw new ValidationException(ErrorCode.OPS_2017);
        }
        banGhi.datDonViThucHien(
                coDonVi ? donVi(form.performerOrgUnitPublicId()).id() : null,
                coTen ? form.performerName().trim() : null);
    }

    /**
     * Mức độ đi cùng chiều với loại sự cố — cả hai phía, giống CHECK ở CSDL.
     *
     * <p>Chiều thứ hai (không phải sự cố thì mức độ phải rỗng) dễ bị coi là thừa. Không thừa: một
     * bản ghi bảo trì định kỳ mang mức độ "Nghiêm trọng" sẽ hiện lên trong mọi bộ lọc theo mức độ,
     * và người trực sẽ đọc nó như một sự cố.
     */
    private static IncidentSeverity kiemMucDo(MaintenanceType loai, IncidentSeverity mucDo) {
        if (loai.laSuCo() == (mucDo == null)) {
            throw new ValidationException(ErrorCode.OPS_2003);
        }
        return mucDo;
    }

    private static void kiemNgay(LocalDate batDau, LocalDate hoanThanh) {
        if (batDau != null && hoanThanh != null && hoanThanh.isBefore(batDau)) {
            throw new ValidationException(ErrorCode.OPS_2001);
        }
    }

    /** Công trình đã thanh lý hoặc đã xoá thì không nhận công việc mới — {@code OPS-2002}. */
    private static void chanCongTrinhDaDong(Construction ct) {
        if (ct.isDeleted() || ct.getLifecycleState() == LifecycleState.DA_THANH_LY) {
            throw new BusinessRuleException(ErrorCode.OPS_2002);
        }
    }

    /**
     * ⛔ Chốt chặn sửa bản ghi đã lưu — T18.9.
     *
     * <p>Hai đường được phép:
     *
     * <ol>
     *   <li>có {@code ops:maintenance:update} — ma trận §6, mặc định là Admin + Quản lý XN
     *   <li><b>cửa sổ người nhập tự sửa</b>: chính người đã tạo, trong N phút đầu, và bản ghi
     *       <i>chưa rời trạng thái ban đầu</i>. N đọc từ {@code settings}, mặc định 0 = tắt.
     * </ol>
     *
     * <p>⚠ Điều kiện "chưa rời trạng thái ban đầu" không phải cẩn thận thừa: một bản ghi đã được
     * người khác tiếp nhận là một bản ghi có người thứ hai đang dựa vào nội dung của nó. Sửa lặng lẽ
     * lúc đó không còn là "sửa lỗi gõ nhầm của chính mình" nữa.
     *
     * <p>⚠ Đọc {@code settings} ở <b>mỗi lượt gọi</b>, không phải một lần lúc dựng bean — đúng bài
     * học của hạn mức tải tệp ở WS-12: chốt lúc dựng thì tham số bày ra màn hình cấu hình trở thành
     * công tắc chết.
     */
    private void chanSuaTraiPhep(MaintenanceLog banGhi) {
        AuthenticatedUser nguoiDung = AuthContext.current().orElse(null);
        if (nguoiDung != null && nguoiDung.hasPermission("ops:maintenance:update")) {
            return;
        }
        Duration cuaSo = settings.getMinutes(KEY_AUTHOR_EDIT_WINDOW, 0);
        if (cuaSo.isZero() || cuaSo.isNegative()) {
            throw new PermissionDeniedException(ErrorCode.AUTH_3001);
        }
        boolean laTacGia = nguoiDung != null
                && banGhi.getCreatedBy() != null
                && banGhi.getCreatedBy().equals(nguoiDung.userId());
        boolean conHan = banGhi.getCreatedAt() != null
                && Instant.now().isBefore(banGhi.getCreatedAt().plus(cuaSo));
        // "Chưa ai động vào" = chưa có lượt ghi nào sau lượt tạo.
        //
        // ⚠ Dùng `version`, KHÔNG dùng `updatedAt == null`: bộ ghi nhật ký của Spring Data đặt
        //   `lastModifiedDate` ngay ở lượt chèn, nên cột đó không bao giờ rỗng và điều kiện này sẽ
        //   luôn sai — cửa sổ bật lên mà không mở cho ai. Bài kiểm bắt được đúng chỗ này.
        //
        // ⚠ Cũng không so trạng thái hiện tại với trạng thái khởi tạo: bản ghi nhập sau khi hoàn
        //   thành vào đời thẳng ở DA_XU_LY, nên phép so đó luôn đúng với nó và cửa sổ mở rộng hơn
        //   dự định.
        boolean chuaAiDongVao = banGhi.getVersion() != null && banGhi.getVersion() == 0;
        if (!(laTacGia && conHan && chuaAiDongVao)) {
            throw new PermissionDeniedException(ErrorCode.AUTH_3001);
        }
        log.info("Người nhập tự sửa bản ghi {} trong cửa sổ {} phút", banGhi.getCode(), cuaSo.toMinutes());
    }

    /**
     * Người phụ trách — mặc định là người đang nhập, cho đổi (CN-02.2).
     *
     * <p>Mặc định chứ không bắt buộc chọn: bản ghi nào cũng phải có người chịu trách nhiệm, mà bắt
     * chọn ở mọi lượt nhập thì người dùng sẽ chọn bừa người đầu danh sách.
     */
    private Long nguoiPhuTrach(UUID publicId) {
        if (publicId != null) {
            return users.internalIdOf(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        }
        return AuthContext.current()
                .map(AuthenticatedUser::userId)
                .orElseThrow(() -> new ValidationException(ErrorCode.SYS_0003));
    }

    private Construction congTrinh(UUID publicId) {
        if (publicId == null) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return constructions.get(publicId);
    }

    private Long congTrinhId(UUID publicId) {
        return publicId == null ? null : congTrinh(publicId).getId();
    }

    private Long donViId(UUID publicId) {
        return publicId == null ? null : donVi(publicId).id();
    }

    private OrgUnitRef donVi(UUID publicId) {
        return orgUnits.findRef(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private void capNhatTrangThaiCongTrinh(Construction ct) {
        trangThai.recompute(ct);
    }

    /**
     * Báo sự cố mới cho đơn vị phụ trách.
     *
     * <p>Dùng {@code alert} (nhóm Ban điều hành ∪ người đứng đầu đơn vị, theo G11) chứ không
     * {@code targeted}: một sự cố công trình thuỷ lợi là việc cấp điều hành cần biết, không chỉ
     * người trực tiếp xử lý. Đây là loại sự kiện <b>hiếm và quan trọng</b> — đúng tiêu chí để gửi
     * rộng; gửi rộng cho việc hằng ngày mới là thứ dẫn tới không ai đọc thông báo nữa
     * ({@code architecture-review.md} §10.10).
     */
    private void baoSuCoMoi(MaintenanceLog banGhi, Construction ct) {
        NotifySeverity mucDo =
                banGhi.getSeverity() == IncidentSeverity.NGHIEM_TRONG || banGhi.getSeverity() == IncidentSeverity.CAO
                        ? NotifySeverity.CRITICAL
                        : NotifySeverity.WARNING;
        notifications.notify(NotifyRequest.alert(
                "INCIDENT_REPORTED",
                "Sự cố tại %s (%s)".formatted(ct.getName(), ct.getCode()),
                "Bản ghi %s — mức độ %s, ghi nhận ngày %s. %s"
                        .formatted(
                                banGhi.getCode(),
                                banGhi.getSeverity(),
                                DateTimeUtils.DISPLAY_DATE.format(banGhi.getStartedOn()),
                                banGhi.getContent()),
                mucDo,
                List.of(ct.getOrgUnitId())));
    }

    private static <T> T yeuCau(T value) {
        if (value == null) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return value;
    }

    private static String chuanHoaNoiDung(String noiDung) {
        if (noiDung == null || noiDung.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return noiDung.trim();
    }
}
