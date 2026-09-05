-- =============================================================================
-- Banner + Menu điều hướng (WS-15 / T15.1) — CN-01.5
--
-- Hai bảng, và CỐ Ý CHỈ HAI. Phần còn lại của CN-01.5 — tên site, logo, màu,
-- footer, khối trang chủ, tuỳ chọn slider — nằm ở bảng `settings` nhóm `site`
-- (T15.2, migration kế tiếp). Ranh giới đặt ở đây:
--
--     Có nhiều dòng, người dùng thêm/bớt/sắp xếp  →  BẢNG
--     Đúng một giá trị cho cả hệ thống            →  settings
--
-- Dựng một bảng `site_config` một dòng là tự nhận thêm một màn hình cấu hình
-- thứ hai, một cơ chế xuất/nhập thứ hai và một bộ nhớ đệm thứ hai — trong khi
-- `settings` đã có đủ cả ba từ WS-6.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- banners — ảnh carousel trang chủ
--
-- Lịch hiển thị là hai cột thời gian chứ không phải một cột trạng thái: đặt lịch
-- rồi để hệ thống tự bật/tắt thì không ai phải nhớ quay lại gỡ banner Tết xuống
-- vào mùng 8. `active` vẫn giữ, vì "tắt ngay bây giờ" khác với "hết hạn".
-- -----------------------------------------------------------------------------
CREATE TABLE banners (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    title                      VARCHAR(255) NOT NULL,
    description                VARCHAR(500),
    -- Trỏ tới `attachments` bằng public_id — `content` cầm UUID chứ không cầm
    -- khoá chạy số của module khác (cùng lý do với articles.cover_…).
    image_attachment_public_id UUID         NOT NULL REFERENCES attachments (public_id),
    link_url                   VARCHAR(1000),
    open_new_tab               BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    start_at                   timestamptz,
    end_at                     timestamptz,
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    created_by                 BIGINT,
    updated_at                 timestamptz,
    updated_by                 BIGINT,
    deleted_at                 timestamptz,
    version                    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_banners_schedule CHECK (start_at IS NULL OR end_at IS NULL OR end_at > start_at)
);

CREATE UNIQUE INDEX uq_banners_public_id ON banners (public_id);
-- Truy vấn của cổng: banner đang bật, đúng khung giờ, theo thứ tự kéo thả.
CREATE INDEX ix_banners_active ON banners (sort_order) WHERE deleted_at IS NULL AND active;

COMMENT ON COLUMN banners.end_at IS
    'Hết hạn thì banner tự rời cổng. Cột này tồn tại để không ai phải nhớ quay lại gỡ tay.';

-- -----------------------------------------------------------------------------
-- menu_items — Header và Footer là HAI CÂY ĐỘC LẬP trong cùng một bảng
--
-- ⭐ Ràng buộc khó nhất của bảng này: một mục con KHÔNG được nằm khác vị trí với
--    cha nó. Kéo một mục của menu Header vào làm con của một mục Footer thì cây
--    vẫn hợp lệ về mặt cấu trúc, chỉ là cổng hiển thị sai — loại lỗi không có
--    thông báo nào.
--
--    Diễn đạt bằng CHECK thì không được (CHECK chỉ thấy một dòng). Cách làm ở
--    đây: khai UNIQUE (id, position) rồi cho khoá ngoại tham chiếu vào cặp đó,
--    tức là "cha phải có ĐÚNG vị trí này". CSDL từ chối, không phải service nhớ.
-- -----------------------------------------------------------------------------
CREATE TABLE menu_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL DEFAULT gen_random_uuid(),
    position     VARCHAR(20)  NOT NULL,
    parent_id    BIGINT,
    label        VARCHAR(255) NOT NULL,
    link_type    VARCHAR(20)  NOT NULL,
    category_id  BIGINT REFERENCES categories (id),
    article_id   BIGINT REFERENCES articles (id),
    url          VARCHAR(1000),
    open_new_tab BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Materialized path '/1/4/' — cùng cơ chế với categories/org_units/media_folders
    path         VARCHAR(500) NOT NULL,
    depth        SMALLINT     NOT NULL DEFAULT 0,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   timestamptz,
    updated_by   BIGINT,
    deleted_at   timestamptz,
    version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_menu_items_position CHECK (position IN ('HEADER', 'FOOTER')),
    CONSTRAINT ck_menu_items_depth CHECK (depth BETWEEN 0 AND 2),
    CONSTRAINT ck_menu_items_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_menu_items_link_type CHECK (
        link_type IN ('CATEGORY', 'ARTICLE', 'URL', 'EXTERNAL_DOC', 'NONE')
    ),
    -- Đúng MỘT đích, khớp với loại liên kết. Không có ràng buộc này thì một mục
    -- có thể vừa mang category_id vừa mang url, và nơi hiển thị phải tự đoán.
    -- NONE = mục chỉ để mở menu con (VD "Giới thiệu ▾") — bấm vào không đi đâu.
    CONSTRAINT ck_menu_items_target CHECK (
        (link_type = 'CATEGORY' AND category_id IS NOT NULL AND article_id IS NULL AND url IS NULL)
            OR (link_type = 'ARTICLE' AND article_id IS NOT NULL AND category_id IS NULL AND url IS NULL)
            OR (link_type IN ('URL', 'EXTERNAL_DOC') AND url IS NOT NULL
                AND category_id IS NULL AND article_id IS NULL)
            OR (link_type = 'NONE' AND category_id IS NULL AND article_id IS NULL AND url IS NULL)
    )
);

-- Cặp này chỉ tồn tại để khoá ngoại phía dưới bám vào được.
CREATE UNIQUE INDEX uq_menu_items_id_position ON menu_items (id, position);

ALTER TABLE menu_items
    ADD CONSTRAINT fk_menu_items_parent_same_position
        FOREIGN KEY (parent_id, position) REFERENCES menu_items (id, position);

CREATE UNIQUE INDEX uq_menu_items_public_id ON menu_items (public_id);
CREATE INDEX ix_menu_items_position ON menu_items (position, path) WHERE deleted_at IS NULL;
CREATE INDEX ix_menu_items_category ON menu_items (category_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_menu_items_article ON menu_items (article_id) WHERE deleted_at IS NULL;

COMMENT ON CONSTRAINT fk_menu_items_parent_same_position ON menu_items IS
    'Mục con phải cùng vị trí với cha. Ràng buộc này là lý do có UNIQUE (id, position).';
COMMENT ON COLUMN menu_items.link_type IS
    'NONE = mục chỉ mở menu con, không dẫn đi đâu. EXTERNAL_DOC = hệ thống văn bản điều hành '
    '(CN-01.7) — lưu URL, việc đăng nhập tự động thuộc G5 và chưa dựng.';
