package com.songnhue.core.api.org;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.org.OrgUnitLeaderService;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.domain.org.OrgUnitLeader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh bạ lãnh đạo của một đơn vị — {@code /api/v1/org-units/{publicId}/leaders} (CR-25, CR-26).
 *
 * <p>Đường <b>ghi</b> cho bảng mà trước 28/08/2026 chỉ có đường đọc; lý do đầy đủ ở
 * {@link OrgUnitLeaderService}.
 *
 * <p>Đặt lồng dưới {@code /org-units/{publicId}} chứ không thành tài nguyên gốc: một dòng danh bạ
 * không tồn tại độc lập với đơn vị của nó, và URL nói đúng điều đó thì không ai phải nhớ truyền
 * {@code orgUnitId} trong thân yêu cầu.
 */
@RestController
@RequestMapping("/api/v1/org-units/{orgUnitPublicId}/leaders")
@Tag(name = "05-adm · Sơ đồ tổ chức", description = "Danh bạ lãnh đạo công bố trên cổng")
public class OrgUnitLeaderController {

    private final OrgUnitLeaderService service;

    public OrgUnitLeaderController(OrgUnitLeaderService service) {
        this.service = service;
    }

    public record LeaderRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 30) String phone,
            @Email @Size(max = 255) String email,
            Integer sortOrder) {}

    public record ActiveRequest(@NotNull Boolean active) {}

    /** Dòng trả về màn hình quản trị — có {@code active}, thứ cổng công khai không bao giờ thấy. */
    public record LeaderRow(
            UUID publicId, String fullName, String title, String phone, String email, int sortOrder, boolean active) {

        static LeaderRow of(OrgUnitLeader e) {
            return new LeaderRow(
                    e.getPublicId(),
                    e.getFullName(),
                    e.getTitle(),
                    e.getPhone(),
                    e.getEmail(),
                    e.getSortOrder(),
                    e.isActive());
        }
    }

    @GetMapping
    @Operation(summary = "Danh bạ lãnh đạo của đơn vị — kể cả dòng đã tắt")
    @RequirePermission("adm:org-unit:view")
    public List<LeaderRow> list(@PathVariable UUID orgUnitPublicId) {
        return service.danhSach(orgUnitPublicId).stream().map(LeaderRow::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm một dòng danh bạ")
    @RequirePermission("adm:org-unit:manage")
    public LeaderRow create(@PathVariable UUID orgUnitPublicId, @Valid @RequestBody LeaderRequest request) {
        return LeaderRow.of(service.them(
                orgUnitPublicId,
                request.fullName(),
                request.title(),
                request.phone(),
                request.email(),
                request.sortOrder()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa một dòng danh bạ")
    @RequirePermission("adm:org-unit:manage")
    public LeaderRow update(
            @PathVariable UUID orgUnitPublicId,
            @PathVariable UUID publicId,
            @Valid @RequestBody LeaderRequest request) {
        return LeaderRow.of(service.sua(
                publicId, request.fullName(), request.title(), request.phone(), request.email(), request.sortOrder()));
    }

    /**
     * Bật / tắt hiển thị trên cổng.
     *
     * <p>Tách khỏi {@code PUT} toàn phần vì đây là thao tác một-cú-bấm trên bảng quản trị: bắt nó
     * gửi kèm cả họ tên và chức danh nghĩa là màn hình phải mở biểu mẫu chỉ để đổi một công tắc, và
     * mỗi lần gửi lại là một cơ hội ghi đè nhầm trường khác.
     */
    @PutMapping("/{publicId}/active")
    @Operation(summary = "Bật/tắt hiển thị một dòng danh bạ trên cổng")
    @RequirePermission("adm:org-unit:manage")
    public LeaderRow setActive(
            @PathVariable UUID orgUnitPublicId,
            @PathVariable UUID publicId,
            @Valid @RequestBody ActiveRequest request) {
        return LeaderRow.of(service.doiTrangThai(publicId, request.active()));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm một dòng danh bạ")
    @RequirePermission("adm:org-unit:manage")
    public void delete(@PathVariable UUID orgUnitPublicId, @PathVariable UUID publicId) {
        service.xoa(publicId);
    }
}
