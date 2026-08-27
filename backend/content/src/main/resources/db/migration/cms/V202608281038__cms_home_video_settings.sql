-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Video phóng sự của khối "Truyền thông & Hình ảnh" — WS-25 · CR-20 · 28/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  ⚠⚠ VÌ SAO: khối đã dựng đủ nhưng KHÔNG NƠI GỌI NÀO TRUYỀN DỮ LIỆU VÀO
--
--  `HomeMediaGallery` nhận ba props (`videoId`, `videoTitle`, `photos`) và dựng khung nhúng
--  YouTube tử tế. Trang chủ gọi nó bằng `<HomeMediaGallery />` — **không props nào**. Nên
--  `videoId` luôn `undefined`, và khối luôn hiện hai ô rỗng, vĩnh viễn, ở mọi môi trường.
--
--  Đây KHÁC với "chưa có nguồn dữ liệu" (mực nước — MOD-03 chưa dựng, OI-01). Mã hiển thị đã
--  hoàn chỉnh và chạy được ngay; thứ thiếu là **một ô để Công ty nhập**. Ba props ấy là quy tắc
--  15 ở dạng React: một tham số bày ra mà không nơi gọi nào đọc.
--
--  ⭐ VÌ SAO LÀ `settings`, KHÔNG PHẢI MỘT BẢNG MỚI
--
--  Khối hiện đúng MỘT video. Dựng một entity `videos` kèm CRUD, duyệt và audit cho một giá trị
--  đơn là xây cơ chế lớn hơn nhu cầu, và mỗi cơ chế thừa là một chỗ nữa phải bảo trì. Khi Công ty
--  cần một thư viện video có phân trang thì lúc ấy mới là một bảng — và lúc ấy hình dạng nhu cầu
--  đã rõ, không phải đoán trước.
--
--  ⛔ MẶC ĐỊNH RỖNG. Không nhúng sẵn một video "mẫu": bản trước của khối này từng có một video
--     YouTube gắn tiêu đề "Phóng sự … Sông Nhuệ" hoàn toàn bịa, đã lên staging và không ai nhìn
--     ra (§10.54). Rỗng ⇒ khối nói thẳng là chưa có.
--
--  ⚠ `video-id` là MÃ video, không phải URL: cổng ghép nó vào
--    `https://www.youtube-nocookie.com/embed/{id}` — tên miền không-cookie đã mở trong CSP
--    (`frame-src`, T24.10). Dán cả `youtube.com/watch?v=…` vào đây sẽ dựng ra khung hỏng, nên
--    phần mô tả của ô nhập nói thẳng điều đó.
--
--  ⬜ Vế ẢNH của CR-20 **chưa đóng được ở lượt này**: nó cần endpoint công khai cho thư viện ảnh
--     công trình (nợ T11.30, vế còn lại) — một quyết định về phạm vi công bố, không phải một ô
--     cấu hình. Ô ảnh vẫn rỗng và nói đúng lý do của nó.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, NULL, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.home.video-id', '', 'STRING',
     'SITE', 'Mã video phóng sự trang chủ',
     'Chỉ MÃ video YouTube (phần sau v= trong địa chỉ), không phải URL đầy đủ. Bỏ trống = khối video hiện trạng thái chưa có.', 100),
    ('site.home.video-title', '', 'STRING',
     'SITE', 'Tiêu đề video phóng sự',
     'Hiện dưới khung video. Bỏ trống thì chỉ hiện video, không có dòng tiêu đề.', 101)
) AS v(k, val, vtype, grp, label, descr, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);
