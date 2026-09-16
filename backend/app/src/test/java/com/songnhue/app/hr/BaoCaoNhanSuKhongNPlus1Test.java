package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

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

import com.songnhue.app.testsupport.DemTruyVan;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Số truy vấn của báo cáo nhân sự ⛔ được tăng theo số hồ sơ</b> — T63.5, trả nợ T58.18.
 *
 * <h2>Khuyết tật đã xảy ra thật</h2>
 *
 * {@code dieuDongTheoThang} bản đầu gọi {@code findByEmployeeId…} trong một vòng lặp ⇒ ~200 lượt
 * truy vấn cho MỘT lượt mở màn hình thống kê, trên VPS 2 nhân. Đã vá bằng một truy vấn gộp
 * ({@code theoLoaiTuNgay}), nhưng kho ⛔ có gì ngăn nó quay lại — và triệu chứng duy nhất là
 * <i>"trang hơi chậm"</i>, thứ ⛔ ai đi đo. Một lượt rà của con người cũng ⛔ thấy: cả hai bản đều
 * chạy đúng, trả đúng số, và khác nhau ở một chỗ ⛔ hiện ra trong kết quả.
 *
 * <h2>Vì sao bài này so HAI CỠ dữ liệu thay vì khẳng định một ngưỡng</h2>
 *
 * Xem javadoc {@link DemTruyVan}. Tóm lại: một ngưỡng tuyệt đối đỏ vì thay đổi vô can, và cách sửa
 * rẻ nhất khi nó đỏ là **nâng con số** — tự tay tháo bộ canh (§11.17, T58.6). Bất biến thật của N+1
 * là <b>độ dốc</b>, và độ dốc thì ⛔ nới được.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaoCaoNhanSuKhongNPlus1Test extends IntegrationTestBase {

    private static final String TIEN_TO = "T635-";
    private static final String VAI_TRO = "KIEMTRA_T635";

    /** Cỡ nhỏ và cỡ lớn. Chênh lệch phải đủ to để một N+1 lộ ra rõ hơn mọi nhiễu. */
    private static final int CO_NHO = 3;

    private static final int CO_LON = 30;

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory emf;

    private PhienHttp phienHttp;
    private PhienHttp.Phien phien;
    private DemTruyVan demTruyVan;
    private UUID donViPublic;

    @BeforeAll
    void dungNen() {
        phienHttp = new PhienHttp(http);
        demTruyVan = new DemTruyVan(emf);
        donViPublic = jdbc.queryForObject("SELECT public_id FROM org_units ORDER BY id LIMIT 1", UUID.class);

        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t635");
        jdbc.update(
                "INSERT INTO roles (code, name) VALUES (?, 'Vai trò kiểm thử T63.5') ON CONFLICT DO NOTHING", VAI_TRO);
        for (String quyen : List.of("hr:report:view", "hr:employee:manage")) {
            jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) "
                            + "SELECT r.id, p.id FROM roles r, permissions p "
                            + "WHERE r.code = ? AND p.code = ? ON CONFLICT DO NOTHING",
                    VAI_TRO,
                    quyen);
        }
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.code = ? "
                        + "ON CONFLICT DO NOTHING",
                username,
                VAI_TRO);
        phien = phienHttp.dangNhap(username);
    }

    @AfterAll
    void donDep() {
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
    }

    @Test
    @DisplayName("⭐⭐ Báo cáo tổng quan: gấp 10 lần số hồ sơ ⛔ được làm số truy vấn tăng theo")
    void soTruyVanKhongTangTheoSoHoSo() {
        taoHoSo(0, CO_NHO);
        long vetNho = demTruyVan.dem(this::moBaoCao);

        taoHoSo(CO_NHO, CO_LON);
        long vetLon = demTruyVan.dem(this::moBaoCao);

        // Tiền đề phải là một KHẲNG ĐỊNH: nếu lượt dựng dữ liệu hỏng thì cả hai lượt đo chạy trên
        // cùng một tập và bài này xanh mà ⛔ so gì (luật 7).
        Integer soHoSo =
                jdbc.queryForObject("SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%");
        assertThat(soHoSo).as("dữ liệu nền phải thật sự có %d hồ sơ", CO_LON).isEqualTo(CO_LON);

        // Ngưỡng ⛔ phải một "ngân sách truy vấn" — nó là vế phân biệt *hằng số* với *tuyến tính*.
        // Một N+1 thật cho `vetLon - vetNho ≈ 27`; một báo cáo gộp truy vấn cho chênh lệch 0.
        // Cho phép một khoảng nhỏ vì lượt gọi còn đi qua xác thực và tra quyền.
        long chenhLech = vetLon - vetNho;
        assertThat(chenhLech)
                .as(
                        """
                        Số truy vấn TĂNG THEO số hồ sơ — đây là hình dạng N+1.

                          %d hồ sơ ⇒ %d câu lệnh
                          %d hồ sơ ⇒ %d câu lệnh   (chênh %d)

                        Thêm %d hồ sơ mà sinh thêm %d câu lệnh nghĩa là ở đâu đó có một vòng lặp gọi \
                        repository. Trên VPS 2 nhân của Công ty, nó là một màn hình *"hơi chậm"* — \
                        thứ ⛔ ai đi đo, và càng nhiều CBNV thì càng chậm.

                        ⛔ ĐỪNG sửa bài kiểm này bằng cách nới con số: ngưỡng ở đây ⛔ phải một ngân \
                        sách truy vấn, nó là vế phân biệt *hằng số* với *tuyến tính*. Hãy đi tìm \
                        vòng lặp và gộp nó thành một truy vấn.""",
                        CO_NHO, vetNho, CO_LON, vetLon, chenhLech, CO_LON - CO_NHO, chenhLech)
                .isLessThan(CO_LON - CO_NHO) // ⇐ độ dốc phải NHỎ HƠN 1 câu lệnh mỗi hồ sơ
                .isLessThan(10);
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: phép đếm PHÂN BIỆT được một truy vấn với nhiều truy vấn")
    void tuKiemChung() {
        // CLAUDE.md luật 1. Bài trên xanh vì mã HÔM NAY đúng; nó ⛔ chứng minh `DemTruyVan` đếm được
        // gì. Ở đây ép đúng hình dạng N+1 bằng tay — một vòng lặp `queryForObject` — rồi đòi bộ đếm
        // thấy nó tăng. Thiếu vế này, một `DemTruyVan` luôn trả 0 (statistics ⛔ bật, đếm nhầm bộ
        // đếm) sẽ làm bài chính xanh **vĩnh viễn** và ⛔ ai biết.
        // ⚠ Phải đi qua **JPA**, ⛔ qua `JdbcTemplate`. Bản đầu của bài này dùng `jdbc.queryForObject`
        //   và đo ra **0** — `DemTruyVan` chỉ thấy câu lệnh đi qua Hibernate. Đó vừa là lý do bài tự
        //   kiểm tồn tại, vừa là giới hạn phải khai ra (javadoc `DemTruyVan`, nợ T63.6).
        long mot = demTruyVan.dem(() -> demBangJpa(1));
        long muoi = demTruyVan.dem(() -> demBangJpa(10));

        assertThat(mot)
                .as("một truy vấn phải đếm ra ít nhất 1 — nếu 0 thì statistics ⛔ bật")
                .isPositive();
        assertThat(muoi - mot)
                .as("mười truy vấn phải đếm ra nhiều hơn một truy vấn ít nhất 8 câu lệnh")
                .isGreaterThanOrEqualTo(8);
    }

    /** Chạy {@code lan} lượt truy vấn JPA — công cụ của bài tự kiểm, ⛔ phải của bài chính. */
    private void demBangJpa(int lan) {
        var em = emf.createEntityManager();
        try {
            for (int i = 0; i < lan; i++) {
                em.createQuery("SELECT count(e) FROM Employee e").getSingleResult();
            }
        } finally {
            em.close();
        }
    }

    private void moBaoCao() {
        ResponseEntity<String> tl = phienHttp.goi(phien, HttpMethod.GET, "/api/v1/hr/bao-cao/tong-quan", null);
        assertThat(tl.getStatusCode())
                .as("lượt mở báo cáo phải thành công, ⛔ thì phép đếm đo một đường lỗi: %s", tl.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    /** Dựng hồ sơ bằng SQL trực tiếp — ⛔ qua API, vì lượt dựng ⛔ phải thứ đang được đo. */
    private void taoHoSo(int tu, int den) {
        for (int i = tu; i < den; i++) {
            jdbc.update(
                    """
                    INSERT INTO employees (public_id, code, full_name, date_of_birth, gender, org_unit_id,
                                           hired_at, contract_type, status, created_at, updated_at)
                    SELECT gen_random_uuid(), ?, ?, DATE '1990-01-01', 'NAM', o.id,
                           DATE '2020-03-15', 'KHONG_XAC_DINH_THOI_HAN', 'DANG_LAM', now(), now()
                      FROM org_units o WHERE o.public_id = ?
                    """,
                    TIEN_TO + i,
                    "Nhân viên kiểm thử " + i,
                    donViPublic);
        }
    }
}
