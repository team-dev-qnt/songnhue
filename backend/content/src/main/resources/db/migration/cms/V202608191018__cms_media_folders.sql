-- =============================================================================
-- Thư viện media (WS-14 / T14.1) — CN-01.3
--
-- ⭐ CHỈ CÓ BẢNG THƯ MỤC, KHÔNG CÓ BẢNG TỆP — điểm nghiệp vụ 8.
--
-- Tệp media là dòng trong `attachments` của Core với `owner_type = 'MEDIA_FOLDER'`
-- và `owner_id` trỏ vào bảng này. Dựng bảng tệp thứ hai nghĩa là có hai nơi kiểm
-- định dạng, hai nơi tính dung lượng, hai nơi xoá mềm — và chúng sẽ lệch nhau,
-- lặng lẽ, theo thời gian. Pattern P3 (WS-6) sinh ra đúng để tránh chuyện đó.
-- =============================================================================

CREATE TABLE media_folders (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    parent_id   BIGINT REFERENCES media_folders (id),
    -- Materialized path '/1/4/9/' — cùng cách với org_units và categories
    path        VARCHAR(500) NOT NULL,
    depth       SMALLINT     NOT NULL DEFAULT 0,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER      NOT NULL DEFAULT 0,
    -- 3 cấp (Ảnh / Video / Tài liệu → con → cháu) = depth 0,1,2
    CONSTRAINT ck_media_folders_depth CHECK (depth BETWEEN 0 AND 2),
    CONSTRAINT ck_media_folders_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX uq_media_folders_public_id ON media_folders (public_id);
CREATE INDEX ix_media_folders_parent ON media_folders (parent_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_media_folders_path ON media_folders (path) WHERE deleted_at IS NULL;

-- Tên duy nhất trong cùng một thư mục cha. Trùng tên ở hai nhánh khác nhau thì được
-- ("Ảnh/2026" và "Tài liệu/2026" là hai thứ khác nhau), nhưng hai "2026" cạnh nhau
-- trong cùng một chỗ là người dùng không phân biệt nổi.
CREATE UNIQUE INDEX uq_media_folders_name_in_parent
    ON media_folders (COALESCE(parent_id, 0), lower(name)) WHERE deleted_at IS NULL;

COMMENT ON TABLE media_folders IS
    'Chỉ là thư mục. Tệp nằm ở attachments với owner_type = ''MEDIA_FOLDER'' (điểm nghiệp vụ 8).';

-- -----------------------------------------------------------------------------
-- Giới hạn dung lượng theo nhóm định dạng
--
-- ⚠ Ba khoá `limits.upload.max-mb.{image,document,gis}` đã seed từ WS-2. Ở đây chỉ
--   thêm nhóm `video` — CN-01.3 cho Video (MP4/WebM) tới 500MB, gấp 10 lần nhóm
--   tài liệu, nên gộp chung một hạn mức là hoặc chặn mất video hoặc mở toang cho
--   tài liệu.
--
-- ZIP đi vào nhóm `gis` (100MB) — trùng khớp với "ZIP 100MB" của CN-01.3, và cũng
-- đúng vì tệp GIS (KMZ, shapefile nén) chính là ZIP.
-- -----------------------------------------------------------------------------
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, v.exportable, v.ord
FROM (VALUES
    ('limits.upload.max-mb.video', '500', 'INTEGER',
     'LIMIT', 'Dung lượng tối đa mỗi tệp video (MB)',
     'CN-01.3. Khuyến nghị dùng nhúng YouTube thay vì tải video lên máy chủ',
     'min=1;max=2000', TRUE, 33)
) AS v(k, val, vtype, grp, label, descr, validation, exportable, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);
