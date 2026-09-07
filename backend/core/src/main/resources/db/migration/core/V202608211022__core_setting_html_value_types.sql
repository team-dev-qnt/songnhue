-- =============================================================================
-- Hai kiểu giá trị mới cho bảng `settings`: HTML và HTML_EMBED
--
-- Vì sao cần: có những tham số chứa HTML do người dùng soạn, và chúng được cổng
-- công khai dựng bằng `dangerouslySetInnerHTML`. Trước migration này, việc khử
-- trùng nằm ở `SiteConfigService` — tức là ở MỘT trong BA đường ghi vào bảng
-- `settings`. Hai đường còn lại (`PUT /api/v1/settings/{key}` của màn hình cấu
-- hình hệ thống, và `POST /api/v1/settings/import`) ghi thẳng chuỗi thô.
--
-- Đo thật trước khi sửa: gửi
--     <img src=x onerror="alert(document.cookie)"><script>…</script>
-- qua cả hai đường → cả hai trả 200, CSDL lưu nguyên văn, và
-- `GET /api/v1/public/site-config` trả lại nguyên văn cho mọi người dân vào xem
-- cổng. Đây là XSS lưu trữ, không phải rủi ro lý thuyết.
--
-- ⛔ Vì sao chuyển sang phân loại bằng CỘT `value_type` thay vì danh sách khoá
--    trong mã: "khoá nào chứa HTML" là thuộc tính CỦA CHÍNH DÒNG DỮ LIỆU. Để ở
--    mã thì mỗi đường ghi phải tự nhớ tra danh sách — mà quên là im lặng, đúng
--    kiểu lỗi đã trả giá ở `SvgSanitizer` (WS-15: lớp khử trùng có 9 bài kiểm
--    xanh trọn vẹn và không nằm trên đường chạy nào). Ghi vào cột thì đường ghi
--    nào cũng nhìn thấy, kể cả đường viết sau này.
--
--   HTML        — văn bản có định dạng, lọc bằng safelist nội dung
--   HTML_EMBED  — mã nhúng iframe, safelist hẹp + chặn theo tên miền
-- =============================================================================

ALTER TABLE settings DROP CONSTRAINT ck_settings_value_type;

ALTER TABLE settings ADD CONSTRAINT ck_settings_value_type CHECK (
    value_type IN ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN',
                   'JSON', 'CRON', 'TIME', 'DATE', 'DURATION',
                   'HTML', 'HTML_EMBED')
);

COMMENT ON COLUMN settings.value_type IS
    'Kiểu giá trị. HTML và HTML_EMBED buộc SettingService khử trùng trước khi ghi — '
    'đừng đặt hai kiểu này cho tham số không thật sự chứa HTML người dùng soạn.';
