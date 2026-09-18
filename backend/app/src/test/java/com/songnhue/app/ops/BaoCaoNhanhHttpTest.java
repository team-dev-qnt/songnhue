package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.export.DocxFiller;
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

    /** Cấu hình vị trí lúc giao (seed V202609181088) — bài nào đổi thì {@link #don} trả lại. */
    private final Map<String, Long> viTriGoc = new HashMap<>();

    @BeforeAll
    void dangNhap() {
        jdbc.query("SELECT ma, construction_id FROM bao_cao_nhanh_vi_tri", rs -> {
            viTriGoc.put(rs.getString("ma"), (Long) rs.getObject("construction_id", Long.class));
        });
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
        jdbc.update("DELETE FROM bao_cao_nhanh_luong_mua");
        jdbc.update("DELETE FROM bao_cao_nhanh_vi_tri_ky");
        viTriGoc.forEach(
                (ma, ct) -> jdbc.update("UPDATE bao_cao_nhanh_vi_tri SET construction_id = ? WHERE ma = ?", ct, ma));
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

    private UUID viTri(String ma) {
        return jdbc.queryForObject("SELECT public_id FROM bao_cao_nhanh_vi_tri WHERE ma = ?", UUID.class, ma);
    }

    private UUID congTrinh(String ma) {
        return jdbc.queryForObject(
                "SELECT public_id FROM constructions WHERE code = ? AND deleted_at IS NULL", UUID.class, ma);
    }

    private ResponseEntity<String> gan(String viTri, UUID congTrinh) {
        return phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                GOC + "/cau-hinh/vi-tri/" + viTri(viTri),
                congTrinh == null
                        ? "{\"constructionPublicId\":null}"
                        : "{\"constructionPublicId\":\"%s\"}".formatted(congTrinh));
    }

    private void chenMucNuocLuongCo() {
        jdbc.update(
                """
                INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id, reading_value, quality, source)
                SELECT ?, s.id, mt.id, 1.40, 'HOP_LE', 'API'
                  FROM stations s, measurement_types mt
                 WHERE s.api_code = 'F01519' AND mt.code = 'MUC_NUOC'
                """,
                Timestamp.from(DEN.minusSeconds(600)));
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
                .contains("Trạm bơm Yên Nghĩa vận hành 5 máy bơm với tổng lưu lượng bơm 60 m³/s.");
        assertThat(r.getBody())
                .as("Bảng 4 nhập tay — giao đi RỖNG: 8 điểm, chưa điểm nào có số")
                .contains("\"bang4\":[{")
                .contains("\"ten\":\"Điệp Sơn\",\"thuTu\":12,\"luongMuaMm\":null");
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
        chenMucNuocLuongCo();

        String than = phienHttp.get(kyThuat, GOC + "/" + ky).getBody();
        assertThat(than)
                .as("Lương Cổ TL = F01519 (V202609181085), số lúc 15:50 +07 ⇒ ⛔ đúng mốc")
                .contains("\"apiCode\":\"F01519\",\"giaTriM\":1.4")
                .contains("\"dungMoc\":false")
                .as("điểm đo SUY RA từ liên kết điểm đo–công trình; vế thiếu nói đích danh công trình")
                .contains("Cống Lương Cổ chưa liên kết điểm đo hạ lưu (OI-BC14)")
                .contains("Không có số đo hợp lệ trong 24 giờ trước mốc báo cáo");
    }

    @Test
    @DisplayName("⭐⭐ Khứ hồi ra BYTE THẬT: tải .docx, mở lại, đọc đúng số ở từng toạ độ ô của mẫu Công ty")
    void xuatWordKhuHoi() throws Exception {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        nhapVanHanh(kyThuat, ky, nhom("TB-HVAN", 1100), 3);
        nhapVanHanh(kyThuat, ky, nhom("TB-YNGHIA", 43200), 5);
        UUID thuongPhuc =
                jdbc.queryForObject("SELECT public_id FROM don_vi_hanh_chinh WHERE ten = 'Thượng Phúc'", UUID.class);
        phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                GOC + "/" + ky + "/ngap-ung",
                "{\"dong\":[{\"xaPublicId\":\"%s\",\"sauNuocLua\":115,\"sauNuocRau\":20}]}".formatted(thuongPhuc));
        chenMucNuocLuongCo();
        ResponseEntity<String> mua = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                GOC + "/" + ky + "/luong-mua",
                "{\"o\":[{\"diemMuaPublicId\":\"%s\",\"luongMuaMm\":15},{\"diemMuaPublicId\":\"%s\",\"luongMuaMm\":12.5}]}"
                        .formatted(diemMua("Liên Mạc"), diemMua("Điệp Sơn")));
        assertThat(mua.getStatusCode()).as("%s", mua.getBody()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> tai = http.exchange(
                GOC + "/" + ky + "/xuat", HttpMethod.GET, new HttpEntity<>(phienHttp.header(kyThuat)), byte[].class);
        assertThat(tai.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tai.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("bao-cao-nhanh-20190615-1600.docx");

        // Để lại một bản trong target/ — mở bằng Word để rà bằng mắt (plan §10 bước 4), ⛔ commit.
        java.nio.file.Files.write(java.nio.file.Path.of("target", "bao-cao-nhanh-khu-hoi.docx"), tai.getBody());

        DocxFiller doc = DocxFiller.mo(tai.getBody());
        // Mục 1 + Bảng 1: dòng Sông Nhuệ có số; "Tổng cộng" TRỐNG (mẫu in sẵn 0).
        assertThat(doc.docO(1, 4, 2)).isEqualTo("2");
        assertThat(doc.docO(1, 4, 3)).isEqualTo("8");
        assertThat(doc.docO(1, 4, 4)).as("5×43.200 + 3×1.100").isEqualTo("219.300");
        assertThat(doc.docO(1, 2, 2)).as("⛔ '0' ở dòng Tổng cộng là câu sai").isEmpty();
        assertThat(doc.docO(4, 4, 3)).isEqualTo("8");
        assertThat(doc.docO(4, 4, 4)).as("cột '43'").isEqualTo("5");
        assertThat(doc.docO(4, 4, 10)).as("cột '1,1 ÷1,9'").isEqualTo("3");
        assertThat(doc.docO(4, 4, 6)).as("cột '12' — 0 máy ⇒ TRỐNG như mẫu").isEmpty();
        assertThat(doc.docO(4, 4, 13)).isEqualTo("219.300");
        // Bảng 2: 1 khối + 2 trạm (Hồng Vân 2 nhóm, Yên Nghĩa 1 nhóm) = 4 dòng dữ liệu + 2 dòng tiêu đề.
        assertThat(doc.soDong(5)).isEqualTo(6);
        assertThat(doc.docO(5, 2, 0)).isEqualTo("I");
        assertThat(doc.docO(5, 3, 1)).isEqualTo("Trạm bơm Hồng Vân");
        assertThat(doc.docO(5, 3, 3)).isEqualTo("1.100");
        assertThat(doc.docO(5, 3, 4)).isEqualTo("3");
        // Hồng Vân 2 nhóm máy ⇒ TT · tên · nguồn tưới gộp DỌC, đúng cách mẫu viết "Đại Áng (tiêu)".
        assertThat(doc.gopDocCua(5, 3, 1)).isEqualTo("restart");
        assertThat(doc.gopDocCua(5, 4, 1)).isEqualTo("continue");
        assertThat(doc.gopDocCua(5, 4, 3)).as("Q của nhóm thứ hai ⛔ gộp").isNull();
        assertThat(doc.gopDocCua(5, 5, 1)).as("Yên Nghĩa một nhóm ⛔ gộp").isNull();
        // Bảng 3: Lương Cổ TL (F01519) có số lúc 15h50 ⇒ ghi kèm giờ; HL ⛔ có điểm đo ⇒ trống.
        assertThat(doc.docO(6, 37, 3)).isEqualTo("1,40 (15h50)");
        assertThat(doc.docO(6, 38, 3)).isEmpty();
        assertThat(doc.docO(6, 25, 3))
                .as("số minh hoạ '200' của mẫu phải bị xoá")
                .isEmpty();
        // Bảng 4: hai điểm đã nhập; mọi dòng khác TRỐNG — số minh hoạ của mẫu bị xoá.
        assertThat(doc.docO(7, 5, 2)).as("Liên Mạc").isEqualTo("15");
        assertThat(doc.docO(7, 12, 2)).as("Điệp Sơn").isEqualTo("12,5");
        assertThat(doc.docO(7, 6, 2))
                .as("Hà Đông — mẫu in '12', kỳ này chưa nhập")
                .isEmpty();
        assertThat(doc.docO(7, 1, 2)).as("Đông Anh — công ty khác").isEmpty();
        // Bảng 5 + Mục 3: Thượng Phúc (TT 55) + dòng III; công ty khác TRỐNG.
        assertThat(doc.docO(8, 60, 1).trim()).isEqualTo("Thượng Phúc");
        assertThat(doc.docO(8, 60, 5)).isEqualTo("115");
        assertThat(doc.docO(8, 60, 7)).isEqualTo("135");
        assertThat(doc.docO(8, 51, 7)).isEqualTo("135");
        assertThat(doc.docO(8, 3, 2)).as("Sông Tích — mẫu in '0'").isEmpty();
        assertThat(doc.docO(2, 4, 7)).as("Mục 3 = dòng III").isEqualTo("135");

        String van = vanBan(tai.getBody());
        assertThat(van)
                .contains("(Từ 6h ngày 15/6/2019 đến 16h ngày 15/6/2019)")
                .contains("Tính đến 16h ngày 15/6/2019, công tác vận hành")
                .contains("Mực nước hồi 16h ngày 15/6/2019")
                .contains("Lượng mưa từ 6h ngày 15/6/2019 đến 16h ngày 15/6/2019")
                .contains("ngày 15 tháng 6 năm 2019")
                .contains("Ghi chú: Trạm bơm Yên Nghĩa vận hành 5 máy bơm với tổng lưu lượng bơm 60 m³/s.")
                .doesNotContain("24/8/2026");
    }

    private UUID diemMua(String ten) {
        return jdbc.queryForObject("SELECT public_id FROM diem_mua_bao_cao_nhanh WHERE ten = ?", UUID.class, ten);
    }

    @Test
    @DisplayName("⭐⭐ Cấu hình trên UI: gắn/gỡ công trình đổi Bảng 3 + Yên Nghĩa; sai loại ⇒ OPS-2032")
    void cauHinhViTri() {
        ResponseEntity<String> ds = phienHttp.get(kyThuat, GOC + "/cau-hinh/vi-tri");
        assertThat(ds.getStatusCode()).as("%s", ds.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(ds.getBody())
                .as("8 vị trí; Lương Cổ TL suy ra F01519 từ liên kết điểm đo–công trình")
                .contains("\"ma\":\"B3_LUONG_CO\"")
                .contains("\"ma\":\"YEN_NGHIA\"")
                .contains("\"apiCode\":\"F01519\"");

        assertThat(phienHttp
                        .get(kyThuat, GOC + "/cau-hinh/cong-trinh?loai=TRAM_BOM")
                        .getBody())
                .contains("TB-YNGHIA")
                .doesNotContain("CTTC-YNGHIA");
        assertThat(phienHttp
                        .get(kyThuat, GOC + "/cau-hinh/cong-trinh?loai=KHAC")
                        .getBody())
                .contains("SYS-0003");

        // Danh mục có HAI "Yên Nghĩa" — gắn nhầm cống tiêu vào ghi chú trạm bơm bị chặn.
        ResponseEntity<String> nham = gan("YEN_NGHIA", congTrinh("CTTC-YNGHIA"));
        assertThat(nham.getBody()).contains("OPS-2032").contains("Yên Nghĩa");

        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        nhapVanHanh(kyThuat, ky, nhom("TB-YNGHIA", 43200), 5);
        chenMucNuocLuongCo();

        assertThat(gan("YEN_NGHIA", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gan("B3_LUONG_CO", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        String sau = phienHttp.get(kyThuat, GOC + "/" + ky).getBody();
        assertThat(sau)
                .as("gỡ trạm ⇒ ghi chú ⛔ đoán theo mã 'TB-YNGHIA' dù nhóm máy mang mã ấy vẫn chạy")
                .contains("\"trangThai\":\"CHUA_GAN_TRAM\"")
                .doesNotContain("Trạm bơm Yên Nghĩa vận hành")
                .as("gỡ cống ⇒ Bảng 3 TRỐNG kèm lý do chỉ đường")
                .contains("Chưa gắn công trình vào dòng này")
                .doesNotContain("\"apiCode\":\"F01519\"");

        // Gắn lại ⇒ số quay về, ⛔ cần deploy.
        gan("YEN_NGHIA", congTrinh("TB-YNGHIA"));
        gan("B3_LUONG_CO", congTrinh("LCO"));
        assertThat(phienHttp.get(kyThuat, GOC + "/" + ky).getBody())
                .contains("Trạm bơm Yên Nghĩa vận hành 5 máy bơm")
                .contains("\"apiCode\":\"F01519\",\"giaTriM\":1.4");
    }

    @Test
    @DisplayName("⭐⭐ Kỳ đã chốt đọc ẢNH CHỤP cấu hình — gỡ công trình SAU chốt ⛔ đổi văn bản đã gửi")
    void chotChupCauHinh() {
        nhapDanhMuc(DANH_MUC);
        String ky = taoKy();
        nhapVanHanh(kyThuat, ky, nhom("TB-YNGHIA", 43200), 5);
        chenMucNuocLuongCo();
        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.POST, GOC + "/" + ky + "/chot", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM bao_cao_nhanh_vi_tri_ky WHERE deleted_at IS NULL", Integer.class))
                .as("chụp đủ 8 vị trí")
                .isEqualTo(8);

        gan("YEN_NGHIA", null);
        gan("B3_LUONG_CO", null);
        assertThat(phienHttp.get(kyThuat, GOC + "/" + ky).getBody())
                .contains("Trạm bơm Yên Nghĩa vận hành 5 máy bơm")
                .contains("\"apiCode\":\"F01519\",\"giaTriM\":1.4")
                .as("vế ⛔ có điểm đo LÚC CHỐT nói đúng như vậy")
                .contains("Lúc chốt kỳ, công trình chưa có điểm đo ở vế này");
    }

    @Test
    @DisplayName("⭐ Bảng 4 nhập tay: số âm bị từ chối; null xoá về 'chưa nhập'; kỳ đã chốt ⇒ OPS-2029")
    void bang4NhapTay() {
        String ky = taoKy();
        String duong = GOC + "/" + ky + "/luong-mua";
        UUID lienMac = diemMua("Liên Mạc");

        ResponseEntity<String> am = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                duong,
                "{\"o\":[{\"diemMuaPublicId\":\"%s\",\"luongMuaMm\":-1}]}".formatted(lienMac));
        assertThat(am.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> co = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                duong,
                "{\"o\":[{\"diemMuaPublicId\":\"%s\",\"luongMuaMm\":0}]}".formatted(lienMac));
        assertThat(co.getBody())
                .as("0 mm là một số ĐÃ NHẬP — khác ô trống")
                .contains("\"ten\":\"Liên Mạc\",\"thuTu\":5,\"luongMuaMm\":0");

        ResponseEntity<String> xoa = phienHttp.goi(
                kyThuat,
                HttpMethod.PUT,
                duong,
                "{\"o\":[{\"diemMuaPublicId\":\"%s\",\"luongMuaMm\":null}]}".formatted(lienMac));
        assertThat(xoa.getBody()).contains("\"ten\":\"Liên Mạc\",\"thuTu\":5,\"luongMuaMm\":null");

        phienHttp.goi(kyThuat, HttpMethod.POST, GOC + "/" + ky + "/chot", null);
        assertThat(phienHttp
                        .goi(
                                kyThuat,
                                HttpMethod.PUT,
                                duong,
                                "{\"o\":[{\"diemMuaPublicId\":\"%s\",\"luongMuaMm\":3}]}".formatted(lienMac))
                        .getBody())
                .contains("OPS-2029");
    }

    /** Toàn văn {@code document.xml} bỏ thẻ — đọc cả đoạn bị Word cắt vụn run. */
    private static String vanBan(byte[] docx) throws java.io.IOException {
        try (java.util.zip.ZipInputStream z =
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(z.readAllBytes(), StandardCharsets.UTF_8)
                            .replaceAll("</w:p>", "\n")
                            .replaceAll("<[^>]+>", "");
                }
            }
        }
        return "";
    }
}
