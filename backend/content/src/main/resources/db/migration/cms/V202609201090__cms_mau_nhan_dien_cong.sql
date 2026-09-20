-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Màu nhận diện cổng đổi được từ giao diện quản trị — T75.7 · 20/09/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Công ty gửi bộ nhận diện 20/09/2026 (trang "MÀU SẮC CHỦ ĐẠO") — bốn màu và vai trò của chúng:
--
--      #1758bf  Xanh dương  — "màu chủ đạo, đại diện cho nguồn nước, sự ổn định"; dùng trong logo
--      #fac036  Vàng cam    — "bông lúa, mùa màng … tạo điểm nhấn và cân bằng với sắc xanh"
--      #ffffff  Trắng       — nền sáng
--      #000000  Đen         — nội dung chữ và thông tin phụ
--
--  Hai màu ĐẦU là nhận diện thương hiệu ⇒ có núm chỉnh ở đây.
--  Hai màu SAU là nền trang và màu chữ ⇒ ⛔ có núm. Một ô nhập cho phép đặt chữ trùng màu nền là
--  cái bẫy đọc-không-nổi mà ⛔ gì chặn; typography thuộc `docs/ui-styles.md` và `neutralColors`.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  ⛔⛔ ĐÂY LÀ LƯỢT DỰNG LẠI MỘT CƠ CHẾ ĐÃ BỊ GỠ — ĐỌC `V202608281037` TRƯỚC KHI SỬA TỆP NÀY
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  `site.color.primary` / `site.color.secondary` seed ngày 19/08 (`V202608191020`), bày trên màn
--  hình *Cấu hình hệ thống* suốt 9 ngày với **0 nơi đọc**, và bị `V202608281037` gỡ theo quy tắc
--  15. Triệu chứng lúc ấy: quản trị viên đặt màu, hệ báo *lưu thành công*, cổng ⛔ đổi một pixel.
--
--  Lượt này khác ở đúng MỘT điểm, và điểm ấy là toàn bộ lý do nó được phép tồn tại: **có đường
--  đọc**. Đường ấy là `public-web/src/lib/mauThuongHieu.ts` → biến CSS trong `layout.tsx` →
--  `var(--sn-brand-*, <token>)` ở `tailwind.config.ts`. Bộ canh `PortalSettingsReadTest
--  .moiKhoaDeuCoNguoiDoc` ⛔ cho tái diễn: khoá `site.*` nào còn sống mà ⛔ ai đọc thì CI đỏ và
--  gọi đích danh khoá đó.
--
--  ⚠ TÊN KHOÁ CỐ Ý KHÁC (`site.brand.*`, ⛔ phải `site.color.*`) vì HAI lý do đo được:
--    1. `PortalSettingsReadTest` dùng chính `site.color.primary` làm **dữ liệu gá** cho bài tự-kiểm
--       *"khoá bị DELETE phải bị trừ khỏi danh sách"*. Hồi sinh đúng tên ấy là phá một fixture đang
--       chạy để lấy một cái tên đẹp hơn.
--    2. Hợp đồng đã KHÁC: kiểu `COLOR` (⛔ phải `STRING`), giá trị tiêm vào `<style>` của cổng
--       công khai, và vai trò *accent* thay cho *secondary*. Dùng lại tên cũ cho một hợp đồng mới
--       là mời người đọc sau suy ra hành vi cũ.
--
--  ⛔ Bản ghi lịch sử giữ nguyên: `site.color.*` vẫn là khoá ĐÃ GỠ và vẫn phải ⛔ ai đọc.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  VÌ SAO THÊM MỘT `value_type` THAY VÌ KIỂM Ở SERVICE
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  Giá trị này đi vào một khối `<style>` của cổng công khai ⇒ một chuỗi tuỳ ý ở đó là đường tiêm
--  CSS. Quy tắc 12 nói đặt bảo đảm ở **chỗ dữ liệu đi qua**, ⛔ ở nơi gọi: `settings` có nhiều
--  đường ghi (màn hình cấu hình, nhập cấu hình, khôi phục sao lưu), nên kiểm ở `SiteConfigService`
--  là bỏ trống ba đường còn lại. `SettingValidator.checkType` là chỗ MỌI đường ghi đi qua.
--
--  Đây là lượt mở rộng THỨ BA của `ck_settings_value_type` — `V202608211022` (HTML, HTML_EMBED)
--  và `V202609161084` (URL) đã đi đúng đường này; giữ nguyên khuôn của chúng.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE settings DROP CONSTRAINT ck_settings_value_type;

ALTER TABLE settings ADD CONSTRAINT ck_settings_value_type CHECK (
    value_type IN ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN',
                   'JSON', 'CRON', 'TIME', 'DATE', 'DURATION',
                   'HTML', 'HTML_EMBED', 'URL', 'COLOR')
);

-- ⚠ `default_value` để RỖNG một cách cố ý, ⛔ phải bỏ sót.
--
--   Quy tắc 3 + T53.4: một tham số có mặc định KHÁC rỗng thì trạng thái *"Công ty chưa chọn"* trở
--   nên ⛔ biểu diễn được — `Setting.effectiveValue()` rơi về `default_value` khi giá trị rỗng, và
--   `changeValue()` quy chuỗi rỗng về NULL. Nếu seed `#1758bf` làm mặc định thì ⛔ còn cách nào
--   phân biệt *"Công ty cố ý chọn đúng màu ấy"* với *"⛔ ai từng mở màn hình này"*.
--
--   Rỗng ⇒ `cssMauThuongHieu()` ⛔ phát biến nào ⇒ trình duyệt rơi về token của `design-tokens`,
--   mà token ấy ĐÃ mang đúng `#1758bf` / `#fac036` của bộ nhận diện. Người dùng thấy đúng màu
--   Công ty muốn, và bảng `settings` vẫn nói thật rằng chưa ai đặt gì.
-- ⚠ `description` KHÔNG khai giá trị mặc định hiện hành.
--
--   Bản nháp của tệp này viết *"Để trống = dùng màu của bộ nhận diện (#1758bf)"* — và đó là nơi
--   THỨ BA khẳng định cùng một con số, sau `design-tokens` và `tailwind.config.ts`. Một migration
--   ĐÃ PHÁT HÀNH thì ⛔ sửa được, nên câu ấy sẽ nói dối vĩnh viễn kể từ lượt Công ty đổi nhận diện
--   lần sau. Mã hex còn lại trong câu là **ví dụ ĐỊNH DẠNG** (chỉ dạy `#` + 6 chữ số), ⛔ phải một
--   lời khai về giá trị đang chạy — hai thứ đó khác nhau, và chỉ thứ sau mới vi phạm quy tắc 14.
-- ⚠⚠ Danh sách cột này ĐỌC TỪ `CREATE TABLE`, ⛔ chép từ seed cũ — bản nháp của tệp này khai
--    `category` và Flyway đỏ với `column "category" of relation "settings" does not exist`.
--    Nguồn của cái sai: `V202608191020` dùng dạng `INSERT … SELECT v.k, v.val, … FROM (VALUES …)`,
--    nên tuple 8 phần tử bên trong là **đối số của SELECT**, ⛔ phải danh sách cột — và giá trị
--    `'SITE'` đứng ở vị trí thứ tư khiến nó đọc y như một cột tên `category`. Cột thật: `group_code`.
--    Hai cột `setting_value` và `default_value` cũng là HAI cột riêng (seed cũ đổ `v.val` vào cả hai).
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES
    ('site.brand.primary', '', 'COLOR', '',
     'SITE', 'Màu chủ đạo (xanh dương)',
     'Mã HEX 6 chữ số, ví dụ #1758bf. Để trống = dùng màu mặc định của bộ nhận diện. '
     || 'Đổi màu nền nút, liên kết và các mảng nhấn chính của cổng thông tin.',
     NULL, TRUE, TRUE, 50),
    ('site.brand.accent', '', 'COLOR', '',
     'SITE', 'Màu nhấn (vàng cam)',
     'Mã HEX 6 chữ số, ví dụ #fac036. Để trống = dùng màu mặc định của bộ nhận diện. '
     || 'Dùng cho chữ và viền nhấn trên nền xanh đậm ở đầu trang.',
     NULL, TRUE, TRUE, 60);

-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Tự kiểm — một migration ⛔ khẳng định gì thì lượt sau ⛔ biết nó đã chạy đúng ⛔
-- ═══════════════════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_khoa_moi   INTEGER;
    so_khoa_cu    INTEGER;
    dinh_nghia    TEXT;
BEGIN
    SELECT count(*) INTO so_khoa_moi
      FROM settings
     WHERE setting_key IN ('site.brand.primary', 'site.brand.accent')
       AND value_type = 'COLOR'
       AND coalesce(default_value, '') = ''
       AND coalesce(setting_value, '') = ''
       AND group_code = 'SITE'
       AND editable;
    IF so_khoa_moi <> 2 THEN
        RAISE EXCEPTION 'Chờ 2 khoá site.brand.* kiểu COLOR với mặc định rỗng, đo được %', so_khoa_moi;
    END IF;

    -- Vế đối chứng: khoá CŨ phải vẫn vắng mặt. Nếu một lượt gộp nào đó hồi sinh chúng thì cổng có
    -- HAI nguồn màu và ⛔ ai biết nguồn nào thắng — đúng hình dạng quy tắc 14.
    SELECT count(*) INTO so_khoa_cu
      FROM settings
     WHERE setting_key IN ('site.color.primary', 'site.color.secondary');
    IF so_khoa_cu <> 0 THEN
        RAISE EXCEPTION 'site.color.* đã bị gỡ ở V202608281037 mà nay đo được % hàng', so_khoa_cu;
    END IF;

    SELECT pg_get_constraintdef(oid) INTO dinh_nghia
      FROM pg_constraint WHERE conname = 'ck_settings_value_type';
    IF dinh_nghia NOT LIKE '%COLOR%' THEN
        RAISE EXCEPTION 'ck_settings_value_type chưa nhận COLOR: %', dinh_nghia;
    END IF;
    -- Và các kiểu cũ phải còn nguyên — một lượt DROP/ADD rất dễ đánh rơi một dòng.
    IF dinh_nghia NOT LIKE '%HTML_EMBED%' OR dinh_nghia NOT LIKE '%URL%' THEN
        RAISE EXCEPTION 'ck_settings_value_type đánh rơi kiểu cũ: %', dinh_nghia;
    END IF;
END $$;
