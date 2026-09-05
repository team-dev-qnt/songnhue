package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * ⭐⭐ Liên kết điểm đo ↔ công trình <b>đi qua HTTP</b> — T28.19, đóng 03/09/2026.
 *
 * <h2>Vì sao lớp này là lớp quan trọng nhất của đợt</h2>
 *
 * <p>Bảng {@code station_constructions} có đủ lược đồ, entity, repository, 4 chỉ mục, một ràng buộc
 * {@code is_primary} và một mã lỗi {@code HYD-2005} riêng — từ 31/08. Và ⛔ <b>không một dòng mã nào
 * tạo được một hàng</b>. Nghĩa là cả cụm cơ chế ấy chưa từng có ai đi qua, nên chưa ai biết nó đúng
 * hay sai (luật 7). {@code HYD-2005} là ví dụ thuần khiết: mã lỗi seed đủ ba tệp, ⛔ không lượt chạy
 * nào chạm tới được.
 *
 * <p>⇒ Bài kiểm ĐẦU TIÊN của đường ghi này phải là bài <b>làm cho {@code HYD-2005} bắn ra thật</b>,
 * ⛔ không phải bài chứng minh đường hạnh phúc chạy được.
 *
 * <p>⚠ Và nó là mắt xích chặn của WS-33: {@code HydroAlertPort.hasActiveAlert} nhận
 * {@code constructionId} trong khi cảnh báo gắn với {@code station_id} — cầu nối là đúng bảng này.
 * Thay {@code DummyHydroAlertService} bằng bản thật trong khi bảng rỗng thì nó trả {@code false} y
 * hệt bản cũ, và sổ được tick (luật 19).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StationConstructionLinkHttpTest extends IntegrationTestBase {

    private static final String MA_CT = "T2819-CT-01";
    private static final String MA_CT_HAI = "T2819-CT-02";
    private static final String MA_API = "F97140";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;

    /** ⚠ Vai trò DUY NHẤT ngoài SA/ADMIN mang {@code hyd:station:manage} — và nó có `ops:construction:view`. */
    private PhienHttp.Phien kyThuat;

    private UUID diemDo;
    private UUID congTrinh;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t2819_kythuat", "TECHNICIAN"));
        donDep();
        diemDo = taoDiemDoThuongLuu();
        congTrinh = taoCongTrinh(MA_CT, "Cống kiểm thử T28.19");
    }

    /**
     * ⚠⚠ BẮT BUỘC. {@code HydroQualityHttpTest} khẳng định đúng <b>19</b> điểm đo ở chỗ dọn của nó,
     * và thứ tự chạy của surefire phụ thuộc hệ tệp — để lại một điểm đo thứ 20 là làm đỏ một lớp
     * khác, ở một môi trường khác, vì một lý do không liên quan (§10.65 họ hàng: ở máy thì xanh).
     */
    @AfterAll
    void donDepCuoiLop() {
        donDep();
    }

    @AfterEach
    void xoaLienKet() {
        jdbc.update(
                "DELETE FROM station_constructions WHERE station_id IN (SELECT id FROM stations WHERE api_code = ?)",
                MA_API);
    }

    // === ⭐⭐ Mã lỗi HYD-2005 — lần đầu tiên trong lịch sử dự án bắn ra thật ====

    @Test
    @DisplayName("⭐⭐ Liên kết CHÍNH mang vai trò khác vai trò điểm đo → HYD-2005, ⛔ không ghi dòng nào")
    void aPrimaryLinkWithAMismatchedRoleIsRejected() {
        ResponseEntity<String> phanHoi = lienKet(diemDo, congTrinh, "HA_LUU", true);

        assertThat(phanHoi.getBody())
                .as(
                        """
                        ⛔ Điểm đo khai là THƯỢNG LƯU, liên kết chính khai là HẠ LƯU. Không có chốt chặn \
                        này thì `stations.position_role` và vai trò của liên kết chính nói hai điều khác \
                        nhau về cùng một điểm đo, và biểu tổng hợp theo tuyến sông xếp nó vào NHẦM CỘT — \
                        im lặng, vì cả hai giá trị đều hợp lệ khi nhìn riêng. CSDL ⛔ không ép được ràng \
                        buộc liên bảng này (chỉ ép nổi "mỗi điểm đo tối đa một liên kết chính"), nên nó \
                        phải nằm ở service. Mã HYD-2005 seed đủ ba tệp từ 31/08 và tới hôm nay ⛔ chưa \
                        lượt chạy nào chạm tới được — đây là lượt đầu tiên (luật 7).""")
                .contains("HYD-2005");
        assertThat(soLienKet()).isZero();
    }

    @Test
    @DisplayName("⭐ Liên kết PHỤ mang vai trò khác thì HỢP LỆ — một điểm đo là HL cống này, TL cống kế tiếp")
    void aNonPrimaryLinkMayCarryADifferentRole() {
        assertThat(lienKet(diemDo, congTrinh, "HA_LUU", false).getStatusCode())
                .as(
                        """
                        ⛔ Ràng buộc vai trò chỉ áp cho liên kết CHÍNH. Trên một tuyến sông, cùng một \
                        điểm đo là hạ lưu của cống này ĐỒNG THỜI là thượng lưu của cống kế tiếp — đó là \
                        cách bố trí có thật, không phải dữ liệu sai. Ép vai trò cho mọi liên kết là làm \
                        hệ thống không mô tả nổi tuyến sông thật.""")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(soLienKet()).isEqualTo(1);
    }

    // === Vòng nhập → lưu → đọc lại ===========================================

    @Test
    @DisplayName("⭐⭐ Khai liên kết chính đúng vai trò → 201, và điểm đo ĐỌC LẠI thấy tên công trình")
    void aValidPrimaryLinkIsWrittenAndReadBackWithTheConstructionName() {
        ResponseEntity<String> tao = lienKet(diemDo, congTrinh, "THUONG_LUU", true);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        String hoSo = phienHttp.get(kyThuat, "/api/v1/hyd/stations/" + diemDo).getBody();

        assertThat(hoSo)
                .as(
                        """
                        ⚠ Nửa ĐỌC thứ hai của cùng cơ chế: trước 03/09 `StationConstructionView` chỉ mang \
                        `constructionId` — một UUID 36 ký tự — nên màn hình có dữ liệu mà người đọc ⛔ \
                        không dùng được. Cùng hình dạng T27.24 ở chiều đọc. Thân thật: %s""",
                        hoSo)
                .contains(MA_CT)
                .contains("Cống kiểm thử T28.19")
                .contains("\"primary\":true");
        assertThat(soLienKet()).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Khai liên kết chính THỨ HAI → liên kết chính cũ tự hạ xuống phụ, ⛔ không đâm ràng buộc")
    void promotingASecondPrimaryDemotesTheFirst() {
        UUID congTrinhHai = taoCongTrinh(MA_CT_HAI, "Cống kiểm thử T28.19 hai");

        assertThat(lienKet(diemDo, congTrinh, "THUONG_LUU", true).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(lienKet(diemDo, congTrinhHai, "THUONG_LUU", true).getStatusCode())
                .as(
                        """
                        ⛔ Chỉ mục một phần `ux_station_constructions_mot_ban_ghi_chinh` sẽ ném \
                        DataIntegrityViolation ⇒ 500, và người dùng phải TỰ ĐOÁN rằng mình cần bỏ tick ở \
                        dòng kia trước. "Đổi liên kết chính" là MỘT thao tác trong đầu người vận hành, nên \
                        nó phải là một giao dịch ở service.""")
                .isEqualTo(HttpStatus.CREATED);

        assertThat(soLienKetChinh())
                .as("và sau lượt đổi vẫn phải còn ĐÚNG MỘT liên kết chính — con số này là bất biến của bảng")
                .isEqualTo(1);
        assertThat(soLienKet()).isEqualTo(2);
    }

    @Test
    @DisplayName("⛔ Khai lại đúng cặp (điểm đo, công trình, vai trò) → HYD-2008, ⛔ không phải 500")
    void aDuplicateLinkIsRejectedWithAReadableCode() {
        assertThat(lienKet(diemDo, congTrinh, "THUONG_LUU", false).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> lai = lienKet(diemDo, congTrinh, "THUONG_LUU", false);

        assertThat(lai.getStatusCode())
                .as(
                        """
                        ⚠ Chỉ mục một phần chỉ bắn lúc câu INSERT thật sự chạm CSDL. Với `save` thì flush \
                        rơi vào lúc COMMIT — tức là ngoài khối try — và người dùng nhận 500 cho một lỗi \
                        nhập liệu bình thường. `saveAndFlush` là thứ kéo lượt ném vào trong tầm bắt. %s""",
                        lai.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(lai.getBody()).contains("HYD-2008");
        assertThat(soLienKet()).isEqualTo(1);
    }

    // === Toàn vẹn: CSDL cố ý không có khoá ngoại nên service phải giữ =========

    @Test
    @DisplayName("⭐⭐ Công trình KHÔNG tồn tại → SYS-0004, ⛔ không lưu một con số trỏ vào khoảng không")
    void linkingToANonExistentConstructionIsRefused() {
        ResponseEntity<String> phanHoi = lienKet(diemDo, UUID.randomUUID(), "THUONG_LUU", false);

        assertThat(phanHoi.getBody())
                .as(
                        """
                        ⛔ `construction_id` KHÔNG có REFERENCES — hai module, §10.4. Đổi lại, toàn vẹn \
                        PHẢI do tầng dịch vụ giữ, và chính migration đã viết lời cảnh báo: không có bước \
                        này thì cột ấy là "một cột số trỏ vào khoảng không". Cổng \
                        ConstructionLookupPort được viện dẫn từ 31/08 mà tới 03/09 mới tồn tại — bài này \
                        là phép đo chứng minh nó đã được nối, không chỉ được khai.""")
                .contains("SYS-0004");
        assertThat(soLienKet()).isZero();
    }

    @Test
    @DisplayName("⛔ Công trình ĐÃ THANH LÝ không nhận liên kết mới")
    void aLiquidatedConstructionRefusesNewLinks() {
        jdbc.update("UPDATE constructions SET lifecycle_state = 'DA_THANH_LY' WHERE code = ?", MA_CT);
        try {
            assertThat(lienKet(diemDo, congTrinh, "THUONG_LUU", false).getBody())
                    .as("số đo gắn vào một công trình đã thanh lý sẽ không bao giờ hiện ở đâu")
                    .contains("OPS-2002");
            assertThat(soLienKet()).isZero();
        } finally {
            jdbc.update("UPDATE constructions SET lifecycle_state = 'DANG_HOAT_DONG' WHERE code = ?", MA_CT);
        }
    }

    @Test
    @DisplayName("Bỏ liên kết → 204, xoá MỀM (quy tắc 9) — dòng vẫn còn trong bảng")
    void removingALinkIsASoftDelete() {
        assertThat(lienKet(diemDo, congTrinh, "THUONG_LUU", false).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        UUID lienKetId = jdbc.queryForObject(
                """
                SELECT sc.public_id FROM station_constructions sc
                JOIN stations s ON s.id = sc.station_id
                WHERE s.api_code = ? AND sc.deleted_at IS NULL
                """,
                UUID.class,
                MA_API);

        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.DELETE, "/api/v1/hyd/stations/lien-ket/" + lienKetId, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(soLienKet()).as("đường đọc không còn thấy nó nữa").isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM station_constructions WHERE public_id = ?", Integer.class, lienKetId))
                .as("⛔ nhưng dòng vẫn nằm trong bảng — xoá mềm + audit, quy tắc 9")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<String> lienKet(UUID station, UUID construction, String vaiTro, boolean chinh) {
        String than = """
                {"constructionId":"%s","role":"%s","primary":%s}"""
                .formatted(construction, vaiTro, chinh);
        return phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/stations/" + station + "/lien-ket", than);
    }

    private int soLienKet() {
        Integer n = jdbc.queryForObject(
                """
                SELECT count(*) FROM station_constructions sc
                JOIN stations s ON s.id = sc.station_id
                WHERE s.api_code = ? AND sc.deleted_at IS NULL
                """,
                Integer.class,
                MA_API);
        return n == null ? 0 : n;
    }

    private int soLienKetChinh() {
        Integer n = jdbc.queryForObject(
                """
                SELECT count(*) FROM station_constructions sc
                JOIN stations s ON s.id = sc.station_id
                WHERE s.api_code = ? AND sc.deleted_at IS NULL AND sc.is_primary
                """,
                Integer.class,
                MA_API);
        return n == null ? 0 : n;
    }

    private UUID taoDiemDoThuongLuu() {
        String than =
                """
                {"code":"T2819-DIEMDO","name":"Điểm đo T28.19","apiCode":"%s","apiSourceId":"%s",
                 "positionRole":"THUONG_LUU","measurementTypeIds":["%s"]}"""
                        .formatted(
                                MA_API,
                                jdbc.queryForObject(
                                        "SELECT public_id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1",
                                        UUID.class),
                                jdbc.queryForObject(
                                        "SELECT public_id FROM measurement_types WHERE code = 'MUC_NUOC'", UUID.class));
        ResponseEntity<String> tao = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/stations", than);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return jdbc.queryForObject("SELECT public_id FROM stations WHERE api_code = ?", UUID.class, MA_API);
    }

    /**
     * ⚠ Chèn thẳng bằng SQL: bài kiểm này đo <b>đường liên kết</b>, ⛔ không đo đường tạo công trình.
     * Đi qua HTTP ở đây là buộc lượt đo phụ thuộc vào 20 trường bắt buộc của một hồ sơ công trình,
     * và một ngày nào đó nó sẽ đỏ vì một lý do chẳng liên quan gì tới liên kết.
     */
    private UUID taoCongTrinh(String ma, String ten) {
        return jdbc.queryForObject(
                """
                INSERT INTO constructions (code, name, construction_type, org_unit_id, management_level,
                                           lifecycle_state, operational_status, created_at)
                VALUES (?, ?, 'CONG', (SELECT id FROM org_units WHERE code = 'CTY'), 'CONG_TY',
                        'DANG_HOAT_DONG', 'BINH_THUONG', now())
                RETURNING public_id
                """,
                UUID.class,
                ma,
                ten);
    }

    private void donDep() {
        jdbc.update(
                "DELETE FROM station_constructions WHERE station_id IN (SELECT id FROM stations WHERE api_code = ?)",
                MA_API);
        jdbc.update(
                "DELETE FROM station_measurement_types WHERE station_id IN (SELECT id FROM stations WHERE api_code = ?)",
                MA_API);
        jdbc.update("DELETE FROM stations WHERE api_code = ?", MA_API);
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T2819-CT-%'");
    }
}
