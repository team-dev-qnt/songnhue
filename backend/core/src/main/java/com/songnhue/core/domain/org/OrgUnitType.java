package com.songnhue.core.domain.org;

/**
 * Loại đơn vị trong cây tổ chức.
 *
 * <p>Một bảng {@code org_units} dùng chung cho <b>cả Xí nghiệp (MOD-02) lẫn phòng ban (MOD-04
 * HRM)</b> — quy tắc 7 của dự án. Tách hai bảng thì cây tổ chức có hai nửa không nối được với nhau,
 * mà thực tế Công ty chỉ có một sơ đồ duy nhất.
 *
 * <p>Đây là enum trong mã (không phải danh mục có CRUD) vì bộ loại đơn vị gắn với cơ cấu pháp lý của
 * doanh nghiệp, không phải thứ Admin thêm bớt hằng ngày — khác với mã tình hình vận hành hay mức
 * ngưỡng (quy tắc 16).
 */
public enum OrgUnitType {
    /** Nút gốc — Công ty. Chỉ có đúng một. */
    CONG_TY,
    PHONG_BAN,
    XI_NGHIEP,
    TO_DOI,
    KHAC
}
