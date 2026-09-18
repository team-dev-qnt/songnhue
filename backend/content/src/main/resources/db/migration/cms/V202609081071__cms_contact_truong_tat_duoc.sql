-- T28.49 — Liên hệ nặc danh có kiểm soát: Họ tên và Tiêu đề TẮT ĐƯỢC, Email BẮT BUỘC.
-- Chốt với QuanTran 08/09/2026.
--
-- =============================================================================
-- ⛔⛔ VÌ SAO TỆP NÀY TỒN TẠI — câu hỏi đã được ghi ra và để ngỏ ở V202609061067
-- =============================================================================
--
-- Migration T36.7 (06/09) tự khai phạm vi của nó bằng đúng những dòng này:
--
--   "Ba trường kia muốn tắt được thì phải nới NOT NULL — và đó là một câu hỏi
--    NGHIỆP VỤ (Công ty có nhận phản ánh nặc danh ⛔ không?), ⛔ không phải một
--    quyết định kỹ thuật. ⛔ Không nới lược đồ trước khi có câu trả lời."
--
-- Câu trả lời có ngày 08/09: **Email bắt buộc; Họ tên và Tiêu đề tắt được**.
--
-- =============================================================================
-- ⛔⛔ VÌ SAO ⛔ KHÔNG DÙNG `ALTER COLUMN email SET NOT NULL`
-- =============================================================================
--
-- `ck_contacts_lien_lac` (V202608291041:252) đòi **ít nhất một** trong
-- email/điện thoại. Hàng cũ chỉ có số điện thoại là **hợp lệ theo luật đang
-- chạy**, và staging/production có thể đang giữ những hàng như thế.
--
-- `SET NOT NULL` quét TOÀN BẢNG lúc áp. Trên CSDL rỗng của bộ test nó xanh
-- trọn vẹn; trên môi trường ĐÃ CÓ dữ liệu nó **đỏ lúc deploy** — đúng lớp lỗi
-- luật 30 mô tả và §10.80 vừa trả giá tháng này.
--
-- =============================================================================
-- ⛔⛔ VÀ VÌ SAO CŨNG ⛔ KHÔNG THÊM MỘT `CHECK ... NOT VALID`
-- =============================================================================
--
-- `CHECK (email IS NOT NULL) NOT VALID` sẽ chặn được hàng mới mà ⛔ không đụng
-- hàng cũ — nghe đúng, và nó là thứ tôi định làm.
--
-- ⛔ Nhưng nó **mâu thuẫn với chính khoá `site.contact.field.email.required`**,
--    một tham số Công ty BẬT/TẮT ĐƯỢC từ màn hình Cấu hình. Tắt khoá ấy đi thì
--    giao diện thôi đánh dấu bắt buộc, tầng service thôi kiểm — và CSDL vẫn từ
--    chối. Người vận hành nhận một lỗi 500 ⛔ không giải thích được, từ một
--    công tắc vừa được bảo là đã tắt.
--
-- ⇒ Đó chính là §10.69: **một tham số cấu hình NÓI DỐI khó thấy hơn hẳn một
--   tham số ⛔ không ai đọc.** Dự án đã trả giá cho hình dạng ấy một lần (trần
--   tải tệp 1MB), ⛔ không lặp lại.
--
-- ⇒ Email bắt buộc được ép ở **một chỗ duy nhất**: `ContactService` đọc
--   `ContactFormPolicy.emailBatBuoc()`. Ràng buộc CSDL giữ nguyên vế YẾU HƠN
--   ("ít nhất một cách liên hệ") — nó là lưới an toàn chống ghi thẳng, ⛔ không
--   phải nơi phát biểu chính sách.

-- --- 1. Nới NOT NULL cho hai trường nay tắt được -----------------------------
--
-- ⚠ `content` KHÔNG nới: một phản ánh ⛔ không có nội dung thì ⛔ không có gì để
--   xử lý. Đó ⛔ không phải "nặc danh", đó là một hàng rác.
ALTER TABLE contacts ALTER COLUMN full_name DROP NOT NULL;
ALTER TABLE contacts ALTER COLUMN subject DROP NOT NULL;

-- --- 2. Hai khoá mới ---------------------------------------------------------
--
-- ⛔ Luật 15: khoá chỉ được sinh ra CÙNG LƯỢT với người đọc nó. Người đọc ở đây
--   là `ContactFormPolicy.hienHoTen()` / `hienTieuDe()` (backend, quyết định
--   trường có bắt buộc) và `app/lien-he/page.tsx` (cổng, quyết định ô có hiện).
--   Cả hai vào cùng commit với tệp này.
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, 'SITE', v.label, v.descr, NULL, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.contact.field.full-name.enabled', 'true', 'BOOLEAN',
     'Hiện ô Họ và tên trên biểu mẫu liên hệ',
     'CN-01.4 — T28.49. Tắt ô này là cho phép gửi phản ánh KHÔNG kèm tên. Email vẫn bắt buộc, nên '
     'Công ty vẫn trả lời được; thứ mất đi chỉ là danh tính người gửi. Bật lại bất cứ lúc nào.',
     28),
    ('site.contact.field.subject.enabled', 'true', 'BOOLEAN',
     'Hiện ô Tiêu đề trên biểu mẫu liên hệ',
     'CN-01.4 — T28.49. Tắt ô này thì người gửi đi thẳng vào nội dung. Màn hình xử lý liên hệ hiện '
     'dấu gạch ở cột Tiêu đề, ⛔ không phải một tiêu đề rỗng.',
     29)
) AS v(k, val, vtype, label, descr, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);

-- --- 3. Email: từ "⛔ không bắt buộc" sang "bắt buộc" -------------------------
--
-- ⚠ Đổi CẢ `default_value` lẫn `setting_value`. Chỉ đổi một trong hai thì nút
--   "Khôi phục mặc định" trên màn hình Cấu hình sẽ lặng lẽ đưa hệ thống về
--   chính sách CŨ, và ⛔ không ai nhớ vì sao email lại thôi bắt buộc.
--
-- ⛔ `WHERE setting_value = default_value` có chủ đích: nếu Công ty ĐÃ tự tay
--   đổi khoá này trên môi trường thật thì lượt migration ⛔ KHÔNG được ghi đè
--   lựa chọn của họ — chỉ đổi mặc định. Cùng nguyên tắc đã ghi ở
--   V202608131007:12-13 ("migration sau KHÔNG được ghi đè lại").
UPDATE settings
   SET setting_value = 'true',
       default_value = 'true',
       description = 'CN-01.4 — T28.49 (chốt 08/09/2026): Email BẮT BUỘC. Đây là cách liên hệ '
                     'ngược duy nhất chắc chắn có, vì ô Số điện thoại tắt được. Tắt khoá này là '
                     'nhận về những phản ánh ⛔ không trả lời được.'
 WHERE setting_key = 'site.contact.field.email.required'
   AND setting_value = default_value;

UPDATE settings
   SET default_value = 'true',
       description = 'CN-01.4 — T28.49 (chốt 08/09/2026): Email BẮT BUỘC. Đây là cách liên hệ '
                     'ngược duy nhất chắc chắn có, vì ô Số điện thoại tắt được. Tắt khoá này là '
                     'nhận về những phản ánh ⛔ không trả lời được.'
 WHERE setting_key = 'site.contact.field.email.required'
   AND default_value <> 'true';

-- =============================================================================
-- Kiểm NGAY TRONG migration — `INSERT ... WHERE NOT EXISTS` khớp hụt chèn 0 hàng
-- và Flyway vẫn xanh trọn vẹn (§10.66). Cùng khuôn V202609061067.
-- =============================================================================
DO $$
DECLARE
    so_khoa   int;
    ho_ten_null_duoc  boolean;
    tieu_de_null_duoc boolean;
    email_bb  text;
BEGIN
    SELECT count(*) INTO so_khoa FROM settings
     WHERE setting_key IN ('site.contact.field.full-name.enabled', 'site.contact.field.subject.enabled');
    IF so_khoa <> 2 THEN
        RAISE EXCEPTION 'T28.49: chờ 2 khoá bật/tắt trường, có %', so_khoa;
    END IF;

    SELECT is_nullable = 'YES' INTO ho_ten_null_duoc FROM information_schema.columns
     WHERE table_name = 'contacts' AND column_name = 'full_name';
    SELECT is_nullable = 'YES' INTO tieu_de_null_duoc FROM information_schema.columns
     WHERE table_name = 'contacts' AND column_name = 'subject';
    IF NOT ho_ten_null_duoc OR NOT tieu_de_null_duoc THEN
        RAISE EXCEPTION 'T28.49: full_name/subject vẫn NOT NULL — hai khoá vừa thêm ⛔ không tắt được gì';
    END IF;

    SELECT default_value INTO email_bb FROM settings WHERE setting_key = 'site.contact.field.email.required';
    IF email_bb <> 'true' THEN
        RAISE EXCEPTION 'T28.49: mặc định email.required là % — chờ true', email_bb;
    END IF;

    -- ⛔ Ràng buộc "ít nhất một cách liên hệ" phải CÒN NGUYÊN. Nó là lưới an toàn chống đường
    --   ghi thẳng vào CSDL, và tệp này cố ý ⛔ KHÔNG thay nó bằng một CHECK mạnh hơn (xem đầu tệp).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_contacts_lien_lac') THEN
        RAISE EXCEPTION 'T28.49: ck_contacts_lien_lac biến mất — lưới an toàn cuối cùng của bảng contacts';
    END IF;
END $$;
