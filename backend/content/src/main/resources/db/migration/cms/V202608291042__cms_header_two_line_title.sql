-- =============================================================================
-- Đầu trang hai dòng: cơ quan chủ quản + tên Công ty (yêu cầu 29/08/2026)
--
--     UỶ BAN NHÂN DÂN THÀNH PHỐ HÀ NỘI
--     CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THUỶ LỢI SÔNG NHUỆ
--
-- ⛔⛔ CHỮ HOA NẰM TRONG GIÁ TRỊ, KHÔNG NẰM TRONG CSS
--
-- Đây là điểm quan trọng nhất của tệp này. CR-42 đã chốt: giao diện hiện NGUYÊN VĂN
-- giá trị trong `settings`, không ép `uppercase` — "ép hoa ở đây là giao diện tự
-- quyết định thay người nhập". `noForcedUppercase` canh đúng luật ấy và chỉ tha cho
-- `PortalNav`.
--
-- Nên chữ hoa phải do NGƯỜI NHẬP đặt, tức nằm ở đây, ở cột `setting_value`. Công ty
-- muốn đổi lại chữ thường thì sửa ở màn hình cấu hình, không phải sửa mã và deploy.
--
-- ⚠ VÌ SAO KHÔNG VIẾT HOA THẲNG `site.name`
--
-- `site.name` còn chạy vào `generateMetadata()` → `<title>` mặc định và `og:title`
-- (layout.tsx:25). Viết hoa nó là tab trình duyệt và thẻ chia sẻ mạng xã hội cũng hoá
-- ALL CAPS — chỗ mà chữ hoa đọc như đang hét.
--
-- ⚠ `site.header.display-name` ĐỂ RỖNG THÌ RƠI VỀ `site.name`
--
-- Hai khoá cùng chở tên Công ty là hai chỗ phải nhớ (luật 14). Giảm thiểu bằng cách
-- để khoá mới là một BẢN GHI ĐÈ có chủ đích: rỗng ⇒ đầu trang dùng `site.name`, và
-- lúc ấy chỉ còn đúng một nguồn. Nhãn ở màn hình quản trị nói rõ điều đó.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, editable, exportable, sort_order
)
VALUES
    ('site.header.parent-org',
     'UỶ BAN NHÂN DÂN THÀNH PHỐ HÀ NỘI', 'STRING', '',
     'SITE', 'Cơ quan chủ quản (dòng trên đầu trang)',
     'Hiện nguyên văn ở dòng đầu của đầu trang. Để rỗng thì đầu trang chỉ còn một dòng.',
     TRUE, TRUE, 11),

    ('site.header.display-name',
     'CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THUỶ LỢI SÔNG NHUỆ', 'STRING', '',
     'SITE', 'Tên hiển thị ở đầu trang (ghi đè)',
     'Chỉ đặt khi muốn đầu trang khác với "Tên cổng thông tin". Để RỖNG thì đầu trang '
     || 'dùng đúng site.name — và khi đó chỉ có một nguồn duy nhất cho tên Công ty. '
     || 'Giá trị hiện nguyên văn: muốn chữ hoa thì nhập chữ hoa.',
     TRUE, TRUE, 12)
ON CONFLICT (setting_key) DO NOTHING;

-- Chốt hạ bằng số đo — xem lý do ở V202608291041.
DO $$
DECLARE so INTEGER;
BEGIN
    SELECT count(*) INTO so FROM settings
     WHERE setting_key IN ('site.header.parent-org', 'site.header.display-name')
       AND editable IS TRUE AND group_code = 'SITE';
    IF so <> 2 THEN
        RAISE EXCEPTION 'Thiếu khoá đầu trang hai dòng: cần 2, đếm được %', so;
    END IF;
END $$;
