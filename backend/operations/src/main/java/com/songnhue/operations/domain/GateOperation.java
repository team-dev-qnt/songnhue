package com.songnhue.operations.domain;

/**
 * Kiểu vận hành cửa cống — CN-02.1, hồ sơ ở {@code sluice_specs}.
 *
 * <p>Cùng câu chuyện {@link SluiceType}: ô chữ tự do ở {@code StepTechnical.tsx:123} trong khi
 * {@code ck_sluice_specs_gate} ({@code V202608211026:264}) chỉ nhận ba giá trị. Xem javadoc bên ấy.
 */
public enum GateOperation {
    /** Đóng mở bằng tay. */
    THU_CONG,

    /** Đóng mở bằng động cơ điện. */
    DIEN,

    /** Đóng mở bằng xy-lanh thuỷ lực. */
    THUY_LUC
}
