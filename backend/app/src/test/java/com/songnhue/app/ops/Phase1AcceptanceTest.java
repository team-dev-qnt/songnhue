package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
 * Nghiệm thu Phase 1 — <b>ba mục Definition of Done chưa có phép kiểm nào</b>.
 *
 * <h2>Vì sao ba mục này, và vì sao bây giờ</h2>
 *
 * <p>Danh sách 17 mục DoD của Phase 1 được ghi từ đầu Phase, và ngày 22/8 WS-22 tuyên bố *"chạy tay
 * lại mọi thứ đã tick"*. Lượt rà độc lập ngày 23/8 đối chiếu từng mục với bộ kiểm thử thật tìm ra
 * ba mục <b>không có bất kỳ bài kiểm nào</b> đứng sau:
 *
 * <ul>
 *   <li><b>DOD1.6</b> — API công khai không lộ bài chưa xuất bản.
 *   <li><b>DOD1.7</b> — đính kèm đầu-cuối <i>qua HTTP</i> (bài hiện có gọi thẳng service).
 *   <li><b>DOD1.11</b> — thêm mã tình hình vận hành mới <b>không cần deploy</b> (quy tắc 16).
 * </ul>
 *
 * <p>Mỗi mục ở đây là một cam kết với Công ty, không phải một chi tiết kỹ thuật: mục 6 là rò rỉ nội
 * dung chưa duyệt ra cổng công khai, mục 7 là hồ sơ công trình có tải lên mà không tải về được, mục
 * 11 là lời hứa "thêm mã mới không phải gọi nhà thầu".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase1AcceptanceTest extends IntegrationTestBase {

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
    private PhienHttp.Phien bienTap;

    private UUID donViGoc;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        donDep();
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "dod_kythuat", "TECHNICIAN"));
        bienTap = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "dod_bientap", "CONTENT_EDITOR"));
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // === DOD1.6 — cổng công khai không lộ bài chưa xuất bản ===================

    @Test
    @DisplayName("⭐⭐ DOD1.6 — bài nháp KHÔNG lọt ra API công khai, kể cả khi biết đúng slug")
    void draftArticlesNeverReachThePublicApi() {
        String slug = "bai-nhap-nghiem-thu-" + System.nanoTime();
        String danhMuc = layMotDanhMuc();
        assertThat(danhMuc)
                .as("môi trường chưa có danh mục nào để gắn bài — không dựng được dữ liệu thì bài "
                        + "kiểm này phải NÓI RA, không được xanh trên tập rỗng")
                .isNotNull();

        ResponseEntity<String> tao = phienHttp.goi(
                bienTap,
                HttpMethod.POST,
                "/api/v1/cms/articles",
                """
                {"title":"Bài nháp nghiệm thu","slug":"%s","summary":"tóm tắt",
                 "content":"<p>Nội dung chưa duyệt</p>","categoryPublicIds":["%s"]}"""
                        .formatted(slug, danhMuc));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        // ⛔ Đoán trúng slug là chuyện dễ: slug sinh từ tiêu đề, và tiêu đề thường đoán được.
        //    Nên phép chặn không được dựa vào việc "không ai biết đường dẫn".
        ResponseEntity<String> congKhai = http.getForEntity("/api/v1/public/articles/" + slug, String.class);
        assertThat(congKhai.getStatusCode())
                .as("bài chưa xuất bản mà cổng công khai trả về là rò rỉ nội dung chưa duyệt — "
                        + "thứ Công ty nhìn thấy trước cả người duyệt")
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Và nó cũng không được nằm trong danh sách.
        ResponseEntity<String> danhSach = http.getForEntity("/api/v1/public/articles?size=100", String.class);
        assertThat(danhSach.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(danhSach.getBody()).doesNotContain(slug);
    }

    // === DOD1.7 — đính kèm đầu-cuối QUA HTTP ==================================

    @Test
    @DisplayName("⭐⭐ DOD1.7 — tải lên rồi tải về được, đi trọn vẹn qua HTTP")
    void attachmentsSurviveAFullHttpRoundTrip() {
        UUID congTrinh = taoCongTrinh("DOD-CT-001");

        // 1) Danh sách rỗng lúc đầu — và trả 200 chứ không phải 400.
        ResponseEntity<String> luc0 = phienHttp.get(kyThuat, "/api/v1/ops/constructions/" + congTrinh + "/documents");
        assertThat(luc0.getStatusCode())
                .as(
                        "⛔ Giao diện từng gọi `/attachments?ownerId=<uuid>` vào một tham số kiểu Long → "
                                + "400 ở MỌI lượt mở tab. Tab hiện ra, bảng rỗng, không có gì báo sai. %s",
                        luc0.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(luc0.getBody()).contains("\"usedBytes\":0");

        // 2) Tải lên một tệp thật.
        ResponseEntity<String> tai = taiTaiLieu(congTrinh, "ho-so-thiet-ke.pdf");
        assertThat(tai.getStatusCode()).as("%s", tai.getBody()).isEqualTo(HttpStatus.CREATED);
        String tepId = PhienHttp.giaTriJson(tai.getBody(), "publicId");

        // 3) Danh sách thấy tệp, và hạn mức đã cộng.
        ResponseEntity<String> luc1 = phienHttp.get(kyThuat, "/api/v1/ops/constructions/" + congTrinh + "/documents");
        assertThat(luc1.getBody()).contains("ho-so-thiet-ke.pdf");
        assertThat(luc1.getBody()).doesNotContain("\"usedBytes\":0");

        // 4) ⛔ Chưa quét virus xong thì CHƯA tải về được — và đó là hành vi đúng, không phải lỗi.
        //    Giao diện phải ẩn nút Tải về theo cờ `downloadable` chứ không cho bấm rồi nhận 409.
        ResponseEntity<String> chuaQuet = phienHttp.get(
                kyThuat, "/api/v1/ops/constructions/" + congTrinh + "/documents/" + tepId + "/download-url");
        assertThat(chuaQuet.getStatusCode())
                .as("tệp mới tải lên đang PENDING — cổng quét phải chặn: %s", chuaQuet.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(chuaQuet.getBody()).contains("SYS-0009");
        assertThat(luc1.getBody())
                .as("và cờ downloadable phải nói đúng điều đó cho giao diện")
                .contains("\"downloadable\":false");

        // 5) ⭐ Quét xong thì tải về được — vế mà mọi bài kiểm trước bỏ qua. Tải lên chạy tốt trong
        //    khi tải về hỏng là hình dạng lỗi đã gặp ở MinIO endpoint (docs/deploy-guideline §9.3).
        // ⚠ Điều kiện chặn là cột `status`, không phải `scan_status` — `Attachment.isDownloadable()`
        //    đọc `status == READY`. Đặt nhầm cột thì bài kiểm vẫn đỏ và ta đi tra nhầm chỗ, đúng như
        //    thông điệp lỗi cũ đã dẫn dụ.
        jdbc.update("UPDATE attachments SET status = 'READY', scan_status = 'CLEAN' WHERE public_id = ?::uuid", tepId);

        ResponseEntity<String> duongDan = phienHttp.get(
                kyThuat, "/api/v1/ops/constructions/" + congTrinh + "/documents/" + tepId + "/download-url");
        assertThat(duongDan.getStatusCode()).as("%s", duongDan.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(duongDan.getBody()).contains("http");
    }

    // === DOD1.11 — thêm mã tình hình vận hành mới KHÔNG cần deploy ============

    @Test
    @DisplayName("⭐⭐ DOD1.11 — mã mới thêm lúc chạy được dùng ngay và lái được trạng thái công trình")
    void aBrandNewOperationCodeWorksWithoutRedeploy() {
        UUID congTrinh = taoCongTrinh("DOD-CT-002");

        // Thêm mã bằng đúng đường mà người quản trị đi: một câu INSERT lúc chạy, không đụng mã nguồn.
        // (Đường HTTP đòi vai trò ADMIN, mà ADMIN buộc 2FA nên không đăng nhập được trong bài kiểm —
        //  điều đang kiểm ở đây là "danh mục là DỮ LIỆU", không phải "màn hình quản trị chạy được".)
        jdbc.update(
                """
                INSERT INTO operation_status_codes (code, name, has_parameter, parameter_unit, color_hex,
                                                    mapped_status, sort_order, active)
                VALUES ('DOD-MO', 'Mở x cm (mã thêm lúc chạy)', TRUE, 'cm', '#0ea5e9', 'CANH_BAO', 900, TRUE)
                """);

        ResponseEntity<String> ghi = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/ops/operation-statuses/batch",
                """
                {"items":[{"constructionPublicId":"%s","operationCode":"DOD-MO",
                           "parameterValue":"35.50","effectiveAt":"2026-08-23T09:00:00+07:00"}]}"""
                        .formatted(congTrinh));

        assertThat(ghi.getStatusCode())
                .as(
                        "⛔ Quy tắc 16: danh mục do khách vận hành là DỮ LIỆU có CRUD, không phải enum "
                                + "trong mã. Thêm mã mới mà phải deploy lại là đã vi phạm. %s",
                        ghi.getBody())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(trangThai(congTrinh))
                .as("mã mới phải lái được cả trạng thái dẫn xuất, không chỉ nằm trong danh sách")
                .isEqualTo("CANH_BAO");

        // Tham số kèm theo giữ đúng thang đo — NUMERIC(10,2), không phải số thực.
        assertThat(jdbc.queryForObject(
                        """
                        SELECT s.parameter_value::text FROM construction_operation_status s
                        JOIN constructions c ON c.id = s.construction_id WHERE c.public_id = ?
                        """,
                        String.class,
                        congTrinh))
                .isEqualTo("35.50");
    }

    // -------------------------------------------------------------------------

    private String layMotDanhMuc() {
        return jdbc.query(
                "SELECT public_id::text FROM categories WHERE deleted_at IS NULL LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private UUID taoCongTrinh(String ma) {
        ResponseEntity<String> phanHoi = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/ops/constructions",
                """
                {"code":"%s","name":"Công trình nghiệm thu %s","constructionType":"CONG",
                 "orgUnitId":"%s","managementLevel":"CONG_TY"}"""
                        .formatted(ma, ma, donViGoc));
        assertThat(phanHoi.getStatusCode())
                .as("dựng dữ liệu hỏng thì mọi khẳng định bên dưới nói dối: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(PhienHttp.giaTriJson(phanHoi.getBody(), "publicId"));
    }

    private ResponseEntity<String> taiTaiLieu(UUID congTrinh, String tenTep) {
        org.springframework.util.LinkedMultiValueMap<String, Object> than =
                new org.springframework.util.LinkedMultiValueMap<>();
        than.add("file", new org.springframework.core.io.ByteArrayResource("%PDF-1.4 nội dung thử".getBytes()) {
            @Override
            public String getFilename() {
                return tenTep;
            }
        });

        org.springframework.http.HttpHeaders headers = phienHttp.header(kyThuat);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

        return http.exchange(
                "/api/v1/ops/constructions/" + congTrinh + "/documents?docType=HO_SO_THIET_KE",
                HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(than, headers),
                String.class);
    }

    private String trangThai(UUID congTrinh) {
        return jdbc.queryForObject(
                "SELECT operational_status FROM constructions WHERE public_id = ?", String.class, congTrinh);
    }

    private void donDep() {
        jdbc.update(
                """
                DELETE FROM construction_operation_status WHERE construction_id IN
                    (SELECT id FROM constructions WHERE code LIKE 'DOD-CT-%')
                """);
        jdbc.update("DELETE FROM attachments WHERE owner_type = 'CONSTRUCTION' AND owner_id IN "
                + "(SELECT id FROM constructions WHERE code LIKE 'DOD-CT-%')");
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'DOD-CT-%'");
        jdbc.update("DELETE FROM operation_status_codes WHERE code = 'DOD-MO'");
        jdbc.update("DELETE FROM articles WHERE slug LIKE 'bai-nhap-nghiem-thu-%'");
    }
}
