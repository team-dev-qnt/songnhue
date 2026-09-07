-- =============================================================================
-- WS-28 / T28.1–T28.6 — Lược đồ danh mục MOD-03: loại chỉ số · nguồn API ·
--                        điểm đo · liên kết công trình  (CN-03.1)
--
-- Migration ĐẦU TIÊN của module `hydro`. Sau file này CSDL đã biết 19 điểm đo
-- thật của Công ty, nhưng chưa có một số đo nào — bảng time-series ở WS-29,
-- poller ở WS-31.
--
-- ⛔ Ba điều cấm, đều đã có tiền lệ hỏng trong dự án:
--
--   1. **Seed điểm đo bằng MÃ API `F#####`, cấm dùng tên.** Có hai công trình
--      khác nhau cùng tên "Yên Nghĩa" (`TB Yên Nghĩa` và `Cống tiêu tự chảy
--      Yên Nghĩa`) và cụm Liên Mạc có cả `Cống Liên Mạc` lẫn `Liên Mạc 2`.
--      Bản suy đoán trước đó dò theo giá trị đo đã **sai 1/4 mã** (`F01705`
--      đoán là Cống Phủ Lý, thực tế là Vân Đình hạ lưu — function-spec.md
--      CN-03.1). Bảng dưới đây là bảng ánh xạ Công ty cấp (chốt G8b), không
--      phải bảng suy ra.
--
--   2. **`river_name` / `chainage` / toạ độ để NULL.** G8 chưa có dữ liệu.
--      Điền "cho có" thì lớp GIS hiện 19 điểm sai vị trí, tệ hơn hẳn lớp GIS
--      rỗng — rỗng thì còn nằm trong danh sách nhắc việc.
--
--   3. **Không `REFERENCES constructions`** ở `station_constructions`
--      (conventions.md §10.4): `constructions` thuộc module `operations`, FK
--      xuyên module ở tầng CSDL là thứ ArchUnit không thấy được và nó khoá
--      chặt hai module lại với nhau.
--
-- ⚠ Hai chỗ file này **cố ý lệch** với `.claude/phase2-plan.md` §3 / T28.3 —
--    lý do ghi ngay tại cột tương ứng, đọc trước khi "sửa lại cho đúng plan":
--      • `stations` KHÔNG có cột `status` 4 giá trị  → khối ghi chú ở bảng 3.
--      • `api_sources` có `cron/frame/timeout/max_retry` NULLable, NULL nghĩa
--        là "dùng tham số chung ở `settings`" → khối ghi chú ở bảng 2.
-- =============================================================================


-- =============================================================================
-- 1. `measurement_types` — loại chỉ số quan trắc  (T28.1)
--
-- Danh mục TOÀN HỆ THỐNG, không thuộc phạm vi đơn vị nào ⇒ kế thừa `BaseEntity`
-- (7 cột chuẩn), KHÔNG phải `ScopedEntity`.
-- =============================================================================
CREATE TABLE measurement_types (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),

    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(100) NOT NULL,

    -- Đơn vị CHUẨN HOÁ đã lưu trong CSDL, không phải đơn vị nguồn trả về.
    -- Nguồn bhh40 trả mực nước bằng **cm**; adapter chia 100 lúc ingest
    -- (function-spec.md CN-03.1). Cột này là "m" và chỉ là "m" — nếu một ngày
    -- nào đó có nguồn thứ hai trả đơn vị khác thì chỗ quy đổi vẫn là adapter,
    -- không phải thêm cột `source_unit` ở đây rồi mỗi truy vấn tự nhớ quy đổi.
    unit        VARCHAR(20)  NOT NULL,

    -- Số chữ số thập phân khi lưu và khi hiển thị. Mực nước 3 (mm), lượng mưa 1.
    -- Đọc ở tầng chuẩn hoá (WS-30) và ở màn hình hiển thị/báo cáo (WS-34).
    value_scale SMALLINT     NOT NULL DEFAULT 3,

    sort_order  INTEGER      NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(500),

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_measurement_types_scale CHECK (value_scale BETWEEN 0 AND 6)
);

CREATE UNIQUE INDEX ux_measurement_types_public_id ON measurement_types (public_id);
CREATE UNIQUE INDEX ux_measurement_types_code ON measurement_types (code) WHERE deleted_at IS NULL;

COMMENT ON TABLE measurement_types IS
    'Loại chỉ số quan trắc (CN-03.1). `unit` là đơn vị ĐÃ CHUẨN HOÁ trong CSDL, không phải đơn vị nguồn.';


-- =============================================================================
-- 2. `api_sources` — nguồn dữ liệu bên thứ 3  (T28.2)
--
-- ⚠⚠ `credential` — conventions.md §4.7. Bốn điều bắt buộc, mỗi điều đều đã
--    từng là một sự cố ở đâu đó:
--      • Lưu **AES-256-GCM** qua `CryptoService`, khoá nằm NGOÀI CSDL. Bản sao
--        lưu trữ của CSDL đi ra khỏi phòng máy; khoá thì không.
--      • ⛔ KHÔNG có cột `credential_key_id`. `CryptoService.encrypt()` trả
--        chuỗi `<key_id>:<base64>` — id khoá đã NẰM TRONG bản mã, và
--        `keyIdOf()` đọc ra được. Một cột riêng là bản sao thứ hai của cùng
--        một sự thật, và nó sẽ lệch đúng vào lần xoay khoá — lúc bản mã đã
--        đổi mà cột chưa. Job xoay khoá lọc bằng `credential LIKE 'v1:%'`.
--        (Cùng cách `user_totp.secret_encrypted` đang làm — có tiền lệ.)
--      • ⛔ KHÔNG endpoint nào trả cột này, **kể cả cho Admin**; UI chỉ hiện
--        "đã cấu hình / chưa cấu hình". `exportable` của bản xuất cấu hình
--        M5.17 loại nó ra.
--      • ⛔ KHÔNG đưa vào payload của `jobs` — payload lưu nguyên văn trong
--        bảng và bảng đó nằm trong mọi bản sao lưu (§9.6).
--
-- ⚠ `cron` / `frame_minutes` / `timeout_seconds` / `max_retry` **NULLable, và
--    NULL là giá trị có nghĩa**: "dùng tham số chung ở bảng `settings`" (nhóm
--    HYDRO, seed từ `V202608131009`). Đây KHÔNG phải hai nguồn sự thật cho
--    cùng một tham số — thứ tự ưu tiên được giải ở **đúng một hàm**:
--    `ApiSourceService.thamSoHieuLuc(...)`, và endpoint chi tiết trả về **giá
--    trị đã giải** kèm cờ `dungThamSoChung` để người vận hành nhìn thấy mình
--    đang chịu tham số nào (§10.29-a: canh giá trị ĐÃ GIẢI, đừng canh giá trị
--    MẶC ĐỊNH). Có cột riêng vì nguồn thứ hai (API lượng mưa, G3-a) gần như
--    chắc chắn có nhịp khác nguồn mực nước.
-- =============================================================================
CREATE TABLE api_sources (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            UUID         NOT NULL DEFAULT gen_random_uuid(),

    code                 VARCHAR(30)  NOT NULL,
    name                 VARCHAR(255) NOT NULL,

    -- Adapter nào biết đọc nguồn này. Không phải "loại nguồn" chung chung:
    -- mỗi giá trị ứng với đúng một lớp `TelemetryAdapter` (WS-30).
    adapter_type         VARCHAR(30)  NOT NULL,

    base_url             VARCHAR(500) NOT NULL,

    -- Bản mã AES-256-GCM dạng `<key_id>:<base64>`, KHÔNG BAO GIỜ là khoá thô.
    -- NULL = chưa cấu hình → poller không chạy nguồn này và nói rõ lý do.
    credential           TEXT,

    -- NULL = dùng tham số chung, xem khối ghi chú ở trên.
    cron                 VARCHAR(100),
    frame_minutes        INTEGER,
    timeout_seconds      INTEGER,
    max_retry            INTEGER,

    -- === Sức khoẻ nguồn — MÁY ghi, một người ghi duy nhất là poller =========
    -- Ba cột này là bản ghi sự kiện gần nhất, không phải trạng thái suy diễn:
    -- chúng luôn đúng kể cả khi poller chết (lúc đó `last_success_at` cứ lùi
    -- xa dần, và đó chính là dấu hiệu cần thấy).
    last_success_at      timestamptz,
    last_failure_at      timestamptz,
    last_failure_reason  VARCHAR(500),
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,

    -- CON NGƯỜI quyết định: nguồn này còn dùng không.
    status               VARCHAR(20)  NOT NULL DEFAULT 'HOAT_DONG',
    description          VARCHAR(500),

    created_at           timestamptz  NOT NULL DEFAULT now(),
    created_by           BIGINT,
    updated_at           timestamptz,
    updated_by           BIGINT,
    deleted_at           timestamptz,
    version              INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_api_sources_adapter CHECK (adapter_type IN ('BHH40', 'MOCK')),
    CONSTRAINT ck_api_sources_status CHECK (status IN ('HOAT_DONG', 'TAM_DUNG')),
    -- Ràng buộc seed của `settings` (V202608131009) chép về đây để CSDL cũng
    -- từ chối, không chỉ tầng ứng dụng.
    CONSTRAINT ck_api_sources_timeout CHECK (timeout_seconds IS NULL OR timeout_seconds BETWEEN 5 AND 300),
    CONSTRAINT ck_api_sources_max_retry CHECK (max_retry IS NULL OR max_retry BETWEEN 0 AND 10),
    CONSTRAINT ck_api_sources_frame CHECK (frame_minutes IS NULL OR frame_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_api_sources_failures_nonneg CHECK (consecutive_failures >= 0),
    -- Bản mã phải mang tiền tố id khoá — `CryptoService.decrypt()` cắt theo dấu
    -- ':' đầu tiên. Một chuỗi không có tiền tố là bản mã không giải được, và nó
    -- chỉ lộ ra ở lượt polling đầu tiên, rất xa chỗ đã ghi sai.
    CONSTRAINT ck_api_sources_credential_format CHECK (
        credential IS NULL OR credential ~ '^[A-Za-z0-9_-]+:.+$'
    )
);

CREATE UNIQUE INDEX ux_api_sources_public_id ON api_sources (public_id);
CREATE UNIQUE INDEX ux_api_sources_code ON api_sources (code) WHERE deleted_at IS NULL;

COMMENT ON TABLE api_sources IS
    'Nguồn dữ liệu quan trắc bên thứ 3 (CN-03.2). `credential` mã hoá AES-256-GCM, không endpoint nào trả về.';
COMMENT ON COLUMN api_sources.credential IS
    '⛔ Bản mã AES-256-GCM dạng <key_id>:<base64>. Cấm log, cấm trả qua API (kể cả Admin), cấm đưa vào payload job, loại khỏi export M5.17.';
COMMENT ON COLUMN api_sources.cron IS
    'NULL = dùng `settings.hydro.polling.cron`. Giải ở ApiSourceService.thamSoHieuLuc(), một nơi duy nhất.';


-- =============================================================================
-- 3. `stations` — điểm đo  (T28.3)
--
-- Kế thừa `ScopedEntity` ⇒ **8 cột chuẩn** (7 của `BaseEntity` + `org_unit_id`).
-- Thiếu một cột thì `ddl-auto: validate` chặn TOÀN BỘ context test tích hợp,
-- không phải chỉ bài kiểm của module này.
--
-- ⚠ `org_unit_id` **NULLable ở v1** — khác mọi `ScopedEntity` khác của dự án.
--   Lý do: OI-05 chưa chốt 7 hay 8 Xí nghiệp nên không ai gán được đơn vị phụ
--   trách cho 19 điểm đo; để `NOT NULL` thì chính câu seed dưới đây không chạy
--   nổi. Hai hệ quả phải xử lý, không được im lặng:
--     a) Bộ lọc phạm vi của entity này có thêm vế `org_unit_id IS NULL` —
--        xem `Station.LOC_PHAM_VI`. Không có vế đó thì 19/19 điểm đo **vô hình
--        với tất cả mọi người**, kể cả SUPER_ADMIN ở path gốc `/1/`, vì
--        `NULL IN (…)` cho ra NULL chứ không phải TRUE.
--     b) Resolver người nhận cảnh báo (G11 tập 2) RỖNG cho tới khi gán xong ⇒
--        màn hình *"Điểm đo chưa gán đơn vị"* (T28.9) là việc bắt buộc, không
--        phải tính năng phụ.
--
-- ⛔⛔ KHÔNG có cột `status` 4 giá trị như `.claude/phase2-plan.md` T28.3 mô tả.
--    Cố ý. Bốn giá trị đó trộn hai bản chất khác nhau vào một cột:
--        NGUNG                       ← CON NGƯỜI quyết định
--        HOAT_DONG / MAT_TIN_HIEU    ← MÁY suy ra từ việc có số về hay không
--        OFFLINE                     ← MÁY, và trùng nghĩa với MAT_TIN_HIEU
--    Trộn lại thì lần đầu poller ghi `MAT_TIN_HIEU` là xoá mất quyết định
--    `NGUNG` của con người; lúc trạm có tín hiệu lại, một điểm đo đã ngừng tự
--    quay về `HOAT_DONG`. Đúng cái bẫy khối ghi chú của `constructions` đã
--    dựng biển báo (`V202608211026`).
--
--    Nhưng ở đây còn một lý do mạnh hơn để **không lưu** vế máy: trạng thái
--    tín hiệu lưu sẵn sẽ ĐỨNG YÊN đúng lúc nó quan trọng nhất. Poller chết →
--    không ai cập nhật cột → cả 19 điểm vẫn hiện `HOAT_DONG` trong khi không
--    có một số nào về. Vì vậy:
--        • CSDL lưu ĐÚNG vế con người: `active`.
--        • Vế tín hiệu suy ở lúc ĐỌC, từ `hydro_latest.reading_at` so với
--          `now()` và `settings.hydro.station.signal-loss-frames` (WS-29/31).
--          Không có bản ghi nào ⇒ CHUA_CO_DU_LIEU. Cách này đúng cả khi poller
--          đã chết, vì nó không phụ thuộc vào việc poller còn sống để ghi.
--    Bốn giá trị của plan vẫn tồn tại — ở tầng HIỂN THỊ, do
--    `TrangThaiDiemDo.suyRa(active, readingAt, now, khung)` sinh ra, một hàm
--    thuần có bài kiểm cho cả bốn nhánh.
-- =============================================================================
CREATE TABLE stations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Mã nội bộ của Công ty (`DO-LMAC-TL`). Người dùng đọc mã này.
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(255) NOT NULL,

    -- ⭐ Mã ánh xạ API bên thứ 3, dạng `F` + 5 chữ số. Đây là khoá nối duy nhất
    --    giữa response của nguồn và điểm đo trong hệ thống.
    --    ⛔ BẤT BIẾN sau khi seed: đổi mã này là âm thầm gán số liệu của trạm
    --       này sang trạm khác, và không một ràng buộc nào bắt được.
    api_code        VARCHAR(20)  NOT NULL,
    api_source_id   BIGINT       NOT NULL REFERENCES api_sources (id),

    -- Vai trò HIỂN THỊ/CHÍNH THỨC của điểm đo (A2b). Khác với
    -- `station_constructions.role`: một điểm đo có thể là HL của công trình này
    -- đồng thời là TL của công trình kế tiếp, nên vai trò theo từng liên kết
    -- nằm ở bảng liên kết; cột này là vai trò dùng cho biểu tổng hợp và nhãn GIS.
    position_role   VARCHAR(20)  NOT NULL,

    -- === Phạm vi đơn vị — nullable ở v1, xem khối ghi chú trên =============
    org_unit_id     BIGINT       REFERENCES org_units (id),

    -- === Vị trí — TẤT CẢ để NULL, chờ G8 ===================================
    -- ⛔ Cấm bịa, cấm suy từ tên điểm đo.
    river_name      VARCHAR(100),
    chainage        VARCHAR(20),
    chainage_m      INTEGER GENERATED ALWAYS AS (
        CASE WHEN chainage ~ '^K[0-9]+\+[0-9]{1,3}$'
             THEN split_part(substring(chainage FROM 2), '+', 1)::integer * 1000
                + split_part(chainage, '+', 2)::integer
        END
    ) STORED,
    latitude        NUMERIC(9,6),
    longitude       NUMERIC(9,6),
    geom            geometry(Point, 4326) GENERATED ALWAYS AS (
        CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL
             THEN ST_SetSRID(ST_MakePoint(longitude::float8, latitude::float8), 4326)
        END
    ) STORED,

    -- Nguồn đánh dấu một số điểm là "giá trị nội suy" (không đo trực tiếp).
    -- Giữ riêng, không trộn vào số đo thật: báo cáo phải phân biệt được.
    is_interpolated BOOLEAN      NOT NULL DEFAULT FALSE,

    -- CON NGƯỜI quyết định điểm đo còn dùng hay không. Đây là cột trạng thái
    -- DUY NHẤT được lưu — xem khối ghi chú ⛔⛔ ở trên.
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(500),

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0,

    -- ⛔ `MN_SONG` bắt buộc phải có mặt: 4/19 điểm đo là trạm thuỷ văn tham
    --    chiếu (TV Hà Nội, TV Ba Thá, An Cảnh, TB Hồng Vân). Thiếu giá trị này
    --    thì seed đổ ngay tại migration.
    CONSTRAINT ck_stations_position_role CHECK (
        position_role IN ('THUONG_LUU', 'HA_LUU', 'BE_HUT', 'MN_SONG', 'MUA')
    ),
    CONSTRAINT ck_stations_api_code_format CHECK (api_code ~ '^F[0-9]{5}$'),
    CONSTRAINT ck_stations_coords_paired CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_stations_lat_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_stations_lng_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_stations_chainage_format CHECK (
        chainage IS NULL OR chainage ~ '^K[0-9]+\+[0-9]{1,3}$'
    )
);

CREATE UNIQUE INDEX ux_stations_public_id ON stations (public_id);
CREATE UNIQUE INDEX ux_stations_code ON stations (code) WHERE deleted_at IS NULL;
-- ⚠ Chỉ mục MỘT PHẦN: xoá mềm một điểm đo rồi lập lại hồ sơ với đúng mã API cũ
--   là việc hợp lệ. Hệ quả bắt buộc nhớ: truy vấn tra `api_code` của poller
--   PHẢI kèm `deleted_at IS NULL`, nếu không có ngày nó chọn nhầm bản ghi đã xoá.
CREATE UNIQUE INDEX ux_stations_api_code ON stations (api_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_stations_org_unit ON stations (org_unit_id) WHERE deleted_at IS NULL;
-- Màn hình "Điểm đo chưa gán đơn vị" (T28.9) chạy trên đúng chỉ mục này.
CREATE INDEX ix_stations_chua_gan_don_vi ON stations (id) WHERE org_unit_id IS NULL AND deleted_at IS NULL;
CREATE INDEX ix_stations_source ON stations (api_source_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_stations_river ON stations (river_name, chainage_m) WHERE deleted_at IS NULL;
CREATE INDEX ix_stations_geom ON stations USING gist (geom);
CREATE INDEX ix_stations_name_search ON stations
    USING gin (sn_khong_dau(name) gin_trgm_ops) WHERE deleted_at IS NULL;

COMMENT ON TABLE stations IS
    'Điểm đo quan trắc (CN-03.1). `org_unit_id` NULLable ở v1 vì OI-05 chưa chốt — bộ lọc phạm vi phải có vế IS NULL.';
COMMENT ON COLUMN stations.api_code IS
    '⭐ Mã ánh xạ API bên thứ 3 (F#####), BẤT BIẾN sau seed. Khoá nối duy nhất giữa response nguồn và điểm đo.';


-- =============================================================================
-- 4. `station_measurement_types` — điểm đo ↔ loại chỉ số (n–n)  (T28.4)
--
-- Một điểm đo có thể đo nhiều chỉ số (mực nước + lượng mưa tại cùng vị trí).
-- Bảng nối thuần, không cột nghiệp vụ ⇒ khoá chính tổ hợp, ánh xạ JPA bằng
-- `@ManyToMany @JoinTable` chứ không phải một entity riêng.
-- =============================================================================
CREATE TABLE station_measurement_types (
    station_id          BIGINT NOT NULL REFERENCES stations (id),
    measurement_type_id BIGINT NOT NULL REFERENCES measurement_types (id),
    PRIMARY KEY (station_id, measurement_type_id)
);

CREATE INDEX ix_station_measurement_types_type ON station_measurement_types (measurement_type_id);

COMMENT ON TABLE station_measurement_types IS
    'Điểm đo ↔ loại chỉ số (n–n). Bảng nối thuần.';


-- =============================================================================
-- 5. `station_constructions` — điểm đo ↔ công trình (n–n có vai trò)  (T28.5)
--
-- ⛔⛔ KHÔNG `REFERENCES constructions` — `constructions` thuộc module
--    `operations`, `stations` thuộc `hydro` (conventions.md §10.4). Giữ cả
--    `construction_id` (để join nhanh) lẫn `construction_public_id` (định danh
--    ổn định dùng ở API và khi đối chiếu giữa hai module).
--
-- ⚠ Vì không có FK, tính toàn vẹn phải do TẦNG DỊCH VỤ giữ: mọi thao tác tạo
--   liên kết đi qua `ConstructionLookupPort` (spi của `operations`) để xác nhận
--   công trình có thật và còn sống. Không có port đó thì đây là một cột số
--   trỏ vào khoảng không.
--
-- ⚠ `is_primary`: bản ghi chính của một điểm đo phải có `role` TRÙNG
--   `stations.position_role` (A2b). CSDL không ép được ràng buộc liên bảng này
--   ⇒ ép ở service + bài kiểm bắt buộc.
--
-- ⛔ Migration này KHÔNG seed một dòng nào ở đây: danh mục `constructions` đang
--    rỗng (G8). Điểm đo `MN_SONG` (4/19) **hợp lệ khi không có dòng nào** —
--    trạm thuỷ văn tham chiếu. Đừng để `NOT NULL` hay inner join làm rớt chúng.
-- =============================================================================
CREATE TABLE station_constructions (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID        NOT NULL DEFAULT gen_random_uuid(),

    station_id             BIGINT      NOT NULL REFERENCES stations (id),
    -- ⛔ Không REFERENCES. Xem khối ghi chú trên.
    construction_id        BIGINT      NOT NULL,
    construction_public_id UUID        NOT NULL,

    role                   VARCHAR(20) NOT NULL,
    is_primary             BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at             timestamptz NOT NULL DEFAULT now(),
    created_by             BIGINT,
    updated_at             timestamptz,
    updated_by             BIGINT,
    deleted_at             timestamptz,
    version                INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT ck_station_constructions_role CHECK (
        role IN ('THUONG_LUU', 'HA_LUU', 'BE_HUT', 'MN_SONG', 'MUA')
    )
);

CREATE UNIQUE INDEX ux_station_constructions_public_id ON station_constructions (public_id);
CREATE UNIQUE INDEX ux_station_constructions_cap ON station_constructions (station_id, construction_id, role)
    WHERE deleted_at IS NULL;
-- Mỗi điểm đo có TỐI ĐA MỘT bản ghi chính. Ràng buộc "role của bản ghi chính
-- phải trùng position_role" thì CSDL không ép được, nhưng "chỉ một bản ghi
-- chính" thì ép được — và đây là nửa dễ hỏng hơn khi sửa qua nhiều màn hình.
CREATE UNIQUE INDEX ux_station_constructions_mot_ban_ghi_chinh ON station_constructions (station_id)
    WHERE is_primary AND deleted_at IS NULL;
CREATE INDEX ix_station_constructions_cong_trinh ON station_constructions (construction_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE station_constructions IS
    'Điểm đo ↔ công trình, n–n có vai trò (A2b). ⛔ KHÔNG FK sang constructions — khác module (§10.4).';


-- =============================================================================
-- 6. SEED
--
-- ⛔ Mọi câu seed dùng `ON CONFLICT DO NOTHING` **và** được khẳng định lại bằng
--    số hàng ở khối kiểm cuối file. §10.66: ngày 27/08/2026 một câu seed
--    `UPDATE` chạm **0 hàng — không lỗi, không log** và không ai biết trong
--    nhiều ngày. `ON CONFLICT DO NOTHING` một mình chính là cỗ máy sinh ra loại
--    hỏng đó; nó chỉ an toàn khi có người đếm lại.
-- =============================================================================

-- --- 6.1 Loại chỉ số (T28.1) -------------------------------------------------
-- ⛔ Giữ "Lượng mưa" dù v1 CHƯA CÓ NGUỒN (G3-a: nguồn bhh40 chỉ có `getmn.aspx`,
--    không có API lượng mưa). Cột "lượng mưa" của biểu §5.2 vì vậy **nhập tay**
--    cho tới khi có nguồn — Công ty phải biết điều này trước khi nghiệm thu.
--    Xoá loại chỉ số đi thì màn hình nhập tay cũng mất chỗ đứng.
INSERT INTO measurement_types (code, name, unit, value_scale, sort_order, description)
VALUES
    ('MUC_NUOC',  'Mực nước',  'm',    3, 10,
     'Nguồn trả bằng cm; adapter chia 100 lúc ingest (function-spec.md CN-03.1).'),
    ('LUONG_MUA', 'Lượng mưa', 'mm',   1, 20,
     'G3-a: v1 chưa có nguồn tự động — nhập tay. Giữ loại chỉ số để có chỗ nhập.'),
    ('LUU_LUONG', 'Lưu lượng', 'm³/s', 3, 30,
     'Chưa có nguồn ở v1; dùng cho báo cáo khi Công ty cấp số liệu.')
ON CONFLICT DO NOTHING;

-- --- 6.2 Nguồn API (T28.2) ---------------------------------------------------
-- ⚠ `credential` để NULL: migration KHÔNG mã hoá được (khoá nằm ngoài CSDL).
--   Khoá thật vào bảng theo đúng một đường: `ApiSourceCredentialBootstrap` đọc
--   `app.hydro.api.key` (biến môi trường `HYDRO_API_KEY`) lúc khởi động, mã hoá
--   và ghi vào **nếu cột đang rỗng**, kèm một sự kiện bảo mật. Sau lần đó CSDL
--   là nguồn sự thật duy nhất; đổi khoá thì đổi trên UI, không phải đổi biến
--   môi trường rồi tự hỏi vì sao không có tác dụng.
--
-- ⚠ `base_url` là **http://**, không phải https — nguồn của Công ty chỉ có HTTP.
--   Hệ quả bắt buộc: trình duyệt TUYỆT ĐỐI không gọi thẳng nguồn này; mọi lượt
--   gọi đi từ backend (nếu không thì trang https bị chặn mixed-content và khoá
--   API lộ ra trong DevTools của bất kỳ ai mở trang).
--
-- ⚠ Cả bốn cột tham số để NULL ⇒ nguồn này chạy theo tham số chung ở `settings`
--   (cron `45 1/2 * * * *`, khung 10 phút, timeout 30s, thử lại 3 lần).
INSERT INTO api_sources (code, name, adapter_type, base_url, description)
VALUES (
    'BHH40',
    'Telemetry Sông Nhuệ (bhh40.net)',
    'BHH40',
    'http://songnhue.bhh40.net',
    'Nguồn mực nước 19 điểm đo. ⚠ Không có API lịch sử — mất dữ liệu là mất vĩnh viễn (quy tắc 18). ⚠ Không có API lượng mưa (G3-a).'
)
ON CONFLICT DO NOTHING;

-- --- 6.3 ⭐ 19 điểm đo — bảng ánh xạ G8b (T28.6) -----------------------------
-- ⛔⛔ SEED BẰNG MÃ API. Cột `api_code` là thứ duy nhất Công ty bảo đảm, và là
--    thứ duy nhất response của nguồn mang theo.
-- ⛔ `river_name` / `chainage` / `latitude` / `longitude` để NULL — G8.
-- ⛔ `org_unit_id` để NULL — OI-05. Xuất hiện ngay ở màn hình T28.9.
INSERT INTO stations (code, name, api_code, api_source_id, position_role)
SELECT v.code, v.name, v.api_code, s.id, v.position_role
FROM (VALUES
    -- Cụm Liên Mạc — ⚠ hai công trình khác nhau: `Cống Liên Mạc` và `Liên Mạc 2`
    ('DO-LMAC-TL',        'Cống Liên Mạc — Thượng lưu',                'F01771', 'THUONG_LUU'),
    ('DO-LMAC-HL',        'Cống Liên Mạc — Hạ lưu',                    'F01672', 'HA_LUU'),
    ('DO-LMAC2-HL',       'Liên Mạc 2 — Hạ lưu',                       'F01965', 'HA_LUU'),

    ('DO-HDONG-TL',       'Hà Đông — Thượng lưu',                      'F01794', 'THUONG_LUU'),

    ('DO-DQUAN-TL',       'Đồng Quan — Thượng lưu',                    'F01905', 'THUONG_LUU'),
    ('DO-DQUAN-HL',       'Đồng Quan — Hạ lưu',                        'F01527', 'HA_LUU'),

    ('DO-NTUU-TL',        'Nhật Tựu — Thượng lưu',                     'F02031', 'THUONG_LUU'),
    ('DO-NTUU-HL',        'Nhật Tựu — Hạ lưu',                         'F02030', 'HA_LUU'),

    ('DO-LCO-TL',         'Lương Cổ — Thượng lưu',                     'F01519', 'THUONG_LUU'),

    ('DO-VDINH-TL',       'Vân Đình — Thượng lưu',                     'F01657', 'THUONG_LUU'),
    ('DO-VDINH-HL',       'Vân Đình — Hạ lưu',                         'F01705', 'HA_LUU'),

    ('DO-HMY-HL',         'Hòa Mỹ — Hạ lưu',                           'F02039', 'HA_LUU'),

    -- ⚠ HAI CÔNG TRÌNH KHÁC NHAU CÙNG TÊN "Yên Nghĩa". Mọi join phải đi bằng mã.
    ('DO-CTTC-YNGHIA-TL', 'Cống tiêu tự chảy Yên Nghĩa — Thượng lưu',   'F01820', 'THUONG_LUU'),
    ('DO-CTTC-YNGHIA-HL', 'Cống tiêu tự chảy Yên Nghĩa — Hạ lưu',       'F01652', 'HA_LUU'),
    ('DO-TB-YNGHIA-BH',   'Trạm bơm Yên Nghĩa — Bể hút',                'F01707', 'BE_HUT'),

    -- 4 điểm MN sông — trạm thuỷ văn tham chiếu, HỢP LỆ khi không liên kết
    -- công trình nào. Cảnh báo của nhóm này chỉ gửi "Ban điều hành" (G11).
    ('DO-TB-HVAN-MN',     'Trạm bơm Hồng Vân — Mực nước sông',          'F01732', 'MN_SONG'),
    ('DO-TV-HNOI-MN',     'Trạm thuỷ văn Hà Nội — Mực nước sông',       'F01559', 'MN_SONG'),
    ('DO-ANCANH-MN',      'An Cảnh — Mực nước sông',                    'F01812', 'MN_SONG'),
    ('DO-TV-BATHA-MN',    'Trạm thuỷ văn Ba Thá — Mực nước sông',       'F01532', 'MN_SONG')
) AS v(code, name, api_code, position_role)
CROSS JOIN (SELECT id FROM api_sources WHERE code = 'BHH40') AS s
ON CONFLICT DO NOTHING;

-- --- 6.4 Cả 19 điểm đo đều đo Mực nước ---------------------------------------
-- ⛔ KHÔNG gắn "Lượng mưa" cho điểm nào: nguồn không trả lượng mưa (G3-a). Gắn
--    sẵn thì biểu tổng hợp sinh 19 ô trống vĩnh viễn và không ai phân biệt được
--    "chưa có nguồn" với "trạm hỏng".
INSERT INTO station_measurement_types (station_id, measurement_type_id)
SELECT st.id, mt.id
FROM stations st
CROSS JOIN measurement_types mt
WHERE mt.code = 'MUC_NUOC'
ON CONFLICT DO NOTHING;


-- =============================================================================
-- 7. KHẲNG ĐỊNH SEED — §10.66
--
-- Ba khẳng định dưới đây cố ý **không chia sẻ giả định nào** với nhau và cũng
-- không chia sẻ giả định với câu INSERT ở trên (§10.62):
--   • đếm SỐ LƯỢNG  — bắt trường hợp thiếu/thừa dòng
--   • đếm mã DUY NHẤT — bắt trường hợp chép trùng một mã thành 2 dòng
--   • đối chiếu MỘT MÃ CỤ THỂ đã từng bị đoán sai (`F01705` = Vân Đình hạ lưu,
--     KHÔNG phải Cống Phủ Lý) — bắt trường hợp cả bảng đúng số lượng nhưng
--     chép nhầm một dòng.
-- =============================================================================
DO $$
DECLARE
    so_loai      INTEGER;
    so_diem      INTEGER;
    so_ma_duy    INTEGER;
    so_lien_ket  INTEGER;
    ten_f01705   TEXT;
    vai_tro_f01705 TEXT;
BEGIN
    SELECT count(*) INTO so_loai FROM measurement_types WHERE deleted_at IS NULL;
    IF so_loai <> 3 THEN
        RAISE EXCEPTION 'V202608311049: phải có đúng 3 loại chỉ số, đang có %', so_loai;
    END IF;

    SELECT count(*), count(DISTINCT api_code) INTO so_diem, so_ma_duy
    FROM stations WHERE deleted_at IS NULL;
    IF so_diem <> 19 THEN
        RAISE EXCEPTION 'V202608311049: phải seed đúng 19 điểm đo (bảng G8b), đang có %', so_diem;
    END IF;
    IF so_ma_duy <> 19 THEN
        RAISE EXCEPTION 'V202608311049: 19 điểm đo nhưng chỉ % mã API duy nhất — có mã bị chép trùng', so_ma_duy;
    END IF;

    SELECT name, position_role INTO ten_f01705, vai_tro_f01705
    FROM stations WHERE api_code = 'F01705' AND deleted_at IS NULL;
    IF vai_tro_f01705 IS DISTINCT FROM 'HA_LUU' OR ten_f01705 NOT LIKE 'Vân Đình%' THEN
        RAISE EXCEPTION
            'V202608311049: F01705 phải là Vân Đình hạ lưu (bảng G8b), đang là "%" vai trò %',
            ten_f01705, vai_tro_f01705;
    END IF;

    SELECT count(*) INTO so_lien_ket
    FROM station_measurement_types smt
    JOIN measurement_types mt ON mt.id = smt.measurement_type_id
    WHERE mt.code = 'MUC_NUOC';
    IF so_lien_ket <> 19 THEN
        RAISE EXCEPTION 'V202608311049: phải có đúng 19 liên kết điểm đo–Mực nước, đang có %', so_lien_ket;
    END IF;
END
$$;
