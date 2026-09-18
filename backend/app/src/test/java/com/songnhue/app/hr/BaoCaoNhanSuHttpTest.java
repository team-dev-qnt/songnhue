package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Thống kê và báo cáo nhân sự — <b>CN-04.8</b>, đo qua HTTP.
 *
 * <h2>⛔⛔ Bài này đi tới BYTE THẬT của tệp, ⛔ không dừng ở mã 200</h2>
 *
 * <p>T42.29 đã trả giá cho đúng chuyện này ở thuỷ văn: <i>"`HydroReportExportHttpTest` có 4 bài,
 * ĐÚNG MỘT bài đi tới byte thật — và nó xuất chỉ BC05. Nội dung tệp của 3 mã báo cáo kia chưa từng
 * bị ai nhìn."</i> Một lượt tải trả 200 với thân <b>rỗng</b>, hoặc thân mang đúng dòng tiêu đề và
 * ⛔ không một hàng dữ liệu nào, trông y hệt một lượt tải đúng.
 *
 * <h2>⛔ Báo cáo CẮT theo phạm vi — ngược với danh bạ và sơ đồ tổ chức</h2>
 *
 * <p>Bốn chức năng đọc cùng bảng {@code employees}: CN-04.6 và CN-04.1 <b>toàn Công ty</b>, CN-04.7
 * và CN-04.8 <b>cắt</b>. Bài {@code baoCaoCatTheoPhamViDonVi} canh vế ấy — và nó là vế dễ hỏng theo
 * chiều <b>im lặng</b>: một báo cáo rộng hơn nguồn của nó là một đường vòng qua M4.13.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaoCaoNhanSuHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T582-";
    private static final String VAI_TRO_XEM = "KIEMTRA_T582_XEM";
    private static final String VAI_TRO_XUAT = "KIEMTRA_T582_XUAT";
    private static final String VAI_TRO_HS = "KIEMTRA_T582_HOSO";

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
    private PhienHttp phienHoSo;
    private PhienHttp.Phien chiXem;
    private PhienHttp.Phien duocXuat;
    private PhienHttp.Phien quanTriHoSo;

    private UUID idDuocXuat;
    private long donViGocId;
    private long xnAId;
    private long xnBId;

    /**
     * ⚠ Mỗi bài kiểm là một máy khách khác nhau — T60.9.
     *
     * <p>Lớp này dựng phiên ở {@code @BeforeAll}, nên ⛔ không có dòng này thì <b>cả lớp dùng chung
     * một IP</b> và chung ngân sách {@code EXPORT} = <b>trần theo giờ</b> (settings, mặc định 30 — T61.27). Triệu chứng rơi vào bài
     * chạy SAU dưới dạng {@code 429}, tức người đọc log đi tìm lỗi ở đúng chỗ ⛔ không có lỗi nào.
     *
     * <p>⛔ ⛔ Không nới hạn mức ở hồ sơ kiểm thử — filter vẫn chạy, vẫn đếm, vẫn chặn.
     */
    @BeforeEach
    void moiBaiMotMayKhach() {
        phienXem.doiIp();
        phienXuat.doiIp();
        phienHoSo.doiIp();
    }

    @BeforeAll
    void dungNen() {
        don();
        taoVaiTro(VAI_TRO_XEM, List.of("hr:report:view"));
        taoVaiTro(VAI_TRO_XUAT, List.of("hr:report:view", "hr:report:export"));
        taoVaiTro(VAI_TRO_HS, List.of("hr:employee:view", "hr:employee:create", "hr:employee:update"));

        donViGocId = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        String pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);
        xnAId = themDonVi(TIEN_TO + "XN-A", donViGocId, pathGoc);
        xnBId = themDonVi(TIEN_TO + "XN-B", donViGocId, pathGoc);

        phienXem = new PhienHttp(http);
        phienXuat = new PhienHttp(http);
        phienHoSo = new PhienHttp(http);
        String tenXem = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t582_xem", VAI_TRO_XEM);
        String tenXuat = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t582_xuat", VAI_TRO_XUAT);
        String tenHoSo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t582_hoso", VAI_TRO_HS);
        chiXem = phienXem.dangNhap(tenXem);
        duocXuat = phienXuat.dangNhap(tenXuat);
        quanTriHoSo = phienHoSo.dangNhap(tenHoSo);
        idDuocXuat = jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, tenXuat);

        taoHoSo("BC-1", xnAId, "DANG_LAM");
        taoHoSo("BC-2", xnAId, "THAI_SAN");
        taoHoSo("BC-3", xnBId, "DANG_LAM");
        taoHoSo("BC-4", xnAId, "NGHI_VIEC");

        // ⛔⛔ BCNS-04 và BCNS-06 là báo cáo CÓ ĐIỀU KIỆN — rỗng là một trạng thái hợp lệ và phổ
        //    biến. Bản đầu của lớp này ⛔ không dựng điều kiện, nên `baySoBaoCaoDeuRaByteThat` đỏ ở
        //    BCNS-04 với đúng một dòng tiêu đề.
        //    ⇒ Cách sửa RẺ NHẤT lúc ấy là miễn hai mã khỏi vòng lặp — và đó là **tự tay tháo bộ
        //      canh**: một BCNS-04 hỏng hoàn toàn sẽ đi lọt mãi mãi (T51.17 cùng hình dạng).
        //      Nên dựng ĐIỀU KIỆN, ⛔ không nới khẳng định.
        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);
        taoHoSoHopDongSapHet("BC-5", xnAId, homNay.plusDays(10));
        themChungChiSapHetHan("BC-1", homNay.plusDays(15));
    }

    @AfterAll
    void donSach() {
        don();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%"))
                .as("⛔⛔ G6-a chưa có dữ liệu và CLAUDE.md cấm seed hồ sơ CBNV — bảng phải RỖNG lại")
                .isZero();
    }

    // =========================================================================

    @Test
    @DisplayName("⛔⛔ XEM và XUẤT là HAI quyền — có `:view` ⛔ KHÔNG đủ để tải tệp về")
    void xemVaXuatLaHaiQuyen() {
        assertThat(phienXem.get(chiXem, "/api/v1/hr/bao-cao/tong-quan").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(phienXem.get(chiXem, "/api/v1/hr/bao-cao/xuat/BCNS-01").getStatusCode())
                .as("⛔⛔ **Xem** số tổng hợp trên màn hình và **mang cả danh sách cán bộ ra khỏi hệ "
                        + "thống** là hai việc khác nhau: tệp trích ngang rời máy chủ thì hệ ⛔ không "
                        + "còn kiểm soát được gì nữa. Đặc tả tách sẵn hai mã quyền; gộp chúng *cho "
                        + "gọn* là xoá một ranh giới khách đã vẽ")
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Vế phân biệt: CÙNG đường, tài khoản CÓ `:export` ⇒ 200.
        assertThat(phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/xuat/BCNS-01").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐⭐ Danh mục khai ĐỦ TÁM báo cáo — BCNS-07 có mặt và NÓI RA vì sao chưa dựng được")
    void danhMucKhaiDuTamVaNoiRaLyDo() {
        String than = phienXem.get(chiXem, "/api/v1/hr/bao-cao/danh-muc").getBody();
        for (int i = 1; i <= 8; i++) {
            assertThat(than)
                    .as("⛔⛔ Bảy mã khả dụng cộng một dòng IM LẶNG thì lượt nghiệm thu đếm bảy nút "
                            + "rồi tick đủ — và *BCNS-07 chưa có* thành một sự thật ⛔ không nơi nào ghi")
                    .contains("BCNS-0" + i);
        }
        assertThat(than).contains("\"khaDung\":false");
        assertThat(than)
                .as("⛔ Lý do phải là câu người vận hành ĐỌC ĐƯỢC, ⛔ không phải một cờ")
                .contains("2C-BNV")
                .contains("G6");
    }

    @Test
    @DisplayName("⛔ Xuất BCNS-07 trả HR-2009 kèm LÝ DO — ⛔ không phải 404")
    void bcns07TraMaLoiRiengKemLyDo() {
        ResponseEntity<String> ra = phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/xuat/BCNS-07");
        assertThat(ra.getStatusCode())
                .as(
                        "⛔⛔ 404 nói *mã ⛔ không tồn tại* — người gọi đi kiểm lại chính tả. Sự thật là "
                                + "*mã có thật, chưa dựng được*, và hai câu ấy dẫn tới hai việc khác hẳn nhau: %s",
                        ra.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ra.getBody()).contains("HR-2009").contains("2C-BNV");

        // Vế phân biệt: một mã KHÔNG tồn tại thì ĐÚNG LÀ 404.
        assertThat(phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/xuat/BCNS-99").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⭐⭐ Bảy báo cáo khả dụng đều ra BYTE THẬT — ⛔ không dừng ở mã 200")
    void baySoBaoCaoDeuRaByteThat() {
        // ⛔⛔ T42.29: *"4 bài, ĐÚNG MỘT bài đi tới byte thật — và nó xuất chỉ BC05"*. Vòng lặp này
        //    ⛔ không cho một mã nào đi lọt.
        for (String ma : List.of("BCNS-01", "BCNS-02", "BCNS-03", "BCNS-04", "BCNS-05", "BCNS-06", "BCNS-08")) {
            ResponseEntity<String> ra = phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/xuat/" + ma);
            assertThat(ra.getStatusCode()).as("%s: %s", ma, ra.getBody()).isEqualTo(HttpStatus.OK);

            String noiDung = ra.getBody();
            assertThat(noiDung).as("%s trả thân RỖNG", ma).isNotNull().isNotBlank();
            assertThat(noiDung.charAt(0))
                    .as(
                            "⛔⛔ %s thiếu BOM UTF-8 — Excel bản tiếng Việt sẽ đoán bảng mã theo địa "
                                    + "phương và *Cống Liên Mạc* thành *CÃ´ng LiÃªn Máº¡c*",
                            ma)
                    .isEqualTo('﻿');
            assertThat(noiDung)
                    .as(
                            "⛔ %s dùng dấu tách `;` — vi-VN dùng dấu phẩy làm dấu THẬP PHÂN, nên dấu "
                                    + "phẩy làm dấu tách cho ra một cột duy nhất",
                            ma)
                    .contains(";");
            assertThat(noiDung.lines().count())
                    .as(
                            "⛔⛔ %s chỉ có dòng tiêu đề — một bảng ⛔ không hàng nào trông y hệt một "
                                    + "bảng đúng (T42.29)",
                            ma)
                    .isGreaterThanOrEqualTo(2);

            assertThat(ra.getHeaders().getFirst("Content-Disposition"))
                    .as("⛔ Thiếu `Content-Disposition` thì trình duyệt MỞ tệp thay vì tải về, và "
                            + "người dùng nhìn một trang chữ CSV")
                    .contains(ma)
                    .contains(".csv");
        }
    }

    @Test
    @DisplayName("⭐ BCNS-01 mang ĐÚNG hồ sơ vừa dựng, ⛔ không phải một bảng trống có tiêu đề")
    void bcns01MangDuLieuThat() {
        String csv = phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/xuat/BCNS-01").getBody();
        assertThat(csv).contains(TIEN_TO + "BC-1").contains(TIEN_TO + "BC-3");
        assertThat(csv)
                .as("⛔ Trích ngang là danh sách TOÀN BỘ hồ sơ trong phạm vi — kể cả người đã nghỉ, "
                        + "vì đó là một bản kê hồ sơ chứ ⛔ không phải một bản kê quân số")
                .contains(TIEN_TO + "BC-4");
        assertThat(csv).contains("Mã CBNV").contains("Họ và tên");
    }

    @Test
    @DisplayName("⭐⭐ KPI đếm người CÒN LÀM VIỆC — người thai sản VẪN tính, người nghỉ việc thì ⛔ không")
    void kpiDemDungNguoiConLamViec() {
        // Bốn người còn làm: BC-1 (đang làm) · BC-2 (THAI SẢN) · BC-3 (đang làm) · BC-5 (đang làm,
        // hợp đồng xác định thời hạn). BC-4 đã nghỉ việc ⇒ ⛔ không tính.
        String than = phienXem.get(chiXem, "/api/v1/hr/bao-cao/tong-quan").getBody();
        assertThat(than).contains("\"tongDangLamViec\":4");
        assertThat(than)
                .as("⛔⛔ *Còn làm việc* ⛔ KHÔNG phải `status = 'DANG_LAM'` — BC-2 nghỉ thai sản vẫn "
                        + "là người của Công ty (T55.2). Đếm theo `DANG_LAM` cho ra 3, và ⛔ không ai "
                        + "đếm lại bằng tay để phát hiện")
                .doesNotContain("\"tongDangLamViec\":3");
    }

    @Test
    @DisplayName("⛔⛔ Báo cáo CẮT theo phạm vi đơn vị — ngược với danh bạ và sơ đồ tổ chức")
    void baoCaoCatTheoPhamViDonVi() {
        String toanBo = phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/tong-quan").getBody();
        assertThat(toanBo)
                .as("⛔ Tiền đề: đứng ở gốc thì phải thấy ĐỦ bốn người, ⛔ không thì phép so dưới "
                        + "xanh vì lý do sai (luật 7)")
                .contains("\"tongDangLamViec\":4");

        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", xnBId, idDuocXuat);
        authorities.invalidateAll();
        try {
            String hep = phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/tong-quan").getBody();
            assertThat(hep)
                    .as("⛔⛔ Báo cáo là ĐẦU RA của hồ sơ, mà hồ sơ chịu M4.13 (*quản lý cấp XN chỉ "
                            + "xem hồ sơ đơn vị mình*). Một báo cáo rộng hơn nguồn của nó là một "
                            + "đường vòng qua chính luật ấy — và nó im lặng hoàn toàn")
                    .contains("\"tongDangLamViec\":1");

            String csv =
                    phienXuat.get(duocXuat, "/api/v1/hr/bao-cao/xuat/BCNS-01").getBody();
            assertThat(csv).contains(TIEN_TO + "BC-3");
            assertThat(csv)
                    .as("⛔ Đường XUẤT phải cắt y hệt đường XEM — nếu ⛔ không thì cổng quyền bị né "
                            + "bằng cách tải tệp về thay vì mở màn hình")
                    .doesNotContain(TIEN_TO + "BC-1");
        } finally {
            jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", donViGocId, idDuocXuat);
            authorities.invalidateAll();
        }
    }

    // =========================================================================

    private void taoHoSo(String hau, long donViId, String trangThai) {
        UUID donViPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, donViId);
        String ngayNghi = "NGHI_VIEC".equals(trangThai) ? ",\"terminatedAt\":\"2025-11-20\"" : "";
        String than =
                """
                {"code":"%s","fullName":"Đỗ Thị Báo","dateOfBirth":"1993-05-17","gender":"NU",\
                "orgUnitId":"%s","hiredAt":"2019-02-01","educationLevel":"DAI_HOC",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"%s"%s}"""
                        .formatted(TIEN_TO + hau, donViPublic, trangThai, ngayNghi);
        ResponseEntity<String> tao = phienHoSo.goi(quanTriHoSo, HttpMethod.POST, "/api/v1/hr/employees", than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** Hồ sơ có hợp đồng XÁC ĐỊNH THỜI HẠN hết hạn vào {@code hetHan} — điều kiện của BCNS-04. */
    private void taoHoSoHopDongSapHet(String hau, long donViId, LocalDate hetHan) {
        UUID donViPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, donViId);
        String than =
                """
                {"code":"%s","fullName":"Vũ Văn Hạn","dateOfBirth":"1995-08-08","gender":"NAM",\
                "orgUnitId":"%s","hiredAt":"2024-01-02","contractType":"XAC_DINH_THOI_HAN",\
                "contractSignedAt":"2024-01-02","contractExpiresAt":"%s","status":"DANG_LAM"}"""
                        .formatted(TIEN_TO + hau, donViPublic, hetHan);
        ResponseEntity<String> tao = phienHoSo.goi(quanTriHoSo, HttpMethod.POST, "/api/v1/hr/employees", than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** Chứng chỉ hết hiệu lực vào {@code hetHan} — điều kiện của BCNS-06. */
    private void themChungChiSapHetHan(String maHoSo, LocalDate hetHan) {
        int so = jdbc.update(
                "INSERT INTO employee_qualifications (employee_id, kind, name, issued_on, expires_on, created_at) "
                        + "SELECT e.id, 'CHUNG_CHI', ?, ?, ?, now() FROM employees e WHERE e.code = ?",
                TIEN_TO + "Chứng chỉ hành nghề thuỷ lợi",
                hetHan.minusYears(3),
                hetHan,
                TIEN_TO + maHoSo);
        assertThat(so)
                .as("⛔ Chống tập rỗng: ⛔ không chèn được thì BCNS-06 rỗng và bài kiểm đỏ vì một lý "
                        + "do chẳng liên quan gì tới thứ đang kiểm")
                .isEqualTo(1);
    }

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

    private long themDonVi(String ma, long chaId, String pathCha) {
        jdbc.update(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/', 1, 10, now())",
                ma,
                "Đơn vị kiểm thử " + ma,
                chaId);
        long id = jdbc.queryForObject("SELECT id FROM org_units WHERE code = ?", Long.class, ma);
        jdbc.update("UPDATE org_units SET path = ? WHERE id = ?", pathCha + id + "/", id);
        return id;
    }

    private void don() {
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_t582%'");
        for (String bang : List.of("employee_events", "employee_qualifications", "employee_sensitive")) {
            jdbc.update(
                    "DELETE FROM " + bang + " WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                    TIEN_TO + "%");
        }
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE code = 'CTY') "
                + "WHERE username LIKE 'kiemtra_t582%'");
        for (String bang : List.of("jobs", "attachments")) {
            jdbc.update(
                    "DELETE FROM " + bang + " WHERE org_unit_id IN (SELECT id FROM org_units WHERE code LIKE ?)",
                    TIEN_TO + "%");
        }
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        for (String vt : List.of(VAI_TRO_XEM, VAI_TRO_XUAT, VAI_TRO_HS)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        jdbc.update("DELETE FROM users WHERE username LIKE 'kiemtra_t582%'");
        authorities.invalidateAll();
    }
}
