-- =============================================================================
-- WS-29 / T29.1–T29.8 — Lưu trữ time-series MOD-03
--
--   hydro_raw_logs           nguyên văn response — APPEND-ONLY, phân mảnh tháng
--   hydro_readings           số đo đã parse      — phân mảnh tháng
--   hydro_latest             1 dòng / (điểm đo × loại chỉ số) — THAY CACHE REDIS
--   hydro_unmapped_readings  số đo của mã nguồn CHƯA KHAI (hôm nay 9 mã)
--   sync_logs                một dòng / một lượt polling
--
-- ⛔⛔ Vì sao file này quan trọng hơn vẻ ngoài của nó: nguồn `songnhue.bhh40.net`
--    **KHÔNG có API lịch sử** — đã đo ngày 01/09/2026, gọi kèm `&date=…&from=…&to=…`
--    trả về **byte y hệt** lượt không tham số. Không có đường backfill nào. Một
--    lượt ingest hỏng là mất dữ liệu **vĩnh viễn** (CLAUDE.md quy tắc 18).
--    Mọi quyết định lược đồ dưới đây đều nghiêng về *ghi được* thay vì *ghi đẹp*.
--
-- ⚠ Số hiệu `1052` là SỐ THỨ TỰ CHẠY TIẾP TOÀN KHO, không phải giờ-phút. Đỉnh
--   trước file này là `V202609011051`. Viết thành giờ-phút đã làm **hai lượt CD
--   đỏ liên tiếp** ngày 27/8 (§10.66) — cùng lỗi ấy còn làm một câu seed chạm
--   **0 hàng, không lỗi, không log**.
-- =============================================================================


-- =============================================================================
-- 0. Từ vựng dùng chung cho CẢ HAI bảng nhật ký  (T29.5)
--
-- `failure_kind` xuất hiện ở `hydro_raw_logs` (một lượt gọi HTTP) và ở
-- `sync_logs` (một lượt polling, có thể gồm nhiều lượt gọi). Cả hai dùng quy ước
-- **NULL = không có lỗi** — ⛔ không thêm một giá trị `OK` vào bộ từ vựng.
--
-- Các giá trị phải PHÂN BIỆT ĐƯỢC, vì mỗi nguyên nhân cần một cách xử lý khác
-- (§10.68-B — bản cũ của bước SSH cho *cùng một vân tay* cho ba nguyên nhân cần
-- ba cách xử lý ngược nhau):
--   THIEU_MA_SO  `api_sources.credential` rỗng → NGƯỜI phải vào cấu hình. Lượt
--                polling dừng TRƯỚC khi mở kết nối.
--   NOT_WORKING  nguồn trả chuỗi `not.working` → sai mã số, hoặc **thiếu dấu `;`
--                cuối mã số** — hai thứ này trông y hệt nhau từ phía ta.
--   TIMEOUT      hết thời gian chờ → mạng hoặc nguồn quá tải, thử lại có ích.
--   HTTP_ERROR   mã trạng thái ≠ 200 → nguồn còn sống nhưng từ chối.
--   EMPTY_BODY   200 nhưng không parse được dòng nào → định dạng nguồn đã đổi.
--                ⚠ Đây là loại nguy hiểm nhất: hệ thống *trông như* đang chạy.
--
-- ⚠⚠ HAI RÀNG BUỘC DƯỚI ĐÂY LỆCH NHAU ĐÚNG MỘT GIÁ TRỊ, VÀ ĐÓ LÀ CHỦ ĐÍCH:
--    `sync_logs` nhận cả **năm**; `hydro_raw_logs` nhận **bốn**, KHÔNG có
--    `THIEU_MA_SO`. Một dòng raw là một lượt gọi HTTP *đã xảy ra* — thiếu mã số
--    thì không có lượt gọi nào, nên cũng không có dòng raw nào mang lý do ấy.
--    Cho phép giá trị đó ở bảng raw là dựng sẵn một trạng thái không ai ghi
--    được (luật 15 ở tầng ràng buộc).
--
-- Ba nơi khai cùng một bộ từ vựng ⇒ luật 14. Phép kiểm nhớ hộ là
-- `HydroEnumSchemaTest`: nó đối chiếu enum Java với **cả hai** ràng buộc, và
-- khẳng định luôn khoảng chênh một giá trị — để nó là một quyết định, không phải
-- một chỗ quên.
-- =============================================================================


-- =============================================================================
-- 1. `hydro_raw_logs` — nguyên văn response, APPEND-ONLY  (T29.1, T29.2)
--
-- Phân mảnh RANGE theo `fetched_at` (tháng). Khoá chính bắt buộc chứa cột phân
-- mảnh → PK `(id, fetched_at)`, đúng khuôn `audit_logs` (`V202608131004`).
--
-- Khối lượng: 2 phút/lần ⇒ ~720 response/ngày ⇒ ~22k/tháng, mỗi bản ~1,6 KB.
-- Khoảng 35 MB/tháng. Retention riêng, ngắn hơn `hydro_readings` rất nhiều —
-- xem `hydro.raw-retention-days` ở cuối file.
--
-- ⛔ KHÔNG có `updated_at` / `deleted_at` / `version`: bảng này không kế thừa
--    `BaseEntity` và cũng không được phép có vòng đời. Một bản ghi raw đã ghi là
--    một sự kiện đã xảy ra; sửa nó là bịa lại lịch sử của nguồn.
-- =============================================================================
CREATE TABLE hydro_raw_logs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,

    -- Mốc **ta gọi**, không phải mốc nguồn đo. Đây cũng là khoá phân mảnh.
    fetched_at    timestamptz  NOT NULL DEFAULT now(),

    api_source_id BIGINT       NOT NULL REFERENCES api_sources (id),

    -- Khung 10' mà lượt gọi này nhắm tới: `floor(now / frame) * frame`. Cho phép
    -- hỏi "khung 10:20 ta đã gọi mấy lần" mà không phải tính lại từ `fetched_at`.
    -- NULL khi lượt gọi hỏng trước cả lúc xác định được khung.
    frame_start   timestamptz,

    http_status   INTEGER,
    duration_ms   INTEGER,

    -- ⭐ NGUYÊN VĂN response, chưa parse, chưa cắt. Ghi TRƯỚC khi parse.
    --   Nguồn trả 28 dòng `F#####;dd/MM/yyyy;HH:mm;value=<cm>;` nối bằng `<br>`
    --   rồi **một trang HTML rỗng** ở đuôi (có `__VIEWSTATE`) — giữ cả phần đuôi
    --   ấy, vì ngày nào nguồn đổi định dạng thì đây là bằng chứng duy nhất.
    --   NULL khi lượt gọi không nhận được byte nào (timeout / lỗi mạng).
    body          TEXT,
    body_bytes    INTEGER,

    -- NULL = lượt gọi thành công. ⚠ BỐN giá trị, ⛔ không có `THIEU_MA_SO` — xem §0.
    failure_kind  VARCHAR(30),
    failure_detail VARCHAR(1000),

    PRIMARY KEY (id, fetched_at),

    CONSTRAINT ck_hydro_raw_logs_failure_kind CHECK (
        failure_kind IS NULL OR failure_kind IN ('NOT_WORKING', 'TIMEOUT', 'HTTP_ERROR', 'EMPTY_BODY')
    ),
    CONSTRAINT ck_hydro_raw_logs_bytes CHECK (body_bytes IS NULL OR body_bytes >= 0),
    CONSTRAINT ck_hydro_raw_logs_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
) PARTITION BY RANGE (fetched_at);

CREATE INDEX ix_hydro_raw_logs_source_time ON hydro_raw_logs (api_source_id, fetched_at DESC);
CREATE INDEX ix_hydro_raw_logs_frame ON hydro_raw_logs (frame_start) WHERE frame_start IS NOT NULL;
CREATE INDEX ix_hydro_raw_logs_hong ON hydro_raw_logs (fetched_at DESC) WHERE failure_kind IS NOT NULL;

-- Lưới an toàn: bản ghi không rơi vào partition tháng nào vẫn ghi được. Bình
-- thường phải LUÔN RỖNG — có dòng ở đây nghĩa là job bảo trì partition đã chết.
-- Thà ghi chậm còn hơn INSERT lỗi làm hỏng giao dịch ingest, mà giao dịch ingest
-- hỏng ở đây = mất dữ liệu vĩnh viễn.
CREATE TABLE hydro_raw_logs_default PARTITION OF hydro_raw_logs DEFAULT;

COMMENT ON TABLE hydro_raw_logs IS
    'Nguyên văn response nguồn quan trắc. APPEND-ONLY: songnhue_app không có UPDATE/DELETE/TRUNCATE (T29.2).';
COMMENT ON COLUMN hydro_raw_logs.body IS
    'Nguyên văn, ghi TRƯỚC khi parse. Nguồn KHÔNG có API lịch sử — đây là bản sao duy nhất.';
COMMENT ON COLUMN hydro_raw_logs.failure_kind IS
    'NULL = thành công. Bốn giá trị dùng chung với sync_logs.failure_kind — canh bởi HydroEnumSchemaTest.';


-- -----------------------------------------------------------------------------
-- T29.2 — ⛔⛔ SIẾT LẠI QUYỀN NGAY TẠI ĐÂY
--
-- `V202608131006__core_db_role_grants.sql` đặt ALTER DEFAULT PRIVILEGES cấp sẵn
-- `UPDATE, DELETE` cho `songnhue_app` trên **mọi bảng tạo sau**, và chính file
-- đó đã ghi lời cảnh báo đích danh `hydro_raw_logs`. Quên khối này thì bảng mất
-- tính append-only mà **không một lệnh nào báo sai** — nó chỉ lộ ra vào ngày ai
-- đó cần chứng minh dữ liệu chưa bị sửa, tức là đúng lúc không còn chứng minh
-- được nữa.
--
-- ⚠ Quyền trên PARTITION không kế thừa từ bảng cha khi truy vấn **thẳng vào
--   partition**. Nên khối này siết cả cha lẫn partition DEFAULT, và hàm tạo
--   partition ở §6 siết từng partition mới. Đúng bài học của
--   `core_create_audit_partition`.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_app') THEN
        GRANT SELECT, INSERT ON hydro_raw_logs TO songnhue_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON hydro_raw_logs FROM songnhue_app;
        GRANT SELECT, INSERT ON hydro_raw_logs_default TO songnhue_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON hydro_raw_logs_default FROM songnhue_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_archiver') THEN
        GRANT SELECT, DELETE ON hydro_raw_logs TO songnhue_archiver;
        GRANT SELECT, DELETE ON hydro_raw_logs_default TO songnhue_archiver;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_readonly') THEN
        GRANT SELECT ON hydro_raw_logs TO songnhue_readonly;
        GRANT SELECT ON hydro_raw_logs_default TO songnhue_readonly;
    END IF;
END $$;


-- =============================================================================
-- 2. `hydro_readings` — số đo đã parse  (T29.3)
--
-- ⛔ KHÁC `hydro_raw_logs`: bảng này **không** append-only. Bản ghi
--    `quality = NGHI_NGO` được người có quyền `hyd:measurement:review` duyệt lên
--    `HOP_LE` qua Workflow engine (WS-32) ⇒ `songnhue_app` cần UPDATE, và nó đã
--    có sẵn từ default privileges. Không siết ở đây là **cố ý**, không phải quên.
--
-- ⚠⚠ `quality = NGHI_NGO` NẰM CHUNG BẢNG NÀY (CLAUDE.md quy tắc 14 — bẫy sai số
--    liệu dễ mắc nhất của dự án). Mọi truy vấn báo cáo / cảnh báo / tổng hợp
--    **phải lọc `quality = 'HOP_LE'`**. Bộ canh cho luật ấy dựng ở WS-32/T32.4;
--    ở tầng lược đồ, thứ giúp được là `hydro_latest` (§3) — nó tách sẵn *giá trị
--    hợp lệ gần nhất* khỏi *bản ghi gần nhất*, để widget và GIS không có cách
--    nào vô tình hiện một số đang bị nghi ngờ.
-- =============================================================================
CREATE TABLE hydro_readings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,

    -- ⭐ Mốc **nguồn đo**, không phải mốc ta ghi. Đây là khoá phân mảnh và là
    --   thành phần của khoá chống trùng: poll 2 phút/lần trên nguồn cập nhật 10
    --   phút/lần **sẽ trả trùng ở phần lớn các lượt gọi** — đó là bình thường.
    measured_at         timestamptz   NOT NULL,

    station_id          BIGINT        NOT NULL REFERENCES stations (id),
    measurement_type_id BIGINT        NOT NULL REFERENCES measurement_types (id),

    -- NUMERIC, ⛔ cấm float/double (CLAUDE.md quy tắc 2). Đơn vị là đơn vị CHUẨN
    -- HOÁ của `measurement_types.unit` — nguồn trả cm, adapter chia 100 lúc
    -- ingest, và phép chia ấy sống ở đúng một chỗ.
    reading_value       NUMERIC(12,3) NOT NULL,

    quality             VARCHAR(20)   NOT NULL DEFAULT 'HOP_LE',
    source              VARCHAR(20)   NOT NULL DEFAULT 'API',

    -- Truy ngược về nguyên văn response đã sinh ra dòng này. ⛔ Cố ý KHÔNG khai
    -- FOREIGN KEY: `hydro_raw_logs` có PK ghép `(id, fetched_at)` nên FK sẽ phải
    -- mang theo cả hai cột, và ràng buộc ấy khoá chặt vòng đời hai bảng lại với
    -- nhau — trong khi retention của raw NGẮN HƠN retention của readings rất
    -- nhiều (ngày so với năm). Có FK thì không dọn được raw mà không xoá số đo.
    raw_log_id          BIGINT,

    ingested_at         timestamptz   NOT NULL DEFAULT now(),

    -- Chỉ có nghĩa với `source = 'MANUAL'` (đường nhập tay khi API gián đoạn,
    -- WS-32/T32.7). Với `source = 'API'` luôn NULL — máy ghi thì không có người.
    created_by          BIGINT,
    note                VARCHAR(500),

    PRIMARY KEY (id, measured_at),

    -- Khoá chống trùng. Ràng buộc UNIQUE trên bảng phân mảnh **bắt buộc chứa
    -- toàn bộ cột phân mảnh** — `measured_at` có mặt, nên khai được ở tầng bảng
    -- cha và PostgreSQL tự dựng trên từng partition.
    CONSTRAINT ux_hydro_readings_diem_do_khung UNIQUE (station_id, measurement_type_id, measured_at),

    CONSTRAINT ck_hydro_readings_quality CHECK (quality IN ('HOP_LE', 'NGHI_NGO')),
    CONSTRAINT ck_hydro_readings_source CHECK (source IN ('API', 'MANUAL')),
    -- Nhập tay phải có người chịu trách nhiệm; máy ghi thì không được mượn tên ai.
    --
    -- ⚠⚠ Bản đầu viết `(source = 'MANUAL') OR (created_by IS NULL AND note IS NULL)`
    --    và chỉ ép được **nửa sau**: với `source = 'MANUAL'` thì vế trái đã TRUE
    --    nên `created_by` NULL đi lọt. Một ràng buộc khai HAI bảo đảm ngay trên
    --    đầu nó mà chỉ thi hành MỘT — đúng hình dạng luật 15/27 ở tầng CHECK, và
    --    nguy hiểm hơn một ràng buộc vắng mặt, vì dòng chú thích làm người đọc
    --    tin là đã có. Viết lại thành hai nhánh loại trừ nhau để mỗi vế phải tự
    --    đứng được.
    CONSTRAINT ck_hydro_readings_nguoi_nhap CHECK (
        (source = 'MANUAL' AND created_by IS NOT NULL)
        OR (source <> 'MANUAL' AND created_by IS NULL AND note IS NULL)
    )
) PARTITION BY RANGE (measured_at);

CREATE INDEX ix_hydro_readings_station_time ON hydro_readings (station_id, measured_at DESC);

-- Index cho màn hình "Dữ liệu nghi ngờ" (WS-32/T32.7). PARTIAL và rất nhỏ: bình
-- thường gần như mọi dòng đều `HOP_LE`, nên một index đầy đủ theo `quality` chỉ
-- tổ tốn chỗ mà không giúp truy vấn nào.
CREATE INDEX ix_hydro_readings_nghi_ngo ON hydro_readings (measured_at DESC)
    WHERE quality <> 'HOP_LE';

CREATE INDEX ix_hydro_readings_raw_log ON hydro_readings (raw_log_id) WHERE raw_log_id IS NOT NULL;

CREATE TABLE hydro_readings_default PARTITION OF hydro_readings DEFAULT;

COMMENT ON TABLE hydro_readings IS
    'Số đo đã parse, phân mảnh tháng theo measured_at. ⚠ quality=NGHI_NGO nằm chung bảng — báo cáo phải lọc HOP_LE (quy tắc 14).';
COMMENT ON COLUMN hydro_readings.measured_at IS
    'Mốc NGUỒN đo (mốc khung 10 phút), không phải mốc ta ghi. Xem ingested_at cho mốc ghi.';
COMMENT ON COLUMN hydro_readings.raw_log_id IS
    'Truy ngược về hydro_raw_logs.id. Cố ý KHÔNG có FK — retention của raw ngắn hơn readings rất nhiều.';


-- =============================================================================
-- 3. `hydro_latest` — bảng "mực nước hiện tại"  (T29.4)
--
-- ⭐ **Đây là thứ thay Redis** (kiến trúc đã chốt: không Redis ở v1). Dashboard,
--    widget cổng công khai và lớp GIS đọc bảng này; ⛔ không bảng nào trong số đó
--    được quét `hydro_readings` (quy tắc 8).
--
-- ⚠⚠ Bảng này có BỐN cột mốc/giá trị chứ không phải hai, và đó là chỗ chịu lực:
--
--    `last_seen_at`      bản ghi gần nhất **bất kể chất lượng**
--                        → dùng cho PHÁT HIỆN MẤT TÍN HIỆU (WS-31/T31.8).
--    `valid_measured_at` mốc của bản ghi **HỢP LỆ** gần nhất
--    `valid_value`       giá trị của bản ghi ấy
--                        → thứ duy nhất được HIỂN THỊ và được đem đi so ngưỡng.
--    `last_quality`      chất lượng của bản ghi gần nhất — để màn hình nói được
--                        *"số mới nhất đang bị nghi ngờ"* thay vì im lặng hiện
--                        một số cũ hơn.
--
--    Vì sao tách: nếu chỉ có một cặp `(measured_at, value)` thì mỗi nơi đọc phải
--    tự nhớ lọc `quality = 'HOP_LE'`, và **có nơi sẽ quên** — đó chính là quy tắc
--    14. Tách ra thì bảo đảm nằm ở *chỗ dữ liệu đi qua* chứ không ở *nơi gọi*
--    (luật 12): một widget đọc `valid_value` **không có cách nào** hiện nhầm số
--    nghi ngờ, kể cả khi người viết nó chưa từng đọc quy tắc 14.
--
--    Và tách ra còn giữ đúng một phân biệt khác: một trạm chỉ trả số nghi ngờ
--    **vẫn đang phát tín hiệu** — gộp hai vế thì nó bị báo là mất tín hiệu, sai
--    nguyên nhân và sai người xử lý.
-- =============================================================================
CREATE TABLE hydro_latest (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    station_id          BIGINT        NOT NULL REFERENCES stations (id),
    measurement_type_id BIGINT        NOT NULL REFERENCES measurement_types (id),

    last_seen_at        timestamptz   NOT NULL,
    last_quality        VARCHAR(20)   NOT NULL,
    last_source         VARCHAR(20)   NOT NULL,

    valid_measured_at   timestamptz,
    valid_value         NUMERIC(12,3),

    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ux_hydro_latest_diem_do_chi_so UNIQUE (station_id, measurement_type_id),
    CONSTRAINT ck_hydro_latest_quality CHECK (last_quality IN ('HOP_LE', 'NGHI_NGO')),
    CONSTRAINT ck_hydro_latest_source CHECK (last_source IN ('API', 'MANUAL')),
    -- Nửa cặp là một trạng thái vô nghĩa: có mốc mà không có số, hoặc ngược lại.
    -- Ép ở CSDL vì đây là bảng do UPSERT ghi, và UPSERT viết sai thì không có
    -- lượt review nào nhìn thấy.
    CONSTRAINT ck_hydro_latest_cap_hop_le CHECK (
        (valid_measured_at IS NULL) = (valid_value IS NULL)
    ),
    -- Bản ghi hợp lệ gần nhất không thể mới hơn bản ghi gần nhất.
    CONSTRAINT ck_hydro_latest_thu_tu CHECK (
        valid_measured_at IS NULL OR valid_measured_at <= last_seen_at
    )
);

CREATE INDEX ix_hydro_latest_station ON hydro_latest (station_id);
-- Truy vấn "trạm nào im lặng quá lâu" của job phát hiện mất tín hiệu.
CREATE INDEX ix_hydro_latest_seen ON hydro_latest (last_seen_at);

COMMENT ON TABLE hydro_latest IS
    'Mực nước hiện tại — 1 dòng/(điểm đo × loại chỉ số). THAY CACHE REDIS: widget/GIS/dashboard đọc bảng này, không quét readings.';
COMMENT ON COLUMN hydro_latest.last_seen_at IS
    'Bản ghi gần nhất BẤT KỂ chất lượng — dùng cho phát hiện mất tín hiệu, KHÔNG dùng để hiển thị.';
COMMENT ON COLUMN hydro_latest.valid_value IS
    'Giá trị HỢP LỆ gần nhất — cột DUY NHẤT được hiển thị và được so ngưỡng. NULL = chưa từng có bản hợp lệ.';


-- =============================================================================
-- 4. `hydro_unmapped_readings` — số đo của mã nguồn CHƯA KHAI  (§1.2 kế hoạch)
--
-- Đo ngày 01/09/2026: nguồn trả **28 mã**, hệ thống khai **19** (cả 19 đều có
-- trong response). **Chín mã chưa khai**: F01535 F01613 F01659 F01696 F01700
-- F01706 F01811 F01830 F01863.
--
-- ⛔ **Vẫn cấm tự tạo điểm đo từ mã lạ** — ta không biết tên, vị trí, thuộc công
--    trình nào; đó là G8, thuộc Công ty. Bản suy đoán trước đó dò theo giá trị đo
--    đã **sai 1/4 mã**.
-- ⭐ **Nhưng cũng không vứt số đo đi**: nguồn không có API lịch sử, nên bỏ hai
--    tháng là mất hai tháng của 9 trạm ấy **ngay cả sau khi** Công ty khai báo.
--
-- ⛔ Cách làm bị loại: cho `hydro_readings.station_id` NULLable. Lý do có thật,
--    không phải khẩu vị — `Station` đã phải xin **một ngoại lệ ArchUnit** cho cột
--    phạm vi NULLable (T28.23, kèm 3 bài tự-kiểm chứng minh ngoại lệ ấy không bị
--    lạm dụng). Thêm một lỗ NULL nữa trên đúng bảng mà quy tắc 14 bắt phải lọc là
--    mở cửa cho lớp lỗi *"quên lọc"* đi vào bảng lớn nhất hệ thống.
--
-- Không phân mảnh: 9 mã × 144 khung/ngày ≈ 1.300 dòng/ngày, và bảng này **rỗng
-- dần** theo tiến độ khai báo. Phân mảnh một bảng có xu hướng teo đi là thêm việc
-- bảo trì cho không.
-- =============================================================================
CREATE TABLE hydro_unmapped_readings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    api_code      VARCHAR(20)   NOT NULL,
    api_source_id BIGINT        NOT NULL REFERENCES api_sources (id),
    measured_at   timestamptz   NOT NULL,

    -- ⚠ Giá trị **NGUYÊN VĂN NGUỒN, CHƯA QUY ĐỔI**, kèm đơn vị nguồn khai.
    --   Chưa biết mã này là loại chỉ số gì thì cũng chưa biết quy đổi về đâu —
    --   quy đổi bây giờ là đoán. Lúc Công ty khai mã, job chuyển lịch sử sang
    --   `hydro_readings` mới quy đổi, và nó dùng ĐÚNG bộ quy đổi của adapter,
    --   không cài lại phép chia lần thứ hai.
    raw_value     NUMERIC(12,3) NOT NULL,
    raw_unit      VARCHAR(20)   NOT NULL,

    raw_log_id    BIGINT,
    ingested_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ux_hydro_unmapped_ma_khung UNIQUE (api_code, measured_at)
);

CREATE INDEX ix_hydro_unmapped_ma ON hydro_unmapped_readings (api_code, measured_at DESC);

COMMENT ON TABLE hydro_unmapped_readings IS
    'Số đo của mã nguồn chưa khai thành điểm đo. Giữ vì nguồn KHÔNG có API lịch sử — mất là mất vĩnh viễn.';
COMMENT ON COLUMN hydro_unmapped_readings.raw_value IS
    'Nguyên văn nguồn, CHƯA quy đổi — chưa biết loại chỉ số thì chưa biết quy đổi về đâu.';


-- =============================================================================
-- 5. `sync_logs` — một dòng cho một lượt polling  (T29.5)
--
-- Nuôi màn hình *Nhật ký đồng bộ* (M3.16) và là nơi trả lời câu hỏi vận hành
-- quan trọng nhất của MOD-03: **lượt vừa rồi không ghi được gì — vì nguồn hỏng,
-- vì đã đủ dữ liệu, hay vì ta chưa kịp gọi?** Ba câu trả lời ấy cần ba cách xử
-- lý ngược nhau, nên chúng phải là ba giá trị khác nhau chứ không phải một dòng
-- log chung.
-- =============================================================================
CREATE TABLE sync_logs (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    api_source_id  BIGINT       NOT NULL REFERENCES api_sources (id),
    started_at     timestamptz  NOT NULL DEFAULT now(),
    finished_at    timestamptz,
    duration_ms    INTEGER,

    -- Khung 10' mà lượt này nhắm tới. Cùng nghĩa với `hydro_raw_logs.frame_start`.
    frame_start    timestamptz,

    status         VARCHAR(30)  NOT NULL,
    failure_kind   VARCHAR(30),
    failure_detail VARCHAR(1000),

    -- Bốn bộ đếm RIÊNG BIỆT. Gộp chúng lại thì "ghi 0 dòng" đọc như một lỗi
    -- trong khi phần lớn các lượt gọi ghi 0 dòng một cách hoàn toàn bình thường
    -- (poll 2' trên nguồn 10' ⇒ 4/5 lượt là dữ liệu trùng).
    received_count INTEGER      NOT NULL DEFAULT 0,
    written_count  INTEGER      NOT NULL DEFAULT 0,
    skipped_count  INTEGER      NOT NULL DEFAULT 0,
    unmapped_count INTEGER      NOT NULL DEFAULT 0,

    raw_log_id     BIGINT,

    CONSTRAINT ck_sync_logs_status CHECK (
        status IN ('SUCCESS', 'PARTIAL', 'FAILED', 'SKIPPED_UP_TO_DATE')
    ),
    -- ⚠ NĂM giá trị — nhiều hơn ck_hydro_raw_logs_failure_kind đúng một, xem §0.
    CONSTRAINT ck_sync_logs_failure_kind CHECK (
        failure_kind IS NULL
        OR failure_kind IN ('THIEU_MA_SO', 'NOT_WORKING', 'TIMEOUT', 'HTTP_ERROR', 'EMPTY_BODY')
    ),
    -- FAILED phải nói được VÌ SAO. Một dòng FAILED không có `failure_kind` là
    -- đúng thứ §10.68-B mô tả: cùng một vân tay cho mọi nguyên nhân.
    CONSTRAINT ck_sync_logs_failed_co_ly_do CHECK (
        status <> 'FAILED' OR failure_kind IS NOT NULL
    ),
    -- Và ngược lại: SUCCESS mà có `failure_kind` là hai câu khẳng định trái nhau
    -- trên cùng một dòng.
    CONSTRAINT ck_sync_logs_success_khong_loi CHECK (
        status <> 'SUCCESS' OR failure_kind IS NULL
    ),
    CONSTRAINT ck_sync_logs_dem_khong_am CHECK (
        received_count >= 0 AND written_count >= 0 AND skipped_count >= 0 AND unmapped_count >= 0
    )
);

CREATE INDEX ix_sync_logs_source_time ON sync_logs (api_source_id, started_at DESC);
CREATE INDEX ix_sync_logs_hong ON sync_logs (started_at DESC) WHERE status IN ('FAILED', 'PARTIAL');

COMMENT ON TABLE sync_logs IS
    'Một dòng / một lượt polling (M3.16). Bốn trạng thái và bốn kiểu lỗi phải PHÂN BIỆT ĐƯỢC — §10.68-B.';
COMMENT ON COLUMN sync_logs.status IS
    'SKIPPED_UP_TO_DATE = cố ý không gọi vì toàn bộ trạm đã có bản ghi của khung hiện tại. KHÁC hẳn FAILED.';


-- =============================================================================
-- 6. Bảo trì partition  (T29.6, T29.9)
--
-- Chép khuôn `core_create_audit_partition` / `core_ensure_audit_partitions`
-- (`V202608131005`) — cùng bài học, cùng cách xử lý khi partition DEFAULT đang
-- giữ bản ghi của tháng cần tạo.
--
-- ⚠ Một hàm dùng chung cho CẢ HAI bảng phân mảnh, tham số hoá bằng tên bảng và
--   tên cột mốc. Viết hai bản gần giống nhau là dựng sẵn chỗ để chúng lệch nhau.
--
-- ⛔⛔ `SECURITY DEFINER` là BẮT BUỘC, không phải lựa chọn: `songnhue_app` KHÔNG
--    có quyền `CREATE` trên schema `public` — đó chính là điều `V202608131006`
--    dựng lên. Thiếu từ khoá này thì job bảo trì partition chạy được ở bài kiểm
--    (Flyway chạy bằng `songnhue_owner`) và **đỏ ở production** sau 12 tháng,
--    tức đúng lúc không ai còn nhớ vì sao. `core_create_audit_partition` đã đi
--    trước con đường này — xem `V202608131006` dòng 140-141.
--
-- ⚠ `search_path` cố định và MỌI tên bảng đều viết đủ `public.` — với một hàm
--   chạy bằng quyền chủ sở hữu thì `search_path` thừa hưởng từ người gọi là một
--   đường chiếm quyền: chỉ cần tạo được một bảng trùng tên ở schema đứng trước.
--   `pg_temp` đặt CUỐI có chủ đích (mặc định nó được tìm TRƯỚC `pg_catalog`).
-- =============================================================================
CREATE FUNCTION hyd_create_month_partition(p_table TEXT, p_month DATE)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_start DATE   := date_trunc('month', p_month)::date;
    v_end   DATE   := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    v_name  TEXT   := p_table || '_p' || to_char(v_start, 'YYYYMM');
    v_col   TEXT;
    v_stuck BIGINT;
BEGIN
    -- Danh sách trắng, không nhận tên bảng tuỳ ý: hàm này chạy `EXECUTE format`
    -- nên tên bảng là dữ liệu đi vào câu lệnh. Hai bảng, hai cột mốc, hết.
    IF p_table = 'hydro_raw_logs' THEN
        v_col := 'fetched_at';
    ELSIF p_table = 'hydro_readings' THEN
        v_col := 'measured_at';
    ELSE
        RAISE EXCEPTION 'hyd_create_month_partition: bảng % không nằm trong danh sách cho phép', p_table;
    END IF;

    IF to_regclass('public.' || quote_ident(v_name)) IS NOT NULL THEN
        RETURN FALSE;
    END IF;

    -- Partition DEFAULT đang giữ bản ghi của tháng này thì PostgreSQL từ chối
    -- tạo partition. Báo rõ và bỏ qua — ⛔ KHÔNG để migration/job đổ vỡ vì
    -- chuyện này, vì job đổ vỡ ở đây kéo theo poller không chạy.
    EXECUTE format(
        'SELECT count(*) FROM public.%I WHERE %I >= %L AND %I < %L',
        p_table || '_default', v_col, v_start, v_col, v_end)
    INTO v_stuck;

    IF v_stuck > 0 THEN
        RAISE WARNING
            'Bỏ qua % — %_default đang giữ % bản ghi thuộc tháng này. '
            'Xem docs/runbook/poller-chet.md để gỡ.', v_name, p_table, v_stuck;
        RETURN FALSE;
    END IF;

    EXECUTE format(
        'CREATE TABLE public.%I PARTITION OF public.%I FOR VALUES FROM (%L) TO (%L)',
        v_name, p_table, v_start, v_end);

    -- ⚠ Quyền trên partition KHÔNG kế thừa từ bảng cha khi truy vấn thẳng vào
    --   partition. Với `hydro_raw_logs` mà quên khối này thì app xoá được bằng
    --   `DELETE FROM hydro_raw_logs_p202609` — append-only chỉ còn trên giấy.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_app') THEN
        IF p_table = 'hydro_raw_logs' THEN
            EXECUTE format('GRANT SELECT, INSERT ON public.%I TO songnhue_app', v_name);
            EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON public.%I FROM songnhue_app', v_name);
        ELSE
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO songnhue_app', v_name);
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_archiver') THEN
        EXECUTE format('GRANT SELECT, DELETE ON public.%I TO songnhue_archiver', v_name);
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_readonly') THEN
        EXECUTE format('GRANT SELECT ON public.%I TO songnhue_readonly', v_name);
    END IF;

    RAISE NOTICE 'Đã tạo partition % (% → %)', v_name, v_start, v_end;
    RETURN TRUE;
END $$;

COMMENT ON FUNCTION hyd_create_month_partition IS
    'Tạo partition tháng cho hydro_raw_logs / hydro_readings + siết quyền. Idempotent.';


CREATE FUNCTION hyd_ensure_time_series_partitions(p_months_ahead INTEGER DEFAULT 6)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_created INTEGER := 0;
    v_table   TEXT;
    i         INTEGER;
BEGIN
    IF p_months_ahead < 0 THEN
        RAISE EXCEPTION 'p_months_ahead phải ≥ 0, nhận được %', p_months_ahead;
    END IF;

    FOREACH v_table IN ARRAY ARRAY['hydro_raw_logs', 'hydro_readings'] LOOP
        FOR i IN 0..p_months_ahead LOOP
            IF hyd_create_month_partition(v_table, (current_date + (i || ' month')::interval)::date) THEN
                v_created := v_created + 1;
            END IF;
        END LOOP;
    END LOOP;

    RETURN v_created;
END $$;

COMMENT ON FUNCTION hyd_ensure_time_series_partitions IS
    'Giữ runway partition cho cả hai bảng time-series. Trả về SỐ partition vừa tạo (0 = đã đủ, không phải lỗi).';

-- ⛔ PostgreSQL cấp EXECUTE cho PUBLIC theo mặc định. Với hàm SECURITY DEFINER
--   thì mặc định ấy là một lỗ hổng chứ không phải một tiện lợi: bất kỳ vai trò
--   nào kết nối được cũng tạo/xoá được bảng bằng quyền chủ sở hữu. Thu hồi rồi
--   cấp lại đúng cho `songnhue_app`.
REVOKE ALL ON FUNCTION hyd_create_month_partition(TEXT, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION hyd_ensure_time_series_partitions(INTEGER) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_app') THEN
        GRANT EXECUTE ON FUNCTION hyd_create_month_partition(TEXT, DATE) TO songnhue_app;
        GRANT EXECUTE ON FUNCTION hyd_ensure_time_series_partitions(INTEGER) TO songnhue_app;
    END IF;
END $$;


-- =============================================================================
-- 7. Dọn dữ liệu quá hạn — DROP PARTITION, không DELETE  (T29.7)
--
-- ⭐ Vì sao SECURITY DEFINER và vì sao drop partition thay vì `DELETE`:
--
--   a) `songnhue_app` **không có DELETE** trên `hydro_raw_logs` (T29.2) — đó là
--      điều đúng và không được nới. Nhưng dọn dữ liệu quá hạn vẫn phải làm được
--      mà không cần một kết nối thứ hai bằng vai trò khác: `hydro` là module
--      nghiệp vụ, nó **không được import** `core.infra` nơi `ArchiverJdbc` sống
--      (conventions.md §1.1, ArchUnit canh). Một hàm SECURITY DEFINER là cách
--      cấp đúng MỘT hành động cụ thể mà không cấp cả quyền DELETE.
--
--   b) DROP một partition là O(1) và không để lại bloat; `DELETE` hàng triệu
--      dòng rồi chờ autovacuum là cách chắc chắn để một job dọn dẹp trở thành
--      sự cố hiệu năng.
--
-- ⛔ Ba lớp chặn, vì đây là hàm XOÁ KHÔNG PHỤC HỒI ĐƯỢC chạy bằng quyền chủ sở
--    hữu — mỗi lớp chặn một cách hàm này có thể bị dùng sai:
--      1. `SET search_path` cố định — chặn đường chiếm quyền bằng schema giả.
--      2. Danh sách trắng tên bảng — tham số không đi thẳng vào `EXECUTE`.
--      3. **Sàn an toàn 7 ngày**: từ chối mọi mốc cắt mới hơn `now() - 7 ngày`.
--         Một lỗi đơn vị (ngày ↔ tháng) trong mã gọi khi ấy chỉ làm job đỏ, chứ
--         không xoá mất dữ liệu tuần này.
-- =============================================================================
CREATE FUNCTION hyd_drop_month_partitions_before(p_table TEXT, p_cutoff DATE)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_dropped INTEGER := 0;
    v_prefix  TEXT;
    v_name    TEXT;
    v_thang   DATE;
BEGIN
    IF p_table NOT IN ('hydro_raw_logs', 'hydro_readings') THEN
        RAISE EXCEPTION 'hyd_drop_month_partitions_before: bảng % không nằm trong danh sách cho phép', p_table;
    END IF;

    IF p_cutoff IS NULL OR p_cutoff > (current_date - 7) THEN
        RAISE EXCEPTION
            'Mốc cắt % quá gần hiện tại (sàn an toàn: cũ hơn % ). Xoá partition là không phục hồi được.',
            p_cutoff, current_date - 7;
    END IF;

    v_prefix := p_table || '_p';

    FOR v_name IN
        SELECT c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind = 'r'
           AND c.relname LIKE v_prefix || '______'
           AND c.relname ~ ('^' || v_prefix || '[0-9]{6}$')
         ORDER BY c.relname
    LOOP
        v_thang := to_date(right(v_name, 6) || '01', 'YYYYMMDD');
        -- Xoá khi TOÀN BỘ tháng ấy đã nằm trước mốc cắt. So bằng đầu tháng KẾ
        -- TIẾP: partition tháng 8 chỉ được xoá khi mốc cắt đã sang 01/09 trở đi.
        IF (v_thang + INTERVAL '1 month')::date <= p_cutoff THEN
            EXECUTE format('DROP TABLE public.%I', v_name);
            v_dropped := v_dropped + 1;
            RAISE NOTICE 'Đã xoá partition quá hạn % (mốc cắt %)', v_name, p_cutoff;
        END IF;
    END LOOP;

    RETURN v_dropped;
END $$;

-- ⛔ Thu hồi quyền chạy của PUBLIC trước, rồi cấp lại đúng cho `songnhue_app`.
--   PostgreSQL cấp EXECUTE cho PUBLIC theo mặc định — với một hàm SECURITY
--   DEFINER thì mặc định ấy là một lỗ hổng, không phải một tiện lợi.
REVOKE ALL ON FUNCTION hyd_drop_month_partitions_before(TEXT, DATE) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_app') THEN
        GRANT EXECUTE ON FUNCTION hyd_drop_month_partitions_before(TEXT, DATE) TO songnhue_app;
    END IF;
END $$;

COMMENT ON FUNCTION hyd_drop_month_partitions_before IS
    'Xoá partition tháng đã hết hạn lưu. SECURITY DEFINER + danh sách trắng + sàn an toàn 7 ngày.';


-- =============================================================================
-- 8. Runway 12 tháng ngay lúc migrate
--
-- Rộng tay vì partition rỗng gần như không tốn gì, và vì hết runway đúng vào
-- ngày job bảo trì đã chết là kịch bản không ai muốn gặp.
-- =============================================================================
SELECT hyd_ensure_time_series_partitions(11);


-- =============================================================================
-- 9. Tham số retention của `hydro_raw_logs`  (T29.7)
--
-- ⛔ Seed khoá **cùng lúc** với mã đọc nó, không sớm hơn (luật 15 · §10.9). Vế
--    đọc là `HydroSettings.soNgayGiuRawLog()` và người gọi là
--    `HydroRetentionHandler` — cả hai ra đời trong cùng đợt này. Tám khoá HYDRO
--    seed từ 13/8 đã nằm **18 ngày không ai đọc**; đừng thêm khoá thứ chín vào
--    tình trạng ấy.
--
-- Vì sao raw giữ NGẮN hơn readings rất nhiều (90 ngày so với 5 năm): raw chỉ có
-- giá trị khi cần đối chiếu *"số này parse từ đâu ra"* hoặc khi nguồn đổi định
-- dạng — cả hai đều là việc của vài tuần gần nhất. Giữ 5 năm raw là giữ ~2 GB
-- văn bản để trả lời một câu hỏi không ai hỏi, trong khi ngân sách bộ nhớ của
-- VPS đã được tính chặt (hosting_recommendations.md §8).
--
-- ⚠ Migration này CHỈ thêm khoá mới, ⛔ không đụng giá trị Admin đã sửa của bất
--   kỳ khoá nào — đúng lời dặn ở đầu `V202608131009`.
-- =============================================================================
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'hydro.raw-retention-days', '90', 'INTEGER', '90',
    'HYDRO', 'Số ngày lưu nguyên văn response nguồn',
    'Bản ghi thô của hydro_raw_logs. Ngắn hơn hẳn hạn lưu số đo (hydro.retention-years) '
    'vì raw chỉ dùng để đối chiếu khi nguồn đổi định dạng. Dọn bằng cách XOÁ HẲN partition tháng — '
    'không kết xuất, không phục hồi được.',
    'min=7;max=1825', TRUE, TRUE, 65
)
ON CONFLICT (setting_key) DO NOTHING;
