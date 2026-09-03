-- =============================================================================
-- WS-33 — Máy cảnh báo ngưỡng: `alert_levels` · `alert_rules` · `alert_events`
--
-- Ba bảng này là nguồn thật của **mắt xích 3** trong `ConstructionStatusService.tinh()`
-- — mắt xích duy nhất suốt Phase 1 và Phase 2 tới nay trả `false` ghi cứng
-- (`DummyHydroAlertService:16`). Nghĩa là trạng thái `CANH_BAO` chưa công trình
-- nào chạm tới được, và chuỗi 6 mắt xích **trông như** đã phủ vì bài kiểm mock cổng.
--
-- ⚠ Số hiệu `1055` là SỐ THỨ TỰ CHẠY TOÀN KHO, ⛔ không phải giờ-phút.
--    Đỉnh trước: `V202609021054`. §10.66 đã làm đỏ hai lượt CD liên tiếp vì đúng
--    chỗ này — `V202608241255` trông như 12:55 và rơi xuống dưới bản đã áp.
--
-- ⛔⛔ MIGRATION NÀY KHÔNG SEED MỘT DÒNG `alert_levels` NÀO
-- -----------------------------------------------------------------------------
-- Bộ mức ngưỡng là **G9-a**, Công ty chưa chốt (`business-open-questions.md`
-- Phần II). Seed sẵn "BĐ I / BĐ II / BĐ III" cho đẹp màn hình là đúng thứ
-- `CLAUDE.md` cấm: một mức cảnh báo bịa mang theo một CON SỐ bịa, và con số ấy
-- sẽ đứng im trong CSDL sau khi Công ty đưa số thật — không lệnh nào báo sai.
-- Màn hình rỗng phải nói *"chưa cấu hình ngưỡng"*, ⛔ không phát cảnh báo nào
-- (`HYD-2003`, T33.6). Đó là hành vi ĐÚNG, không phải hành vi thiếu.
--
-- ⛔ Và vì thế mức ngưỡng là **danh mục có CRUD**, ⛔ cấm enum trong mã (quy tắc
--    16): thêm một mức mới không được đòi một lượt deploy.
-- =============================================================================


-- =============================================================================
-- 1. `alert_levels` — danh mục mức cảnh báo  (T33.1)
--
-- ⚠ `color_token` là KHOÁ của bảng màu (`design-tokens`), ⛔ không phải mã hex.
--    Ràng buộc CHECK dưới đây chặn hẳn `#RRGGBB` ở tầng CSDL, không nhờ ai nhớ:
--    dự án đang có nợ T25.23 vì 29 mã màu ghi cứng lọt vào `admin-app`, và cách
--    rẻ nhất để không sinh thêm là làm cho giá trị sai KHÔNG LƯU ĐƯỢC.
-- =============================================================================
CREATE TABLE alert_levels (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID         NOT NULL DEFAULT gen_random_uuid(),

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(255) NOT NULL,

    -- Khoá màu trong `design-tokens`, VD `alert-level-1`.
    color_token   VARCHAR(60)  NOT NULL,

    -- ⭐ Càng lớn càng nặng. Đây là thứ quyết định mức nào thắng khi một điểm đo
    --    vượt nhiều ngưỡng cùng lúc — và nó phải DUY NHẤT, nếu không câu hỏi
    --    "mức nào nặng hơn" không có câu trả lời và kết quả phụ thuộc thứ tự DB trả về.
    severity_rank INTEGER      NOT NULL,

    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    description   VARCHAR(500),

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    timestamptz,
    updated_by    BIGINT,
    deleted_at    timestamptz,
    version       INTEGER      NOT NULL DEFAULT 0,

    -- ⛔ Chặn mã màu ghi cứng ở tầng CSDL: `#0d6efd` không lưu được.
    CONSTRAINT ck_alert_levels_color_token CHECK (color_token ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT ck_alert_levels_severity CHECK (severity_rank BETWEEN 1 AND 999)
);

CREATE UNIQUE INDEX ux_alert_levels_public_id ON alert_levels (public_id);
CREATE UNIQUE INDEX ux_alert_levels_code ON alert_levels (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_alert_levels_severity ON alert_levels (severity_rank) WHERE deleted_at IS NULL;

COMMENT ON TABLE alert_levels IS
    'Mức cảnh báo ngưỡng (G9-a). ⛔ CỐ Ý RỖNG cho tới khi Công ty đưa bộ mức thật — bảng rỗng là trạng thái hợp lệ.';
COMMENT ON COLUMN alert_levels.color_token IS
    '⛔ Khoá trong design-tokens, KHÔNG phải mã hex — CHECK chặn ở tầng CSDL (nợ T25.23).';


-- =============================================================================
-- 2. `alert_rules` — ngưỡng theo (điểm đo × loại chỉ số × mức)  (T33.2)
--
-- ⚠ Lịch sử thay đổi ngưỡng đi qua `audit_logs` (quy tắc 9), ⛔ KHÔNG dựng bảng
--    lịch sử riêng: hai kho lịch sử cho một loại thay đổi là hai kho sẽ lệch nhau.
--
-- ⭐ `delay_minutes` — điều kiện phải GIỮ được bao lâu thì mới thành cảnh báo thật.
--    Nguồn trả một khung 10' mỗi lượt; một giá trị nhảy vọt rồi về ngay là chuyện
--    có thật của cảm biến. Xem khối hysteresis ở bảng 3.
-- =============================================================================
CREATE TABLE alert_rules (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            UUID          NOT NULL DEFAULT gen_random_uuid(),

    station_id           BIGINT        NOT NULL REFERENCES stations (id),
    measurement_type_id  BIGINT        NOT NULL REFERENCES measurement_types (id),
    alert_level_id       BIGINT        NOT NULL REFERENCES alert_levels (id),

    -- GT | LT | OUT_OF_RANGE | RATE_OF_CHANGE — khớp enum `AlertConditionType`.
    -- ⚠ Ba nơi phải khớp nhau: CHECK này ↔ enum Java ↔ từ vựng FE. Có bài kiểm
    --   `HydroEnumSchemaTest` nhớ hộ (luật 14) — ⛔ đừng thêm giá trị ở một nơi.
    condition_type       VARCHAR(20)   NOT NULL,

    -- ⛔ NUMERIC, không float — quy tắc 2. Cùng thang với `hydro_readings.value`.
    threshold_value      NUMERIC(12,3) NOT NULL,
    -- Chỉ dùng cho OUT_OF_RANGE (cận trên). CHECK dưới ép đúng chiều.
    threshold_value_high NUMERIC(12,3),

    delay_minutes        INTEGER       NOT NULL DEFAULT 0,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    note                 VARCHAR(500),

    created_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           BIGINT,
    updated_at           timestamptz,
    updated_by           BIGINT,
    deleted_at           timestamptz,
    version              INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_alert_rules_condition CHECK (
        condition_type IN ('GT', 'LT', 'OUT_OF_RANGE', 'RATE_OF_CHANGE')
    ),

    -- ⭐ Cận trên tồn tại KHI VÀ CHỈ KHI loại là OUT_OF_RANGE. Viết bằng phép
    --    tương đương chứ không bằng hai câu OR rời: một luật "chỉ thiếu chiều
    --    ngược" là luật cho phép lưu `GT` kèm một cận trên không ai đọc.
    CONSTRAINT ck_alert_rules_high_paired CHECK (
        (condition_type = 'OUT_OF_RANGE') = (threshold_value_high IS NOT NULL)
    ),
    CONSTRAINT ck_alert_rules_high_order CHECK (
        threshold_value_high IS NULL OR threshold_value_high > threshold_value
    ),
    -- Tốc độ đổi là một ĐỘ LỚN (đơn vị/giờ) — số âm không có nghĩa.
    CONSTRAINT ck_alert_rules_rate_positive CHECK (
        condition_type <> 'RATE_OF_CHANGE' OR threshold_value > 0
    ),
    CONSTRAINT ck_alert_rules_delay CHECK (delay_minutes BETWEEN 0 AND 1440)
);

CREATE UNIQUE INDEX ux_alert_rules_public_id ON alert_rules (public_id);

-- Một điểm đo chỉ có MỘT ngưỡng cho mỗi (loại chỉ số × mức). Hai dòng "BĐ I của
-- mực nước tại Liên Mạc" với hai con số khác nhau là một câu hỏi không trả lời được.
CREATE UNIQUE INDEX ux_alert_rules_bo_ba
    ON alert_rules (station_id, measurement_type_id, alert_level_id)
    WHERE deleted_at IS NULL;

-- Truy vấn nóng nhất của hệ: mỗi số đo ghi xuống đều hỏi "điểm đo này có ngưỡng nào".
CREATE INDEX ix_alert_rules_danh_gia
    ON alert_rules (station_id, measurement_type_id)
    WHERE deleted_at IS NULL AND active = TRUE;

CREATE INDEX ix_alert_rules_level ON alert_rules (alert_level_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE alert_rules IS
    'Ngưỡng cảnh báo theo (điểm đo × loại chỉ số × mức). Lịch sử sửa đổi ở audit_logs, ⛔ không có bảng lịch sử riêng.';
COMMENT ON COLUMN alert_rules.delay_minutes IS
    'Điều kiện phải giữ được bấy nhiêu phút mới thành cảnh báo thật. 0 = xác nhận ngay.';


-- =============================================================================
-- 3. `alert_events` — lần vượt ngưỡng  (T33.3 · T33.4)
--
-- ⭐⭐ HYSTERESIS NẰM Ở ĐÂY, TRONG CSDL — ⛔ không nằm trong bộ nhớ tiến trình.
-- -----------------------------------------------------------------------------
-- §6.4 chốt "app stateless tuyệt đối". Một `Map<ruleId, dangVuot>` trong heap
-- mất sạch mỗi lượt deploy — và lượt deploy nào cũng rơi vào giữa một đợt lũ thì
-- cảnh báo đang mở bỗng bắn lại từ đầu, hoặc tệ hơn, im lặng đóng.
--
-- Vòng đời một dòng, ba trạng thái, mỗi trạng thái có ĐÚNG một nghĩa:
--
--   vượt ngưỡng lần đầu ──► DANG_XAY_RA, confirmed_at = NULL   (đang chờ đủ delay)
--         │                          │
--         │ giữ đủ `delay_minutes`   │ hết vượt TRƯỚC khi đủ delay
--         ▼                          ▼
--   confirmed_at = <mốc>       FALSE_ALARM, ended_at = <mốc>
--   ⇒ GỬI THÔNG BÁO 1 lần      ⇒ ⛔ chưa từng gửi thông báo nào
--         │
--         │ hết vượt
--         ▼
--   DA_XU_LY, ended_at = <mốc>
--
-- ⚠ `DA_XU_LY` có HAI người sinh ra, phân biệt bằng `resolved_by`:
--     * NULL      — mực nước tự rút về dưới ngưỡng. Cảnh báo hết vì lý do của nó.
--     * <user id> — người trực bấm "Đã xử lý" trên màn hình.
--   ⛔ Không tách thành trạng thái thứ tư: `alert_events.status` là thứ mắt xích 3
--   đọc, và một enum bốn giá trị cho một câu hỏi nhị phân là bốn nhánh phải nhớ.
--
-- ⛔ `FALSE_ALARM` cũng đặt được bằng tay (người trực xem lại và bác bỏ) — nên
--    ⛔ KHÔNG có CHECK `FALSE_ALARM ⇒ confirmed_at IS NULL`.
--
-- ⛔⛔ KHÔNG FK XUYÊN MODULE (T33.4, §10.4)
-- -----------------------------------------------------------------------------
-- Bảng này ⛔ KHÔNG `REFERENCES constructions`. Đường nối tới công trình đi qua
-- `station_constructions` (T28.19) — cùng module `hyd`, và là bảng người khai.
-- Chiều ngược lại, `maintenance_logs.alert_event_public_id UUID` đã có sẵn từ
-- V202608211028: một UUID trần, ⛔ không phải khoá ngoại.
-- =============================================================================
CREATE TABLE alert_events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID          NOT NULL DEFAULT gen_random_uuid(),

    rule_id             BIGINT        NOT NULL REFERENCES alert_rules (id),

    -- ⭐ Ba cột SAO CHÉP từ quy tắc lúc bắn, cố ý dư thừa. Một quy tắc bị sửa
    --    ngưỡng hoặc xoá mềm sau đó ⛔ không được phép làm lịch sử cảnh báo kể
    --    lại một câu chuyện khác. Đây là bản ghi của một SỰ KIỆN ĐÃ XẢY RA.
    station_id          BIGINT        NOT NULL REFERENCES stations (id),
    measurement_type_id BIGINT        NOT NULL REFERENCES measurement_types (id),
    alert_level_id      BIGINT        NOT NULL REFERENCES alert_levels (id),

    started_at          timestamptz   NOT NULL,
    confirmed_at        timestamptz,
    ended_at            timestamptz,

    status              VARCHAR(20)   NOT NULL DEFAULT 'DANG_XAY_RA',

    -- Giá trị làm điều kiện đúng lần đầu, và giá trị nặng nhất quan sát được.
    trigger_value       NUMERIC(12,3) NOT NULL,
    peak_value          NUMERIC(12,3) NOT NULL,
    peak_at             timestamptz   NOT NULL,

    -- Mô tả do `DanhGiaNguong` sinh ("2.30 > 2.00"), lưu để lịch sử đọc được mà
    -- không phải dựng lại phép so từ ba cột số.
    reason              VARCHAR(255)  NOT NULL,

    resolved_by         BIGINT,
    resolved_at         timestamptz,
    note                VARCHAR(500),

    created_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_alert_events_status CHECK (
        status IN ('DANG_XAY_RA', 'DA_XU_LY', 'FALSE_ALARM')
    ),
    -- Đang xảy ra thì chưa kết thúc; đã kết thúc thì phải có mốc. Phép tương
    -- đương, ⛔ không phải hai câu rời — xem lý do ở `ck_alert_rules_high_paired`.
    CONSTRAINT ck_alert_events_ended_paired CHECK (
        (status = 'DANG_XAY_RA') = (ended_at IS NULL)
    ),
    CONSTRAINT ck_alert_events_thu_tu_moc CHECK (
        (confirmed_at IS NULL OR confirmed_at >= started_at)
        AND (ended_at IS NULL OR ended_at >= started_at)
    ),
    CONSTRAINT ck_alert_events_resolved_paired CHECK (
        (resolved_by IS NULL) OR (resolved_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_alert_events_public_id ON alert_events (public_id);

-- Chống bắn trùng ở tầng CSDL (T33.3). Hai lượt đánh giá cùng một số đo — poller
-- gọi lại, hoặc một số đo NGHI_NGO được duyệt thành HOP_LE — ⛔ không được sinh
-- hai dòng. Đây là bảo đảm KHÔNG nhờ ai nhớ.
CREATE UNIQUE INDEX ux_alert_events_rule_started ON alert_events (rule_id, started_at);

-- ⭐⭐ Chính là hysteresis: MỘT quy tắc có tối đa MỘT cảnh báo đang mở. Lượt ghi
--    thứ hai đâm vào chỉ mục này, ⛔ không đâm vào một biến trong heap.
CREATE UNIQUE INDEX ux_alert_events_mot_cai_dang_mo
    ON alert_events (rule_id) WHERE status = 'DANG_XAY_RA';

-- Mắt xích 3 chạy trên chỉ mục này: "điểm đo nào đang có cảnh báo ĐÃ XÁC NHẬN".
-- ⚠ `confirmed_at IS NOT NULL` nằm trong ĐIỀU KIỆN của chỉ mục, không chỉ trong
--   câu truy vấn — một cảnh báo còn đang chờ đủ `delay_minutes` chưa phải cảnh báo.
CREATE INDEX ix_alert_events_dang_canh_bao
    ON alert_events (station_id)
    WHERE status = 'DANG_XAY_RA' AND confirmed_at IS NOT NULL;

CREATE INDEX ix_alert_events_lich_su ON alert_events (station_id, started_at DESC);
CREATE INDEX ix_alert_events_rule ON alert_events (rule_id, started_at DESC);

COMMENT ON TABLE alert_events IS
    '⭐ Lần vượt ngưỡng. Hysteresis lưu ở đây (chỉ mục ux_alert_events_mot_cai_dang_mo), ⛔ không lưu trong bộ nhớ. ⛔ Không FK sang constructions — nối qua station_constructions.';
COMMENT ON COLUMN alert_events.confirmed_at IS
    'Mốc điều kiện đã giữ đủ delay_minutes. NULL = chưa phải cảnh báo thật, ⛔ chưa gửi thông báo, ⛔ chưa tính vào mắt xích 3.';
COMMENT ON COLUMN alert_events.resolved_by IS
    'NULL = tự hết (giá trị về dưới ngưỡng); có giá trị = người trực đóng bằng tay.';


-- =============================================================================
-- 4. Gỡ `hydro.threshold.default-set` — trả nợ T33.12
--
-- Khoá này seed ngày 13/08 với giá trị RỖNG (`''`) và ⛔ chưa một dòng mã nào
-- đọc nó suốt 21 ngày. Luật 15: *một công tắc chưa ai đọc là một lỗi, không phải
-- việc để dành* — và `CLAUDE.md` nói thẳng hệ quả: ⛔ không seed tham số
-- `settings` cho tính năng chưa dựng.
--
-- T33.12 cho hai đường: nối vế đọc, hoặc gỡ. ⇒ **Gỡ**, vì nối vế đọc ở đây là
-- làm đúng thứ dự án cấm:
--
--   1. "Bộ ngưỡng mặc định khi tạo điểm đo mới" nghĩa là một tập CON SỐ áp tự
--      động cho mọi trạm mới. Những con số ấy là **G9-a**, Công ty chưa đưa. Đổ
--      một bộ mặc định vào đó là bịa ngưỡng — và ngưỡng bịa thì hoặc réo nhầm,
--      hoặc **im lặng đúng lúc cần kêu**.
--   2. Từ WS-33 đã có màn hình cấu hình ngưỡng thật. Một bộ mặc định áp ngầm khi
--      tạo điểm đo tạo ra những dòng `alert_rules` mà **không ai chọn** — và
--      chúng trông y hệt những dòng do người vận hành cố ý khai.
--   3. Ô này bày ra ở màn hình *Cấu hình hệ thống*: người vận hành sửa nó và
--      ⛔ không có gì đổi. Đó chính là §10.69 — *một tham số nói dối khó thấy hơn
--      một tham số không ai đọc*.
--
-- ⬜ Ngày Công ty muốn có bộ mặc định thật, khoá này dựng lại **cùng commit với
--    đoạn mã đọc nó** — đúng khuôn `hydro.source.alert-after-failures` ở
--    V202609021053.
--
-- ⚠ `DELETE` an toàn: khoá chưa từng có người đọc nên ⛔ không đường chạy nào
--    hỏng, và giá trị đang là chuỗi rỗng nên ⛔ không mất cấu hình của ai.
-- =============================================================================
DELETE FROM settings WHERE setting_key = 'hydro.threshold.default-set';
