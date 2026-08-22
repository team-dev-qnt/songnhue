-- =============================================================================
-- Danh mục nội dung + Bài viết (WS-13 / T13.1) — CN-01.1, CN-01.2
--
-- ⚠ Thư mục là `db/migration/cms`, KHÔNG phải `db/migration/content`. Flyway chỉ
--   quét 5 đường dẫn khai ở `app/application.yml`; đặt nhầm thì migration không
--   chạy và KHÔNG có lỗi nào — triệu chứng đầu tiên là `relation does not exist`
--   ở tầng nghiệp vụ (docs/coding-guide.md §3.1).
--
-- ⚠ Quyền cho `songnhue_app` KHÔNG khai ở đây: V202608131006 đã đặt
--   `ALTER DEFAULT PRIVILEGES` cho cả TABLES lẫn SEQUENCES, nên bảng nào do
--   `songnhue_owner` tạo về sau đều tự có quyền. (Đúng chỗ này từng thủng ở WS-7:
--   bản đầu chỉ khai TABLES, và bảng sổ đăng ký sao lưu tạo sau đó đã chặn mất cả
--   cơ chế sao lưu — V202608171011 vá lại.)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- categories — cây tối đa 3 cấp (CN-01.2)
-- -----------------------------------------------------------------------------
CREATE TABLE categories (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                       VARCHAR(255) NOT NULL,
    slug                       VARCHAR(255) NOT NULL,
    parent_id                  BIGINT REFERENCES categories (id),
    description                TEXT,
    -- Ảnh đại diện trỏ tới `attachments` bằng public_id: module `content` không được
    -- import entity của core, nên nó cầm UUID chứ không cầm khoá chạy số.
    cover_attachment_public_id UUID REFERENCES attachments (public_id) ON DELETE SET NULL,
    -- Materialized path '/1/4/9/' — giống org_units, tìm cây con bằng LIKE '/1/4/%'
    path                       VARCHAR(500) NOT NULL,
    depth                      SMALLINT     NOT NULL DEFAULT 0,
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    -- Hiện/Ẩn trên cổng. Ẩn danh mục KHÔNG ẩn bài viết trong đó — bài vẫn vào được
    -- bằng đường dẫn trực tiếp, đúng như "Lưu trữ" của bài viết.
    visible                    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    created_by                 BIGINT,
    updated_at                 timestamptz,
    updated_by                 BIGINT,
    deleted_at                 timestamptz,
    version                    INTEGER      NOT NULL DEFAULT 0,
    -- 3 cấp = depth 0,1,2. Chặn ở CSDL vì đây là ràng buộc của spec, không phải
    -- tuỳ chọn giao diện; tầng service chặn thêm để trả mã lỗi cho người dùng.
    CONSTRAINT ck_categories_depth CHECK (depth BETWEEN 0 AND 2),
    CONSTRAINT ck_categories_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX uq_categories_public_id ON categories (public_id);
CREATE UNIQUE INDEX uq_categories_slug ON categories (slug) WHERE deleted_at IS NULL;
CREATE INDEX ix_categories_parent ON categories (parent_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_categories_path ON categories (path) WHERE deleted_at IS NULL;

COMMENT ON COLUMN categories.slug IS
    'Duy nhất trong số danh mục CHƯA xoá — xoá mềm rồi thì slug được dùng lại, '
    'vì địa chỉ công khai của nó cũng không còn.';

-- -----------------------------------------------------------------------------
-- articles — CN-01.1
--
-- ⭐ HAI CỘT QUYẾT ĐỊNH HAI VIỆC KHÁC NHAU, đây là chỗ dễ nhầm nhất của bảng này:
--
--     published_version_id  →  CỔNG HIỂN THỊ NỘI DUNG NÀO
--     status                →  CÓ HIỂN THỊ HAY KHÔNG
--
-- Sửa một bài đang xuất bản thì `status` chạy về CHO_DUYET nhưng
-- `published_version_id` GIỮ NGUYÊN bản cũ → cổng vẫn phục vụ nội dung cũ trong
-- lúc bản mới chờ duyệt. Đó chính là copy-on-write (điểm nghiệp vụ 1,
-- architecture-review.md §10.2). Gộp hai việc vào một cột thì hoặc là bài biến
-- mất khỏi cổng lúc biên tập viên bấm Lưu, hoặc là bản chưa duyệt lên thẳng cổng.
-- -----------------------------------------------------------------------------
CREATE TABLE articles (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- Bản làm việc mà biên tập viên đang sửa. Bản công khai nằm ở article_versions.
    title                      VARCHAR(255) NOT NULL,
    slug                       VARCHAR(255) NOT NULL,
    summary                    VARCHAR(500),
    content                    TEXT         NOT NULL,
    cover_attachment_public_id UUID REFERENCES attachments (public_id) ON DELETE SET NULL,
    -- Mặc định là người đang đăng nhập, cho đổi (CN-01.1)
    author_user_id             BIGINT       NOT NULL REFERENCES users (id),
    source                     VARCHAR(255),
    status                     VARCHAR(50)  NOT NULL DEFAULT 'NHAP',
    -- Bản đang phục vụ trên cổng. NULL = chưa từng xuất bản. FK thêm ở cuối file
    -- vì hai bảng tham chiếu vòng nhau.
    published_version_id       BIGINT,
    -- Thời điểm hiệu lực. Ở tương lai = "Đã lên lịch" (điểm nghiệp vụ 5): truy vấn
    -- công khai lọc `published_at <= now()`, nên KHÔNG cần trạng thái thứ bảy.
    published_at               timestamptz,
    -- Lý do khi trả bài về YEU_CAU_CHINH_SUA — giữ lại thì mới lọc được danh sách
    -- "bài bị trả về" và người viết mới biết phải sửa gì (điểm nghiệp vụ 2).
    review_note                TEXT,
    meta_title                 VARCHAR(70),
    meta_description           VARCHAR(160),
    meta_keywords              VARCHAR(500),
    -- Số xấp xỉ, cộng dồn theo lô (điểm nghiệp vụ 6). KHÔNG dùng để kiểm toán.
    view_count                 BIGINT       NOT NULL DEFAULT 0,
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    created_by                 BIGINT,
    updated_at                 timestamptz,
    updated_by                 BIGINT,
    deleted_at                 timestamptz,
    version                    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_articles_status CHECK (
        status IN ('NHAP', 'CHO_DUYET', 'YEU_CAU_CHINH_SUA', 'XUAT_BAN', 'GO_BAI', 'LUU_TRU')
    ),
    CONSTRAINT ck_articles_view_count_not_negative CHECK (view_count >= 0)
);

CREATE UNIQUE INDEX uq_articles_public_id ON articles (public_id);
-- Slug là địa chỉ công khai: trùng nghĩa là hai bài tranh nhau một URL, nên chặn
-- cứng ở CSDL chứ không chỉ cảnh báo như spec viết (điểm nghiệp vụ 4 → CMS-2001).
CREATE UNIQUE INDEX uq_articles_slug ON articles (slug) WHERE deleted_at IS NULL;
CREATE INDEX ix_articles_status ON articles (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_articles_author ON articles (author_user_id) WHERE deleted_at IS NULL;
-- Truy vấn của cổng công khai: bài có bản đã xuất bản, tới hạn, mới nhất trước.
CREATE INDEX ix_articles_public_listing ON articles (published_at DESC)
    WHERE deleted_at IS NULL AND published_version_id IS NOT NULL;
-- Job hẹn giờ đăng quét đúng tập này, 5 phút một lượt — giữ nó nhỏ.
CREATE INDEX ix_articles_scheduled ON articles (published_at)
    WHERE deleted_at IS NULL AND status = 'XUAT_BAN';

-- Tìm kiếm quản trị (T13.8): gõ không dấu vẫn ra.
CREATE INDEX ix_articles_title_trgm ON articles
    USING gin (sn_khong_dau(title) gin_trgm_ops) WHERE deleted_at IS NULL;

COMMENT ON COLUMN articles.published_version_id IS
    'Bản mà cổng công khai đang phục vụ. Sửa bài đã xuất bản KHÔNG đụng vào cột này '
    '— bản mới đi qua duyệt rồi mới thay thế (copy-on-write, điểm nghiệp vụ 1).';
COMMENT ON COLUMN articles.status IS
    'Trạng thái biên tập của bản đang sửa. Quyết định CÓ hiển thị hay không, '
    'không quyết định hiển thị NỘI DUNG nào.';
COMMENT ON COLUMN articles.view_count IS
    'Xấp xỉ — cộng dồn trong bộ nhớ rồi đẩy xuống theo lô. Không kiểm toán được.';

-- -----------------------------------------------------------------------------
-- article_versions — ảnh chụp nội dung tại một thời điểm
--
-- Vừa là nguồn cho so sánh/phục hồi (CN-01.1 "Audit log bài viết"), vừa là thứ
-- cổng công khai thật sự đọc. Bảng này KHÔNG có `deleted_at`: nó là lịch sử, mà
-- lịch sử sửa được thì không còn là lịch sử.
-- -----------------------------------------------------------------------------
CREATE TABLE article_versions (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    article_id                 BIGINT       NOT NULL REFERENCES articles (id) ON DELETE CASCADE,
    version_no                 INTEGER      NOT NULL,
    title                      VARCHAR(255) NOT NULL,
    slug                       VARCHAR(255) NOT NULL,
    summary                    VARCHAR(500),
    content                    TEXT         NOT NULL,
    cover_attachment_public_id UUID,
    meta_title                 VARCHAR(70),
    meta_description           VARCHAR(160),
    meta_keywords              VARCHAR(500),
    -- Vì sao có bản này: 'Lưu nháp', 'Duyệt xuất bản', 'Phục hồi từ bản #3'…
    note                       VARCHAR(500),
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    created_by                 BIGINT,
    CONSTRAINT ck_article_versions_no_positive CHECK (version_no > 0)
);

CREATE UNIQUE INDEX uq_article_versions_public_id ON article_versions (public_id);
CREATE UNIQUE INDEX uq_article_versions_no ON article_versions (article_id, version_no);
CREATE INDEX ix_article_versions_article ON article_versions (article_id, version_no DESC);

ALTER TABLE articles
    ADD CONSTRAINT fk_articles_published_version
        FOREIGN KEY (published_version_id) REFERENCES article_versions (id) ON DELETE SET NULL;

COMMENT ON TABLE article_versions IS
    'Ảnh chụp nội dung. Cổng công khai đọc bản mà articles.published_version_id trỏ tới, '
    'KHÔNG đọc cột nội dung của articles — đó mới là bản đang biên tập.';

-- -----------------------------------------------------------------------------
-- Quan hệ nhiều–nhiều: danh mục và thẻ
-- -----------------------------------------------------------------------------
CREATE TABLE article_categories (
    article_id  BIGINT NOT NULL REFERENCES articles (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id),
    PRIMARY KEY (article_id, category_id)
);

-- Không ON DELETE CASCADE ở phía category: xoá danh mục còn bài phải bị CHẶN và
-- bắt chuyển bài đi trước (CN-01.2, T13.9). Để cascade là lặng lẽ tháo bài khỏi
-- danh mục rồi báo "xoá thành công".
CREATE INDEX ix_article_categories_category ON article_categories (category_id);

CREATE TABLE tags (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    created_by BIGINT
);

CREATE UNIQUE INDEX uq_tags_public_id ON tags (public_id);
CREATE UNIQUE INDEX uq_tags_slug ON tags (slug);

CREATE TABLE article_tags (
    article_id BIGINT NOT NULL REFERENCES articles (id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, tag_id)
);

CREATE INDEX ix_article_tags_tag ON article_tags (tag_id);
