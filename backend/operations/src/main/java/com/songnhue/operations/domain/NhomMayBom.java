package com.songnhue.operations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một nhóm máy của một trạm bơm — một dòng Bảng 2 Báo cáo nhanh.
 *
 * <h2>⛔ Q lưu m³/h NGUYÊN BẢN</h2>
 *
 * <p>⛔ Tái dùng {@code pump_station_specs.flow_per_pump_m3s}: vòng khứ hồi qua m³/s ba lẻ làm hỏng
 * 801/830 máy của danh mục thật, và tổng chỉ lệch −240 m³/h nên ⛔ phép kiểm tổng nào đỏ. Xem
 * {@code V202609181086}.
 *
 * <h2>⛔ KHÔNG phải {@code ScopedEntity}</h2>
 *
 * <p>Báo cáo nhanh là văn bản <b>cấp Công ty</b> gửi UBND — Bảng 2 liệt kê trạm của MỌI Xí nghiệp.
 * Cắt theo phạm vi ở đây là để mỗi Xí nghiệp dựng ra một Bảng 1 khác nhau cho cùng một kỳ, và ⛔ bản
 * nào đúng. Cổng là mã quyền ({@code ops:report:view}), cùng quyết định với {@code GisLayer}.
 */
@Entity
@Table(name = "nhom_may_bom")
@Audited(module = "ops", entityType = "Nhóm máy bơm")
public class NhomMayBom extends BaseEntity {

    @Column(name = "construction_id", nullable = false, updatable = false)
    private Long constructionId;

    @Column(name = "so_may", nullable = false)
    private short soMay;

    @Column(name = "q_mot_may_m3h", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal qMotMayM3h;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected NhomMayBom() {}

    /**
     * ⛔ Q và công trình là KHOÁ của nhóm ({@code ux_nhom_may_bom_cap}) nên {@code updatable = false}:
     * đổi Q của một nhóm là một nhóm KHÁC, và mọi kỳ báo cáo đã chốt vẫn trỏ vào nhóm cũ.
     */
    public NhomMayBom(Long constructionId, short soMay, BigDecimal qMotMayM3h, int sortOrder) {
        this.constructionId = constructionId;
        this.soMay = soMay;
        this.qMotMayM3h = qMotMayM3h;
        this.sortOrder = sortOrder;
    }

    public Long getConstructionId() {
        return constructionId;
    }

    public short getSoMay() {
        return soMay;
    }

    public void setSoMay(short soMay) {
        this.soMay = soMay;
    }

    public BigDecimal getQMotMayM3h() {
        return qMotMayM3h;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
