package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Ma trận phân quyền sửa được từ giao diện — đi qua HTTP.</b> T27.31, CN-05.2.
 *
 * <h2>⛔⛔ Mâu thuẫn ba chiều đã sống suốt hai phase</h2>
 *
 * <p>Đo 08/09/2026, trước lượt vá:
 *
 * <ul>
 *   <li>Migration seed {@code V202608131007:12-13} <b>hứa</b>: <i>"Admin sửa role_permissions qua UI
 *       (CN-05.2)"</i>.
 *   <li>{@code RolesPage.tsx} <b>hiện ra màn hình</b> câu ngược lại: <i>"Ma trận phân quyền là dữ
 *       liệu nền, chỉ xem"</i>.
 *   <li>Mã đứng về phía giao diện: <b>0</b> endpoint ghi, và {@code adm:role:manage} có <b>đúng
 *       1</b> lượt xuất hiện trong toàn kho — dòng <i>miễn kiểm</i> của {@link RbacMatrixTest}.
 * </ul>
 *
 * <p>Lý do được ghi lại cho quyết định khoá màn hình là: <i>"mở cho sửa là để một thao tác nhấp
 * chuột phá vỡ thứ mà cả một bộ kiểm thử đang canh"</i>. Câu ấy <b>sai</b>: {@code RbacMatrixTest}
 * ⛔ không đối chiếu từng dòng ma trận — khẳng định duy nhất chạm tới số lượng là một <b>SÀN</b>
 * ({@code role_permissions >= 300}) đặt ở đó để chặn kiểu hỏng <i>seed ⛔ không chạy</i>. Một quyết
 * định thiết kế đứng hai phase trên một <b>lời mô tả sai về một bài kiểm</b>.
 *
 * <h2>⭐ Ba mảnh nằm ngủ từ Phase 0, mỗi mảnh viết sẵn cho đúng lượt này</h2>
 *
 * <ul>
 *   <li>{@code roles.is_system} — cột ⛔ không ai đọc; bảo đảm "⛔ không sửa được" chỉ nằm trong một
 *       dòng chú thích SQL suốt 26 ngày.
 *   <li>{@code AuthorityLoader.invalidateAll()} — <b>0 nơi gọi</b>, javadoc ghi thẳng <i>"Gọi khi
 *       sửa quyền của một vai trò"</i>.
 *   <li>{@code adm:role:manage} — xem trên.
 * </ul>
 *
 * <h2>Vì sao qua HTTP chứ ⛔ không gọi thẳng service (luật 5)</h2>
 *
 * <p>Bảo đảm quan trọng nhất của lượt này ⛔ <b>không</b> nằm trong service — nó nằm ở
 * {@code AuthorityLoader}, một cache in-process TTL 30 giây <b>ngoài</b> transaction. Gọi thẳng
 * service rồi đọc lại CSDL sẽ xanh trọn vẹn kể cả khi ⛔ không ai gọi {@code invalidateAll()}, vì
 * CSDL <b>luôn</b> đúng ngay lập tức; thứ sai là thứ người dùng thật gặp — quyền vừa cấp mà ⛔ chưa
 * dùng được. Chỉ hai lượt HTTP của <b>một phiên khác</b>, trước và sau lượt sửa, mới phân biệt được
 * hai trạng thái ấy (luật 9).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaTranPhanQuyenHttpTest extends IntegrationTestBase {

    /** Vai trò bị đem ra sửa. ⛔ Không đụng vai trò seed — bài kiểm khác đang đọc chúng. */
    private static final String VAI_TRO_THU = "T27_ROLE_TARGET";

    /** Vai trò của người <b>đi sửa</b>, mang {@code adm:role:manage}. */
    private static final String VAI_TRO_SUA = "T27_ROLE_EDITOR";

    /** Vai trò chỉ xem — chứng minh cổng quyền tách {@code :view} khỏi {@code :manage}. */
    private static final String VAI_TRO_XEM = "T27_ROLE_VIEWER";

    private static final String QUYEN_XEM = "adm:role:view";
    private static final String QUYEN_SUA = "adm:role:manage";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorityLoader authorities;

    private PhienHttp phienHttp;
    private PhienHttp.Phien nguoiSua;
    private PhienHttp.Phien nguoiChiXem;
    private PhienHttp.Phien nguoiBiSua;

    @BeforeAll
    void dungBaPhien() {
        phienHttp = new PhienHttp(http);

        // Người đi sửa: có CẢ `:view` lẫn `:manage`.
        nguoiSua = phienHttp.dangNhap(taoNguoiDung("t27_sua", VAI_TRO_SUA, List.of(QUYEN_XEM, QUYEN_SUA)));
        // Người chỉ xem: chỉ `:view` — cổng quyền phải chặn lượt PUT của họ.
        nguoiChiXem = phienHttp.dangNhap(taoNguoiDung("t27_xem", VAI_TRO_XEM, List.of(QUYEN_XEM)));
        // Người mang vai trò SẼ BỊ SỬA — nhân chứng cho lượt xoá cache.
        nguoiBiSua = phienHttp.dangNhap(taoNguoiDung("t27_bisua", VAI_TRO_THU, List.of(QUYEN_XEM)));
    }

    /**
     * ⚠⚠ Lượt dọn này phải xoá cache, và lý do chính là thứ cả lớp đang kiểm.
     *
     * <p>{@link #datQuyen} ghi <b>thẳng</b> vào {@code role_permissions} bằng JDBC — nó đi vòng qua
     * service, nên ⛔ không ai gọi {@code invalidateAll()}. Bỏ dòng dưới đây thì một bài vừa gỡ sạch
     * quyền qua HTTP (⇒ cache của {@code nguoiBiSua} ghi nhớ "⛔ không có quyền nào") sẽ để lại
     * trạng thái ấy cho bài chạy sau, trong khi CSDL đã được trả về đúng — và bài sau đỏ vì
     * <b>lý do sai</b>, ở một dòng ⛔ không liên quan gì tới thứ nó đang kiểm.
     *
     * <p>⇒ Đúng cái bẫy T27.31 sinh ra để vá, chỉ là ở phía bài kiểm. Ghi ra thay vì im lặng gọi.
     */
    @AfterEach
    void traVaiTroThuVeTrangThaiDau() {
        datQuyen(VAI_TRO_THU, List.of(QUYEN_XEM));
        authorities.invalidateAll();
    }

    // ─────────────── Vòng khép kín nhập → lưu → có hiệu lực ───────────────

    @Test
    @DisplayName("⭐ Đặt lại quyền của một vai trò → CSDL đúng tập mới, và granted_by ĐƯỢC GHI")
    void datLaiQuyenGhiXuongDayDu() {
        ResponseEntity<String> ra = put(nguoiSua, VAI_TRO_THU, List.of(QUYEN_XEM, "adm:audit:view"));
        assertThat(ra.getStatusCode()).as("đặt lại quyền: %s", ra.getBody()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(quyenCua(VAI_TRO_THU)).containsExactlyInAnyOrder(QUYEN_XEM, "adm:audit:view");

        // ⛔ `granted_by` có mặt ở lược đồ từ V202608131002 và tới trước T27.31 CHƯA MỘT DÒNG MÃ NÀO
        //    ghi vào nó — trong khi javadoc của `UserAdminRepository` nêu chính nó là lý do lớp ấy
        //    dùng JDBC thay `@ManyToMany`. Đếm ở đây, không khẳng định suông.
        Integer coNguoiCap = jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                        + "WHERE r.code = ? AND rp.granted_by IS NOT NULL",
                Integer.class,
                VAI_TRO_THU);
        assertThat(coNguoiCap)
                .as("dấu vết 'ai cấp quyền này' — cột `granted_by` trước T27.31 toàn NULL")
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "⭐⭐ Quyền vừa cấp DÙNG ĐƯỢC NGAY ở một phiên khác — phép đo DUY NHẤT chứng minh invalidateAll() được nối")
    void quyenMoiCoHieuLucNgayOPhienKhac() {
        // Trạng thái đầu: vai trò T27_ROLE_TARGET chỉ có `:view`, nên người mang nó ĐỌC được danh
        // mục. Lượt gọi này cũng NẠP cache phân quyền của họ — đó là điều kiện bắt buộc để phép đo
        // sau có nghĩa: một cache rỗng thì tự nó đọc lại DB, và bài kiểm sẽ xanh cả khi bản vá
        // không tồn tại (luật 7 — chưa ai đi qua thì chưa biết đúng hay sai).
        assertThat(phienHttp
                        .get(nguoiBiSua, "/api/v1/admin/users/roles/catalog")
                        .getStatusCode())
                .as("nạp cache: vai trò đang có adm:role:view")
                .isEqualTo(HttpStatus.OK);

        // Người quản trị gỡ sạch quyền của vai trò ấy.
        assertThat(put(nguoiSua, VAI_TRO_THU, List.of()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // ⛔⛔ Đây là khẳng định đắt nhất của cả lớp. Nếu `invalidateAll()` KHÔNG được gọi thì lượt
        //    gọi này trả 200 — cache còn giữ quyền cũ tới hết TTL 30 giây. Hai trạng thái ấy khác
        //    nhau ở đúng một dòng mã, và ⛔ không phép đo nào ở tầng CSDL phân biệt được chúng.
        assertThat(phienHttp
                        .get(nguoiBiSua, "/api/v1/admin/users/roles/catalog")
                        .getStatusCode())
                .as("gỡ quyền rồi mà vẫn gọi được = cache chưa bị xoá; đây là lỗi nghiệm thu, "
                        + "không phải một sự đánh đổi")
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Và chiều ngược lại: cấp lại thì dùng được ngay, ⛔ không phải đăng nhập lại.
        assertThat(put(nguoiSua, VAI_TRO_THU, List.of(QUYEN_XEM)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(phienHttp
                        .get(nguoiBiSua, "/api/v1/admin/users/roles/catalog")
                        .getStatusCode())
                .as("cấp lại quyền phải có hiệu lực ngay — người dùng ⛔ không đăng xuất/đăng nhập lại")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Danh sách RỖNG là thao tác hợp lệ — gỡ sạch quyền của một vai trò")
    void danhSachRongLaHopLe() {
        assertThat(put(nguoiSua, VAI_TRO_THU, List.of()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(quyenCua(VAI_TRO_THU)).isEmpty();
    }

    // ─────────────── Bốn bất biến ───────────────

    @Test
    @DisplayName("⛔ Vai trò hệ thống (SUPER_ADMIN) → ADM-2014, và ma trận của nó KHÔNG suy suyển")
    void vaiTroHeThongKhongSuaDuoc() {
        int truoc = quyenCua("SUPER_ADMIN").size();

        ResponseEntity<String> ra = put(nguoiSua, "SUPER_ADMIN", List.of(QUYEN_XEM));
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ra.getBody()).contains("ADM-2014");

        // ⛔ Khẳng định ở CSDL, ⛔ không chỉ ở mã trạng thái: một 403 trả về SAU khi câu DELETE đã
        //    chạy là kịch bản tệ nhất có thể — người dùng thấy "bị từ chối" mà ma trận đã trống.
        assertThat(quyenCua("SUPER_ADMIN")).hasSize(truoc);
        assertThat(truoc)
                .as("bài kiểm này rỗng nếu SUPER_ADMIN vốn ⛔ không có quyền nào")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("⛔ Mã quyền ⛔ không có trong danh mục → ADM-2015, và ma trận cũ ĐƯỢC HOÀN NGUYÊN")
    void maQuyenKhongCoThatBiTuChoiVaRollback() {
        ResponseEntity<String> ra = put(nguoiSua, VAI_TRO_THU, List.of(QUYEN_XEM, "adm:khong:cothat"));
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ra.getBody()).contains("ADM-2015");

        // ⚠ Chỗ này phân biệt "từ chối" với "từ chối SAU KHI đã xoá". `replaceRoles` — người anh em
        //    của phương thức này — cố ý bỏ qua mã ⛔ không có thật trong im lặng; nếu ta chép khuôn
        //    ấy thì vai trò sẽ mất `adm:role:view` mà màn hình báo lưu thành công.
        assertThat(quyenCua(VAI_TRO_THU))
                .as("tập quyền cũ phải còn nguyên sau một lượt bị từ chối")
                .containsExactly(QUYEN_XEM);
    }

    @Test
    @DisplayName("⛔⛔ Tự gỡ adm:role:manage khỏi vai trò MÌNH ĐANG MANG → ADM-2016")
    void khongTuKhoaDuocChinhMinh() {
        ResponseEntity<String> ra = put(nguoiSua, VAI_TRO_SUA, List.of(QUYEN_XEM));
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ra.getBody()).contains("ADM-2016");

        assertThat(quyenCua(VAI_TRO_SUA))
                .as("người dùng vẫn phải vào lại được màn hình phân quyền")
                .contains(QUYEN_SUA);
    }

    @Test
    @DisplayName("⭐ Nhưng sửa vai trò KHÁC thì gỡ adm:role:manage được — bất biến 4 ⛔ không nới quá tay")
    void goQuyenKhoiVaiTroKhongPhaiCuaMinhThiDuoc() {
        // ⚠ Đối chứng cho bài trên. Một bất biến chặn quá rộng ("⛔ không ai gỡ được adm:role:manage
        //    ở đâu hết") sẽ làm bài `khongTuKhoaDuocChinhMinh` xanh vì LÝ DO SAI, và biến một màn
        //    hình vừa mở ra thành một màn hình ⛔ không sửa được nửa số việc (luật 29).
        assertThat(put(nguoiSua, VAI_TRO_THU, List.of(QUYEN_SUA)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(quyenCua(VAI_TRO_THU)).containsExactly(QUYEN_SUA);

        assertThat(put(nguoiSua, VAI_TRO_THU, List.of(QUYEN_XEM)).getStatusCode())
                .as("gỡ adm:role:manage khỏi vai trò người sửa ⛔ KHÔNG mang thì phải được")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("⛔ Vai trò ⛔ không tồn tại → 404, ⛔ không phải 204 im lặng")
    void vaiTroKhongTonTaiTra404() {
        // Nếu ⛔ không kiểm, câu DELETE … WHERE role_id = (SELECT …) xoá 0 dòng và INSERT chèn 0
        // dòng: màn hình báo *lưu thành công* cho một vai trò ⛔ không tồn tại (luật 9).
        ResponseEntity<String> ra = put(nguoiSua, "KHONG_CO_VAI_TRO_NAY", List.of(QUYEN_XEM));
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────── Cổng quyền ───────────────

    @Test
    @DisplayName("⛔ Chỉ có adm:role:view → ĐỌC được, GHI bị chặn 403")
    void quyenXemKhongKemTheoQuyenSua() {
        assertThat(phienHttp
                        .get(nguoiChiXem, "/api/v1/admin/users/roles/catalog")
                        .getStatusCode())
                .as("`:view` phải đọc được — nếu ⛔ không thì bài dưới xanh vì lý do sai")
                .isEqualTo(HttpStatus.OK);

        assertThat(put(nguoiChiXem, VAI_TRO_THU, List.of()).getStatusCode())
                .as("tách `:view` khỏi `:manage` là có chủ đích — ma trận seed cấp `:view` rộng hơn hẳn")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⭐ Danh mục quyền trả về đủ dày để màn hình dựng được ô đánh dấu")
    void danhMucQuyenKhongRong() {
        ResponseEntity<String> ra = phienHttp.get(nguoiSua, "/api/v1/admin/users/permissions/catalog");
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Chống tập rỗng (luật 7): một endpoint trả `[]` cũng "hoạt động", và màn hình sẽ hiện ra
        // một danh sách trống mà ⛔ không ai báo lỗi. Ngưỡng là SÀN, ⛔ không phải số chính xác —
        // ma trận phải thêm được quyền mới mà ⛔ không làm đỏ bài kiểm này.
        assertThat(ra.getBody()).contains(QUYEN_SUA).contains("cms:").contains("hyd:");
        Integer soQuyen = jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class);
        assertThat(soQuyen).isGreaterThanOrEqualTo(80);
    }

    // ─────────────── Hạ tầng ───────────────

    private ResponseEntity<String> put(PhienHttp.Phien phien, String vaiTro, List<String> quyen) {
        String than = "{\"permissionCodes\":["
                + String.join(",", quyen.stream().map(q -> "\"" + q + "\"").toList()) + "]}";
        return phienHttp.goi(phien, HttpMethod.PUT, "/api/v1/admin/users/roles/" + vaiTro + "/permissions", than);
    }

    private List<String> quyenCua(String vaiTro) {
        return jdbc.queryForList(
                "SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                        + "JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ? ORDER BY p.code",
                String.class,
                vaiTro);
    }

    private String taoNguoiDung(String hau, String vaiTro, List<String> quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update(
                "INSERT INTO roles (code, name) VALUES (?, 'Vai trò kiểm thử T27.31') ON CONFLICT DO NOTHING", vaiTro);
        datQuyen(vaiTro, quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = ? ON CONFLICT DO NOTHING",
                username,
                vaiTro);
        return username;
    }

    private void datQuyen(String vaiTro, List<String> quyen) {
        jdbc.update("DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE code = ?)", vaiTro);
        for (String ma : quyen) {
            jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                            + "WHERE r.code = ? AND p.code = ? ON CONFLICT DO NOTHING",
                    vaiTro,
                    ma);
        }
    }
}
