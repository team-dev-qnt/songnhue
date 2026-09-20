-- =============================================================================
-- T73.8 (ASVS 2.3.1) — mật khẩu tạm có HẠN.
--
-- Hai đường đặt mật khẩu tạm (quản trị tạo tài khoản · quản trị đặt lại mật
-- khẩu) bật `must_change_password` mà ⛔ có hạn ⇒ một mật khẩu tạm chưa ai dùng
-- sống mãi — trong tin nhắn, trên giấy nhớ, trong hộp thư của người đã nghỉ.
--
-- ⛔ Suy hạn từ `password_changed_at` là KHOÁ NGƯỢC mọi tài khoản đang giữ mật
--   khẩu tạm cũ ngay lượt deploy. Cột riêng: bản ghi cũ NULL = ⛔ hạn (như trước
--   T73.8), và đổi số giờ trong `settings` sau này ⛔ đổi hạn của mật khẩu đã phát.
-- ⛔ Bootstrap `superadmin` (`AdminBootstrapRunner`) để NULL: hết hạn ở đó thì
--   ⛔ còn ai đặt lại được.
-- =============================================================================
ALTER TABLE users ADD COLUMN temp_password_expires_at timestamptz;

COMMENT ON COLUMN users.temp_password_expires_at IS
    'Hạn của mật khẩu tạm do quản trị phát (T73.8, ASVS 2.3.1). NULL = ⛔ hạn: mật khẩu thường, bootstrap, hoặc bản ghi trước T73.8.';

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'security.password.temp-ttl-hours', '72', 'INTEGER', '72',
    'SECURITY', 'Hạn dùng mật khẩu tạm (giờ)',
    'Mật khẩu tạm do quản trị viên đặt (tạo tài khoản · đặt lại mật khẩu) hết hiệu lực sau số giờ này nếu người dùng '
    'chưa đăng nhập để đổi; khi ấy phải nhờ quản trị viên đặt lại. Đổi số ở đây chỉ áp cho mật khẩu tạm phát SAU lúc đổi.',
    'min=1;max=720', TRUE, TRUE, 55
)
ON CONFLICT (setting_key) DO NOTHING;
