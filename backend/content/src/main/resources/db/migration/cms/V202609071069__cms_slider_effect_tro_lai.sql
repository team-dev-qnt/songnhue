-- =============================================================================
-- CN-01.3 — hiệu ứng chuyển ảnh của slider trang chủ. WS-36 / T36.11.
--
-- ⛔⛔ KHOÁ NÀY TỪNG BỊ XOÁ, VÀ NÓ CHỈ ĐƯỢC QUAY LẠI VÌ NAY CÓ NGƯỜI ĐỌC
--
--   `V202608271032` XOÁ `site.slider.effect` với lý do đúng: nó bày ra trên màn
--   hình Cấu hình hệ thống mà ⛔ KHÔNG dòng mã nào của cổng đọc — quy tắc 15,
--   một công tắc chưa ai đọc là một LỖI, ⛔ không phải việc để dành. Triệu chứng
--   im lặng hoàn toàn: quản trị viên chọn "Fade", màn hình báo lưu thành công,
--   và trang chủ ⛔ không đổi một pixel nào.
--
--   Ghi chú T36.11 trong sổ dặn thẳng: *"muốn có hiệu ứng Fade thì phải dựng
--   NƠI ĐỌC trước (`HomeBannerSlider` nhận thêm một prop), rồi mới seed lại
--   khoá. ⛔ Đừng seed lại rồi hẹn làm sau."*
--
--   ⇒ Lượt này làm đúng thứ tự ấy, và bốn thứ dưới đây nằm trong CÙNG một commit
--     với tệp này — ⛔ không phải một lời hứa:
--       • `lib/slider.ts` → `docHieuUngSlider()`
--       • `AnhCarousel`   → prop `hieuUng`, và một nhánh SLIDE dựng dải ngang thật
--       • `HomeBannerSlider` + `HomeMediaGallery` → truyền xuống
--       • `app/page.tsx`  → đọc `config['site.slider.effect']`
--
--   ⚠ Bộ canh `PortalSettingsReadTest.khoaDaGoKhongConNoiDoc` khẳng định khoá
--     này ⛔ KHÔNG được đọc ở đâu. Nó phải sửa CÙNG lượt — nếu không, nó đỏ vì
--     một lý do đã hết đúng, và một bộ canh nói sai thì lượt sau người ta nới nó.
--
-- ⭐ MẶC ĐỊNH `FADE`, ⛔ không phải `SLIDE`
--
--   `FADE` là hành vi ĐANG CHẠY từ WS-16 (`transition-opacity duration-700`).
--   Seed `SLIDE` là đổi diện mạo trang chủ bằng một lượt deploy mà ⛔ không ai
--   bấm gì — cùng họ với luật 3 (canh giá trị ĐÃ GIẢI, đừng canh giá trị mặc
--   định): giá trị seed là thứ Công ty NHẬN ĐƯỢC, ⛔ không phải thứ ta thấy đẹp.
--
-- ⚠ `validation` khai đủ hai giá trị để màn hình dựng được ô CHỌN thay vì ô gõ
--   tự do. Nhưng `docHieuUngSlider()` vẫn phải chịu được chuỗi lạ: cột này ⛔
--   không có ràng buộc CHECK, và một lượt `UPDATE` tay bỏ qua mọi thứ ở giao diện.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT 'site.slider.effect', 'FADE', 'STRING', 'FADE', 'SITE',
       'Hiệu ứng chuyển ảnh của slider trang chủ',
       'CN-01.3. FADE = ảnh mờ dần chồng lên nhau (mặc định, đang chạy). '
       'SLIDE = ảnh trượt ngang. Áp cho CẢ HAI slider của trang chủ — ảnh hoạt động và '
       'thư viện ảnh cạnh video (yêu cầu 29/08: hai slider dùng chung bộ khoá site.slider.*).',
       '{"enum":["FADE","SLIDE"]}', TRUE, TRUE, 24
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = 'site.slider.effect');


-- =============================================================================
-- Kiểm ngay trong migration — `INSERT ... WHERE NOT EXISTS` khớp hụt chèn 0 hàng
-- và Flyway vẫn xanh trọn vẹn (§10.66).
-- =============================================================================
DO $$
DECLARE
    gia_tri TEXT;
BEGIN
    SELECT setting_value INTO gia_tri FROM settings WHERE setting_key = 'site.slider.effect';
    IF gia_tri IS NULL THEN
        RAISE EXCEPTION 'Khoá site.slider.effect chưa được chèn — 0 hàng, và Flyway vẫn xanh';
    END IF;
    IF gia_tri <> 'FADE' THEN
        RAISE EXCEPTION 'Giá trị seed phải là FADE (hành vi đang chạy), đang là %', gia_tri;
    END IF;

    -- ⚠ Vế chịu lực: khoá phải nằm ở nhóm SITE. `SiteConfigService.effectiveValues()` chỉ
    --   trả SITE + COMPANY ra `/api/v1/public/site-config`; ở nhóm khác thì `app/page.tsx`
    --   ⛔ không bao giờ đọc được, và ta vừa dựng lại đúng nửa cặp đọc–ghi đã xoá năm ngoái.
    IF NOT EXISTS (
        SELECT 1 FROM settings WHERE setting_key = 'site.slider.effect' AND group_code = 'SITE'
    ) THEN
        RAISE EXCEPTION 'site.slider.effect ⛔ không ở nhóm SITE — cổng sẽ ⛔ không đọc được';
    END IF;
END $$;
