package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.content.api.PublicPortalController;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>CN-01.6 — góp ý / đánh giá, kiểm duyệt 100%.</b> WS-36 / T36.8.
 *
 * <h2>⭐⭐ Bài chịu lực là VÒNG KHÉP KÍN, ⛔ không phải từng nửa</h2>
 *
 * <p>"Kiểm duyệt" chỉ có nghĩa nếu <i>đã duyệt</i> khác <i>chưa duyệt</i> ở một chỗ ai đó nhìn
 * thấy được. Dựng màn hình duyệt mà ⛔ không dựng nơi công bố thì nút Duyệt ⛔ không quyết định gì
 * — đúng luật 27, và lượt 28/8 tìm ra sáu lần trong một buổi cùng một triệu chứng: <i>màn hình báo
 * lưu thành công, cổng ⛔ không đổi gì</i>.
 *
 * <p>⇒ {@link #vongKhepKinKiemDuyet()} đi trọn: gửi bằng đường công khai → <b>⛔ không</b> thấy
 * trên cổng → duyệt bằng đường quản trị → <b>thấy</b> trên cổng → ẩn → <b>⛔ không</b> thấy nữa.
 *
 * <h2>Vì sao đi bằng HTTP</h2>
 *
 * <p>Ba cam kết nằm <b>ngoài</b> service: quyền của từng bước do {@code workflow_transitions} khai
 * và engine ép; danh sách nút hiện ra là một endpoint riêng; và ranh giới "trường nào ra cổng"
 * chỉ tồn tại ở tầng record của controller. Gọi thẳng service là kiểm đúng một phần ba (luật 5).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedbackHttpTest extends IntegrationTestBase {

    private static final String GUI = "/api/v1/public/feedbacks";
    private static final String DOC_CONG = "/api/v1/public/feedbacks";
    private static final String QUAN_TRI = "/api/v1/cms/feedbacks";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    /** ⚠ Cần để XOÁ ĐỆM sau mỗi lượt sửa `settings` — xem {@link #datKhoa}. */
    @Autowired
    private SettingService settings;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        duQuyen = phienHttp.dangNhap(taoNguoiDungCoQuyen("t36_fb", "cms:feedback:manage"));
    }

    /**
     * ⚠ Dọn cả {@code notifications}: mỗi lượt gửi sinh một {@code FEEDBACK_RECEIVED}, và để chúng
     * lại là để lớp kiểm chạy sau đếm nhầm — đúng chuyện đã xảy ra với
     * {@code ContactEmailSlaHttpTest} (xanh khi chạy riêng, đỏ trong cả bộ).
     *
     * <p>⛔ Chỉ xoá đúng loại sự kiện của mình, ⛔ không {@code DELETE FROM notifications} trần.
     */
    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type = 'FEEDBACK_RECEIVED'");
        jdbc.update("DELETE FROM notifications WHERE event_type = 'FEEDBACK_RECEIVED'");
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
        jdbc.update("DELETE FROM feedbacks");
    }

    // ═══════════════ Vòng khép kín ═══════════════

    @Test
    @DisplayName("⭐⭐ Gửi → ⛔ KHÔNG lên cổng → duyệt → LÊN cổng → ẩn → ⛔ KHÔNG còn")
    void vongKhepKinKiemDuyet() {
        gui("Nguyễn Văn A", "a@example.invalid", (short) 5, "Cổng thông tin tra cứu rất tiện");

        assertThat(trangThai())
                .as("⛔⛔ Chốt D1: MỌI mục vào Chờ duyệt. Một mặc định khác ở lược đồ là bỏ hẳn "
                        + "khâu kiểm duyệt mà ⛔ không ai thấy")
                .isEqualTo("CHO_DUYET");

        assertThat(congKhai())
                .as("⛔⛔ Chưa duyệt mà đã ra cổng thì bước duyệt ⛔ không chặn gì cả")
                .doesNotContain("tra cứu rất tiện");

        String id = maCongKhai();
        buoc(id, "APPROVE", null, HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("DA_DUYET");

        assertThat(congKhai())
                .as("⛔⛔ Duyệt xong mà cổng ⛔ không đổi gì là nửa cặp đọc–ghi (luật 27) — nút "
                        + "Duyệt trở thành một nút ⛔ không quyết định điều gì")
                .contains("tra cứu rất tiện")
                .contains("Nguyễn Văn A");

        buoc(id, "HIDE", "Trùng nội dung với một mục đã đăng", HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("AN");
        assertThat(congKhai()).doesNotContain("tra cứu rất tiện");

        buoc(id, "SHOW", null, HttpStatus.OK);
        assertThat(congKhai()).contains("tra cứu rất tiện");
    }

    @Test
    @DisplayName("⛔ Bước đòi lý do mà thiếu lý do → 400, và trạng thái ⛔ KHÔNG đổi")
    void thieuLyDoThiKhongChuyen() {
        gui("Ẩn danh", null, null, "Đề nghị bổ sung mục hỏi đáp");
        String id = maCongKhai();

        buoc(id, "REJECT", null, HttpStatus.BAD_REQUEST);

        assertThat(trangThai())
                .as("⛔ Một bước chuyển bị từ chối ⛔ không được để lại nửa kết quả")
                .isEqualTo("CHO_DUYET");
    }

    @Test
    @DisplayName("⭐ TU_CHOI và AN là HAI trạng thái — cả hai ⛔ không lên cổng, nhưng lịch sử khác nhau")
    void tuChoiKhacAn() {
        gui(null, null, (short) 1, "Nội dung bị từ chối");
        String id = maCongKhai();

        buoc(id, "REJECT", "Ngôn từ ⛔ không phù hợp", HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("TU_CHOI");
        assertThat(congKhai()).doesNotContain("Nội dung bị từ chối");
        assertThat(jdbc.queryForObject("SELECT moderation_note FROM feedbacks", String.class))
                .as("⛔ `requires_reason` mà lý do ⛔ không được lưu thì ô nhập chỉ bắt người ta gõ rồi ném đi")
                .contains("không phù hợp");

        // Duyệt lại được — người kiểm duyệt đổi ý là chuyện có thật.
        buoc(id, "APPROVE", null, HttpStatus.OK);
        assertThat(trangThai()).isEqualTo("DA_DUYET");
        assertThat(jdbc.queryForObject("SELECT moderation_note FROM feedbacks", String.class))
                .as("⭐ bước ⛔ không đòi lý do phải XOÁ lý do cũ — nếu không, câu giải thích của "
                        + "bước trước đứng cạnh trạng thái của bước này và người đọc hiểu nhầm")
                .isNull();
    }

    // ═══════════════ Ranh giới dữ liệu ra cổng ═══════════════

    /**
     * ⛔⛔ Bài chịu lực thứ hai — chép khuôn đã chứng minh giá trị ở
     * {@code PublicOperationStatusServiceTest}.
     *
     * <p>⚠ Vế <b>đếm</b> ({@code hasSize}) ⛔ không chia sẻ giả định nào với danh sách tên: một
     * record <b>rỗng</b> làm mọi khẳng định {@code doesNotContain} xanh trọn vẹn (luật 29).
     */
    @Test
    @DisplayName("⛔⛔ Record công khai chỉ có ĐÚNG 4 trường — ⛔ không email, ⛔ không ghi chú kiểm duyệt")
    void recordCongKhaiKhongLoTruongNhayCam() {
        List<String> ten = Arrays.stream(PublicPortalController.PublicFeedbackView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(ten)
                .as("⛔⛔ Mỗi trường thêm vào record này là một trường đi thẳng ra Internet. Bốn "
                        + "trường vắng mặt đều là quyết định có lý do — xem javadoc của record.")
                .containsExactlyInAnyOrder("fullName", "rating", "content", "createdAt");

        // ⚠ Vế đếm: ⛔ không có nó thì một record rỗng cũng qua được `containsExactlyInAnyOrder`?
        //   ⛔ Không — nhưng nó bắt được trường hợp ngược lại: ai đó thêm trường VÀ sửa danh sách
        //   trên cho khớp. Con số 4 buộc lượt sửa ấy phải đi qua một dòng thứ hai.
        assertThat(ten).hasSize(4);

        assertThat(ten)
                .doesNotContain("email")
                .doesNotContain("moderationNote")
                .doesNotContain("status")
                .doesNotContain("publicId");
    }

    @Test
    @DisplayName("⛔⛔ Thân JSON của cổng ⛔ KHÔNG mang email lẫn ghi chú kiểm duyệt — đo trên byte thật")
    void thanJsonCongKhaiSachTruongNhayCam() {
        gui("Lê Văn C", "levanc@example.invalid", (short) 4, "Bản đồ công trình dễ dùng");
        String id = maCongKhai();
        buoc(id, "APPROVE", null, HttpStatus.OK);
        buoc(id, "HIDE", "Ghi chú nội bộ ⛔ không được ra ngoài", HttpStatus.OK);
        buoc(id, "SHOW", null, HttpStatus.OK);
        // Đặt lại một ghi chú để chắc chắn cột KHÔNG rỗng lúc đo.
        jdbc.update("UPDATE feedbacks SET moderation_note = 'Ghi chú nội bộ về người gửi'");

        String than = congKhai();

        assertThat(than).as("⚠ vế chống tập rỗng (luật 7)").contains("Bản đồ công trình dễ dùng");
        assertThat(than)
                .as("⛔⛔ Địa chỉ thư là dữ liệu cá nhân (NĐ 13/2023) và người gửi ⛔ KHÔNG hề đồng "
                        + "ý cho công bố. Đây cũng là cách bơm địa chỉ cho máy quét thu hoạch.")
                .doesNotContain("levanc@example.invalid");
        assertThat(than)
                .as("⛔⛔ `moderation_note` là chỗ cán bộ viết VỀ người gửi — lộ ra là công bố "
                        + "nhận xét nội bộ dưới tên của chính người bị nhận xét")
                .doesNotContain("Ghi chú nội bộ về người gửi");
    }

    @Test
    @DisplayName("⛔ Đường đọc quản trị ĐÓNG với khách vãng lai — 401")
    void duongQuanTriDongVoiKhachVangLai() {
        assertThat(http.getForEntity(QUAN_TRI, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity(QUAN_TRI + "/summary", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ═══════════════ Tổng hợp ═══════════════

    @Test
    @DisplayName("⭐⭐ Điểm trung bình LUÔN kèm mẫu số — và `null` khi chưa ai chấm")
    void tongHopLuonKemMauSo() {
        // Một mục có chấm điểm, một mục ⛔ không — để mẫu số KHÁC tổng số mục đã duyệt.
        gui("A", null, (short) 5, "Rất tốt");
        buoc(maCongKhai(), "APPROVE", null, HttpStatus.OK);
        gui("B", null, null, "Chỉ góp ý, ⛔ không chấm sao");
        String idKhongDiem =
                jdbc.queryForObject("SELECT public_id::text FROM feedbacks WHERE rating IS NULL", String.class);
        buoc(idKhongDiem, "APPROVE", null, HttpStatus.OK);
        // Một mục chưa duyệt — phải nằm NGOÀI phép tính nhưng vẫn đếm được.
        gui("C", null, (short) 1, "Chưa duyệt");

        String than = phienHttp.get(duQuyen, QUAN_TRI + "/summary").getBody();

        assertThat(than).isNotNull();
        assertThat(than)
                .as("⛔⛔ Một con số 5,00 đứng một mình ⛔ không phân biệt được \"5 trên 1 phiếu\" "
                        + "với \"5 trên 500 phiếu\" (luật 9)")
                .contains("\"soCoDiem\":1")
                .contains("\"daDuyet\":2")
                .contains("\"choDuyet\":1")
                .contains("\"tong\":3");
        assertThat(than)
                .as("⭐ Tính ở BE bằng BigDecimal (quy tắc 2 + 3) — ⛔ không phải một `double` chia ở FE")
                .contains("5.00");
    }

    @Test
    @DisplayName("⭐ Chưa mục nào có điểm ⇒ `diemTrungBinh` là null — ⛔ KHÔNG phải 0")
    void chuaAiChamThiNullChuKhongPhaiKhong() {
        gui(null, null, null, "Góp ý ⛔ không kèm điểm");
        buoc(maCongKhai(), "APPROVE", null, HttpStatus.OK);

        String than = phienHttp.get(duQuyen, QUAN_TRI + "/summary").getBody();

        // ⚠ ĐO ĐƯỢC ở lượt chạy đầu: bộ tuần tự hoá của dự án BỎ HẲN trường `null` khỏi JSON, nên
        //   khẳng định `"diemTrungBinh":null` ĐỎ — đỏ vì hình dạng dây, ⛔ không vì hành vi sai.
        //   Sửa thành khẳng định về thứ THẬT SỰ nguy hiểm: một con số 0 bịa ra.
        assertThat(than)
                .as("⚠ vế chống tập rỗng (luật 7): endpoint phải trả về một mục đã duyệt")
                .contains("\"daDuyet\":1")
                .contains("\"soCoDiem\":0");
        assertThat(than)
                .as("⛔⛔ Quy tắc 16: số 0 là một KHẲNG ĐỊNH. \"0 sao\" nói rằng người dùng rất "
                        + "⛔ không hài lòng; \"chưa ai chấm\" thì ⛔ không nói gì. Một "
                        + "`coalesce(...,0)` ở đây bịa ra một mức hài lòng ⛔ chưa ai phát biểu.")
                .doesNotContain("\"diemTrungBinh\":0");

        // ⭐ Vế đối chứng — ⛔ không có nó thì `doesNotContain` ở trên xanh cả khi trường ấy ⛔ không
        //   bao giờ được tuần tự hoá, kể cả lúc CÓ điểm (luật 9: phân biệt hai trạng thái).
        //   `tongHopLuonKemMauSo()` khẳng định chiều còn lại: có điểm thì "5.00" phải ra tới dây.
    }

    // ═══════════════ Cổng gửi vào ═══════════════

    @Test
    @DisplayName("⛔ Điểm ngoài dải 1..5 → 400, và ⛔ KHÔNG hàng nào được ghi")
    void diemNgoaiDaiThiTuChoi() {
        assertThat(guiRaw("{\"rating\":9,\"content\":\"Điểm bịa\"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(dem()).isZero();
    }

    @Test
    @DisplayName("⛔ Thiếu nội dung → 400; nội dung quá dài → 400")
    void thieuHoacQuaDaiThiTuChoi() {
        assertThat(guiRaw("{\"rating\":5,\"content\":\"   \"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(guiRaw("{\"content\":\"%s\"}".formatted("x".repeat(2_001))).getStatusCode())
                .as("⛔ Cột TEXT ⛔ không chặn gì — ⛔ không có trần thì một lượt gửi nhét được vài "
                        + "megabyte vào một hàng, và màn hình kiểm duyệt lãnh hậu quả")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(dem()).isZero();
    }

    @Test
    @DisplayName("⭐ Gửi ẨN DANH được — đây là phiếu khảo sát, ⛔ không phải kênh khiếu nại")
    void guiAnDanhDuoc() {
        assertThat(guiRaw("{\"rating\":4,\"content\":\"Ẩn danh vẫn gửi được\"}").getStatusCode())
                .as("⛔ Bắt buộc họ tên ở đây là chép nhầm luật của `contacts` — nơi "
                        + "`ck_contacts_lien_lac` đòi liên hệ ngược VÌ Công ty phải trả lời được")
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(dem()).isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Tắt `site.feedback.enabled` ⇒ endpoint TỪ CHỐI — ⛔ không chỉ ẩn biểu mẫu")
    void tatCongTacThiEndpointCungTuChoi() {
        datKhoa("site.feedback.enabled", "false");
        try {
            assertThat(guiRaw("{\"content\":\"Gửi khi đã tắt\"}").getStatusCode())
                    .as("⛔⛔ Giao diện ⛔ KHÔNG phải đường vào duy nhất (luật 12): một lượt `curl` "
                            + "thẳng vào endpoint bỏ qua toàn bộ phía trình duyệt")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(dem()).isZero();
        } finally {
            datKhoa("site.feedback.enabled", "true");
        }
    }

    @Test
    @DisplayName("⛔ Tắt `public-list.enabled` ⇒ cổng trả RỖNG dù mục đã duyệt")
    void tatCongBoThiCongRong() {
        gui("D", null, (short) 5, "Đã duyệt nhưng ⛔ không công bố");
        buoc(maCongKhai(), "APPROVE", null, HttpStatus.OK);
        assertThat(congKhai()).contains("⛔ không công bố");

        datKhoa("site.feedback.public-list.enabled", "false");
        try {
            assertThat(congKhai())
                    .as("⛔ \"Có nhận góp ý\" và \"có công bố góp ý\" là HAI quyết định — gộp một "
                            + "khoá thì tắt công bố kéo theo tắt cả việc thu thập")
                    .doesNotContain("⛔ không công bố");
        } finally {
            datKhoa("site.feedback.public-list.enabled", "true");
        }
    }

    @Test
    @DisplayName("⭐ Mỗi bước kiểm duyệt ĐẶT VIỆC dựng lại cổng — lượt GỬI thì ⛔ KHÔNG")
    void chiKiemDuyetMoiXoaDem() {
        gui("E", null, (short) 5, "Đo việc dựng lại cổng");

        assertThat(demViecDungLai())
                .as("⛔⛔ Mục vừa gửi đang CHỜ DUYỆT — cổng ⛔ không đổi gì. Xoá đệm ở đó là đặt "
                        + "một việc dựng lại trang cho MỖI lượt người dân bấm Gửi, kể cả spam.")
                .isZero();

        buoc(maCongKhai(), "APPROVE", null, HttpStatus.OK);

        assertThat(demViecDungLai())
                .as("⛔⛔ Duyệt xong mà ⛔ không xoá đệm thì cổng trễ tới 5 phút — đúng nợ T25.22 "
                        + "và T27.7, và triệu chứng của nó IM LẶNG hoàn toàn")
                .isEqualTo(1);
    }

    // ═══════════════ Bộ canh cấu trúc ═══════════════

    /**
     * ⛔⛔ Cùng khuôn với {@code ContactWorkflowHttpTest#chiConMotDuongGhiTrangThai}.
     *
     * <p>Luật ArchUnit {@code chi_workflow_engine_duoc_goi_applyState} soi <b>lời gọi</b>
     * {@code applyState()}; một phép gán field <i>bên trong</i> entity nằm ngoài tầm nhìn của nó —
     * đã <b>ĐO</b> ở T36.1: chèn một đường tắt gán thẳng status thì luật ấy vẫn 3/3 XANH. Và ⛔
     * không bài kiểm hành vi nào thấy: hai đường đều cho ra cùng một trạng thái.
     */
    @Test
    @DisplayName("⛔⛔ `Feedback` chỉ có ĐÚNG MỘT chỗ gán `status` — và chỗ ấy là `applyState`")
    void chiConMotDuongGhiTrangThai() {
        String nguon = docTuGocKho("backend/content/src/main/java/com/songnhue/content/domain/Feedback.java");

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
                        "⛔⛔ `status` bị gán %d chỗ. Đường ghi thứ hai ⛔ KHÔNG bị ArchUnit bắt và "
                                + "⛔ không bài kiểm hành vi nào thấy — chỉ khác: một đường kiểm quyền, "
                                + "bắn thông báo, ghi nhật ký; đường kia ⛔ không.",
                        soLanGan)
                .isEqualTo(1);

        int viTriApply = nguon.indexOf("public void applyState(String newState)");
        assertThat(nguon.indexOf("this.status ="))
                .as("⛔ Chỗ gán `status` nằm NGOÀI `applyState` — quy tắc 4")
                .isGreaterThan(viTriApply);
    }

    /**
     * ⛔⛔ Chốt D1 tắt bình luận công khai tự do — và khác biệt nằm ở <b>lược đồ</b>, ⛔ không ở tên.
     *
     * <p>Ba cột vắng mặt là ba thứ biến một bảng góp ý thành một bảng bình luận: cây trả lời, gắn
     * vào một bài viết, và một mặc định trạng thái khác {@code CHO_DUYET}.
     */
    @Test
    @DisplayName("⛔⛔ `feedbacks` ⛔ KHÔNG phải bảng bình luận — đo trên LƯỢC ĐỒ đang chạy")
    void khongPhaiBangBinhLuan() {
        List<String> cot = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'feedbacks'", String.class);

        assertThat(cot).as("⚠ vế chống tập rỗng (luật 7)").contains("content", "status", "rating");
        assertThat(cot)
                .as("⛔⛔ Chốt D1 (12/8/2026) TẮT bình luận công khai tự do. Một cột `parent_id` "
                        + "hay `article_id` dựng lại đúng cái D1 đã loại — chỉ khác cái tên bảng.")
                .doesNotContain("parent_id")
                .doesNotContain("article_id");

        assertThat(jdbc.queryForObject(
                        "SELECT column_default FROM information_schema.columns "
                                + "WHERE table_name = 'feedbacks' AND column_name = 'status'",
                        String.class))
                .as("⛔⛔ Mặc định ở LƯỢC ĐỒ là nơi bảo đảm \"kiểm duyệt 100%\" sống — một dòng "
                        + "`if` ở service là chỗ để ngày nào đó có người thêm nhánh \"tự duyệt nếu…\"")
                .contains("CHO_DUYET");
    }

    // ─────────────── Tiện ích ───────────────

    private void gui(String hoTen, String email, Short diem, String noiDung) {
        String than = "{\"fullName\":%s,\"email\":%s,\"rating\":%s,\"content\":%s}"
                .formatted(oJson(hoTen), oJson(email), diem == null ? "null" : diem.toString(), oJson(noiDung));
        assertThat(guiRaw(than).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private ResponseEntity<String> guiRaw(String than) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(GUI, new HttpEntity<>(than, h), String.class);
    }

    private static String oJson(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String congKhai() {
        ResponseEntity<String> r = http.getForEntity(DOC_CONG, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody() == null ? "" : r.getBody();
    }

    private void buoc(String id, String hanhDong, String lyDo, HttpStatus mongDoi) {
        String than = "{\"action\":\"" + hanhDong + "\",\"reason\":" + oJson(lyDo) + "}";
        ResponseEntity<String> r = phienHttp.goi(duQuyen, HttpMethod.POST, QUAN_TRI + "/" + id + "/transitions", than);
        assertThat(r.getStatusCode())
                .as("bước %s — thân: %s", hanhDong, r.getBody())
                .isEqualTo(mongDoi);
    }

    private String maCongKhai() {
        return jdbc.queryForObject("SELECT public_id::text FROM feedbacks ORDER BY id DESC LIMIT 1", String.class);
    }

    private String trangThai() {
        return jdbc.queryForObject("SELECT status FROM feedbacks ORDER BY id DESC LIMIT 1", String.class);
    }

    private int dem() {
        return jdbc.queryForObject("SELECT count(*) FROM feedbacks", Integer.class);
    }

    private int demViecDungLai() {
        return jdbc.queryForObject("SELECT count(*) FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'", Integer.class);
    }

    /**
     * Sửa thẳng CSDL rồi <b>xoá đệm</b>.
     *
     * <h2>⛔⛔ Hai cách bài kiểm này tự nói dối, và cả hai đều in màu xanh</h2>
     *
     * <ol>
     *   <li><b>{@code UPDATE} tác động 0 hàng</b> — khoá chưa được seed. Bài kiểm vẫn chạy tiếp và
     *       xanh <i>vì giá trị mặc định</i>, tức xanh vì một lý do KHÁC với lý do nó khẳng định
     *       (§10.66: seed ghi vào một khoá chưa tồn tại, 0 hàng, ⛔ không một dòng log).
     *   <li><b>{@code SettingPort} đệm trong tiến trình</b> (Caffeine, ⛔ không Redis) — sửa hàng
     *       trong CSDL mà ⛔ không xoá đệm thì {@code getBoolean} trả giá trị CŨ, và cả hai bài
     *       công tắc dưới đây <b>đã ĐỎ đúng vì lý do này</b> ở lượt chạy đầu.
     * </ol>
     */
    private void datKhoa(String khoa, String giaTri) {
        int soHang = jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, khoa);
        assertThat(soHang)
                .as("⛔⛔ `UPDATE` tác động 0 hàng nghĩa là khoá `%s` CHƯA được seed — xem javadoc", khoa)
                .isEqualTo(1);
        settings.invalidate(khoa);
    }

    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T36_FB_PROBE', 'Vai trò kiểm thử WS-36 góp ý') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T36_FB_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T36_FB_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }

    private static String docTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Không đọc được " + duongDanTuongDoi, e);
                }
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
