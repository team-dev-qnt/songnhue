package com.songnhue.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Ảnh chụp cấu hình một vị trí LÚC CHỐT kỳ — văn bản đã gửi ⛔ đổi khi Công ty gắn lại công trình hay
 * liên kết lại điểm đo sau đó. {@code apiTl}/{@code apiHl} {@code null} = vế ấy ⛔ có điểm đo lúc chốt.
 */
@Entity
@Table(name = "bao_cao_nhanh_vi_tri_ky")
@Audited(module = "ops", entityType = "Báo cáo nhanh — ảnh chụp vị trí")
public class BaoCaoNhanhViTriKy extends BaseEntity {

    @Column(name = "bao_cao_id", nullable = false, updatable = false)
    private Long baoCaoId;

    @Column(name = "vi_tri_id", nullable = false, updatable = false)
    private Long viTriId;

    @Column(name = "construction_id")
    private Long constructionId;

    @Column(name = "api_tl", length = 50)
    private String apiTl;

    @Column(name = "api_hl", length = 50)
    private String apiHl;

    protected BaoCaoNhanhViTriKy() {}

    public BaoCaoNhanhViTriKy(Long baoCaoId, Long viTriId) {
        this.baoCaoId = baoCaoId;
        this.viTriId = viTriId;
    }

    /** Ba ô đi thành MỘT lượt ghi — lượt chốt lại sau khi mở lại ghi đè trọn. */
    public void ghi(Long constructionId, String apiTl, String apiHl) {
        this.constructionId = constructionId;
        this.apiTl = apiTl;
        this.apiHl = apiHl;
    }

    public Long getBaoCaoId() {
        return baoCaoId;
    }

    public Long getViTriId() {
        return viTriId;
    }

    public Long getConstructionId() {
        return constructionId;
    }

    public String getApiTl() {
        return apiTl;
    }

    public String getApiHl() {
        return apiHl;
    }
}
