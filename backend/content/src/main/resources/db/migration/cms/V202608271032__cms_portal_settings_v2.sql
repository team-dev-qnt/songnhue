-- =============================================================================
-- Tham số cổng của đợt chỉnh sửa 27/08/2026 — CR-07, CR-10, CR-12, CR-37
--
-- ⛔ Luật 12: tham số vận hành nằm ở `settings` có UI sửa, KHÔNG ở `application.yml`
--    và không hard-code. Chính §2 của tài liệu yêu cầu điều này bằng lời:
--    *"Mọi tham số vận hành (chu kỳ refresh, số bài hiển thị, số ảnh slider, thời
--    gian chuyển ảnh) phải cấu hình được, không gán cứng trong mã nguồn."*
--
-- ⚠ Luật 15 nói ngược lại một nửa: *công tắc chưa ai đọc là một lỗi*. Nên mỗi khoá
--   dưới đây phải có một dòng mã đọc nó **trong cùng lượt sửa này**; khoá nào chưa
--   có nơi đọc thì không seed. Cột "đọc ở đâu" trong chú thích từng khoá là lời hứa
--   đó, và `PortalSettingsAreReadTest` là thứ giữ nó.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    -- === CR-07 · Hệ thống văn bản điều hành ==================================
    -- Đóng nợ T11.28: địa chỉ này từng ghi cứng ở BA tệp giao diện
    -- (`SiteFooter`, `PortalSidebar`, `DirectiveDocumentsSection`), nên đổi địa chỉ
    -- của khách là sửa mã nguồn và dựng lại image.
    --
    -- ⚠⚠ Địa chỉ ĐỔI so với bản cũ: `songnhue.bhh40.net` → hệ thống của Thành phố.
    --    Hai host là hai hệ thống khác nhau; `bhh40.net` vẫn là nguồn API thuỷ văn
    --    của MOD-03 và KHÔNG bị đụng tới ở đây.
    ('site.external.doc-system-url', 'https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi', 'STRING',
     'SITE', 'Địa chỉ hệ thống văn bản điều hành',
     'Nút "Văn bản điều hành" mở tab mới sang địa chỉ này. Cổng KHÔNG đồng bộ dữ liệu văn bản (CN-01.7).',
     '^https?://.+', 90),

    -- === CR-10 · Slider ảnh hoạt động ========================================
    -- `site.slider.interval-seconds` (3–5 giây) và bốn khoá `site.slider.*` khác đã
    -- có từ V202608191020. Chỉ thiếu trần số ảnh — tài liệu nói "10–20 ảnh".
    ('site.slider.max-items', '20', 'INTEGER',
     'SITE', 'Số ảnh tối đa của slider trang chủ',
     'Tài liệu Bố cục: 10–20 ảnh chạy xoay vòng. Vượt trần thì phần dư bị bỏ qua, không lỗi.',
     '^([1-9]|[1-4][0-9]|50)$', 91),

    -- === CR-12 · Khối Tin tức – Sự kiện ======================================
    ('site.home.news-count', '5', 'INTEGER',
     'SITE', 'Số bài ở khối "Tin tức – Sự kiện" trang chủ',
     'Cột bên phải banner. Tài liệu: khoảng 5 bài mới nhất.',
     '^([1-9]|1[0-9]|20)$', 92),

    -- === CR-17 · Khối "Công bố thông tin" trang chủ ==========================
    -- Khối này liệt kê bài mới nhất của một nhánh danh mục. Đặt slug ở đây thay vì viết
    -- vào mã giao diện vì cây danh mục là DỮ LIỆU: Công ty đổi tên hay tách nhánh
    -- "Công bố thông tin" là chuyện của một buổi chiều, còn sửa mã là một lượt dựng
    -- image. Slug lạ ⇒ khối nói thẳng là chưa có bài, không nổ.
    ('site.home.documents-category', 'cong-bo-thong-tin', 'STRING',
     'SITE', 'Danh mục nguồn của khối "Công bố thông tin" trang chủ',
     'Slug danh mục. Khối hiển thị các bài mới nhất thuộc nhánh này.',
     '^[a-z0-9-]+$', 94),

    ('site.home.documents-count', '6', 'INTEGER',
     'SITE', 'Số văn bản hiển thị ở khối "Công bố thông tin" trang chủ', NULL,
     '^([1-9]|1[0-9]|20)$', 95),

    -- === CR-30 · Trang Tiến độ sản xuất ======================================
    -- Trang chọn Năm → Vụ đọc cây danh mục dưới nhánh này: cấp 1 là năm, cấp 2 là vụ.
    -- Cùng lý lẽ với khoá trên — cây danh mục là dữ liệu, không phải hằng số trong mã.
    ('site.page.production-progress-category', 'tien-do-san-xuat', 'STRING',
     'SITE', 'Danh mục nguồn của trang "Tiến độ sản xuất"',
     'Slug danh mục gốc. Cấp con thứ nhất là Năm, cấp con thứ hai là Vụ (Xuân / Mùa / Đông).',
     '^[a-z0-9-]+$', 96),

    -- === CR-35, CR-37 · Khối dữ liệu thời gian thực ==========================
    -- ⚠ Đây là chu kỳ làm mới của GIAO DIỆN, không phải chu kỳ poller thuỷ văn
    --   (2 phút/lần, luật 17 — nằm ở nhóm khác và do backend giữ). Đặt nhầm hai
    --   con số này vào nhau là giao diện hỏi nhanh hơn nguồn có dữ liệu mới.
    --   OI-09 còn mở: Công ty chọn 5 / 10 / 15 phút. 300 giây là mốc tạm.
    ('site.home.realtime.refresh-seconds', '300', 'INTEGER',
     'SITE', 'Chu kỳ tự làm mới khối dữ liệu thời gian thực (giây)',
     'Áp cho khối Mực nước – lượng mưa và Vận hành công trình. 0 = tắt tự làm mới, chỉ còn nút bấm tay.',
     '^(0|[1-9][0-9]{1,3}|[1-9])$', 93)
) AS v(k, val, vtype, grp, label, descr, validation, ord)
ON CONFLICT (setting_key) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Gỡ hai công tắc không còn ánh xạ vào khối nào — luật 15
-- -----------------------------------------------------------------------------
-- Cùng một luật mà `SiteLayoutTest.khongCoCongTacWidgetThuyVan` đã ép từ WS-15:
-- *bày ra một tham số mà không dòng mã nào đọc thì quản trị viên đặt giá trị, hệ
-- thống báo lưu thành công, và không có gì thay đổi.*
--
--   • `site.home.blocks` liệt kê `SLIDER · FEATURED · NEWS · NOTICE · THUY_VAN` —
--     từ vựng có trước cây nội dung §3. `FEATURED` (bài đinh) đã bị CR-10 thay bằng
--     slider ảnh; `NOTICE` đã bị CR-01 bỏ khỏi cây nội dung. Bố cục trang chủ nay
--     bám menu, nên bớt một khối là bỏ mục tương ứng khỏi menu (§2: một hệ phân
--     loại dùng chung).
--   • `site.slider.effect` chưa từng có nơi đọc, kể cả trước đợt này. Hiệu ứng
--     chuyển ảnh hiện là mờ dần, cố định trong `HomeBannerSlider`.
--
-- ⚠ DELETE chứ không để lại: một khoá còn nằm trên màn hình cấu hình là một lời hứa.
DELETE FROM settings WHERE setting_key IN ('site.home.blocks', 'site.slider.effect');
