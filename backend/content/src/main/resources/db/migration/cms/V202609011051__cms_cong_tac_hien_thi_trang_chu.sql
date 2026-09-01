-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Hai công tắc hiển thị của trang chủ — WS-39, yêu cầu QuanTran 01/09/2026 (đợt hai)
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  [1] `site.home.show-dieu-hanh`      bật/tắt Nhóm 2 "Điều hành & số liệu công trình"
--  [2] `site.home.lien-ket.show-label` bật/tắt phần CHỮ của dải "Liên kết website"
--
--  ⚠⚠ VÌ SAO ĐÂY KHÔNG PHẢI `site.home.blocks` QUAY LẠI
--
--  Khoá `site.home.blocks` bị gỡ ngày 27/08 (`V202608271032`) cùng một chính sách viết ở javadoc
--  `app/page.tsx`: *bố cục trang chủ LÀ cây nội dung Công ty đã duyệt; muốn bớt một khối thì bỏ
--  mục tương ứng khỏi menu*. Chính sách ấy vẫn đúng và vẫn là MẶC ĐỊNH — `SiteLayoutTest` và
--  `PortalSettingsReadTest` vẫn canh cho khoá cũ không sống lại.
--
--  Nhưng nó chỉ áp được cho khối nào CÓ một mục menu để gỡ. Nhóm 2 không có: "Mực nước, lượng
--  mưa" và "Vận hành công trình" trên trang chủ là hai khối tóm tắt, còn mục menu tương ứng trỏ
--  sang hai TRANG riêng dưới nhánh `/quan-ly-van-hanh`. Gỡ mục menu ấy là gỡ mất cả hai trang chi
--  tiết — tức cái nút duy nhất có sẵn làm nhiều hơn hẳn thứ người dùng muốn. Đây là cùng dạng
--  ngoại lệ mà bộ khoá `site.slider.*` đang dùng: chỉnh CÁCH TRÌNH BÀY của một khối, không chỉnh
--  việc khối ấy có thuộc cây nội dung hay không.
--
--  ⛔ MỘT công tắc cho cả Nhóm 2, không phải hai. Tách riêng Mực nước / Vận hành là dựng thêm
--     một cột không ai yêu cầu, và quy tắc 15 tính nó là một lỗi chứ không phải một tính năng
--     để dành.
--
--  ⭐ KHÔNG cần một dòng mã admin-app nào: `SiteConfigTab.tsx` dựng `<Switch>` cho MỌI khoá có
--     `value_type = 'BOOLEAN'`. Đổi lại, tên khoá phải tránh hậu tố `.attachment-id` (nó lái sang
--     ô tải ảnh) và tránh các chuỗi `address` / `info` / `map` (chúng lái sang ô TextArea).
--
--  ⚠ Mặc định 'true' cho cả hai: đây là bản vá thêm một công tắc, không phải một lượt đổi giao
--    diện. Deploy xong mà cổng khác đi là một thay đổi không ai yêu cầu.
--
--  ⚠ `validation` để NULL — với BOOLEAN thì `SettingValidator.requireBoolean` đã là ràng buộc
--    (và nó cố ý từ chối 'yes' / '1' / '0', vì `Boolean.parseBoolean` nhận mọi thứ rồi trả false
--    trong im lặng). Cùng khuôn với ba khoá `site.slider.*` ở `V202608191020`.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, NULL, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.home.show-dieu-hanh', 'true', 'BOOLEAN',
     'SITE', 'Hiện khối "Điều hành & số liệu công trình" trên trang chủ',
     'Tắt thì cả nhãn nhóm lẫn hai bảng Mực nước và Vận hành công trình đều ẩn khỏi trang chủ. Hai trang chi tiết dưới mục Quản lý vận hành KHÔNG bị ảnh hưởng.', 102),
    ('site.home.lien-ket.show-label', 'true', 'BOOLEAN',
     'SITE', 'Hiện tên cơ quan dưới logo ở dải "Liên kết website"',
     'Tắt thì mỗi ô chỉ còn ảnh logo phủ kín khung, tên cơ quan vẫn giữ cho trình đọc màn hình. Mục CHƯA tải logo luôn hiện tên, bất kể công tắc này.', 103)
) AS v(k, val, vtype, grp, label, descr, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);

-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  ⛔ ĐẾM LẠI THỨ VỪA GHI — `WHERE NOT EXISTS` nuốt trượt trong im lặng và Flyway vẫn báo thành
--     công (§10.66: một bộ seed từng ghi 0 hàng vào một khoá chưa tồn tại, không một dòng log).
-- ═══════════════════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_khoa int;
BEGIN
    SELECT count(*) INTO so_khoa FROM settings
     WHERE setting_key IN ('site.home.show-dieu-hanh', 'site.home.lien-ket.show-label')
       AND group_code = 'SITE'
       AND value_type = 'BOOLEAN'
       AND editable IS TRUE
       AND setting_value IN ('true', 'false');
    IF so_khoa <> 2 THEN
        RAISE EXCEPTION 'Cần đúng 2 công tắc BOOLEAN nhóm SITE, đếm được % — công tắc không có mặt thì màn hình quản trị không dựng ra ô nào để bấm', so_khoa;
    END IF;
END $$;
