package com.songnhue.operations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một ô *"Tình hình vận hành"* của Bảng 2 — số máy ĐANG CHẠY của một nhóm máy trong một kỳ.
 *
 * <p>⭐ {@code soMayThietKe} + {@code qMotMayM3h} là ẢNH CHỤP danh mục lúc ghi — kỳ đã chốt giữ đúng
 * con số của văn bản đã gửi dù danh mục đổi sau đó ({@code V202609181087}).
 */
@Entity
@Table(name = "bao_cao_nhanh_van_hanh")
@Audited(module = "ops", entityType = "Báo cáo nhanh — vận hành trạm bơm")
public class BaoCaoNhanhVanHanh extends BaseEntity {

    @Column(name = "bao_cao_id", nullable = false, updatable = false)
    private Long baoCaoId;

    @Column(name = "nhom_may_id", nullable = false, updatable = false)
    private Long nhomMayId;

    @Column(name = "so_may_thiet_ke", nullable = false)
    private short soMayThietKe;

    @Column(name = "q_mot_may_m3h", nullable = false, precision = 12, scale = 2)
    private BigDecimal qMotMayM3h;

    /** {@code null} = CHƯA NHẬP — khác 0 ("đã kiểm, ⛔ máy nào chạy"). */
    @Column(name = "so_may_van_hanh")
    private Short soMayVanHanh;

    protected BaoCaoNhanhVanHanh() {}

    public BaoCaoNhanhVanHanh(Long baoCaoId, Long nhomMayId) {
        this.baoCaoId = baoCaoId;
        this.nhomMayId = nhomMayId;
    }

    /** Chụp lại danh mục + ghi số đang chạy — một lượt, để ảnh chụp ⛔ lệch số nhập. */
    public void ghi(short soMayThietKe, BigDecimal qMotMayM3h, Short soMayVanHanh) {
        this.soMayThietKe = soMayThietKe;
        this.qMotMayM3h = qMotMayM3h;
        this.soMayVanHanh = soMayVanHanh;
    }

    public Long getBaoCaoId() {
        return baoCaoId;
    }

    public Long getNhomMayId() {
        return nhomMayId;
    }

    public short getSoMayThietKe() {
        return soMayThietKe;
    }

    public BigDecimal getQMotMayM3h() {
        return qMotMayM3h;
    }

    public Short getSoMayVanHanh() {
        return soMayVanHanh;
    }
}
