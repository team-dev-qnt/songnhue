-- =============================================================================
-- Bước chuyển khai được "PHẢI KÈM LÝ DO" (vá lỗ chặn của WS-19)
--
-- ⚠⚠ LỖI ĐANG SỬA: trả bài về sửa là thao tác KHÔNG DÙNG ĐƯỢC.
--
-- `ArticleController.transition` ép buộc lý do cho `REQUEST_CHANGES` bằng một
-- dòng khai cứng:
--
--     if ("REQUEST_CHANGES".equals(request.action()) && blank(request.reason()))
--         throw new ValidationException(SYS-0003, "reason", …);
--
-- Còn giao diện chỉ mở ô nhập lý do khi hành động mang cờ `requiresReason` — mà
-- record `AllowedAction` của backend là `(action, label, toState)`, **không có
-- cờ đó và không nơi nào điền nó**. Hệ quả đo được: người duyệt bấm "Yêu cầu
-- chỉnh sửa" → không có ô nào để nhập → gửi lên thiếu `reason` → nhận đúng lỗi
-- "Phải nêu lý do khi yêu cầu chỉnh sửa" → **không có đường nào đi tiếp**.
--
-- Đây là hai bản sao của cùng một luật, đặt ở hai nơi, và chúng đã lệch nhau —
-- đúng khuôn CLAUDE.md luật 14. Cách chữa là bỏ bản sao, để luật nằm ở DỮ LIỆU
-- (cùng chỗ với `required_permission` và `notify_*`), rồi engine vừa QUẢNG CÁO
-- cờ này cho giao diện vừa ÉP BUỘC nó khi chuyển trạng thái — luật 12: đặt bảo
-- đảm ở chỗ dữ liệu đi qua, không ở nơi gọi.
--
-- Thêm một bước chuyển đòi lý do về sau = một dòng UPDATE, không phải một lượt
-- deploy (luật 16).
-- =============================================================================

ALTER TABLE workflow_transitions
    ADD COLUMN requires_reason BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN workflow_transitions.requires_reason IS
    'Bước chuyển này bắt buộc kèm lý do. Engine ép buộc lúc execute() và trả cờ ra cho '
    'giao diện trong AllowedAction, nên hai bên không thể lệch nhau: cùng đọc một dòng. '
    'Mặc định FALSE — chỉ bật cho những bước mà người nhận cần biết VÌ SAO.';

-- Trả bài về sửa mà không nói vì sao thì người viết chỉ biết là "bị từ chối", và vòng
-- lặp sửa/gửi lại chạy vài lượt trước khi hai bên hiểu nhau. Đây chính là luật mà
-- `ArticleController` đang khai cứng — nay chuyển về đúng chỗ của nó.
UPDATE workflow_transitions SET requires_reason = TRUE WHERE action = 'REQUEST_CHANGES';
