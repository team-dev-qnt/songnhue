package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.PortalCache;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.hydro.application.AlertLevelService;
import com.songnhue.hydro.application.StationForm;
import com.songnhue.hydro.application.StationService;
import com.songnhue.hydro.domain.PositionRole;
import com.songnhue.hydro.infra.HydroLatestRecomputer;

/**
 * <b>Ranh giới T35.9</b> — đường ghi nào xoá đệm cổng, đường ghi nào ⛔ KHÔNG.
 *
 * <h2>⛔⛔ Vì sao ranh giới này cần một bài kiểm riêng, không gộp vào bài cũ</h2>
 *
 * <p>{@code PortalCacheInvalidationTest} trả lời câu <i>"cổng có được báo không"</i>. Câu ở đây khó
 * hơn hẳn và <b>ngược chiều</b>: <i>"đường nào ⛔ KHÔNG được báo"</i>. Một khẳng định phủ định thì
 * ⛔ không tự đúng — nó xanh y hệt khi cơ chế chưa từng chạy (luật 7), nên mỗi phép đo âm ở đây phải
 * đi kèm một phép đo dương chứng minh cùng bộ đếm ấy <b>biết nhúc nhích</b>.
 *
 * <h2>Ranh giới đo được, 04/09/2026</h2>
 *
 * <p>{@code hydro} có năm đường ghi. Chúng ⛔ không khác nhau ở <i>loại dữ liệu</i> — cả năm đều
 * chạm số liệu thuỷ văn — mà khác ở <b>tần suất</b>:
 *
 * <ul>
 *   <li><b>Biên tập</b>: con người bấm Lưu, vài lượt/ngày ⇒ xoá đệm. Cổng phải đổi <i>ngay</i>, vì
 *       người vừa bấm sẽ mở cổng để kiểm chứng, và một cổng chưa đổi đọc như <i>"lưu hỏng"</i>.
 *   <li><b>Số đo</b>: poller ghi <b>2 phút/lần vĩnh viễn</b> ⇒ ⛔ không xoá đệm. Cửa sổ ISR 5 phút
 *       đã là câu trả lời đã cam kết với Công ty (OI-09); nối thêm chỉ đổi 5 phút lấy 2 phút và trả
 *       bằng <b>~720 việc/ngày</b> — {@code dedupKey} ⛔ không cứu được, vì hàng đợi chỉ gộp khi việc
 *       cũ còn chờ mà worker rút mỗi 5 giây.
 * </ul>
 *
 * <p>📌 {@code SoDoNhapTayService} nằm ở vế <b>biên tập</b> dù nó ghi một số đo: một con người gõ
 * nó, và lý do họ gõ thường là <i>"API chết, mực nước trên cổng đang sai"</i>. Bắt họ chờ hết 5 phút
 * là bắt chờ đúng lúc con số ấy gấp nhất. Ranh giới là <b>ai ghi</b>, ⛔ không phải <b>ghi gì</b>.
 */
class HydroPortalCacheSplitTest extends IntegrationTestBase {

    private static final String MA = "T359-001";
    private static final String MA_API = "F96001";

    @Autowired
    private StationService stations;

    @Autowired
    private AlertLevelService alertLevels;

    @Autowired
    private HydroLatestRecomputer recomputer;

    @Autowired
    private SettingService settings;

    @Autowired
    private PortalCachePort portalCache;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID nguon;

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        donDep();
        nguon = jdbc.queryForObject(
                "SELECT public_id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", UUID.class);
        assertThat(nguon).as("⚠ vế chống tập rỗng: phải có nguồn dữ liệu seed").isNotNull();
    }

    @AfterEach
    void tearDown() {
        settings.update("hydro.portal.station-codes", "");
        donDep();
    }

    // === Vế DƯƠNG: đường biên tập phải xoá đệm ===============================

    @Test
    @DisplayName("⭐ Thêm điểm đo → ĐẾM ĐƯỢC việc dựng lại cổng, mang nhãn `thuy-van`")
    void creatingAStationEnqueues() {
        donDepHangDoi();
        int truoc = soViec();

        stations.create(hoSo());

        assertThat(soViec())
                .as(
                        """
                        Từ T35.7, `code`/`name`/`riverName`/`chainage` của điểm đo đi THẲNG ra bảng \
                        "Mực nước, lượng mưa" trên cổng. Bằng nhau nghĩa là người nhập bấm Lưu, màn hình \
                        báo thành công, và cổng hiện dữ liệu cũ tới 5 phút. Trước: %d.""",
                        truoc)
                .isGreaterThan(truoc);
        assertThat(payloads()).anyMatch(p -> p.contains(PortalCache.TAG_THUY_VAN));
    }

    @Test
    @DisplayName("⭐ Sửa điểm đo → ĐẾM ĐƯỢC việc dựng lại cổng")
    void updatingAStationEnqueues() {
        UUID id = stations.create(hoSo()).getPublicId();
        donDepHangDoi();
        int truoc = soViec();

        stations.update(id, doiTen("Tên đã đổi, phải lên cổng"));

        assertThat(soViec())
                .as("thêm thì xoá mà sửa thì không là cái bẫy khó thấy hơn hẳn. Trước: %d.", truoc)
                .isGreaterThan(truoc);
    }

    @Test
    @DisplayName("⭐⭐ XOÁ điểm đo → ĐẾM ĐƯỢC việc dựng lại cổng — đường dễ quên nhất, hậu quả nặng nhất")
    void deletingAStationEnqueues() {
        UUID id = stations.create(hoSo()).getPublicId();
        donDepHangDoi();
        int truoc = soViec();

        stations.delete(id);

        assertThat(soViec())
                .as(
                        """
                        ⛔ Một điểm đo ĐÃ XOÁ còn nằm trên cổng là công bố số liệu của một trạm mà người \
                        vận hành vừa tuyên bố là không dùng nữa. Đường xoá không trả về gì nên nhìn qua \
                        không giống một lượt "ghi nội dung" — đó đúng lý do nó cần bài kiểm này. Trước: %d.""",
                        truoc)
                .isGreaterThan(truoc);
    }

    @Test
    @DisplayName("⭐ Đổi MÀU một mức cảnh báo → ĐẾM ĐƯỢC việc dựng lại cổng (T35.14)")
    void changingAnAlertLevelEnqueues() {
        donDepHangDoi();
        int truoc = soViec();

        // ⚠ `severityRank` bị kẹp 1..999 (`AlertLevelService.hang`) — 959 nằm cuối khoảng để ⛔ không
        //   đụng hạng mà bài kiểm khác dựng, và ⛔ không vượt trần.
        alertLevels.create("T359-MUC", "Mức kiểm thử T35.9", "alert-level-3", 959, true, null);

        assertThat(soViec())
                .as("`colorToken` và `name` của mức đi thẳng ra marker GIS lẫn bảng cổng. Trước: %d.", truoc)
                .isGreaterThan(truoc);
    }

    @Test
    @DisplayName("⭐⭐ Đổi DANH SÁCH điểm đo công bố (T35.8) → ĐẾM ĐƯỢC việc dựng lại cổng")
    void changingThePortalStationListEnqueues() {
        donDepHangDoi();
        int truoc = soViec();

        settings.update("hydro.portal.station-codes", MA);

        assertThat(soViec())
                .as(
                        """
                        ⛔ Khoá này là NỘI DUNG CỔNG đội lốt một tham số kỹ thuật — sửa nó là thêm/bớt dòng \
                        trên bảng mực nước. Đây là đường ghi thứ tư của cùng một cơ chế, và nó ra đời CÙNG \
                        ĐỢT với đường đọc — đúng cái bẫy T27.7 đã mô tả. Trước: %d.""",
                        truoc)
                .isGreaterThan(truoc);
    }

    // === ⛔⛔ Vế ÂM: đường số đo ⛔ KHÔNG được xoá đệm ==========================

    /**
     * ⛔⛔ Bài chịu lực nhất của T35.9.
     *
     * <p>{@link HydroLatestRecomputer#dungLai} là lượt ghi mà poller chạy <b>mỗi 2 phút, vĩnh
     * viễn</b>. Nối nó vào {@code PortalCachePort} là một sai lầm <b>trông rất giống việc làm đúng</b>
     * — nó "hoàn thiện" bộ nối, mọi bài kiểm dương ở trên vẫn xanh, và hậu quả (một hàng đợi không
     * bao giờ rỗng) chỉ lộ ra trên staging sau vài ngày.
     *
     * <p>⚠ Bài này giữ <b>cả hai</b> vế trong <b>một</b> phương thức: đo âm rồi đo dương bằng
     * <b>cùng một bộ đếm</b>. Tách ra hai bài thì vế âm có thể xanh vì bộ đếm hỏng, và ⛔ không có gì
     * chỉ ra điều đó (luật 29 — vế kiểm chứng ⛔ không được chia sẻ giả định với vế nó kiểm).
     */
    @Test
    @DisplayName("⛔⛔ Lượt ghi SỐ ĐO (đường poller) ⛔ KHÔNG đặt việc nào — và bộ đếm vẫn biết nhúc nhích")
    void aMeasurementWriteEnqueuesNothing() {
        long idDiemDo = stations.create(hoSo()).getId();
        Long idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        assertThat(idLoai)
                .as("⚠ vế chống tập rỗng: loại chỉ số MUC_NUOC phải có trong seed")
                .isNotNull();
        ghiSoDo(idDiemDo, idLoai);

        donDepHangDoi();
        int truoc = soViec();

        // Đúng lời gọi mà `TelemetryIngestService` chạy sau mỗi lượt polling.
        recomputer.dungLai(idDiemDo, idLoai);

        assertThat(soViec())
                .as(
                        """
                        ⛔ Poller chạy 2 phút/lần VĨNH VIỄN. Một việc dựng lại cổng ở đây là ~720 việc/ngày, \
                        để rút một cửa sổ 5 phút đã CAM KẾT với Công ty (OI-09) xuống 2 phút. `dedupKey` \
                        không cứu được: hàng đợi chỉ gộp khi việc cũ CÒN CHỜ, mà worker rút mỗi 5 giây.""")
                .isEqualTo(truoc);

        // ⭐ Vế dương, cùng bộ đếm: nếu `soViec()` đếm nhầm bảng hay nhầm `job_type` thì khẳng định
        //    trên xanh mà chẳng chứng minh gì.
        portalCache.hydroStationsChanged();
        assertThat(soViec())
                .as("bộ đếm phải nhúc nhích khi gọi thẳng SPI — nếu không thì phép đo mù, ⛔ không phải mã đúng")
                .isGreaterThan(truoc);
    }

    /**
     * ⚠ Vế thứ hai của bộ lọc {@code HydroPortalSettingListener}: nó lọc theo <b>khoá</b>, ⛔ không
     * theo nhóm.
     *
     * <p>Không có bài này thì một bản "dọn dẹp" đổi điều kiện thành {@code "HYDRO".equals(groupCode)}
     * đi lọt hoàn toàn — {@link #changingThePortalStationListEnqueues()} vẫn xanh, và mỗi lần ai đó
     * chỉnh timeout gọi API lại đặt một việc dựng lại cổng. Một lời gọi <b>đúng mà vô nghĩa</b> là
     * thứ dạy người đọc log bỏ qua nó.
     */
    @Test
    @DisplayName("⛔ Đổi một khoá HYDRO KHÁC (timeout polling) ⛔ KHÔNG đặt việc nào")
    void changingAnUnrelatedHydroSettingEnqueuesNothing() {
        String cu = jdbc.queryForObject(
                "SELECT coalesce(setting_value, default_value) FROM settings WHERE setting_key = ?",
                String.class,
                "hydro.polling.timeout-seconds");
        donDepHangDoi();
        int truoc = soViec();
        try {
            settings.update("hydro.polling.timeout-seconds", "45");

            assertThat(soViec())
                    .as("⛔ chín khoá HYDRO còn lại ⛔ không chạm một pixel nào của cổng — lọc theo KHOÁ, "
                            + "⛔ không theo nhóm")
                    .isEqualTo(truoc);
        } finally {
            settings.update("hydro.polling.timeout-seconds", cu);
        }
    }

    // -------------------------------------------------------------------------

    private int soViec() {
        return jdbc.queryForObject("SELECT count(*) FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'", Integer.class);
    }

    private List<String> payloads() {
        return jdbc.queryForList(
                "SELECT payload::text FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'", String.class);
    }

    private void donDepHangDoi() {
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
    }

    private void donDep() {
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
        jdbc.update("DELETE FROM hydro_latest WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA);
        jdbc.update("DELETE FROM alert_levels WHERE code = 'T359-MUC'");
    }

    private void ghiSoDo(long idDiemDo, long idLoai) {
        Instant moc = Instant.now().minusSeconds(60);
        jdbc.update(
                """
                INSERT INTO hydro_latest (
                    station_id, measurement_type_id, last_seen_at, last_quality, last_source,
                    valid_measured_at, valid_value)
                VALUES (?, ?, ?, 'HOP_LE', 'API', ?, ?)
                """,
                idDiemDo,
                idLoai,
                java.sql.Timestamp.from(moc),
                java.sql.Timestamp.from(moc),
                new BigDecimal("1.234"));
    }

    private StationForm hoSo() {
        return moTa("Điểm đo kiểm thử T35.9");
    }

    private StationForm doiTen(String ten) {
        return moTa(ten);
    }

    private StationForm moTa(String ten) {
        return new StationForm(
                MA, ten, MA_API, nguon, PositionRole.THUONG_LUU, null, null, null, null, null, false, true, null, null);
    }
}
