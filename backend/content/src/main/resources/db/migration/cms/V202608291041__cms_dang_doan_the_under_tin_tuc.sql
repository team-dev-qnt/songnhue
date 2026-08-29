-- =============================================================================
-- "Hoạt động Đảng, đoàn thể" chuyển thành mục CON của "Tin tức – Sự kiện"
--
-- Yêu cầu 29/08/2026. Cây nội dung §3 xếp nó là mục cấp 1 thứ tư; đo trên thanh
-- điều hướng thì đó là nhãn DÀI NHẤT (24 ký tự) trong tám mục, và nội dung của nó
-- vốn là tin — cùng loại với "Tin thủy lợi" và "Tin Công ty".
--
-- ⚠ VÌ SAO PHẢI LÀ MIGRATION, KHÔNG PHẢI SỬA GIAO DIỆN
--
-- Thanh điều hướng KHÔNG viết trong mã: `SiteHeader` gọi `getMenu('HEADER')` rồi
-- `buildMenuTree`. Đổi thứ bậc bằng cách sửa component là dựng một cây thứ hai
-- cạnh cây trong CSDL — hai nơi trả lời cùng một câu hỏi, đúng hình dạng luật 14.
-- Sau lượt này Công ty vẫn kéo-thả lại được ở màn hình quản trị.
--
-- ⚠ CHUYỂN CẢ DANH MỤC, KHÔNG CHỈ MỤC MENU
--
-- `menu_items` và `categories` là hai cây khác nhau. Chuyển mỗi mục menu thì thanh
-- điều hướng nói "nằm trong Tin tức" còn trang `/danh-muc/hoat-dong-dang-doan-the`
-- vẫn nói "mục gốc" — hai câu trả lời khác nhau cho cùng một câu hỏi. Slug KHÔNG
-- đổi nên mọi địa chỉ đã phát hành vẫn sống.
--
-- ⛔ KHÔNG xoá, KHÔNG tạo mới: chỉ đổi chỗ. Bài viết đang gắn vào danh mục
--    (`article_categories`) giữ nguyên liên kết.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Mục menu — HEADER
-- -----------------------------------------------------------------------------
-- ⚠⚠ QUY ƯỚC PATH: con = path của cha NỐI thêm id của chính nó — `/16/` → `/16/17/`.
--
-- Bản đầu của migration này viết `path = p.path`, tức con mang ĐÚNG path của cha. Đọc
-- `V202608271031` thì tưởng vậy (khối INSERT cấp 2 dùng `p.path`), nhưng đó chưa phải
-- trạng thái cuối — có một lượt UPDATE sau đó nối id con vào. Đo trên CSDL thật mới thấy:
-- mọi mục cấp 2 đang có path dạng `/15/22/`.
--
-- Hậu quả của bản sai KHÔNG phải là "thứ tự sai" mà là **thứ tự không xác định**:
-- `MenuService.tree()` sắp theo `ORDER BY path ASC, sort_order ASC`, nên con và cha cùng
-- path `/16/` và cùng `sort_order` 30 thành một cặp hoà — Postgres trả thứ tự nào cũng
-- được. `buildMenuTree` ở public-web duyệt MỘT lượt, gặp con trước cha là con rơi ra
-- ngoài cây. Hai lượt chạy cho hai kết quả khác nhau; `SiteLayoutTest.pathCuaMenuSeedDung`
-- bắt được.
UPDATE menu_items c
   SET parent_id  = p.id,
       depth      = 1,
       path       = p.path || c.id || '/',
       -- ⚠⚠ 5 chứ không phải 30, và lý do là một KHIẾM KHUYẾT ĐO ĐƯỢC của lược đồ hiện tại.
       --
       -- `MenuService.tree()` sắp `ORDER BY path ASC, sort_order ASC`. Mọi mục cùng một cha có
       -- path KHÁC nhau (mỗi mục nối id của chính nó), nên `path` quyết định xong trước khi
       -- `sort_order` được nhìn tới — tức **`sort_order` không có tác dụng giữa các mục cùng
       -- cấp**, thứ tự thật là thứ tự id.
       --
       -- Các nhánh cũ không lộ ra vì id của chúng tăng đúng theo thứ tự mong muốn (22,23,24,25).
       -- Mục này có id nhỏ hơn hai anh em (nó vốn là mục cấp 1 tạo sớm hơn) nên nó hiện ĐẦU
       -- danh sách dù đặt sort_order bao nhiêu.
       --
       -- Đặt 5 để giá trị lưu KHỚP với thứ tự thật sự hiển thị. Đặt 30 thì CSDL nói "cuối" mà
       -- màn hình cho ra "đầu", và người sửa menu lần sau sẽ đuổi theo một con ma.
       -- ⛔ Khiếm khuyết gốc (một cái núm không điều khiển được gì) ghi thành T26.25.
       sort_order = 5,
       updated_at = now()
  FROM menu_items p
 WHERE c.position = 'HEADER'
   AND c.label    = 'Hoạt động Đảng, đoàn thể'
   -- ⚠⚠ KHÔNG lọc `c.depth = 0`.
   --
   -- Lọc theo trạng thái đầu vào làm migration chỉ chạy đúng từ ĐÚNG MỘT trạng thái. Đo
   -- được trên CSDL dev 29/08: bản đầu của tệp này đã áp và để lại mục ở depth = 1 với
   -- path SAI; lượt áp lại của bản đã sửa khớp 0 hàng, path vẫn sai, và khối kiểm ở cuối
   -- ném lỗi — tức migration tự chặn chính nó.
   --
   -- Viết theo kiểu HỘI TỤ: bất kể mục đang ở đâu, sau lượt này nó nằm đúng chỗ. Đây cũng
   -- là hình dạng an toàn hơn cho một migration nói chung — nó chạy được trên cả CSDL đã
   -- chạy bản nháp lẫn CSDL rỗng.
   AND c.deleted_at IS NULL
   AND p.position = 'HEADER'
   AND p.label    = 'Tin tức – Sự kiện'
   AND p.depth    = 0
   AND p.deleted_at IS NULL;

-- Bảy mục cấp 1 còn lại: đánh số lại 10..70 để không còn khoảng trống ở vị trí 40.
-- Khoảng trống không sai, nhưng màn hình quản trị hiện số thứ tự và một dãy nhảy cóc
-- luôn khiến người sửa tưởng mình xoá nhầm.
WITH danh_sach AS (
    SELECT id, row_number() OVER (ORDER BY sort_order, id) * 10 AS thu_tu
      FROM menu_items
     WHERE position = 'HEADER' AND depth = 0 AND deleted_at IS NULL
)
UPDATE menu_items m
   SET sort_order = d.thu_tu,
       updated_at = now()
  FROM danh_sach d
 WHERE m.id = d.id AND m.sort_order <> d.thu_tu;

-- -----------------------------------------------------------------------------
-- 2. Danh mục nội dung
-- -----------------------------------------------------------------------------
-- Quy ước path của `categories`: con = path của cha NỐI thêm id của chính nó
-- (xem V202608271031, khối "Cấp 2 của Công bố thông tin").
UPDATE categories c
   SET parent_id   = p.id,
       depth       = 1,
       path        = p.path || c.id || '/',
       sort_order  = 30,
       updated_at  = now()
  FROM categories p
  WHERE c.slug = 'hoat-dong-dang-doan-the'
    AND c.deleted_at IS NULL
    AND p.slug = 'tin-tuc'
    AND p.deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- 3. Chốt hạ bằng SỐ ĐO, không bằng lời hứa
-- -----------------------------------------------------------------------------
-- Migration này chạy trên CSDL ĐÃ CÓ DỮ LIỆU, nên hai lệnh trên có thể khớp 0 hàng
-- mà Flyway vẫn báo thành công — đúng lỗi §10.66 (seed ghi vào khoá chưa tồn tại,
-- 0 hàng, không một dòng log). Kiểm ngay tại đây và DỪNG nếu sai.
DO $$
DECLARE
    so_menu INTEGER;
    so_cat  INTEGER;
    so_cap1 INTEGER;
BEGIN
    SELECT count(*) INTO so_menu
      FROM menu_items c JOIN menu_items p ON p.id = c.parent_id
     WHERE c.position = 'HEADER' AND c.label = 'Hoạt động Đảng, đoàn thể'
       AND c.depth = 1 AND p.label = 'Tin tức – Sự kiện' AND c.deleted_at IS NULL;

    SELECT count(*) INTO so_cat
      FROM categories c JOIN categories p ON p.id = c.parent_id
     WHERE c.slug = 'hoat-dong-dang-doan-the' AND c.depth = 1
       AND p.slug = 'tin-tuc' AND c.deleted_at IS NULL;

    SELECT count(*) INTO so_cap1
      FROM menu_items
     WHERE position = 'HEADER' AND depth = 0 AND deleted_at IS NULL;

    IF so_menu <> 1 THEN
        RAISE EXCEPTION 'Mục menu "Hoạt động Đảng, đoàn thể" không nằm dưới "Tin tức – Sự kiện" (đếm được %)', so_menu;
    END IF;
    IF so_cat <> 1 THEN
        RAISE EXCEPTION 'Danh mục hoat-dong-dang-doan-the không nằm dưới tin-tuc (đếm được %)', so_cat;
    END IF;
    IF so_cap1 <> 7 THEN
        RAISE EXCEPTION 'HEADER phải còn 7 mục cấp 1 sau lượt chuyển, đếm được %', so_cap1;
    END IF;

    -- ⭐ Chốt luôn HÌNH DẠNG path, không chỉ chốt quan hệ cha-con. Đây đúng là chỗ bản đầu
    --    sai: quan hệ cha-con đã đúng mà thứ tự vẫn hỏng, vì path con trùng path cha.
    SELECT count(*) INTO so_menu
      FROM menu_items c JOIN menu_items p ON p.id = c.parent_id
     WHERE c.label = 'Hoạt động Đảng, đoàn thể' AND c.position = 'HEADER'
       AND c.deleted_at IS NULL
       AND c.path = p.path || c.id || '/';
    IF so_menu <> 1 THEN
        RAISE EXCEPTION 'path của mục con phải là path cha nối id con — nếu không, thứ tự menu là không xác định';
    END IF;
END $$;
