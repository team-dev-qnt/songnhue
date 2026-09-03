package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.ConstructionLookupPort;
import com.songnhue.core.spi.ConstructionRef;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.hydro.domain.CanhBaoDangMo;
import com.songnhue.hydro.domain.NguongApDung;
import com.songnhue.hydro.domain.PositionRole;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.StationConstruction;
import com.songnhue.hydro.infra.AlertEngineRepository;
import com.songnhue.hydro.infra.StationConstructionRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Dựng thông báo cho một cảnh báo ngưỡng và chọn người nhận — <b>T33.7 · T33.8</b>.
 *
 * <h2>⭐⭐ {@code NotifyRequest.alert(...)}, ⛔ TUYỆT ĐỐI KHÔNG {@code targeted(...)}</h2>
 *
 * <p>Hai hàm dựng ấy trông gần giống nhau và phục vụ <b>hai bài toán ngược nhau</b> (§10.10):
 *
 * <ul>
 *   <li>{@code alert} — hệ thống <i>đoán</i> ai nên biết. <b>Không ai "sở hữu" một mực nước vượt
 *       ngưỡng.</b> Người nhận = nhóm "Ban điều hành" ∪ người phụ trách đơn vị liên quan (chốt G11).
 *   <li>{@code targeted} — nơi gọi <i>biết chính xác</i> ai cần biết (quy trình duyệt). Ở đó nhóm
 *       suy ra <b>thay thế</b> Ban điều hành chứ không cộng dồn.
 * </ul>
 *
 * <p>⛔ Nhầm nhánh ở đây thì mỗi lần một biên tập viên bấm "Gửi duyệt" là cả ban lãnh đạo nhận một
 * email; vài tuần sau không ai đọc thông báo nữa — <i>và lúc đó cảnh báo sự cố thật chết theo</i>.
 *
 * <h2>Đơn vị liên quan đến từ đâu — và vì sao có thể RỖNG</h2>
 *
 * <p>Hai nguồn, hợp lại rồi khử trùng lặp: đơn vị quản lý <b>chính điểm đo</b>
 * ({@code stations.org_unit_id}) và đơn vị quản lý <b>các công trình đã liên kết</b> (qua
 * {@code station_constructions} → {@link ConstructionLookupPort}).
 *
 * <p>⚠⚠ <b>Cả hai nguồn hôm nay đều có thể rỗng, và đó ⛔ không phải lỗi:</b>
 *
 * <ul>
 *   <li>19/19 điểm đo còn {@code org_unit_id = NULL} — chờ <b>OI-05</b> (7 hay 8 Xí nghiệp).
 *   <li><b>T33.8</b>: 4/19 điểm là {@code MN_SONG} (TV Hà Nội, TV Ba Thá, An Cảnh, TB Hồng Vân) —
 *       trạm thuỷ văn tham chiếu, ⛔ <b>không thuộc công trình nào theo thiết kế</b>. "Chưa liên
 *       kết" ở đây là dữ liệu ĐỦ, ⛔ không phải dữ liệu thiếu.
 * </ul>
 *
 * <p>Danh sách đơn vị rỗng ⇒ {@code RecipientResolver} rơi về <b>đúng nhóm cố định</b> "Ban điều
 * hành". Đó là hành vi đúng cho một trạm tham chiếu. Với các điểm còn lại, {@link #ghiNhatKyThieu}
 * ghi một dòng để Admin biết mà bổ sung — ⛔ không im lặng, và ⛔ không chặn việc gửi.
 *
 * <h2>⛔ Bẫy phải nói ra: gửi được ⛔ không bằng có người nhận</h2>
 *
 * <p>Đo được 02/09: {@code notification.alert-group.executive-board} đang là {@code '[]'} và
 * {@code org_units.head_user_id} ⛔ <b>không có một lời gọi setter nào trong toàn kho</b>. Nếu cả
 * hai còn rỗng lúc hệ chạy thật thì mọi cảnh báo ở đây tới <b>đúng 0 người</b>, trong khi bảng
 * {@code notifications} vẫn có dòng và mọi bài kiểm {@code verify(notify)} vẫn xanh. ⇒ DoD của
 * WS-33 phải đếm {@code notification_recipients > 0}, ⛔ không đếm "notify được gọi" (luật 27).
 */
@Component
public class AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

    private final StationRepository stations;
    private final StationConstructionRepository lienKets;
    private final ConstructionLookupPort constructions;
    private final AlertEngineRepository nguongs;
    private final NotificationPort notifications;

    public AlertNotifier(
            StationRepository stations,
            StationConstructionRepository lienKets,
            ConstructionLookupPort constructions,
            AlertEngineRepository nguongs,
            NotificationPort notifications) {
        this.stations = stations;
        this.lienKets = lienKets;
        this.constructions = constructions;
        this.nguongs = nguongs;
        this.notifications = notifications;
    }

    /** Cảnh báo vừa được <b>xác nhận</b> — gửi đúng một lần cho mỗi lần vượt. */
    public void baoVuotNguong(long eventId, NguongApDung nguong, BigDecimal giaTri, String lyDo, Instant mocDo) {
        BoiCanh bc = boiCanh(nguong);
        String tieuDe = "Vượt ngưỡng: %s".formatted(bc.moTaViTri());
        String than = "%s lúc %s. %s".formatted(lyDo, mocDo, bc.moTaCongTrinh());
        gui(eventId, nguong, bc, tieuDe, than);
    }

    /** Giá trị đã về dưới ngưỡng — ⚠ chỉ gửi khi cảnh báo <b>đã từng</b> được báo động. */
    public void baoHetVuotNguong(long eventId, NguongApDung nguong, CanhBaoDangMo mo, Instant mocDo) {
        BoiCanh bc = boiCanh(nguong);
        String tieuDe = "Hết vượt ngưỡng: %s".formatted(bc.moTaViTri());
        String than = "Giá trị đã về trong ngưỡng lúc %s. Đỉnh ghi nhận được: %s lúc %s."
                .formatted(mocDo, mo.dinh().toPlainString(), mo.dinhLuc());
        gui(eventId, nguong, bc, tieuDe, than);
    }

    private void gui(long eventId, NguongApDung nguong, BoiCanh bc, String tieuDe, String than) {
        // ⭐ alert(...) — xem khối javadoc ⭐⭐ ở đầu lớp. ⛔ Đừng đổi sang targeted(...).
        notifications.notify(new NotifyRequest(
                NguongAlertService.SU_KIEN_VUOT_NGUONG,
                tieuDe,
                than,
                mucNang(nguong.severityRank()),
                "/thuy-van/canh-bao",
                "ALERT_EVENT",
                eventId,
                List.copyOf(bc.donViIds()),
                List.of(),
                null,
                List.of(com.songnhue.core.spi.NotifyChannel.IN_APP, com.songnhue.core.spi.NotifyChannel.EMAIL)));
        ghiNhatKyThieu(bc);
    }

    /**
     * Mức nặng suy từ <b>danh mục đang có</b>, ⛔ không từ một bảng ánh xạ cứng.
     *
     * <p>Mức nặng nhất đang hoạt động ⇒ {@code CRITICAL}; mọi mức khác ⇒ {@code WARNING}. Danh mục
     * còn rỗng thì không có "nặng nhất" để so — và lúc ấy cũng không có ngưỡng nào để bắn, nên
     * nhánh này chỉ chạy khi ai đó vừa tắt mức nặng nhất giữa chừng.
     */
    private NotifySeverity mucNang(int severityRank) {
        return nguongs.hangNangNhat()
                .filter(nangNhat -> severityRank >= nangNhat)
                .map(x -> NotifySeverity.CRITICAL)
                .orElse(NotifySeverity.WARNING);
    }

    private BoiCanh boiCanh(NguongApDung nguong) {
        Station diemDo = stations.findById(nguong.stationId()).orElse(null);
        if (diemDo == null) {
            // Không thể xảy ra qua đường thường (khoá ngoại), nhưng ⛔ không được ném: một cảnh báo
            // đã ghi xuống CSDL rồi thì việc gửi thông báo hỏng ⛔ không được kéo theo rollback lượt
            // ghi số đo. Ghi log ở mức error để nó không đi qua trong im lặng.
            log.error("Cảnh báo trỏ vào điểm đo #{} không tồn tại — không dựng được thông báo", nguong.stationId());
            return new BoiCanh("điểm đo #" + nguong.stationId(), "", Set.of(), null, 0);
        }

        List<StationConstruction> ds = lienKets.findByStationIdAndDeletedAtIsNull(diemDo.getId());
        Map<Long, ConstructionRef> ct = ds.isEmpty()
                ? Map.of()
                : constructions.timTheoIds(
                        ds.stream().map(StationConstruction::getConstructionId).toList());

        Set<Long> donVis = new LinkedHashSet<>();
        Optional.ofNullable(diemDo.getOrgUnitId()).ifPresent(donVis::add);
        ct.values().stream()
                .map(ConstructionRef::orgUnitId)
                .filter(java.util.Objects::nonNull)
                .forEach(donVis::add);

        String tenCongTrinh = ct.values().stream()
                .map(ConstructionRef::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);

        return new BoiCanh(
                "%s (%s)".formatted(diemDo.getName(), diemDo.getCode()),
                tenCongTrinh == null ? "" : "Công trình liên quan: " + tenCongTrinh + ".",
                donVis,
                diemDo.getPositionRole(),
                ct.size());
    }

    /**
     * T33.8 — ghi lại khi một cảnh báo không tìm được đơn vị nào để suy ra người nhận.
     *
     * <p>⚠ Hai trường hợp, hai mức log khác nhau, và phân biệt được là điểm mấu chốt:
     *
     * <ul>
     *   <li>{@code MN_SONG} không liên kết công trình — <b>đúng thiết kế</b>, chỉ nhóm cố định nhận.
     *       Ghi {@code debug}: réo về một chuyện bình thường là dạy người ta bỏ qua log.
     *   <li>Mọi vai trò khác mà chưa liên kết công trình nào và chưa gán đơn vị — <b>một khoảng
     *       trống dữ liệu</b> cần Admin bổ sung. Ghi {@code warn}, kèm đúng thứ cần làm.
     * </ul>
     */
    private void ghiNhatKyThieu(BoiCanh bc) {
        if (!bc.donViIds().isEmpty()) {
            return;
        }
        if (bc.vaiTro() == PositionRole.MN_SONG) {
            log.debug("Cảnh báo tại {} — trạm tham chiếu MN_SONG, chỉ nhóm Ban điều hành nhận", bc.moTaViTri());
            return;
        }
        log.warn(
                "Cảnh báo tại {} không suy ra được đơn vị nhận: điểm đo chưa gán đơn vị và {} công trình liên kết "
                        + "cũng chưa có đơn vị. Chỉ nhóm Ban điều hành nhận — hãy khai liên kết hoặc gán đơn vị.",
                bc.moTaViTri(),
                bc.soLienKet());
    }

    private record BoiCanh(
            String moTaViTri, String moTaCongTrinh, Set<Long> donViIds, PositionRole vaiTro, int soLienKet) {}
}
