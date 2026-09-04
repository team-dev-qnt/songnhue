package com.songnhue.content.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.AttachmentDeletedEvent;

/**
 * Gỡ tham chiếu ảnh khi tệp bị xoá — <b>T28.34</b>, vế {@code content}.
 *
 * <p>Bối cảnh đầy đủ ở javadoc {@link AttachmentDeletedEvent}. Ba cột ở module này khai
 * {@code ON DELETE SET NULL} và ràng buộc ấy <b>chưa từng bắn</b>:
 * {@code categories.cover_attachment_public_id} · {@code articles.cover_attachment_public_id} ·
 * {@code menu_items.logo_attachment_public_id}.
 *
 * <h2>⚠ Triệu chứng ở đây KHÁC vế {@code operations} — và nhẹ hơn hẳn</h2>
 *
 * <p>Ảnh bìa hỏng cho ra một khung ảnh vỡ, thứ người ta <b>nhìn thấy ngay</b>. Liên kết tài liệu
 * hỏng thì trông y hệt một liên kết tốt cho tới khi có người bấm. ⇒ vế kia là chỗ chịu lực; vế này
 * được sửa cùng lúc vì nó là <b>cùng một khuyết tật</b>, và sửa một nửa là để lại một khuyết tật
 * ⛔ không ai còn nhớ vì sao nó ở lại.
 *
 * <h2>⛔ {@code banners.image_attachment_public_id} ⛔ KHÔNG có mặt ở đây — cố ý</h2>
 *
 * <p>Cột ấy là {@code NOT NULL} và ⛔ <b>không</b> khai {@code ON DELETE SET NULL}, nên nó ⛔ không
 * mang khuyết tật này: một banner ⛔ không thể tồn tại mà không có ảnh. Gỡ tham chiếu ở đó là vi
 * phạm ràng buộc, và câu hỏi đúng — <i>"xoá ảnh của một banner đang chạy thì banner ra sao"</i> —
 * là một <b>quyết định nghiệp vụ</b> chưa ai đặt ra, ⛔ không phải một dòng {@code UPDATE}.
 */
@Component
public class CmsAttachmentRefCleaner {

    private static final Logger log = LoggerFactory.getLogger(CmsAttachmentRefCleaner.class);

    /**
     * ⚠ Ba câu, ⛔ không một câu gộp: chúng chạm <b>ba bảng khác nhau</b>.
     *
     * <p>Mỗi phần tử là {@code {bảng, cột}}. Danh sách khai tường minh để một bảng thứ tư ra đời
     * <i>phải</i> được thêm vào đây.
     *
     * <p>⭐ {@code public} là <b>cố ý</b>, ⛔ không phải sơ suất: {@code CmsAttachmentRefCleanerTest}
     * (module {@code app}) đối chiếu danh sách này với <b>lược đồ thật</b> — mọi cột migration
     * {@code cms} khai {@code REFERENCES attachments (public_id) ON DELETE SET NULL} đều phải có mặt
     * ở đây. Thu hẹp lại {@code package-private} là làm bộ canh ấy ⛔ không biên dịch được.
     *
     * <p>⚠⚠ Câu trên <b>từng là một lời nói dối</b>. Bản viết 04/09/2026 nêu đích danh
     * {@code CmsAttachmentRefCleanerTest} trong khi tệp ấy ⛔ <b>không tồn tại</b> — lần thứ hai
     * trong cùng một ngày (sáng hôm ấy {@code PortalCache#layoutChanged} bị bắt vì trỏ vào
     * {@code CongTacTrangChuTest}, cũng không có thật). Một chú thích nêu tên bài kiểm đọc y hệt một
     * lời bảo đảm, nên ⛔ không ai đi kiểm. ⇒ Tìm bằng {@code grep} trước khi viết tên vào đây.
     */
    public static final List<String[]> BANG_CO_THAM_CHIEU = List.of(
            new String[] {"categories", "cover_attachment_public_id"},
            new String[] {"articles", "cover_attachment_public_id"},
            new String[] {"menu_items", "logo_attachment_public_id"});

    private final JdbcTemplate jdbc;
    private final PortalCache portalCache;

    public CmsAttachmentRefCleaner(JdbcTemplate jdbc, PortalCache portalCache) {
        this.jdbc = jdbc;
        this.portalCache = portalCache;
    }

    /**
     * ⚠ {@code @EventListener} thường, ⛔ không {@code @TransactionalEventListener} — phải nguyên tử
     * với lượt xoá. Xem javadoc {@link AttachmentDeletedEvent}.
     */
    @EventListener
    public void onAttachmentDeleted(AttachmentDeletedEvent event) {
        UUID id = event.publicId();
        int tong = 0;
        for (String[] noi : BANG_CO_THAM_CHIEU) {
            tong += jdbc.update("UPDATE %s SET %s = NULL WHERE %s = ?".formatted(noi[0], noi[1], noi[1]), id);
        }
        if (tong > 0) {
            log.info("Gỡ {} tham chiếu ảnh ở CMS sau khi xoá tệp {}", tong, id);
            // ⚠ Ảnh bìa và logo menu nằm trên trang công khai ⇒ đây là một thay đổi NỘI DUNG cổng
            //   (T35.9, nhánh biên tập). `layoutChanged` phủ menu + trang chủ; nhãn `bai-viet` của
            //   danh sách bài được lượt dựng lại trang chủ kéo theo.
            portalCache.layoutChanged();
        }
    }
}
