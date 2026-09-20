-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  "Hệ thống văn bản điều hành" TẮT ĐƯỢC từ giao diện quản trị — T79.1 · 20/09/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  QuanTran 20/09: *"remove đi mục Hệ thống văn bản điều hành ở public-web"*. Đo ra rằng cơ chế
--  ẩn ĐÃ CÓ mà ⛔ bao giờ dùng được — nên việc phải làm ⛔ phải xoá mã, mà là sửa DỮ LIỆU.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  ⛔⛔ VÌ SAO XOÁ TRẮNG Ô TRÊN MÀN HÌNH LẠI ⛔ TẮT ĐƯỢC MỤC NÀY
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  Ba chỗ trên cổng (thẻ trang chủ · dòng thanh bên · nút chân trang) đều render CÓ ĐIỀU KIỆN
--  trên `lienKetAnToan(url)`, và hàm ấy trả NULL khi chuỗi rỗng. Tức về nguyên tắc: xoá ô ⇒ cả
--  ba biến mất.
--
--  Nhưng ô ấy ⛔ xoá được:
--
--      Setting.effectiveValue()
--        → settingValue != null && !settingValue.isBlank() ? settingValue : defaultValue
--
--      V202608271032:19   SELECT v.k, v.val, v.vtype, v.val, …
--                                   ↑setting_value  ↑default_value   ⇠ CÙNG một giá trị
--
--  ⇒ `default_value` mang sẵn `https://quanlyvanban.hanoi.gov.vn/…`. Quản trị viên xoá trắng ô,
--  `changeValue()` quy chuỗi rỗng về NULL, `effectiveValue()` rơi về mặc định, **URL quay lại**.
--  Màn hình báo *lưu thành công* và cổng ⛔ đổi một pixel — đúng khuyết tật `site.color.*`.
--
--  ⭐ Đây là **T53.4** ở dạng ngược: ở đó một mặc định khác rỗng làm trạng thái *"Công ty chưa
--  chọn"* ⛔ biểu diễn được; ở đây nó làm trạng thái *"Công ty ⛔ muốn dùng mục này"* ⛔ biểu diễn
--  được. Cùng một cột, cùng một cơ chế, hai câu hỏi khác nhau — và cả hai đều ⛔ trả lời nổi.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  VÌ SAO ⛔ DELETE HẲN KHOÁ
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  `CR-07` (nghiệm thu 27/8) và `CN-01.7` (function-spec) đều nói tới mục này; gỡ khoá là rút
--  một mã CR khỏi cổng và biến "bật lại" thành một lượt deploy. QuanTran chốt 20/09: giữ cơ chế,
--  chỉ làm cho nó TẮT ĐƯỢC. ⇒ Hôm nay mục biến mất khỏi cổng; dán URL vào ô là nó hiện lại đủ ba
--  chỗ, ⛔ cần ai deploy.
--
-- ⚠ `value_type` giữ nguyên `URL` (đặt ở `V202609161084`). Chuỗi RỖNG vẫn hợp lệ vì
--   `SettingValidator.validate` trả về NGAY ở nhánh rỗng — *"Rỗng = quay về giá trị mặc định của
--   danh mục, luôn hợp lệ"* — và từ bản này mặc định ấy cũng rỗng.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

UPDATE settings
   SET setting_value = '',
       default_value = '',
       description = 'Dán địa chỉ hệ thống văn bản điều hành để hiện mục này trên cổng '
                  || '(thẻ trang chủ, thanh bên, chân trang). Để TRỐNG = ẩn cả ba. '
                  || 'Cổng KHÔNG đồng bộ dữ liệu văn bản (CN-01.7).'
 WHERE setting_key = 'site.external.doc-system-url';

-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Tự kiểm — một migration ⛔ khẳng định gì thì lượt sau ⛔ biết nó đã chạy đúng ⛔
-- ═══════════════════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_dong INTEGER;
    kieu    TEXT;
BEGIN
    SELECT count(*) INTO so_dong
      FROM settings
     WHERE setting_key = 'site.external.doc-system-url'
       AND coalesce(setting_value, '') = ''
       AND coalesce(default_value, '') = ''
       AND editable;
    IF so_dong <> 1 THEN
        RAISE EXCEPTION 'Chờ đúng 1 hàng site.external.doc-system-url rỗng cả hai cột, đo được %', so_dong;
    END IF;

    -- Vế đối chứng: khoá phải CÒN SỐNG. Nếu một lượt gộp nào đó xoá nó thì ba nơi đọc trên cổng
    -- thành nửa cặp đọc–ghi và `PortalSettingsReadTest` ⛔ thấy gì (nó chỉ canh chiều ngược lại).
    SELECT value_type INTO kieu FROM settings WHERE setting_key = 'site.external.doc-system-url';
    IF kieu IS DISTINCT FROM 'URL' THEN
        RAISE EXCEPTION 'site.external.doc-system-url phải giữ kiểu URL, đo được %', coalesce(kieu, '⛔ có hàng');
    END IF;
END $$;
