package com.songnhue.hr.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.WorkflowPort;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.LeaveRequest;
import com.songnhue.hr.domain.LeaveState;
import com.songnhue.hr.infra.LeaveRequestRepository;

/**
 * Đơn nghỉ phép — CN-04.9 (SRS M4.10).
 *
 * <h2>⛔⛔ "Quản lý ĐƠN VỊ duyệt" cần HAI cơ chế, ⛔ không một</h2>
 *
 * <p>{@code workflow_transitions.required_permission} là một <b>mã quyền</b>; đặc tả nói một
 * <b>quan hệ</b> (<i>quản lý của đơn vị người nộp</i>). {@code hr:leave:approve} một mình cho một
 * trưởng Xí nghiệp 3 duyệt đơn của Xí nghiệp 5.
 *
 * <p>⇒ Vế còn lại là <b>bộ lọc phạm vi tầng 3</b> trên {@code leave_requests.org_unit_id}: người
 * duyệt ⛔ không <b>nhìn thấy</b> đơn ngoài phạm vi, nên ⛔ không có gì để bấm. Cùng hình dạng
 * CN-04.7 (*"chính nhân viên đó"* cũng ⛔ không biểu diễn được bằng một mã quyền) — và cùng cách
 * giải: thay một cổng quyền ⛔ không đủ bằng một tính chất <b>cấu trúc</b>.
 *
 * <h2>⛔ Ai được nộp đơn cho AI</h2>
 *
 * <ul>
 *   <li>{@code employeePublicId = null} ⇒ nộp cho <b>chính mình</b>, suy từ {@code users.employee_id}
 *       (T51.8). Đòi {@code hr:leave:request} — quyền mà chốt C3 cấp cho mọi CBNV.
 *   <li>{@code employeePublicId} khác null ⇒ <b>nộp hộ</b> (chốt C3). Đòi thêm
 *       {@code hr:leave:view-all}, và ghi {@code created_for_by} — đúng câu đặc tả *"lưu trường
 *       'người tạo hộ', ghi audit"*.
 * </ul>
 *
 * <p>⛔⛔ Nếu tài khoản chưa liên kết hồ sơ CBNV thì <b>⛔ không nộp được</b> ({@code SYS-0004}) —
 * và đó chính là lý do T51.8 phải trả trước CN-04.9: một quy trình duyệt ⛔ không biết ai đang xin
 * nghỉ là một quy trình ⛔ không duyệt được gì.
 */
@Service
public class DonNghiPhepService {

    private static final Logger log = LoggerFactory.getLogger(DonNghiPhepService.class);

    private final LeaveRequestRepository donNghi;
    private final EmployeeService employees;
    private final DemNgayCongService demNgayCong;
    private final SoDuPhepService soDu;
    private final ChinhSachPhep chinhSach;
    private final WorkflowPort workflow;
    private final NotificationPort thongBao;

    public DonNghiPhepService(
            LeaveRequestRepository donNghi,
            EmployeeService employees,
            DemNgayCongService demNgayCong,
            SoDuPhepService soDu,
            ChinhSachPhep chinhSach,
            WorkflowPort workflow,
            NotificationPort thongBao) {
        this.donNghi = donNghi;
        this.employees = employees;
        this.demNgayCong = demNgayCong;
        this.soDu = soDu;
        this.chinhSach = chinhSach;
        this.workflow = workflow;
        this.thongBao = thongBao;
    }

    /**
     * Xem trước một đơn <b>trước khi nộp</b> — đặc tả: *"tự tính số ngày + hiển thị số dư + cảnh
     * báo vượt phép"*.
     *
     * <p>⛔ Đường này ⛔ <b>không ghi gì</b>. Nó tồn tại để giao diện nói được ba điều mà người nộp
     * cần biết <b>trước</b> khi bấm gửi, và để cả ba con số ấy do <b>backend</b> tính (quy tắc 3).
     */
    @Transactional(readOnly = true)
    public XemTruoc xemTruoc(DonNghiPhepForm form) {
        Employee hoSo = hoSoMucTieu(form.employeePublicId());
        DemNgayCongService.KetQua dem = demNgayCong.dem(form.fromDate(), form.toDate());
        SoDuPhepService.SoDu du = soDu.tinh(hoSo, form.fromDate().getYear());

        boolean vuotPhep = form.leaveType().truVaoSoDuPhepNam() && du.conLai().compareTo(dem.soNgayCong()) < 0;

        long soNguoiNghiCung =
                donNghi.soNguoiNghiCungLuc(hoSo.getOrgUnitId(), hoSo.getId(), form.fromDate(), form.toDate());

        return new XemTruoc(
                dem.soNgayCong(),
                dem.soNgayLeTru(),
                dem.soNgayLeDaKhai(),
                dem.duNgayLeTheoLuat(),
                du,
                vuotPhep,
                chinhSach.canCapHai(dem.soNgayCong()),
                soNguoiNghiCung,
                canhBaoTrungLich(hoSo, soNguoiNghiCung));
    }

    /**
     * Nộp đơn.
     *
     * <p>⛔⛔ Bốn chốt chặn, và <b>thứ tự</b> của chúng là cố ý: rẻ trước, đắt sau, và cái nào cho
     * người dùng một câu trả lời rõ nhất thì đứng trước.
     */
    @Transactional
    public LeaveRequest nop(DonNghiPhepForm form) {
        Employee hoSo = hoSoMucTieu(form.employeePublicId());
        DemNgayCongService.KetQua dem = demNgayCong.dem(form.fromDate(), form.toDate());

        // [1] Đơn 0 ngày công — toàn cuối tuần/lễ.
        if (dem.soNgayCong().signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.HR_2004, form.fromDate(), form.toDate());
        }

        // [2] Chồng lên một đơn còn hiệu lực ⇒ đếm HAI LẦN cùng những ngày ấy vào số dư.
        List<LeaveRequest> chong = donNghi.donChongKhoang(hoSo.getId(), form.fromDate(), form.toDate());
        if (!chong.isEmpty()) {
            throw new ConflictException(ErrorCode.HR_2005, form.fromDate(), form.toDate());
        }

        // [3] Vượt số dư — chỉ áp cho PHÉP NĂM. Bốn loại đặc biệt có hạn mức riêng.
        // ⚠ `HR-2001`, ⛔ KHÔNG phải một mã mới: danh mục đã đặt sẵn mã ấy cho đúng trạng
        //   thái này từ 13/08/2026 và để nó mồ côi chờ CN-04.9 — xem javadoc `ErrorCode.HR_2001`.
        if (form.leaveType().truVaoSoDuPhepNam()) {
            SoDuPhepService.SoDu du = soDu.tinh(hoSo, form.fromDate().getYear());
            if (du.conLai().compareTo(dem.soNgayCong()) < 0) {
                throw new BusinessRuleException(ErrorCode.HR_2001, du.conLai(), dem.soNgayCong());
            }
        }

        // ⛔⛔ Câu ở đây trước đó khẳng định `resolveInitialState` là *"chốt chặn thật của đường
        //    vào đời — nó kiểm quyền của bước `__NEW__`"*. **Sai**, và đo được:
        //    `WorkflowEngine:177` trả về NGAY khi trạng thái xin bằng
        //    `workflow_definitions.initial_state`, ⛔ không tra hàng `__NEW__` nào. Với
        //    `LEAVE_REQUEST` thì `initial_state = 'CHO_DUYET'` chính là thứ ta xin ⇒ nhánh kiểm
        //    quyền **chưa bao giờ chạy**.
        //
        //    Nó vẫn phải gọi, vì đó là thứ giữ trạng thái khởi tạo do DỮ LIỆU quyết định thay vì
        //    do một hằng trong mã; và nó CÓ chặn thật ở các quy trình cho người dùng chọn đường
        //    vào (`ops`). Nhưng cổng quyền của đường nộp ở đây là `@RequirePermission` trên
        //    controller, ⛔ không phải dòng này — nói khác đi là dựng một bảo đảm trên giấy.
        LeaveRequest don = new LeaveRequest(
                hoSo.getId(),
                hoSo.getOrgUnitId(),
                form.leaveType(),
                form.fromDate(),
                form.toDate(),
                dem.soNgayCong(),
                // ⛔⛔ Trạng thái vào ĐỜI đi thẳng vào hàm dựng, ⛔ KHÔNG qua `applyState` sau đó:
                //    luật ArchUnit của quy tắc 4 cấm mọi lớp ngoài engine gọi `applyState()`, và
                //    nó bắt được bản đầu của chính đoạn này ở lượt chạy TOÀN BỘ đầu tiên.
                //    Tiền lệ: `MaintenanceLog` (Phase 1) — xem javadoc hàm dựng `LeaveRequest`.
                workflow.resolveInitialState(LeaveRequest.WORKFLOW, LeaveState.CHO_DUYET.name()));
        don.setReason(form.reason());
        don.setRequesterUserId(taiKhoanCuaHoSo(hoSo));
        if (form.employeePublicId() != null) {
            // Nộp HỘ (chốt C3) — ghi ai đã nộp thay. ⛔ Khác `created_by`: cột ấy luôn là người bấm
            //    nút, cột này khai RẰNG lượt bấm ấy là nộp hộ.
            don.setCreatedForBy(nguoiDangThaoTac());
        }

        LeaveRequest daLuu = donNghi.save(don);

        baoNguoiDuyet(daLuu, hoSo);

        log.info(
                "Nộp đơn nghỉ {} cho hồ sơ {} — {} → {} ({} ngày công)",
                form.leaveType(),
                hoSo.getCode(),
                form.fromDate(),
                form.toDate(),
                dem.soNgayCong());
        return daLuu;
    }

    /**
     * Báo cho người duyệt rằng có đơn mới — <b>phát tường minh, ⛔ không qua bảng bước chuyển</b>.
     *
     * <h2>⛔⛔ Vì sao ⛔ không khai ở {@code workflow_transitions}</h2>
     *
     * <p>Vì hàng {@code __NEW__} <b>⛔ không được chạy</b> cho đường vào đời mặc định:
     * {@code WorkflowEngine.resolveInitialState} trả về ngay khi trạng thái xin bằng
     * {@code initial_state}, nên {@code notifyAfterTransition} chưa bao giờ tới lượt. Bản đầu của
     * lượt này khai {@code notify_event = 'LEAVE_SUBMITTED'} ở hàng ấy và tin rằng đã xong; bài
     * {@code nopDonThiNguoiDuyetNhanDuocThongBao} đo ra <b>0</b> thông báo.
     *
     * <p>⭐ Đây là lý do nó đáng có một bài kiểm riêng thay vì một dòng chú thích: hộp
     * <i>Chờ duyệt</i> mà ⛔ không có chuông là một màn hình <b>⛔ không ai mở</b> — đúng hình dạng
     * đã trả giá ở T50.4 (<i>một con số trên màn hình ⛔ không phải một cái chuông</i>).
     *
     * <h2>⚠ Người nhận rộng hơn đặc tả, và điều đó được KHAI RA</h2>
     *
     * <p>{@code targetedWithUnits} giải người nhận bằng {@code findActiveIdsByPermission} —
     * <b>toàn Công ty</b>, ⛔ không cắt theo đơn vị (cộng thêm trưởng/phó của chính đơn vị người
     * nộp). Đặc tả nói <i>"quản lý <b>đơn vị</b> duyệt"</i>, hẹp hơn thế. Vế <i>duyệt</i> thì đã
     * hẹp đúng nhờ bộ lọc phạm vi; vế <i>nhận thư</i> thì chưa — nợ <b>T57.15</b>. ⛔ Thu hẹp nó là
     * đổi ngữ nghĩa của {@code RecipientResolver} cho <b>cả</b> CMS và vận hành công trình, nên nó
     * ⛔ không phải việc của một lượt dựng tính năng.
     */
    private void baoNguoiDuyet(LeaveRequest don, Employee hoSo) {
        thongBao.notify(NotifyRequest.targetedWithUnits(
                "LEAVE_SUBMITTED",
                "Đơn nghỉ phép mới chờ duyệt",
                "%s (%s) xin nghỉ %s từ %s đến %s — %s ngày công"
                        .formatted(
                                hoSo.getFullName(),
                                hoSo.getCode(),
                                don.getLeaveType(),
                                don.getFromDate(),
                                don.getToDate(),
                                don.getWorkingDays()),
                NotifySeverity.INFO,
                "hr:leave:approve",
                don.getOrgUnitId() == null ? List.of() : List.of(don.getOrgUnitId()),
                List.of()));
    }

    /** Hồ sơ của <b>chính người đang đăng nhập</b> — ⛔ ném {@code SYS-0004} khi chưa liên kết. */
    @Transactional(readOnly = true)
    public Employee hoSoCuaToi() {
        return hoSoMucTieu(null);
    }

    /** Hộp chờ duyệt — bộ lọc phạm vi tự cắt theo đơn vị của người đang đăng nhập. */
    @Transactional(readOnly = true)
    public Page<LeaveRequest> hopChoDuyet(Pageable pageable) {
        return donNghi.hopChoDuyet(pageable);
    }

    /** Đơn của một hồ sơ. */
    @Transactional(readOnly = true)
    public Page<LeaveRequest> cuaHoSo(UUID employeePublicId, Pageable pageable) {
        Employee hoSo = hoSoMucTieu(employeePublicId);
        return donNghi.findByEmployeeIdAndDeletedAtIsNullOrderByFromDateDesc(hoSo.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public LeaveRequest get(UUID publicId) {
        return donNghi.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /** Nút giao diện được phép hiện — đã lọc theo quyền của người đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<AllowedAction> hanhDongChoPhep(UUID publicId) {
        return workflow.allowedActions(get(publicId));
    }

    /**
     * Thực hiện một bước chuyển.
     *
     * <h2>⭐ Vì sao APPROVE ở cấp 1 có thể ⛔ KHÔNG dẫn tới `DA_DUYET`</h2>
     *
     * <p>Chốt C2 cho cấu hình *"≥ N ngày cần thêm cấp duyệt 2"*. Bảng {@code workflow_transitions}
     * ⛔ không diễn đạt được điều kiện ấy — nó là một bảng bước chuyển, ⛔ không phải một ngôn ngữ
     * luật. ⇒ <b>Service</b> chọn hành động: {@code APPROVE} hay {@code ESCALATE}, và engine chỉ
     * khai rằng cả hai đều hợp lệ từ {@code CHO_DUYET}.
     *
     * <p>⚠ Người gọi gửi {@code APPROVE}; việc đổi nó thành {@code ESCALATE} nằm ở đây, ⛔ không ở
     * giao diện — nếu ⛔ không thì hai khoá {@code settings} thành núm điều khiển <b>trình duyệt</b>
     * thay vì điều khiển <b>quy trình</b>.
     */
    @Transactional
    public LeaveRequest thucHien(UUID publicId, String action, String lyDo) {
        LeaveRequest don = get(publicId);

        if ("CANCEL".equals(action) && don.trangThai() == LeaveState.DA_DUYET) {
            LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);
            if (don.daBatDau(homNay)) {
                throw new BusinessRuleException(ErrorCode.HR_2007, don.getFromDate());
            }
        }

        String hanhDongThat = action;
        if ("APPROVE".equals(action)
                && don.trangThai() == LeaveState.CHO_DUYET
                && chinhSach.canCapHai(don.getWorkingDays())) {
            hanhDongThat = "ESCALATE";
        }

        LeaveRequest sau = workflow.execute(don, hanhDongThat, null, lyDo);
        if (sau.trangThai() == LeaveState.DA_DUYET || sau.trangThai() == LeaveState.TU_CHOI) {
            sau.ghiQuyetDinh(nguoiDangThaoTac(), Instant.now());
        }
        return donNghi.save(sau);
    }

    // ---- Nội bộ ---------------------------------------------------------------

    /**
     * Hồ sơ mà đơn này thuộc về.
     *
     * <p>⛔⛔ {@code null} ⇒ <b>chính người đang đăng nhập</b>, suy từ token. Đây là chỗ T51.8 trả
     * cổ tức: trước lượt ấy {@code AuthenticatedUser} ⛔ không mang {@code employeeId}, nên một quy
     * trình duyệt ⛔ không có cách nào biết ai đang xin nghỉ.
     */
    private Employee hoSoMucTieu(UUID employeePublicId) {
        if (employeePublicId != null) {
            // Nộp HỘ — đòi quyền xem toàn bộ đơn của đơn vị (chốt C3: *quản lý đơn vị* tạo hộ).
            AuthContext.current().ifPresent(ai -> {
                if (!ai.hasPermission("hr:leave:view-all")) {
                    // ⛔⛔ ⛔ Không truyền `"hr:leave:view-all"` làm đối số thông điệp. Bản đầu có
                    //    truyền, và bài kiểm HTTP đo ra rằng nó **biến mất**: `AUTH-3001` là mã 403
                    //    dùng chung của `PermissionInterceptor`, thông điệp của nó ⛔ không có một
                    //    chỗ cắm `{n}` nào, nên `MessageFormat` bỏ lặng đối số thừa. Một đối số ⛔
                    //    không ai đọc là nửa cặp đọc–ghi (luật 15) — ở đây nó còn tệ hơn một chút:
                    //    người viết tưởng mình vừa nói cho người dùng biết thiếu quyền gì.
                    //    ⚠ Và ⛔ không được vá bằng cách thêm `{0}` vào `AUTH-3001`: năm nơi ném
                    //      khác ⛔ không truyền đối số nào, chúng sẽ in ra `{0}` nguyên văn.
                    throw new PermissionDeniedException(ErrorCode.AUTH_3001);
                }
            });
            return employees.get(employeePublicId);
        }

        Long employeeId =
                AuthContext.current().map(AuthenticatedUser::employeeId).orElse(null);
        if (employeeId == null) {
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
        return employees.cuaChinhMinh(employeeId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /** Tài khoản của chính người nghỉ — {@code null} khi hồ sơ chưa liên kết (chốt C3). */
    private Long taiKhoanCuaHoSo(Employee hoSo) {
        Long dangDangNhap =
                AuthContext.current().map(AuthenticatedUser::employeeId).orElse(null);
        if (hoSo.getId().equals(dangDangNhap)) {
            return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
        }
        // ⛔ Nộp hộ: ⛔ không tra ngược `users.employee_id` ở đây — `hr` ⛔ không được đọc bảng
        //   `users` (quy tắc 6), và cổng `UserDirectoryPort` cố ý ⛔ không có phép tra ấy. Người
        //   nhận thông báo khi đó là người nộp hộ, qua `created_for_by`.
        return null;
    }

    private Long nguoiDangThaoTac() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
    }

    private Optional<String> canhBaoTrungLich(Employee hoSo, long soNguoiNghiCung) {
        long quanSo = employees.soNguoiConLamViec(hoSo.getOrgUnitId());
        if (quanSo <= 0) {
            return Optional.empty();
        }
        int tiLe = (int) Math.round((soNguoiNghiCung + 1) * 100.0 / quanSo);
        int nguong = chinhSach.nguongCanhBaoTrungLich();
        if (tiLe < nguong) {
            return Optional.empty();
        }
        return Optional.of("Khoảng này đã có %d/%d người của đơn vị nghỉ (%d%% ≥ ngưỡng %d%%)"
                .formatted(soNguoiNghiCung + 1, quanSo, tiLe, nguong));
    }

    /**
     * @param duNgayLeTheoLuat ⛔ <b>false</b> ⇒ giao diện phải nói *"năm N mới khai X/11 ngày lễ"*
     *     thay vì hiện {@code soNgayCong} như một sự thật đã kiểm — xem {@link DemNgayCongService}
     * @param canhBaoTrungLich rỗng = dưới ngưỡng. ⛔ Là một <b>cảnh báo</b>, ⛔ không phải chặn
     */
    public record XemTruoc(
            BigDecimal soNgayCong,
            int soNgayLeTru,
            int soNgayLeDaKhai,
            boolean duNgayLeTheoLuat,
            SoDuPhepService.SoDu soDu,
            boolean vuotPhep,
            boolean canCapHaiDuyet,
            long soNguoiNghiCungLuc,
            Optional<String> canhBaoTrungLich) {}
}
