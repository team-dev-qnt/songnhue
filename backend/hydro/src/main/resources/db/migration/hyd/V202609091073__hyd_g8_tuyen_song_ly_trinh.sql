-- =============================================================================
-- G8 (một phần) — tuyến sông + lý trình của 19 điểm đo, và sửa vai trò F01519
--
-- NGUỒN: bảng đối chiếu Công ty, QuanTran cấp ngày 09/09/2026. Bản chụp ghi
-- nguyên văn 5 cột `Mã API · Tên điểm đo/Công trình · Vai trò TL-HL · Tuyến
-- sông · Lý trình` cho đủ 19 mã. Chép lại ở `business-open-questions.md` §G8
-- để đối chiếu được mà không cần mở ảnh.
--
-- =============================================================================
-- ⭐ G8 CHƯA ĐÓNG. File này đóng ĐÚNG HAI cột trong bốn.
-- =============================================================================
--
--   ✅ `river_name` — 13/19 có giá trị, 6/19 bản chụp ghi "Chưa rõ"  → NULL
--   ✅ `chainage`   — 10/19 có giá trị,  9/19 "Chưa rõ" hoặc không dùng được → NULL
--   ⛔ `latitude` / `longitude` — bản chụp **KHÔNG có cột toạ độ**. Cả 19 vẫn
--      NULL, nên `geom` vẫn NULL, nên lớp GIS điểm đo vẫn RỖNG. Đây là phần
--      G8 còn thiếu và nó vẫn chặn nghiệm thu C3 y như trước file này.
--   ⛔ `constructions` — bản chụp là bảng ĐIỂM ĐO, không phải danh mục công
--      trình. Cột "Tên điểm đo/Công trình" là nhãn, không phải hồ sơ công trình.
--      `station_constructions` vì vậy vẫn 0 dòng.
--
-- =============================================================================
-- ⚠ F01519 — hai nguồn của Công ty lệch nhau đúng một dòng
-- =============================================================================
--
-- `V202608311049` seed F01519 = `DO-LCO-TL` / `THUONG_LUU`, chú thích ghi bảng
-- ấy là "bảng ánh xạ Công ty cấp (chốt G8b)". Bản chụp 09/09 ghi **Hạ lưu**.
-- 18/19 dòng còn lại khớp tuyệt đối giữa hai nguồn, nên đây không phải hai bảng
-- khác nhau mà là một dòng sai ở một trong hai bản.
--
-- QuanTran chốt 09/09: **lấy theo bản chụp** — nó mới hơn, mang thêm tuyến sông
-- và lý trình, và nội bộ nhất quán (Lương Cổ chỉ xuất hiện MỘT lần, ở K72+506 —
-- lý trình lớn nhất trên sông Nhuệ trong toàn bảng, tức cuối tuyến).
--
-- ⛔ Đổi `position_role` là đổi biểu tổng hợp: một điểm `THUONG_LUU` lên cột
--    "Mực nước thượng lưu", `HA_LUU` lên cột khác. Sai cột thì số vẫn hợp lý và
--    không ai đọc ra — đúng loại hỏng câm mà `HydroCatalogueSeedTest` sinh ra
--    để bắt. Bài kiểm ấy được sửa CÙNG file này, không phải sau.
--
-- ⚠ `code` đổi theo (`DO-LCO-TL` → `DO-LCO-HL`) vì hậu tố mã mang nghĩa vai
--    trò ở cả 19 dòng. Giữ mã cũ là để lại một cái bẫy đọc: mã nói TL, cột nói
--    HL. An toàn vì `stations` chưa có một số đo nào và chưa hệ nào tham chiếu
--    mã này ngoài seed (đã đo: 1 lần xuất hiện trong migration, 1 trong bài kiểm).
--
-- =============================================================================
-- ⚠ F01657 — lý trình bản chụp ghi "(K72+000 – sông Đáy)", CỐ Ý để NULL
-- =============================================================================
--
-- F01657 là Vân Đình **thượng lưu**, tuyến sông bản chụp ghi "Sông Vân Đình".
-- Nhưng lý trình lại chú "– sông Đáy", và F01705 (Vân Đình hạ lưu, tuyến "Sông
-- Đáy") mang đúng lý trình K72+000. ⇒ K72+000 gần như chắc chắn là lý trình
-- **trên sông Đáy**, không phải trên sông Vân Đình.
--
-- Ghi nó vào `chainage` của một dòng có `river_name = 'Sông Vân Đình'` là làm
-- hỏng `ix_stations_river (river_name, chainage_m)`: sông Vân Đình dài chừng
-- 10 km, một điểm ở 72.000 m trên tuyến ấy sẽ đứng sai chỗ trong mọi phép sắp
-- theo tuyến — và biểu BC-11 nhóm theo đúng cặp cột đó.
--
-- ⇒ `chainage` NULL, nguyên văn bản chụp giữ ở `description` để Công ty xác
--    nhận. Quy tắc 16: ô chưa rõ nói là chưa rõ, ⛔ không đoán cho đầy.
-- =============================================================================


-- --- 1. F01519: Thượng lưu → Hạ lưu -----------------------------------------
UPDATE stations
   SET code          = 'DO-LCO-HL',
       name          = 'Lương Cổ — Hạ lưu',
       position_role = 'HA_LUU',
       updated_at    = now()
 WHERE api_code = 'F01519'
   AND deleted_at IS NULL;


-- --- 2. Tuyến sông + lý trình ------------------------------------------------
-- ⛔ Chỉ liệt kê những mã bản chụp có giá trị. Mã không có trong VALUES giữ
--    NULL — ⛔ không có câu `SET river_name = ''` nào, vì chuỗi rỗng và NULL là
--    hai trạng thái khác nhau và chỉ một trong hai đọc được là "chưa có".
UPDATE stations s
   SET river_name = v.river_name,
       chainage   = v.chainage,
       updated_at = now()
  FROM (VALUES
    -- Mã API   tuyến sông          lý trình
    -- ⚠ Ép kiểu ở dòng ĐẦU: hằng trong VALUES mang kiểu `unknown`, và một cột
    --   toàn `unknown` + `NULL` thì PostgreSQL suy ra `text` — đúng ở đây,
    --   nhưng đó là suy diễn, ⛔ không phải khai báo. Ép một lần cho chắc.
    ('F01771'::varchar, 'Sông Nhuệ'::varchar, 'K0+390'::varchar), -- Cống Liên Mạc — TL
    ('F01672', 'Sông Nhuệ',       NULL),      -- Cống Liên Mạc — HL, lý trình "Chưa rõ"
    ('F01794', 'Sông Nhuệ',      'K18+100'),  -- Hà Đông — TL
    ('F01905', 'Sông Nhuệ',      'K43+750'),  -- Đồng Quan — TL
    ('F01527', 'Sông Nhuệ',      'K43+750'),  -- Đồng Quan — HL (cùng lý trình, hai bờ cống)
    ('F02031', 'Sông Nhuệ',      'K63+405'),  -- Nhật Tựu — TL
    ('F02030', 'Sông Nhuệ',      'K63+405'),  -- Nhật Tựu — HL
    ('F01519', 'Sông Nhuệ',      'K72+506'),  -- Lương Cổ — HL, cuối tuyến Nhuệ
    ('F01657', 'Sông Vân Đình',   NULL),      -- Vân Đình — TL, xem khối ⚠ F01657
    ('F02039', 'Sông Vân Đình',  'K1+460'),   -- Hòa Mỹ — HL
    ('F01705', 'Sông Đáy',       'K72+000'),  -- Vân Đình — HL
    ('F01532', 'Sông Đáy',       'K46+500'),  -- TV Ba Thá — MN sông
    ('F01707', 'Sông La Khê',     NULL)       -- TB Yên Nghĩa — Bể hút, lý trình "Chưa rõ"
  ) AS v(api_code, river_name, chainage)
 WHERE s.api_code = v.api_code
   AND s.deleted_at IS NULL;


-- --- 3. Nguyên văn ô lý trình của F01657 -------------------------------------
UPDATE stations
   SET description = 'Bản chụp G8 (09/09/2026) ghi lý trình "(K72+000 – sông Đáy)". '
                     || 'Lý trình để NULL vì K72+000 thuộc tuyến sông Đáy, không phải sông Vân Đình '
                     || '(F01705 Vân Đình hạ lưu mang đúng K72+000 trên sông Đáy). Chờ Công ty xác nhận.',
       updated_at  = now()
 WHERE api_code = 'F01657'
   AND deleted_at IS NULL;


-- =============================================================================
-- 4. KHẲNG ĐỊNH — §10.66
--
-- Năm khẳng định, cố ý ⛔ KHÔNG chia sẻ giả định với nhau (§10.62):
--   • đếm số dòng CÓ tuyến sông        — bắt trường hợp UPDATE chạm 0 hàng
--   • đếm số dòng CÓ lý trình          — bắt trường hợp CHECK từ chối im lặng
--   • đếm toạ độ                       — bắt trường hợp ai đó nhân tiện điền toạ độ
--   • soi F01519 theo TÊN và VAI TRÒ   — bắt trường hợp đúng số lượng, sai dòng
--   • soi `chainage_m` của một mã cụ thể — bắt trường hợp cột sinh không tính
--
-- ⚠ `chainage_m` là cột GENERATED. Nó là thứ `ix_stations_river` sắp theo, nên
--   một lỗi ở biểu thức sinh sẽ không hiện ra ở `chainage` mà chỉ hiện ra ở
--   THỨ TỰ của biểu BC-11 — quá xa chỗ sai để ai đó nối lại được.
-- =============================================================================
DO $$
DECLARE
    so_tuyen     INTEGER;
    so_ly_trinh  INTEGER;
    so_toa_do    INTEGER;
    ten_lco      TEXT;
    vai_tro_lco  TEXT;
    ma_lco       TEXT;
    m_f01519     INTEGER;
BEGIN
    SELECT count(*) INTO so_tuyen
      FROM stations WHERE deleted_at IS NULL AND river_name IS NOT NULL;
    IF so_tuyen <> 13 THEN
        RAISE EXCEPTION
            'V202609091073: bản chụp G8 có 13 điểm đo biết tuyến sông, đang có %', so_tuyen;
    END IF;

    SELECT count(*) INTO so_ly_trinh
      FROM stations WHERE deleted_at IS NULL AND chainage IS NOT NULL;
    IF so_ly_trinh <> 10 THEN
        RAISE EXCEPTION
            'V202609091073: bản chụp G8 có 10 điểm đo dùng được lý trình, đang có %', so_ly_trinh;
    END IF;

    SELECT count(*) INTO so_toa_do
      FROM stations WHERE deleted_at IS NULL AND (latitude IS NOT NULL OR longitude IS NOT NULL);
    IF so_toa_do <> 0 THEN
        RAISE EXCEPTION
            'V202609091073: bản chụp G8 KHÔNG có cột toạ độ — % dòng đang mang toạ độ từ đâu đó khác',
            so_toa_do;
    END IF;

    SELECT code, name, position_role INTO ma_lco, ten_lco, vai_tro_lco
      FROM stations WHERE api_code = 'F01519' AND deleted_at IS NULL;
    IF vai_tro_lco IS DISTINCT FROM 'HA_LUU'
       OR ma_lco IS DISTINCT FROM 'DO-LCO-HL'
       OR ten_lco NOT LIKE '%Hạ lưu' THEN
        RAISE EXCEPTION
            'V202609091073: F01519 phải là Lương Cổ HẠ LƯU (bản chụp 09/09), đang là % / "%" / %',
            ma_lco, ten_lco, vai_tro_lco;
    END IF;

    SELECT chainage_m INTO m_f01519
      FROM stations WHERE api_code = 'F01519' AND deleted_at IS NULL;
    IF m_f01519 IS DISTINCT FROM 72506 THEN
        RAISE EXCEPTION
            'V202609091073: chainage_m của F01519 phải là 72506 (K72+506), đang là %', m_f01519;
    END IF;
END
$$;
