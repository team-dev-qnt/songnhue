package com.songnhue.hydro.domain;

/**
 * Vai trò vị trí của điểm đo so với công trình — chốt A2b (12/8/2026).
 *
 * <p>Xuất hiện ở <b>hai</b> chỗ, cố ý, và đây là chỗ dễ hiểu nhầm nhất của MOD-03:
 *
 * <ul>
 *   <li>{@code stations.position_role} — vai trò <b>chính thức, để hiển thị</b>. Bắt buộc, dùng cho
 *       biểu tổng hợp và nhãn trên bản đồ.
 *   <li>{@code station_constructions.role} — vai trò <b>theo từng liên kết</b>. Tồn tại vì một điểm
 *       đo hoàn toàn có thể là hạ lưu của công trình này <i>đồng thời</i> là thượng lưu của công
 *       trình kế tiếp trên cùng tuyến.
 * </ul>
 *
 * <p>Ràng buộc nối hai chỗ đó: bản ghi liên kết <b>chính</b> ({@code is_primary}) phải mang đúng vai
 * trò của cột {@code position_role}. CSDL không ép được ràng buộc liên bảng này nên nó được ép ở
 * tầng dịch vụ, và có bài kiểm riêng — nếu không thì hai giá trị lệch nhau lặng lẽ và biểu tổng hợp
 * xếp một điểm đo vào nhầm cột.
 */
public enum PositionRole {
    /** Thượng lưu công trình. */
    THUONG_LUU,

    /** Hạ lưu công trình. */
    HA_LUU,

    /** Bể hút trạm bơm. */
    BE_HUT,

    /**
     * Mực nước sông — trạm thuỷ văn tham chiếu.
     *
     * <p>⛔ Điểm đo vai trò này <b>được phép không liên kết công trình nào</b> (TV Hà Nội, TV Ba
     * Thá, An Cảnh, TB Hồng Vân — 4/19 điểm). Đó là dữ liệu <i>đủ</i>, không phải dữ liệu thiếu:
     * một inner join hay một {@code NOT NULL} đặt sai chỗ sẽ làm rớt đúng bốn điểm này khỏi mọi màn
     * hình, và triệu chứng là "bản đồ thiếu vài chấm", không phải một lỗi.
     */
    MN_SONG,

    /** Đo mưa. ⚠ v1 chưa có nguồn tự động (G3-a) — nhập tay. */
    MUA
}
