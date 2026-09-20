-- =============================================================================
-- T68.10 — phép năm theo thâm niên: Điều 113 (cơ sở) + Điều 114 BLLĐ 2019.
--
-- Điều 114: "Cứ đủ 05 năm làm việc cho một người sử dụng lao động thì số ngày
-- nghỉ hằng năm ... được tăng thêm tương ứng 01 ngày." Ba khoá cũ chia 3 bậc
-- (dưới 5 · 5–10 · trên 10 = 12/13/14, seed V202608131009, nhãn "Điều 113") ⇒
-- đủ 10 năm được 13 (luật: 14), đủ 15 năm kẹt ở 14 (luật: 15), đủ 20 năm vẫn 14
-- (luật: 16). Mô hình 3 bậc ⛔ biểu diễn được luật ⇒ thay bằng ba tham số:
--
--     phép năm = cơ sở + (thâm niên ÷ số năm mỗi bậc) × số ngày mỗi bậc
--     (÷ là chia lấy phần nguyên — "cứ ĐỦ 5 năm")
--
-- ⛔ Giữ lựa chọn của người vận hành: CƠ SỞ lấy giá trị ĐANG CÓ của khoá "dưới 5
--   năm" (bản ghi mặc định 12).
-- ⛔⛔ Hai khoá bậc trên mà đã bị sửa khỏi dạng (cơ sở + 1, cơ sở + 2) ⇒ DỪNG.
--   Ánh xạ một chính sách tuỳ biến sang mô hình mới là quyết định nhân sự, ⛔ để
--   migration đoán — cùng tiền lệ V202609181085 (RAISE khi dữ liệu lệch điều
--   migration giả định). Lượt deploy dừng ở bước migrate, bản cũ vẫn phục vụ.
-- =============================================================================
DO $$
DECLARE
    duoi5  integer := (SELECT NULLIF(trim(setting_value), '')::integer
                         FROM settings WHERE setting_key = 'hr.leave.annual-days.under-5-years');
    tu5    integer := (SELECT NULLIF(trim(setting_value), '')::integer
                         FROM settings WHERE setting_key = 'hr.leave.annual-days.5-to-10-years');
    tren10 integer := (SELECT NULLIF(trim(setting_value), '')::integer
                         FROM settings WHERE setting_key = 'hr.leave.annual-days.over-10-years');
BEGIN
    IF duoi5 IS NOT NULL AND (tu5 IS DISTINCT FROM duoi5 + 1 OR tren10 IS DISTINCT FROM duoi5 + 2) THEN
        RAISE EXCEPTION 'T68.10: ba khoá phép năm đã bị sửa khỏi dạng cơ sở/+1/+2 (dưới 5 = %, 5–10 = %, trên 10 = %) — ánh xạ sang mô hình Điều 114 cần người quyết (architecture-review §12.10)',
            duoi5, tu5, tren10;
    END IF;
END $$;

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES
    ('hr.leave.annual-days.base', '12', 'INTEGER', '12',
     'HR', 'Phép năm — số ngày cơ sở',
     'Điều 113 khoản 1 BLLĐ 2019: 12 ngày với công việc trong điều kiện bình thường. '
     'Phép năm = cơ sở + (thâm niên ÷ số năm mỗi bậc) × số ngày mỗi bậc.',
     'min=0;max=60', TRUE, TRUE, 10),
    ('hr.leave.annual-days.seniority-step-years', '5', 'INTEGER', '5',
     'HR', 'Phép năm — số năm làm việc mỗi bậc thâm niên',
     'Điều 114 BLLĐ 2019: cứ đủ 05 năm làm việc cho một người sử dụng lao động.',
     'min=1;max=10', TRUE, TRUE, 20),
    ('hr.leave.annual-days.seniority-step-days', '1', 'INTEGER', '1',
     'HR', 'Phép năm — số ngày cộng thêm mỗi bậc thâm niên',
     'Điều 114 BLLĐ 2019: tăng thêm tương ứng 01 ngày cho mỗi bậc.',
     'min=0;max=10', TRUE, TRUE, 30)
ON CONFLICT (setting_key) DO NOTHING;

-- Giữ cơ sở mà người vận hành đang dùng (nếu có và khác mặc định).
UPDATE settings
   SET setting_value = (SELECT NULLIF(trim(setting_value), '')
                          FROM settings WHERE setting_key = 'hr.leave.annual-days.under-5-years')
 WHERE setting_key = 'hr.leave.annual-days.base'
   AND (SELECT NULLIF(trim(setting_value), '')
          FROM settings WHERE setting_key = 'hr.leave.annual-days.under-5-years') IS NOT NULL;

-- ⛔ Luật 15: khoá ⛔ ai đọc là một lỗi — ba khoá 3 bậc ra đi cùng lượt nơi đọc chúng ra đi.
DELETE FROM settings WHERE setting_key IN (
    'hr.leave.annual-days.under-5-years',
    'hr.leave.annual-days.5-to-10-years',
    'hr.leave.annual-days.over-10-years'
);
