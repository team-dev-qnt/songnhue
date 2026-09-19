-- =============================================================================
-- F01519 Lương Cổ — đổi về THƯỢNG LƯU (đảo quyết định 09/09 của V202609091073)
--
-- QuanTran chốt 18/09/2026. Ba nguồn của Công ty, hai nói Thượng lưu:
--
--   | Nguồn                                           | Vai trò F01519 |
--   |-------------------------------------------------|----------------|
--   | bảng ánh xạ G8b → V202608311049                 | THUONG_LUU     |
--   | bản chụp 09/09 → V202609091073                  | HA_LUU         |
--   | mẫu Báo cáo nhanh (18/09) — Bảng 3              | THUONG_LUU     |
--
-- Mẫu Báo cáo nhanh ghi dòng `TL (nhuệ)` CÓ số (140) và dòng `HL (đáy)` TRỐNG.
-- Thêm một bằng chứng NỘI TẠI: V1073 đặt `river_name = 'Sông Nhuệ'` trên một
-- dòng khai HA_LUU, trong khi hạ lưu cống Lương Cổ đổ ra sông ĐÁY. Hai lời khai
-- trong cùng một hàng ⛔ nhất quán; sửa vai trò thì `river_name` + K72+506 lại
-- đúng ⇒ GIỮ NGUYÊN hai cột ấy.
--
-- ⛔ Sửa BA chỗ cùng lượt — sửa một mình `stations` là để lại hai cột nói hai điều:
--   1. `stations` — code / name / position_role.
--   2. `station_constructions.role` — V202609091075 chép nguyên `position_role`
--      sang đây, và bản ghi chính phải có `role` TRÙNG `position_role` (A2b).
--   3. `HydroCatalogueSeedTest` — sửa CÙNG commit.
--
-- ⛔ V1073 là migration ĐÃ PHÁT HÀNH (bất biến, kể cả chú thích — §10.65). Khối
--    `DO $$` của nó khẳng định HA_LUU tại THỜI ĐIỂM nó chạy — vẫn đúng trên CSDL
--    rỗng vì 1073 chạy trước tệp này. Tệp này mang khối kiểm của riêng nó.
--
-- ⚠ Hệ quả thấy được: lưới mực nước trên cổng công khai chuyển số Lương Cổ sang
--   cột *Thượng lưu*. `hydro_readings` ⛔ đổi gì — số đo gắn `station_id`, ⛔ gắn vai trò.
-- =============================================================================

UPDATE stations
   SET code          = 'DO-LCO-TL',
       name          = 'Lương Cổ — Thượng lưu',
       position_role = 'THUONG_LUU',
       updated_at    = now()
 WHERE api_code = 'F01519'
   AND deleted_at IS NULL;

UPDATE station_constructions sc
   SET role       = 'THUONG_LUU',
       updated_at = now()
  FROM stations s
 WHERE s.id = sc.station_id
   AND s.api_code = 'F01519'
   AND sc.is_primary
   AND sc.deleted_at IS NULL;

-- =============================================================================
-- KHỐI KIỂM — một UPDATE chạm 0 hàng vẫn thoát 0 (§10.66).
-- Ba khẳng định ⛔ chia sẻ giả định: vai trò điểm đo · cột giữ nguyên · vai trò liên kết.
-- =============================================================================
DO $$
DECLARE
    ma        TEXT;
    ten       TEXT;
    vai_tro   TEXT;
    tuyen     TEXT;
    m_ly_trinh INTEGER;
    so_lk_lech INTEGER;
    so_lk      INTEGER;
BEGIN
    SELECT code, name, position_role, river_name, chainage_m
      INTO ma, ten, vai_tro, tuyen, m_ly_trinh
      FROM stations WHERE api_code = 'F01519' AND deleted_at IS NULL;
    IF vai_tro IS DISTINCT FROM 'THUONG_LUU'
       OR ma IS DISTINCT FROM 'DO-LCO-TL'
       OR ten NOT LIKE '%Thượng lưu' THEN
        RAISE EXCEPTION
            'V202609181085: F01519 phải là Lương Cổ THƯỢNG LƯU (chốt 18/09), đang là % / "%" / %',
            ma, ten, vai_tro;
    END IF;

    -- Tuyến sông + lý trình vốn ĐÚNG với vế TL — ⛔ được đổi theo.
    IF tuyen IS DISTINCT FROM 'Sông Nhuệ' OR m_ly_trinh IS DISTINCT FROM 72506 THEN
        RAISE EXCEPTION
            'V202609181085: F01519 phải giữ Sông Nhuệ / K72+506, đang là % / %', tuyen, m_ly_trinh;
    END IF;

    SELECT count(*),
           count(*) FILTER (WHERE sc.role <> s.position_role)
      INTO so_lk, so_lk_lech
      FROM station_constructions sc JOIN stations s ON s.id = sc.station_id
     WHERE s.api_code = 'F01519' AND sc.is_primary AND sc.deleted_at IS NULL;
    IF so_lk <> 1 THEN
        RAISE EXCEPTION
            'V202609181085: F01519 phải có đúng 1 liên kết chính tới Cống Lương Cổ (V1075), đang có %',
            so_lk;
    END IF;
    IF so_lk_lech <> 0 THEN
        RAISE EXCEPTION
            'V202609181085: liên kết chính của F01519 lệch vai trò với position_role (A2b)';
    END IF;
END
$$;
