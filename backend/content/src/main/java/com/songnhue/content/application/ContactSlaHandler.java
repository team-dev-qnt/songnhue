package com.songnhue.content.application;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.SettingPort;

/**
 * Nhắc những liên hệ <b>quá hạn xử lý</b> — CN-01.4 / T36.4, SRS UC1.3.
 *
 * <h2>⭐⭐ MỘT thông báo cho cả đợt, ⛔ không phải một thông báo cho mỗi liên hệ</h2>
 *
 * <p>Đây là quyết định thiết kế đắt nhất của lớp này. Hộp thư tồn đọng 40 liên hệ quá hạn thì cách
 * "mỗi bản ghi một thư" gửi 40 email <b>mỗi ngày</b> cho cùng một người, và sang ngày thứ ba họ đặt
 * quy tắc lọc thư. Lúc đó cảnh báo sự cố thật cũng chết theo — cùng lập luận mà
 * {@code RecipientResolver} đã viết ra khi từ chối cộng Ban điều hành vào mọi bước duyệt.
 *
 * <p>⇒ Một thông báo, mang <b>con số</b> và <b>vài dòng đầu</b>. Con số là thứ đổi mỗi ngày và là
 * thứ người đọc thật sự cần.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28): nhắc ai</h2>
 *
 * <p>✅ <b>Từ 08/09/2026 (T28.51)</b>: nhắc mọi tài khoản có {@code cms:contact:manage}
 * <b>cộng người đứng đầu những đơn vị đang giữ việc</b> ({@code contacts.assigned_org_unit_id}).
 *
 * <p>⚠ Đường dây ấy từng đứt ở <b>HAI</b> chỗ độc lập, và vá một chỗ ⛔ không đủ:
 * {@code NotifyRequest.targeted} ghi cứng {@code List.of()} cho {@code relatedOrgUnitIds}, <i>và</i>
 * {@code RecipientResolver} bỏ qua danh sách ấy khi đã khai {@code targetPermission}. Nay có
 * {@code targetedWithUnits}, và bộ giải người nhận tôn trọng đơn vị <b>được nêu đích danh</b> —
 * xem javadoc ở đó để biết vì sao đây là phép <b>thu hẹp</b>, ⛔ không phải nới lỏng luật G11.
 *
 * <p>⛔ <b>Vẫn giữ nguyên nhóm theo quyền</b>, ⛔ không thay thế: liên hệ chưa chuyển cho ai thì
 * người phải xử lý chính là nhóm ấy. Thay thế là biến một lỗ (không ai đúng nhận được) thành một
 * lỗ khác (việc chưa giao thì ⛔ không ai nhận).
 *
 * <p>⚠ Ghi chú cũ ở đây khai rằng {@code OrgUnitPort} ⛔ không có phương thức tra người đứng đầu —
 * <b>đúng</b>, và nay vẫn đúng: bộ giải người nhận nằm ở {@code core} nên nó dùng thẳng
 * {@code OrgUnitRepository.findActiveHeadAndDeputyUserIds}, ⛔ không phải nới SPI ra cho một nơi gọi.
 *
 * <h2>⛔ Job này ⛔ KHÔNG đổi trạng thái bản ghi nào</h2>
 *
 * <p>Nó chỉ đọc và gửi. Một job tự đẩy liên hệ sang trạng thái "quá hạn" là dựng thêm một trạng
 * thái ⛔ không có trong {@code ck_contacts_status}, và đổi trạng thái ngoài Workflow engine (quy
 * tắc 4). "Quá hạn" là một <b>phép tính trên thời gian</b>, ⛔ không phải một trạng thái.
 */
@Component
public class ContactSlaHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ContactSlaHandler.class);

    /**
     * Trần số bản ghi đọc mỗi lượt.
     *
     * <p>⚠ Đây là trần của <b>danh sách trích dẫn</b>, ⛔ không phải của phép đếm — nhưng lượt đầu
     * tiên sau khi bật tính năng trên một hộp thư tồn đọng có thể ra hàng nghìn dòng, và một job
     * đọc hết chúng vào bộ nhớ để đếm là một job treo. Trần này ⛔ không giấu gì: thông điệp nói rõ
     * "ít nhất N".
     */
    private static final int TRAN_DOC = 200;

    /** Chỉ trích dẫn vài dòng đầu — thư dài ⛔ không ai đọc, và con số mới là thứ cần. */
    private static final int SO_DONG_TRICH = 5;

    private static final DateTimeFormatter NGAY_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContactRepository contacts;
    private final SettingPort settings;
    private final NotificationPort notifications;

    public ContactSlaHandler(ContactRepository contacts, SettingPort settings, NotificationPort notifications) {
        this.contacts = contacts;
        this.settings = settings;
        this.notifications = notifications;
    }

    @Override
    public String jobType() {
        return CmsJobTypes.CONTACT_SLA_REMIND;
    }

    /** Nhắc hụt một ngày ⛔ không đáng để thử lại nhiều lần — ngày mai vẫn còn một lượt nữa. */
    @Override
    public short maxAttempts() {
        return 2;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        int soGio = settings.getInt(ContactService.KHOA_HAN_SLA_GIO, 48);
        if (soGio <= 0) {
            // ⛔ Đây là công tắc TẮT, ⛔ không phải giá trị sai — xem migration V202609061066.
            log.info("Nhắc SLA đang tắt (cms.contact.sla-hours = {})", soGio);
            return;
        }

        Instant hanChot = Instant.now().minus(Duration.ofHours(soGio));
        List<Contact> quaHan = contacts.quaHan(hanChot, PageRequest.of(0, TRAN_DOC));
        if (quaHan.isEmpty()) {
            // ⛔ ⛔ Không gửi "hôm nay không có gì quá hạn". Một chuông kêu mỗi ngày kể cả khi ⛔
            //    không có gì là một chuông mang ĐÚNG MỘT BIT — và nó bị lọc mất trước khi có việc
            //    thật (§10.76).
            log.debug("⛔ Không có liên hệ nào quá hạn {} giờ", soGio);
            return;
        }

        boolean chamTran = quaHan.size() == TRAN_DOC;
        String tieuDe =
                "%s liên hệ quá hạn xử lý %d giờ".formatted(chamTran ? "Ít nhất " + TRAN_DOC : quaHan.size(), soGio);

        StringBuilder than = new StringBuilder();
        than.append("Các liên hệ sau chưa được phản hồi hoặc đóng sau %d giờ:\n\n".formatted(soGio));
        quaHan.stream()
                .limit(SO_DONG_TRICH)
                .forEach(c -> than.append("• %s — %s (nhận ngày %s)\n"
                        .formatted(
                                c.getSubject(),
                                c.getFullName(),
                                ZonedDateTime.ofInstant(c.getCreatedAt(), DateTimeUtils.ZONE_VN)
                                        .format(NGAY_VN))));
        if (quaHan.size() > SO_DONG_TRICH) {
            than.append("… và %d liên hệ khác.\n".formatted(quaHan.size() - SO_DONG_TRICH));
        }
        than.append("\nMở Quản trị nội dung › Hộp thư liên hệ để xử lý.");

        // ⭐⭐ T28.51 — nhắc TỚI CẢ đơn vị đã được chuyển xử lý.
        //
        // Bản trước gửi cho **mọi** tài khoản có `cms:contact:manage` và chỉ thế. Người phụ trách
        // Xí nghiệp đang giữ việc thì ⛔ không nhận được gì, còn người ⛔ không liên quan thì nhận
        // hết — đúng cách một hộp thư học được thói quen bỏ qua cảnh báo.
        //
        // ⚠ Lọc `null`: liên hệ CHƯA chuyển cho ai vẫn phải được nhắc, và người nhận của nó chính
        //   là nhóm giữ quyền xử lý. Bỏ vế lọc thì `findActiveHeadAndDeputyUserIds` nhận một danh
        //   sách có `null` và câu truy vấn hỏng — im lặng, vì lượt nhắc chạy trong job nền.
        List<Long> donViDangGiu = quaHan.stream()
                .map(Contact::getAssignedOrgUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        notifications.notify(NotifyRequest.targetedWithUnits(
                "CONTACT_SLA_BREACH",
                tieuDe,
                than.toString(),
                NotifySeverity.WARNING,
                ContactService.QUYEN_XU_LY,
                donViDangGiu,
                List.of()));

        log.info("Đã nhắc {} liên hệ quá hạn {} giờ", quaHan.size(), soGio);
    }
}
