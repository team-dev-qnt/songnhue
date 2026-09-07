package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Migration đã phát hành là BẤT BIẾN — kể cả chú thích.</b>
 *
 * <h2>Chuyện đã xảy ra — 27/8, §10.65</h2>
 *
 * CD Staging đỏ ở bước "Triển khai". Ứng dụng không khởi động được:
 *
 * <pre>
 *   Migration checksum mismatch for migration version 202608251100
 *   -&gt; Applied to database : -1232886408
 *   -&gt; Resolved locally    :  2110920357
 * </pre>
 *
 * Thứ đã đổi trong tệp ấy: <b>14 dòng thêm, 5 dòng xoá, toàn bộ nằm trong khối chú thích {@code --}.
 * Không một dòng SQL nào đổi.</b> Flyway băm toàn bộ nội dung tệp, nên một đợt cải thiện tài liệu
 * cũng đủ làm mọi CSDL đã áp migration ấy từ chối khởi động.
 *
 * <h2>⚠⚠ Vì sao bộ test không bao giờ bắt được</h2>
 *
 * Mọi bài kiểm chạy migration từ CSDL <b>rỗng</b> (Testcontainers dựng mới mỗi lượt), nên
 * <b>không có checksum cũ nào để so</b>. 680 bài xanh trọn vẹn trong khi lượt deploy kế tiếp chết.
 * Đây là lớp lỗi mà bộ test — về nguyên tắc — không thấy: nó chỉ hiện ra ở một CSDL đã sống.
 *
 * <p>Vì thế bảo đảm không nằm ở một bài kiểm hành vi, mà ở một <b>vân tay ghi trong kho</b>:
 * {@code backend/db-migration-checksums.txt}. Sửa một tệp migration thì bài này đỏ, và người sửa
 * phải quyết định một cách có ý thức — chứ không phát hiện ra lúc CD đã đỏ.
 *
 * <h2>Giới hạn của bài này — nói ra thay vì để người đọc tự suy</h2>
 *
 * <ul>
 *   <li>Nó dùng <b>SHA-256</b>, không phải thuật toán checksum của Flyway. Với việc <i>phát hiện
 *       thay đổi</i> thì tương đương; nhưng SHA-256 <b>chặt hơn</b> — Flyway chuẩn hoá ký tự xuống
 *       dòng còn SHA-256 thì không. Một thay đổi chỉ ở CRLF/LF sẽ làm bài này đỏ trong khi Flyway
 *       vẫn chấp nhận. Đỏ nhầm theo hướng an toàn.</li>
 *   <li>Nó <b>không</b> biết migration nào đã thật sự được áp ở đâu. Một migration vừa thêm hôm nay
 *       và chưa chạy ở đâu cả thì sửa vẫn vô hại — nhưng bài này vẫn đỏ. Cố ý: phân biệt "đã áp ở
 *       một CSDL nào đó trên đời" là việc không kiểm được từ trong kho, và đoán sai theo hướng
 *       thoải mái thì đúng bằng không có bộ canh.</li>
 * </ul>
 */
class MigrationImmutabilityTest {

    private static final String MANIFEST = "backend/db-migration-checksums.txt";
    private static final String LENH_SINH = "make migration-manifest";

    @Test
    @DisplayName("⭐⭐ Không tệp migration nào đã ghi vân tay bị sửa nội dung")
    void khongMigrationNaoBiSua() {
        Map<String, String> ghiTrongKho = docManifest();
        Map<String, String> tren0ia = quetNguon();

        // Chặn xanh-trên-tập-rỗng ở CẢ HAI vế: quét hỏng, hoặc manifest rỗng, đều phải ĐỎ.
        assertThat(ghiTrongKho)
                .as("`%s` không có dòng vân tay nào — bộ canh này sẽ xanh mà không soi gì", MANIFEST)
                .hasSizeGreaterThanOrEqualTo(30);
        assertThat(tren0ia)
                .as("Không quét ra tệp migration nào — đường dẫn đã đổi, SỬA bài kiểm chứ đừng xoá")
                .hasSizeGreaterThanOrEqualTo(30);

        List<String> biSua = new ArrayList<>();
        List<String> biXoa = new ArrayList<>();
        List<String> chuaGhi = new ArrayList<>();

        for (Map.Entry<String, String> e : ghiTrongKho.entrySet()) {
            String thucTe = tren0ia.get(e.getKey());
            if (thucTe == null) {
                biXoa.add(e.getKey());
            } else if (!thucTe.equals(e.getValue())) {
                biSua.add("%s%n      vân tay trong kho: %s%n      trên đĩa         : %s"
                        .formatted(e.getKey(), e.getValue(), thucTe));
            }
        }
        for (String duong : tren0ia.keySet()) {
            if (!ghiTrongKho.containsKey(duong)) {
                chuaGhi.add(duong);
            }
        }

        if (!biSua.isEmpty()) {
            fail(
                    """
                    %d tệp migration ĐÃ PHÁT HÀNH bị sửa nội dung:

                      %s

                    ⛔ Flyway băm TOÀN BỘ tệp — sửa một dòng `--` cũng đổi checksum, và mọi CSDL đã \
                    áp migration ấy sẽ từ chối khởi động với `checksum mismatch`. Đã làm đỏ CD \
                    Staging ngày 27/8 bằng đúng một đợt sửa CHÚ THÍCH (§10.65).

                    Cần đổi lược đồ  → viết một migration MỚI.
                    Cần ghi chú điều gì → ghi ở `architecture-review.md`, đừng ghi vào tệp migration.
                    Tệp thật sự CHƯA từng được áp ở đâu → chạy `%s` để nhận vân tay mới, và nói rõ \
                    trong mô tả PR vì sao chắc chắn như vậy."""
                            .formatted(biSua.size(), String.join("\n      ", biSua), LENH_SINH));
        }
        if (!biXoa.isEmpty()) {
            fail(
                    """
                    %d tệp migration đã ghi vân tay nhưng KHÔNG còn trên đĩa: %s

                    Xoá một migration đã phát hành còn nặng hơn sửa nó: CSDL đã áp sẽ báo \
                    `applied migration not resolved locally`."""
                            .formatted(biXoa.size(), biXoa));
        }
        if (!chuaGhi.isEmpty()) {
            fail(
                    """
                    %d tệp migration MỚI chưa có vân tay: %s

                    Chạy `%s` rồi commit `%s` kèm theo. Không có bước này thì lần sửa kế tiếp vào \
                    chính những tệp ấy sẽ đi lọt."""
                            .formatted(chuaGhi.size(), chuaGhi, LENH_SINH, MANIFEST));
        }
    }

    @Test
    @DisplayName("⭐ Vân tay chỉ lấy từ NGUỒN — không lẫn `target/` hay migration của bài kiểm")
    void manifestKhongLanBanSaoBuild() {
        for (String duong : docManifest().keySet()) {
            assertThat(duong)
                    .as("`%s` là bản sao lúc build — `mvn clean` sẽ làm bộ canh đỏ mà không ai sửa gì", duong)
                    .doesNotContain("/target/");
            assertThat(duong)
                    .as("`%s` chỉ áp lên CSDL dùng-một-lần của Testcontainers — bất biến không có nghĩa ở đó", duong)
                    .doesNotContain("/src/test/");
        }
    }

    @Test
    @DisplayName("⛔ Bộ sinh vân tay phải tồn tại và chạy được — con trỏ không được trỏ vào chỗ trống")
    void boSinhVanTayCoThat() {
        Path script = timTuGocKho("backend/tools/sinh-vantay-migration.sh");
        assertThat(Files.isExecutable(script))
                .as("`%s` phải có cờ thực thi", script)
                .isTrue();

        assertThat(doc(timTuGocKho("Makefile")))
                .as("`Makefile` chưa có đích `migration-manifest` — thông báo lỗi của bài trên sẽ chỉ vào hư không")
                .contains("migration-manifest");
    }

    // -------------------------------------------------------------------------

    private static Map<String, String> docManifest() {
        Map<String, String> ket = new LinkedHashMap<>();
        for (String dong : doc(timTuGocKho(MANIFEST)).split("\n")) {
            String d = dong.strip();
            if (d.isEmpty() || d.startsWith("#")) {
                continue;
            }
            String[] phan = d.split("\\s+", 2);
            if (phan.length == 2) {
                ket.put(phan[1].strip(), phan[0].strip());
            }
        }
        return ket;
    }

    private static Map<String, String> quetNguon() {
        Path goc = timTuGocKho("backend");
        Path gocKho = goc.getParent();
        Map<String, String> ket = new TreeMap<>();
        try {
            Files.walkFileTree(goc, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path thuMuc, BasicFileAttributes a) {
                    String ten = thuMuc.getFileName().toString();
                    return "target".equals(ten) || "node_modules".equals(ten)
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    String duong = gocKho.relativize(tep).toString().replace('\\', '/');
                    if (duong.contains("/src/main/resources/db/")
                            && tep.getFileName().toString().matches("V.*\\.sql")) {
                        ket.put(duong, sha256(tep));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ket;
    }

    private static String sha256(Path tep) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bam = md.digest(Files.readAllBytes(tep));
            StringBuilder sb = new StringBuilder(bam.length * 2);
            for (byte b : bam) {
                sb.append("%02x".formatted(b));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không có SHA-256", e);
        }
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
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
