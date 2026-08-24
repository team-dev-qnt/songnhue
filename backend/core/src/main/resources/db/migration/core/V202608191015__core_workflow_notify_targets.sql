-- =============================================================================
-- Bước chuyển quy trình khai được NGƯỜI CẦN BIẾT (WS-13, vá lỗ của WS-6)
--
-- ⚠⚠ VÌ SAO PHẢI SỬA CORE Ở GIỮA WS-13.
--
-- `WorkflowEngine.notifyAfterTransition` (WS-6) truyền `entity.orgUnitId()` làm
-- đơn vị liên quan, rồi `RecipientResolver` luôn luôn cộng thêm nhóm
-- "Ban điều hành". Đó là luật của **chốt G11 — cảnh báo vận hành công trình**,
-- và nó đúng cho đúng bài toán ấy.
--
-- Bài viết là người đầu tiên đi qua cơ chế này, và lộ ra hai chỗ sai:
--
--   1. `articles` KHÔNG thuộc phạm vi đơn vị (điểm nghiệp vụ 9) → `orgUnitId()`
--      trả null → không ai được suy ra từ đơn vị.
--   2. Người nhận còn lại là **Ban điều hành**. Tức là mỗi lần một biên tập viên
--      bấm "Gửi duyệt", toàn bộ ban lãnh đạo Công ty nhận một email — trong khi
--      CN-01.1 ghi rõ phải báo cho **Quản trị nội dung**.
--
-- Cơ chế `notify_event` của WS-6 vì thế **chưa từng dùng được cho một quy trình
-- duyệt nào** — nó xanh suốt Phase 0 vì chưa có quy trình duyệt nào tồn tại.
-- Cùng một dạng với những lỗi đã gặp: cơ chế có mặt, chưa ai đi qua.
--
-- Cách vá giữ nguyên triết lý "quy trình khai bằng dữ liệu": ai ĐƯỢC LÀM đã nằm
-- ở `required_permission`, nay ai CẦN BIẾT nằm ngay cạnh, cùng một dòng.
-- =============================================================================

ALTER TABLE workflow_transitions
    ADD COLUMN notify_permission VARCHAR(100),
    ADD COLUMN notify_owner      BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN workflow_transitions.notify_permission IS
    'Báo cho mọi tài khoản đang hoạt động có quyền này. Khác hẳn required_permission: '
    'một bên là ai được bấm, một bên là ai cần biết. Gửi duyệt thì người bấm là biên tập '
    'viên còn người cần biết là quản trị nội dung — hai tập rời nhau.';

COMMENT ON COLUMN workflow_transitions.notify_owner IS
    'Báo cho chủ bản ghi (tác giả bài viết, người tạo phiếu). Dùng ở chiều phản hồi: '
    'duyệt xong hay trả về sửa thì người cần biết là người đã gửi.';

-- ⚠ Khai `notify_permission` hoặc `notify_owner` mà quên `notify_event` là thông báo
--   không bao giờ được sinh ra — engine kiểm `notify_event` trước tiên. Ràng buộc này
--   biến một lỗi seed im lặng thành lỗi lúc chạy migration.
ALTER TABLE workflow_transitions
    ADD CONSTRAINT ck_workflow_transitions_notify_target_needs_event CHECK (
        notify_event IS NOT NULL
            OR (notify_permission IS NULL AND notify_owner = FALSE)
    );
