package com.songnhue.core.application.settings;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.util.HtmlSanitizer;
import com.songnhue.core.domain.settings.Setting;
import com.songnhue.core.infra.settings.SettingRepository;
import com.songnhue.core.spi.SettingAdminPort;
import com.songnhue.core.spi.SettingChangedEvent;
import com.songnhue.core.spi.SettingItem;
import com.songnhue.core.spi.SettingPort;

/**
 * Đọc tham số nghiệp vụ từ bảng {@code settings} (quy tắc 12 của dự án).
 *
 * <p>WS-5 chỉ cần phần đọc: chính sách mật khẩu, ngưỡng khoá tài khoản, giờ hành chính. <b>WS-6 /
 * T6.11</b> mở rộng lớp này thành dịch vụ đầy đủ (ghi, validate theo cột {@code validation}, API cho
 * UI, xuất/nhập cấu hình loại trừ credential) — chỗ cắm đã chừa ở {@link #invalidate(String)}.
 *
 * <p><b>Vì sao có cache:</b> mỗi lần đăng nhập cần 4–5 tham số; không cache thì mỗi lần đăng nhập là
 * 5 vòng tới DB cho những giá trị gần như không bao giờ đổi. TTL ngắn để Admin sửa trên UI thấy có
 * hiệu lực ngay mà không phải khởi động lại.
 *
 * <p>⚠ Cache nằm trong tiến trình (Caffeine, không có Redis ở v1). Khi lên ≥2 node, sửa tham số ở
 * node này thì node kia còn giữ giá trị cũ tối đa hết TTL — mốc đó được ghi ở
 * {@code architecture-review.md} §6.4 cùng các thay đổi khác phải làm.
 */
@Service
public class SettingService implements SettingPort, SettingAdminPort {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int CACHE_MAX_SIZE = 500;

    private final SettingRepository repository;
    private final SettingValidator validator;
    private final ApplicationEventPublisher events;

    /** Giữ cả giá trị rỗng để khoá không tồn tại cũng không phải hỏi DB lại mỗi lần. */
    private final Cache<String, Optional<String>> cache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .build();

    public SettingService(SettingRepository repository, SettingValidator validator, ApplicationEventPublisher events) {
        this.repository = repository;
        this.validator = validator;
        this.events = events;
    }

    /**
     * ⚠ Cố ý <b>không</b> {@code @Transactional}. Ba lý do, và cái thứ ba mới là cái quyết định:
     *
     * <ol>
     *   <li>Phần lớn lượt gọi dừng ở bộ nhớ đệm, không chạm CSDL.
     *   <li>Lúc trượt đệm thì chỉ có đúng một câu truy vấn — Spring Data tự mở giao dịch của nó.
     *   <li>{@link #getInt}, {@link #getBoolean}, {@link #getTime} gọi hàm này bằng {@code this}, nên
     *       chú thích ở đây <b>không có tác dụng</b> với chúng. Giữ lại là ghi một bảo đảm không tồn
     *       tại — và tham số cấu hình bị đọc từ filter, bộ hẹn giờ, worker, những nơi vốn không có
     *       giao dịch nào để tham gia.
     * </ol>
     */
    @Override
    public Optional<String> getString(String key) {
        return cache.get(
                key,
                k -> repository.findBySettingKey(k).map(Setting::effectiveValue).filter(value -> !value.isBlank()));
    }

    /**
     * @param fallback dùng khi thiếu khoá hoặc giá trị sai định dạng — hệ thống vẫn phải đăng nhập
     *     được kể cả khi ai đó lỡ tay xoá một dòng cấu hình
     */
    @Override
    public int getInt(String key, int fallback) {
        return getString(key).map(value -> parseInt(key, value, fallback)).orElse(fallback);
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        return getString(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    @Override
    public Duration getMinutes(String key, int fallbackMinutes) {
        return Duration.ofMinutes(getInt(key, fallbackMinutes));
    }

    /** Giá trị kiểu {@code TIME} trong bảng settings, VD {@code 08:00}. */
    @Override
    public LocalTime getTime(String key, LocalTime fallback) {
        return getString(key).map(value -> parseTime(key, value, fallback)).orElse(fallback);
    }

    /** Gọi sau khi sửa tham số, để thay đổi có hiệu lực ngay thay vì chờ hết TTL. */
    public void invalidate(String key) {
        cache.invalidate(key);
    }

    // ---- Phần ghi và quản trị (T6.11) ----------------------------------------

    @Transactional(readOnly = true)
    public List<Setting> listAll() {
        return repository.findAllByOrderByGroupCodeAscSortOrderAscSettingKeyAsc();
    }

    @Transactional(readOnly = true)
    public List<Setting> listByGroup(String groupCode) {
        return repository.findByGroupCodeOrderBySortOrderAscSettingKeyAsc(groupCode);
    }

    /**
     * Sửa một tham số.
     *
     * <p>Ba chốt chặn trước khi ghi — xem {@link #apDung(Setting, String)}.
     */
    @Transactional
    public Setting update(String key, String newValue) {
        Setting setting =
                repository.findBySettingKey(key).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        if (!setting.isEditable()) {
            throw new PermissionDeniedException(ErrorCode.ADM_2007);
        }
        return apDung(setting, newValue);
    }

    /**
     * Ghi một giá trị đã qua đủ ba chốt chặn. <b>Mọi đường ghi vào bảng {@code settings} phải đi qua
     * đây</b> — đó là lý do nó tồn tại.
     *
     * <ol>
     *   <li>{@link SettingValidator} → sai kiểu hoặc vi phạm luật thì báo ngay. Không chặn ở đây thì
     *       giá trị rác chỉ lộ ra lúc đọc, mà nơi đọc lại lặng lẽ dùng giá trị dự phòng.
     *   <li><b>Khử trùng theo {@code value_type}</b> — xem {@link #khuTrung}.
     *   <li>Dọn bộ nhớ đệm <i>và</i> phát {@link SettingChangedEvent}. Hai việc khác nhau:
     *       {@link #invalidate} chỉ dọn đệm của chính lớp này, còn sự kiện là thứ đánh thức đệm
     *       riêng của các module khác ({@code SiteConfigService} giữ đệm 10 phút).
     * </ol>
     *
     * <p>⚠ Trước bản này, {@code update()} làm cả ba việc còn {@link #importConfiguration} <b>không
     * làm việc nào trong hai việc sau</b>: nhập cấu hình ghi thẳng HTML thô và không đánh thức đệm
     * của cổng, nên cổng còn hiện giá trị cũ tới hết TTL. Chú thích cũ ngay tại chỗ đó ghi "đây là
     * nơi DUY NHẤT ghi bảng settings" — đúng ở mức <i>lớp</i>, sai ở mức <i>phương thức</i>, và cái
     * sai đó không có bài kiểm nào chạm tới.
     */
    private Setting apDung(Setting setting, String newValue) {
        validator.validate(setting, newValue);

        String key = setting.getSettingKey();
        String previous = setting.getSettingValue();
        setting.changeValue(khuTrung(setting, newValue));
        Setting saved = repository.save(setting);
        invalidate(key);
        events.publishEvent(new SettingChangedEvent(key, saved.getGroupCode()));

        log.info("Đổi tham số '{}': '{}' → '{}'", key, previous, saved.getSettingValue());
        return saved;
    }

    /**
     * Khử trùng theo <b>kiểu giá trị</b>, không theo danh sách khoá.
     *
     * <p>Hai kiểu {@code HTML} và {@code HTML_EMBED} đánh dấu những tham số mà nơi hiển thị dựng
     * bằng {@code dangerouslySetInnerHTML} (hiện là khối thông tin Công ty và khối bản đồ ở chân
     * trang cổng công khai). Chúng là dữ liệu do người dùng soạn và được phục vụ cho <b>người dân
     * vào tra cứu</b>, nên một đoạn mã lọt vào đây chạy trong trình duyệt của tất cả họ.
     *
     * <p>⛔ Vì sao phân loại bằng cột chứ không bằng {@code switch (key)} ở nơi gọi: đã từng làm thế
     * và nó chỉ đúng ở một trong ba đường ghi. Đo thật trước khi sửa — gửi
     * {@code <img src=x onerror=…><script>…</script>} qua {@code PUT /api/v1/settings/{key}} và qua
     * {@code POST /api/v1/settings/import}: cả hai trả 200, CSDL lưu nguyên văn, và
     * {@code GET /api/v1/public/site-config} trả lại nguyên văn. Đặt luật vào <i>dữ liệu</i> thì
     * đường ghi viết sau này cũng bị ràng buộc mà không ai phải nhớ.
     */
    private static String khuTrung(Setting setting, String value) {
        return switch (setting.getValueType()) {
            case "HTML" -> HtmlSanitizer.clean(value);
            case "HTML_EMBED" -> HtmlSanitizer.cleanMapEmbed(value);
            default -> value;
        };
    }

    // ---- SettingAdminPort: ghi trong phạm vi một nhóm (WS-15) -----------------

    @Transactional(readOnly = true)
    @Override
    public List<SettingItem> listGroup(String groupCode) {
        return listByGroup(groupCode).stream().map(SettingService::toItem).toList();
    }

    /**
     * ⛔ Chốt chặn của {@link SettingAdminPort}: khoá phải <b>thuộc đúng nhóm đã khai</b>.
     *
     * <p>Không có dòng kiểm tra này thì port trở thành đường ghi tự do vào toàn bộ bảng
     * {@code settings}, và giới hạn "CMS chỉ sửa được nhóm site" tụt xuống thành một quy ước người ta
     * nhớ hoặc quên.
     */
    @Transactional
    @Override
    public SettingItem updateInGroup(String groupCode, String key, String value) {
        Setting setting =
                repository.findBySettingKey(key).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        if (!setting.getGroupCode().equals(groupCode)) {
            log.warn(
                    "Từ chối sửa '{}' qua nhóm '{}' — khoá này thuộc nhóm '{}'",
                    key,
                    groupCode,
                    setting.getGroupCode());
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
        // Tự gọi trong cùng lớp nên KHÔNG đi qua proxy Spring — ở đây vô hại vì phương thức này đã
        // mở sẵn giao dịch và update() chỉ cần REQUIRED. Ghi ra để lần sau ai đổi update() sang
        // REQUIRES_NEW thì thấy ngay là đường này sẽ lặng lẽ không có hiệu lực (bẫy đã trả giá ở
        // BackupService, WS-7).
        return toItem(update(key, value));
    }

    private static SettingItem toItem(Setting setting) {
        return new SettingItem(
                setting.getSettingKey(),
                setting.getSettingValue(),
                setting.effectiveValue(),
                setting.getValueType(),
                setting.getDefaultValue(),
                setting.getGroupCode(),
                setting.getLabel(),
                setting.getDescription(),
                setting.getValidation(),
                setting.isEditable());
    }

    /**
     * Bản xuất cấu hình (M5.17).
     *
     * <p>⚠ <b>Chỉ những dòng {@code exportable = true}.</b> Đây là chốt chặn của conventions.md §4.7:
     * credential bên thứ ba không bao giờ nằm trong bản xuất. Bản xuất cấu hình hay được gửi qua
     * email hoặc kèm vào phiếu hỗ trợ — lọc ở đây là lọc đúng chỗ nó rời khỏi hệ thống.
     *
     * <p>Lọc bằng truy vấn chứ không lọc trong Java sau khi đã tải hết: giá trị nhạy cảm không cần
     * đi vào bộ nhớ tiến trình rồi mới bị bỏ đi.
     */
    @Transactional(readOnly = true)
    public Map<String, String> exportConfiguration() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Setting setting : repository.findByExportableTrueOrderBySettingKeyAsc()) {
            result.put(setting.getSettingKey(), setting.effectiveValue());
        }
        return result;
    }

    /**
     * Nhập cấu hình (M5.17).
     *
     * <p><b>Kiểm tra hết rồi mới ghi</b> — một dòng sai thì không dòng nào được áp dụng. Áp dụng nửa
     * chừng để lại hệ thống ở trạng thái lai giữa hai bộ cấu hình, và không ai biết đã tới dòng nào.
     *
     * <p>Khoá lạ hoặc khoá không xuất được đều bị <b>bỏ qua</b>, không phải lỗi: bản xuất từ phiên
     * bản khác có thể thiếu hoặc thừa khoá, mà chặn cả gói vì một khoá đã bị gỡ là buộc người vận
     * hành phải sửa tay file JSON.
     *
     * @return số tham số thực sự thay đổi
     */
    @Transactional
    public ImportResult importConfiguration(Map<String, String> incoming) {
        List<Setting> toApply = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            Optional<Setting> found = repository.findBySettingKey(entry.getKey());
            if (found.isEmpty() || !found.get().isEditable() || !found.get().isExportable()) {
                skipped.add(entry.getKey());
                continue;
            }
            Setting setting = found.get();
            validator.validate(setting, entry.getValue());
            toApply.add(setting);
        }

        int changed = 0;
        for (Setting setting : toApply) {
            String value = incoming.get(setting.getSettingKey());
            if (!Objects.equals(setting.getSettingValue(), value)) {
                // Đi qua apDung() chứ không tự ghi: khử trùng HTML và đánh thức đệm của các module
                // khác đều nằm trong đó. Bản trước tự ghi ở đây và mất cả hai.
                apDung(setting, value);
                changed++;
            }
        }
        log.info("Nhập cấu hình: {} tham số đổi, {} khoá bỏ qua", changed, skipped.size());
        return new ImportResult(changed, skipped);
    }

    /** @param skippedKeys khoá không tồn tại, không sửa được, hoặc không thuộc phạm vi xuất/nhập */
    public record ImportResult(int changed, List<String> skippedKeys) {}

    // -------------------------------------------------------------------------

    private static int parseInt(String key, String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            // Không ném: một dòng cấu hình hỏng không được phép làm sập đường đăng nhập
            log.warn("Tham số '{}' không phải số nguyên, dùng giá trị dự phòng {}", key, fallback);
            return fallback;
        }
    }

    private static LocalTime parseTime(String key, String value, LocalTime fallback) {
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.warn("Tham số '{}' không phải giờ hợp lệ (HH:mm), dùng giá trị dự phòng {}", key, fallback);
            return fallback;
        }
    }
}
