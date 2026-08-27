package com.songnhue.core.api.org;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitType;

/** DTO của API sơ đồ tổ chức. Không record nào mang {@code id} chạy số ra ngoài. */
public final class OrgUnitDtos {

    private OrgUnitDtos() {}

    /**
     * ⚠ Ba trường liên hệ ({@code address}, {@code phone}, {@code email}) thêm ngày 28/08/2026.
     *
     * <p>Ba cột ấy có trong {@code org_units} từ {@code V202608131004}, và
     * {@code /api/v1/public/org-units/subsidiaries} <b>hiển thị chúng</b> trên cổng công khai
     * (bảng 6 cột của CR-26, và các thẻ "Đơn vị trực thuộc" ở trang chủ). Nhưng đo ngày 28/8:
     * {@code setAddress}/{@code setPhone}/{@code setEmail} <b>không có lời gọi nào</b> trong toàn
     * bộ mã ngoài chính lớp entity — biểu mẫu quản trị chỉ có mã, tên, tên tắt, loại, đơn vị cha.
     *
     * <p>Tức là ba cột <b>đọc được mà không ghi được</b>: cổng bày ra ba ô mà không ai có cách nào
     * điền. Quy tắc 15 ở chiều ghi — cùng hình dạng với {@code categories.visible} (T24.25) và với
     * {@code limits.upload.max-mb.*}. Chỗ này nặng hơn vì nó <b>chặn nghiệm thu CR-26</b>: bảng
     * Xí nghiệp trực thuộc không thể có dữ liệu.
     */
    public record CreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String shortName,
            @NotNull OrgUnitType unitType,
            /** Bỏ trống = tạo nút gốc. Chỉ được đúng một nút gốc trong toàn hệ thống. */
            UUID parentPublicId,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            @Email @Size(max = 255) String email) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String shortName,
            @NotNull OrgUnitType unitType,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            @Email @Size(max = 255) String email) {}

    public record MoveRequest(@NotNull UUID newParentPublicId) {}

    /** Thứ tự mới của các đơn vị <b>cùng một cấp</b> — vị trí trong mảng chính là thứ tự hiển thị. */
    public record ReorderRequest(@NotEmpty List<UUID> orderedPublicIds) {}

    /** Dạng phẳng cho ô chọn đơn vị; {@code path} để FE tự thụt lề mà không cần cấu trúc lồng. */
    public record OrgUnitSummary(
            UUID publicId,
            String code,
            String name,
            String shortName,
            OrgUnitType unitType,
            String path,
            int depth,
            boolean active,
            String address,
            String phone,
            String email) {

        public static OrgUnitSummary of(OrgUnit unit) {
            return new OrgUnitSummary(
                    unit.getPublicId(),
                    unit.getCode(),
                    unit.getName(),
                    unit.getShortName(),
                    unit.getUnitType(),
                    unit.getPath(),
                    unit.getDepth(),
                    unit.isActive(),
                    unit.getAddress(),
                    unit.getPhone(),
                    unit.getEmail());
        }
    }
}
