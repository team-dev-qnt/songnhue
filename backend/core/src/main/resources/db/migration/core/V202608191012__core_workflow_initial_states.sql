-- =============================================================================
-- Quy trình duyệt: cho phép NHIỀU trạng thái khởi tạo (WS-12 / T12.5)
--
-- Vì sao cần. CN-02.2 nói bản ghi sửa chữa có hai đường vào đời khác hẳn nhau:
-- ghi nhận một sự cố ĐANG xảy ra thì bắt đầu ở `MOI`; nhập một công việc đã
-- làm xong từ tuần trước thì bắt đầu thẳng ở `DA_XU_LY`. Nhưng
-- `workflow_definitions.initial_state` chỉ có đúng MỘT giá trị.
--
-- ⛔ Cách lách hiển nhiên — tạo ở `MOI` rồi chạy transition cho tới `DA_XU_LY`
--    — bị cấm. Nó ghi vào nhật ký kiểm toán một chuỗi sự việc CHƯA TỪNG XẢY RA:
--    "20:14 tiếp nhận, 20:14 bắt đầu xử lý, 20:14 hoàn thành" cho một việc làm
--    xong tuần trước. Mà nhật ký này có hash chain, tức là hệ thống đang ký tên
--    bảo đảm cho một lịch sử bịa. Không đáng để tiết kiệm một bảng.
--
-- Cách làm: dùng lại chính `workflow_transitions` với một trạng thái-giả
-- `__NEW__` ở vế `from_state`. Một dòng (`__NEW__`, action) → to_state là một
-- "đường vào đời" hợp lệ.
--
-- Được gì khi dùng lại bảng cũ thay vì thêm bảng mới:
--   · `required_permission` có sẵn → hạn chế ai được tạo thẳng ở trạng thái nào
--   · `label` + `sort_order` có sẵn → giao diện dựng ô chọn từ API, không tự suy
--   · một cơ chế duy nhất để đọc hiểu, thay vì hai bảng gần giống nhau
--
-- `initial_state` của definition GIỮ NGUYÊN vai trò: đường vào mặc định khi nơi
-- gọi không chỉ định gì. Quy trình chỉ có một đường vào thì không phải khai gì
-- thêm — đúng như 100% quy trình hiện có.
-- =============================================================================

-- Trạng thái-giả chỉ được đứng ở vế `from_state`. Vào được `__NEW__` nghĩa là
-- có bản ghi thật mang trạng thái đó, và từ đó nó nhận được cả những bước
-- chuyển vốn chỉ dành cho lúc tạo mới. Chặn ở tầng CSDL vì đây là dữ liệu seed
-- bằng migration — sai thì sai lúc triển khai, không phải lúc chạy test.
ALTER TABLE workflow_transitions
    ADD CONSTRAINT ck_workflow_transitions_to_state_not_sentinel
        CHECK (to_state <> '__NEW__');

ALTER TABLE workflow_definitions
    ADD CONSTRAINT ck_workflow_definitions_initial_state_not_sentinel
        CHECK (initial_state <> '__NEW__');

COMMENT ON COLUMN workflow_transitions.from_state IS
    'Trạng thái nguồn. Giá trị đặc biệt ''__NEW__'' = bản ghi chưa tồn tại: '
    'dòng đó khai một trạng thái khởi tạo hợp lệ, không phải một bước chuyển. '
    'Xem WorkflowPort.CREATION_STATE (WS-12/T12.5)';

COMMENT ON COLUMN workflow_definitions.initial_state IS
    'Trạng thái khởi tạo MẶC ĐỊNH — dùng khi nơi gọi không chỉ định. Các đường '
    'vào khác khai bằng dòng workflow_transitions có from_state = ''__NEW__''';
