-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Bản đồ trụ sở + gắn bài seed vào chuyên mục con — 29/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Tệp này nằm ở `db/seed/portal`, tức **chỉ chạy khi `SEED_LOCATION` trỏ vào** — staging và
--  máy dev. Production không giải được nó. Đó là chỗ đặt đúng cho cả hai mục dưới đây: cả hai
--  là dữ liệu ĐỂ ĐO trên staging, không phải nội dung chính thức của Công ty.
--
--  ⛔ VÌ SAO KHÔNG ĐƯA VÀO `db/migration/cms`
--
--  Mã nhúng bản đồ chính thức thuộc **G13** (bộ nhận diện cổng) và Công ty chưa cấp. Ghi một
--  giá trị do phía phát triển chọn vào location mặc định là để nó chạy thẳng lên production và
--  **ghi đè** giá trị thật Công ty nhập sau này — một migration chạy một chiều, không hỏi ai.
--
-- ═══════════════════════════════════════════════════════════════════════════════════════════

-- ── [1] Bản đồ trụ sở ở chân trang ─────────────────────────────────────────────────────────
--
-- ⭐ DỰNG TỪ CHÍNH `company.address`, KHÔNG CHÉP LẠI ĐỊA CHỈ VÀO ĐÂY
--
-- Địa chỉ đã có một nguồn duy nhất trong `settings`. Viết lại nó vào chuỗi URL ở đây là nguồn
-- thứ hai: Công ty sửa địa chỉ ở màn hình cấu hình thì chân trang nói một đằng, bản đồ chỉ một
-- nẻo, và không có gì đỏ (luật 14). Nối chuỗi ngay lúc áp migration thì hai vế không thể lệch
-- **tại thời điểm này** — về sau vẫn phải nhập lại mã nhúng thật, xem cảnh báo cuối khối.
--
-- ⚠ MÃ HOÁ TỐI THIỂU, VÀ NÓI RÕ NÓ TỐI THIỂU: khoảng trắng → `+`, bỏ `"` và `&` (hai ký tự phá
--   được thuộc tính HTML và chuỗi truy vấn). Ký tự tiếng Việt để nguyên — trình duyệt tự mã hoá
--   phần truy vấn theo UTF-8 trước khi gửi, và Google Maps nhận đúng.
--
-- ⚠ VIẾT THẲNG HTML ĐÃ SẠCH, VÌ MIGRATION KHÔNG ĐI QUA `HtmlSanitizer`
--   Đường ghi bình thường của khoá này là API quản trị, nơi `cleanMapEmbed()` lọc thẻ và lọc
--   tên miền. Migration ghi thẳng vào bảng nên KHÔNG có lớp lọc nào — chuỗi dưới đây phải là
--   thứ mà `cleanMapEmbed()` sẽ cho qua nguyên vẹn: đúng một `<iframe>`, `src` https, tên miền
--   `www.google.com` (nằm trong `MIEN_NHUNG_BAN_DO`), và chỉ những thuộc tính có trong safelist
--   (`src`, `width`, `height`, `style`, `loading`, `allowfullscreen`).
--   ⛔ `referrerpolicy` KHÔNG có trong safelist — thêm vào đây là tạo ra một giá trị mà lượt
--      sửa kế tiếp qua màn hình quản trị sẽ lặng lẽ cắt mất, tức hai đường ghi cho hai kết quả.
--
-- ⚠ CSP: `frame-src` đã mở `https://www.google.com` từ T24.10 (`next.config.ts`), có
--   `csp.test.ts` canh. Không cần đổi gì; ghi ra đây để lượt rà sau không phải đi tìm.
--
-- ⛔ ĐỊA CHỈ RỖNG ⇒ KHÔNG GHI GÌ. Một iframe trỏ tới `maps?q=&output=embed` là bản đồ giữa Đại
--    Tây Dương — trông như đã cấu hình xong mà chỉ sai. Rỗng thì chân trang rơi về thẻ chỉ
--    đường nhỏ, đúng nhánh đã dựng.
UPDATE settings
   SET setting_value =
           '<iframe src="https://www.google.com/maps?q='
           || replace(replace(replace(
                  (SELECT btrim(setting_value) FROM settings WHERE setting_key = 'company.address'),
                  '"', ''), '&', ''), ' ', '+')
           || '&amp;output=embed" width="100%" height="100%" style="border:0" loading="lazy" allowfullscreen></iframe>',
       updated_at = now()
 WHERE setting_key = 'site.footer.map-embed'
   AND coalesce(btrim((SELECT setting_value FROM settings WHERE setting_key = 'company.address')), '') <> '';


-- ── [2] Năm bài của bộ seed vào chuyên mục con "Tin thủy lợi" ───────────────────────────────
--
-- ⭐ VÌ SAO CẦN KHỐI NÀY
--
-- Hàng chuyên mục dưới slider dựng ba ô từ các mục con của nhánh `tin-tuc`. Câu lọc của backend
-- so ĐÚNG một id danh mục — không gộp nhánh con — mà cả 5 bài seed đều chỉ gắn vào nhánh CHA
-- (`V202608251100`). Nên trên staging cả ba ô rỗng, và không ai phân biệt được "khối chưa dựng
-- xong" với "chưa ai gắn bài". Khối này làm ô đầu có nội dung để đo được.
--
-- ⛔ CHỈ GẮN VÀO "TIN THỦY LỢI", KHÔNG RẢI ĐỀU CHO BA Ô ĐẸP LƯỚI
--
-- Năm bài ấy đều là tin ngành thuỷ lợi và phòng chống thiên tai — gắn chúng vào "Tin Công ty"
-- hay "Hoạt động Đảng, đoàn thể" là xếp sai chỗ để lưới trông đầy, đúng thứ §10.54 gọi tên. Hai
-- ô kia ở lại rỗng và nói đúng lý do của mình.
--
-- ⚠ CHỌN BÀI THEO `source LIKE 'http%'` — đó là dấu hiệu ĐO ĐƯỢC của bộ seed: `V202608251100`
--   ghi URL báo gốc vào cột ấy (hanoimoi.vn, vneconomy.vn), còn bài do biên tập viên đăng thì
--   không. Liệt kê 5 slug ở đây thì có nguồn thứ hai phải nhớ cập nhật.
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id
  FROM articles a
 CROSS JOIN categories c
 WHERE a.deleted_at IS NULL
   AND a.source LIKE 'http%'
   AND c.slug = 'tin-thuy-loi'
   AND c.deleted_at IS NULL
ON CONFLICT DO NOTHING;


-- ── [3] Chốt hạ bằng số đo ──────────────────────────────────────────────────────────────────
--
-- `UPDATE`/`INSERT … ON CONFLICT` ở trên có thể chạm 0 hàng mà Flyway vẫn báo thành công —
-- đúng hình dạng §10.66. Kiểm ngay tại đây.
DO $$
DECLARE
    ma_nhung TEXT;
    so_bai   INTEGER;
BEGIN
    SELECT setting_value INTO ma_nhung FROM settings WHERE setting_key = 'site.footer.map-embed';

    IF ma_nhung IS NULL OR ma_nhung = '' THEN
        -- Không phải lỗi: `company.address` rỗng là một trạng thái hợp lệ (nhánh "chưa cấu
        -- hình" của chân trang). Nhưng phải NÓI RA, chứ không im lặng bỏ qua.
        RAISE NOTICE 'V202608291046: company.address rỗng nên KHÔNG dựng mã nhúng bản đồ — chân trang sẽ hiện thẻ chỉ đường.';
    ELSE
        -- Canh HÌNH DẠNG thứ `cleanMapEmbed()` sẽ chấp nhận, không canh độ dài chuỗi.
        IF ma_nhung NOT LIKE '<iframe src="https://www.google.com/maps?q=%output=embed"%</iframe>' THEN
            RAISE EXCEPTION 'V202608291046: mã nhúng bản đồ sai hình dạng — cleanMapEmbed() sẽ cắt mất. Nhận được: %', left(ma_nhung, 120);
        END IF;
    END IF;

    SELECT count(*) INTO so_bai
      FROM article_categories ac
      JOIN categories c ON c.id = ac.category_id
     WHERE c.slug = 'tin-thuy-loi';
    IF so_bai < 1 THEN
        RAISE EXCEPTION 'V202608291046: không bài seed nào được gắn vào tin-thuy-loi — hàng chuyên mục sẽ rỗng cả ba ô';
    END IF;
END $$;
