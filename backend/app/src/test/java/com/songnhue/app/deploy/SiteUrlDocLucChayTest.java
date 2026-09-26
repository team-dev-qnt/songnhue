package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>{@code SITE_URL} đọc LÚC CHẠY, ⛔ nướng vào ảnh lúc build</b> — T68.12.
 *
 * <h2>Khuyết tật đã đo được, 19/09/2026</h2>
 *
 * {@code staging.songnhue.com/robots.txt} khai {@code Allow: /} kèm
 * {@code Host: https://thuyloisongnhue.vn}; canonical và og:url của trang chủ staging cũng trỏ về
 * production. Thứ duy nhất còn che là header {@code x-robots-tag} đặt ở nginx biên — gỡ nó ra là
 * Google có hai bản của cùng nội dung và có thể xếp bản staging lên trước.
 *
 * <p>Nguyên nhân: Next thay {@code process.env.NEXT_PUBLIC_*} bằng một <b>chuỗi hằng lúc build</b>,
 * mà staging và production dùng <b>chung một ảnh Docker</b> ⇒ chúng buộc phải mang chung giá trị.
 * Nhánh <i>"chặn lập chỉ mục ở staging"</i> viết từ T9.3 vì thế <b>chưa bao giờ chạy</b> (luật 7).
 *
 * <h2>Vì sao cần một bộ canh cho BỐN tệp</h2>
 *
 * Bất biến này là luật 14 ở dạng rộng nhất: một giá trị, bốn nơi con người phải nhớ cùng lúc
 * ({@code site.ts} · {@code public-web.Dockerfile} · {@code ci.yml} · {@code compose.prod.yml}),
 * và <b>mỗi nơi để sót đều cho ra một hệ vẫn chạy bình thường</b>. Trả lại build-arg là tái lập
 * nguyên vẹn khuyết tật; giữ dòng {@code ENV} trong Dockerfile thì giá trị build vẫn thắng.
 *
 * <p>⚠ Vế frontend ({@code site.ts} đọc đúng biến · {@code robots.ts} gọi {@code connection()} ·
 * ⛔ thành phần máy khách nào nhập {@code SITE_URL}) nằm ở {@code robots.test.ts} — nhà của nó, và
 * nó chạy ở job Frontend. Lớp này chỉ giữ vế <b>deploy</b>.
 */
class SiteUrlDocLucChayTest {

    private static String compose;
    private static String dockerfile;
    private static String ci;

    @BeforeAll
    static void doc() throws Exception {
        Path goc = Path.of("").toAbsolutePath();
        while (goc != null && !Files.isDirectory(goc.resolve("deploy"))) {
            goc = goc.getParent();
        }
        assertThat(goc).as("⛔ tìm thấy gốc kho").isNotNull();
        compose = Files.readString(goc.resolve("deploy/compose.prod.yml"));
        dockerfile = Files.readString(goc.resolve("deploy/docker/public-web.Dockerfile"));
        ci = Files.readString(goc.resolve(".github/workflows/ci.yml"));
    }

    @Test
    @DisplayName("⚠ chống tập rỗng: đọc được cả ba tệp và chúng đúng là tệp cần đọc")
    void docDuocCaBaTep() {
        // Thiếu vế này thì một lượt đổi tên tệp làm mọi khẳng định bên dưới chạy trên chuỗi RỖNG
        // — và `not.contains(...)` trên chuỗi rỗng thì LUÔN xanh (luật 7). Đây đúng là hình dạng
        // mà ba bài `notContains` bên dưới về nguyên tắc ⛔ tự bảo vệ được.
        assertThat(compose).as("compose.prod.yml").contains("public-web:");
        assertThat(dockerfile).as("public-web.Dockerfile").contains("npm run build --workspace public-web");
        assertThat(ci).as("ci.yml").contains("NEXT_PUBLIC_API_BASE_URL=");
    }

    /**
     * Bỏ dòng chú thích {@code #}, <b>giữ</b> dòng lệnh — điều kiện để một bộ canh
     * {@code doesNotContain} nói về <i>lời thi hành</i> chứ ⛔ về <i>lời giải thích</i>.
     *
     * <p>⛔⛔ Bản đầu của lớp này quét thẳng cả tệp và <b>đỏ ngay lượt chạy đầu vì chú thích của
     * chính tôi</b> — chú thích giải thích <i>vì sao ⛔ được khai {@code NEXT_PUBLIC_SITE_URL}</i>
     * bị đọc thành một lượt khai. Đây là lần thứ <b>sáu</b> của hình dạng ấy trong dự án
     * (T46.7 · T54.8 · T49.6 · T83.4 · và một lần nữa ở {@code site.test.ts} cùng buổi), và lần
     * này nặng hơn vì <b>tôi đã lường trước nó</b>: phép kiểm {@code ci.yml} bên dưới cố ý chỉ
     * quét khối {@code build-args:} vì đúng lý do này, mà tôi ⛔ áp cùng lập luận cho Dockerfile.
     *
     * <p>⛔ Sửa chú thích cho hết đỏ là <b>xoá bài học mà vẫn để bộ canh thủng</b>.
     */
    private static String boDongChuThich(String noiDung) {
        return noiDung.lines().filter(d -> !d.strip().startsWith("#")).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("⚠ Tự kiểm: `boDongChuThich` phân biệt lời giải thích với lời thi hành")
    void boDongChuThichPhanBietDuoc() {
        // Luật 1: bài trên xanh vì tệp HÔM NAY đúng, nó ⛔ chứng minh phép bóc chú thích làm việc.
        // Thiếu vế này thì một `boDongChuThich` trả về chuỗi RỖNG sẽ làm mọi `doesNotContain`
        // bên dưới xanh VĨNH VIỄN và ⛔ ai biết (luật 7).
        String mau = "# ⛔ đừng khai NEXT_PUBLIC_SITE_URL\nARG NEXT_PUBLIC_API_BASE_URL\n";
        assertThat(boDongChuThich(mau))
                .as("phải BỎ dòng chú thích")
                .doesNotContain("NEXT_PUBLIC_SITE_URL")
                .as("và phải GIỮ dòng lệnh")
                .contains("ARG NEXT_PUBLIC_API_BASE_URL");
    }

    @Test
    @DisplayName("⛔⛔ Dockerfile ⛔ được khai `NEXT_PUBLIC_SITE_URL` — nó nướng giá trị vào ảnh")
    void dockerfileKhongConNuongSiteUrl() {
        assertThat(boDongChuThich(dockerfile))
                .as(
                        """
                        `public-web.Dockerfile` khai lại `NEXT_PUBLIC_SITE_URL`.

                        Next thay `process.env.NEXT_PUBLIC_*` bằng chuỗi hằng LÚC BUILD, nên giá trị \
                        đi thẳng vào ảnh. Staging và production dùng CHUNG ảnh này ⇒ chúng buộc phải \
                        mang chung giá trị, và robots/sitemap/canonical của staging tự nhận là \
                        production — đúng khuyết tật T68.12 vừa trả.

                        ⚠ `NEXT_PUBLIC_API_BASE_URL` thì ĐƯỢC ở lại: nó là địa chỉ TRÌNH DUYỆT gọi nên \
                        buộc phải nằm trong bundle, và mặc định của nó là đường dẫn TƯƠNG ĐỐI nên nó \
                        ⛔ mang bẫy "hai môi trường một giá trị".""")
                .doesNotContain("NEXT_PUBLIC_SITE_URL");
    }

    @Test
    @DisplayName("⛔⛔ `ci.yml` ⛔ được truyền `NEXT_PUBLIC_SITE_URL` qua build-args")
    void ciKhongConTruyenBuildArg() {
        // ⚠ Quét trên `build-args:` chứ ⛔ toàn tệp: chú thích giải thích vì sao ⛔ được truyền
        //   nữa CÓ quyền nhắc tên biến, và phạt người viết tài liệu tử tế là hình dạng đã trả giá
        //   bốn lần (T46.7 · T54.8 · T49.6).
        int i = ci.indexOf("build-args:");
        assertThat(i)
                .as("⛔ tìm thấy khối `build-args:` trong ci.yml — phản xạ đã hỏng")
                .isPositive();
        String khoi = boDongChuThich(ci.substring(i, Math.min(ci.length(), i + 400)));
        assertThat(khoi)
                .as("khối `build-args:` còn truyền `NEXT_PUBLIC_SITE_URL` ⇒ giá trị lại bị nướng vào ảnh:\n%s", khoi)
                .doesNotContain("NEXT_PUBLIC_SITE_URL");
    }

    @Test
    @DisplayName("⛔⛔ `compose.prod.yml` khai `SITE_URL` kèm `:?` — nửa rỗng còn tệ hơn rỗng hẳn")
    void composeKhaiSiteUrlVaChanNuaRong() {
        String dong = compose.lines()
                .filter(d -> d.strip().startsWith("SITE_URL:"))
                .findFirst()
                .orElse("");
        assertThat(dong)
                .as("`compose.prod.yml` ⛔ truyền `SITE_URL` lúc chạy ⇒ cổng rơi về localhost")
                .isNotEmpty();
        assertThat(dong)
                .as(
                        """
                        Dòng `SITE_URL` thiếu `:?`.

                        Giá trị dựng bằng `https://${PUBLIC_DOMAIN}`, nên một `.env` thiếu \
                        `PUBLIC_DOMAIN` cho ra đúng chuỗi `"https://"` — mà chuỗi ấy **TRUTHY**, nên \
                        `||` trong `site.ts` ⛔ cứu được, và `new URL("https://")` ném \
                        `ERR_INVALID_URL` (đo bằng Node 24/09).

                        Khác §10.38 ở chỗ nguy hiểm hơn: lần ấy build chết ầm ĩ, còn ở đây container \
                        LÊN bình thường rồi MỌI lượt dựng trang mới ném. ⇒ Hỏng phải xảy ra sớm, ở \
                        `docker compose up`.

                        Dòng đang đọc được: %s""",
                        dong)
                .contains(":?");
    }
}
