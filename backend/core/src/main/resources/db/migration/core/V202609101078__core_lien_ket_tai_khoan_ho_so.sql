-- =============================================================================
-- T51.8 · CN-05.1 — LIÊN KẾT TÀI KHOẢN ↔ HỒ SƠ CBNV
--
-- Cột `users.employee_id` ra đời cùng lược đồ định danh (`V202608131002:86`) rồi
-- nằm im 28 ngày với **0 đường ghi trong toàn kho** — đo 10/09/2026. Nó là nửa
-- đọc của một cặp đọc–ghi chưa có nửa ghi, đúng hình dạng luật 27 đã trả giá sáu
-- lần. Migration này ⛔ không tạo cột (cột đã có); nó đặt **ràng buộc** mà đường
-- ghi sắp mở ra bắt buộc phải có.
--
-- ⛔⛔ VÌ SAO PHẢI DUY NHẤT — và vì sao chỉ mục cũ KHÔNG đủ
--
-- `ix_users_employee_id` (`V202608131002:111`) là chỉ mục **thường**: nó tăng tốc
-- tra cứu, ⛔ không cấm gì. Hai tài khoản cùng trỏ một hồ sơ là hai con người
-- cùng khai mình *là* một nhân viên — mà vế thứ hai của CN-04.7 (*"chính nhân
-- viên đó xem được trường 🔒 của mình"*) suy quyền đọc CCCD/lương/số tài khoản
-- **thẳng từ cột này**. ⇒ Trùng ở đây ⛔ không phải dữ liệu bẩn, nó là một lượt
-- lộ dữ liệu cá nhân, và nó im lặng tuyệt đối: cả hai màn hình đều hiện đúng.
--
-- ⚠ `deleted_at IS NULL` nằm trong vị từ vì tài khoản **xoá mềm** vẫn giữ nguyên
--   hàng. Thiếu vế ấy thì xoá một tài khoản là **khoá vĩnh viễn** hồ sơ nhân
--   viên đó — người kế nhiệm ⛔ không bao giờ liên kết lại được, và thông báo lỗi
--   sẽ trỏ vào một tài khoản ⛔ không còn hiện trên màn hình nào.
--
-- ⭐ An toàn để áp ngay: cột đang **toàn NULL** (0 đường ghi), nên chỉ mục duy
--   nhất ⛔ không thể vấp dữ liệu sẵn có.
-- =============================================================================

CREATE UNIQUE INDEX uq_users_employee_id
    ON users (employee_id)
    WHERE employee_id IS NOT NULL AND deleted_at IS NULL;

COMMENT ON COLUMN users.employee_id IS
    'Trỏ sang hr.employees — KHÔNG có FK, ràng buộc ở tầng service (ranh giới module). '
    'DUY NHẤT trong số tài khoản còn sống: cột này là căn cứ cho quyền tự đọc trường 🔒 (CN-04.7).';
