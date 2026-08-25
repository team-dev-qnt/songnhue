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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Workflow nào chạy {@code ./mvnw} thì phải đệm bản phân phối Maven và cho lượt tải thử lại.</b>
 *
 * <h2>Lỗi này đã xảy ra, và nó trỏ vào một bước vô can</h2>
 *
 * <pre>
 *   Run ./mvnw -B -ntp spotless:check checkstyle:check
 *   wget: Failed to fetch .../apache-maven-3.9.9-bin.zip
 *   Error: Process completed with exit code 1
 * </pre>
 *
 * URL vẫn sống (đo lại ngay sau đó: <b>HTTP 200</b>, và tải một byte đầu trả <b>206</b>) — đây là
 * một lượt chập mạng trên runner. Nhưng nó lộ ra chỗ mong manh thật:
 *
 * <ul>
 *   <li>{@code cache: maven} của {@code actions/setup-java} <b>chỉ</b> đệm
 *       {@code ~/.m2/repository}. Bản phân phối Maven mà {@code mvnw} tải nằm ở
 *       {@code ~/.m2/wrapper/dists} và không nằm trong đó — nên <b>mỗi lượt CI đều tải lại ~9 MB</b>.</li>
 *   <li>{@code mvnw} tải bằng {@code wget} <b>không có thử lại</b>. Một lượt chập là CI đỏ ở dòng
 *       đầu tiên, trước khi có gì được biên dịch.</li>
 *   <li>Và nó đỏ <b>dưới tên bước kế tiếp</b>: thông báo nói {@code spotless:check}, một bước chưa
 *       từng được chạy. Đúng CLAUDE.md luật 22 — <i>dòng đáng chú ý nhất nằm trước thứ được báo là
 *       lỗi</i>.</li>
 * </ul>
 *
 * <p>Vá bằng hai thứ độc lập: đệm {@code ~/.m2/wrapper} (lượt sau khỏi tải), và tách lượt tải ra
 * một bước riêng có thử lại 3 lần (lượt đầu chập thì tự khỏi, và không bị báo nhầm tên).
 */
class MavenWrapperCiTest {

    private static final String GOI_MVNW = "./mvnw";
    private static final String DUONG_DEM = "~/.m2/wrapper";
    private static final String VONG_THU_LAI = "for lan in 1 2 3";

    @Test
    @DisplayName("⭐⭐ Workflow dùng ./mvnw phải đệm ~/.m2/wrapper TRƯỚC lượt gọi đầu tiên")
    void phaiDemBanPhanPhoiTruocKhiGoi() {
        List<Path> dungMvnw = workflowDungMvnw();

        assertThat(dungMvnw)
                .as("không workflow nào gọi `%s` — bài này sẽ xanh trên tập rỗng", GOI_MVNW)
                .isNotEmpty();

        for (Path tep : dungMvnw) {
            String noiDung = boChuThich(doc(tep));
            int viTriDem = noiDung.indexOf(DUONG_DEM);
            int viTriGoi = noiDung.indexOf(GOI_MVNW);

            assertThat(viTriDem)
                    .as(
                            """
                            `%s` gọi `./mvnw` mà không đệm `%s`.

                            `cache: maven` của setup-java CHỈ đệm `~/.m2/repository`; bản phân phối Maven \
                            nằm ở `~/.m2/wrapper/dists`. Thiếu bước đệm là mỗi lượt CI tải lại ~9 MB bằng \
                            `wget` không thử lại — một lượt chập mạng làm CI đỏ ở dòng đầu tiên (§10.47).""",
                            tep.getFileName(), DUONG_DEM)
                    .isGreaterThan(-1);

            assertThat(viTriDem)
                    .as(
                            "`%s` đặt bước đệm SAU lượt gọi `./mvnw` đầu tiên — đệm khi đã tải xong thì vô nghĩa",
                            tep.getFileName())
                    .isLessThan(viTriGoi);
        }
    }

    @Test
    @DisplayName("⭐ Lượt tải Maven phải có thử lại")
    void luotTaiPhaiCoThuLai() {
        for (Path tep : workflowDungMvnw()) {
            assertThat(boChuThich(doc(tep)))
                    .as(
                            """
                            `%s` không còn vòng thử lại quanh lượt tải Maven.

                            Bộ đệm chỉ giúp từ lượt thứ hai trở đi; lượt đầu (hoặc sau khi đổi phiên bản \
                            wrapper) vẫn phải tải thật. Không thử lại thì một lượt chập mạng bắt người ta \
                            bấm chạy lại tay.""",
                            tep.getFileName())
                    .contains(VONG_THU_LAI);
        }
    }

    private static List<Path> workflowDungMvnw() {
        try (Stream<Path> duyet = Files.list(timTuGocKho(".github/workflows"))) {
            return duyet.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .filter(p -> boChuThich(doc(p)).contains(GOI_MVNW))
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
