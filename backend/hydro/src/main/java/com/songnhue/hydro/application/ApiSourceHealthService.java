package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.infra.ApiSourceRepository;

/**
 * Ghi lại sức khoẻ của một nguồn sau mỗi lượt gọi, và đánh thức Quản trị khi cần — T30.6.
 *
 * <h2>⚠⚠ Đây là VẾ GHI còn thiếu của bốn cột đã có người đọc từ 31/08</h2>
 *
 * <p>{@code last_success_at} · {@code last_failure_at} · {@code last_failure_reason} ·
 * {@code consecutive_failures} đều nằm trong {@code ApiSourceView} và đều hiện trên màn hình
 * <i>Nguồn dữ liệu</i>. Đo 02/09/2026: <b>không một dòng mã nào ghi chúng</b> — bốn ô rỗng vĩnh viễn
 * mà người đọc hiểu thành "chưa có sự cố nào". Luật 27, và là chỗ thứ bảy cùng hình dạng trong dự án.
 *
 * <h2>⭐ Cảnh báo phát ở CHUYỂN TRẠNG THÁI, không phát theo trạng thái</h2>
 *
 * <p>Poller gọi 2 phút/lần. "Hỏng ≥ ngưỡng thì cảnh báo" nghĩa là <b>720 thông báo mỗi ngày</b> cho
 * một nguồn hỏng — và một chuông kêu liên tục vì một lý do ai cũng biết là một chuông sẽ bị tắt, rồi
 * vẫn tắt vào ngày nguồn hỏng thật (§10.42). Nên đúng hai thời điểm phát:
 *
 * <ul>
 *   <li>lượt hỏng thứ {@code N} — đúng lúc bộ đếm <i>bằng</i> ngưỡng, ⛔ không phải mỗi lượt vượt;
 *   <li>lượt thành công đầu tiên sau một chuỗi hỏng — <b>tin phục hồi</b>. Thiếu vế này thì người
 *       nhận cảnh báo không bao giờ biết chuyện đã xong, và lần sau họ đọc cảnh báo chậm hơn.
 * </ul>
 *
 * <h2>📌 MOD-03 là module nghiệp vụ ĐẦU TIÊN thật sự gọi {@code NotificationPort}</h2>
 *
 * <p>{@code NotificationPortTest} ghi thẳng điều đó: cổng dựng cho module nghiệp vụ <i>"chưa ai đi
 * qua, vì bốn module nghiệp vụ [chưa gọi]"</i>. Nên đây cũng là lượt kiểm chứng đầu tiên rằng đường
 * dây thông báo chạy thật từ một module ngoài {@code core} (luật 7).
 *
 * <p>Gửi theo <b>quyền</b> chứ không theo đơn vị: một nguồn dữ liệu là tài sản toàn Công ty, không
 * thuộc Xí nghiệp nào, nên người cần biết là người quản được nó — {@code hyd:api-source:manage}.
 */
@Service
public class ApiSourceHealthService {

    private static final Logger log = LoggerFactory.getLogger(ApiSourceHealthService.class);

    static final String SU_KIEN_HONG = "HYDRO_SOURCE_DOWN";
    static final String SU_KIEN_PHUC_HOI = "HYDRO_SOURCE_RECOVERED";
    static final String QUYEN_NHAN = "hyd:api-source:manage";

    private final ApiSourceRepository sources;
    private final HydroSettings settings;
    private final NotificationPort notifications;

    public ApiSourceHealthService(ApiSourceRepository sources, HydroSettings settings, NotificationPort notifications) {
        this.sources = sources;
        this.settings = settings;
        this.notifications = notifications;
    }

    /**
     * Một lượt gọi đã tới nơi và dùng được.
     *
     * @param nguon ⚠ thực thể <b>đã tách khỏi phiên</b> khi lượt gọi HTTP diễn ra ngoài giao dịch —
     *     nên nạp lại theo id thay vì {@code save()} thẳng bản đang cầm. Giữ một giao dịch mở suốt
     *     một lượt gọi mạng 30 giây là cách một nguồn chậm khoá cạn hồ kết nối CSDL của cả ứng dụng.
     */
    @Transactional
    public void ghiNhanThanhCong(ApiSource nguon, Instant mocGoi) {
        ApiSource moi = sources.findById(nguon.getId()).orElse(nguon);
        boolean vuaTroLai = moi.ghiNhanThanhCong(mocGoi);
        sources.save(moi);
        if (vuaTroLai) {
            log.info("Nguồn {} đã trở lại sau chuỗi lượt gọi hỏng", moi.getCode());
            notifications.notify(NotifyRequest.targeted(
                    SU_KIEN_PHUC_HOI,
                    "Nguồn dữ liệu %s đã trở lại".formatted(moi.getCode()),
                    "Lượt gọi lúc %s thành công. Nguồn đang nhận số liệu bình thường.".formatted(mocGoi),
                    NotifySeverity.INFO,
                    QUYEN_NHAN,
                    List.of()));
        }
    }

    /**
     * Một lượt gọi hỏng.
     *
     * @param lyDo ⛔ phải là câu đã qua bộ che mã số của adapter — nó đi vào cột
     *     {@code last_failure_reason}, ra màn hình, và vào thân thông báo email
     */
    @Transactional
    public void ghiNhanThatBai(ApiSource nguon, Instant mocGoi, SyncFailureKind kieu, String lyDo) {
        ApiSource moi = sources.findById(nguon.getId()).orElse(nguon);
        int soLanHong = moi.ghiNhanThatBai(mocGoi, lyDo);
        sources.save(moi);
        int nguong = settings.soLanHongTruocKhiCanhBao();
        log.warn("Nguồn {} hỏng lượt thứ {} liên tiếp ({}): {}", moi.getCode(), soLanHong, kieu, lyDo);
        if (soLanHong != nguong) {
            return;
        }
        notifications.notify(NotifyRequest.targeted(
                SU_KIEN_HONG,
                "Nguồn dữ liệu %s hỏng %d lượt liên tiếp".formatted(moi.getCode(), soLanHong),
                thanThongBao(kieu, lyDo),
                NotifySeverity.WARNING,
                QUYEN_NHAN,
                List.of()));
    }

    /**
     * ⚠ Thân thông báo nói ra <b>việc phải làm</b>, không chỉ nói cái gì hỏng.
     *
     * <p>{@code NOT_WORKING} đáng một câu riêng: nguồn trả đúng chuỗi ấy khi mã số sai <b>và</b> khi
     * mã số thiếu dấu {@code ;} ở cuối — hai nguyên nhân không phân biệt được từ phía ta. Người nhận
     * cảnh báo lúc 2 giờ sáng cần đọc thấy câu hỏi đúng ngay dòng đầu.
     */
    private static String thanThongBao(SyncFailureKind kieu, String lyDo) {
        String viec =
                switch (kieu) {
                    case NOT_WORKING ->
                        "Nguồn từ chối mã số. ⚠ Kiểm tra mã số còn dấu ';' ở cuối không "
                                + "— thiếu dấu ấy nguồn trả đúng thông báo này, trông y hệt mã số sai.";
                    case TIMEOUT ->
                        "Nguồn không trả lời kịp. Kiểm tra đường mạng tới nguồn và mức tải "
                                + "phía nguồn trước khi nới timeout.";
                    case HTTP_ERROR -> "Không gọi được nguồn. Kiểm tra địa chỉ nguồn và đường mạng ra ngoài.";
                    case EMPTY_BODY ->
                        "Nguồn trả HTTP 200 nhưng không có nội dung — nhiều khả năng phía " + "nguồn đang bảo trì.";
                    case THIEU_MA_SO -> "Nguồn chưa cấu hình mã số. Đặt mã số ở màn hình Nguồn dữ liệu.";
                };
        return "%s%n%nChi tiết kỹ thuật: %s%n%n⛔ Nguồn không có API lịch sử: mỗi phút không lấy được "
                        .formatted(viec, lyDo)
                + "là số đo mất vĩnh viễn.";
    }
}
