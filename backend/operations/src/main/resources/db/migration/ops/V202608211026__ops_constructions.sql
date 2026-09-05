-- =============================================================================
-- WS-17 / T17.1 + T17.4 + T17.5 + T17.11 — Danh mục công trình (CN-02.1)
--
-- Đây là bảng nghiệp vụ ĐẦU TIÊN thuộc phạm vi đơn vị. `org_unit_id NOT NULL`
-- không phải một cột thông tin: nó là thứ mà `ScopedEntity` + `ScopeFilterAspect`
-- dùng để lọc mọi truy vấn theo Xí nghiệp của người đăng nhập (conventions.md
-- §4.2 tầng 3). Suốt Phase 0 cơ chế đó chỉ chạy trên một entity dựng riêng cho
-- bài kiểm; từ migration này trở đi nó chạy trên dữ liệu thật.
--
-- ⛔ KHÔNG seed một dòng công trình nào. Danh mục thật đang chờ Công ty gửi
--    (mục G8). Seed "cho đẹp demo" thì tới lúc nghiệm thu không ai phân biệt
--    được số thật với số bịa — chốt của dự án, `CLAUDE.md`.
--
-- ⚠ Ba quyết định dễ bị hiểu nhầm nếu chỉ đọc DDL:
--
--   1. `total_investment` lưu **VND**, không phải triệu VND (điểm nghiệp vụ 18).
--      CN-02.1 ghi "triệu VND" còn CN-02.2 ghi "VND"; hai đơn vị trong cùng một
--      CSDL là lỗi cộng dồn chờ sẵn. Form nào muốn hiện triệu thì quy đổi ở FE.
--
--   2. `basin_note` là **trường văn bản**, cố ý (chốt F3). Không bảng danh mục
--      lưu vực, không ranh giới GIS riêng, không FK.
--
--   3. `operational_status` là **giá trị dẫn xuất** — xem khối ghi chú riêng ở
--      dưới. Không có đường nào cho người dùng ghi thẳng vào cột này.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Cụm công trình — G15 đã đóng: CHỈ là cách nhóm để hiển thị và lọc
--
-- ⛔ `cluster_id` TUYỆT ĐỐI không được xuất hiện trong bất kỳ truy vấn phân
--    quyền nào. Phạm vi dữ liệu đi bằng `org_unit_id`, và chỉ bằng nó. Nếu một
--    ngày cụm cần mang ý nghĩa phân quyền thì đường đúng là thêm một cấp vào
--    `org_units`, không phải nới ý nghĩa của cột này — vì lúc đó sẽ có hai
--    nguồn phạm vi và chúng sẽ lệch nhau.
-- -----------------------------------------------------------------------------
CREATE TABLE construction_clusters (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    -- Đơn vị quản lý cụm. Chỉ để hiển thị và lọc; không thay thế org_unit_id của
    -- từng công trình, vì một cụm có thể được bàn giao cho Xí nghiệp khác mà hồ
    -- sơ từng công trình vẫn phải giữ đơn vị phụ trách của chính nó.
    org_unit_id BIGINT       NOT NULL REFERENCES org_units (id),
    description VARCHAR(500),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_construction_clusters_public_id ON construction_clusters (public_id);
CREATE UNIQUE INDEX ux_construction_clusters_code ON construction_clusters (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_construction_clusters_org_unit ON construction_clusters (org_unit_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE construction_clusters IS
    'Cụm công trình — G15: chỉ là cách NHÓM để hiển thị/lọc, không mang ý nghĩa phân quyền.';

-- -----------------------------------------------------------------------------
-- Công trình
-- -----------------------------------------------------------------------------
CREATE TABLE constructions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Mã duy nhất TOÀN HỆ THỐNG (CN-02.1). Nhập tay, hệ thống chỉ gợi ý — điểm
    -- nghiệp vụ 13: Công ty đã có mã in trên hồ sơ giấy, ép tự sinh là buộc họ
    -- đổi mã đang dùng.
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    construction_type   VARCHAR(20)  NOT NULL,
    -- Tưới / Tiêu / Hỗn hợp — chủ yếu cho trạm bơm, để trống với loại khác.
    purpose             VARCHAR(20),

    -- === Phạm vi đơn vị — tầng 3 phân quyền =================================
    org_unit_id         BIGINT       NOT NULL REFERENCES org_units (id),
    management_level    VARCHAR(20)  NOT NULL DEFAULT 'XI_NGHIEP',
    cluster_id          BIGINT       REFERENCES construction_clusters (id),

    -- === Vị trí =============================================================
    address             VARCHAR(500),
    -- Decimal(9,6) theo CN-02.1: đủ ~0,1 m, và là kiểu chính xác — cấm float
    -- (quy tắc 2 của dự án).
    latitude            NUMERIC(9,6),
    longitude           NUMERIC(9,6),
    -- Cột PostGIS SINH RA TỪ toạ độ, không phải cột nhập song song.
    --
    -- ⭐ Đây là cách rẻ nhất để một giá trị dẫn xuất KHÔNG THỂ lệch: không có
    --    đường ghi nào, nên không có chỗ nào để quên cập nhật. Bài học lặp lại
    --    nhiều lần trong dự án là giá trị dẫn xuất được lưu rồi không ai tính
    --    lại thì nó âm thầm sai.
    geom                geometry(Point, 4326) GENERATED ALWAYS AS (
        CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL
             THEN ST_SetSRID(ST_MakePoint(longitude::float8, latitude::float8), 4326)
        END
    ) STORED,

    -- Tuyến sông + lý trình `K<km>+<m>` — cách hệ thống nguồn của Công ty đang
    -- định danh vị trí (CN-02.1). Giữ nguyên văn để đối chiếu được với biểu giấy.
    river_name          VARCHAR(100),
    chainage            VARCHAR(20),
    -- Lý trình quy ra mét, để SẮP XẾP dọc tuyến sông. Cũng là cột sinh: viết
    -- tay hai giá trị cho cùng một sự thật là hẹn trước một lần lệch nhau.
    chainage_m          INTEGER GENERATED ALWAYS AS (
        CASE WHEN chainage ~ '^K[0-9]+\+[0-9]{1,3}$'
             THEN split_part(substring(chainage FROM 2), '+', 1)::integer * 1000
                + split_part(chainage, '+', 2)::integer
        END
    ) STORED,

    -- Lưu vực / khu tưới tiêu — chốt F3: TRƯỜNG VĂN BẢN, không danh mục.
    basin_note          VARCHAR(500),

    -- === Hồ sơ chung ========================================================
    built_year          SMALLINT,
    commissioned_year   SMALLINT,
    designer            VARCHAR(255),
    contractor          VARCHAR(255),
    -- Đơn vị: VND (điểm nghiệp vụ 18). NUMERIC — cấm float cho mọi số tiền.
    total_investment    NUMERIC(18,2),
    description         TEXT,

    -- === Trạng thái =========================================================
    -- Hai cột, hai bản chất khác nhau — chỗ này hay bị gộp làm một rồi hỏng cả hai:
    --
    --   `lifecycle_state`     — CON NGƯỜI quyết định: công trình còn dùng, ngừng
    --                           theo mùa vụ, hay đã thanh lý. Sửa qua một endpoint
    --                           riêng, có audit.
    --   `operational_status`  — MÁY tính ra từ dữ liệu vận hành theo 5 mức ưu tiên
    --                           ở CN-02.1. Không endpoint nào nhận giá trị này;
    --                           client gửi lên → OPS-3001.
    --
    -- ⚠ Ở WS-17 chuỗi suy ra mới có mắt xích cuối (vòng đời) vì sự cố/bảo trì
    --   thuộc WS-18 và cảnh báo ngưỡng thuộc Phase 2. `ConstructionStatusService`
    --   là NƠI DUY NHẤT ghi cột này — WS-18/WS-19 thêm luật vào đúng hàm đó,
    --   không thêm đường ghi mới.
    lifecycle_state     VARCHAR(20)  NOT NULL DEFAULT 'DANG_HOAT_DONG',
    operational_status  VARCHAR(20)  NOT NULL DEFAULT 'BINH_THUONG',

    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          timestamptz,
    updated_by          BIGINT,
    deleted_at          timestamptz,
    version             INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_constructions_type CHECK (
        construction_type IN ('TRAM_BOM', 'CONG', 'KENH_MUONG', 'DE_DIEU', 'KHAC')
    ),
    CONSTRAINT ck_constructions_purpose CHECK (
        purpose IS NULL OR purpose IN ('TUOI', 'TIEU', 'HON_HOP')
    ),
    CONSTRAINT ck_constructions_management_level CHECK (
        management_level IN ('CONG_TY', 'XI_NGHIEP', 'CUM')
    ),
    CONSTRAINT ck_constructions_lifecycle CHECK (
        lifecycle_state IN ('DANG_HOAT_DONG', 'NGUNG_MUA_VU', 'DA_THANH_LY')
    ),
    CONSTRAINT ck_constructions_operational_status CHECK (
        operational_status IN ('BINH_THUONG', 'CANH_BAO', 'SU_CO', 'BAO_TRI', 'NGUNG_MUA_VU', 'DA_THANH_LY')
    ),
    -- Toạ độ đi theo cặp. Một nửa toạ độ là một điểm sai trên bản đồ, tệ hơn hẳn
    -- việc chưa số hoá — chưa số hoá thì còn nằm trong danh sách nhắc việc.
    CONSTRAINT ck_constructions_coords_paired CHECK (
        (latitude IS NULL) = (longitude IS NULL)
    ),
    CONSTRAINT ck_constructions_lat_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_constructions_lng_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_constructions_chainage_format CHECK (
        chainage IS NULL OR chainage ~ '^K[0-9]+\+[0-9]{1,3}$'
    ),
    CONSTRAINT ck_constructions_years CHECK (
        commissioned_year IS NULL OR built_year IS NULL OR commissioned_year >= built_year
    ),
    CONSTRAINT ck_constructions_investment_nonneg CHECK (
        total_investment IS NULL OR total_investment >= 0
    ),
    -- Cấp quản lý "Cụm" mà không gắn cụm nào thì hồ sơ tự mâu thuẫn.
    CONSTRAINT ck_constructions_cluster_level CHECK (
        management_level <> 'CUM' OR cluster_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_constructions_public_id ON constructions (public_id);
-- Mã duy nhất toàn hệ thống, nhưng chỉ trong số bản ghi còn sống: xoá mềm một
-- công trình rồi lập lại hồ sơ với đúng mã cũ là việc hợp lệ.
CREATE UNIQUE INDEX ux_constructions_code ON constructions (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_constructions_org_unit ON constructions (org_unit_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_constructions_type ON constructions (construction_type) WHERE deleted_at IS NULL;
CREATE INDEX ix_constructions_status ON constructions (operational_status) WHERE deleted_at IS NULL;
CREATE INDEX ix_constructions_cluster ON constructions (cluster_id) WHERE cluster_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_constructions_river ON constructions (river_name, chainage_m) WHERE deleted_at IS NULL;
CREATE INDEX ix_constructions_geom ON constructions USING gist (geom);
-- Tìm kiếm bỏ dấu: gõ "de dieu" phải ra "đê điều" (hàm dùng chung ở core).
CREATE INDEX ix_constructions_name_search ON constructions
    USING gin (sn_khong_dau(name) gin_trgm_ops) WHERE deleted_at IS NULL;

COMMENT ON COLUMN constructions.operational_status IS
    'GIÁ TRỊ DẪN XUẤT — chỉ ConstructionStatusService ghi. Client gửi lên bị từ chối OPS-3001.';
COMMENT ON COLUMN constructions.total_investment IS 'Đơn vị VND (điểm nghiệp vụ 18), KHÔNG phải triệu VND.';
COMMENT ON COLUMN constructions.basin_note IS 'Lưu vực / khu tưới tiêu — trường văn bản, chốt F3.';

-- -----------------------------------------------------------------------------
-- Thông số kỹ thuật theo loại — bảng 1-1, khoá chính là chính khoá ngoại
--
-- Vì sao tách bảng thay vì nhồi mọi cột vào `constructions`: trạm bơm có 9
-- thông số, cống có 8, và chúng không giao nhau. Để chung thì mỗi hồ sơ mang
-- theo mười mấy cột NULL, và không ràng buộc nào ngăn được một cái cống có
-- "số máy bơm" — dữ liệu bẩn mà CSDL vẫn coi là hợp lệ.
-- -----------------------------------------------------------------------------
CREATE TABLE pump_station_specs (
    construction_id     BIGINT PRIMARY KEY REFERENCES constructions (id) ON DELETE CASCADE,
    total_power_kw      NUMERIC(12,2),
    pump_count          SMALLINT,
    standby_pump_count  SMALLINT,
    -- Lưu lượng thiết kế mỗi máy (m³/s)
    flow_per_pump_m3s   NUMERIC(10,3),
    -- Tổng lưu lượng = số máy × lưu lượng/máy (CN-02.1 ghi rõ "auto").
    -- Cột sinh ở CSDL: quy tắc 3 nói giá trị tính toán tính ở BE, và không có
    -- chỗ nào "BE" hơn chỗ này — FE không có đường nào để tính ra số khác.
    total_flow_m3s      NUMERIC(14,3) GENERATED ALWAYS AS (
        CASE WHEN pump_count IS NOT NULL AND flow_per_pump_m3s IS NOT NULL
             THEN pump_count * flow_per_pump_m3s
        END
    ) STORED,
    head_m              NUMERIC(8,2),
    power_source        VARCHAR(100),
    voltage_kv          NUMERIC(8,3),
    -- Ngưỡng mực nước vận hành (m) — thông số hồ sơ, KHÁC ngưỡng cảnh báo thuỷ
    -- văn của MOD-03 (cái đó là danh mục có CRUD, thuộc Phase 2).
    operating_level_min_m NUMERIC(8,3),
    operating_level_max_m NUMERIC(8,3),
    CONSTRAINT ck_pump_specs_counts CHECK (
        (pump_count IS NULL OR pump_count > 0)
        AND (standby_pump_count IS NULL OR standby_pump_count >= 0)
    ),
    CONSTRAINT ck_pump_specs_levels CHECK (
        operating_level_min_m IS NULL OR operating_level_max_m IS NULL
        OR operating_level_max_m >= operating_level_min_m
    )
);

CREATE TABLE sluice_specs (
    construction_id     BIGINT PRIMARY KEY REFERENCES constructions (id) ON DELETE CASCADE,
    -- Hộp / Tròn / Van phẳng / Clape
    sluice_type         VARCHAR(20),
    bay_count           SMALLINT,
    -- Khẩu độ mỗi khoang (m)
    bay_width_m         NUMERIC(8,2),
    sill_elevation_m    NUMERIC(8,3),
    crest_elevation_m   NUMERIC(8,3),
    design_flow_m3s     NUMERIC(10,3),
    -- Thủ công / Điện / Thủy lực
    gate_operation      VARCHAR(20),
    upstream_warning_level_m  NUMERIC(8,3),
    upstream_danger_level_m   NUMERIC(8,3),
    CONSTRAINT ck_sluice_specs_type CHECK (
        sluice_type IS NULL OR sluice_type IN ('HOP', 'TRON', 'VAN_PHANG', 'CLAPE')
    ),
    CONSTRAINT ck_sluice_specs_gate CHECK (
        gate_operation IS NULL OR gate_operation IN ('THU_CONG', 'DIEN', 'THUY_LUC')
    ),
    CONSTRAINT ck_sluice_specs_bays CHECK (bay_count IS NULL OR bay_count > 0),
    CONSTRAINT ck_sluice_specs_elevation CHECK (
        sill_elevation_m IS NULL OR crest_elevation_m IS NULL
        OR crest_elevation_m >= sill_elevation_m
    ),
    -- Ngưỡng nguy hiểm phải cao hơn ngưỡng cảnh báo, nếu không thì cảnh báo
    -- không bao giờ bắn trước mức nguy hiểm — lỗi vô hình cho tới mùa lũ.
    CONSTRAINT ck_sluice_specs_levels CHECK (
        upstream_warning_level_m IS NULL OR upstream_danger_level_m IS NULL
        OR upstream_danger_level_m >= upstream_warning_level_m
    )
);

-- Kênh mương + đê điều — "hồ sơ tối thiểu" theo CN-02.1 / A3 đợt 1.
--
-- Một bảng cho cả hai loại vì hồ sơ tối thiểu của chúng trùng nhau: đều là công
-- trình tuyến, đều định vị bằng lý trình đầu–cuối. Cột nào chỉ một loại dùng thì
-- để trống, và `spec_note` giữ phần đặc thù dạng văn bản — đúng mức chi tiết mà
-- Công ty đã đồng ý ("bảng riêng chỉ khi cần").
CREATE TABLE linear_specs (
    construction_id     BIGINT PRIMARY KEY REFERENCES constructions (id) ON DELETE CASCADE,
    length_km           NUMERIC(10,3),
    start_chainage      VARCHAR(20),
    end_chainage        VARCHAR(20),
    -- Kênh: lưu lượng thiết kế. Đê: cao trình đỉnh.
    design_flow_m3s     NUMERIC(10,3),
    crest_elevation_m   NUMERIC(8,3),
    -- Cấp công trình theo phân cấp kỹ thuật (I, II, III, IV, V) — để văn bản vì
    -- cách ghi trên hồ sơ giấy của Công ty chưa thống nhất.
    technical_grade     VARCHAR(20),
    cross_section       VARCHAR(255),
    spec_note           TEXT,
    CONSTRAINT ck_linear_specs_length CHECK (length_km IS NULL OR length_km > 0),
    CONSTRAINT ck_linear_specs_start_format CHECK (
        start_chainage IS NULL OR start_chainage ~ '^K[0-9]+\+[0-9]{1,3}$'
    ),
    CONSTRAINT ck_linear_specs_end_format CHECK (
        end_chainage IS NULL OR end_chainage ~ '^K[0-9]+\+[0-9]{1,3}$'
    )
);
