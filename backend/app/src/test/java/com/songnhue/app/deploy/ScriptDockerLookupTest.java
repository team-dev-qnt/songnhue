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
 * <b>Script vận hành không được gọi {@code docker compose} — kể cả lệnh chỉ đọc.</b>
 *
 * <h2>Vì sao một lệnh chỉ đọc cũng hỏng</h2>
 *
 * Compose <b>nội suy toàn bộ tệp</b> trước khi trả lời bất cứ câu hỏi nào. {@code compose.prod.yml}
 * khai ba tag image ở dạng bắt buộc — {@code ${APP_IMAGE:?…}}, {@code ${ADMIN_IMAGE:?…}},
 * {@code ${PUBLIC_IMAGE:?…}} — và ba biến ấy <b>cố ý không nằm trong {@code .env}</b>: workflow
 * triển khai {@code export} chúng ngay trước khi gọi compose, vì ghim một phiên bản image vào đĩa
 * máy chủ là đúng thứ luồng đề bạt tránh.
 *
 * <p>Nên mọi script chạy <i>ngoài</i> lượt triển khai đều thiếu chúng, và mọi lệnh compose trong
 * script đó hỏng. Tái hiện tại chỗ, đúng thông báo của máy chủ:
 *
 * <pre>
 *   $ docker compose --env-file /tmp/min.env -f compose.prod.yml ps -q minio
 *   error while interpolating services.app.image: required variable APP_IMAGE is missing a value
 *   → mã thoát 1, kết quả trả về: rỗng
 * </pre>
 *
 * <h2>Hai chỗ đã trả giá, và cả hai đều báo SAI nguyên nhân</h2>
 *
 * <table border="1">
 *   <caption>Cùng một gốc, hai thông báo lạc hướng</caption>
 *   <tr><th>Script</th><th>Người vận hành đọc được gì</th></tr>
 *   <tr><td>{@code seed.sh}</td>
 *       <td>{@code docker: no name set for network} (exit 125) — vì chuỗi rỗng đi thẳng vào
 *           {@code --network ""}</td></tr>
 *   <tr><td>{@code pre-deploy-dump.sh}</td>
 *       <td><b>"✗ Postgres không trả lời — DỪNG"</b> rồi thoát 1. CSDL hoàn toàn khoẻ. Và đây là bản
 *           chụp trước triển khai — <b>điểm quay lui duy nhất</b> khi migration làm hỏng dữ liệu, vì
 *           dự án cố ý không có PITR (§6.5). Lượt deploy production đầu tiên sẽ dừng ở đây, với một
 *           thông báo cử người đi cứu một CSDL không hề ốm.</td></tr>
 * </table>
 *
 * <p>Cách chữa: hỏi bằng <b>nhãn {@code com.docker.compose.*}</b> mà chính compose gắn lên container
 * lúc tạo — hỏi thứ đang chạy, không hỏi tệp mô tả nó. Xem {@code deploy/lib/docker-svc.sh}.
 */
class ScriptDockerLookupTest {

    private static final String GOI_COMPOSE = "docker compose";
    private static final String TRA_BANG_NHAN = "container_cua";

    /** {@code ${TÊN:?…}} — dạng bắt buộc, thiếu là compose dừng. */
    private static final Pattern IMAGE_BAT_BUOC = Pattern.compile("\\$\\{(APP_IMAGE|ADMIN_IMAGE|PUBLIC_IMAGE):\\?");

    @Test
    @DisplayName("⭐⭐ Không script nào trong deploy/ được gọi `docker compose`")
    void khongScriptNaoGoiCompose() {
        List<Path> script = scriptTrongDeploy();

        assertThat(script)
                .as("không tìm thấy script nào trong deploy/ — bài này sẽ xanh trên tập rỗng")
                .isNotEmpty();

        for (Path tep : script) {
            assertThat(boChuThich(doc(tep)))
                    .as(
                            """
                            `%s` gọi `%s`.

                            Compose nội suy TOÀN BỘ tệp trước khi trả lời, kể cả lệnh chỉ đọc như \
                            `ps -q`. `compose.prod.yml` đòi APP_IMAGE/ADMIN_IMAGE/PUBLIC_IMAGE dạng \
                            `${X:?}`, mà ba biến ấy chỉ tồn tại trong lượt triển khai. Script chạy ngoài \
                            lượt ấy sẽ hỏng — và hỏng với thông báo trỏ vào chỗ khác hẳn (§10.48).

                            Dùng `container_cua` / `mang_cua` trong `deploy/lib/docker-svc.sh`: hỏi bằng \
                            nhãn compose gắn trên container đang chạy.""",
                            tep.getFileName(), GOI_COMPOSE)
                    .doesNotContain(GOI_COMPOSE);
        }
    }

    @Test
    @DisplayName("⭐ Phải có script thật dùng cách tra bằng nhãn — neo bài trên vào việc có thật")
    void coScriptDungCachTraBangNhan() {
        // ⚠ PHẢI loại `deploy/lib/` ra. Bản đầu không loại, và lượt kiểm chứng ngược cho thấy nó
        //   KHÔNG bắt được khi cả hai script gọi đều đổi tên hàm: chính tệp ĐỊNH NGHĨA
        //   `container_cua` cũng là một `.sh` trong `deploy/`, nên nó tự đếm mình và bài luôn xanh.
        //   Câu đang hỏi là "có ai DÙNG không", không phải "có ai viết ra nó không".
        long soScript = scriptTrongDeploy().stream()
                .filter(tep -> !tep.getParent().getFileName().toString().equals("lib"))
                .filter(tep -> boChuThich(doc(tep)).contains(TRA_BANG_NHAN))
                .count();

        assertThat(soScript)
                .as(
                        """
                        Không script nào dùng `%s`.

                        Bài `khongScriptNaoGoiCompose` là một khẳng định PHỦ ĐỊNH: bỏ hết script đi thì \
                        nó xanh trọn vẹn mà không canh gì. Dòng này neo nó vào một thứ có thật.""",
                        TRA_BANG_NHAN)
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("⭐ Ba biến image vẫn ở dạng BẮT BUỘC — đây là lý do luật trên tồn tại")
    void baBienImageVanBatBuoc() {
        String compose = doc(timTuGocKho("deploy/compose.prod.yml"));

        assertThat(IMAGE_BAT_BUOC.matcher(compose).results().count())
                .as(
                        """
                        `compose.prod.yml` không còn khai APP_IMAGE/ADMIN_IMAGE/PUBLIC_IMAGE ở dạng \
                        `${X:?}`.

                        Nếu đúng là đã đổi thì lý do cấm `docker compose` trong script có thể không còn \
                        — hãy xem lại luật thay vì để nó nằm đó như một điều cấm không ai nhớ vì sao. \
                        Nhắc lại: dạng `:?` khiến compose DỪNG khi thiếu biến, kể cả với lệnh chỉ đọc, \
                        và ba biến này cố ý chỉ tồn tại trong lượt triển khai.""")
                .isGreaterThanOrEqualTo(4);
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

    private static String boChuThich(String noiDung) {
        return noiDung.lines()
                .filter(dong -> !dong.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"));
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
