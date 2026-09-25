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
 * <b>Năm chỗ khai ảnh MinIO, và không chỗ nào được lệch chỗ nào.</b>
 *
 * <h2>Chuyện đã xảy ra — 14/9/2026</h2>
 *
 * {@link com.songnhue.app.testsupport.SongnhueMinio} mang sẵn một câu dặn trong javadoc:
 * <i>"Cùng image với {@code compose.infra.yml} — đổi ở một nơi thì phải đổi cả hai."</i> Câu ấy
 * đúng, đứng đó từ WS-4, và <b>chưa bao giờ là một cổng kiểm</b>. CLAUDE.md luật 14: chỗ nào con
 * người phải nhớ nhiều nơi thì chỗ đó cần một phép kiểm nhớ hộ.
 *
 * <p>Lượt CI đầu tiên của PR Phase 3 đỏ ở <b>86 lớp</b>, tất cả đều là nạn nhân dây chuyền của một
 * dòng duy nhất:
 *
 * <pre>
 * NotFoundException: Status 404: pull access denied for minio/minio,
 *   repository does not exist or may require 'docker login'
 * </pre>
 *
 * Đo lại cùng ngày: <b>cả repository {@code minio/minio} đã biến mất khỏi Docker Hub</b> — không
 * phải một tag bị dọn. {@code hub.docker.com/v2/repositories/minio/minio/} trả
 * {@code object not found}, registry v2 trả {@code UNAUTHORIZED}. Ảnh nay sống ở {@code quay.io}.
 *
 * <h2>Vì sao {@code make ci-local} ở máy vẫn xanh</h2>
 *
 * Ảnh đã nằm trong <b>đệm Docker cục bộ</b> (kéo về 12 tháng trước), nên Testcontainers không cần
 * hỏi registry lần nào. Đây là một biến thể mới của <i>"xanh ở máy không phải bằng chứng"</i>: lần
 * trước là {@code .env.local} (§10.38), lần này là <b>đệm ảnh</b>. Runner checkout sạch <i>và</i>
 * đệm ảnh rỗng, nên nó là chỗ duy nhất trạng thái này dựng lại được.
 *
 * <h2>Bài kiểm này canh gì, và KHÔNG canh gì</h2>
 *
 * <ul>
 *   <li><b>Canh</b>: mọi nơi khai ảnh MinIO khai <b>cùng một chuỗi</b>, và chuỗi ấy nêu <b>tên kho
 *       ảnh tường minh</b>. Dạng trần {@code minio/minio:…} âm thầm nghĩa là Docker Hub — đúng chỗ
 *       ảnh không còn ở đó nữa.
 *   <li>⛔ <b>Không canh</b>: ảnh có kéo về được hay không. Đó là một sự thật của <i>mạng</i>, và
 *       nhét một lượt gọi registry vào bộ kiểm là dựng lại đúng T11.78 — một cú chớp mạng hạ đỏ
 *       cổng bắt buộc. Vế ấy do lượt kéo ảnh thật phán xử: CI khi build, và <b>máy chủ khi triển
 *       khai</b>.
 * </ul>
 */
class AnhMinioDongBoTest {

    /**
     * ⚠ Ghép từ hai mảnh, không viết liền — bài kiểm này quét cả cây mã nguồn, và một hằng số viết
     * liền biến chính tệp này thành một "nguồn khai báo". Bộ canh tự khớp vào mình là bộ canh không
     * canh gì (đã mắc ba lượt trong dự án; xem {@link PostgresCollationParityTest}).
     */
    private static final String MOC = "mi" + "nio:";

    /**
     * Bắt cả {@code image: <ref>} của YAML lẫn {@code DockerImageName.parse("<ref>")} của Java.
     *
     * <p>⛔⛔ Bản đầu viết phần đầu là {@code [A-Za-z0-9._-]*(?:/[A-Za-z0-9._-]+)*} và <b>nuốt mất
     * {@code quay.io/}</b>: khớp trái-nhất thất bại ở vị trí 0 rồi thành công ở vị trí 8, nên nó trả
     * ra dạng TRẦN cho một tham chiếu CÓ kho ảnh. Bộ canh khi ấy đọc hai trạng thái nó sinh ra để
     * phân biệt thành cùng một thứ — CLAUDE.md luật 9. {@link #tuKiemChung()} đỏ ngay lượt chạy đầu
     * và đúng chỗ.
     *
     * <p>⛔⛔ <b>Bản vá ấy cũng chỉ đúng tới 25/9/2026</b>: nó cho phép <b>đúng một</b> đoạn trước
     * {@code minio/minio:}, tiền đề chỉ đứng được chừng nào kho ảnh không có <b>không gian tên</b>.
     * GHCR có ({@code ghcr.io/<tổ-chức>/<gói>}), và neo cũ {@code minio/minio:} còn <b>biến mất
     * hẳn</b> khi bản gương đổi tên gói ⇒ bộ canh mù chứ không phải bóc thiếu. Đo được:
     * {@link #tuKiemChungKhoAnhNhieuDoan()} đỏ với {@code actual: []}.
     *
     * <p>⇒ Neo nay là <b>tên ảnh</b> ({@code minio:}), và phần trước nó là <b>một hoặc nhiều</b>
     * đoạn đường dẫn. Hai thứ giữ cho nó không nới quá tay:
     *
     * <ul>
     *   <li>Bắt buộc <b>ít nhất một</b> đoạn kèm dấu {@code /} ngay trước — nếu không thì
     *       {@code http://minio:9000} (tên dịch vụ Docker, có <b>8</b> chỗ trong hai tệp compose)
     *       thành dương tính giả, và bộ canh sẽ báo "hai ảnh khác nhau trong cùng một tệp".
     *       ⭐ Chuỗi ấy viết thẳng ở đây được vì hai dấu gạch liền nhau <b>không</b> tạo nổi một
     *       đoạn hợp lệ — tức chính tệp này là một ca thử của điều nó vừa khẳng định.
     *   <li>Mỗi đoạn dừng ở {@code /}, nên các đoạn không tranh chỗ của nhau: không có backtracking
     *       cấp số nhân, và cũng không có chỗ để mất một đoạn như bản đầu.
     * </ul>
     *
     * <p>⚠ Neo {@link #MOC} ghép từ hai mảnh vì lý do ở javadoc của chính nó — và với neo mới thì
     * điều đó <b>quan trọng hơn</b>: mọi javadoc trong tệp này nhắc {@code <kho>/minio:<tag>} đều sẽ
     * bị chính bộ canh tính là một nguồn khai báo.
     */
    private static final Pattern THAM_CHIEU_ANH =
            Pattern.compile("((?:[A-Za-z0-9._-]+(?::[0-9]+)?/)+" + Pattern.quote(MOC) + "[A-Za-z0-9._-]+)");

    private static final Set<String> DUOI = Set.of(".yml", ".yaml", ".java", ".sh");

    /** Không có tệp nào trong danh sách này thì bài kiểm đỏ — chặn "quét ra tập rỗng nên xanh". */
    private static final List<String> BAT_BUOC =
            List.of("deploy/compose.infra.yml", "deploy/compose.prod.yml", "SongnhueMinio.java");

    private static final Set<String> BO_QUA_THU_MUC =
            Set.of(".git", "node_modules", "target", ".next", "dist", "build", "venv", ".venv", "coverage");

    @Test
    @DisplayName("⭐⭐ Mọi nơi khai ảnh MinIO đều khai CÙNG MỘT chuỗi")
    void moiNoiKhaiCungMotAnh() {
        var theoTep = quetCaCay();

        Set<String> khacNhau = new TreeSet<>(theoTep.values());
        assertThat(khacNhau)
                .as(
                        """
                        Ảnh MinIO đang có %d bản khác nhau trong cây mã nguồn: %s

                        Cụ thể từng tệp:
                        %s

                        ⛔ Bộ kiểm chạy trên một bản MinIO khác bản sẽ triển khai là kiểm chứng một \
                        hệ thống khác. Câu dặn trong javadoc của `SongnhueMinio` nói đúng điều này \
                        từ WS-4 — và một câu dặn không phải một cổng kiểm.""",
                        khacNhau.size(),
                        khacNhau,
                        theoTep.entrySet().stream()
                                .map(e -> "  · %s → %s".formatted(e.getKey(), e.getValue()))
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse("(không có)"))
                .hasSize(1);
    }

    @Test
    @DisplayName("⛔⛔ Ảnh phải nêu KHO ẢNH tường minh — dạng trần nghĩa là Docker Hub, nơi nó đã biến mất")
    void anhPhaiNeuKhoAnhTuongMinh() {
        // Khẳng định trên MỌI giá trị, không chỉ giá trị đầu: `moiNoiKhaiCungMotAnh` có thể đỏ vì
        // lệch, và khi ấy bài này vẫn phải nói được cái nào sai chứ không im lặng lấy cái đầu.
        assertThat(quetCaCay().values()).allSatisfy(anh -> assertThat(coKhoAnhTuongMinh(anh))
                .as(
                        """
                        `%s` không nêu kho ảnh.

                        Docker hiểu dạng trần là `docker.io/minio/minio` — và đo ngày 14/9/2026 thì \
                        Docker Hub trả `object not found` cho chính repository ấy. Một tham chiếu \
                        không nêu kho ảnh là một tham chiếu ngầm định vào nơi ảnh không còn ở đó.""",
                        anh)
                .isTrue());
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
                    .as("Không thấy khai báo ảnh MinIO ở `%s` — nó phải có, hoặc bài kiểm này đã mù", can)
                    .anySatisfy(tep -> assertThat(tep).endsWith(can));
        }
        assertThat(theoTep).hasSizeGreaterThanOrEqualTo(BAT_BUOC.size());
    }

    /**
     * Bộ canh phải <b>bắt được</b> vi phạm, không chỉ xanh khi không có vi phạm (CLAUDE.md luật 1).
     *
     * <p>Vế phân biệt quan trọng nhất là cặp <i>trần</i> ↔ <i>có kho ảnh</i>: đó đúng là hình dạng
     * của sự cố 14/9, và hai chuỗi ấy chỉ khác nhau ở phần đầu mà mắt người rất dễ đọc lướt qua.
     */
    @Test
    @DisplayName("⭐ Tự kiểm chứng: bộ bóc tách phân biệt được dạng trần với dạng có kho ảnh")
    void tuKiemChung() {
        // ⚠ *"Dạng trần"* ở đây là `<không-gian-tên>/minio:<tag>` — đúng hình dạng đã gây ra sự cố
        //   14/9, nơi Docker ngầm hiểu là `docker.io/…`. ⛔ Phải `minio:<tag>` không có đoạn nào:
        //   chuỗi ấy ⛔ bao giờ xuất hiện trong kho, và lấy nó làm ca thử thì bài này khẳng định về
        //   một thứ ⛔ ai viết. Neo {@link #MOC} đổi ngày 25/9 nên dòng này phải đổi theo — và nó
        //   đỏ ngay lượt chạy đầu sau khi đổi, đúng việc của một bài tự-kiểm-chứng.
        String tran = "mi" + "nio/" + MOC + "RELEASE.2025-09-07T16-13-09Z";
        String coKho = "quay.io/" + tran;

        assertThat(bocTach("    image: " + coKho)).containsExactly(coKho);
        assertThat(bocTach("DockerImageName.parse(\"" + coKho + "\")")).containsExactly(coKho);
        assertThat(bocTach("    image: " + tran))
                .as("Dạng trần vẫn phải BỊ THẤY — thấy rồi mới từ chối được")
                .containsExactly(tran);
        assertThat(bocTach("ở đây không có ảnh nào")).isEmpty();

        assertThat(coKhoAnhTuongMinh(tran)).as("Dạng trần phải bị từ chối").isFalse();
        assertThat(coKhoAnhTuongMinh(coKho))
                .as("Dạng có kho ảnh phải được nhận")
                .isTrue();
        assertThat(coKhoAnhTuongMinh("localhost:5000/" + tran))
                .as("Kho ảnh nội bộ nêu cổng cũng là nêu tường minh")
                .isTrue();
    }

    /**
     * ⛔⛔ <b>T60.2 lặp lại ở một hình dạng MỚI — 25/9/2026.</b>
     *
     * <p>Lượt trước bộ bóc tách nuốt mất {@code quay.io/} vì phần đầu regex khớp trái-nhất thất bại
     * ở vị trí 0 rồi thành công ở vị trí 8. Bản vá khi ấy cho phép <b>đúng một</b> đoạn tên máy chủ
     * — và đó là tiền đề chỉ đúng chừng nào kho ảnh không có <b>không gian tên</b>.
     *
     * <p>GHCR thì có: {@code ghcr.io/<tổ-chức>/<gói>}. Với bản một-đoạn, phép khớp lại thất bại ở
     * vị trí 0 rồi thành công ở {@code team-dev-qnt/…} ⇒ trả ra một chuỗi <b>không</b> nêu kho ảnh
     * cho một tham chiếu <b>có</b> nêu. Đúng hai trạng thái bài kiểm này sinh ra để phân biệt, đọc
     * thành một (CLAUDE.md luật 9) — chỉ khác là lần này nó đỏ ở phía <i>từ chối nhầm</i>, tức nó
     * <b>chặn</b> bản vá thay vì cho lọt. Vế dưới đây là thứ đo được sự khác nhau ấy.
     */
    @Test
    @DisplayName("⛔⛔ Tự kiểm chứng: kho ảnh có KHÔNG GIAN TÊN vẫn phải bóc ra NGUYÊN VẸN")
    void tuKiemChungKhoAnhNhieuDoan() {
        String duoi = "mi" + "nio:RELEASE.2025-09-07T16-13-09Z";
        String ghcr = "ghcr.io/team-dev-qnt/songnhue/" + duoi;

        assertThat(bocTach("    image: " + ghcr))
                .as(
                        """
                        Bóc ra thiếu đoạn đầu thì `coKhoAnhTuongMinh` đọc `team-dev-qnt` là tên kho \
                        ảnh — không có dấu chấm, không có cổng ⇒ nó từ chối một tham chiếu ĐÚNG, và \
                        thông điệp đỏ sẽ nói sai hẳn nguyên nhân.""")
                .containsExactly(ghcr);
        assertThat(bocTach("DockerImageName.parse(\"" + ghcr + "\")")).containsExactly(ghcr);
        assertThat(coKhoAnhTuongMinh(ghcr))
                .as("`ghcr.io` có dấu chấm ⇒ là tên máy chủ ⇒ tường minh")
                .isTrue();

        // ⚠ Vế NGƯỢC, để phần trên không được xanh bằng cách nới ra bắt mọi thứ: dạng trần nhiều
        //   đoạn — `mot-to-chuc/songnhue/minio:…` — vẫn phải bị TỪ CHỐI, vì Docker đọc nó là
        //   docker.io/mot-to-chuc/…, đúng nơi ảnh đã biến mất.
        assertThat(coKhoAnhTuongMinh("mot-to-chuc/songnhue/" + duoi))
                .as("Nhiều đoạn mà đoạn đầu không phải tên máy chủ thì vẫn là dạng trần")
                .isFalse();
    }

    // -------------------------------------------------------------------------

    /**
     * Một tham chiếu nêu kho ảnh khi đoạn TRƯỚC dấu {@code /} đầu tiên trông như một tên máy chủ —
     * có dấu chấm ({@code quay.io}) hoặc có cổng ({@code localhost:5000}). Đây đúng là luật Docker
     * dùng để phân biệt, nên bài kiểm không dựng một luật riêng của mình.
     */
    static boolean coKhoAnhTuongMinh(String anh) {
        int vach = anh.indexOf('/');
        if (vach < 0) {
            return false;
        }
        String dau = anh.substring(0, vach);
        return dau.contains(".") || dau.contains(":");
    }

    /** path (tương đối gốc kho) → tham chiếu ảnh. Lệch nhau trong cùng một tệp thì tệp ấy đỏ ngay. */
    private static TreeMap<String, String> quetCaCay() {
        Path goc = timTuGocKho("deploy").getParent();
        TreeMap<String, String> ket = new TreeMap<>();

        try {
            // ⚠ `walkFileTree` + SKIP_SUBTREE chứ không phải `Files.walk(...).filter(...)`: lọc sau
            //   khi đã duyệt vẫn duyệt qua `node_modules` và `.git`.
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
                                .as("`%s` khai HAI ảnh MinIO khác nhau trong cùng một tệp", goc.relativize(tep))
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
        Matcher khop = THAM_CHIEU_ANH.matcher(noiDung);
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
