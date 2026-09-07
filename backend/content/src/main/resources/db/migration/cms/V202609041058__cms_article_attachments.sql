-- =============================================================================
-- WS-40 — Tài liệu đính kèm bài viết: bản LÀM VIỆC + bản CHỤP
--
-- ⭐ VÌ SAO HAI BẢNG, KHÔNG PHẢI MỘT
--
-- QuanTran chốt 04/09/2026: *tài liệu nằm trong bản chụp phiên bản — đổi tài liệu
-- phải qua duyệt*. Khoá danh sách tài liệu theo `article_id` một mình mang đúng hai
-- hệ quả, cả hai đều IM LẶNG:
--
--   1. Phục hồi bản #3 vẫn giữ danh sách tài liệu HIỆN TẠI ⇒ cổng phục vụ
--      "nội dung bản 3 + tài liệu bản 7".
--   2. ⛔ Đổi tài liệu của một bài ĐANG XUẤT BẢN là đổi ngay trên cổng, KHÔNG qua
--      ai duyệt — trái CN-01.1 và đúng thứ cơ chế bản chụp sinh ra để chặn.
--
-- Tiền lệ gần nhất KHÔNG phải `article_categories` (phân loại, không chụp) mà là
-- `cover_attachment_public_id` — tham chiếu TỆP duy nhất đang có, và nó CÓ trong
-- bản chụp. Tài liệu giống ảnh bìa hơn giống danh mục.
--
-- ⛔ Và không gộp thành một bảng có hai khoá ngoại nullable: đó là chỗ sinh ra
--    trạng thái không biểu diễn được (cả hai NULL, hoặc cả hai có giá trị).
--
-- ⚠ SỐ HIỆU 1058 — không phải 1052. Nhánh Phase 2 (`fix/cong-de-bat-cho-ci-chua-xong`)
--   đang giữ năm tệp V202609011052 → V202609031056 CHƯA GỘP. Quy tắc của kho là
--   *số mới phải lớn hơn MỌI số đã có*, và "đã có" gồm cả một nhánh anh em chưa gộp.
--   `MigrationNamingTest` chỉ đòi dãy TĂNG DẦN nghiêm ngặt nên khoảng trống hợp lệ.
--
--   ⛔⛔ QuanTran chốt 04/09 là WS-40 GỘP VÀ TRIỂN KHAI TRƯỚC. Hệ quả bắt buộc:
--       nhánh Phase 2 phải ĐÁNH SỐ LẠI năm tệp ấy lên trên 1058 trước khi gộp —
--       Flyway sắp theo cả chuỗi `<YYYYMMDD><nnnn>`, nên V202609011052 (ngày 01/09)
--       nằm DƯỚI V202609041058 (ngày 04/09) và sẽ là out-of-order trên CSDL đã áp
--       bản này. Đó chính là §10.66, hai lượt CD đỏ ngày 27/8.
--
-- ⚠ GRANT: V202608131006 đã đặt ALTER DEFAULT PRIVILEGES cho bảng mới trong schema
--   `public` ⇒ không cần migration quyền.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Bản LÀM VIỆC — biên tập viên sửa ở đây. ⛔ Cổng công khai KHÔNG đọc bảng này.
-- -----------------------------------------------------------------------------
-- ⛔ KHÔNG có public_id / version / deleted_at / updated_*. Đây là một hàng NỐI, không phải một
--    entity nghiệp vụ: JPA ánh xạ nó bằng @ElementCollection, nên mỗi lượt sửa là xoá sạch rồi
--    ghi lại — y hệt `article.getCategories().clear()`. Cột nào Hibernate không ghi thì đó là một
--    cột chưa ai đọc, và quy tắc 15 gọi đó là lỗi chứ không phải chỗ để dành.
CREATE TABLE article_attachments (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id           BIGINT      NOT NULL REFERENCES articles (id) ON DELETE CASCADE,
    -- Trỏ bằng public_id theo đúng tiền lệ articles.cover_attachment_public_id (§4.2 chống IDOR).
    -- ⛔ KHÔNG khoá ngoại sang attachments: bảng ấy thuộc module Core và ràng buộc chéo module ở
    --    tầng CSDL là một phụ thuộc không ai thấy khi đọc mã Java. Tệp không tồn tại được chặn ở
    --    ArticleService (SYS-0004), tệp bị xoá mềm tự biến khỏi DTO vì findRef lọc deleted_at.
    attachment_public_id UUID        NOT NULL,
    -- ⭐ Tên gợi nhớ hiện trên cổng — "Xem quyết định ở đây" thay cho "quyet-dinh-thanh-lap.pdf".
    --    Nhãn thuộc về MỐI NỐI, không thuộc về tệp: cùng một PDF gắn vào ba bài mang ba nhãn khác
    --    nhau được. Rỗng ⇒ hiển thị tên gốc; ⛔ KHÔNG sinh nhãn mặc định kiểu "Tài liệu 1" (quy tắc 16).
    label                VARCHAR(255),
    sort_order           INTEGER     NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_attachments UNIQUE (article_id, attachment_public_id)
);

CREATE INDEX ix_article_attachments_article ON article_attachments (article_id, sort_order);
CREATE INDEX ix_article_attachments_tep ON article_attachments (attachment_public_id);

COMMENT ON TABLE article_attachments IS
    'Tài liệu đính kèm — BẢN LÀM VIỆC. Cổng công khai đọc article_version_attachments, '
    'KHÔNG đọc bảng này: đổi tài liệu của bài đang xuất bản phải qua duyệt.';

-- -----------------------------------------------------------------------------
-- Bản CHỤP — cổng đọc bảng NÀY, đúng như nó đọc article_versions.content.
-- ⛔ Không có deleted_at và không có version: đây là lịch sử, mà lịch sử sửa được
--    thì không còn là lịch sử (cùng lý do với chính article_versions).
-- -----------------------------------------------------------------------------
CREATE TABLE article_version_attachments (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_version_id   BIGINT      NOT NULL REFERENCES article_versions (id) ON DELETE CASCADE,
    attachment_public_id UUID        NOT NULL,
    label                VARCHAR(255),
    sort_order           INTEGER     NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_article_version_attachments UNIQUE (article_version_id, attachment_public_id)
);

CREATE INDEX ix_article_version_attachments_version
    ON article_version_attachments (article_version_id, sort_order);
CREATE INDEX ix_article_version_attachments_tep
    ON article_version_attachments (attachment_public_id);

COMMENT ON TABLE article_version_attachments IS
    'Tài liệu đính kèm — BẢN CHỤP theo phiên bản. Đây là thứ cổng công khai đọc; '
    'nhờ vậy vòng qua bước duyệt là KHÔNG BIỂU DIỄN ĐƯỢC, không phải "được ghi chú là đừng làm".';

-- -----------------------------------------------------------------------------
-- Bộ canh của chính migration này.
--
-- ⚠ CREATE TABLE thất bại thì Flyway đã dừng, nên khối này không canh "bảng có
--   tồn tại không". Nó canh thứ dễ sai mà KHÔNG báo: ràng buộc UNIQUE — thiếu nó
--   thì cùng một tệp gắn hai lần vào một bài, và cổng hiện hai dòng giống hệt nhau
--   mà không lỗi nào. Đếm được thì phải đếm (quy tắc 29).
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    so_rang_buoc INTEGER;
BEGIN
    SELECT count(*) INTO so_rang_buoc
    FROM pg_constraint
    WHERE conname IN ('uq_article_attachments', 'uq_article_version_attachments')
      AND contype = 'u';

    IF so_rang_buoc <> 2 THEN
        RAISE EXCEPTION
            'WS-40: chờ 2 ràng buộc UNIQUE trên hai bảng nối, đếm được %. '
            'Thiếu nó thì một tệp gắn được nhiều lần vào cùng một bài — im lặng.', so_rang_buoc;
    END IF;
END $$;
