package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.songnhue.hydro.domain.PositionRole;

/**
 * Dữ liệu nhập của một hồ sơ điểm đo — CN-03.2.
 *
 * <p>Gom thành một record thay vì 13 tham số rời: cùng khuôn với {@code ConstructionForm} của
 * {@code operations}. Một danh sách tham số dài cùng kiểu ({@code String code, String name, String
 * apiCode, …}) là chỗ hoán vị hai đối số vẫn biên dịch sạch và vẫn chạy — trình biên dịch không có
 * cách nào biết {@code code} bị đưa vào chỗ {@code name}. Record đặt tên cho từng ô nên chỗ gọi
 * đọc được, và Checkstyle {@code ParameterNumber} bắt đúng thứ này.
 *
 * <h2>⛔ Vì sao {@code apiCode} vẫn nằm đây dù không đổi được</h2>
 *
 * <p>{@link StationService#update} từ chối mọi giá trị khác giá trị đang có bằng {@code HYD-2006}.
 * Bỏ trường ra khỏi form thì Jackson lặng lẽ bỏ qua và người tích hợp tưởng mình vừa đổi được mã
 * ánh xạ — im lặng mới là cái bẫy, không phải lỗi.
 *
 * <h2>⚠ {@code riverName} / {@code chainage} / toạ độ nhận {@code null} là bình thường</h2>
 *
 * <p>G8 chưa có dữ liệu cho 19 điểm đo. ⛔ Không suy từ tên, không điền cho đẹp (luật 16).
 *
 * <p>{@code interpolated} và {@code active} là {@code boolean} nguyên thuỷ — mặc định đã được
 * controller giải trước khi dựng form, để tầng application không phải mang theo ba trạng thái
 * (true/false/chưa gửi) cho một câu hỏi chỉ có hai câu trả lời.
 */
public record StationForm(
        String code,
        String name,
        String apiCode,
        /**
         * ⚠⚠ Thêm 01/09/2026 — trước đó ô "Nguồn dữ liệu" của màn hình <b>Sửa điểm đo</b> là một ô
         * bắt buộc mà giá trị đi vào khoảng không.
         *
         * <p>{@code StationsPage} render {@code Form.Item name="apiSourceId"} với
         * {@code rules={{ required: true }}} ở CẢ hai chế độ và {@code PUT} trọn {@code values},
         * nhưng {@code update()} không có tham số nào nhận nó. Người dùng đổi nguồn → nhận
         * <i>"Đã cập nhật điểm đo"</i> → {@code api_source_id} không đổi. Luật 27 nguyên bản, và im
         * lặng đúng kiểu §10.62: màn hình báo lưu thành công, dữ liệu không nhúc nhích.
         */
        UUID apiSourcePublicId,
        PositionRole positionRole,
        UUID orgUnitPublicId,
        String riverName,
        String chainage,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean interpolated,
        boolean active,
        String description,
        Set<UUID> measurementTypePublicIds) {}
