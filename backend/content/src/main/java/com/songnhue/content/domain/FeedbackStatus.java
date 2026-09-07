package com.songnhue.content.domain;

/**
 * Trạng thái kiểm duyệt một phản hồi từ cổng — CN-01.6, chốt <b>D1</b>.
 *
 * <h2>⭐ {@link #TU_CHOI} và {@link #AN} là HAI trạng thái, ⛔ không phải một</h2>
 *
 * <p>{@code TU_CHOI} = <b>chưa từng</b> hiện trên cổng. {@code AN} = <b>đã</b> hiện rồi bị gỡ
 * xuống. Gộp lại thì lịch sử ⛔ không trả lời được đúng câu hỏi người ta sẽ hỏi lúc có khiếu nại:
 * <i>"nội dung ấy đã từng công khai chưa?"</i> — và đó là câu hỏi mà một bảng dữ liệu công khai
 * bắt buộc phải trả lời được.
 *
 * <p>⛔ Đây <b>không</b> phải một máy trạng thái tự quản. Bước chuyển nằm trong
 * {@code workflow_transitions} và chỉ đi qua Workflow engine (quy tắc 4) — ⛔ đừng thêm một
 * phương thức {@code chuyen()} vào đây.
 */
public enum FeedbackStatus {
    /** Trạng thái ban đầu của <b>mọi</b> mục gửi lên — chốt D1: kiểm duyệt 100%. */
    CHO_DUYET,
    /** Đang hiện trên cổng. */
    DA_DUYET,
    /** Bị từ chối, chưa từng công khai. */
    TU_CHOI,
    /** Đã công khai rồi bị gỡ xuống. */
    AN,
}
