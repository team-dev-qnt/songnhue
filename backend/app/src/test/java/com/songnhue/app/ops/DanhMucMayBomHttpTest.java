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
    @DisplayName("⛔ Cống (⛔ trạm bơm) · mã lạ · trùng Q trong tệp ⇒ lỗi DÒNG; nhập thật trả OPS-2016, 0 dòng ghi")
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
        assertThat(that.getBody()).contains("OPS-2016");
        assertThat(soNhom()).as("⛔ có một dòng lỗi thì ⛔ dòng nào được ghi").isZero();
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
