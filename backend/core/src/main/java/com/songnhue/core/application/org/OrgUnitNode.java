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
        /*
          Ba trường liên hệ đi kèm nút cây từ 28/08/2026.

          ⚠ Chúng ở đây vì màn hình quản trị nạp CẢ cây bằng một lượt gọi rồi mở biểu mẫu sửa từ
          dữ liệu đã có — không có endpoint "lấy một đơn vị" nào cho giao diện. Thiếu ba trường
          này thì biểu mẫu sửa mở ra với ba ô trống, người dùng bấm Lưu, và ba giá trị đang có
          trong CSDL bị ghi đè bằng rỗng. Một biểu mẫu nạp thiếu trường thì mỗi lượt lưu là một
          lượt xoá dữ liệu, và không có thông báo nào.
        */
        String address,
        String phone,
        String email,
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
                unit.getAddress(),
                unit.getPhone(),
                unit.getEmail(),
                children);
    }
}
