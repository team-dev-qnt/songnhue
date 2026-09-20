package com.songnhue.operations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một điểm mưa của Bảng 4 trong một kỳ — lượng mưa (mm) NHẬP TAY. {@code null} = chưa nhập (ô trống),
 * khác 0 mm. Nguồn tự động (G3-a) chưa có — ngày có thì THAY ô nhập, ⛔ trộn hai nguồn.
 */
@Entity
@Table(name = "bao_cao_nhanh_luong_mua")
@Audited(module = "ops", entityType = "Báo cáo nhanh — lượng mưa")
public class BaoCaoNhanhLuongMua extends BaseEntity {

    @Column(name = "bao_cao_id", nullable = false, updatable = false)
    private Long baoCaoId;

    @Column(name = "diem_mua_id", nullable = false, updatable = false)
    private Long diemMuaId;

    @Column(name = "luong_mua_mm", precision = 8, scale = 1)
    private BigDecimal luongMuaMm;

    protected BaoCaoNhanhLuongMua() {}

    public BaoCaoNhanhLuongMua(Long baoCaoId, Long diemMuaId) {
        this.baoCaoId = baoCaoId;
        this.diemMuaId = diemMuaId;
    }

    public void ghi(BigDecimal luongMuaMm) {
        this.luongMuaMm = luongMuaMm;
    }

    public Long getBaoCaoId() {
        return baoCaoId;
    }

    public Long getDiemMuaId() {
        return diemMuaId;
    }

    public BigDecimal getLuongMuaMm() {
        return luongMuaMm;
    }
}
