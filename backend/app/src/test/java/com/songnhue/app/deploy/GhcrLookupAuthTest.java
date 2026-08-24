package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Lượt tra image trên GHCR phải chạy ở trạng thái ĐÃ đăng nhập.</b>
 *
 * <h2>Lỗi này đã xảy ra — và nó chỉ ra sai chỗ</h2>
 *
 * Ngày 24/8, cả ba lượt CD Staging đều hỏng với thông báo <i>"Không tìm thấy image 'app' trong 50
 * commit gần nhất"</i>, kèm ba bước chẩn đoán trỏ vào job đóng gói · gói GHCR · bước đăng nhập.
 * <b>Cả ba đều là ngõ cụt</b>: job đóng gói xanh ở mọi commit của {@code dev}, gói GHCR có đủ tag,
 * và log ghi rõ {@code Login Succeeded!}.
 *
 * <p>Nguyên nhân thật nằm ở một dòng cách đó 100 dòng: bước tra image bị gắn
 *
 * <pre>
 *   env:
 *     DOCKER_CONFIG: ${{ runner.temp }}/.docker
 * </pre>
 *
 * còn {@code docker/login-action} ghi thông tin đăng nhập vào {@code $HOME/.docker}. Hai thư mục
 * khác nhau, nên lượt tra đi <b>ẩn danh</b>. Gói GHCR mặc định là <b>riêng tư</b> kể cả khi kho mã
 * công khai (kiểm chứng 24/8: token ẩn danh trả về rỗng, {@code GET .../manifests/<sha>} trả
 * <b>403</b>) — thế là cả 50 ứng viên đều trượt, và vòng lặp báo "không có image".
 *
 * <h2>Vì sao cần một bài kiểm chứ không phải một lời dặn</h2>
 *
 * Đây đúng dạng CLAUDE.md luật 9: <i>một khẳng định không phân biệt được hai trạng thái thì không
 * khẳng định gì</i>. {@code docker manifest inspect ... 2>/dev/null} làm 403 (không có quyền) và
 * 404 (chưa dựng) trông y hệt nhau. Bản vá gồm hai phần — gỡ {@code DOCKER_CONFIG}, và thêm một
 * lượt tra thử <b>để nguyên stderr</b> để hỏng về quyền tự khai là hỏng về quyền — còn bài này canh
 * phần thứ nhất khỏi bị đặt lại.
 *
 * <p>Và nó là dạng <i>"cơ chế chưa ai đi qua"</i> (luật 7): workflow này viết từ PR #1 nhưng mãi tới
 * 24/8 mới chạy thật lần đầu. Bốn tháng "xanh" chỉ vì chưa ai bấm.
 */
class GhcrLookupAuthTest {

    /** Chỉ khớp DẠNG KHOÁ YAML, không khớp chữ {@code DOCKER_CONFIG} trong chú thích. */
    private static final Pattern KHOA_DOCKER_CONFIG =
            Pattern.compile("^[ \\t]*DOCKER_CONFIG[ \\t]*:", Pattern.MULTILINE);

    private static final String TRA_IMAGE = "docker manifest inspect";
    private static final String DANG_NHAP = "docker/login-action";

    private static final List<String> WORKFLOW =
            List.of(".github/workflows/deploy-staging.yml", ".github/workflows/deploy-prod.yml");

    @Test
    @DisplayName("⭐⭐ Workflow triển khai không được đổi DOCKER_CONFIG ở bước tra image")
    void khongDuocDoiDockerConfig() {
        for (String duongDan : WORKFLOW) {
            String noiDung = doc(duongDan);
            Matcher m = KHOA_DOCKER_CONFIG.matcher(noiDung);

            assertThat(m.find())
                    .as(
                            """
                            `%s` đặt khoá `DOCKER_CONFIG:`.

                            `docker/login-action` ghi thông tin đăng nhập vào `$HOME/.docker`. Trỏ \
                            `DOCKER_CONFIG` sang chỗ khác là ép mọi lệnh docker ở bước đó đi ẩn danh — \
                            mà gói GHCR RIÊNG TƯ, nên lượt tra sẽ trượt SẠCH rồi báo "không tìm thấy \
                            image". Thông báo ấy trỏ vào job đóng gói và bước đăng nhập: cả hai đều xanh, \
                            và người đọc mất hàng giờ ở ngõ cụt (§10.43).

                            Muốn dùng thư mục riêng thì phải đặt CÙNG giá trị đó ở bước đăng nhập — \
                            và khi ấy hãy sửa luôn bài kiểm này để nó canh đúng ràng buộc mới.""",
                            duongDan)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("⭐ Bước đăng nhập GHCR phải đứng TRƯỚC lượt tra image đầu tiên")
    void dangNhapPhaiDungTruocLuotTra() {
        for (String duongDan : WORKFLOW) {
            // ⚠ PHẢI bỏ dòng chú thích trước khi đo vị trí. Bản đầu của bài này đo trên nguyên văn
            //   và đỏ ngay — vì chính `deploy-staging.yml` có một chú thích nhắc tên lệnh
            //   `docker manifest inspect` ở đầu tệp, đứng trước bước đăng nhập. Đo văn bản thay vì
            //   đo cấu trúc là đúng lỗi CLAUDE.md luật 2.
            String noiDung = boChuThich(doc(duongDan));

            int viTriTra = noiDung.indexOf(TRA_IMAGE);
            int viTriDangNhap = noiDung.indexOf(DANG_NHAP);

            assertThat(viTriTra)
                    .as("`%s` không còn lượt tra `%s` nào — bài kiểm này sẽ xanh trên tập rỗng", duongDan, TRA_IMAGE)
                    .isGreaterThan(-1);
            assertThat(viTriDangNhap)
                    .as("`%s` tra image mà không có bước `%s` nào", duongDan, DANG_NHAP)
                    .isGreaterThan(-1);
            assertThat(viTriDangNhap)
                    .as(
                            """
                            `%s` đặt bước đăng nhập GHCR SAU lượt tra image.

                            Gói GHCR mặc định riêng tư, nên lượt tra ẩn danh trượt hết ứng viên rồi báo \
                            "không tìm thấy image" — sai hẳn nguyên nhân.""",
                            duongDan)
                    .isLessThan(viTriTra);
        }
    }

    @Test
    @DisplayName("Lượt tra hỏng về QUYỀN phải có đường báo riêng, không lẫn với thiếu image")
    void coDuongBaoRiengChoLoiQuyen() {
        String noiDung = doc(".github/workflows/deploy-staging.yml");

        // Phép tra thử: KHÔNG có `2>/dev/null` thì stderr của docker mới hiện ra log.
        assertThat(noiDung)
                .as(
                        """
                        `deploy-staging.yml` không còn lượt tra thử nào báo lỗi QUYỀN riêng.

                        Thiếu nó thì 403 và 404 lại cho ra cùng một thông báo, và lần sau vẫn mất \
                        hàng giờ đi tìm một image vốn nằm sẵn ở đó.""")
                .contains("đây là lỗi QUYỀN, không phải thiếu image");
    }

    /** Bỏ mọi dòng chú thích (YAML và shell đều dùng {@code #}) để đo CẤU TRÚC, không đo văn bản. */
    private static String boChuThich(String noiDung) {
        return noiDung.lines()
                .filter(dong -> !dong.stripLeading().startsWith("#"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String doc(String duongDanTuongDoi) {
        try {
            return Files.readString(timTuGocKho(duongDanTuongDoi), StandardCharsets.UTF_8);
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
