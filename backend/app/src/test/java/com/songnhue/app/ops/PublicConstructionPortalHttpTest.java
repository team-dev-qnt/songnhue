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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Hai đường công khai mở ở WS-27 <b>đi qua HTTP thật</b> — T27.18.
 *
 * <h2>Vì sao bài kiểm này phải tồn tại dù đã có hai lớp kiểm đơn vị</h2>
 *
 * <p>{@code PublicOperationStatusServiceTest} và {@code PublicConstructionCatalogServiceTest} đều
 * <b>mock repository</b>. Chúng chứng minh logic gộp dòng là đúng, và không chứng minh gì về đường
 * mà người dùng thật đi: {@code mock đặt đúng chỗ mã chạm ra ngoài là chưa kiểm gì cả} (luật 4 —
 * {@code BackupServiceTest} mock {@code PostgresToolRunner} và sao lưu chưa từng sinh ra một tệp
 * nào suốt 4 ngày), và {@code bài kiểm gọi thẳng service không đi cùng đường với production}
 * (luật 5 — 391 bài xanh trong khi mọi màn hình quản trị nội dung trả 500).
 *
 * <h2>⭐ Bài kiểm đi nhánh CÓ DỮ LIỆU, không chỉ nhánh 404</h2>
 *
 * <p>§10.52: đường ảnh cổng <b>chưa từng trả về một byte nào</b> suốt nhiều WS, vì bài kiểm duy nhất
 * canh nó dùng một UUID không tồn tại — nên nó chỉ đi nhánh 404 và xanh mãi mãi. Ở đây mỗi đường đều
 * có một bài <b>dựng dữ liệu thật rồi đòi byte thật</b>, và các bài 404 chỉ là vế bổ sung.
 *
 * <h2>⚠ Vì sao mọi lượt gọi mang {@code Origin}</h2>
 *
 * <p>Luật 6: {@code curl} không có origin, không preflight, nên đi lọt qua đúng bức tường chặn người
 * dùng thật — CORS đã chặn toàn bộ giao diện quản trị suốt WS-8→WS-20 mà không bài kiểm nào thấy.
 * Dự án hiện <b>không</b> có lớp CORS nào (cổng và API cùng origin sau nginx —
 * {@code FrontendSameOriginTest} canh điều đó), nên khẳng định ở đây là: <i>vẫn phải 200 khi trình
 * duyệt gửi Origin</i>. Ngày nào có người thêm một lớp CORS, bài này đỏ trước khi cổng công khai
 * trắng trang.
 */
// PER_CLASS để @BeforeAll dùng được bean được tiêm — cùng lý do đã ghi ở ConstructionHttpTest.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicConstructionPortalHttpTest extends IntegrationTestBase {

    /** Trùng với origin mà cổng Next.js gọi sang API trong lượt dựng trang. */
    private static final String ORIGIN_CONG = "https://songnhue.example.vn";

    private static final String MA_DONG_KIN = "ĐK";

    /**
     * ⚠ Phải mở đầu bằng chữ ký {@code %PDF-} thật.
     *
     * <p>{@code AttachmentService} <b>ngửi nội dung</b> chứ không tin phần mở rộng của tên tệp — gửi
     * byte tuỳ ý kèm tên {@code .pdf} thì nhận {@code FILE_TYPE_NOT_ALLOWED} với
     * {@code rejectedValue: unknown}. Đó là hành vi đúng (đổi tên {@code .exe} thành {@code .pdf} là
     * đường tấn công cũ nhất), và bài kiểm phải đi đúng đường người dùng thật đi.
     */
    private static final String NOI_DUNG_TEP = "%PDF-1.4 NOI-DUNG-QUY-TRINH";

    /** Ghi chú nội bộ giữa các ca trực — ⛔ giá trị này KHÔNG được xuất hiện trên cổng. */
    private static final String GHI_CHU_NOI_BO = "Ghi chú nội bộ ca trực không được ra cổng";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;
    private PhienHttp.Phien trucBan;
    private UUID donViGoc;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        // TECHNICIAN là vai trò duy nhất có ops:construction:create (ma trận §6); XN_OPERATOR ghi
        // tình hình vận hành. ⚠ Không dùng ADMIN: T5.8 bắt buộc 2FA nên lượt đăng nhập dừng ở
        // TWO_FACTOR_ENROLL_REQUIRED và không có access token.
        kyThuat = phienHttp.dangNhap(
                PhienHttp.taoNguoiDung(users, passwords, jdbc, "t2718_kythuat", "TECHNICIAN", "XN_MANAGER"));
        trucBan = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t2718_trucban", "XN_OPERATOR"));
    }

    @BeforeEach
    void setUp() {
        donDep();
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // === Đường 1: tình hình vận hành công khai (T27.16) =======================

    @Test
    @DisplayName("⭐ Khách vãng lai (không đăng nhập, có Origin) đọc được dòng tình hình vận hành THẬT")
    void anonymousBrowserReadsRealOperationStatusRows() {
        UUID congTrinh = taoCongTrinh("T2718-001", "Cống Kiểm Thử Công Khai");
        ghiNhanTinhHinh(congTrinh);

        ResponseEntity<String> phanHoi = getCongKhai("/api/v1/public/constructions/operation-statuses");

        assertThat(phanHoi.getStatusCode())
                .as(
                        "cổng dựng trang bằng đường này — 4xx/5xx ở đây là khối Vận hành công trình trắng: %s",
                        phanHoi.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(phanHoi.getBody())
                .as(
                        """
                        ⭐ Đây là vế mà §10.52 đã dạy: một bài chỉ đi nhánh RỖNG sẽ xanh mãi mãi. Bài này \
                        dựng một công trình thật + một bản ghi thật rồi đòi thấy nó.""")
                .contains("T2718-001")
                .contains("Cống Kiểm Thử Công Khai")
                .contains(MA_DONG_KIN);
    }

    @Test
    @DisplayName("⛔ Cổng KHÔNG nhận được ghi chú nội bộ và danh tính người cập nhật")
    void theInternalNoteAndTheAuthorNeverReachThePortal() {
        UUID congTrinh = taoCongTrinh("T2718-002", "Cống Có Ghi Chú");
        ghiNhanTinhHinh(congTrinh);

        String than =
                getCongKhai("/api/v1/public/constructions/operation-statuses").getBody();

        assertThat(than)
                .as("bản ghi đã vào CSDL — nếu không thì bài này xanh vì lý do sai")
                .contains("T2718-002");
        assertThat(than)
                .as(
                        """
                        ⛔ Phạm vi công bố là một QUYẾT ĐỊNH (QuanTran chốt 31/08), không phải một cột \
                        quên đấu dây: `note` là trao đổi nội bộ giữa các ca trực, và người cập nhật là \
                        danh tính cán bộ. Một trường thêm vào record sẽ đi thẳng ra Internet mà không ai \
                        duyệt.""")
                .doesNotContain(GHI_CHU_NOI_BO)
                .doesNotContain("\"note\"")
                .doesNotContain("t2718_trucban")
                .doesNotContain("updatedBy");
    }

    @Test
    @DisplayName("Công trình đã thanh lý biến mất khỏi khối tình hình vận hành trên cổng")
    void aDecommissionedConstructionDropsOutOfThePortalBlock() {
        UUID congTrinh = taoCongTrinh("T2718-003", "Cống Sắp Thanh Lý");
        ghiNhanTinhHinh(congTrinh);
        assertThat(getCongKhai("/api/v1/public/constructions/operation-statuses")
                        .getBody())
                .contains("T2718-003");

        thanhLy(congTrinh);

        assertThat(getCongKhai("/api/v1/public/constructions/operation-statuses")
                        .getBody())
                .as("bộ lọc phải trùng khít catalogByUnit() — hai nơi lệch nhau là cổng nói một đằng, "
                        + "danh mục nói một nẻo")
                .doesNotContain("T2718-003");
    }

    @Test
    @DisplayName("⭐ Giá trị tham số BigDecimal giữ nguyên số chữ số thập phân trên dây")
    void aBigDecimalParameterKeepsItsScaleOnTheWire() {
        jdbc.update(
                """
                INSERT INTO operation_status_codes
                    (code, name, has_parameter, parameter_unit, color_hex, mapped_status, sort_order, created_at)
                VALUES ('T2718M', 'Mở tham số kiểm thử', TRUE, 'm', '#10b981', 'BINH_THUONG', 900, now())
                """);
        UUID congTrinh = taoCongTrinh("T2718-020", "Cống Có Tham Số");
        String than =
                """
                {"items":[{"constructionPublicId":"%s","operationCode":"T2718M",
                           "parameterValue":2.30,"effectiveAt":"2026-08-30T08:00:00+07:00"}]}"""
                        .formatted(congTrinh);
        ResponseEntity<String> ghi =
                phienHttp.goi(trucBan, HttpMethod.POST, "/api/v1/ops/operation-statuses/batch", than);
        assertThat(ghi.getStatusCode())
                .as("%s", ghi.getBody())
                .isIn(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NO_CONTENT);

        String body =
                getCongKhai("/api/v1/public/constructions/operation-statuses").getBody();

        assertThat(body)
                .as(
                        """
                        ⚠ Quy tắc 2 cấm float/double cho mọi số đo, và `2,30 m` khác `2,3 m` với người \
                        đọc mực nước. Bài này ĐO dạng thật trên dây thay vì tin khai báo kiểu ở FE — \
                        `lib/api.ts` khai `parameterValue: string | null`, một lời khẳng định chưa ai \
                        đối chiếu. Thân phản hồi thật: %s""",
                        body)
                .contains("\"parameterValue\":\"2.30\"");
    }

    // === Đường 2: tệp tài liệu công bố của công trình (T27.14) ================

    @Test
    @DisplayName("⭐⭐ Tệp Quy trình vận hành đã công bố ra được BYTE THẬT qua đường công khai")
    void aPublishedOperatingProcedureFileActuallyReturnsBytes() {
        UUID congTrinh = taoCongTrinh("T2718-010", "Cống Có Quy Trình");
        UUID tep = taiTaiLieu(congTrinh, "quy-trinh-van-hanh.pdf");
        congBoQuyTrinh(congTrinh, tep);

        ResponseEntity<byte[]> phanHoi = getCongKhaiNhiPhan("/api/v1/public/constructions/documents/" + tep);

        assertThat(phanHoi.getStatusCode())
                .as("⛔ §10.52 — trước bài này, đường tệp công trình chưa từng được chứng minh là trả byte")
                .isEqualTo(HttpStatus.OK);
        assertThat(phanHoi.getBody())
                .as("một phản hồi 200 rỗng vẫn là một liên kết tải về hỏng")
                .isNotNull()
                .isNotEmpty();
        assertThat(new String(phanHoi.getBody(), StandardCharsets.UTF_8))
                .as("phải đúng nội dung đã tải lên, không phải một envelope JSON bọc quanh byte[]")
                .contains("NOI-DUNG-QUY-TRINH");
        assertThat(phanHoi.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .as("ô chọn tệp ở màn hình quản trị hứa `liên kết tải về`, không phải xem tại chỗ")
                .startsWith("attachment;")
                .contains("quy-trinh-van-hanh.pdf");
    }

    @Test
    @DisplayName("⛔ Tệp CÙNG công trình nhưng KHÔNG nằm ở hai cột công bố → 404")
    void anUnpublishedFileOfTheSameConstructionIsNotReachable() {
        UUID congTrinh = taoCongTrinh("T2718-011", "Cống Có Hồ Sơ Mật");
        UUID tepCongBo = taiTaiLieu(congTrinh, "quy-trinh-van-hanh.pdf");
        UUID tepNoiBo = taiTaiLieu(congTrinh, "ho-so-hoan-cong.pdf");
        congBoQuyTrinh(congTrinh, tepCongBo);

        assertThat(getCongKhaiNhiPhan("/api/v1/public/constructions/documents/" + tepCongBo)
                        .getStatusCode())
                .as("dựng dữ liệu đúng thì tệp công bố phải ra — nếu không, bài 404 dưới đây vô nghĩa")
                .isEqualTo(HttpStatus.OK);

        assertThat(getCongKhaiNhiPhan("/api/v1/public/constructions/documents/" + tepNoiBo)
                        .getStatusCode())
                .as(
                        """
                        ⛔ Đây là lý do WS-27 mở một đường HẸP thay vì nới LOAI_TEP_CONG_KHAI: nới danh \
                        sách của cổng là mở cả kho tài liệu công trình, gồm hồ sơ hoàn công và ảnh hiện \
                        trạng. Hai tệp này thuộc CÙNG một công trình, chỉ khác nhau ở chỗ có được đặt \
                        vào cột công bố hay không.""")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ Thanh lý công trình thì tệp đã công bố của nó cũng đóng lại")
    void decommissioningAlsoClosesThePublishedFile() {
        UUID congTrinh = taoCongTrinh("T2718-012", "Cống Thanh Lý Kèm Tệp");
        UUID tep = taiTaiLieu(congTrinh, "quy-trinh-van-hanh.pdf");
        congBoQuyTrinh(congTrinh, tep);
        assertThat(getCongKhaiNhiPhan("/api/v1/public/constructions/documents/" + tep)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        thanhLy(congTrinh);

        assertThat(getCongKhaiNhiPhan("/api/v1/public/constructions/documents/" + tep)
                        .getStatusCode())
                .as("lớp chặn thứ hai: công trình còn sống chưa thanh lý. Thiếu nó thì hồ sơ của một "
                        + "công trình đã bỏ vẫn tải về được vĩnh viễn")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Mã tệp không tồn tại → 404, không phải 500")
    void anUnknownFileIdIs404() {
        assertThat(getCongKhaiNhiPhan("/api/v1/public/constructions/documents/" + UUID.randomUUID())
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------

    /** ⚠ Không mang Authorization: đây đúng là thứ khách vãng lai gửi. */
    private ResponseEntity<String> getCongKhai(String duong) {
        return http.exchange(duong, HttpMethod.GET, new HttpEntity<>(headerTrinhDuyet()), String.class);
    }

    private ResponseEntity<byte[]> getCongKhaiNhiPhan(String duong) {
        return http.exchange(duong, HttpMethod.GET, new HttpEntity<>(headerTrinhDuyet()), byte[].class);
    }

    private static HttpHeaders headerTrinhDuyet() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, ORIGIN_CONG);
        return headers;
    }

    private UUID taoCongTrinh(String ma, String ten) {
        String than =
                """
                {"code":"%s","name":"%s","constructionType":"CONG",
                 "orgUnitId":"%s","managementLevel":"XI_NGHIEP"}"""
                        .formatted(ma, ten, donViGoc);
        ResponseEntity<String> phanHoi = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/ops/constructions", than);
        assertThat(phanHoi.getStatusCode())
                .as("dựng dữ liệu hỏng thì mọi bài kiểm bên dưới nói dối: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(PhienHttp.giaTriJson(phanHoi.getBody(), "publicId"));
    }

    private void ghiNhanTinhHinh(UUID congTrinh) {
        String than =
                """
                {"items":[{"constructionPublicId":"%s","operationCode":"%s",
                           "note":"%s","effectiveAt":"2026-08-30T08:00:00+07:00"}]}"""
                        .formatted(congTrinh, MA_DONG_KIN, GHI_CHU_NOI_BO);
        ResponseEntity<String> phanHoi =
                phienHttp.goi(trucBan, HttpMethod.POST, "/api/v1/ops/operation-statuses/batch", than);
        assertThat(phanHoi.getStatusCode())
                .as("%s", phanHoi.getBody())
                .isIn(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
    }

    /** Tải một tệp thật lên tab Tài liệu đính kèm, rồi đưa nó qua cổng quét virus. */
    private UUID taiTaiLieu(UUID congTrinh, String tenTep) {
        ResponseEntity<String> phanHoi = phienHttp.dangTep(
                kyThuat,
                "/api/v1/ops/constructions/" + congTrinh + "/documents?docType=QUY_TRINH_VAN_HANH",
                NOI_DUNG_TEP.getBytes(StandardCharsets.UTF_8),
                tenTep);
        assertThat(phanHoi.getStatusCode()).as("%s", phanHoi.getBody()).isEqualTo(HttpStatus.CREATED);
        String tepId = PhienHttp.giaTriJson(phanHoi.getBody(), "publicId");

        // ⚠ Điều kiện chặn là cột `status` (Attachment.isDownloadable đọc status == READY), không
        // phải `scan_status` — đặt nhầm cột thì bài kiểm đỏ và ta đi tra nhầm chỗ.
        jdbc.update("UPDATE attachments SET status = 'READY', scan_status = 'CLEAN' WHERE public_id = ?::uuid", tepId);
        return UUID.fromString(tepId);
    }

    /** Đặt tệp vào cột "Quy trình vận hành" — vế GHI mà tới 31/08 chưa màn hình nào có. */
    private void congBoQuyTrinh(UUID congTrinh, UUID tep) {
        String than =
                """
                {"code":"%s","name":"%s","constructionType":"CONG","orgUnitId":"%s",
                 "managementLevel":"XI_NGHIEP","operatingProcedureAttachmentId":"%s"}"""
                        .formatted(maCua(congTrinh), tenCua(congTrinh), donViGoc, tep);
        ResponseEntity<String> phanHoi =
                phienHttp.goi(kyThuat, HttpMethod.PUT, "/api/v1/ops/constructions/" + congTrinh, than);
        assertThat(phanHoi.getStatusCode()).as("%s", phanHoi.getBody()).isEqualTo(HttpStatus.OK);
    }

    private void thanhLy(UUID congTrinh) {
        ResponseEntity<String> phanHoi = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                "/api/v1/ops/constructions/" + congTrinh + "/lifecycle",
                """
                {"state":"DA_THANH_LY","reason":"kiểm thử đường công khai"}""");
        assertThat(phanHoi.getStatusCode()).as("%s", phanHoi.getBody()).isEqualTo(HttpStatus.OK);
    }

    private String maCua(UUID congTrinh) {
        return jdbc.queryForObject("SELECT code FROM constructions WHERE public_id = ?", String.class, congTrinh);
    }

    private String tenCua(UUID congTrinh) {
        return jdbc.queryForObject("SELECT name FROM constructions WHERE public_id = ?", String.class, congTrinh);
    }

    private void donDep() {
        jdbc.update("DELETE FROM construction_operation_status WHERE construction_id IN "
                + "(SELECT id FROM constructions WHERE code LIKE 'T2718-%')");
        jdbc.update("DELETE FROM attachments WHERE owner_type = 'CONSTRUCTION' AND owner_id IN "
                + "(SELECT id FROM constructions WHERE code LIKE 'T2718-%')");
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T2718-%'");
        jdbc.update("DELETE FROM operation_status_codes WHERE code = 'T2718M'");
    }
}
