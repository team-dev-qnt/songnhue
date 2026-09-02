package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;

/**
 * Bốn danh sách giá trị của MOD-03 sống ở <b>hai nơi</b> — enum Java và ràng buộc {@code CHECK} của
 * CSDL — và không cơ chế nào tự bắt chúng lệch nhau.
 *
 * <h2>Vì sao bài này phải có ngay từ lượt tạo bảng</h2>
 *
 * <p>Lệch enum ↔ CHECK không bị trình biên dịch thấy, không bị {@code tsc} thấy, và <b>không bị bộ
 * test đơn vị thấy</b> — nó nổ thành một lỗi ràng buộc <i>ở giữa một lượt ingest</i>. Với MOD-03 thì
 * đó không phải một dòng đỏ trong log: nguồn <b>không có API lịch sử</b>, nên một lượt ingest đổ vỡ
 * là mất vĩnh viễn khung dữ liệu ấy. Chi phí của một lệch chính tả ở đây cao hơn hẳn ở các module
 * khác.
 *
 * <p>{@code EnumBaNoiTest} đã làm đúng việc này cho năm enum hồ sơ công trình sau khi hai giá trị ma
 * lọt vào giao diện suốt nhiều tuần ({@code TUOI_TIEU_KET_HOP}, {@code KHAC}). Bài này là bản cho
 * {@code hydro}, và cố ý viết <b>trước</b> khi có dữ liệu chứ không sau khi có sự cố.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28) — đọc trước khi tin cái xanh của bài này</h2>
 *
 * <ul>
 *   <li>Soi <b>đúng bốn</b> enum liệt kê ở {@link #DOI_CHIEU} và <b>đúng một</b> tệp migration.
 *       Enum của các module khác, và enum {@code hydro} của {@code V202608311049}
 *       ({@code PositionRole}, {@code AdapterType}, {@code ApiSourceStatus}) <b>không</b> nằm trong
 *       phạm vi — chúng đã có {@code HydroCatalogueSeedTest} soi ở tầng dữ liệu.
 *   <li>Đọc <b>văn bản migration</b>, ⛔ không đọc lược đồ CSDL đang chạy. Nghĩa là nó bắt được
 *       "người viết migration mới gõ sai", ⛔ không bắt được "một CSDL đã sống mang ràng buộc cũ".
 *       Lớp lỗi thứ hai neo vào {@code db-migration-checksums.txt}, không vào bài kiểm.
 *   <li>Chưa có union TypeScript nào để đối chiếu — màn hình quản trị của bốn enum này ra đời ở
 *       WS-31. ⬜ Khi có, thêm vế thứ ba theo đúng khuôn {@code EnumBaNoiTest}.
 * </ul>
 */
class HydroEnumSchemaTest {

    /**
     * Một dòng = một enum phải khớp một ràng buộc.
     *
     * @param loaiTru giá trị enum <b>cố ý không</b> có trong ràng buộc ấy — phải nêu tên, không được
     *     để "còn lại". Một khoảng chênh có tên là một quyết định; một khoảng chênh im lặng là một
     *     chỗ quên.
     */
    private record DoiChieu(Class<? extends Enum<?>> enumJava, String tenRangBuoc, Set<String> loaiTru) {
        DoiChieu(Class<? extends Enum<?>> enumJava, String tenRangBuoc) {
            this(enumJava, tenRangBuoc, Set.of());
        }
    }

    private static final List<DoiChieu> DOI_CHIEU = List.of(
            new DoiChieu(ReadingQuality.class, "ck_hydro_readings_quality"),
            new DoiChieu(ReadingQuality.class, "ck_hydro_latest_quality"),
            new DoiChieu(ReadingSource.class, "ck_hydro_readings_source"),
            new DoiChieu(ReadingSource.class, "ck_hydro_latest_source"),
            new DoiChieu(SyncStatus.class, "ck_sync_logs_status"),
            new DoiChieu(SyncFailureKind.class, "ck_sync_logs_failure_kind"),
            // ⭐ Khoảng chênh DUY NHẤT của cả bộ, và nó là chủ đích: một dòng `hydro_raw_logs` là
            //   một lượt gọi HTTP ĐÃ XẢY RA. Thiếu mã số thì không có lượt gọi nào, nên cũng không
            //   có dòng raw nào mang lý do ấy. Cho phép giá trị đó ở bảng raw là dựng sẵn một trạng
            //   thái không ai ghi được (luật 15 ở tầng ràng buộc).
            new DoiChieu(SyncFailureKind.class, "ck_hydro_raw_logs_failure_kind", Set.of("THIEU_MA_SO")));

    private static final Path MIGRATION =
            gocKho().resolve("backend/hydro/src/main/resources/db/migration/hyd/V202609011052__hyd_time_series.sql");

    @Test
    @DisplayName("⭐⭐ Enum Java ↔ CHECK của CSDL — cùng một bộ giá trị ở cả hai nơi")
    void enumVaRangBuocCungMotBoGiaTri() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        for (DoiChieu bo : DOI_CHIEU) {
            Set<String> mongDoi = giaTriJava(bo.enumJava());
            mongDoi.removeAll(bo.loaiTru());
            Set<String> csdl = giaTriCsdl(sql, bo.tenRangBuoc());

            assertThat(csdl)
                    .as(
                            """
                            Ràng buộc `%s` lệch enum `%s`.
                              Java (nguồn sự thật, đã trừ loại-trừ %s): %s
                              CSDL                                    : %s
                            Lệch ở đây KHÔNG bị trình biên dịch thấy — nó nổ thành lỗi ràng buộc giữa một \
                            lượt ingest, và nguồn không có API lịch sử nên khung dữ liệu ấy mất vĩnh viễn.""",
                            bo.tenRangBuoc(), bo.enumJava().getSimpleName(), bo.loaiTru(), mongDoi, csdl)
                    .isEqualTo(mongDoi);
        }
    }

    @Test
    @DisplayName("⛔ Vế chống xanh-trên-tập-rỗng: bộ đọc thật sự bóc được giá trị ở cả hai nguồn")
    void boDocKhongChayQuaTapRong() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        // Luật 7: một khẳng định chạy qua tập rỗng vẫn xanh trọn vẹn. Nếu ai đó đổi tên ràng buộc
        // hay đổi cách trình bày, bộ đọc trả về rỗng — bài trên sẽ đỏ vì lệch, nhưng bài này nói
        // thẳng nguyên nhân thay vì bắt người đọc tự suy.
        assertThat(DOI_CHIEU)
                .as("bảng đối chiếu rỗng thì bài trên không khẳng định gì")
                .hasSize(7);

        for (DoiChieu bo : DOI_CHIEU) {
            assertThat(giaTriJava(bo.enumJava()))
                    .as("enum %s không có hằng nào", bo.enumJava().getSimpleName())
                    .isNotEmpty();
            assertThat(giaTriCsdl(sql, bo.tenRangBuoc()))
                    .as("không bóc được giá trị nào của `%s` từ %s — ràng buộc đổi tên?", bo.tenRangBuoc(), MIGRATION)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("⛔ Tự kiểm chứng: bộ đọc PHÁT HIỆN được khi ràng buộc thiếu một giá trị")
    void boDocBatDuocViPham() {
        // Luật 1: mỗi cơ chế canh gác phải có bài kiểm chứng minh nó BẮT ĐƯỢC vi phạm — dự án đã có
        // năm cơ chế "xanh mà không chạy". Và luật 29: một bài kiểm chứng ngược có thể sai theo đúng
        // cách mà thứ nó kiểm đang sai, nên ở đây kiểm chứng bằng một chuỗi SQL DỰNG TAY, độc lập
        // hoàn toàn với tệp migration thật.
        String sqlHong =
                """
                CONSTRAINT ck_sync_logs_status CHECK (
                    status IN ('SUCCESS', 'PARTIAL', 'FAILED')
                ),
                """;

        Set<String> bocRa = giaTriCsdl(sqlHong, "ck_sync_logs_status");

        assertThat(bocRa)
                .as("bộ đọc phải bóc đúng ba giá trị của chuỗi dựng tay")
                .containsExactlyInAnyOrder("SUCCESS", "PARTIAL", "FAILED");
        assertThat(bocRa)
                .as("và phải KHÁC enum — nếu bằng thì bộ đọc đang bịa giá trị ra chứ không đọc")
                .isNotEqualTo(giaTriJava(SyncStatus.class));
        assertThat(giaTriJava(SyncStatus.class))
                .as("khoảng chênh phải đúng là giá trị bị gỡ")
                .containsAll(bocRa)
                .contains("SKIPPED_UP_TO_DATE");
    }

    @Test
    @DisplayName("⛔ THIEU_MA_SO có ở sync_logs và KHÔNG có ở hydro_raw_logs — cả hai vế đều phải đúng")
    void khoangChenhMotGiaTriLaChuDich() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(giaTriJava(SyncFailureKind.class))
                .as("enum là nguồn sự thật — gỡ giá trị này là gỡ trạng thái 'chưa cấu hình mã số'")
                .contains("THIEU_MA_SO");
        assertThat(giaTriCsdl(sql, "ck_sync_logs_failure_kind"))
                .as("một lượt polling CÓ THỂ hỏng trước khi mở kết nối")
                .contains("THIEU_MA_SO");
        assertThat(giaTriCsdl(sql, "ck_hydro_raw_logs_failure_kind"))
                .as("một dòng raw là một lượt gọi ĐÃ XẢY RA — không có mã số thì không có lượt gọi nào")
                .doesNotContain("THIEU_MA_SO");

        // Khẳng định về SỐ LƯỢNG, không chia sẻ giả định nào với hai khẳng định trên (luật 29 —
        // đúng thứ đã cứu lượt rà 28/8).
        assertThat(EnumSet.allOf(SyncFailureKind.class)).hasSize(5);
        assertThat(giaTriCsdl(sql, "ck_hydro_raw_logs_failure_kind")).hasSize(4);
    }

    // -------------------------------------------------------------------------

    private static Set<String> giaTriJava(Class<? extends Enum<?>> loai) {
        return Arrays.stream(loai.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Bóc danh sách trong {@code CONSTRAINT <tên> CHECK ( … IN ('A', 'B') )}.
     *
     * <p>⚠ Cố ý <b>đếm ngoặc</b> thay vì khớp bằng một mẫu regex trải dài: ràng buộc trong tệp này
     * viết cả một dòng lẫn nhiều dòng, và một mẫu "tới ngoặc đóng đầu tiên có xuống dòng" sẽ lặng lẽ
     * nuốt sang ràng buộc kế tiếp rồi trả về danh sách của <i>ràng buộc khác</i> — xanh, và sai. Đó
     * đúng hình dạng §10.62: bộ canh khớp trúng chuỗi ở một quy tắc khác.
     */
    private static Set<String> giaTriCsdl(String sql, String tenRangBuoc) {
        Matcher dau = Pattern.compile("CONSTRAINT\\s+" + Pattern.quote(tenRangBuoc) + "\\s+CHECK\\s*\\(")
                .matcher(sql);
        if (!dau.find()) {
            return Set.of();
        }
        int i = dau.end();
        int sau = 1;
        while (i < sql.length() && sau > 0) {
            char c = sql.charAt(i);
            if (c == '(') {
                sau++;
            } else if (c == ')') {
                sau--;
            }
            i++;
        }
        if (sau != 0) {
            return Set.of();
        }
        String than = sql.substring(dau.end(), i - 1);

        Matcher danhSach =
                Pattern.compile("IN\\s*\\(([^)]*)\\)", Pattern.DOTALL).matcher(than);
        if (!danhSach.find()) {
            return Set.of();
        }
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(danhSach.group(1));
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /** Đi ngược lên tới thư mục chứa {@code .claude} — chạy được cả từ module lẫn từ gốc repo. */
    private static Path gocKho() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Không tìm thấy gốc repo (thư mục chứa .claude)");
        }
        return p;
    }
}
