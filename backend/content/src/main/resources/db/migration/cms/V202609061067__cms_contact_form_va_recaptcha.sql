-- =============================================================================
-- CN-01.4 — bật/tắt & đặt bắt buộc từng trường biểu mẫu, và CHỖ CẮM reCAPTCHA.
-- WS-36 / T36.7 + T36.6.
--
-- ⭐⭐ VÌ SAO NHÓM `SITE` CHỨ ⛔ KHÔNG PHẢI `CMS`
--
--   `PublicPortalService.siteConfig()` trả về **đúng hai nhóm**: SITE và COMPANY
--   (`SiteConfigService.effectiveValues`). Cổng công khai ⛔ không có đường nào
--   đọc một khoá nhóm CMS. Đặt bộ khoá này vào CMS là dựng một nửa cặp đọc–ghi
--   ngay từ dòng đầu — biểu mẫu sẽ ⛔ không bao giờ biết trường nào bắt buộc.
--
--   Ngược lại, hai khoá của V202609061066 (`ack-email-enabled`, `sla-hours`) ở
--   nhóm CMS là ĐÚNG: chúng là việc hậu trường, và ⛔ không có lý do gì để một
--   endpoint công khai không cần đăng nhập kể ra hạn xử lý nội bộ của Công ty.
--
-- ⛔⛔ VÌ SAO TỆP NÀY ⛔ KHÔNG SEED MỘT KHOÁ reCAPTCHA NÀO
--
--   Bản đầu của tệp này seed ba khoá `site.contact.recaptcha.*`. Bộ canh
--   `PortalSettingsReadTest.moiKhoaDeuCoNguoiDoc` ĐỎ ngay lượt chạy đầu và nó
--   đỏ ĐÚNG: ⛔ không dòng mã nào của CỔNG đọc chúng — phần giao diện của
--   reCAPTCHA chưa dựng, và nó chưa dựng được vì G13 chặn (chưa có khoá).
--
--   Quy tắc 15: *"chọn một — viết dòng mã đọc nó, hoặc đừng seed nó"*. Chính
--   ghi chú của T36.4/T36.6 trong sổ cũng dặn đúng câu ấy, và bản đầu vi phạm.
--
--   ⇒ Chỗ cắm sống trong MÃ, ⛔ không trong `settings`: `ContactFormPolicy` đọc
--     `site.contact.recaptcha.enabled` bằng `getBoolean(..., false)` — khoá
--     VẮNG chính là "tắt", đúng nghĩa và ⛔ không cần một hàng nào. Ngày G13 về,
--     một migration thêm cả ba khoá CÙNG LÚC với đoạn mã cổng đọc chúng.
--
--   ⚠ Khoá **bí mật** thì ⛔ KHÔNG BAO GIỜ vào bảng này, kể cả ngày ấy: bảng đi
--     thẳng ra `GET /api/v1/public/site-config` — endpoint ⛔ không cần đăng
--     nhập. Nó đọc từ biến môi trường `RECAPTCHA_SECRET_KEY`, cùng đường với
--     `HYDRO_API_KEY`. Bộ canh `RecaptchaKhoaBiMatTest` khẳng định điều đó.
--
-- ⚠ PHẠM VI THẬT của "bật/tắt từng trường" (luật 28 — bộ canh phải tự khai)
--
--   Lược đồ hôm nay: `full_name`, `subject`, `content` là NOT NULL, và
--   `ck_contacts_lien_lac` đòi **ít nhất một** trong email/điện thoại. Nên thứ
--   thật sự cấu hình được là:
--     • có hiện ô Số điện thoại hay ⛔ không
--     • email và/hoặc điện thoại có BẮT BUỘC hay ⛔ không
--   Ba trường kia muốn tắt được thì phải nới NOT NULL — và đó là một câu hỏi
--   NGHIỆP VỤ (Công ty có nhận phản ánh nặc danh ⛔ không?), ⛔ không phải một
--   quyết định kỹ thuật. ⛔ Không nới lược đồ trước khi có câu trả lời.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, 'SITE', v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    -- ---- Biểu mẫu liên hệ (T36.7) -------------------------------------------
    ('site.contact.field.phone.enabled', 'true', 'BOOLEAN',
     'Hiện ô Số điện thoại trên biểu mẫu liên hệ',
     'CN-01.4. Tắt ô này thì email trở thành BẮT BUỘC — ràng buộc ck_contacts_lien_lac vẫn đòi ít '
     'nhất một cách liên hệ ngược, và hệ thống tự suy ra điều đó thay vì để người dùng gặp lỗi.',
     NULL, 30),
    ('site.contact.field.email.required', 'false', 'BOOLEAN',
     'Bắt buộc nhập Email',
     'CN-01.4. Mặc định tắt: người dân chỉ có số điện thoại vẫn phải gửi được phản ánh.',
     NULL, 31),
    ('site.contact.field.phone.required', 'false', 'BOOLEAN',
     'Bắt buộc nhập Số điện thoại',
     'CN-01.4. ⛔ Bật CẢ HAI khoá bắt buộc nghĩa là người gửi phải có đủ email lẫn điện thoại.',
     NULL, 32)

) AS v(k, val, vtype, label, descr, validation, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);


-- =============================================================================
-- Kiểm ngay trong migration — `INSERT ... WHERE NOT EXISTS` khớp hụt chèn 0 hàng
-- và Flyway vẫn xanh trọn vẹn (§10.66).
-- =============================================================================
DO $$
DECLARE
    so_khoa INTEGER;
BEGIN
    SELECT count(*) INTO so_khoa
      FROM settings
     WHERE setting_key LIKE 'site.contact.%' AND group_code = 'SITE';
    IF so_khoa <> 3 THEN
        RAISE EXCEPTION 'Phải có đủ 3 khoá site.contact.*, đang có %', so_khoa;
    END IF;

    -- ⛔⛔ Vế chịu lực: chưa bao giờ có, và ⛔ KHÔNG BAO GIỜ được có.
    IF EXISTS (SELECT 1 FROM settings WHERE setting_key LIKE '%recaptcha%secret%') THEN
        RAISE EXCEPTION 'Khoá bí mật reCAPTCHA nằm trong bảng settings — bảng này đi ra endpoint công khai';
    END IF;
END $$;
