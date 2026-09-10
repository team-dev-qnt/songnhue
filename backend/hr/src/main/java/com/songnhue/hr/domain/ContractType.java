package com.songnhue.hr.domain;

/**
 * Loại hợp đồng lao động — CN-04.2, theo <b>BLLĐ 2019</b>.
 *
 * <p>⛔ Là enum trong mã chứ ⛔ không phải danh mục có CRUD (khác quy tắc 16): danh sách này do
 * <b>luật</b> quy định (Điều 20 — chỉ hai loại HĐLĐ; Điều 24 — hợp đồng thử việc), ⛔ không phải do
 * Công ty vận hành. Thêm một loại ở đây kéo theo một nhánh trong cảnh báo hết hạn (M4.9), tức phải
 * sửa mã dù có bảng danh mục hay ⛔ không.
 *
 * <p>Bộ ba enum ↔ TS ↔ CHECK, xem {@link Gender}.
 */
public enum ContractType {
    /** Điều 20.1.a — ⛔ không có ngày hết hạn, nên ⛔ không vào diện cảnh báo M4.9. */
    KHONG_XAC_DINH_THOI_HAN,
    /** Điều 20.1.b — tối đa 36 tháng. Đây là loại M4.9 phải theo dõi. */
    XAC_DINH_THOI_HAN,
    /** Điều 24 — hợp đồng thử việc. */
    THU_VIEC,
    /** Hợp đồng dịch vụ / khoán việc — ⛔ không phải HĐLĐ. */
    KHAC
}
