package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi biến {@code compose.prod.yml} tham chiếu phải có mặt trong cả hai tệp env mẫu.</b>
 *
 * <h2>Lỗi này đã xảy ra, và nó không báo gì cả</h2>
 *
 * Trước bản vá 24/8, {@code compose.prod.yml} tham chiếu <b>sáu</b> biến mà không tệp mẫu nào có:
 *
 * <pre>
 *   PUBLIC_DOMAIN · ADMIN_DOMAIN · FILES_DOMAIN · MINIO_ROOT_USER
 *   MINIO_ROOT_PASSWORD · REVALIDATE_SECRET
 * </pre>
 *
 * <p>Và chúng viết ở dạng {@code ${TÊN}} <b>không có {@code :?}</b>, nên Compose không coi thiếu là
 * lỗi — nó thay bằng <b>chuỗi rỗng</b> rồi chạy tiếp. Hậu quả đo được ở từng biến:
 *
 * <table border="1">
 *   <caption>Thiếu biến thì hỏng ở đâu</caption>
 *   <tr><th>Biến</th><th>Triệu chứng</th></tr>
 *   <tr><td>{@code PUBLIC_DOMAIN}/{@code ADMIN_DOMAIN}</td>
 *       <td>{@code server_name} rỗng → mọi tên miền rơi vào server block mặc định, tức là bị
 *           {@code ssl_reject_handshake} từ chối. Trang "không vào được", không log ứng dụng nào</td></tr>
 *   <tr><td>{@code FILES_DOMAIN}</td>
 *       <td>vừa là tên miền MinIO vừa là bí danh mạng của nginx; rỗng thì {@code MINIO_ENDPOINT}
 *           thành {@code https://} và <b>mọi nút Tải về hỏng</b> — tải LÊN vẫn chạy, nên rất khó thấy</td></tr>
 *   <tr><td>{@code MINIO_ROOT_USER}/{@code MINIO_ROOT_PASSWORD}</td>
 *       <td>MinIO khởi động bằng tài khoản mặc định — kho tệp nhân sự mở bằng mật khẩu ai cũng biết</td></tr>
 *   <tr><td>{@code REVALIDATE_SECRET}</td>
 *       <td>{@code /api/revalidate} trả 503; cổng công khai <b>đứng yên ở nội dung cũ</b> sau mỗi lần
 *           duyệt bài, không báo lỗi ở đâu</td></tr>
 * </table>
 *
 * <h2>Vì sao {@link EnvFileCommentTest} và {@code UnresolvedPlaceholderGuard} không bắt được</h2>
 *
 * Cả hai soi <b>giá trị</b> của những biến <i>đã có mặt</i>. Không bài nào hỏi câu "còn biến nào
 * compose cần mà tệp mẫu chưa có" — mà đó mới là câu người điền {@code .env} lần đầu đang dựa vào,
 * vì {@code deploy-guideline.md} chỉ thẳng tệp mẫu là "danh sách biến đầy đủ".
 *
 * <p>Đây là dạng "chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ"
 * (CLAUDE.md luật 14): thêm một service vào compose là phải nhớ thêm biến vào hai tệp mẫu, và
 * không có gì nhắc.
 */
class ComposeEnvCompletenessTest {

    /** {@code ${TÊN}}, {@code ${TÊN:-mặc định}}, {@code ${TÊN:?thông báo}} — lấy phần TÊN. */
    private static final Pattern THAM_CHIEU = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)");

    /** {@code TÊN=} ở đầu dòng của tệp env mẫu. */
    private static final Pattern KHAI_BAO = Pattern.compile("^[ \\t]*([A-Z][A-Z0-9_]*)=");

    private static final List<String> TEP_ENV = List.of("staging.env.example", "prod.env.example");

    /**
     * Bốn biến cố ý KHÔNG nằm trong tệp mẫu — mỗi dòng phải nói rõ ai cấp, nếu không thì danh sách
     * miễn trừ sẽ phình ra cho tới lúc bài kiểm này không còn canh gì.
     */
    private static final Set<String> MIEN_TRU = new LinkedHashSet<>(List.of(
            // Ba tag image do workflow triển khai `export` ngay trước khi gọi compose. Đưa vào
            // `.env` là đóng đinh một phiên bản trên đĩa máy chủ, đúng thứ luồng đề bạt tránh.
            "APP_IMAGE",
            "ADMIN_IMAGE",
            "PUBLIC_IMAGE",
            // Do chính entrypoint của image nginx điền, bật bằng NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
            // (xem chú thích ở service `nginx`). Người vận hành không đặt biến này.
            "NGINX_LOCAL_RESOLVERS"));

    @Test
    @DisplayName("⭐⭐ Mọi biến compose.prod.yml tham chiếu đều có trong cả hai tệp env mẫu")
    void moiBienComposeCanDeuCoTrongTepMau() {
        Set<String> composeCan = bienComposeThamChieu();

        for (String ten : TEP_ENV) {
            Set<String> daKhai = bienDaKhai(timTuGocKho("deploy/env/" + ten));
            Set<String> thieu = new TreeSet<>(composeCan);
            thieu.removeAll(daKhai);
            thieu.removeAll(MIEN_TRU);

            assertThat(thieu)
                    .as(
                            """
                            `%s` thiếu biến mà `compose.prod.yml` tham chiếu.

                            Compose viết chúng ở dạng `${TÊN}` KHÔNG có `:?`, nên thiếu KHÔNG phải là lỗi \
                            lúc khởi động — nó thay bằng chuỗi rỗng rồi chạy tiếp. Người điền `.env` lần \
                            đầu dựa vào tệp mẫu này làm danh sách đầy đủ (deploy-guideline.md §3.2), nên \
                            biến vắng mặt ở đây là biến sẽ không bao giờ được điền.

                            Sửa: thêm vào CẢ HAI tệp mẫu, hoặc thêm vào MIEN_TRU kèm lý do ai cấp nó.""",
                            ten)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("compose.prod.yml phải tham chiếu ít nhất 20 biến — chặn xanh-trên-tập-rỗng")
    void composeThamChieuTapKhacRong() {
        // conventions.md §1.5: một phép kiểm chạy qua tập rỗng vẫn xanh trọn vẹn. Nếu ai đó đổi tên
        // tệp compose hoặc regex hỏng, bài trên sẽ xanh mà không kiểm gì — dòng này chặn đúng chỗ đó.
        assertThat(bienComposeThamChieu()).hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("⛔ Và bài kiểm phải thật sự bắt được thiếu sót — kiểm chứng ngược")
    void batDuocKhiThieuBien() {
        Set<String> composeCan = bienComposeThamChieu();
        Set<String> daKhai = bienDaKhai(timTuGocKho("deploy/env/staging.env.example"));

        // Sáu biến đúng là bộ đã thiếu thật trước bản vá 24/8. Giả lập tệp mẫu chưa có chúng và
        // khẳng định phép trừ nêu ra đủ cả sáu — nếu không thì bài trên chỉ chứng minh chưa ai thử.
        List<String> daTungThieu = List.of(
                "PUBLIC_DOMAIN",
                "ADMIN_DOMAIN",
                "FILES_DOMAIN",
                "MINIO_ROOT_USER",
                "MINIO_ROOT_PASSWORD",
                "REVALIDATE_SECRET");
        assertThat(composeCan)
                .as("sáu biến này phải thật sự được compose tham chiếu, nếu không thì ví dụ đã lỗi thời")
                .containsAll(daTungThieu);

        Set<String> mauCu = new TreeSet<>(daKhai);
        daTungThieu.forEach(mauCu::remove);

        Set<String> thieu = new TreeSet<>(composeCan);
        thieu.removeAll(mauCu);
        thieu.removeAll(MIEN_TRU);
        assertThat(thieu).containsExactlyInAnyOrderElementsOf(daTungThieu);
    }

    // -------------------------------------------------------------------------

    private static Set<String> bienComposeThamChieu() {
        Set<String> ket = new TreeSet<>();
        Matcher khop = THAM_CHIEU.matcher(doc(timTuGocKho("deploy/compose.prod.yml")));
        while (khop.find()) {
            ket.add(khop.group(1));
        }
        return ket;
    }

    private static Set<String> bienDaKhai(Path duongDan) {
        Set<String> ket = new TreeSet<>();
        for (String dong : doc(duongDan).lines().toList()) {
            Matcher khop = KHAI_BAO.matcher(dong);
            if (khop.find()) {
                ket.add(khop.group(1));
            }
        }
        return ket;
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    /** Đi ngược lên từ thư mục đang chạy để tìm gốc kho mã — cùng cách {@link EnvFileCommentTest} dùng. */
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
