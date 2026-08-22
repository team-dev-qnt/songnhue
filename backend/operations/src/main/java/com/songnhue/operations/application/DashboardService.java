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
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.MaintenanceLogRepository;

/**
 * Dashboard điều hành — CN-02.5 / T23.6, T23.7.
 *
 * <h2>⛔ Ô nào chưa có nguồn thì nói thẳng là chưa có</h2>
 *
 * CN-02.5 liệt kê sáu nhóm KPI. Ở WS-23 có bốn nhóm chưa có nguồn; WS-18 đã trả hai trong số đó
 * (công việc bảo trì và sự cố chưa xử lý). Còn <b>hai</b> ô thuộc MOD-03 — cảnh báo thuỷ văn và
 * điểm đo mất tín hiệu — trả {@code value = null} kèm <b>lý do</b> và <b>mốc thời gian sẽ có</b>.
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
 * <h2>⚠ Con số ở đây đã bị lọc theo phạm vi đơn vị</h2>
 *
 * Giống {@link ConstructionStatisticsService}: người của Xí nghiệp thấy số của Xí nghiệp mình. Đó là
 * hành vi đúng, và cũng là lý do endpoint này không bao giờ được đem ra cổng công khai.
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

    private static final String LY_DO_THUY_VAN = "Chưa đấu nối dữ liệu thuỷ văn";

    private final ConstructionRepository constructions;
    private final MaintenanceLogRepository maintenanceLogs;
    private final ConstructionStatisticsService statistics;
    private final MapConfigService mapConfig;
    private final SettingPort settings;

    public DashboardService(
            ConstructionRepository constructions,
            MaintenanceLogRepository maintenanceLogs,
            ConstructionStatisticsService statistics,
            MapConfigService mapConfig,
            SettingPort settings) {
        this.constructions = constructions;
        this.maintenanceLogs = maintenanceLogs;
        this.statistics = statistics;
        this.mapConfig = mapConfig;
        this.settings = settings;
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

        // === Hai ô chưa có nguồn — CN-02.5 đòi, dữ liệu chưa tồn tại =========
        kpis.add(
                Kpi.chuaCo("hydro.active-alerts", "Cảnh báo thuỷ văn đang xảy ra", LY_DO_THUY_VAN, "Phase 2 (MOD-03)"));
        kpis.add(Kpi.chuaCo("hydro.stations-offline", "Điểm đo mất tín hiệu", LY_DO_THUY_VAN, "Phase 2 (MOD-03)"));

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
