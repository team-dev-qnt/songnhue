package com.songnhue.operations.domain;

/**
 * Loại hình học của một lớp bản đồ — CN-02.4 / M2.9.
 *
 * <p>⛔ Khai ra ở bản ghi chứ ⛔ <b>không</b> suy lúc vẽ. Một tệp GeoJSON trộn
 * {@code Point}/{@code LineString}/{@code Polygon} là hợp lệ, nhưng bảng chọn kiểu vẽ (marker hay
 * nét hay vùng) phải biết <b>trước</b> khi tải nội dung về — ⛔ không thì mỗi lần bật một lớp là
 * một lượt tải vài MB chỉ để biết nên vẽ thế nào.
 *
 * <p>Bộ ba enum ↔ union TypeScript ↔ {@code ck_gis_layers_geometry_type} — {@code EnumBaNoiTest}.
 */
public enum GisGeometryType {
    POINT,
    LINE,
    POLYGON,
    /** Tệp có nhiều loại hình học — giao diện vẽ từng đối tượng theo loại thật của nó. */
    HON_HOP;

    /**
     * Suy loại từ tập kiểu hình học <b>đọc được trong tệp</b>.
     *
     * <p>⛔ Tập rỗng ⇒ ném: một tệp GeoJSON ⛔ không có đối tượng nào là một tệp ⛔ không vẽ được gì,
     * và nhận nó vào là để một lớp "đã nạp thành công" hiện ra một bản đồ trống — đúng thứ người
     * dùng đọc thành *"hệ thống hỏng"*.
     */
    public static GisGeometryType tuTapKieu(java.util.Set<String> kieu) {
        if (kieu == null || kieu.isEmpty()) {
            throw new IllegalArgumentException("Tệp ⛔ không có đối tượng hình học nào");
        }
        java.util.Set<GisGeometryType> gom = new java.util.LinkedHashSet<>();
        for (String k : kieu) {
            gom.add(
                    switch (k == null ? "" : k) {
                        case "Point", "MultiPoint" -> POINT;
                        case "LineString", "MultiLineString" -> LINE;
                        case "Polygon", "MultiPolygon" -> POLYGON;
                        default -> HON_HOP;
                    });
        }
        return gom.size() == 1 ? gom.iterator().next() : HON_HOP;
    }
}
