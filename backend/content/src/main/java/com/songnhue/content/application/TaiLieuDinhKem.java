package com.songnhue.content.application;

import java.util.UUID;

/**
 * Một tài liệu đính kèm <b>đã ghép với siêu dữ liệu của tệp</b> — dùng cho màn hình quản trị (WS-40).
 *
 * <h2>Vì sao ghép ở tầng application chứ không ở màn hình</h2>
 *
 * Bảng nối chỉ giữ {@code attachment_public_id} + {@code label}; tên gốc, định dạng và dung lượng
 * nằm ở {@code attachments} của Core. Để giao diện tự đi hỏi từng tệp là một lượt gọi API cho mỗi
 * dòng, và là <b>một chỗ thứ hai phải nhớ lọc tệp đã xoá</b> — chỗ thứ hai luôn là chỗ bị quên
 * (quy tắc 12).
 *
 * @param label tên gợi nhớ do người biên tập đặt; {@code null} = chưa đặt, nơi hiển thị rơi về
 *     {@code originalName}. ⛔ Không sinh nhãn mặc định ở đây (quy tắc 16)
 * @param originalName tên tệp lúc tải lên — <b>luôn trả kèm</b>, kể cả khi đã có nhãn: màn hình
 *     phải cho người biên tập thấy <i>tệp nào</i> đứng sau một nhãn tự đặt, nếu không thì ba dòng
 *     cùng mang chữ <i>"Xem quyết định ở đây"</i> là không truy được cái nào là cái nào
 * @param downloadable {@code false} = còn đang chờ quét virus hoặc đã bị cách ly. Màn hình hiện đúng
 *     trạng thái ấy thay vì bày ra một nút tải sẽ bị từ chối
 */
public record TaiLieuDinhKem(
        UUID publicId, String label, String originalName, String contentType, long sizeBytes, boolean downloadable) {}
