-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Số ký hiệu và ngày ban hành của văn bản — WS-39, yêu cầu QuanTran 01/09/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  ⚠⚠ SỰ THẬT NỀN: CỔNG KHÔNG CÓ THỰC THỂ "VĂN BẢN", VÀ ĐỢT NÀY KHÔNG DỰNG MỘT CÁI
--
--  Một "văn bản" trên cổng này **là** một `Article` thuộc nhánh danh mục `cong-bo-thong-tin`
--  (CR-07: cổng KHÔNG dựng mô-đun văn bản nội bộ và KHÔNG đồng bộ từ hệ thống văn bản điều hành
--  của Thành phố — CN-01.7). Bảng `articles` đã có `title` (trích yếu) và `published_at` (thời
--  gian đăng tải). Thiếu đúng hai thứ để dựng được bảng danh sách kiểu cổng tham chiếu:
--  **số ký hiệu** và **ngày ban hành**.
--
--  ⛔ VÌ SAO ĐÚNG HAI CỘT, KHÔNG PHẢI MỘT BỘ HỒ SƠ VĂN BẢN
--
--  Bảng của cổng tham chiếu (`thuyloisongday.vn/van-ban`, đo 01/09) HIỂN đúng năm cột:
--  Số ký hiệu · Trích yếu · Nội dung chi tiết (nút) · Ngày ban hành · Thời gian đăng tải.
--  Cơ quan ban hành, người ký và tệp đính kèm không xuất hiện ở đâu trên đó. Thêm chúng là thêm
--  cột không ai đọc — quy tắc 15 tính đó là một lỗi. Khi nào có màn hình cần chúng thì lúc ấy
--  hình dạng nhu cầu đã rõ, không phải đoán trước.
--
--  ⛔ HAI CỘT NÀY LÀ Ô ĐỂ BIÊN TẬP VIÊN NHẬP, KHÔNG PHẢI Ô ĐỂ SUY RA
--
--  Để trống ⇒ ô tương ứng trên cổng **để trống**. Không suy `doc_issued_date` từ `published_at`
--  (ngày đăng lên cổng không phải ngày ký ban hành), không dựng dấu gạch giả làm một giá trị.
--  Bản trang chủ 29/08 từng có bốn văn bản viết cứng **kèm số hiệu và người ký, tất cả bịa** —
--  chúng đã lên staging và làm một mục rỗng trông như một mục đầy (§10.54, quy tắc 16).
--
--  ⭐ VÌ SAO `DATE` CHỨ KHÔNG `timestamptz`
--
--  Quy tắc 1 của dự án nói về **timestamp**: mốc thời gian phải lưu `timestamptz` UTC. Ngày ban
--  hành không phải một mốc thời gian — nó là một NGÀY in trên tờ giấy, không có giờ, không có
--  múi. Ép nó thành `timestamptz` là bịa ra một giờ 00:00 rồi để nó lệch một ngày ở đúng biên
--  UTC+7. `published_at` vẫn là `timestamptz` vì nó là một mốc thật do hệ thống ghi.
--
--  ⚠ Cả hai NULLable và KHÔNG có `DEFAULT`: đại đa số bài viết trên cổng là tin tức, không phải
--    văn bản. Một giá trị mặc định ở đây là gán số ký hiệu cho mọi bản tin.
--
--  ⭐⭐ VÌ SAO THÊM VÀO CẢ `article_versions`, KHÔNG CHỈ `articles`
--
--  Javadoc của entity `Article` chốt: *`publishedVersionId` quyết định hiển thị NỘI DUNG NÀO,
--  `status` quyết định CÓ hiển thị hay không* — copy-on-write (điểm nghiệp vụ 1, §10.2). Truy vấn
--  của cổng (`ArticleRepository.findPublic`) vì thế lấy MỌI trường nội dung từ bản đã duyệt
--  (`ArticleVersion v`), chỉ lấy `slug`/`publishedAt`/`viewCount` từ `articles`.
--
--  Số ký hiệu và ngày ban hành là NỘI DUNG. Để chúng chỉ ở `articles` thì biên tập viên sửa số
--  ký hiệu của một bài đang xuất bản là **đổi ngay trên cổng, không qua duyệt** — đúng thứ mà
--  cả cơ chế bản chụp sinh ra để chặn, và triệu chứng thì im: bài vẫn hiện, chỉ là một ô của nó
--  đi trước lượt duyệt.
--
--  ⚠ Hệ quả: `ArticleVersion.snapshotOf` và `restoreInto` phải chép hai cột này. Đó là hai nơi
--    con người phải nhớ, nên có bài kiểm nhớ hộ (`ArticleVersionSnapshotTest`, quy tắc 14).
-- ═══════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE articles
    ADD COLUMN doc_number      VARCHAR(100),
    ADD COLUMN doc_issued_date DATE;

ALTER TABLE article_versions
    ADD COLUMN doc_number      VARCHAR(100),
    ADD COLUMN doc_issued_date DATE;

COMMENT ON COLUMN articles.doc_number IS
    'Số ký hiệu văn bản, ví dụ 43/2015/NĐ-CP. Chỉ có nghĩa với bài thuộc nhánh "Công bố thông tin"; '
    'NULL với tin bài thường. Biên tập viên nhập tay — cổng KHÔNG đồng bộ từ hệ thống văn bản '
    'điều hành của Thành phố (CN-01.7 / CR-07).';

COMMENT ON COLUMN articles.doc_issued_date IS
    'Ngày ký ban hành văn bản. DATE chứ không timestamptz: đây là ngày in trên tờ giấy, không có '
    'giờ và không có múi giờ. KHÁC published_at (thời gian đăng tải lên cổng) — hai cột này hiện '
    'ở hai ô khác nhau của bảng danh sách và không được suy ra từ nhau.';

-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  ⛔ ĐO LẠI THỨ VỪA TẠO. `ALTER TABLE … ADD COLUMN` khó trượt, nhưng KIỂU thì trượt được —
--     và một cột `doc_issued_date` lỡ mang kiểu `timestamptz` sẽ chạy đúng suốt cho tới khi ai
--     đó nhập một ngày rồi thấy nó lùi một hôm ở múi giờ UTC+7.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_cot int;
BEGIN
    -- Bốn cột: hai bảng × hai cột. Đếm cả hai bảng trong MỘT phép đếm để không có cách nào ra
    -- kết quả đúng khi chỉ một bảng được sửa.
    SELECT count(*) INTO so_cot
      FROM information_schema.columns
     WHERE table_name IN ('articles', 'article_versions')
       AND ((column_name = 'doc_number' AND data_type = 'character varying' AND character_maximum_length = 100)
         OR (column_name = 'doc_issued_date' AND data_type = 'date'));
    IF so_cot <> 4 THEN
        RAISE EXCEPTION 'Cần đúng 4 cột văn bản (2 bảng × 2 cột) với đúng kiểu varchar(100)/date, đếm được %', so_cot;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name IN ('articles', 'article_versions')
                  AND column_name IN ('doc_number', 'doc_issued_date')
                  AND is_nullable = 'NO') THEN
        RAISE EXCEPTION 'Hai cột văn bản phải NULLable — đa số bài viết không phải văn bản';
    END IF;
END $$;
