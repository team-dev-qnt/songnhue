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
 * <h2>⛔⛔ T50.4 — bản trước kêu ĐÚNG MỘT LẦN, và nó im suốt một sự cố 9 NGÀY</h2>
 *
 * <p>Bản đầu viết {@code if (soLanHong != nguong) return;} — phát đúng lúc bộ đếm <b>bằng</b> ngưỡng.
 * Lập luận chống spam ấy <b>đúng</b>: poller gọi 2 phút/lần nên <i>"hỏng ≥ ngưỡng thì cảnh báo"</i>
 * là <b>720 thông báo mỗi ngày</b>, và một chuông kêu liên tục là một chuông sẽ bị tắt (§10.42).
 *
 * <p>⛔ Nhưng nó đổi lấy một khuyết tật nặng hơn, đo được trên staging 10/09/2026:
 *
 * <pre>
 *   HYDRO_SOURCE_DOWN : phát ĐÚNG 1 lần — 04/09 11:03
 *   consecutive_failures = 3323 · last_success_at = NULL · hydro_raw_logs = 0
 * </pre>
 *
 * <p>Bộ đếm chỉ về 0 khi có một lượt <b>thành công</b>. Nguồn ⛔ không bao giờ thành công ⇒ bộ đếm
 * ⛔ không bao giờ bằng ngưỡng lần thứ hai ⇒ <b>chuông ⛔ không bao giờ kêu lại</b>. Một sự cố mỗi
 * lúc một nặng thêm sinh ra <b>0 tín hiệu mới</b> — đúng họ *"chuông một bit"* của §10.76: <i>đỏ
 * như cũ</i> và <i>đỏ và tệ hơn</i> in ra cùng một câu, ở đây là cùng một sự im lặng.
 *
 * <h2>⭐ Thang leo: giữ chống spam, mà sự cố vẫn phải kêu tiếp</h2>
 *
 * <p>Phát ở các mốc {@code N · N×4 · N×16 · …} thay vì đúng một mốc {@code N}. Khoảng cách **tự
 * giãn ra**, nên chống spam vẫn còn nguyên; mà một sự cố kéo dài thì vẫn liên tục sinh tín hiệu mới.
 *
 * <p>Đối chiếu với chính sự cố thật (ngưỡng 3, poller 2 phút/lần):
 *
 * <pre>
 *   mốc:      3      12      48     192     768    3072
 *   sau:    6ph    24ph   1.6giờ  6.4giờ  25.6giờ  4.3ngày
 *   ⇒ 9 ngày hỏng cho **6** cảnh báo, thay vì 1. Ngày thứ chín vẫn còn tín hiệu.
 * </pre>
 *
 * <p>⚠ So <b>chỉ số mốc</b> của lượt này với lượt trước, ⛔ không so bằng {@code ==} với một con số:
 * người vận hành hạ ngưỡng giữa chừng sẽ làm mọi mốc dịch chỗ, và một phép so bằng sẽ nhảy qua mốc
 * trong im lặng — đúng hình dạng đã hỏng một lần rồi.
 *
 * <p>Vế còn lại giữ nguyên: lượt thành công đầu tiên sau một chuỗi hỏng phát <b>tin phục hồi</b>.
 * Thiếu nó thì người nhận cảnh báo ⛔ không bao giờ biết chuyện đã xong, và lần sau họ đọc cảnh báo
 * chậm hơn.
 *
 * <h2>⚠⚠ Kênh EMAIL của staging chưa từng gửi nổi một thư — nợ T50.9</h2>
 *
 * <p>Đo 10/09/2026 trên toàn bảng {@code notification_recipients} của staging: {@code EMAIL} có
 * <b>154 FAILED · 88 SKIPPED · 0 SENT</b>, vì {@code SMTP_HOST} <b>rỗng</b>. {@code IN_APP} có 242
 * {@code SENT} mà <b>1</b> lượt đọc. ⇒ Lớp này gửi đúng cả hai kênh, nhưng trên staging chỉ còn một
 * kênh sống, và kênh ấy ⛔ không ai nhìn. Sửa được bằng cấu hình, ⛔ không phải bằng mã — nhưng
 * <b>⛔ đừng đọc cái xanh của lớp này thành "người ta đã được báo"</b> (luật 28).
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

    /**
     * Mỗi mốc cảnh báo cách mốc trước <b>bốn lần</b>.
     *
     * <p>⚠ Con số này chọn bằng **đối chiếu với sự cố thật**, ⛔ không bằng cảm tính. Ngưỡng 3 +
     * poller 2 phút/lần ⇒ mốc rơi vào 6 phút · 24 phút · 1.6 giờ · 6.4 giờ · 25.6 giờ · 4.3 ngày.
     * Sự cố 9 ngày của staging sẽ cho **6** cảnh báo — đủ để ngày thứ chín vẫn còn tín hiệu, mà
     * ⛔ không lượt nào cách lượt trước dưới sáu phút. Hệ số 2 cho 11 cảnh báo (ồn); hệ số 10 cho 4,
     * và mốc thứ tư rơi vào **hơn bốn ngày** — quá thưa cho một nguồn mất dữ liệu vĩnh viễn.
     */
    static final int HE_SO_LEO_THANG = 4;

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

        int mocMoi = soMocDaVuot(soLanHong, nguong);
        if (mocMoi <= soMocDaVuot(soLanHong - 1, nguong)) {
            return;
        }
        notifications.notify(NotifyRequest.targeted(
                SU_KIEN_HONG,
                "Nguồn dữ liệu %s hỏng %d lượt liên tiếp".formatted(moi.getCode(), soLanHong),
                thanThongBao(kieu, lyDo, soLanHong, moi.getLastSuccessAt()),
                mocMoi >= 2 ? NotifySeverity.CRITICAL : NotifySeverity.WARNING,
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
    private static String thanThongBao(SyncFailureKind kieu, String lyDo, int soLanHong, Instant lanCuoiLayDuoc) {
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
        return """
                %s

                Đã hỏng %d lượt liên tiếp. %s

                Chi tiết kỹ thuật: %s

                ⛔ Nguồn không có API lịch sử: mỗi phút không lấy được là số đo mất vĩnh viễn."""
                .formatted(viec, soLanHong, moNeo(lanCuoiLayDuoc), lyDo);
    }

    /**
     * Câu neo sự cố vào một <b>mốc đo được</b>, ⛔ không phải một tính từ.
     *
     * <p>⭐ Vế {@code null} là vế quan trọng nhất và nó <b>⛔ không</b> phải một ca hiếm: đúng sự cố
     * 01/09→10/09/2026 rơi vào đây. <i>"Chưa từng lấy được số liệu lần nào"</i> nói ngay rằng đây là
     * lỗi <b>cấu hình</b> chứ ⛔ không phải nguồn chết — hai chuyện cần hai việc phải làm khác hẳn
     * nhau, và một câu chung cho cả hai thì ⛔ không nói gì (luật 9).
     */
    private static String moNeo(Instant lanCuoiLayDuoc) {
        return lanCuoiLayDuoc == null
                ? "⚠ Nguồn này CHƯA TỪNG lấy được số liệu lần nào — nhiều khả năng là lỗi cấu hình, "
                        + "⛔ không phải nguồn chết."
                : "Lần cuối lấy được số liệu: %s.".formatted(lanCuoiLayDuoc);
    }

    /**
     * Số mốc leo thang mà {@code soLanHong} đã vượt qua — {@code N · N×4 · N×16 · …}
     *
     * <p>Trả {@code 0} khi chưa tới ngưỡng, {@code 1} ở mốc đầu, {@code 2} ở mốc thứ hai… Người gọi
     * phát chuông khi <b>chỉ số mốc TĂNG</b> so với lượt trước, chứ ⛔ không so bằng {@code ==} với
     * một con số: hạ ngưỡng giữa chừng làm mọi mốc dịch chỗ, và một phép so bằng sẽ nhảy qua mốc
     * trong im lặng — đúng hình dạng đã hỏng một lần rồi (T50.4).
     */
    static int soMocDaVuot(int soLanHong, int nguong) {
        if (nguong <= 0 || soLanHong < nguong) {
            return 0;
        }
        int dem = 0;
        long moc = nguong;
        while (moc <= soLanHong) {
            dem++;
            moc *= HE_SO_LEO_THANG;
        }
        return dem;
    }
}
