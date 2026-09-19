package com.songnhue.operations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một cỡ máy — một trong 9 cột của Bảng 1 Báo cáo nhanh ({@code V202609181086}).
 *
 * <p>⛔ Chỉ SỬA BIÊN được: số cỡ cố định theo mẫu Word của Công ty. Logic xếp Q ở
 * {@link BangCoMayBom}, ⛔ ở entity.
 */
@Entity
@Table(name = "co_may_bom")
@Audited(module = "ops", entityType = "Cỡ máy bơm")
public class CoMayBom extends BaseEntity {

    @Column(name = "nhan", nullable = false, length = 20)
    private String nhan;

    @Column(name = "q_tu_m3h", precision = 12, scale = 2)
    private BigDecimal qTuM3h;

    @Column(name = "q_den_m3h", precision = 12, scale = 2)
    private BigDecimal qDenM3h;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CoMayBom() {}

    public String getNhan() {
        return nhan;
    }

    public BigDecimal getQTuM3h() {
        return qTuM3h;
    }

    public BigDecimal getQDenM3h() {
        return qDenM3h;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** Hai biên đi thành CẶP — cùng lý lẽ {@code GisLayer.ganTep}. */
    public void datBien(BigDecimal tu, BigDecimal den) {
        this.qTuM3h = tu;
        this.qDenM3h = den;
    }

    public BangCoMayBom.Co toCo() {
        return new BangCoMayBom.Co(getPublicId(), nhan, qTuM3h, qDenM3h, sortOrder);
    }
}
