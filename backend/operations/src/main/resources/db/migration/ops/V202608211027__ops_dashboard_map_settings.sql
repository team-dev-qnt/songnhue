-- =============================================================================
-- Cấu hình bản đồ nền cho dashboard điều hành (WS-23 / T23.9) — CN-02.4, CN-02.5
--
-- ⛔ VÌ SAO ĐỂ Ở `settings` CHỨ KHÔNG GHI CỨNG TRONG MÃ FE
--
-- `architecture-review.md` §3 mục 9 chốt "OSM mặc định, Google Maps optional, để
-- config switch được". Ghi cứng URL tile trong mã nghĩa là đổi nguồn bản đồ phải
-- build lại cả ảnh admin-app — đúng thứ quy tắc 12 của dự án cấm.
--
-- ⚠⚠ NHƯNG ĐỔI HOST TILE THÌ PHẢI MỞ CSP — và đó là lý do dòng `description` bên
-- dưới nói thẳng điều đó cho người ngồi ở màn hình cấu hình.
--
-- Ảnh admin-app đặt `Content-Security-Policy: img-src 'self' data: blob: …`. Một
-- host tile không nằm trong danh sách đó thì trình duyệt CHẶN từng ô ảnh, và triệu
-- chứng là **bản đồ xám trơn có marker nổi lên trên** — không một lỗi nào ở tầng
-- ứng dụng, chỉ vài dòng trong console. `NginxSecurityHeadersTest` có bài đối chiếu
-- host mặc định ở đây với CSP trong `deploy/docker/admin-app.Dockerfile`; đổi giá
-- trị mặc định mà quên CSP thì CI đỏ chứ không phải người dùng phát hiện.
--
-- ⛔ Cố ý KHÔNG có khoá "bật/tắt bản đồ": một dashboard điều hành công trình thuỷ
-- lợi mà tắt được bản đồ thì công tắc đó sẽ có ngày bị gạt nhầm và không ai biết vì
-- sao màn hình trống. Muốn bỏ bản đồ là quyết định thiết kế, không phải tham số.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, 'OPERATION', v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    ('ops.map.tile-url', 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', 'STRING',
     'Nguồn ảnh bản đồ nền',
     'Mẫu URL tile theo chuẩn XYZ. ⚠ Đổi sang tên miền khác thì phải mở thêm tên miền đó '
     || 'trong chỉ thị img-src của Content-Security-Policy (deploy/docker/admin-app.Dockerfile), '
     || 'nếu không trình duyệt sẽ chặn ảnh và bản đồ hiện xám trơn.',
     'regex=^https://.+\{z\}.+\{x\}.+\{y\}.*$', 10),

    ('ops.map.tile-attribution', '© OpenStreetMap contributors', 'STRING',
     'Dòng ghi công nguồn bản đồ',
     'Điều khoản sử dụng của OpenStreetMap bắt buộc hiển thị dòng này trên bản đồ.',
     NULL, 20),

    -- Tâm mặc định: vùng Sông Nhuệ (Hà Nội). Đây chỉ là khung nhìn ban đầu khi CHƯA
    -- có công trình nào có toạ độ — có dữ liệu rồi thì bản đồ tự khớp theo các điểm
    -- thật, vì một tâm cố định sẽ sai ngay khi Công ty mở rộng địa bàn.
    ('ops.map.center-lat', '20.9800', 'DECIMAL',
     'Vĩ độ tâm bản đồ mặc định',
     'Chỉ dùng khi chưa công trình nào được số hoá toạ độ.', 'min=-90;max=90', 30),
    ('ops.map.center-lng', '105.7800', 'DECIMAL',
     'Kinh độ tâm bản đồ mặc định',
     'Chỉ dùng khi chưa công trình nào được số hoá toạ độ.', 'min=-180;max=180', 40),
    ('ops.map.default-zoom', '11', 'INTEGER',
     'Mức phóng mặc định', NULL, 'min=1;max=20', 50),
    ('ops.map.max-zoom', '18', 'INTEGER',
     'Mức phóng tối đa',
     'OpenStreetMap phục vụ tới mức 19; đặt cao hơn khả năng của nguồn thì người dùng '
     || 'phóng vào chỉ thấy ô trống.',
     'min=1;max=22', 60)
) AS v(k, val, vtype, label, descr, validation, ord);
