package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.hydro.application.ApiSourceService;
import com.songnhue.hydro.application.MeasurementTypeService;

/**
 * <b>Phép kiểm "còn ai dùng không" ⛔ KHÔNG được chạy trong bộ lọc phạm vi</b> — <b>T28.32</b>.
 *
 * <h2>⛔⛔ Khuyết tật: câu trả lời phụ thuộc NGƯỜI ĐANG HỎI</h2>
 *
 * <p>{@code ApiSourceService.delete} và {@code MeasurementTypeService.delete} đều hỏi <i>"đối tượng
 * này còn được tham chiếu không"</i> qua {@code StationRepository}, mà {@code Station} chịu
 * {@code @Filter} phạm vi đơn vị (tầng 3 phân quyền). Hệ quả đo được:
 *
 * <ol>
 *   <li>người của <b>Xí nghiệp A</b> gọi xoá một nguồn dữ liệu;
 *   <li>phép đếm chỉ thấy điểm đo của A ⇒ trả <b>0</b>;
 *   <li>{@code HYD-1002} ⛔ không bắn, nguồn bị xoá mềm;
 *   <li><b>điểm đo của Xí nghiệp B mất đường lấy số liệu.</b>
 * </ol>
 *
 * <p>⚠ ⛔ Không lỗi nào, ⛔ không cảnh báo nào — và <b>người gây ra ⛔ không nhìn thấy hậu quả</b>,
 * vì hậu quả nằm ngoài phạm vi của họ. Đây đúng họ với luật 13 (§10.35 lỗi 2): một phép tính trộn
 * hai nguồn khác chiều lọc thì kết quả phụ thuộc ai bấm nút.
 *
 * <h2>⚠ Vì sao bộ test cũ ⛔ KHÔNG thấy</h2>
 *
 * <p>{@code ApiSourceServiceTest} chạy với {@code AuthContext} rỗng hoặc ở cấp Công ty, nơi bộ lọc
 * ⛔ không cắt gì cả. Lỗ chỉ mở ra khi người gọi đứng ở <b>một Xí nghiệp cụ thể</b> — tức là ở đúng
 * vai trò mà hệ thống sinh ra để phục vụ. Đây là luật 7 ở dạng khó thấy: bảo đảm <i>có</i> chạy,
 * nhưng chạy qua một tập ⛔ không bao giờ bị cắt.
 */
class RangBuocToanVenNgoaiPhamViTest extends IntegrationTestBase {

    private static final String MA_XN_A = "T2832-XN-A";
    private static final String MA_XN_B = "T2832-XN-B";
    private static final String MA_NGUON = "T2832-NGUON";
    private static final String MA_LOAI = "T2832-LOAI";
    private static final String MA_DIEM_DO_B = "T2832-DO-B";

    @Autowired
    private ApiSourceService nguonDuLieu;

    @Autowired
    private MeasurementTypeService loaiChiSo;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID publicIdNguon;
    private UUID publicIdLoai;
    private long xnAId;
    private String pathA;

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        donDep();

        String pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        Long goc = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        xnAId = themDonVi(MA_XN_A, goc, pathGoc);
        long xnBId = themDonVi(MA_XN_B, goc, pathGoc);
        pathA = pathGoc + xnAId + "/";

        publicIdNguon = themNguon();
        publicIdLoai = themLoaiChiSo();

        // ⭐ Điểm đo nằm ở Xí nghiệp B, và nó dùng CẢ nguồn lẫn loại chỉ số vừa tạo.
        long idDiemDo = themDiemDo(xnBId);
        jdbc.update(
                "INSERT INTO station_measurement_types (station_id, measurement_type_id) "
                        + "SELECT ?, id FROM measurement_types WHERE code = ?",
                idDiemDo,
                MA_LOAI);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        donDep();
    }

    // =========================================================================

    /**
     * ⭐⭐ Bài chịu lực số 1. Trước bản vá 04/09, lượt gọi này <b>thành công</b> và nguồn dữ liệu mà
     * Xí nghiệp B đang dùng bị xoá mềm.
     */
    @Test
    @DisplayName("⭐⭐ Người XN A ⛔ KHÔNG xoá được nguồn dữ liệu mà điểm đo XN B đang dùng")
    void deletingAnApiSourceStillUsedByAnotherUnitIsRejected() {
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        Throwable loi = catchThrowable(() -> nguonDuLieu.delete(publicIdNguon));

        assertThat(loi)
                .as("⛔ Phép đếm chạy TRONG bộ lọc phạm vi ⇒ người XN A thấy 0 điểm đo dùng nguồn này, "
                        + "HYD-1002 ⛔ không bắn, và điểm đo của XN B mất đường lấy số liệu — ⛔ không lỗi "
                        + "nào, và người gây ra ⛔ không nhìn thấy hậu quả vì nó nằm ngoài phạm vi của họ.")
                .isInstanceOf(ConflictException.class);

        assertThat(conSongNguon())
                .as("⛔ Bị từ chối thì phải từ chối THẬT — bản ghi ⛔ không được đụng tới")
                .isTrue();
    }

    /**
     * ⭐⭐ Bài chịu lực số 2 — hậu quả im lặng hơn hẳn: {@code hydro_readings} của loại chỉ số ấy
     * <b>mồ côi</b>. Số liệu <b>vẫn được ghi</b>, chỉ là ⛔ không màn hình nào đọc ra nữa.
     */
    @Test
    @DisplayName("⭐⭐ Người XN A ⛔ KHÔNG xoá được loại chỉ số mà điểm đo XN B đang gắn")
    void deletingAMeasurementTypeStillUsedByAnotherUnitIsRejected() {
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        Throwable loi = catchThrowable(() -> loaiChiSo.delete(publicIdLoai));

        assertThat(loi).isInstanceOf(ConflictException.class);
        assertThat(conSongLoai()).isTrue();
    }

    /**
     * ⚠ Vế phân biệt (luật 9) — ⛔ không có nó thì hai bài trên xanh kể cả khi ai đó "vá" bằng cách
     * <b>luôn</b> từ chối xoá.
     *
     * <p>Một ràng buộc toàn vẹn luôn nói "không" là một ràng buộc <b>hỏng theo chiều ngược lại</b>:
     * danh mục trở thành chỉ-thêm, và người vận hành ⛔ không dọn được một mã gõ nhầm.
     */
    @Test
    @DisplayName("⚠ Vế phân biệt — gỡ liên kết rồi thì XN A xoá được, ⛔ không phải luôn từ chối")
    void onceNothingReferencesItTheDeleteSucceeds() {
        jdbc.update("DELETE FROM station_measurement_types WHERE station_id IN "
                + "(SELECT id FROM stations WHERE code = '" + MA_DIEM_DO_B + "')");
        jdbc.update("DELETE FROM stations WHERE code = ?", MA_DIEM_DO_B);

        AuthContext.set(nguoiDungTai(xnAId, pathA));

        nguonDuLieu.delete(publicIdNguon);
        loaiChiSo.delete(publicIdLoai);

        assertThat(conSongNguon())
                .as("⛔ Không còn ai tham chiếu thì phải xoá ĐƯỢC — một ràng buộc luôn nói 'không' biến "
                        + "danh mục thành chỉ-thêm, và người vận hành ⛔ không dọn được một mã gõ nhầm")
                .isFalse();
        assertThat(conSongLoai()).isFalse();
    }

    // -------------------------------------------------------------------------

    private boolean conSongNguon() {
        return dem("SELECT count(*) FROM api_sources WHERE code = ? AND deleted_at IS NULL", MA_NGUON) > 0;
    }

    private boolean conSongLoai() {
        return dem("SELECT count(*) FROM measurement_types WHERE code = ? AND deleted_at IS NULL", MA_LOAI) > 0;
    }

    private int dem(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                996L,
                UUID.randomUUID(),
                "t2832-probe",
                "Người kiểm thử T28.32",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private long themDonVi(String ma, Long cha, String pathCha) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                ma,
                cha);
        String path = pathCha + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private UUID themNguon() {
        return jdbc.queryForObject(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, created_at)
                VALUES (?, 'Nguồn kiểm thử T28.32', 'MOCK', 'https://vi-du.invalid/api', 'HOAT_DONG', now())
                RETURNING public_id
                """,
                UUID.class,
                MA_NGUON);
    }

    private UUID themLoaiChiSo() {
        return jdbc.queryForObject(
                """
                INSERT INTO measurement_types (code, name, unit, active, created_at)
                VALUES (?, 'Loại chỉ số kiểm thử T28.32', 'm', TRUE, now())
                RETURNING public_id
                """,
                UUID.class,
                MA_LOAI);
    }

    private long themDiemDo(long orgUnitId) {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE code = ? AND deleted_at IS NULL", Long.class, MA_NGUON);
        return jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, org_unit_id, created_at)
                VALUES (?, 'Điểm đo XN B (T28.32)', 'F94001', ?, 'MN_SONG', ?, now())
                RETURNING id
                """,
                Long.class,
                MA_DIEM_DO_B,
                idNguon,
                orgUnitId);
    }

    private void donDep() {
        jdbc.update("DELETE FROM station_measurement_types WHERE station_id IN "
                + "(SELECT id FROM stations WHERE code LIKE 'T2832-%')");
        jdbc.update("DELETE FROM stations WHERE code LIKE 'T2832-%'");
        jdbc.update("DELETE FROM measurement_types WHERE code LIKE 'T2832-%'");
        jdbc.update("DELETE FROM api_sources WHERE code LIKE 'T2832-%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T2832-%'");
    }
}
