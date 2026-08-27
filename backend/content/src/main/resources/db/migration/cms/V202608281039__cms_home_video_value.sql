-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Điền giá trị cho video phóng sự trang chủ — 27/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  `V202608281038` dựng hai ô `site.home.video-*` và cố ý để RỖNG, với lý do ghi thẳng trong nó:
--  bản trước từng nhúng một video kèm tiêu đề "Phóng sự … Sông Nhuệ" **hoàn toàn bịa** và đã lên
--  staging mà không ai nhìn ra (§10.54).
--
--  Lượt này khác ở đúng một điểm: video là THẬT, do Công ty chỉ định.
--
--  ⚠⚠ VÀ TIÊU ĐỀ CŨNG PHẢI THẬT. Nó không do phía phát triển đặt — lấy nguyên văn từ oEmbed của
--     YouTube cho chính mã video này:
--
--       https://www.youtube.com/oembed?url=…v=Mb70qe84eqU&format=json
--       → title       = "Hà Nội đầu tư hơn 75.000 tỷ đồng hồi sinh sông Nhuệ"
--       → author_name = "MÔI TRƯỜNG TV"
--
--  ⭐ VÌ SAO GHI KÈM NGUỒN. Kênh "MÔI TRƯỜNG TV" **không phải** kênh của Công ty — đây là phóng sự
--     của một đài ngoài nói về sông Nhuệ, không phải phim Công ty tự sản xuất. Khối trên trang chủ
--     mang nhãn "video phóng sự", nên để trống nguồn là để người đọc mặc định hiểu sai. Dự án đã
--     có đúng tiền lệ này: 5 bài trong bộ seed 25/8 là bài sao chép nguyên văn từ báo ngoài và mỗi
--     bài ghi URL gốc ở cột `source`.
--
--  ⚠ `video-id` là MÃ, không phải URL — cổng ghép vào `youtube-nocookie.com/embed/{id}`.
--    `Mb70qe84eqU` là phần sau `v=` của `https://www.youtube.com/watch?v=Mb70qe84eqU`.
--
--  ⚠ `UPDATE`, không phải `INSERT`: hai khoá đã tồn tại từ `V202608281038`. Và chỉ ghi đè khi giá
--    trị đang RỖNG — nếu Công ty đã tự nhập video khác qua màn hình quản trị thì migration này
--    không được giẫm lên lựa chọn của họ.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

UPDATE settings
   SET setting_value = 'Mb70qe84eqU',
       updated_at    = now()
 WHERE setting_key   = 'site.home.video-id'
   AND coalesce(setting_value, '') = '';

UPDATE settings
   SET setting_value = 'Hà Nội đầu tư hơn 75.000 tỷ đồng hồi sinh sông Nhuệ (Nguồn: MÔI TRƯỜNG TV)',
       updated_at    = now()
 WHERE setting_key   = 'site.home.video-title'
   AND coalesce(setting_value, '') = '';

-- ───────────────────────────────────────────────────────────────────────────────────────────
--  Ô chỉ định thư mục ảnh cho khối thư viện trang chủ
--
--  ⛔ MẶC ĐỊNH RỖNG, cùng lý do với `site.home.video-id` ở `V202608281038`: rỗng ⇒ khối nói
--     thẳng là chưa có ảnh. Giá trị thật do bộ SEED đặt (chỉ staging), hoặc do Công ty tự chọn
--     thư mục ở màn hình quản trị. Đặt sẵn ở đây một `public_id` chỉ tồn tại trong bộ seed sẽ
--     làm production trỏ vào một thư mục KHÔNG CÓ THẬT — đúng bẫy §10.54.
-- ───────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT 'site.home.photos-folder', '', 'STRING', '',
       'SITE', 'Thư mục ảnh của thư viện trang chủ',
       'Mã (public_id) của thư mục trong Thư viện media. Bỏ trống = khối ảnh hiện trạng thái chưa có.',
       NULL, TRUE, TRUE, 102
WHERE NOT EXISTS (SELECT 1 FROM settings WHERE setting_key = 'site.home.photos-folder');
