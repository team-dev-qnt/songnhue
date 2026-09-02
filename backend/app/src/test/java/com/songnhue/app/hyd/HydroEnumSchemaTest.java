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
 *   <li>✅ <b>Vế TypeScript phủ ĐỦ BỐN enum từ 02/09 (T31.13 hai cái, WS-32 hai cái)</b>. Cả bốn
 *       đều được đối chiếu ở <b>ba nơi</b>: enum Java · ràng buộc {@code CHECK} · union TS kèm bản
 *       đồ nhãn. ⛔ Không còn enum {@code hydro} nào của {@code V202609011052} đứng ngoài.
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
            // ⭐⭐ `hydro_latest` CỐ Ý hẹp hơn: bảng "hiện tại" ⛔ không bao giờ được trỏ vào một bản
            //    ghi đã bị loại bỏ. `HydroLatestRecomputer` lọc `quality <> 'XOA'` trước khi ghi, nên
            //    ràng buộc hẹp ở đây là LƯỚI AN TOÀN cho lượt ghi ấy. Khoảng chênh khai CÓ TÊN —
            //    một khoảng chênh im lặng là một chỗ quên.
            new DoiChieu(ReadingQuality.class, "ck_hydro_latest_quality", Set.of("XOA")),
            new DoiChieu(ReadingSource.class, "ck_hydro_readings_source"),
            new DoiChieu(ReadingSource.class, "ck_hydro_latest_source"),
            new DoiChieu(SyncStatus.class, "ck_sync_logs_status"),
            new DoiChieu(SyncFailureKind.class, "ck_sync_logs_failure_kind"),
            // ⭐ Khoảng chênh DUY NHẤT của cả bộ, và nó là chủ đích: một dòng `hydro_raw_logs` là
            //   một lượt gọi HTTP ĐÃ XẢY RA. Thiếu mã số thì không có lượt gọi nào, nên cũng không
            //   có dòng raw nào mang lý do ấy. Cho phép giá trị đó ở bảng raw là dựng sẵn một trạng
            //   thái không ai ghi được (luật 15 ở tầng ràng buộc).
            new DoiChieu(SyncFailureKind.class, "ck_hydro_raw_logs_failure_kind", Set.of("THIEU_MA_SO")));

    /**
     * ⭐⭐ TOÀN BỘ migration của {@code hyd}, nối theo <b>thứ tự số hiệu</b> — ⛔ không phải một tệp.
     *
     * <p>Bản đầu (T29) đọc đúng một tệp: {@code V202609011052__hyd_time_series.sql}. Nó đúng đúng
     * <b>một ngày</b> — tới khi {@code V202609021054} thêm {@code XOA} bằng
     * {@code ALTER TABLE … DROP CONSTRAINT … ADD CONSTRAINT …}. Khi ấy bộ canh vẫn đọc bản
     * {@code CHECK} <i>cũ</i> trong tệp cũ, và nó sẽ <b>đỏ vì một lý do sai</b> (báo enum lệch trong
     * khi CSDL thật đã khớp) — hoặc tệ hơn, nếu enum cũng không đổi thì nó <b>xanh trong khi lược đồ
     * đã khác</b>.
     *
     * <p>📌 Đúng hình dạng luật 28: một bộ canh hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như
     * một lời bảo đảm. Nối mọi tệp theo thứ tự rồi lấy <b>định nghĩa CUỐI CÙNG</b> của mỗi ràng buộc
     * là mô hình đúng của thứ CSDL thật sự mang.
     *
     * <p>⚠ Giới hạn còn lại, nói ra thay vì để suy: một ràng buộc bị {@code DROP} mà ⛔ không
     * {@code ADD} lại thì bộ đọc vẫn thấy định nghĩa cũ. Lớp lỗi ấy neo vào
     * {@code db-migration-checksums.txt}, ⛔ không vào bài kiểm.
     */
    private static final Path THU_MUC_MIGRATION = gocKho().resolve("backend/hydro/src/main/resources/db/migration/hyd");

    /** Nối mọi {@code V*.sql} của {@code hyd} theo thứ tự số hiệu. */
    private static String sqlHyd() throws IOException {
        try (java.util.stream.Stream<Path> cay = Files.list(THU_MUC_MIGRATION)) {
            List<Path> tep = cay.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
            if (tep.isEmpty()) {
                throw new IllegalStateException("Không thấy migration nào ở " + THU_MUC_MIGRATION);
            }
            StringBuilder ra = new StringBuilder();
            for (Path t : tep) {
                ra.append(Files.readString(t, StandardCharsets.UTF_8)).append('\n');
            }
            return ra.toString();
        }
    }

    @Test
    @DisplayName("⭐⭐ Enum Java ↔ CHECK của CSDL — cùng một bộ giá trị ở cả hai nơi")
    void enumVaRangBuocCungMotBoGiaTri() throws IOException {
        String sql = sqlHyd();

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
        String sql = sqlHyd();

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
                    .as(
                            "không bóc được giá trị nào của `%s` từ %s — ràng buộc đổi tên?",
                            bo.tenRangBuoc(), THU_MUC_MIGRATION)
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
        String sql = sqlHyd();

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

        // ⭐⭐ Lấy định nghĩa CUỐI CÙNG, ⛔ không phải định nghĩa đầu tiên. Một ràng buộc bị
        //    `DROP CONSTRAINT` rồi `ADD CONSTRAINT` ở migration sau thì bản có hiệu lực là bản sau;
        //    dừng ở lần khớp đầu là đọc lược đồ của quá khứ và gọi nó là hiện tại.
        int batDau = -1;
        while (dau.find()) {
            batDau = dau.end();
        }
        if (batDau < 0) {
            return Set.of();
        }
        int i = batDau;
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
        String than = sql.substring(batDau, i - 1);

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

    // =========================================================================
    // Vế thứ ba — TypeScript (T31.13, 02/09/2026)
    // =========================================================================

    /**
     * Một dòng = một enum phải khớp một union TS <b>và</b> một bản đồ nhãn.
     *
     * @param tenBanDoNhan hằng trong {@code hydroVocabulary.ts} ánh xạ <i>mọi</i> giá trị enum sang
     *     nhãn tiếng Việt. Đây là vế đáng giá hơn cả union: thiếu một khoá ở đây thì
     *     {@code LY_DO_HONG[v].label} là {@code undefined.label} — <b>màn hình trắng</b>, không
     *     phải một nhãn xấu. Và {@code tsc} <b>không</b> thấy: {@code Record<K, V>} chỉ được kiểm khi
     *     khai đủ khoá lúc viết, còn thêm một hằng vào enum Java thì {@code tsc} chẳng biết gì.
     */
    private record BoBaTs(Class<? extends Enum<?>> enumJava, String tenKieuTs, String tenBanDoNhan) {}

    private static final List<BoBaTs> BO_BA_TS = List.of(
            new BoBaTs(SyncStatus.class, "SyncStatus", "KET_CUC_DONG_BO"),
            new BoBaTs(SyncFailureKind.class, "SyncFailureKind", "LY_DO_HONG"),
            // ✅ WS-32 nối nốt hai enum còn lại: màn hình Dữ liệu nghi ngờ đã ra đời nên hai union
            //    ấy tồn tại. Từ đây bảng đối chiếu phủ ĐỦ BỐN enum ở cả ba nơi (Java · CHECK · TS).
            new BoBaTs(ReadingQuality.class, "ReadingQuality", "CHAT_LUONG_SO_DO"),
            new BoBaTs(ReadingSource.class, "ReadingSource", "NGUON_SO_DO"));

    private static final Path API_TYPES = gocKho().resolve("frontend/admin-app/src/shared/api-types.ts");

    private static final Path TU_VUNG_HYDRO =
            gocKho().resolve("frontend/admin-app/src/features/hydro/hydroVocabulary.ts");

    @Test
    @DisplayName("⭐⭐ Enum Java ↔ union TypeScript ↔ bản đồ nhãn — ba nơi cùng một bộ giá trị")
    void enumUnionTsVaBanDoNhanKhopNhau() throws IOException {
        String ts = Files.readString(API_TYPES, StandardCharsets.UTF_8);
        String tuVung = Files.readString(TU_VUNG_HYDRO, StandardCharsets.UTF_8);

        assertThat(BO_BA_TS)
                .as("bảng đối chiếu rỗng thì bài này không khẳng định gì")
                .hasSize(4);

        for (BoBaTs bo : BO_BA_TS) {
            Set<String> java = giaTriJava(bo.enumJava());

            assertThat(giaTriTypeScript(ts, bo.tenKieuTs()))
                    .as(
                            """
                            `%s`: union TypeScript lệch enum Java.
                            Thừa ở TS = giao diện chào một giá trị backend KHÔNG GIẢI ĐƯỢC.
                            Thiếu ở TS = một giá trị hợp lệ mà giao diện không xử lý được.""",
                            bo.tenKieuTs())
                    .isEqualTo(java);

            assertThat(khoaBanDoNhan(tuVung, bo.tenBanDoNhan()))
                    .as(
                            """
                            `%s` trong hydroVocabulary.ts thiếu/thừa khoá so với enum `%s`.
                            ⛔ Thiếu một khoá là `%s[v].label` trả undefined ⇒ MÀN HÌNH TRẮNG, và tsc \
                            không thấy vì nó không biết enum Java có bao nhiêu hằng.""",
                            bo.tenBanDoNhan(), bo.enumJava().getSimpleName(), bo.tenBanDoNhan())
                    .isEqualTo(java);
        }
    }

    @Test
    @DisplayName("⛔⛔ “Bỏ qua vì đã đủ” KHÔNG được tô đỏ — 4/5 lượt chạy rơi vào đúng trạng thái ấy")
    void trangThaiBoQuaKhongDuocToDo() throws IOException {
        String tuVung = Files.readString(TU_VUNG_HYDRO, StandardCharsets.UTF_8);

        String mau = mauCuaKhoa(tuVung, "KET_CUC_DONG_BO", SyncStatus.SKIPPED_UP_TO_DATE.name());

        assertThat(mau)
                .as("§10.42: một chuông kêu vì lý do ai cũng biết là một chuông sẽ bị tắt. Poller gọi "
                        + "2 phút/lần trên nguồn 10 phút/lần nên bỏ qua là kết cục BÌNH THƯỜNG của "
                        + "phần lớn lượt chạy — vẽ nó đỏ là dạy người vận hành bỏ qua màu đỏ")
                .isNotNull()
                .isNotEqualTo("red")
                .isNotEqualTo("volcano");
        assertThat(mauCuaKhoa(tuVung, "KET_CUC_DONG_BO", SyncStatus.FAILED.name()))
                .as("⚠ Vế PHÂN BIỆT: thiếu nó thì khẳng định trên xanh cả khi bộ đọc trả null cho MỌI "
                        + "khoá (luật 7 · luật 9)")
                .isEqualTo("red");
    }

    @Test
    @DisplayName("⛔ Tự kiểm chứng: bộ đọc bản đồ nhãn PHÁT HIỆN được khi thiếu một khoá")
    void boDocBanDoNhanBatDuocViPham() {
        String hong =
                """
                export const LY_DO_HONG: Record<SyncFailureKind, { label: string }> = {
                  THIEU_MA_SO: { label: 'Chưa có mã số' },
                  NOT_WORKING: { label: 'Nguồn từ chối mã số' },
                };
                """;

        Set<String> bocRa = khoaBanDoNhan(hong, "LY_DO_HONG");

        assertThat(bocRa)
                .as("bộ đọc phải bóc đúng hai khoá của chuỗi dựng tay, ⛔ không nuốt các khoá chữ "
                        + "thường bên trong (`label`)")
                .containsExactlyInAnyOrder("THIEU_MA_SO", "NOT_WORKING");
        assertThat(bocRa)
                .as("và phải KHÁC enum — nếu bằng thì bộ đọc đang bịa khoá ra chứ không đọc")
                .isNotEqualTo(giaTriJava(SyncFailureKind.class));
        assertThat(giaTriJava(SyncFailureKind.class)).hasSize(bocRa.size() + 3);
    }

    /** Bóc {@code export type <Ten> = \'A\' | \'B\';} — chấp nhận xuống dòng tuỳ ý giữa các giá trị. */
    private static Set<String> giaTriTypeScript(String noiDung, String tenKieu) {
        Matcher khai = Pattern.compile("export\\s+type\\s+" + Pattern.quote(tenKieu) + "\\s*=([^;]*);", Pattern.DOTALL)
                .matcher(noiDung);
        if (!khai.find()) {
            return Set.of();
        }
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'([A-Z_][A-Z0-9_]*)'").matcher(khai.group(1));
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /**
     * Khoá cấp một của {@code export const <TEN>: Record<…> = { … };}.
     *
     * <p>⚠ Ràng buộc <b>hai dấu cách đầu dòng</b>, ⛔ không bắt mọi cụm CHỮ_HOA rồi hai chấm: các
     * khoá lồng bên trong ({@code label}, {@code color}) là chữ thường nên không lẫn, nhưng một câu
     * tiếng Việt trong chuỗi nhãn hoàn toàn có thể chứa một cụm viết hoa kèm dấu hai chấm. Bắt theo
     * <i>cấu trúc</i> (thụt lề của Prettier) chứ không theo <i>văn bản</i> — luật 2.
     */
    private static Set<String> khoaBanDoNhan(String noiDung, String tenHang) {
        Matcher khoi = Pattern.compile(
                        "export\\s+const\\s+" + Pattern.quote(tenHang) + "\\b[^=]*=\\s*\\{(.*?)\\n\\};", Pattern.DOTALL)
                .matcher(noiDung);
        if (!khoi.find()) {
            return Set.of();
        }
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^ {2}([A-Z][A-Z0-9_]*):").matcher(khoi.group(1));
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /** @return giá trị {@code color: '…'} của một khoá cấp một, hoặc {@code null} khi không thấy */
    private static String mauCuaKhoa(String noiDung, String tenHang, String khoa) {
        Matcher khoi = Pattern.compile(
                        "export\\s+const\\s+" + Pattern.quote(tenHang) + "\\b[^=]*=\\s*\\{(.*?)\\n\\};", Pattern.DOTALL)
                .matcher(noiDung);
        if (!khoi.find()) {
            return null;
        }
        Matcher m = Pattern.compile("(?m)^ {2}" + Pattern.quote(khoa) + ":\\s*\\{(.*?)^ {2}\\},", Pattern.DOTALL)
                .matcher(khoi.group(1));
        if (!m.find()) {
            return null;
        }
        Matcher mau = Pattern.compile("color:\\s*'([^']*)'").matcher(m.group(1));
        return mau.find() ? mau.group(1) : null;
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
