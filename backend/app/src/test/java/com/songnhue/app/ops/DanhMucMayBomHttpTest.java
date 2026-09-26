package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Danh mục máy bơm của Báo cáo nhanh — đi QUA HTTP, cùng đường với màn hình.
 *
 * <p>Dùng hai trạm bơm có sẵn trong seed ({@code V202609091075}: {@code TB-HVAN}, {@code TB-YNGHIA})
 * và một cống ({@code LCO}) làm vế âm. Mọi nhóm máy tạo ra bị dọn sau mỗi bài — {@code nhom_may_bom}
 * giao đi RỖNG và các lớp khác được quyền giả định điều ấy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DanhMucMayBomHttpTest extends IntegrationTestBase {

    private static final String GOC = "/api/v1/ops/may-bom";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;

    @BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tmb_kythuat", "TECHNICIAN"));
    }

    @AfterEach
    void donNhomMay() {
        jdbc.update("DELETE FROM nhom_may_bom");
        // ⚠ T75.6 — đường nhập nay TẠO ĐƯỢC công trình, nên dọn phải chạm cả `constructions`.
        //    Thiếu dòng này thì trạm do bài trước sinh ra rò sang bài sau và `soNhom()` đếm nhầm,
        //    một kiểu rò trạng thái mà surefire xếp lớp theo hệ tệp sẽ biến thành đỏ ngẫu nhiên (T48.8).
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'TB-CTY-%'");
    }

    private ResponseEntity<String> nhap(String duong, String csv) {
        return phienHttp.dangTep(kyThuat, GOC + duong, csv.getBytes(StandardCharsets.UTF_8), "nhom.csv");
    }

    private int soNhom() {
        return jdbc.queryForObject("SELECT count(*) FROM nhom_may_bom WHERE deleted_at IS NULL", Integer.class);
    }

    @Test
    @DisplayName("⭐ Tệp mẫu sinh từ chính danh mục cột bộ đọc dùng")
    void tepMauMangDuCot() {
        ResponseEntity<String> mau = phienHttp.get(kyThuat, GOC + "/nhom-may/mau-nhap");
        assertThat(mau.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mau.getBody()).contains("ma_cong_trinh").contains("so_may").contains("q_mot_may_m3h");
    }

    @Test
    @DisplayName("⭐⭐ Xem trước ⛔ ghi; nhập thật tạo nhóm, Q '1.100' đọc thành 1100 m³/h (⛔ 1,1)")
    void nhapTaoNhomVaDocQKieuViet() {
        String csv = "ma_cong_trinh,so_may,q_mot_may_m3h\nTB-HVAN,24,1.100\nTB-HVAN,1,43.200\n";

        ResponseEntity<String> xem = nhap("/nhom-may/nhap/xem-truoc", csv);
        assertThat(xem.getStatusCode()).as("xem trước: %s", xem.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(soNhom()).as("⛔ lượt XEM TRƯỚC ⛔ được ghi một dòng nào").isZero();

        ResponseEntity<String> that = nhap("/nhom-may/nhap", csv);
        assertThat(that.getStatusCode()).as("nhập thật: %s", that.getBody()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> dong = jdbc.queryForList(
                "SELECT so_may, q_mot_may_m3h FROM nhom_may_bom WHERE deleted_at IS NULL ORDER BY sort_order");
        assertThat(dong).hasSize(2);
        assertThat(((Number) dong.get(0).get("so_may")).intValue()).isEqualTo(24);
        assertThat(dong.get(0).get("q_mot_may_m3h").toString()).isEqualTo("1100.00");
        assertThat(dong.get(1).get("q_mot_may_m3h").toString()).isEqualTo("43200.00");

        ResponseEntity<String> ds = phienHttp.get(kyThuat, GOC + "/nhom-may");
        assertThat(ds.getBody())
                .as("cỡ máy tính ở BE: 1.100 → '1,1 ÷1,9' · 43.200 → '43'")
                .contains("\"coMay\":\"1,1 ÷1,9\"")
                .contains("\"coMay\":\"43\"")
                .contains("Trạm bơm Hồng Vân");
    }

    @Test
    @DisplayName("⭐⭐ Nhập lại: trùng (trạm, Q) ⇒ CẬP NHẬT số máy; nhóm VẮNG khỏi tệp ⛔ bị xoá")
    void nhapLaiCapNhatKhongXoa() {
        nhap("/nhom-may/nhap", "ma_cong_trinh,so_may,q_mot_may_m3h\nTB-HVAN,24,1100\nTB-YNGHIA,10,43200\n");
        ResponseEntity<String> lan2 = nhap("/nhom-may/nhap", "ma_cong_trinh,so_may,q_mot_may_m3h\nTB-HVAN,20,1100\n");

        assertThat(lan2.getStatusCode()).as("%s", lan2.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(soNhom())
                .as("⛔ tệp thứ hai VẮNG Yên Nghĩa ⛔ phải là lệnh xoá")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT so_may FROM nhom_may_bom WHERE deleted_at IS NULL AND q_mot_may_m3h = 1100",
                        Integer.class))
                .isEqualTo(20);
    }

    @Test
    @DisplayName("⛔ Cống (⛔ trạm bơm) · mã lạ · trùng Q trong tệp ⇒ lỗi DÒNG; nhập thật trả SYS-0015, 0 dòng ghi")
    void loiDongChanCaLuot() {
        String csv = "ma_cong_trinh,so_may,q_mot_may_m3h\n"
                + "LCO,2,980\n"
                + "KHONG-CO,2,980\n"
                + "TB-HVAN,2,980\n"
                + "TB-HVAN,3,980\n";

        ResponseEntity<String> xem = nhap("/nhom-may/nhap/xem-truoc", csv);
        assertThat(xem.getBody())
                .contains("không phải trạm bơm")
                .contains("Không có công trình mã 'KHONG-CO'")
                .contains("hai dòng cùng Q");

        ResponseEntity<String> that = nhap("/nhom-may/nhap", csv);
        assertThat(that.getBody()).contains("SYS-0015");
        assertThat(soNhom()).as("⛔ có một dòng lỗi thì ⛔ dòng nào được ghi").isZero();
    }

    // =========================================================================
    // T75.6 — MỘT tệp dựng cả TRẠM lẫn NHÓM MÁY (hình dạng sheet `TB Tiêu (KH)` của Công ty)
    // =========================================================================

    /** Đúng hình dạng sheet gốc: ⛔ cột mã · Xí nghiệp ở cột · nhóm thứ hai là dòng ⛔ tên. */
    private static final String CSV_KIEU_CONG_TY =
            """
            ma_cong_trinh,ten_cong_trinh,ma_don_vi,nguon_tuoi_huong_tieu,dia_diem,ly_trinh,so_may,q_mot_may_m3h
            ,Đại áng (tiêu),CTY,Sông Nhuệ,Ngọc Hồi,K28+970,4,4000
            ,,,,,,1,1950
            ,Đại áng (tưới),CTY,Sông Nhuệ,Ngọc Hồi,,2,980
            """;

    private String maCua(String ten) {
        return jdbc.queryForObject(
                "SELECT code FROM constructions WHERE name = ? AND deleted_at IS NULL", String.class, ten);
    }

    @Test
    @DisplayName("⭐⭐ Tệp Công ty ⛔ có cột mã ⇒ TẠO trạm + SINH mã; dòng ⛔ tên là nhóm máy của trạm trên")
    void motTepDungCaTramVaNhomMay() {
        ResponseEntity<String> that = nhap("/nhom-may/nhap", CSV_KIEU_CONG_TY);
        assertThat(that.getStatusCode()).as("%s", that.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(maCua("Đại áng (tiêu)"))
                .as("mã SINH theo quy ước sẵn có của sản phẩm, ⛔ phải một chuỗi tự chế")
                .isEqualTo("TB-CTY-001");
        assertThat(maCua("Đại áng (tưới)")).isEqualTo("TB-CTY-002");

        assertThat(jdbc.queryForObject("SELECT basin_note FROM constructions WHERE code = 'TB-CTY-001'", String.class))
                .as("⭐ cột 'Nguồn tưới, hướng tiêu' — thứ in ra cột cuối Bảng 2 — nay CÓ chỗ nhận")
                .isEqualTo("Sông Nhuệ");
        assertThat(jdbc.queryForObject("SELECT chainage FROM constructions WHERE code = 'TB-CTY-001'", String.class))
                .isEqualTo("K28+970");

        assertThat(jdbc.queryForList(
                        "SELECT q_mot_may_m3h FROM nhom_may_bom n JOIN constructions c ON c.id = n.construction_id"
                                + " WHERE c.code = 'TB-CTY-001' AND n.deleted_at IS NULL ORDER BY n.sort_order",
                        String.class))
                .as("dòng ⛔ tên phải thuộc TRẠM NGAY TRÊN, ⛔ phải một trạm mới")
                .containsExactly("4000.00", "1950.00");
        assertThat(soNhom()).isEqualTo(3);
    }

    @Test
    @DisplayName("⭐⭐ Nhập LẠI cùng tệp ⇒ khớp theo (đơn vị, tên), ⛔ tạo trùng danh mục")
    void nhapLaiKhongTaoTrung() {
        nhap("/nhom-may/nhap", CSV_KIEU_CONG_TY);
        ResponseEntity<String> lan2 = nhap("/nhom-may/nhap", CSV_KIEU_CONG_TY);

        assertThat(lan2.getStatusCode()).as("%s", lan2.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM constructions WHERE code LIKE 'TB-CTY-%' AND deleted_at IS NULL",
                        Integer.class))
                .as("⛔ có khoá (đơn vị, tên) thì lượt nhập thứ hai nhân đôi cả danh mục, IM LẶNG")
                .isEqualTo(2);
        assertThat(soNhom()).isEqualTo(3);
    }

    @Test
    @DisplayName("⛔⛔ Bổ sung nhóm máy cho trạm ĐÃ CÓ ⛔ được hạ cấp quản lý của nó")
    void khongHaCapQuanLyTramDaCo() {
        // ⛔⛔ Đặt mốc TẠI CHỖ, ⛔ đọc thứ bài trước để lại. Bản đầu của bài này chỉ đọc giá trị
        //    đang có rồi so lại sau lượt nhập — và nó XANH TRÊN CẢ BẢN HỎNG: một bài chạy trước
        //    cũng nhập `TB-YNGHIA`, nên dưới bản hỏng cấp đã bị hạ TỪ TRƯỚC, `capTruoc` đọc ra
        //    đúng thứ khuyết tật sẽ ghi. Rò trạng thái giữa các bài (T48.8) cộng một mốc trùng
        //    giá trị hỏng (T48.7) — lượt kiểm chứng ngược bắt được, lượt đọc lại thì ⛔.
        jdbc.update("UPDATE constructions SET management_level = 'CONG_TY' WHERE code = 'TB-YNGHIA'");
        String capTruoc = jdbc.queryForObject(
                "SELECT management_level FROM constructions WHERE code = 'TB-YNGHIA'", String.class);
        assertThat(capTruoc)
                .as("tiền đề: mốc phải KHÁC giá trị mà khuyết tật ghi ra, ⛔ thì bài xanh vì lý do sai")
                .isNotEqualTo("XI_NGHIEP");

        ResponseEntity<String> that =
                nhap("/nhom-may/nhap", "ma_cong_trinh,so_may,q_mot_may_m3h\nTB-YNGHIA,10,43200\n");

        assertThat(that.getStatusCode()).as("%s", that.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT management_level FROM constructions WHERE code = 'TB-YNGHIA'", String.class))
                .as("Bản nháp đầu truyền XI_NGHIEP cho CẢ đường cập nhật ⇒ mọi trạm trong tệp bị hạ "
                        + "cấp, ⛔ một dòng lỗi, ⛔ một dòng log")
                .isEqualTo(capTruoc);
    }

    @Test
    @DisplayName("⛔ Dòng ĐẦU ⛔ xác định được trạm · trạm mới thiếu mã đơn vị ⇒ lỗi DÒNG")
    void loiDinhDanhTram() {
        ResponseEntity<String> dongDau = nhap(
                "/nhom-may/nhap/xem-truoc", "ma_cong_trinh,ten_cong_trinh,ma_don_vi,so_may,q_mot_may_m3h\n,,,4,4000\n");
        assertThat(dongDau.getBody()).contains("Dòng đầu tiên phải xác định được công trình");

        ResponseEntity<String> thieuDonVi = nhap(
                "/nhom-may/nhap/xem-truoc",
                "ma_cong_trinh,ten_cong_trinh,ma_don_vi,so_may,q_mot_may_m3h\n,Trạm lạ hoắc,,4,4000\n");
        assertThat(thieuDonVi.getBody())
                .as("⛔ biết Xí nghiệp nào phụ trách thì ⛔ sinh được mã, và ⛔ đoán bừa")
                .contains("ma_don_vi");

        ResponseEntity<String> donViLa = nhap(
                "/nhom-may/nhap/xem-truoc",
                "ma_cong_trinh,ten_cong_trinh,ma_don_vi,so_may,q_mot_may_m3h\n,Trạm lạ hoắc,XN-KHONG-CO,4,4000\n");
        assertThat(donViLa.getBody()).contains("Không có đơn vị mã 'XN-KHONG-CO'");
    }

    @Test
    @DisplayName("⛔ Tệp thiếu CẢ hai cột định danh ⇒ MỘT câu lỗi ở tiêu đề, ⛔ phải mỗi dòng một câu")
    void thieuCotDinhDanhBaoMotLan() {
        ResponseEntity<String> xem = nhap("/nhom-may/nhap/xem-truoc", "so_may,q_mot_may_m3h\n4,4000\n1,1950\n2,980\n");
        assertThat(xem.getBody())
                .contains("ít nhất một cột định danh trạm bơm")
                .as("ba dòng dữ liệu nhưng chỉ MỘT câu lỗi — một màn hình đầy lỗi ⛔ nói được "
                        + "điều thật sự sai là cái TIÊU ĐỀ")
                .doesNotContain("Dòng đầu tiên phải xác định được công trình");
    }

    @Test
    @DisplayName("⛔⛔ Sửa biên cỡ máy có KHE ⇒ OPS-2031, CSDL ⛔ đổi")
    void suaBienCoKheBiTuChoi() {
        List<Map<String, Object>> co = jdbc.queryForList(
                "SELECT public_id, q_tu_m3h, q_den_m3h FROM co_may_bom WHERE deleted_at IS NULL ORDER BY sort_order");
        assertThat(co).as("vế chống tập rỗng — seed 9 cỡ").hasSize(9);

        StringBuilder than = new StringBuilder("{\"bien\":[");
        for (int i = 0; i < co.size(); i++) {
            Map<String, Object> c = co.get(i);
            Object tu = i == 4 ? "3600" : c.get("q_tu_m3h"); // khe [3500, 3600)
            than.append(i == 0 ? "" : ",")
                    .append("{\"publicId\":\"%s\",\"qTu\":%s,\"qDen\":%s}"
                            .formatted(c.get("public_id"), tu, c.get("q_den_m3h")));
        }
        than.append("]}");

        ResponseEntity<String> phanHoi = phienHttp.goi(kyThuat, HttpMethod.PUT, GOC + "/co-may", than.toString());
        assertThat(phanHoi.getBody()).contains("OPS-2031");
        assertThat(jdbc.queryForObject(
                        "SELECT q_tu_m3h FROM co_may_bom WHERE sort_order = 5 AND deleted_at IS NULL", String.class))
                .as("lượt bị từ chối ⛔ được để lại biên dở dang")
                .isEqualTo("3500.00");
    }
}
