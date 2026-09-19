package com.songnhue.operations.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.persistence.WorkflowReasonAware;

/**
 * Một kỳ Báo cáo nhanh — *"Từ 6h ngày … đến 16h ngày …"*.
 *
 * <p>Bản ghi CÓ VÒNG ĐỜI: số liệu nhập tay (Bảng 2, Bảng 5) thuộc về một kỳ, và kỳ đã chốt là văn bản
 * ĐÃ GỬI UBND. ⛔ Vì vậy nó ⛔ là một mã trong {@code MaBaoCaoVanHanh} (chọn mã → tải CSV, ⛔ trạng thái).
 *
 * <p>⛔ KHÔNG phải {@code ScopedEntity} — văn bản cấp Công ty (xem {@code NhomMayBom}).
 */
@Entity
@Table(name = "bao_cao_nhanh")
@Audited(module = "ops", entityType = "Báo cáo nhanh")
public class BaoCaoNhanh extends BaseEntity implements WorkflowReasonAware {

    /** Khớp {@code workflow_definitions.entity_type}. */
    public static final String ENTITY_TYPE = "QUICK_REPORT";

    @Column(name = "tu_thoi_diem", nullable = false)
    private Instant tuThoiDiem;

    @Column(name = "den_thoi_diem", nullable = false)
    private Instant denThoiDiem;

    @Enumerated(EnumType.STRING)
    @Column(name = "trang_thai", nullable = false, length = 20)
    private TrangThaiBaoCaoNhanh trangThai;

    /** Lý do của lượt MỞ LẠI gần nhất — vào {@code audit_logs} (chuỗi băm) cùng ai bấm, lúc nào. */
    @Column(name = "ly_do_mo_lai", length = 1000)
    private String lyDoMoLai;

    protected BaoCaoNhanh() {}

    /** @param trangThaiDau lấy từ {@code WorkflowPort.initialState} — ⛔ gõ tay */
    public BaoCaoNhanh(Instant tuThoiDiem, Instant denThoiDiem, String trangThaiDau) {
        this.tuThoiDiem = tuThoiDiem;
        this.denThoiDiem = denThoiDiem;
        this.trangThai = TrangThaiBaoCaoNhanh.valueOf(trangThaiDau);
    }

    public Instant getTuThoiDiem() {
        return tuThoiDiem;
    }

    public Instant getDenThoiDiem() {
        return denThoiDiem;
    }

    public void datKhung(Instant tu, Instant den) {
        this.tuThoiDiem = tu;
        this.denThoiDiem = den;
    }

    public TrangThaiBaoCaoNhanh getTrangThai() {
        return trangThai;
    }

    public boolean daChot() {
        return trangThai == TrangThaiBaoCaoNhanh.DA_CHOT;
    }

    public String getLyDoMoLai() {
        return lyDoMoLai;
    }

    @Override
    public String workflowEntityType() {
        return ENTITY_TYPE;
    }

    @Override
    public String currentState() {
        return trangThai.name();
    }

    /** ⛔ Chỉ {@code WorkflowEngine} gọi — đường ghi trạng thái DUY NHẤT. */
    @Override
    public void applyState(String newState) {
        this.trangThai = TrangThaiBaoCaoNhanh.valueOf(newState);
    }

    @Override
    public Long entityId() {
        return getId();
    }

    /**
     * Chỉ lượt MỞ LẠI mang lý do. Lượt CHỐT kế tiếp ⛔ xoá nó: lý do mở lại gần nhất vẫn là câu trả
     * lời đúng cho *"vì sao văn bản này khác bản đã gửi lần đầu"*.
     */
    @Override
    public void applyWorkflowReason(String action, String reason) {
        if ("MO_LAI".equals(action)) {
            this.lyDoMoLai = reason;
        }
    }
}
