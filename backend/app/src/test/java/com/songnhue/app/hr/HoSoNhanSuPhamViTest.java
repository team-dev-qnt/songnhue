package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hr.application.EmployeeFilter;
import com.songnhue.hr.application.EmployeeForm;
import com.songnhue.hr.application.EmployeeService;
import com.songnhue.hr.domain.Employee;

/**
 * Phạm vi đơn vị của hồ sơ CBNV — CN-04.7 / SRS M4.13, WS-51.
 *
 * <h2>⛔⛔ Vì sao lớp này chạy ở tầng SERVICE chứ ⛔ KHÔNG qua HTTP</h2>
 *
 * <p>Đây là ngoại lệ có chủ đích với luật 5 (<i>"cam kết nằm ở controller/filter thì phải kiểm qua
 * HTTP"</i>), và lý do đo được: {@code PhienHttp.taoNguoiDung} <b>luôn</b> đặt tài khoản kiểm thử ở
 * nút gốc {@code org_units.code = 'CTY'} — nó cố ý ⛔ không đẻ nhánh mới trong cây tổ chức. Mà path
 * của nút gốc ({@code /1/}) là <b>tiền tố của MỌI path</b>, nên điều kiện lọc
 * {@code path LIKE '/1/%'} khớp toàn bộ bảng.
 *
 * <p>⇒ Một bài kiểm phạm vi đi qua HTTP với bộ đồ gá hiện có <b>về nguyên tắc ⛔ không thể lộ ra
 * gì</b>: nó sẽ xanh y hệt nhau dù bộ lọc chạy đúng, chạy sai, hay ⛔ không chạy. Đó chính là hình
 * dạng "phép kiểm chạy qua tập rỗng vẫn xanh trọn vẹn" (luật 7) — thứ đã giấu tầng 3 phân quyền
 * suốt cả Phase 0. Chỗ duy nhất đặt được người dùng ở một Xí nghiệp <i>lá</i> là
 * {@code AuthContext} đặt tay, đúng khuôn {@code ScopeFilterEndToEndTest}.
 *
 * <p>⚠ Bảo đảm ở tầng HTTP (quyền {@code hr:employee:*}, envelope, mã lỗi) là việc của
 * {@code HoSoNhanSuHttpTest} — hai lớp phủ hai vế khác nhau, ⛔ không thay thế nhau được.
 *
 * <h2>Chống-tập-rỗng: bài nào cũng phải có một vế "PHẢI THẤY"</h2>
 *
 * <p>Một bộ lọc hỏng kiểu {@code WHERE 1 = 0} cắt sạch mọi thứ và làm mọi khẳng định
 * <i>"⛔ không thấy hồ sơ của B"</i> xanh. Vì vậy mỗi bài ở đây kèm một khẳng định rằng dữ liệu
 * mốc <b>đang có thật</b> — §11.19 đã trả giá đúng chỗ này: bài kiểm chứng ngược của lượt ấy đỏ ở
 * vế chống-tập-rỗng chứ ⛔ không ở vế so sánh.
 *
 * <p>⛔ ⛔ Không seed một dòng CBNV nào ngoài phạm vi bài kiểm: G6-a còn mở, và mọi hàng lớp này
 * tạo ra đều bị dọn ở {@code @AfterEach}.
 *
 * <h2>⚠⚠ Sửa 10/09/2026 — lý do miễn trừ luật 5 của bản đầu ⛔ KHÔNG đứng vững</h2>
 *
 * <p>Bản đầu của lớp này khai rằng một bài phạm vi <b>qua HTTP</b> <i>"về nguyên tắc ⛔ không thể
 * lộ ra gì"</i>, vì {@code PhienHttp.taoNguoiDung} luôn đặt người dùng ở nút gốc {@code CTY}. Vế
 * đầu đúng, kết luận sai: {@code AuthorityLoader:105} suy {@code orgUnitPath} <b>từ
 * {@code users.org_unit_id}</b>, nên <b>một câu {@code UPDATE} trước lượt đăng nhập</b> là có ngay
 * một phiên đứng ở Xí nghiệp lá.
 *
 * <p>Chuyện đó quan trọng vì bộ lọc phạm vi được bật bởi {@code ScopeFilterAspect} <b>quanh
 * {@code @Transactional}</b>, và <i>thứ tự aspect quanh transaction</i> là một khuyết tật đã có
 * thật trong dự án này — nó làm bộ lọc rơi vào một {@code Session} tạm bị vứt đi, và <b>mọi đơn vị
 * đọc được dữ liệu của nhau mà ⛔ không một dòng lỗi nào</b>. Bài gọi thẳng service ⛔ không đi qua
 * chuỗi bộ lọc/interceptor của web nên nó ⛔ không thấy được lớp ấy. ⇒ {@link
 * #traHoSoNgoaiPhamViQuaHttpLaTuChoi()} bổ sung đúng vế đó.
 */
class HoSoNhanSuPhamViTest extends IntegrationTestBase {

    /** Tiền tố dùng chung cho mọi hàng lớp này tạo ra — vừa để dọn, vừa để lọc khỏi dữ liệu lạ. */
    private static final String TIEN_TO = "WS51PV-";

    private static final Pageable TRANG = PageRequest.of(0, 50, Sort.by("code"));

    @Autowired
    private EmployeeService hoSoNhanSu;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String VAI_TRO_HTTP = "KIEMTRA_HR_PHAM_VI_HTTP";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    private long xiNghiepAId;
    private long xiNghiepBId;
    private long toDoiA1Id;
    private UUID xiNghiepAPublicId;
    private UUID xiNghiepBPublicId;
    private UUID toDoiA1PublicId;
    private String pathRoot;
    private String pathA;
    private String pathB;
    private Long rootId;

    @BeforeEach
    void dungCayToChuc() {
        AuthContext.clear();
        don();

        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);

        xiNghiepAId = themDonVi(TIEN_TO + "XN-A", "Xí nghiệp A (kiểm thử)", "XI_NGHIEP", rootId, pathRoot);
        xiNghiepBId = themDonVi(TIEN_TO + "XN-B", "Xí nghiệp B (kiểm thử)", "XI_NGHIEP", rootId, pathRoot);
        pathA = pathRoot + xiNghiepAId + "/";
        pathB = pathRoot + xiNghiepBId + "/";
        toDoiA1Id = themDonVi(TIEN_TO + "TO-A1", "Tổ đội A1 (kiểm thử)", "TO_DOI", xiNghiepAId, pathA);

        xiNghiepAPublicId = publicIdDonVi(xiNghiepAId);
        xiNghiepBPublicId = publicIdDonVi(xiNghiepBId);
        toDoiA1PublicId = publicIdDonVi(toDoiA1Id);
    }

    @AfterEach
    void donSauMoiBai() {
        AuthContext.clear();
        don();
    }

    @Test
    @DisplayName("Xí nghiệp A ⛔ không thấy hồ sơ của Xí nghiệp B — và người ở nút gốc thấy CẢ HAI")
    void danhSachChiTraVeHoSoTrongPhamVi() {
        heThong(() -> {
            taoHoSo("NV-A", "Nguyễn Văn A", xiNghiepAPublicId);
            taoHoSo("NV-B", "Trần Thị B", xiNghiepBPublicId);
        });

        AuthContext.set(nguoiDungTai(xiNghiepAId, pathA));
        assertThat(maTrongPhamVi())
                .as("hồ sơ nhân sự là dữ liệu cá nhân (NĐ 13/2023) — Xí nghiệp A chỉ được thấy người của mình")
                .containsExactly(TIEN_TO + "NV-A");

        AuthContext.set(nguoiDungTai(xiNghiepBId, pathB));
        assertThat(maTrongPhamVi()).containsExactly(TIEN_TO + "NV-B");

        // ⛔⛔ CHỐNG-TẬP-RỖNG. Thiếu vế này thì một bộ lọc cắt sạch mọi thứ — hoặc đơn giản là hai
        // hồ sơ chưa từng được tạo — vẫn cho cả hai khẳng định trên màu xanh (luật 7, §11.19).
        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        assertThat(maTrongPhamVi())
                .as("người ở nút gốc phải thấy CẢ HAI: đây là bằng chứng dữ liệu mốc đang tồn tại thật")
                .containsExactly(TIEN_TO + "NV-A", TIEN_TO + "NV-B");
    }

    @Test
    @DisplayName("⚠ Tra hồ sơ của Xí nghiệp khác → AUTH-3002, ⛔ KHÔNG phải 404")
    void traHoSoNgoaiPhamViLaTuChoiChuKhongPhaiKhongThay() {
        UUID hoSoCuaB = heThongTraVe(() -> taoHoSo("NV-B", "Trần Thị B", xiNghiepBPublicId));

        // Chống-tập-rỗng cho chính bài này: hồ sơ ấy PHẢI đang tồn tại, nếu không thì ngoại lệ ném
        // ra sẽ là ResourceNotFoundException và bài vẫn "đỏ đúng lý do sai".
        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        assertThat(maTrongPhamVi()).contains(TIEN_TO + "NV-B");

        AuthContext.set(nguoiDungTai(xiNghiepAId, pathA));
        assertThatThrownBy(() -> hoSoNhanSu.get(hoSoCuaB))
                .as("CN-04.7 đòi \"từ chối + ghi log\" — 404 lặng lẽ ⛔ không phân biệt được người "
                        + "gõ nhầm với người đang dò public_id")
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(e -> ((PermissionDeniedException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_3002);
    }

    @Test
    @DisplayName("⚠ Lượt từ chối ấy để lại một dòng security_events ACCESS_DENIED_SCOPE")
    void luotTuChoiDeLaiDauVet() {
        UUID hoSoCuaB = heThongTraVe(() -> taoHoSo("NV-B", "Trần Thị B", xiNghiepBPublicId));

        int truoc = demSuKienChanPhamVi(hoSoCuaB);
        assertThat(truoc)
                .as("chưa ai chạm tới hồ sơ này thì ⛔ không được có dòng nào — nếu đã có thì phép "
                        + "đếm trước/sau bên dưới ⛔ không khẳng định được gì")
                .isZero();

        AuthContext.set(nguoiDungTai(xiNghiepAId, pathA));
        assertThatThrownBy(() -> hoSoNhanSu.get(hoSoCuaB)).isInstanceOf(PermissionDeniedException.class);

        assertThat(demSuKienChanPhamVi(hoSoCuaB))
                .as("⛔ không ghi lại thì một người dò tuần tự public_id để tìm hồ sơ đơn vị khác "
                        + "trông y hệt một người gõ nhầm đường dẫn (M5.16)")
                .isEqualTo(truoc + 1);
    }

    @Test
    @DisplayName("Quản lý Xí nghiệp A THẤY hồ sơ của Tổ đội trực thuộc — lọc theo path, ⛔ không theo org_unit_id")
    void capTrenThayDuLieuCapDuoi() {
        heThong(() -> {
            taoHoSo("NV-A1", "Lê Văn A1", toDoiA1PublicId);
            taoHoSo("NV-B", "Trần Thị B", xiNghiepBPublicId);
        });

        AuthContext.set(nguoiDungTai(xiNghiepAId, pathA));

        // Đây là bài phân biệt "CÓ lọc" với "lọc ĐÚNG": so bằng org_unit_id thì hồ sơ của Tổ đội A1
        // (một org_unit_id KHÁC hẳn) biến mất khỏi màn hình của chính người phụ trách nó — mất dữ
        // liệu trong im lặng, ⛔ không một dòng lỗi nào.
        assertThat(maTrongPhamVi())
                .as("hồ sơ thuộc Tổ đội A1 nằm trong cây con của Xí nghiệp A ⇒ phải thấy")
                .containsExactly(TIEN_TO + "NV-A1");
    }

    // -------------------------------------------------------------------------

    /** Mã CBNV trong phạm vi người đang đăng nhập, đã lọc bỏ dữ liệu ⛔ không thuộc lớp này. */
    private List<String> maTrongPhamVi() {
        return hoSoNhanSu.search(EmployeeFilter.rong(), TRANG).getContent().stream()
                .map(Employee::getCode)
                .filter(ma -> ma.startsWith(TIEN_TO))
                .toList();
    }

    private int demSuKienChanPhamVi(UUID publicId) {
        Integer so = jdbc.queryForObject(
                // `detail` là jsonb — phải ép sang text mới LIKE được.
                "SELECT count(*) FROM security_events WHERE event_type = 'ACCESS_DENIED_SCOPE' "
                        + "AND detail::text LIKE ? AND detail::text LIKE ?",
                Integer.class,
                "%" + publicId + "%",
                "%Employee%");
        return so == null ? 0 : so;
    }

    /**
     * Tạo hồ sơ với đúng những trường bắt buộc — ⛔ KHÔNG một trường trang trí nào.
     *
     * <p>{@code EmployeeForm} là biểu mẫu <b>thay toàn phần</b>: mọi {@code null} ở đây được ghi
     * thành {@code null}. Đó là điều bài kiểm này muốn — nó chỉ hỏi về phạm vi đơn vị, và một hồ sơ
     * đầy dữ liệu bịa sẽ làm người đọc tưởng có thêm bảo đảm nào đó được kiểm.
     */
    /**
     * ⛔⛔ Vế <b>qua HTTP</b> của cùng bảo đảm — luật 5.
     *
     * <p>Đặt người dùng thử xuống Xí nghiệp A bằng một câu {@code UPDATE} <b>trước</b> lượt đăng
     * nhập, rồi hỏi hồ sơ của Xí nghiệp B qua đúng đường mà trình duyệt đi. Hai điều chỉ lượt này
     * chứng minh được: chuỗi bộ lọc web + thứ tự aspect quanh giao dịch có giữ đúng phạm vi ⛔
     * không, và mã trạng thái ra tới dây là <b>403</b> chứ ⛔ không phải 404.
     */
    @Test
    @DisplayName("⛔⛔ Qua HTTP: hỏi hồ sơ Xí nghiệp khác → 403 + AUTH-3002, và hồ sơ ĐƠN VỊ MÌNH vẫn 200")
    void traHoSoNgoaiPhamViQuaHttpLaTuChoi() {
        UUID hoSoA = heThongTraVe(() -> taoHoSo("HTTP-A", "Người của A", xiNghiepAPublicId));
        UUID hoSoB = heThongTraVe(() -> taoHoSo("HTTP-B", "Người của B", xiNghiepBPublicId));
        AuthContext.clear();

        donVaiTroHttp();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử phạm vi HTTP', 'Tạm, xoá ở cuối bài', FALSE, now())",
                VAI_TRO_HTTP);
        int soQuyen = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = 'hr:employee:view'",
                VAI_TRO_HTTP);
        assertThat(soQuyen)
                .as("⚠ Chống tập rỗng: mã quyền đổi tên thì lệnh trên gán 0 dòng trong im lặng, và bài "
                        + "dưới đây đỏ với 403 ở CẢ HAI vế — tức xanh vì lý do sai")
                .isEqualTo(1);

        try {
            PhienHttp phienHttp = new PhienHttp(http);
            String tenDangNhap = PhienHttp.taoNguoiDung(users, passwords, jdbc, "hr_pv_http", VAI_TRO_HTTP);

            // ⛔⛔ Đây là câu làm cho bài này KHẢ THI. `PhienHttp.taoNguoiDung` đặt mọi người dùng ở
            //    nút gốc, mà người ở nút gốc thấy TẤT CẢ — nên nếu bỏ câu này thì cả hai khẳng định
            //    dưới đây đều 200 và bài xanh trong đúng tình huống nó sinh ra để bắt.
            int soDoi = jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", xiNghiepAId, tenDangNhap);
            assertThat(soDoi)
                    .as("⛔ ⛔ Không đặt được người dùng thử xuống Xí nghiệp A")
                    .isEqualTo(1);

            PhienHttp.Phien phien = phienHttp.dangNhap(tenDangNhap);

            ResponseEntity<String> trongPhamVi = phienHttp.get(phien, "/api/v1/hr/employees/" + hoSoA);
            assertThat(trongPhamVi.getStatusCode())
                    .as(
                            "⚠ ĐỐI CHỨNG phải-thành-công: thiếu nó thì một hệ từ chối TẤT CẢ cũng làm vế "
                                    + "dưới xanh — và nó sẽ khoá cứng màn hình hồ sơ (luật 7): %s",
                            trongPhamVi.getBody())
                    .isEqualTo(HttpStatus.OK);

            ResponseEntity<String> ngoaiPhamVi = phienHttp.get(phien, "/api/v1/hr/employees/" + hoSoB);
            assertThat(ngoaiPhamVi.getStatusCode())
                    .as("CN-04.7 đòi *từ chối + ghi log*, ⛔ không phải *giả vờ ⛔ không có*: %s", ngoaiPhamVi.getBody())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ngoaiPhamVi.getBody())
                    .as("⛔ 404 sẽ nói dối rằng hồ sơ ⛔ không tồn tại; AUTH-3002 nói đúng chuyện gì xảy ra")
                    .contains("AUTH-3002");
            assertThat(ngoaiPhamVi.getBody())
                    .as("⛔ và thân từ chối ⛔ không được lộ một mẩu nào của hồ sơ ngoài phạm vi")
                    .doesNotContain("Người của B");
        } finally {
            // ⛔⛔ TRẢ người dùng thử về nút gốc TRƯỚC khi dọn vai trò — và đây là một bài học phải
            //    ghi lại: bản đầu quên câu này, `users.org_unit_id` còn trỏ vào Xí nghiệp A, nên
            //    `@AfterEach` ⛔ không xoá nổi cây đơn vị (`users_org_unit_id_fkey`) và **BỐN bài
            //    khác của chính lớp này đỏ** với một thông báo chẳng liên quan gì tới thứ chúng
            //    kiểm. Đúng hình dạng T48.8: rò trạng thái làm đỏ một chỗ khác chỗ hỏng.
            jdbc.update(
                    "UPDATE users SET org_unit_id = ? WHERE org_unit_id IN (?, ?, ?)",
                    rootId,
                    xiNghiepAId,
                    xiNghiepBId,
                    toDoiA1Id);
            // ⛔ `finally`: bỏ lại vai trò tạm sẽ làm `RbacMatrixTest` đỏ ở MỘT LỚP KHÁC.
            donVaiTroHttp();
        }
    }

    private void donVaiTroHttp() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_HTTP);
        jdbc.update(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_HTTP);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_HTTP);
    }

    private UUID taoHoSo(String hau, String hoTen, UUID donViPublicId) {
        EmployeeForm form = new EmployeeForm(
                TIEN_TO + hau,
                hoTen,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                donViPublicId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return hoSoNhanSu.create(form).getPublicId();
    }

    /**
     * Chạy ⛔ không có người đăng nhập — {@code ScopeFilterAspect} ⛔ không bật lọc, nên dựng được
     * dữ liệu ở cả hai Xí nghiệp. Đây đúng là đường mà job nền / lệnh bootstrap đi.
     */
    private void heThong(Runnable viec) {
        AuthContext.clear();
        viec.run();
    }

    private <T> T heThongTraVe(java.util.function.Supplier<T> viec) {
        AuthContext.clear();
        return viec.get();
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                999L,
                UUID.randomUUID(),
                "ws51-pham-vi",
                "Người kiểm thử phạm vi",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private long themDonVi(String ma, String ten, String loai, Long chaId, String pathCha) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, ?, ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                ten,
                loai,
                chaId);
        String path = pathCha + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private UUID publicIdDonVi(long id) {
        return jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, id);
    }

    private void don() {
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        // ⚠ Cố ý KHÔNG dọn `security_events`: vai trò `songnhue_app` ⛔ không có DELETE trên bảng đó
        // (WS-2/T2.7). Phép đếm ở trên lọc theo public_id của chính hồ sơ vừa tạo nên dữ liệu sót
        // ⛔ không ảnh hưởng.
    }
}
