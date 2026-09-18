-- T28.53 — "Góp ý & đánh giá" thành MENU CON của "Liên hệ" (chốt với QuanTran 08/09/2026).
--
-- ⛔⛔ VÌ SAO CẦN MIGRATION NÀY
--
-- Trang `/gop-y` đã tồn tại từ WS-36 (`frontend/public-web/src/app/gop-y/page.tsx`), có endpoint
-- thật, có kiểm duyệt ở phía quản trị, có thống kê. Thứ nó ⛔ KHÔNG có là **một lối vào từ menu**.
-- Suốt từ lúc dựng, cách duy nhất tới được nó là một liên kết nằm trong thân trang `/lien-he` —
-- và `PortalTaxonomyTest.LOI_VAO_NGOAI_MENU` phải khai một dòng MIỄN TRỪ để bộ canh
-- "mọi tuyến đường đều có lối vào" khỏi đỏ.
--
-- Một dòng miễn trừ ⛔ không phải một lối vào. Người dân vào cổng tìm chỗ góp ý sẽ mở menu, ⛔ không
-- đọc hết thân trang Liên hệ.
--
-- ⚠ VÌ SAO LÀ MENU CON, ⛔ KHÔNG PHẢI MỤC CẤP 1
--
-- WS-25 đo được thanh điều hướng **tràn 1454/1192px ở MỌI bề rộng màn hình** (`flex-wrap` che đi).
-- Thêm một mục cấp 1 thứ tám là làm tệ đi đúng chỗ ấy. "Góp ý" cũng thuộc về "Liên hệ" về nghiệp
-- vụ — cả hai đều là đường người dân nói với Công ty.
--
-- ⚠ "Liên hệ" là `link_type = 'URL'` (trỏ `/lien-he`) và VẪN nhận được menu con: thanh điều hướng
--   hỗ trợ mục vừa có URL vừa có mục con. Đây là điểm khác `Quản lý, vận hành` (`NONE` — một nút
--   ⛔ không hành vi, chính là khuyết tật WS-25 đã vá).
--
-- ⛔ CHỈ HEADER, ⛔ không FOOTER: chân trang đã có mục "Liên hệ" riêng ở `sort_order = 60`, và
--    chân trang ⛔ không dựng cấp 2 (mọi mục FOOTER đều `depth = 0`). Thêm một dòng depth-1 vào đó
--    là dựng một mục ⛔ không giao diện nào vẽ ra.

INSERT INTO menu_items (position, parent_id, label, link_type, url, category_id, article_id, path, depth, sort_order)
SELECT 'HEADER', p.id, 'Góp ý & đánh giá', 'URL', '/gop-y', NULL::bigint, NULL::bigint, p.path, 1, 10
  FROM menu_items p
 WHERE p.position = 'HEADER'
   AND p.label = 'Liên hệ'
   AND p.depth = 0
   AND p.deleted_at IS NULL
   -- ⛔ Chạy lại phải AN TOÀN. Migration này ⛔ không có khoá duy nhất nào chặn trùng
   --   (`menu_items` cố ý cho phép hai mục cùng nhãn ở hai nhánh khác nhau), nên điều kiện
   --   "chưa có" phải viết ra ở đây. Thiếu nó, một lượt `flyway repair` + chạy lại cho ra HAI
   --   mục "Góp ý & đánh giá" nằm cạnh nhau, và ⛔ không lỗi nào báo.
   AND NOT EXISTS (
           SELECT 1 FROM menu_items c
            WHERE c.parent_id = p.id AND c.url = '/gop-y' AND c.deleted_at IS NULL);

-- ⛔⛔ BƯỚC HAI BẮT BUỘC: mục con phải NỐI ID CỦA CHÍNH NÓ vào `path`.
--
-- Câu INSERT trên đặt `path = p.path`, tức TRÙNG path của cha. Menu đọc bằng
-- `findByPositionAndDeletedAtIsNullOrderByPathAscSortOrderAsc` — sắp theo `path` rồi `sort_order`
-- — nên hai dòng cùng path sẽ so bằng `sort_order`, và mục con (`10`) đứng TRƯỚC cha "Liên hệ"
-- (`70`). `buildMenuTree` ở public-web duyệt **một lượt**: gặp con trước cha thì con ⛔ KHÔNG
-- được gắn vào đâu cả, và mục "Góp ý & đánh giá" biến mất khỏi thanh điều hướng.
--
-- ⚠ Triệu chứng là một mục menu VẮNG MẶT — ⛔ không lỗi nào, ⛔ không dòng log nào. Đây đúng thứ
--   `SiteLayoutTest.pathCuaMenuSeedDung()` canh, và nó đã bắt được bản đầu của tệp này.
--
-- ⚠ Khuôn lấy từ `V202608271031:237` (`UPDATE menu_items SET path = path || id || '/' WHERE
--   depth = 1`) — nhưng ⛔ KHÔNG chép nguyên câu ấy: nó đụng MỌI dòng depth-1 và sẽ nối id lần
--   thứ hai vào những dòng đã đúng. Điều kiện `NOT LIKE` giữ cho câu này chạy lại được.
UPDATE menu_items
   SET path = path || id || '/'
 WHERE depth = 1
   AND url = '/gop-y'
   AND deleted_at IS NULL
   AND path NOT LIKE '%/' || id || '/';
