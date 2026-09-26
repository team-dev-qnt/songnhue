package com.songnhue.app.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.songnhue.core.application.org.OrgUnitNode;
import com.songnhue.core.application.org.OrgUnitService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * ⭐⭐ <b>Cờ {@code trongPhamVi} trên từng nút của cây đơn vị — T74.11.</b>
 *
 * <h2>Khuyết tật</h2>
 *
 * <p>{@code GET /org-units/selectable} trả {@code service.tree()}, tức <b>toàn cây</b>, cho mọi ô chọn đơn vị của
 * mọi biểu mẫu. Sau T74.8 backend chặn đúng bằng {@code ScopeGuard.requireWritableOrgUnit} — nhưng người dùng chỉ
 * biết điều đó <b>sau khi đã điền xong cả biểu mẫu và bấm Lưu</b>, và câu họ nhận là {@code AUTH-3002}.
 *
 * <p>⚠ Bia mộ ở {@code OrgUnitController} từng ghi {@code /selectable} là <i>"cây có phạm vi"</i>. Câu ấy sai từ
 * lúc viết: cả hai đường đọc gọi đúng một {@code service.tree()}. Và nó <b>phải</b> trả toàn cây — bỏ nút cha ngoài
 * phạm vi đi là nút con <i>trong</i> phạm vi mất đường hiển thị.
 *
 * <h2>Bốn vế, bốn loại hậu quả khác nhau — nên chúng ⛔ thay thế nhau được</h2>
 *
 * <ol>
 *   <li><b>Ngữ nghĩa</b>: cờ phải là <i>cây con</i> ({@code startsWith}), ⛔ phải <i>đúng đơn vị của tôi</i>. Một
 *       phép so bằng là cách viết sai tự nhiên nhất, và nó làm mờ mọi Tổ đội bên dưới chính Xí nghiệp của người
 *       dùng — tức làm mờ đúng nơi họ làm việc.</li>
 *   <li><b>Vế phân biệt</b>: người cấp Công ty phải thấy <b>tất cả</b> {@code true}. ⛔ có vế này thì một bản trả
 *       {@code false} cho mọi nút cũng xanh ở vế 1 (luật 9).</li>
 *   <li><b>Trên dây</b>: cờ phải ra tới JSON <b>đúng tên</b>. Đổi tên trường là giao diện đọc {@code undefined},
 *       tức <i>falsy</i>, tức <b>làm mờ toàn bộ cây</b> — hỏng theo chiều im lặng nhất.</li>
 *   <li><b>Độ dốc</b>: hỏi cờ bằng khoá số ({@code trongPhamVi(Long)}) là một câu {@code SELECT} mỗi nút. Bài này
 *       đo <b>độ dốc</b> chứ ⛔ một ngưỡng tuyệt đối — một ngưỡng tuyệt đối đỏ vì thay đổi vô can, và cách sửa rẻ
 *       nhất khi nó đỏ là nâng con số, tức tự tay tháo bộ canh (§11.17 · T58.6).</li>
 * </ol>
 */
class CoPhamViTrenCayDonViTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T74.11-";

    @Autowired
    private OrgUnitService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private EntityManagerFactory emf;

    private long rootId;
    private String pathRoot;
    private long xnA;
    private String pathA;

    @BeforeEach
    void dung() {
        don();
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        xnA = themDonVi(TIEN_TO + "XN-A", rootId, pathRoot);
        pathA = jdbc.queryForObject("SELECT path FROM org_units WHERE id = ?", String.class, xnA);
        themDonVi(TIEN_TO + "TO-A1", xnA, pathA);
        themDonVi(TIEN_TO + "XN-B", rootId, pathRoot);
    }

    @AfterEach
    void donSau() {
        AuthContext.clear();
        don();
    }

    @Test
    @DisplayName("⭐⭐ Người ở XN-A: XN-A và Tổ đội BÊN DƯỚI nó ⇒ trongPhamVi=true; XN-B và gốc ⇒ false mà VẪN CÓ MẶT")
    void coPhamViLaCayCon() {
        AuthContext.set(nguoiDungTai(xnA, pathA));

        Map<String, OrgUnitNode> theoMa = phang(service.tree());

        // Tiền đề, ⛔ phải kết luận: thiếu vế này thì mọi khẳng định dưới chạy trên tập rỗng (luật 7).
        assertThat(theoMa)
                .as("cây phải mang đủ bốn nút của đồ gá")
                .containsKeys(TIEN_TO + "XN-A", TIEN_TO + "TO-A1", TIEN_TO + "XN-B");

        assertThat(theoMa.get(TIEN_TO + "XN-A").trongPhamVi())
                .as("đơn vị của chính người dùng")
                .isTrue();
        assertThat(theoMa.get(TIEN_TO + "TO-A1").trongPhamVi())
                .as("⛔⛔ Tổ đội NẰM DƯỚI XN-A: một phép so BẰNG sẽ làm mờ đúng nơi người dùng làm việc")
                .isTrue();
        assertThat(theoMa.get(TIEN_TO + "XN-B").trongPhamVi())
                .as("Xí nghiệp khác — lượt Lưu sẽ nhận AUTH-3002, nên ô chọn phải nói trước")
                .isFalse();

        OrgUnitNode goc = theoMa.values().stream()
                .filter(n -> n.path().equals(pathRoot))
                .findFirst()
                .orElseThrow();
        assertThat(goc.trongPhamVi())
                .as("nút CHA của XN-A nằm NGOÀI phạm vi ghi")
                .isFalse();
        assertThat(theoMa)
                .as("⛔⛔ mà nó vẫn phải CÓ MẶT: bỏ nút cha đi là con mất đường hiển thị")
                .isNotEmpty();
    }

    @Test
    @DisplayName("⭐ Vế phân biệt: người cấp Công ty thấy MỌI nút trongPhamVi=true — ⛔ thì 'luôn false' cũng xanh")
    void capCongTyThayTatCa() {
        AuthContext.set(nguoiDungTai(rootId, pathRoot));

        List<OrgUnitNode> tatCa = new ArrayList<>(phang(service.tree()).values());

        assertThat(tatCa).as("chống tập rỗng").hasSizeGreaterThanOrEqualTo(4);
        assertThat(tatCa).as("phạm vi của người ở gốc phủ toàn cây").allMatch(OrgUnitNode::trongPhamVi);
    }

    @Test
    @DisplayName("⭐ Trên DÂY: JSON của /org-units/selectable mang đúng tên `trongPhamVi` — đổi tên ⇒ mờ cả cây")
    void coRaToiJson() {
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t7411_xn_a");
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", xnA, ten);
        PhienHttp phien = new PhienHttp(http);
        PhienHttp.Phien p = phien.dangNhap(ten);

        ResponseEntity<String> tra = phien.goi(p, HttpMethod.GET, "/api/v1/org-units/selectable", null);

        assertThat(tra.getStatusCode()).as("%s", tra.getBody()).isEqualTo(HttpStatus.OK);
        String than = tra.getBody();
        assertThat(than)
                .as("⛔ tên trường đổi ⇒ FE đọc undefined ⇒ falsy ⇒ làm mờ TOÀN BỘ cây")
                .contains("trongPhamVi");
        // Hai trạng thái phải cùng xuất hiện trên MỘT phản hồi: một phía luôn-true hay luôn-false
        // đều đi lọt nếu chỉ hỏi sự có mặt của tên trường (luật 9).
        assertThat(than).contains("\"trongPhamVi\":true");
        assertThat(than).contains("\"trongPhamVi\":false");
    }

    @Test
    @DisplayName("⛔⛔ Độ dốc: thêm 10 đơn vị ⛔ được thêm câu lệnh nào — hỏi cờ bằng khoá số là N+1")
    void hoiCoKhongSinhTruyVanMoi() {
        // ⚠ PHẢI có người đăng nhập: `duongDanTrongPhamVi` và `trongPhamVi(Long)` đều thoát sớm khi
        //    ⛔ có ai đăng nhập ⇒ chạy ⛔ xác thực thì bản HỎNG cũng ⛔ sinh truy vấn nào, và bài này
        //    thành một khẳng định RỖNG xanh trên cả hai trạng thái (luật 9 · luật 10).
        AuthContext.set(nguoiDungTai(xnA, pathA));
        DemTruyVan dem = new DemTruyVan(emf);

        long nho = dem.dem(() -> service.tree());

        for (int i = 0; i < 10; i++) {
            themDonVi(TIEN_TO + "THEM-" + i, xnA, pathA);
        }
        long lon = dem.dem(() -> service.tree());

        assertThat(nho)
                .as("chống tập rỗng: lượt đo phải thấy ít nhất câu lệnh nạp cây")
                .isPositive();
        assertThat(soDonVi())
                .as("nền phải thật sự lớn lên, ⛔ thì phép so độ dốc ⛔ nói gì")
                .isGreaterThanOrEqualTo(13);
        assertThat(lon - nho)
                .as("⛔⛔ 10 đơn vị mới ⇒ %d câu lệnh mới. Hỏi cờ bằng `trongPhamVi(Long)` cho ra +10", lon - nho)
                .isZero();
    }

    // ---- đồ gá ----------------------------------------------------------------

    private Map<String, OrgUnitNode> phang(List<OrgUnitNode> cay) {
        Map<String, OrgUnitNode> ra = new java.util.LinkedHashMap<>();
        gom(cay, ra);
        return ra;
    }

    private void gom(List<OrgUnitNode> nut, Map<String, OrgUnitNode> ra) {
        for (OrgUnitNode n : nut) {
            ra.put(n.code(), n);
            gom(n.children(), ra);
        }
    }

    private long soDonVi() {
        return jdbc.queryForObject("SELECT count(*) FROM org_units WHERE deleted_at IS NULL", Long.class);
    }

    private long themDonVi(String ma, long chaId, String pathCha) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                "Đơn vị kiểm thử " + ma,
                chaId);
        String path = pathCha + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                9974L,
                UUID.randomUUID(),
                "t7411-probe",
                "Người kiểm thử T74.11",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
    }

    private void don() {
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE parent_id IS NULL) "
                + "WHERE username = 'kiemtra_t7411_xn_a'");
        // Xoá từ LÁ lên: `org_units.parent_id` có khoá ngoại.
        jdbc.update(
                "DELETE FROM org_units WHERE code LIKE ? AND parent_id IN "
                        + "(SELECT id FROM org_units WHERE code LIKE ?)",
                TIEN_TO + "%",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM org_units WHERE code LIKE ?", Long.class, TIEN_TO + "%"))
                .as("⚠ đồ gá rò sang lớp chạy sau thì nó đỏ ở một chỗ vô can (T48.8)")
                .isZero();
    }
}
