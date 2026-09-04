package com.songnhue.app.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.attachment.AttachmentService;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Xoá tệp đính kèm qua đường CHUNG</b> — nợ <b>A1</b> (mới, sổ chưa có) + <b>T28.34</b>.
 *
 * <h2>⛔⛔ Vì sao lỗ này sống được: đường chung ⛔ KHÔNG có một bài kiểm nào</h2>
 *
 * <p>Đo 04/09/2026: {@code /api/v1/attachments} có <b>0</b> lượt xuất hiện trong toàn bộ
 * {@code app/src/test}. Ba endpoint ghi của nó — {@code POST}, {@code DELETE} — chưa từng được gọi
 * bởi một vai trò nào. Nên {@code @RequirePermission("ops:document:upload")} trên {@code DELETE}
 * nằm đó từ WS-6 mà ⛔ không ai đi qua để thấy nó sai.
 *
 * <p>⚠ Và nó là hình dạng <b>nguy hiểm nhất</b> của một lỗ phân quyền: <b>tầng 1 (menu) và tầng 2
 * (nút) đều đúng</b> — {@code ConstructionDocumentsPanel} ẩn nút xoá theo {@code ops:document:delete}
 * — nên ⛔ không màn hình nào lộ ra điều gì. Chỉ tầng 3 sai, và tầng 3 là tầng <b>duy nhất</b> thật
 * sự chặn. Người khai thác ⛔ không cần giao diện; họ cần một lượt {@code curl}.
 *
 * <h2>Bài này đăng nhập bằng vai trò THẬT, ⛔ không phải ADMIN</h2>
 *
 * <p>{@code TECHNICIAN} là vai trò <b>có</b> {@code ops:document:upload} và ⛔ <b>không</b> có
 * {@code ops:document:delete} — đúng cặp làm cho lỗ này khai thác được. Đo bằng ADMIN thì cả bản
 * hỏng lẫn bản vá đều trả 204, và bài kiểm ⛔ không phân biệt được hai trạng thái (luật 9).
 */
class AttachmentDeleteHttpTest extends IntegrationTestBase {

    private static final String OWNER_TYPE = "TEST_A1_OWNER";
    private static final String MA_CONG_TRINH = "A1-CT-01";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    private PhienHttp phienHttp;

    /** ⚠ CÓ {@code ops:document:upload}, ⛔ KHÔNG có {@code ops:document:delete}. */
    private PhienHttp.Phien kyThuat;

    @BeforeEach
    void setUp() {
        donDep();
        if (phienHttp == null) {
            phienHttp = new PhienHttp(http);
            kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "a1_kythuat", "TECHNICIAN"));
        }
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // === A1 — quyền của đường chung =========================================

    /**
     * ⭐⭐ Bài chịu lực. Trước bản vá 04/09 lượt gọi này trả <b>204</b>.
     *
     * <p>Ba vai trò ({@code TECHNICIAN} · {@code XN_MANAGER} · {@code XN_OPERATOR}) đều có
     * {@code upload}, nên cả ba xoá được <b>bất kỳ tệp nào trong hệ</b> — kể cả tệp của đơn vị khác,
     * kể cả Quyết định phê duyệt Quy trình vận hành đang công bố trên cổng.
     */
    @Test
    @DisplayName("⭐⭐ A1 — TECHNICIAN (có `upload`, KHÔNG có `delete`) gọi DELETE ⇒ phải 403")
    void aTechnicianCannotDeleteThroughTheGenericPath() {
        UUID tep = taoTep();

        ResponseEntity<String> ra = xoa(kyThuat, tep);

        assertThat(ra.getStatusCode())
                .as(
                        """
                        ⛔ Đường CHUNG /api/v1/attachments/{id} từng gác bằng `ops:document:upload`, trong khi \
                        hai đường RIÊNG (ConstructionDocumentController, MaintenanceLogController) gác bằng \
                        `ops:document:delete` và giao diện cũng ẩn nút theo mã ấy. Đường rộng hơn là đường \
                        không ai canh. Thân: %s""",
                        ra.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(conSong(tep))
                .as("⛔ 403 phải là 403 THẬT — bản ghi ⛔ không được đụng tới")
                .isTrue();
    }

    /**
     * ⚠⚠ Vế phân biệt (luật 9) — và nó ⛔ <b>KHÔNG</b> đi được bằng HTTP. Lý do đáng ghi lại.
     *
     * <p>Đo 04/09/2026: {@code ops:document:delete} chỉ được cấp cho {@code SUPER_ADMIN} và
     * {@code ADMIN}, mà cả hai nằm trong {@code AuthenticatedUser.TWO_FACTOR_REQUIRED_ROLES} (chốt
     * G12) ⇒ lượt {@code POST /auth/login} của chúng trả {@code TWO_FACTOR_ENROLL_REQUIRED}, ⛔ không
     * trả token. ⇒ ⛔ <b>không vai trò nào</b> đăng nhập được bằng mật khẩu đơn thuần mà xoá được
     * tệp — một phát hiện phụ, và là một tin tốt.
     *
     * <p>⇒ Vế phân biệt phải hỏi ở tầng khác, và câu hỏi đúng là: <b>bản vá có biến một cổng quá
     * rộng thành một cổng CHẾT không</b>. Một endpoint gác bằng một mã quyền ⛔ không ai có cũng là
     * một khuyết tật — nó chỉ hỏng theo chiều ngược lại, và cũng im lặng y hệt.
     */
    @Test
    @DisplayName("⚠ Vế phân biệt — `ops:document:delete` CÓ vai trò nắm giữ, ⛔ không phải một cổng chết")
    void thePermissionIsActuallyHeldBySomeRole() {
        Integer soVaiTro = jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.code = 'ops:document:delete'
                """,
                Integer.class);

        assertThat(soVaiTro)
                .as(
                        """
                        ⛔ Vá một cổng quá rộng bằng một mã quyền ⛔ KHÔNG ai có là đổi một lỗ lấy một cánh                         cửa khoá chết — và cả hai đều im lặng. Vế 403 ở bài trên xanh trong CẢ HAI trường                         hợp, nên nó ⛔ không phân biệt được (luật 9).""")
                .isNotNull()
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * ⭐⭐ Bất biến THẬT SỰ đã vỡ: <b>ba cửa vào cùng một hành vi phải cùng một ổ khoá</b>.
     *
     * <p>Đây là khẳng định mà — nếu có từ đầu — sẽ bắt được A1 ngay ngày nó ra đời. Ba controller
     * đều xoá một tệp đính kèm; hai cái gác {@code :delete}, một cái gác {@code :upload}. ⛔ Không
     * bài kiểm nào so ba giá trị ấy với nhau, nên cái lệch sống được từ WS-6.
     *
     * <p>⚠ Đọc bằng <b>phản chiếu</b> chứ ⛔ không bằng {@code grep}: một chuỗi trong tệp nguồn ⛔
     * không chứng minh chú thích ấy nằm trên đúng phương thức nào (luật 2).
     */
    @Test
    @DisplayName("⭐⭐ Ba cửa vào cùng một hành vi XOÁ TỆP phải đòi CÙNG một quyền")
    void allThreeDeleteDoorsRequireTheSamePermission() throws Exception {
        List<String> duongChung =
                quyenCua(com.songnhue.core.api.attachment.AttachmentController.class, "delete", UUID.class);
        List<String> duongCongTrinh = quyenCua(
                com.songnhue.operations.api.ConstructionDocumentController.class, "delete", UUID.class, UUID.class);

        assertThat(duongChung)
                .as(
                        "⛔ Đường CHUNG đòi %s, đường RIÊNG đòi %s. Đường rộng hơn là đường không ai canh: "
                                + "TECHNICIAN · XN_MANAGER · XN_OPERATOR đều có `ops:document:upload` và ⛔ KHÔNG ai "
                                + "có `ops:document:delete`, nên cái lệch này cho ba vai trò xoá bất kỳ tệp nào "
                                + "trong hệ.",
                        duongChung, duongCongTrinh)
                .isEqualTo(duongCongTrinh)
                .containsExactly("ops:document:delete");
    }

    /**
     * ⚠ {@code value()} là {@code String[]} — chú thích nhận <b>nhiều</b> mã ở chế độ HOẶC. Nên phép
     * so phải so <b>cả danh sách</b>: một endpoint khai {@code {"...delete", "...upload"}} thì lỗ cũ
     * quay lại nguyên vẹn, mà một phép so trên phần tử đầu sẽ ⛔ không thấy gì.
     */
    private static List<String> quyenCua(Class<?> lop, String ten, Class<?>... thamSo) throws Exception {
        com.songnhue.core.common.security.RequirePermission a =
                lop.getMethod(ten, thamSo).getAnnotation(com.songnhue.core.common.security.RequirePermission.class);
        assertThat(a)
                .as("⚠ vế chống tập rỗng: %s.%s ⛔ không có @RequirePermission — phép so dưới đây vô nghĩa", lop, ten)
                .isNotNull();
        return List.of(a.value());
    }

    // === T28.34 — gỡ tham chiếu ============================================

    /**
     * ⭐⭐ {@code ON DELETE SET NULL} khai ở <b>năm cột</b> và <b>chưa từng bắn một lần nào</b>.
     *
     * <p>Xoá ở đây là xoá <b>mềm</b> (quy tắc 9), nên với CSDL thì ⛔ không có gì bị xoá và ⛔ không
     * có gì để {@code SET NULL}. Hai luật đúng riêng lẻ, loại trừ nhau khi ghép.
     *
     * <p>Triệu chứng: {@code readForPublic} lọc {@code deleted_at IS NULL} ⇒ 404, trong khi cột ở
     * {@code constructions} <b>vẫn giữ UUID</b> nên cổng <b>vẫn dựng liên kết</b>. Người dân bấm
     * "Quy trình vận hành" và nhận trang lỗi.
     */
    @Test
    @DisplayName("⭐⭐ T28.34 — xoá tệp thì cột tài liệu công bố của công trình về NULL")
    void deletingAnAttachmentClearsThePublishedDocumentReference() {
        UUID tep = taoTep();
        // ⚠ Phải TỰ DỰNG công trình: `constructions` RỖNG trên CSDL kiểm thử, và đó là chủ ý —
        //   ⛔ CẤM seed dữ liệu công trình "cho đẹp demo" (G8 chưa về). Bản đầu của bài này tra
        //   `ORDER BY id LIMIT 1` và đổ vỡ ngay lượt chạy đầu; ⛔ đó là luật làm đúng việc của nó.
        long idCongTrinh = taoCongTrinh();
        jdbc.update(
                "UPDATE constructions SET operating_procedure_attachment_public_id = ? WHERE id = ?", tep, idCongTrinh);
        assertThat(troToiTep(tep)).as("tiền đề: liên kết ĐANG tồn tại").isEqualTo(1);

        // ⚠ Gọi thẳng service, ⛔ không qua HTTP — xem `thePermissionIsActuallyHeldBySomeRole`: ⛔
        //   không vai trò nào đăng nhập được bằng mật khẩu đơn thuần mà có `ops:document:delete`.
        //   ⭐ Và điều đó ĐÚNG ở đây: bảo đảm đang kiểm nằm ở tầng SỰ KIỆN (`AttachmentDeletedEvent`
        //   + hai người nghe), ⛔ không ở controller. Luật 5 đòi đi qua HTTP khi cam kết nằm ở
        //   controller/filter; ép nó qua HTTP ở đây chỉ thêm một lớp không liên quan.
        attachments.delete(tep);

        assertThat(troToiTep(tep))
                .as(
                        """
                        ⛔ Cột vẫn giữ UUID của một tệp đã chết ⇒ cổng vẫn dựng liên kết "Quy trình vận hành" \
                        và liên kết ấy trả 404 CÂM. Ràng buộc ON DELETE SET NULL đọc như một bảo đảm hoàn \
                        chỉnh mà chưa từng bắn — vì xoá ở đây là xoá MỀM.""")
                .isZero();
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<String> xoa(PhienHttp.Phien phien, UUID tep) {
        return phienHttp.goi(phien, HttpMethod.DELETE, "/api/v1/attachments/" + tep, null);
    }

    private boolean conSong(UUID tep) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE public_id = ? AND deleted_at IS NULL", Integer.class, tep);
        return n != null && n > 0;
    }

    private int troToiTep(UUID tep) {
        Integer n = jdbc.queryForObject(
                """
                SELECT count(*) FROM constructions
                 WHERE operating_procedure_attachment_public_id = ?
                    OR protection_plan_attachment_public_id = ?
                """,
                Integer.class,
                tep,
                tep);
        return n == null ? 0 : n;
    }

    /**
     * ⛔ Chèn thẳng bằng SQL, ⛔ không qua {@code AttachmentService.upload}.
     *
     * <p>MinIO trong môi trường kiểm thử là địa chỉ giả ({@code IntegrationTestBase}), nên một lượt
     * tải <i>thành công</i> ⛔ không đi hết đường được. Bài này hỏi về <b>quyền</b> và về <b>tham
     * chiếu</b>, và cả hai chỉ cần một dòng {@code attachments} có thật.
     */
    private UUID taoTep() {
        return jdbc.queryForObject(
                """
                INSERT INTO attachments (owner_type, original_name, storage_bucket, storage_key,
                                         content_type, size_bytes, status, scan_status)
                VALUES (?, 'quy-trinh-van-hanh.pdf', 'kiem-thu', 'a1/' || gen_random_uuid(),
                        'application/pdf', 1024, 'READY', 'CLEAN')
                RETURNING public_id
                """,
                UUID.class,
                OWNER_TYPE);
    }

    /**
     * ⛔ Chèn thẳng bằng SQL — bài này hỏi về <b>tham chiếu</b>, ⛔ không về đường tạo hồ sơ công
     * trình (thứ đã có {@code ConstructionHttpTest} phủ kín).
     */
    private long taoCongTrinh() {
        Long donVi = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        return jdbc.queryForObject(
                """
                INSERT INTO constructions (code, name, construction_type, management_level, org_unit_id,
                                           lifecycle_state, operational_status, created_at)
                VALUES (?, 'Cống kiểm thử A1/T28.34', 'CONG', 'XI_NGHIEP', ?,
                        'DANG_HOAT_DONG', 'BINH_THUONG', now())
                RETURNING id
                """,
                Long.class,
                MA_CONG_TRINH,
                donVi);
    }

    private void donDep() {
        jdbc.update(
                """
                UPDATE constructions
                   SET operating_procedure_attachment_public_id = NULL,
                       protection_plan_attachment_public_id = NULL
                 WHERE operating_procedure_attachment_public_id IN (SELECT public_id FROM attachments WHERE owner_type = ?)
                    OR protection_plan_attachment_public_id IN (SELECT public_id FROM attachments WHERE owner_type = ?)
                """,
                OWNER_TYPE,
                OWNER_TYPE);
        jdbc.update("DELETE FROM attachments WHERE owner_type = ?", OWNER_TYPE);
        jdbc.update("DELETE FROM constructions WHERE code = ?", MA_CONG_TRINH);
    }
}
