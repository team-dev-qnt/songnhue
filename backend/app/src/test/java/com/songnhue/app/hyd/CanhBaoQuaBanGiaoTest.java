package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.ConstructionStatusService;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * <b>Mắt xích 3 — cảnh báo ngưỡng — cùng chiều lọc với 4 mắt xích còn lại.</b> Luật 13, <b>DOD2.17</b>.
 *
 * <h2>⛔⛔ Vì sao mục này nguy hiểm hơn vẻ ngoài của nó</h2>
 *
 * {@code constructions.operational_status} là một giá trị <b>dẫn xuất được GHI XUỐNG CSDL</b>, ⛔
 * không phải một giá trị tính lúc hiển thị. Nghĩa là nếu một mắt xích của
 * {@code ConstructionStatusService.tinh()} đi qua bộ lọc phạm vi trong khi bốn mắt xích kia thì
 * không, kết quả ⛔ <b>không</b> phụ thuộc người đang xem — nó phụ thuộc <b>ai bấm F5 sau cùng</b>,
 * và giá trị sai ấy được ghi lại <b>cho tất cả mọi người</b>.
 *
 * <p>§10.13 đã trả giá đúng hình dạng này một lần: mắt xích 1 đếm sự cố bằng câu native (không lọc)
 * còn mắt xích 4 tra mã tình hình vận hành bằng câu derived (có lọc), nên người ngoài đơn vị mở màn
 * hình là trạng thái bị hạ xuống <i>"Bình thường"</i> cho cả hệ thống.
 *
 * <h2>⭐ Bất biến đang đúng — bài này GIỮ nó, ⛔ không sửa gì</h2>
 *
 * Đo 07/09/2026: {@code HydroAlertAdapter.hasActiveAlert} →
 * {@code AlertEventQueryRepository.congTrinhDangCanhBao} dùng {@code jdbc.queryForList}, tức
 * <b>native</b> ⇒ ⛔ không chịu {@code @Filter} của Hibernate ⇒ <b>cùng chiều</b> bốn mắt xích kia.
 *
 * <p>⚠ Nhưng luật 7 nói thẳng: <i>một cơ chế chưa ai đi qua thì chưa biết đúng hay sai</i>. Đổi một
 * dòng ở {@code AlertEventQueryRepository} sang câu derived trên một entity có {@code @Filter} sẽ
 * ⛔ <b>không làm đỏ một bài kiểm nào</b> trong 1476 bài hiện có — bài này là bài đầu tiên đi qua nó.
 *
 * <h2>⚠ Vì sao dựng {@code alert_events} thẳng bằng SQL thay vì bắn qua máy cảnh báo</h2>
 *
 * Thứ đang kiểm là <b>chuỗi tính trạng thái</b>, ⛔ không phải máy cảnh báo — {@code AlertEngineHttpTest}
 * đã phủ vòng khép kín *"số đo vượt ngưỡng ⇒ sinh cảnh báo ⇒ lật công trình"*. Đi qua máy cảnh báo ở
 * đây sẽ trộn hai nguyên nhân hỏng vào một bài kiểm: một lượt đỏ ⛔ không nói được là chuỗi tính sai
 * hay máy bắn sai.
 */
class CanhBaoQuaBanGiaoTest extends IntegrationTestBase {

    private static final String MA_CT = "T217-CT";
    private static final String MA_DIEM_DO = "T217-DD";
    private static final String MA_MUC = "T217-MUC";

    /**
     * ⚠ {@code ck_stations_api_code_format} ép {@code ^F[0-9]{5}$} — mã kiểm thử ⛔ không tự bịa được.
     *
     * <p>⭐ Đáng ghi lại vì nó **hẹp hơn** regex của parser ({@code ^([A-Z]\d+)}, giữ đúng spec), và
     * sự lệch ấy là **cố ý**: parser phải bóc được thứ nguồn <i>gửi tới</i>, còn danh mục thì kén thứ
     * ta <i>đăng ký</i>. Nới ràng buộc CSDL cho khớp parser là mở đường cho một mã gõ nhầm nằm vĩnh
     * viễn trong danh mục.
     */
    private static final String MA_API = "F97217";

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private ConstructionStatusService trangThai;

    @Autowired
    private JdbcTemplate jdbc;

    private long rootId;
    private String pathRoot;
    private long xnAId;
    private long xnBId;
    private UUID xnAPublicId;
    private UUID xnBPublicId;
    private String pathA;
    private String pathB;

    private UUID congTrinhPublicId;
    private long idCongTrinh;

    @BeforeEach
    void dungDuLieu() {
        donDep();

        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        xnAId = themDonVi("T217-XN-A", "Xí nghiệp A (T217)");
        xnBId = themDonVi("T217-XN-B", "Xí nghiệp B (T217)");
        xnAPublicId = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnAId);
        xnBPublicId = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnBId);
        pathA = jdbc.queryForObject("SELECT path FROM org_units WHERE id = ?", String.class, xnAId);
        pathB = jdbc.queryForObject("SELECT path FROM org_units WHERE id = ?", String.class, xnBId);

        laHeThong();
        congTrinhPublicId = constructions
                .create(hoSo(MA_CT, "Cống kiểm thử bàn giao", xnAPublicId))
                .getPublicId();

        idCongTrinh = jdbc.queryForObject("SELECT id FROM constructions WHERE code = ?", Long.class, MA_CT);
        long idDiemDo = themDiemDo();
        jdbc.update(
                """
                INSERT INTO station_constructions (
                    station_id, construction_id, construction_public_id, role, is_primary, created_at)
                SELECT ?, c.id, c.public_id, 'MN_SONG', TRUE, now() FROM constructions c WHERE c.id = ?
                """,
                idDiemDo,
                idCongTrinh);
        moCanhBaoDangXayRa(idDiemDo);
    }

    @AfterEach
    void dep() {
        AuthContext.clear();
        donDep();
    }

    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("⭐⭐ Tính lại BẰNG VAI TRÒ của Xí nghiệp KHÁC — cờ CẢNH BÁO VẪN đúng, ⛔ không âm thầm tắt")
    void tinhLaiBangVaiTroNgoaiDonViVanRaCanhBao() {
        laHeThong();
        assertThat(trangThai.recomputeFor(idCongTrinh))
                .as("⚠ Vế chống tập rỗng: ⛔ không có cảnh báo nào thì ba khẳng định dưới đây vô nghĩa")
                .isEqualTo(OperationalStatus.CANH_BAO);

        // ⛔⛔ ĐÂY là phép đo của luật 13, và nó gọi thẳng đường mà `NguongAlertService` +
        //    `StatusReconcileJob` đi. Người của B ⛔ không "thấy" điểm đo của A; nếu mắt xích 3 đi qua
        //    bộ lọc phạm vi thì lượt tính này kết luận công trình ⛔ không còn cảnh báo — và kết luận
        //    ấy được GHI XUỐNG cột `operational_status` cho TẤT CẢ mọi người.
        AuthContext.set(nguoiDungTai(xnBId, pathB));
        assertThat(trangThai.recomputeFor(idCongTrinh))
                .as("trạng thái là sự thật về CÔNG TRÌNH, ⛔ không phải về người đang nhìn")
                .isEqualTo(OperationalStatus.CANH_BAO);

        assertThat(trongCsdl())
                .as("⛔ Và nó phải ĐÚNG TRONG BẢNG — cột dẫn xuất là thứ mọi màn hình khác đọc")
                .isEqualTo(OperationalStatus.CANH_BAO.name());
    }

    @Test
    @DisplayName("⭐⭐ Bàn giao công trình sang Xí nghiệp khác — cờ CẢNH BÁO sống sót")
    void canhBaoSongSotQuaLuotBanGiao() {
        laHeThong();
        assertThat(trangThai.recomputeFor(idCongTrinh)).isEqualTo(OperationalStatus.CANH_BAO);

        // Bàn giao hồ sơ sang B. Điểm đo và cảnh báo ⛔ KHÔNG đi theo — chúng thuộc `hydro`, và
        // `alert_events` ⛔ cố ý không có cột phạm vi: một sự kiện ĐÃ XẢY RA ⛔ không đổi chủ khi hồ
        // sơ đổi chủ.
        laHeThong();
        constructions.update(congTrinhPublicId, hoSo(MA_CT, "Cống kiểm thử bàn giao", xnBPublicId));
        assertThat(trongCsdl())
                .as("lượt GHI là lúc giá trị dẫn xuất được tính lại VÀ lưu xuống")
                .isEqualTo(OperationalStatus.CANH_BAO.name());

        // Và một lượt ghi tiếp của chính người B cũng ⛔ không được làm cờ tắt.
        AuthContext.set(nguoiDungTai(xnBId, pathB));
        assertThat(trangThai.recomputeFor(idCongTrinh)).isEqualTo(OperationalStatus.CANH_BAO);
        assertThat(trongCsdl()).isEqualTo(OperationalStatus.CANH_BAO.name());
    }

    @Test
    @DisplayName("⭐ Vế phân biệt — ĐÓNG cảnh báo thì trạng thái PHẢI về BINH_THUONG")
    void dongCanhBaoThiVeBinhThuong() {
        // ⛔ Không có vế này thì một cài đặt ghi cứng `return CANH_BAO` cũng làm hai bài trên xanh
        //    trọn vẹn (luật 9 — một khẳng định ⛔ không phân biệt được hai trạng thái thì ⛔ không
        //    khẳng định gì).
        laHeThong();
        assertThat(trangThai.recomputeFor(idCongTrinh)).isEqualTo(OperationalStatus.CANH_BAO);

        jdbc.update(
                "UPDATE alert_events SET status = 'DA_XU_LY', ended_at = now(), resolved_at = now() "
                        + "WHERE station_id = (SELECT id FROM stations WHERE code = ?)",
                MA_DIEM_DO);

        assertThat(trangThai.recomputeFor(idCongTrinh)).isEqualTo(OperationalStatus.BINH_THUONG);
        assertThat(trongCsdl()).isEqualTo(OperationalStatus.BINH_THUONG.name());
    }

    @Test
    @DisplayName("⛔ Cảnh báo CHƯA XÁC NHẬN (còn trong `delay_minutes`) ⛔ KHÔNG lật trạng thái")
    void chuaXacNhanThiChuaLatTrangThai() {
        // ⚠ Vế này ra đời từ một lượt đỏ THẬT: bản đầu của fixture để `confirmed_at` rỗng và bài
        //    chính đỏ với BINH_THUONG. Mã đúng, fixture sai — và *"có một cảnh báo"* ⛔ không phải
        //    *"đang cảnh báo"*. Giữ lại thành một khẳng định để người sau ⛔ không đọc lượt đỏ ấy
        //    thành "mắt xích 3 hỏng" rồi đi vá nhầm chỗ.
        jdbc.update(
                "UPDATE alert_events SET confirmed_at = NULL "
                        + "WHERE station_id = (SELECT id FROM stations WHERE code = ?)",
                MA_DIEM_DO);

        laHeThong();
        assertThat(trangThai.recomputeFor(idCongTrinh)).isEqualTo(OperationalStatus.BINH_THUONG);
    }

    private String trongCsdl() {
        return jdbc.queryForObject("SELECT operational_status FROM constructions WHERE code = ?", String.class, MA_CT);
    }

    // ─────────────── Dựng và dọn ───────────────

    private long themDonVi(String ma, String ten) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                ten,
                rootId);
        String path = pathRoot + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private long themDiemDo() {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon)
                .as("⚠ Vế chống tập rỗng: ⛔ không có nguồn seed nào thì điểm đo ⛔ không tạo được")
                .isNotNull();
        return jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử bàn giao', ?, ?, 'MN_SONG', TRUE, now())
                RETURNING id
                """,
                Long.class,
                MA_DIEM_DO,
                MA_API,
                idNguon);
    }

    /**
     * Một cảnh báo <b>đang xảy ra VÀ ĐÃ XÁC NHẬN</b> — trạng thái mà mắt xích 3 đọc.
     *
     * <h2>⚠⚠ {@code confirmed_at IS NOT NULL} ⛔ không phải chi tiết vụn</h2>
     *
     * Bản đầu của fixture này để {@code confirmed_at} rỗng và bài kiểm đỏ với {@code BINH_THUONG}.
     * <b>Mã đúng, fixture sai</b>: điều kiện thật là
     * {@code e.status = 'DANG_XAY_RA' AND e.confirmed_at IS NOT NULL}, tức một cảnh báo còn trong
     * cửa sổ {@code delay_minutes} <b>chưa</b> lật trạng thái công trình — đúng như
     * {@code AlertEngineHttpTest} khẳng định (<i>"lượt vượt ĐẦU TIÊN chưa báo động"</i>).
     *
     * <p>⇒ *"Có một cảnh báo"* và *"đang cảnh báo"* là <b>hai trạng thái khác nhau</b>. Ghi lại vì
     * một lượt đọc vội sẽ đọc lượt đỏ ấy thành *"mắt xích 3 hỏng"* và đi vá nhầm chỗ.
     */
    private void moCanhBaoDangXayRa(long idDiemDo) {
        Long idLoaiChiSo = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Long idMuc = jdbc.queryForObject(
                "INSERT INTO alert_levels (code, name, color_token, severity_rank, active, created_at) "
                        + "VALUES (?, 'Mức kiểm thử bàn giao', 'alert-level-1', 217, TRUE, now()) RETURNING id",
                Long.class,
                MA_MUC);
        Long idQuyTac = jdbc.queryForObject(
                """
                INSERT INTO alert_rules (
                    station_id, measurement_type_id, alert_level_id, condition_type,
                    threshold_value, delay_minutes, active, created_at)
                VALUES (?, ?, ?, 'GT', 2.000, 0, TRUE, now())
                RETURNING id
                """,
                Long.class,
                idDiemDo,
                idLoaiChiSo,
                idMuc);
        // ⚠ Mốc TƯỜNG MINH lùi về quá khứ, ⛔ không dùng `now()`: `now()` của Postgres là mốc **bắt
        //    đầu giao dịch**, và giao dịch của bài kiểm có thể mở TRƯỚC lượt dựng dữ liệu này ⇒ câu
        //    đóng cảnh báo (`ended_at = now()`) rơi vào quá khứ so với `started_at` và
        //    `ck_alert_events_thu_tu_moc` từ chối. Đã trả giá đúng chỗ này ở lượt chạy đầu.
        Instant batDau = Instant.now().minusSeconds(600);
        Instant xacNhan = Instant.now().minusSeconds(300);
        jdbc.update(
                """
                INSERT INTO alert_events (
                    rule_id, station_id, measurement_type_id, alert_level_id,
                    started_at, confirmed_at, status, trigger_value, peak_value, peak_at, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'DANG_XAY_RA', 3.000, 3.000, ?, '3.000 > 2.000', now())
                """,
                idQuyTac,
                idDiemDo,
                idLoaiChiSo,
                idMuc,
                java.sql.Timestamp.from(batDau),
                java.sql.Timestamp.from(xacNhan),
                java.sql.Timestamp.from(xacNhan));
    }

    /**
     * ⛔ Dọn theo thứ tự khoá ngoại, và dọn <b>trước</b> mỗi lượt chạy chứ ⛔ không chỉ sau: một lượt
     * chạy trước bị ngắt giữa chừng để lại điểm đo thừa, và {@code HydroCatalogueSeedTest} khẳng định
     * danh mục có <b>đúng 19</b> điểm đo — nó sẽ đỏ ở một lớp khác, với thông điệp ⛔ không hề chỉ về
     * phía nguyên nhân.
     */
    private void donDep() {
        jdbc.update(
                "DELETE FROM alert_events WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM alert_rules WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM alert_levels WHERE code = ?", MA_MUC);
        jdbc.update(
                "DELETE FROM station_constructions WHERE station_id IN (SELECT id FROM stations WHERE code = ?)",
                MA_DIEM_DO);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA_DIEM_DO);
        jdbc.update("DELETE FROM constructions WHERE code = ?", MA_CT);
        jdbc.update("DELETE FROM org_units WHERE code IN ('T217-XN-A', 'T217-XN-B')");
    }

    private void laHeThong() {
        AuthContext.set(nguoiDungTai(rootId, pathRoot));
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                nguoiDungKiemThuId(),
                UUID.randomUUID(),
                "t217-probe",
                "Người kiểm thử T217",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of("ops:construction:update"),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    /** Khoá ngoại người dùng là NOT NULL — mượn một tài khoản seed có sẵn. */
    private Long nguoiDungKiemThuId() {
        return jdbc.queryForObject("SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
    }

    private static ConstructionForm hoSo(String ma, String ten, UUID donViPublicId) {
        return new ConstructionForm(
                ma,
                ten,
                ConstructionType.CONG,
                null,
                donViPublicId,
                ManagementLevel.XI_NGHIEP,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
