package com.songnhue.hr.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Uỷ quyền duyệt nghỉ phép có thời hạn — <b>chốt B3</b> (CN-04.9).
 *
 * <h2>⛔⛔ Nó CHUYỂN VAI, ⛔ cấp quyền — và đó là cả thiết kế</h2>
 *
 * <p>{@code AuthenticatedUser} khai thẳng: <i>"record bất biến có chủ đích: ⛔ đoạn mã nghiệp vụ nào
 * được phép thêm quyền cho chính mình giữa chừng"</i>. Nên một hàng ở đây <b>⛔ đụng tới tập
 * quyền</b> của ai cả. Nó được đọc <b>lại từ CSDL</b> ở đúng khoảnh khắc người ta bấm nút, y hệt
 * cách bộ lọc phạm vi tầng 3 vẫn làm — ⇒ <b>thu hồi có hiệu lực NGAY</b>.
 *
 * <p>Hai phương án "rẻ" hơn đều hỏng ở chỗ ⛔ nhìn thấy được:
 *
 * <ul>
 *   <li>Ghi {@code hr:leave:approve} vào vai trò người được uỷ quyền ⇒ quyền là <b>toàn cục và vĩnh
 *       viễn</b>; hết hạn ⛔ ai nhớ gỡ. Đúng hình dạng <b>T54.4</b>.
 *   <li>Cộng quyền vào token lúc đăng nhập ⇒ access token sống <b>30 phút</b>, nên <b>thu hồi trễ
 *       tới 30 phút</b>, và một lượt uỷ quyền bắt đầu 00:00 ⛔ dùng được cho tới khi người ấy đăng
 *       nhập lại.
 * </ul>
 *
 * <h2>⚠ Người được uỷ quyền phải SẴN có {@code hr:leave:approve}</h2>
 *
 * <p>Quyết định của QuanTran 20/09/2026. Nếu ⛔ thì biểu mẫu nghỉ phép trở thành một <b>đường cấp
 * quyền ẩn</b> nằm ngoài màn hình Vai trò &amp; phân quyền — và màn hình ấy thôi là bức tranh đầy
 * đủ. Ai chưa có thì Admin HR gán vai trò: một hành động <b>riêng</b>, nhìn thấy được, có audit.
 *
 * <h2>{@code revokedAt} ⛔ phải {@code deletedAt}</h2>
 *
 * <p><i>Người quản trị xoá nhầm một bản ghi</i> và <i>trưởng đơn vị đi công tác về sớm nên rút uỷ
 * quyền</i> là hai sự kiện khác nhau, và chỉ cái thứ hai phải hiện trên lịch sử duyệt.
 */
@Entity
@Table(name = "leave_approval_delegations")
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
@Audited(module = "hr", entityType = "UyQuyenDuyetPhep")
public class UyQuyenDuyetPhep extends ScopedEntity {

    @Column(name = "delegator_user_id", nullable = false)
    private Long nguoiUyQuyenId;

    @Column(name = "delegate_user_id", nullable = false)
    private Long nguoiDuocUyQuyenId;

    @Column(name = "from_date", nullable = false)
    private LocalDate tuNgay;

    @Column(name = "to_date", nullable = false)
    private LocalDate denNgay;

    @Column(name = "reason", length = 500)
    private String lyDo;

    @Column(name = "revoked_at")
    private Instant thuHoiLuc;

    @Column(name = "revoked_by")
    private Long thuHoiBoi;

    protected UyQuyenDuyetPhep() {}

    /**
     * ⛔ ⛔ Không có setter cho năm trường đầu: một lượt uỷ quyền là một <b>sự kiện đã xảy ra</b>.
     * Sửa ngày kết thúc của một uỷ quyền đang chạy là viết lại lịch sử của những lượt duyệt đã trỏ
     * vào nó — muốn dừng sớm thì <b>thu hồi</b>, và lượt thu hồi ấy có dấu vết riêng.
     */
    public UyQuyenDuyetPhep(
            Long orgUnitId,
            Long nguoiUyQuyenId,
            Long nguoiDuocUyQuyenId,
            LocalDate tuNgay,
            LocalDate denNgay,
            String lyDo) {
        setOrgUnitId(orgUnitId);
        this.nguoiUyQuyenId = nguoiUyQuyenId;
        this.nguoiDuocUyQuyenId = nguoiDuocUyQuyenId;
        this.tuNgay = tuNgay;
        this.denNgay = denNgay;
        this.lyDo = lyDo;
    }

    /** Còn hiệu lực vào {@code ngay} — <b>ba</b> vế, và bỏ vế nào cũng là một lỗ. */
    public boolean coHieuLuc(LocalDate ngay) {
        return thuHoiLuc == null && getDeletedAt() == null && !ngay.isBefore(tuNgay) && !ngay.isAfter(denNgay);
    }

    public void thuHoi(Long boiAi, Instant luc) {
        this.thuHoiBoi = boiAi;
        this.thuHoiLuc = luc;
    }

    public Long getNguoiUyQuyenId() {
        return nguoiUyQuyenId;
    }

    public Long getNguoiDuocUyQuyenId() {
        return nguoiDuocUyQuyenId;
    }

    public LocalDate getTuNgay() {
        return tuNgay;
    }

    public LocalDate getDenNgay() {
        return denNgay;
    }

    public String getLyDo() {
        return lyDo;
    }

    public Instant getThuHoiLuc() {
        return thuHoiLuc;
    }

    public Long getThuHoiBoi() {
        return thuHoiBoi;
    }
}
