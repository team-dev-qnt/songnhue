-- =============================================================================
-- Cây nội dung chuẩn của Công ty — thay khung đề xuất của V202608191021
--
-- Nguồn: "YÊU CẦU CHỈNH SỬA WEBSITE" v1.0 ngày 27/08/2026 §3, mã CR-01…CR-07,
-- CR-09, CR-30, CR-31, CR-32. Tài liệu ấy **đóng mục G14** của
-- `business-open-questions.md` — khung cũ chỉ là câu trả lời tạm khi Công ty chưa
-- gửi sơ đồ.
--
-- ⚠ VÌ SAO XOÁ HẲN `menu_items` RỒI DỰNG LẠI, KHÔNG SỬA TỪNG DÒNG
--
-- Menu cũ và menu mới không có ánh xạ 1-1: "Thông báo" biến mất, "Văn bản điều
-- hành" đổi bản chất từ mục nội dung sang liên kết ra ngoài, và hai mục cấp 1 mới
-- xuất hiện. Sửa từng dòng cho một phép biến đổi như vậy là dãy UPDATE mà không ai
-- đọc lại được, còn dựng lại thì trạng thái cuối nhìn thấy ngay trong tệp này.
--
-- Toàn bộ hàng dưới đây do chính chuỗi migration sinh ra ở V202608191021, không có
-- hàng nào do người dùng tạo — cổng chưa bàn giao cho biên tập viên nào. Sau lượt
-- này thì menu sửa được hoàn toàn qua giao diện; không migration nào nữa.
--
-- ⛔ DANH MỤC thì KHÔNG xoá — chúng có bài viết bám vào (`article_categories`).
--    Danh mục cũ được đổi tên/đổi slug tại chỗ để bài viết giữ nguyên liên kết.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Danh mục cũ — đổi tên tại chỗ (CR-03)
-- -----------------------------------------------------------------------------
-- Giữ nguyên slug `tin-tuc`: nó vẫn mô tả đúng mục, ngắn hơn, và mọi bài đang
-- nằm trong đó (5 bài của bộ seed staging) không phải đụng tới.
UPDATE categories
   SET name = 'Tin tức – Sự kiện',
       description = 'Tin ngành thủy lợi và tin hoạt động của Công ty'
 WHERE slug = 'tin-tuc';

-- `tin-chuyen-nganh` = tin của UBND TP Hà Nội, Sở Nông nghiệp và Môi trường.
UPDATE categories
   SET name = 'Tin thủy lợi',
       slug = 'tin-thuy-loi',
       description = 'Tin ngành thủy lợi của UBND thành phố Hà Nội, Sở Nông nghiệp và Môi trường'
 WHERE slug = 'tin-chuyen-nganh';

UPDATE categories
   SET name = 'Tin Công ty',
       slug = 'tin-cong-ty',
       description = 'Hoạt động phục vụ sản xuất của Công ty'
 WHERE slug = 'tin-hoat-dong';

-- CR-01 bỏ "Thông báo" khỏi menu. Danh mục ẩn đi chứ KHÔNG xoá: ẩn là thao tác
-- quay lui được bằng một cú bấm, còn xoá thì không. Danh mục ẩn không xuất hiện ở
-- điều hướng của cổng (`PublicPortalService.categories()` lọc theo `visible`).
UPDATE categories SET visible = FALSE WHERE slug = 'thong-bao';

-- -----------------------------------------------------------------------------
-- 2. Danh mục mới — CR-04, CR-06, CR-30, CR-31, CR-32
-- -----------------------------------------------------------------------------
INSERT INTO categories (name, slug, description, path, depth, sort_order)
VALUES
    ('Hoạt động Đảng, đoàn thể', 'hoat-dong-dang-doan-the',
     'Công tác Đảng, Công đoàn, Đoàn Thanh niên, Hội Phụ nữ, Hội Cựu chiến binh', '/', 0, 40),
    ('Công bố thông tin', 'cong-bo-thong-tin',
     'Văn bản pháp luật và văn bản chỉ đạo, điều hành của Công ty', '/', 0, 60),
    ('Tiến độ sản xuất', 'tien-do-san-xuat',
     'Tiến độ sản xuất theo năm và theo vụ', '/', 0, 50)
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

UPDATE categories SET path = '/' || id || '/' WHERE depth = 0 AND path = '/';

-- Cấp 2 của "Công bố thông tin" (CR-06)
INSERT INTO categories (name, slug, description, parent_id, path, depth, sort_order)
SELECT x.name, x.slug, x.mota, p.id, p.path, 1, x.ord
  FROM categories p
  JOIN (VALUES
            ('Văn bản pháp luật', 'van-ban-phap-luat',
             'Văn bản quy phạm lĩnh vực Thủy lợi, Môi trường, Đất đai, Xây dựng', 10),
            ('Văn bản Công ty', 'van-ban-cong-ty',
             'Văn bản chỉ đạo, điều hành của Công ty', 20)
       ) AS x(name, slug, mota, ord) ON TRUE
 WHERE p.slug = 'cong-bo-thong-tin'
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

UPDATE categories SET path = path || id || '/' WHERE depth = 1 AND path NOT LIKE '%/' || id || '/';

-- Cấp 3 — phân nhóm văn bản (CR-31, CR-32).
--
-- ⚠ Hai nhóm đều có mục "Quyết định", mà `uq_categories_slug` là UNIQUE toàn bảng
--   chứ không unique theo cha. Nên slug phải mang hậu tố phân biệt; tên hiển thị
--   thì giữ nguyên "Quyết định" vì người đọc đã có ngữ cảnh từ mục cha.
INSERT INTO categories (name, slug, parent_id, path, depth, sort_order)
SELECT x.name, x.slug, p.id, p.path, 2, x.ord
  FROM categories p
  JOIN (VALUES
            ('van-ban-phap-luat', 'Luật',              'luat',                  10),
            ('van-ban-phap-luat', 'Nghị định',         'nghi-dinh',             20),
            ('van-ban-phap-luat', 'Thông tư, Chỉ thị', 'thong-tu-chi-thi',      30),
            ('van-ban-phap-luat', 'Quyết định',        'quyet-dinh-phap-luat',  40),
            ('van-ban-phap-luat', 'Hướng dẫn',         'huong-dan',             50),
            ('van-ban-cong-ty',   'Kế hoạch',          'ke-hoach',              10),
            ('van-ban-cong-ty',   'Thông cáo, Báo cáo', 'thong-cao-bao-cao',    20),
            ('van-ban-cong-ty',   'Quyết định',        'quyet-dinh-cong-ty',    30)
       ) AS x(cha, name, slug, ord) ON x.cha = p.slug
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

UPDATE categories SET path = path || id || '/' WHERE depth = 2 AND path NOT LIKE '%/' || id || '/';

-- -----------------------------------------------------------------------------
-- 3. Trang tĩnh — CR-23 gộp "Giới thiệu chung" thành "Tổng quan"
-- -----------------------------------------------------------------------------
-- ⚠ Đổi cả `articles.slug` lẫn `article_versions.slug`. Cổng tra bài theo
--   `articles.slug`, nhưng bản version là ảnh chụp dùng cho lịch sử — để hai bên
--   lệch nhau là gieo một câu hỏi không ai trả lời được sau sáu tháng.
UPDATE articles
   SET title = 'Tổng quan',
       slug = 'tong-quan',
       summary = 'Lịch sử hình thành, quá trình phát triển, chức năng và nhiệm vụ của Công ty',
       meta_title = 'Tổng quan'
 WHERE slug = 'gioi-thieu-chung';

UPDATE article_versions v
   SET title = 'Tổng quan',
       slug = 'tong-quan',
       summary = 'Lịch sử hình thành, quá trình phát triển, chức năng và nhiệm vụ của Công ty'
  FROM articles a
 WHERE v.article_id = a.id AND a.slug = 'tong-quan';

-- -----------------------------------------------------------------------------
-- 3-b. Ba trang tĩnh bị cây nội dung mới thay thế
-- -----------------------------------------------------------------------------
-- ⚠⚠ ĐÂY LÀ HỆ QUẢ BẮT BUỘC PHẢI XỬ LÝ, KHÔNG PHẢI DỌN DẸP CHO GỌN
--
-- Menu cũ trỏ vào bốn trang tĩnh bằng `link_type = 'ARTICLE'`. Menu mới chỉ còn
-- giữ một trong bốn ("Tổng quan"); ba trang kia bị thay bằng TRANG THẬT ở đường
-- dẫn khác:
--
--   chuc-nang-nhiem-vu → gộp vào "Tổng quan"                   (CR-23)
--   co-cau-to-chuc     → /gioi-thieu/co-cau-to-chuc, đọc org_units (CR-24)
--   lien-he            → /lien-he, đọc settings + bản đồ        (CR-22)
--
-- Để nguyên thì có ba hậu quả, không cái nào tự báo:
--
--   1. Ba bài thành MỒ CÔI — vào được bằng địa chỉ trực tiếp nhưng không lối nào
--      dẫn tới, và nội dung của chúng vẫn là "Nội dung đang được cập nhật".
--   2. `/bai-viet/lien-he` và `/lien-he` cùng phục vụ "trang liên hệ" ở hai địa
--      chỉ — nội dung trùng lặp, và bản rỗng là bản có thứ hạng.
--   3. ⛔ Bộ seed staging `V202608251100` XOÁ CỨNG *mọi bài không có menu nào trỏ
--      tới*. Vị từ ấy được viết khi cả bốn trang đều có mục menu; sau lượt này nó
--      sẽ nuốt ba bài mà không ai thấy.
--
-- ⚠ CHỈ xoá mềm bản CHƯA AI ĐỘNG TỚI: `created_by IS NULL` (do migration tạo) và
--   `updated_at IS NULL` (chưa qua màn hình soạn thảo lần nào). Biên tập viên đã
--   viết nội dung thật vào đó thì bài ở lại — nội dung của khách không phải thứ
--   một migration được quyền quyết định thay họ.
UPDATE articles
   SET deleted_at = now()
 WHERE slug IN ('chuc-nang-nhiem-vu', 'co-cau-to-chuc', 'lien-he')
   AND created_by IS NULL
   AND updated_at IS NULL;

-- -----------------------------------------------------------------------------
-- 4. Menu HEADER — CR-01…CR-07
-- -----------------------------------------------------------------------------
-- Xoá con trước cha: `fk_menu_items_parent_same_position` không có ON DELETE.
DELETE FROM menu_items WHERE depth > 0;
DELETE FROM menu_items;

INSERT INTO menu_items (position, label, link_type, url, category_id, article_id, path, depth, sort_order)
SELECT 'HEADER', 'Trang chủ', 'URL', '/', NULL::bigint, NULL::bigint, '/', 0, 10
UNION ALL
SELECT 'HEADER', 'Giới thiệu', 'NONE', NULL::varchar, NULL::bigint, NULL::bigint, '/', 0, 20
UNION ALL
SELECT 'HEADER', 'Tin tức – Sự kiện', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 30
  FROM categories c WHERE c.slug = 'tin-tuc'
UNION ALL
SELECT 'HEADER', 'Hoạt động Đảng, đoàn thể', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 40
  FROM categories c WHERE c.slug = 'hoat-dong-dang-doan-the'
UNION ALL
SELECT 'HEADER', 'Quản lý, vận hành', 'NONE', NULL::varchar, NULL::bigint, NULL::bigint, '/', 0, 50
UNION ALL
SELECT 'HEADER', 'Công bố thông tin', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 60
  FROM categories c WHERE c.slug = 'cong-bo-thong-tin'
UNION ALL
SELECT 'HEADER', 'Liên hệ', 'URL', '/lien-he', NULL::bigint, NULL::bigint, '/', 0, 70
UNION ALL
-- CR-07: KHÔNG dựng module văn bản điều hành nội bộ. Mục này chỉ là một cánh cửa
-- mở tab mới sang hệ thống riêng của Thành phố. `EXTERNAL_DOC` tồn tại đúng để
-- phân biệt nó với một liên kết ngoài thông thường (CN-01.7).
--
-- ⚠ Địa chỉ ở đây phải khớp `settings['site.external.doc-system-url']` — cùng một
--   sự thật nằm ở hai bảng. `PortalDocSystemUrlTest` canh cho khỏi lệch (luật 14).
SELECT 'HEADER', 'Văn bản điều hành', 'EXTERNAL_DOC',
       'https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi',
       NULL::bigint, NULL::bigint, '/', 0, 80;

UPDATE menu_items SET open_new_tab = TRUE WHERE link_type = 'EXTERNAL_DOC';
UPDATE menu_items SET path = '/' || id || '/' WHERE depth = 0;

-- Cấp 2 — CR-02 (Giới thiệu) và CR-05 (Quản lý, vận hành)
--
-- ⚠ Bốn mục của "Quản lý, vận hành" và ba mục trang của "Giới thiệu" là `URL` trỏ
--   vào tuyến đường do chính cổng Next định nghĩa, KHÔNG phải bài viết. Đó là lý do
--   phải dựng đủ 7 trang ấy trong cùng lượt sửa này: một mục menu trỏ vào 404 là
--   đúng hình dạng lỗi §10.54 (cổng quảng cáo khu vực bấm vào là không có).
INSERT INTO menu_items (position, parent_id, label, link_type, url, category_id, article_id, path, depth, sort_order)
SELECT 'HEADER', p.id, 'Tổng quan', 'ARTICLE', NULL::varchar, NULL::bigint, a.id, p.path, 1, 10
  FROM menu_items p JOIN articles a ON a.slug = 'tong-quan'
 WHERE p.position = 'HEADER' AND p.label = 'Giới thiệu'
UNION ALL
SELECT 'HEADER', p.id, x.label, 'URL', x.url, NULL::bigint, NULL::bigint, p.path, 1, x.ord
  FROM menu_items p
  JOIN (VALUES
            ('Cơ cấu tổ chức',        '/gioi-thieu/co-cau-to-chuc', 20),
            ('Lãnh đạo Công ty',      '/gioi-thieu/lanh-dao',       30),
            ('Xí nghiệp trực thuộc',  '/gioi-thieu/xi-nghiep',      40)
       ) AS x(label, url, ord) ON TRUE
 WHERE p.position = 'HEADER' AND p.label = 'Giới thiệu'
UNION ALL
SELECT 'HEADER', p.id, x.label, 'URL', x.url, NULL::bigint, NULL::bigint, p.path, 1, x.ord
  FROM menu_items p
  JOIN (VALUES
            ('Danh mục công trình',  '/quan-ly-van-hanh/danh-muc-cong-trinh', 10),
            ('Tiến độ sản xuất',     '/quan-ly-van-hanh/tien-do-san-xuat',    20),
            ('Mực nước, lượng mưa',  '/quan-ly-van-hanh/muc-nuoc-luong-mua',  30),
            ('Vận hành công trình',  '/quan-ly-van-hanh/van-hanh-cong-trinh', 40)
       ) AS x(label, url, ord) ON TRUE
 WHERE p.position = 'HEADER' AND p.label = 'Quản lý, vận hành'
UNION ALL
SELECT 'HEADER', p.id, c.name, 'CATEGORY', NULL::varchar, c.id, NULL::bigint, p.path, 1, x.ord
  FROM menu_items p
  JOIN (VALUES ('van-ban-phap-luat', 10), ('van-ban-cong-ty', 20)) AS x(slug, ord) ON TRUE
  JOIN categories c ON c.slug = x.slug
 WHERE p.position = 'HEADER' AND p.label = 'Công bố thông tin'
UNION ALL
SELECT 'HEADER', p.id, c.name, 'CATEGORY', NULL::varchar, c.id, NULL::bigint, p.path, 1, x.ord
  FROM menu_items p
  JOIN (VALUES ('tin-thuy-loi', 10), ('tin-cong-ty', 20)) AS x(slug, ord) ON TRUE
  JOIN categories c ON c.slug = x.slug
 WHERE p.position = 'HEADER' AND p.label = 'Tin tức – Sự kiện';

UPDATE menu_items SET path = path || id || '/' WHERE depth = 1;

-- -----------------------------------------------------------------------------
-- 5. Menu FOOTER — CR-09: chân trang dùng ĐÚNG hệ phân loại của menu chính
-- -----------------------------------------------------------------------------
-- Bảy mục cấp 1, không cấp con: chân trang không có chỗ cho menu thả xuống, và
-- điều CR-09 đòi là *cùng một hệ phân loại*, không phải cùng một độ sâu.
INSERT INTO menu_items (position, label, link_type, url, category_id, article_id, path, depth, sort_order)
SELECT 'FOOTER', 'Giới thiệu', 'ARTICLE', NULL::varchar, NULL::bigint, a.id, '/', 0, 10
  FROM articles a WHERE a.slug = 'tong-quan'
UNION ALL
SELECT 'FOOTER', 'Tin tức – Sự kiện', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 20
  FROM categories c WHERE c.slug = 'tin-tuc'
UNION ALL
SELECT 'FOOTER', 'Hoạt động Đảng, đoàn thể', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 30
  FROM categories c WHERE c.slug = 'hoat-dong-dang-doan-the'
UNION ALL
SELECT 'FOOTER', 'Quản lý, vận hành', 'URL', '/quan-ly-van-hanh/danh-muc-cong-trinh',
       NULL::bigint, NULL::bigint, '/', 0, 40
UNION ALL
SELECT 'FOOTER', 'Công bố thông tin', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 50
  FROM categories c WHERE c.slug = 'cong-bo-thong-tin'
UNION ALL
SELECT 'FOOTER', 'Liên hệ', 'URL', '/lien-he', NULL::bigint, NULL::bigint, '/', 0, 60
UNION ALL
SELECT 'FOOTER', 'Văn bản điều hành', 'EXTERNAL_DOC',
       'https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi',
       NULL::bigint, NULL::bigint, '/', 0, 70;

UPDATE menu_items SET open_new_tab = TRUE WHERE link_type = 'EXTERNAL_DOC';
UPDATE menu_items SET path = '/' || id || '/' WHERE position = 'FOOTER' AND depth = 0;
