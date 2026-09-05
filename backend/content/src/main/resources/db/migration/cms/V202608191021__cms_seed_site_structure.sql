-- =============================================================================
-- Khung danh mục · trang tĩnh · menu ĐỀ XUẤT (WS-15 / T15.7 + WS-13 / T13.13)
--
-- Đây là câu trả lời tạm cho mục G14 (Công ty chưa gửi sơ đồ danh mục và menu).
-- Toàn bộ sửa được qua giao diện, không cần migration nào nữa.
--
-- ⚠ VÌ SAO CHỖ NÀY SEED, TRONG KHI V…1008 CỐ Ý KHÔNG SEED CƠ CẤU TỔ CHỨC
--
-- Không mâu thuẫn — hai loại dữ liệu chịu hậu quả khác hẳn nhau khi đoán sai:
--
--   • `org_units` là dữ liệu CHỊU TẢI: phân quyền tầng 3, hồ sơ công trình, hồ sơ
--     nhân sự đều neo vào id của nó. Đoán sai rồi sửa là phải di chuyển dữ liệu
--     đã bám vào, nên thà để trống.
--   • Danh mục và menu là dữ liệu TRÌNH BÀY. Đoán sai thì đổi tên, kéo thả lại,
--     xoá — trong vài phút, không kéo theo gì cả.
--
-- Và cái giá của việc để trống thì ngược nhau: cổng rỗng nghĩa là không có gì để
-- Công ty xem lúc nghiệm thu, tức là G14 quay lại chặn đúng lúc muộn nhất.
--
-- ⚠ BỐN TRANG TĨNH ĐƯỢC ĐẶT THẲNG Ở TRẠNG THÁI XUẤT BẢN, KHÔNG ĐI QUA QUY TRÌNH DUYỆT
--
-- Cố ý, và chỉ đúng cho dữ liệu khởi tạo. Không phải "lách quy trình": không có
-- bước chuyển giả nào được ghi vào lịch sử, `created_by` để NULL (= hệ thống) nên
-- nhật ký không hề nói rằng có người nào đó đã duyệt. Nếu để ở trạng thái Nháp thì
-- menu trỏ vào bốn địa chỉ trả 404 — đúng thứ mà việc seed này sinh ra để tránh.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Cây danh mục (2 cấp)
-- -----------------------------------------------------------------------------
INSERT INTO categories (name, slug, description, path, depth, sort_order)
VALUES
    ('Tin tức',    'tin-tuc',    'Tin hoạt động và tin chuyên ngành',       '/', 0, 10),
    ('Thông báo',  'thong-bao',  'Thông báo điều hành gửi tới cộng đồng',   '/', 0, 20),
    ('Giới thiệu', 'gioi-thieu', 'Các trang giới thiệu chung về Công ty',   '/', 0, 30);

UPDATE categories SET path = '/' || id || '/' WHERE depth = 0;

INSERT INTO categories (name, slug, parent_id, path, depth, sort_order)
SELECT x.name, x.slug, p.id, p.path, 1, x.ord
  FROM categories p
  JOIN (VALUES
            ('Tin hoạt động',    'tin-hoat-dong',    10),
            ('Tin chuyên ngành', 'tin-chuyen-nganh', 20)
       ) AS x(name, slug, ord) ON TRUE
 WHERE p.slug = 'tin-tuc';

-- Con vừa chèn mang path của cha; nối thêm id của chính nó cho đủ '/cha/con/'.
UPDATE categories SET path = path || id || '/' WHERE depth = 1;

-- -----------------------------------------------------------------------------
-- 2. Bốn trang tĩnh — G14 còn chờ nội dung thật của Công ty
-- -----------------------------------------------------------------------------
INSERT INTO articles (
    title, slug, summary, content, author_user_id, status, published_at, meta_title, meta_description
)
SELECT x.title, x.slug, x.summary,
       '<p>Nội dung đang được cập nhật.</p>',
       u.id, 'XUAT_BAN', now(), x.title, x.summary
  FROM (VALUES
            ('Giới thiệu chung',    'gioi-thieu-chung',
             'Thông tin chung về Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ'),
            ('Chức năng nhiệm vụ',  'chuc-nang-nhiem-vu',
             'Chức năng, nhiệm vụ và quyền hạn của Công ty'),
            ('Cơ cấu tổ chức',      'co-cau-to-chuc',
             'Sơ đồ tổ chức và các đơn vị trực thuộc'),
            ('Liên hệ',             'lien-he',
             'Địa chỉ, điện thoại và đầu mối liên hệ')
       ) AS x(title, slug, summary)
  CROSS JOIN users u
 WHERE u.username = 'superadmin';

-- Bản nội dung mà cổng công khai thật sự đọc. Thiếu bảng này thì bài ở trạng thái
-- Xuất bản vẫn không hiện ra — `published_version_id` mới là thứ quyết định.
INSERT INTO article_versions (article_id, version_no, title, slug, summary, content, note)
SELECT a.id, 1, a.title, a.slug, a.summary, a.content, 'Khung khởi tạo (WS-15/T15.7)'
  FROM articles a
 WHERE a.slug IN ('gioi-thieu-chung', 'chuc-nang-nhiem-vu', 'co-cau-to-chuc', 'lien-he');

UPDATE articles a
   SET published_version_id = v.id
  FROM article_versions v
 WHERE v.article_id = a.id
   AND v.version_no = 1
   AND a.slug IN ('gioi-thieu-chung', 'chuc-nang-nhiem-vu', 'co-cau-to-chuc', 'lien-he');

INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id
  FROM articles a
 CROSS JOIN categories c
 WHERE c.slug = 'gioi-thieu'
   AND a.slug IN ('gioi-thieu-chung', 'chuc-nang-nhiem-vu', 'co-cau-to-chuc', 'lien-he');

-- -----------------------------------------------------------------------------
-- 3. Menu Header — cấp 0
-- -----------------------------------------------------------------------------
-- ⚠ Ép kiểu tường minh cho mọi cột NULL. PostgreSQL suy kiểu UNION theo từng cặp
--   nhánh từ trái sang: hai nhánh đầu cùng cho NULL (kiểu `unknown`) nên nó chốt
--   thành `text`, rồi nhánh thứ ba mang `c.id` kiểu bigint và cả câu lệnh chết với
--   "UNION types text and bigint cannot be matched". Bỏ ép kiểu ở đây là migration
--   hỏng ngay lượt chạy đầu tiên.
INSERT INTO menu_items (position, label, link_type, url, category_id, article_id, path, depth, sort_order)
SELECT 'HEADER', 'Trang chủ', 'URL', '/', NULL::bigint, NULL::bigint, '/', 0, 10
UNION ALL
-- NONE = mục chỉ mở menu con. Đây là lý do link_type có giá trị đó: không có nó
-- thì "Giới thiệu ▾" buộc phải mang một đường dẫn giả.
SELECT 'HEADER', 'Giới thiệu', 'NONE', NULL::varchar, NULL::bigint, NULL::bigint, '/', 0, 20
UNION ALL
SELECT 'HEADER', 'Tin tức', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 30
  FROM categories c WHERE c.slug = 'tin-tuc'
UNION ALL
SELECT 'HEADER', 'Thông báo', 'CATEGORY', NULL::varchar, c.id, NULL::bigint, '/', 0, 40
  FROM categories c WHERE c.slug = 'thong-bao'
UNION ALL
-- CN-01.7: liên kết sang hệ thống văn bản điều hành. Ở đây chỉ là đường dẫn thường —
-- phần đăng nhập tự động bằng mã số thuộc G5 và chưa được dựng.
SELECT 'HEADER', 'Văn bản điều hành', 'EXTERNAL_DOC', 'http://songnhue.bhh40.net',
       NULL::bigint, NULL::bigint, '/', 0, 50
UNION ALL
SELECT 'HEADER', 'Liên hệ', 'ARTICLE', NULL::varchar, NULL::bigint, a.id, '/', 0, 60
  FROM articles a WHERE a.slug = 'lien-he';

UPDATE menu_items SET open_new_tab = TRUE WHERE link_type = 'EXTERNAL_DOC';
UPDATE menu_items SET path = '/' || id || '/' WHERE depth = 0;

-- Menu Header — cấp 1, nằm dưới "Giới thiệu"
INSERT INTO menu_items (position, parent_id, label, link_type, article_id, path, depth, sort_order)
SELECT 'HEADER', p.id, a.title, 'ARTICLE', a.id, p.path, 1, x.ord
  FROM menu_items p
  JOIN (VALUES
            ('gioi-thieu-chung',   10),
            ('chuc-nang-nhiem-vu', 20),
            ('co-cau-to-chuc',     30)
       ) AS x(slug, ord) ON TRUE
  JOIN articles a ON a.slug = x.slug
 WHERE p.position = 'HEADER' AND p.label = 'Giới thiệu';

UPDATE menu_items SET path = path || id || '/' WHERE depth = 1;

-- -----------------------------------------------------------------------------
-- 4. Menu Footer — "Liên kết nhanh" của CN-01.5 chính là cây này, không phải một
--    tham số riêng ở `settings`
-- -----------------------------------------------------------------------------
INSERT INTO menu_items (position, label, link_type, category_id, article_id, path, depth, sort_order)
SELECT 'FOOTER', 'Giới thiệu chung', 'ARTICLE', NULL::bigint, a.id, '/', 0, 10
  FROM articles a WHERE a.slug = 'gioi-thieu-chung'
UNION ALL
SELECT 'FOOTER', 'Tin tức', 'CATEGORY', c.id, NULL::bigint, '/', 0, 20
  FROM categories c WHERE c.slug = 'tin-tuc'
UNION ALL
SELECT 'FOOTER', 'Thông báo', 'CATEGORY', c.id, NULL::bigint, '/', 0, 30
  FROM categories c WHERE c.slug = 'thong-bao'
UNION ALL
SELECT 'FOOTER', 'Liên hệ', 'ARTICLE', NULL::bigint, a.id, '/', 0, 40
  FROM articles a WHERE a.slug = 'lien-he';

UPDATE menu_items SET path = '/' || id || '/' WHERE depth = 0 AND path = '/';
