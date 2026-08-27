-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Vị trí menu thứ ba: LIEN_KET — dải "Liên kết Cổng TTĐT" ở cuối trang chủ
--  WS-25 · CR-21 · 28/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  ⚠⚠ VÌ SAO: bốn liên kết ấy đang VIẾT CỨNG trong mã giao diện
--
--  `AffiliatedUnitsLinks.tsx` giữ một hằng số `EXTERNAL_PORTALS` gồm bốn cơ quan (Bộ NN&PTNT,
--  UBND TP Hà Nội, Sở Nông nghiệp và Môi trường, Cục Thuỷ lợi) kèm địa chỉ. CR-21 yêu cầu
--  *"rà soát lại tên và đường link chính thức"* — mà rà soát xong thì Công ty **không có cách
--  nào sửa**: đổi một cái tên là sửa mã nguồn, dựng lại image, và đề bạt qua ba chặng.
--
--  Đây đúng cái nợ đã trả cho địa chỉ hệ thống văn bản điều hành (T11.28 / T24.3), cho số điện
--  thoại và địa chỉ Công ty (§10.54), và cho `site.external.doc-system-url`. Cùng một hình
--  dạng, cùng một cách chữa: đưa dữ liệu ra khỏi mã.
--
--  ⭐ VÌ SAO LÀ MỘT VỊ TRÍ MENU, KHÔNG PHẢI MỘT KHOÁ `settings`
--
--  Đây là một DANH SÁCH có thứ tự, mỗi phần tử có nhãn + đích + cờ mở tab mới — đúng hình dạng
--  `menu_items` đã phục vụ. Nhét nó vào một khoá `settings` nghĩa là chôn JSON trong một cột
--  văn bản: không validate được, không sắp xếp được trên giao diện, và phải dựng một màn hình
--  soạn thảo thứ hai cho thứ đã có sẵn một màn hình.
--
--  Javadoc của `MenuPosition` chốt điều kiện để thêm vị trí: *"thêm một vị trí thứ ba là phải
--  có chỗ trên giao diện cổng để hiển thị nó"*. Chỗ ấy tồn tại từ trước — dải "Liên kết Cổng
--  TTĐT" ở cuối trang chủ. Điều kiện đã đủ.
--
--  ⛔ VÌ SAO SEED BỐN DÒNG NÀY, TRONG KHI DỰ ÁN CẤM SEED DỮ LIỆU "CHO ĐẸP DEMO"
--
--  Vì đây không phải dữ liệu bịa ra: bốn liên kết ấy ĐANG chạy trên staging, đã qua rà soát
--  CR-21 đợt trước (tên "Sở Nông nghiệp và Môi trường" là bản đã sửa theo tài liệu). Migration
--  này **chuyển chỗ** một dữ liệu đang có, không tạo mới. Không chuyển thì đợt vá này làm dải
--  liên kết biến mất khỏi trang chủ — một hồi quy có thật để đổi lấy sự sạch sẽ trên giấy.
--
--  ⚠ Hai dòng Bộ NN&PTNT và Cục Thuỷ lợi vẫn CHỜ Công ty xác nhận tên gọi hiện hành (CR-21).
--    Khác biệt sau hôm nay: xác nhận xong thì sửa bằng một cú bấm, không phải một lượt deploy.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE menu_items DROP CONSTRAINT ck_menu_items_position;

ALTER TABLE menu_items
    ADD CONSTRAINT ck_menu_items_position CHECK (position IN ('HEADER', 'FOOTER', 'LIEN_KET'));

COMMENT ON CONSTRAINT ck_menu_items_position ON menu_items IS
    'Ba cây menu độc lập. LIEN_KET = dải liên kết cổng TTĐT cơ quan cấp trên ở cuối trang chủ '
    '(CR-21); trước 28/08/2026 bốn mục này viết cứng trong mã giao diện.';

INSERT INTO menu_items (position, label, link_type, url, category_id, article_id, path, depth, sort_order, open_new_tab)
SELECT 'LIEN_KET', x.label, 'URL', x.url, NULL::bigint, NULL::bigint, '/', 0, x.ord, TRUE
  FROM (VALUES
        ('Bộ Nông nghiệp & PTNT',            'https://www.mard.gov.vn',        10),
        ('UBND Thành phố Hà Nội',            'https://hanoi.gov.vn',           20),
        ('Sở Nông nghiệp và Môi trường Hà Nội', 'https://sonnptnt.hanoi.gov.vn', 30),
        ('Cục Thủy lợi',                     'http://cucthuyloi.gov.vn',       40)
       ) AS x(label, url, ord)
 -- Chạy lại được: môi trường nào đã có dòng cùng nhãn thì bỏ qua, không dựng bản sao.
 WHERE NOT EXISTS (
        SELECT 1 FROM menu_items m
         WHERE m.position = 'LIEN_KET' AND m.label = x.label AND m.deleted_at IS NULL);
