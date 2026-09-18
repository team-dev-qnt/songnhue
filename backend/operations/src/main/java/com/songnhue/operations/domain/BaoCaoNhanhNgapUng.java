package com.songnhue.operations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một dòng xã của Bảng 5 — diện tích ngập úng (ha). {@code null} = chưa nhập (ô trống), khác 0.
 *
 * <p>⛔ Cột "Cộng"/"Tổng cộng" ⛔ lưu — dẫn xuất ở {@link TinhBaoCaoNhanh} (quy tắc 3).
 */
@Entity
@Table(name = "bao_cao_nhanh_ngap_ung")
@Audited(module = "ops", entityType = "Báo cáo nhanh — diện tích ngập úng")
public class BaoCaoNhanhNgapUng extends BaseEntity {

    @Column(name = "bao_cao_id", nullable = false, updatable = false)
    private Long baoCaoId;

    @Column(name = "don_vi_hanh_chinh_id", nullable = false, updatable = false)
    private Long donViHanhChinhId;

    @Column(name = "ngap_trang_lua", precision = 12, scale = 2)
    private BigDecimal ngapTrangLua;

    @Column(name = "ngap_trang_rau", precision = 12, scale = 2)
    private BigDecimal ngapTrangRau;

    @Column(name = "sau_nuoc_lua", precision = 12, scale = 2)
    private BigDecimal sauNuocLua;

    @Column(name = "sau_nuoc_rau", precision = 12, scale = 2)
    private BigDecimal sauNuocRau;

    protected BaoCaoNhanhNgapUng() {}

    public BaoCaoNhanhNgapUng(Long baoCaoId, Long donViHanhChinhId) {
        this.baoCaoId = baoCaoId;
        this.donViHanhChinhId = donViHanhChinhId;
    }

    /** Bốn ô đi thành MỘT lượt ghi — thay toàn phần, cùng hình dạng thân gửi lên. */
    public void ghi(BigDecimal ngapTrangLua, BigDecimal ngapTrangRau, BigDecimal sauNuocLua, BigDecimal sauNuocRau) {
        this.ngapTrangLua = ngapTrangLua;
        this.ngapTrangRau = ngapTrangRau;
        this.sauNuocLua = sauNuocLua;
        this.sauNuocRau = sauNuocRau;
    }

    public Long getBaoCaoId() {
        return baoCaoId;
    }

    public Long getDonViHanhChinhId() {
        return donViHanhChinhId;
    }

    public BigDecimal getNgapTrangLua() {
        return ngapTrangLua;
    }

    public BigDecimal getNgapTrangRau() {
        return ngapTrangRau;
    }

    public BigDecimal getSauNuocLua() {
        return sauNuocLua;
    }

    public BigDecimal getSauNuocRau() {
        return sauNuocRau;
    }
}
