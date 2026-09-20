-- =============================================================================
-- T57.16 — năm đầu tiên hệ ghi nhận ĐỦ đơn nghỉ của cả năm.
--
-- Số phép chuyển sang năm Y+1 = phần phép năm Y ⛔ dùng hết (kẹp trần
-- hr.leave.carry-over-max-days). Nó suy được từ dữ liệu của hệ CHỈ KHI hệ ghi
-- nhận đủ mọi đơn của năm Y. Hệ lên production giữa năm 2026: đơn trên giấy tháng
-- 01–09/2026 ⛔ nằm trong hệ.
--
-- Bản cũ đoán "đủ" bằng "người ấy có ≥ 1 đơn năm trước" — sai cả hai chiều:
--   · có một đơn 2026 ⇒ coi như biết cả năm ⇒ CẤP THỪA tới trần chuyển năm;
--   · năm đã ghi đủ mà một người ⛔ nghỉ ngày nào ⇒ coi như chưa biết ⇒ MẤT số chuyển.
--
-- ⇒ Một tham số tường minh, mặc định 2027 (năm đầu tiên hệ chạy trọn). Công ty
--   nhập bù đủ đơn năm 2026 thì đổi về 2026. Đường nhập số dư đầu kỳ 2026→2027
--   vẫn là câu hỏi nghiệp vụ (G16-a) — ⛔ dựng trên phỏng đoán.
-- =============================================================================
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'hr.leave.first-fully-recorded-year', '2027', 'INTEGER', '2027',
    'HR', 'Năm đầu tiên hệ ghi nhận đủ đơn nghỉ phép',
    'Số phép chuyển sang năm sau chỉ được tính từ dữ liệu của hệ khi năm trước ≥ năm này; trước đó hệ ⛔ biết '
    'số phép tồn và màn hình nói "chưa có dữ liệu năm trước". Hệ bắt đầu dùng giữa năm 2026 nên đơn trên giấy '
    'tháng 01–09/2026 ⛔ nằm trong hệ — nhập bù đủ năm 2026 thì đổi về 2026.',
    'min=2000;max=2100', TRUE, TRUE, 85
)
ON CONFLICT (setting_key) DO NOTHING;
