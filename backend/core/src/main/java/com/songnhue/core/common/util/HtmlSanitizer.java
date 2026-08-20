package com.songnhue.core.common.util;

import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * Khử trùng HTML do người soạn nội dung nhập — WS-16.
 *
 * <h2>Vì sao cần, khi nội dung đã đi qua quy trình duyệt</h2>
 *
 * Quy trình duyệt bảo đảm <i>ai đó đã đọc bài</i>, không bảo đảm <i>không có mã chạy được trong
 * đó</i> — người duyệt nhìn nội dung hiển thị, không nhìn mã nguồn HTML. Ba đường vào thật:
 *
 * <ul>
 *   <li>Dán nội dung từ Word hoặc từ một trang web, kéo theo thẻ và thuộc tính không ai để ý.
 *   <li>Tài khoản biên tập viên bị chiếm — chính là loại tài khoản dễ bị nhắm nhất vì nó không
 *       phải Admin nên ít ai canh.
 *   <li>Trình soạn thảo có nút "xem mã HTML", và spec CN-01.1 yêu cầu có nút đó.
 * </ul>
 *
 * <p>Kết quả của một lỗ ở đây không dừng ở cổng công khai: cùng chuỗi HTML đó hiển thị trong màn
 * hình xem trước của {@code admin-app}, nơi người xem đang <b>đăng nhập</b>.
 *
 * <h2>Danh sách CHO PHÉP, không phải danh sách chặn</h2>
 *
 * Ngược với {@code SvgSanitizer} — ở đó chấp nhận danh sách chặn vì người tải là Quản trị viên và
 * số tệp đếm trên đầu ngón tay. Ở đây thì không: nội dung nhiều, người soạn nhiều, và HTML có quá
 * nhiều cách viết cùng một thứ. Chỉ những thẻ nằm trong danh sách mới đi qua; mọi thứ khác bị gỡ
 * <i>nhưng giữ lại phần chữ bên trong</i>, nên bài viết không mất nội dung.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    /** Máy chủ nhúng bản đồ được chấp nhận. Thêm tên miền vào đây là một quyết định, không phải cấu hình. */
    private static final List<String> MIEN_NHUNG_BAN_DO =
            List.of("google.com", "www.google.com", "maps.google.com", "goo.gl", "maps.app.goo.gl");

    /**
     * Nội dung bài viết và các khối văn bản có định dạng.
     *
     * <p>{@code relaxed()} của jsoup đã gồm tiêu đề, danh sách, bảng, liên kết và ảnh. Thêm vào ba
     * thứ mà trình soạn thảo hay sinh ra: {@code figure}/{@code figcaption} cho ảnh có chú thích, và
     * thuộc tính {@code class} để giữ được định dạng căn lề.
     *
     * <p>⛔ {@code <script>}, {@code <iframe>}, {@code <object>} và <b>mọi</b> thuộc tính
     * {@code on*} nằm ngoài danh sách nên bị gỡ — đó là toàn bộ mục đích.
     */
    private static final Safelist NOI_DUNG = Safelist.relaxed()
            .addTags("figure", "figcaption", "hr", "span")
            .addAttributes(":all", "class")
            .addAttributes("img", "loading", "width", "height")
            // `rel=noopener` để liên kết mở tab mới không trao quyền điều khiển tab gốc cho trang đích
            .addAttributes("a", "target", "rel")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            // ⚠⚠ BẮT BUỘC, và bản đầu thiếu nó. Ảnh chèn giữa bài trỏ tới `/api/v1/public/files/<id>`
            // — một đường dẫn TƯƠNG ĐỐI. jsoup mặc định coi mọi giá trị không khớp giao thức cho phép
            // là không an toàn và gỡ thuộc tính đó đi, nên MỌI ảnh trong MỌI bài viết biến mất lặng lẽ
            // ngay lượt lưu kế tiếp. `MediaLibraryTest` bắt được vì nó dò tham chiếu ảnh trong nội dung.
            //
            // Giữ đường dẫn tương đối KHÔNG mở lại lỗ `javascript:`: chuỗi đó mang giao thức nên vẫn
            // bị đối chiếu với danh sách http/https và vẫn bị gỡ. Có bài kiểm riêng cho đúng điểm này.
            .preserveRelativeLinks(true);

    /**
     * Khối nhúng bản đồ — <b>chỉ</b> một {@code <iframe>} trỏ tới máy chủ bản đồ đã biết.
     *
     * <p>Đây là ngoại lệ duy nhất cho {@code iframe} trong toàn hệ thống, và nó hẹp tới mức gần như
     * không còn là ngoại lệ: sai tên miền thì thẻ bị gỡ luôn.
     */
    private static final Safelist NHUNG_BAN_DO = new Safelist()
            .addTags("iframe")
            .addAttributes("iframe", "src", "width", "height", "style", "loading", "allowfullscreen")
            .addProtocols("iframe", "src", "https");

    /**
     * Làm sạch nội dung có định dạng.
     *
     * @return HTML đã lọc; {@code null} vào thì {@code null} ra, vì "chưa nhập gì" khác với "nhập
     *     một chuỗi rỗng"
     */
    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, "", NOI_DUNG);
    }

    /**
     * Làm sạch mã nhúng bản đồ, và <b>từ chối</b> iframe trỏ ra ngoài danh sách tên miền.
     *
     * <p>Lọc bằng safelist thôi thì chưa đủ: safelist cho phép {@code src} với giao thức https, tức
     * là một iframe trỏ tới máy chủ bất kỳ vẫn đi qua. Trang bên trong iframe không đọc được nội
     * dung trang cha, nhưng nó vẽ được một biểu mẫu đăng nhập giả ngay giữa chân trang của cơ quan
     * nhà nước — và người dùng không có cách nào phân biệt.
     *
     * @return chuỗi rỗng khi không còn iframe hợp lệ nào; nơi gọi hiểu là "không có khối bản đồ"
     */
    public static String cleanMapEmbed(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Document document = Jsoup.parseBodyFragment(Jsoup.clean(html, "", NHUNG_BAN_DO));
        document.select("iframe").forEach(iframe -> {
            if (!mienDuocPhep(iframe.attr("src"))) {
                iframe.remove();
            }
        });
        return document.body().html();
    }

    /** Có phải nội dung này đã sạch không — dùng cho bài kiểm và cho phép kiểm chứng ngược. */
    public static boolean coMaChayDuoc(String html) {
        if (html == null) {
            return false;
        }
        String thuong = html.toLowerCase(Locale.ROOT);
        return thuong.contains("<script")
                || thuong.contains("javascript:")
                || thuong.matches("(?s).*\\son[a-z]+\\s*=.*");
    }

    private static boolean mienDuocPhep(String src) {
        if (src == null || src.isBlank()) {
            return false;
        }
        try {
            String host = java.net.URI.create(src).getHost();
            if (host == null) {
                return false;
            }
            String thuong = host.toLowerCase(Locale.ROOT);
            // So khớp cả tên miền con: `maps.google.com` khớp `google.com`. Dùng hậu tố có dấu chấm
            // để `evilgoogle.com` KHÔNG khớp — đây là bẫy kinh điển của phép so khớp tên miền.
            return MIEN_NHUNG_BAN_DO.stream().anyMatch(m -> thuong.equals(m) || thuong.endsWith("." + m));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Chỉ giữ phần chữ — dùng cho tóm tắt và thẻ mô tả SEO, nơi HTML không có tác dụng gì. */
    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Element body = Jsoup.parseBodyFragment(html).body();
        return body.text();
    }
}
