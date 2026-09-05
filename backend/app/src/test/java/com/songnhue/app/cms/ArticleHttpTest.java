package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Vòng đời bài viết <b>đi qua HTTP thật</b> — trả nợ #65, và giữ chỗ cho một sự cố đã xảy ra.
 *
 * <h2>⚠⚠ Lỗi bài kiểm này ra đời để chặn</h2>
 *
 * {@code GET /api/v1/cms/articles} và {@code GET /api/v1/cms/articles/&#123;id&#125;} trả
 * <b>500 {@code SYS-0001}</b> cho <b>mọi lượt gọi</b> kể từ WS-13 — tức là danh sách bài viết và màn
 * hình sửa bài, toàn bộ phần quản trị nội dung, không dùng được. Nguyên nhân:
 * {@code ArticleController} ánh xạ entity sang DTO <i>trong controller</i>, sau khi giao dịch đã
 * đóng, còn {@code ArticleSummary.of}/{@code ArticleDetail.of} đọc {@code getCategories()} — quan hệ
 * lười → {@code LazyInitializationException}.
 *
 * <p><b>391 bài kiểm xanh không thấy gì</b>, vì {@code ArticleLifecycleTest} gọi thẳng service:
 * phép khẳng định chạy bên trong giao dịch, nơi nạp lười vẫn hoạt động. Nợ #65 không phải mục cho
 * đẹp hồ sơ — nó đang che một sự cố toàn phần, và chỉ đường HTTP mới tái hiện được.
 *
 * <h2>Bốn thứ chỉ tồn tại ở tầng HTTP</h2>
 *
 * <ul>
 *   <li><b>Envelope</b> {@code {success, data, traceId}} — controller trả DTO trần, lớp bọc là của
 *       {@code ResponseEnvelopeAdvice}.
 *   <li><b>{@code @RequirePermission} tầng 2</b> — interceptor, không phải mã trong service.
 *   <li><b>Ràng buộc "phải nêu lý do khi trả bài"</b> — nay nằm ở {@code WorkflowEngine}, khai bằng
 *       {@code workflow_transitions.requires_reason}. Trước 24/8 nó khai cứng trong
 *       {@code ArticleController}, còn giao diện đọc một cờ <i>không ai điền</i>: hai bản sao của
 *       một luật, và thao tác trả bài về sửa hỏng hẳn vì không có ô nào để nhập lý do.
 *   <li><b>{@code created_by}</b> — {@code AuditorAwareImpl} đọc {@code AuditContext} do <i>filter</i>
 *       đặt. Test gọi thẳng service chỉ đặt {@code AuthContext}, nên mọi dòng chúng tạo đều có
 *       {@code created_by = NULL} và cột này <b>chưa từng được kiểm chứng</b> (nợ #66).
 * </ul>
 */
// PER_CLASS để @BeforeAll không phải static — nó cần các bean được tiêm vào thực thể.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleHttpTest extends IntegrationTestBase {

    private static final String DUONG = "/api/v1/cms/articles";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phien;
    private PhienHttp.Phien bienTapVien;
    private PhienHttp.Phien quanTriNoiDung;
    private PhienHttp.Phien khongQuyen;

    /**
     * ⚠⚠ Đăng nhập <b>một lần cho cả lớp</b> — ba tài khoản, không phải ba × số bài kiểm.
     *
     * <p>Chuyển từ {@code @BeforeEach} sang đây ở WS-18. Hạn mức đăng nhập là 30 lượt / 15 phút
     * <b>theo IP</b>, và bộ đếm Caffeine dùng chung cho <i>toàn bộ</i> lượt chạy — riêng lớp này
     * trước đó xin 20 vé. Khi WS-18 thêm một lớp HTTP nữa thì trần vỡ, và <b>lớp bị đỏ lại là lớp
     * khác</b>: người đọc log sẽ đi tìm lỗi ở đúng chỗ không có lỗi nào.
     *
     * <p>📌 Ngân sách này là tài nguyên dùng chung giữa mọi lớp kiểm thử HTTP — xem
     * {@code docs/coding-guide.md} §4.
     */
    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phien = new PhienHttp(http);
        bienTapVien = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_vien", "CONTENT_EDITOR"));
        quanTriNoiDung =
                phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_quantri", "CONTENT_MANAGER"));
        khongQuyen = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "bt_khongquyen"));
    }

    /**
     * Một danh mục có thật do migration seed.
     *
     * <p>{@code categoryPublicIds} mang {@code @NotEmpty}: bài viết bắt buộc thuộc ít nhất một danh
     * mục. Đây cũng chính là thứ làm cho lỗi nạp lười <b>chắc chắn</b> xảy ra ở production — không
     * có bài viết nào không có danh mục, nên không có lượt gọi nào thoát.
     */
    private String danhMucSeed() {
        return jdbc.queryForObject(
                "SELECT public_id::text FROM categories WHERE deleted_at IS NULL ORDER BY id LIMIT 1", String.class);
    }

    private String thanBaiViet(String tieuDe, String slug) {
        return """
                {"title":"%s","slug":"%s","content":"<p>Nội dung</p>","categoryPublicIds":["%s"]}"""
                .formatted(tieuDe, slug, danhMucSeed());
    }

    @Test
    @DisplayName("⭐⭐ Danh sách và chi tiết bài viết trả 200 — chính hai endpoint từng trả 500")
    void danhSachVaChiTietKhongCon500() {
        PhienHttp.Phien vien = bienTapVien;

        String tao = phien.goi(vien, HttpMethod.POST, DUONG, thanBaiViet("Bài kiểm đường HTTP", "bai-kiem-duong-http"))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        ResponseEntity<String> danhSach = phien.get(vien, DUONG);
        assertThat(danhSach.getStatusCode())
                .as(
                        "danh sách bài viết — trả 500 suốt từ WS-13 vì ánh xạ quan hệ lười ngoài "
                                + "giao dịch; body: %s",
                        danhSach.getBody())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> chiTiet = phien.get(vien, DUONG + "/" + publicId);
        assertThat(chiTiet.getStatusCode())
                .as("chi tiết bài viết — cùng nguyên nhân với danh sách; body: %s", chiTiet.getBody())
                .isEqualTo(HttpStatus.OK);

        // Vế thứ hai, và là vế thật sự chứng minh quan hệ đã được nạp: chỉ khẳng định mã 200 thì
        // một bản dựng trả về danh sách rỗng cũng xanh. Trường `categoryNames` chỉ dựng được khi
        // Hibernate đã nạp `categories`.
        assertThat(danhSach.getBody()).contains("\"categoryNames\"");
        assertThat(chiTiet.getBody()).contains("\"categoryPublicIds\"");
    }

    @Test
    @DisplayName("⭐ Bài viết tạo qua HTTP phải có created_by — cột này chưa từng được kiểm (nợ #66)")
    void createdByDuocDienKhiDiQuaHttp() {
        String username = "kiemtra_bt_vien";
        PhienHttp.Phien vien = bienTapVien;

        String tao = phien.goi(
                        vien,
                        HttpMethod.POST,
                        DUONG,
                        thanBaiViet("Bài có dấu vết người tạo", "bai-co-dau-vet-nguoi-tao"))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        Long nguoiTao =
                jdbc.queryForObject("SELECT created_by FROM articles WHERE public_id = ?::uuid", Long.class, publicId);
        Long mongDoi = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);

        assertThat(nguoiTao)
                .as("created_by phải là id của người vừa gọi API. NULL ở đây nghĩa là AuditContext "
                        + "không được đặt, và cả cột dấu vết người tạo/sửa của hệ thống là vô nghĩa")
                .isEqualTo(mongDoi);
    }

    @Test
    @DisplayName("⛔ Thiếu quyền cms:article:view → 403 AUTH-3001, chặn ở interceptor chứ không ở service")
    void thieuQuyenBiChanTang2() {
        // Tài khoản không vai trò nào: đăng nhập được nhưng không có một quyền CMS nào.
        ResponseEntity<String> res = phien.get(khongQuyen, DUONG);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody())
                .as("phải là AUTH-3001 (thiếu quyền), không phải một mã lỗi chung chung")
                .contains("AUTH-3001");
    }

    @Test
    @DisplayName("⭐⭐ `allowedActions` NÓI RA bước nào đòi lý do — thiếu cờ này là giao diện tắc hẳn")
    void allowedActionsNoiRaBuocDoiLyDo() {
        // ⛔ ĐÂY là vế đã hỏng, và nó hỏng độc lập với vế ép buộc bên dưới.
        //
        //    `ApprovalActions` mở ô nhập lý do khi `action.requiresReason` bật. Record
        //    `AllowedAction` trước đây là `(action, label, toState)` — không có cờ đó, không ai
        //    điền — nên người duyệt bấm "Yêu cầu chỉnh sửa" là gửi thẳng, thiếu `reason`, và nhận
        //    đúng lỗi "phải nêu lý do" mà **màn hình không có ô nào để nhập**. Thao tác trả bài về
        //    sửa không dùng được, trong khi bài kiểm vế ép buộc (`traBaiPhaiNeuLyDo`) vẫn xanh
        //    trọn vẹn: nó gọi bằng JSON dựng tay nên không bao giờ chạm vào chỗ hỏng.
        //
        //    Kiểm qua HTTP chứ không gọi thẳng engine: cờ phải đi hết đường tuần tự hoá ra tới dây.
        PhienHttp.Phien quanTri = quanTriNoiDung;
        String tao = phien.goi(quanTri, HttpMethod.POST, DUONG, thanBaiViet("Bài đòi lý do", "bai-doi-ly-do"))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");
        phien.goi(
                quanTri,
                HttpMethod.POST,
                DUONG + "/" + publicId + "/transitions",
                """
                {"action":"SUBMIT"}""");

        String chiTiet = phien.get(quanTri, DUONG + "/" + publicId).getBody();

        assertThat(chiTiet)
                .as("bước trả bài phải mang requiresReason=true, nếu không giao diện không biết phải hỏi")
                .containsPattern("\"action\"\\s*:\\s*\"REQUEST_CHANGES\"[^}]*\"requiresReason\"\\s*:\\s*true");
        assertThat(chiTiet)
                .as("và Duyệt thì KHÔNG đòi lý do — một bản dựng bật cờ cho mọi bước cũng qua được "
                        + "khẳng định trên, nên phải chốt cả vế ngược")
                .containsPattern("\"action\"\\s*:\\s*\"APPROVE\"[^}]*\"requiresReason\"\\s*:\\s*false");
    }

    @Test
    @DisplayName("⛔ Trả bài mà không nêu lý do → 400; luật nằm ở Workflow engine, mọi module dùng chung")
    void traBaiPhaiNeuLyDo() {
        PhienHttp.Phien quanTri = quanTriNoiDung;

        String tao = phien.goi(quanTri, HttpMethod.POST, DUONG, thanBaiViet("Bài chờ duyệt", "bai-cho-duyet-http"))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        assertThat(phien.goi(
                                quanTri,
                                HttpMethod.POST,
                                DUONG + "/" + publicId + "/transitions",
                                """
                                {"action":"SUBMIT"}""")
                        .getStatusCode())
                .as("gửi duyệt phải được, nếu không thì bài kiểm chưa tới được bước cần kiểm")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> khongLyDo = phien.goi(
                quanTri,
                HttpMethod.POST,
                DUONG + "/" + publicId + "/transitions",
                """
                        {"action":"REQUEST_CHANGES"}""");

        assertThat(khongLyDo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Và vế còn lại: có lý do thì đi qua. Chỉ kiểm vế cấm thì một bản dựng từ chối MỌI lượt trả
        // bài cũng xanh trọn vẹn.
        assertThat(phien.goi(
                                quanTri,
                                HttpMethod.POST,
                                DUONG + "/" + publicId + "/transitions",
                                """
                                {"action":"REQUEST_CHANGES","reason":"Thiếu số liệu mực nước"}""")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Envelope + traceId phủ cả lượt thành công lẫn lượt lỗi")
    void envelopePhuCaHaiHuong() {
        PhienHttp.Phien vien = bienTapVien;

        assertThat(phien.get(vien, DUONG).getBody())
                .contains("\"success\":true")
                .contains("\"traceId\"");

        ResponseEntity<String> khong = phien.get(vien, DUONG + "/00000000-0000-0000-0000-000000000000");
        assertThat(khong.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(khong.getBody()).contains("\"success\":false").contains("\"traceId\"");
    }
}
