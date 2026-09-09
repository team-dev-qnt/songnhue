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

    private static final String THU_MUC_MIGRATION = "backend/hydro/src/main/resources/db/migration/hyd";

    /** Nơi một cột có thể được đọc hoặc ghi. ⛔ Cố ý ⛔ không gồm thư mục test. */
    private static final List<String> NOI_DOC_GHI = List.of(
            "backend/hydro/src/main/java",
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
                    + "bảng đã có dữ liệu, và `latitude`/`longitude` — nguồn của nó — đang được đọc thật."));

    /** Cột ai cũng biết là có người đọc — đối chứng chứng minh phép tìm còn sống (luật 10). */
    private static final List<String> PHAI_TIM_THAY = List.of("position_role", "api_code", "valid_value");

    private static final Pattern KIEU_COT = Pattern.compile(
            "^ {4}([a-z_]+)\\s+(BIGINT|BIGSERIAL|VARCHAR|TEXT|BOOLEAN|NUMERIC|timestamptz|DATE|INTEGER"
                    + "|INT|SMALLINT|JSONB|JSON|UUID|geometry|DOUBLE)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

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
        for (Path p : tepTrong(THU_MUC_MIGRATION, ".sql")) {
            // ⛔ Bỏ chú thích TRƯỚC khi khớp (luật 2 + §10.62): một cột được nhắc trong chú thích
            //   "cột này đã bị gỡ" ⛔ không phải một cột đang sống, và mẫu ⛔ không tự biết điều đó.
            String sql = doc(p).replaceAll("--[^\n]*", "");

            Matcher bang = BANG_TAO.matcher(sql);
            while (bang.find()) {
                Matcher c = KIEU_COT.matcher(bang.group(2));
                while (c.find()) {
                    ghi(ket, c.group(1), bang.group(1));
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
