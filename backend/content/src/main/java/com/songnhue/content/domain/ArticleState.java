package com.songnhue.content.domain;

/**
 * Tên các trạng thái của quy trình bài viết.
 *
 * <p>⚠ <b>Đây KHÔNG phải máy trạng thái.</b> Luật chuyển trạng thái nằm ở {@code
 * workflow_definitions} + {@code workflow_transitions}, seed bằng migration (quy tắc 4 của dự án).
 * Lớp này chỉ để mã Java khỏi rải chuỗi ma thuật khi cần <i>so sánh</i> trạng thái — VD lọc danh
 * sách, hoặc quyết định có ghi phiên bản mới hay không.
 *
 * <p>⛔ Thêm hằng ở đây <b>không</b> tạo ra trạng thái mới, và cũng không có tác dụng gì nếu không
 * thêm dòng tương ứng vào migration. Chiều ngược lại nguy hiểm hơn: thêm trạng thái trong CSDL mà
 * quên ở đây thì mã so sánh trạng thái lặng lẽ rơi vào nhánh sai.
 */
public final class ArticleState {

    /** Đang soạn. Chỉ tác giả và Admin xem được. */
    public static final String NHAP = "NHAP";

    /** Đã gửi, khoá chỉnh sửa, chờ Quản trị nội dung xử lý. */
    public static final String CHO_DUYET = "CHO_DUYET";

    /** Bị trả về kèm lý do ở {@code review_note}. Trạng thái riêng chứ không quay về Nháp — giữ dấu vết. */
    public static final String YEU_CAU_CHINH_SUA = "YEU_CAU_CHINH_SUA";

    /** Đã duyệt. Có hiện trên cổng hay chưa còn phụ thuộc {@code published_at}. */
    public static final String XUAT_BAN = "XUAT_BAN";

    /** Gỡ khỏi cổng, giữ nguyên dữ liệu. Đường dẫn công khai trả 404. */
    public static final String GO_BAI = "GO_BAI";

    /** Ẩn khỏi danh sách nhưng vẫn vào được bằng đường dẫn trực tiếp. */
    public static final String LUU_TRU = "LUU_TRU";

    private ArticleState() {}
}
