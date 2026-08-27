-- =============================================================================
-- Địa chỉ trụ sở theo địa giới hành chính MỚI — CR-42
--
-- Nguồn: "YÊU CẦU CHỈNH SỬA WEBSITE" v1.0 ngày 27/08/2026, mã CR-42, ưu tiên Cao.
-- Bỏ cấp "Quận"; "New House Xala – Khu đô thị Xala – Quận Hà Đông" thành
-- "Newhouse – Phường Hà Đông".
--
-- ⚠ Ghi đúng chữ hoa/thường như Công ty gửi. Bản cũ viết HOA toàn bộ vì giao diện
--   ép `uppercase`; nay giao diện thôi ép, nên chuỗi trong CSDL là thứ người đọc
--   thấy. Cùng lúc đó `noFabricatedContent.test.ts` canh chuỗi này KHÔNG được xuất
--   hiện trong mã giao diện — đúng chỗ của nó là ở đây.
--
-- CR-44 (địa điểm công trình theo cấp xã mới) là việc nhập liệu ở `constructions`,
-- không đụng tới khoá này.
-- =============================================================================

UPDATE settings
   SET setting_value = 'Tầng 4-5 Tòa nhà Newhouse – Phường Hà Đông – Thành phố Hà Nội',
       default_value = 'Tầng 4-5 Tòa nhà Newhouse – Phường Hà Đông – Thành phố Hà Nội'
 WHERE setting_key = 'company.address';

-- CR-40 (email) và CR-41 (giờ làm việc): tài liệu yêu cầu bỏ khỏi CHÂN TRANG, không
-- yêu cầu xoá dữ liệu — và OI-04 còn đang hỏi Công ty có thay email công vụ không.
-- Nên hai khoá dưới đây giữ nguyên giá trị; việc thôi hiển thị nằm ở `SiteFooter`.
-- Ghi lại ý định ngay trong mô tả để người mở màn hình cấu hình không tưởng là hỏng.
UPDATE settings
   SET description = 'Chỉ dùng ở trang Liên hệ. Đã bỏ khỏi chân trang theo CR-40; OI-04 chờ Công ty '
                  || 'chốt bỏ hẳn hay thay bằng email công vụ.'
 WHERE setting_key = 'company.email';

UPDATE settings
   SET description = 'Chỉ dùng ở trang Liên hệ. Đã bỏ khỏi chân trang theo CR-41.'
 WHERE setting_key = 'company.working-hours';
