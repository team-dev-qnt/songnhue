package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>nginx trả 429, ⛔ 503, khi chặn theo hạn mức — T61.17 (WS-72).</b>
 *
 * <p>Mặc định của nginx cho {@code limit_req}/{@code limit_conn} là <b>503</b>. Đo 19/09/2026 trên
 * {@code nginx:1.30-alpine}: bản ⛔ có {@code limit_*_status} trả {@code 503 Service Temporarily Unavailable}; thêm
 * hai dòng ở tầng http thì cả {@code limit_req} lẫn {@code limit_conn} trả {@code 429}, ở MỌI khối server. 503 nói
 * "máy chủ ⛔ phục vụ được":
 *
 * <ul>
 *   <li>lượt tải thử bắn từ một máy đếm nó vào {@code loi_5xx} thay vì {@code bi_chan_429}
 *       ({@code tools/tai-thu/README.md} §2);
 *   <li>hai biểu mẫu công khai có thông điệp riêng cho 429 (<i>"thử lại sau"</i>), 503 thì in câu lỗi chung;
 *   <li>người đọc log đi tìm một sự cố ⛔ có thật.
 * </ul>
 *
 * <p>Bài canh <b>chỗ đặt</b> (luật 2 — cấu trúc, ⛔ chuỗi): đặt trong một khối {@code server} thì chỉ khối ấy trả
 * 429, và ba khối server của template trả hai mã khác nhau cho cùng một sự việc.
 *
 * <p>⚠ Giới hạn (luật 28): bài đọc TỆP. Hành vi lúc chạy đo tay 19/09 (xem trên); cú pháp của bản ĐÃ render thì
 * lượt triển khai kiểm bằng {@code nginx -t} trước khi thay container (§11.27).
 */
class NginxHanMucTra429Test {

    private static final String TEMPLATE = "deploy/nginx/templates/default.conf.template";

    @Test
    @DisplayName("⛔ limit_req_status + limit_conn_status 429 đặt ở TẦNG HTTP (độ sâu 0) — phủ mọi khối server")
    void tangHttpTra429() {
        List<ChiThi> chiThi = chiThiTheoDoSau(doc(TEMPLATE));

        assertThat(chiThi.stream()
                        .filter(c -> c.lenh().startsWith("limit_req zone=")
                                || c.lenh().startsWith("limit_conn "))
                        .count())
                .as("chống tập rỗng (luật 7): template phải còn dùng limit_req/limit_conn — ⛔ còn thì bài ⛔ canh gì")
                .isGreaterThanOrEqualTo(3);
        assertThat(chiThi)
                .as(
                        """
                        Hai dòng phải nằm ở TẦNG HTTP (ngoài mọi khối `server`) — đặt trong một khối thì chỉ khối ấy \
                        trả 429, các khối còn lại vẫn 503 (mặc định của nginx).""")
                .contains(new ChiThi(0, "limit_req_status 429;"), new ChiThi(0, "limit_conn_status 429;"));
    }

    @Test
    @DisplayName("⛔ Không khối nào ghi đè mã trạng thái hạn mức bằng một giá trị khác 429")
    void khongKhoiNaoGhiDe() {
        List<ChiThi> ghiDe = chiThiTheoDoSau(doc(TEMPLATE)).stream()
                .filter(c -> c.lenh().matches("limit_(req|conn)_status\\s.*"))
                .filter(c -> !c.lenh().matches("limit_(req|conn)_status\\s+429;"))
                .toList();

        assertThat(ghiDe)
                .as("một khối server/location đặt lại 503 là trả lại đúng khuyết tật cho riêng khối ấy")
                .isEmpty();
    }

    /**
     * Bộ đo độ sâu phải phân biệt được hai chỗ đặt, và ⛔ bị {@code ${BIEN}} hay dấu ngoặc trong chú thích làm lệch
     * (luật 1) — ⛔ có bài này thì bài trên có thể xanh vì bộ đo trả 0 cho mọi dòng.
     */
    @Test
    @DisplayName("Tự kiểm: bộ đo độ sâu phân biệt tầng http với trong khối, ⛔ lệch vì ${BIEN} hay chú thích có ngoặc")
    void tuKiemDoSau() {
        String mau =
                """
                # chú thích có ngoặc mở { và 15' ⇒ ⛔ được làm lệch độ sâu
                limit_req_status   429;
                server {
                    server_name ${ADMIN_DOMAIN};
                    limit_conn_status 429; # chú thích cuối dòng }
                    location ~ ^/api/(a|b) {
                        add_header X "a#b 'c'";
                    }
                }
                limit_conn_status 503;
                """;

        assertThat(chiThiTheoDoSau(mau))
                .containsExactly(
                        new ChiThi(0, "limit_req_status 429;"),
                        new ChiThi(1, "server_name ${ADMIN_DOMAIN};"),
                        new ChiThi(1, "limit_conn_status 429;"),
                        new ChiThi(2, "add_header X \"a#b 'c'\";"),
                        new ChiThi(0, "limit_conn_status 503;"));
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    /** Một chỉ thị kết thúc bằng {@code ;}, kèm độ sâu khối tại đầu dòng (0 = tầng http của template). */
    record ChiThi(int doSau, String lenh) {}

    /**
     * Chỉ thị theo độ sâu. Bỏ chú thích {@code #} nằm NGOÀI dấu nháy; {@code ${BIEN}} của envsubst ⛔ tính là khối
     * (ngoặc của nó cân nhau trong cùng biến); ngoặc trong dấu nháy ⛔ tính.
     */
    static List<ChiThi> chiThiTheoDoSau(String tep) {
        List<ChiThi> kq = new ArrayList<>();
        int doSau = 0;
        for (String dongGoc : tep.split("\n")) {
            String dong = boChuThich(dongGoc).trim();
            if (dong.endsWith(";")) {
                // nginx coi mọi khoảng trắng là MỘT dấu phân tách — template căn cột bằng nhiều dấu cách.
                kq.add(new ChiThi(doSau, dong.replaceAll("\\s+", " ")));
            }
            doSau += canNgoac(dong);
        }
        return kq;
    }

    private static String boChuThich(String dong) {
        char nhay = 0;
        for (int i = 0; i < dong.length(); i++) {
            char c = dong.charAt(i);
            if (nhay != 0) {
                if (c == nhay) {
                    nhay = 0;
                }
            } else if (c == '"' || c == '\'') {
                nhay = c;
            } else if (c == '#') {
                return dong.substring(0, i);
            }
        }
        return dong;
    }

    /** Số ngoặc mở trừ số ngoặc đóng, bỏ qua ngoặc trong dấu nháy và trong biến envsubst. */
    private static int canNgoac(String dong) {
        int d = 0;
        char nhay = 0;
        for (int i = 0; i < dong.length(); i++) {
            char c = dong.charAt(i);
            if (nhay != 0) {
                if (c == nhay) {
                    nhay = 0;
                }
            } else if (c == '"' || c == '\'') {
                nhay = c;
            } else if (c == '$' && i + 1 < dong.length() && dong.charAt(i + 1) == '{') {
                int dongBien = dong.indexOf('}', i);
                i = dongBien < 0 ? dong.length() : dongBien;
            } else if (c == '{') {
                d++;
            } else if (c == '}') {
                d--;
            }
        }
        return d;
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
