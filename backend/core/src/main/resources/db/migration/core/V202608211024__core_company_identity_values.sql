-- =============================================================================
-- Thông tin nhận diện Công ty: điền giá trị thật + thêm đường dây nóng
--
-- Vì sao: `SiteFooter.tsx` của cổng công khai đang GHI CỨNG địa chỉ trụ sở, số
-- điện thoại, fax, email và số đường dây nóng phòng chống thiên tai. Cùng lúc
-- đó năm khoá `company.*` seed từ V202608131009 KHÔNG có dòng mã nào đọc, và ba
-- trong năm khoá còn để trống.
--
-- Đây đúng cặp lỗi ngược nhau đã trả giá ở WS-12 với `limits.upload.max-mb.*`:
-- một bên là công tắc bày ra giao diện mà không ai đọc, một bên là giá trị thật
-- nằm cứng trong mã. Hậu quả cụ thể ở đây nặng hơn: đổi số điện thoại của một
-- doanh nghiệp nhà nước phải sửa mã nguồn và dựng lại image — trong khi đó là
-- số mà người dân gọi khi có sự cố công trình.
--
-- ⚠ Số đường dây nóng tách riêng khỏi `company.phone`: một bên là tổng đài giờ
--   hành chính, một bên là trực ban 24/7 phòng chống thiên tai. Gộp làm một thì
--   tới mùa lũ sẽ có người sửa số này và vô tình đổi luôn số kia.
--
-- Giá trị điền vào đúng bằng chuỗi đang ghi cứng trong `SiteFooter.tsx`, nên
-- cổng hiển thị KHÔNG đổi gì sau migration này — chỉ khác ở chỗ từ nay sửa được
-- trên giao diện.
-- =============================================================================

UPDATE settings
SET setting_value = 'Số 14 đường Thanh Bình, phường Mộ Lao, quận Hà Đông, TP. Hà Nội',
    default_value = 'Số 14 đường Thanh Bình, phường Mộ Lao, quận Hà Đông, TP. Hà Nội'
WHERE setting_key = 'company.address';

UPDATE settings
SET setting_value = '(024) 3382 4586',
    default_value = '(024) 3382 4586'
WHERE setting_key = 'company.phone';

UPDATE settings
SET setting_value = 'vanphong@thuyloisongnhue.vn',
    default_value = 'vanphong@thuyloisongnhue.vn'
WHERE setting_key = 'company.email';

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
) VALUES
    ('company.fax', '(024) 3382 4587', 'STRING', '(024) 3382 4587',
     'COMPANY', 'Số fax', 'Hiện ở chân trang cổng thông tin điện tử.', NULL, TRUE, TRUE, 60),
    ('company.hotline', '(024) 3382 4586', 'STRING', '(024) 3382 4586',
     'COMPANY', 'Đường dây nóng phòng chống thiên tai & TKCN',
     'Số trực ban 24/7, hiện ở dải trên cùng của chân trang. Khác số tổng đài giờ hành chính.',
     NULL, TRUE, TRUE, 70),
    ('company.working-hours', '8:00 - 17:00 (Thứ 2 đến Thứ 6)', 'STRING', '8:00 - 17:00 (Thứ 2 đến Thứ 6)',
     'COMPANY', 'Giờ làm việc', 'Hiện ở khối liên hệ của chân trang.', NULL, TRUE, TRUE, 80)
ON CONFLICT (setting_key) DO NOTHING;
