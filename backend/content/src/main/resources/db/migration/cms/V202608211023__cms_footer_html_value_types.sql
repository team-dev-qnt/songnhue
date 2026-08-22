-- =============================================================================
-- Hai tham số chân trang chuyển sang kiểu HTML / HTML_EMBED
--
-- `site.footer.company-info` và `site.footer.map-embed` là hai giá trị duy nhất
-- trong bảng `settings` được cổng công khai dựng bằng `dangerouslySetInnerHTML`
-- (xem `SiteFooter.tsx`). Khai đúng kiểu ở đây là thứ khiến `SettingService`
-- khử trùng chúng ở MỌI đường ghi — chi tiết ở V202608211022.
--
-- ⚠ Dòng UPDATE giá trị bên dưới là bắt buộc, không phải cho gọn: nếu hệ thống
--   đã chạy và ai đó đã lưu HTML thô vào hai khoá này trước khi có bản vá, thì
--   đổi kiểu thôi chưa dọn được thứ đang nằm sẵn trong CSDL. Chạy lại qua bộ
--   lọc thì không làm được trong SQL, nên ở đây chọn cách an toàn nhất: xoá
--   giá trị nếu nó chứa dấu hiệu mã chạy được. Mất một khối chân trang thì soạn
--   lại mất hai phút; để lại một `<script>` thì mọi người dân vào cổng đều chạy nó.
-- =============================================================================

UPDATE settings
SET value_type = 'HTML'
WHERE setting_key = 'site.footer.company-info';

UPDATE settings
SET value_type = 'HTML_EMBED'
WHERE setting_key = 'site.footer.map-embed';

UPDATE settings
SET setting_value = NULL
WHERE setting_key IN ('site.footer.company-info', 'site.footer.map-embed')
  AND setting_value IS NOT NULL
  AND (setting_value ILIKE '%<script%'
       OR setting_value ILIKE '%javascript:%'
       OR setting_value ~* 'on[a-z]+[[:space:]]*=');
