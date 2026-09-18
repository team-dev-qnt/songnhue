package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

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
 * Báo cáo nhanh — vòng đời một kỳ đi QUA HTTP: tạo → nhập Bảng 2/5 → chốt → sửa bị chặn → mở lại.
 *
 * <p>Danh mục máy bơm dựng qua đường NHẬP TỆP thật (⛔ INSERT tay), trên hai trạm bơm có sẵn trong
 * seed ({@code TB-YNGHIA}, {@code TB-HVAN}). Mọi dữ liệu tạo ra được dọn sau mỗi bài — các bảng này
 * giao đi RỖNG.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaoCaoNhanhHttpTest extends IntegrationTestBase {

    private static final String GOC = "/api/v1/ops/bao-cao-nhanh";

    /** Mốc cuối kỳ — xa mọi dữ liệu của lớp khác. */
    private static final Instant DEN = Instant.parse("2019-06-15T09:00:00Z");

    private static final Instant TU = Instant.parse("2019-06-14T23:00:00Z");

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
    private PhienHttp.Phien quanTri;

    @BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tbcn_kythuat", "TECHNICIAN"));
        // ⚠ ADMIN bị BUỘC 2FA (T61.30) — đăng nhập trần sẽ dừng ở bước đăng ký mã.
        quanTri = phienHttp
                .dangNhapHaiBuoc(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tbcn_quantri", "ADMIN"))
                .phien();
    }

    @AfterEach
    void don() {
        jdbc.update("DELETE FROM bao_cao_nhanh_van_hanh");
        jdbc.update("DELETE FROM bao_cao_nhanh_ngap_ung");
        jdbc.update("DELETE FROM bao_cao_nhanh");
        jdbc.update("DELETE FROM nhom_may_bom");
        jdbc.update(
                "DELETE FROM hydro_readings WHERE measured_at = ? AND station_id = "
                        + "(SELECT id FROM stations WHERE api_code = 'F01519')",
                Timestamp.from(DEN.minusSeconds(600)));
    }

    // ==== Tiện ích ==========================================================

    private void nhapDanhMuc(String csv) {
        ResponseEntity<String> r = phienHttp.dangTep(
                kyThuat, "/api/v1/ops/may-bom/nhom-may/nhap", csv.getBytes(StandardCharsets.UTF_8), "nhom.csv");
        assertThat(r.getStatusCode()).as("nhập danh mục: %s", r.getBody()).isEqualTo(HttpStatus.OK);
    }

    private String taoKy() {
        ResponseEntity<String> r = phienHttp.goi(
                kyThuat, HttpMethod.POST, GOC, "{\"tuThoiDiem\":\"%s\",\"denThoiDiem\":\"%s\"}".formatted(TU, DEN));
        assertThat(r.getStatusCode()).as("tạo kỳ: %s", r.getBody()).isEqualTo(HttpStatus.CREATED);
        return PhienHttp.giaTriJson(r.getBody(), "publicId");
    }

    private UUID nhom(String maCongTrinh, int q) {
        return jdbc.queryForObject(
                "SELECT n.public_id FROM nhom_may_bom n JOIN constructions c ON c.id = n.construction_id "
                        + "WHERE c.code = ? AND n.q_mot_may_m3h = ? AND n.deleted_at IS NULL",
                UUID.class,
                maCongTrinh,
                q);
    }

    private ResponseEntity<String> nhapVanHanh(PhienHttp.Phien p, String ky, UUID nhom, Integer so) {
        return phienHttp.goi(
                p,
                HttpMethod.PUT,
                GOC + "/" + ky + "/van-hanh",
                "{\"o\":[{\"nhomMayPublicId\":\"%s\",\"soMayVanHanh\":%s}]}".formatted(nhom, so));
    }

    private static final String DANH_MUC = "ma_cong_trinh,so_may,q_mot_may_m3h\n"
            + "TB-HVAN,24,1.100\n"
            + "TB-HVAN,1,1.950\n"
            + "TB-YNGHIA,10,43.200\n";

    // ==== Bài kiểm ==========================================================

    @Test
    @DisplayName("⛔ Khung giờ ngược ('đến' ≤ 'từ') ⇒ OPS-2030, ⛔ kỳ nào được tạo")
    void khungNguocBiTuChoi() {
        ResponseEntity<String> r = phienHttp.goi(
                kyThuat, HttpMethod.POST, GOC, "{\"tuThoiDiem\":\"%s\",\"denThoiDiem\":\"%s\"}".formatted(DEN, TU));
        assertThat(r.getBody()).contains("OPS-2030");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bao_cao_nhanh", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("⭐⭐ Bảng 2 → Bảng 1 → Mục 1 → ghi chú Yên Nghĩa, số do BE tính; chưa nhập ⇒ Bảng 1 TRỐNG")
    void chuoiTinhMotChieu() {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();

        ResponseEntity<String> trong = phienHttp.get(kyThuat, GOC + "/" + ky);
        assertThat(trong.getStatusCode()).as("%s", trong.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(trong.getBody())
                .as("⛔ '0 trạm · 0 máy' khi CHƯA AI NHẬP là một câu sai gửi UBND")
                .contains("\"bang1SongNhue\":null")
                .contains("\"trangThai\":\"CHUA_NHAP\"");

        nhapVanHanh(kyThuat, ky, nhom("TB-HVAN", 1100), 3);
        nhapVanHanh(kyThuat, ky, nhom("TB-HVAN", 1950), 1);
        ResponseEntity<String> r = nhapVanHanh(kyThuat, ky, nhom("TB-YNGHIA", 43200), 5);
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(r.getBody())
                .as("Hồng Vân hai nhóm cùng chạy ⇒ MỘT trạm; 9 cột: 5 ở '43', 4 ở '1,1 ÷1,9'")
                .contains("\"tongTram\":2")
                .contains("\"tongMay\":9")
                .contains("\"theoCo\":[5,0,0,0,0,0,4,0,0]")
                // 3×1100 + 1×1950 + 5×43200
                .contains("\"tongLuuLuongM3h\":221250")
                .contains("Trạm bơm Yên Nghĩa vận hành 5 máy bơm với tổng lưu lượng bơm 60 m3/s.");
        assertThat(r.getBody())
                .as("Bảng 4 để TRỐNG kèm lý do (chốt 18/09) — cùng câu với cổng công khai")
                .contains("G3-a");
    }

    @Test
    @DisplayName("⛔ Số máy vận hành vượt thiết kế ⇒ OPS-2028 gọi đích danh trạm")
    void vuotThietKeBiTuChoi() {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        ResponseEntity<String> r = nhapVanHanh(kyThuat, ky, nhom("TB-YNGHIA", 43200), 11);
        assertThat(r.getBody()).contains("OPS-2028").contains("Yên Nghĩa");
    }

    @Test
    @DisplayName("⭐ Bảng 5: Mục 3 = dòng III; xã của công ty KHÁC bị từ chối (OI-BC1)")
    void bang5VaMuc3() {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        UUID thuongPhuc =
                jdbc.queryForObject("SELECT public_id FROM don_vi_hanh_chinh WHERE ten = 'Thượng Phúc'", UUID.class);
        ResponseEntity<String> r = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                GOC + "/" + ky + "/ngap-ung",
                "{\"dong\":[{\"xaPublicId\":\"%s\",\"sauNuocLua\":115,\"sauNuocRau\":20}]}".formatted(thuongPhuc));
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"muc3\":{").contains("\"sauNuocCong\":135");

        UUID quangOai =
                jdbc.queryForObject("SELECT public_id FROM don_vi_hanh_chinh WHERE ten = 'Quảng Oai'", UUID.class);
        ResponseEntity<String> khac = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                GOC + "/" + ky + "/ngap-ung",
                "{\"dong\":[{\"xaPublicId\":\"%s\",\"sauNuocLua\":1}]}".formatted(quangOai));
        assertThat(khac.getBody()).contains("SYS-0003");
    }

    @Test
    @DisplayName("⭐⭐ Chốt ⇒ sửa trả OPS-2029; danh mục đổi SAU chốt ⛔ đổi văn bản; mở lại cần quyền RIÊNG + lý do")
    void chotVaMoLai() {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        UUID yenNghia = nhom("TB-YNGHIA", 43200);
        nhapVanHanh(kyThuat, ky, yenNghia, 5);

        ResponseEntity<String> chot = phienHttp.goi(kyThuat, HttpMethod.POST, GOC + "/" + ky + "/chot", null);
        assertThat(chot.getStatusCode()).as("%s", chot.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chot.getBody()).contains("\"trangThai\":\"DA_CHOT\"");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM bao_cao_nhanh_van_hanh WHERE deleted_at IS NULL", Integer.class))
                .as("lượt chốt CHỤP mọi nhóm máy sống — kể cả hai nhóm chưa nhập")
                .isEqualTo(3);

        assertThat(nhapVanHanh(kyThuat, ky, yenNghia, 4).getBody()).contains("OPS-2029");

        // Danh mục đổi SAU chốt: Yên Nghĩa 10 → 12 máy. Văn bản đã gửi ⛔ được đổi theo.
        nhapDanhMuc("ma_cong_trinh,so_may,q_mot_may_m3h\nTB-YNGHIA,12,43200\n");
        assertThat(phienHttp.get(kyThuat, GOC + "/" + ky).getBody())
                .contains("\"soMayThietKe\":10")
                .doesNotContain("\"soMayThietKe\":12");

        ResponseEntity<String> kyThuatMoLai =
                phienHttp.goi(kyThuat, HttpMethod.POST, GOC + "/" + ky + "/mo-lai", "{\"lyDo\":\"sửa số Yên Nghĩa\"}");
        assertThat(kyThuatMoLai.getStatusCode())
                .as("⛔ `manage` ⛔ đủ để mở lại một văn bản đã gửi")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> thieuLyDo =
                phienHttp.goi(quanTri, HttpMethod.POST, GOC + "/" + ky + "/mo-lai", "{\"lyDo\":\"  \"}");
        assertThat(thieuLyDo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> moLai = phienHttp.goi(
                quanTri, HttpMethod.POST, GOC + "/" + ky + "/mo-lai", "{\"lyDo\":\"UBND yêu cầu cập nhật 17h\"}");
        assertThat(moLai.getStatusCode()).as("%s", moLai.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(moLai.getBody())
                .contains("\"trangThai\":\"NHAP\"")
                .contains("UBND yêu cầu cập nhật 17h")
                .as("mở lại ⇒ đọc lại danh mục SỐNG")
                .contains("\"soMayThietKe\":12");
    }

    @Test
    @DisplayName("⭐ Bảng 3 lấy số đo HỢP LỆ gần nhất TRƯỚC mốc; cống ⛔ có điểm đo ⇒ ô trống KÈM LÝ DO")
    void bang3MucNuocTaiThoiDiem() {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        jdbc.update(
                """
                INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id, reading_value, quality, source)
                SELECT ?, s.id, mt.id, 1.40, 'HOP_LE', 'API'
                  FROM stations s, measurement_types mt
                 WHERE s.api_code = 'F01519' AND mt.code = 'MUC_NUOC'
                """,
                Timestamp.from(DEN.minusSeconds(600)));

        String than = phienHttp.get(kyThuat, GOC + "/" + ky).getBody();
        assertThat(than)
                .as("Lương Cổ TL = F01519 (V202609181085), số lúc 15:50 +07 ⇒ ⛔ đúng mốc")
                .contains("\"apiCode\":\"F01519\",\"giaTriM\":1.4")
                .contains("\"dungMoc\":false")
                .contains("Cống chưa có điểm đo ở vế này (OI-BC14)")
                .contains("Không có số đo hợp lệ trong 24 giờ trước mốc báo cáo");
    }
}
