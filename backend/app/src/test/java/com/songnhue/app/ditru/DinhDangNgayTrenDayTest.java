package com.songnhue.app.ditru;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.org.OrgUnitService;
import com.songnhue.core.domain.org.OrgUnitType;
import com.songnhue.core.infra.identity.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⛔⛔ Ngày tháng trên dây phải là <b>chuỗi ISO-8601</b>, ⛔ không phải số epoch — T11.69.
 *
 * <h2>Vì sao bài kiểm này tồn tại</h2>
 *
 * <p>Lượt di trú Boot 3.5 → 4.1.1 buộc phải dời một khoá cấu hình: Jackson 3 đưa
 * {@code WRITE_DATES_AS_TIMESTAMPS} ra khỏi {@code SerializationFeature}, nên
 * {@code spring.jackson.serialization.write-dates-as-timestamps} đổi thành
 * {@code spring.jackson.datatype.datetime.write-dates-as-timestamps}.
 *
 * <p>Để <b>nguyên khoá cũ</b> thì ứng dụng ⛔ không khởi động được — hỏng ồn ào, tự lộ.
 *
 * <p>⚠ <b>Một khẳng định của chính lượt vá này đã bị bác.</b> Bản nháp bài kiểm nói <i>"xoá khoá ấy
 * thì Jackson ghi ngày thành số"</i>. Lượt kiểm chứng ngược cho thấy ⛔ KHÔNG: gỡ hẳn khoá đi mà bài
 * vẫn xanh, vì {@code DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS.enabledByDefault()} = {@code false}
 * — Jackson 3 đã đảo mặc định so với Jackson 2 (đo thẳng trên jar 3.1.5). Bài kiểm chỉ đỏ khi đặt
 * {@code true}, và đó là điều kiện ĐẠT thật của nó.
 *
 * <p>⇒ Vậy nó canh cái gì? Canh <b>cam kết</b>, ⛔ không canh dòng cấu hình: quy tắc 1 của dự án buộc
 * mọi timestamp ra dây là ISO-8601 UTC. Hôm nay cam kết ấy được giữ bởi một mặc định của thư viện
 * bên thứ ba — thứ vừa đổi đúng một lần trong chính lượt di trú này, và ⛔ không ai hứa nó đứng yên.
 * Một cam kết treo vào mặc định là một cam kết chưa ai canh (luật 3, luật 15).
 *
 * <p>Đây đúng hình dạng đắt nhất của dự án: <i>biên dịch sạch, test xanh, định dạng trên dây đổi</i>
 * (§11.19). Một dòng cấu hình ⛔ không có bộ canh là một dòng chưa ai chứng minh còn hiệu lực
 * (luật 15).
 *
 * <h2>Vì sao đo qua HTTP chứ ⛔ không hỏi bean {@code ObjectMapper}</h2>
 *
 * <p>Hỏi bean rồi kết luận về dây là <b>chia sẻ giả định</b> với chính thứ đang kiểm (luật 29): nó
 * giả sử bộ chuyển đổi HTTP dùng đúng bean ấy. Boot 4 <i>đang</i> làm thế, nhưng đó là một sự thật
 * cần được kiểm chứ ⛔ không phải một tiền đề. Đi qua dây thì cả hai vế cùng được đo một lượt
 * (luật 5).
 */
class DinhDangNgayTrenDayTest extends IntegrationTestBase {

    private static final JsonMapper DOC = new JsonMapper();

    private static final String VAI_TRO = "KIEMTHU_T1169_NGAY";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private OrgUnitService orgUnits;

    private UUID donViDaTao;

    @org.junit.jupiter.api.AfterEach
    void donDep() {
        if (donViDaTao != null) {
            jdbc.update("DELETE FROM org_units WHERE public_id = ?", donViDaTao);
            donViDaTao = null;
        }
    }

    @Test
    @DisplayName("⭐ `occurredAt` của nhật ký kiểm toán ra dây là chuỗi ISO-8601, KHÔNG phải số")
    void ngayRaDayLaChuoiIso() {
        // ⛔ Sinh MỘT dòng nhật ký chắc chắn có, ⛔ đừng trông vào dòng do bài khác để lại: bộ kiểm
        //   dùng chung một container, nên "tình cờ có dữ liệu" là một tiền đề ⛔ không ai bảo đảm.
        //   `OrgUnitService.create` đi qua listener kiểm toán — `AuditChainTest` đã chứng minh.
        donViDaTao = orgUnits.create(
                        "T1169-NGAY",
                        "Đơn vị kiểm thử định dạng ngày",
                        OrgUnitType.PHONG_BAN,
                        goc(),
                        null,
                        null,
                        null,
                        null)
                .getPublicId();

        PhienHttp phien = new PhienHttp(http);
        PhienHttp.Phien quanTri = phien.dangNhap(nguoiDocNhatKy());

        ResponseEntity<String> phanHoi = phien.get(quanTri, "/api/v1/audit-logs?size=1&sort=seq,desc");
        assertThat(phanHoi.getStatusCode().value())
                .as("thân phản hồi: %s", phanHoi.getBody())
                .isEqualTo(200);

        // ⚠ `data` là MẢNG thẳng, ⛔ không có `content` bọc ngoài — `ApiResponse.ofPage` đặt
        //   `page.getContent()` vào thẳng `data` và đẩy phân trang sang `pageMeta` (§2.1).
        JsonNode dong = DOC.readTree(phanHoi.getBody()).path("data").path(0);

        // ⛔ VẾ CHỐNG TẬP RỖNG — bắt buộc, và nó phải đứng TRƯỚC phép so.
        //
        // Không có vế này thì khi bảng nhật ký rỗng, `path("occurredAt")` trả MissingNode:
        // `isNumber()` = false ⇒ bài kiểm XANH trong đúng tình huống nó sinh ra để bắt (luật 7).
        // Nhật ký kiểm toán luôn có dòng vì chính lượt tạo tài khoản ở trên đã ghi một dòng.
        assertThat(dong.isObject())
                .as("⛔ Không có dòng nhật ký nào để đo — bài kiểm này sẽ xanh vì lý do sai")
                .isTrue();

        JsonNode ngay = dong.path("occurredAt");
        assertThat(ngay.isMissingNode() || ngay.isNull())
                .as("⛔ Trường `occurredAt` vắng mặt — ⛔ không có gì để đo")
                .isFalse();

        // Hai vế, cố ý tách rời — chúng phân biệt được HAI trạng thái khác nhau (luật 9):
        //   vế 1 bắt đúng lúc khoá cấu hình bị xoá  (số epoch)
        //   vế 2 bắt lúc nó còn nhưng định dạng sai (chuỗi ⛔ không parse được)
        assertThat(ngay.isNumber())
                .as(
                        """
                        ⛔⛔ `occurredAt` ra dây là SỐ (%s). Nghĩa là khoá \
                        `spring.jackson.datatype.datetime.write-dates-as-timestamps` đã mất hiệu lực — \
                        bị xoá, viết sai đường dẫn, hoặc Jackson lại đổi chỗ nó một lần nữa.""",
                        ngay)
                .isFalse();

        String van = ngay.stringValue();
        assertThat(Instant.parse(van))
                .as("`%s` phải là ISO-8601 parse ngược được, và về đúng UTC", van)
                .isNotNull();
        assertThat(van).endsWith("Z");
    }

    private UUID goc() {
        return jdbc.queryForObject("SELECT public_id FROM org_units WHERE parent_id IS NULL", UUID.class);
    }

    /**
     * Tài khoản mang <b>đúng một</b> quyền {@code adm:audit:view}.
     *
     * <p>⛔ Cố ý ⛔ KHÔNG dùng vai trò {@code SUPER_ADMIN} có sẵn: nó nằm trong nhóm bắt buộc 2FA, nên
     * lượt đăng nhập dừng ở {@code TWO_FACTOR_ENROLL_REQUIRED} và bài kiểm đỏ vì <b>lý do sai</b> —
     * ⛔ không liên quan gì tới định dạng ngày. (Đã đo ở lượt chạy đầu của chính bài này.)
     */
    private String nguoiDocNhatKy() {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ngay_iso");
        jdbc.update(
                "INSERT INTO roles (code, name) VALUES (?, 'Vai trò kiểm thử T11.69') ON CONFLICT DO NOTHING", VAI_TRO);
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code = 'adm:audit:view' ON CONFLICT DO NOTHING",
                VAI_TRO);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = ? ON CONFLICT DO NOTHING",
                username,
                VAI_TRO);
        return username;
    }
}
