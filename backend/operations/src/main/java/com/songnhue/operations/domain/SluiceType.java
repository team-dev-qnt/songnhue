package com.songnhue.operations.domain;

/**
 * Loại cống — CN-02.1, hồ sơ ở {@code sluice_specs}.
 *
 * <p>⛔⛔ <b>T68.28 — trước 23/09/2026 đây là một ô CHỮ TỰ DO.</b> {@code StepTechnical.tsx:79} là một
 * {@code <Input/>}, DTO chỉ ràng {@code @Size(max = 20)}, entity giữ {@code String} — trong khi CSDL
 * đã chốt đúng bốn giá trị từ {@code V202608211026:261} ({@code ck_sluice_specs_type}). Hệ quả đo
 * được <b>⛔ phải</b> một lỗi hợp lệ hoá tử tế: giá trị lạ đi hết đường tới CSDL, bật
 * {@code DataIntegrityViolationException}, và {@code GlobalExceptionHandler} đổi nó thành <b>409
 * {@code SYS-0005}</b> — <i>"Dữ liệu vừa được người khác thay đổi, vui lòng tải lại"</i>. Người nhập
 * đọc câu ấy rồi đi tải lại trang, gõ lại đúng giá trị cũ, và gặp lại đúng câu ấy.
 *
 * <p>⇒ Kiểu hoá là cách chặn rẻ nhất: Jackson từ chối giá trị lạ ngay tầng đọc thân yêu cầu và trả
 * <b>400</b> kèm TÊN TRƯỜNG. Cùng khuôn {@link ConstructionType} và {@link ManagementLevel} — hai
 * trường enum đã nằm sẵn trong chính DTO ấy, tức tiền lệ có từ đầu mà hai ô này lỡ mất.
 *
 * <p>⚠ Đây là <b>enum trong mã</b>, ⛔ phải danh mục có CRUD (quy tắc 16): thêm một loại cống là thêm
 * một bộ thông số và một cách vận hành, tức phải sửa mã — xem lập luận đầy đủ ở {@link ConstructionType}.
 */
public enum SluiceType {
    /** Cống hộp — thân cống mặt cắt chữ nhật. */
    HOP,

    /** Cống tròn — thân cống mặt cắt tròn. */
    TRON,

    /** Cống van phẳng. */
    VAN_PHANG,

    /** Cống cla-pê — cửa tự động một chiều. */
    CLAPE
}
