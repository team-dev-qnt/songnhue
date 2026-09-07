package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi biến {@code 10-bootstrap.sh} đòi phải được CẢ HAI tệp compose truyền vào service
 * {@code postgres}.</b>
 *
 * <h2>Lỗi này đã làm hỏng lượt dựng staging đầu tiên</h2>
 *
 * {@code compose.prod.yml} khai {@code DB_APP_PASSWORD}, còn script gọi
 * {@code require_env DB_PASSWORD}. Hai cái tên khác nhau, nên:
 *
 * <ul>
 *   <li>biến được truyền thì <b>không ai đọc</b> (CLAUDE.md luật 15);
 *   <li>biến script cần thì <b>không được truyền</b> → container {@code postgres} quay vòng khởi
 *       động lại, rồi {@code songnhue_app} báo {@code password authentication failed}.
 * </ul>
 *
 * <h2>Vì sao nó sống sót tới tận lúc dựng máy thật</h2>
 *
 * {@code compose.infra.yml} — đường chạy <b>local</b> — khai <b>đúng</b> cả bốn tên. Nên mọi lượt
 * thử ở máy đều xanh, và cái sai chỉ tồn tại trên đường mà không ai đi cho tới hôm dựng VPS.
 *
 * <p>Cùng hình dạng với CLAUDE.md luật 3: hai đường vào cùng một script, chỉ một đường được đi thử.
 * Bài kiểm này bắt cả hai đi cùng nhau.
 *
 * @see ComposeEnvCompletenessTest bài cùng họ, soi phía tệp env mẫu
 */
class PostgresInitEnvTest {

    /** {@code require_env TÊN_BIẾN} trong script khởi tạo. */
    private static final Pattern DOI_HOI = Pattern.compile("require_env\\s+([A-Z][A-Z0-9_]*)");

    /** {@code TÊN_BIẾN:} trong khối `environment:` — compose dùng dấu hai chấm, không dấu bằng. */
    // ⚠ `MULTILINE` là bắt buộc: thiếu nó thì `^` chỉ khớp đầu CẢ CHUỖI, hàm trả về tập rỗng,
    //   và bài kiểm "thiếu biến" đỏ với cả bốn biến — báo sai chỗ. Đã mắc đúng lỗi này một lượt.
    private static final Pattern TRUYEN = Pattern.compile("^\\s+([A-Z][A-Z0-9_]*):\\s", Pattern.MULTILINE);

    private static final List<String> COMPOSE = List.of("compose.prod.yml", "compose.infra.yml");

    @Test
    @DisplayName("⭐⭐ Mọi biến 10-bootstrap.sh đòi đều được truyền vào postgres ở CẢ HAI compose")
    void moiBienScriptDoiDeuDuocTruyen() {
        Set<String> canCo = bienScriptDoi();

        for (String ten : COMPOSE) {
            Set<String> daTruyen = bienTruyenChoPostgres(ten);
            Set<String> thieu = new TreeSet<>(canCo);
            thieu.removeAll(daTruyen);

            assertThat(thieu)
                    .as(
                            """
                            `%s` không truyền biến mà `deploy/postgres/init/10-bootstrap.sh` gọi \
                            `require_env`.

                            Thiếu thì container postgres quay vòng khởi động lại ngay ở lần dựng \
                            volume đầu tiên, và triệu chứng tiếp theo là `songnhue_app` báo \
                            `password authentication failed` — không ai đọc ra được nguyên nhân từ \
                            dòng đó.

                            ⚠ Sửa ở CẢ HAI tệp: `compose.infra.yml` là đường local, `compose.prod.yml` \
                            là đường staging/production. Đúng một bên là lỗi chỉ lộ ra lúc dựng máy \
                            thật.""",
                            ten)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("⛔ Và không tệp compose nào truyền biến THỪA cho postgres — biến không ai đọc là một lỗi")
    void khongTruyenBienThua() {
        // CLAUDE.md luật 15: "Công tắc / cột / tham số chưa ai đọc là một lỗi, không phải việc để
        // dành". `DB_APP_PASSWORD` đã nằm đó im lặng cho tới lúc nó che mất biến thật sự thiếu.
        Set<String> canCo = bienScriptDoi();

        for (String ten : COMPOSE) {
            Set<String> thua = new TreeSet<>();
            for (String bien : bienTruyenChoPostgres(ten)) {
                // Chỉ soi họ `DB_*`: postgres còn nhận POSTGRES_*, TZ… do image của nó đọc.
                if (bien.startsWith("DB_") && !canCo.contains(bien) && !MIEN_TRU.contains(bien)) {
                    thua.add(bien);
                }
            }
            assertThat(thua)
                    .as(
                            "`%s` truyền biến `DB_*` mà `10-bootstrap.sh` không đọc — hoặc là sai tên, "
                                    + "hoặc là rác. Cả hai đều phải xử lý, không để lại.",
                            ten)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Script phải đòi ít nhất 4 biến — chặn xanh-trên-tập-rỗng")
    void scriptDoiTapKhacRong() {
        // conventions.md §1.5: regex hỏng hoặc script đổi tên thì hai bài trên xanh mà không kiểm gì.
        assertThat(bienScriptDoi()).hasSizeGreaterThanOrEqualTo(4);
    }

    /** Biến `DB_*` truyền cho postgres nhưng do thứ khác đọc, không phải script khởi tạo. */
    private static final Set<String> MIEN_TRU = Set.of(
            // `compose.infra.yml` dùng hai biến này cho `ports:` và `POSTGRES_INITDB_ARGS`.
            "DB_PORT", "DB_ARGS");

    // -------------------------------------------------------------------------

    private static Set<String> bienScriptDoi() {
        Set<String> ket = new TreeSet<>();
        Matcher khop = DOI_HOI.matcher(doc(timTuGocKho("deploy/postgres/init/10-bootstrap.sh")));
        while (khop.find()) {
            ket.add(khop.group(1));
        }
        return ket;
    }

    /**
     * Đọc khối {@code environment:} của riêng service {@code postgres}.
     *
     * <p>Cắt theo thụt lề thay vì phân tích YAML cho tử tế là đủ ở đây, nhưng phải chắc mình cắt
     * đúng khối — nên có bài {@link #scriptDoiTapKhacRong()} và khẳng định dưới đây.
     */
    private static Set<String> bienTruyenChoPostgres(String tenTep) {
        String noiDung = doc(timTuGocKho("deploy/" + tenTep));
        int dau = noiDung.indexOf("\n  postgres:");
        assertThat(dau).as("`%s` phải có service `postgres`", tenTep).isNotNegative();

        // Service kế tiếp bắt đầu bằng đúng hai dấu cách + tên + dấu hai chấm ở cuối dòng.
        Matcher ketThuc =
                Pattern.compile("^  [a-z][a-z0-9-]*:$", Pattern.MULTILINE).matcher(noiDung);
        int cuoi = noiDung.length();
        while (ketThuc.find()) {
            if (ketThuc.start() > dau + 1) {
                cuoi = ketThuc.start();
                break;
            }
        }

        Set<String> ket = new TreeSet<>();
        Matcher khop = TRUYEN.matcher(noiDung.substring(dau, cuoi));
        while (khop.find()) {
            ket.add(khop.group(1));
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
