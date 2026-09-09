package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>CN-01.4 phần xử lý — quy trình, phân loại, chuyển đơn vị, ghi chú, chặn xoá.</b> WS-36,
 * T36.1 + T36.2.
 *
 * <h2>Vì sao lại đi bằng HTTP, ⛔ không gọi service</h2>
 *
 * <p>Ba trong bốn cam kết của đợt này nằm <b>ngoài</b> service: quyền của từng bước chuyển do
 * {@code workflow_transitions} khai và do engine ép; danh sách nút hiện ra là một endpoint riêng;
 * mã lỗi {@code CMS-2018} chỉ thành 409 sau khi đi qua bộ xử lý ngoại lệ. Gọi thẳng service là kiểm
 * đúng một phần tư — luật 5.
 *
 * <h2>⛔⛔ Bài chịu lực là {@link #chiConMotDuongGhiTrangThai()}</h2>
 *
 * <p>Trước WS-36, {@code Contact} có <b>hai</b> đường đổi {@code status}: engine, và một phép gán
 * field trong {@code danhDauDaDoc()}. Luật ArchUnit
 * {@code SilentFailureRuleTest#chi_workflow_engine_duoc_goi_applyState} ⛔ <b>không</b> thấy đường
 * thứ hai — nó soi lời gọi {@code applyState()}, còn đây là một phép gán bên trong chính entity.
 * Một bài kiểm hành vi cũng ⛔ không thấy: cả hai đường đều cho ra {@code DA_DOC}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactWorkflowHttpTest extends IntegrationTestBase {

    private static final String CONG_KHAI = "/api/v1/public/contacts";
    private static final String QUAN_TRI = "/api/v1/cms/contacts";
    private static final String DANH_MUC = "/api/v1/cms/contact-categories";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        duQuyen = phienHttp.dangNhap(taoNguoiDungCoQuyen("t36_ct", "cms:contact:manage"));
    }

    /**
     * ⚠ Dọn cả {@code notifications}: mỗi lượt gửi biểu mẫu ở đây sinh một {@code CONTACT_RECEIVED}
     * (T36.3), và để chúng lại là để cho lớp kiểm chạy sau đếm nhầm. Đúng chuyện đã xảy ra —
     * {@code ContactEmailSlaHttpTest} đỏ trong cả bộ trong khi xanh khi chạy riêng.
     *
     * <p>⛔ Chỉ xoá đúng loại sự kiện của mình, ⛔ không {@code DELETE FROM notifications} trần.
     */
    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM notifications WHERE event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contact_notes");
        jdbc.update("DELETE FROM contacts");
        jdbc.update("DELETE FROM contact_categories");
    }

    // ═══════════════ T36.1 — quy trình sáu trạng thái ═══════════════

    @Test
    @DisplayName("⭐⭐ Đi trọn sáu trạng thái qua HTTP — mỗi bước ĐO ở CSDL, ⛔ không đọc lại lời API")
    void diTronSauTrangThai() {
        String id = guiMotLienHe();

        assertThat(trangThai()).isEqualTo("MOI");

        buoc(id, "READ", null, HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("DA_DOC");

        buoc(id, "START", null, HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("DANG_XU_LY");

        buoc(id, "REPLY", "Đã gọi điện trả lời ngày 06/09, hẹn kiểm tra hiện trường tuần sau", HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("DA_PHAN_HOI");
        assertThat(jdbc.queryForObject("SELECT resolution_note FROM contacts", String.class))
                .as("⛔ `requires_reason` mà lý do ⛔ không được lưu thì ô nhập chỉ bắt người ta gõ rồi ném đi")
                .contains("Đã gọi điện trả lời");

        buoc(id, "CLOSE", null, HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("DONG");
        assertThat(jdbc.queryForObject("SELECT resolution_note FROM contacts", String.class))
                .as("⭐ bước ⛔ không đòi lý do phải XOÁ lý do cũ — nếu không, câu giải thích của "
                        + "bước trước đứng cạnh trạng thái của bước này và người đọc hiểu nhầm")
                .isNull();

        buoc(id, "ARCHIVE", null, HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("LUU_TRU");
    }

    @Test
    @DisplayName("⛔ Bước đòi lý do mà thiếu lý do → 400, và trạng thái ⛔ KHÔNG đổi")
    void thieuLyDoThiKhongChuyen() {
        String id = guiMotLienHe();
        buoc(id, "READ", null, HttpStatus.OK);
        buoc(id, "START", null, HttpStatus.OK);

        buoc(id, "REPLY", null, HttpStatus.BAD_REQUEST);

        assertThat(trangThai())
                .as("⛔ Một bước chuyển bị từ chối ⛔ không được để lại nửa kết quả")
                .isEqualTo("DANG_XU_LY");
    }

    @Test
    @DisplayName("⛔ Hành động ⛔ không hợp lệ ở trạng thái hiện tại → 422 (SYS-0008)")
    void hanhDongSaiTrangThaiThiTuChoi() {
        String id = guiMotLienHe();
        // ARCHIVE chỉ đi được từ DONG. Từ MOI thì ⛔ không có dòng nào trong `workflow_transitions`.
        buoc(id, "ARCHIVE", null, HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(trangThai()).isEqualTo("MOI");
    }

    @Test
    @DisplayName("⭐ `/actions` trả đúng bước hợp lệ của trạng thái, kèm cờ `requiresReason`")
    void danhSachNutDungTheoTrangThai() {
        String id = guiMotLienHe();

        String oMoi = phienHttp.get(duQuyen, QUAN_TRI + "/" + id + "/actions").getBody();
        assertThat(oMoi).contains("\"READ\"").contains("\"START\"");
        assertThat(oMoi)
                .as("⛔ Ở MOI ⛔ không có đường nào tới DA_PHAN_HOI — giao diện ⛔ không được tự suy")
                .doesNotContain("\"REPLY\"");

        buoc(id, "READ", null, HttpStatus.OK);
        buoc(id, "START", null, HttpStatus.OK);

        String oDangXuLy =
                phienHttp.get(duQuyen, QUAN_TRI + "/" + id + "/actions").getBody();
        assertThat(oDangXuLy).contains("\"REPLY\"");
        assertThat(oDangXuLy)
                .as("⭐ Cờ này là thứ nói cho màn hình biết có phải mở ô nhập lý do hay ⛔ không — "
                        + "thiếu nó thì FE phải giữ một bản sao của luật đang nằm ở CSDL")
                .contains("\"requiresReason\":true");
    }

    /**
     * ⛔⛔ Bài chịu lực — xem javadoc lớp.
     *
     * <p>⚠ Canh <b>cấu trúc</b> (luật 2) và kèm một khẳng định <b>về số lượng</b> (luật 29): mẫu
     * regex và phép đếm ⛔ không chia sẻ giả định nào với nhau, nên một mẫu khớp hụt ⛔ không thể
     * làm cả bài xanh trong im lặng.
     */
    @Test
    @DisplayName("⛔⛔ `Contact` chỉ có ĐÚNG MỘT chỗ gán `status` — và chỗ ấy là `applyState`")
    void chiConMotDuongGhiTrangThai() {
        String nguon = docTuGocKho("backend/content/src/main/java/com/songnhue/content/domain/Contact.java");

        // Đối chứng phải-tìm-thấy: mẫu ⛔ không khớp gì thì hai khẳng định dưới đều xanh vô nghĩa.
        assertThat(nguon)
                .as("⛔ Tệp ⛔ không còn `applyState` — bộ canh này đang soi một thứ đã đổi tên")
                .contains("public void applyState(String newState)");

        Matcher m = Pattern.compile("this\\.status\\s*=").matcher(nguon);
        int soLanGan = 0;
        while (m.find()) {
            soLanGan++;
        }
        assertThat(soLanGan)
                .as(
                        "⛔⛔ `status` bị gán %d chỗ. Đường ghi thứ hai ⛔ KHÔNG bị luật ArchUnit "
                                + "`chi_workflow_engine_duoc_goi_applyState` bắt (luật ấy soi LỜI GỌI "
                                + "applyState, còn đây là phép gán field), và ⛔ không bài kiểm hành vi nào "
                                + "thấy — cả hai đường đều cho ra cùng một trạng thái. Chỉ khác: một đường "
                                + "kiểm quyền, bắn thông báo, ghi nhật ký; đường kia ⛔ không.",
                        soLanGan)
                .isEqualTo(1);

        // Và chỗ gán duy nhất ấy phải nằm trong `applyState`, ⛔ không phải ở một hàm tiện ích nào.
        int viTriApply = nguon.indexOf("public void applyState(String newState)");
        int viTriGan = nguon.indexOf("this.status =");
        assertThat(viTriGan)
                .as("⛔ Chỗ gán `status` nằm NGOÀI `applyState` — quy tắc 4")
                .isGreaterThan(viTriApply);
        assertThat(nguon.indexOf('}', viTriGan))
                .as("⚠ vế neo: phép gán và dấu đóng khối phải cùng nằm sau `applyState`")
                .isGreaterThan(viTriGan);
    }

    // ═══════════════ T36.2 — phân loại · đơn vị · ghi chú · chặn xoá ═══════════════

    @Test
    @DisplayName("⛔⛔ Xoá liên hệ đang XỬ LÝ → 409 CMS-2018, và hàng vẫn còn sống")
    void camXoaKhiDangXuLy() {
        String id = guiMotLienHe();
        buoc(id, "READ", null, HttpStatus.OK);
        buoc(id, "START", null, HttpStatus.OK);

        ResponseEntity<String> xoa = phienHttp.goi(duQuyen, HttpMethod.DELETE, QUAN_TRI + "/" + id, null);

        assertThat(xoa.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(xoa.getBody()).contains("CMS-2018");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts WHERE deleted_at IS NULL", Integer.class))
                .as("một việc đang dở dang thì có người đang chờ trả lời — xoá là xoá luôn dấu vết ấy")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Xoá ở trạng thái khác thì được — xoá MỀM, hàng vẫn nằm trong bảng")
    void xoaMemDuocOTrangThaiKhac() {
        String id = guiMotLienHe();
        buoc(id, "READ", null, HttpStatus.OK);

        ResponseEntity<String> xoa = phienHttp.goi(duQuyen, HttpMethod.DELETE, QUAN_TRI + "/" + id, null);

        assertThat(xoa.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .as("⛔ xoá mềm — bản ghi đã có lịch sử thao tác ⛔ không được biến mất khỏi bảng")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts WHERE deleted_at IS NULL", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("⭐⭐ Phân loại: tạo → gán → TÊN quay ra ở danh sách (vòng khép kín, luật 27)")
    void phanLoaiDiTronVongDocGhi() {
        String maDanhMuc = taoPhanLoai("KIEN_NGHI", "Kiến nghị của người dân");
        String id = guiMotLienHe();

        ResponseEntity<String> gan = phienHttp.goi(
                duQuyen,
                HttpMethod.PATCH,
                QUAN_TRI + "/" + id + "/category",
                "{\"categoryPublicId\":\"" + maDanhMuc + "\"}");
        assertThat(gan.getStatusCode()).as("thân: %s", gan.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(phienHttp.get(duQuyen, QUAN_TRI).getBody())
                .as("⛔ Gán được mà TÊN ⛔ không ra tới danh sách là đúng nửa cặp đọc–ghi mà lượt "
                        + "28/8 tìm ra sáu lần — màn hình báo lưu thành công, danh sách ⛔ không đổi gì")
                .contains("Kiến nghị của người dân");

        // Gỡ phân loại
        phienHttp.goi(duQuyen, HttpMethod.PATCH, QUAN_TRI + "/" + id + "/category", "{\"categoryPublicId\":null}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts WHERE category_id IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ Xoá phân loại đang có liên hệ gán → 409 CMS-2020; TẮT thì được")
    void khongXoaDuocPhanLoaiDangDung() {
        String maDanhMuc = taoPhanLoai("KHIEU_NAI", "Khiếu nại");
        String id = guiMotLienHe();
        phienHttp.goi(
                duQuyen,
                HttpMethod.PATCH,
                QUAN_TRI + "/" + id + "/category",
                "{\"categoryPublicId\":\"" + maDanhMuc + "\"}");

        ResponseEntity<String> xoa = phienHttp.goi(duQuyen, HttpMethod.DELETE, DANH_MUC + "/" + maDanhMuc, null);
        assertThat(xoa.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(xoa.getBody()).contains("CMS-2020");

        ResponseEntity<String> tat = phienHttp.goi(
                duQuyen,
                HttpMethod.PUT,
                DANH_MUC + "/" + maDanhMuc,
                "{\"name\":\"Khiếu nại\",\"active\":false,\"sortOrder\":0}");
        assertThat(tat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT category_id IS NOT NULL FROM contacts WHERE deleted_at IS NULL", Boolean.class))
                .as("⭐ tắt một phân loại ⛔ KHÔNG được làm bản ghi cũ mất phân loại — báo cáo "
                        + "theo phân loại phải đọc lại được lịch sử")
                .isTrue();
    }

    @Test
    @DisplayName("⛔ Mã phân loại trùng → 409 CMS-2019")
    void maPhanLoaiTrungThiTuChoi() {
        taoPhanLoai("GOP_Y", "Góp ý");
        ResponseEntity<String> lai = phienHttp.goi(
                duQuyen, HttpMethod.POST, DANH_MUC, "{\"code\":\"GOP_Y\",\"name\":\"Góp ý khác\",\"sortOrder\":1}");
        assertThat(lai.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lai.getBody()).contains("CMS-2019");
    }

    /**
     * ⚠ Đo ở <b>migration</b>, ⛔ không ở CSDL đang chạy.
     *
     * <p>{@code @AfterEach} của lớp này xoá sạch {@code contact_categories}, nên một phép đếm trên
     * bảng sẽ trả 0 <i>dù migration có seed hay không</i> — nó ⛔ không phân biệt được hai trạng
     * thái (luật 9). Câu hỏi thật là <i>"tệp migration có chèn dòng nào ⛔ không"</i>.
     */
    @Test
    @DisplayName("⭐ Danh mục phân loại RỖNG lúc dựng — migration ⛔ không seed giá trị mẫu nào")
    void danhMucRaDoiRong() {
        String migration = docTuGocKho("backend/content/src/main/resources/db/migration/cms/"
                + "V202609061065__cms_contact_workflow_phan_loai_ghi_chu.sql");

        // Đối chứng phải-tìm-thấy: ⛔ không có nó thì một tệp bị đổi tên làm bài này xanh vô nghĩa.
        assertThat(migration)
                .as("⛔ Bộ canh đang soi một tệp ⛔ không còn dựng bảng này")
                .contains("CREATE TABLE contact_categories");

        assertThat(migration)
                .as("⛔ Danh sách phân loại là của CÔNG TY và chưa có văn bản nào cấp nó. Seed "
                        + "\"Góp ý / Khiếu nại\" cho đẹp màn hình là biến một ô CHƯA AI QUYẾT thành "
                        + "một ô trông như đã cấu hình xong.")
                .doesNotContain("INSERT INTO contact_categories");
    }

    @Test
    @DisplayName("⭐ Ghi chú nội bộ: thêm → đọc lại; ⛔ và đường đọc ĐÓNG với khách vãng lai")
    void ghiChuNoiBoDiTronVong() {
        String id = guiMotLienHe();

        ResponseEntity<String> them = phienHttp.goi(
                duQuyen,
                HttpMethod.POST,
                QUAN_TRI + "/" + id + "/notes",
                "{\"content\":\"Đã chuyển XN Thanh Trì kiểm tra hiện trường\"}");
        assertThat(them.getStatusCode()).as("thân: %s", them.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(phienHttp.get(duQuyen, QUAN_TRI + "/" + id + "/notes").getBody())
                .contains("Đã chuyển XN Thanh Trì");

        // ⛔⛔ Ghi chú nội bộ là chỗ cán bộ viết về người dân. Một lượt gọi ẩn danh phải là 401.
        assertThat(http.getForEntity(QUAN_TRI + "/" + id + "/notes", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("⭐ Chuyển đơn vị xử lý — TÊN đơn vị quay ra ở danh sách")
    void chuyenDonViDiTronVong() {
        String maDonVi = jdbc.queryForObject(
                "SELECT public_id::text FROM org_units WHERE deleted_at IS NULL ORDER BY id LIMIT 1", String.class);
        assertThat(maDonVi)
                .as("⚠ vế chống tập rỗng (luật 7): ⛔ không có đơn vị nào thì bài này ⛔ " + "không kiểm gì cả")
                .isNotNull();
        String tenDonVi =
                jdbc.queryForObject("SELECT name FROM org_units WHERE public_id::text = ?", String.class, maDonVi);

        String id = guiMotLienHe();
        ResponseEntity<String> chuyen = phienHttp.goi(
                duQuyen,
                HttpMethod.PATCH,
                QUAN_TRI + "/" + id + "/assignment",
                "{\"orgUnitPublicId\":\"" + maDonVi + "\"}");

        assertThat(chuyen.getStatusCode()).as("thân: %s", chuyen.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chuyen.getBody()).contains(tenDonVi);
        assertThat(jdbc.queryForObject("SELECT assigned_org_unit_id IS NOT NULL FROM contacts", Boolean.class))
                .isTrue();
    }

    // ─────────────── Tiện ích ───────────────

    private String guiMotLienHe() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> gui = http.postForEntity(
                CONG_KHAI,
                new HttpEntity<>(
                        """
                        {
                          "fullName": "Trần Thị B",
                          "email": "tranthib@example.invalid",
                          "phone": null,
                          "subject": "Phản ánh cống Liên Mạc",
                          "content": "Cống rò rỉ, đề nghị Công ty kiểm tra."
                        }
                        """,
                        h),
                String.class);
        assertThat(gui.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return jdbc.queryForObject("SELECT public_id::text FROM contacts", String.class);
    }

    private void buoc(String id, String hanhDong, String lyDo, HttpStatus mongDoi) {
        String than =
                "{\"action\":\"" + hanhDong + "\",\"reason\":" + (lyDo == null ? "null" : "\"" + lyDo + "\"") + "}";
        ResponseEntity<String> r = phienHttp.goi(duQuyen, HttpMethod.POST, QUAN_TRI + "/" + id + "/transitions", than);
        assertThat(r.getStatusCode())
                .as("bước %s — thân: %s", hanhDong, r.getBody())
                .isEqualTo(mongDoi);
    }

    private String trangThai() {
        return jdbc.queryForObject("SELECT status FROM contacts", String.class);
    }

    private String taoPhanLoai(String ma, String ten) {
        ResponseEntity<String> r = phienHttp.goi(
                duQuyen,
                HttpMethod.POST,
                DANH_MUC,
                "{\"code\":\"" + ma + "\",\"name\":\"" + ten + "\",\"sortOrder\":0}");
        assertThat(r.getStatusCode()).as("thân: %s", r.getBody()).isEqualTo(HttpStatus.OK);
        return PhienHttp.giaTriJson(r.getBody(), "publicId");
    }

    private static String docTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException("Không đọc được " + duongDanTuongDoi, e);
                }
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("Không tìm thấy " + duongDanTuongDoi);
    }

    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T36_CONTACT_PROBE', 'Vai trò kiểm thử WS-36') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T36_CONTACT_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T36_CONTACT_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
