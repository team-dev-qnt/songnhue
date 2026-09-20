package com.songnhue.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một chỗ CỐ ĐỊNH của mẫu Báo cáo nhanh cần gắn một công trình — 7 cống của Bảng 3 và trạm của ghi chú
 * Yên Nghĩa. Seed cấu trúc ({@code V202609181088}); thứ Công ty chọn trên giao diện là
 * {@code construction_id} — {@code null} = chưa gắn, ô tương ứng TRỐNG kèm lý do.
 *
 * <p>⛔ lưu mã điểm đo ở đây: điểm đo của từng vế SUY RA từ liên kết điểm đo–công trình
 * ({@code station_constructions.role}), để một câu hỏi chỉ có một nơi trả lời (luật 14).
 */
@Entity
@Table(name = "bao_cao_nhanh_vi_tri")
@Audited(module = "ops", entityType = "Báo cáo nhanh — vị trí gắn công trình")
public class BaoCaoNhanhViTri extends BaseEntity {

    /** Mã vị trí của ghi chú Yên Nghĩa. */
    public static final String YEN_NGHIA = "YEN_NGHIA";

    @Column(name = "ma", nullable = false, updatable = false, length = 30)
    private String ma;

    @Column(name = "nhan", nullable = false, updatable = false)
    private String nhan;

    @Column(name = "loai_cong_trinh", nullable = false, updatable = false, length = 30)
    private String loaiCongTrinh;

    @Column(name = "construction_id")
    private Long constructionId;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    protected BaoCaoNhanhViTri() {}

    /** {@code null} gỡ công trình khỏi vị trí — ô về TRỐNG. Loại công trình do service kiểm. */
    public void ganCongTrinh(Long constructionId) {
        this.constructionId = constructionId;
    }

    public String getMa() {
        return ma;
    }

    public String getNhan() {
        return nhan;
    }

    public String getLoaiCongTrinh() {
        return loaiCongTrinh;
    }

    public Long getConstructionId() {
        return constructionId;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
