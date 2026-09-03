-- =============================================================================
-- WS-34 / T34.1 — Bảng TỔNG HỢP NGÀY của số đo thuỷ văn + hàng đợi tính lại
--
--   T34.1  `hydro_agg_daily` — idempotent theo khoá kỳ, kèm `computed_at`
--   T34.1  `hydro_agg_dirty` — cờ bẩn, đánh bằng TRIGGER trên đường ghi
--   T34.2  mọi báo cáo/dashboard đọc bảng này, ⛔ không scan `hydro_readings`
--
-- ⛔ Migration này ⛔ KHÔNG seed một dòng số liệu nào. Bảng sinh ra rỗng và được
--    đổ đầy bằng chính số đo đã có (§5), ⛔ không bằng số bịa.
--
--
-- 0. Vì sao cần một bảng nữa, trong khi `hydro_readings` đã có đủ số liệu
-- -----------------------------------------------------------------------------
-- CLAUDE.md quy tắc 8: *báo cáo/dashboard đọc từ bảng agg, không scan raw*. Con
-- số ở đây không trừu tượng — một điểm đo × một loại chỉ số sinh **144 bản ghi
-- mỗi ngày** (khung 10'), tức ~52.500 dòng/năm cho MỘT cặp. Với 19 điểm đo đã
-- khai và hạn lưu 5 năm (chốt D5), báo cáo tháng của toàn hệ phải quét hàng
-- triệu dòng cho một bảng 30 hàng. NFR-04 đòi báo cáo tháng < 60 giây.
--
-- ⚠ Nhưng lý do NẶNG hơn hiệu năng: `hydro_readings` là bảng PHÂN MẢNH theo
--   tháng và có **hạn lưu**. Ngày nào retention dọn partition năm thứ sáu thì
--   số liệu ấy biến mất vĩnh viễn — nguồn ⛔ không có API lịch sử (quy tắc 18).
--   Bảng tổng hợp này nhỏ (19 × 2 × 365 ≈ 14 nghìn dòng/năm), ⛔ KHÔNG phân
--   mảnh, ⛔ KHÔNG bị retention dọn: nó là bản ghi dài hạn duy nhất còn lại sau
--   khi số đo chi tiết hết hạn. Xem §6.
--
--
-- 1. ⭐⭐ `quality` NẰM TRONG KHOÁ — quyết định chịu lực nhất của tệp này
-- -----------------------------------------------------------------------------
-- Cách làm quen thuộc là một hàng cho mỗi (điểm đo × chỉ số × ngày), với
-- max/min/TB tính trên `HOP_LE` và thêm một cột `suspect_count` bên cạnh. Nó
-- **sai theo đúng kiểu dự án này đã trả giá nhiều lần**: tên cột `max_value`
-- khi ấy hứa *"giá trị lớn nhất trong ngày"* nhưng thật ra là *"lớn nhất trong
-- số bản ghi hợp lệ"*, và lời hứa ấy chỉ sống trong một dòng chú thích. §10.69
-- gọi tên đúng hình dạng ấy: **một tham số nói dối khó thấy hơn một tham số
-- không ai đọc**.
--
-- ⇒ Ở đây `quality` là **thành phần của khoá**. Một ngày của một điểm đo có tối
--   đa ba hàng — `HOP_LE`, `NGHI_NGO`, `XOA` — và mỗi hàng nói đúng một điều về
--   đúng một tập. Hệ quả kép, cả hai đều là thứ ta muốn:
--
--   (a) Báo cáo nghiệp vụ (BC-05, BC-11) viết `WHERE quality = 'HOP_LE'` — đúng
--       vị từ mà `QualityFilterGuardTest` đang canh, nên bộ canh ấy **mở rộng
--       được sang bảng này mà không phải nới một chữ nào**. Bảng được thêm vào
--       `BANG_CANH` trong ĐÚNG commit này (luật 28: bộ canh phải nói ra phạm vi
--       của chính nó, và phạm vi ấy phải đi cùng lược đồ).
--
--   (b) BC-13 — báo cáo chất lượng dữ liệu — đọc **cả ba** hàng, và vì thế nó
--       phải khai một ngoại lệ CÓ TÊN ở bộ canh. Đó chính là điều đúng: một báo
--       cáo tồn tại ĐỂ đếm dữ liệu xấu thì việc nó không lọc là một quyết định,
--       và quyết định thì phải đi qua review. Cùng khuôn `SuspectReadingRepository`.
--
--
-- 2. ⚠⚠ `agg_date` là NGÀY GIỜ VIỆT NAM, ⛔ không phải ngày UTC
-- -----------------------------------------------------------------------------
-- Quy tắc 1: lưu `timestamptz` UTC, hiển thị UTC+7. Với một cột dấu thời gian
-- thì luật ấy vô hại. Với một cột NGÀY thì nó là chỗ sai số liệu lớn nhất có
-- thể mắc ở tệp này: `measured_at::date` cắt ngày theo UTC, nên **mọi số đo từ
-- 00:00 tới 06:59 giờ VN bị xếp sang ngày HÔM TRƯỚC**. Đó là 42 trong 144 khung
-- của một ngày — 29%.
--
-- Triệu chứng thì gần như vô hình: báo cáo vẫn ra 30 hàng, vẫn có max/min hợp
-- lý, chỉ là *"mực nước cao nhất ngày 12"* thật ra xảy ra rạng sáng ngày 13.
-- Không ngoại lệ, không dòng log, không cột đỏ — đúng ba thứ vắng mặt trong mọi
-- vụ sai số liệu của dự án này.
--
-- ⇒ Múi giờ sống ở ĐÚNG MỘT CẶP HÀM (§3), và §4 có một khối tự kiểm chứng chạy
--   ngay lúc migrate: nếu ai đó sửa một hàm mà quên hàm kia thì Flyway **hỏng
--   ngay tại đây**, ⛔ không phải ba tháng sau ở một ô báo cáo lệch 7 giờ.
-- =============================================================================


-- =============================================================================
-- 3. Cặp hàm múi giờ — MỘT chỗ duy nhất biết 'Asia/Ho_Chi_Minh'
--
-- ⚠ `timezone(text, timestamptz)` được PostgreSQL đánh dấu IMMUTABLE (nó chỉ
--   phụ thuộc CSDL múi giờ của hệ, không phụ thuộc phiên), nên hai hàm dưới đây
--   dùng được trong biểu thức chỉ mục và trong CHECK nếu sau này cần.
-- =============================================================================

CREATE OR REPLACE FUNCTION hyd_ngay_vn(moc timestamptz)
RETURNS date
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT (moc AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
$$;

COMMENT ON FUNCTION hyd_ngay_vn(timestamptz) IS
    'Mốc đo (UTC) → ngày làm việc giờ VN. ⛔ Đừng thay bằng ::date — nó cắt theo UTC '
    'và đẩy 42/144 khung mỗi ngày sang ngày hôm trước, im lặng.';

CREATE OR REPLACE FUNCTION hyd_dau_ngay_vn(ngay date)
RETURNS timestamptz
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT ngay::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
$$;

COMMENT ON FUNCTION hyd_dau_ngay_vn(date) IS
    'Ngày giờ VN → mốc 00:00 giờ VN dưới dạng timestamptz. Hàm NGƯỢC của hyd_ngay_vn — '
    'sửa một cái mà quên cái kia thì khối kiểm ở V202609031056 §4 làm Flyway hỏng ngay.';


-- =============================================================================
-- 4. ⭐ Tự kiểm chứng — chạy MỖI lượt migrate, kể cả trên máy dev
--
-- Luật 1 của dự án: *mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt
-- được vi phạm*. Cặp hàm trên là một cơ chế canh gác (nó giữ cho múi giờ chỉ
-- nằm ở một chỗ), nên nó phải tự chứng minh — và chứng minh Ở ĐÂY, vì một bài
-- kiểm Java chạy trong JVM có thể trùng múi giờ với CSDL và **về nguyên tắc**
-- không phân biệt được hai trạng thái (luật 9, cùng lớp lỗi với `HydroRetentionHandler`).
--
-- ⚠ Ba khẳng định, mỗi cái bắt một kiểu hỏng KHÁC nhau:
--   (a) khứ hồi  — hai hàm còn là cặp nghịch đảo
--   (b) biên     — micro-giây cuối của ngày vẫn thuộc ngày ấy, và micro-giây
--                  trước đó thuộc ngày hôm trước ⇒ ranh giới đặt đúng chỗ
--   (c) độ lệch  — 00:30 giờ VN ⛔ KHÔNG được rơi vào ngày hôm trước. Đây là
--                  khẳng định duy nhất phân biệt được `hyd_ngay_vn` với `::date`;
--                  thiếu nó thì cả khối này xanh trên một cài đặt sai.
-- =============================================================================

DO $$
DECLARE
    ngay_thu date := DATE '2026-09-03';
BEGIN
    IF hyd_ngay_vn(hyd_dau_ngay_vn(ngay_thu)) <> ngay_thu THEN
        RAISE EXCEPTION
            'hyd_ngay_vn/hyd_dau_ngay_vn không còn là cặp nghịch đảo: % → %',
            ngay_thu, hyd_ngay_vn(hyd_dau_ngay_vn(ngay_thu));
    END IF;

    IF hyd_ngay_vn(hyd_dau_ngay_vn(ngay_thu + 1) - INTERVAL '1 microsecond') <> ngay_thu THEN
        RAISE EXCEPTION 'Micro-giây cuối của % bị xếp sang ngày khác', ngay_thu;
    END IF;

    IF hyd_ngay_vn(hyd_dau_ngay_vn(ngay_thu) - INTERVAL '1 microsecond') <> ngay_thu - 1 THEN
        RAISE EXCEPTION 'Micro-giây trước 00:00 giờ VN của % không thuộc ngày hôm trước', ngay_thu;
    END IF;

    -- (c) 00:30 giờ VN ngày 3/9 = 17:30 UTC ngày 2/9. `::date` sẽ trả 2026-09-02.
    IF hyd_ngay_vn(TIMESTAMPTZ '2026-09-02 17:30:00+00') <> DATE '2026-09-03' THEN
        RAISE EXCEPTION
            'hyd_ngay_vn đang cắt ngày theo UTC — 00:30 giờ VN rơi vào ngày hôm trước. '
            'Đây là 42/144 khung mỗi ngày bị xếp sai, im lặng.';
    END IF;
END $$;


-- =============================================================================
-- 5. `hydro_agg_daily` — tổng hợp ngày
-- =============================================================================

CREATE TABLE hydro_agg_daily (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    station_id          BIGINT        NOT NULL REFERENCES stations (id),
    measurement_type_id BIGINT        NOT NULL REFERENCES measurement_types (id),

    -- ⚠ NGÀY GIỜ VN — xem §2. Luôn ghi bằng `hyd_ngay_vn(measured_at)`.
    agg_date            date          NOT NULL,

    -- ⭐ Thành phần khoá, ⛔ không phải một cột mô tả — xem §1.
    quality             VARCHAR(20)   NOT NULL,

    -- Số bản ghi thuộc đúng nhóm chất lượng ấy trong ngày ấy. Nhờ ràng buộc
    -- `ux_hydro_readings_diem_do_khung` (một bản ghi cho mỗi mốc), con số này
    -- CŨNG LÀ số khung 10' đã nhận được — vế bị trừ của cột "số khung bỏ sót"
    -- ở BC-13, tức phép đo duy nhất của NFR-03.
    reading_count       INTEGER       NOT NULL,

    min_value           NUMERIC(12,3) NOT NULL,
    -- ⭐ BC-05 đòi "kèm thời điểm đạt max/min". Tính ở đây một lần thay vì bắt
    --   báo cáo quay lại quét raw — đúng quy tắc 8.
    --   Hoà: mốc SỚM NHẤT đạt giá trị ấy (xem `HydroAggRepository`).
    min_at              timestamptz   NOT NULL,
    max_value           NUMERIC(12,3) NOT NULL,
    max_at              timestamptz   NOT NULL,

    -- Thang rộng hơn cột nguồn: trung bình của các số 3 chữ số thập phân không
    -- phải là một số 3 chữ số thập phân, và làm tròn ở tầng lưu là vứt đi độ
    -- chính xác mà tầng hiển thị đằng nào cũng phải làm tròn lần nữa.
    avg_value           NUMERIC(14,5) NOT NULL,

    -- ⭐⭐ `sum_value` KHÔNG dư thừa — nó là điều kiện để BC-05 (tháng) đúng.
    --
    -- Trung bình tháng ⛔ KHÔNG phải trung bình của các trung bình ngày: ngày
    -- có 144 bản ghi và ngày poller chết chỉ có 12 bản ghi sẽ được tính CÙNG
    -- trọng số, và một ngày dữ liệu thưa kéo cả tháng theo nó. Đúng — và đây là
    -- phần đắt — con số sai ấy vẫn nằm trong khoảng min/max của tháng, nên
    -- ⛔ không có cách nào nhìn ra bằng mắt.
    --
    -- Với `sum_value` thì trung bình tháng = SUM(sum_value)/SUM(reading_count),
    -- đúng theo trọng số, và phép cộng ấy vẫn chỉ đọc bảng agg.
    sum_value           NUMERIC(18,3) NOT NULL,

    -- Mốc đầu/cuối THẬT của dữ liệu trong ngày — ⛔ không phải 00:00/23:50.
    -- Nhờ nó mà một ngày chỉ có dữ liệu buổi sáng nói được điều đó.
    first_at            timestamptz   NOT NULL,
    last_at             timestamptz   NOT NULL,

    -- Lượt tính lại gần nhất. Đây là thứ trả lời "số này cũ tới mức nào" khi
    -- người dùng vừa duyệt một bản ghi nghi ngờ và bảng chưa kịp cập nhật.
    computed_at         timestamptz   NOT NULL DEFAULT now(),

    -- ⭐ Khoá kỳ. `ON CONFLICT` của lượt tính lại bám đúng bộ bốn này.
    CONSTRAINT ux_hydro_agg_daily_ky UNIQUE (station_id, measurement_type_id, agg_date, quality),

    -- ⚠ Ba giá trị, khớp `ck_hydro_readings_quality` sau V202609021054.
    --   `HydroEnumSchemaTest` canh cặp enum ↔ CHECK này.
    CONSTRAINT ck_hydro_agg_daily_quality CHECK (quality IN ('HOP_LE', 'NGHI_NGO', 'XOA')),

    -- ⛔ Một hàng agg với 0 bản ghi là một câu khẳng định RỖNG đội lốt số liệu
    --   (quy tắc 16). Không có bản ghi nào thì ⛔ KHÔNG có hàng — và BC-13 đọc
    --   sự VẮNG MẶT ấy thành "bỏ sót", đúng thứ nó phải đo.
    CONSTRAINT ck_hydro_agg_daily_dem CHECK (reading_count > 0),

    CONSTRAINT ck_hydro_agg_daily_bien CHECK (min_value <= max_value),
    CONSTRAINT ck_hydro_agg_daily_moc CHECK (first_at <= last_at),

    -- ⭐ Bốn ràng buộc dưới đây bắt một lượt TÍNH LẠI HỎNG, không bắt dữ liệu
    --   xấu. Chúng rẻ, và chúng là thứ duy nhất phân biệt được "bảng agg đúng"
    --   với "bảng agg có số" — mà hai thứ ấy trông y hệt nhau trên màn hình.
    CONSTRAINT ck_hydro_agg_daily_tb_trong_bien CHECK (
        avg_value >= min_value AND avg_value <= max_value
    ),
    CONSTRAINT ck_hydro_agg_daily_tong_khop CHECK (
        sum_value >= min_value * reading_count AND sum_value <= max_value * reading_count
    ),
    CONSTRAINT ck_hydro_agg_daily_moc_min_trong_ngay CHECK (
        min_at >= first_at AND min_at <= last_at
    ),
    CONSTRAINT ck_hydro_agg_daily_moc_max_trong_ngay CHECK (
        max_at >= first_at AND max_at <= last_at
    )
);

-- Tra theo điểm đo + khoảng ngày — đường đi của BC-05 và BC-11.
CREATE INDEX ix_hydro_agg_daily_diem_ngay
    ON hydro_agg_daily (station_id, measurement_type_id, agg_date DESC);

-- Tra theo ngày cho toàn hệ — đường đi của BC-13 và của dashboard.
CREATE INDEX ix_hydro_agg_daily_ngay ON hydro_agg_daily (agg_date DESC);

COMMENT ON TABLE hydro_agg_daily IS
    'Tổng hợp NGÀY (giờ VN) của hydro_readings, một hàng cho mỗi mức chất lượng. '
    'Mọi báo cáo/dashboard đọc bảng này — quy tắc 8. ⛔ Không phân mảnh, ⛔ không bị '
    'retention dọn: đây là bản ghi dài hạn còn lại sau khi số đo chi tiết hết hạn 5 năm.';

COMMENT ON COLUMN hydro_agg_daily.agg_date IS
    '⚠ Ngày GIỜ VIỆT NAM (hyd_ngay_vn), ⛔ không phải measured_at::date — cắt theo UTC '
    'đẩy 42/144 khung mỗi ngày sang ngày hôm trước.';

COMMENT ON COLUMN hydro_agg_daily.quality IS
    '⭐ Thành phần KHOÁ. Báo cáo nghiệp vụ lọc = HOP_LE; BC-13 đọc cả ba và khai ngoại lệ có tên.';

COMMENT ON COLUMN hydro_agg_daily.sum_value IS
    '⭐ Để trung bình THÁNG tính theo trọng số: SUM(sum_value)/SUM(reading_count). '
    'Trung bình của các trung bình ngày là sai, và sai trong khoảng min/max nên không nhìn ra được.';

COMMENT ON COLUMN hydro_agg_daily.reading_count IS
    'Cũng chính là SỐ KHUNG 10 phút đã nhận — vế bị trừ của cột "số khung bỏ sót" (BC-13/NFR-03).';


-- =============================================================================
-- 6. ⛔ Vì sao bảng này KHÔNG có trong `hyd_don_du_lieu_qua_han`
--
-- `HydroRetentionHandler` dọn bốn thứ: raw log (90 ngày), sync log, số đo (5
-- năm), mã chưa khai. Bảng agg cố ý ⛔ KHÔNG nằm trong danh sách ấy, và đó là
-- một quyết định, không phải một chỗ quên:
--
--   • Kích thước: 19 điểm đo × 2 chỉ số × 3 mức × 365 ngày ≈ 42 nghìn hàng/năm.
--     Mười năm vẫn nhỏ hơn MỘT THÁNG của `hydro_readings`.
--   • Nguồn ⛔ không có API lịch sử. Khi partition năm thứ sáu bị DROP, hàng
--     agg là thứ **duy nhất** còn nói được mực nước cao nhất tháng ấy là bao
--     nhiêu. Dọn nó là tự nguyện quên.
--
-- ⚠ Hệ quả phải biết trước: sau khi số đo chi tiết bị dọn, một lượt tính lại
--   trên ngày cũ sẽ XOÁ hàng agg (không còn bản ghi nguồn). Cờ bẩn ⛔ không bao
--   giờ được đánh cho những ngày ấy — trigger chỉ bắn khi có ai GHI, mà
--   `DROP PARTITION` ⛔ không bắn trigger. Nên đường đó đóng một cách tự nhiên.
--   ⛔ Đừng thêm một job "tính lại toàn bộ lịch sử": nó sẽ xoá sạch đúng phần
--   dữ liệu mà bảng này sinh ra để giữ.
-- =============================================================================


-- =============================================================================
-- 7. `hydro_agg_dirty` — hàng đợi "kỳ nào cần tính lại"
--
-- ⭐ Vì sao là một BẢNG chứ không phải một cột `dirty` trên `hydro_agg_daily`:
--    một kỳ **chưa từng được tổng hợp** thì chưa có hàng agg nào để cắm cờ lên.
--    Ngày đầu tiên của một điểm đo mới là đúng trường hợp ấy, và một cờ không
--    đặt được cho trường hợp phổ biến nhất là một cờ nói dối.
-- =============================================================================

-- ⛔⛔ CỐ Ý KHÔNG có KHOÁ NGOẠI — và bản đầu của tệp này ĐÃ có, rồi bị đo là sai.
--
-- Lý lẽ ban đầu là "nhất quán với mọi bảng khác của lược đồ". Lượt chạy bộ kiểm
-- đầu tiên bác bỏ nó bằng một con số: **năm lớp kiểm** hỏng ở bước DỌN DẸP với
--     update or delete on table "stations" violates foreign key constraint
--     "hydro_agg_dirty_station_id_fkey"
-- Một hàng của HÀNG ĐỢI — thứ sinh ra để bị xoá trong vài phút — giành được
-- quyền phủ quyết lên một thao tác trên DANH MỤC.
--
-- ⚠ Đó ⛔ không phải "chuyện của bộ kiểm". Cùng hình dạng ấy sẽ cắn ở vận hành:
--    ngày nào cần gỡ cứng một điểm đo khai nhầm, thao tác sẽ hỏng vì một dòng
--    rác trong hàng đợi, và thông báo lỗi chỉ về phía `hydro_agg_dirty` — nơi
--    ⛔ không ai nghĩ tới.
--
-- Và khoá ngoại ở đây ⛔ KHÔNG mua được gì: một hàng mồ côi chỉ làm đúng một
-- việc — lượt tính lại chạy qua tập rỗng, xoá hàng agg (nếu có) rồi tự xoá
-- chính nó. Tự lành, ⛔ không để lại số sai.
--
-- 📌 `hydro_agg_daily` thì GIỮ khoá ngoại: nó là **số liệu**, sống nhiều năm và
--    phải trỏ tới một điểm đo có thật. Sự khác nhau ấy là chủ ý, ⛔ không phải
--    một chỗ quên: hàng đợi và số liệu chịu hai luật khác nhau.
CREATE TABLE hydro_agg_dirty (
    station_id          BIGINT      NOT NULL,
    measurement_type_id BIGINT      NOT NULL,
    agg_date            date        NOT NULL,
    marked_at           timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (station_id, measurement_type_id, agg_date)
);

-- Lấy việc theo thứ tự cũ trước — một ngày bị bỏ quên không được xếp sau mãi.
CREATE INDEX ix_hydro_agg_dirty_moc ON hydro_agg_dirty (marked_at);

COMMENT ON TABLE hydro_agg_dirty IS
    'Hàng đợi kỳ cần tính lại. Đánh bằng TRIGGER trên hydro_readings — ⛔ không bằng lời gọi '
    'ở tầng ứng dụng, vì đường ghi thứ tư luôn ra đời sau bản vá cho ba đường đầu (T27.7).';


-- =============================================================================
-- 8. ⭐⭐ TRIGGER — bảo đảm đặt ở CHỖ DỮ LIỆU ĐI QUA (luật 12)
--
-- Hôm nay `hydro_readings` có BA đường ghi: `TelemetryIngestService` (poller),
-- `SoDoNhapTayService` (nhập tay), `HydroReviewService` (duyệt/xoá — UPDATE cột
-- `quality`). WS-33 vừa phải dựng cả một bài ArchUnit (`AlertHookCoverageTest`)
-- chỉ để đếm đủ ba đường ấy, vì máy cảnh báo được gọi từ tầng ứng dụng.
--
-- ⚠ Bài học đã trả giá đúng ở chỗ này: T27.7 vá đệm cổng ở BA điểm ghi, và
--   **điểm ghi thứ tư ra đời cùng đợt mang lại đúng lỗi cũ**. Luật 12 nói rõ —
--   *đặt bảo đảm ở chỗ dữ liệu đi qua, đừng đặt ở nơi gọi; không đặt được thì
--   phải có phép kiểm đếm đủ các đường vào.*
--
-- Ở đây **đặt được**. Mọi đường ghi, kể cả một đường sinh ra sang năm, kể cả
-- một câu `UPDATE` gõ tay lúc xử lý sự cố, đều đi qua trigger này. Cái giá là
-- một INSERT `ON CONFLICT DO NOTHING` trên mỗi dòng — 28 dòng mỗi 2 phút.
--
-- ⛔ KHÔNG bắt DELETE, và đó là chủ ý:
--    • Xoá nghiệp vụ đi qua `quality = 'XOA'` (một UPDATE — đã bắt).
--    • Xoá vật lý chỉ đến từ `DROP PARTITION` của retention, mà DDL ⛔ không bắn
--      trigger. Đánh cờ bẩn cho những ngày ấy chính là thứ §6 vừa cấm.
-- =============================================================================

CREATE OR REPLACE FUNCTION hyd_danh_dau_ky_can_tinh_lai()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO hydro_agg_dirty (station_id, measurement_type_id, agg_date)
    VALUES (NEW.station_id, NEW.measurement_type_id, hyd_ngay_vn(NEW.measured_at))
    ON CONFLICT DO NOTHING;

    -- ⚠ Một UPDATE có thể DỜI bản ghi sang kỳ khác (sửa `measured_at`, hoặc gán
    --   lại điểm đo). Kỳ CŨ khi ấy còn một hàng agg tính trên một tập đã đổi —
    --   im lặng sai. Nên đánh cả hai đầu, và `IS DISTINCT FROM` để lượt duyệt
    --   thông thường (chỉ đổi `quality`) ⛔ không tốn một hàng thừa.
    IF TG_OP = 'UPDATE'
        AND (OLD.station_id, OLD.measurement_type_id, hyd_ngay_vn(OLD.measured_at))
            IS DISTINCT FROM
            (NEW.station_id, NEW.measurement_type_id, hyd_ngay_vn(NEW.measured_at))
    THEN
        INSERT INTO hydro_agg_dirty (station_id, measurement_type_id, agg_date)
        VALUES (OLD.station_id, OLD.measurement_type_id, hyd_ngay_vn(OLD.measured_at))
        ON CONFLICT DO NOTHING;
    END IF;

    RETURN NULL;
END $$;

COMMENT ON FUNCTION hyd_danh_dau_ky_can_tinh_lai() IS
    'AFTER INSERT/UPDATE trên hydro_readings → đánh kỳ vào hydro_agg_dirty. ⛔ Không có '
    'đường ghi nào lách được — đó là toàn bộ lý do nó là trigger chứ không phải lời gọi.';

-- ⚠ Trigger FOR EACH ROW khai trên BẢNG CHA của một cây phân mảnh được
--   PostgreSQL nhân bản xuống mọi partition, kể cả partition tạo SAU (PG ≥ 13).
--   Nhờ vậy `hyd_tao_partition_thuy_van` ⛔ không phải nhớ gắn lại — nếu phải
--   nhớ thì đó lại là một chỗ để quên (luật 14).
CREATE TRIGGER trg_hydro_readings_danh_dau_agg
    AFTER INSERT OR UPDATE ON hydro_readings
    FOR EACH ROW
    EXECUTE FUNCTION hyd_danh_dau_ky_can_tinh_lai();


-- =============================================================================
-- 9. Nạp cờ bẩn cho số đo ĐÃ CÓ
--
-- ⛔ Đây ⛔ KHÔNG phải seed dữ liệu: nó ⛔ không tạo ra một con số nào. Nó chỉ
--    xếp hàng những kỳ đã có số đo thật để lượt chạy đầu tiên của
--    `HYDRO_AGG_REBUILD` tính chúng từ chính `hydro_readings`.
--
-- ⚠⚠ Câu này đọc `hydro_readings` KHÔNG lọc `quality` — cố ý, và đã khai ngoại
--    lệ CÓ TÊN ở `QualityFilterGuardTest.NGOAI_LE`. Bảng agg có một hàng cho
--    mỗi mức chất lượng, nên một ngày chỉ toàn bản ghi nghi ngờ **vẫn phải**
--    được tổng hợp — lọc HOP_LE ở đây làm BC-13 mù trước đúng những ngày tồi tệ
--    nhất, tức là mù đúng lúc nó cần nhìn.
-- =============================================================================

INSERT INTO hydro_agg_dirty (station_id, measurement_type_id, agg_date)
SELECT DISTINCT station_id, measurement_type_id, hyd_ngay_vn(measured_at)
  FROM hydro_readings
ON CONFLICT DO NOTHING;
