package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Khối ADMIN luôn trả {@code X-Robots-Tag: noindex, nofollow} — T68.13.</b>
 *
 * <p>Đo 19/09/2026 bằng {@code curl -sI}: admin production trả {@code all}, admin staging trả {@code noindex,
 * nofollow}. Header chung một biến {@code ROBOTS_TAG} cho mọi host, nên đặt {@code all} cho cổng công khai là
 * đặt {@code all} cho cả màn hình quản trị. (Thẻ meta {@code robots} trong {@code admin-app/index.html} đang
 * che — nhưng một bot phải TẢI trang mới đọc được thẻ ấy; header chặn trước khi trang được đọc.)
 *
 * <h2>Ba mắt xích, mỗi mắt một khẳng định — hỏng một mắt là header về {@code all} trong im lặng</h2>
 *
 * <ol>
 *   <li>{@code map $host $robots_tag} có khoá REGEX gắn đúng {@code ${ADMIN_DOMAIN}}, neo {@code ^…$}, ⛔ phân
 *       biệt hoa thường. ⛔ Khoá chính xác: nó vào bảng băm có trần {@code map_hash_bucket_size} (§11.27) —
 *       ranh giới ấy do {@code CanhBaoCoDuongDiTest.khoaMapKhongDuocMangChoCam} canh.
 *   <li>Khối {@code server} của {@code ${ADMIN_DOMAIN}} gắn snippet {@code edge-headers.conf} — nơi header được
 *       phát.
 *   <li>Snippet phát {@code X-Robots-Tag $robots_tag}, ⛔ một giá trị ghi cứng.
 * </ol>
 *
 * <p>⚠ Giới hạn (luật 28): bài đọc TỆP. Hành vi lúc chạy đã đo 19/09 trên {@code nginx:1.30-alpine} với khoá
 * regex 99 byte — đúng host ⇒ {@code noindex, nofollow} (kể cả viết HOA), host khác và host có tiền tố giả ⇒
 * {@code all}; ghi ở chú thích template và {@code master-tracking.md} T70. Cú pháp của bản ĐÃ render thì lượt
 * triển khai kiểm bằng {@code nginx -t} trước khi thay container (§11.27).
 */
class NginxRobotsTagTest {

    private static final String TEMPLATE = "deploy/nginx/templates/default.conf.template";
    private static final String SNIPPET = "deploy/nginx/snippets/edge-headers.conf";

    @Test
    @DisplayName(
            "⛔ map $robots_tag: khoá REGEX neo ^…$ gắn đúng ${ADMIN_DOMAIN} ⇒ `noindex, nofollow`; mặc định vẫn ROBOTS_TAG")
    void mapCoKhoaRegexChoAdmin() {
        List<String> dong = dongCuaMap(doc(TEMPLATE), "$host", "$robots_tag");

        assertThat(dong)
                .as("chống tập rỗng (luật 7): phải bóc được khối map $robots_tag")
                .isNotEmpty();
        assertThat(dong).contains("default \"${ROBOTS_TAG}\";");
        assertThat(dong)
                .as(
                        """
                        Khối ADMIN phải luôn `noindex, nofollow` (T68.13). Khoá phải là REGEX (`~*`, ⛔ khoá chính xác — \
                        §11.27), neo đủ `^` và `$` (thiếu `^` thì `xadmin.…` cũng khớp; thiếu `$` thì `admin.…evil` \
                        cũng khớp), và gắn đúng biến `${ADMIN_DOMAIN}` (⛔ ghi cứng tên miền — đổi tên miền là \
                        header về `all` trong im lặng).""")
                .contains("~*^${ADMIN_DOMAIN}$ \"noindex, nofollow\";");
    }

    @Test
    @DisplayName("⛔ Khối server của ${ADMIN_DOMAIN} gắn edge-headers.conf, và snippet phát X-Robots-Tag từ $robots_tag")
    void adminPhatHeaderTuMap() {
        String tpl = doc(TEMPLATE);
        String khoiAdmin = khoiServer(tpl, "server_name ${ADMIN_DOMAIN};");

        assertThat(khoiAdmin)
                .as("khối server ADMIN phải gắn snippet phát header — ⛔ gắn thì map ⛔ có nơi dùng")
                .contains("include /etc/nginx/snippets/edge-headers.conf;");
        assertThat(doc(SNIPPET))
                .as("snippet phải phát từ biến của map, ⛔ một giá trị ghi cứng")
                .contains("add_header X-Robots-Tag $robots_tag always;");
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    /** Các dòng KHÔNG phải chú thích bên trong {@code map <nguon> <dich> { … }}. */
    static List<String> dongCuaMap(String tpl, String nguon, String dich) {
        Matcher m = Pattern.compile(
                        "^map\\s+" + Pattern.quote(nguon) + "\\s+" + Pattern.quote(dich) + "\\s*\\{(.*?)^}",
                        Pattern.DOTALL | Pattern.MULTILINE)
                .matcher(tpl);
        if (!m.find()) {
            return List.of();
        }
        return Arrays.stream(m.group(1).split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .toList();
    }

    /** Thân khối {@code server { … }} chứa {@code dauHieu}, cắt theo cân ngoặc (luật 2 — ⛔ regex tham lam). */
    static String khoiServer(String tpl, String dauHieu) {
        int viTri = tpl.indexOf(dauHieu);
        assertThat(viTri).as("⛔ tìm thấy `%s` trong template", dauHieu).isNotNegative();
        int batDau = tpl.lastIndexOf("\nserver {", viTri);
        assertThat(batDau).as("⛔ tìm thấy `server {` bao quanh `%s`", dauHieu).isNotNegative();
        int sau = 0;
        for (int i = tpl.indexOf('{', batDau); i < tpl.length(); i++) {
            char c = tpl.charAt(i);
            if (c == '{') {
                sau++;
            } else if (c == '}') {
                sau--;
                if (sau == 0) {
                    return tpl.substring(batDau, i + 1);
                }
            }
        }
        return fail("khối server quanh `%s` ⛔ đóng ngoặc", dauHieu);
    }

    private static String doc(String duongDan) {
        try {
            return Files.readString(timTuGocKho(duongDan), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
