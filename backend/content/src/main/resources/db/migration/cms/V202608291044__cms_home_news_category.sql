-- =============================================================================
-- Hàng CHUYÊN MỤC TIN của trang chủ — hai khoá `settings`, đợt bố cục 29/08/2026.
--
-- ⭐ VÌ SAO PHẢI CÓ KHOÁ NÀY, THAY VÌ VIẾT 'tin-tuc' VÀO MÃ GIAO DIỆN
--
-- Trước lượt này `page.tsx` truyền thẳng `categorySlug="tin-tuc"` và `tieuDe="Tin tức – Sự
-- kiện"` — hai chuỗi viết cứng cho một thứ vốn là DỮ LIỆU. Công ty đổi tên nhánh danh mục
-- (chuyện của một buổi chiều trên màn hình quản trị) là khối trang chủ trỏ vào một slug đã
-- chết, và không có lỗi nào để ai nhìn thấy: khối chỉ lặng lẽ nói "chưa có bài viết nào".
--
-- Cùng đúng lý lẽ đã dùng cho `site.home.documents-category` ở V202608271032 (quy tắc 12).
--
-- ⛔ KHÔNG seed tham số cho tính năng chưa dựng (quy tắc 15). Cả hai khoá dưới đây có nơi đọc
--    NGAY trong lượt này — `frontend/public-web/src/app/page.tsx`. `PortalSettingsReadTest`
--    đọc cả thư mục migration này rồi đối chiếu với mã cổng, nên một khoá không ai đọc là
--    một bài kiểm đỏ chứ không phải một việc để dành.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    -- Nhánh danh mục nuôi CẢ hai khối tin của trang chủ: cột "Tin tức – Sự kiện" cạnh slider
    -- và hàng chuyên mục ngay dưới nó. Nhãn hiển thị KHÔNG nằm ở đây — nó lấy từ nhãn mục
    -- menu tương ứng, để thanh điều hướng và trang chủ không thể gọi cùng một nhánh bằng hai
    -- cái tên khác nhau (luật 14).
    ('site.home.news-category', 'tin-tuc', 'STRING',
     'SITE', 'Danh mục nguồn của khối tin trang chủ',
     'Slug nhánh danh mục. Các mục menu con của nhánh này dựng thành hàng chuyên mục dưới slider.',
     '^[a-z0-9-]+$', 97),

    ('site.home.category-news-count', '4', 'INTEGER',
     'SITE', 'Số bài mỗi chuyên mục ở hàng chuyên mục trang chủ',
     'Áp cho từng ô chuyên mục con. Ô nào chưa có bài thì nói thẳng là chưa có, không mượn bài của ô khác.',
     '^([1-9]|1[0-2])$', 98)
) AS v(k, val, vtype, grp, label, descr, validation, ord)
ON CONFLICT (setting_key) DO NOTHING;

-- ⚠ Khẳng định ĐẾM ĐƯỢC, không phải một lời hứa trong chú thích. `ON CONFLICT DO NOTHING` ở
--   trên nuốt mọi va chạm trong im lặng — không có khối này thì một khoá trùng tên đã tồn tại
--   với giá trị khác sẽ đi lọt, và migration vẫn xanh (đúng hình dạng §10.66: 0 hàng, 0 log).
DO $$
DECLARE
    so_khoa INT;
BEGIN
    SELECT count(*) INTO so_khoa
      FROM settings
     WHERE setting_key IN ('site.home.news-category', 'site.home.category-news-count');

    IF so_khoa <> 2 THEN
        RAISE EXCEPTION 'V202608291044: cần đúng 2 khoá site.home.* mới, đếm được %', so_khoa;
    END IF;
END $$;
