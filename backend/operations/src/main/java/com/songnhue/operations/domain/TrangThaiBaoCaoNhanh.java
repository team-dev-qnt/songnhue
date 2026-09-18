package com.songnhue.operations.domain;

/** Trạng thái kỳ Báo cáo nhanh — khớp {@code ck_bao_cao_nhanh_trang_thai}. Chỉ Workflow engine đổi. */
public enum TrangThaiBaoCaoNhanh {
    /** Đang nhập — sửa được Bảng 2/5. */
    NHAP,
    /** Đã chốt — văn bản đã gửi; sửa trả {@code OPS-2029}, phải MỞ LẠI (kèm lý do). */
    DA_CHOT
}
