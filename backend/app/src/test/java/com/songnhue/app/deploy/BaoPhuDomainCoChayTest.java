package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ⛔⛔⛔ <b>Một cổng bao phủ KHÔNG CHẠY ⛔ đọc như một cổng bao phủ ĐỎ</b> — T68.31.
 *
 * <h2>Khuyết tật</h2>
 *
 * <p>Đo 19–20/09/2026: {@code backend/hr/src/test} có <b>0</b> tệp, nên JaCoCo in
 * {@code 'Skipping JaCoCo execution due to missing execution data file'} và build **xanh trọn vẹn**.
 * Cổng bao phủ của cả một module biến mất mà ⛔ một dòng đỏ nào — luật 7 (<i>cơ chế chưa ai đi qua
 * ⛔ biết đúng sai</i>) gặp luật 31 (<i>thứ nguy hiểm là SỰ VẮNG MẶT, ⛔ phải màu đỏ</i>).
 *
 * <p>Khuyết tật thứ hai nằm cạnh: sàn {@code jacoco.domain.line.coverage} của pom cha là
 * <b>0.18</b> — con số đo được hồi Phase 0 — trong khi đo 20/09 cho {@code core} 0.3490 ·
 * {@code content} 0.3873 · {@code hydro} 0.5490 · {@code operations} 0.8028. ⇒ Bánh cóc <b>thôi
 * cóc</b>: một module tụt từ 0.55 về 0.19 vẫn "đạt" và ⛔ ai biết.
 *
 * <h2>Bộ canh này ĐO vế trái, ⛔ đọc một danh sách gõ tay (luật 28)</h2>
 *
 * <p>Cùng khuôn với {@code CotPhase2CoDocGhiTest} · {@code VongKhuHoiDuPhamViTest}: liệt kê mọi
 * module backend <b>có tầng domain</b> từ đĩa, rồi bắt từng cái phải thoả hai điều kiện. Module
 * mới mà quên là một lượt CI đỏ gọi đích danh, thay vì lặng lẽ nằm ngoài tầm quét như {@code hr} đã
 * nằm.
 *
 * <p>⚠ Nó ⛔ khẳng định về <i>con số</i> bao phủ — đó là việc của chính cổng JaCoCo. Nó khẳng định
 * <b>cổng ấy có chạy ⛔</b> và <b>có ngưỡng của riêng mình ⛔</b>.
 */
class BaoPhuDomainCoChayTest {

    /** Tên thuộc tính mà {@code backend/pom.xml} truyền vào {@code jacoco:check}. */
    private static final String KHOA_NGUONG = "<jacoco.domain.line.coverage>";

    /** Số module có tầng domain, đo 20/09/2026: core · content · hydro · operations · hr. */
    private static final int TOI_THIEU_MODULE_CO_DOMAIN = 5;

    @Test
    @DisplayName("⛔⛔⛔ Module có tầng domain PHẢI có bộ kiểm — ⛔ thì JaCoCo bỏ qua trong im lặng")
    void moduleCoDomainPhaiCoBoKiem() {
        List<Path> modules = moduleCoTangDomain();

        assertThat(modules)
                .as(
                        "⚠ vế chống tập rỗng (luật 29): phép đo tìm ra %d module có tầng domain. Ít hơn %d "
                                + "nghĩa là phép quét hỏng — và một bộ canh quét ra TẬP RỖNG thì xanh trọn vẹn "
                                + "trong đúng tình huống nó sinh ra để bắt (luật 7).",
                        modules.size(), TOI_THIEU_MODULE_CO_DOMAIN)
                .hasSizeGreaterThanOrEqualTo(TOI_THIEU_MODULE_CO_DOMAIN);

        for (Path module : modules) {
            assertThat(coBoKiem(module))
                    .as(
                            """
                        ⛔⛔⛔ Module `%s` có lớp ở tầng `domain` nhưng `src/test/java` ⛔ có tệp `*Test.java` nào.
                        Hệ quả ⛔ phải "thiếu vài bài kiểm" mà là: JaCoCo in `Skipping JaCoCo execution due to
                        missing execution data file`, **bỏ qua cổng bao phủ của cả module**, và build XANH.
                        Đó đúng là trạng thái `hr` đã nằm từ 10/09 tới 20/09/2026.
                        ⇒ Viết bài kiểm cho phần mang LUẬT NGHIỆP VỤ (⛔ phải getter — xem chú thích ở
                        `backend/pom.xml`), rồi khai ngưỡng đo được vào `%s/pom.xml`.""",
                            module.getFileName(), module.getFileName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("⛔⛔ Mỗi module phải KHAI ngưỡng của riêng mình — thừa kế sàn cha là bánh cóc thôi cóc")
    void moiModulePhaiKhaiNguongRieng() {
        List<Path> modules = moduleCoTangDomain();
        assertThat(modules).hasSizeGreaterThanOrEqualTo(TOI_THIEU_MODULE_CO_DOMAIN);

        for (Path module : modules) {
            assertThat(docTep(module.resolve("pom.xml")))
                    .as(
                            """
                        ⛔⛔ `%s/pom.xml` ⛔ khai `%s…`, nên nó chạy trên sàn **0.18** của pom cha — con số đo
                        được hồi Phase 0. Đo 20/09/2026: core 0.3490 · content 0.3873 · hydro 0.5490 ·
                        operations 0.8028 · hr 0.2526. ⇒ Một module tụt về 0.19 vẫn "đạt" và ⛔ dòng nào báo.
                        Ngưỡng là một SỐ ĐO có ngày, ⛔ phải một mục tiêu: khai đúng con số đang có, và siết
                        lại mỗi lượt dọn — ⛔ thì nó thôi là bánh cóc.""",
                            module.getFileName(), KHOA_NGUONG)
                    .contains(KHOA_NGUONG);
        }
    }

    // ---- Bài TỰ KIỂM: hai vị từ trên có phân biệt được hai trạng thái ⛔ (luật 1 · luật 9) -----

    @Test
    @DisplayName("⚠ TỰ KIỂM — `coBoKiem` phân biệt được ba hình dạng thư mục")
    void tuKiemViTuBoKiem(@TempDir Path tam) throws IOException {
        Path khongCoThuMuc = Files.createDirectory(tam.resolve("khong-co-src-test"));
        assertThat(coBoKiem(khongCoThuMuc))
                .as("⛔ có `src/test` ⇒ đúng trạng thái `hr` đã nằm")
                .isFalse();

        Path rongRuot = tam.resolve("co-thu-muc-rong");
        Files.createDirectories(rongRuot.resolve("src/test/java/com/x"));
        assertThat(coBoKiem(rongRuot))
                .as("⛔⛔ có thư mục mà ⛔ tệp `*Test.java` nào thì JaCoCo VẪN bỏ qua — một bản vá chỉ "
                        + "`mkdir` cho hết đỏ phải bị bắt")
                .isFalse();

        Path coThat = tam.resolve("co-bai-kiem");
        Files.createDirectories(coThat.resolve("src/test/java/com/x"));
        Files.writeString(coThat.resolve("src/test/java/com/x/AlphaTest.java"), "class AlphaTest {}");
        assertThat(coBoKiem(coThat)).isTrue();

        Path chiCoTepThuong = tam.resolve("co-tep-khong-phai-test");
        Files.createDirectories(chiCoTepThuong.resolve("src/test/java/com/x"));
        Files.writeString(chiCoTepThuong.resolve("src/test/java/com/x/Fixture.java"), "class Fixture {}");
        assertThat(coBoKiem(chiCoTepThuong))
                .as("⚠ chỉ có fixture, ⛔ lớp `*Test` nào ⇒ surefire ⛔ chạy gì ⇒ JaCoCo vẫn bỏ qua")
                .isFalse();
    }

    @Test
    @DisplayName("⚠ TỰ KIỂM — phép đo `moduleCoTangDomain` chỉ nhận module CÓ THẬT tầng domain")
    void tuKiemPhepDoPhamVi() {
        List<String> ten = moduleCoTangDomain().stream()
                .map(p -> p.getFileName().toString())
                .toList();

        assertThat(ten)
                .as("năm module mang luật nghiệp vụ phải nằm trong tập đo được")
                .contains("core", "content", "hydro", "operations", "hr");
        assertThat(ten)
                .as("⛔⛔ `app` là module lắp ráp — nó ⛔ có gói `domain` nào, và kể nó vào là ép một "
                        + "ngưỡng bao phủ lên tầng controller/cấu hình, đúng thứ `backend/pom.xml` "
                        + "nói là làm loãng con số")
                .doesNotContain("app");
    }

    // ---- Phép đo ---------------------------------------------------------------

    /** Mọi thư mục module dưới {@code backend/} có ít nhất một tệp {@code .java} trong gói `domain`. */
    private static List<Path> moduleCoTangDomain() {
        Path backend = gocKho().resolve("backend");
        try (Stream<Path> duyet = Files.list(backend)) {
            return duyet.filter(Files::isDirectory)
                    .filter(BaoPhuDomainCoChayTest::coLopDomain)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("⛔ duyệt được " + backend, e);
        }
    }

    private static boolean coLopDomain(Path module) {
        Path src = module.resolve("src/main/java");
        if (!Files.isDirectory(src)) {
            return false;
        }
        try (Stream<Path> duyet = Files.walk(src)) {
            return duyet.anyMatch(p -> p.toString().endsWith(".java")
                    && p.getParent() != null
                    && p.getParent().toString().replace('\\', '/').contains("/domain"));
        } catch (IOException e) {
            throw new IllegalStateException("⛔ duyệt được " + src, e);
        }
    }

    /**
     * Có ít nhất một lớp {@code *Test.java} trong {@code src/test/java}.
     *
     * <p>⚠ ⛔ hỏi <i>thư mục có tồn tại ⛔</i>: một thư mục rỗng (hoặc chỉ có fixture) vẫn làm JaCoCo
     * in {@code Skipping} y hệt, nên hai trạng thái ấy phải đọc GIỐNG NHAU ở đây (luật 9).
     */
    private static boolean coBoKiem(Path module) {
        Path test = module.resolve("src/test/java");
        if (!Files.isDirectory(test)) {
            return false;
        }
        try (Stream<Path> duyet = Files.walk(test)) {
            return duyet.anyMatch(p -> p.getFileName().toString().endsWith("Test.java"));
        } catch (IOException e) {
            throw new IllegalStateException("⛔ duyệt được " + test, e);
        }
    }

    private static String docTep(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("⛔ đọc được " + p, e);
        }
    }

    private static Path gocKho() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("⛔ tìm thấy gốc kho (thư mục chứa `.claude`)");
        }
        return p;
    }
}
