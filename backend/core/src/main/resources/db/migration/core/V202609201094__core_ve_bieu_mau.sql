-- =============================================================================
-- T73.9 (ASVS 11.1.2) — vé biểu mẫu công khai: tuổi tối thiểu (giây).
--
-- Biểu mẫu liên hệ/góp ý xin một vé do máy chủ ký khi người dùng bắt đầu điền;
-- lượt gửi phải mang vé ấy và vé phải đủ N giây tuổi — người điền ⛔ gửi được
-- trong chưa tới ba giây, máy thì có. Giao diện tự chờ đủ tuổi rồi mới gửi.
-- Xem `VeBieuMauService`.
--
-- ⛔ Nhóm SECURITY chứ ⛔ SITE: nhóm SITE đi thẳng ra `GET /api/v1/public/site-config`
--   — con số này ⛔ bí mật, nhưng ⛔ có lý do bày nó cho máy đọc.
-- =============================================================================
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'security.form.min-fill-seconds', '3', 'INTEGER', '3',
    'SECURITY', 'Thời gian tối thiểu điền biểu mẫu công khai (giây)',
    'Lượt gửi biểu mẫu liên hệ/góp ý phải cách lúc người dùng bắt đầu điền ít nhất số giây này (chống gửi tự động); '
    'giao diện tự chờ đủ rồi mới gửi. 0 = ⛔ đòi thời gian tối thiểu, chỉ kiểm vé hợp lệ và chưa quá 24 giờ.',
    'min=0;max=60', TRUE, TRUE, 57
)
ON CONFLICT (setting_key) DO NOTHING;
