-- ---------------------------------------------------------------------------
-- CN-02.4 / M2.9 — Lớp bản đồ GIS do người vận hành tự nạp (WS-59, 14/09/2026)
--
-- ⛔⛔ BẢNG NÀY GIAO ĐI RỖNG, và đó là điều ĐÚNG.
--
--    CLAUDE.md cấm seed dữ liệu công trình/thuỷ văn "cho đẹp demo". Một lớp
--    "Ranh giới quản lý" bịa sẵn trông y hệt một lớp Công ty đã số hoá, và
--    người nghiệm thu ⛔ không có cách nào phân biệt. Danh sách lớp điển hình
--    (kênh mương · ranh giới lưu vực · vùng cảnh báo lũ) nằm ở đặc tả, ⛔ không
--    nằm trong migration.
--
-- ⛔ Nội dung tệp KHÔNG nằm ở bảng này — nó đi qua `attachments`
--    (`owner_type = 'GIS_LAYER'`), tức dùng lại toàn bộ cơ chế đã có: quét
--    virus, hạn mức dung lượng, phiên bản, xoá mềm. Chép một `bytea` vào đây là
--    dựng kho tệp thứ hai với ⛔ không một cơ chế nào trong số đó.
-- ---------------------------------------------------------------------------

CREATE TABLE gis_layers (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID         NOT NULL DEFAULT gen_random_uuid(),

    name          VARCHAR(255) NOT NULL,
    description   VARCHAR(500),

    -- ⛔ Loại hình học KHAI RA, ⛔ không suy lúc vẽ. Một tệp GeoJSON trộn
    --   Point/LineString/Polygon là hợp lệ, nhưng bảng chọn kiểu vẽ (marker hay
    --   nét hay vùng) phải biết TRƯỚC khi tải nội dung về — nếu ⛔ không thì mỗi
    --   lần bật một lớp là một lượt tải vài MB chỉ để biết nên vẽ thế nào.
    geometry_type VARCHAR(20)  NOT NULL,

    -- Màu nét/nền, dạng `#RRGGBB`. ⚠ Đây là dữ liệu do người vận hành đặt cho
    -- TỪNG lớp bản đồ của họ — nó ⛔ không phải màu thương hiệu, nên nó ở CSDL
    -- chứ ⛔ không ở `design-tokens`.
    color         VARCHAR(7)   NOT NULL DEFAULT '#1677ff',

    -- 0–100. ⛔ Lưu số NGUYÊN phần trăm chứ ⛔ không lưu 0.0–1.0: đặc tả nói
    --   "opacity 0–100%", giao diện hiện thanh trượt 0–100, và một phép đổi đơn
    --   vị ở giữa là một chỗ để 0.8 và 80 lẫn vào nhau.
    opacity       SMALLINT     NOT NULL DEFAULT 70,

    -- Thứ tự chồng lớp (z-index) do người dùng kéo–thả. Số NHỎ vẽ trước (nằm dưới).
    sort_order    INTEGER      NOT NULL DEFAULT 0,

    -- Tắt một lớp mà ⛔ không xoá: lớp quy hoạch cũ vẫn cần giữ để đối chiếu.
    active        BOOLEAN      NOT NULL DEFAULT TRUE,

    -- ⛔ Tệp nội dung nằm ở `attachments`. NULL = lớp đã khai mà CHƯA nạp tệp —
    --   một trạng thái CÓ THẬT và phải phân biệt được với "lớp rỗng": người dùng
    --   tạo lớp trước rồi nạp tệp sau.
    attachment_public_id UUID,

    -- Số đối tượng hình học đọc được lúc nạp. ⛔ Ghi xuống chứ ⛔ không đếm lại
    -- mỗi lượt đọc: nó là một sự thật TẠI THỜI ĐIỂM nạp, và đếm lại nghĩa là
    -- phải tải cả tệp về chỉ để hiện một con số trên bảng danh sách.
    feature_count INTEGER,

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    timestamptz,
    updated_by    BIGINT,
    deleted_at    timestamptz,
    version       INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_gis_layers_geometry_type CHECK (
        geometry_type IN ('POINT', 'LINE', 'POLYGON', 'HON_HOP')
    ),
    CONSTRAINT ck_gis_layers_opacity CHECK (opacity BETWEEN 0 AND 100),
    CONSTRAINT ck_gis_layers_color CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT ck_gis_layers_name CHECK (length(btrim(name)) > 0),
    -- ⛔ Có tệp thì phải có số đối tượng, và ngược lại. Hai cột lệch nhau nghĩa
    --   là một lượt nạp hỏng giữa chừng — bắt ở CSDL thì nó đỏ ngay lượt ghi,
    --   ⛔ không đỏ ở một cuộc họp.
    CONSTRAINT ck_gis_layers_tep_va_so_doi_tuong CHECK (
        (attachment_public_id IS NULL AND feature_count IS NULL)
        OR (attachment_public_id IS NOT NULL AND feature_count IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_gis_layers_public_id ON gis_layers (public_id);

-- ⚠ Tên lớp duy nhất trong số lớp CÒN SỐNG. Vế `deleted_at IS NULL` load-bearing:
--   thiếu nó thì xoá một lớp là khoá chết cái tên ấy vĩnh viễn (bài học
--   `uq_users_employee_id`, T51.8).
CREATE UNIQUE INDEX uq_gis_layers_name ON gis_layers (lower(btrim(name))) WHERE deleted_at IS NULL;

CREATE INDEX ix_gis_layers_hien_thi ON gis_layers (sort_order, id) WHERE deleted_at IS NULL AND active;

COMMENT ON TABLE gis_layers IS
    'M2.9 — lớp bản đồ do Admin/Kỹ thuật nạp. Nội dung tệp ở attachments (owner_type = GIS_LAYER). '
    'Giao đi RỖNG: cấm seed lớp bịa (CLAUDE.md).';

DO $$
DECLARE so_dong INTEGER;
BEGIN
    SELECT count(*) INTO so_dong FROM gis_layers;
    IF so_dong <> 0 THEN
        RAISE EXCEPTION 'gis_layers phải RỖNG khi giao — cấm seed lớp bản đồ bịa (đang có % dòng)', so_dong;
    END IF;
END $$;
