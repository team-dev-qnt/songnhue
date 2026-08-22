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
 * <b>Biến môi trường "để trống" phải trống THẬT.</b>
 *
 * <h2>Lỗi này đã xảy ra, và nó im lặng suốt từ WS-3</h2>
 *
 * Docker Compose đọc {@code env_file} theo luật riêng, <b>không phải luật của shell</b>. Với dòng
 *
 * <pre>{@code BOOTSTRAP_ADMIN_PASSWORD=           # [T] chỉ dùng 1 lần}</pre>
 *
 * nó <b>không</b> cắt phần chú thích, mà cắt khoảng trắng đầu rồi lấy toàn bộ phần còn lại làm giá
 * trị. Biến đó vào container mang giá trị {@code "# [T] chỉ dùng 1 lần"} — <b>không rỗng</b>.
 *
 * <p>Kiểm chứng bằng ví dụ tối giản (alpine + compose):
 *
 * <pre>
 *   RONG=           # chú thích      →  RONG=[# chú thích]     ⛔
 *   CO_GIATRI=abc   # chú thích      →  CO_GIATRI=[abc]        ✔ (có giá trị thì cắt đúng)
 * </pre>
 *
 * <p>Nghĩa là <b>chỉ trường hợp giá trị rỗng mới hỏng</b> — đúng những biến mà "để trống" mang ý
 * nghĩa nghiệp vụ, nên hậu quả rơi vào chỗ đắt nhất:
 *
 * <table border="1">
 *   <caption>Hậu quả đo được</caption>
 *   <tr><th>Biến</th><th>"Rỗng" nghĩa là</th><th>Thực tế xảy ra</th></tr>
 *   <tr>
 *     <td>{@code BOOTSTRAP_ADMIN_PASSWORD}</td>
 *     <td>không kích hoạt tài khoản quản trị</td>
 *     <td>⚠⚠ {@code AdminBootstrapRunner} chạy ở <b>mọi</b> lượt khởi động và đặt mật khẩu
 *         {@code superadmin} thành <b>chính đoạn chú thích</b> — một chuỗi nằm trong tệp
 *         {@code .example} đã commit lên repo</td>
 *   </tr>
 *   <tr>
 *     <td>{@code DB_RESTORE_PASSWORD}</td>
 *     <td>tắt chức năng khôi phục qua UI ({@code ADM-2010})</td>
 *     <td>chức năng khôi phục — thao tác phá huỷ nhất trong hệ — tưởng như đã được cấu hình</td>
 *   </tr>
 *   <tr>
 *     <td>{@code SMTP_USERNAME}/{@code SMTP_PASSWORD}</td>
 *     <td>máy chủ thư không cần xác thực</td>
 *     <td>client thử {@code AUTH} với chuỗi rác → hỏng toàn bộ đường gửi thư</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GOOGLE_MAPS_API_KEY}</td>
 *     <td>chỉ dùng OSM</td>
 *     <td>đi đường Google Maps với khoá rác</td>
 *   </tr>
 * </table>
 *
 * <h2>Vì sao {@code UnresolvedPlaceholderGuard} không bắt được</h2>
 *
 * Bộ canh dựng ở WS-4/T4.8 tìm giá trị còn nguyên dạng {@code "${TÊN_BIẾN}"} — dấu hiệu của một
 * placeholder không giải được. Ở đây giá trị <b>giải ra bình thường</b>, chỉ là giải ra sai; và nó
 * <b>không rỗng</b> nên {@code @NotBlank} cũng đi qua. Cùng một họ lỗi, khác một bậc: lần trước là
 * "thiếu biến mà tưởng có", lần này là "có biến mà tưởng thiếu".
 *
 * <p>⚠ Lỗi chỉ xuất hiện ở đường <b>Docker</b>. Chạy native thì {@code make} nạp tệp bằng shell, mà
 * shell cắt chú thích đúng — nên hai lối chạy cho ra hai hành vi khác nhau từ cùng một tệp. Đó là
 * lý do nó sống sót qua mọi lượt chạy tay từ WS-3 tới nay.
 */
class EnvFileCommentTest {

    /**
     * {@code KEY=} + khoảng trắng + {@code #…}
     *
     * <p>Cố ý <b>chỉ</b> bắt trường hợp giá trị rỗng. Dòng có giá trị thật rồi mới tới chú thích
     * ({@code MINIO_ACCESS_KEY=minioadmin   # [B]}) được Compose cắt đúng, và cả tệp đang viết theo
     * lối đó — cấm luôn thì phải sửa hơn trăm dòng mà không chữa được lỗi nào.
     */
    private static final Pattern RONG_KEM_CHU_THICH = Pattern.compile("^([A-Z_][A-Z0-9_]*)=[ \\t]+#.*$");

    private static final List<String> TEP_ENV = List.of("local.env.example", "staging.env.example", "prod.env.example");

    @Test
    @DisplayName("⭐⭐ Không tệp env nào có biến rỗng kèm chú thích cùng dòng")
    void khongCoBienRongKemChuThich() {
        List<String> viPham = new ArrayList<>();

        for (String ten : TEP_ENV) {
            Path duongDan = timTuGocKho("deploy/env/" + ten);
            List<String> dong = docDong(duongDan);
            for (int i = 0; i < dong.size(); i++) {
                Matcher khop = RONG_KEM_CHU_THICH.matcher(dong.get(i));
                if (khop.matches()) {
                    viPham.add("%s:%d  %s".formatted(ten, i + 1, khop.group(1)));
                }
            }
        }

        assertThat(viPham)
                .as(
                        """
                        Docker Compose KHÔNG cắt chú thích khi giá trị rỗng — biến vào container sẽ mang \
                        chính đoạn chú thích làm giá trị. Với những biến mà "để trống" nghĩa là "tắt tính \
                        năng" (BOOTSTRAP_ADMIN_PASSWORD, DB_RESTORE_PASSWORD, SMTP_*, GOOGLE_MAPS_API_KEY) \
                        thì tính năng đó bị BẬT bằng một chuỗi rác, im lặng.

                        Sửa: đưa chú thích lên dòng riêng phía trên, để lại `TÊN_BIẾN=` trống thật.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Ba tệp env mẫu đều đọc được — thiếu dòng này thì bài trên xanh khi tệp biến mất")
    void docDuocCaBaTep() {
        for (String ten : TEP_ENV) {
            assertThat(docDong(timTuGocKho("deploy/env/" + ten)))
                    .as("tệp %s", ten)
                    .hasSizeGreaterThan(20);
        }
    }

    @Test
    @DisplayName("⛔ Và mẫu bắt lỗi phải thật sự bắt được — kiểm chứng ngược")
    void mauBatDuocViPham() {
        // Bài canh mà không tự chứng minh nó bắt được vi phạm thì chỉ chứng minh chưa ai thử
        // (conventions.md §1.5). Ba dòng dưới đây là ba dạng đã có thật trong repo.
        assertThat(RONG_KEM_CHU_THICH
                        .matcher("BOOTSTRAP_ADMIN_PASSWORD=           # [T] chỉ dùng 1 lần")
                        .matches())
                .isTrue();
        assertThat(RONG_KEM_CHU_THICH.matcher("AES_KEY_V1=\t# [B] ⛔").matches()).isTrue();

        // Và phải KHÔNG bắt nhầm dòng hợp lệ, nếu không cả tệp thành vi phạm.
        assertThat(RONG_KEM_CHU_THICH
                        .matcher("MINIO_ACCESS_KEY=minioadmin          # [B]")
                        .matches())
                .isFalse();
        assertThat(RONG_KEM_CHU_THICH.matcher("SMTP_PASSWORD=").matches()).isFalse();
        assertThat(RONG_KEM_CHU_THICH.matcher("# [T] chỉ dùng 1 lần").matches()).isFalse();
    }

    // -------------------------------------------------------------------------

    private static List<String> docDong(Path duongDan) {
        try {
            return Files.readAllLines(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    /** Đi ngược lên từ thư mục đang chạy để tìm gốc kho mã — cùng cách {@code EditorVocabularyTest} dùng. */
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
