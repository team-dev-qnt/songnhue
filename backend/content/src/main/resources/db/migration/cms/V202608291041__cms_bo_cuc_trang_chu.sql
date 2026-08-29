-- =============================================================================
-- Đợt bố cục trang chủ 29/08/2026 — MỘT migration cho cả đợt
--
-- ⭐ VÌ SAO GỘP, VÀ VÌ SAO GỘP ĐƯỢC
--
-- Đợt này ban đầu là NĂM tệp: V202608291041 (chuyển "Hoạt động Đảng, đoàn thể"),
-- …1042 (đầu trang hai dòng), …1043 (bảng `contacts`), …1044 (danh mục nguồn của
-- khối tin), …1045 (số bài dải chữ chạy). Năm tệp cho một đợt làm việc kéo dài nửa
-- ngày là năm lần phải hỏi "tệp này đã lên staging chưa" thay vì một lần.
--
-- ⛔ Gộp được vì ĐO ĐƯỢC là chưa tệp nào rời khỏi máy dev:
--
--     nhánh `staging` (22876c8) — migration cms đỉnh: V202608281039
--     lượt CD Staging gần nhất : 27/08, cho `fix(db)` …281040
--     `origin/dev` (9a09eac)    : có 1041→1044, NHƯNG CD Staging chạy trên nhánh
--                                 `staging`, không chạy trên `dev`
--
--   Tức năm tệp ấy chỉ từng chạy trên CSDL dev cục bộ. Gộp một migration ĐÃ áp ở
--   staging thì Flyway `validate` đỏ vĩnh viễn với "applied migration not resolved"
--   (§10.65 cùng họ) — lúc ấy đường ra duy nhất là dựng lại CSDL.
--
-- ⚠ HỆ QUẢ CHO MÁY DEV ĐÃ ÁP BẢN CŨ
--
--   CSDL nào đã áp 1041→1045 rời sẽ thấy "applied migration not resolved". Cách xử
--   lý: `make reset-db` (dựng lại từ đầu — đúng đường bộ test vẫn đi), hoặc gỡ tay
--   năm dòng lịch sử rồi để tệp này chạy lại:
--
--     DELETE FROM flyway_schema_history
--      WHERE version IN ('202608291041','202608291042','202608291043',
--                        '202608291044','202608291045');
--
--   Mọi lệnh dưới đây viết theo kiểu HỘI TỤ (không lọc theo trạng thái đầu vào,
--   `ON CONFLICT DO NOTHING`, `IF NOT EXISTS`) nên chạy lại trên CSDL đã có bản cũ
--   là an toàn — trừ `CREATE TABLE contacts`, xem ghi chú tại chỗ.
--
-- ⚠ SAU KHI KÉO BẢN NÀY VỀ: chạy `cd backend && ./mvnw clean` MỘT LẦN.
--
--   Maven chép tài nguyên vào `target/classes` nhưng KHÔNG xoá thứ đã bị gỡ khỏi mã nguồn, nên
--   bốn tệp cũ còn nằm lại ở đó. Flyway đọc classpath, thấy hai tệp cùng version …1041 và dừng
--   ứng dụng với `Found more than one migration with version 202608291041` — một thông báo không
--   hề gợi ý rằng thủ phạm là bản dịch cũ chứ không phải mã nguồn. Đo được ngay ở lượt
--   `make ci-local` đầu tiên sau khi gộp: 178 lỗi, tất cả cùng một gốc.
--   ⛔ Lượt build image Docker KHÔNG dính lỗi này (multi-stage, cây sạch) — tức nó chỉ hiện ra ở
--      máy dev, đúng nhóm lỗi "xanh ở máy khác trạng thái với xanh ở runner".
--
-- ⛔ SỐ HIỆU: giữ nguyên …1041 chứ không nhảy lên số mới. Nó đã là số nhỏ nhất của
--    đợt, lớn hơn đỉnh của staging (…281040), và giữ nguyên thì bộ canh thứ tự
--    (`kiem-thu-tu-migration.sh`) không thấy một "migration mới" nào lùi về sau.
-- =============================================================================


-- ═════════════════════════════════════════════════════════════════════════════
-- [1] "Hoạt động Đảng, đoàn thể" thành mục CON của "Tin tức – Sự kiện"
-- ═════════════════════════════════════════════════════════════════════════════
--
-- Yêu cầu 29/08. Cây nội dung §3 xếp nó là mục cấp 1 thứ tư; đo trên thanh điều
-- hướng thì đó là nhãn DÀI NHẤT (24 ký tự) trong tám mục, và nội dung của nó vốn là
-- tin — cùng loại với "Tin thủy lợi" và "Tin Công ty".
--
-- ⚠ VÌ SAO PHẢI LÀ MIGRATION, KHÔNG PHẢI SỬA GIAO DIỆN
--   Thanh điều hướng KHÔNG viết trong mã: `SiteHeader` gọi `getMenu('HEADER')` rồi
--   `buildMenuTree`. Đổi thứ bậc bằng cách sửa component là dựng một cây thứ hai
--   cạnh cây trong CSDL — hai nơi trả lời cùng một câu hỏi (luật 14). Sau lượt này
--   Công ty vẫn kéo-thả lại được ở màn hình quản trị.
--
-- ⚠ CHUYỂN CẢ DANH MỤC, KHÔNG CHỈ MỤC MENU
--   `menu_items` và `categories` là hai cây khác nhau. Chuyển mỗi mục menu thì thanh
--   điều hướng nói "nằm trong Tin tức" còn trang `/danh-muc/hoat-dong-dang-doan-the`
--   vẫn nói "mục gốc". Slug KHÔNG đổi nên mọi địa chỉ đã phát hành vẫn sống.
-- -----------------------------------------------------------------------------

-- ⚠⚠ QUY ƯỚC PATH: con = path của cha NỐI thêm id của chính nó — `/16/` → `/16/17/`.
--
-- Bản nháp viết `path = p.path`, tức con mang ĐÚNG path của cha. Đọc `V202608271031`
-- thì tưởng vậy (khối INSERT cấp 2 dùng `p.path`), nhưng đó chưa phải trạng thái
-- cuối — có một lượt UPDATE sau đó nối id con vào. Đo trên CSDL thật mới thấy: mọi
-- mục cấp 2 đang có path dạng `/15/22/`.
--
-- Hậu quả của bản sai KHÔNG phải "thứ tự sai" mà là **thứ tự không xác định**:
-- `MenuService.tree()` sắp `ORDER BY path ASC, sort_order ASC`, nên con và cha cùng
-- path `/16/` và cùng `sort_order` 30 thành một cặp hoà — Postgres trả thứ tự nào
-- cũng được. `buildMenuTree` ở public-web duyệt MỘT lượt, gặp con trước cha là con
-- rơi ra ngoài cây. `SiteLayoutTest.pathCuaMenuSeedDung` bắt được.
UPDATE menu_items c
   SET parent_id  = p.id,
       depth      = 1,
       path       = p.path || c.id || '/',
       -- ⚠⚠ 5 chứ không phải 30, và lý do là một KHIẾM KHUYẾT ĐO ĐƯỢC của lược đồ.
       --
       -- `MenuService.tree()` sắp `ORDER BY path ASC, sort_order ASC`. Mọi mục cùng
       -- một cha có path KHÁC nhau (mỗi mục nối id của chính nó), nên `path` quyết
       -- định xong trước khi `sort_order` được nhìn tới — tức **`sort_order` không
       -- có tác dụng giữa các mục cùng cấp**, thứ tự thật là thứ tự id.
       --
       -- Các nhánh cũ không lộ ra vì id của chúng tăng đúng theo thứ tự mong muốn
       -- (22,23,24,25). Mục này có id nhỏ hơn hai anh em (nó vốn là mục cấp 1 tạo
       -- sớm hơn) nên nó hiện ĐẦU danh sách dù đặt sort_order bao nhiêu.
       --
       -- Đặt 5 để giá trị lưu KHỚP thứ tự thật sự hiển thị. Đặt 30 thì CSDL nói
       -- "cuối" mà màn hình cho ra "đầu", và người sửa menu lần sau đuổi theo một
       -- con ma. ⛔ Khiếm khuyết gốc ghi thành T26.25.
       sort_order = 5,
       updated_at = now()
  FROM menu_items p
 WHERE c.position = 'HEADER'
   AND c.label    = 'Hoạt động Đảng, đoàn thể'
   -- ⚠⚠ KHÔNG lọc `c.depth = 0`.
   --
   -- Lọc theo trạng thái đầu vào làm migration chỉ chạy đúng từ ĐÚNG MỘT trạng
   -- thái. Đo được trên CSDL dev 29/08: bản nháp đã áp và để lại mục ở depth = 1
   -- với path SAI; lượt áp lại của bản đã sửa khớp 0 hàng, path vẫn sai, và khối
   -- kiểm ở cuối ném lỗi — tức migration tự chặn chính nó.
   --
   -- Viết theo kiểu HỘI TỤ: bất kể mục đang ở đâu, sau lượt này nó nằm đúng chỗ.
   AND c.deleted_at IS NULL
   AND p.position = 'HEADER'
   AND p.label    = 'Tin tức – Sự kiện'
   AND p.depth    = 0
   AND p.deleted_at IS NULL;

-- Bảy mục cấp 1 còn lại: đánh số lại 10..70 để không còn khoảng trống ở vị trí 40.
-- Khoảng trống không sai, nhưng màn hình quản trị hiện số thứ tự và một dãy nhảy
-- cóc luôn khiến người sửa tưởng mình xoá nhầm.
WITH danh_sach AS (
    SELECT id, row_number() OVER (ORDER BY sort_order, id) * 10 AS thu_tu
      FROM menu_items
     WHERE position = 'HEADER' AND depth = 0 AND deleted_at IS NULL
)
UPDATE menu_items m
   SET sort_order = d.thu_tu,
       updated_at = now()
  FROM danh_sach d
 WHERE m.id = d.id AND m.sort_order <> d.thu_tu;

-- Quy ước path của `categories`: con = path của cha NỐI thêm id của chính nó
-- (xem V202608271031, khối "Cấp 2 của Công bố thông tin").
UPDATE categories c
   SET parent_id   = p.id,
       depth       = 1,
       path        = p.path || c.id || '/',
       sort_order  = 30,
       updated_at  = now()
  FROM categories p
  WHERE c.slug = 'hoat-dong-dang-doan-the'
    AND c.deleted_at IS NULL
    AND p.slug = 'tin-tuc'
    AND p.deleted_at IS NULL;


-- ═════════════════════════════════════════════════════════════════════════════
-- [2] Đầu trang hai dòng: cơ quan chủ quản + tên Công ty
-- ═════════════════════════════════════════════════════════════════════════════
--
--     UỶ BAN NHÂN DÂN THÀNH PHỐ HÀ NỘI
--     CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THUỶ LỢI SÔNG NHUỆ
--
-- ⛔⛔ CHỮ HOA NẰM TRONG GIÁ TRỊ, KHÔNG NẰM TRONG CSS
--   CR-42 đã chốt: giao diện hiện NGUYÊN VĂN giá trị trong `settings`, không ép
--   `uppercase` — "ép hoa ở đây là giao diện tự quyết định thay người nhập".
--   `noForcedUppercase` canh đúng luật ấy. Nên chữ hoa phải do NGƯỜI NHẬP đặt, tức
--   nằm ở cột `setting_value` này. Công ty muốn đổi lại chữ thường thì sửa ở màn
--   hình cấu hình, không phải sửa mã và deploy.
--
-- ⚠ VÌ SAO KHÔNG VIẾT HOA THẲNG `site.name`
--   `site.name` còn chạy vào `generateMetadata()` → `<title>` mặc định và `og:title`
--   (layout.tsx). Viết hoa nó là tab trình duyệt và thẻ chia sẻ mạng xã hội cũng hoá
--   ALL CAPS — chỗ mà chữ hoa đọc như đang hét.
--
-- ⚠ `site.header.display-name` ĐỂ RỖNG THÌ RƠI VỀ `site.name`
--   Hai khoá cùng chở tên Công ty là hai chỗ phải nhớ (luật 14). Giảm thiểu bằng
--   cách để khoá mới là một BẢN GHI ĐÈ có chủ đích: rỗng ⇒ đầu trang dùng
--   `site.name`, và lúc ấy chỉ còn đúng một nguồn.
-- -----------------------------------------------------------------------------
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, editable, exportable, sort_order
)
VALUES
    ('site.header.parent-org',
     'UỶ BAN NHÂN DÂN THÀNH PHỐ HÀ NỘI', 'STRING', '',
     'SITE', 'Cơ quan chủ quản (dòng trên đầu trang)',
     'Hiện nguyên văn ở dòng đầu của đầu trang. Để rỗng thì đầu trang chỉ còn một dòng.',
     TRUE, TRUE, 11),

    ('site.header.display-name',
     'CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THUỶ LỢI SÔNG NHUỆ', 'STRING', '',
     'SITE', 'Tên hiển thị ở đầu trang (ghi đè)',
     'Chỉ đặt khi muốn đầu trang khác với "Tên cổng thông tin". Để RỖNG thì đầu trang '
     || 'dùng đúng site.name — và khi đó chỉ có một nguồn duy nhất cho tên Công ty. '
     || 'Giá trị hiện nguyên văn: muốn chữ hoa thì nhập chữ hoa.',
     TRUE, TRUE, 12)
ON CONFLICT (setting_key) DO NOTHING;


-- ═════════════════════════════════════════════════════════════════════════════
-- [3] Bảng tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4
-- ═════════════════════════════════════════════════════════════════════════════
--
-- ⚠ VÌ SAO BẢNG NÀY RA ĐỜI CÙNG LÚC VỚI BIỂU MẪU, KHÔNG SAU
--   Chú thích cũ ở `app/lien-he/page.tsx` nói đúng: *"một form gửi đi mà không ai
--   nhận tệ hơn hẳn không có form: người dân tin là đã gửi được"*. Nên dựng ĐỦ vòng
--   khép kín — nhập → lưu → đọc được ở màn hình quản trị — chứ không dựng ô nhập
--   trước rồi hẹn phần sau (luật 27).
--
-- ⛔ PHẦN CÒN LẠI CỦA CN-01.4 CHƯA DỰNG, VÀ ĐƯỢC GHI RA THAY VÌ ĐỂ IM
--   • reCAPTCHA v3 — chặn bởi G13. Trong lúc chờ, chống lạm dụng dựa vào
--     `RateLimitPolicy.PUBLIC` sẵn có trên tiền tố /api/v1/public.
--   • Email báo có liên hệ mới + email xác nhận cho người gửi.
--   • Bốn trạng thái sau `DA_DOC`, phân loại, chuyển phòng ban, ghi chú nội bộ,
--     xuất Excel, nhắc SLA.
--   Bốn trạng thái ấy VẪN nằm trong ràng buộc CHECK dưới đây dù chưa dùng: thêm giá
--   trị vào một CHECK đang chạy tốn một migration nữa, để sẵn thì không tốn gì.
--   ⚠ Nhưng CHỈ enum là để sẵn — không dựng cột nào cho chức năng chưa có (luật 15).
--
-- ⛔ KHÔNG LƯU ĐỊA CHỈ IP NGƯỜI GỬI
--   IP là dữ liệu cá nhân theo NĐ 13/2023, và ở đây nó không phục vụ mục đích nào đã
--   công bố: chống lạm dụng đã do bộ lọc tần suất lo, ngay trong bộ nhớ, không lưu.
--   Thu thập "để đó phòng khi cần" chính là thứ nghị định ấy cấm.
--
-- ⚠ `IF NOT EXISTS`: khối này là phần DUY NHẤT của tệp không hội tụ được bằng
--   `ON CONFLICT` — một CSDL dev đã áp bản …1043 rời sẽ có sẵn bảng. Không có nó
--   thì lượt áp lại chết ở "relation already exists".
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS contacts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL DEFAULT gen_random_uuid(),

    full_name    VARCHAR(255) NOT NULL,
    -- Một trong hai phải có, ràng buộc ở dưới: không có đường liên lạc ngược thì
    -- bản ghi này là một lời nhắn không thể trả lời.
    email        VARCHAR(255),
    phone        VARCHAR(30),
    subject      VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,

    status       VARCHAR(20)  NOT NULL DEFAULT 'MOI',
    -- Người đọc bản ghi đầu tiên. NULL = chưa ai mở.
    read_by      BIGINT,
    read_at      timestamptz,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   timestamptz,
    updated_by   BIGINT,
    deleted_at   timestamptz,
    version      INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_contacts_status CHECK (
        status IN ('MOI', 'DA_DOC', 'DANG_XU_LY', 'DA_PHAN_HOI', 'DONG', 'LUU_TRU')
    ),
    -- Phải có ít nhất một đường liên lạc ngược.
    CONSTRAINT ck_contacts_lien_lac CHECK (
        (email IS NOT NULL AND length(btrim(email)) > 0)
        OR (phone IS NOT NULL AND length(btrim(phone)) > 0)
    ),
    CONSTRAINT uq_contacts_public_id UNIQUE (public_id)
);

-- Màn hình quản trị luôn mở theo "mới nhất trước, chưa đọc lên đầu".
CREATE INDEX IF NOT EXISTS ix_contacts_status_created ON contacts (status, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE contacts IS
    'Liên hệ / phản ánh gửi từ cổng công khai (CN-01.4). KHÔNG lưu IP người gửi — NĐ 13/2023.';
COMMENT ON COLUMN contacts.status IS
    'MOI → DA_DOC là hai trạng thái ĐANG dùng. Bốn giá trị còn lại để sẵn trong CHECK cho phần sau của CN-01.4, chưa có mã nào ghi vào.';


-- ═════════════════════════════════════════════════════════════════════════════
-- [4] Ba tham số của trang chủ — hàng chuyên mục tin và dải chữ chạy
-- ═════════════════════════════════════════════════════════════════════════════
--
-- ⭐ VÌ SAO PHẢI CÓ KHOÁ, THAY VÌ VIẾT 'tin-tuc' VÀ SỐ 10 VÀO MÃ GIAO DIỆN
--   Trước đợt này `page.tsx` truyền thẳng `categorySlug="tin-tuc"` + tiêu đề
--   "Tin tức – Sự kiện", còn `SiteHeader.tsx` có `const SO_TIN_CHAY = 10`. Ba giá
--   trị nghiệp vụ nằm trong mã: Công ty đổi tên nhánh danh mục (chuyện của một buổi
--   chiều trên màn hình quản trị) là khối trang chủ trỏ vào slug đã chết — và không
--   có lỗi nào để ai nhìn thấy, khối chỉ lặng lẽ nói "chưa có bài viết nào".
--   Cùng lý lẽ đã dùng cho `site.home.documents-category` ở V202608271032 (quy tắc 12).
--
-- ⛔ KHÔNG seed tham số cho tính năng chưa dựng (quy tắc 15). Cả ba khoá dưới đây có
--    nơi đọc NGAY trong đợt này — `app/page.tsx` và `components/SiteHeader.tsx`.
--    `PortalSettingsReadTest` quét cả thư mục migration rồi đối chiếu với mã cổng,
--    nên một khoá không ai đọc là một bài kiểm đỏ chứ không phải việc để dành.
--
-- ⛔ `site.home.marquee-count` = 0 KHÔNG phải công tắc tắt dải, và phần mô tả nói
--    đúng như thế. Bản nháp ghi "Đặt 0 để tắt hẳn dải chữ chạy"; đo trên stack đang
--    chạy thì sai — với 0 bài, `PortalInfoStrip` vẫn còn hai mục "Giờ làm việc" và
--    "Thư điện tử" lấy từ `company.*`, nên dải vẫn chạy. Một dòng mô tả hứa điều mã
--    không làm nằm ngay trên màn hình cấu hình, người vận hành đọc nó như một cam
--    kết, và không bài kiểm nào đối chiếu chữ với hành vi.
--
-- ⚠ Trần 20 của dải chữ chạy: danh sách được vẽ HAI LẦN để nối vòng liền mạch (xem
--   `PortalTicker`), nên mỗi bài thêm vào là hai nút DOM nữa trên đường tới hạn của
--   trang chủ — thứ DOD1.17 (< 3s) đang đếm từng KB.
-- -----------------------------------------------------------------------------
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    -- Nhánh danh mục nuôi CẢ hai khối tin của trang chủ: cột "Tin tức – Sự kiện"
    -- cạnh slider và hàng chuyên mục ngay dưới nó. Nhãn hiển thị KHÔNG nằm ở đây —
    -- nó lấy từ nhãn mục menu tương ứng, để thanh điều hướng và trang chủ không thể
    -- gọi cùng một nhánh bằng hai cái tên khác nhau (luật 14).
    ('site.home.news-category', 'tin-tuc', 'STRING',
     'SITE', 'Danh mục nguồn của khối tin trang chủ',
     'Slug nhánh danh mục. Các mục menu con của nhánh này dựng thành hàng chuyên mục dưới slider.',
     '^[a-z0-9-]+$', 97),

    -- ⭐ 2, không phải 4 — Công ty chốt sổ ngày 29/08: *"chỉ hiển thị mỗi column 2 bài viết
    --   mới nhất"*. Hàng chuyên mục vẽ bài đầu bằng ảnh lớn rồi các bài sau bằng dòng ảnh
    --   nhỏ, nên 2 cho ra đúng một ảnh lớn + một dòng: ba cột cao gần bằng nhau với MỌI bộ
    --   nội dung. Ở 4, cột nào đủ bài thì cao gấp đôi cột chưa có bài — thứ đo được trên
    --   stack sáng 29/08.
    ('site.home.category-news-count', '2', 'INTEGER',
     'SITE', 'Số bài mỗi chuyên mục ở hàng chuyên mục trang chủ',
     'Áp cho từng ô chuyên mục con. Ô nào chưa có bài thì nói thẳng là chưa có, không mượn bài của ô khác.',
     '^([1-9]|1[0-2])$', 98),

    ('site.home.marquee-count', '10', 'INTEGER',
     'SITE', 'Số bài trên dải chữ chạy dưới thanh điều hướng',
     'Số tiêu đề bài mới nhất chạy ngang dưới thanh điều hướng. Đặt 0 thì dải chỉ còn giờ làm việc và thư điện tử — muốn bỏ hẳn hai mục ấy thì xoá giá trị của company.working-hours và company.email.',
     '^(0|[1-9]|1[0-9]|20)$', 99)
) AS v(k, val, vtype, grp, label, descr, validation, ord)
ON CONFLICT (setting_key) DO NOTHING;


-- ═════════════════════════════════════════════════════════════════════════════
-- [5] Chốt hạ bằng SỐ ĐO, không bằng lời hứa
-- ═════════════════════════════════════════════════════════════════════════════
--
-- Migration này chạy trên CSDL ĐÃ CÓ DỮ LIỆU, nên các lệnh trên có thể khớp 0 hàng
-- mà Flyway vẫn báo thành công — đúng lỗi §10.66 (seed ghi vào khoá chưa tồn tại,
-- 0 hàng, không một dòng log). `ON CONFLICT DO NOTHING` cũng nuốt mọi va chạm trong
-- im lặng. Kiểm ngay tại đây và DỪNG nếu sai.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    so_menu  INTEGER;
    so_cat   INTEGER;
    so_cap1  INTEGER;
    so_khoa  INTEGER;
BEGIN
    -- [1] Mục menu và danh mục đã nằm đúng chỗ
    SELECT count(*) INTO so_menu
      FROM menu_items c JOIN menu_items p ON p.id = c.parent_id
     WHERE c.position = 'HEADER' AND c.label = 'Hoạt động Đảng, đoàn thể'
       AND c.depth = 1 AND p.label = 'Tin tức – Sự kiện' AND c.deleted_at IS NULL;
    IF so_menu <> 1 THEN
        RAISE EXCEPTION 'Mục menu "Hoạt động Đảng, đoàn thể" không nằm dưới "Tin tức – Sự kiện" (đếm được %)', so_menu;
    END IF;

    SELECT count(*) INTO so_cat
      FROM categories c JOIN categories p ON p.id = c.parent_id
     WHERE c.slug = 'hoat-dong-dang-doan-the' AND c.depth = 1
       AND p.slug = 'tin-tuc' AND c.deleted_at IS NULL;
    IF so_cat <> 1 THEN
        RAISE EXCEPTION 'Danh mục hoat-dong-dang-doan-the không nằm dưới tin-tuc (đếm được %)', so_cat;
    END IF;

    SELECT count(*) INTO so_cap1
      FROM menu_items
     WHERE position = 'HEADER' AND depth = 0 AND deleted_at IS NULL;
    IF so_cap1 <> 7 THEN
        RAISE EXCEPTION 'HEADER phải còn 7 mục cấp 1 sau lượt chuyển, đếm được %', so_cap1;
    END IF;

    -- ⭐ Chốt luôn HÌNH DẠNG path, không chỉ chốt quan hệ cha-con. Đây đúng là chỗ
    --    bản nháp sai: quan hệ cha-con đã đúng mà thứ tự vẫn hỏng, vì path con trùng
    --    path cha.
    SELECT count(*) INTO so_menu
      FROM menu_items c JOIN menu_items p ON p.id = c.parent_id
     WHERE c.label = 'Hoạt động Đảng, đoàn thể' AND c.position = 'HEADER'
       AND c.deleted_at IS NULL
       AND c.path = p.path || c.id || '/';
    IF so_menu <> 1 THEN
        RAISE EXCEPTION 'path của mục con phải là path cha nối id con — nếu không, thứ tự menu là không xác định';
    END IF;

    -- [2] + [4] Năm khoá `settings` của đợt này
    SELECT count(*) INTO so_khoa FROM settings
     WHERE setting_key IN ('site.header.parent-org', 'site.header.display-name',
                           'site.home.news-category', 'site.home.category-news-count',
                           'site.home.marquee-count')
       AND editable IS TRUE AND group_code = 'SITE';
    IF so_khoa <> 5 THEN
        RAISE EXCEPTION 'Đợt bố cục 29/08 cần đúng 5 khoá SITE, đếm được %', so_khoa;
    END IF;

    -- [3] Bảng contacts tồn tại và mang đúng ràng buộc liên lạc ngược
    IF to_regclass('public.contacts') IS NULL THEN
        RAISE EXCEPTION 'Bảng contacts không được tạo';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_contacts_lien_lac') THEN
        RAISE EXCEPTION 'Thiếu ràng buộc ck_contacts_lien_lac — biểu mẫu sẽ nhận được lời nhắn không thể trả lời';
    END IF;
END $$;
