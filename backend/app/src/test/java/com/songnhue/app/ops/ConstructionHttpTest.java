package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.importer.SpreadsheetReader;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Danh mục công trình <b>đi qua HTTP</b> — T17.12.
 *
 * <h2>Vì sao không gọi thẳng service</h2>
 *
 * Bài học đắt nhất của lượt rà soát 21/8: {@code GET /api/v1/cms/articles} trả <b>500 cho mọi lượt
 * gọi suốt từ WS-13</b> trong khi 391 bài kiểm vẫn xanh — vì chúng gọi service, tức là khẳng định
 * <i>bên trong</i> giao dịch, nơi nạp lười còn hoạt động. Công trình dùng {@code @SecondaryTable},
 * một cơ chế Hibernate khác nữa mà đường ánh xạ sang DTO nằm ngoài giao dịch; nên nếu có chỗ nào vỡ
 * theo kiểu đó thì chỉ đường HTTP mới thấy.
 *
 * <p>Ba nhóm khẳng định: <b>quyền</b> (tầng 2 chặn đúng), <b>envelope + traceId</b>, và <b>trạng thái
 * dẫn xuất</b> ({@code OPS-3001}).
 */
// PER_CLASS để @BeforeAll không phải static — nó cần các bean được tiêm vào thực thể.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConstructionHttpTest extends IntegrationTestBase {

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;
    private PhienHttp.Phien khongQuyen;
    private UUID donViGoc;

    /**
     * ⚠⚠ Đăng nhập <b>một lần cho cả lớp</b> — chuyển từ {@code @BeforeEach} sang đây ở WS-18.
     *
     * <p>Hạn mức đăng nhập là 30 lượt / 15 phút <b>theo IP</b>, bộ đếm Caffeine dùng chung cho toàn
     * bộ lượt chạy. Lớp này trước đó xin 18 vé (9 bài × 2 tài khoản); khi WS-18 thêm một lớp HTTP
     * nữa thì trần vỡ và <b>chính lớp này</b> đỏ vì lỗi của lớp khác. Ngân sách là tài nguyên dùng
     * chung — xem {@code docs/coding-guide.md} §4.
     */
    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        // ⚠ Cố ý dùng TECHNICIAN chứ không dùng ADMIN. Hai lý do, cả hai đều nói lên điều gì đó:
        //   · ADMIN nằm trong nhóm bắt buộc 2FA nên đăng nhập không ra thẳng AUTHENTICATED;
        //   · quan trọng hơn — TECHNICIAN là vai trò THẬT sẽ dùng chức năng này theo ma trận §6, nên
        //     bài kiểm cũng đồng thời chứng minh ma trận cấp đủ quyền cho đúng người. Cấp ADMIN cho
        //     mọi bài kiểm là cách chắc chắn nhất để một ô thiếu quyền không bao giờ lộ ra.
        String coQuyen = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_full", "TECHNICIAN");
        String traiTay = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_zero");

        duQuyen = phienHttp.dangNhap(coQuyen);
        khongQuyen = phienHttp.dangNhap(traiTay);
    }

    @BeforeEach
    void setUp() {
        donDep();
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    @Test
    @DisplayName("⭐ Tạo → danh sách → chi tiết đều 200, và thông số kỹ thuật về đủ")
    void createListDetailAllWork() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        assertThat(tao.getStatusCode()).as("tạo hồ sơ: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        ResponseEntity<String> danhSach = phienHttp.get(duQuyen, "/api/v1/ops/constructions?q=T17H");
        assertThat(danhSach.getStatusCode())
                .as("danh sách: %s", danhSach.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(danhSach.getBody()).contains("T17H-001");

        ResponseEntity<String> chiTiet = phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + publicId);
        assertThat(chiTiet.getStatusCode())
                .as("⛔ 500 ở đây nghĩa là ánh xạ entity→DTO chạy ngoài giao dịch: %s", chiTiet.getBody())
                .isEqualTo(HttpStatus.OK);

        // ⭐ Khẳng định NỘI DUNG chứ không chỉ mã trạng thái: 200 với thân rỗng vẫn là 200.
        assertThat(chiTiet.getBody())
                .as("thông số trạm bơm nằm ở bảng phụ — 200 mà thiếu chúng nghĩa là bảng phụ không được nạp")
                .contains("\"pumpCount\":3")
                .contains("\"totalFlowM3s\":");
    }

    @Test
    @DisplayName("⭐ Tổng lưu lượng do CSDL tính — client không gửi, mà kết quả vẫn đúng 3 × 1.5")
    void totalFlowIsComputedByDatabase() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        String chiTiet =
                phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + publicId).getBody();
        assertThat(chiTiet)
                .as("quy tắc 3: giá trị tính toán tính ở BE. Ở đây còn chặt hơn — cột sinh của CSDL, "
                        + "không có đường nào để FE tính ra số khác")
                .contains("\"totalFlowM3s\":4.500");
    }

    @Test
    @DisplayName("⛔ Client gửi trạng thái vận hành → OPS-3001, không nuốt lặng lẽ")
    void clientSuppliedStatusIsRejected() {
        String than = thanTramBom().replace("\"description\":null", "\"operationalStatus\":\"SU_CO\"");

        ResponseEntity<String> tao = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", than);

        assertThat(tao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tao.getBody()).contains("OPS-3001");
    }

    /**
     * ⚠⚠ Sắp xếp mặc định mà GIAO DIỆN gửi phải nằm trong bảng trắng của backend.
     *
     * <p>Lỗi đã có thật, đo ngày 01/09/2026: {@code ConstructionsPage.tsx} khai
     * {@code useState('updatedAt,desc')} — tức tham số ấy đi kèm <b>mọi</b> lượt gọi, kể cả lượt
     * tải đầu — trong khi {@code updatedAt} không có trong {@code ConstructionService.SAP_XEP_CHO_PHEP}.
     * {@code PageUtils.parseSort} <b>ném</b> {@code SORT_FIELD_NOT_ALLOWED} chứ không lặng lẽ bỏ qua,
     * nên <b>màn hình danh mục công trình trả 422 ngay lượt tải đầu tiên</b> — và đó cũng là màn hình
     * đặt nút "Nhập nhanh tình hình vận hành".
     *
     * <p>⛔ Vì sao không ai thấy suốt thời gian ấy: bảng vốn đang rỗng thật (G8 chưa có danh mục công
     * trình), nên "rỗng" trông đúng — <i>triệu chứng trùng khít trạng thái đúng</i> (§10.62).
     *
     * <p>⭐ Bài này <b>đọc giá trị mặc định từ chính tệp giao diện</b> thay vì chép lại chuỗi
     * {@code "updatedAt,desc"}: chép lại thì ngày nào ai đó đổi mặc định sang một trường khác cũng
     * không được phép, bài kiểm vẫn xanh và lỗi tái phát y nguyên.
     */
    @Test
    @DisplayName("⭐⭐ Sắp xếp mặc định của ConstructionsPage.tsx phải được backend chấp nhận")
    void defaultSortFromTheAdminScreenIsAccepted() throws java.io.IOException {
        java.nio.file.Path trang = gocKho().resolve("frontend/admin-app/src/features/operations/ConstructionsPage.tsx");
        String nguon = java.nio.file.Files.readString(trang, java.nio.charset.StandardCharsets.UTF_8);

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("useState\\(\\s*'([A-Za-z]+,(?:asc|desc))'\\s*\\)")
                .matcher(nguon);
        assertThat(m.find())
                .as(
                        "không bóc được sort mặc định từ %s — trang đổi cách khai? (luật 7: tập rỗng thì "
                                + "khẳng định dưới đây vô nghĩa)",
                        trang)
                .isTrue();
        String sortMacDinh = m.group(1);

        ResponseEntity<String> danhSach =
                phienHttp.get(duQuyen, "/api/v1/ops/constructions?page=1&size=20&sort=" + sortMacDinh);

        assertThat(danhSach.getStatusCode())
                .as(
                        "giao diện gửi `sort=%s` ở MỌI lượt gọi; backend từ chối là màn hình trắng ngay "
                                + "lượt tải đầu, không phải khi người dùng bấm gì cả",
                        sortMacDinh)
                .isEqualTo(HttpStatus.OK);
        assertThat(danhSach.getBody()).doesNotContain("SORT_FIELD_NOT_ALLOWED");
    }

    /** Đi ngược lên tới thư mục chứa {@code .claude} — chạy được cả từ module lẫn từ gốc repo. */
    private static java.nio.file.Path gocKho() {
        java.nio.file.Path p = java.nio.file.Paths.get("").toAbsolutePath();
        while (p != null && !java.nio.file.Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Không tìm thấy gốc repo (thư mục chứa .claude)");
        }
        return p;
    }

    // =========================================================================
    // Nhập danh mục — G8. ⛔ Trước 09/09/2026 đường này có **0 bài kiểm HTTP**:
    // `ConstructionImportTest` gọi thẳng service, nên nó ⛔ không thấy được phân quyền, ⛔ không
    // thấy multipart, ⛔ không thấy envelope lỗi — đúng luật 5 của dự án.
    // =========================================================================

    /**
     * ⭐ Tệp mẫu — thứ hộp thoại nhập đã hứa từ T17.9 (<i>"đúng biểu mẫu"</i>) mà kho ⛔ không có.
     *
     * <p>Ba khẳng định ⛔ không chia sẻ giả định: <b>có BOM</b> (Excel Windows mở mới ⛔ không vỡ
     * dấu), <b>đủ số cột</b>, và <b>dòng 2 mô tả</b> — dòng ấy là lưới an toàn khiến tải mẫu rồi nhập
     * thẳng lại ⛔ không tạo ra hồ sơ rác nào.
     */
    @Test
    @DisplayName("⭐ GET tệp mẫu: có BOM, đủ 19 cột, và dòng 2 là mô tả (không phải dữ liệu hợp lệ)")
    void importTemplateIsServedAndSelfDescribing() {
        ResponseEntity<String> mau = phienHttp.get(duQuyen, "/api/v1/ops/constructions/import/template");

        assertThat(mau.getStatusCode()).as("tệp mẫu: %s", mau.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(mau.getHeaders().getFirst("Content-Disposition"))
                .as("⛔ thiếu Content-Disposition thì trình duyệt mở CSV trong tab thay vì tải về")
                .contains("attachment")
                .contains("mau-nhap-danh-muc-cong-trinh.csv");

        String csv = mau.getBody();
        assertThat(csv).as("thân tệp mẫu").isNotNull();
        assertThat(csv.charAt(0))
                .as("⛔ CSV không BOM ⇒ Excel bản Windows mở ra tiếng Việt vỡ dấu, và người dùng sẽ "
                        + "'sửa lỗi phông' bằng cách lưu lại ở một bảng mã khác")
                .isEqualTo('﻿');

        String[] dong = csv.split("\r\n|\n");
        assertThat(dong).as("tiêu đề + đúng một dòng mô tả").hasSize(2);
        assertThat(dong[0])
                .contains("ma_cong_trinh")
                .contains("ten_cong_trinh")
                .contains("loai_cong_trinh")
                .contains("ma_don_vi")
                .contains("vi_do")
                .contains("ly_trinh");
        assertThat(dong[1])
                .as("dòng 2 phải là MÔ TẢ, ⛔ không phải một hồ sơ hợp lệ — nếu không thì tải mẫu về "
                        + "rồi nhập thẳng lại sẽ im lặng tạo ra hồ sơ ví dụ")
                .contains("BẮT BUỘC");
    }

    @Test
    @DisplayName("⛔ Không có quyền ops:construction:create → 403 cả ba endpoint nhập")
    void importEndpointsAreGuarded() {
        assertThat(phienHttp
                        .get(khongQuyen, "/api/v1/ops/constructions/import/template")
                        .getStatusCode())
                .as("tệp mẫu mô tả lược đồ nhập — người không nhập được thì không có việc gì với nó")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> xemTruoc = phienHttp.dangTep(
                khongQuyen,
                "/api/v1/ops/constructions/import/preview",
                tepCsv(1).getBytes(StandardCharsets.UTF_8),
                "a.csv");
        assertThat(xemTruoc.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(xemTruoc.getBody()).contains("AUTH-3001");
    }

    /**
     * ⛔⛔ Trần dòng phải <b>NÉM</b>, ⛔ không cắt cụt.
     *
     * <p>Tới 09/09/2026 {@code SpreadsheetReader.dungRows} dừng im lặng ở dòng thứ
     * {@code MAX_ROWS} — ⛔ không ngoại lệ, ⛔ không một {@code RowError} — và
     * {@code ConstructionImportService} lấy {@code tongDong = rows.size()}, tức là <b>đếm sau khi
     * cắt</b>. Hệ quả đo được: một tệp {@code MAX_ROWS + n} dòng cho ra bản báo cáo nói
     * <i>"tổng 5000 dòng, 0 lỗi"</i>, người dùng bấm Nhập, và {@code n} hồ sơ ⛔ không bao giờ tồn
     * tại — ⛔ không một dòng log nào.
     *
     * <p>⚠ Bài kiểm này phân biệt được hai trạng thái (luật 9): bản cũ trả <b>200 kèm
     * {@code totalRows = 5000}</b>, bản mới trả <b>422 {@code SYS-0012}</b>. Một khẳng định kiểu
     * <i>"⛔ không 500"</i> sẽ xanh với cả hai.
     */
    @Test
    @DisplayName("⛔⛔ Tệp vượt trần → 422 SYS-0012, KHÔNG phải 200 với số dòng đã bị cắt")
    void overTheRowCapThrowsInsteadOfTruncating() {
        int tran = SpreadsheetReader.MAX_ROWS;

        ResponseEntity<String> vuot = phienHttp.dangTep(
                duQuyen,
                "/api/v1/ops/constructions/import/preview",
                tepCsv(tran + 3).getBytes(StandardCharsets.UTF_8),
                "qua-tran.csv");

        assertThat(vuot.getStatusCode())
                .as("⛔ 200 ở đây nghĩa là tệp vừa bị cắt cụt trong im lặng: %s", vuot.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(vuot.getBody()).contains("SYS-0012");

        // ⚠ Bộ định dạng thông điệp nhóm hàng nghìn kiểu Việt Nam: 5002 in ra là "5.002". Bỏ dấu
        //   nhóm trước khi so là cách khẳng định về CON SỐ chứ ⛔ không về ĐỊNH DẠNG — nếu không thì
        //   bài kiểm sẽ đỏ vào ngày ai đó đổi locale, một lượt đỏ ⛔ không nói gì về khuyết tật.
        String chiSo = vuot.getBody().replace(".", "").replace(",", "");
        assertThat(chiSo)
                .as("thông báo phải nêu SỐ DÒNG người dùng thấy trong Excel, để họ mở đúng chỗ mà tách tệp")
                .contains(String.valueOf(tran + 2));
    }

    /**
     * ⭐ Đúng trần thì vẫn qua — cận trên là {@code >=}, ⛔ không phải {@code >}.
     *
     * <p>Đặt cạnh bài trên là cố ý: hai bài lệch nhau đúng <b>một dòng</b>, nên một lỗi off-by-one ở
     * chỗ kiểm trần sẽ làm đỏ đúng một trong hai. Một bài đơn lẻ ⛔ không phân biệt được.
     */
    @Test
    @DisplayName("⭐ Đúng trần dòng → vẫn 200, và totalRows là số dòng THẬT")
    void exactlyAtTheCapStillPasses() {
        int tran = SpreadsheetReader.MAX_ROWS;

        ResponseEntity<String> vua = phienHttp.dangTep(
                duQuyen,
                "/api/v1/ops/constructions/import/preview",
                tepCsv(tran).getBytes(StandardCharsets.UTF_8),
                "vua-tran.csv");

        assertThat(vua.getStatusCode())
                .as("đúng trần: %s", tomTat(vua.getBody()))
                .isEqualTo(HttpStatus.OK);
        assertThat(vua.getBody()).contains("\"totalRows\":" + tran);
    }

    /**
     * ⛔ Hộp thoại nhận {@code .xls} suốt từ T17.9, mà bộ đọc ⛔ không đọc được nó.
     *
     * <p>{@code SpreadsheetReader} nhận diện XLSX bằng chữ ký ZIP {@code PK\x03\x04}; {@code .xls}
     * cũ là OLE2 ({@code D0 CF 11 E0}) nên nó rơi xuống nhánh CSV. Nhánh ấy chặn byte {@code 0x00}
     * nên kết cục là một lỗi — nhưng là lỗi <i>"tệp thiếu cột bắt buộc"</i>, câu dẫn người dùng đi
     * sửa tiêu đề của một tệp hoàn toàn đúng. Bài kiểm ghim hành vi thật để lượt sửa
     * {@code accept} ở FE có cái đối chiếu.
     */
    @Test
    @DisplayName("⛔ Tệp .xls (OLE2) không đọc được → 422, không nuốt lặng lẽ thành 0 dòng")
    void oldXlsIsRejectedNotSilentlyEmpty() {
        byte[] ole2 = new byte[] {
            (byte) 0xD0,
            (byte) 0xCF,
            0x11,
            (byte) 0xE0,
            (byte) 0xA1,
            (byte) 0xB1,
            0x1A,
            (byte) 0xE1,
            0x00,
            0x00,
            0x00,
            0x00
        };

        ResponseEntity<String> xls =
                phienHttp.dangTep(duQuyen, "/api/v1/ops/constructions/import/preview", ole2, "danh-muc.xls");

        assertThat(xls.getStatusCode())
                .as("⛔ 200 với 0 dòng ở đây là câu 'tệp của bạn rỗng' cho một tệp đầy dữ liệu: %s", xls.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /** {@code so} dòng dữ liệu hợp lệ + dòng tiêu đề. Mã công trình đánh số để ⛔ không trùng nhau. */
    private static String tepCsv(int so) {
        StringBuilder sb = new StringBuilder("ma_cong_trinh,ten_cong_trinh,loai_cong_trinh,ma_don_vi\n");
        for (int i = 1; i <= so; i++) {
            sb.append("TRAN-").append(i).append(",Công trình ").append(i).append(",Cống,CTY\n");
        }
        return sb.toString();
    }

    /** Thân phản hồi của một tệp lớn có thể rất dài — cắt bớt để thông điệp lỗi còn đọc được. */
    private static String tomTat(String than) {
        return than == null ? "(rỗng)" : than.substring(0, Math.min(300, than.length()));
    }

    @Test
    @DisplayName("Thiếu quyền → 403 AUTH-3001, đúng tầng 2")
    void withoutPermissionIsForbidden() {
        ResponseEntity<String> danhSach = phienHttp.get(khongQuyen, "/api/v1/ops/constructions");

        assertThat(danhSach.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(danhSach.getBody()).contains("AUTH-3001");
    }

    @Test
    @DisplayName("Envelope + traceId có ở cả lượt thành công lẫn lượt lỗi")
    void envelopeAlwaysCarriesTraceId() {
        ResponseEntity<String> ok = phienHttp.get(duQuyen, "/api/v1/ops/constructions");
        assertThat(ok.getBody()).contains("\"success\":true").contains("\"traceId\"");

        ResponseEntity<String> loi = phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + UUID.randomUUID());
        assertThat(loi.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(loi.getBody()).contains("\"success\":false").contains("\"traceId\"");
    }

    @Test
    @DisplayName("⚠ created_by được điền — AuditContext do filter đặt, không phải do test mô phỏng")
    void createdByIsFilledOnTheHttpPath() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        Long nguoiTao = jdbc.queryForObject(
                "SELECT created_by FROM constructions WHERE public_id = ?::uuid", Long.class, publicId);
        Long mongDoi = jdbc.queryForObject("SELECT id FROM users WHERE username = 'kiemtra_ops_full'", Long.class);

        assertThat(nguoiTao)
                .as("AuditorAwareImpl đọc AuditContext do filter đặt, không đọc AuthContext — "
                        + "bài kiểm gọi thẳng service sẽ để cột này NULL mà vẫn xanh (nợ #66)")
                .isEqualTo(mongDoi);
    }

    @Test
    @DisplayName("⛔ Lý trình sai định dạng → OPS-2011; toạ độ nửa vời → OPS-2010")
    void locationRulesAreEnforced() {
        String lyTrinhSai = thanTramBom().replace("\"chainage\":\"K0+390\"", "\"chainage\":\"km 390\"");
        ResponseEntity<String> a = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", lyTrinhSai);
        assertThat(a.getBody()).contains("OPS-2011");

        String thieuKinhDo = thanTramBom().replace("\"longitude\":105.780000,", "\"longitude\":null,");
        ResponseEntity<String> b = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thieuKinhDo);
        assertThat(b.getBody())
                .as("một nửa toạ độ là một điểm sai trên bản đồ — tệ hơn hẳn chưa số hoá")
                .contains("OPS-2010");
    }

    @Test
    @DisplayName("⛔ Thông số sai loại công trình → OPS-2009 (cống không có số máy bơm)")
    void specsMustMatchTheType() {
        String congCoBom = thanTramBom()
                .replace("\"constructionType\":\"TRAM_BOM\"", "\"constructionType\":\"CONG\"")
                .replace("T17H-001", "T17H-002");

        ResponseEntity<String> tao = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", congCoBom);

        assertThat(tao.getBody()).contains("OPS-2009");
    }

    @Test
    @DisplayName("Nhật ký thay đổi hồ sơ đọc được từ audit_logs — không có bảng lịch sử riêng")
    void changeLogReadsFromAuditLog() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        ResponseEntity<String> nhatKy = phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + publicId + "/change-log");

        assertThat(nhatKy.getStatusCode()).as("%s", nhatKy.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(nhatKy.getBody())
                .as("bộ ghi nhật ký của Core bắt lượt tạo ở tầng Hibernate — không service nào phải gọi tay")
                .contains("CREATE");
    }

    // -------------------------------------------------------------------------

    private String thanTramBom() {
        return """
            {"code":"T17H-001","name":"Trạm bơm kiểm thử HTTP","constructionType":"TRAM_BOM",
             "orgUnitId":"%s","managementLevel":"XI_NGHIEP",
             "latitude":20.980000,"longitude":105.780000,
             "riverName":"Nhuệ","chainage":"K0+390","basinNote":"Lưu vực sông Nhuệ",
             "pump":{"pumpCount":3,"flowPerPumpM3s":1.5,"totalPowerKw":250},
             "description":null}"""
                .formatted(donViGoc);
    }

    private void donDep() {
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T17H-%'");
    }
}
