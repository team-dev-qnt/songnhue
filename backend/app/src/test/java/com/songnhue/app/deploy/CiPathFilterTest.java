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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Bộ lọc đường dẫn của CI phải bao MỌI tệp mà bộ test backend thật sự ĐỌC.</b>
 *
 * <h2>Vì sao đây là một lỗ câm</h2>
 *
 * GitHub tính <b>{@code skipped} của một required check là ĐẠT</b>. Nên một bộ lọc bỏ sót không hiện
 * ra như lỗi: job biến mất khỏi danh sách, PR merge được, và bộ kiểm chưa từng chạy.
 *
 * <p>Đã trả giá một lượt (§10.37): 7 lớp kiểm của bộ BE đọc tệp trong {@code frontend/} và
 * {@code deploy/}, mà bộ lọc cũ chỉ nhìn {@code backend/} — tức là <b>đúng lúc những tệp ấy thay đổi
 * thì bộ canh chúng không chạy</b>. Và lại suýt trả giá lần hai ngày 26/8: {@code SecretGateTest}
 * chạy {@code .github/scripts/kiem-secret-may-chu.sh}, trong khi bộ lọc chỉ bao
 * {@code .github/workflows/}.
 *
 * <h2>Vì sao bài này không mục theo thời gian</h2>
 *
 * Nó không giữ một danh sách chép tay. Nó <b>quét mã nguồn test</b> tìm mọi hằng chuỗi trỏ ra ngoài
 * {@code backend/}, rồi thử <b>chính biểu thức trong {@code ci.yml}</b> với từng đường dẫn ấy. Thêm
 * một lớp kiểm đọc thư mục mới là bài này đỏ ngay, không cần ai nhớ cập nhật danh sách (CLAUDE.md
 * luật 14).
 */
class CiPathFilterTest {

    /**
     * Thư mục gốc nằm ngoài `backend/` mà một bài kiểm BE có thể đọc.
     *
     * <p>⚠ {@code docs} vào danh sách 3/9/2026 cùng {@code TenMienTaiLieuTest} (T11.53). Không thêm
     * thì bài kiểm ấy đọc {@code docs/**} mà bộ canh này <b>không biết</b>, nên nó sẽ không bao giờ
     * báo rằng bộ lọc CI bỏ sót {@code docs/} — một bộ canh hẹp hơn nơi nó phải chặn (luật 28), và
     * cái xanh của nó đọc như một lời bảo đảm.
     */
    private static final Pattern DUONG_DAN_NGOAI = Pattern.compile("\"((?:\\.github|deploy|frontend|docs)/[^\"]*)\"");

    /** Dòng quyết định vế `backend` trong `ci.yml`. */
    private static final Pattern BO_LOC_BACKEND =
            Pattern.compile("grep -qE '([^']+)'\\s*<<< \"\\$changed\" \\|\\| backend=false");

    /**
     * Dòng quyết định vế {@code frontend} — thêm 8/9/2026 (T41.18).
     *
     * <p>⚠⚠ Cho tới hôm ấy, lớp này <b>chỉ có</b> {@link #BO_LOC_BACKEND}: chiều frontend hoàn toàn
     * không được canh, trong khi javadoc của lớp lại nói về "bộ lọc đường dẫn của CI" như một khối.
     * Đó chính là luật 28 — một bộ canh hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời
     * bảo đảm cho cả hai vế.
     */
    private static final Pattern BO_LOC_FRONTEND =
            Pattern.compile("grep -qE '([^']+)'\\s*<<< \"\\$changed\" \\|\\| frontend=false");

    /** Hằng chuỗi trỏ vào {@code backend/} trong mã nguồn test của frontend — TS dùng nháy đơn. */
    private static final Pattern DUONG_DAN_BACKEND = Pattern.compile("['\"](backend/[^'\"]*)['\"]");

    @Test
    @DisplayName("⭐⭐ Mọi đường dẫn ngoài `backend/` mà test BE đọc đều lọt qua bộ lọc của `ci.yml`")
    void moiDuongDanTestDocDeuLotBoLoc() {
        Pattern boLoc = boLocBackend();
        Set<String> duongDan = duongDanTestBeDoc();

        Set<String> botSot = new TreeSet<>();
        for (String p : duongDan) {
            if (!boLoc.matcher(p).find()) {
                botSot.add(p);
            }
        }

        assertThat(botSot)
                .as(
                        """
                        Bộ lọc `%s` trong `ci.yml` KHÔNG bao những đường dẫn sau, mà bộ test backend \
                        thì đọc chúng:

                        %s

                        Hệ quả: một PR chỉ đụng các tệp ấy làm job `Backend — build, lint, test` bị bỏ \
                        qua — đúng lúc bộ canh chúng cần chạy nhất. Và bỏ qua KHÔNG hiện ra như lỗi: \
                        GitHub tính `skipped` của một required check là ĐẠT.""",
                        boLoc.pattern(), String.join("\n", botSot))
                .isEmpty();
    }

    @Test
    @DisplayName("Phải quét ra ít nhất 10 đường dẫn — chặn xanh-trên-tập-rỗng")
    void quetRaTapKhacRong() {
        // conventions.md §1.5. Đổi cách viết đường dẫn (biến, `Path.of("deploy", "x")`) làm biểu thức
        // trên trả tập rỗng, và bài kia xanh mà không so gì.
        assertThat(duongDanTestBeDoc()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: bộ lọc THIẾU một tiền tố phải bị bắt")
    void tuKiemChung() {
        // CLAUDE.md luật 1. Kiểm trên chính phép so, với đúng bộ lọc CŨ đã để lọt `.github/scripts/`.
        Pattern boLocCu = Pattern.compile("^(backend/|frontend/|deploy/|\\.github/workflows/)");
        Set<String> duongDan = duongDanTestBeDoc();

        assertThat(duongDan)
                .as("Phải còn ít nhất một đường dẫn `.github/scripts/` để phép tự kiểm này có nghĩa")
                .anyMatch(p -> p.startsWith(".github/scripts/"));
        assertThat(duongDan)
                .as("Bộ lọc CŨ phải để lọt đúng những tệp ấy — nếu không, bài trên không chứng minh gì")
                .anyMatch(p -> !boLocCu.matcher(p).find());
    }

    @Test
    @DisplayName("Đọc được biểu thức bộ lọc từ `ci.yml` — không âm thầm dùng mặc định")
    void docDuocBoLoc() {
        assertThat(boLocBackend().pattern()).contains("backend/").startsWith("^(");
        assertThat(boLocFrontend().pattern()).contains("frontend/").startsWith("^(");
    }

    // ---- Vế FRONTEND, thêm 8/9/2026 (T41.18) --------------------------------

    @Test
    @DisplayName("⭐⭐ Mọi đường dẫn `backend/` mà test FE đọc đều lọt qua bộ lọc frontend của `ci.yml`")
    void moiDuongDanBackendTestFeDocDeuLotBoLoc() {
        Pattern boLoc = boLocFrontend();
        Set<String> khongLot = new TreeSet<>();
        for (String duongDan : duongDanBackendTestFeDoc()) {
            if (!boLoc.matcher(duongDan).find()) {
                khongLot.add(duongDan);
            }
        }
        assertThat(khongLot)
                .as(
                        """
                        Bộ test FRONTEND đọc những tệp này, mà bộ lọc `frontend` trong `ci.yml` bỏ qua \
                        chúng. Nghĩa là job Frontend bị SKIP đúng lúc thứ nó canh thay đổi — và GitHub \
                        tính `skipped` của required check là ĐẠT.

                        Ba bài kiểm ấy là bộ canh "hai nơi phải khớp nhau" (quy tắc 14): mã lỗi BE ↔ FE, \
                        khoá màu mức cảnh báo, danh sách thẻ nội dung. Chúng tồn tại ĐÚNG để bắt lượt \
                        sửa một phía — nên bỏ qua chúng lúc một phía đổi là bỏ qua toàn bộ lý do chúng \
                        có mặt.

                        Sửa: thêm tiền tố vào biểu thức `|| frontend=false` trong `ci.yml`.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Phải quét ra ít nhất 3 đường dẫn `backend/` — chặn xanh-trên-tập-rỗng")
    void quetRaTapBackendKhacRong() {
        // conventions.md §1.5. Đổi cách viết đường dẫn ở FE (ghép biến, `join(...)`) làm biểu thức
        // trên trả tập rỗng, và bài kia xanh mà không so gì.
        assertThat(duongDanBackendTestFeDoc()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: bộ lọc frontend CŨ (thiếu `backend/`) phải bị bắt")
    void tuKiemChungVeFrontend() {
        // CLAUDE.md luật 1 — kiểm trên chính phép so, với đúng bộ lọc đã dùng tới 8/9/2026.
        Pattern boLocCu = Pattern.compile("^(frontend/|deploy/|\\.github/workflows/)");
        Set<String> duongDan = duongDanBackendTestFeDoc();

        assertThat(duongDan)
                .as("Phải còn ít nhất một đường dẫn `backend/` để phép tự kiểm này có nghĩa")
                .isNotEmpty();
        assertThat(duongDan)
                .as("Bộ lọc CŨ phải để lọt những tệp ấy — nếu không, bài trên không chứng minh gì")
                .allMatch(p -> !boLocCu.matcher(p).find());
    }

    // -------------------------------------------------------------------------

    private static Pattern boLocBackend() {
        String ci = doc(timTuGocKho(".github/workflows/ci.yml"));
        Matcher khop = BO_LOC_BACKEND.matcher(ci);
        if (!khop.find()) {
            return fail(
                    """
                    Không tìm thấy dòng quyết định vế `backend` trong `ci.yml`.

                    Hoặc dòng ấy đã đổi cách viết — khi đó SỬA biểu thức trong bài kiểm này, đừng xoá \
                    bài. Hoặc bộ lọc đã bị gỡ, và khi ấy job backend chạy ở mọi PR: an toàn, nhưng \
                    phải là một quyết định có người ký, không phải một tác dụng phụ.""");
        }
        return Pattern.compile(khop.group(1));
    }

    private static Pattern boLocFrontend() {
        String ci = doc(timTuGocKho(".github/workflows/ci.yml"));
        Matcher khop = BO_LOC_FRONTEND.matcher(ci);
        if (!khop.find()) {
            return fail(
                    """
                    Không tìm thấy dòng quyết định vế `frontend` trong `ci.yml`.

                    Hoặc dòng ấy đã đổi cách viết — khi đó SỬA biểu thức trong bài kiểm này, đừng xoá \
                    bài. Hoặc bộ lọc đã bị gỡ, và khi ấy job frontend chạy ở mọi PR: an toàn, nhưng \
                    phải là một quyết định có người ký, không phải một tác dụng phụ.""");
        }
        return Pattern.compile(khop.group(1));
    }

    /**
     * Mọi hằng chuỗi trỏ vào {@code backend/} trong mã nguồn test của <b>frontend</b>.
     *
     * <p>⚠ Chỉ quét tệp {@code *.test.ts} / {@code *.test.tsx}: mã sản phẩm của FE ⛔ không được đọc
     * tệp backend, và nếu có thì đó là một khuyết tật khác — không phải việc của bài kiểm này.
     */
    private static Set<String> duongDanBackendTestFeDoc() {
        Path goc = timTuGocKho("frontend");
        Set<String> ket = new TreeSet<>();

        try {
            Files.walkFileTree(goc, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path thuMuc, BasicFileAttributes a) {
                    String ten = thuMuc.getFileName().toString();
                    // `node_modules` chứa hàng vạn tệp và không có gì của ta trong đó; `dist`/`.next`
                    // là bản dựng — quét chúng chỉ làm bài kiểm chậm và nhiễu.
                    return Set.of("node_modules", "dist", ".next", "coverage").contains(ten)
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    String s = tep.toString();
                    if (s.endsWith(".test.ts") || s.endsWith(".test.tsx")) {
                        Matcher khop = DUONG_DAN_BACKEND.matcher(doc(tep));
                        while (khop.find()) {
                            ket.add(khop.group(1));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path tep, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được " + goc, e);
        }
        return ket;
    }

    /** Mọi hằng chuỗi trỏ ra ngoài {@code backend/} trong mã nguồn test của backend. */
    private static Set<String> duongDanTestBeDoc() {
        Path goc = timTuGocKho("deploy").getParent().resolve("backend");
        Set<String> ket = new TreeSet<>();

        try {
            Files.walkFileTree(goc, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path thuMuc, BasicFileAttributes a) {
                    return "target".equals(thuMuc.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    // Chỉ mã nguồn TEST: mã sản phẩm không được đọc tệp ngoài cây build của nó, và
                    // nếu có thì đó là một lỗi khác, không phải việc của bài kiểm này.
                    if (tep.toString().endsWith(".java") && tep.toString().contains("src/test/java")) {
                        Matcher khop = DUONG_DAN_NGOAI.matcher(doc(tep));
                        while (khop.find()) {
                            ket.add(khop.group(1));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path tep, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được " + goc, e);
        }
        return ket;
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
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
