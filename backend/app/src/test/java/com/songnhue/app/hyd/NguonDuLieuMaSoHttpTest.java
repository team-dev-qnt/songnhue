package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Địa chỉ gốc của nguồn ⛔ không được mang mã số</b> — T50.1.
 *
 * <h2>⛔⛔ Sự cố thật trên staging: 9 ngày, 3323 lượt hỏng, 0 byte dữ liệu</h2>
 *
 * <p>Ngày 01/09/2026 mã số truy cập bị dán <b>nguyên URL</b> vào ô <i>Địa chỉ gốc</i>:
 * {@code http://songnhue.bhh40.net/api/getmn.aspx?key=<mã số>}. Đo lại staging ngày 10/09:
 *
 * <pre>
 *   base_url             = http://songnhue.bhh40.net/api/getmn.aspx?key=&lt;mã số&gt;
 *   credential           = NULL
 *   last_failure_reason  = "Nguồn chưa cấu hình mã số — đặt mã số rồi gọi lại"
 *   consecutive_failures = 3323        last_success_at = NULL
 *   hydro_raw_logs       = 0           hydro_latest    = 0
 * </pre>
 *
 * <p>Poller hỏng <b>trước khi mở HTTP</b> nên ⛔ không lần nào gọi tới nguồn — và quy tắc 18 nói
 * <b>⛔ không có API lịch sử ⇒ mất dữ liệu là vĩnh viễn</b>. Chín ngày ấy ⛔ không lấy lại được.
 *
 * <p>Hệ quả thứ hai nặng ngang: mã số nằm <b>nguyên văn</b> ở một cột ⛔ không mã hoá, mà
 * {@code ApiSourceView} trả {@code baseUrl} ra API cho mọi vai trò có {@code hyd:station:manage}.
 * Quy tắc 13 nói mã số phải AES-256-GCM, ⛔ không log, ⛔ không trả ra API — cả ba vế đều vỡ, chỉ
 * vì chuỗi ấy nằm ở <b>ô bên cạnh</b>.
 *
 * <h2>⛔ Đây ⛔ KHÔNG phải lỗi người dùng</h2>
 *
 * <p>Có <b>hai ô</b> nhận cùng một chuỗi — một ô được mã hoá, một ô ⛔ không — và ⛔ không gì nói
 * cho người gõ biết họ chọn nhầm. Cùng họ T46.6 (dán toạ độ vào {@code InputNumber} ra {@code 21}):
 * <i>con đường tự nhiên nhất vừa im lặng vừa sai</i>. ⇒ Bảo đảm đặt ở
 * {@code ApiSourceService.diaChi(...)}, chỗ <b>cả</b> {@code create} lẫn {@code update} đi qua
 * (quy tắc 12) — ⛔ không đặt ở biểu mẫu, vì một ô nhập chỉ đỡ được người dùng ô ấy.
 *
 * <h2>Vì sao kiểm qua HTTP</h2>
 *
 * <p>Vì thứ phải đúng gồm cả <b>mã trạng thái</b> và <b>mã lỗi</b> ra tới trình duyệt, mà §11.18 đã
 * trả giá đúng chỗ này: một lượt vá khẳng định {@code SYS-0003} là 422 trong khi nó là <b>400</b>.
 * Gọi thẳng service chỉ chứng minh được nửa nằm trong service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NguonDuLieuMaSoHttpTest extends IntegrationTestBase {

    private static final String DUONG_DAN = "/api/v1/hyd/api-sources";

    /** Đúng chuỗi đã gây sự cố, chỉ thay mã số thật bằng một chuỗi vô hại. */
    private static final String DIA_CHI_LOT_MA_SO = "http://songnhue.bhh40.net/api/getmn.aspx?key=matkhau123;";

    private static final String DIA_CHI_SACH = "http://songnhue.bhh40.net";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * ⚠ Vai trò TẠM mang <b>đúng một</b> quyền, ⛔ không dùng {@code ADMIN}.
     *
     * <p>Bản đầu của lớp này đăng nhập bằng {@code ADMIN} và đỏ ngay lượt chạy đầu: {@code ADMIN}
     * nằm trong {@code TWO_FACTOR_REQUIRED_ROLES} (chốt G12) nên lượt đăng nhập trả
     * {@code TWO_FACTOR_ENROLL_REQUIRED} chứ ⛔ không phải {@code AUTHENTICATED}. Dùng đúng khuôn
     * {@code TelemetryProbeHttpTest}: một vai trò tạm, xoá hẳn ở {@code @AfterAll}.
     */
    private static final String VAI_TRO_TAM = "KIEMTRA_DIA_CHI_NGUON";

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;

    @BeforeAll
    void dungVaiTroVaDangNhap() {
        donSachVaiTroTam();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử Địa chỉ nguồn', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO_TAM);
        int soQuyen = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + " WHERE r.code = ? AND p.code = 'hyd:api-source:manage'",
                VAI_TRO_TAM);
        assertThat(soQuyen)
                .as("⚠ Chống tập rỗng: mã quyền đổi tên thì lệnh trên gán 0 dòng trong im lặng, và mọi "
                        + "bài dưới đây đỏ với 403 — một triệu chứng chẳng liên quan gì tới thứ đang kiểm")
                .isEqualTo(1);

        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tnguon_dc", VAI_TRO_TAM));
    }

    @AfterAll
    void donVaiTroTam() {
        donSachVaiTroTam();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE code = ?", Integer.class, VAI_TRO_TAM))
                .as("⛔ Dọn dẹp hỏng trong im lặng ở đây làm RbacMatrixTest đỏ ở MỘT LỚP KHÁC — "
                        + "loại lỗi khó lần nhất. Khẳng định ngay tại chỗ dọn.")
                .isZero();
    }

    private void donSachVaiTroTam() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_TAM);
    }

    @Test
    @DisplayName("⛔⛔ THÊM nguồn với địa chỉ mang ?key= bị TỪ CHỐI kèm HYD-2016")
    void creatingASourceWithACredentialInTheUrlIsRejected() {
        ResponseEntity<String> phanHoi = phienHttp.goi(quanTri, HttpMethod.POST, DUONG_DAN, thanTao(DIA_CHI_LOT_MA_SO));

        assertThat(phanHoi.getStatusCode())
                .as("chuỗi này đúng là chuỗi đã làm staging mất 9 ngày dữ liệu: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(phanHoi.getBody())
                .as("⛔ mã lỗi phải nói ĐÚNG chuyện gì sai — 'dữ liệu không hợp lệ' chung chung sẽ "
                        + "đẩy người dùng đi sửa nhầm ô lần nữa")
                .contains("HYD-2016");
        assertThat(phanHoi.getBody())
                .as("và phải chỉ đích danh tham số bị bắt, để người đọc biết cắt chỗ nào")
                .contains("key");
    }

    @Test
    @DisplayName("⛔⛔ SỬA nguồn sang địa chỉ mang ?key= cũng bị TỪ CHỐI — đúng đường người dùng đã đi")
    void updatingASourceWithACredentialInTheUrlIsRejected() {
        UUID nguon = taoNguonSach("tnguon_sua");

        ResponseEntity<String> phanHoi =
                phienHttp.goi(quanTri, HttpMethod.PUT, DUONG_DAN + "/" + nguon, thanSua(DIA_CHI_LOT_MA_SO));

        assertThat(phanHoi.getStatusCode())
                .as(
                        """
                        ⛔ Đường THÊM và đường SỬA là hai lời gọi khác nhau. Sự cố thật đi qua đường \
                        SỬA (nguồn BHH40 có sẵn từ migration), nên chỉ canh đường THÊM là canh đúng \
                        cái đã KHÔNG xảy ra. %s""",
                        phanHoi.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(phanHoi.getBody()).contains("HYD-2016");

        assertThat(diaChiCua(nguon))
                .as("⛔ và bản ghi phải GIỮ NGUYÊN địa chỉ cũ — một lượt từ chối mà vẫn ghi đè "
                        + "một nửa thì tệ hơn không từ chối")
                .isEqualTo(DIA_CHI_SACH);
    }

    @Test
    @DisplayName("⚠ Đối chứng: địa chỉ sạch vẫn THÊM và SỬA được — luật không chặn nhầm")
    void aCleanBaseUrlStillWorksOnBothPaths() {
        // Luật 7. Một luật từ chối TẤT CẢ cũng làm hai bài trên xanh, và nó sẽ khoá cứng màn hình
        // Nguồn dữ liệu — người vận hành sẽ tắt luật đi, tức tệ hơn không có luật.
        UUID nguon = taoNguonSach("tnguon_doi_chung");

        ResponseEntity<String> sua =
                phienHttp.goi(quanTri, HttpMethod.PUT, DUONG_DAN + "/" + nguon, thanSua("https://vi-du.test/api"));
        assertThat(sua.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(diaChiCua(nguon)).isEqualTo("https://vi-du.test/api");
    }

    @Test
    @DisplayName("⚠ Đối chứng: `?keyword=` KHÔNG bị bắt — luật so TRỌN TÊN tham số, ⛔ không tìm chuỗi")
    void aParameterMerelyContainingTheWordKeyIsNotRejected() {
        // Canh cấu trúc, ⛔ không canh văn bản (luật 2). Một bản `url.contains("key")` cũng làm hai
        // bài đầu xanh, rồi đỏ oan với `?keyword=` hay đường dẫn `/api/keyword` — và cách sửa rẻ
        // nhất lúc ấy là nới luật cho hết đỏ, tức tháo chính bộ canh.
        UUID nguon = taoNguonSach("tnguon_keyword");

        ResponseEntity<String> sua = phienHttp.goi(
                quanTri, HttpMethod.PUT, DUONG_DAN + "/" + nguon, thanSua("https://vi-du.test/api?keyword=mua"));

        assertThat(sua.getStatusCode())
                .as("`keyword` ⛔ không phải `key`: %s", sua.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⚠ Đối chứng: `?key=` RỖNG được tha — nó ⛔ không mang bí mật nào")
    void anEmptyCredentialParameterIsTolerated() {
        UUID nguon = taoNguonSach("tnguon_key_rong");

        ResponseEntity<String> sua =
                phienHttp.goi(quanTri, HttpMethod.PUT, DUONG_DAN + "/" + nguon, thanSua("https://vi-du.test/api?key="));

        assertThat(sua.getStatusCode())
                .as("từ chối một địa chỉ vô hại là bắt người dùng đi sửa một thứ ⛔ không sai: %s", sua.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    // -----------------------------------------------------------------------

    private UUID taoNguonSach(String ma) {
        ResponseEntity<String> tao = phienHttp.goi(quanTri, HttpMethod.POST, DUONG_DAN, thanTao(DIA_CHI_SACH, ma));
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định bên dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(giaTri(tao.getBody(), "id"));
    }

    private String diaChiCua(UUID publicId) {
        return jdbc.queryForObject("SELECT base_url FROM api_sources WHERE public_id = ?", String.class, publicId);
    }

    private static String thanTao(String diaChi) {
        return thanTao(diaChi, "tnguon_" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static String thanTao(String diaChi, String ma) {
        return """
                {"code":"%s","name":"Nguồn kiểm thử","adapterType":"MOCK","baseUrl":"%s"}"""
                .formatted(ma, diaChi);
    }

    private static String thanSua(String diaChi) {
        return """
                {"name":"Nguồn kiểm thử","baseUrl":"%s","status":"HOAT_DONG"}""".formatted(diaChi);
    }

    private static String giaTri(String json, String truong) {
        int i = json.indexOf("\"" + truong + "\":\"");
        assertThat(i).as("⛔ không tìm thấy trường `%s` trong: %s", truong, json).isGreaterThanOrEqualTo(0);
        int dau = i + truong.length() + 4;
        return json.substring(dau, json.indexOf('"', dau));
    }
}
