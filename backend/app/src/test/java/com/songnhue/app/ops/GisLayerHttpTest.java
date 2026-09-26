package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
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
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Lớp bản đồ GIS — <b>CN-02.4 / M2.9</b>, đo qua HTTP.
 *
 * <h2>⛔⛔ Ba ca TỪ CHỐI là phần đáng giá nhất của lớp này</h2>
 *
 * <p>Một hệ nhận <b>mọi</b> tệp rồi lưu sẽ qua được mọi bài kiểm *"nạp thành công"* — và cho ra
 * đúng thứ người dùng đọc thành *"bản đồ hỏng"*: lớp hiện trong danh sách, bản đồ ⛔ không vẽ gì.
 * Chốt chặn: KML/KMZ ĐỔI sang GeoJSON (T59.14; tệp hỏng ⇒ {@code OPS-2033}), tệp rỗng hình học
 * ({@code OPS-2026}), trùng tên
 * ({@code OPS-2024}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GisLayerHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T592-";
    private static final String VAI_TRO_XEM = "KIEMTRA_T592_XEM";
    private static final String VAI_TRO_QL = "KIEMTRA_T592_QUANLY";

    /** GeoJSON tối thiểu, hai đối tượng LineString ⇒ loại hình học phải ra {@code LINE}. */
    private static final byte[] GEOJSON_KENH =
            ("""
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"ten":"Kênh N1"},
               "geometry":{"type":"LineString","coordinates":[[105.78,21.04],[105.79,21.05]]}},
              {"type":"Feature","properties":{"ten":"Kênh N2"},
               "geometry":{"type":"LineString","coordinates":[[105.80,21.06],[105.81,21.07]]}}
            ]}""")
                    .getBytes(StandardCharsets.UTF_8);

    /** ⛔ JSON HỢP LỆ mà ⛔ không có hình học — ca mà một phép kiểm "parse được ⛔ không" bỏ lọt. */
    private static final byte[] JSON_RONG_HINH_HOC =
            "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8);

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
    private PhienHttp phienQl;
    private PhienHttp.Phien chiXem;
    private PhienHttp.Phien quanLy;

    @BeforeAll
    void dungNen() {
        don();
        taoVaiTro(VAI_TRO_XEM, List.of("ops:gis-layer:view"));
        taoVaiTro(VAI_TRO_QL, List.of("ops:gis-layer:view", "ops:gis-layer:manage"));
        phienXem = new PhienHttp(http);
        phienQl = new PhienHttp(http);
        chiXem = phienXem.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t592_xem", VAI_TRO_XEM));
        quanLy = phienQl.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t592_ql", VAI_TRO_QL));
    }

    @AfterEach
    void xoaLopThuNghiem() {
        jdbc.update(
                "DELETE FROM attachments WHERE owner_type = 'GIS_LAYER' "
                        + "AND owner_id IN (SELECT id FROM gis_layers WHERE name LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM gis_layers WHERE name LIKE ?", TIEN_TO + "%");
    }

    @AfterAll
    void donSach() {
        don();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gis_layers", Integer.class))
                .as("⛔⛔ CLAUDE.md cấm seed dữ liệu bản đồ *cho đẹp demo* — bảng phải RỖNG lại")
                .isZero();
    }

    // =========================================================================

    @Test
    @DisplayName("⛔ `:manage` là cổng thật — có `:view` ⛔ KHÔNG tạo được lớp")
    void xemVaQuanLyLaHaiQuyen() {
        String than = "{\"name\":\"%sQuyen\"}".formatted(TIEN_TO);
        assertThat(phienXem.goi(chiXem, HttpMethod.POST, "/api/v1/ops/gis-layers", than)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(phienQl.goi(quanLy, HttpMethod.POST, "/api/v1/ops/gis-layers", than)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(phienXem.get(chiXem, "/api/v1/ops/gis-layers?chiDangBat=false")
                        .getStatusCode())
                .as("⛔ Người chỉ được XEM vẫn phải đọc được danh sách — nếu ⛔ không thì bản đồ của "
                        + "họ có lớp mà họ ⛔ không biết lớp nào là lớp nào")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐⭐ Nạp GeoJSON: loại hình học SUY TỪ TỆP, số đối tượng ghi xuống, nội dung ra byte thật")
    void napGeoJsonThiSuyDuocLoaiVaSoDoiTuong() {
        UUID id = taoLop("Kenh-muong");

        String truoc =
                phienXem.get(chiXem, "/api/v1/ops/gis-layers?chiDangBat=false").getBody();
        assertThat(truoc)
                .as("⛔⛔ Lớp mới khai CHƯA có tệp — `coTep=false` và `soDoiTuong=null`. Đó là một "
                        + "trạng thái CÓ THẬT (khai trước, nạp sau), khác hẳn *0 đối tượng* — thứ ⛔ "
                        + "không tồn tại vì tệp rỗng hình học bị từ chối ngay lúc nạp")
                .contains("\"coTep\":false")
                // ⚠ Trường VẮNG MẶT chứ ⛔ không phải `null`: Jackson của dự án cấu hình
                //   `NON_NULL` toàn cục. Khẳng định `"soDoiTuong":null` sẽ ĐỎ GIẢ, và khẳng định
                //   `coTep=false` một mình ⛔ không phân biệt được *chưa nạp* với *nạp rồi mà rỗng*.
                .doesNotContain("\"soDoiTuong\"");

        ResponseEntity<String> nap =
                phienQl.dangTep(quanLy, "/api/v1/ops/gis-layers/" + id + "/tep", GEOJSON_KENH, "kenh-muong.geojson");
        assertThat(nap.getStatusCode()).as("%s", nap.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chuoi(nap.getBody(), "geometryType"))
                .as("⛔ Loại hình học SUY TỪ TỆP, ⛔ không do người dùng khai: hai LineString ⇒ LINE. "
                        + "Để người dùng tự chọn là để một lớp vùng được vẽ bằng marker")
                .isEqualTo("LINE");
        assertThat(chuoi(nap.getBody(), "soDoiTuong")).isEqualTo("2");

        // ⛔⛔ Tệp vừa nạp có `status = UPLOADING` cho tới khi việc nền quét virus xong, mà
        //    `WORKER_ENABLED = false` trong bộ kiểm. Lượt chạy ĐẦU của bài này nhận **404** ở
        //    đường `/noi-dung` — và nó lộ ra một khuyết tật THẬT: `readForOwner` trả rỗng cho CẢ
        //    *chưa quét xong* lẫn *⛔ không tồn tại*, nên giao diện nói *"lớp ⛔ không tồn tại"* về
        //    một lớp vừa nạp vài giây trước. Nay hai trạng thái tách nhau bằng `SYS-0009`.
        assertThat(phienXem.get(chiXem, "/api/v1/ops/gis-layers/" + id + "/noi-dung")
                        .getBody())
                .as("⛔ Trước khi quét xong phải là SYS-0009 (*đang quét*), ⛔ không phải 404")
                .contains("SYS-0009");
        datTrangThaiTep(id, "READY");

        // ⭐ BYTE THẬT: một endpoint trả 200 với thân rỗng trông y hệt một endpoint đúng (T42.29).
        ResponseEntity<String> noiDung = phienXem.get(chiXem, "/api/v1/ops/gis-layers/" + id + "/noi-dung");
        assertThat(noiDung.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(noiDung.getBody()).contains("LineString").contains("Kênh N1");
    }

    @Test
    @DisplayName("⭐⭐ T59.14 — KMZ nạp được và ra BYTE GeoJSON thật, ⛔ còn bị từ chối")
    void kmzDuocDoiSangGeoJsonVaPhucVuDuoc() throws Exception {
        UUID id = taoLop("Quy-hoach-KMZ");

        java.io.ByteArrayOutputStream goi = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(goi)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("doc.kml"));
            zip.write(
                    ("""
                     <?xml version="1.0" encoding="UTF-8"?>
                     <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
                       <Placemark><name>Kênh KMZ</name><LineString><coordinates>
                         105.78,21.04 105.79,21.05
                       </coordinates></LineString></Placemark>
                     </Document></kml>
                     """)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ResponseEntity<String> ra =
                phienQl.dangTep(quanLy, "/api/v1/ops/gis-layers/" + id + "/tep", goi.toByteArray(), "quy-hoach.kmz");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);

        // ⚠ Đường phục vụ đi qua chốt QUÉT VIRUS: tệp vừa nạp mang `UPLOADING` nên `/noi-dung` trả
        //   `SYS-0009` (409) cho tới khi quét xong. Lượt chạy đầu của bài này đỏ đúng ở đó — và đó
        //   là một cái đỏ ĐÚNG: nó chứng minh chốt ấy thật sự nằm trên đường đi. Quét virus ⛔ phải
        //   thứ đang đo ở đây, nên đẩy trạng thái thẳng ở CSDL đúng như lối bài anh em vẫn làm.
        datTrangThaiTep(id, "READY");

        // ⭐ BYTE THẬT ở đường PHỤC VỤ — một lượt nạp trả 200 rồi phục vụ thân rỗng trông y hệt một
        //   lượt nạp đúng (T42.29). Đây là chỗ duy nhất chứng minh phép đổi đã chạy tới nơi.
        ResponseEntity<String> noiDung = phienQl.get(quanLy, "/api/v1/ops/gis-layers/" + id + "/noi-dung");
        assertThat(noiDung.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(noiDung.getBody())
                .as("⛔ Thứ LƯU phải là GeoJSON đã đổi — mọi tầng dưới chỉ biết định dạng ấy")
                .contains("FeatureCollection")
                .contains("LineString")
                .contains("Kênh KMZ");
        assertThat(noiDung.getBody())
                .as("⛔⛔ Kinh độ đứng TRƯỚC. Đảo nhầm thì hình dạng vẫn vẽ ra nhưng ở SAI bán cầu, "
                        + "và ⛔ một dòng lỗi nào")
                .contains("[105.78,21.04]");

        // Tên tệp giữ đuôi CŨ để còn truy được nguồn gốc.
        assertThat(jdbc.queryForObject(
                        "SELECT original_name FROM attachments WHERE owner_type = 'GIS_LAYER' "
                                + "AND owner_id = (SELECT id FROM gis_layers WHERE public_id = ?)",
                        String.class,
                        id))
                .isEqualTo("quy-hoach.kmz.geojson");
    }

    @Test
    @DisplayName("⛔ T59.14 — tệp KML HỎNG trả OPS-2033 và ⛔ để lại byte nào trong kho")
    void kmlHongTraOps2033VaKhongLuuGi() {
        UUID id = taoLop("Quy-hoach-hong");
        ResponseEntity<String> ra = phienQl.dangTep(
                quanLy,
                "/api/v1/ops/gis-layers/" + id + "/tep",
                "<kml><Placemark><Point>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "hong.kml");

        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ra.getBody())
                .as("⛔ Cố ý KHÁC OPS-2026 (đọc được mà rỗng hình học): *chọn nhầm tệp* và *tệp hỏng* "
                        + "dẫn tới hai việc khác nhau (T59.0)")
                .contains("OPS-2033");

        // ⛔ Một lượt bị từ chối ⛔ được tiêu hạn mức dung lượng của lớp.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM attachments WHERE owner_type = 'GIS_LAYER' "
                                + "AND owner_id = (SELECT id FROM gis_layers WHERE public_id = ?)",
                        Integer.class,
                        id))
                .isZero();
    }

    @Test
    @DisplayName("⛔ JSON HỢP LỆ mà rỗng hình học bị từ chối — OPS-2026")
    void tepRongHinhHocBiTuChoi() {
        UUID id = taoLop("Rong");
        ResponseEntity<String> ra =
                phienQl.dangTep(quanLy, "/api/v1/ops/gis-layers/" + id + "/tep", JSON_RONG_HINH_HOC, "rong.geojson");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ra.getBody())
                .as("⛔⛔ Một phép kiểm *parse được ⛔ không* sẽ NHẬN tệp này — nó là JSON hoàn toàn "
                        + "hợp lệ. Và kết quả là một lớp *đã nạp thành công* hiện một bản đồ trống")
                .contains("OPS-2026");
    }

    @Test
    @DisplayName("⛔ Trùng tên lớp bị chặn — OPS-2024, và lớp CŨ còn nguyên")
    void trungTenBiChan() {
        taoLop("Trung-ten");
        ResponseEntity<String> hai = phienQl.goi(
                quanLy, HttpMethod.POST, "/api/v1/ops/gis-layers", "{\"name\":\"%sTrung-ten\"}".formatted(TIEN_TO));
        assertThat(hai.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(hai.getBody()).contains("OPS-2024");

        // Chống tập rỗng: một hệ xoá sạch rồi báo lỗi cũng qua được vế trên.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM gis_layers WHERE name = ? AND deleted_at IS NULL",
                        Integer.class,
                        TIEN_TO + "Trung-ten"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Thứ tự chồng lớp gửi TOÀN BỘ danh sách đã sắp — ⛔ không phải *lên một bậc*")
    void sapThuTuGuiToanBoDanhSach() {
        UUID a = taoLop("Lop-A");
        UUID b = taoLop("Lop-B");

        ResponseEntity<String> ra = phienQl.goi(
                quanLy,
                HttpMethod.PATCH,
                "/api/v1/ops/gis-layers/thu-tu",
                "{\"theoThuTu\":[\"%s\",\"%s\"]}".formatted(b, a));
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("SELECT sort_order FROM gis_layers WHERE public_id = ?", Integer.class, b))
                .as("⛔ Gửi một phép dịch chuyển tương đối là hai nơi cùng phải biết thứ tự hiện "
                        + "tại, và hai nơi biết một sự thật là hai nơi sẽ lệch")
                .isZero();
        assertThat(jdbc.queryForObject("SELECT sort_order FROM gis_layers WHERE public_id = ?", Integer.class, a))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ Lớp chưa nạp tệp trả 404 ở đường nội dung — ⛔ không trả một GeoJSON rỗng")
    void lopChuaCoTepTra404() {
        UUID id = taoLop("Chua-tep");
        assertThat(phienXem.get(chiXem, "/api/v1/ops/gis-layers/" + id + "/noi-dung")
                        .getStatusCode())
                .as("⛔ Trả một `FeatureCollection` rỗng làm *lớp chưa nạp tệp* và *lớp nạp rồi mà "
                        + "rỗng* trông y hệt nhau — mà cái sau ⛔ không tồn tại (OPS-2026 chặn)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================

    private UUID taoLop(String ten) {
        ResponseEntity<String> ra = phienQl.goi(
                quanLy,
                HttpMethod.POST,
                "/api/v1/ops/gis-layers",
                "{\"name\":\"%s%s\",\"description\":\"kiểm thử\"}".formatted(TIEN_TO, ten));
        assertThat(ra.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", ra.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(ra.getBody(), "publicId"));
    }

    /** ⚠ Đẩy trạng thái tệp thẳng ở CSDL — quét virus là cơ chế riêng, ⛔ không phải thứ đang đo. */
    private void datTrangThaiTep(UUID lop, String trangThai) {
        int so = jdbc.update(
                "UPDATE attachments SET status = ? WHERE owner_type = 'GIS_LAYER' "
                        + "AND owner_id = (SELECT id FROM gis_layers WHERE public_id = ?)",
                trangThai,
                lop);
        assertThat(so)
                .as("⛔ Chống tập rỗng: ⛔ không cập nhật được dòng nào thì khẳng định dưới xanh trên "
                        + "một lượt đọc ⛔ không bao giờ xảy ra")
                .isPositive();
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

    private void don() {
        jdbc.update("DELETE FROM attachments WHERE owner_type = 'GIS_LAYER'");
        jdbc.update("DELETE FROM gis_layers");
        for (String vt : List.of(VAI_TRO_XEM, VAI_TRO_QL)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        jdbc.update("DELETE FROM users WHERE username LIKE 'kiemtra_t592%'");
        authorities.invalidateAll();
    }

    private static String chuoi(String json, String truong) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(truong) + "\":\\s*(null|\"([^\"]*)\"|[^,}\\]]+)")
                .matcher(json == null ? "" : json);
        if (!m.find()) {
            return null;
        }
        return m.group(2) != null ? m.group(2) : m.group(1);
    }
}
