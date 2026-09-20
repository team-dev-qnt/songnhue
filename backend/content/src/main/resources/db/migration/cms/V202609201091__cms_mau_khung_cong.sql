-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Nền đầu trang / chân trang đổi được từ giao diện quản trị — T77.1 · 20/09/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Nối tiếp `V202609201090` (màu chủ đạo + màu nhấn). Cùng một cơ chế, cùng một kiểu `COLOR`,
--  cùng một đường đọc — nên tệp này ⛔ phải một lượt dựng mới mà là **hai dòng thêm vào một cơ
--  chế đã có đường đọc được bộ canh canh**. ⛔ đụng `ck_settings_value_type`: `COLOR` đã nằm
--  trong đó từ lượt trước.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  VÌ SAO LÀ `site.brand.*` CHỨ ⛔ PHẢI `site.header.*` / `site.footer.*`
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  Kho ĐÃ có hai họ khoá ấy — `site.header.display-name`, `site.header.parent-org`,
--  `site.footer.copyright`, `site.footer.company-info`, `site.footer.social.*`… Đặt màu nền vào
--  đó nghe rất thuận, và đó là phương án được cân nhắc TRƯỚC.
--
--  ⛔ Bỏ vì hai lý do đo được:
--    1. Hai họ ấy hiện là **nội dung chữ** do Công ty soạn. Màu là một **cơ chế** khác hẳn: nó đi
--       qua `value_type = COLOR`, qua `laMaMauHopLe`, và ra một biến CSS. Trộn vào là để hai thứ
--       chịu hai luật khác nhau nằm chung một nhóm trên màn hình cấu hình.
--    2. Ánh xạ **khoá ↔ biến CSS ↔ token** phải cơ học để còn canh được:
--       `site.brand.<x>` ⇒ `--sn-brand-<x>` ⇒ token tên `<x>`. `mauThuongHieu.test.ts` khẳng
--       định đúng câu ấy cho MỌI dòng của `ANH_XA_MAU`. Một họ khoá thứ hai là một ngoại lệ
--       trong bộ canh, và một bộ canh có ngoại lệ là một bộ canh sắp có ngoại lệ thứ hai.
--
--  ⇒ Trên màn hình *Cấu hình hệ thống*, bốn núm màu nằm cạnh nhau (sort_order 50→80), đúng chỗ
--  người đi tìm "đổi màu cổng" sẽ nhìn.
--
--  ───────────────────────────────────────────────────────────────────────────────────────
--  HAI NÚM CHO HAI VÙNG — ⛔ PHẢI NĂM NÚM CHO NĂM BẬC NAVY
--  ───────────────────────────────────────────────────────────────────────────────────────
--
--  Đầu trang hôm nay là một gradient ba chặng (`navy800 → navy500 → navy800`), chân trang cũng
--  ba chặng (`navy700 → navy600 → navy900`) cộng hai dải con. Tám chặng, năm sắc độ.
--
--  Bày cả năm bậc ra màn hình là giao cho người quản trị một bài **phối màu**: năm ô nhập phải
--  hợp nhau thì khung cổng mới ⛔ loang lổ, và ⛔ ô nào nói cho họ biết ô kia đang ở đâu.
--
--  Cách đã chọn: **một biến cho mỗi vùng, và mỗi chặng giữ giá trị dự phòng RIÊNG của nó**
--  (`public-web/tailwind.config.ts`). Hệ quả:
--      để trống ⇒ mỗi chặng rơi về token của chính nó ⇒ gradient y hệt hôm nay
--      có màu   ⇒ mọi chặng cùng một giá trị    ⇒ gradient xẹp thành màu phẳng
--  Tức ô nhập hứa đúng thứ nó làm được: *"nền của vùng này"*. ⛔ phép tính sắc độ nào lúc chạy —
--  T75.7 đã từ chối hướng ấy và lý do vẫn nguyên: một màu vừa gõ ⛔ có gì bảo đảm nó còn đọc
--  được sau khi bị làm sáng/tối theo công thức.
--
-- ⚠ `default_value` RỖNG, cùng lý lẽ `V202609201090` (quy tắc 3 + T53.4): `effectiveValue()` rơi
--   về `default_value` khi giá trị rỗng, nên seed một mã màu vào đó là **khoá chết** trạng thái
--   *"⛔ ai từng mở màn hình này"*. Rỗng ⇒ ⛔ phát biến ⇒ trình duyệt dùng token — mà token đang
--   mang đúng sắc navy Công ty đã nghiệm thu 27/08 (*"hệ màu GIỮ NGUYÊN"*).
--
-- ⚠ `description` ⛔ khai giá trị mặc định hiện hành — migration đã phát hành thì ⛔ sửa được, nên
--   một con số viết ở đây sẽ nói dối kể từ lượt đổi nhận diện sau. Mã hex trong câu là **ví dụ
--   ĐỊNH DẠNG**, ⛔ phải lời khai về giá trị đang chạy.
--
-- ⚠⚠ Danh sách cột ĐỌC TỪ `CREATE TABLE` (`V202608131003`), ⛔ chép từ seed cũ — `V202608191020`
--    dùng dạng `INSERT … SELECT … FROM (VALUES …)` nên tuple bên trong là **đối số của SELECT**;
--    chép nó ra đã làm Flyway đỏ một lượt ở `V202609201090` với `column "category" … does not
--    exist`. Cột thật: `group_code`; `setting_value` và `default_value` là HAI cột riêng.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES
    ('site.brand.header', '', 'COLOR', '',
     'SITE', 'Màu nền đầu trang',
     'Mã HEX 6 chữ số, ví dụ #061b37. Để trống = giữ dải màu mặc định của cổng. '
     || 'Đặt một màu sẽ thay cả dải chuyển sắc ở đầu trang bằng đúng màu phẳng đó.',
     NULL, TRUE, TRUE, 70),
    ('site.brand.footer', '', 'COLOR', '',
     'SITE', 'Màu nền chân trang',
     'Mã HEX 6 chữ số, ví dụ #081e3a. Để trống = giữ dải màu mặc định của cổng. '
     || 'Áp cho cả thân chân trang, dải đường dây nóng và dải bản quyền.',
     NULL, TRUE, TRUE, 80);

-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Tự kiểm — một migration ⛔ khẳng định gì thì lượt sau ⛔ biết nó đã chạy đúng ⛔
-- ═══════════════════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_khoa_moi INTEGER;
    so_nut_mau  INTEGER;
    so_trung    INTEGER;
BEGIN
    SELECT count(*) INTO so_khoa_moi
      FROM settings
     WHERE setting_key IN ('site.brand.header', 'site.brand.footer')
       AND value_type = 'COLOR'
       AND coalesce(default_value, '') = ''
       AND coalesce(setting_value, '') = ''
       AND group_code = 'SITE'
       AND editable;
    IF so_khoa_moi <> 2 THEN
        RAISE EXCEPTION 'Chờ 2 khoá nền khung cổng kiểu COLOR với mặc định rỗng, đo được %', so_khoa_moi;
    END IF;

    -- Vế đối chứng 1: hai khoá của V202609201090 phải CÒN SỐNG. Tệp này nối vào cơ chế của
    -- chúng; nếu chúng biến mất thì `ANH_XA_MAU` đang trỏ vào khoá ⛔ tồn tại và bốn núm trên màn
    -- hình chỉ còn hai — một trạng thái ⛔ gì khác trong kho báo được.
    SELECT count(*) INTO so_nut_mau
      FROM settings
     WHERE setting_key LIKE 'site.brand.%' AND value_type = 'COLOR';
    IF so_nut_mau <> 4 THEN
        RAISE EXCEPTION 'Chờ đúng 4 núm màu site.brand.* kiểu COLOR, đo được %', so_nut_mau;
    END IF;

    -- Vế đối chứng 2: bốn núm màu ⛔ được đụng nhau ở sort_order, ⛔ thì thứ tự của chúng trên
    -- màn hình do CSDL quyết định ngẫu nhiên và mỗi lượt tải lại đổi chỗ.
    --
    -- ⚠ Phạm vi CỐ Ý hẹp ở `site.brand.%`, ⛔ phủ cả nhóm SITE: một ràng buộc rộng hơn thứ tệp
    --   này thay đổi sẽ làm Flyway đỏ vì một trạng thái có sẵn mà lượt di trú ⛔ hề gây ra — và
    --   lúc ấy thông điệp đỏ trỏ vào đúng người ⛔ có lỗi.
    SELECT count(*) INTO so_trung
      FROM (SELECT sort_order FROM settings WHERE setting_key LIKE 'site.brand.%'
             GROUP BY sort_order HAVING count(*) > 1) t;
    IF so_trung <> 0 THEN
        RAISE EXCEPTION 'Bốn núm màu có % mức sort_order bị trùng', so_trung;
    END IF;
END $$;
