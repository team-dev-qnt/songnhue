package com.songnhue.core.application.org;

import java.util.List;
import java.util.UUID;

import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitType;

/**
 * Một nút trong sơ đồ tổ chức trả ra API — dạng lồng nhau để FE render thẳng thành cây.
 *
 * <p>Không có {@code id} chạy số: API chỉ nói chuyện bằng {@code publicId} (§4.2 chống IDOR). Có
 * {@code path} vì FE cần nó để thụt lề danh sách phẳng và để biết quan hệ cha–con mà không phải suy
 * từ cấu trúc lồng.
 */
public record OrgUnitNode(
        UUID publicId,
        String code,
        String name,
        String shortName,
        OrgUnitType unitType,
        String path,
        int depth,
        int sortOrder,
        boolean active,
        List<OrgUnitNode> children) {

    public static OrgUnitNode of(OrgUnit unit, List<OrgUnitNode> children) {
        return new OrgUnitNode(
                unit.getPublicId(),
                unit.getCode(),
                unit.getName(),
                unit.getShortName(),
                unit.getUnitType(),
                unit.getPath(),
                unit.getDepth(),
                unit.getSortOrder(),
                unit.isActive(),
                children);
    }
}
