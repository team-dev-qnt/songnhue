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
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.operations.application.MaintenanceLogService;

/**
 * Lịch sử sửa chữa và sự cố <b>đi qua HTTP</b> — T18.11, kiểm chứng của WS-18.
 *
 * <h2>Vì sao qua HTTP, không gọi thẳng service</h2>
 *
 * Ba cam kết của WS-18 chỉ tồn tại trên đường HTTP và <b>không</b> lộ ra khi gọi service:
 *
 * <ol>
 *   <li><b>Tách quyền hai đường tạo</b> nằm ở {@code @RequirePermission} của controller — gọi service
 *       thì đi vòng qua nó.
 *   <li><b>Quyền của từng bước chuyển</b> đọc {@code AuthContext}, thứ do bộ lọc HTTP đặt.
 *   <li><b>{@code created_by}</b> đọc {@code AuditContext}, cũng do bộ lọc đặt — mà cửa sổ tự sửa
 *       (T18.9) dựa hẳn vào cột đó.
 * </ol>
 *
 * <p>Bài học 391-bài-xanh-mà-mọi-màn-hình-500 của WS-20 áp thẳng vào đây.
 *
 * <h2>Ba vai trò thật, không dùng ADMIN cho tiện</h2>
 *
 * Ma trận §6 tách ba cột khác nhau ở đúng chức năng này, nên bài kiểm dùng đúng ba vai trò đó. Cấp
 * ADMIN cho mọi bài kiểm là cách chắc chắn nhất để một ô thiếu quyền không bao giờ lộ ra.
 */
// PER_CLASS để @BeforeAll không phải static — nó cần các bean được tiêm vào thực thể.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceLogHttpTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private SettingService settings;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;

    /** Kỹ thuật: ghi công trình + ghi bảo trì + ghi nhận sự cố. KHÔNG đóng được sự cố, không sửa. */
    private PhienHttp.Phien kyThuat;

    /** Quản lý XN: sửa, xoá, đóng bản ghi sự cố. */
    private PhienHttp.Phien quanLy;

    /** Cán bộ vận hành: CHỈ ghi nhận sự cố. */
    private PhienHttp.Phien vanHanh;

    /** Một Kỹ thuật thứ hai — để chứng minh cửa sổ tự sửa không mở cho người khác. */
    private PhienHttp.Phien kyThuatKhac;

    private UUID donViGoc;
    private UUID congTrinh;

    /**
     * ⚠⚠ Đăng nhập <b>một lần cho cả lớp</b> — bốn tài khoản, không phải bốn × số bài kiểm.
     *
     * <p>Bản đầu đăng nhập trong {@code @BeforeEach} và <b>14/22 bài đỏ</b> với {@code SYS-0002 · 429}
     * ngay lượt chạy đầu tiên. Hạn mức đăng nhập là 30 lượt / 15 phút <b>theo IP</b>, mọi bài kiểm
     * HTTP đều đi từ {@code 127.0.0.1}, và bộ đếm Caffeine dùng chung cho toàn bộ lượt chạy — lớp
     * này một mình đã xin 66 vé.
     *
     * <p>📌 Đây là ngân sách <b>dùng chung và hữu hạn</b> giữa mọi lớp kiểm thử HTTP, đã ghi ở
     * {@code docs/coding-guide.md} §4. Lớp nào thêm sau cũng phải đăng nhập ở {@code @BeforeAll},
     * nếu không nó sẽ làm đỏ một lớp <i>khác</i>.
     */
    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_kythuat", "TECHNICIAN"));
        quanLy = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_quanly", "XN_MANAGER"));
        vanHanh = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_vanhanh", "XN_OPERATOR"));
        kyThuatKhac = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_kythuat2", "TECHNICIAN"));
    }

    @BeforeEach
    void setUp() {
        donDep();
        congTrinh = UUID.fromString(PhienHttp.giaTriJson(
                phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/constructions", thanCongTrinh("T18H-001"))
                        .getBody(),
                "publicId"));
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // === Kiểm chứng chính của WS-18 ==========================================

    @Test
    @DisplayName("⭐⭐ Ghi một sự cố → công trình mang cờ đỏ; đóng bản ghi → tự trả về Bình thường")
    void incidentDrivesConstructionStatusBothWays() {
        assertThat(trangThaiCongTrinh()).isEqualTo("BINH_THUONG");

        String id = taoSuCo("CAO", "Vỡ ống đẩy tổ máy 2");

        assertThat(trangThaiCongTrinh())
                .as("⛔ Đây là toàn bộ lý do WS-18 tồn tại: cờ đỏ trên bản đồ điều hành phải CÓ HỒ SƠ "
                        + "truy vết, không phải một cột ai đó sửa tay")
                .isEqualTo("SU_CO");

        // Quản lý XN đóng bản ghi — kèm ngày hoàn thành, nếu không thì OPS-2004.
        ResponseEntity<String> dong = bam(quanLy, id, "RESOLVE", "2026-08-05");
        assertThat(dong.getStatusCode()).as("%s", dong.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(trangThaiCongTrinh())
                .as("đóng bản ghi cuối cùng → tự trả về trạng thái trước đó, không kẹt ở đỏ")
                .isEqualTo("BINH_THUONG");
    }

    @Test
    @DisplayName("⭐ Sự cố đứng TRÊN bảo trì: vừa hỏng vừa đang sửa thì người trực phải thấy cờ đỏ")
    void incidentOutranksMaintenance() {
        taoBaoTri("Thay dầu tổ máy 1", null);
        assertThat(trangThaiCongTrinh()).isEqualTo("BAO_TRI");

        taoSuCo("NGHIEM_TRONG", "Sạt mái kênh thượng lưu");
        assertThat(trangThaiCongTrinh())
                .as("thứ tự ưu tiên CN-02.1: sự cố (1) trước bảo trì (2)")
                .isEqualTo("SU_CO");
    }

    @Test
    @DisplayName("Xoá bản ghi sự cố đang mở → cờ đỏ tắt, không kẹt lại vĩnh viễn")
    void deletingTheOpenIncidentClearsTheFlag() {
        String id = taoSuCo("THAP", "Rò rỉ nhẹ cánh van");
        assertThat(trangThaiCongTrinh()).isEqualTo("SU_CO");

        ResponseEntity<String> xoa =
                phienHttp.goi(quanLy, HttpMethod.DELETE, "/api/v1/ops/maintenance-logs/" + id, null);
        assertThat(xoa.getStatusCode()).as("%s", xoa.getBody()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(trangThaiCongTrinh()).isEqualTo("BINH_THUONG");
    }

    // === Tách quyền — ma trận §6 =============================================

    @Test
    @DisplayName("⛔ Kỹ thuật KHÔNG đóng được bản ghi sự cố (§6), nhưng Quản lý XN thì được")
    void onlyManagerCanCloseAnIncident() {
        String id = taoSuCo("TRUNG_BINH", "Kẹt cánh van khoang 3");

        ResponseEntity<String> kyThuatDong = bam(kyThuat, id, "RESOLVE", "2026-08-05");
        assertThat(kyThuatDong.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(kyThuatDong.getBody())
                .as("luật này nằm ở workflow_transitions.required_permission, không ở câu if nào")
                .contains("AUTH-3001");
        assertThat(trangThaiCongTrinh())
                .as("lượt bị từ chối không được để lại dấu vết nào")
                .isEqualTo("SU_CO");

        assertThat(bam(quanLy, id, "RESOLVE", "2026-08-05").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Kỹ thuật tiếp nhận sự cố được (START) — §6 chỉ cấm TUYÊN BỐ đã xong")
    void technicianMayStartWorkingOnAnIncident() {
        String id = taoSuCo("CAO", "Mất pha tủ điện");

        ResponseEntity<String> batDau = bam(kyThuat, id, "START", null);

        assertThat(batDau.getStatusCode()).as("%s", batDau.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(batDau.getBody()).contains("DANG_XU_LY");
        assertThat(trangThaiCongTrinh())
                .as("đang xử lý vẫn là đang mở → vẫn đỏ")
                .isEqualTo("SU_CO");
    }

    @Test
    @DisplayName("⛔ Cán bộ vận hành chỉ ghi nhận sự cố — đường ghi bảo trì trả 403")
    void operatorMayOnlyReportIncidents() {
        ResponseEntity<String> baoTri = phienHttp.goi(
                vanHanh, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri("Thử ghi bảo trì", null));
        assertThat(baoTri.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> suCo = phienHttp.goi(
                vanHanh, HttpMethod.POST, "/api/v1/ops/maintenance-logs/incidents", thanSuCo("CAO", "Nước tràn bờ"));
        assertThat(suCo.getStatusCode()).as("%s", suCo.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("⛔⛔ Đường /incidents ÉP loại — gửi BAO_TRI_DINH_KY vào đó vẫn ra Khắc phục sự cố")
    void theIncidentEndpointForcesTheWorkType() {
        String than = thanSuCo("CAO", "Payload cố tình khai sai loại")
                .replace("\"workType\":\"KHAC_PHUC_SU_CO\"", "\"workType\":\"BAO_TRI_DINH_KY\"");

        ResponseEntity<String> tao =
                phienHttp.goi(vanHanh, HttpMethod.POST, "/api/v1/ops/maintenance-logs/incidents", than);

        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(tao.getBody())
                .as("tin workType của payload ở đường này thì quyền report-incident mở luôn cả đường "
                        + "ghi bảo trì, và ops:maintenance:create thành thứ trang trí")
                .contains("\"workType\":\"KHAC_PHUC_SU_CO\"");
    }

    @Test
    @DisplayName("⛔ Nhập thẳng sự cố ở 'Đã xử lý' cũng đòi close-incident — không lách bằng cách tạo mới")
    void loggingAResolvedIncidentNeedsTheClosingPermission() {
        String than = than("KHAC_PHUC_SU_CO", "\"THAP\"", "Đã khắc phục xong từ tuần trước", "DA_XU_LY", "2026-08-03");

        ResponseEntity<String> cuaKyThuat =
                phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", than);
        assertThat(cuaKyThuat.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cuaKyThuat.getBody()).contains("AUTH-3001");

        ResponseEntity<String> cuaQuanLy = phienHttp.goi(quanLy, HttpMethod.POST, "/api/v1/ops/maintenance-logs", than);
        assertThat(cuaQuanLy.getStatusCode()).as("%s", cuaQuanLy.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(trangThaiCongTrinh())
                .as("bản ghi vào đời ở trạng thái đã đóng thì không có gì đang mở")
                .isEqualTo("BINH_THUONG");
    }

    @Test
    @DisplayName("⭐ Nhập công việc bảo trì đã hoàn thành → vào thẳng DA_XU_LY, KHÔNG qua transition giả")
    void completedWorkEntersDirectlyAtDone() {
        String than = than("BAO_TRI_DINH_KY", "null", "Đã bảo trì xong tuần trước", "DA_XU_LY", "2026-08-03");

        ResponseEntity<String> tao = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", than);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(tao.getBody()).contains("\"status\":\"DA_XU_LY\"");

        Long idNoiBo = jdbc.queryForObject(
                "SELECT id FROM maintenance_logs WHERE code = ?",
                Long.class,
                PhienHttp.giaTriJson(tao.getBody(), "code"));
        Long soBuocChuyen = jdbc.queryForObject(
                """
                SELECT count(*) FROM audit_logs
                WHERE module = 'ops' AND entity_type = 'Lịch sử sửa chữa'
                  AND entity_id = ? AND action <> 'CREATE'
                """,
                Long.class,
                idNoiBo);
        assertThat(soBuocChuyen)
                .as("⛔ điểm nghiệp vụ 15: tạo ở MOI rồi chạy transition cho tới DA_XU_LY là ký tên "
                        + "vào một lịch sử chưa từng xảy ra — nhật ký này có chuỗi băm")
                .isZero();
    }

    // === Quy tắc nghiệp vụ — bốn mã lỗi đã có sẵn trong catalog ==============

    @Test
    @DisplayName("⛔ Chuyển sang 'Đã xử lý' khi chưa có ngày hoàn thành → OPS-2004, và trạng thái KHÔNG đổi")
    void closingWithoutACompletionDateIsRejectedAndRolledBack() {
        String id = taoSuCo("CAO", "Chưa điền ngày hoàn thành");

        ResponseEntity<String> dong = bam(quanLy, id, "RESOLVE", null);

        assertThat(dong.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(dong.getBody()).contains("OPS-2004");

        ResponseEntity<String> chiTiet = phienHttp.get(quanLy, "/api/v1/ops/maintenance-logs/" + id);
        assertThat(chiTiet.getBody())
                .as("⚠ engine đã chuyển trạng thái TRƯỚC khi bị chặn — nếu giao dịch không quay lui thì "
                        + "bản ghi kẹt ở DA_XU_LY mà không có ngày hoàn thành, đúng thứ CHECK của CSDL cấm")
                .contains("\"status\":\"MOI\"");
        assertThat(trangThaiCongTrinh()).isEqualTo("SU_CO");
    }

    @Test
    @DisplayName("⛔ Mức độ đi cùng chiều với loại — thiếu và thừa đều là OPS-2003")
    void severityIsBoundToTheIncidentType() {
        String thieuMucDo = thanSuCo("CAO", "Thiếu mức độ").replace("\"severity\":\"CAO\"", "\"severity\":null");
        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thieuMucDo)
                        .getBody())
                .contains("OPS-2003");

        String baoTriCoMucDo = thanBaoTri("Bảo trì mà mang mức độ", null)
                .replace("\"severity\":null", "\"severity\":\"NGHIEM_TRONG\"");
        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", baoTriCoMucDo)
                        .getBody())
                .as("chiều thứ hai không thừa: một bản ghi bảo trì mang mức độ 'Nghiêm trọng' sẽ hiện "
                        + "trong mọi bộ lọc theo mức độ và bị đọc như một sự cố")
                .contains("OPS-2003");
    }

    @Test
    @DisplayName("⛔ Đơn vị thực hiện: cả hai hoặc không cái nào đều là OPS-2017 (điểm nghiệp vụ 17)")
    void performerMustBeExactlyOneOfTwoColumns() {
        String caHai = thanBaoTri("Khai cả hai", null)
                .replace("\"performerOrgUnitId\":null", "\"performerOrgUnitId\":\"" + donViGoc + "\"");
        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", caHai)
                        .getBody())
                .contains("OPS-2017");

        String khongCaiNao = thanBaoTri("Không khai gì", null)
                .replace("\"performerName\":\"Tổ kỹ thuật Xí nghiệp\"", "\"performerName\":null");
        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", khongCaiNao)
                        .getBody())
                .contains("OPS-2017");
    }

    @Test
    @DisplayName("⛔ Ngày hoàn thành trước ngày bắt đầu → OPS-2001")
    void completionCannotPrecedeStart() {
        String than = thanBaoTri("Ngày ngược", "2026-07-01");

        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", than)
                        .getBody())
                .contains("OPS-2001");
    }

    @Test
    @DisplayName("⛔ Công trình đã thanh lý không nhận công việc mới → OPS-2002")
    void decommissionedConstructionsRejectNewWork() {
        phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                "/api/v1/ops/constructions/" + congTrinh + "/lifecycle",
                "{\"state\":\"DA_THANH_LY\",\"reason\":\"hư hỏng không sửa được\"}");

        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri("Sửa tiếp", null))
                        .getBody())
                .contains("OPS-2002");
    }

    // === Chi phí, mã bản ghi, tệp đính kèm ===================================

    @Test
    @DisplayName("⭐ Tổng chi phí cộng ở CSDL bằng NUMERIC — ba số lẻ không sinh sai số")
    void costIsSummedInTheDatabaseWithoutFloatingPointDrift() {
        taoBaoTriCoChiPhi("Sửa lần 1", "1500000.10");
        taoBaoTriCoChiPhi("Sửa lần 2", "2300000.20");
        taoBaoTriCoChiPhi("Sửa lần 3", "999999.70");

        ResponseEntity<String> tong =
                phienHttp.get(kyThuat, "/api/v1/ops/maintenance-logs/cost-summary?constructionId=" + congTrinh);

        assertThat(tong.getStatusCode()).as("%s", tong.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(tong.getBody())
                .as("0.10 + 0.20 + 0.70 = 1.00 chẵn. Cộng bằng Number của JavaScript ra 1.0000000000000002 "
                        + "— quy tắc 3 tồn tại vì đúng chuyện này")
                .contains("\"total\":4800000.00")
                .contains("\"recordCount\":3");
    }

    @Test
    @DisplayName("Tổng chi phí của kỳ RỖNG trả null, không trả 0 — hai câu khác nhau")
    void anEmptyPeriodReturnsNullNotZero() {
        ResponseEntity<String> tong = phienHttp.get(
                kyThuat,
                "/api/v1/ops/maintenance-logs/cost-summary?constructionId=" + congTrinh
                        + "&from=2020-01-01&to=2020-12-31");

        assertThat(tong.getBody())
                .as("'chưa ai điền chi phí' và 'đã làm mà không tốn tiền' là hai câu khác nhau; trên một "
                        + "bảng quyết toán, chọn nhầm câu là đưa ra một con số không có thật")
                .contains("\"total\":null");
    }

    @Test
    @DisplayName("Mã bản ghi chạy số theo năm — BT-<năm>-xxxx (T18.5)")
    void codeFollowsTheYearlySequence() {
        String than = phienHttp
                .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri("Sinh mã", null))
                .getBody();

        assertThat(PhienHttp.giaTriJson(than, "code")).matches("BT-\\d{4}-\\d{4,}");
    }

    // === Cửa sổ người nhập tự sửa — T18.9 ====================================

    @Test
    @DisplayName("⭐ Cửa sổ tự sửa mặc định TẮT → Kỹ thuật không sửa được bản ghi của chính mình")
    void theAuthorEditWindowIsOffByDefault() {
        String id = PhienHttp.giaTriJson(
                phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri("Bản gốc", null))
                        .getBody(),
                "id");

        ResponseEntity<String> sua = phienHttp.goi(
                kyThuat, HttpMethod.PUT, "/api/v1/ops/maintenance-logs/" + id, thanBaoTri("Sửa lại nội dung", null));

        assertThat(sua.getStatusCode())
                .as("mặc định 0 phút = đúng ma trận §6: sửa bản ghi đã lưu là việc của Admin + Quản lý XN")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⭐⭐ Bật cửa sổ lên thì hành vi ĐỔI THEO — công tắc này có người đọc thật")
    void turningTheWindowOnChangesBehaviour() {
        String id = PhienHttp.giaTriJson(
                phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri("Bản gốc", null))
                        .getBody(),
                "id");

        // ⚠ Khẳng định "đổi tham số thì hành vi đổi theo", không phải "mã đọc được một con số nào đó".
        //   Dự án đã có ba tham số bày ra màn hình cấu hình mà không dòng mã nào đọc.
        // ⚠ Đổi qua SettingService chứ không UPDATE thẳng CSDL: tham số nằm sau một lớp đệm
        //   Caffeine, nên câu SQL trần đổi được cột mà KHÔNG đổi được hành vi — bài kiểm sẽ đỏ vì
        //   một lý do không liên quan tới thứ nó định kiểm.
        settings.update(MaintenanceLogService.KEY_AUTHOR_EDIT_WINDOW, "60");
        try {
            ResponseEntity<String> sua = phienHttp.goi(
                    kyThuat,
                    HttpMethod.PUT,
                    "/api/v1/ops/maintenance-logs/" + id,
                    thanBaoTri("Sửa lại nội dung", null));

            assertThat(sua.getStatusCode()).as("%s", sua.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(sua.getBody()).contains("Sửa lại nội dung");
        } finally {
            settings.update(MaintenanceLogService.KEY_AUTHOR_EDIT_WINDOW, "0");
        }
    }

    @Test
    @DisplayName("⛔ Cửa sổ tự sửa không mở cho NGƯỜI KHÁC, kể cả khi đang bật")
    void theWindowIsForTheAuthorOnly() {
        String id = PhienHttp.giaTriJson(
                phienHttp
                        .goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri("Bản gốc", null))
                        .getBody(),
                "id");

        // ⚠ Đổi qua SettingService chứ không UPDATE thẳng CSDL: tham số nằm sau một lớp đệm
        //   Caffeine, nên câu SQL trần đổi được cột mà KHÔNG đổi được hành vi — bài kiểm sẽ đỏ vì
        //   một lý do không liên quan tới thứ nó định kiểm.
        settings.update(MaintenanceLogService.KEY_AUTHOR_EDIT_WINDOW, "60");
        try {
            ResponseEntity<String> sua = phienHttp.goi(
                    kyThuatKhac,
                    HttpMethod.PUT,
                    "/api/v1/ops/maintenance-logs/" + id,
                    thanBaoTri("Người khác sửa", null));

            assertThat(sua.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            settings.update(MaintenanceLogService.KEY_AUTHOR_EDIT_WINDOW, "0");
        }
    }

    // === Dashboard — trả nợ hai ô KPI của WS-23 ==============================

    @Test
    @DisplayName("⭐ Hai ô KPI 'Sự cố chưa xử lý' và 'Bảo trì đang thực hiện' nay có SỐ THẬT")
    void theTwoDashboardCellsAreNoLongerUnavailable() {
        taoSuCo("CAO", "Sự cố cho dashboard");
        taoBaoTri("Bảo trì cho dashboard", null);

        String dashboard = phienHttp.get(kyThuat, "/api/v1/ops/dashboard").getBody();

        assertThat(dashboard)
                .as("WS-23 để hai ô này null kèm lý do 'WS-18 (CN-02.2)'. WS-18 xong thì lý do đó phải "
                        + "biến mất — một ô vĩnh viễn 'chưa có dữ liệu' là một ô không ai còn nhìn")
                .doesNotContain("WS-18 (CN-02.2)");
        assertThat(dashboard)
                .containsPattern("\"key\":\"incident.open\",\"label\":\"[^\"]+\",\"value\":\\d+")
                .containsPattern("\"key\":\"maintenance.in-progress\",\"label\":\"[^\"]+\",\"value\":\\d+");
        // ⚠⚠ Khẳng định ở đây đã bị ĐẢO ở T35.3 (04/09/2026), ⛔ không phải xoá cho hết đỏ.
        //
        // Bản cũ: `.contains("Phase 2 (MOD-03)")` — "hai ô thuỷ văn thì VẪN chưa có nguồn". Câu ấy
        // đúng ở WS-18 và sai từ WS-35: hai ô ấy nay đọc `hydro.spi`. Giữ nguyên là để một bài kiểm
        // đóng đinh trạng thái CŨ, và nó sẽ chặn đúng lượt sửa làm cho đúng.
        //
        // ⭐ Giữ lại vế đối xứng — vế mà bài này thật sự sinh ra để canh: ⛔ KHÔNG ô nào của dashboard
        //    còn hẹn một hạng mục tương lai. Đó là bất biến bền, ⛔ không phải một tên hạng mục cụ
        //    thể sẽ hết hạn ở lượt sau.
        assertThat(dashboard)
                .as("⛔ mọi ô KPI đều đã có nguồn — không ô nào còn hẹn 'sẽ có ở <hạng mục>'")
                .doesNotContain("Phase 2 (MOD-03)")
                .doesNotContain("\"availableIn\":\"");
    }

    @Test
    @DisplayName("Danh sách 'Sự cố chưa xử lý' xếp nặng nhất lên trước (T18.8)")
    void openIncidentsAreOrderedBySeverity() {
        taoSuCo("THAP", "Sự cố nhẹ");
        taoSuCo("NGHIEM_TRONG", "Sự cố nặng");

        String danhSach = phienHttp
                .get(kyThuat, "/api/v1/ops/maintenance-logs/open-incidents")
                .getBody();

        assertThat(danhSach).isNotNull();
        assertThat(danhSach.indexOf("Sự cố nặng"))
                .as("đây là danh sách VIỆC PHẢI LÀM, không phải kho lưu trữ")
                .isLessThan(danhSach.indexOf("Sự cố nhẹ"));
    }

    // -------------------------------------------------------------------------

    private String taoSuCo(String mucDo, String noiDung) {
        ResponseEntity<String> tao = phienHttp.goi(
                kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs/incidents", thanSuCo(mucDo, noiDung));
        assertThat(tao.getStatusCode()).as("tạo sự cố: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return PhienHttp.giaTriJson(tao.getBody(), "id");
    }

    private String taoBaoTri(String noiDung, String ngayHoanThanh) {
        ResponseEntity<String> tao = phienHttp.goi(
                kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", thanBaoTri(noiDung, ngayHoanThanh));
        assertThat(tao.getStatusCode()).as("tạo bảo trì: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return PhienHttp.giaTriJson(tao.getBody(), "id");
    }

    private void taoBaoTriCoChiPhi(String noiDung, String chiPhi) {
        String than = thanBaoTri(noiDung, null).replace("\"cost\":null", "\"cost\":" + chiPhi);
        ResponseEntity<String> tao = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/ops/maintenance-logs", than);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<String> bam(PhienHttp.Phien phien, String id, String action, String ngayHoanThanh) {
        String than = """
            {"action":"%s","completedOn":%s,"note":null}"""
                .formatted(action, ngayHoanThanh == null ? "null" : "\"" + ngayHoanThanh + "\"");
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/ops/maintenance-logs/" + id + "/actions", than);
    }

    /** Trạng thái vận hành hiện tại của công trình — đọc qua HTTP, đúng đường mà bản đồ đi. */
    private String trangThaiCongTrinh() {
        String body =
                phienHttp.get(kyThuat, "/api/v1/ops/constructions/" + congTrinh).getBody();
        return PhienHttp.giaTriJson(body, "operationalStatus");
    }

    private String thanCongTrinh(String ma) {
        return """
            {"code":"%s","name":"Trạm bơm kiểm thử WS-18","constructionType":"TRAM_BOM",
             "orgUnitId":"%s","managementLevel":"XI_NGHIEP"}"""
                .formatted(ma, donViGoc);
    }

    private String thanSuCo(String mucDo, String noiDung) {
        return than("KHAC_PHUC_SU_CO", quote(mucDo), noiDung, null, null);
    }

    private String thanBaoTri(String noiDung, String ngayHoanThanh) {
        return than("BAO_TRI_DINH_KY", "null", noiDung, null, ngayHoanThanh);
    }

    /**
     * ⚠ Dựng thân JSON bằng tham số chứ không bằng {@code replace} chồng lên bản mặc định.
     *
     * <p>Bản đầu làm theo lối thay chuỗi và dính bẫy ngay: chèn {@code "completedOn"} vào chỗ
     * {@code "initialState"} để lại <b>hai</b> khoá cùng tên trong một đối tượng JSON, Jackson lấy
     * khoá sau — tức là giá trị mặc định {@code null} — và bài kiểm nhận {@code OPS-2004} thay vì thứ
     * nó định kiểm. Một thân JSON sai âm thầm còn tệ hơn một bài kiểm đỏ.
     */
    private String than(String loai, String mucDo, String noiDung, String trangThaiDau, String ngayHoanThanh) {
        return """
            {"constructionId":"%s","workType":"%s","severity":%s,"initialState":%s,
             "startedOn":"2026-08-01","completedOn":%s,"content":"%s",
             "itemOrEquipment":"Tổ máy số 2","performerOrgUnitId":null,
             "performerName":"Tổ kỹ thuật Xí nghiệp","cost":null,"assigneeUserId":null}"""
                .formatted(congTrinh, loai, mucDo, quote(trangThaiDau), quote(ngayHoanThanh), noiDung);
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private void donDep() {
        jdbc.update(
                """
                DELETE FROM maintenance_logs WHERE construction_id IN
                    (SELECT id FROM constructions WHERE code LIKE 'T18H-%')
                """);
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T18H-%'");
    }
}
