package com.songnhue.content.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.content.application.SiteConfigService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.SettingItem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cấu hình giao diện cổng — {@code /api/v1/cms/site-config/**} (CN-01.5).
 *
 * <h2>Vì sao có màn hình này bên cạnh Cấu hình hệ thống của MOD-05</h2>
 *
 * Dữ liệu nằm cùng một bảng {@code settings}, nhưng <b>người dùng khác nhau</b>: tên cổng, logo,
 * footer là việc của Quản trị nội dung, còn ngưỡng khoá tài khoản hay chu kỳ sao lưu thì không.
 * Bắt Quản trị nội dung phải có {@code adm:setting:update} để sửa được dòng bản quyền ở chân trang
 * là cấp cho họ luôn quyền sửa chính sách bảo mật.
 *
 * <p>Phạm vi được thi hành ở tầng dưới chứ không chỉ ở annotation: {@code SiteConfigService} khai
 * đúng nhóm {@code SITE} với {@code SettingAdminPort}, nên khoá thuộc nhóm khác không đi qua đường
 * này được.
 *
 * <p>⛔ <b>Widget thuỷ văn (CN-01.5) chưa có ở đây</b> — nó cần MOD-03 (Phase 2). Cố ý <i>không</i>
 * bày ra một tham số bật/tắt cho nó: một công tắc chưa có mã nào đọc thì người vận hành gạt xong sẽ
 * tưởng mình đã bật được thứ gì đó.
 */
@RestController
@RequestMapping("/api/v1/cms/site-config")
@Tag(name = "01-cms · Cấu hình giao diện", description = "Nhận diện cổng, chân trang, trang chủ, banner slider")
public class SiteConfigController {

    private final SiteConfigService siteConfig;

    public SiteConfigController(SiteConfigService siteConfig) {
        this.siteConfig = siteConfig;
    }

    public record UpdateRequest(String value) {}

    public record BrandImage(UUID attachmentPublicId) {}

    @GetMapping
    @Operation(summary = "Toàn bộ tham số cấu hình giao diện, kèm nhãn và luật kiểm tra")
    @RequirePermission("cms:layout:manage")
    public List<SettingItem> list() {
        return siteConfig.list();
    }

    @GetMapping("/effective")
    @Operation(summary = "Chỉ khoá và giá trị đang có hiệu lực — dạng cổng công khai sẽ dùng")
    @RequirePermission("cms:layout:manage")
    public Map<String, String> effective() {
        return siteConfig.effectiveValues();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Sửa một tham số — có hiệu lực ngay, không chờ hết hạn bộ nhớ đệm")
    @RequirePermission("cms:layout:manage")
    public SettingItem update(@PathVariable String key, @Valid @RequestBody UpdateRequest request) {
        return siteConfig.update(key, request.value());
    }

    /**
     * Tải logo hoặc favicon.
     *
     * <p>⭐ Đây là <b>đường duy nhất</b> đưa SVG vào hệ thống (điểm nghiệp vụ 7). Tệp đi qua
     * {@code SvgSanitizer} ở tầng đính kèm của Core — không phải ở đây, để không có đường tải lên nào
     * quên mất bước đó.
     */
    @PostMapping(path = "/brand-images/{key}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải một ảnh cấu hình (logo, favicon, sơ đồ hệ thống) — nhận PNG, JPEG, WebP và SVG")
    @RequirePermission("cms:layout:manage")
    public BrandImage uploadBrandImage(@PathVariable String key, @RequestPart("file") MultipartFile file) {
        // ⚠ Đọc từ SiteConfigService.KHOA_ANH, KHÔNG liệt kê lại ở đây: bản trước viết hai
        //   `equals` ngay tại chỗ này, nên thêm một khoá ảnh là phải nhớ sửa một tệp thứ hai mà
        //   không có gì nhắc (quy tắc 14).
        if (!SiteConfigService.KHOA_ANH.contains(key)) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("key", "UNSUPPORTED_BRAND_IMAGE", key);
        }
        if (file == null || file.isEmpty()) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_EMPTY", "");
        }
        try {
            return new BrandImage(siteConfig
                    .uploadBrandImage(key, file.getOriginalFilename(), file.getBytes())
                    .publicId());
        } catch (IOException e) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003, e)
                    .withDetail("file", "FILE_READ_FAILED", file.getOriginalFilename());
        }
    }
}
