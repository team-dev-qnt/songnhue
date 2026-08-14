package com.songnhue.core.api.org;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitType;

/** DTO của API sơ đồ tổ chức. Không record nào mang {@code id} chạy số ra ngoài. */
public final class OrgUnitDtos {

    private OrgUnitDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String shortName,
            @NotNull OrgUnitType unitType,
            /** Bỏ trống = tạo nút gốc. Chỉ được đúng một nút gốc trong toàn hệ thống. */
            UUID parentPublicId) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String name, @Size(max = 100) String shortName, @NotNull OrgUnitType unitType) {}

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
            boolean active) {

        public static OrgUnitSummary of(OrgUnit unit) {
            return new OrgUnitSummary(
                    unit.getPublicId(),
                    unit.getCode(),
                    unit.getName(),
                    unit.getShortName(),
                    unit.getUnitType(),
                    unit.getPath(),
                    unit.getDepth(),
                    unit.isActive());
        }
    }
}
