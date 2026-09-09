package com.songnhue.content.application;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.MailPort;
import com.songnhue.core.spi.SettingPort;

import tools.jackson.databind.ObjectMapper;

/**
 * Thư xác nhận gửi cho <b>người dân</b> vừa điền biểu mẫu liên hệ — CN-01.4 / T36.3.
 *
 * <h2>⛔⛔ Payload chỉ mang MÃ CÔNG KHAI, ⛔ không mang địa chỉ email</h2>
 *
 * <p>{@code JobRequest} nói rõ: payload nằm <b>nguyên văn</b> trong bảng {@code jobs} và lọt vào
 * bản sao lưu. Địa chỉ email của người dân là dữ liệu cá nhân theo NĐ 13/2023, và nó <i>đã</i> nằm
 * ở {@code contacts} — chép thêm một bản vào hàng đợi là nhân đôi phạm vi mà ⛔ không đổi lấy gì.
 *
 * <p>⭐ Và cách này còn đúng hơn về nghiệp vụ: liên hệ bị xoá mềm giữa lúc chờ gửi thì thư <b>⛔
 * không đi</b>. Payload mang sẵn địa chỉ thì thư vẫn đi, cho một bản ghi ⛔ không còn tồn tại.
 *
 * <h2>⛔ Tiêu đề thư là HẰNG SỐ — ⛔ không ghép chuỗi người lạ nhập vào</h2>
 *
 * <p>{@code SimpleMailMessage} đặt tiêu đề thẳng vào header. Một chuỗi có ký tự xuống dòng ở đó là
 * <b>chèn header</b> — thêm được {@code Bcc:} vào thư của chính ta. {@code ContactService} đã cắt
 * ký tự điều khiển lúc nhận, nhưng đó là <b>tầng khác</b>: chỗ này ⛔ không được dựa vào một lớp bảo
 * vệ nằm ở nơi khác còn đúng hay không (luật 12). Chủ đề người dân nhập đi vào <b>thân thư</b>, nơi
 * xuống dòng chỉ là xuống dòng.
 */
@Component
public class ContactAckMailHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ContactAckMailHandler.class);

    /** ⛔ HẰNG SỐ — xem javadoc lớp. */
    private static final String TIEU_DE = "Đã nhận được ý kiến của Quý vị";

    private static final DateTimeFormatter GIO_VN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final ContactRepository contacts;
    private final MailPort mail;
    private final SettingPort settings;
    private final ObjectMapper objectMapper;

    public ContactAckMailHandler(
            ContactRepository contacts, MailPort mail, SettingPort settings, ObjectMapper objectMapper) {
        this.contacts = contacts;
        this.mail = mail;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return CmsJobTypes.CONTACT_ACK_MAIL;
    }

    @Override
    @Transactional(readOnly = true)
    public void handle(JobContext context) throws Exception {
        UUID maLienHe = UUID.fromString(
                objectMapper.readTree(context.payload()).get("contactPublicId").asText());

        // ⚠ Đọc lại công tắc ở ĐÂY, ⛔ không chỉ ở lúc đặt việc: một việc có thể nằm chờ qua đêm, và
        //   người tắt thư xác nhận lúc 8h sáng mong nó có hiệu lực ngay.
        if (!settings.getBoolean(ContactService.KHOA_THU_XAC_NHAN, true)) {
            log.info("Thư xác nhận đang TẮT theo cấu hình — bỏ qua {}", maLienHe);
            return;
        }

        Contact c = contacts.findByPublicIdAndDeletedAtIsNull(maLienHe).orElse(null);
        if (c == null) {
            // ⛔ ⛔ Không ném: bản ghi đã bị xoá là một kết cục HỢP LỆ, ⛔ không phải hỏng. Ném ở đây
            //    là bắt hàng đợi thử lại ba lần một việc ⛔ không bao giờ thành công được.
            log.info("Liên hệ {} không còn — ⛔ không gửi thư xác nhận", maLienHe);
            return;
        }
        if (c.getEmail() == null || c.getEmail().isBlank()) {
            // Biểu mẫu cho phép chỉ để lại số điện thoại (`ck_contacts_lien_lac`).
            log.info("Liên hệ {} ⛔ không có email — ⛔ không gửi thư xác nhận", maLienHe);
            return;
        }

        String than =
                """
                Kính gửi %s,

                Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ đã nhận được ý kiến của Quý vị \
                lúc %s.

                Chủ đề: %s

                Ý kiến của Quý vị sẽ được chuyển tới bộ phận chuyên môn xem xét. Đây là thư tự động, \
                Quý vị vui lòng không trả lời thư này."""
                        .formatted(
                                c.getFullName(),
                                ZonedDateTime.ofInstant(c.getCreatedAt(), DateTimeUtils.ZONE_VN)
                                        .format(GIO_VN),
                                c.getSubject());

        // ⛔ ⛔ Không đính kèm đường dẫn: người dân ⛔ không có tài khoản, mọi liên kết của hệ thống
        //    đều dẫn tới một màn hình đăng nhập. Một liên kết dẫn tới chỗ vào không được tệ hơn là
        //    ⛔ không có liên kết.
        boolean daGui = mail.send(c.getEmail(), TIEU_DE, than, null);
        if (!daGui) {
            log.warn("Chưa cấu hình SMTP — thư xác nhận cho liên hệ {} ⛔ không gửi được", maLienHe);
        }
    }
}
