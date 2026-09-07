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
}
