-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Hạ `limits.upload.max-mb.video` 500 → 120 cho khớp thứ hạ tầng thật sự làm được — 30/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  ⭐ MIGRATION NÀY LÀ NỬA SAU CỦA MỘT BẢN VÁ, KHÔNG ĐỨNG MỘT MÌNH
--
--  Nửa trước nằm ở `application.yml`: `spring.servlet.multipart.max-file-size` trước nay KHÔNG
--  được khai, nên Spring Boot áp mặc định **1MB/tệp** — và mặc định ấy chặn ở
--  `DispatcherServlet.checkMultipart`, tức TRƯỚC controller. Hệ quả đo được trên staging ngày
--  30/08: mọi tệp > 1MB trả **500**, trong khi màn hình cấu hình bày ra `max-mb.image = 10`.
--
--  Nghĩa là toàn bộ cơ chế hạn mức đọc từ `settings` (WS-12) **chưa từng quyết định điều gì**:
--  nó nằm sau một trần thấp hơn mà không ai nhìn thấy. Nó sống được 18 ngày vì 39/39 tệp trên
--  staging đều dưới 1MB (lớn nhất 570 kB) — tấm sơ đồ hệ thống là tệp đầu tiên vượt qua.
--
--  ⛔ VÌ SAO HẠ 500 XUỐNG, THAY VÌ NÂNG TRẦN LÊN 500
--
--  Đường tải lên đi qua `byte[]`: `file.getBytes()`, rồi `ImageSanitizer`/`storage.put` — ít
--  nhất hai bản sao nằm trong heap cùng lúc. Đo trên staging: heap tối đa **1.076 GB**
--  (`MaxRAMPercentage=70` trên container 1.5 GB), và JVM chạy `-XX:+ExitOnOutOfMemoryError`.
--
--  Nên nâng trần lên 500MB KHÔNG mở khoá gì cả — nó đổi một lỗi 413 sạch sẽ lấy một lượt **giết
--  tiến trình**, đúng thứ tệ hơn lỗi ban đầu. 500 là con số hạ tầng này chưa bao giờ làm được.
--
--  ⚠ Và đây là quy tắc 15 ở dạng ngược: `max-mb.video = 500` CÓ mã đọc (`AttachmentService`),
--    nhưng nó quảng cáo một năng lực không tồn tại. Một tham số nói dối còn khó phát hiện hơn
--    một tham số không ai đọc, vì nó có đủ mọi dấu hiệu của một tham số đang chạy tốt.
--
--  📌 Hạ số này KHÔNG mất dữ liệu và KHÔNG chặn ai:
--       • 0 tệp video từng được tải lên (`SELECT count(*) … content_type LIKE 'video/%'` = 0);
--       • video trang chủ là nhúng YouTube — `site.home.video-id = 'Mb70qe84eqU'`, không phải tệp.
--     Ngày cần video > 120MB thì thứ phải đổi là ĐƯỜNG TẢI (chuyển sang luồng, bỏ `byte[]`),
--     không phải con số này — nợ T26.71.
--
--  ⛔ SỐ HIỆU: phải lớn hơn `V202608291047` — bản ấy ĐÃ ÁP trên staging (đo 30/08). Đánh số thấp
--     hơn thì Flyway `validate` chặn lượt deploy, đúng §10.66 đã làm đỏ CD hai lần.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

-- ⚠ Chỉ hạ khi giá trị đang là 500 — con số do migration seed đặt ra. Nếu Công ty đã tự sửa
--   thành số khác thì đó là quyết định của họ, migration không được giẫm lên. (Vẫn phải kẹp trần
--   ở khối [2] bên dưới, vì "họ tự đặt" cũng không làm 500MB chạy được.)
UPDATE settings
   SET setting_value = '120',
       default_value = '120',
       description = 'Dung lượng tối đa mỗi tệp video tải lên thư viện media. Trần cứng của máy chủ '
                  || 'là 120MB (spring.servlet.multipart.max-file-size) — đặt lớn hơn số đó thì phần '
                  || 'vượt bị chặn ở tầng hạ tầng với mã SYS-0011, không phải ở đây.',
       updated_at = now()
 WHERE setting_key = 'limits.upload.max-mb.video'
   AND setting_value = '500';

-- ── [2] Kẹp mọi khoá còn lại xuống dưới trần ───────────────────────────────────────────────
--
-- Không chỉ `video`: bất kỳ khoá `limits.upload.max-mb.*` nào lớn hơn trần đều là một lời hứa
-- máy chủ không giữ được. Hôm nay chỉ `video` vi phạm, nhưng viết theo NHÓM thì lần sau ai thêm
-- khoá mới cũng được kẹp — thay vì phải nhớ (quy tắc 14).
UPDATE settings
   SET setting_value = '120', updated_at = now()
 WHERE setting_key LIKE 'limits.upload.max-mb.%'
   AND setting_value ~ '^[0-9]+$'
   AND setting_value::INTEGER > 120;

-- ── [3] Chốt hạ bằng số đo ─────────────────────────────────────────────────────────────────
--
-- `UPDATE … WHERE` chạm 0 hàng vẫn là một lượt Flyway thành công. Khẳng định ở đây nói về TRẠNG
-- THÁI CUỐI, không về việc câu lệnh trên có chạy hay không — trạng thái cuối mới là thứ ứng dụng
-- đọc, và nó đúng dù migration này chạy trên CSDL rỗng hay trên staging đã có dữ liệu.
DO $$
DECLARE
    vuot_tran INTEGER;
    tran_video TEXT;
BEGIN
    SELECT count(*) INTO vuot_tran FROM settings
     WHERE setting_key LIKE 'limits.upload.max-mb.%'
       AND setting_value ~ '^[0-9]+$'
       AND setting_value::INTEGER > 120;
    IF vuot_tran <> 0 THEN
        RAISE EXCEPTION 'V202608301048: còn % khoá limits.upload.max-mb.* vượt trần 120MB', vuot_tran;
    END IF;

    SELECT setting_value INTO tran_video FROM settings WHERE setting_key = 'limits.upload.max-mb.video';
    IF tran_video IS NULL THEN
        RAISE EXCEPTION 'V202608301048: thiếu hẳn khoá limits.upload.max-mb.video';
    END IF;
END $$;
