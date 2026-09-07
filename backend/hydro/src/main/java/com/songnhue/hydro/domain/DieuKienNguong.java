package com.songnhue.hydro.domain;

import java.math.BigDecimal;

/**
 * Phần <b>đánh giá được</b> của một luật ngưỡng — WS-33 (T33.2).
 *
 * <p>Cố ý tách khỏi entity {@code AlertRule}: entity mang khoá, điểm đo, mức, cờ bật/tắt, dấu vết
 * audit — toàn những thứ <b>không tham gia</b> vào câu hỏi <i>"con số này có vi phạm không"</i>.
 * Tách ra thì {@link DanhGiaNguong} là hàm thuần, kiểm được không cần CSDL, và ⛔ không cách nào
 * lỡ tay đọc một trường ngoài phạm vi câu hỏi.
 *
 * @param loai dạng điều kiện
 * @param nguong cận chính; với {@link AlertConditionType#RATE_OF_CHANGE} là <b>đơn vị mỗi giờ</b>
 * @param nguongCao cận trên, <b>chỉ</b> {@link AlertConditionType#OUT_OF_RANGE} dùng tới
 */
public record DieuKienNguong(AlertConditionType loai, BigDecimal nguong, BigDecimal nguongCao) {

    public DieuKienNguong {
        if (loai == null) {
            throw new IllegalArgumentException("Loại điều kiện là bắt buộc");
        }
        if (nguong == null) {
            throw new IllegalArgumentException(
                    "Ngưỡng là bắt buộc — một luật không có số để so là một luật không chạy");
        }
        // ⛔ Ép ở HÀM DỰNG, không ở lời dặn (quy tắc 16). OUT_OF_RANGE thiếu cận trên thì phép so
        //    sẽ lặng lẽ chỉ còn một nửa — bắt được đúng những giá trị thấp và mù với mọi giá trị
        //    cao, tức là cảnh báo lũ không bao giờ bắn. CSDL có ck_alert_rules_khoang_du_hai_can
        //    canh nốt; hai chốt vì đây là đường mà dữ liệu vào bằng CẢ migration lẫn màn hình.
        if (loai == AlertConditionType.OUT_OF_RANGE) {
            if (nguongCao == null) {
                throw new IllegalArgumentException("OUT_OF_RANGE bắt buộc có cận trên");
            }
            if (nguong.compareTo(nguongCao) > 0) {
                throw new IllegalArgumentException(
                        "Khoảng đảo ngược: cận dưới %s > cận trên %s".formatted(nguong, nguongCao));
            }
        }
        if (loai == AlertConditionType.RATE_OF_CHANGE && nguong.signum() < 0) {
            throw new IllegalArgumentException("Tốc độ đổi tối đa không được âm: " + nguong);
        }
    }

    /** Dạng này có cần một số đo trước đó mới kết luận được không. */
    public boolean canMocSoSanh() {
        return loai == AlertConditionType.RATE_OF_CHANGE;
    }
}
