package com.songnhue.operations.domain;

/**
 * Cấp quản lý công trình — CN-02.1 (SRS quy tắc §3.2.3).
 *
 * <p>⚠ Cấp quản lý <b>không</b> quyết định phạm vi dữ liệu. Phạm vi đi bằng {@code org_unit_id} và
 * chỉ bằng nó (tầng 3, {@code conventions.md} §4.2). Đây là thông tin hành chính trên hồ sơ: ai là
 * cấp chịu trách nhiệm chính về công trình.
 */
public enum ManagementLevel {
    CONG_TY,
    XI_NGHIEP,

    /** Do một cụm quản lý — bắt buộc phải gắn cụm, ràng buộc CHECK ở CSDL giữ điều đó. */
    CUM
}
