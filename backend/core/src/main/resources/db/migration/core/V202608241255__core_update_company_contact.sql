-- =============================================================================
-- Cập nhật thông tin Công ty theo phản hồi mới nhất
-- Cập nhật địa chỉ, điện thoại, email và bổ sung các thông tin còn thiếu:
-- fax, hotline, working-hours.
-- =============================================================================

-- Cập nhật các thông tin đã có sẵn trong bảng settings
UPDATE settings 
SET setting_value = 'TẦNG 4-5 TÒA NHÀ NEW HOUSE XALA - KHU ĐÔ THỊ XALA - QUẬN HÀ ĐÔNG - THÀNH PHỐ HÀ NỘI.', 
    default_value = 'TẦNG 4-5 TÒA NHÀ NEW HOUSE XALA - KHU ĐÔ THỊ XALA - QUẬN HÀ ĐÔNG - THÀNH PHỐ HÀ NỘI.' 
WHERE setting_key = 'company.address';

UPDATE settings 
SET setting_value = '(024) 33.546.247', 
    default_value = '(024) 33.546.247' 
WHERE setting_key = 'company.phone';

UPDATE settings 
SET setting_value = 'songnhue2015@gmail.com', 
    default_value = 'songnhue2015@gmail.com' 
WHERE setting_key = 'company.email';

-- Thêm mới hoặc cập nhật các thông tin còn thiếu
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, editable, exportable, sort_order
)
VALUES
    ('company.fax', '(024) 33.540.794', 'STRING', '(024) 33.540.794', 'COMPANY', 'Fax', TRUE, TRUE, 45),
    ('company.hotline', '(024) 33.546.247', 'STRING', '(024) 33.546.247', 'COMPANY', 'Đường dây nóng', TRUE, TRUE, 46),
    ('company.working-hours', 'Thứ Hai – Thứ Sáu: 08:00 – 17:00 (Trực ban PCTT 24/24h)', 'STRING', 'Thứ Hai – Thứ Sáu: 08:00 – 17:00 (Trực ban PCTT 24/24h)', 'COMPANY', 'Giờ làm việc', TRUE, TRUE, 47)
ON CONFLICT (setting_key) DO UPDATE 
SET setting_value = EXCLUDED.setting_value, 
    default_value = EXCLUDED.default_value;
