-- =============================================================================
-- T63.4 (ASVS 5.3.3) — những khoá `settings` ĐI THẲNG VÀO `href` nay mang
-- `value_type = 'URL'`, để `SettingValidator` chặn `javascript:` NGAY LÚC GHI.
--
-- ⛔⛔ Vì sao phân loại bằng CỘT chứ ⛔ bằng `switch (key)` ở nơi gọi: đó chính là
--    quyết định `SettingService.khuTrung()` đã trả giá và ghi lại — làm theo khoá
--    thì nó chỉ đúng ở MỘT trong ba đường ghi (`update`, `updateInGroup`,
--    `importConfiguration`). Đặt luật vào *dữ liệu* thì đường ghi viết sau này
--    cũng bị ràng buộc mà ⛔ ai phải nhớ (luật 12).
--
-- ⚠ `ops.map.tile-url` CỐ Ý ⛔ nằm trong danh sách. Nó ⛔ phải một liên kết người
--   dùng bấm — nó là **mẫu ô bản đồ** Leaflet ghép toạ độ vào
--   (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`), và cặp `{}` trong đó ⛔
--   hợp lệ với phép phân tích URL nào. Ép nó vào kiểu `URL` là từ chối chính giá
--   trị đang chạy đúng — một lượt "siết bảo mật" làm hỏng bản đồ.
-- =============================================================================

-- Đo TRƯỚC khi siết: nếu một giá trị đang có mà luật mới sẽ từ chối, lượt migrate
-- phải đỏ **tại đây** kèm tên khoá — chứ ⛔ phải để người quản trị phát hiện vào
-- lần sau họ mở màn hình cấu hình và ⛔ lưu nổi một ô họ ⛔ hề đụng tới.
DO $$
DECLARE
    khoa_xau text;
BEGIN
    -- ⚠ Phải soi CẢ HAI cột. `Setting.effectiveValue()` rơi về `default_value` khi
    --   `setting_value` rỗng, nên một `default_value` mang `javascript:` cũng đi thẳng
    --   vào `href` y như vậy — và nó là cột ⛔ ai nhìn tới.
    SELECT string_agg(setting_key || '.' || cot || ' = ' || gia_tri, ', ')
      INTO khoa_xau
      FROM (
            SELECT setting_key, 'setting_value' AS cot, btrim(setting_value) AS gia_tri
              FROM settings
            UNION ALL
            SELECT setting_key, 'default_value', btrim(default_value)
              FROM settings
           ) t
     WHERE setting_key IN (
               'site.external.doc-system-url',
               'site.privacy.policy-url',
               'site.footer.social.facebook',
               'site.footer.social.youtube',
               'site.footer.social.zalo')
       AND coalesce(gia_tri, '') <> ''
       AND gia_tri !~* '^(https?://|mailto:|tel:|/[^/\\]|#)';

    IF khoa_xau IS NOT NULL THEN
        RAISE EXCEPTION
            'T63.4: giá trị đang lưu sẽ bị luật mới từ chối — sửa dữ liệu trước khi đổi kiểu: %',
            khoa_xau;
    END IF;
END $$;

-- Danh sách kiểu do CSDL ép (tiền lệ V202608211022 khi thêm HTML/HTML_EMBED) — phải
-- nới TRƯỚC lượt UPDATE, ⛔ thì `ck_settings_value_type` từ chối.
ALTER TABLE settings DROP CONSTRAINT ck_settings_value_type;

ALTER TABLE settings ADD CONSTRAINT ck_settings_value_type CHECK (
    value_type IN ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN',
                   'JSON', 'CRON', 'TIME', 'DATE', 'DURATION',
                   'HTML', 'HTML_EMBED', 'URL')
);

UPDATE settings
   SET value_type = 'URL'
 WHERE setting_key IN (
           'site.external.doc-system-url',
           'site.privacy.policy-url',
           'site.footer.social.facebook',
           'site.footer.social.youtube',
           'site.footer.social.zalo');

COMMENT ON COLUMN settings.value_type IS
    'Kiểu giá trị, quyết định cả phép hợp lệ hoá (SettingValidator) lẫn phép khử trùng '
    '(SettingService.khuTrung). URL = giá trị sẽ được đặt vào href ⇒ chỉ nhận '
    'http/https/mailto/tel, đường dẫn nội bộ một dấu gạch, hoặc neo #.';
