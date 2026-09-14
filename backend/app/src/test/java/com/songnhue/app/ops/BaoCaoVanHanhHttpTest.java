package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Báo cáo vận hành công trình — <b>CN-02.10</b>, đo qua HTTP.
 *
 * <h2>⛔⛔ BỐN mã đã bỏ vĩnh viễn phải CÓ MẶT trong danh mục</h2>
 *
 * <p>BC-01/02/03 mất nguồn (nhật ký vận hành loại khỏi phạm vi — B1/F1, xác nhận bởi G2), BC-04
 * mất nguồn (kế hoạch vụ mùa — A1). Liệt kê ba mã còn sống là để câu hỏi *"BC-01 đâu?"* quay lại ở
 * mọi lượt nghiệm thu — và lần nào cũng phải đi tra tài liệu để trả lời.
 *
 * <p>⚠ Và nó là một <b>trạng thái khác</b> với {@code BCNS-07} của CN-04.8: mã kia *chưa làm được*
 * (chờ G6 — <b>sẽ</b> có), bốn mã ở đây *⛔ không bao giờ làm*. Hai mã lỗi khác nhau ({@code
 * HR-2009} vs {@code OPS-2023}) giữ khác biệt ấy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaoCaoVanHanhHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO_XEM = "KIEMTRA_T591_XEM";
    private static final String VAI_TRO_XUAT = "KIEMTRA_T591_XUAT";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorityLoader authorities;

    private PhienHttp phienXem;
    private PhienHttp phienXuat;
    private PhienHttp.Phien chiXem;
    private PhienHttp.Phien duocXuat;

    @BeforeAll
    void dungNen() {
        don();
        taoVaiTro(VAI_TRO_XEM, List.of("ops:report:view"));
        taoVaiTro(VAI_TRO_XUAT, List.of("ops:report:view", "ops:report:export"));
        phienXem = new PhienHttp(http);
        phienXuat = new PhienHttp(http);
        chiXem = phienXem.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t591_xem", VAI_TRO_XEM));
        duocXuat = phienXuat.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t591_xuat", VAI_TRO_XUAT));
    }

    @AfterAll
    void donSach() {
        don();
    }

    @Test
    @DisplayName("⛔⛔ XEM và XUẤT là HAI quyền — có `:view` ⛔ KHÔNG đủ để tải tệp")
    void xemVaXuatLaHaiQuyen() {
        assertThat(phienXem.get(chiXem, "/api/v1/ops/bao-cao/danh-muc").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(phienXem.get(chiXem, "/api/v1/ops/bao-cao/xuat/BC-10").getStatusCode())
                .as("⛔ **Xem** danh mục và **mang danh sách công trình + chi phí sửa chữa ra khỏi hệ "
                        + "thống** là hai việc khác nhau. Đặc tả tách sẵn hai mã quyền")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(phienXuat.get(duocXuat, "/api/v1/ops/bao-cao/xuat/BC-10").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐⭐ Danh mục khai ĐỦ BẢY mã — bốn mã đã bỏ vĩnh viễn NÓI RA lý do kèm mã chốt")
    void danhMucKhaiDuBayMaKemLyDo() {
        String than = phienXem.get(chiXem, "/api/v1/ops/bao-cao/danh-muc").getBody();
        for (String ma : List.of("BC-01", "BC-02", "BC-03", "BC-04", "BC-06", "BC-09", "BC-10")) {
            assertThat(than)
                    .as("⛔⛔ Ba mã còn sống cộng bốn dòng IM LẶNG thì câu hỏi *BC-01 đâu?* quay lại "
                            + "ở mọi lượt nghiệm thu, và mỗi lần lại phải đi tra tài liệu")
                    .contains(ma);
        }
        assertThat(than).contains("\"khaDung\":false");
        assertThat(than)
                .as("⛔ Lý do phải nêu MÃ CHỐT để người đọc tra ngược được, ⛔ không chỉ nói *đã bỏ*")
                .contains("B1/F1")
                .contains("A1");
    }

    @Test
    @DisplayName("⛔ Xuất BC-01 trả OPS-2023 — ⛔ không phải 404, và ⛔ không phải HR-2009")
    void maDaBoTraMaLoiRieng() {
        ResponseEntity<String> ra = phienXuat.get(duocXuat, "/api/v1/ops/bao-cao/xuat/BC-01");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ra.getBody())
                .as("⛔⛔ Ba trạng thái, ba câu trả lời: **404** = gõ sai mã · **HR-2009** = chưa làm "
                        + "được (BCNS-07 chờ G6, SẼ có) · **OPS-2023** = bỏ vĩnh viễn (KHÔNG BAO GIỜ "
                        + "có). Gộp chúng là để người vận hành đi chờ một thứ ⛔ không bao giờ tới")
                .contains("OPS-2023");

        // Vế phân biệt: một mã ⛔ KHÔNG tồn tại thì ĐÚNG LÀ 404.
        assertThat(phienXuat.get(duocXuat, "/api/v1/ops/bao-cao/xuat/BC-99").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⭐⭐ Ba báo cáo khả dụng đều ra BYTE THẬT — BOM, dấu tách `;`, có dòng dữ liệu")
    void baBaoCaoDeuRaByteThat() {
        for (String ma : List.of("BC-06", "BC-09", "BC-10")) {
            ResponseEntity<String> ra = phienXuat.get(duocXuat, "/api/v1/ops/bao-cao/xuat/" + ma);
            assertThat(ra.getStatusCode()).as("%s: %s", ma, ra.getBody()).isEqualTo(HttpStatus.OK);
            String noiDung = ra.getBody();
            assertThat(noiDung).as("%s trả thân RỖNG", ma).isNotNull().isNotBlank();
            assertThat(noiDung.charAt(0))
                    .as("⛔ %s thiếu BOM UTF-8 — Excel bản tiếng Việt đọc *Cống Liên Mạc* thành ký tự lạ", ma)
                    .isEqualTo('﻿');
            assertThat(noiDung).as("%s ⛔ không dùng dấu tách `;`", ma).contains(";");
            assertThat(noiDung.lines().count())
                    .as(
                            "⛔⛔ %s chỉ có dòng tiêu đề — một bảng ⛔ không hàng nào trông y hệt một bảng "
                                    + "đúng (T42.29)",
                            ma)
                    .isGreaterThanOrEqualTo(2);
            assertThat(ra.getHeaders().getFirst("Content-Disposition"))
                    .contains(ma)
                    .contains(".csv");
        }
    }

    @Test
    @DisplayName("⭐ BC-06 NÓI RA rằng hai khối có PHẠM VI khác nhau")
    void bc06NoiRaHaiPhamVi() {
        String csv = phienXuat.get(duocXuat, "/api/v1/ops/bao-cao/xuat/BC-06").getBody();
        assertThat(csv)
                .as("⛔⛔ Khối CẢNH BÁO toàn hệ (alert_events ⛔ không có cột đơn vị), khối SỰ CỐ cắt "
                        + "theo phạm vi. Hai người ở hai đơn vị xuất cùng bản này sẽ thấy khối một "
                        + "GIỐNG nhau và khối hai KHÁC nhau — im lặng ở đây là để họ đối chiếu rồi "
                        + "kết luận hệ thống sai")
                .contains("TOÀN HỆ THỐNG")
                .contains("phạm vi đơn vị");
        assertThat(csv).contains("KHỐI 1").contains("KHỐI 2");
    }

    @Test
    @DisplayName("⭐ BC-10 nói *chưa số hoá* cho công trình thiếu toạ độ — ⛔ không để ô trống")
    void bc10NoiChuaSoHoaThayViOTrong() {
        String csv = phienXuat.get(duocXuat, "/api/v1/ops/bao-cao/xuat/BC-10").getBody();
        // ⛔⛔ Đo 10/09/2026: `constructions` 11 hàng / **0 toạ độ** (nợ G8). Một ô trống đọc như
        //    *"quên điền"*; câu *"chưa số hoá"* đọc đúng như nó là — và đó là thông tin duy nhất
        //    trên bản báo cáo này nói cho Công ty biết vì sao bản đồ chưa có gì.
        assertThat(csv).contains("chưa số hoá");
        assertThat(csv).contains("Toạ độ");
    }

    // =========================================================================

    private void taoVaiTro(String ma, List<String> quyen) {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, ?, 'Tạm, xoá ở @AfterAll', FALSE, now())",
                ma,
                "Vai trò kiểm thử " + ma);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code IN ("
                        + String.join(",", java.util.Collections.nCopies(quyen.size(), "?")) + ")",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(ma), quyen.stream())
                        .toArray());
        assertThat(gan)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM LẶNG")
                .isEqualTo(quyen.size());
    }

    private void don() {
        for (String vt : List.of(VAI_TRO_XEM, VAI_TRO_XUAT)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        jdbc.update("DELETE FROM users WHERE username LIKE 'kiemtra_t591%'");
        authorities.invalidateAll();
    }
}
