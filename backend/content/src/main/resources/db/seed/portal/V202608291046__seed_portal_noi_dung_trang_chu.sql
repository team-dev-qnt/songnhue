-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Nội dung khởi tạo cho trang chủ — bản đồ · chuyên mục · bài từ ảnh hoạt động — 29/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Năm khối:
--    [1] Mã nhúng bản đồ trụ sở ở chân trang, dựng từ chính `company.address`
--    [2] Gắn 5 bài của bộ seed vào chuyên mục con "Tin thủy lợi"
--    [3] 10 bài dựng từ ảnh hoạt động Công ty gửi → hai ô chuyên mục còn rỗng
--    [4] Slider trang chủ: chú thích thành liên kết bài viết + một dòng mô tả
--    [5] Chốt hạ bằng số đo
--
--  Tệp này nằm ở `db/seed/portal`, tức **chỉ chạy khi `SEED_LOCATION` trỏ vào** — staging và
--  máy dev. Production không giải được nó. Đó là chỗ đặt đúng cho cả năm khối: tất cả là dữ
--  liệu ĐỂ ĐO trên staging, không phải nội dung chính thức của Công ty.
--
--  ⛔ VÌ SAO KHÔNG ĐƯA VÀO `db/migration/cms`
--
--  Mã nhúng bản đồ chính thức thuộc **G13** (bộ nhận diện cổng) và Công ty chưa cấp. Ghi một
--  giá trị do phía phát triển chọn vào location mặc định là để nó chạy thẳng lên production và
--  **ghi đè** giá trị thật Công ty nhập sau này — một migration chạy một chiều, không hỏi ai.
--  Cùng lập luận cho 10 bài của khối [3]: chúng là chỗ giữ chỗ có ảnh thật, không phải bài
--  Công ty đã duyệt, nên không được xuất hiện trên cổng chính thức.
--
--  ⚠ SỐ HIỆU PHẢI LỚN HƠN `V202608281040` — khối [4] `UPDATE banners`, mà 5 hàng ấy do
--    `V202608281040` `INSERT` ra. Chạy trước nó thì `UPDATE` chạm ĐÚNG 0 HÀNG: không lỗi, không
--    cảnh báo, slider không có liên kết nào (§10.66). Bộ canh: `backend/tools/kiem-thu-tu-migration.sh`.
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


-- ── [3] Mười bài từ ẢNH HOẠT ĐỘNG Công ty gửi — hai ô chuyên mục còn rỗng + liên kết slider ──
--
-- ⭐⭐ MỖI BÀI CHỈ MANG ĐÚNG NHỮNG GÌ CÔNG TY ĐÃ VIẾT
--
-- Tiêu đề lấy NGUYÊN VĂN chú thích Công ty đặt trong tên tệp ảnh (`V202608281040` giữ nguyên
-- văn ấy trong `attachments.original_name`). Tóm tắt và thân bài **dựng bằng SQL từ chính tiêu
-- đề** — không câu nào viết tay, nên không có chỗ nào để một dữ kiện mới lọt vào. Không có
-- ngày diễn ra sự kiện, không có tên người dự, không có số liệu: những thứ ấy chưa ai cung cấp.
--
-- ⛔ VÌ SAO KHÔNG VIẾT THÂN BÀI CHO ĐẸP
--
-- Lượt 28/08 đã từ chối đúng việc này với đúng lý do (§10.54): ảnh là thật, chú thích là thật,
-- còn nội dung bài thì không có nguồn nào — viết ra là bịa, và một trang bịa trông y hệt một
-- trang thật. Yêu cầu 29/08 nhắc lại nên khối này được dựng, nhưng dựng theo cách **tự nói ra
-- mình đang thiếu gì**: thân bài mang tấm ảnh, mang chú thích, rồi nói thẳng rằng phần nội dung
-- chi tiết chưa được biên soạn và ai là người sẽ biên soạn.
--
-- ⭐ PHÂN CHUYÊN MỤC LÀ MỘT DỮ KIỆN CÓ SẴN, KHÔNG PHẢI MỘT LỰA CHỌN
--
-- Năm bài của "Hoạt động Đảng, đoàn thể" đều mang tên tổ chức trong chính chú thích (Đảng bộ ·
-- Đoàn TNCS · Công đoàn · Đoàn Thanh niên · Dân quân tự vệ). Năm bài còn lại là hoạt động của
-- pháp nhân Công ty. Không có bài nào phải đoán.
--
-- ⚠ `status = 'XUAT_BAN'` ghi thẳng, tức KHÔNG đi qua Workflow engine (luật 4) — cùng ngoại lệ
--   và cùng lý do với `V202608251100`: dữ liệu để ĐO trên staging. Đừng lấy khối này làm mẫu
--   cho bất kỳ đường ghi nào khác.
--
-- ⚠ `published_at` là thời điểm ĐĂNG LÊN CỔNG, không phải thời điểm diễn ra sự kiện. Ảnh chụp
--   năm 2013–2025; ngày đăng thì đặt trong tuần 19–23/08/2026 để thứ tự "mới nhất" xác định
--   được. Không có chỗ nào trong dữ liệu này khẳng định sự kiện xảy ra ngày ấy.

-- ⚠ BẢNG THƯỜNG, KHÔNG PHẢI `TEMP` — đo được ngày 29/08: `songnhue_owner` bị thu hồi quyền
--   TEMP trên CSDL (`permission denied to create temporary tables in database "songnhue"`),
--   nên `CREATE TEMP TABLE` làm cả migration dừng. Đó là một lựa chọn siết quyền có chủ đích,
--   không phải một thiếu sót để đi vá: `deploy/postgres/init/10-bootstrap.sh` chạy
--   `REVOKE ALL ON DATABASE … FROM PUBLIC` rồi chỉ cấp lại `CONNECT` + `CREATE` — `TEMP` không
--   bao giờ được cấp lại cho vai nào. Đã đo: `has_database_privilege(…,'TEMP')` trả `f` cho cả
--   bốn vai `songnhue_*`.
--   Flyway chạy mỗi migration trong MỘT giao dịch, và hai lệnh `DROP` ở cuối tệp nằm cùng giao
--   dịch ấy — nên bảng này không tồn tại một khoảnh khắc nào sau khi migration kết thúc, dù
--   thành công hay thất bại. Tên mang số hiệu migration để nếu có ai bắt gặp nó giữa chừng thì
--   biết ngay nó từ đâu ra.
CREATE TABLE seed_tmp_bai_anh_202608291046 (
    anh_id      UUID        NOT NULL,
    slug        TEXT        NOT NULL,
    tieu_de     TEXT        NOT NULL,
    chuyen_muc  TEXT        NOT NULL,
    dang_luc    TIMESTAMPTZ NOT NULL
);

INSERT INTO seed_tmp_bai_anh_202608291046 (anh_id, slug, tieu_de, chuyen_muc, dang_luc) VALUES
-- Hoạt động Đảng, đoàn thể
('fce78091-eadd-5839-a498-a28c2b6e3b87', 'dai-hoi-dai-bieu-dang-bo-khoi-doanh-nghiep-ha-noi-lan-thu-iii',
 'Đại Hội đại biểu Đảng bộ Khối Doanh nghiệp Hà Nội lần thứ III',
 'hoat-dong-dang-doan-the', '2026-08-23T02:10:00+00:00'),
('7bbec8db-906c-5250-b70d-9f38ef8dac82', 'dai-hoi-dai-bieu-doan-tncs-hcm-ubnd-thanh-pho-ha-noi-lan-thu-i',
 'Đại hội Đại biểu Đoàn TNCS HCM UBND thành phố Hà Nội lần thứ I, nhiệm kỳ 2025-2030',
 'hoat-dong-dang-doan-the', '2026-08-22T02:10:00+00:00'),
('483e1777-7ade-55ca-a8f1-1e391480ca7b', 'dai-hoi-cong-doan-cong-ty-nhiem-ky-2023-2028',
 'Đại hội Công đoàn Công ty nhiệm kỳ 2023-2028',
 'hoat-dong-dang-doan-the', '2026-08-21T02:10:00+00:00'),
('bfa61c98-4fe1-5221-86ef-474e89806544', 'doan-thanh-nien-cong-ty-tham-gia-ngay-hoi-hien-mau-nam-2023',
 'Đoàn Thanh niên Công ty tham gia Ngày hội hiến máu năm 2023',
 'hoat-dong-dang-doan-the', '2026-08-20T02:10:00+00:00'),
('fd58be19-98ac-52ed-909c-1a039527e81d', 'doi-dan-quan-tu-ve-cong-ty-tham-gia-ban-dan-that-nam-2015',
 'Đội Dân quân tự vệ Công ty tham gia bắn đạn thật năm 2015',
 'hoat-dong-dang-doan-the', '2026-08-19T02:10:00+00:00'),
-- Tin Công ty
('6ab30651-64ee-5d18-a9f8-66dde78d277e', 'le-phat-dong-tet-trong-cay-doi-doi-nho-on-bac-ho-xuan-giap-thin-nam-2024',
 'Lễ phát động Tết trồng cây - Đời đời nhớ ơn Bác Hồ Xuân Giáp Thìn - năm 2024',
 'tin-cong-ty', '2026-08-23T03:10:00+00:00'),
('3c6aef03-c1d5-5a8e-9d45-0510997408db', 'bo-truong-bo-nn-ptnt-le-minh-hoan-kiem-tra-cong-tac-phong-chong-thien-tai-nam-2024',
 'Bộ trưởng Bộ NN&PTNT Lê Minh Hoan kiểm tra công tác phòng chống thiên tai năm 2024 tại trạm bơm Yên Nghĩa',
 'tin-cong-ty', '2026-08-22T03:10:00+00:00'),
('8301221d-be30-595d-8d46-9dce8c029445', 'cat-bang-khanh-thanh-tram-bom-ngoai-do-ii',
 'Thứ trưởng Bộ NN&PTNN Hoàng Văn Thắng và Phó Chủ tịch UBND Thành phố Hà Nội Trần Xuân Việt cắt băng khánh thành Trạm bơm Ngoại Độ II',
 'tin-cong-ty', '2026-08-21T03:10:00+00:00'),
('947251cc-22a3-5fb9-9b27-8f71827dab3c', 'cong-ty-tham-dong-vien-quan-va-dan-quan-dao-truong-sa-nam-2025',
 'Công ty thăm, động viên quân và dân quần đảo Trường Sa; cán bộ, chiến sĩ Nhà giàn DK-1 năm 2025',
 'tin-cong-ty', '2026-08-20T03:10:00+00:00'),
('9dc02a24-0085-57ee-995e-30ec47aee7c5', 'lap-dat-khan-cap-may-bom-da-chien-tren-song-cau-nga',
 'Lắp đặt khẩn cấp máy bơm dã chiến trên sông Cầu Ngà chống ngập lụt khu dân cư',
 'tin-cong-ty', '2026-08-19T03:10:00+00:00');

-- ⛔ Ảnh phải TỒN TẠI trong `attachments` trước khi bài trỏ vào nó. Thiếu một ảnh thì bài ấy có
--    ô ảnh bìa rỗng trên cổng — hỏng câm, đúng loại khó truy nhất. Dừng ngay tại đây thay vì
--    dựng ra một trang chủ trông đầy mà một ô trống.
DO $$
DECLARE thieu INTEGER;
BEGIN
    SELECT count(*) INTO thieu
      FROM seed_tmp_bai_anh_202608291046 t
     WHERE NOT EXISTS (SELECT 1 FROM attachments a WHERE a.public_id = t.anh_id);
    IF thieu > 0 THEN
        RAISE EXCEPTION 'V202608291046: % ảnh của khối [3] không có trong attachments — chạy V202608281040 trước', thieu;
    END IF;
END $$;

-- Tóm tắt và thân bài: MỘT công thức, áp cho cả mười bài.
--
-- ⛔ TÓM TẮT KHÔNG NHẮC LẠI TIÊU ĐỀ. Bản đầu ghép nguyên tiêu đề vào giữa câu tóm tắt, và đo
--    trên slider thì thấy ngay: thẻ chú thích in tiêu đề ở dòng trên rồi in lại đúng chuỗi ấy ở
--    dòng dưới — một câu dài gấp đôi mà không nói thêm gì. Tóm tắt ở đây nói **xuất xứ và tình
--    trạng**, tức đúng phần người đọc chưa biết; tiêu đề nói nội dung.
--
CREATE VIEW seed_tmp_bai_du_202608291046 AS
SELECT t.*,
       'Ảnh tư liệu hoạt động do Công ty cung cấp ngày 27/08/2026; tiêu đề giữ nguyên văn chú '
       || 'thích Công ty đặt cho tấm ảnh. Nội dung chi tiết của bài chưa được biên soạn.' AS tom_tat,
       -- ⛔ THÂN BÀI KHÔNG CHÈN LẠI TẤM ẢNH. Trang chi tiết đã vẽ ảnh bìa từ
       --    `cover_attachment_public_id` ngay dưới tiêu đề; thêm một `<figure>` mang đúng ảnh ấy
       --    là người đọc thấy cùng một bức hai lần liền nhau — đo được trên `/bai-viet/…` ở bản
       --    đầu. Chú thích cũng không mất gì: `<figcaption>` khi ấy chỉ chép lại tiêu đề.
       '<p>Ảnh tư liệu do Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ cung cấp ngày '
       || '27/08/2026. Tiêu đề của bài lấy nguyên văn chú thích Công ty đặt cho tấm ảnh.</p>' || chr(10)
       || '<p>Phần nội dung chi tiết chưa được cung cấp nên chưa có ở đây. Biên tập viên của Công ty '
       || 'soạn và thay khối chữ này ở màn hình Bài viết của trang quản trị.</p>' AS than_bai
  FROM seed_tmp_bai_anh_202608291046 t;

INSERT INTO articles (title, slug, summary, content, cover_attachment_public_id, source, status,
        published_at, meta_title, meta_description, meta_keywords, author_user_id, created_by)
SELECT d.tieu_de, d.slug, d.tom_tat, d.than_bai, d.anh_id,
       'Ảnh hoạt động Công ty cung cấp 27/08/2026', 'XUAT_BAN', d.dang_luc,
       -- ⚠ `meta_description` lấy TIÊU ĐỀ chứ không lấy tóm tắt: tóm tắt nay giống nhau ở cả
       --   mười bài, mà mười trang cùng một mô tả meta là mười trang trùng lặp dưới mắt máy tìm
       --   kiếm. Tiêu đề là dữ kiện duy nhất khác nhau giữa các bài này.
       left(d.tieu_de, 70), left(d.tieu_de, 160), 'seeding, article, hoat dong cong ty',
       (SELECT id FROM users WHERE username = 'superadmin'),
       (SELECT id FROM users WHERE username = 'superadmin')
  FROM seed_tmp_bai_du_202608291046 d
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, d.tieu_de, d.slug, d.tom_tat, d.than_bai, d.anh_id,
       left(d.tieu_de, 70), left(d.tieu_de, 160), 'seeding, article, hoat dong cong ty',
       'Nội dung seed cho staging', a.created_by
  FROM articles a
  JOIN seed_tmp_bai_du_202608291046 d ON d.slug = a.slug
 WHERE NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- ⛔ Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
--    `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
--    INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a
   SET published_version_id = v.id
  FROM article_versions v, seed_tmp_bai_anh_202608291046 t
 WHERE v.article_id = a.id AND a.slug = t.slug AND a.published_version_id IS NULL;

-- Gắn CẢ nhánh cha (`tin-tuc`, nuôi cột tin cạnh slider) LẪN chuyên mục con (nuôi hàng chuyên
-- mục). Backend so đúng một id danh mục, không gộp nhánh con — thiếu vế nào là mất hẳn một khối.
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id
  FROM articles a
  JOIN seed_tmp_bai_anh_202608291046 t ON t.slug = a.slug
  JOIN categories c ON c.slug IN ('tin-tuc', t.chuyen_muc) AND c.deleted_at IS NULL
ON CONFLICT DO NOTHING;


-- ── [4] Slider trang chủ: chú thích thành LIÊN KẾT, kèm một dòng mô tả ──────────────────────
--
-- Yêu cầu 29/08: *"phần text sẽ giống như 1 thẻ link, có thể click được vào bài viết tương
-- ứng"*. Vế giao diện đã có sẵn (`AnhCarousel` bọc tiêu đề trong `<a>` khi `linkUrl` có giá
-- trị); thứ thiếu là DỮ LIỆU — `V202608281040` chỉ ghi `title`, để trống cả `link_url` lẫn
-- `description`.
--
-- ⚠ NỐI BẰNG `image_attachment_public_id`, không bằng tiêu đề: tiêu đề là chuỗi người sửa được
--   ở màn hình Banner, còn id ảnh là khoá ngoại. Nối bằng chuỗi thì lượt sửa chính tả đầu tiên
--   làm câu lệnh này chạm 0 hàng — im lặng.
--
-- ⛔ KHÔNG giẫm lên banner biên tập viên đã tự đặt liên kết (`link_url` đã có giá trị).
UPDATE banners b
   SET description = d.tom_tat,
       link_url    = '/bai-viet/' || d.slug,
       updated_at  = now()
  FROM seed_tmp_bai_du_202608291046 d
 WHERE b.image_attachment_public_id = d.anh_id
   AND b.deleted_at IS NULL
   AND coalesce(b.link_url, '') = ''
   AND coalesce(b.description, '') = '';

-- Ảnh thứ năm của slider — "Cống Liên Mạc - Đầu nguồn Sông Nhuệ" — là ảnh CÔNG TRÌNH, không phải
-- ảnh một sự kiện, nên nó KHÔNG có bài viết tương ứng và cố ý không có liên kết. Đây cũng là
-- lượt đi qua nhánh "ảnh không có liên kết" của slider — một nhánh chưa ai đi qua thì chưa biết
-- nó đúng hay sai (luật 7).
UPDATE banners
   SET description = 'Ảnh tư liệu công trình do Công ty cung cấp. Chưa có bài viết tương ứng.',
       updated_at  = now()
 WHERE image_attachment_public_id = '0d748e75-cf9d-5914-94a3-394c02a9d407'
   AND deleted_at IS NULL
   AND coalesce(description, '') = '';

-- ── [5] Chốt hạ bằng số đo ──────────────────────────────────────────────────────────────────
--
-- Mọi câu `UPDATE` và `INSERT … ON CONFLICT` ở trên có thể chạm **0 hàng** mà Flyway vẫn báo
-- thành công — đúng hình dạng §10.66, và đúng cách bốn khối trên đây hỏng nếu một khoá lệch.
-- Kiểm ngay tại đây, bằng SỐ ĐẾM chứ không bằng sự tồn tại.
DO $$
DECLARE
    ma_nhung  TEXT;
    so_bai    INTEGER;
    so_dang   INTEGER;
    so_cty    INTEGER;
    so_lien   INTEGER;
    so_mo_ta  INTEGER;
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

    -- [2] Ô "Tin thủy lợi"
    SELECT count(*) INTO so_bai
      FROM article_categories ac JOIN categories c ON c.id = ac.category_id
     WHERE c.slug = 'tin-thuy-loi';
    IF so_bai < 1 THEN
        RAISE EXCEPTION 'V202608291046: không bài seed nào được gắn vào tin-thuy-loi — hàng chuyên mục sẽ rỗng cả ba ô';
    END IF;

    -- [3] Hai ô còn lại. ⭐ Ngưỡng là 2 vì `site.home.category-news-count` chốt ở 2: dưới ngưỡng
    --     ấy thì ô vẫn hiện nhưng thiếu bài, tức khối "đã dựng xong" mà người xem thấy một nửa.
    --     Câu này chỉ đếm bài ĐANG XUẤT BẢN và ĐÃ có `published_version_id` — đúng bộ điều kiện
    --     truy vấn công khai dùng; đếm hàng trong `articles` thì xanh cả khi cổng hiển thị rỗng.
    SELECT count(*) INTO so_dang
      FROM articles a
      JOIN article_categories ac ON ac.article_id = a.id
      JOIN categories c ON c.id = ac.category_id AND c.slug = 'hoat-dong-dang-doan-the'
     WHERE a.deleted_at IS NULL AND a.status = 'XUAT_BAN' AND a.published_version_id IS NOT NULL;

    SELECT count(*) INTO so_cty
      FROM articles a
      JOIN article_categories ac ON ac.article_id = a.id
      JOIN categories c ON c.id = ac.category_id AND c.slug = 'tin-cong-ty'
     WHERE a.deleted_at IS NULL AND a.status = 'XUAT_BAN' AND a.published_version_id IS NOT NULL;

    IF so_dang < 2 OR so_cty < 2 THEN
        RAISE EXCEPTION 'V202608291046: hai ô chuyên mục cần ít nhất 2 bài xuất bản mỗi ô, đếm được đảng/đoàn thể=% · công ty=%', so_dang, so_cty;
    END IF;

    -- [4] Slider. Bốn ảnh có bài tương ứng ⇒ bốn liên kết; ảnh thứ năm là ảnh công trình, cố ý
    --     KHÔNG có liên kết — nên `= 4`, không phải `>= 1`. Một khẳng định không phân biệt được
    --     hai trạng thái thì không khẳng định gì (luật 9).
    SELECT count(*) INTO so_lien FROM banners
     WHERE deleted_at IS NULL AND link_url LIKE '/bai-viet/%';
    IF so_lien <> 4 THEN
        RAISE EXCEPTION 'V202608291046: slider phải có đúng 4 ảnh trỏ vào bài viết, đếm được %', so_lien;
    END IF;

    -- Cả năm ảnh phải có mô tả — kể cả ảnh không liên kết. Thẻ chữ của slider cao bằng nội dung
    -- nó mang, nên một ảnh thiếu mô tả là một khung nhảy chiều cao mỗi lượt chuyển ảnh.
    SELECT count(*) INTO so_mo_ta FROM banners
     WHERE deleted_at IS NULL AND coalesce(btrim(description), '') <> '';
    IF so_mo_ta <> 5 THEN
        RAISE EXCEPTION 'V202608291046: cả 5 ảnh slider phải có mô tả, đếm được %', so_mo_ta;
    END IF;

    -- ⛔ Và KHÔNG bài nào của khối [3] được mang một dữ kiện ngoài chú thích: thân bài phải nói
    --    ra rằng nó chưa có nội dung. Đây là chỗ chặn của §10.54 ở dạng đếm được — ai đó "viết
    --    thêm cho đẹp" thì câu này đỏ.
    SELECT count(*) INTO so_bai
      FROM articles
     WHERE source = 'Ảnh hoạt động Công ty cung cấp 27/08/2026'
       AND deleted_at IS NULL
       AND content NOT LIKE '%chưa được cung cấp%';
    IF so_bai > 0 THEN
        RAISE EXCEPTION 'V202608291046: % bài ảnh hoạt động không còn câu nói rõ nội dung chi tiết chưa có — xem §10.54', so_bai;
    END IF;
END $$;


DROP VIEW seed_tmp_bai_du_202608291046;
DROP TABLE seed_tmp_bai_anh_202608291046;
