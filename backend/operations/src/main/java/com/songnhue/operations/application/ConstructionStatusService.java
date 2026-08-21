package com.songnhue.operations.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationalStatus;

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
 * <h2>Chuỗi suy ra, và phần chưa tới lượt</h2>
 *
 * CN-02.1 xếp 5 mức ưu tiên. Ở WS-17 mới có mắt xích cuối cùng, vì bốn mắt xích trên cần dữ liệu
 * chưa tồn tại:
 *
 * <ol>
 *   <li>Sự cố đang mở → {@code SU_CO} — cần {@code maintenance_logs}, <b>WS-18</b>
 *   <li>Bảo trì đang thực hiện → {@code BAO_TRI} — <b>WS-18</b>
 *   <li>Cảnh báo ngưỡng đang xảy ra → {@code CANH_BAO} — cần MOD-03, <b>Phase 2</b>
 *   <li>Ánh xạ từ mã tình hình vận hành → <b>WS-19</b>
 *   <li>Mặc định {@code BINH_THUONG} ✔ <i>đang chạy</i>
 * </ol>
 *
 * <p>⚠ Đây <b>không</b> phải "công tắc chưa ai đọc" — lỗi mà dự án đã trả giá ở WS-12 và WS-15. Hàm
 * này được gọi thật ở mọi lượt tạo/sửa và giá trị nó ghi ra được đọc thật ở danh sách, bản đồ và
 * thống kê. Thứ còn thiếu là <i>đầu vào</i>, không phải người đọc. WS-18 và WS-19 thêm luật vào
 * <b>đúng hàm này</b>, không mở đường ghi mới — nếu không thì sẽ có hai nơi cùng ghi một cột và
 * chúng sẽ ghi đè lẫn nhau theo thứ tự ngẫu nhiên.
 *
 * <h2>Vòng đời đứng trên tất cả</h2>
 *
 * Một công trình đã thanh lý không có "sự cố đang mở" nào đáng hiển thị, và hiện "Bình thường" cho
 * nó là nói sai. Nên {@link LifecycleState} được xét trước toàn bộ chuỗi trên.
 */
@Service
public class ConstructionStatusService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionStatusService.class);

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
            log.info("Công trình {} đổi trạng thái {} → {}", construction.getCode(), truoc, sau);
        }
        return sau;
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

        // Mắt xích 1–4 thuộc WS-18 / WS-19 / Phase 2. Thêm vào ĐÂY, không thêm
        // đường ghi khác vào cột operational_status.
        return OperationalStatus.BINH_THUONG;
    }
}
