package com.songnhue.hr.domain;

/**
 * Giới tính trên hồ sơ CBNV — CN-04.2.
 *
 * <p>⛔ Ba giá trị này sống ở <b>ba nơi</b> và phải khớp nhau: enum Java này ·
 * {@code ck_employees_gender} ở {@code V202609101076} · kiểu union TypeScript
 * {@code Gender} ở {@code frontend/admin-app/src/features/hr/hrVocabulary.ts}. {@code EnumBaNoiTest}
 * canh đúng bộ ba ấy — sự cố nó sinh ra để chặn là một biểu mẫu chào giá trị mà Jackson ⛔ không
 * giải được, làm hỏng <b>cả</b> lượt lưu chứ ⛔ không riêng ô đó.
 */
public enum Gender {
    NAM,
    NU,
    KHAC
}
