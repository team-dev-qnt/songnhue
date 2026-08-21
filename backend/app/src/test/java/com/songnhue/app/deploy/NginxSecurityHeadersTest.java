package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Header bảo mật của tầng phục vụ admin-app — conventions.md §4.5.
 *
 * <h2>Lỗi đã đo được</h2>
 *
 * Image admin-app <b>không đặt một header bảo mật nào</b>. {@code curl -I http://localhost:15173/}
 * trả về {@code Server}, {@code Date}, {@code Content-Type}, {@code ETag}, {@code Cache-Control} —
 * hết. Không {@code X-Frame-Options}, không {@code CSP}, không {@code X-Content-Type-Options}. Cùng
 * lúc đó public-web đặt sẵn ba cái, nên hai tầng phục vụ của cùng một hệ thống có hai mức bảo vệ
 * khác hẳn nhau mà không ai chọn điều đó.
 *
 * <h2>⚠⚠ Vì sao bài kiểm này soi CẤU TRÚC khối {@code location}</h2>
 *
 * Trong nginx, {@code add_header} <b>không cộng dồn qua các cấp</b>: một khối {@code location} có
 * {@code add_header} riêng sẽ vứt bỏ <i>toàn bộ</i> {@code add_header} kế thừa từ khối
 * {@code server}. Cấu hình này có {@code add_header Cache-Control} ở cả {@code /assets/} lẫn
 * {@code /} — đúng hai khối phục vụ mọi thứ người dùng tải về.
 *
 * <p>Nghĩa là "đã khai header ở cấp server" là một câu <b>đúng mà vô nghĩa</b>. Bài kiểm chỉ tìm
 * chuỗi {@code Content-Security-Policy} trong tệp sẽ xanh trọn vẹn trong khi trình duyệt không nhận
 * được header nào — đúng loại lỗi đã trả giá ở {@code articleContentCss.test.ts} (canh văn bản thay
 * vì canh cấu trúc). Nên phép khẳng định ở đây là: <b>khối nào có {@code add_header} riêng thì khối
 * đó bắt buộc phải {@code include} lại tệp header bảo mật</b>.
 */
class NginxSecurityHeadersTest {

    private static final String DOCKERFILE = "deploy/docker/admin-app.Dockerfile";

    private static final String SNIPPET = "/etc/nginx/snippets/security-headers.conf";

    private static final List<String> HEADER_BAT_BUOC =
            List.of("X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy", "Content-Security-Policy");

    @Test
    @DisplayName("⛔ Khối location nào có add_header riêng thì phải include lại header bảo mật")
    void moiKhoiLocationGiuDuocHeaderBaoMat() throws IOException {
        String cauHinh = docTemplateNginx();

        List<String> thieu = new ArrayList<>();
        for (KhoiLocation khoi : timCacKhoiLocation(cauHinh)) {
            if (khoi.than().contains("add_header") && !khoi.than().contains(SNIPPET)) {
                thieu.add(khoi.duong());
            }
        }

        assertThat(thieu)
                .as(
                        "Những khối location này tự khai add_header nên đã CẮT ĐỨT kế thừa từ cấp "
                                + "server — trình duyệt sẽ không nhận được header bảo mật nào khi tải "
                                + "đường dẫn đó. Phải include lại %s",
                        SNIPPET)
                .isEmpty();
    }

    @Test
    @DisplayName("Tệp snippet khai đủ bốn header, và đều có `always`")
    void snippetKhaiDuHeader() throws IOException {
        String snippet = docSnippet();

        for (String header : HEADER_BAT_BUOC) {
            assertThat(snippet).as("thiếu header %s", header).contains(header);
        }
        // `always` là điều kiện để header đi kèm cả phản hồi lỗi (4xx/5xx). Thiếu nó thì trang 404
        // và trang lỗi — đúng những trang hay bị dùng để thử chèn nội dung — lại là những trang
        // không được bảo vệ.
        long soDong =
                snippet.lines().filter(d -> d.trim().startsWith("add_header")).count();
        long soAlways = snippet.lines()
                .filter(d -> d.trim().startsWith("add_header"))
                .filter(d -> d.contains("always"))
                .count();
        assertThat(soAlways).as("mọi add_header phải có `always`").isEqualTo(soDong);
    }

    @Test
    @DisplayName("⛔ CSP: script-src phải chặt; style-src được nới có lý do và chỉ nới đúng chỗ đó")
    void cspChatOChoDangKe() throws IOException {
        String csp = dongCsp(docSnippet());

        assertThat(chiThi(csp, "script-src"))
                .as("script-src là lớp chặn XSS thật sự. Bản dựng Vite chỉ có thẻ script mang "
                        + "src, không có script nội tuyến — nên không có cớ gì để nới")
                .isEqualTo("'self'");
        assertThat(chiThi(csp, "object-src")).isEqualTo("'none'");
        assertThat(chiThi(csp, "frame-ancestors")).isEqualTo("'none'");
        assertThat(chiThi(csp, "base-uri")).isEqualTo("'self'");

        // style-src buộc phải nới vì AntD 5 (cssinjs) chèn thẻ <style> lúc chạy. Khẳng định ở đây
        // để lần sau ai siết lại thì thấy ngay lý do, thay vì siết rồi phát hiện giao diện quản
        // trị mất sạch định dạng lúc đã lên staging.
        assertThat(chiThi(csp, "style-src")).contains("'unsafe-inline'");

        assertThat(csp)
                .as("⛔ 'unsafe-inline' KHÔNG được lọt sang script-src — đó là lúc CSP mất hết " + "tác dụng chống XSS")
                .doesNotContain("script-src 'self' 'unsafe-inline'");
    }

    // -------------------------------------------------------------------------

    private record KhoiLocation(String duong, String than) {}

    /** Cắt lấy phần heredoc dựng {@code default.conf.template}. */
    private static String docTemplateNginx() throws IOException {
        String dockerfile = Files.readString(timTuGocKho(DOCKERFILE), StandardCharsets.UTF_8);
        int bat = dockerfile.indexOf("/etc/nginx/templates/default.conf.template");
        assertThat(bat).as("không thấy heredoc dựng template nginx").isNotNegative();
        int het = dockerfile.indexOf("\nEOF", bat);
        assertThat(het).as("heredoc template không đóng").isNotNegative();
        return dockerfile.substring(bat, het);
    }

    private static String docSnippet() throws IOException {
        String dockerfile = Files.readString(timTuGocKho(DOCKERFILE), StandardCharsets.UTF_8);
        int bat = dockerfile.indexOf(SNIPPET);
        assertThat(bat).as("Dockerfile chưa dựng tệp %s", SNIPPET).isNotNegative();
        int het = dockerfile.indexOf("\nEOF", bat);
        return dockerfile.substring(bat, het);
    }

    /**
     * Tách các khối {@code location … { … }} bằng cách đếm ngoặc, không bằng biểu thức chính quy —
     * khối lồng nhau và dấu ngoặc trong chuỗi làm regex sai lặng lẽ.
     */
    private static List<KhoiLocation> timCacKhoiLocation(String cauHinh) {
        List<KhoiLocation> ket = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\s*location\\s+([^{]+)\\{").matcher(cauHinh);
        while (m.find()) {
            int i = m.end() - 1;
            int sau = 0;
            int dau = i;
            while (i < cauHinh.length()) {
                char c = cauHinh.charAt(i);
                if (c == '{') {
                    sau++;
                } else if (c == '}') {
                    sau--;
                    if (sau == 0) {
                        break;
                    }
                }
                i++;
            }
            ket.add(new KhoiLocation(m.group(1).trim(), cauHinh.substring(dau, Math.min(i + 1, cauHinh.length()))));
        }
        assertThat(ket)
                .as("không tách được khối location nào — bài kiểm đang soi nhầm tệp")
                .isNotEmpty();
        return ket;
    }

    private static String dongCsp(String snippet) {
        return snippet.lines()
                .filter(d -> d.contains("Content-Security-Policy"))
                .findFirst()
                .orElseGet(() -> fail("snippet không khai Content-Security-Policy"));
    }

    /** Giá trị của một chỉ thị CSP, VD {@code script-src} → {@code 'self'}. */
    private static String chiThi(String csp, String ten) {
        Matcher m = Pattern.compile(ten + "\\s+([^;\"]+)").matcher(csp);
        assertThat(m.find()).as("CSP không khai chỉ thị %s", ten).isTrue();
        return m.group(1).trim();
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
