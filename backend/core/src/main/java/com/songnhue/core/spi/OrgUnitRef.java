package com.songnhue.core.spi;

import java.util.UUID;

/**
 * Một đơn vị trong sơ đồ tổ chức.
 *
 * <p>Bảng {@code org_units} dùng chung cho <b>cả Xí nghiệp (MOD-02) lẫn phòng ban (MOD-04)</b> —
 * quy tắc 7. Tách hai bảng thì cây tổ chức có hai nửa không nối được, mà Công ty chỉ có một sơ đồ.
 *
 * @param id khoá nội bộ — cần vì bản ghi nghiệp vụ lưu {@code org_unit_id} và bộ lọc phạm vi ở tầng
 *     3 làm việc trên khoá đó. Mọi thứ đi ra API dùng {@code publicId}
 * @param unitType {@code CONG_TY} · {@code PHONG_BAN} · {@code XI_NGHIEP} · {@code TO_DOI} ·
 *     {@code KHAC}
 * @param path đường dẫn vật chất hoá, VD {@code /1/4/9/} — dùng để biết quan hệ cha–con mà không
 *     phải truy vấn đệ quy
 */
public record OrgUnitRef(
        Long id, UUID publicId, String code, String name, String shortName, String unitType, String path, int depth) {}
