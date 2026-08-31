package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

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
    private String thanSua(UUID diemDo, UUID nguon) {
        return jdbc.queryForObject(
                "SELECT code, name, api_code, position_role FROM stations WHERE public_id = ?",
                (rs, i) ->
                        """
                        {"code":"%s","name":"%s","apiCode":"%s","apiSourceId":"%s","positionRole":"%s"}"""
                                .formatted(rs.getString(1), rs.getString(2), rs.getString(3), nguon, rs.getString(4)),
                diemDo);
    }
}
