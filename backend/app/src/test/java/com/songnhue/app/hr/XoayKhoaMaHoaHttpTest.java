package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.crypto.MaHoaLaiService;
import com.songnhue.core.common.config.CryptoProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.spi.JobContext;

/**
 * <b>Xoay khoá AES ⛔ được tắt phép chống trùng CCCD, và phải hoàn tất được</b> — T61.11 (nợ T51.9).
 *
 * <h2>Kịch bản runbook, chạy thật</h2>
 *
 * Khoá {@code v1} và {@code v2} cùng nạp ({@code IntegrationTestBase}); bài đổi {@code activeKeyId} tại chỗ
 * — đúng việc người vận hành làm bằng {@code AES_KEY_ID=v2} + khởi động lại.
 *
 * <p>⚠ Mỗi bài trả khoá về {@code v1} VÀ chạy job ngược lại ở {@link #traKhoaV1()}: bỏ vế sau thì bảng dùng
 * chung mang hai khoá, và {@code HoSoNhanSuMaHoaTest#theFingerprintColumnCarriesExactlyOneKeyId} đỏ ở một
 * lớp vô can tuỳ thứ tự chạy (§11.19).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XoayKhoaMaHoaHttpTest extends IntegrationTestBase {

    private static final String DUONG_DAN_NV = "/api/v1/hr/employees";
    private static final String TIEN_TO_MA = "KTXK-";
    private static final String VAI_TRO = "KIEMTRA_XOAY_KHOA";
    private static final String MA_NGUON = "KTXK_NGUON";
    private static final String MA_SO_NGUON = "ma-so-thu;";
    private static final String BI_MAT_TOTP = "JBSWY3DPEHPK3PXP";
    private static final String BI_MAT_TICH_HOP = "khoa-bi-mat-recaptcha-xoay-khoa";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CryptoService crypto;

    @Autowired
    private CryptoProperties khoa;

    @Autowired
    private MaHoaLaiService maHoaLai;

    private PhienHttp phienHttp;
    private PhienHttp.Phien phien;
    private UUID donViGoc;
    private long userId;

    @BeforeAll
    void dung() {
        donSach();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Kiểm thử xoay khoá', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO);
        int quyen = 0;
        for (String p : List.of("hr:employee:view", "hr:employee:create", "hr:employee:view-sensitive")) {
            quyen += jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) "
                            + "SELECT r.id, pm.id FROM roles r, permissions pm WHERE r.code = ? AND pm.code = ?",
                    VAI_TRO,
                    p);
        }
        assertThat(quyen)
                .as("chống tập rỗng — mã quyền đổi tên thì mọi bài đỏ 403 vì lý do sai")
                .isEqualTo(3);

        phienHttp = new PhienHttp(http);
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, "xoaykhoa", VAI_TRO);
        phien = phienHttp.dangNhap(username);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        donViGoc = UUID.fromString(
                jdbc.queryForObject("SELECT public_id::text FROM org_units WHERE code = 'CTY'", String.class));
    }

    @AfterEach
    void traKhoaV1() {
        datKhoa("v1");
        jdbc.update("DELETE FROM employee_sensitive WHERE national_id LIKE 'v1:KHONG-GIAI-MA-DUOC%'");
        maHoaLai.chay(p -> {});
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CRYPTO_REENCRYPT'");
        donSachDuLieu();
    }

    @AfterAll
    void donSach() {
        datKhoa("v1");
        donSachDuLieu();
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
    }

    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Ngay sau khi đổi AES_KEY_ID, một CCCD đã có (vân tay v1) nhập lại lần hai vẫn bị chặn 409")
    void trungCccdGiuaHaiKhoaVanBiChan() {
        UUID a = taoHoSo("A");
        UUID b = taoHoSo("B");
        luu(a, "001299990001", HttpStatus.NO_CONTENT);
        assertThat(tienToVanTay(a)).as("tiền đề: hồ sơ A mang vân tay khoá cũ").isEqualTo("v1");

        datKhoa("v2");

        ResponseEntity<String> trung =
                phienHttp.goi(phien, HttpMethod.PUT, duong(b), than("001299990001", "19001111000001"));
        assertThat(trung.getStatusCode())
                .as(
                        "⛔ Bản cũ tra ĐÚNG MỘT vân tay (v2:…) ⇒ lượt này THÀNH CÔNG và hai hồ sơ cùng CCCD: %s",
                        trung.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(trung.getBody()).contains("HR-1003");

        // Đối chứng: một phép chống trùng từ chối tất cả cũng làm vế trên xanh.
        luu(b, "001299990002", HttpStatus.NO_CONTENT);
        assertThat(tienToVanTay(b)).as("ghi mới sau khi đổi khoá dùng khoá MỚI").isEqualTo("v2");
    }

    @Test
    @DisplayName("⭐⭐ Job đổi MỌI bản mã + vân tay ở cả bốn bảng sang khoá mới, giá trị giữ nguyên")
    void jobDoiHetSangKhoaMoi() {
        UUID a = taoHoSo("JOB");
        luu(a, "001299990003", HttpStatus.NO_CONTENT);
        jdbc.update(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, credential, created_at)
                VALUES (?, 'Nguồn kiểm thử xoay khoá', 'MOCK', 'http://xoay-khoa.invalid/', 'HOAT_DONG', ?, now())
                """,
                MA_NGUON,
                crypto.encrypt(MA_SO_NGUON));
        jdbc.update(
                "INSERT INTO user_totp (user_id, secret_encrypted, key_id) VALUES (?, ?, 'v1')",
                userId,
                crypto.encrypt(BI_MAT_TOTP));
        // T61.44 — bảng bí mật tích hợp khai `MaHoaLaiJdbc` thứ tư; ⛔ có hàng thì bài này ⛔ chứng minh nó được đổi.
        jdbc.update("DELETE FROM integration_secrets WHERE secret_code = 'RECAPTCHA_SECRET_KEY'");
        jdbc.update(
                "INSERT INTO integration_secrets (secret_code, ciphertext) VALUES ('RECAPTCHA_SECRET_KEY', ?)",
                crypto.encrypt(BI_MAT_TICH_HOP));

        datKhoa("v2");
        assertThat(maHoaLai.conKhoaCu())
                .as("⛔ Cả BỐN bảng phải có người khai — thiếu một bảng thì gỡ khoá cũ làm nó ⛔ đọc lại được")
                .containsKeys("employee_sensitive", "api_sources", "user_totp", "integration_secrets")
                .allSatisfy((bang, n) -> assertThat(n).as(bang).isPositive());

        MaHoaLaiService.KetQua kq = maHoaLai.chay(p -> {});

        assertThat(kq.soDoi()).as("%s", kq).isGreaterThanOrEqualTo(4);
        // Hàng CỦA bài này — ⛔ khẳng định toàn cục `conLai == 0` vì bảng dùng chung với lớp khác.
        assertThat(jdbc.queryForList(
                        """
                        SELECT DISTINCT split_part(c, ':', 1) FROM (
                          SELECT unnest(ARRAY[national_id, national_id_issued_on, national_id_issued_place,
                                 base_salary, salary_coefficient, bank_account, tax_code, social_insurance_no,
                                 national_id_fingerprint]) AS c
                            FROM employee_sensitive
                           WHERE employee_id = (SELECT id FROM employees WHERE public_id = ?)) t
                         WHERE c IS NOT NULL
                        """,
                        String.class,
                        a))
                .as("mọi cột 🔒 + vân tay của hồ sơ A")
                .containsExactly("v2");
        assertThat(jdbc.queryForObject("SELECT credential FROM api_sources WHERE code = ?", String.class, MA_NGUON))
                .startsWith("v2:")
                .satisfies(c -> assertThat(crypto.decrypt(c)).isEqualTo(MA_SO_NGUON));
        assertThat(jdbc.queryForMap("SELECT secret_encrypted, key_id FROM user_totp WHERE user_id = ?", userId))
                .satisfies(m -> {
                    assertThat((String) m.get("secret_encrypted")).startsWith("v2:");
                    assertThat(crypto.decrypt((String) m.get("secret_encrypted")))
                            .isEqualTo(BI_MAT_TOTP);
                    assertThat(m.get("key_id")).as("cột chết vẫn ghi cho khớp").isEqualTo("v2");
                });

        assertThat(jdbc.queryForObject(
                        "SELECT ciphertext FROM integration_secrets WHERE secret_code = 'RECAPTCHA_SECRET_KEY'",
                        String.class))
                .startsWith("v2:")
                .satisfies(c -> assertThat(crypto.decrypt(c)).isEqualTo(BI_MAT_TICH_HOP));
        jdbc.update("DELETE FROM integration_secrets WHERE secret_code = 'RECAPTCHA_SECRET_KEY'");

        // Giá trị đọc qua HTTP giữ nguyên — ⛔ chỉ tiền tố đổi.
        ResponseEntity<String> doc = phienHttp.get(phien, duong(a));
        assertThat(PhienHttp.giaTriJson(doc.getBody(), "nationalId")).isEqualTo("001299990003");
        assertThat(PhienHttp.giaTriJson(doc.getBody(), "bankAccount")).isEqualTo("19001111000003");

        // Và chỉ mục UNIQUE lại có hiệu lực trên cùng một khoá.
        UUID b = taoHoSo("JOB-B");
        luu(b, "001299990003", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("⛔ Một hàng ⛔ giải mã được làm job HỎNG (ADM-2019) — nhưng ⛔ giữ các hàng khác ở khoá cũ")
    void hangHongLamJobHong() {
        UUID a = taoHoSo("TOT");
        luu(a, "001299990004", HttpStatus.NO_CONTENT);
        UUID c = taoHoSo("HONG");
        jdbc.update(
                // Có CCCD ⟺ có vân tay (`ck_employee_sensitive_vantay_cap`) ⇒ vân tay giả đúng dạng.
                "INSERT INTO employee_sensitive (public_id, employee_id, national_id, national_id_fingerprint, "
                        + "created_at) SELECT gen_random_uuid(), id, 'v1:KHONG-GIAI-MA-DUOC-AAAAAAAAAAAAAAAA', "
                        + "'v1:' || repeat('a', 64), now() "
                        + "FROM employees WHERE public_id = ?",
                c);

        datKhoa("v2");
        AtomicReference<String> ketQua = new AtomicReference<>();
        JobContext ctx = new JobContext(UUID.randomUUID(), "CRYPTO_REENCRYPT", "{}", null, p -> {}, ketQua::set);

        assertThatThrownBy(() -> maHoaLai.handle(ctx))
                .as("⛔ Job xanh khi còn hàng khoá cũ là lời mời gỡ khoá cũ — tức mất dữ liệu")
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        e -> assertThat(((BusinessRuleException) e).errorCode()).isEqualTo(ErrorCode.ADM_2019));
        assertThat(tienToVanTay(a)).as("hàng tốt vẫn được đổi dù có hàng hỏng").isEqualTo("v2");
        // `jobs.result` là JSONB — để chính Postgres phán JSON có hợp lệ ⛔, và người vận hành đọc được số còn lại.
        Long conLai = jdbc.queryForObject("SELECT (?::jsonb ->> 'conLai')::bigint", Long.class, ketQua.get());
        assertThat(conLai).as("%s", ketQua.get()).isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT (?::jsonb -> 'conLaiTheoBang' ->> 'employee_sensitive')::bigint",
                        Long.class,
                        ketQua.get()))
                .isPositive();
    }

    @Test
    @DisplayName("Khởi động với hàng khoá cũ ⇒ tự đặt MỘT job (chống trùng); ⛔ còn gì ⇒ ⛔ đặt")
    void khoiDongTuDatJob() {
        UUID a = taoHoSo("KD");
        luu(a, "001299990005", HttpStatus.NO_CONTENT);

        datKhoa("v2");
        var lan1 = maHoaLai.kichHoatNeuCan();
        var lan2 = maHoaLai.kichHoatNeuCan();
        assertThat(lan1).isPresent();
        assertThat(lan2.orElseThrow().getPublicId())
                .as("khoá chống trùng: hai node / hai lượt khởi động vẫn MỘT job")
                .isEqualTo(lan1.orElseThrow().getPublicId());

        maHoaLai.chay(p -> {});
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CRYPTO_REENCRYPT'");
        long conLai = maHoaLai.conKhoaCu().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        assertThat(maHoaLai.kichHoatNeuCan())
                .as("sau khi đổi xong (còn %d hàng lớp khác ⛔ đổi được) ⛔ được đặt job thừa", conLai)
                .matches(o -> conLai > 0 || o.isEmpty());
    }

    // =========================================================================

    private void datKhoa(String id) {
        ReflectionTestUtils.setField(khoa, "activeKeyId", id);
    }

    private String tienToVanTay(UUID hoSo) {
        return jdbc.queryForObject(
                "SELECT split_part(national_id_fingerprint, ':', 1) FROM employee_sensitive "
                        + "WHERE deleted_at IS NULL AND employee_id = (SELECT id FROM employees WHERE public_id = ?)",
                String.class,
                hoSo);
    }

    private static String duong(UUID hoSo) {
        return DUONG_DAN_NV + "/" + hoSo + "/sensitive";
    }

    private UUID taoHoSo(String hau) {
        String than =
                """
                {"code":"%s","fullName":"Kiểm Thử Xoay Khoá %s","orgUnitId":"%s","status":"THU_VIEC"}"""
                        .formatted(TIEN_TO_MA + hau, hau, donViGoc);
        ResponseEntity<String> tao = phienHttp.goi(phien, HttpMethod.POST, DUONG_DAN_NV, than);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(PhienHttp.giaTriJson(tao.getBody(), "publicId"));
    }

    private void luu(UUID hoSo, String cccd, HttpStatus mongDoi) {
        String taiKhoan = "1900111100" + cccd.substring(cccd.length() - 4);
        ResponseEntity<String> r = phienHttp.goi(phien, HttpMethod.PUT, duong(hoSo), than(cccd, taiKhoan));
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(mongDoi);
    }

    private static String than(String cccd, String taiKhoan) {
        return """
                {"nationalId":"%s","nationalIdIssuedOn":"2019-05-20","nationalIdIssuedPlace":"Cục CS QLHC",\
                "baseSalary":"12500000","salaryCoefficient":"3.66","bankAccount":"%s",\
                "taxCode":"8123456789","socialInsuranceNo":"0123456789"}"""
                .formatted(cccd, taiKhoan);
    }

    private void donSachDuLieu() {
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO_MA + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO_MA + "%");
        jdbc.update("DELETE FROM api_sources WHERE code = ?", MA_NGUON);
        jdbc.update(
                "DELETE FROM user_totp WHERE user_id IN (SELECT id FROM users WHERE username = 'kiemtra_xoaykhoa')");
    }
}
