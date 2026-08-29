package com.songnhue.content.application;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Nhóm tham số duy nhất lớp này được phép <b>ghi</b>. */
    public static final String GROUP = "SITE";

    /**
     * Nhóm nhận diện Công ty — lớp này chỉ <b>đọc</b>, để cổng công khai dựng chân trang.
     *
     * <p>Cố ý không cho ghi: tên pháp nhân, địa chỉ trụ sở và số đường dây nóng không phải lựa chọn
     * trình bày, và người có quyền sửa giao diện cổng không đương nhiên có quyền sửa chúng.
     */
    public static final String GROUP_COMPANY = "COMPANY";

    public static final String KEY_LOGO = "site.logo.attachment-id";
    public static final String KEY_FAVICON = "site.favicon.attachment-id";

    /** Ảnh sơ đồ hệ thống công trình trên trang chủ — CN-02.4, thêm 29/08/2026. */
    public static final String KEY_HOME_MAP = "site.home.map-image.attachment-id";

    /**
     * Những khoá {@code settings} nhận được một tệp ảnh — <b>một nguồn sự thật</b>.
     *
     * <p>⚠⚠ Trước 29/08 danh sách này là một biểu thức {@code if} viết thẳng trong
     * {@code SiteConfigController.uploadBrandImage}. Thêm khoá ảnh thứ ba nghĩa là phải nhớ sửa ở
     * một chỗ mà không ai nghĩ tới khi đọc lớp này — đúng tình huống quy tắc 14 cấm, và triệu
     * chứng của nó rất im: màn hình quản trị dựng ô tải ảnh (nó chỉ nhìn hậu tố khoá), người dùng
     * chọn tệp, rồi nhận một lỗi "UNSUPPORTED_BRAND_IMAGE" không nói được gì.
     *
     * <p>⭐ Hậu tố {@code .attachment-id} là thứ LÁI GIAO DIỆN, không phải quy ước cho đẹp:
     * {@code SiteConfigTab.tsx} lọc {@code key.endsWith('.attachment-id')} để quyết định dựng ô
     * tải ảnh hay ô nhập chữ. {@code SiteConfigKeysTest} khẳng định mọi khoá trong tập này đều
     * mang hậu tố ấy — thiếu nó thì khoá có đường ghi mà không có ô để bấm.
     */
    public static final Set<String> KHOA_ANH = Set.of(KEY_LOGO, KEY_FAVICON, KEY_HOME_MAP);

    /**
     * Hai khoá duy nhất mà cổng công khai dựng bằng {@code dangerouslySetInnerHTML}.
     *
     * <p>Việc khử trùng <b>không</b> nằm ở lớp này (xem {@link #update}); chúng có mặt ở đây để
     * {@code SiteConfigHtmlTypeTest} khẳng định hai dòng {@code settings} tương ứng vẫn mang
     * {@code value_type} là {@code HTML} / {@code HTML_EMBED}. Đổi kiểu về {@code TEXT} là bộ lọc
     * lặng lẽ ngừng chạy mà không lỗi nào — nên phải có bài kiểm giữ hộ.
     */
    public static final String KEY_FOOTER_INFO = "site.footer.company-info";

    public static final String KEY_FOOTER_MAP = "site.footer.map-embed";

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
     *
     * <p><b>Gộp hai nhóm</b>, và chúng khác vai trò chứ không phải chia cho gọn:
     *
     * <ul>
     *   <li>{@code SITE} — cách cổng <i>trình bày</i>: màu, tiêu đề, slider, trang lỗi. Sửa được từ
     *       màn hình cấu hình giao diện của CMS.
     *   <li>{@code COMPANY} — <i>nhận diện</i> của Công ty: tên, địa chỉ, điện thoại, email, đường
     *       dây nóng. Chỉ sửa được từ màn hình cấu hình hệ thống (MOD-05), vì đây là dữ liệu pháp
     *       nhân chứ không phải lựa chọn thẩm mỹ.
     * </ul>
     *
     * <p>⚠ Trước bản này chỉ trả nhóm {@code SITE}, nên chân trang cổng <b>ghi cứng</b> địa chỉ trụ
     * sở, điện thoại, fax, email và số đường dây nóng — đổi số điện thoại của một doanh nghiệp nhà
     * nước phải sửa mã nguồn và dựng lại image, trong khi năm khoá {@code company.*} vẫn nằm đó
     * không ai đọc.
     */
    @Transactional(readOnly = true)
    public Map<String, String> effectiveValues() {
        return cache.get(CACHE_KEY, k -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (String nhom : List.of(GROUP, GROUP_COMPANY)) {
                for (SettingItem item : settings.listGroup(nhom)) {
                    // Quy null về chuỗi rỗng: một tham số chưa đặt và chưa có mặc định không được
                    // phép làm hỏng cả cụm (Map.copyOf từ chối giá trị null), và nơi hiển thị coi
                    // hai thứ đó như nhau — "chưa có gì để hiện".
                    values.put(item.key(), item.effectiveValue() == null ? "" : item.effectiveValue());
                }
            }
            return Map.copyOf(values);
        });
    }

    /**
     * Sửa một tham số.
     *
     * <p>⛔ <b>Không còn khử trùng ở đây.</b> Hai khoá HTML nay mang {@code value_type} là
     * {@code HTML} / {@code HTML_EMBED}, và {@code SettingService} khử trùng theo kiểu đó ở mọi
     * đường ghi.
     *
     * <p>Bản trước đặt một {@code switch (key)} ngay tại chỗ này, và nó chỉ đúng cho đường đi qua
     * màn hình cấu hình giao diện. Cùng hai dòng {@code settings} ấy còn sửa được bằng
     * {@code PUT /api/v1/settings/{key}} và {@code POST /api/v1/settings/import} — hai đường không
     * biết gì về danh sách khoá ở đây, nên ghi thẳng HTML thô ra cổng công khai. Bài học lặp lại:
     * <i>khử trùng phải nằm ở nơi dữ liệu đi qua, không nằm ở nơi gọi</i> — giống hệt lý do
     * {@code AttachmentService} tự đưa SVG qua {@code SvgSanitizer} thay vì tin nơi gọi nhớ làm.
     */
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
     * @param key một khoá trong {@link #KHOA_ANH}
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
        // ⚠ Phải phủ ĐÚNG những nhóm mà effectiveValues() gộp vào. Nghe thiếu một nhóm thì quản trị
        // viên đổi số đường dây nóng, giao diện báo thành công, và cổng vẫn hiện số cũ tới hết TTL
        // 10 phút — không lỗi nào, không dấu vết nào.
        if (GROUP.equals(event.groupCode()) || GROUP_COMPANY.equals(event.groupCode())) {
            cache.invalidateAll();
        }
    }
}
