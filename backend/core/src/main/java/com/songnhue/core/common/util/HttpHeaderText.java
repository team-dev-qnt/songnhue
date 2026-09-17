package com.songnhue.core.common.util;

/**
 * Làm sạch chuỗi trước khi ghép vào một header HTTP.
 *
 * <h2>Vì sao là một lớp dùng chung chứ không phải một hàm riêng ở mỗi controller</h2>
 *
 * <p>Tên tệp gốc do người tải lên đặt. Một tên chứa xuống dòng là chèn được header tuỳ ý vào phản
 * hồi (<i>HTTP response splitting</i>) — lỗ cũ, nhưng vẫn sống ở đúng những chỗ ghép chuỗi vào
 * header. Bảo đảm ấy phải đúng ở <b>mọi</b> endpoint phục vụ tệp, và tới 31/08/2026 nó tồn tại
 * dưới dạng một hàm {@code private} trong {@code PublicPortalController}: endpoint thứ hai phục vụ
 * tệp (tài liệu công trình — CR-28) không có cách nào dùng lại nó ngoài chép.
 *
 * <p>Đây đúng chỗ luật 14 nói tới: <i>chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép
 * kiểm nhớ hộ</i> — hoặc, rẻ hơn, một nơi duy nhất để nhớ. Đặt bảo đảm ở chỗ <b>dữ liệu đi qua</b>,
 * không ở từng <i>nơi gọi</i> (quy tắc 12, §10.31).
 */
public final class HttpHeaderText {

    /** Ký tự phá được cấu trúc header: CR, LF, dấu nháy kép và gạch chéo ngược. */
    private static final String KY_TU_PHA_HEADER = "[\\r\\n\"\\\\]";

    private HttpHeaderText() {}

    /**
     * Tên tệp an toàn để đặt vào {@code Content-Disposition}.
     *
     * @param originalName tên người dùng đặt lúc tải lên; {@code null} hoặc rỗng đều chấp nhận
     * @return tên đã thay mọi ký tự phá header bằng {@code _}; {@code "tep"} khi không có tên
     */
    public static String tenTepAnToan(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "tep";
        }
        return originalName.replaceAll(KY_TU_PHA_HEADER, "_");
    }

    /**
     * Giá trị {@code Content-Disposition} đầy đủ — <b>hai dạng tên</b>, T61.40 (ASVS 12.3.4, RFC 5987).
     *
     * <h2>Vì sao ⛔ chỉ {@code filename="…"}</h2>
     *
     * <p>Header HTTP là ASCII. Một tên tệp tiếng Việt đặt trần vào {@code filename=} sẽ bị máy khách
     * đọc sai hoặc bị ta thay bằng {@code _} ⇒ người dùng nhận về {@code Quy_t____nh_2026.pdf}. Năm
     * đường phát tệp TRỰC TIẾP của hệ (bản kết xuất liên hệ, báo cáo nhân sự, báo cáo thuỷ văn, ZIP hồ
     * sơ, tệp cổng công khai) đều đang như vậy, trong khi đường đi qua MinIO presigned thì ⛔ —
     * {@code ObjectStorage} đã làm đúng từ đầu. Hai đường tải cùng một hệ, hai kết quả khác nhau.
     *
     * <p>⇒ Dạng ASCII cho máy khách cũ, {@code filename*=UTF-8''…} cho mọi trình duyệt còn lại; trình
     * duyệt hiện đại ưu tiên dạng sau.
     */
    public static String contentDisposition(String tenGoi) {
        return contentDisposition("attachment", tenGoi);
    }

    /**
     * Dạng {@code inline} — ảnh/PDF hiện TRONG trang thay vì bật hộp thoại tải về.
     *
     * <p>⚠ Vẫn cần {@code filename*}: người dùng bấm *"Lưu ảnh"* thì tên gợi ý lấy từ đây.
     */
    public static String contentDispositionInline(String tenGoi) {
        return contentDisposition("inline", tenGoi);
    }

    private static String contentDisposition(String kieu, String tenGoi) {
        String ten = tenTepAnToan(tenGoi);
        String ascii = ten.replaceAll("[^\\x20-\\x7E]", "_");
        String maHoa = java.net.URLEncoder.encode(ten, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "%s; filename=\"%s\"; filename*=UTF-8''%s".formatted(kieu, ascii, maHoa);
    }
}
