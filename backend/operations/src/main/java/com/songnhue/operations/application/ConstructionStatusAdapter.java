package com.songnhue.operations.application;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.ConstructionStatusPort;

/**
 * Cài đặt {@link ConstructionStatusPort} — WS-33 / T33.9.
 *
 * <p>⚠ Đặt ở {@code application}, ⛔ <b>không</b> ở {@code spi}: {@code LayeringTest} khẳng định
 * <i>"@Transactional chỉ đặt ở tầng application"</i>, và T28.19 đã va đúng luật ấy khi
 * {@code ConstructionLookupAdapter} nằm nhầm chỗ. Khuôn đúng có sẵn ở
 * {@code content.application.PortalCache}.
 *
 * <p>⛔ Lớp này cố ý <b>mỏng</b>: mọi lô-gic ở {@code ConstructionStatusService}. Một adapter mang
 * lô-gic riêng là một đường tính trạng thái thứ hai, và hai đường thì có ngày cho ra hai kết quả —
 * trên một cột được <b>ghi xuống CSDL cho tất cả mọi người</b>.
 */
@Component
public class ConstructionStatusAdapter implements ConstructionStatusPort {

    private static final Logger log = LoggerFactory.getLogger(ConstructionStatusAdapter.class);

    private final ConstructionStatusService trangThai;

    public ConstructionStatusAdapter(ConstructionStatusService trangThai) {
        this.trangThai = trangThai;
    }

    /**
     * ⚠ {@code REQUIRED} (mặc định) — cố ý tham gia giao dịch của nơi gọi.
     *
     * <p>Nơi gọi là máy cảnh báo, và nó chạy <b>bên trong</b> giao dịch ghi số đo. Mở một giao dịch
     * riêng ở đây là để mắt xích 3 đọc {@code alert_events} <b>trước</b> khi dòng cảnh báo vừa ghi
     * được commit — tức tính lại trên dữ liệu cũ, rồi ghi một trạng thái sai xuống CSDL và ⛔ không
     * có lượt nào sửa nó cho tới lần đối soát sau.
     */
    @Override
    @Transactional
    public void recomputeFor(Collection<Long> constructionIds) {
        if (constructionIds == null || constructionIds.isEmpty()) {
            // ⚠ Bình thường: điểm đo MN_SONG (4/19 trạm) ⛔ không thuộc công trình nào theo thiết kế.
            return;
        }
        for (Long id : constructionIds) {
            if (id == null) {
                continue;
            }
            if (trangThai.recomputeFor(id) == null) {
                // ⛔ Không ném: một liên kết trỏ vào công trình đã xoá ⛔ không được kéo theo rollback
                //   lượt ghi số đo. Nhưng cũng ⛔ không im lặng — đó là một liên kết cần dọn.
                log.warn("Liên kết điểm đo trỏ vào công trình #{} không còn tồn tại — không tính lại được", id);
            }
        }
    }
}
