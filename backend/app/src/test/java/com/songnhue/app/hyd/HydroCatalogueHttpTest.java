package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Danh mục thuỷ văn <b>đi qua HTTP</b> — vá sau lượt rà 01/09/2026.
 *
 * <h2>Ba controller, 17 endpoint, không một bài kiểm HTTP nào</h2>
 *
 * <p>WS-28 đóng với {@code StationScopeTest} và {@code ApiSourceServiceTest} — cả hai gọi <b>thẳng
 * service</b>. Đó đúng là hình dạng luật 5 mà dự án đã trả giá: 391 bài xanh trong khi mọi màn hình
 * quản trị nội dung trả 500. Bài này đi đường mà người dùng thật đi, và ngay lượt đầu nó bắt được
 * ba khuyết tật im lặng mà mọi bài kiểm cũ không thể thấy:
 *
 * <ol>
 *   <li><b>Ô "Nguồn dữ liệu" gửi lên rồi bị vứt</b> khi sửa điểm đo — {@code StationForm} không có
 *       chỗ ngồi cho nó (luật 27, im lặng đúng kiểu §10.62).
 *   <li><b>{@code TECHNICIAN} không tạo nổi điểm đo nào</b> — danh sách nguồn nằm sau
 *       {@code hyd:api-source:manage} mà vai trò ấy không có, trong khi ô là bắt buộc. Tái phát
 *       nguyên hình dạng T27.20 ở WS-28.
 *   <li><b>Cờ "Đang dùng" bị bỏ rơi</b> khi tạo loại chỉ số.
 * </ol>
 *
 * <p>Cả ba đều <i>lưu thành công</i> trên màn hình. Đơn vị đếm đúng không phải "đã dựng bao nhiêu
 * màn hình" mà là "vòng nhập → lưu → đọc lại có khép không" (luật 27).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HydroCatalogueHttpTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;

    /** ⚠ Vai trò DUY NHẤT ngoài SUPER_ADMIN/ADMIN mang {@code hyd:station:manage} (ma trận §6). */
    private PhienHttp.Phien kyThuat;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "thyd_kythuat", "TECHNICIAN"));
    }

    // === ⭐⭐ Vai trò × việc họ phải làm được ==================================

    @Test
    @DisplayName("⭐⭐ TECHNICIAN đọc được DANH SÁCH NGUỒN — ô bắt buộc của biểu mẫu điểm đo có dữ liệu")
    void aTechnicianCanLoadTheApiSourceListThatTheStationFormRequires() {
        ResponseEntity<String> phanHoi = phienHttp.get(kyThuat, "/api/v1/hyd/api-sources");

        assertThat(phanHoi.getStatusCode())
                .as(
                        """
                        ⛔ 403 ở đây nghĩa là TECHNICIAN — vai trò duy nhất ngoài SA/ADMIN có \
                        hyd:station:manage — KHÔNG tạo nổi một điểm đo nào, vì ô "Nguồn dữ liệu" là \
                        trường bắt buộc và danh sách của nó vĩnh viễn rỗng. Đếm màn hình đã dựng thì \
                        xanh; đếm vai trò × việc họ phải làm được thì ra số không (T27.20). %s""",
                        phanHoi.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(phanHoi.getBody())
                .as("và phải có nguồn thật trong đó, không phải một mảng rỗng trả 200 cho có")
                .contains("\"code\"");
    }

    @Test
    @DisplayName("⛔ TECHNICIAN vẫn KHÔNG sửa/xoá được nguồn dữ liệu — chỉ nới đường ĐỌC")
    void aTechnicianStillCannotWriteApiSources() {
        UUID nguon = motNguon();

        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.DELETE, "/api/v1/hyd/api-sources/" + nguon, null)
                        .getStatusCode())
                .as("nới quyền ĐỌC để giao diện chạy được, không nới quyền GHI — nếu không thì đây là "
                        + "nới quyền cho tiện, đúng thứ T27.20 đã từ chối làm")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // === ⭐ Vòng nhập → lưu → đọc lại của điểm đo =============================

    @Test
    @DisplayName("⭐⭐ Đổi Nguồn dữ liệu của điểm đo thì CÓ ĐỔI THẬT trong CSDL")
    void changingAStationsApiSourceActuallyPersists() {
        UUID diemDo = motDiemDo();
        UUID nguonCu = nguonCuaDiemDo(diemDo);
        UUID nguonMoi = nguonKhac(nguonCu);

        ResponseEntity<String> sua =
                phienHttp.goi(kyThuat, HttpMethod.PUT, "/api/v1/hyd/stations/" + diemDo, thanSua(diemDo, nguonMoi));

        assertThat(sua.getStatusCode()).as("%s", sua.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(nguonCuaDiemDo(diemDo))
                .as(
                        """
                        ⛔ Trước 01/09 StationForm không có ô cho apiSourcePublicId, nên giá trị này đi \
                        vào khoảng không: màn hình báo "Đã cập nhật điểm đo", api_source_id không đổi. \
                        Luật 27 nguyên bản — và im lặng tuyệt đối, vì 200 OK là câu trả lời đúng cho \
                        mọi thứ khác trong cùng lượt gửi.""")
                .isEqualTo(nguonMoi)
                .isNotEqualTo(nguonCu);
    }

    @Test
    @DisplayName("⛔ Đổi mã API của điểm đo bị từ chối bằng HYD-2006, không lặng lẽ bỏ qua")
    void changingTheApiCodeIsRejectedWithHyd2006() {
        UUID diemDo = motDiemDo();
        String than = thanSua(diemDo, nguonCuaDiemDo(diemDo))
                .replaceAll("\"apiCode\":\"F[0-9]{5}\"", "\"apiCode\":\"F99999\"");

        ResponseEntity<String> sua = phienHttp.goi(kyThuat, HttpMethod.PUT, "/api/v1/hyd/stations/" + diemDo, than);

        assertThat(sua.getBody())
                .as(
                        """
                        Javadoc của StationService gọi đây là loại hỏng câm: đổi mã API của một điểm đo \
                        đang chạy là gán toàn bộ số liệu lịch sử của trạm này sang trạm khác — không \
                        ràng buộc nào bắt được, biểu đồ vẫn vẽ đẹp. Chốt chặn có thật từ WS-28 nhưng \
                        chưa bài kiểm nào đi qua nó (luật 1).""")
                .contains("HYD-2006");
    }

    @Test
    @DisplayName("⛔ PUT thiếu `measurementTypeIds` bị TỪ CHỐI — không được lặng lẽ xoá hết liên kết")
    void updatingAStationWithoutMeasurementTypesIsRejected() {
        // ⚠⚠ Bài này ra đời từ một lượt CI ĐỎ, không từ một lượt rà. Trước 01/09 lượt gọi dưới
        //    đây trả **200 OK** và **gỡ sạch** liên kết loại chỉ số của điểm đo — im lặng tuyệt
        //    đối, vì 200 là câu trả lời đúng cho mọi trường khác trong cùng thân gửi. Nó chỉ lộ
        //    ra ở một bài kiểm KHÁC (`HydroCatalogueSeedTest` đếm 18/19), và chỉ trên runner
        //    Linux, vì thứ tự chạy của surefire phụ thuộc hệ tệp.
        //
        // 📌 Một điểm đo không đo chỉ số nào là bản ghi vô nghĩa — nó không sinh được số liệu.
        //    Nên câu trả lời đúng là 422, không phải 200 kèm mất dữ liệu.
        UUID diemDo = motDiemDo();
        int truoc = soLoaiChiSoCua(diemDo);
        assertThat(truoc)
                .as("điểm đo thử phải đang CÓ liên kết, nếu không bài kiểm không đo gì")
                .isPositive();

        String thanThieu = jdbc.queryForObject(
                "SELECT code, name, api_code, position_role FROM stations WHERE public_id = ?",
                (rs, i) ->
                        """
                        {"code":"%s","name":"%s","apiCode":"%s","apiSourceId":"%s","positionRole":"%s"}"""
                                .formatted(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        nguonCuaDiemDo(diemDo),
                                        rs.getString(4)),
                diemDo);

        ResponseEntity<String> tl = phienHttp.goi(kyThuat, HttpMethod.PUT, "/api/v1/hyd/stations/" + diemDo, thanThieu);

        assertThat(tl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tl.getBody()).contains("measurementTypeIds");
        // ⭐ Khẳng định QUAN TRỌNG NHẤT: dữ liệu KHÔNG suy suyển. Chỉ khẳng định mã trạng thái
        //    thôi thì một bản vá trả 400 rồi vẫn xoá vẫn qua được bài này.
        assertThat(soLoaiChiSoCua(diemDo)).isEqualTo(truoc);
    }

    private int soLoaiChiSoCua(UUID diemDo) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM station_measurement_types smt "
                        + "JOIN stations s ON s.id = smt.station_id WHERE s.public_id = ?",
                Integer.class,
                diemDo);
        return n == null ? 0 : n;
    }

    // === ⭐ Vòng nhập → lưu → đọc lại của loại chỉ số =========================

    @Test
    @DisplayName("⭐ Tạo loại chỉ số với `active=false` thì bản ghi PHẢI là đang tắt")
    void creatingAnInactiveMeasurementTypeStaysInactive() {
        String ma = "T29LOAI";
        jdbc.update("DELETE FROM measurement_types WHERE code = ?", ma);
        String than =
                """
                {"code":"%s","name":"Loại kiểm thử","unit":"m","valueScale":3,"active":false}"""
                        .formatted(ma);

        ResponseEntity<String> tao = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/measurement-types", than);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        try {
            assertThat(jdbc.queryForObject("SELECT active FROM measurement_types WHERE code = ?", Boolean.class, ma))
                    .as(
                            """
                            ⛔ `active` được DTO nhận và validate rồi bị bỏ rơi ở controller cho tới \
                            01/09: bỏ tick "Đang dùng" lúc tạo vẫn ra một bản ghi đang dùng, và màn hình \
                            báo thành công. Một loại chỉ số tưởng đã tắt mà vẫn hiện trong mọi ô chọn.""")
                    .isFalse();
        } finally {
            jdbc.update("DELETE FROM measurement_types WHERE code = ?", ma);
        }
    }

    // === ⭐⭐ Đường ĐỌC điểm đo qua HTTP — chưa từng có bài kiểm nào ============

    @Test
    @DisplayName("⭐⭐ GET danh sách điểm đo qua HTTP trả 200 — trước 03/09 nó trả 500 ở MỌI lượt gọi")
    void theStationListActuallyLoadsOverHttp() {
        ResponseEntity<String> phanHoi = phienHttp.get(kyThuat, "/api/v1/hyd/stations");

        assertThat(phanHoi.getStatusCode())
                .as(
                        """
                        ⛔⛔ `spring.jpa.open-in-view = false` (cố ý), nên phiên Hibernate đóng ngay khi                         StationService.list() trả về — còn StationController.toView đọc                         getMeasurementTypes() SAU đó. Kết quả: LazyInitializationException ⇒ 500 ở mọi                         lượt mở màn hình Danh mục điểm đo, kể từ WS-28.                         Vì sao không ai thấy: WS-28 đóng bằng StationScopeTest và ApiSourceServiceTest,                         CẢ HAI gọi thẳng service — tức chạy trong giao dịch của bài kiểm, nơi phiên còn                         sống (luật 5). Và đường POST vẫn chạy vì entity vừa dựng mang Set thường chứ                         không phải proxy, nên thử tay thấy "thêm được" là màn hình có vẻ ổn. %s""",
                        phanHoi.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(phanHoi.getBody())
                .as("⛔ và phải có loại chỉ số trong thân — 200 với mảng rỗng thì bài này không đo gì")
                .contains("\"measurementTypes\"")
                .contains("MUC_NUOC");
    }

    /**
     * ⛔ <b>Đổi 04/09/2026</b>: {@code GET /chua-gan-don-vi} đã được <b>gỡ</b> (nợ T28.30) — nó có 0
     * nơi gọi từ giao diện suốt từ WS-28, trong khi cờ {@code chuaGanDonVi} đi cùng mỗi dòng của
     * {@code GET /stations} đã trả lời đúng câu hỏi ấy và {@code StationsPage} đọc theo cờ.
     *
     * <p>⚠ Bài này <b>không xoá theo</b>, vì thứ nó thật sự canh vẫn còn nguyên giá trị: cờ ấy phải
     * <b>ra tới dây</b>. Cho tới khi OI-05 có câu trả lời, <b>19/19</b> điểm đo seed chưa gán đơn
     * vị, và resolver người nhận cảnh báo (G11 tập 2) ⛔ không tìm được ai để gửi — <i>một cảnh báo
     * không có người nhận là một cảnh báo không tồn tại</i>. Con số ấy phải hiện được trên màn hình,
     * nên nó phải đi qua được đường tuần tự hoá.
     */
    @Test
    @DisplayName("⭐ Cờ `chuaGanDonVi` ra tới dây — con số việc-còn-thiếu của OI-05 phải hiện được")
    void theUnassignedFlagReachesTheWire() {
        ResponseEntity<String> phanHoi = phienHttp.get(kyThuat, "/api/v1/hyd/stations");

        assertThat(phanHoi.getStatusCode()).as("%s", phanHoi.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(phanHoi.getBody())
                .as("19/19 điểm đo seed chưa gán đơn vị (OI-05) — cờ này ⛔ không được vắng mặt và ⛔ "
                        + "không được toàn `false` hôm nay")
                .contains("\"chuaGanDonVi\":true");
    }

    // === ⭐⭐ T28.33 — POST validate 14 trường thì phải GHI đủ 14 ==============

    @Test
    @DisplayName("⭐⭐ T28.33 — POST kèm toạ độ / tuyến sông / lý trình thì bảy trường ấy PHẢI vào CSDL")
    void creatingAStationPersistsAllFourteenFields() {
        String maApi = "F97128";
        donDepDiemDo(maApi);
        String than =
                """
                {"code":"T2833-DIEMDO","name":"Điểm đo kiểm thử T28.33","apiCode":"%s","apiSourceId":"%s",
                 "positionRole":"THUONG_LUU","riverName":"Sông Kiểm Thử","chainage":"K12+300",
                 "latitude":"20.980000","longitude":"105.780000","interpolated":true,"active":false,
                 "description":"Ghi chú kiểm thử","measurementTypeIds":["%s"]}"""
                        .formatted(maApi, motNguon(), motLoaiChiSo());

        try {
            ResponseEntity<String> tao = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/stations", than);
            assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

            assertThat(jdbc.queryForMap(
                            """
                            SELECT river_name, chainage, latitude, longitude, is_interpolated, active, description
                            FROM stations WHERE api_code = ?
                            """,
                            maApi))
                    .as(
                            """
                            ⛔⛔ Trước 02/09 `StationRequest` khai và validate ĐỦ 14 trường cho POST, còn \
                            `StationController.create` chuyển sang service đúng 7. Gửi kèm toạ độ thì nhận \
                            201 Created và toạ độ BIẾN MẤT — không lỗi, không cảnh báo, không dấu vết. \
                            Đó là hợp đồng API NÓI DỐI (§10.69), khó thấy hơn một tham số không ai đọc: \
                            người tích hợp đọc lược đồ, gửi đủ, nhận 201, và tin là đã lưu. Hôm nay chưa \
                            ai gặp CHỈ VÌ màn hình Thêm cố ý không vẽ bảy ô ấy — tức là giao diện đang \
                            che một khuyết tật của API.""")
                    .containsEntry("river_name", "Sông Kiểm Thử")
                    .containsEntry("chainage", "K12+300")
                    .containsEntry("is_interpolated", true)
                    .containsEntry("active", false)
                    .containsEntry("description", "Ghi chú kiểm thử")
                    .hasEntrySatisfying("latitude", v -> assertThat(v).hasToString("20.980000"))
                    .hasEntrySatisfying("longitude", v -> assertThat(v).hasToString("105.780000"));
        } finally {
            donDepDiemDo(maApi);
        }
    }

    @Test
    @DisplayName("⛔ T28.33 — POST NỬA cặp toạ độ bị từ chối, ⛔ không lặng lẽ bỏ qua")
    void creatingAStationWithHalfACoordinatePairIsRejected() {
        String maApi = "F97129";
        donDepDiemDo(maApi);
        String than =
                """
                {"code":"T2833-NUACAP","name":"Điểm đo nửa cặp","apiCode":"%s","apiSourceId":"%s",
                 "positionRole":"THUONG_LUU","latitude":"20.980000","measurementTypeIds":["%s"]}"""
                        .formatted(maApi, motNguon(), motLoaiChiSo());

        try {
            ResponseEntity<String> tao = phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/stations", than);

            assertThat(tao.getStatusCode())
                    .as(
                            """
                            ⚠ Lỗ thứ hai của T28.33, im lặng hơn lỗ thứ nhất: `create` cũ không đi qua \
                            `datToaDo()`/`lyTrinh()`, nên nửa cặp toạ độ hay một lý trình sai định dạng \
                            KHÔNG bị từ chối — nó bị BỎ QUA, và `ck_stations_coords_paired` không bao giờ \
                            bắn vì cả hai cột ở lại NULL. Bản ghi ra đời trông hoàn toàn bình thường. %s""",
                            tao.getBody())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(tao.getBody())
                    .as("⭐ F1 — lỗi phải KÈM TÊN TRƯỜNG, nếu không giao diện chỉ hiện được một toast "
                            + "chung chung cho một biểu mẫu 14 ô và người dùng không biết ô nào sai")
                    .contains("\"field\":\"latitude\"");
            assertThat(soDiemDoMangMa(maApi))
                    .as("và ⛔ không được ghi một bản ghi nào — 422 rồi vẫn tạo là tệ hơn cả hai đằng")
                    .isZero();
        } finally {
            donDepDiemDo(maApi);
        }
    }

    private UUID motLoaiChiSo() {
        return jdbc.queryForObject(
                "SELECT public_id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", UUID.class);
    }

    private int soDiemDoMangMa(String maApi) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM stations WHERE api_code = ?", Integer.class, maApi);
        return n == null ? 0 : n;
    }

    /**
     * ⚠ Xoá CỨNG, ⛔ không xoá mềm — {@code ux_stations_api_code} là chỉ mục <i>partial</i>
     * ({@code WHERE deleted_at IS NULL}), nên xoá mềm vẫn để lại hàng và bài kiểm khác đếm phải nó.
     * {@code HydroQualityHttpTest} khẳng định đúng <b>19</b> điểm đo ở chỗ dọn của nó.
     */
    private void donDepDiemDo(String maApi) {
        jdbc.update(
                "DELETE FROM station_measurement_types WHERE station_id IN (SELECT id FROM stations WHERE api_code = ?)",
                maApi);
        jdbc.update("DELETE FROM stations WHERE api_code = ?", maApi);
    }

    // -------------------------------------------------------------------------

    private UUID motNguon() {
        return jdbc.queryForObject(
                "SELECT public_id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", UUID.class);
    }

    private UUID motDiemDo() {
        return jdbc.queryForObject(
                "SELECT public_id FROM stations WHERE deleted_at IS NULL ORDER BY id LIMIT 1", UUID.class);
    }

    private UUID nguonCuaDiemDo(UUID diemDo) {
        return jdbc.queryForObject(
                """
                SELECT a.public_id FROM stations s JOIN api_sources a ON a.id = s.api_source_id
                WHERE s.public_id = ?
                """,
                UUID.class,
                diemDo);
    }

    /**
     * Một nguồn KHÁC nguồn đang gắn — dựng thêm nếu seed mới chỉ có một nguồn.
     *
     * <p>⚠ Không {@code assumeTrue} bỏ qua bài kiểm khi chỉ có một nguồn: một bài "skipped" đọc như
     * một bài đã chạy trong báo cáo, và đây đúng là bài đã bắt được khuyết tật.
     */
    private UUID nguonKhac(UUID nguonCu) {
        UUID khac = jdbc.query(
                "SELECT public_id FROM api_sources WHERE deleted_at IS NULL AND public_id <> ? LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                nguonCu);
        if (khac != null) {
            return khac;
        }
        return jdbc.queryForObject(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, created_at)
                VALUES ('T29NGUON', 'Nguồn kiểm thử', 'BHH40', 'https://vi-du.invalid', 'HOAT_DONG', now())
                RETURNING public_id
                """,
                UUID.class);
    }

    /** Thân PUT dựng từ chính bản ghi đang có — chỉ đổi đúng nguồn, để phép đo không lẫn biến khác. */
    /**
     * Thân {@code PUT} cho một điểm đo — <b>kèm nguyên vẹn danh sách loại chỉ số đang có</b>.
     *
     * <h2>⚠⚠ Vì sao phải gửi lại `measurementTypeIds` dù bài kiểm không quan tâm tới nó</h2>
     *
     * Bản đầu của hàm này dựng thân <b>5 trường</b> và bỏ qua `measurementTypeIds`. Hậu quả không
     * nằm ở bài kiểm này mà nằm ở <b>bài kiểm khác</b>: `StationService` hiểu "không gửi" là "xoá
     * hết", nên điểm đo mất liên kết `MUC_NUOC`, và
     * {@code HydroCatalogueSeedTest.moiDiemDoDeuDoMucNuoc} đếm ra <b>18/19</b>.
     *
     * <p>Ở máy thì xanh: thứ tự chạy của surefire phụ thuộc hệ tệp, macOS xếp `Seed` trước `Http`
     * còn runner Linux xếp ngược lại. Chỉ CI đỏ. Đúng nguyên văn *"xanh ở máy cũng không phải bằng
     * chứng"* — và lần này thứ khác nhau giữa hai môi trường là **thứ tự đọc thư mục**.
     *
     * <p>📌 Hai bài học, và bài thứ hai đắt hơn:
     * <ol>
     *   <li>bài kiểm dùng chung một CSDL thì mỗi lượt ghi là một tác dụng phụ lên bài kiểm khác —
     *       gửi <b>trọn</b> trạng thái hiện có, đừng gửi phần mình quan tâm;
     *   <li>và nếu một thân JSON thiếu trường có thể xoá dữ liệu, thì <b>khuyết tật nằm ở API</b>,
     *       không ở bài kiểm. {@code measurementTypeIds} nay là {@code @NotEmpty} — xem javadoc
     *       của {@code StationRequest}.
     * </ol>
     */
    private String thanSua(UUID diemDo, UUID nguon) {
        String loai = jdbc
                .queryForList(
                        "SELECT mt.public_id FROM station_measurement_types smt "
                                + "JOIN measurement_types mt ON mt.id = smt.measurement_type_id "
                                + "JOIN stations s ON s.id = smt.station_id WHERE s.public_id = ?",
                        String.class,
                        diemDo)
                .stream()
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        return jdbc.queryForObject(
                """
                SELECT code, name, api_code, position_role, river_name, chainage
                  FROM stations WHERE public_id = ?
                """,
                (rs, i) ->
                        """
                        {"code":"%s","name":"%s","apiCode":"%s","apiSourceId":"%s","positionRole":"%s",\
                        "riverName":%s,"chainage":%s,"measurementTypeIds":[%s]}"""
                                .formatted(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        nguon,
                                        rs.getString(4),
                                        json(rs.getString(5)),
                                        json(rs.getString(6)),
                                        loai),
                diemDo);
    }

    /** {@code null} → {@code null} (⛔ không phải chuỗi "null"); còn lại → chuỗi JSON có nháy kép. */
    private static String json(String v) {
        return v == null ? "null" : "\"" + v + "\"";
    }

    /**
     * ⭐⭐ Vòng khứ hồi của màn hình sửa điểm đo <b>⛔ KHÔNG được xoá dữ liệu G8</b>.
     *
     * <h2>Vì sao bài này tồn tại</h2>
     *
     * <p>Ngày 09/09/2026 CI đỏ ở {@code HydroCatalogueSeedTest} trong khi máy dev xanh. Nguyên nhân:
     * lớp này {@code PUT} một thân JSON <b>thiếu</b> {@code riverName}/{@code chainage} lên
     * {@code motDiemDo()} = {@code ORDER BY id LIMIT 1} = <b>F01771</b>, và {@code StationService}
     * hiểu "không gửi" là "xoá" ⇒ điểm đo đầu tiên mất tuyến sông. Trước bản chụp G8 (09/09) mọi
     * điểm đo đều NULL sẵn nên tác dụng phụ ấy <b>vô hình suốt từ WS-29</b>.
     *
     * <p>⚠ Đây là <b>lần thứ hai</b> cùng một hình dạng trong chính tệp này — lần đầu là
     * {@code measurementTypeIds} (xem javadoc {@link #thanSua}). Bài học lần ấy ghi rõ: <i>gửi TRỌN
     * trạng thái hiện có</i>. Một bài học chỉ nằm trong javadoc thì lần sau vẫn mắc lại, nên lần này
     * nó thành một <b>phép kiểm</b>.
     *
     * <h2>⛔ Vì sao ⛔ KHÔNG chữa bằng cách bắt buộc {@code riverName}</h2>
     *
     * <p>Khác {@code measurementTypeIds} (rỗng ⇒ luôn sai), {@code riverName} NULL là trạng thái
     * <b>hợp lệ</b> — 6/19 điểm đo hôm nay bản chụp ghi "Chưa rõ". {@code PUT} là thay-toàn-phần
     * nên xoá một trường bị bỏ trống là <i>đúng ngữ nghĩa</i>; thứ phải bảo đảm là <b>màn hình gửi
     * đủ</b>, và {@code StationsPage.tsx} có nạp cả hai ô ấy vào form. Bài này canh đúng lời hứa đó.
     */
    @Test
    @DisplayName("⭐⭐ Sửa điểm đo rồi lưu KHÔNG xoá tuyến sông/lý trình — vòng khứ hồi của admin")
    void editingAStationKeepsItsG8Location() {
        UUID diemDo = motDiemDo();

        String tuyenTruoc =
                jdbc.queryForObject("SELECT river_name FROM stations WHERE public_id = ?", String.class, diemDo);
        String lyTrinhTruoc =
                jdbc.queryForObject("SELECT chainage FROM stations WHERE public_id = ?", String.class, diemDo);
        assertThat(tuyenTruoc)
                .as("⚠ Chống tập rỗng (luật 7): điểm đo mốc phải ĐANG CÓ tuyến sông, nếu không thì "
                        + "bài kiểm này xanh mà ⛔ không chứng minh gì — đúng cách nó vô hình từ WS-29")
                .isNotNull();

        ResponseEntity<String> sua = phienHttp.goi(
                kyThuat, HttpMethod.PUT, "/api/v1/hyd/stations/" + diemDo, thanSua(diemDo, nguonCuaDiemDo(diemDo)));
        assertThat(sua.getStatusCode()).as("lưu: %s", sua.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("SELECT river_name FROM stations WHERE public_id = ?", String.class, diemDo))
                .as("⛔⛔ Lưu một lượt sửa ⛔ KHÔNG được xoá tuyến sông — dữ liệu G8 phải do Công ty "
                        + "cấp lại, và ⛔ không có API lịch sử nào lấy lại được")
                .isEqualTo(tuyenTruoc);
        assertThat(jdbc.queryForObject("SELECT chainage FROM stations WHERE public_id = ?", String.class, diemDo))
                .as("lý trình cũng vậy")
                .isEqualTo(lyTrinhTruoc);
    }

    // =========================================================================
    // Nhập VỊ TRÍ điểm đo từ tệp — G8 phần còn lại (09/09/2026).
    //
    // ⭐ Vì sao đường này tồn tại: toạ độ của 19/19 điểm đo vẫn NULL ⇒ lớp GIS RỖNG, và đường sửa
    // duy nhất trước đây là mở từng bản ghi trên màn hình, 19 lượt. Ngày Công ty gửi bảng toạ độ,
    // người quản trị chỉ cần tải mẫu → điền → upload.
    // =========================================================================

    /** ⚠ Mã dùng cho các bài ghi bên dưới — bản chụp G8 ghi "Chưa rõ" cả hai cột, nên khôi phục = NULL. */
    private static final String MA_THU = "F01965";

    @Test
    @DisplayName("⭐ Tệp mẫu vị trí: có BOM, đủ 5 cột, dòng 2 là MÔ TẢ (không phải dữ liệu hợp lệ)")
    void stationImportTemplateIsServedAndSelfDescribing() {
        ResponseEntity<String> mau = phienHttp.get(kyThuat, "/api/v1/hyd/stations/import/template");

        assertThat(mau.getStatusCode()).as("tệp mẫu: %s", mau.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(mau.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment")
                .contains("mau-nhap-vi-tri-diem-do.csv");

        String csv = mau.getBody();
        assertThat(csv).isNotNull();
        assertThat(csv.charAt(0))
                .as("⛔ CSV không BOM ⇒ Excel bản Windows mở ra tiếng Việt vỡ dấu")
                .isEqualTo('\uFEFF');

        String[] dong = csv.split("\r\n|\n");
        assertThat(dong).as("tiêu đề + đúng một dòng mô tả").hasSize(2);
        assertThat(dong[0])
                .contains("ma_api")
                .contains("tuyen_song")
                .contains("ly_trinh")
                .contains("vi_do")
                .contains("kinh_do");
        assertThat(dong[1])
                .as("dòng 2 phải là MÔ TẢ — mẫu gồm dòng ví dụ HỢP LỆ sẽ im lặng ghi đè dữ liệu thật")
                .contains("BẮT BUỘC");
    }

    /**
     * ⛔⛔ Đường này CHỈ cập nhật — một mã lạ phải thành <b>lỗi dòng</b>, ⛔ không phải một trạm mới.
     *
     * <p>{@code api_code} là khoá nối duy nhất giữa response của nguồn và điểm đo, và migration khai
     * thẳng nó <i>bất biến sau seed</i>. Cho tệp tạo điểm đo mới là mở đường để một mã gõ sai lặng lẽ
     * sinh ra một trạm ma — nó ⛔ không bao giờ có số liệu, và ⛔ không ai biết nó từ đâu ra.
     */
    @Test
    @DisplayName("⛔⛔ Mã API lạ → lỗi dòng, KHÔNG tạo điểm đo mới")
    void unknownApiCodeIsARowErrorNotANewStation() {
        int truoc = jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Integer.class);

        String csv = "ma_api,tuyen_song\nF99999,Sông Bịa\n";
        ResponseEntity<String> xem = phienHttp.dangTep(
                kyThuat, "/api/v1/hyd/stations/import/preview", csv.getBytes(StandardCharsets.UTF_8), "la.csv");

        assertThat(xem.getStatusCode()).as("xem trước: %s", xem.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(xem.getBody()).contains("Không có điểm đo mang mã");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Integer.class))
                .as("⛔ lượt XEM TRƯỚC ⛔ không được ghi một dòng nào")
                .isEqualTo(truoc);
    }

    /** ⛔ Một nửa toạ độ là một điểm SAI trên bản đồ điều hành — tệ hơn hẳn chưa số hoá. */
    @Test
    @DisplayName("⛔ Toạ độ nửa vời và lý trình sai dạng đều bị chặn ở bước chạy khô")
    void halfCoordinatesAndBadChainageAreRejected() {
        String csv = "ma_api,ly_trinh,vi_do,kinh_do\n" + MA_THU + ",km 390,20.945123,\n";
        ResponseEntity<String> xem = phienHttp.dangTep(
                kyThuat, "/api/v1/hyd/stations/import/preview", csv.getBytes(StandardCharsets.UTF_8), "sai.csv");

        assertThat(xem.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xem.getBody())
                .as("cả hai lỗi phải hiện ra CÙNG lúc — báo từng lỗi một bắt người dùng sửa nhiều vòng")
                .contains("Lý trình phải có dạng")
                .contains("ĐỦ cả vĩ độ và kinh độ");
    }

    /**
     * ⭐⭐ Vòng khép kín: upload → ghi thật → đọc lại thấy toạ độ.
     *
     * <p>⚠ Bài này <b>khôi phục</b> hai cột về NULL ở cuối: {@code HydroCatalogueSeedTest} ghim từng
     * ô của cả 19 dòng, và cả hai lớp dùng CHUNG một CSDL cho toàn lượt JVM. Để lại dữ liệu thừa là
     * làm đỏ một lớp khác vì lý do ⛔ không liên quan — đúng cách CI đỏ ngày 09/09.
     */
    @Test
    @DisplayName("⭐⭐ Upload tệp vị trí → toạ độ vào CSDL thật, rồi khôi phục nguyên trạng")
    void uploadingLocationsActuallyWritesCoordinates() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stations WHERE api_code = ? AND river_name IS NULL "
                                + "AND latitude IS NULL AND deleted_at IS NULL",
                        Integer.class,
                        MA_THU))
                .as(
                        "⚠ Chống tập rỗng: %s phải đang RỖNG cả hai cột, nếu không bài kiểm ⛔ không "
                                + "chứng minh được lượt ghi đã xảy ra",
                        MA_THU)
                .isEqualTo(1);

        String csv = "ma_api,tuyen_song,ly_trinh,vi_do,kinh_do\n" + MA_THU + ",Sông Nhuệ,K1+085,20.945123,105.782456\n";
        ResponseEntity<String> nhap = phienHttp.dangTep(
                kyThuat, "/api/v1/hyd/stations/import", csv.getBytes(StandardCharsets.UTF_8), "vitri.csv");

        try {
            assertThat(nhap.getStatusCode()).as("nhập: %s", nhap.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(nhap.getBody())
                    .as("đường này CHỈ cập nhật — `toCreate` phải là 0")
                    .contains("\"toUpdate\":1")
                    .contains("\"toCreate\":0");

            Map<String, Object> sau = jdbc.queryForMap(
                    "SELECT river_name, chainage, chainage_m, latitude, longitude, geom IS NOT NULL AS co_geom "
                            + "FROM stations WHERE api_code = ?",
                    MA_THU);

            assertThat(sau.get("river_name")).isEqualTo("Sông Nhuệ");
            assertThat(sau.get("chainage")).isEqualTo("K1+085");
            assertThat(sau.get("chainage_m"))
                    .as("cột GENERATED phải tính lại — nó là thứ ix_stations_river sắp theo")
                    .isEqualTo(1085);
            assertThat(sau.get("co_geom"))
                    .as("⭐⭐ `geom` là cột GENERATED từ cặp toạ độ — đây là thứ DUY NHẤT làm lớp GIS "
                            + "hết rỗng, và ⛔ không màn hình nào hiện nó ra để kiểm bằng mắt")
                    .isEqualTo(true);
        } finally {
            // ⛔ Khôi phục nguyên trạng bản chụp G8 — xem javadoc.
            int cham = jdbc.update(
                    "UPDATE stations SET river_name = NULL, chainage = NULL, latitude = NULL, "
                            + "longitude = NULL WHERE api_code = ?",
                    MA_THU);
            assertThat(cham)
                    .as("câu khôi phục phải chạm đúng 1 hàng, nếu không lớp kiểm khác sẽ đỏ vì lý do sai")
                    .isEqualTo(1);
        }
    }
}
