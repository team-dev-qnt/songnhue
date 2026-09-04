package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.songnhue.hydro.domain.AlertConditionType;

/**
 * Hồ sơ một ngưỡng cảnh báo trên đường ghi — WS-33.
 *
 * <p>Cùng khuôn với {@code StationForm} / {@code ApiSourceForm} / {@code ConstructionForm}: gom tham
 * số vì Checkstyle {@code ParameterNumber} chặn ở 8, và ⛔ vì gom là đúng — chín tham số cùng kiểu
 * {@code UUID}/{@code BigDecimal} đứng cạnh nhau là chín cơ hội hoán vị hai đối số mà trình biên
 * dịch ⛔ không thấy. T28.22 đã sửa đúng lỗi này ở hai service khác.
 *
 * <p>⚠ Ba khoá đầu ({@code diemDoPublicId} · {@code maLoaiChiSo} · {@code mucPublicId}) chỉ dùng ở
 * đường <b>tạo</b>: bộ ba ấy là <b>bất biến</b> của một dòng ngưỡng — sửa nó là gán lịch sử cảnh báo
 * của bộ ba này sang một bộ ba khác, cùng họ với {@code HYD-2006} của {@code api_code}.
 *
 * @param nguongCao ⛔ chỉ có nghĩa với {@code OUT_OF_RANGE}; service tự bỏ với loại khác, và
 *     {@code ck_alert_rules_high_paired} chặn ở tầng CSDL
 */
public record AlertRuleForm(
        UUID diemDoPublicId,
        String maLoaiChiSo,
        UUID mucPublicId,
        AlertConditionType loai,
        BigDecimal nguong,
        BigDecimal nguongCao,
        Integer treTrongPhut,
        Boolean active,
        String note) {}
