package com.songnhue.operations.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.HydroAlertPort;
import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.MaintenanceLogRepository;

/**
 * Tính trạng thái vận hành công trình — CN-02.1, quy tắc 4 của dự án.
 *
 * <h2>⛔ Nơi DUY NHẤT ghi cột {@code operational_status}</h2>
 *
 * Không màn hình nào sửa được, không endpoint nào nhận được ({@code OPS-3001}). Lý do không phải là
 * sự cẩn thận thừa: trạng thái công trình quyết định màu marker trên bản đồ điều hành và ô "Sự cố
 * chưa xử lý" trên dashboard. Cho phép sửa tay thì sẽ có lúc một công trình mang cờ đỏ mà không có
 * bản ghi sự cố nào, hoặc ngược lại — và không ai đối chiếu được vì hai nguồn đều "hợp lệ".
 *
 * <h2>Chuỗi suy ra — WS-18 đã cắm hai mắt xích đầu</h2>
 *
 * CN-02.1 xếp 5 mức ưu tiên. Tình trạng hiện tại:
 *
 * <ol>
 *   <li>Sự cố đang mở → {@code SU_CO} ✔ <i>đang chạy từ WS-18</i>
 *   <li>Bảo trì đang thực hiện → {@code BAO_TRI} ✔ <i>đang chạy từ WS-18</i>
 *   <li>Cảnh báo ngưỡng đang xảy ra → {@code CANH_BAO} — cần MOD-03, <b>Phase 2</b>
 *   <li>Ánh xạ từ mã tình hình vận hành → <b>WS-19</b>
 *   <li>Mặc định {@code BINH_THUONG} ✔
 * </ol>
 *
 * <p>WS-19 và Phase 2 thêm luật vào <b>đúng hàm {@link #tinh}</b>, không mở đường ghi mới — nếu
 * không thì sẽ có hai nơi cùng ghi một cột và chúng sẽ ghi đè lẫn nhau theo thứ tự ngẫu nhiên.
 *
 * <h2>⚠⚠ Trạng thái là sự thật về CÔNG TRÌNH, không phải về người đang nhìn</h2>
 *
 * <b>Toàn bộ</b> câu tra trong {@link #tinh} đi bằng native nên không bị bộ lọc phạm vi đơn vị áp
 * vào — xem {@link MaintenanceLogRepository#demBanGhiDangMo} và
 * {@link ConstructionOperationStatusRepository#banGhiMoiNhat}. Đây là điều kiện để cột này có một
 * giá trị duy nhất: nếu phép tra phụ thuộc người gọi thì mỗi lượt tính lại sẽ ghi ra một giá trị
 * khác nhau và cột cuối cùng mang giá trị của người mở màn hình gần nhất.
 *
 * <p>⚠ Bảo đảm đó từng đúng với hai mắt xích đầu và <b>sai với mắt xích 4</b>: nó dùng câu derived
 * nên có lọc. Hậu quả không phải là chặn nhầm mà là ghi sai — người ngoài đơn vị mở màn hình, mắt
 * xích 4 tra ra rỗng, và trạng thái bị hạ xuống "Bình thường" cho tất cả mọi người. Một cột dẫn xuất
 * trộn hai nguồn khác chiều lọc thì kết quả phụ thuộc <i>ai bấm F5 sau cùng</i>.
 *
 * <h2>Vòng đời đứng trên tất cả</h2>
 *
 * Một công trình đã thanh lý không có "sự cố đang mở" nào đáng hiển thị, và hiện "Bình thường" cho
 * nó là nói sai. Nên {@link LifecycleState} được xét trước toàn bộ chuỗi trên.
 */
@Service
public class ConstructionStatusService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionStatusService.class);

    private final ConstructionRepository constructions;
    private final MaintenanceLogRepository maintenanceLogs;
    private final ConstructionOperationStatusRepository operationStatuses;
    private final HydroAlertPort hydroAlertPort;
    private final PortalCachePort portalCache;

    public ConstructionStatusService(
            ConstructionRepository constructions,
            MaintenanceLogRepository maintenanceLogs,
            ConstructionOperationStatusRepository operationStatuses,
            HydroAlertPort hydroAlertPort,
            PortalCachePort portalCache) {
        this.constructions = constructions;
        this.maintenanceLogs = maintenanceLogs;
        this.operationStatuses = operationStatuses;
        this.hydroAlertPort = hydroAlertPort;
        this.portalCache = portalCache;
    }

    /**
     * Tính lại và áp trạng thái cho một công trình.
     *
     * <p>Cố ý <b>không</b> có {@code @Transactional}: hàm này luôn chạy bên trong giao dịch của người
     * gọi, trên một entity đang được quản lý. Gắn thêm chú thích giao dịch ở đây chỉ tạo ra một lời
     * hứa không đúng ({@code REQUIRED} sẽ tham gia giao dịch sẵn có chứ không mở giao dịch mới), mà
     * dự án đã có ba chỗ dính đúng kiểu chú thích ghi một bảo đảm không tồn tại.
     *
     * @return trạng thái sau khi tính — trả ra để nơi gọi ghi log hoặc đối chiếu, không bắt buộc dùng
     */
    public OperationalStatus recompute(Construction construction) {
        OperationalStatus truoc = construction.getOperationalStatus();
        OperationalStatus sau = tinh(construction);
        if (sau != truoc) {
            construction.apDungTrangThai(sau);
            // ⭐⭐ Xoá đệm cổng đặt Ở ĐÂY, ⛔ không ở từng nơi gọi — luật 12, và đây là chỗ dữ liệu
            //    THẬT SỰ đi qua.
            //
            // Đo được 04/09/2026: `operational_status` được cổng công bố (Danh mục công trình, bản
            // đồ hệ thống, khối "Vận hành công trình"), và nó đổi qua BỐN đường —
            // `MaintenanceLogService` (mở/đóng sự cố), `NguongAlertService` (cảnh báo ngưỡng),
            // `OperationStatusCodeService`, `ConstructionService`. Chỉ hai đường sau gọi
            // `portalCache`; hai đường đầu ⛔ chưa từng gọi. ⇒ trực ban ghi một SỰ CỐ, công trình
            // chuyển đỏ trong CSDL, và cổng vẫn hiện "Bình thường" tới 5 phút — đúng triệu chứng
            // §10.62/T27.7, tái phát ở đường ghi thứ năm và thứ sáu.
            //
            // ⭐ Rải lời gọi ra bốn nơi là mời lời gọi thứ năm đặt sai chỗ (đúng lịch sử T27.7).
            //    Đặt trong nhánh này thì nó chỉ bắn khi trạng thái THẬT SỰ đổi — một lượt
            //    `recompute` không đổi gì ⛔ không đặt việc nào, nên nó rẻ kể cả trên đường ingest.
            portalCache.constructionsChanged();
            log.info("Công trình {} đổi trạng thái {} → {}", construction.getCode(), truoc, sau);
        }
        return sau;
    }

    /**
     * Tính lại theo khoá nội bộ — dùng khi nơi gọi chỉ cầm {@code construction_id}.
     *
     * <p>⚠⚠ Cố ý dùng {@code findById}, thứ mà {@code conventions.md} §4.2 cấm cho <b>request của
     * người dùng</b>. Ở đây không phải request của người dùng: khoá không đến từ payload mà từ chính
     * bản ghi sửa chữa vừa được {@code ScopeGuard} kiểm quyền, và kết quả không trả ra API — nó chỉ
     * đi vào phép tính một giá trị dẫn xuất rồi bị bỏ đi.
     *
     * <p>Và đây là <b>đường duy nhất đúng</b> cho tình huống T18.2: bản ghi giữ đơn vị lúc phát
     * sinh, nên ngay sau một lượt bàn giao công trình, người phụ trách mới đứng ngoài phạm vi của
     * các bản ghi cũ. Tra bằng câu có lọc phạm vi sẽ trả rỗng và trạng thái công trình âm thầm rơi
     * về "Bình thường" trong khi sự cố vẫn đang mở.
     *
     * @return trạng thái sau khi tính, hoặc {@code null} khi công trình đã bị xoá thật
     */
    @Transactional
    public OperationalStatus recomputeFor(Long constructionId) {
        return constructions.findById(constructionId).map(this::recompute).orElse(null);
    }

    private OperationalStatus tinh(Construction construction) {
        // Mắt xích 0 — vòng đời, đứng trên mọi thứ khác.
        switch (construction.getLifecycleState()) {
            case DA_THANH_LY -> {
                return OperationalStatus.DA_THANH_LY;
            }
            case NGUNG_MUA_VU -> {
                return OperationalStatus.NGUNG_MUA_VU;
            }
            default -> {
                // đang hoạt động — đi tiếp xuống chuỗi CN-02.1
            }
        }

        // Công trình chưa được lưu lần nào thì chưa thể có bản ghi nào trỏ tới nó. Không có nhánh
        // này thì `demBanGhiDangMo(null, …)` chạy với khoá rỗng ở mọi lượt tạo hồ sơ mới.
        Long id = construction.getId();
        if (id == null) {
            return OperationalStatus.BINH_THUONG;
        }

        // Mắt xích 1 — sự cố đang mở. Đứng TRÊN bảo trì: một công trình vừa hỏng vừa đang được sửa
        // thì điều người trực cần thấy là cờ đỏ, không phải cờ vàng.
        if (maintenanceLogs.demBanGhiDangMo(id, true) > 0) {
            return OperationalStatus.SU_CO;
        }

        // Mắt xích 2 — sửa chữa / bảo trì đang thực hiện.
        if (maintenanceLogs.demBanGhiDangMo(id, false) > 0) {
            return OperationalStatus.BAO_TRI;
        }

        // Mắt xích 3 (cảnh báo ngưỡng — Phase 2)
        if (hydroAlertPort.hasActiveAlert(id)) {
            return OperationalStatus.CANH_BAO;
        }

        // Mắt xích 4 — mã tình hình vận hành (WS-19). Câu native, không lọc phạm vi, cùng lý do với
        // hai mắt xích trên: xem javadoc ConstructionOperationStatusRepository#banGhiMoiNhat.
        //
        // Mã có mapped_status để trống thì rơi xuống mắt xích 5, KHÔNG phải "giữ nguyên trạng thái
        // hiện tại" — một giá trị dẫn xuất không có "hiện tại" để giữ, nó được tính lại từ đầu ở mỗi
        // lượt. Chú thích ở migration V202608221029 từng ghi ngược lại; đã sửa.
        var banGhiMoiNhat = operationStatuses.banGhiMoiNhat(id);
        if (banGhiMoiNhat.isPresent()) {
            OperationalStatus mappedStatus =
                    banGhiMoiNhat.get().getOperationCode().getMappedStatus();
            if (mappedStatus != null) {
                return mappedStatus;
            }
        }

        // Mắt xích 5: Mặc định
        return OperationalStatus.BINH_THUONG;
    }
}
