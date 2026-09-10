package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.security.RequirePermission;

/**
 * Ma trận <b>vai trò × tài nguyên</b> đối chiếu với dữ liệu phân quyền thật trong DB — T10.3,
 * NFR-06 đòi hỏi đúng 100%.
 *
 * <p><b>Vì sao không kiểm bằng cách gọi HTTP cho từng ô.</b> 12 vai trò × 89 quyền là hơn một nghìn
 * ô; dựng phiên đăng nhập cho từng vai trò rồi gọi từng endpoint sẽ mất hàng phút mỗi lần chạy CI, và
 * một bài kiểm chậm là một bài kiểm sớm muộn bị bỏ qua. Cơ chế chặn (tầng 2) đã có
 * {@code PermissionInterceptorTest} và {@code DenyByDefaultTest} lo; cái còn thiếu là <b>bản thân
 * ma trận</b> — hàng trăm dòng phân quyền dịch tay từ {@code function-spec.md} §6, nơi mỗi lỗi gõ
 * đều im lặng.
 *
 * <h2>⛔⛔ Bài kiểm này canh BẤT BIẾN, ⛔ KHÔNG đối chiếu từng dòng — đọc nhầm chỗ này là nguy hiểm</h2>
 *
 * <p>Trước T27.31 (08/09/2026) có <b>ba</b> nơi trong kho khẳng định bài này *"đối chiếu lại từng
 * dòng"* của ma trận, kèm con số <b>334</b> ghi cứng trong văn xuôi: javadoc ở đây, javadoc
 * {@code RolesPage.tsx}, và một dòng trong sổ. Cả ba đều <b>sai</b>. Thứ duy nhất chạm tới số lượng
 * là {@link #matrixIsNotDegenerate()}, và nó khẳng định một <b>SÀN</b> — {@code >= 300} — chứ ⛔
 * không phải một con số chính xác; mục đích của nó là chặn kiểu hỏng tệ nhất (<i>seed ⛔ không chạy,
 * mọi bài ở trên xanh trên bảng rỗng</i>), ⛔ không phải khoá ma trận lại.
 *
 * <p>Sai lệch ấy ⛔ không vô hại: nó chính là lý do màn hình Vai trò & phân quyền bị để <b>chỉ
 * xem</b> suốt Phase 0–1 — <i>"mở cho sửa là để một thao tác nhấp chuột phá vỡ thứ mà cả một bộ
 * kiểm thử đang canh"</i>. Bộ kiểm thử ⛔ không canh thứ ấy, và ⛔ chưa từng canh. Một quyết định
 * thiết kế đứng trên một lời mô tả sai về một bài kiểm.
 *
 * <p>⇒ Sửa ma trận từ giao diện ⛔ <b>không</b> làm bài nào ở đây đỏ, miễn là bốn bất biến còn đúng
 * (xem {@code UserAdminService#replacePermissionsOfRole}). Đó là thiết kế, ⛔ không phải lỗ hổng:
 * bài kiểm canh <i>hình dạng hợp lệ</i> của ma trận, còn <i>nội dung</i> ma trận là quyết định của
 * Công ty và phải đổi được ⛔ không cần deploy (quy tắc 16).
 *
 * <h2>⚠⚠ PHẠM VI CỦA BỘ CANH NÀY — hẹp đi kể từ T27.31 (luật 28)</h2>
 *
 * <p>Sáu bất biến dưới đây chạy trong CI trên một CSDL <b>vừa migrate xong</b>. Chúng canh
 * <b>SEED</b>, ⛔ <b>không</b> canh ma trận đang chạy trên production. Trước T27.31 hai thứ ấy là
 * một — ma trận chỉ đổi được bằng migration, nên seed <i>chính là</i> trạng thái runtime. Nay ⛔
 * không còn: một người quản trị bấm chuột là hai thứ tách nhau, và ⛔ không lượt chạy CI nào nhìn
 * thấy trạng thái sau cú bấm ấy.
 *
 * <p>⛔ Khoảng trống ấy là <b>có chủ đích</b>, ⛔ không phải sót. Ba trong sáu bất biến ở đây
 * (<i>mọi quyền phải gán cho ≥1 vai trò</i> · <i>mọi endpoint phải có vai trò gọi được</i> · <i>vai
 * trò ⛔ không được rỗng quyền</i>) là tiêu chuẩn <b>chất lượng của một bản seed</b>, ⛔ không phải
 * ràng buộc an toàn. Ép chúng lúc chạy sẽ cấm Công ty làm một việc hoàn toàn hợp lệ: <i>tắt hẳn một
 * chức năng bằng cách gỡ quyền của nó khỏi mọi vai trò</i>. Bốn bất biến ép ở service là bốn thứ
 * <b>thật sự ⛔ không quay lui được</b> — khác hẳn về loại.
 *
 * <p>⇒ Cái xanh của lớp này nói: <i>"bản seed trong kho hợp lệ"</i>. Nó ⛔ <b>không</b> nói
 * <i>"ma trận trên production hợp lệ"</i>. Ai cần câu thứ hai thì phải đo trên máy thật.
 *
 * <p><b>Bốn kiểu sai của ma trận, và không kiểu nào tự báo:</b>
 *
 * <ul>
 *   <li><b>Gõ sai mã quyền trong {@code @RequirePermission}</b> — mã không khớp bảng
 *       {@code permissions} thì <i>không ai</i> gọi được endpoint đó, kể cả Super Admin. Người dùng
 *       báo "bấm vào không thấy gì", không ai nghĩ tới lỗi chính tả.
 *   <li><b>Quyền không gán cho vai trò nào</b> — chức năng tồn tại nhưng không tài khoản nào chạm
 *       tới được.
 *   <li><b>Vai trò không có quyền nào</b> — gán người vào đó là họ nhận 403 ở mọi nơi.
 *   <li><b>Vai trò chỉ-đọc lỡ có quyền ghi</b> — leo thang đặc quyền, và là kiểu sai <i>duy nhất</i>
 *       trong bốn kiểu mà không ai phàn nàn, vì mọi thứ đều "chạy được".
 * </ul>
 */
class RbacMatrixTest extends IntegrationTestBase {

    private static final String BASE_PACKAGE = "com.songnhue";

    /** Hành động làm thay đổi dữ liệu — vai trò chỉ-đọc không được giữ bất kỳ cái nào. */
    private static final Set<String> WRITE_ACTIONS = Set.of(
            "create",
            "update",
            "delete",
            "approve",
            "publish",
            "unpublish",
            "import",
            "manage",
            "lock",
            "reset-password",
            "assign-role",
            "restore",
            "backup",
            "close",
            "verify");

    /** Vai trò chỉ được xem — đối chiếu {@code function-spec.md} §6. */
    private static final Set<String> READ_ONLY_ROLES = Set.of("VIEWER");

    /**
     * Quyền đã seed cho Phase 2/3 nhưng chưa chức năng nào dùng.
     *
     * <p>⚠ Mỗi dòng ở đây là một quyền <b>được miễn kiểm</b>. Danh sách phình lên là chuyện dễ xảy
     * ra — thêm một dòng cho hết đỏ là thao tác một dòng — nên
     * {@link #ngoaiLeQuyenPhaseSauVanConDung()} canh chiều ngược lại: quyền nào đã có người dùng thì
     * phải bị gỡ khỏi đây, không được nằm lại.
     */
    private static final Set<String> QUYEN_PHASE_SAU = Set.of(
            "ops:gis-layer:manage", // Tầng GIS — Phase 3
            "ops:gis-layer:view", // Xem tầng GIS — Phase 3
            "ops:report:export", // Kết xuất báo cáo — Phase 3
            "ops:report:view", // Xem báo cáo — Phase 3
            // ⬇ WS-28 đã GỠ ba dòng khỏi danh sách này: `hyd:station:view`,
            //   `hyd:station:manage`, `hyd:api-source:manage`. Danh mục điểm đo / loại chỉ số /
            //   nguồn dữ liệu đã có endpoint thật, nên chúng không còn là "quyền chờ Phase sau".
            // ⬇ WS-33 đã GỠ bốn dòng khỏi danh sách này: `hyd:alert:view`, `hyd:alert:handle`,
            //   `hyd:threshold:view`, `hyd:threshold:manage`. Máy cảnh báo ngưỡng đã có ba
            //   controller thật (`AlertLevelController` · `AlertRuleController` ·
            //   `AlertEventController`) gác bằng đúng bốn quyền ấy, nên chúng không còn là "quyền
            //   chờ Phase sau". ⛔ Đừng thêm lại cho hết đỏ — bài `ngoaiLeQuyenPhaseSauVanConDung()`
            //   canh đúng chiều này.
            "hyd:alert-group:manage", // Nhóm cảnh báo — Phase 2
            // ⬇ WS-31/T31.13 đã GỠ `hyd:measurement:view`: hai màn hình chẩn đoán (Nhật ký đồng bộ
            //   M3.16 · Mã lạ từ nguồn) canh bằng đúng quyền ấy, nên nó không còn là "quyền chờ
            //   Phase sau". ⛔ Đừng thêm lại cho hết đỏ — bài ngoaiLeQuyenPhaseSauVanConDung() canh
            //   đúng chiều này.
            // ⬇ WS-32 đã GỠ `hyd:measurement:review`: màn hình Dữ liệu nghi ngờ gác bằng đúng quyền
            //   ấy, VÀ hai bước chuyển `DUYET`/`XOA` của quy trình HYDRO_READING khai nó ở
            //   `workflow_transitions.required_permission`. ⛔ Đừng thêm lại cho hết đỏ.
            //   ⚠ `hyd:measurement:create` (mới ở V202609041061) CỐ Ý không có mặt ở đây: nó có
            //   endpoint thật ngay từ lượt ra đời — POST /hyd/so-do/nhap-tay.
            // ⭐ `hyd:report:view` RỜI khỏi danh sách này ở WS-34/T34.3 — BC-13 là người đọc đầu
            //   tiên (`HydroReportController`). Giữ lại một quyền đã có cổng dùng là làm bài này
            //   khẳng định điều sai, và một bài kiểm nói sai thì lượt sau người ta nới nó ra.
            // ⭐ `hyd:report:export` RỜI ở WS-34/T34.7 — POST /hyd/bao-cao/xuat và GET
            //   /hyd/bao-cao/tai/{jobId} gác bằng đúng nó. ⚠ Hai quyền này CỐ Ý tách nhau: đo trên
            //   ma trận seed thì XN_OPERATOR và DUTY_OFFICER chỉ có `:view` — họ đọc được báo cáo
            //   trên màn hình nhưng ⛔ không mang được nó ra ngoài, và đó là quyết định của Công ty
            //   chứ ⛔ không phải một chi tiết kỹ thuật.
            // ⬇ WS-51 đã GỠ NĂM dòng khỏi danh sách này — dòng mã HRM đầu tiên (10/09/2026):
            //   `hr:employee:view` · `:create` · `:update` · `:delete` gác `EmployeeController` và
            //   `PositionController`; `hr:employee:view-sensitive` gác `EmployeeSensitiveController`
            //   (đường RIÊNG cho CCCD/lương/tài khoản/MST/BHXH — ⛔ không phải một nhánh `if` bên
            //   trong endpoint chi tiết). ⛔ Đừng thêm lại cho hết đỏ — bài
            //   `ngoaiLeQuyenPhaseSauVanConDung()` canh đúng chiều này.
            //   ⚠ `hr:employee:view` cố ý cũng gác đường ĐỌC danh mục chức vụ: ô "Chức vụ" của biểu
            //   mẫu hồ sơ nạp bằng endpoint ấy, và bắt nó sau một quyền khác là tái lập đúng sự cố
            //   WS-28 — danh sách vĩnh viễn rỗng ⇒ ⛔ không tạo nổi một hồ sơ đầy đủ nào.
            "hr:contract:manage", // Hợp đồng — Phase 3 (CN-04.5)
            "hr:leave:request", // Phép — Phase 3 (CN-04.9)
            "hr:leave:approve", // Duyệt phép — Phase 3 (CN-04.9)
            "hr:leave:view-all", // Xem phép — Phase 3 (CN-04.9)
            "hr:org-chart:view", // Sơ đồ tổ chức — Phase 3 (CN-04.1)
            // ⬇ WS-55 đã GỠ `hr:directory:view`: `DanhBaController` gác bằng đúng quyền ấy ở cả
            //   hai endpoint (danh sách · chi tiết). Nó là mã quyền seed từ 13/08/2026 với **0
            //   endpoint** suốt 28 ngày, và bài `ngoaiLeQuyenPhaseSauVanConDung()` bắt được lượt
            //   gỡ này NGAY khi endpoint đầu tiên ra đời — ⛔ đừng thêm lại cho hết đỏ.
            "hr:report:view", // Báo cáo HR — Phase 3 (CN-04.8)
            "hr:report:export", // Xuất báo cáo HR — Phase 3 (CN-04.8)
            // ⬇ WS-36/T36.8 đã GỠ `cms:feedback:manage`: `FeedbackController` (danh sách · tổng
            //   hợp · bước chuyển · xoá) gác bằng đúng quyền ấy, VÀ năm bước chuyển của quy trình
            //   FEEDBACK khai nó ở `workflow_transitions.required_permission`. ⛔ Đừng thêm lại cho
            //   hết đỏ — bài `ngoaiLeQuyenPhaseSauVanConDung()` canh đúng chiều này.
            "cms:external-doc:view", // CMS — Phase 2
            "cms:external-doc:link", // CMS — Phase 2
            "cms:external-doc:manage-flag", // CMS — Phase 2
            "adm:user:reset-password", // Admin — Phase 2
            "adm:session:view", // Admin — Phase 2
            "adm:session:revoke", // Admin — Phase 2
            "adm:security-event:view" // Admin — Phase 2
            // ⬇ T27.31 đã GỠ `adm:role:manage`: `PUT /admin/users/roles/{roleCode}/permissions` gác
            //   bằng đúng quyền ấy, và màn hình Vai trò & phân quyền nay sửa được (CN-05.2).
            //   ⛔ Đừng thêm lại cho hết đỏ — bài `ngoaiLeQuyenPhaseSauVanConDung()` canh đúng chiều
            //   này, và chính nó là thứ ÉP lượt gỡ này xảy ra: quyền vừa có endpoint thật là bài ấy
            //   đỏ ngay, ⛔ không cần ai nhớ ra phải gỡ.
            // ⭐ Trước T27.31 quyền này có ĐÚNG 1 lượt xuất hiện trong MÃ NGUỒN — chính dòng miễn
            //   kiểm ở đây. Một quyền mà nơi duy nhất nhắc tới nó là danh sách "miễn kiểm nó" thì
            //   ⛔ không bộ canh nào còn đứng sau nó.
            // ⚠ Đo lần đầu ghi "1 lượt trong TOÀN KHO" và con số ấy SAI: `git grep` cho **3 lượt ở
            //   2 tệp** — hai lượt kia ở `.claude/master-tracking.md`, nơi mâu thuẫn ba chiều đã
            //   được ghi sẵn. `rg` mặc định BỎ QUA thư mục ẩn, mà `.claude/` chính là chỗ dự án này
            //   để nguồn sự thật. Một phép đếm hụt vẫn đọc như một phép đếm (§10.75).
            );

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("⚠ Mọi mã quyền khai trong @RequirePermission đều tồn tại trong bảng permissions")
    void everyDeclaredPermissionExistsInTheCatalog() {
        Set<String> declared = declaredPermissionCodes();
        Set<String> catalog = Set.copyOf(jdbc.queryForList("SELECT code FROM permissions", String.class));

        assertThat(declared)
                .as("mã quyền không có trong danh mục thì endpoint chặn TẤT CẢ mọi người, kể cả Super Admin, "
                        + "và không có dấu hiệu nào ngoài việc 'bấm vào không thấy gì'")
                .isSubsetOf(catalog);
        assertThat(declared)
                .as("phải có endpoint nào đó khai quyền, nếu không bài kiểm này rỗng")
                .isNotEmpty();
    }

    @Test
    @DisplayName("⚠ Mọi mã quyền trong danh mục phải có ít nhất một endpoint sử dụng (trừ ngoại lệ có chủ đích)")
    void everyCatalogPermissionIsDeclared() {
        Set<String> declared = usedPermissionCodes();
        Set<String> catalog = Set.copyOf(jdbc.queryForList("SELECT code FROM permissions", String.class));

        Set<String> futurePermissions = QUYEN_PHASE_SAU;

        Set<String> unused = new LinkedHashSet<>(catalog);
        unused.removeAll(declared);
        unused.removeAll(futurePermissions);

        assertThat(unused)
                .as("quyền có trong DB nhưng không có endpoint nào dùng @RequirePermission đòi hỏi "
                        + "tức là công tắc chết — chức năng đó chưa có hoặc đã bị xoá mà quên dọn danh mục")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠⚠ Danh sách quyền 'Phase sau' không được chứa quyền ĐANG dùng")
    void ngoaiLeQuyenPhaseSauVanConDung() {
        Set<String> dangDung = usedPermissionCodes();

        Set<String> nhamLan = new LinkedHashSet<>(QUYEN_PHASE_SAU);
        nhamLan.retainAll(dangDung);

        assertThat(nhamLan)
                .as("⛔ Những quyền này ĐANG được dùng thật mà vẫn nằm trong danh sách miễn kiểm. Bản "
                        + "trước có 6 dòng như vậy — bốn bước duyệt bài viết và hai bước xử lý sự cố — vì "
                        + "phép quét chỉ nhìn @RequirePermission mà bỏ qua workflow_transitions. Gỡ chúng "
                        + "khỏi QUYEN_PHASE_SAU.")
                .isEmpty();
    }

    @Test
    @DisplayName("Mọi quyền đều được gán cho ít nhất một vai trò")
    void noPermissionIsOrphaned() {
        List<String> orphans = jdbc.queryForList(
                "SELECT p.code FROM permissions p WHERE NOT EXISTS "
                        + "(SELECT 1 FROM role_permissions rp WHERE rp.permission_id = p.id) ORDER BY p.code",
                String.class);

        assertThat(orphans)
                .as("quyền không thuộc vai trò nào = chức năng không tài khoản nào chạm tới được")
                .isEmpty();
    }

    @Test
    @DisplayName("Mọi vai trò đều có ít nhất một quyền")
    void noRoleIsEmpty() {
        List<String> empty = jdbc.queryForList(
                "SELECT r.code FROM roles r WHERE NOT EXISTS "
                        + "(SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id) ORDER BY r.code",
                String.class);

        assertThat(empty)
                .as("gán người vào vai trò rỗng là họ nhận 403 ở mọi nơi mà không hiểu vì sao")
                .isEmpty();
    }

    @Test
    @DisplayName("SUPER_ADMIN giữ toàn bộ quyền — kể cả quyền thêm mới về sau")
    void superAdminHoldsEveryPermission() {
        List<String> missing = jdbc.queryForList(
                "SELECT p.code FROM permissions p WHERE NOT EXISTS ("
                        + "  SELECT 1 FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                        + "  WHERE rp.permission_id = p.id AND r.code = 'SUPER_ADMIN') ORDER BY p.code",
                String.class);

        assertThat(missing)
                .as("thêm quyền mới mà quên gán cho SUPER_ADMIN thì không còn ai gỡ được sự cố phân quyền")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Vai trò chỉ-đọc không giữ bất kỳ quyền ghi nào")
    void readOnlyRolesHoldNoWritePermission() {
        Map<String, List<String>> offenders = new LinkedHashMap<>();

        for (String role : READ_ONLY_ROLES) {
            List<String> writes = permissionsOf(role).stream()
                    .filter(code -> WRITE_ACTIONS.contains(actionOf(code)))
                    .toList();
            if (!writes.isEmpty()) {
                offenders.put(role, writes);
            }
        }

        assertThat(offenders)
                .as("leo thang đặc quyền là kiểu sai duy nhất của ma trận mà không ai phàn nàn — "
                        + "mọi thứ vẫn 'chạy được'")
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi endpoint có ít nhất một vai trò gọi được")
    void everyEndpointIsReachableBySomeRole() {
        Map<String, Set<String>> matrix = roleMatrix();
        List<String> unreachable = new ArrayList<>();

        for (String code : declaredPermissionCodes()) {
            boolean reachable = matrix.values().stream().anyMatch(granted -> granted.contains(code));
            if (!reachable) {
                unreachable.add(code);
            }
        }

        assertThat(unreachable)
                .as("endpoint không vai trò nào gọi được = chức năng chết")
                .isEmpty();
    }

    @Test
    @DisplayName("Ma trận đủ dày để bài kiểm này có nghĩa (12 vai trò, ≥80 quyền)")
    void matrixIsNotDegenerate() {
        // Chặn kiểu hỏng tệ nhất: seed không chạy, mọi bài kiểm ở trên đều xanh trên bảng rỗng.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Integer.class))
                .isGreaterThanOrEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class))
                .isGreaterThanOrEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions", Integer.class))
                .isGreaterThanOrEqualTo(300);
    }

    // -------------------------------------------------------------------------

    private Map<String, Set<String>> roleMatrix() {
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        jdbc.query(
                "SELECT r.code AS role_code, p.code AS permission_code FROM role_permissions rp "
                        + "JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id "
                        + "ORDER BY r.code, p.code",
                rs -> {
                    matrix.computeIfAbsent(rs.getString("role_code"), key -> new LinkedHashSet<>())
                            .add(rs.getString("permission_code"));
                });
        return matrix;
    }

    private List<String> permissionsOf(String roleCode) {
        return jdbc.queryForList(
                "SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                        + "JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ? ORDER BY p.code",
                String.class,
                roleCode);
    }

    private static String actionOf(String permissionCode) {
        String[] parts = permissionCode.split(":");
        return parts.length == 3 ? parts[2] : "";
    }

    /** Mọi mã quyền mà mã nguồn thật sự đòi hỏi — nguồn là annotation, không phải danh sách chép tay. */
    /**
     * Tập quyền <b>đang thực sự được dùng</b> — hợp của hai kênh khai báo, không phải một.
     *
     * <p>⚠⚠ Bản trước chỉ quét {@code @RequirePermission}, và đó là lý do danh sách "quyền của Phase
     * sau" phình tới 46 dòng trong đó <b>ít nhất 6 dòng sai</b>: {@code cms:article:submit},
     * {@code approve}, {@code publish}, {@code unpublish}, {@code ops:maintenance:update},
     * {@code ops:maintenance:close-incident} — tất cả đều đang chạy thật, chỉ là chúng được khai ở
     * {@code workflow_transitions.required_permission} chứ không ở annotation.
     *
     * <p>Hậu quả không phải một dòng thừa: một quyền nằm trong danh sách ngoại lệ là một quyền
     * <b>được miễn kiểm</b>. Xoá nhầm nó khỏi bảng {@code permissions}, hoặc gõ sai nó trong một bước
     * chuyển workflow, đều không còn ai bắt.
     */
    private Set<String> usedPermissionCodes() {
        Set<String> codes = new LinkedHashSet<>(declaredPermissionCodes());
        codes.addAll(jdbc.queryForList(
                "SELECT DISTINCT required_permission FROM workflow_transitions WHERE required_permission IS NOT NULL",
                String.class));
        return codes;
    }

    private static Set<String> declaredPermissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Không nạp được lớp " + definition.getBeanClassName(), e);
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                RequirePermission required =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
                if (required != null) {
                    codes.addAll(List.of(required.value()));
                }
            }
        }
        return codes;
    }
}
