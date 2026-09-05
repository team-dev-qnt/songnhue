package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Đích healthcheck của nginx phải là một {@code location} CÓ CHUYỂN TIẾP, và phải tồn tại.</b>
 *
 * <h2>Hai cách hỏng, đã gặp cả hai</h2>
 *
 * <ol>
 *   <li><b>Đích không tồn tại.</b> Bản cũ trỏ vào {@code /.well-known/acme-challenge/}, trả
 *       <b>404</b> khi không có lượt xin chứng chỉ nào đang chạy — tức gần như luôn luôn. Đo trên
 *       staging 26/8: {@code songnhue-nginx  Up 22 hours (unhealthy)} trong khi cả hệ phục vụ bình
 *       thường. Một cảnh báo luôn bật là một cảnh báo không ai đọc nữa.
 *   <li><b>Đích tồn tại nhưng không đại diện.</b> {@code /healthz} là {@code return 200} tĩnh: nó
 *       xanh cả khi nginx không định tuyến được tới đâu — mà định tuyến chính là việc của nginx
 *       (CLAUDE.md luật 8, đã sập 3 lần vì đúng hình dạng này).
 * </ol>
 *
 * <p>Bài này đọc đường dẫn <b>từ chính {@code compose.prod.yml}</b> rồi tìm nó trong template nginx —
 * nên hai tệp không lệch nhau được (luật 14), và nó canh <b>cấu trúc</b> chứ không canh chuỗi ký tự
 * (luật 2).
 */
class NginxHealthcheckTest {

    /** Dòng `test:` trong khối healthcheck của service `nginx`. */
    private static final Pattern DICH = Pattern.compile("http://127\\.0\\.0\\.1(/[^\"\\s]*)");

    @Test
    @DisplayName("⭐⭐ Đích healthcheck của nginx tồn tại trong template VÀ có `proxy_pass`")
    void dichPhaiTonTaiVaChuyenTiep() {
        String duongDan = dichHealthcheck();
        String than = khoiLocation(duongDan);

        assertThat(than)
                .as(
                        """
                        `location = %s` trong template nginx không có `proxy_pass`.

                        Một đích tĩnh (`return 200`, `root`) chỉ chứng minh tiến trình nginx còn sống \
                        và cấu hình nạp được. Nó KHÔNG chứng minh nginx định tuyến được — mà đó là \
                        việc duy nhất nginx làm.""",
                        duongDan)
                .contains("proxy_pass");
    }

    @Test
    @DisplayName("⛔ Đích KHÔNG được là `/.well-known/acme-challenge/` — nó 404 gần như luôn luôn")
    void dichKhongDuocLaAcmeChallenge() {
        // Ghi đích danh cái đã hỏng: một bài kiểm chỉ nói "phải có proxy_pass" sẽ xanh trở lại nếu ai
        // đó trỏ về acme-challenge rồi thêm một `proxy_pass` vô nghĩa vào đấy.
        assertThat(dichHealthcheck()).doesNotContain("acme-challenge");
    }

    @Test
    @DisplayName("⭐ Đích chỉ cho phép gọi từ localhost — nó là cửa nội bộ, không phải endpoint công khai")
    void dichChiChoLocalhost() {
        String than = khoiLocation(dichHealthcheck());

        assertThat(than)
                .as("Đích healthcheck chuyển tiếp mà mở ra ngoài là một đường vòng qua nginx tới upstream")
                .contains("deny all");
        assertThat(than).contains("allow 127.0.0.1");
    }

    @Test
    @DisplayName("Đọc được đích từ compose — không âm thầm dùng mặc định")
    void docDuocDichTuCompose() {
        // conventions.md §1.5: đổi cách viết khối healthcheck làm ba bài trên xanh trên tập rỗng.
        assertThat(dichHealthcheck()).startsWith("/").hasSizeGreaterThan(1);
    }

    // -------------------------------------------------------------------------

    private static String dichHealthcheck() {
        String compose = doc(timTuGocKho("deploy/compose.prod.yml"));
        int dau = compose.indexOf("\n  nginx:");
        assertThat(dau).as("`compose.prod.yml` phải có service `nginx`").isNotNegative();

        // Khối healthcheck của RIÊNG service nginx: cắt tới service kế tiếp.
        Matcher ketThuc =
                Pattern.compile("^  [a-z][a-z0-9-]*:$", Pattern.MULTILINE).matcher(compose);
        int cuoi = compose.length();
        while (ketThuc.find()) {
            if (ketThuc.start() > dau + 1) {
                cuoi = ketThuc.start();
                break;
            }
        }
        String khoi = compose.substring(dau, cuoi);
        int hc = khoi.indexOf("healthcheck:");
        assertThat(hc).as("service `nginx` phải có `healthcheck:`").isNotNegative();

        Matcher khop = DICH.matcher(khoi.substring(hc));
        if (!khop.find()) {
            return fail("Không đọc được đích healthcheck của nginx từ `compose.prod.yml`");
        }
        return khop.group(1);
    }

    /** Thân của {@code location = <đường dẫn>} trong template, cắt theo cặp ngoặc nhọn. */
    private static String khoiLocation(String duongDan) {
        String tpl = doc(timTuGocKho("deploy/nginx/templates/default.conf.template"));
        String moc = "location = " + duongDan + " {";
        int dau = tpl.indexOf(moc);

        if (dau < 0) {
            return fail(
                    """
                    Template nginx không có `%s`.

                    `healthcheck:` trong compose trỏ vào một đường dẫn KHÔNG TỒN TẠI — nginx sẽ trả \
                    404 và container báo `unhealthy` mãi mãi, trong khi dịch vụ vẫn chạy. Đúng thứ \
                    đã xảy ra suốt từ lúc dựng staging tới 26/8.""",
                    moc);
        }

        int mo = 0;
        for (int i = dau + moc.length() - 1; i < tpl.length(); i++) {
            char c = tpl.charAt(i);
            if (c == '{') {
                mo++;
            } else if (c == '}') {
                mo--;
                if (mo == 0) {
                    return tpl.substring(dau, i + 1);
                }
            }
        }
        return fail("Khối `%s` không đóng ngoặc".formatted(moc));
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
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
}
