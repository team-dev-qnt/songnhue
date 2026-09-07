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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Tệp {@code .env} của Compose KHÔNG phải script shell — cấm {@code source} nó.</b>
 *
 * <h2>Lỗi này đã xảy ra ở đúng lượt chạy đầu tiên</h2>
 *
 * Lượt "chạy thử" đầu tiên của {@code seed-staging.yml} hỏng với:
 *
 * <pre>
 *   /opt/songnhue/.env: line 87: nofollow: command not found     (exit 127)
 * </pre>
 *
 * Dòng thủ phạm <b>hoàn toàn hợp lệ</b> với Docker Compose:
 *
 * <pre>
 *   ROBOTS_TAG=noindex, nofollow
 * </pre>
 *
 * Compose đọc thành chuỗi {@code "noindex, nofollow"}. Shell gán {@code ROBOTS_TAG=noindex,} rồi
 * <b>chạy {@code nofollow} như một lệnh</b>. Cùng một tệp, hai bộ phân tích khác nhau — và tệp ấy
 * viết cho bộ kia.
 *
 * <h2>Vì sao nó qua được mọi lượt thử trước đó</h2>
 *
 * Đo trực tiếp trên hai tệp mẫu đang dùng:
 *
 * <table border="1">
 *   <caption>{@code source} tệp env mẫu</caption>
 *   <tr><th>Tệp</th><th>Kết quả</th></tr>
 *   <tr><td>{@code prod.env.example}</td><td><b>chạy lọt</b> — không giá trị nào nhiều từ</td></tr>
 *   <tr><td>{@code staging.env.example}</td><td>chết ở dòng 137</td></tr>
 * </table>
 *
 * Chỉ staging mới có {@code ROBOTS_TAG=noindex, nofollow} (production phải cho đánh chỉ mục). Một
 * cái bẫy nằm im cho tới đúng môi trường có dữ liệu kích hoạt nó — CLAUDE.md luật 24: <i>bộ canh
 * theo hình dạng phải được thử với dữ liệu THẬT đang dùng</i>.
 *
 * <p>Vì vậy bài kiểm này có hai vế: cấm {@code source}, <b>và</b> khẳng định tệp mẫu vẫn còn ít
 * nhất một giá trị nhiều từ. Vế sau giữ vế trước khỏi trở thành lời dặn suông — nếu ngày nào đó
 * không còn giá trị nào như thế, ta cần biết là mình đang canh một tập rỗng.
 */
class EnvFileNotShellTest {

    /** {@code . "$ENV_FILE"} · {@code source .env} · {@code . /opt/songnhue/.env} */
    private static final Pattern NAP_KIEU_SHELL =
            Pattern.compile("(^|\\s)(\\.|source)\\s+[\"']?\\$?\\{?[A-Za-z0-9_/.]*(ENV_FILE|\\.env)");

    /** {@code TÊN=giá trị nhiều từ} — không nháy, không phải chú thích cuối dòng. */
    private static final Pattern NHIEU_TU = Pattern.compile("^[A-Z][A-Z0-9_]*=[^\"'#\\s]+ +[^#\\s]+");

    private static final List<String> TEP_ENV_MAU =
            List.of("deploy/env/staging.env.example", "deploy/env/prod.env.example");

    @Test
    @DisplayName("⭐⭐ Không script nào trong deploy/ được `source` tệp .env")
    void khongScriptNaoSourceEnv() {
        List<Path> script = scriptTrongDeploy();

        assertThat(script)
                .as("không tìm thấy script nào trong deploy/ — bài này sẽ xanh trên tập rỗng")
                .isNotEmpty();

        for (Path tep : script) {
            String khongChuThich = doc(tep).lines()
                    .filter(dong -> !dong.stripLeading().startsWith("#"))
                    .collect(Collectors.joining("\n"));

            assertThat(NAP_KIEU_SHELL.matcher(khongChuThich).find())
                    .as(
                            """
                            `%s` nạp tệp .env bằng `source`/`.`.

                            Tệp .env của Compose KHÔNG phải script shell. Compose cho phép giá trị nhiều \
                            từ không nháy (`ROBOTS_TAG=noindex, nofollow`); shell thì gán nửa đầu rồi CHẠY \
                            nửa sau như một lệnh — `command not found`, exit 127. Đã hỏng đúng như vậy ở \
                            lượt chạy đầu tiên của seed-staging (§10.46).

                            Đọc bằng bộ phân tích theo luật Compose — xem `doc_env()` trong \
                            `deploy/seed/seed.sh`.""",
                            tep.getFileName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("⭐ Tệp env mẫu vẫn còn giá trị nhiều từ — neo bài trên vào dữ liệu thật")
    void tepMauVanConGiaTriNhieuTu() {
        long soDong = TEP_ENV_MAU.stream()
                .flatMap(ten -> doc(timTuGocKho(ten)).lines())
                .filter(dong -> NHIEU_TU.matcher(dong).find())
                .count();

        assertThat(soDong)
                .as(
                        """
                        Không tệp env mẫu nào còn giá trị nhiều từ không nháy.

                        Nếu đúng là đã bỏ hết thì bài `khongScriptNaoSourceEnv` đang canh một tập rỗng — \
                        hãy sửa bài này cho khớp thực tế mới, đừng để nó tự xanh. Nhắc lại vì sao ràng \
                        buộc tồn tại: `source` một tệp .env hợp lệ của Compose có thể CHẠY một phần giá \
                        trị như lệnh.""")
                .isGreaterThan(0);
    }

    private static List<Path> scriptTrongDeploy() {
        try (Stream<Path> duyet = Files.walk(timTuGocKho("deploy"))) {
            return duyet.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sh"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String doc(Path tep) {
        try {
            return Files.readString(tep, StandardCharsets.UTF_8);
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
