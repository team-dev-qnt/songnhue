package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Khoá bí mật reCAPTCHA đi bằng BIẾN MÔI TRƯỜNG, ⛔ không bằng bảng {@code settings}.</b>
 * CN-01.4 — T36.6.
 *
 * <h2>⛔⛔ Vì sao đây là một bất biến đáng có bộ canh riêng</h2>
 *
 * <p>Bảng {@code settings} nhóm {@code SITE} đi <b>thẳng</b> ra
 * {@code GET /api/v1/public/site-config} — một endpoint ⛔ <b>không cần đăng nhập</b>
 * ({@code SiteConfigService.effectiveValues} trả trọn hai nhóm SITE + COMPANY). Một credential đặt
 * nhầm vào đó ⛔ không hỏng gì cả: màn hình cấu hình vẫn hiện một ô nhập trông rất hợp lý, người
 * quản trị dán khoá vào, mọi thứ "chạy" — và khoá ấy được phát cho bất kỳ ai mở cổng.
 *
 * <p>⚠ Cùng lúc đó, khoá <b>công khai</b> (site key) thì <i>phải</i> ở {@code settings}: Google in
 * nó ra trong HTML, và Công ty phải đổi được ⛔ không cần deploy. Hai khoá nghe giống nhau, nằm
 * cạnh nhau trong tài liệu của Google, và chỉ khác nhau ở đúng chỗ ⛔ không ai nhìn.
 *
 * <h2>⚠ Bài này canh phần CẤU HÌNH & TRIỂN KHAI</h2>
 *
 * <p>Phần <b>dữ liệu đang phục vụ</b> do
 * {@code ContactFormPolicyHttpTest#cauHinhCongKhaiKhongLoKhoaBiMat} canh — nó gọi endpoint công
 * khai thật và đếm trên bảng {@code settings} thật. Hai bài, hai tầng, ⛔ không thay thế nhau: bài
 * kia ⛔ không thấy một tệp migration chưa được áp, bài này ⛔ không thấy một hàng ai đó
 * {@code INSERT} bằng tay.
 *
 * <p>⭐ Cả hai tên đều đã {@code grep} trước khi viết vào chú thích ở migration và ở
 * {@code local.env.example} — hai lượt trước của kho này đều nêu tên một bài kiểm <b>⛔ không tồn
 * tại</b> ({@code CongTacTrangChuTest}, {@code CmsAttachmentRefCleanerTest}), và lời dặn "tìm bằng
 * grep trước khi viết tên vào đây" ⛔ không ngăn được lần thứ hai.
 */
class RecaptchaKhoaBiMatTest {

    private static final String TEN_BIEN = "RECAPTCHA_SECRET_KEY";

    private static final List<String> TEP_ENV =
            List.of("deploy/env/local.env.example", "deploy/env/staging.env.example", "deploy/env/prod.env.example");

    private static final String MIGRATION =
            "backend/content/src/main/resources/db/migration/cms/V202609061067__cms_contact_form_va_recaptcha.sql";

    /**
     * ⚠ Bài này canh <b>toàn bộ</b> tệp migration của {@code cms}, ⛔ không riêng tệp T36.6 — vì
     * ngày G13 về, bộ khoá reCAPTCHA sẽ được thêm bởi một tệp <b>khác</b>, và đúng lúc ấy mới là
     * lúc dễ đặt nhầm khoá bí mật vào.
     */
    @Test
    @DisplayName("⛔⛔ ⛔ KHÔNG migration nào seed khoá BÍ MẬT reCAPTCHA vào `settings`")
    void khongMigrationNaoSeedKhoaBiMat() {
        List<Path> tep = migrationCms();

        assertThat(tep)
                .as("⚠ vế chống tập rỗng (luật 7): ⛔ không tìm thấy tệp migration nào thì bài này "
                        + "⛔ không kiểm gì cả")
                .hasSizeGreaterThan(10);

        for (Path t : tep) {
            String sql = docTep(t).toLowerCase();
            assertThat(sql)
                    .as(
                            "⛔⛔ `%s` seed một khoá mang dáng credential vào `settings` — bảng ấy đi "
                                    + "thẳng ra `/api/v1/public/site-config`, endpoint ⛔ không cần đăng nhập.",
                            t.getFileName())
                    .doesNotContain("recaptcha.secret")
                    .doesNotContain("recaptcha-secret")
                    .doesNotContain("recaptcha.secret-key");
        }
    }

    /**
     * ⭐ Và ⛔ <b>không migration nào seed một khoá reCAPTCHA</b>, kể cả khoá công khai.
     *
     * <p>Bản đầu của T36.6 seed cả ba, và {@code PortalSettingsReadTest.moiKhoaDeuCoNguoiDoc} đỏ
     * ngay lượt chạy đầu — đỏ đúng: ⛔ không dòng mã nào của <b>cổng</b> đọc chúng, vì phần giao
     * diện reCAPTCHA chưa dựng (G13 chặn). Quy tắc 15: <i>viết dòng mã đọc nó, hoặc đừng seed
     * nó</i>.
     *
     * <p>⚠⚠ <b>Bài này soi CẢ THƯ MỤC, ⛔ không riêng tệp T36.6</b> — bản trước chỉ soi một tệp và
     * đó là đúng hình dạng luật 28 (một bộ canh hẹp hơn nơi nó phải chặn): khoá mới sẽ được thêm
     * bởi một tệp <b>khác</b>, và một tệp khác thì bản cũ ⛔ không nhìn thấy.
     *
     * <p>⚠ Tiền tố kiểm là {@code site.recaptcha} <b>lẫn</b> {@code site.contact.recaptcha} — khoá
     * đã đổi tên ở T36.9 khi captcha chuyển sang {@code InboundSubmissionGate}, và một bộ canh chỉ
     * biết tên cũ sẽ xanh trong khi tên mới được seed thoải mái.
     */
    @Test
    @DisplayName("⛔ ⛔ Không migration nào seed khoá reCAPTCHA — chỗ cắm sống trong MÃ")
    void khongMigrationNaoSeedKhoaReCaptcha() {
        List<Path> tep = migrationCms();
        assertThat(tep).as("⚠ vế chống tập rỗng (luật 7)").hasSizeGreaterThan(10);

        // Đối chứng phải-tìm-thấy: ⛔ không có nó thì một tệp bị đổi tên làm bài này xanh vô nghĩa.
        assertThat(doc(MIGRATION))
                .as("⛔ Bộ canh đang soi một tệp ⛔ không còn seed bộ khoá biểu mẫu liên hệ")
                .contains("site.contact.field.phone.enabled");

        for (Path t : tep) {
            assertThat(docTep(t).toLowerCase())
                    .as(
                            "⛔ `%s`: khoá VẮNG chính là \"tắt\" — `InboundSubmissionGate` đọc bằng "
                                    + "`getBoolean(..., false)`. Ngày G13 về thì thêm khoá CÙNG LÚC với "
                                    + "đoạn mã cổng đọc chúng — ⛔ không phải trước.",
                            t.getFileName())
                    .doesNotContain("'site.recaptcha")
                    .doesNotContain("'site.contact.recaptcha");
        }
    }

    @Test
    @DisplayName("⭐ `application.yml` ĐỌC biến môi trường — và đọc với mặc định RỖNG")
    void applicationYmlDocBienMoiTruong() {
        String yml = doc("backend/app/src/main/resources/application.yml");

        assertThat(yml)
                .as("⛔ Khoá bí mật ⛔ không có đường nào vào ứng dụng — chỗ cắm T36.6 đứt ở đây")
                .contains("${" + TEN_BIEN + ":}");

        // ⛔⛔ `:?` (bắt buộc) hay `:REPLACE_ME` (mặc định giả) đều sai. WS-27 đã đo: một `@NotBlank`
        //    trên credential của một tính năng Công ty CHƯA cấp khoá làm cả hệ thống ⛔ không khởi
        //    động được, và làm đỏ mọi bài kiểm tích hợp của MỌI module.
        assertThat(yml)
                .as("⛔ Biến này phải có mặc định RỖNG — G13 chưa về, và rỗng là trạng thái bình "
                        + "thường, lâu dài, ⛔ không phải một lỗi cấu hình")
                .doesNotContain("${" + TEN_BIEN + ":?")
                .doesNotContain("${" + TEN_BIEN + ":REPLACE");
    }

    @Test
    @DisplayName("⭐ Ba tệp env mẫu đều KHAI biến — một biến chỉ sống trong `application.yml` là cần gạt giấu sau lưng")
    void baTepEnvMauDeuKhai() {
        assertThat(TEP_ENV).as("⚠ vế chống tập rỗng (luật 7)").hasSize(3);

        for (String tep : TEP_ENV) {
            assertThat(doc(tep))
                    .as(
                            """
                            ⛔ `%s` ⛔ không khai `%s`. Người dựng máy chủ ⛔ không có cách nào biết \
                            biến ấy tồn tại — đúng hình dạng nợ T27.4, nơi hai công tắc nới bảo mật \
                            được `application.yml` ĐỌC mà ⛔ không tệp mẫu nào KHAI.""",
                            tep, TEN_BIEN)
                    .contains(TEN_BIEN + "=");
        }
    }

    @Test
    @DisplayName("⛔ Ba tệp env mẫu để giá trị RỖNG — ⛔ không mồi một khoá giả")
    void khongMoiKhoaGia() {
        for (String tep : TEP_ENV) {
            String dong = doc(tep).lines()
                    .filter(d -> d.startsWith(TEN_BIEN + "="))
                    .findFirst()
                    .orElseGet(() -> fail("⛔ %s ⛔ không có dòng %s=".formatted(tep, TEN_BIEN)));

            String giaTri =
                    dong.substring((TEN_BIEN + "=").length()).split("#", 2)[0].trim();
            assertThat(giaTri)
                    .as(
                            "⛔⛔ `%s` mồi giá trị %s. Một khoá GIẢ tệ hơn ⛔ không có khoá: "
                                    + "`ContactFormPolicy` sẽ coi là ĐÃ CẤU HÌNH, thôi ghi ERROR, và mọi "
                                    + "lượt gửi biểu mẫu bị Google từ chối — mất hẳn trạng thái « chưa cấu "
                                    + "hình » vốn là thứ duy nhất chỉ đúng chỗ cần làm (cùng bài học với "
                                    + "`HydroApiProperties.TIEN_TO_CHO_DIEN`).",
                            tep, giaTri)
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------------

    private static List<Path> migrationCms() {
        Path goc = timGoc("backend/content/src/main/resources/db/migration/cms");
        try (var luong = Files.list(goc)) {
            return luong.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Không liệt kê được " + goc, e);
        }
    }

    private static String docTep(Path t) {
        try {
            return Files.readString(t, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + t, e);
        }
    }

    private static Path timGoc(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }

    private static String doc(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Không đọc được " + duongDanTuongDoi, e);
                }
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
