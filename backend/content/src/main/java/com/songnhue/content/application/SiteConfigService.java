package com.songnhue.content.application;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;
import com.songnhue.core.spi.SettingAdminPort;
import com.songnhue.core.spi.SettingChangedEvent;
import com.songnhue.core.spi.SettingItem;

/**
 * Cấu hình giao diện cổng — CN-01.5, T15.2–T15.6.
 *
 * <h2>Không có bảng riêng</h2>
 *
 * Toàn bộ nằm ở {@code settings} nhóm {@link #GROUP}. Lớp này là <b>cửa vào có phạm vi</b>: nó khai
 * đúng một nhóm với {@link SettingAdminPort}, nên dù ai đó lỡ tay gỡ mất annotation phân quyền trên
 * controller, đường này vẫn không chạm được tới nhóm bảo mật hay nhóm hạn mức.
 *
 * <h2>Bộ nhớ đệm, và vì sao nó phải nghe sự kiện thay vì tự dọn (T15.6)</h2>
 *
 * Cổng công khai đọc cụm cấu hình này ở <i>mọi</i> lượt dựng trang, còn Công ty sửa nó vài lần một
 * năm — tỉ lệ đọc/ghi đủ lệch để bộ nhớ đệm là việc đáng làm.
 *
 * <p>Nhưng cùng một dòng {@code settings} lại sửa được từ <b>hai</b> màn hình: cấu hình giao diện
 * (ở đây) và cấu hình hệ thống (MOD-05). Tự dọn bộ nhớ đệm trong {@link #update} thì đường thứ hai
 * không dọn gì cả — Quản trị viên hệ thống đổi tên cổng, giao diện báo thành công, và cổng vẫn hiện
 * tên cũ cho tới khi hết hạn. Nghe {@link SettingChangedEvent} phủ được cả hai đường, vì sự kiện
 * phát từ nơi duy nhất ghi bảng.
 *
 * <p>Vẫn giữ thêm TTL: bộ nhớ đệm nằm trong tiến trình, nên khi lên ≥2 node thì sự kiện chỉ tới
 * được node xử lý lượt sửa. TTL là lưới an toàn cho mốc đó ({@code architecture-review.md} §6.4).
 */
@Service
public class SiteConfigService {

    private static final Logger log = LoggerFactory.getLogger(SiteConfigService.class);

    /** Nhóm tham số duy nhất lớp này chạm tới. */
    public static final String GROUP = "SITE";

    public static final String KEY_LOGO = "site.logo.attachment-id";
    public static final String KEY_FAVICON = "site.favicon.attachment-id";

    /** Khớp {@code attachments.owner_type}. Cấu hình chỉ có một bản nên chủ sở hữu là hằng số. */
    private static final String OWNER_TYPE = "SITE_CONFIG";

    private static final Long OWNER_ID = 1L;

    /**
     * Định dạng nhận cho logo và favicon.
     *
     * <p>⭐ Đây là <b>nơi duy nhất trong hệ thống</b> khai {@code image/svg+xml} — điểm nghiệp vụ 7.
     * Thư viện media và ảnh trong bài viết đều không nhận. Việc khử trùng thì không phụ thuộc danh
     * sách này: {@code AttachmentService} luôn cho SVG đi qua {@code SvgSanitizer}, bất kể ai gọi.
     */
    private static final List<String> DINH_DANG_NHAN_DIEN =
            List.of("image/png", "image/svg+xml", "image/jpeg", "image/webp");

    private static final String CACHE_KEY = "site";

    private final SettingAdminPort settings;
    private final AttachmentPort attachments;

    private final Cache<String, Map<String, String>> cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public SiteConfigService(SettingAdminPort settings, AttachmentPort attachments) {
        this.settings = settings;
        this.attachments = attachments;
    }

    /** Danh sách đầy đủ cho màn hình quản trị — kèm nhãn, kiểu và luật kiểm tra. */
    @Transactional(readOnly = true)
    public List<SettingItem> list() {
        return settings.listGroup(GROUP);
    }

    /**
     * Cụm khoá–giá trị đã có hiệu lực, phục vụ cổng công khai.
     *
     * <p>Trả {@code effectiveValue} chứ không phải {@code value}: nơi hiển thị không cần biết giá trị
     * đang là mặc định hay đã đặt tay, nó chỉ cần thứ sẽ hiện ra.
     */
    @Transactional(readOnly = true)
    public Map<String, String> effectiveValues() {
        return cache.get(CACHE_KEY, k -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (SettingItem item : settings.listGroup(GROUP)) {
                // Quy null về chuỗi rỗng: một tham số chưa đặt và chưa có mặc định không được phép
                // làm hỏng cả cụm (Map.copyOf từ chối giá trị null), và nơi hiển thị coi hai thứ đó
                // như nhau — "chưa có gì để hiện".
                values.put(item.key(), item.effectiveValue() == null ? "" : item.effectiveValue());
            }
            return Map.copyOf(values);
        });
    }

    @Transactional
    public SettingItem update(String key, String value) {
        return settings.updateInGroup(GROUP, key, value);
    }

    /**
     * Tải logo hoặc favicon lên và trỏ tham số tương ứng vào tệp mới.
     *
     * <p>⚠ Tệp cũ <b>không bị xoá</b>. Trang đã dựng sẵn (ISR) còn đang trỏ vào nó, và một lần đổi
     * logo không đáng để làm hỏng ảnh trên những trang đó cho tới lượt dựng lại kế tiếp. Vài tệp
     * logo bỏ lại trong kho là cái giá rẻ hơn nhiều.
     *
     * @param key {@link #KEY_LOGO} hoặc {@link #KEY_FAVICON}
     */
    @Transactional
    public AttachmentRef uploadBrandImage(String key, String originalName, byte[] content) {
        AttachmentRef ref = attachments.upload(
                new AttachmentUploadCommand(OWNER_TYPE, OWNER_ID, key, originalName, content, DINH_DANG_NHAN_DIEN));
        settings.updateInGroup(GROUP, key, ref.publicId().toString());
        log.info("Đổi ảnh nhận diện '{}' sang tệp {}", key, ref.publicId());
        return ref;
    }

    /** {@code publicId} của tệp đang gán cho một khoá ảnh, hoặc rỗng nếu chưa đặt. */
    @Transactional(readOnly = true)
    public UUID brandImageId(String key) {
        String value = effectiveValues().get(key);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    /**
     * Dọn bộ nhớ đệm khi bất kỳ tham số nào của nhóm này đổi giá trị — <b>kể cả khi lượt sửa đến từ
     * màn hình cấu hình hệ thống</b>.
     *
     * <p>{@code AFTER_COMMIT} là bắt buộc: dọn trước khi commit thì lượt đọc kế tiếp nạp lại đúng giá
     * trị cũ và không còn ai dọn lần nữa ({@link SettingChangedEvent}).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettingChanged(SettingChangedEvent event) {
        if (GROUP.equals(event.groupCode())) {
            cache.invalidateAll();
        }
    }
}
