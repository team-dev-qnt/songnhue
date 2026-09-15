package com.songnhue.core.api.settings;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.XacThucLaiService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.domain.settings.Setting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cấu hình hệ thống — {@code /api/v1/settings/**} (CN-05.3, M5.17).
 *
 * <p>Đây là màn hình biến quy tắc 12 của dự án thành hiện thực: tham số nghiệp vụ nằm trong DB và
 * <b>sửa được không cần deploy</b>. Cũng chính là hạng mục nghiệm thu của G9 (Admin tự cấu hình
 * ngưỡng cảnh báo).
 */
@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "05-adm · Cấu hình hệ thống", description = "Tham số nghiệp vụ sửa được trên giao diện")
public class SettingController {

    private final SettingService settingService;
    private final XacThucLaiService xacThucLai;

    public SettingController(SettingService settingService, XacThucLaiService xacThucLai) {
        this.settingService = settingService;
        this.xacThucLai = xacThucLai;
    }

    @GetMapping
    @Operation(summary = "Danh sách tham số, lọc theo nhóm nếu có")
    @RequirePermission("adm:setting:view")
    public List<SettingDtos.SettingView> list(@RequestParam(required = false) String group) {
        List<Setting> rows =
                group == null || group.isBlank() ? settingService.listAll() : settingService.listByGroup(group);
        return rows.stream().map(SettingDtos.SettingView::of).toList();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Sửa một tham số — có hiệu lực ngay, không cần khởi động lại")
    @RequirePermission("adm:setting:update")
    public SettingDtos.SettingView update(
            @PathVariable String key,
            @Valid @RequestBody SettingDtos.UpdateRequest request,
            HttpServletRequest httpRequest) {
        // T61.42 — nhóm nhạy cảm (SECURITY/AUDIT/BACKUP): nhập lại mã 2FA NGAY LÚC NÀY.
        boolean nhayCam = settingService.canXacThucLai(key);
        if (nhayCam) {
            xacThucLai.xacThuc(request.maXacThuc(), ClientInfo.from(httpRequest));
        }
        return SettingDtos.SettingView.of(settingService.update(key, request.value(), nhayCam));
    }

    @GetMapping("/export")
    @Operation(summary = "Xuất bộ cấu hình — KHÔNG bao gồm credential (§4.7)")
    @RequirePermission("adm:setting:export")
    public Map<String, String> export() {
        return settingService.exportConfiguration();
    }

    @PostMapping("/import")
    @Operation(summary = "Nhập bộ cấu hình — kiểm tra toàn bộ rồi mới áp dụng")
    @RequirePermission("adm:setting:import")
    public SettingService.ImportResult importConfiguration(
            @Valid @RequestBody SettingDtos.ImportRequest request, HttpServletRequest httpRequest) {
        boolean nhayCam = settingService.nhapCanXacThucLai(request.values());
        if (nhayCam) {
            xacThucLai.xacThuc(request.maXacThuc(), ClientInfo.from(httpRequest));
        }
        return settingService.importConfiguration(request.values(), nhayCam);
    }

    /** DTO của API cấu hình. */
    public static final class SettingDtos {

        private SettingDtos() {}

        /** Giá trị rỗng = quay về mặc định của danh mục, nên cố ý KHÔNG bắt {@code @NotBlank}. */
        /**
         * @param maXacThuc mã 2FA nhập lại — BẮT BUỘC khi khoá thuộc nhóm nhạy cảm ({@code canXacThucLai}), bỏ qua với
         *     nhóm khác
         */
        public record UpdateRequest(String value, String maXacThuc) {}

        /** @param maXacThuc bắt buộc khi bộ cấu hình ĐỔI ít nhất một khoá nhóm nhạy cảm */
        public record ImportRequest(@NotEmpty Map<String, String> values, String maXacThuc) {}

        /**
         * Trả kèm {@code valueType} và {@code validation} để FE dựng đúng ô nhập và kiểm sơ bộ.
         *
         * <p>Kiểm ở FE chỉ để người dùng biết sớm; chốt chặn thật vẫn ở
         * {@code SettingValidator} phía máy chủ (§4.2 — tầng 1 chỉ phục vụ trải nghiệm).
         */
        public record SettingView(
                String key,
                String value,
                String effectiveValue,
                String valueType,
                String defaultValue,
                String groupCode,
                String label,
                String description,
                String validation,
                boolean editable,
                boolean exportable,
                boolean canXacThucLai) {

            public static SettingView of(Setting setting) {
                return new SettingView(
                        setting.getSettingKey(),
                        setting.getSettingValue(),
                        setting.effectiveValue(),
                        setting.getValueType(),
                        setting.getDefaultValue(),
                        setting.getGroupCode(),
                        setting.getLabel(),
                        setting.getDescription(),
                        setting.getValidation(),
                        setting.isEditable(),
                        setting.isExportable(),
                        SettingService.nhayCam(setting));
            }
        }
    }
}
