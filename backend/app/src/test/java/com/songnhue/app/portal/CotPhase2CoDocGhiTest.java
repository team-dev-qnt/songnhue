package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>DOD2.20 — mọi cột nghiệp vụ của Phase 2 phải có ít nhất một nơi mã đọc hoặc ghi nó.</b>
 *
 * <h2>Luật 27 — đếm "đã dựng xong bao nhiêu tính năng" là đếm sai đơn vị</h2>
 *
 * <p>Thứ người dùng nhận được là một vòng khép kín <i>nhập → lưu → hiện</i>, và <b>một nửa vòng
 * chạy hoàn hảo vẫn cho ra số không</b>. Lượt rà 28/08/2026 tìm ra <b>sáu</b> cột/khoá thiếu đúng
 * một nửa cặp, <b>bốn</b> trong đó ra đời <i>một ngày trước</i> từ một đợt cẩn thận, có bài kiểm,
 * có nghiệm thu. Triệu chứng luôn giống nhau và luôn im lặng: <i>màn hình báo lưu thành công, cổng
 * ⛔ không đổi gì.</i>
 *
 * <h2>⚠⚠ PHẠM VI CỦA BỘ CANH NÀY — nói ra thay vì để cái xanh tự nói (luật 28)</h2>
 *
 * <p><b>Phủ</b>: mọi cột khai trong migration của module {@code hydro} — {@code CREATE TABLE} và
 * {@code ADD COLUMN} — trừ tám cột hạ tầng chung ({@code id}, {@code public_id}, {@code created_at}
 * …) do lớp cơ sở lo.
 *
 * <p><b>⛔ KHÔNG phủ</b>, và mỗi mục là một khoảng trống có thật:
 *
 * <ul>
 *   <li><b>Khoá {@code settings}</b> — đã có {@code PortalSettingsReadTest} (nhóm CMS, người đọc ở
 *       {@code public-web}) và {@code HydroSettingsReadTest} (nhóm HYDRO). Bộ canh này ⛔ không
 *       chồng lên chúng.
 *   <li><b>Cột của {@code content}/{@code operations}/{@code core}</b> — chúng là Phase 0–1, ⛔
 *       không thuộc DOD2.20. Mở rộng ra được, và nên mở khi có ai cần.
 *   <li><b>Câu hỏi 4 và 6 của §7.3</b> (<i>"có màn hình nào GỌI endpoint đó ⛔ không"</i> ·
 *       <i>"có nơi HIỂN THỊ ⛔ không"</i>) — bộ canh này trả lời được câu 1–3 và 5 bằng máy; hai
 *       câu kia đòi hiểu ngữ cảnh giao diện và vẫn phải rà tay.
 * </ul>
 *
 * <p>⇒ Cái xanh của lớp này nói: <i>"⛔ không cột hydro nào chỉ tồn tại trong migration"</i>. Nó ⛔
 * <b>không</b> nói <i>"mọi vòng nhập→lưu→hiện của Phase 2 đều khép kín"</i>.
 *
 * <h2>⛔ Vì sao phép tìm dùng {@code \\b…\\b} trên CẢ hai dạng tên</h2>
 *
 * <p>Cột khai bằng {@code snake_case} ở SQL và đọc bằng {@code camelCase} ở Java/TypeScript. Tìm
 * một dạng thôi là bỏ sót một nửa số nơi đọc. Và ranh giới từ là bắt buộc: ⛔ không có nó thì
 * {@code note} khớp trúng {@code footnote}, {@code code} khớp trúng {@code error_code}, và bộ canh
 * xanh vì lý do sai.
 *
 * <p>⚠ Bản nháp của lượt đo này chạy bằng {@code git grep -E '\\b…\\b'} và cho ra <b>93/93 cột mồ
 * côi</b> — ERE của git ⛔ <b>không</b> hỗ trợ {@code \\b}, nên mọi mẫu đều khớp 0 tệp. Một kết quả
 * <i>"mọi thứ đều hỏng"</i> gần như luôn là một <b>phép đo hỏng</b>, và thứ lộ ra điều đó là
 * {@link #phepTimThucSuTimDuoc()} — một đối chứng <b>phải-tìm-thấy</b> (luật 10).
 */
class CotPhase2CoDocGhiTest {

    /**
     * Module <b>được phủ</b>, và bên dưới là những module <b>cố ý chưa phủ</b> kèm lý do.
     *
     * <p>⛔⛔ Hai tập này ⛔ phải một lời dặn — {@link #phamViPhaiDuocXepLoai()} đối chiếu chúng
     * với <b>những gì có THẬT trên đĩa</b>, nên một module mới ra đời mà ⛔ ai xếp loại sẽ làm bộ
     * canh đỏ thay vì lặng lẽ ở ngoài tầm quét. Đó đúng là khoảng trống T51.10(b) ghi lại: javadoc
     * cũ tự khai <i>"mở rộng ra được, và nên mở khi có ai cần"</i>, rồi HRM ra đời với ~24 trường hồ
     * sơ mà ⛔ ai nhớ câu ấy. <b>Phạm vi phải do bộ canh ĐO, ⛔ do người viết gõ tay</b> (luật 28,
     * cùng cách T49.1 đã vá {@code MigrationNamingTest}).
     */
    private static final Set<String> MODULE_DUOC_PHU = Set.of("hydro", "hr", "core");

    /**
     * Module chưa phủ — mỗi dòng một lý do <b>đo được</b>, tối thiểu 40 ký tự (như
     * {@link #KHONG_CAN_MA_DOC}).
     *
     * <p>⭐⭐ <b>`core` rời danh sách này ngày 23/09/2026 (T68.32), và lý do rời đi đáng ghi.</b> Dòng
     * miễn trừ của nó khai một <b>DỰ ĐOÁN</b>: <i>"phép tìm theo TÊN CỘT sẽ cho một tập mồ côi khổng
     * lồ mà gần như toàn dương tính giả"</i>. Dự đoán ấy <b>chưa ai đo</b>, và lượt đo đầu tiên bác
     * nó: bật `core` lên ra đúng <b>5</b> cột — {@code archived_at} · {@code archived_by} ·
     * {@code is_recurring} · {@code last_seq} · {@code lock_until} — đọc hết trong một phút, và 3
     * trong 5 có lời giải cấu trúc (SQL sở hữu · thư viện sở hữu · {@code DEFAULT now()}).
     *
     * <p>⇒ <b>Một lý do miễn trừ cũng là dữ liệu chưa kiểm.</b> Nó giữ `core` ngoài tầm quét 4 ngày
     * bằng một con số ⛔ ai đếm — cùng hình dạng với lý do khai nợ của {@code hydro.polling.cron}
     * (T63.10), và với chín lượt <i>"một dòng nợ tự nó sai"</i>.
     */
    private static final Map<String, String> MODULE_CHUA_PHU = new LinkedHashMap<>(Map.of(
            "content",
            "Phase 1 (CMS). Nhiều cột ở đây được đọc từ `public-web` qua tên trường JSON đã ánh xạ "
                    + "chứ ⛔ phải tên cột, nên phép tìm hai dạng snake/camel hiện tại còn hụt. Đã có "
                    + "`PortalSettingsReadTest` phủ nhóm khoá cấu hình cổng. Mở phạm vi ở đây cần bổ "
                    + "sung lượt đọc qua tên trường DTO trước, ⛔ thì tập mồ côi toàn dương tính giả.",
            "operations",
            "Phase 1 (MOD-02). Hồ sơ công trình nay đã có bộ canh MẠNH HƠN ở tầng biểu mẫu — "
                    + "`hoSoCongTrinhVongKhuHoi.test.tsx` (T61.12) đọc thẳng DTO backend và bắt được "
                    + "cả trường bị đánh rơi giữa đường, thứ phép tìm theo tên cột ⛔ thấy. Mở phạm vi "
                    + "ở đây trùng lặp phần lớn với bộ canh ấy."));

    /** Mọi module có thư mục migration — ĐO trên đĩa, ⛔ gõ tay. */
    private static Set<String> moduleCoMigration() {
        Path goc = gocKho().resolve("backend");
        try (Stream<Path> cay = Files.list(goc)) {
            return cay.filter(Files::isDirectory)
                    .filter(m -> Files.isDirectory(m.resolve("src/main/resources/db/migration")))
                    .map(m -> m.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Thư mục migration của những module ĐANG được phủ. */
    private static List<String> thuMucMigration() {
        return MODULE_DUOC_PHU.stream()
                .sorted()
                .map(m -> "backend/" + m + "/src/main/resources/db/migration")
                .toList();
    }

    /** Nơi một cột có thể được đọc hoặc ghi. ⛔ Cố ý ⛔ không gồm thư mục test. */
    private static final List<String> NOI_DOC_GHI = List.of(
            "backend/hydro/src/main/java",
            "backend/hr/src/main/java",
            "backend/core/src/main/java",
            "backend/app/src/main/java",
            "frontend/admin-app/src",
            "frontend/public-web/src");

    /** Cột hạ tầng chung — lớp cơ sở JPA và {@code @Audited} lo, ⛔ không phải cột nghiệp vụ. */
    private static final Set<String> COT_HA_TANG =
            Set.of("id", "public_id", "created_at", "updated_at", "deleted_at", "created_by", "updated_by", "version");

    /**
     * Cột <b>cố ý</b> ⛔ không có nơi mã đọc — mỗi dòng kèm lý do <b>đo được</b>, tối thiểu 40 ký tự.
     *
     * <p>⚠ Ràng buộc độ dài ⛔ không phải hình thức: nó chặn kiểu miễn trừ <i>"⛔ chưa dùng"</i> —
     * một câu đúng với <b>mọi</b> khoảng trống và vì thế ⛔ không phân biệt <i>có chủ đích</i> với
     * <i>bị bỏ quên</i>. Cùng ràng buộc đã dùng ở {@code MaLoiCoNoiNemTest}, nơi nó bắt được đúng
     * một dòng miễn trừ 33 ký tự của chính người viết.
     */
    private static final Map<String, String> KHONG_CAN_MA_DOC = new LinkedHashMap<>(Map.of(
            "geom",
            "Cột `GENERATED ALWAYS AS … STORED` — CSDL tự dựng từ latitude/longitude, nên về thiết kế "
                    + "nó ⛔ KHÔNG có đường ghi từ mã. Đường đọc của nó là chỉ mục GiST `ix_stations_geom`, "
                    + "dựng sẵn cho truy vấn không gian của GIS Phase 3; hôm nay ⛔ chưa truy vấn nào dùng "
                    + "tới. ⛔ Đừng xoá để 'đóng nợ': cột sinh và chỉ mục là thứ đắt để thêm lại trên một "
                    + "bảng đã có dữ liệu, và `latitude`/`longitude` — nguồn của nó — đang được đọc thật.",
            "prev_hash",
            "Cột của `audit_logs`, do trigger `core_audit_log_chain()` cấp — và một đường ghi từ Java ở đây là SAI "
                    + "THEO THIẾT KẾ: chú thích migration (dòng 11) khai thẳng *app user ⛔ thể tự đặt "
                    + "seq/prev_hash/hash, có ghi lên cũng bị ghi đè*. Cột này vô hình với bộ canh cho tới 23/09 vì "
                    + "kiểu `CHAR(64)` ⛔ nằm trong danh sách kiểu GÕ TAY của `KIEU_COT` — xem javadoc mẫu ấy.",
            "last_seq",
            "Cột của `audit_chain_head`, và nó ĐƯỢC đọc lẫn ghi — bằng SQL, ⛔ bằng Java: hàm PL/pgSQL "
                    + "`core_audit_log_chain()` trong CHÍNH migration khai nó làm cả hai việc "
                    + "(`SET last_seq = last_seq + 1` và `RETURNING last_seq`). Bộ canh chỉ quét Java + "
                    + "TypeScript nên ⛔ thấy đường ấy. Đây là dương tính giả THẬT, ⛔ phải nợ — và nó "
                    + "phải nằm trong mã Java thì mới sai: chuỗi hash cố ý ⛔ đi qua tầng ứng dụng.",
            "lock_until",
            "Cột của bảng `shedlock`, do thư viện `shedlock-provider-jdbc-template` sở hữu và ghi. "
                    + "Migration khai thẳng: 'Schema theo chuẩn …, ⛔ tự đặt tên cột'. Kho có 0 dòng Java "
                    + "chạm tới nó và ⛔ NÊN có — viết tay vào bảng khoá phân tán là phá đúng thứ nó bảo "
                    + "vệ. Bảng đang TẮT ở v1 (1 node), bật bằng env khi lên ≥ 2 node.",
            "archived_at",
            "Cột của `audit_archive_anchors`, khai `DEFAULT now()` ⇒ CSDL ghi, mã ⛔ cần chạm. Vế ĐỌC "
                    + "thì thiếu thật, nhưng nó là nợ cấp BẢNG chứ ⛔ cấp cột: T85.6 đo CẢ bảng neo có "
                    + "0 người đọc (kể cả `last_hash`, thứ javadoc của `AuditArchiveHandler` khai là lý "
                    + "do bảng tồn tại). Vá một cột ở đây là che mất hình dạng thật của nợ.",
            "is_recurring",
            "Cột của `holidays`. Seed đặt TRUE cho ĐÚNG 4 ngày lễ dương lịch cố định × 2 năm — nó đang "
                    + "mã hoá một tri thức PHÁP LÝ (Điều 112 BLLĐ: ngày nào cố định theo dương lịch, "
                    + "ngày nào do Chính phủ công bố hằng năm), nên xoá cột là xoá tri thức ấy. Nhưng "
                    + "`Holiday.java` ⛔ ánh xạ nó ⇒ mọi ngày lễ admin tự thêm lặng lẽ nhận FALSE. "
                    + "⛔ Bày nó ra biểu mẫu mà ⛔ có hành vi lặp lại là hứa một thứ ⛔ ai thực hiện "
                    + "(luật 15 · 16). Việc *có nên tự sinh ngày lễ năm sau ⛔* là quyết định của "
                    + "QuanTran, ⛔ phải của mã ⇒ T85.9."));

    /** Cột ai cũng biết là có người đọc — đối chứng chứng minh phép tìm còn sống (luật 10). */
    private static final List<String> PHAI_TIM_THAY = List.of("position_role", "api_code", "valid_value");

    /**
     * Một dòng khai cột bên trong {@code CREATE TABLE} — <b>đảo phép khớp</b> ngày 23/09/2026 (T68.32).
     *
     * <p>⛔⛔ Bản cũ liệt kê <b>gõ tay</b> 16 kiểu SQL được nhận. Đo lại thì migration của ba module đang phủ dùng
     * <b>18</b> kiểu, và hai kiểu ⛔ có trong danh sách là {@code CHAR} (7 cột) và {@code INET} (4 cột) ⇒ bộ canh
     * mù trước <b>11 cột</b> kể từ ngày nó ra đời. Lộ ra vì cái neo phạm vi {@code core} đòi {@code last_hash}, một
     * cột {@code CHAR(64)}.
     *
     * <p>⇒ Đây đúng là luật 28 lặp lại <b>một tầng sâu hơn</b>: T63.7 đã sửa danh sách <i>module</i> thành ĐO trên
     * đĩa, nhưng danh sách <i>kiểu</i> vẫn là một danh sách người viết gõ — và một danh sách gõ tay ở vế trái thì
     * bao giờ cũng có phần tử thứ 17. Nay phép khớp hỏi câu ngược lại: <b>dòng nào ⛔ phải ràng buộc bảng thì là
     * cột</b>, nên một kiểu SQL mới ⛔ bao giờ lọt ra ngoài tầm quét nữa.
     *
     * <p>⚠ Ràng buộc bảng trong kho viết HOA ({@code CONSTRAINT} · {@code PRIMARY KEY} · {@code UNIQUE} …) nên vế
     * {@code [a-z_]} đã loại phần lớn; {@link #TU_KHOA_RANG_BUOC} chặn nốt trường hợp viết thường.
     */
    private static final Pattern KIEU_COT = Pattern.compile("^ {4}([a-z_][a-z0-9_]*)\\s+[A-Za-z]", Pattern.MULTILINE);

    /** Từ khoá mở đầu một RÀNG BUỘC bảng, ⛔ phải tên cột. */
    private static final Set<String> TU_KHOA_RANG_BUOC =
            Set.of("constraint", "primary", "unique", "check", "foreign", "exclude", "like", "partition");

    private static final Pattern BANG_TAO =
            Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?(\\w+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL);

    private static final Pattern COT_THEM =
            Pattern.compile("ALTER TABLE (\\w+)[^;]*?ADD COLUMN (?:IF NOT EXISTS )?([a-z_]+)", Pattern.DOTALL);

    @Test
    @DisplayName("⛔⛔ DOD2.20 — ⛔ KHÔNG cột hydro nào chỉ tồn tại trong migration")
    void moiCotDeuCoNoiDocHoacGhi() {
        Map<String, Set<String>> cot = cotNghiepVu();
        String nguon = toanBoMaNguon();

        List<String> moCoi = cot.keySet().stream()
                .filter(c -> !KHONG_CAN_MA_DOC.containsKey(c))
                .filter(c -> !xuatHien(nguon, c))
                .sorted()
                .toList();

        assertThat(moCoi)
                .as(
                        """
                        Những cột này khai trong migration `hydro` mà ⛔ KHÔNG dòng mã nào đọc hay ghi: %s

                        Đó là một nửa cặp đọc–ghi (luật 27). Triệu chứng luôn im lặng: dữ liệu vào được \
                        CSDL — hoặc ⛔ không bao giờ vào — và ⛔ không màn hình nào, ⛔ không báo cáo nào \
                        đổi khác.

                        Cột CỐ Ý chưa có mã đọc thì khai ở `KHONG_CAN_MA_DOC` kèm lý do ĐO ĐƯỢC \
                        (≥ 40 ký tự). "Chưa dùng" ⛔ KHÔNG phải một lý do — nó đúng với mọi khoảng trống.""",
                        moCoi)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ Module MỚI có migration mà ⛔ ai xếp loại phải bị BẮT, ⛔ lặng lẽ ngoài tầm quét")
    void phamViPhaiDuocXepLoai() {
        // ⛔⛔ Đây là vế vá T51.10(b). Bản cũ ghim cứng đúng MỘT thư mục `hydro` và javadoc tự khai
        //    *"mở rộng ra được, và nên mở khi có ai cần"* — rồi module `hr` ra đời với ~24 trường hồ
        //    sơ CBNV, và ⛔ ai nhớ câu ấy suốt bốn ngày. Một lời dặn ⛔ phải một cổng kiểm.
        //
        // ⇒ Phạm vi do bộ canh **ĐO** (liệt kê thư mục thật trên đĩa), và mỗi module phải rơi vào
        //   MỘT trong hai tập: được phủ, hoặc chưa phủ KÈM LÝ DO. Thêm module thứ sáu mà quên xếp
        //   loại ⇒ bài này đỏ và gọi đích danh nó.
        Set<String> coThat = moduleCoMigration();
        Set<String> daXepLoai = new LinkedHashSet<>(MODULE_DUOC_PHU);
        daXepLoai.addAll(MODULE_CHUA_PHU.keySet());

        List<String> chuaXepLoai =
                coThat.stream().filter(m -> !daXepLoai.contains(m)).sorted().toList();
        assertThat(chuaXepLoai)
                .as(
                        """
                        Module `%s` có thư mục migration mà ⛔ nằm trong `MODULE_DUOC_PHU` lẫn                         `MODULE_CHUA_PHU`.

                        Nghĩa là mọi cột của nó đang ở NGOÀI tầm quét của bộ canh này, và cái xanh                         của bộ canh đọc như một lời bảo đảm cho một phạm vi nó ⛔ soi (luật 28).

                        Hãy CHỌN: thêm vào `MODULE_DUOC_PHU` (rồi vá những cột mồ côi nó lôi ra),                         hoặc thêm vào `MODULE_CHUA_PHU` kèm lý do ĐO ĐƯỢC ≥ 40 ký tự.                         "Chưa cần" ⛔ phải một lý do.""",
                        chuaXepLoai)
                .isEmpty();

        // Chiều ngược: một module đã biến mất khỏi đĩa mà vẫn nằm trong danh sách ⇒ danh sách đang
        // mô tả một kho ⛔ còn tồn tại, và người đọc sau sẽ tin nó.
        List<String> khongConTonTai =
                daXepLoai.stream().filter(m -> !coThat.contains(m)).sorted().toList();
        assertThat(khongConTonTai)
                .as("Module khai trong danh sách mà ⛔ còn thư mục migration nào: %s", khongConTonTai)
                .isEmpty();

        // Lý do phải ĐO ĐƯỢC — cùng ràng buộc độ dài đã bắt được một dòng miễn trừ 33 ký tự của
        // chính người viết ở `MaLoiCoNoiNemTest`.
        List<String> lyDoHoiHot = MODULE_CHUA_PHU.entrySet().stream()
                .filter(e -> e.getValue().length() < 40)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        assertThat(lyDoHoiHot)
                .as(
                        "Module miễn phủ mà lý do ngắn hơn 40 ký tự: %s — *\"chưa cần\"* đúng với MỌI "
                                + "khoảng trống nên nó ⛔ phân biệt được *có chủ đích* với *bị bỏ quên*",
                        lyDoHoiHot)
                .isEmpty();

        // Chống tập rỗng: nếu phép ĐO thư mục hỏng thì `coThat` rỗng và cả ba khẳng định trên xanh.
        assertThat(coThat)
                .as("⚠ phép đo thư mục migration hỏng ⇒ mọi khẳng định trên xanh trên tập rỗng")
                .hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("⚠ Đối chứng PHẢI-TÌM-THẤY: phép tìm còn sống, ⛔ không xanh vì khớp 0 tệp")
    void phepTimThucSuTimDuoc() {
        // ⛔⛔ Vế cứu cả lớp này. Bản nháp chạy `git grep -E` với `\\b` — ERE của git ⛔ không hỗ trợ
        //    nó — nên MỌI mẫu khớp 0 tệp và phép đo cho ra "93/93 cột mồ côi". Một bộ dò đã chết
        //    trông y hệt một hệ thống hỏng toàn phần (T11.80, `strings` của macOS).
        String nguon = toanBoMaNguon();
        assertThat(nguon.length())
                .as("⚠ ⛔ không đọc được mã nguồn thì mọi khẳng định ở lớp này vô nghĩa")
                .isGreaterThan(100_000);

        for (String c : PHAI_TIM_THAY) {
            assertThat(xuatHien(nguon, c))
                    .as(
                            "cột `%s` chắc chắn có người đọc — ⛔ không tìm ra nghĩa là PHÉP TÌM hỏng, "
                                    + "⛔ không phải mã hỏng",
                            c)
                    .isTrue();
        }

        // Và một cột BỊA phải ⛔ KHÔNG tìm thấy — nếu tìm thấy thì mẫu đang khớp bừa.
        assertThat(xuatHien(nguon, "cot_hoan_toan_khong_ton_tai_xyz"))
                .as("mẫu khớp cả một tên bịa ⇒ ranh giới từ hỏng, và mọi cột đều 'có người đọc'")
                .isFalse();
    }

    @Test
    @DisplayName("⚠ Chống tập rỗng: bộ canh phải thật sự đọc được migration (luật 7 + 29)")
    void docDuocDuCot() {
        Map<String, Set<String>> cot = cotNghiepVu();

        // ⚠ Khẳng định về SỐ LƯỢNG — vế duy nhất ⛔ không chia sẻ giả định nào với mẫu regex. Nếu
        //   migration đổi cách xuống dòng, mẫu bắt hụt và mọi so sánh ở trên xanh trên tập rỗng.
        //   SÀN chứ ⛔ không con số chính xác: thêm cột là chuyện thường, thêm cột mà bộ canh THÔI
        //   NHÌN THẤY thì ⛔ không.
        assertThat(cot).hasSizeGreaterThanOrEqualTo(80);
        assertThat(cot.keySet())
                .as("phải bắt được cả cột của CREATE TABLE lẫn cột của ADD COLUMN")
                .contains("position_role", "quality_reason");

        // ⛔⛔ Neo cho phạm vi MỚI MỞ (T63.7). `hr` vào danh sách phủ mà lượt chạy ra **0 cột mồ
        //    côi** — một kết quả đúng, nhưng nó đọc y hệt *"bộ canh ⛔ hề quét hr"*. Hai trạng thái
        //    ấy phải phân biệt được, ⛔ thì việc mở phạm vi hôm nay có thể bị một lượt tái cấu trúc
        //    ngày mai vô hiệu hoá trong im lặng (luật 9 · T49.3).
        assertThat(cot.keySet())
                .as("phạm vi phải THẬT SỰ gồm module `hr` — ⛔ thì cái xanh ở trên là xanh vì lý do sai")
                .contains("bank_account", "contract_expires_at", "national_id_fingerprint");

        // ⛔⛔ Neo THỨ HAI, cho phạm vi mở ngày 23/09 (T68.32). `core` vào danh sách phủ mà lượt chạy
        //    ra 0 cột mồ côi — lại đúng hình dạng đã phải neo cho `hr`: một kết quả đúng đọc y hệt
        //    *"bộ canh ⛔ hề quét core"*. Ba cột dưới đây nằm ở ba tệp migration khác nhau của `core`.
        assertThat(cot.keySet())
                .as("phạm vi phải THẬT SỰ gồm module `core` — ⛔ thì cái xanh ở trên là xanh vì lý do sai")
                .contains("max_attempts", "holiday_date", "last_hash");
    }

    /**
     * ⛔⛔ Luật 1 — <b>phép bỏ chú thích phải có bằng chứng nó bỏ được</b>.
     *
     * <p>{@link #boChuThich} là thứ mọi khẳng định của lớp này đứng lên. Một biểu thức chính quy
     * hỏng ở đây ⛔ không làm bài nào đỏ — nó chỉ lặng lẽ đưa bộ canh về hành vi cũ (chú thích tính
     * là mã đọc), và cái xanh khi ấy đọc như một lời bảo đảm. Bài này là chỗ duy nhất phân biệt
     * được hai trạng thái.
     *
     * <p>⚠ Vế thứ ba (<i>{@code //} cuối dòng KHÔNG bị bỏ</i>) ⛔ không phải mô tả một thiếu sót cần
     * sửa — nó <b>ghim</b> ranh giới cố ý ở javadoc của {@link #boChuThich}. Ai đó siết phép bỏ cho
     * "kín hơn" sẽ làm bài này đỏ và phải đọc lý do trước khi đánh đổi lấy rủi ro đỏ giả.
     */
    @Test
    @DisplayName("⛔ Bằng chứng cho phép BỎ CHÚ THÍCH — javadoc ⛔ không phải một đường đọc")
    void boChuThichThucSuBoDuoc() {
        String ma =
                """
                /** Javadoc nhắc tên cot_chi_trong_javadoc nhưng ⛔ không đọc nó. */
                // cot_chi_trong_dong_chu_thich cũng vậy
                public String doc() { return "cot_that_su_doc"; }
                String u = "https://vi.du/khong-duoc-cat";
                """;
        String sach = boChuThich(ma);

        assertThat(sach)
                .as("⛔ khối javadoc phải biến mất — đây là chỗ `geom` đã đi lọt")
                .doesNotContain("cot_chi_trong_javadoc");
        assertThat(sach).as("⛔ dòng bắt đầu bằng `//` phải biến mất").doesNotContain("cot_chi_trong_dong_chu_thich");
        assertThat(sach)
                .as("⭐ ĐỐI CHỨNG: mã thật PHẢI còn lại — bỏ quá tay là sinh ra ĐỎ GIẢ, hỏng theo "
                        + "chiều tệ hơn hẳn (luật 10)")
                .contains("cot_that_su_doc");
        assertThat(sach)
                .as("⚠ GHIM ranh giới cố ý: `//` GIỮA dòng ⛔ không bị cắt, nếu không thì mọi chuỗi "
                        + "`https://…` trong mã thật mất phần đuôi — xem javadoc `boChuThich`")
                .contains("khong-duoc-cat");
    }

    @Test
    @DisplayName("⚠⚠ Danh sách miễn trừ ⛔ không được chứa cột ĐANG có mã đọc (chiều ngược)")
    void mienTruKhongPhinhTo() {
        String nguon = toanBoMaNguon();
        Map<String, Set<String>> cot = cotNghiepVu();

        List<String> nhamLan = KHONG_CAN_MA_DOC.keySet().stream()
                .filter(c -> xuatHien(nguon, c))
                .sorted()
                .toList();

        assertThat(nhamLan)
                .as("⛔ Những cột này ĐÃ có mã đọc mà vẫn nằm trong danh sách miễn trừ. Một dòng miễn "
                        + "trừ là một cột ĐƯỢC MIỄN KIỂM — giữ lại một dòng đã hết đúng là tự tay mở "
                        + "một lỗ hổng, và lỗ ấy lớn dần mỗi lần ai đó thêm một dòng cho hết đỏ.")
                .isEmpty();

        List<String> khongConTonTai = KHONG_CAN_MA_DOC.keySet().stream()
                .filter(c -> !cot.containsKey(c))
                .sorted()
                .toList();
        assertThat(khongConTonTai)
                .as("⛔ Cột đã bị xoá khỏi lược đồ mà dòng miễn trừ còn nằm lại — sự miễn trừ sống "
                        + "lâu hơn cái nó miễn trừ")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Mỗi dòng miễn trừ phải có lý do ĐO ĐƯỢC — ⛔ không phải 'chưa dùng'")
    void moiMienTruCoLyDoThuc() {
        KHONG_CAN_MA_DOC.forEach((cot, lyDo) -> assertThat(lyDo.length())
                .as("lý do miễn trừ cột `%s` quá ngắn để nói được điều gì: \"%s\"", cot, lyDo)
                .isGreaterThanOrEqualTo(40));
    }

    // -------------------------------------------------------------------------

    /** Có tên cột ở dạng {@code snake_case} <b>hoặc</b> {@code camelCase} trong mã ⛔ không. */
    private static boolean xuatHien(String nguon, String cot) {
        Pattern mau = Pattern.compile("\\b(" + Pattern.quote(cot) + "|" + Pattern.quote(camel(cot)) + ")\\b");
        return mau.matcher(nguon).find();
    }

    private static String camel(String snake) {
        String[] phan = snake.split("_");
        StringBuilder sb = new StringBuilder(phan[0]);
        for (int i = 1; i < phan.length; i++) {
            sb.append(Character.toUpperCase(phan[i].charAt(0))).append(phan[i].substring(1));
        }
        return sb.toString();
    }

    /** Tên cột nghiệp vụ → tập bảng khai nó. */
    private static Map<String, Set<String>> cotNghiepVu() {
        Map<String, Set<String>> ket = new LinkedHashMap<>();
        for (Path p : thuMucMigration().stream()
                .flatMap(tm -> tepTrong(tm, ".sql").stream())
                .toList()) {
            // ⛔ Bỏ chú thích TRƯỚC khi khớp (luật 2 + §10.62): một cột được nhắc trong chú thích
            //   "cột này đã bị gỡ" ⛔ không phải một cột đang sống, và mẫu ⛔ không tự biết điều đó.
            String sql = doc(p).replaceAll("--[^\n]*", "");

            Matcher bang = BANG_TAO.matcher(sql);
            while (bang.find()) {
                Matcher c = KIEU_COT.matcher(bang.group(2));
                while (c.find()) {
                    if (!TU_KHOA_RANG_BUOC.contains(c.group(1).toLowerCase(java.util.Locale.ROOT))) {
                        ghi(ket, c.group(1), bang.group(1));
                    }
                }
            }
            Matcher them = COT_THEM.matcher(sql);
            while (them.find()) {
                ghi(ket, them.group(2), them.group(1));
            }
        }
        return ket;
    }

    private static void ghi(Map<String, Set<String>> ket, String cot, String bang) {
        if (!COT_HA_TANG.contains(cot)) {
            ket.computeIfAbsent(cot, k -> new LinkedHashSet<>()).add(bang);
        }
    }

    private static String toanBoMaNguon() {
        StringBuilder sb = new StringBuilder();
        for (String thuMuc : NOI_DOC_GHI) {
            for (String duoi : List.of(".java", ".ts", ".tsx")) {
                for (Path p : tepTrong(thuMuc, duoi)) {
                    sb.append(boChuThich(doc(p))).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static List<Path> tepTrong(String thuMuc, String duoi) {
        Path goc = timTuGocKho(thuMuc);
        if (!Files.isDirectory(goc)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(goc)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(duoi))
                    .filter(p -> !p.toString().contains("node_modules"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * ⚠ Thư mục làm việc của surefire là {@code backend/app}, ⛔ KHÔNG phải gốc kho.
     *
     * <p>Bản đầu dùng {@code Paths.get("..").resolve(thuMuc)} và giải ra {@code backend/backend/…}
     * ⇒ <b>0 tệp</b>. Ba bài đỏ ngay, và một trong ba là chính đối chứng phải-tìm-thấy — đúng việc
     * của nó: ⛔ không có nó thì bài chính XANH TRỌN VẸN trên một tập cột rỗng.
     *
     * <p>Khuôn lấy từ {@code PortalSettingsReadTest.timTuGocKho()} — leo tối đa 6 cấp cha.
     */
    /** Gốc kho — neo vào {@code backend/} vì đó là thứ chắc chắn có ở mọi bản clone. */
    private static Path gocKho() {
        return timTuGocKho("backend").getParent();
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
        throw new IllegalStateException(
                "⛔ Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }

    /**
     * ⛔⛔ Bỏ <b>chú thích</b> trước khi tìm — WS-46, và đây là một lỗ hổng THẬT của bộ canh.
     *
     * <h2>Nó lộ ra thế nào</h2>
     *
     * <p>Cột {@code geom} nằm trong {@link #KHONG_CAN_MA_DOC} với lý do <i>"hôm nay ⛔ chưa truy vấn
     * nào dùng tới"</i>. Bài {@link #mienTruKhongPhinhTo} bỗng đỏ sau khi một tệp <b>frontend</b>
     * thêm một dòng javadoc nhắc tên cột ấy — {@code toaDo.ts} giải thích rằng
     * {@code ST_MakePoint(longitude, latitude)} là chỗ duy nhất đảo thứ tự, <i>"viết đúng một lần
     * trong cột sinh {@code geom}"</i>. ⛔ Không một dòng mã nào đọc cột ấy; chỉ có một câu văn.
     *
     * <h2>⭐ Vì sao vá bộ canh chứ ⛔ không viết né nó</h2>
     *
     * <p>Cách rẻ nhất là sửa câu văn cho hết chứa từ {@code geom}. Nhưng nó để nguyên lỗ hổng, và
     * lỗ ấy nằm ở bài <b>chính</b> chứ ⛔ không phải bài phụ: {@link #moiCotDeuCoNoiDocHoacGhi} bắt
     * cột <i>chỉ tồn tại trong migration</i>, và nếu một <b>chú thích</b> tính là "có mã đọc" thì bộ
     * canh ấy <b>im được bằng cách viết tên cột vào javadoc</b> — ⛔ không cần viết một dòng mã nào.
     * Đó đúng là luật 2: <i>canh cấu trúc, đừng canh văn bản</i>.
     *
     * <h2>⚠ Bỏ ÍT chứ ⛔ không bỏ NHIỀU — chiều an toàn của phép này</h2>
     *
     * <p>Bỏ khối {@code /* … *&#47;} (mọi javadoc/TSDoc) và dòng <b>bắt đầu bằng</b> {@code //}. ⛔ Cố
     * ý ⛔ <b>không</b> bỏ {@code //} nằm giữa dòng: một chuỗi {@code "https://…"} trong mã thật sẽ
     * bị cắt mất phần đuôi, và mất mã thật là tạo ra <b>đỏ giả</b> — hỏng theo chiều tệ hơn hẳn.
     *
     * <p>⇒ Còn sót đúng một khe: nhắc tên cột trong một chú thích {@code //} <i>cuối dòng mã</i>.
     * Khe ấy hẹp và ⛔ không thể thu nhỏ thêm mà ⛔ không mở ra rủi ro đỏ giả — ghi ra đây thay vì
     * để người sau tưởng phép này kín (luật 28: một bộ canh phải nói ra phạm vi của chính nó).
     */
    static String boChuThich(String ma) {
        return ma.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)^\\s*//.*$", " ");
    }

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("⛔ không đọc được " + p, e);
        }
    }
}
