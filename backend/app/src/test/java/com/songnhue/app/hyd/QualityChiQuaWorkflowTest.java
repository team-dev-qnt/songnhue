package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * <b>Đổi {@code quality} chỉ qua Workflow engine</b> — quy tắc 4 + 18, <b>DOD2.13</b>.
 *
 * <h2>⛔⛔ Vì sao bài này cần tồn tại khi đã có ArchUnit và đã có {@code HydroQualityHttpTest}</h2>
 *
 * Hai cơ chế kia phủ hai thứ khác, và <b>không cái nào phủ chỗ này</b>:
 *
 * <ul>
 *   <li>{@code SilentFailureRuleTest.chi_workflow_engine_duoc_goi_applyState} soi <b>lời gọi tới</b>
 *       {@code applyState()}. Một phép gán {@code this.quality = …} viết <b>bên trong chính entity</b>
 *       (một setter mới, một hàm tiện ích) ⛔ <b>không phải một lời gọi</b> — luật ấy mù trước nó.
 *   <li>{@code HydroQualityHttpTest} (21 bài) phủ <b>đường ĐI</b>: duyệt {@code NGHI_NGO → HOP_LE},
 *       xoá kèm lý do, {@code XOA} là trạng thái cuối. Nó ⛔ không nói gì về <b>sự VẮNG MẶT của
 *       đường khác</b> — mà đó mới là nội dung của quy tắc 4.
 * </ul>
 *
 * <p>⇒ Lỗ thật là <b>SQL thuần</b>. Module {@code hydro} dùng {@code JdbcTemplate} ở khắp nơi
 * ({@code AlertEventQueryRepository}, {@code PollerRepository}, {@code HydroReportRepository}…), và
 * một câu {@code UPDATE hydro_readings SET quality = 'HOP_LE' WHERE …} sẽ:
 *
 * <ol>
 *   <li>đổi trạng thái mà ⛔ <b>không đi qua</b> {@code workflow_transitions} — tức lách cả bảng luật
 *       lẫn cột {@code requires_reason};
 *   <li>⛔ <b>không ghi</b> {@code audit_logs}, mà audit đang <b>ký hash chain</b>: một bước chuyển
 *       không có chữ ký là một lỗ trong chuỗi, và chuỗi ấy là thứ duy nhất chứng minh lịch sử số đo
 *       ⛔ không bị sửa;
 *   <li>đi qua <b>mọi</b> bài kiểm hiện có mà ⛔ không làm đỏ một cái nào.
 * </ol>
 *
 * <h2>⭐ Đo 07/09/2026 trước khi viết: bất biến ĐANG đúng</h2>
 *
 * {@code this.quality =} xuất hiện <b>đúng 1 lần</b> trong toàn {@code HydroReading.java} · <b>0</b>
 * câu {@code UPDATE hydro_readings} trong mã main · <b>0</b> {@code @Modifying} chạm {@code quality}.
 * Bài này ⛔ không sửa gì — nó <b>giữ</b> một bất biến đã đúng, và luật 7 nói đúng lý do phải làm
 * thế: một cơ chế chưa ai đi qua thì chưa biết đúng hay sai, kể cả khi hôm nay nó đúng.
 *
 * <h2>⚠ Dùng lại bộ bóc chú thích của {@link QualityFilterGuardTest}</h2>
 *
 * ⛔ Cố ý ⛔ không chép: §10.62 đã trả giá đúng ở chỗ này — một bộ canh mù trước SQL đã chú thích
 * ({@code --} đặt trước câu {@code DELETE}) báo xanh trong khi vi phạm nằm ngay đó. Bộ bóc kia đã có
 * bài tự-kiểm-chứng riêng; một bản chép tay thứ hai là một chỗ nữa để sai <b>theo cách khác</b>.
 */
class QualityChiQuaWorkflowTest {

    private static final String TEP_ENTITY = "HydroReading.java";

    /**
     * Phép <b>GÁN</b> thẳng vào cột trạng thái — thứ duy nhất được phép nằm trong {@code applyState}.
     *
     * <p>⭐ {@code (?!=)} ⛔ không phải chi tiết vụn: bản đầu viết {@code quality\s*=} và nó khớp cả
     * {@code this.quality == null}, tức đếm một phép <b>so sánh</b> thành một phép <b>gán</b>. Hệ quả
     * là bài chính đỏ giả ở lượt đầu tiên ai đó thêm một câu {@code if} — và cách chữa rẻ nhất lúc ấy
     * sẽ là <i>nới bài kiểm</i>, tức tự tay tháo bộ canh. Bài tự-kiểm-chứng bên dưới bắt được đúng
     * lỗi này ở lượt chạy đầu (luật 1).
     */
    private static final Pattern GAN_QUALITY = Pattern.compile("this\\s*\\.\\s*quality\\s*=(?!=)");

    /**
     * {@code UPDATE <bảng>} với {@code <bảng>} là bảng số đo. ⚠ Cố ý ⛔ không đòi chữ
     * {@code quality} đứng cùng: một câu {@code UPDATE hydro_readings SET …} <b>bất kỳ</b> đã là thứ
     * phải đọc bằng mắt, vì cột trạng thái nằm cùng bảng với cột số liệu.
     */
    private static final Pattern UPDATE_BANG_SO_DO =
            Pattern.compile("(?i)\\bupdate\\s+(?:only\\s+)?(hydro_readings)\\b");

    /** Câu JPQL/native gắn `@Modifying` mà chạm tới `quality`. */
    private static final Pattern MODIFYING_CHAM_QUALITY = Pattern.compile("(?is)@Modifying.{0,400}?\\bquality\\b");

    // ═══════════════════════ Bất biến ═══════════════════════

    @Test
    @DisplayName("⭐⭐ `this.quality =` chỉ có ĐÚNG MỘT chỗ, và nó nằm trong `applyState`")
    void motChoDuyNhatVaNamTrongApplyState() {
        String ma = QualityFilterGuardTest.boChuThichJava(docEntity());

        List<Integer> viTri = new ArrayList<>();
        Matcher m = GAN_QUALITY.matcher(ma);
        while (m.find()) {
            viTri.add(m.start());
        }

        assertThat(viTri)
                .as(
                        """
                        ⛔ `HydroReading.quality` phải có ĐÚNG MỘT chỗ ghi, và chỗ ấy là `applyState`.
                        Thêm một chỗ ghi thứ hai — dù là một setter trông vô hại — là mở một đường đổi
                        trạng thái ⛔ KHÔNG qua Workflow engine, ⛔ không ghi audit, và ⛔ không có luật
                        nào chặn: ArchUnit soi LỜI GỌI applyState(), nó mù trước một phép gán nằm bên
                        trong chính lớp này.""")
                .hasSize(1);

        int batDauApplyState = ma.indexOf("void applyState(");
        assertThat(batDauApplyState)
                .as("⛔ Không tìm thấy `applyState` — lớp đã đổi hình dạng?")
                .isGreaterThan(0);
        assertThat(viTri.get(0))
                .as("⛔ Phép gán duy nhất phải nằm SAU chữ ký `applyState`, ⛔ không đứng rời ở nơi khác")
                .isGreaterThan(batDauApplyState);
    }

    @Test
    @DisplayName("⛔⛔ ⛔ KHÔNG câu SQL nào của mã main `UPDATE hydro_readings` — kể cả câu đã chú thích")
    void khongCauSqlNaoUpdateBangSoDo() {
        List<String> pham = new ArrayList<>();
        int soTepDaSoi = 0;

        for (Path tep : tepMain()) {
            String noiDung = docTep(tep);
            String sach = tep.toString().endsWith(".java")
                    ? QualityFilterGuardTest.boChuThichSql(QualityFilterGuardTest.boChuThichJava(noiDung))
                    : QualityFilterGuardTest.boChuThichSql(noiDung);
            soTepDaSoi++;
            Matcher m = UPDATE_BANG_SO_DO.matcher(sach);
            while (m.find()) {
                pham.add(tep.getFileName() + ": " + trichQuanh(sach, m.start()));
            }
        }

        assertThat(soTepDaSoi)
                .as("⚠ Vế chống tập rỗng (luật 7): ⛔ không soi tệp nào thì bài này xanh mà ⛔ không canh gì")
                .isGreaterThanOrEqualTo(300);

        assertThat(pham)
                .as(
                        """
                        ⛔⛔ Một câu `UPDATE hydro_readings` là đường đổi `quality` ⛔ KHÔNG qua Workflow
                        engine. Nó lách `workflow_transitions` (bảng LUẬT, gồm cả cột `requires_reason`),
                        ⛔ không ghi `audit_logs` — mà audit đang KÝ HASH CHAIN, nên một bước chuyển không
                        chữ ký là một lỗ trong thứ duy nhất chứng minh lịch sử số đo chưa bị sửa.
                        ⇒ Đi qua `WorkflowPort.execute(...)` như `HydroQualityService` đang làm.
                        ⚠ Cần một lượt sửa hàng loạt thật (di trú dữ liệu) thì nó thuộc MIGRATION có
                          review, ⛔ không thuộc mã ứng dụng — và phải nói ra ở đây.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ ⛔ KHÔNG `@Modifying` nào chạm `quality` — đường JPA cũng phải đi qua engine")
    void khongModifyingNaoChamQuality() {
        List<String> pham = new ArrayList<>();
        for (Path tep : tepMain()) {
            if (!tep.toString().endsWith(".java")) {
                continue;
            }
            String sach = QualityFilterGuardTest.boChuThichJava(docTep(tep));
            if (MODIFYING_CHAM_QUALITY.matcher(sach).find()) {
                pham.add(tep.getFileName().toString());
            }
        }
        assertThat(pham)
                .as("⛔ `@Modifying` là một câu UPDATE/DELETE hàng loạt — nó bỏ qua cả entity lẫn engine")
                .isEmpty();
    }

    // ═══════════════════════ Tự kiểm chứng ═══════════════════════

    /**
     * ⭐ Luật 1 — mỗi cơ chế canh gác phải có bài chứng minh nó bắt được vi phạm.
     *
     * <p>⚠ Và luật 29: ba bài trên đều kết thúc bằng {@code isEmpty()}, nên một mẫu khớp gõ sai cho
     * chúng xanh <b>trọn vẹn mãi mãi</b>. Khối này là thứ duy nhất phân biệt <i>"⛔ không có vi
     * phạm"</i> với <i>"⛔ không nhìn thấy gì"</i>.
     */
    @Nested
    @DisplayName("Bộ canh tự kiểm chứng")
    class TuKiemChung {

        @Test
        @DisplayName("⭐ Bắt được phép gán thứ hai, và ⛔ không bắt nhầm phép ĐỌC")
        void batDuocPhepGan() {
            assertThat(GAN_QUALITY.matcher("this.quality = newState;").find()).isTrue();
            assertThat(GAN_QUALITY.matcher("  this . quality  =  x;").find())
                    .as("khoảng trắng quanh dấu chấm và dấu bằng ⛔ không được làm mất dấu")
                    .isTrue();
            // ⛔ Không bắt nhầm:
            assertThat(GAN_QUALITY.matcher("return this.quality;").find()).isFalse();
            assertThat(GAN_QUALITY.matcher("if (this.quality == null)").find()).isFalse();
        }

        @Test
        @DisplayName("⭐⭐ Bắt được `UPDATE hydro_readings` ở mọi dạng viết, ⛔ không bắt bảng gần giống")
        void batDuocCauUpdate() {
            assertThat(UPDATE_BANG_SO_DO
                            .matcher("UPDATE hydro_readings SET quality = 'HOP_LE'")
                            .find())
                    .isTrue();
            assertThat(UPDATE_BANG_SO_DO
                            .matcher("update   hydro_readings\n set x = 1")
                            .find())
                    .as("thường/hoa và xuống dòng ⛔ không được làm mất dấu")
                    .isTrue();
            assertThat(UPDATE_BANG_SO_DO
                            .matcher("UPDATE ONLY hydro_readings SET x = 1")
                            .find())
                    .as("`UPDATE ONLY <bảng>` là cú pháp Postgres hợp lệ trên bảng phân mảnh")
                    .isTrue();

            // ⛔ Và ⛔ không bắt nhầm — đây là vế phân biệt, ⛔ không có nó thì một mẫu quá rộng cũng
            //    "bắt được" và bài trên vẫn xanh vì tình cờ kho ⛔ không có câu nào.
            assertThat(UPDATE_BANG_SO_DO
                            .matcher("UPDATE hydro_readings_archive SET x = 1")
                            .find())
                    .as("bảng KHÁC có tên bắt đầu giống ⛔ không được tính")
                    .isFalse();
            assertThat(UPDATE_BANG_SO_DO
                            .matcher("INSERT INTO hydro_readings (quality) VALUES ('HOP_LE')")
                            .find())
                    .as("⛔ INSERT ⛔ không phải đổi trạng thái — bản ghi mới CÓ QUYỀN mang một chất lượng")
                    .isFalse();
            assertThat(UPDATE_BANG_SO_DO
                            .matcher("UPDATE hydro_latest SET valid_value = ?")
                            .find())
                    .as("`hydro_latest` là bảng DẪN XUẤT — cập nhật nó ⛔ không phải đổi trạng thái")
                    .isFalse();
        }

        @Test
        @DisplayName("⭐⭐ Câu bị CHÚ THÍCH thì ⛔ KHÔNG còn là câu — §10.62 ở dạng cụ thể")
        void chuThichBiBocTruocKhiKhop() {
            String sql = "-- UPDATE hydro_readings SET quality = 'HOP_LE';\nSELECT 1;";
            assertThat(UPDATE_BANG_SO_DO
                            .matcher(QualityFilterGuardTest.boChuThichSql(sql))
                            .find())
                    .as("một câu đã chú thích ⛔ không chạy, nên nó ⛔ không phải vi phạm")
                    .isFalse();
            // ⚠ Vế NGƯỢC LẠI mới là vế đắt: bộ bóc ⛔ không được ăn mất câu thật.
            assertThat(UPDATE_BANG_SO_DO
                            .matcher(QualityFilterGuardTest.boChuThichSql(
                                    "-- ghi chú\nUPDATE hydro_readings SET quality = 'XOA';"))
                            .find())
                    .as("⛔ Bộ bóc chú thích nuốt mất câu THẬT thì bài chính xanh giả — đây là vế bắt nó")
                    .isTrue();
        }

        @Test
        @DisplayName("⭐ `@Modifying` — bắt được, và ⛔ không bắt nhầm câu chỉ ĐỌC")
        void batDuocModifying() {
            assertThat(MODIFYING_CHAM_QUALITY
                            .matcher("@Modifying\n@Query(\"update HydroReading r set r.quality = ?1\")")
                            .find())
                    .isTrue();
            assertThat(MODIFYING_CHAM_QUALITY
                            .matcher("@Query(\"select r from HydroReading r where r.quality = 'HOP_LE'\")")
                            .find())
                    .as("⛔ Câu ĐỌC có chữ `quality` ⛔ không phải vi phạm — bộ canh lọc là việc của DOD2.12")
                    .isFalse();
        }
    }

    // ─────────────── Tiện ích ───────────────

    private static String docEntity() {
        Path tep = thuMucBackend().resolve("hydro/src/main/java/com/songnhue/hydro/domain/" + TEP_ENTITY);
        assertThat(Files.exists(tep)).as("⛔ Không tìm thấy %s", tep).isTrue();
        return docTep(tep);
    }

    private static String trichQuanh(String ma, int viTri) {
        int tu = Math.max(0, viTri - 40);
        int den = Math.min(ma.length(), viTri + 90);
        return ma.substring(tu, den).replaceAll("\\s+", " ").trim();
    }

    private static String docTep(Path tep) {
        try {
            return new String(Files.readAllBytes(tep), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> tepMain() {
        try (Stream<Path> cay = Files.walk(thuMucBackend())) {
            return cay.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/")
                                && !s.contains("/target/")
                                && (s.endsWith(".java") || s.endsWith(".sql"));
                    })
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bài chạy với cwd = {@code backend/app}; đi ngược cho tới khi thấy {@code backend/pom.xml}. */
    private static Path thuMucBackend() {
        Path p = Paths.get("").toAbsolutePath();
        Set<Path> daXet = new LinkedHashSet<>();
        while (p != null && daXet.add(p)) {
            Path ungVien =
                    p.getFileName() != null && p.getFileName().toString().equals("backend") ? p : p.resolve("backend");
            if (Files.exists(ungVien.resolve("pom.xml")) && Files.isDirectory(ungVien.resolve("hydro"))) {
                return ungVien;
            }
            p = p.getParent();
        }
        throw new IllegalStateException(
                "⛔ Không tìm thấy thư mục `backend` tính từ " + Paths.get("").toAbsolutePath());
    }
}
