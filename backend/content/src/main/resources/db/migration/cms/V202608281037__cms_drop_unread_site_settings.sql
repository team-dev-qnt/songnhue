-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Gỡ bốn khoá `settings` chưa từng có nơi đọc — WS-25 · 28/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Quy tắc 15: *công tắc / cột / tham số chưa ai đọc là một lỗi, không phải việc để dành.*
--
--  Đo ngày 28/08/2026 (grep toàn kho: `backend/*/src/main/java`, `frontend/*/src`, `tools`):
--
--      site.analytics.ga-tracking-id     → 0 nơi đọc
--      site.analytics.gtm-container-id   → 0 nơi đọc
--      site.color.primary                → 0 nơi đọc
--      site.color.secondary              → 0 nơi đọc
--
--  Cả bốn seed từ `V202608191020` (19/08) và bày ra trên màn hình *Cấu hình hệ thống* suốt
--  9 ngày. Quản trị viên đặt giá trị, hệ thống báo **lưu thành công**, và không có gì thay đổi
--  — đúng lỗi đã sửa ở WS-12 cho `limits.upload.max-mb.*` và `company.*`.
--
--  ⚠ `PortalSettingsReadTest` (T24.19) sinh ra để chặn đúng chuyện này, nhưng nó chỉ soi khoá
--    seed ở `V202608271032`. Một bộ canh đúng luật mà **phạm vi hẹp hơn nơi nó phải chặn** —
--    cùng hình dạng với `NginxSecurityHeadersTest` chỉ soi `admin-app` trong khi cổng công khai
--    không có CSP (§10.61). Lượt này mở phạm vi bài kiểm ra **toàn bộ khoá nhóm SITE/COMPANY**.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  VÌ SAO GỠ CHỨ KHÔNG VIẾT MÃ ĐỌC CHÚNG
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  ▸ `site.analytics.*` — bật GA/GTM đòi tải mã từ `googletagmanager.com`, mà CSP của cổng
--    chốt `script-src 'self'` (`conventions.md` §4.5, dựng ở `next.config.ts` ngày 27/8).
--    Đọc khoá này mà không nới CSP thì trình duyệt chặn, **không báo lỗi nào**, và ta lại có
--    thêm một công tắc bấm-mà-không-chạy. Nới CSP là một quyết định về quyền riêng tư của
--    người dân tra cứu — cùng lý lẽ đã khiến dự án tự host Noto Sans thay vì lấy từ CDN Google
--    (`ui-styles.md` §3.1): người đọc cổng của cơ quan nhà nước không có cách nào từ chối.
--    ⇒ Quyết định ấy thuộc Công ty, nằm trong **G13**, và phải đi kèm một lượt sửa CSP có chủ ý.
--       Khoá sẽ được dựng lại **cùng lượt** với thay đổi CSP, không sớm hơn.
--
--  ▸ `site.color.*` — `ui-styles.md` §2.1 chốt ⛔ *"Cấm khai màu cứng… Mọi màu phải được định
--    nghĩa trong design-tokens"*. Một khoá `settings` đổi được màu thương hiệu lúc chạy là
--    **nguồn thứ hai** cho cùng một giá trị, tức đúng hình dạng quy tắc 14. Nhận diện thương
--    hiệu đổi vài năm một lần; nó xứng đáng một lượt deploy, không xứng đáng một cơ chế song
--    song tồn tại vĩnh viễn để chờ.
--    ⚠ Và giá trị seed còn **sai**: `#1677ff` là xanh mặc định của AntD, trong khi màu thương
--      hiệu thật là `#165bb6` (`brandColors.primary`). Ô "Màu chủ đạo" ấy đã hiện một mã màu
--      không đúng và không ai đọc — bỏ đi tốt hơn hẳn sửa nó thành một mã đúng mà vẫn không ai đọc.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

DELETE FROM settings
 WHERE setting_key IN (
    'site.analytics.ga-tracking-id',
    'site.analytics.gtm-container-id',
    'site.color.primary',
    'site.color.secondary');
