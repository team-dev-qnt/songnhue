-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Logo cho dải "Liên kết website" + ảnh cho khối "Bản đồ hệ thống công trình" — 29/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Hai ô trên trang chủ mà Công ty **không có cách nào nhập**, và cả hai đã bị ghi lại là nợ:
--
--    • **Liên kết website** (CR-21) — bốn cơ quan cấp trên nay nằm trong `menu_items` vị trí
--      `LIEN_KET`, sửa được nhãn và đường dẫn ở màn hình Menu. Nhưng thẻ chỉ mang CHỮ: bảng
--      không có cột ảnh nào, nên lượt rà 29/08 đã ghi thẳng vào sổ *"không có chỗ để đặt logo"*
--      (T26.60) thay vì dựng một ô ảnh chờ tệp.
--    • **Bản đồ hệ thống công trình** (CN-02.4) — khối này vẽ bằng Leaflet từ toạ độ trong danh
--      mục công trình, mà toạ độ thuộc **G8** và Công ty chưa cung cấp. Nên ô ấy rỗng, và sẽ
--      còn rỗng cho tới khi có G8 — trong khi Công ty đã có sẵn ảnh sơ đồ hệ thống để treo lên.
--
--  ⭐ VÌ SAO HAI CÁCH LÀM KHÁC NHAU CHO HAI Ô CÙNG LÀ "TẢI ẢNH LÊN"
--
--  Ranh giới đã có sẵn ở `V202608191019`, và lượt này đi theo đúng nó:
--
--      Có nhiều dòng, người dùng thêm/bớt/sắp xếp  →  BẢNG   (cột trên `menu_items`)
--      Đúng một giá trị cho cả hệ thống            →  settings
--
--  Mỗi cổng TTĐT là một dòng của một danh sách có thứ tự ⇒ cột. Ảnh sơ đồ hệ thống thì cả cổng
--  chỉ có một ⇒ khoá `settings`, và nó dùng lại nguyên cơ chế đã chạy cho logo/favicon.
--
--  ⛔⛔ SỐ HIỆU PHẢI LỚN HƠN `V202608291046` — ĐÓ LÀ RÀNG BUỘC, KHÔNG PHẢI SỞ THÍCH
--
--     `V202608291046` là migration SEED và nó **đã áp trên staging** (PR #59 merge 29/08). Đánh
--     số tệp này là …1042 (số còn trống sau đợt gộp) thì nó rơi XUỐNG DƯỚI bản staging đã áp, và
--     Flyway `validate` chặn luôn lượt deploy: *"Detected resolved migration not applied to
--     database"*. Đúng §10.66, đã làm đỏ CD hai lần.
--
--     Bộ canh: `backend/tools/kiem-thu-tu-migration.sh` (bước 2/10 của `make ci-local` + job CI).
-- ═══════════════════════════════════════════════════════════════════════════════════════════

-- ── [1] Logo của từng mục menu ─────────────────────────────────────────────────────────────
--
-- ⚠ KHOÁ NGOẠI TỚI `attachments(public_id)`, không phải một cột UUID trơn. Cùng cách `banners`
--   và `articles.cover_attachment_public_id` làm: CSDL từ chối một id không tồn tại, thay vì để
--   cổng dựng ra `<img src="/api/v1/public/files/<id-ma>">` và trả 404 — hỏng câm.
--
-- ⚠ `ON DELETE SET NULL` là CÓ CHỦ ĐÍCH. Xoá một tệp đính kèm thì mục menu phải mất logo chứ
--   không được chặn lượt xoá (RESTRICT) và cũng không được biến mất theo (CASCADE): mục menu là
--   thứ điều hướng của cổng, một tấm ảnh không được phép kéo nó đi.
ALTER TABLE menu_items
    ADD COLUMN logo_attachment_public_id UUID REFERENCES attachments (public_id) ON DELETE SET NULL;

COMMENT ON COLUMN menu_items.logo_attachment_public_id IS
    'Logo hiển thị cạnh nhãn — CHỈ dùng ở vị trí LIEN_KET (dải "Liên kết website" cuối trang chủ). '
    'Menu Header/Footer là menu chữ; ràng buộc ấy ép ở MenuService.uploadLogo, không ép ở đây, '
    'vì CHECK chỉ thấy một dòng còn vị trí thì nằm ở chính dòng ấy — xem Javadoc của phương thức.';


-- ── [2] Ảnh sơ đồ hệ thống công trình ──────────────────────────────────────────────────────
--
-- ⭐ HẬU TỐ `.attachment-id` KHÔNG PHẢI QUY ƯỚC ĐẶT TÊN — NÓ LÀ THỨ LÁI GIAO DIỆN
--
--   `SiteConfigTab.tsx` chia danh sách tham số bằng đúng một dòng:
--
--       const anhNhanDien = items.filter((item) => item.key.endsWith('.attachment-id'));
--
--   Khoá nào kết thúc bằng hậu tố ấy thì màn hình cấu hình giao diện dựng cho nó một ô **tải
--   ảnh lên**; khoá khác chỉ có ô nhập chữ. Đặt tên là `site.home.map-image` thì Công ty nhận
--   được một ô để **gõ một UUID vào**, tức đúng thứ không ai làm được.
--
-- ⚠ `value_type = 'STRING'` chứ không phải một kiểu ảnh riêng: giá trị lưu là `publicId` của
--   tệp, y hệt `site.logo.attachment-id` và `site.favicon.attachment-id` đã chạy từ WS-15.
--
-- ⛔ Mặc định RỖNG. Không trỏ sẵn vào một ảnh nào — chưa ai tải lên thì khối nói thẳng là chưa
--   có, và nói luôn ai là người tải (luật 16).
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order)
SELECT v.k, '', 'STRING', '', v.grp, v.label, v.descr, NULL, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.home.map-image.attachment-id', 'SITE', 'Ảnh sơ đồ hệ thống công trình (trang chủ)',
     'Tải lên ở màn hình Cấu hình giao diện. Hiện ở khối "Bản đồ hệ thống công trình". Bản đồ tương tác chỉ vẽ được khi danh mục công trình đã có toạ độ (G8); trước lúc đó ảnh này là thứ duy nhất người đọc thấy.',
     45)
   ) AS v(k, grp, label, descr, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);


-- ── [3] Chốt hạ bằng số đo ─────────────────────────────────────────────────────────────────
--
-- `ALTER TABLE`/`INSERT … WHERE NOT EXISTS` có thể chạm 0 hàng mà Flyway vẫn báo thành công.
DO $$
DECLARE
    so_cot  INTEGER;
    so_khoa INTEGER;
    xoa_gi  TEXT;
BEGIN
    SELECT count(*) INTO so_cot FROM information_schema.columns
     WHERE table_name = 'menu_items' AND column_name = 'logo_attachment_public_id';
    IF so_cot <> 1 THEN
        RAISE EXCEPTION 'V202608291047: menu_items thiếu cột logo_attachment_public_id';
    END IF;

    -- Canh HÀNH VI của khoá ngoại, không chỉ canh sự tồn tại của nó: `SET NULL` và `CASCADE`
    -- đều là "có khoá ngoại", nhưng cái thứ hai làm mục menu biến mất khi ai đó dọn kho tệp.
    SELECT rc.delete_rule INTO xoa_gi
      FROM information_schema.referential_constraints rc
      JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name
     WHERE kcu.table_name = 'menu_items' AND kcu.column_name = 'logo_attachment_public_id';
    IF xoa_gi IS DISTINCT FROM 'SET NULL' THEN
        RAISE EXCEPTION 'V202608291047: khoá ngoại logo phải ON DELETE SET NULL, đang là %', coalesce(xoa_gi, '(không có)');
    END IF;

    -- Khoá ảnh: phải đúng hậu tố `.attachment-id`, nếu không màn hình quản trị dựng ô nhập chữ
    -- và Công ty không có cách nào tải ảnh lên — cả migration này thành vô nghĩa.
    SELECT count(*) INTO so_khoa FROM settings
     WHERE setting_key = 'site.home.map-image.attachment-id'
       AND group_code = 'SITE' AND editable IS TRUE AND setting_key LIKE '%.attachment-id';
    IF so_khoa <> 1 THEN
        RAISE EXCEPTION 'V202608291047: thiếu khoá ảnh sơ đồ hệ thống ở nhóm SITE';
    END IF;
END $$;
