package com.songnhue.app.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.content.application.SiteConfigService;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Trần dung lượng tải tệp — <b>đo qua đúng đường trình duyệt đi</b>, không gọi service.
 *
 * <h2>Sự cố bài kiểm này ra đời để chặn</h2>
 *
 * Ngày 30/08/2026, Công ty tải ảnh sơ đồ hệ thống lên staging và nhận <b>500</b>. Log thật:
 *
 * <pre>{@code
 * org.springframework.web.multipart.MaxUploadSizeExceededException: Maximum upload size exceeded
 *   at ...StandardMultipartHttpServletRequest.parseRequest
 *   at ...DispatcherServlet.checkMultipart
 * }</pre>
 *
 * Nguyên nhân: {@code application.yml} <b>chưa từng khai</b> {@code spring.servlet.multipart.*},
 * nên Spring Boot áp mặc định <b>1MB/tệp</b>. Mặc định ấy chặn ở {@code checkMultipart}, tức trước
 * cả controller — nên nó thắng mọi hạn mức nghiệp vụ nằm sau nó.
 *
 * <h2>⚠⚠ VÌ SAO 723 BÀI KIỂM KHÔNG THẤY, VÀ VÌ SAO BÀI NÀY THẤY</h2>
 *
 * {@code AttachmentQuotaTest} có sẵn từ WS-12 và <i>đúng</i>: nó chứng minh
 * {@code limits.upload.max-mb.*} được đọc và quyết định. Nhưng nó gọi
 * {@code attachments.upload(lenh)} — <b>thẳng vào service</b>, không qua bộ phân tích multipart.
 * Trần 1MB nằm ở tầng bài kiểm ấy về nguyên tắc không đi qua.
 *
 * <p>Đó là quy tắc 5 nguyên văn: <i>bài kiểm gọi thẳng service không đi cùng đường với
 * production</i>. Hệ quả nặng hơn một lỗi 500 — cả cơ chế hạn mức đọc từ {@code settings}
 * <b>chưa từng quyết định điều gì</b>, vì nó luôn nằm sau một trần thấp hơn mà không ai thấy
 * (quy tắc 3: canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH).
 *
 * <p>Nó sống được 18 ngày vì một sự tình cờ đo được: <b>39/39 tệp trên staging đều dưới 1MB</b>
 * (lớn nhất 570 kB). Tấm sơ đồ hệ thống là tệp đầu tiên vượt qua — nên đây cũng là quy tắc 25,
 * một bộ canh chỉ chứng minh được điều gì khi gặp dữ liệu THẬT.
 *
 * <h2>Phạm vi bài kiểm này (quy tắc 28)</h2>
 *
 * Canh <b>trần hạ tầng</b> và quan hệ của nó với hạn mức nghiệp vụ. KHÔNG canh từng endpoint tải
 * lên: cả 7 endpoint {@code MultipartFile} của hệ dùng chung một bộ phân tích, nên một chỗ đo được
 * là đủ cho tầng này. Hạn mức riêng của từng loại tệp vẫn thuộc {@code AttachmentQuotaTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadSizeCeilingTest extends IntegrationTestBase {

    /** Mặc định của Spring Boot khi không ai khai {@code spring.servlet.multipart.max-file-size}. */
    private static final long MAC_DINH_SPRING_BOOT = 1024L * 1024L;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize tranMotTep;

    private PhienHttp phien;
    private PhienHttp.Phien quanTriNoiDung;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phien = new PhienHttp(http);
        quanTriNoiDung = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tran_tai", "CONTENT_MANAGER"));
    }

    @AfterEach
    void donDep() {
        CmsFixtures.donDep(jdbc);
        CmsFixtures.datLaiCauHinhSite(jdbc);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    //  1 · Chính xác kịch bản Công ty đã làm hỏng staging
    // ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Ảnh 2MB tải lên được qua HTTP — đúng lượt gọi đã trả 500 trên staging")
    void anhLonHonMotMegaByteDiQuaDuocDuongHttp() throws Exception {
        byte[] anh = anhNhieu(1100, 900);
        // Khẳng định về CHÍNH KÍCH THƯỚC dữ liệu thử: một tấm ảnh vô tình nén xuống dưới 1MB sẽ
        // làm bài kiểm này xanh mà không chứng minh gì (quy tắc 9).
        assertThat(anh.length)
                .as("ảnh thử phải thật sự vượt trần cũ 1MB, nếu không bài kiểm không phân biệt được gì")
                .isGreaterThan((int) MAC_DINH_SPRING_BOOT);

        ResponseEntity<String> phanHoi = taiAnhSoDo(anh, "so-do-he-thong.png");

        assertThat(phanHoi.getStatusCode())
                .as("thân phản hồi: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.OK);

        // Vòng khép kín (quy tắc 27): không dừng ở mã 200 — hỏi CSDL xem byte có tới nơi không.
        String publicId = PhienHttp.giaTriJson(phanHoi.getBody(), "attachmentPublicId");
        Map<String, Object> tep = jdbc.queryForMap(
                "SELECT size_bytes, content_type FROM attachments WHERE public_id = ?::uuid", publicId);
        assertThat(((Number) tep.get("size_bytes")).longValue())
                .as("tệp đã lưu phải lớn hơn trần cũ — nếu không thì có gì đó đã cắt bớt nó")
                .isGreaterThan(MAC_DINH_SPRING_BOOT);

        assertThat(jdbc.queryForObject(
                        "SELECT setting_value FROM settings WHERE setting_key = ?",
                        String.class,
                        SiteConfigService.KEY_HOME_MAP))
                .as("tham số ảnh sơ đồ phải trỏ vào tệp vừa tải — nửa GHI của cặp đọc–ghi")
                .isEqualTo(publicId);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    //  2 · Trần phải là con số ĐÃ ĐƯỢC KHAI, không phải mặc định của framework
    // ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Trần multipart phải được khai tường minh, lớn hơn mặc định 1MB của Spring Boot")
    void tranPhaiDuocKhaiChuKhongDeRoiVeMacDinh() {
        // ⭐ Đây là khẳng định PHÂN BIỆT ĐƯỢC HAI TRẠNG THÁI: "đã cấu hình" và "quên cấu hình".
        //   Xoá khối `spring.servlet.multipart` khỏi application.yml là bài kiểm này đỏ ngay.
        assertThat(tranMotTep.toBytes())
                .as("thiếu khối spring.servlet.multipart thì trần rơi về 1MB và mọi tệp lớn trả 500")
                .isGreaterThan(MAC_DINH_SPRING_BOOT);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    //  3 · Hạn mức nghiệp vụ không được hứa nhiều hơn hạ tầng làm được
    // ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Mọi limits.upload.max-mb.* phải nằm dưới trần hạ tầng")
    void hanMucNghiepVuKhongDuocVuotTranHaTang() {
        List<Map<String, Object>> khoa = jdbc.queryForList("SELECT setting_key, setting_value FROM settings "
                + "WHERE setting_key LIKE 'limits.upload.max-mb.%' ORDER BY setting_key");

        // Khẳng định về SỐ LƯỢNG trước: một câu truy vấn trả 0 dòng làm vòng lặp bên dưới xanh
        // trọn vẹn mà không kiểm gì (quy tắc 7 — phép kiểm chạy qua tập rỗng).
        assertThat(khoa)
                .as("phải có ít nhất 4 khoá hạn mức đã seed (image/document/gis/video)")
                .hasSizeGreaterThanOrEqualTo(4);

        long tranMb = tranMotTep.toMegabytes();
        for (Map<String, Object> dong : khoa) {
            String ten = (String) dong.get("setting_key");
            long giaTri = Long.parseLong((String) dong.get("setting_value"));
            assertThat(giaTri)
                    .as(
                            "%s = %d MB nhưng máy chủ chỉ nhận %d MB — màn hình cấu hình đang hứa "
                                    + "một năng lực không tồn tại",
                            ten, giaTri, tranMb)
                    .isLessThanOrEqualTo(tranMb);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════

    /** Gọi đúng endpoint màn hình Cấu hình giao diện gọi, với đủ Bearer + CSRF + cookie. */
    private ResponseEntity<String> taiAnhSoDo(byte[] noiDung, String ten) {
        return phien.dangTep(
                quanTriNoiDung, "/api/v1/cms/site-config/brand-images/" + SiteConfigService.KEY_HOME_MAP, noiDung, ten);
    }

    /**
     * Ảnh nhiễu ngẫu nhiên — <b>cố ý không nén được</b>.
     *
     * <p>Một ảnh trơn màu 1100×900 ra tệp PNG vài KB, và bài kiểm "tệp 2MB" sẽ gửi đi 3 KB mà vẫn
     * xanh. Nhiễu ngẫu nhiên giữ cho kích thước tệp gần bằng kích thước dữ liệu thô.
     */
    private static byte[] anhNhieu(int rong, int cao) throws Exception {
        BufferedImage img = new BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(20260830L); // hạt cố định: cùng một tệp ở mọi lượt chạy
        for (int y = 0; y < cao; y++) {
            for (int x = 0; x < rong; x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
