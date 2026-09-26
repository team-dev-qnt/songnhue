-- =============================================================================
-- WS-87 · T87.1 · T87.2 — Nguồn thuỷ văn tách làm HAI, và đường mực nước ĐANG CHẾT
--
-- Công ty cấp hai URL thay cho một (26/09/2026). Đo trên nguồn THẬT cùng ngày:
--
--   api/getmucnuoc.aspx  ⇒ 200 · 28 bản ghi · value NGUYÊN (cm)
--   api/getluongmua.aspx ⇒ 200 · 15 bản ghi · value THẬP PHÂN (mm)
--   api/getmn.aspx       ⇒ 200 · thân `not.working`   ← đường ta ĐANG gọi
--
-- Quy tắc 18: nguồn ⛔ có API lịch sử (đo lại 26/09 — `&ngay=`, `&date=`,
-- `&tu=&den=` đều bị BỎ QUA, luôn trả snapshot hiện tại) ⇒ mỗi lượt poll hỏng
-- là một khung 10 phút mất VĨNH VIỄN. Khối 1 dưới đây là phần gấp nhất.
--
--
-- ⛔⛔⛔ KHỐI 1 — VÌ SAO PHẢI CẮT `base_url`, VÀ VÌ SAO CHÍNH LƯỢT VÁ NÀY LÀ THỨ
--        SẼ PHÁ NẾU ⛔ CẮT
--
-- `DiaChiNguon.chuanHoaGoc` cắt phần TRÙNG theo ĐOẠN giữa đuôi `base_url` và
-- đầu đường dẫn endpoint (T52.2). Hôm nay hằng số là `api/getmn.aspx`, nên một
-- hàng có `base_url = http://host/api/getmn.aspx` vẫn chạy ĐÚNG: k=2, cắt hết,
-- URL ra `/api/getmn.aspx`. Tức lược đồ hiện tại đang **ĐƯỢC CỨU** bởi phép cắt.
--
-- Lượt đổi hằng số sang `api/getmucnuoc.aspx` **phá đúng lượt cứu ấy**:
-- `/api/getmn.aspx` ⛔ còn là tiền tố của đường dẫn mới ⇒ k=0 ⇒ URL nối thành
--
--     http://host/api/getmn.aspx/api/getmucnuoc.aspx     ⇒ 404
--
-- — đúng hình dạng T52.0/T52.1 đã trả giá: `consecutive_failures = 3576`,
-- `last_success_at = NULL`, `hydro_readings = 0`. ⇒ Phải **CẮT đường dẫn về
-- gốc**, ⛔ phải "thay bằng đường mới": gốc là thứ duy nhất `DiaChiNguon` cần,
-- và cắt thì đúng cho CẢ hàng đã sửa tay lẫn hàng seed.
--
-- ⚠ Điều kiện HẸP theo đúng luật `V202608131009` (*"migration sau CẤM ghi đè giá
--   trị Admin đã sửa"*): khoá theo **"còn trỏ đường ĐÃ CHẾT"**, ⛔ theo một giá
--   trị thay thế. Một máy chủ đặt nguồn ở thư mục con (`/songnhue/api/...`), một
--   hàng trỏ host khác, một hàng thử nghiệm trên `127.0.0.1` — ⛔ hàng nào bị
--   đụng vì ⛔ hàng nào khớp mẫu.
-- ⛔ ⛔ Thêm `AND adapter_type = 'BHH40'`: hẹp nhầm TRỤC. Vị từ đúng là *đường
--    dẫn đã chết*, ⛔ phải *adapter nào* — một hàng đã được trỏ lại adapter khác
--    mà còn giữ đuôi chết vẫn phải được cứu.
-- =============================================================================

UPDATE api_sources
   SET base_url = regexp_replace(base_url, '/api/getmn\.aspx/?$', ''),
       -- Bộ đếm hỏng là bản ghi của MỘT sự cố đã qua. Để nguyên thì chuông
       -- `HYDRO_SOURCE_RECOVERED` (ApiSourceHealthService) ⛔ có mốc để kêu, và
       -- màn hình Nguồn dữ liệu vẫn đỏ sau khi nguồn đã sống lại.
       -- ⛔ Đụng `last_success_at`: nó là sự thật lịch sử, ⛔ phải trạng thái.
       consecutive_failures = 0,
       last_failure_reason = 'Đường dẫn cũ getmn.aspx đã ngừng phục vụ — cắt về gốc ở V202609261099 (WS-87)'
 WHERE base_url ~ '/api/getmn\.aspx/?$';


-- =============================================================================
-- KHỐI 2 — Adapter thứ hai được phép tồn tại
--
-- `EnumBaNoiTest` đọc định nghĩa CHECK ở migration có SỐ HIỆU LỚN NHẤT, nên
-- dựng lại ràng buộc ở đây là đường được đỡ. Bộ ba `AdapterType` (Java) ↔ CHECK
-- (SQL) ↔ union TypeScript phải đi CÙNG MỘT PR, ⛔ thì CI đỏ — và đó là cổng
-- đúng: ba nơi khai cùng một bảng từ vựng thì phải có người bắt chúng lệch nhau.
-- =============================================================================

ALTER TABLE api_sources DROP CONSTRAINT ck_api_sources_adapter;
ALTER TABLE api_sources ADD CONSTRAINT ck_api_sources_adapter
    CHECK (adapter_type IN ('BHH40', 'BHH40_MUA', 'MOCK'));


-- =============================================================================
-- KHỐI 3 — Hàng nguồn lượng mưa
--
-- ⛔⛔ `cron` KHAI TƯỜNG MINH, ⛔ để NULL — và đây ⛔ phải khẩu vị:
--
-- `TelemetryIngestService.daDuDuLieu` trả `false` NGAY khi `dangHoatDong() == 0`,
-- còn `PollerRepository.SQL_DEM_HOAT_DONG` đếm trạm theo `api_source_id`. Nguồn
-- này có **0 trạm khai** (15 mã của nó ⛔ mã nào nằm trong danh mục 19 điểm đo —
-- đo 26/09) ⇒ **hạn mức theo khung ⛔ BAO GIỜ chặn được lượt gọi nào**. Để NULL
-- là thừa hưởng cron chung `45 1/2 * * * *` ⇒ **720 lượt/ngày** lấy một thứ đổi
-- **24 lần/ngày**, đúng thứ Công ty yêu cầu tránh (*"hạn chế call API mà response
-- ⛔ đổi"*), và 720 dòng `sync_logs`/ngày làm loãng đúng màn hình chẩn đoán mà
-- người vận hành nhìn.
--
-- ⚠ Nhịp 10 phút, ⛔ phải 1 giờ: đo 26/09 lúc **10:41** thì mốc nguồn vẫn là
--   **10:00** ⇒ ⛔ biết nguồn phát hành lúc nào trong giờ. Một lượt gọi/giờ có
--   thể trượt hẳn một khung, mà quy tắc 18 nói trượt là mất vĩnh viễn ⇒ đứng về
--   phía gọi thừa. 10 phút = 144 lượt/ngày và **6 cơ hội mỗi khung**.
-- ⚠ Giây 45 + phút lẻ: cùng lý do đã chốt cho nguồn mực nước (G3) — gọi vào giây
--   0 là gọi TRƯỚC khi dữ liệu khung mới kịp lên.
--
-- ⚠ `frame_minutes = 60` khai đúng nhịp ĐO ĐƯỢC của nguồn này. Nó chỉ nuôi
--   `dauKhung`/hạn mức của CHÍNH nguồn này; ngưỡng mất tín hiệu đọc khung CHUNG
--   ở `settings`, nên ⛔ có chuyện một nguồn kéo theo nguồn kia (V202609201089).
--
-- ⛔ `credential` để NULL: mã số là bí mật, ⛔ bao giờ nằm trong migration
--    (quy tắc 13). `ApiSourceCredentialBootstrap` mồi từ `HYDRO_API_KEY` —
--    đo 26/09: **cùng một mã số chạy cho CẢ HAI endpoint**.
-- =============================================================================

INSERT INTO api_sources (code, name, adapter_type, base_url, cron, frame_minutes, description)
VALUES (
    'BHH40_MUA',
    'Telemetry lượng mưa Sông Nhuệ (bhh40.net)',
    'BHH40_MUA',
    'http://songnhue.bhh40.net',
    '45 5/10 * * * *',
    60,
    'Nguồn lượng mưa 15 mã (đo 26/09/2026). ⚠ Không có API lịch sử (quy tắc 18). '
    '⚠ 15 mã CHƯA khai thành điểm đo ⇒ số đo nằm ở hydro_unmapped_readings, '
    'nguyên văn + đơn vị nguồn (mm). Khai mã là việc của Công ty (G8).'
)
ON CONFLICT DO NOTHING;


-- =============================================================================
-- KHỐI 4 — Mô tả của nguồn mực nước thôi khai một câu đã SAI
--
-- Câu *"Không có API lượng mưa (G3-a)"* đúng vào ngày viết và hết đúng hôm nay.
-- Nó là dữ liệu người vận hành ĐỌC trên màn hình Nguồn dữ liệu.
-- ⚠ Hẹp: chỉ ghi đè khi mô tả CÒN NGUYÊN chuỗi seed — Admin đã sửa tay thì giữ.
-- =============================================================================

UPDATE api_sources
   SET description = 'Nguồn mực nước 19 điểm đo (nguồn trả 28 mã). ⚠ Không có API lịch sử — '
                     'mất dữ liệu là mất vĩnh viễn (quy tắc 18). '
                     '⚠ Endpoint đổi getmn.aspx → getmucnuoc.aspx ngày 26/09/2026 (WS-87).'
 WHERE code = 'BHH40'
   AND description LIKE '%Không có API lượng mưa%';


-- =============================================================================
-- KHỐI 5 — Tự kiểm ngay trong lượt áp
--
-- §10.66: một lượt seed chạm 0 hàng mà im lặng là một lượt seed ⛔ ai biết là
-- hỏng. Khối này in số hàng đã chạm và NÉM khi một bất biến vỡ.
--
-- ⛔ ⛔ Khẳng định `hydro_readings` có dòng mới: migration chạy TRƯỚC khi poller
--    kịp gọi lần nào. Bất biến đo được ở đây chỉ là bất biến của LƯỢC ĐỒ.
-- =============================================================================

DO $$
DECLARE
    con_duong_chet INTEGER;
    so_nguon_mua   INTEGER;
    dinh_nghia     TEXT;
BEGIN
    SELECT count(*) INTO con_duong_chet
      FROM api_sources WHERE base_url ~ '/api/getmn\.aspx/?$';
    IF con_duong_chet > 0 THEN
        RAISE EXCEPTION 'Còn % hàng api_sources trỏ đường đã chết getmn.aspx — phép cắt ở KHỐI 1 đã trượt', con_duong_chet;
    END IF;

    SELECT count(*) INTO so_nguon_mua
      FROM api_sources WHERE adapter_type = 'BHH40_MUA' AND deleted_at IS NULL;
    IF so_nguon_mua <> 1 THEN
        RAISE EXCEPTION 'Phải có ĐÚNG 1 nguồn BHH40_MUA, đang có %', so_nguon_mua;
    END IF;

    -- Nguồn mưa mà thừa hưởng cron chung thì gọi 720 lượt/ngày (xem KHỐI 3).
    PERFORM 1 FROM api_sources
      WHERE code = 'BHH40_MUA' AND (cron IS NULL OR frame_minutes IS NULL);
    IF FOUND THEN
        RAISE EXCEPTION 'Nguồn BHH40_MUA phải khai cron và frame_minutes TƯỜNG MINH — NULL là thừa hưởng nhịp của nguồn mực nước';
    END IF;

    SELECT pg_get_constraintdef(oid) INTO dinh_nghia
      FROM pg_constraint WHERE conname = 'ck_api_sources_adapter';
    IF dinh_nghia NOT LIKE '%BHH40_MUA%' THEN
        RAISE EXCEPTION 'ck_api_sources_adapter chưa nhận BHH40_MUA: %', dinh_nghia;
    END IF;

    RAISE NOTICE 'WS-87: đã cắt đường chết ở % hàng, dựng 1 nguồn lượng mưa.',
        (SELECT count(*) FROM api_sources WHERE last_failure_reason LIKE '%V202609261099%');
END $$;
