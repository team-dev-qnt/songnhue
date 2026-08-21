package com.songnhue.operations.domain;

/**
 * Loại công trình — CN-02.1.
 *
 * <p>Quyết định bảng thông số kỹ thuật nào có hiệu lực và biểu tượng nào hiện trên bản đồ GIS.
 *
 * <p>⛔ Đây là <b>enum trong mã</b>, không phải danh mục có CRUD — khác hẳn "mã tình hình vận hành"
 * (CN-02.11) hay "mức ngưỡng" (quy tắc 16). Lý do: thêm một loại công trình không phải là thêm một
 * dòng dữ liệu mà là thêm một bộ thông số kỹ thuật với bảng riêng, biểu mẫu riêng, biểu tượng riêng
 * — tức là phải sửa mã dù có bảng danh mục hay không. Bảng danh mục ở đây chỉ tạo ảo giác linh hoạt.
 */
public enum ConstructionType {
    /** Trạm bơm — hồ sơ ở {@code pump_station_specs}. */
    TRAM_BOM,

    /** Cống điều tiết — hồ sơ ở {@code sluice_specs}. */
    CONG,

    /** Kênh mương — công trình tuyến, hồ sơ ở {@code linear_specs}. */
    KENH_MUONG,

    /** Đê điều — công trình tuyến, hồ sơ ở {@code linear_specs}. */
    DE_DIEU,

    /** Loại khác — chỉ hồ sơ chung, không có bảng thông số riêng. */
    KHAC;

    /** Công trình tuyến (kênh, đê) dùng chung bảng thông số {@code linear_specs}. */
    public boolean laCongTrinhTuyen() {
        return this == KENH_MUONG || this == DE_DIEU;
    }
}
