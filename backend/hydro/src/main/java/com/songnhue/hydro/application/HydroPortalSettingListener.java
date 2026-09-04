package com.songnhue.hydro.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.core.spi.SettingChangedEvent;

/**
 * Đổi danh sách điểm đo công bố ⇒ xoá đệm cổng — <b>T35.8 + T35.9</b>.
 *
 * <h2>Vì sao phải có lớp này, trong khi khoá đã có màn hình sửa</h2>
 *
 * <p>{@code hydro.portal.station-codes} là <b>nội dung cổng</b> đội lốt một tham số kỹ thuật: sửa
 * nó là thêm hoặc bớt dòng trên bảng "Mực nước, lượng mưa". ⛔ Không có lớp này thì người vận hành
 * gõ danh sách, màn hình báo <i>"Đã lưu"</i>, và cổng ⛔ không đổi gì trong tối đa 300 giây rồi tự
 * đúng lại — <b>đúng nguyên văn triệu chứng §10.62</b>, thứ mà T25.22 · T27.7 · T27.16 đã đi trả nợ
 * ba lượt ở ba đường ghi khác nhau. Đây là đường ghi thứ tư, và nó ra đời <b>cùng đợt</b> với đường
 * đọc — đúng cái bẫy T27.7 đã mô tả.
 *
 * <h2>⛔ Lọc theo KHOÁ, ⛔ không theo nhóm</h2>
 *
 * <p>{@code SiteConfigService.onSettingChanged} lọc theo {@code groupCode} vì <b>cả</b> nhóm
 * {@code SITE} là nội dung cổng. Nhóm {@code HYDRO} thì ⛔ không: chín khoá còn lại là chu kỳ
 * polling, hạn lưu trữ, ngưỡng cảnh báo nguồn, bộ quy tắc nghi ngờ — ⛔ không khoá nào chạm một
 * pixel nào của cổng. Nghe cả nhóm là đặt một việc dựng lại cổng mỗi lần ai đó chỉnh timeout gọi
 * API, tức là một lời gọi <b>đúng mà vô nghĩa</b>, và loại lời gọi ấy dạy người đọc log bỏ qua nó.
 *
 * <h2>⚠ {@code AFTER_COMMIT}, ⛔ không phải người nghe thường</h2>
 *
 * <p>Cùng lý do đã ghi ở {@link SettingChangedEvent}: dựng lại cổng <i>trước</i> khi giao dịch
 * commit thì cổng đọc lại đúng giá trị <b>cũ</b> (giao dịch chưa nhìn thấy được) rồi giao dịch có
 * thể rollback — bộ đệm vừa được làm mới bằng dữ liệu sai và ⛔ không ai dọn nó lần nữa.
 *
 * <p>⚠ Sự kiện phát ở {@code SettingService.update} — nơi <b>duy nhất</b> ghi bảng
 * {@code settings} — nên lớp này phủ <b>cả hai</b> màn hình sửa (Cấu hình hệ thống của MOD-05 và
 * mọi màn hình chuyên đề). Đặt lời gọi trong một service cụ thể thì đường thứ hai im lặng.
 */
@Component
public class HydroPortalSettingListener {

    private static final Logger log = LoggerFactory.getLogger(HydroPortalSettingListener.class);

    private final PortalCachePort portalCache;

    public HydroPortalSettingListener(PortalCachePort portalCache) {
        this.portalCache = portalCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettingChanged(SettingChangedEvent event) {
        if (!HydroSettings.KHOA_DIEM_DO_LEN_CONG.equals(event.key())) {
            return;
        }
        log.info(
                "Danh sách điểm đo công bố (`{}`) vừa đổi — đặt việc dựng lại cổng",
                HydroSettings.KHOA_DIEM_DO_LEN_CONG);
        portalCache.hydroStationsChanged();
    }
}
