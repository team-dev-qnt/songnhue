package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Ba chỗ khai tham số {@code initdb}, và không chỗ nào được lệch chỗ nào.</b>
 *
 * <h2>Chuyện đã xảy ra</h2>
 *
 * Tham số collation được chốt 14/8/2026 và ghi vào {@code compose.infra.yml} (đường local) cùng
 * {@link com.songnhue.app.testsupport.SongnhuePostgres} (đường test). Nó <b>không bao giờ</b> được
 * ghi vào {@code compose.prod.yml} — tệp dựng cluster staging và production. Cái thiếu ấy sống sót
 * 12 ngày vì <b>mọi lượt thử ở máy đều xanh</b>: cả hai đường được đi thử đều có tham số, còn đường
 * không ai đi thì không.
 *
 * <p>Cùng hình dạng với {@link PostgresInitEnvTest} (biến {@code DB_APP_PASSWORD}), và với CLAUDE.md
 * luật 14: chỗ nào con người phải nhớ nhiều nơi thì chỗ đó cần một phép kiểm nhớ hộ.
 *
 * <h2>Vì sao khoản nợ này đắt hơn vẻ ngoài của nó</h2>
 *
 * {@code POSTGRES_INITDB_ARGS} chỉ có tác dụng ở lượt dựng volume <b>đầu tiên</b>. Thêm nó vào một
 * cluster đã có dữ liệu thì image bỏ qua hoàn toàn — đo được 26/8: sửa tệp compose rồi
 * {@code up -d --force-recreate} vẫn cho ra đúng collation cũ. Nên bài kiểm này chỉ giữ cho
 * <b>cluster chưa dựng</b> được dựng đúng; cluster <b>đang chạy</b> phải được ĐO, và việc đó nằm ở
 * {@code deploy/postgres/kiem-collation.sh} — xem {@link #cluterDangChayPhaiDuocDoLucTrienKhai()}.
 */
class PostgresCollationParityTest {

    /**
     * ⚠ Ghép từ hai mảnh, không viết liền. Bài kiểm này quét cả cây mã nguồn tìm chuỗi tham số, và
     * một hằng số viết liền sẽ làm chính tệp này thành một "nguồn khai báo" — bộ canh tự khớp vào
     * mình là bộ canh không canh gì (đã mắc ba lượt trong dự án).
     */
    private static final String MOC = "--locale" + "-provider=";

    /** Chuỗi tham số nằm trong dấu nháy kép ở cả YAML lẫn Java, nên một biểu thức là đủ cho cả hai. */
    private static final Pattern CHUOI_THAM_SO = Pattern.compile("\"([^\"]*" + Pattern.quote(MOC) + "[^\"]*)\"");

    /**
     * Chỉ quét tệp <b>dựng ra một cluster</b>. Tài liệu {@code .md} cố ý nằm ngoài: chúng trích dẫn
     * tham số trong dấu nháy ngược để giải thích, và bắt một đoạn văn phải khớp từng byte với cấu
     * hình là đổi một lỗi thật lấy một lỗi giả.
     */
    private static final Set<String> DUOI = Set.of(".yml", ".yaml", ".java", ".sh");

    /** Không có tệp nào trong danh sách này thì bài kiểm đỏ — chặn "quét ra tập rỗng nên xanh". */
    private static final List<String> BAT_BUOC =
            List.of("deploy/compose.infra.yml", "deploy/compose.prod.yml", "SongnhuePostgres.java");

    private static final Set<String> BO_QUA_THU_MUC =
            Set.of(".git", "node_modules", "target", ".next", "dist", "build", "venv", ".venv", "coverage");

    @Test
    @DisplayName("⭐⭐ Mọi chỗ khai tham số initdb đều khai CÙNG MỘT chuỗi")
    void moiNoiKhaiCungMotChuoi() {
        var theoTep = quetCaCay();

        Set<String> khacNhau = new TreeSet<>(theoTep.values());
        assertThat(khacNhau)
                .as(
                        """
                        Tham số `initdb` đang có %d bản khác nhau trong cây mã nguồn: %s

                        Cụ thể từng tệp:
                        %s

                        ⛔ Lệch một ký tự là hai cluster có collation khác nhau, và chênh lệch ấy \
                        chỉ lộ ra khi có người báo danh sách tiếng Việt xếp sai — lúc đó sửa được \
                        thì phải dump toàn bộ CSDL rồi dựng lại.""",
                        khacNhau.size(),
                        khacNhau,
                        theoTep.entrySet().stream()
                                .map(e -> "  · %s → %s".formatted(e.getKey(), e.getValue()))
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse("(không có)"))
                .hasSize(1);
    }

    @Test
    @DisplayName("⛔ Và chuỗi ấy phải là ICU vi-VN — ba tệp giống nhau mà cùng sai vẫn là sai")
    void chuoiAyPhaiLaIcuViVn() {
        // Khẳng định trên MỌI giá trị, không chỉ giá trị đầu: `moiNoiKhaiCungMotChuoi` có thể đỏ
        // vì lệch, và khi ấy bài này vẫn phải nói được cái nào sai chứ không im lặng lấy cái đầu.
        assertThat(quetCaCay().values()).allSatisfy(chuoi -> {
            assertThat(chuoi)
                    .as("`ORDER BY` tiếng Việt đúng (Anh < Dung < Đăng < Em) đòi nhà cung cấp ICU")
                    .contains(MOC + "icu");
            assertThat(chuoi)
                    .as("ICU không có locale thì xếp theo quy tắc chung, không theo bảng chữ cái tiếng Việt")
                    .contains("--icu-" + "locale=vi-VN");
        });
    }

    @Test
    @DisplayName("Ba tệp bắt buộc đều phải khai — chặn xanh-trên-tập-rỗng")
    void batBuocPhaiCoMatDu() {
        // conventions.md §1.5. Đổi tên tệp, đổi cách viết, hỏng regex — cả ba đều làm hai bài trên
        // xanh mà không so gì. Danh sách này là thứ duy nhất phân biệt "không có vi phạm" với
        // "không quét được gì".
        var theoTep = quetCaCay();
        for (String can : BAT_BUOC) {
            assertThat(theoTep.keySet())
                    .as("Không tìm thấy khai báo tham số initdb ở `%s` — nó phải có, hoặc bài kiểm này đã mù", can)
                    .anySatisfy(tep -> assertThat(tep).endsWith(can));
        }
        assertThat(theoTep).hasSizeGreaterThanOrEqualTo(BAT_BUOC.size());
    }

    /**
     * Bộ canh phải <b>bắt được</b> vi phạm, không chỉ xanh khi không có vi phạm (CLAUDE.md luật 1).
     *
     * <p>Kiểm trên chính hàm bóc tách, với một mẫu lệch đúng một ký tự — vì kiểu lệch thật sẽ trông
     * như thế, chứ không phải như hai chuỗi khác hẳn nhau.
     */
    @Test
    @DisplayName("⭐ Tự kiểm chứng: bộ bóc tách phân biệt được hai chuỗi lệch một ký tự")
    void tuKiemChung() {
        String dung = "--encoding=UTF8 " + MOC + "icu --icu-" + "locale=vi-VN --locale=C.UTF-8";
        String lech = dung.replace("vi-VN", "vi_VN");

        assertThat(bocTach("POSTGRES_INITDB_ARGS: \"" + dung + "\"")).containsExactly(dung);
        assertThat(bocTach("x = \"" + dung + "\"; y = \"" + lech + "\";"))
                .as("Hai bản khác nhau trong cùng một tệp cũng phải hiện ra là hai")
                .containsExactlyInAnyOrder(dung, lech);
        assertThat(bocTach("không có tham số nào ở đây")).isEmpty();
        assertThat(bocTach("`" + dung + "` trong dấu nháy ngược của tài liệu"))
                .as("Trích dẫn trong văn bản không phải một khai báo — nó không nằm trong nháy kép")
                .isEmpty();
    }

    /**
     * Tệp cấu hình chỉ nói cluster <b>sẽ</b> được dựng ra sao. Nó không nói cluster <b>đã</b> được
     * dựng ra sao, và hai điều đó có thể khác nhau vĩnh viễn — chính là chuyện đang xảy ra với
     * staging. Nên phải có một phép ĐO trên cluster đang chạy, và phép đo ấy phải được GỌI.
     *
     * <p>CLAUDE.md: "một cổng kiểm tồn tại trong mã nhưng chưa có hiệu lực ở nơi nó phải chặn".
     */
    @Test
    @DisplayName("⭐⭐ Cluster ĐANG CHẠY được đo ở mỗi lượt triển khai, không chỉ được khai trong compose")
    void cluterDangChayPhaiDuocDoLucTrienKhai() {
        Path script = timTuGocKho("deploy/postgres/kiem-collation.sh");
        assertThat(Files.isExecutable(script))
                .as("`%s` phải có cờ chạy — rsync giữ nguyên quyền, không có cờ là bước triển khai đỏ", script)
                .isTrue();

        String workflow = doc(timTuGocKho(".github/workflows/deploy.yml"));
        assertThat(workflow)
                .as(
                        """
                        `deploy.yml` không gọi `kiem-collation.sh`.

                        Một script đo collation mà không lượt triển khai nào chạy nó thì cluster sai \
                        vẫn đi qua trọn vẹn — đúng thứ đã cho staging một cluster xếp sai suốt từ \
                        25/8 mà không lượt deploy nào đỏ.""")
                .contains("kiem-collation.sh");
    }

    // -------------------------------------------------------------------------

    /** path (tương đối gốc kho) → chuỗi tham số. Lệch nhau trong cùng một tệp thì tệp ấy đỏ ngay. */
    private static TreeMap<String, String> quetCaCay() {
        Path goc = timTuGocKho("deploy").getParent();
        TreeMap<String, String> ket = new TreeMap<>();

        try {
            // ⚠ `walkFileTree` + SKIP_SUBTREE chứ không phải `Files.walk(...).filter(...)`:
            //   lọc sau khi đã duyệt vẫn duyệt qua `node_modules` và `.git` — hàng trăm nghìn tệp
            //   cho một bài kiểm chỉ cần bốn.
            Files.walkFileTree(goc, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path thuMuc, BasicFileAttributes a) {
                    return BO_QUA_THU_MUC.contains(thuMuc.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    if (!duoiDuocQuet(tep)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Set<String> thay = bocTach(doc(tep));
                    if (!thay.isEmpty()) {
                        assertThat(thay)
                                .as("`%s` khai HAI chuỗi tham số khác nhau trong cùng một tệp", goc.relativize(tep))
                                .hasSize(1);
                        ket.put(goc.relativize(tep).toString(), thay.iterator().next());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path tep, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được cây mã nguồn từ " + goc, e);
        }
        return ket;
    }

    private static boolean duoiDuocQuet(Path p) {
        String ten = p.getFileName().toString();
        return DUOI.stream().anyMatch(ten::endsWith);
    }

    /** Hàm thuần — tách ra để {@link #tuKiemChung()} thử được với dữ liệu tự soạn. */
    static Set<String> bocTach(String noiDung) {
        Set<String> ket = new LinkedHashSet<>();
        Matcher khop = CHUOI_THAM_SO.matcher(noiDung);
        while (khop.find()) {
            ket.add(khop.group(1));
        }
        return ket;
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Tệp nhị phân lẫn vào cây quét — bỏ qua, không phải nguồn khai báo cấu hình.
            return "";
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
