package com.songnhue.operations.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.core.spi.SettingPort;
import com.songnhue.hydro.spi.HydroAlertQueryPort;
import com.songnhue.hydro.spi.HydroLatestQueryPort;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.MaintenanceLogRepository;

/**
 * Dashboard điều hành — CN-02.5 / T23.6, T23.7.
 *
 * <h2>⛔ Ô nào chưa có nguồn thì nói thẳng là chưa có</h2>
 *
 * CN-02.5 liệt kê sáu nhóm KPI. Ở WS-23 có bốn nhóm chưa có nguồn; WS-18 đã trả hai (công việc bảo
 * trì · sự cố chưa xử lý) và <b>WS-35 / T35.3 đã trả hai ô cuối</b> — cảnh báo thuỷ văn và điểm đo
 * mất tín hiệu — qua {@link com.songnhue.hydro.spi.HydroAlertQueryPort} và
 * {@link com.songnhue.hydro.spi.HydroLatestQueryPort}. ✅ Từ 04/09/2026 <b>không còn ô nào trả
 * {@code null}</b>.
 *
 * <p>⚠ Cơ chế {@link Kpi#chuaCo} vẫn <b>giữ nguyên</b> dù hiện không ai gọi. Đây là ngoại lệ có chủ
 * đích với luật 15: nó là ràng buộc ở <i>tầng kiểu</i> cho mọi ô KPI thêm về sau, và bài kiểm của
 * chính {@link Kpi} vẫn đi qua nó. ⛔ Đừng "dọn dẹp" nó đi — ô KPI thứ mười hai sẽ cần đúng nó.
 *
 * <p>⚠⚠ <b>Không được trả số 0.</b> Số 0 nghĩa là "đã đo và bằng không" — trên một dashboard điều
 * hành công trình thuỷ lợi, ô "Sự cố chưa xử lý: 0" là câu khẳng định rằng không có sự cố nào, và
 * người trực sẽ tin nó. "Chưa có dữ liệu" và "có dữ liệu, bằng không" là hai câu khác nhau, và ở
 * đây chọn nhầm câu là mất niềm tin vào toàn bộ màn hình. Bản ghi kiểu {@link Kpi} ép điều đó ở
 * tầng kiểu: một ô không có số mà không nói được lý do thì <b>không dựng được</b>.
 *
 * <h2>Một lượt gọi, không phải bảy</h2>
 *
 * Quy tắc 3 của dự án: mọi giá trị tính toán tính ở BE. Để FE gọi từng endpoint rồi tự cộng là
 * chuyển phép tính sang chỗ không kiểm chứng được, và mỗi màn hình sẽ cộng theo một cách.
 *
 * <h2>⚠⚠ Con số ở đây đã bị lọc theo phạm vi đơn vị — TRỪ hai ô thuỷ văn</h2>
 *
 * Giống {@link ConstructionStatisticsService}: người của Xí nghiệp thấy số của Xí nghiệp mình. Đó là
 * hành vi đúng, và cũng là lý do endpoint này không bao giờ được đem ra cổng công khai.
 *
 * <p>⛔ <b>Ngoại lệ có tên, khai ra thay vì để im lặng</b> (T35.3): {@code hydro.active-alerts} và
 * {@code hydro.stations-offline} <b>không lọc phạm vi</b>. Ba lý do, theo thứ tự sức nặng:
 *
 * <ol>
 *   <li>⭐ <i>"Điểm đo này đang im lặng"</i> là một <b>sự thật về thiết bị</b>, không phụ thuộc ai
 *       đang nhìn — cùng lập luận đã ghi ở {@code core.spi.HydroAlertPort#hasActiveAlert}.
 *   <li>Định nghĩa "mất tín hiệu" dùng chung <b>đúng một</b> đường với job rà tín hiệu 5 phút/lần
 *       ({@code HydroSignalLossHandler}), và job thì không có người dùng để mà lọc theo. Dựng một
 *       đường lọc song song là dựng định nghĩa thứ hai — xem javadoc {@code HydroQueryAdapter}.
 *   <li>⚠ Hôm nay hai cách cho ra <b>cùng một con số</b>: cả 19 điểm đo có {@code org_unit_id} NULL
 *       (chặn bởi <b>OI-05</b> — 7 hay 8 Xí nghiệp), và {@code Station.LOC_PHAM_VI} cho NULL đi qua
 *       với mọi người. ⇒ Chọn cách lọc lúc này là dựng một cơ chế <b>không phép kiểm nào phân biệt
 *       được đúng hay sai</b> (luật 7).
 * </ol>
 *
 * <p>⚠ Khi OI-05 về và {@code stations.org_unit_id} có giá trị thật, hai con số sẽ tách nhau và
 * <b>phải quyết lại</b>. Đây ⛔ không phải chỗ để im lặng chọn mặc định — ⛔ không lọc là quyết định
 * <i>đang</i> có hiệu lực, không phải thứ chưa ai nghĩ tới.
 */
@Service
public class DashboardService {

    /** Sắc thái của một ô KPI — FE tra sang bảng màu trạng thái ở {@code design-tokens}. */
    public enum Tone {
        NORMAL,
        WARNING,
        DANGER,
        UNKNOWN
    }

    /**
     * Một ô KPI.
     *
     * @param value {@code null} = <b>chưa có nguồn dữ liệu</b>, khác hẳn với 0
     * @param total mẫu số khi ô là một tỉ lệ ("32 / 40"); {@code null} khi chỉ là một con số
     * @param unavailableReason bắt buộc có khi {@code value} rỗng — xem ràng buộc ở hàm dựng
     * @param availableIn hạng mục sẽ mang dữ liệu về, VD {@code "WS-18"}; nói thẳng bao giờ có
     */
    // ⚠⚠ ALWAYS đè cấu hình NON_NULL chung của Jackson, và đây là chỗ duy nhất trong dự án cần điều
    // đó. Bỏ hẳn khoá `value` khỏi JSON thì phía nhận đọc ra `undefined` — không phân biệt được với
    // "API đổi tên trường" hay "bản cũ chưa có trường này". Cả thiết kế của ô KPI dựa trên việc nói
    // rõ "không có số"; để nó im lặng biến mất là mâu thuẫn với chính điều đang cố diễn đạt.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Kpi(
            String key, String label, Long value, Long total, Tone tone, String unavailableReason, String availableIn) {

        /**
         * ⛔ Ép "ô trống phải nói được vì sao nó trống" ở tầng kiểu.
         *
         * <p>Kiểm ở đây chứ không ở bài kiểm, vì bài kiểm chỉ phủ những ô đã tồn tại lúc viết nó.
         * Ô KPI thứ mười một — thêm vào lúc dựng WS-18 hay Phase 2 — cũng phải đi qua đúng ràng
         * buộc này mà không cần ai nhớ.
         */
        public Kpi {
            if (value == null && (unavailableReason == null || unavailableReason.isBlank())) {
                throw new IllegalArgumentException(
                        "KPI '%s' không có số thì bắt buộc phải nói lý do — một ô trống không giải thích "
                                        .formatted(key)
                                + "được đọc thành 'bằng không'");
            }
        }

        static Kpi co(String key, String label, long value, Long total, Tone tone) {
            return new Kpi(key, label, value, total, tone, null, null);
        }

        static Kpi chuaCo(String key, String label, String lyDo, String seCoO) {
            return new Kpi(key, label, null, null, Tone.UNKNOWN, lyDo, seCoO);
        }
    }

    /**
     * @param autoRefreshSeconds chu kỳ tự làm mới — đọc {@code settings} <b>mỗi lượt gọi</b> (T23.5)
     * @param wallRotateSeconds chu kỳ tự chuyển khối ở chế độ màn hình lớn
     */
    public record Dashboard(
            Instant generatedAt,
            int autoRefreshSeconds,
            int wallRotateSeconds,
            List<Kpi> kpis,
            ConstructionStatisticsService.Statistics statistics,
            MapConfigService.MapConfig map) {}

    public static final String KEY_AUTO_REFRESH = "system.dashboard.auto-refresh-minutes";
    public static final String KEY_WALL_ROTATE = "system.wall.auto-rotate-seconds";

    private final ConstructionRepository constructions;
    private final MaintenanceLogRepository maintenanceLogs;
    private final ConstructionStatisticsService statistics;
    private final MapConfigService mapConfig;
    private final SettingPort settings;
    private final HydroLatestQueryPort hydroTinHieu;
    private final HydroAlertQueryPort hydroCanhBao;

    public DashboardService(
            ConstructionRepository constructions,
            MaintenanceLogRepository maintenanceLogs,
            ConstructionStatisticsService statistics,
            MapConfigService mapConfig,
            SettingPort settings,
            HydroLatestQueryPort hydroTinHieu,
            HydroAlertQueryPort hydroCanhBao) {
        this.constructions = constructions;
        this.maintenanceLogs = maintenanceLogs;
        this.statistics = statistics;
        this.mapConfig = mapConfig;
        this.settings = settings;
        this.hydroTinHieu = hydroTinHieu;
        this.hydroCanhBao = hydroCanhBao;
    }

    @Transactional(readOnly = true)
    public Dashboard summary() {
        Map<OperationalStatus, Long> theoTrangThai = demTheoTrangThai();
        Map<LifecycleState, Long> theoVongDoi = demTheoVongDoi();
        long tong = theoVongDoi.values().stream().mapToLong(Long::longValue).sum();

        List<Kpi> kpis = new ArrayList<>();
        kpis.add(Kpi.co(
                "construction.active",
                "Công trình đang hoạt động",
                theoVongDoi.getOrDefault(LifecycleState.DANG_HOAT_DONG, 0L),
                tong,
                Tone.NORMAL));
        kpis.add(Kpi.co(
                "construction.normal",
                "Bình thường",
                theoTrangThai.getOrDefault(OperationalStatus.BINH_THUONG, 0L),
                null,
                Tone.NORMAL));
        kpis.add(Kpi.co(
                "construction.warning",
                "Cảnh báo",
                theoTrangThai.getOrDefault(OperationalStatus.CANH_BAO, 0L),
                null,
                Tone.WARNING));
        kpis.add(Kpi.co(
                "construction.incident",
                "Sự cố",
                theoTrangThai.getOrDefault(OperationalStatus.SU_CO, 0L),
                null,
                Tone.DANGER));
        kpis.add(Kpi.co(
                "construction.maintenance",
                "Đang bảo trì",
                theoTrangThai.getOrDefault(OperationalStatus.BAO_TRI, 0L),
                null,
                Tone.WARNING));

        // Nhắc việc, không phải số liệu trang trí: hồ sơ thiếu toạ độ thì không lên bản đồ, và một
        // công trình vắng mặt trên bản đồ điều hành là thứ không ai phát hiện bằng cách nhìn bản đồ.
        long chuaSoHoa = constructions.countByLatitudeIsNullAndDeletedAtIsNull();
        kpis.add(Kpi.co(
                "construction.without-location",
                "Chưa số hoá toạ độ",
                chuaSoHoa,
                tong,
                chuaSoHoa == 0 ? Tone.NORMAL : Tone.WARNING));

        // === Hai ô thuỷ văn — T35.3, nay CÓ NGUỒN THẬT ======================
        //
        // ⚠⚠ Từ 04/09/2026 số 0 ở hai ô này là một câu KHẲNG ĐỊNH: "đã đếm, và không có". Trước đó
        //    chúng cố ý trả `null` kèm lý do suốt từ WS-23, vì MOD-03 chưa tồn tại — và đó là lý do
        //    `DashboardHttpTest` từng khẳng định `doesNotContain("\"value\":0")`. Khẳng định ấy đã
        //    được ĐẢO trong cùng commit này, ⛔ không phải nới ra: một ô "Cảnh báo thuỷ văn: 0" chỉ
        //    được phép xuất hiện sau khi thật sự có ai đếm.
        //
        // ⛔ Dữ liệu đọc qua `hydro.spi`, ⛔ không đụng bảng `hydro_*` — xem chú thích ở
        //    `operations/pom.xml` về cạnh Maven `operations → hydro` và hệ quả không đảo ngược được.
        long canhBaoDangMo = hydroCanhBao.demCanhBaoDangXayRa();
        kpis.add(Kpi.co(
                "hydro.active-alerts",
                "Cảnh báo thuỷ văn đang xảy ra",
                canhBaoDangMo,
                null,
                canhBaoDangMo == 0 ? Tone.NORMAL : Tone.DANGER));

        // ⭐ Tử số và mẫu số đến từ MỘT ảnh chụp — xem `HydroLatestQueryPort.TinhTrangTinHieu`.
        //    Hai lượt đếm rời là hai mốc thời gian, và ô KPI sẽ có lúc hiện "20 / 19".
        HydroLatestQueryPort.TinhTrangTinHieu tinHieu = hydroTinHieu.tinhTrangTinHieu();
        kpis.add(Kpi.co(
                "hydro.stations-offline",
                "Điểm đo mất tín hiệu",
                tinHieu.matTinHieu(),
                tinHieu.dangDung(),
                tinHieu.matTinHieu() == 0 ? Tone.NORMAL : Tone.WARNING));

        // === Hai ô WS-18 vừa trả nợ — nay có nguồn thật ======================
        //
        // ⚠ Từ đây trở đi số 0 ở hai ô này là một câu KHẲNG ĐỊNH: "đã đếm, và không có bản ghi nào
        //   đang mở". Đó là điều đúng, và cũng là lý do chúng không được trả 0 trước khi WS-18 tồn
        //   tại — một ô "Sự cố chưa xử lý: 0" trên màn hình trực ban là thứ người ta tin.
        long suCoDangMo = maintenanceLogs.countOpenIncidents();
        kpis.add(Kpi.co(
                "incident.open", "Sự cố chưa xử lý", suCoDangMo, null, suCoDangMo == 0 ? Tone.NORMAL : Tone.DANGER));

        long baoTriDangLam = maintenanceLogs.countOpenWork();
        kpis.add(Kpi.co(
                "maintenance.in-progress",
                "Công việc bảo trì đang thực hiện",
                baoTriDangLam,
                null,
                baoTriDangLam == 0 ? Tone.NORMAL : Tone.WARNING));

        return new Dashboard(
                Instant.now(),
                chuKyLamMoiGiay(),
                settings.getInt(KEY_WALL_ROTATE, 30),
                List.copyOf(kpis),
                statistics.summary(),
                mapConfig.current());
    }

    /**
     * Chu kỳ tự làm mới, tính bằng giây.
     *
     * <p>⚠ Đọc {@code settings} ở <b>mỗi lượt gọi</b>, không phải một lần lúc dựng bean. Chốt lúc
     * dựng thì tham số M2.15 bày ra màn hình cấu hình trở thành công tắc chết: quản trị viên đổi từ
     * 5 phút xuống 1 phút, giao diện báo lưu thành công, và không gì thay đổi cho tới lần khởi động
     * lại tiếp theo. Đây đúng là lỗi đã trả giá ở WS-12 với hạn mức tải tệp.
     */
    private int chuKyLamMoiGiay() {
        return (int) settings.getMinutes(KEY_AUTO_REFRESH, 5).toSeconds();
    }

    private Map<OperationalStatus, Long> demTheoTrangThai() {
        Map<OperationalStatus, Long> ket = new EnumMap<>(OperationalStatus.class);
        for (Object[] dong : constructions.countByStatus()) {
            ket.put((OperationalStatus) dong[0], ((Number) dong[1]).longValue());
        }
        return ket;
    }

    private Map<LifecycleState, Long> demTheoVongDoi() {
        Map<LifecycleState, Long> ket = new EnumMap<>(LifecycleState.class);
        for (Object[] dong : constructions.countByLifecycle()) {
            ket.put((LifecycleState) dong[0], ((Number) dong[1]).longValue());
        }
        return ket;
    }
}
