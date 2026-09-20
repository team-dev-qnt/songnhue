package com.songnhue.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.persistence.WorkflowAware;

/**
 * Đơn nghỉ phép — CN-04.9 (SRS M4.10).
 *
 * <h2>⛔⛔ Trạng thái đổi DUY NHẤT qua Workflow engine</h2>
 *
 * <p>⛔ Không có {@code setState}. {@link #applyState(String)} là đường duy nhất và chỉ
 * {@code WorkflowEngine} được gọi nó (quy tắc 4 — luật ArchUnit {@code SilentFailureRuleTest} canh).
 * Bịa một lượt đổi trạng thái ở đây là bỏ qua kiểm quyền, bỏ qua bắn thông báo, và bỏ qua ghi nhật
 * ký — ba thứ mà một quy trình duyệt tồn tại vì chúng.
 *
 * <h2>Hai cột là BẢN SAO có chủ đích, ⛔ không phải dữ liệu trùng lặp</h2>
 *
 * <ul>
 *   <li>{@code org_unit_id} — đơn vị của nhân viên <b>tại thời điểm nộp</b>. Bộ lọc phạm vi tầng 3
 *       chạy trên chính cột này, nên một người chuyển đơn vị giữa chừng ⛔ không được làm đơn cũ
 *       nhảy sang hộp duyệt của trưởng đơn vị mới — người đã duyệt là người cũ.
 *   <li>{@link #workingDays} — số ngày công <b>đã đếm lúc nộp</b>. Xem javadoc của nó.
 * </ul>
 *
 * <h2>⚠ {@code @Audited} — và vì sao ⛔ KHÔNG cần {@code excludeFields}</h2>
 *
 * <p>Đơn nghỉ phép ⛔ không mang một trường 🔒 nào: ⛔ không CCCD, ⛔ không lương, ⛔ không số tài
 * khoản. Trường "nhạy cảm" nhất là {@link #reason} — lý do nghỉ, thứ người nộp <b>tự viết cho người
 * duyệt đọc</b>. Ghi nó vào {@code audit_logs} là đúng: tranh chấp phép năm luôn xoay quanh câu
 * *"tôi đã ghi lý do gì"*. ⚠ Bộ canh {@code AuditRedactionRuleTest} nhận diện bảng 🔒 bằng
 * {@code CHECK} dạng bản mã trong migration — bảng này ⛔ không có, nên nó ⛔ không đòi gì, và điều
 * đó <b>đúng</b> chứ ⛔ không phải một khe hở.
 */
@Entity
@Table(name = "leave_requests")
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
@Audited(module = "hr", entityType = "LeaveRequest")
public class LeaveRequest extends ScopedEntity implements WorkflowAware {

    /** Khớp {@code workflow_definitions.entity_type} — sai chuỗi này là ⛔ không tìm ra quy trình nào. */
    public static final String WORKFLOW = "LEAVE_REQUEST";

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /**
     * Số ngày công <b>đã đếm lúc nộp</b> — trừ cuối tuần và ngày lễ.
     *
     * <h2>⛔⛔ Cố ý GHI XUỐNG, ⛔ không tính lại mỗi lượt đọc</h2>
     *
     * <p>Đây là ngoại lệ <b>có ý thức</b> với quy tắc 3. Quy tắc ấy nói *tính ở BE thay vì FE*; nó
     * ⛔ không nói *phải tính lại mỗi lượt đọc*. Con số này là một <b>sự thật lịch sử tại thời điểm
     * quyết định</b>: Công ty thêm một ngày lễ vào tháng sau thì đơn đã duyệt tháng trước ⛔ <b>không
     * được</b> đổi số ngày — người lao động đã nghỉ đúng ngần ấy ngày và số dư đã trừ đúng ngần ấy.
     *
     * <p>⚠ Phân biệt với quy tắc 13 (*cột dẫn xuất trộn hai nguồn ⇒ phụ thuộc ai bấm F5 sau cùng*):
     * cột ở đó mô tả <b>trạng thái HIỆN TẠI</b> nên phải sinh; cột này ghi một <b>quyết định ĐÃ XẢY
     * RA</b> nên phải đóng băng. Hai cột trông giống nhau, hai luật ngược nhau.
     *
     * <p>{@code NUMERIC(5,1)} chứ ⛔ không {@code double} — quy tắc 2.
     */
    @Column(name = "working_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal workingDays;

    @Column(name = "reason", length = 1000)
    private String reason;

    /** Chốt C3 — người tạo đơn <b>hộ</b>. {@code null} = chính nhân viên nộp. */
    @Column(name = "created_for_by")
    private Long createdForBy;

    /** Tài khoản của chính người nghỉ; {@code null} khi CBNV chưa liên kết tài khoản (chốt C3). */
    @Column(name = "requester_user_id")
    private Long requesterUserId;

    /**
     * Trạng thái.
     *
     * <p>⛔ Không có setter, và ⛔ <b>không có giá trị mặc định</b> — xem javadoc hàm dựng.
     */
    @Column(name = "state", nullable = false, length = 30)
    private String state;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Người quyết <b>cấp 1</b> — T80.3. Xem {@link #ghiCapMot}. */
    @Column(name = "cap1_by")
    private Long cap1By;

    @Column(name = "cap1_at")
    private Instant cap1At;

    /** Lượt uỷ quyền đã dùng, {@code null} = quyết với tư cách trưởng/phó của chính mình (B3). */
    @Column(name = "uy_quyen_id")
    private Long uyQuyenId;

    @Column(name = "duyet_du_phong", nullable = false)
    private boolean duyetDuPhong;

    protected LeaveRequest() {}

    /**
     * @param initialState trạng thái khởi tạo <b>đã được {@code WorkflowPort.resolveInitialState}
     *     kiểm</b>.
     *     <h2>⛔⛔ Vì sao nhận ở HÀM DỰNG chứ ⛔ không gọi {@code applyState} sau khi tạo</h2>
     *     <p>Bản đầu của {@code DonNghiPhepService.nop()} làm đúng thế, và
     *     {@code SilentFailureRuleTest.onlyTheEngineChangesState} đỏ ngay ở lượt chạy TOÀN BỘ đầu
     *     tiên: luật ArchUnit của quy tắc 4 cấm <b>mọi</b> lớp ngoài
     *     {@code core.application.workflow} gọi {@code applyState()}. Nó <b>đúng</b> — một lời gọi
     *     như vậy ⛔ không phân biệt được với một lượt đổi trạng thái lén, và bịa một bước chuyển là
     *     ký tên vào một lịch sử chưa xảy ra (nhật ký kiểm toán có chuỗi băm).
     *     <p>⭐ Tiền lệ đúng đã có từ Phase 1: {@code MaintenanceLog} nhận {@code initialState} ở hàm
     *     dựng với đúng lý lẽ ấy — <i>một bản ghi tồn tại ở trạng thái chưa kiểm, dù chỉ trong vài
     *     dòng mã, là một bản ghi có thể được lưu nhầm ở trạng thái đó</i>.
     *     <p>⚠ Và trường {@code state} vì thế ⛔ <b>không</b> mang giá trị mặc định nữa: một mặc định
     *     ở đây làm hàm dựng <b>bỏ qua được</b> mà vẫn cho ra một bản ghi "trông đúng" (luật 3).
     */
    // CHECKSTYLE.OFF: ParameterNumber - đây là tập trường BẤT BIẾN của một đơn nghỉ; để trống bất kỳ
    // cái nào rồi gán sau nghĩa là có một khoảnh khắc bản ghi tồn tại mà ⛔ không hợp lệ.
    public LeaveRequest(
            Long employeeId,
            Long orgUnitId,
            LeaveType leaveType,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal workingDays,
            String initialState) {
        this.employeeId = employeeId;
        setOrgUnitId(orgUnitId);
        this.leaveType = leaveType;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.workingDays = workingDays;
        this.state = initialState;
    }
    // CHECKSTYLE.ON: ParameterNumber

    // ---- Workflow ------------------------------------------------------------

    @Override
    public String workflowEntityType() {
        return WORKFLOW;
    }

    @Override
    public String currentState() {
        return state;
    }

    @Override
    public void applyState(String newState) {
        this.state = newState;
    }

    @Override
    public Long entityId() {
        return getId();
    }

    /**
     * Người nhận thông báo khi đơn đổi trạng thái.
     *
     * <h2>⚠ Vì sao có vế dự phòng {@code createdForBy}</h2>
     *
     * <p>Chốt C3 nói <i>"nhân viên ⛔ không dùng máy tính → quản lý đơn vị tạo đơn hộ"</i>. Với
     * người ấy {@link #requesterUserId} là {@code null} — họ ⛔ không có tài khoản để nhận thông
     * báo. Trả {@code null} ở đây thì {@code notify_owner = TRUE} bắn vào <b>hư không</b>, và ⛔
     * không ai biết đơn đã được duyệt hay bị từ chối.
     *
     * <p>⇒ Rơi về người đã nộp hộ — người <b>duy nhất</b> thật sự đọc được thông báo, và cũng là
     * người sẽ đi báo lại cho nhân viên. Ràng buộc {@code ck_leave_requests_co_nguoi_nhan} ép ít
     * nhất một trong hai cột khác {@code null}, nên hàm này ⛔ không bao giờ trả {@code null}.
     */
    @Override
    public Long ownerUserId() {
        return requesterUserId != null ? requesterUserId : createdForBy;
    }

    // ---- Truy vấn nghiệp vụ --------------------------------------------------

    public LeaveState trangThai() {
        return LeaveState.valueOf(state);
    }

    /** Đơn đã bắt đầu nghỉ tính tới {@code homNay} — ⛔ không huỷ được nữa. */
    public boolean daBatDau(LocalDate homNay) {
        return !fromDate.isAfter(homNay);
    }

    /** Hai khoảng ngày có chồng nhau ⛔ không — dùng cho cảnh báo trùng lịch và chặn trùng đơn. */
    public boolean chongKhoang(LocalDate tu, LocalDate den) {
        return !fromDate.isAfter(den) && !toDate.isBefore(tu);
    }

    // ---- Getter / setter ------------------------------------------------------

    public Long getEmployeeId() {
        return employeeId;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public BigDecimal getWorkingDays() {
        return workingDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getCreatedForBy() {
        return createdForBy;
    }

    public void setCreatedForBy(Long createdForBy) {
        this.createdForBy = createdForBy;
    }

    public Long getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(Long requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /** Ghi dấu ai quyết và lúc nào — gọi <b>cùng lượt</b> với bước chuyển của engine. */
    public void ghiQuyetDinh(Long nguoiQuyet, Instant luc) {
        this.decidedBy = nguoiQuyet;
        this.decidedAt = luc;
    }

    public Long getCap1By() {
        return cap1By;
    }

    public Instant getCap1At() {
        return cap1At;
    }

    /**
     * Ghi người đã quyết <b>cấp 1</b> — thứ DUY NHẤT làm cấp 2 khác cấp 1 (T80.3).
     *
     * <p>⛔⛔ ⛔ Dùng lại {@link #decidedBy}: cột ấy mang người ra quyết định <b>cuối</b>, mà lúc
     * chuyển sang {@code CHO_DUYET_2} thì chưa có quyết định cuối nào. Nhét tạm vào đó rồi ghi đè ở
     * cấp 2 nghĩa là sau khi duyệt xong ⛔ còn dấu vết nào của người cấp 1 — trong khi đó đúng là
     * thứ một lượt rà soát phép năm đi tìm.
     */
    public void ghiCapMot(Long nguoiQuyet, Instant luc) {
        this.cap1By = nguoiQuyet;
        this.cap1At = luc;
    }

    public Long getUyQuyenId() {
        return uyQuyenId;
    }

    public boolean isDuyetDuPhong() {
        return duyetDuPhong;
    }

    /**
     * Ghi <b>tư cách</b> của lượt quyết vừa rồi — chốt B3 (<i>"audit ghi 'duyệt theo uỷ quyền của
     * X'"</i>).
     *
     * <p>Vết đi trên chính lá đơn chứ ⛔ chỉ trong {@code audit_logs}: tranh chấp phép năm nổ ra
     * hàng tháng sau, và thứ người ta mở ra là <b>cái đơn</b> — bảng nhật ký giữ 5 năm và ⛔ phải
     * ai cũng đọc được.
     *
     * <p>⚠ {@code uyQuyenId} là một <b>khoá ngoại</b>, ⛔ phải một chuỗi chép lại tên người giao:
     * đổi tên tài khoản ⛔ được làm lịch sử nói sai.
     */
    public void ghiTuCach(Long uyQuyenId, boolean duPhong) {
        this.uyQuyenId = uyQuyenId;
        this.duyetDuPhong = duPhong;
    }
}
