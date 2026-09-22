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
     * Máy chủ nhúng video được chấp nhận trong nội dung bài — CN-01.1 yêu cầu embed YouTube/Vimeo.
     *
     * <p>⚠ Chỉ tên miền <b>nhúng</b>, không phải tên miền xem. {@code youtube.com/watch?v=…} là trang
     * xem đầy đủ; nhúng nó vào một khung là đưa cả thanh điều hướng và phần gợi ý video của YouTube
     * vào giữa bài của cơ quan nhà nước. Đường nhúng đúng là {@code youtube-nocookie.com/embed/…}
     * hoặc {@code player.vimeo.com/video/…}.
     *
     * <p>Ưu tiên {@code youtube-nocookie.com}: bản thường đặt cookie theo dõi ngay khi trang tải, kể
     * cả khi người đọc không bấm phát — với cổng thông tin của cơ quan nhà nước thì đó là chuyện
     * không nên có. Vẫn nhận {@code youtube.com} vì nội dung cũ và đường dán tay đều dùng nó.
     */
    private static final List<String> MIEN_NHUNG_VIDEO = List.of(
            "youtube.com", "www.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com", "player.vimeo.com");

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
            // ⚠ `s` không nằm trong `relaxed()` — jsoup chỉ có `strike`, thẻ đã bị HTML5 loại bỏ.
            // Mọi trình soạn thảo hiện đại phát ra `<s>`, nên thiếu dòng này thì nút "gạch ngang"
            // bấm được, lưu xong báo thành công, và định dạng biến mất khi mở lại. Bài kiểm liên
            // ngôn ngữ `EditorVocabularyTest` bắt được đúng chỗ này ở lượt chạy đầu tiên.
            .addTags("figure", "figcaption", "hr", "span", "s")
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
            .preserveRelativeLinks(true)
            // Khung nhúng video — CN-01.1. Safelist chỉ cho thẻ đi qua; TÊN MIỀN được lọc riêng ở
            // `clean()`, vì safelist của jsoup chỉ biết giao thức chứ không biết máy chủ.
            .addTags("iframe")
            .addAttributes("iframe", "src", "width", "height", "title", "allow", "allowfullscreen", "loading")
            .addProtocols("iframe", "src", "https")
            // ⚠⚠ Video TẢI LÊN — T84.14. `relaxed()` ⛔ có `video`/`source` (đo bằng `jshell` trên
            // jsoup 1.23.2: đầu vào `<video src="/a"></video><source src="/b">` cho ra CHUỖI RỖNG).
            //
            // ⛔⛔ `addProtocols` là BẮT BUỘC và KHÔNG ĐỦ — cả hai vế đều đo được:
            //   · thiếu nó  ⇒ `src="javascript:alert(1)"` LỌT NGUYÊN VẸN;
            //   · có nó     ⇒ `src="https://evil.example/x.mp4"` và `src="//evil.example/x.mp4"`
            //                 VẪN LỌT, vì safelist của jsoup chỉ biết GIAO THỨC, ⛔ biết ĐÍCH.
            // ⇒ Lượt lọc DOM thứ hai `locVideoTheoDuong` ở `clean()`, cùng khuôn `locIframeTheoMien`.
            //
            // ⚠ `playsinline` phải khai tường minh: thiếu nó thì Safelist gỡ, và iOS ép video chạy
            //   TOÀN MÀN HÌNH — một bài tin mở ra là một video chiếm hết màn hình điện thoại.
            .addTags("video", "source")
            .addAttributes("video", "src", "controls", "preload", "playsinline", "width", "height")
            .addAttributes("source", "src", "type")
            .addProtocols("video", "src", "https")
            .addProtocols("source", "src", "https");

    /**
     * Tiền tố đường dẫn <b>duy nhất</b> một {@code <video>} được phép trỏ tới — T84.14.
     *
     * <p>Hẹp hơn hẳn danh sách tên miền của iframe, và cố ý: video nhúng đi qua
     * {@code GET /api/v1/public/videos/&#123;id&#125;}, một endpoint của chính hệ này. ⛔ Có lý do
     * nào để một {@code <video>} trong bài viết trỏ ra ngoài — ai muốn nhúng video của bên thứ ba
     * thì dùng {@code <iframe>} và đi qua {@code MIEN_NHUNG_VIDEO}.
     */
    private static final String TIEN_TO_VIDEO_NOI_BO = "/api/v1/public/videos/";

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
        // Hai lượt lọc DOM sau safelist, mỗi lượt cho một thẻ mà jsoup chỉ biết giao thức chứ ⛔
        // biết đích: `<iframe>` lọc theo TÊN MIỀN, `<video>` lọc theo TIỀN TỐ ĐƯỜNG DẪN.
        return locVideoTheoDuong(locIframeTheoMien(Jsoup.clean(html, "", NOI_DUNG), MIEN_NHUNG_VIDEO));
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
        return locIframeTheoMien(Jsoup.clean(html, "", NHUNG_BAN_DO), MIEN_NHUNG_BAN_DO);
    }

    /**
     * Gỡ mọi {@code <iframe>} trỏ ra ngoài danh sách tên miền.
     *
     * <p>Bước bắt buộc thứ hai sau safelist, và <b>không thay thế được bằng safelist</b>: safelist
     * của jsoup chỉ biết giao thức, nên nó cho qua một iframe https trỏ tới máy chủ bất kỳ. Trang bên
     * trong iframe không đọc được nội dung trang cha, nhưng nó vẽ được một biểu mẫu đăng nhập giả ở
     * giữa bài của cơ quan nhà nước — và người dùng không có cách nào phân biệt.
     */
    private static String locIframeTheoMien(String daLamSach, List<String> mienChoPhep) {
        if (!daLamSach.contains("<iframe")) {
            // Đường đi thường gặp: bài viết không nhúng gì. Bỏ qua một lượt phân tích DOM.
            return daLamSach;
        }
        Document document = Jsoup.parseBodyFragment(daLamSach);
        document.select("iframe").forEach(iframe -> {
            if (!mienDuocPhep(iframe.attr("src"), mienChoPhep)) {
                iframe.remove();
            }
        });
        return document.body().html();
    }

    /**
     * Gỡ mọi {@code <video>}/{@code <source>} ⛔ trỏ vào endpoint video của chính hệ này — T84.14.
     *
     * <p><b>Bước bắt buộc thứ hai sau safelist</b>, cùng lý do với {@link #locIframeTheoMien} và
     * cùng bằng chứng đo được: {@code addProtocols("video","src","https")} cho
     * {@code https://evil.example/x.mp4} <b>và</b> {@code //evil.example/x.mp4} đi qua, vì safelist
     * của jsoup chỉ biết <b>giao thức</b>, ⛔ biết <b>đích</b>.
     *
     * <p>⛔⛔ Ở thẻ {@code <video>} hậu quả nặng hơn iframe một bậc: trình duyệt <b>tự tải</b> nội
     * dung của {@code src} ngay khi dựng trang (kể cả {@code preload="metadata"}), nên một địa chỉ
     * lạ trong thân bài là một <b>đèn hiệu</b> báo cho máy chủ của người khác biết ai đang đọc bài
     * nào, trên IP nào — ⛔ cần người đọc bấm gì.
     *
     * <p>⚠ Gỡ <b>cả thẻ</b> chứ ⛔ chỉ thuộc tính: một {@code <video controls>} ⛔ nguồn là một ô
     * đen giữa bài, trông y hệt một lỗi tải — người đọc sẽ bấm F5 mà ⛔ bao giờ hiện ra gì.
     */
    private static String locVideoTheoDuong(String daLamSach) {
        if (!daLamSach.contains("<video") && !daLamSach.contains("<source")) {
            return daLamSach;
        }
        Document document = Jsoup.parseBodyFragment(daLamSach);
        document.select("video").forEach(video -> {
            // `<source>` con cũng phải qua cùng phép kiểm — một `<video>` hợp lệ bọc một `<source>`
            // trỏ ra ngoài vẫn tải về từ địa chỉ lạ.
            video.select("source").forEach(nguon -> {
                if (!duongVideoNoiBo(nguon.attr("src"))) {
                    nguon.remove();
                }
            });
            boolean tuThanCoNguon = duongVideoNoiBo(video.attr("src"));
            if (!tuThanCoNguon && video.select("source").isEmpty()) {
                video.remove();
            } else if (!tuThanCoNguon && video.hasAttr("src")) {
                // Còn `<source>` hợp lệ nhưng `src` của chính thẻ trỏ ra ngoài ⇒ gỡ thuộc tính ấy,
                // ⛔ gỡ cả thẻ: trình duyệt ưu tiên `src` của thẻ, nên để lại là để nguyên lỗ hổng.
                video.removeAttr("src");
            }
        });
        // `<source>` lạc ngoài mọi `<video>` ⛔ phát gì, nhưng cũng ⛔ có lý do tồn tại.
        document.select("source").forEach(nguon -> {
            if (nguon.parent() == null || !"video".equals(nguon.parent().tagName())) {
                nguon.remove();
            }
        });
        return document.body().html();
    }

    private static boolean duongVideoNoiBo(String src) {
        return src != null && src.startsWith(TIEN_TO_VIDEO_NOI_BO);
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

    private static boolean mienDuocPhep(String src, List<String> mienChoPhep) {
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
            return mienChoPhep.stream().anyMatch(m -> thuong.equals(m) || thuong.endsWith("." + m));
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
