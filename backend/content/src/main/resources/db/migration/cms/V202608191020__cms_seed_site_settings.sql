-- =============================================================================
-- Cấu hình giao diện cổng (WS-15 / T15.2–T15.4) — CN-01.5
--
-- Tất cả nằm ở `settings` nhóm SITE, KHÔNG có bảng `site_config`. Lý do ở đầu
-- migration V…1019.
--
-- ⚠⚠ HAI THỨ CỐ Ý KHÔNG CÓ Ở ĐÂY, và cả hai đều là quyết định chứ không phải bỏ sót
--
--  1. `site.maintenance-mode` — CN-01.5 liệt kê Maintenance Mode trong nhóm cấu
--     hình chung, nhưng khoá `system.maintenance-mode` ĐÃ có từ WS-7 và đang được
--     `MaintenanceFilter` đọc thật. Thêm khoá thứ hai là dựng hai công tắc cho một
--     bóng đèn: người vận hành gạt cái đang nhìn thấy, hệ thống nghe cái kia.
--     Màn hình cấu hình giao diện hiển thị lại đúng khoá cũ.
--
--  2. `site.hydro-widget.*` — T15.5 ghi "giữ chỗ cấu hình", nhưng widget thuỷ văn
--     cần MOD-03 (Phase 2) nên KHÔNG có dòng mã nào đọc được khoá đó. Một tham số
--     bày ra giao diện mà không ai đọc chính là lỗi đã trả giá ở WS-12: quản trị
--     viên đặt giá trị, hệ thống báo lưu thành công, và không có gì thay đổi.
--     Chỗ giữ là một khối bị khoá trên giao diện (WS-20), không phải một dòng ở đây.
--
-- Liên kết nhanh ở footer cũng không có khoá riêng: đó chính là menu vị trí
-- FOOTER ở bảng `menu_items`. Hai nơi khai cùng một danh sách thì chúng lệch nhau.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    -- === Nhận diện (T15.2) — giá trị thật chờ G13 ============================
    ('site.name', 'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ', 'STRING',
     'SITE', 'Tên cổng thông tin', 'Hiển thị ở thẻ tiêu đề trình duyệt và phần đầu trang', NULL, 10),
    ('site.slogan', '', 'STRING',
     'SITE', 'Khẩu hiệu', NULL, NULL, 20),
    -- Lưu public_id của tệp trong `attachments`, không lưu đường dẫn: đường dẫn
    -- đổi khi đổi bucket hoặc bật CDN, còn public_id thì không.
    ('site.logo.attachment-id', '', 'STRING',
     'SITE', 'Logo', 'Tải lên ở màn hình cấu hình giao diện. Nhận PNG và SVG', NULL, 30),
    ('site.favicon.attachment-id', '', 'STRING',
     'SITE', 'Favicon', 'Khuyến nghị 32×32', NULL, 40),
    ('site.color.primary', '#1677ff', 'STRING',
     'SITE', 'Màu chủ đạo', 'Mã màu HEX, VD #1677ff', NULL, 50),
    ('site.color.secondary', '#52c41a', 'STRING',
     'SITE', 'Màu phụ', NULL, NULL, 60),
    ('site.analytics.ga-tracking-id', '', 'STRING',
     'SITE', 'Google Analytics Tracking ID', 'Để trống là không nhúng mã theo dõi', NULL, 70),
    ('site.analytics.gtm-container-id', '', 'STRING',
     'SITE', 'Google Tag Manager Container ID', NULL, NULL, 80),

    -- === Footer (T15.3) ======================================================
    ('site.footer.company-info', '', 'TEXT',
     'SITE', 'Khối thông tin Công ty', 'Soạn thảo có định dạng. Hiển thị ở cột đầu của chân trang', NULL, 110),
    ('site.footer.map-embed', '', 'TEXT',
     'SITE', 'Mã nhúng bản đồ',
     'Dán mã nhúng Google Maps. Để trống là ẩn khối bản đồ', NULL, 120),
    ('site.footer.social.facebook', '', 'STRING',
     'SITE', 'Facebook', NULL, NULL, 130),
    ('site.footer.social.zalo', '', 'STRING',
     'SITE', 'Zalo', NULL, NULL, 140),
    ('site.footer.social.youtube', '', 'STRING',
     'SITE', 'YouTube', NULL, NULL, 150),
    ('site.footer.copyright', '© Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ', 'STRING',
     'SITE', 'Dòng bản quyền', NULL, NULL, 160),

    -- === Trang chủ và trang đặc biệt (T15.4) =================================
    -- JSON chứ không phải chuỗi ngăn cách dấu phẩy: thứ tự phần tử LÀ thứ tự khối
    -- trên trang, và SettingValidator kiểm được cú pháp JSON ngay lúc ghi.
    ('site.home.blocks', '["SLIDER","FEATURED","NEWS","NOTICE"]', 'JSON',
     'SITE', 'Các khối trang chủ',
     'Thứ tự trong danh sách là thứ tự hiển thị. Khối THUY_VAN cần MOD-03 (Phase 2)', NULL, 210),
    ('site.page.404.title', 'Không tìm thấy trang', 'STRING',
     'SITE', 'Tiêu đề trang 404', NULL, NULL, 220),
    ('site.page.404.message',
     'Trang bạn tìm không còn tồn tại hoặc đã được chuyển sang địa chỉ khác.', 'TEXT',
     'SITE', 'Nội dung trang 404', NULL, NULL, 230),

    -- === Tuỳ chọn trình chiếu banner (CN-01.5) ===============================
    ('site.slider.interval-seconds', '5', 'INTEGER',
     'SITE', 'Thời gian dừng mỗi ảnh (giây)', NULL, 'min=2;max=30', 310),
    ('site.slider.effect', 'SLIDE', 'STRING',
     'SITE', 'Hiệu ứng chuyển ảnh', NULL, 'in=SLIDE,FADE', 320),
    ('site.slider.autoplay', 'true', 'BOOLEAN',
     'SITE', 'Tự động chạy', NULL, NULL, 330),
    ('site.slider.show-arrows', 'true', 'BOOLEAN',
     'SITE', 'Hiện nút chuyển trái/phải', NULL, NULL, 340),
    ('site.slider.show-dots', 'true', 'BOOLEAN',
     'SITE', 'Hiện chấm chỉ vị trí', NULL, NULL, 350)
) AS v(k, val, vtype, grp, label, descr, validation, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);
