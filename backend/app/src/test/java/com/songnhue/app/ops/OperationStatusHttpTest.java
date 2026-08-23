package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Tình hình vận hành cống <b>đi qua HTTP</b> — CN-02.11, đợt vá #6/#7/#10.
 *
 * <h2>Vì sao lớp này tồn tại</h2>
 *
 * <p>Chức năng nhập tình hình vận hành có bài kiểm ở tầng service từ WS-19 và chúng <b>xanh trọn
 * vẹn</b>. Trong khi đó, trên đường HTTP thật:
 *
 * <ul>
 *   <li>Giao diện gọi {@code /ops/operation-status/batch} còn backend phục vụ
 *       {@code /ops/operation-statuses} → <b>404 ở mọi lượt bấm Lưu</b>, chức năng chưa từng chạy.
 *   <li>Đường ghi nhận khoá công trình kiểu {@code Long} từ payload rồi tra bằng {@code findById},
 *       thứ <b>không đi qua bộ lọc phạm vi</b> → ghi được nhật ký vận hành cho công trình của Xí
 *       nghiệp khác và lật trạng thái công trình của họ.
 *   <li>Quyền {@code ops:operation-status:view} được cấp cho 6 vai trò mà <b>không endpoint nào đòi
 *       nó</b> — dữ liệu chỉ có đường vào, không có đường ra.
 * </ul>
 *
 * <p>Cả ba đều vô hình với bài kiểm gọi thẳng service. Đây đúng là bài học 391-bài-xanh-mà-mọi-màn-
 * hình-500 của WS-20, và luật 5: <i>cam kết nằm ở controller thì phải kiểm qua HTTP</i>.
 */
// PER_CLASS để @BeforeAll không phải static — nó cần bean được tiêm vào thực thể.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationStatusHttpTest extends IntegrationTestBase {

    /** Mã seed WS-19 ánh xạ sang {@code NGUNG_MUA_VU} — dùng để chứng minh trạng thái có đổi thật. */
    private static final String MA_DONG_KIN = "ĐK";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;

    /** Trực ban Xí nghiệp A — có {@code ops:operation-status:update}, phạm vi bó trong A. */
    private PhienHttp.Phien trucBanA;

    /** Trực ban Xí nghiệp B — cùng quyền, khác đơn vị. Đây là "kẻ tấn công" của bài kiểm IDOR. */
    private PhienHttp.Phien trucBanB;

    /**
     * Người dựng dữ liệu công trình cho cả hai Xí nghiệp — đứng ở cấp Công ty nên thấy cả hai.
     *
     * <p>Mang {@code TECHNICIAN} vì đó là vai trò <b>duy nhất</b> có {@code ops:construction:create}
     * (ma trận §6); {@code XN_MANAGER} chỉ sửa và xoá.
     *
     * <p>⚠ Cố ý <b>không</b> mang vai trò {@code ADMIN}: T5.8 bắt buộc 2FA cho ADMIN nên lượt đăng
     * nhập dừng ở {@code TWO_FACTOR_ENROLL_REQUIRED} và không có access token. Hệ quả kèm theo —
     * danh mục mã tình hình vận hành <b>chỉ</b> quản trị được bởi ADMIN/SUPER_ADMIN, nên bảo đảm
     * "đường dẫn đi bằng publicId" của nó được canh bằng luật cấu trúc ({@code ApiSurfaceRuleTest})
     * thay vì một phiên HTTP.
     */
    private PhienHttp.Phien quanLyCty;

    private long xnAId;
    private long xnBId;
    private UUID xnAPublicId;
    private UUID xnBPublicId;

    private UUID congTrinhCuaA;
    private UUID congTrinhCuaB;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        donDepDonVi();

        long rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        String pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);
        xnAId = themDonVi("T19-XN-A", rootId, pathRoot);
        xnBId = themDonVi("T19-XN-B", rootId, pathRoot);
        xnAPublicId = publicIdDonVi(xnAId);
        xnBPublicId = publicIdDonVi(xnBId);

        // ⚠ Đặt đơn vị TRƯỚC khi đăng nhập: `orgUnitPath` nằm trong access token, đổi bảng users sau
        // khi đã đăng nhập thì phiên vẫn mang phạm vi cũ và bài kiểm chứng minh nhầm thứ.
        String tenA = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_trucban_a", "XN_OPERATOR");
        String tenB = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_trucban_b", "XN_OPERATOR");
        datDonVi(tenA, xnAId);
        datDonVi(tenB, xnBId);

        trucBanA = phienHttp.dangNhap(tenA);
        trucBanB = phienHttp.dangNhap(tenB);
        quanLyCty = phienHttp.dangNhap(
                PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_quanly_cty", "TECHNICIAN", "XN_MANAGER"));
    }

    @BeforeEach
    void setUp() {
        donDepCongTrinh();
        congTrinhCuaA = taoCongTrinh("T19H-A-001", xnAPublicId);
        congTrinhCuaB = taoCongTrinh("T19H-B-001", xnBPublicId);
    }

    @AfterEach
    void tearDown() {
        donDepCongTrinh();
    }

    // === ⭐⭐ Phạm vi đơn vị trên đường GHI ===================================

    @Test
    @DisplayName("⭐⭐ Trực ban B ghi tình hình cho công trình của A → AUTH-3002, không dòng nào được ghi")
    void anOperatorCannotRecordForAnotherUnitsConstruction() {
        ResponseEntity<String> phanHoi = ghiNhan(trucBanB, congTrinhCuaA, MA_DONG_KIN);

        assertThat(phanHoi.getStatusCode())
                .as(
                        "⛔ Đây là lỗ IDOR của WS-19: khoá công trình từng là số tự tăng và tra bằng "
                                + "findById, thứ Hibernate KHÔNG áp bộ lọc phạm vi vào. %s",
                        phanHoi.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(phanHoi.getBody()).contains("AUTH-3002");

        assertThat(soBanGhi(congTrinhCuaA))
                .as("lượt bị từ chối không được để lại dấu vết nào")
                .isZero();
        assertThat(trangThai(congTrinhCuaA))
                .as("⛔ Nặng hơn cả rò rỉ: bản ghi chép org_unit_id của NẠN NHÂN nên nó nằm gọn trong "
                        + "phạm vi của họ và lật luôn trạng thái công trình của họ")
                .isEqualTo("BINH_THUONG");
    }

    @Test
    @DisplayName("Trực ban A ghi cho công trình của chính A → 201, trạng thái đổi theo ánh xạ của mã")
    void anOperatorCanRecordForTheirOwnConstruction() {
        ResponseEntity<String> phanHoi = ghiNhan(trucBanA, congTrinhCuaA, MA_DONG_KIN);

        assertThat(phanHoi.getStatusCode()).as("%s", phanHoi.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(soBanGhi(congTrinhCuaA)).isEqualTo(1);
        assertThat(trangThai(congTrinhCuaA))
                .as("mắt xích 4 của CN-02.1 — mã ĐK ánh xạ sang NGUNG_MUA_VU")
                .isEqualTo("NGUNG_MUA_VU");
    }

    @Test
    @DisplayName("⭐ Cả lô hoặc không dòng nào — một dòng ngoài phạm vi làm hỏng cả lượt gửi")
    void theWholeBatchRollsBackWhenOneRowIsOutOfScope() {
        String than =
                """
                {"items":[
                  {"constructionPublicId":"%s","operationCode":"%s","effectiveAt":"2026-08-23T08:00:00+07:00"},
                  {"constructionPublicId":"%s","operationCode":"%s","effectiveAt":"2026-08-23T08:00:00+07:00"}
                ]}"""
                        .formatted(congTrinhCuaA, MA_DONG_KIN, congTrinhCuaB, MA_DONG_KIN);

        ResponseEntity<String> phanHoi =
                phienHttp.goi(trucBanA, HttpMethod.POST, "/api/v1/ops/operation-statuses/batch", than);

        assertThat(phanHoi.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(soBanGhi(congTrinhCuaA))
                .as("⛔ Dòng ĐẦU hợp lệ và đã save() trước khi dòng sau ném lỗi. Không có giao dịch "
                        + "bao quanh thì nó nằm lại trong CSDL và người dùng tin rằng cả lô đã hỏng")
                .isZero();
    }

    @Test
    @DisplayName("⭐ Lô nhiều dòng lỗi → báo ĐỦ cả ba, không phải dừng ở dòng đầu")
    void everyBadRowIsReportedAtOnce() {
        String than =
                """
                {"items":[
                  {"constructionPublicId":"%s","operationCode":"KHONG-CO-MA-NAY","effectiveAt":"2026-08-23T08:00:00+07:00"},
                  {"constructionPublicId":"%s","operationCode":"%s","parameterValue":"1.70","effectiveAt":"2026-08-23T08:00:00+07:00"},
                  {"constructionPublicId":"%s","operationCode":"%s","effectiveAt":"2026-08-23T08:00:00+07:00"}
                ]}"""
                        .formatted(congTrinhCuaA, congTrinhCuaA, MA_DONG_KIN, congTrinhCuaA, MA_DONG_KIN);

        ResponseEntity<String> phanHoi =
                phienHttp.goi(trucBanA, HttpMethod.POST, "/api/v1/ops/operation-statuses/batch", than);

        assertThat(phanHoi.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(phanHoi.getBody()).contains("OPS-2019");

        // Dòng 0 sai mã, dòng 1 gửi tham số cho mã không có tham số. Dòng 2 hợp lệ.
        assertThat(phanHoi.getBody())
                .as(
                        "⛔ Giao dịch dừng ở dòng lỗi ĐẦU TIÊN. Với màn hình nhập vài chục cống, điều đó "
                                + "nghĩa là sửa một dòng, gửi lại, lại hỏng ở dòng khác — mỗi vòng một lỗi. %s",
                        phanHoi.getBody())
                .contains("items[0]")
                .contains("items[1]");
        assertThat(phanHoi.getBody()).contains("SYS-0004").contains("OPS-2006");

        assertThat(soBanGhi(congTrinhCuaA))
                .as("dòng thứ ba hợp lệ nhưng cả lô bị huỷ — không dòng nào được ghi")
                .isZero();
    }

    @Test
    @DisplayName("⭐⭐ Nhập bù cho quá khứ KHÔNG làm đổi tình hình hiện hành")
    void aBackdatedRecordDoesNotBecomeTheCurrentStatus() {
        // Trực ban ghi tình hình hôm nay: cống đóng kín.
        assertThat(ghiNhanLuc(trucBanA, congTrinhCuaA, MA_DONG_KIN, "2026-08-23T08:00:00+07:00")
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(trangThai(congTrinhCuaA)).isEqualTo("NGUNG_MUA_VU");

        // Sau đó nhập bù cho HÔM KIA: cống mở tự do. Bản ghi vào sau nhưng có hiệu lực TRƯỚC.
        assertThat(ghiNhanLuc(trucBanA, congTrinhCuaA, "MT", "2026-08-21T08:00:00+07:00")
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(soBanGhi(congTrinhCuaA)).isEqualTo(2);
        assertThat(trangThai(congTrinhCuaA))
                .as("⛔ \"Hiện hành\" đi theo `effective_at`, KHÔNG theo `created_at` — T19.2. Lấy nhầm "
                        + "cột thì mọi lượt nhập bù cho quá khứ sẽ ghi đè tình hình hôm nay, và triệu "
                        + "chứng chỉ là màu marker trên bản đồ điều hành đổi mà không ai hiểu vì sao")
                .isEqualTo("NGUNG_MUA_VU");
    }

    // === Đường dẫn — chỗ giao diện và backend phải khớp nhau ==================

    @Test
    @DisplayName("⭐ Đường dẫn số ít /operation-status/batch KHÔNG tồn tại — giao diện từng gọi đúng nó")
    void theOldSingularPathIsGone() {
        ResponseEntity<String> phanHoi = phienHttp.goi(
                trucBanA, HttpMethod.POST, "/api/v1/ops/operation-status/batch", """
                {"items":[]}""");

        assertThat(phanHoi.getStatusCode())
                .as("bài kiểm này không canh một lỗi — nó canh việc hai bên KHỚP nhau. Đổi đường dẫn "
                        + "ở một phía mà quên phía kia là hình dạng lỗi đã lặp lại nhiều lần (luật 11)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // === Quyền đọc — endpoint lấp một quyền chết ==============================

    @Test
    @DisplayName("Trực ban đọc được lịch sử của công trình mình, KHÔNG đọc được của đơn vị khác")
    void historyIsScopedToo() {
        ghiNhan(trucBanA, congTrinhCuaA, MA_DONG_KIN);

        ResponseEntity<String> cuaMinh =
                phienHttp.get(trucBanA, "/api/v1/ops/operation-statuses?constructionPublicId=" + congTrinhCuaA);
        assertThat(cuaMinh.getStatusCode()).as("%s", cuaMinh.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(cuaMinh.getBody()).contains(MA_DONG_KIN);

        ResponseEntity<String> cuaDonViKhac =
                phienHttp.get(trucBanB, "/api/v1/ops/operation-statuses?constructionPublicId=" + congTrinhCuaA);
        assertThat(cuaDonViKhac.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⭐ Danh mục mã cho màn hình nhập đòi quyền NHẬP LIỆU, không đòi quyền quản trị danh mục")
    void theActiveCatalogUsesTheDataEntryPermission() {
        ResponseEntity<String> danhMuc = phienHttp.get(trucBanA, "/api/v1/ops/operation-status-codes/active");

        assertThat(danhMuc.getStatusCode())
                .as(
                        "⛔ Không có endpoint này thì màn hình nhập buộc phải gọi đường quản trị, và cách "
                                + "chữa duy nhất còn lại là cấp quyền quản trị danh mục cho toàn bộ người "
                                + "trực ban — tức là nới quyền để giao diện chạy được. %s",
                        danhMuc.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(danhMuc.getBody()).contains(MA_DONG_KIN);

        assertThat(phienHttp.get(trucBanA, "/api/v1/ops/operation-status-codes").getStatusCode())
                .as("trực ban vẫn KHÔNG quản trị được danh mục")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<String> ghiNhanLuc(PhienHttp.Phien phien, UUID congTrinh, String ma, String hieuLucLuc) {
        String than =
                """
                {"items":[{"constructionPublicId":"%s","operationCode":"%s","effectiveAt":"%s"}]}"""
                        .formatted(congTrinh, ma, hieuLucLuc);
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/ops/operation-statuses/batch", than);
    }

    private ResponseEntity<String> ghiNhan(PhienHttp.Phien phien, UUID congTrinh, String ma) {
        String than =
                """
                {"items":[{"constructionPublicId":"%s","operationCode":"%s",
                           "note":"Kiểm thử đợt vá","effectiveAt":"2026-08-23T08:00:00+07:00"}]}"""
                        .formatted(congTrinh, ma);
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/ops/operation-statuses/batch", than);
    }

    private UUID taoCongTrinh(String ma, UUID donVi) {
        String than =
                """
                {"code":"%s","name":"Cống %s","constructionType":"CONG",
                 "orgUnitId":"%s","managementLevel":"XI_NGHIEP"}"""
                        .formatted(ma, ma, donVi);
        ResponseEntity<String> phanHoi = phienHttp.goi(quanLyCty, HttpMethod.POST, "/api/v1/ops/constructions", than);
        assertThat(phanHoi.getStatusCode())
                .as("dựng dữ liệu hỏng thì mọi bài kiểm bên dưới nói dối: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(PhienHttp.giaTriJson(phanHoi.getBody(), "publicId"));
    }

    private String trangThai(UUID congTrinh) {
        return jdbc.queryForObject(
                "SELECT operational_status FROM constructions WHERE public_id = ?", String.class, congTrinh);
    }

    private int soBanGhi(UUID congTrinh) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM construction_operation_status s
                JOIN constructions c ON c.id = s.construction_id
                WHERE c.public_id = ?
                """,
                Integer.class,
                congTrinh);
    }

    private long themDonVi(String ma, long parentId, String parentPath) {
        Long id = jdbc.queryForObject(
                """
                INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at)
                VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id
                """,
                Long.class,
                ma,
                "Xí nghiệp " + ma,
                parentId);
        String path = parentPath + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private UUID publicIdDonVi(long id) {
        return jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, id);
    }

    private void datDonVi(String username, long orgUnitId) {
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", orgUnitId, username);
    }

    private void donDepCongTrinh() {
        jdbc.update(
                """
                DELETE FROM construction_operation_status WHERE construction_id IN
                    (SELECT id FROM constructions WHERE code LIKE 'T19H-%')
                """);
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T19H-%'");
    }

    private void donDepDonVi() {
        donDepCongTrinh();
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T19-XN-%'");
    }
}
