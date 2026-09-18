-- ============================================================================
-- WS-46 / T46.1 — DỰNG DANH MỤC CÔNG TRÌNH TỪ CHÍNH DỮ LIỆU CÔNG TY ĐÃ GỬI
--
-- Trước bản này: `constructions` có **0 hàng** trong toàn chuỗi migration, nên
-- `StationMapService.lopDiemDo()` và mọi màn hình vận hành chạy trên **tập
-- rỗng** (luật 7). Cùng lúc, 19 điểm đo đã mang sẵn `structure_code` /
-- `structure_name` / `river_name` / `chainage` — dữ liệu **của Công ty**, vào
-- kho ở V202609091073 (bản chụp G8) và V202609091074 (khoá gộp công trình).
--
-- ⇒ Việc ở đây KHÔNG phải "seed cho đẹp demo". Nó là **rút một danh mục ra từ
--   dữ liệu đã có nguồn**, và mọi ô CHƯA có nguồn thì để NULL và nói thẳng ra.
--
-- ============================================================================
-- ⛔⛔ BA RANH GIỚI, đọc trước khi sửa tệp này
-- ============================================================================
--
-- 1. ⛔ **TOẠ ĐỘ ĐỂ NULL — 0/11.** Đây là cấm lệnh nặng nhất của dự án
--    (`CLAUDE.md`: *"Cấm seed dữ liệu công trình/thuỷ văn cho đẹp demo — ô nào
--    chưa có nguồn thì nói thẳng là chưa có"*). Bản chụp G8 của Công ty **không
--    có cột toạ độ**; bịa ra một điểm trên bản đồ là tệ hơn hẳn một bản đồ
--    trống, vì bản đồ trống còn nằm trong danh sách nhắc việc. Khối kiểm ở cuối
--    tệp **ném** nếu có dù chỉ một hàng mang toạ độ.
--
-- 2. ⛔ **CHỈ 11/14 nhóm thành công trình.** Ba nhóm bị loại **có tên**:
--       · `TV-BATHA` (Trạm thuỷ văn Ba Thá) · `TV-HNOI` (Trạm thuỷ văn Hà Nội)
--         — **trạm quan trắc tham chiếu**, ⛔ không phải công trình Công ty vận
--         hành. `ConstructionStatusPort` đã ghi sẵn: *"MN_SONG (4/19 trạm)
--         ⛔ không thuộc công trình nào theo thiết kế"*.
--       · `ANCANH` (An Cảnh) — ⛔ không có tiền tố loại hình nào. Đoán nó là
--         cống hay trạm bơm là bịa một thuộc tính hồ sơ.
--    ⚠ Nhận diện loại đi theo **tiền tố tên do Công ty đặt** (`Cống …` →
--      `CONG`, `Trạm bơm …` → `TRAM_BOM`), ⛔ không đoán theo mã.
--
-- 3. ⛔ **`org_unit_id` = CTY, `management_level` = 'CONG_TY'.** Bảng
--    `org_units` hôm nay có **đúng 1 hàng**; 7-hay-8 Xí nghiệp vẫn là **OI-05**
--    chưa chốt (Bố cục ghi 7, danh mục công trình có 8). Gán bừa một Xí nghiệp
--    là dựng sẵn một lỗi **phạm vi dữ liệu** — tầng 3 phân quyền đọc đúng cột
--    này, nên một giá trị sai ở đây **giấu công trình khỏi đúng người phải
--    thấy nó**, im lặng.
--
-- ============================================================================
-- ⭐ TUYẾN SÔNG / LÝ TRÌNH — suy ra khi KHÔNG mâu thuẫn, ⛔ không chọn bừa
-- ============================================================================
--
-- Một công trình có nhiều điểm đo, và chúng ⛔ không luôn khai cùng tuyến sông.
-- Đo được hôm nay trên CSDL thật:
--
--     Cống Vân Đình  ·  thượng lưu = "Sông Vân Đình"  ·  hạ lưu = "Sông Đáy"
--
-- ⚠⚠ Đó **⛔ KHÔNG phải dữ liệu lỗi** — nó đúng vật lý: một cống nằm **giữa**
--    hai tuyến, lấy nước từ tuyến này và tiêu ra tuyến kia. Ép nó về một giá
--    trị (lấy vế thượng lưu, hay lấy `min()`) là **bịa ra một sự thật** rồi in
--    nó lên hồ sơ công trình.
--
-- ⇒ Luật: mỗi cột suy ra **⇔ tập giá trị KHÁC NULL của nhóm có đúng MỘT phần
--   tử**. Mâu thuẫn ⇒ để NULL, admin điền. Hai cột xét **độc lập** — Vân Đình
--   vì thế được `chainage = 'K72+000'` (một giá trị) nhưng `river_name = NULL`
--   (hai giá trị).
--
-- Số đo mong đợi, đếm bằng tay từ 19 hàng `stations` trước khi viết câu SQL:
--     11 công trình · 7 có tuyến sông · 7 có lý trình · 0 có toạ độ
--     15 liên kết điểm đo–công trình (19 − 3 của nhóm bị loại − 1 MN_SONG)
-- ============================================================================

INSERT INTO constructions (
    code, name, construction_type, org_unit_id, management_level,
    river_name, chainage, lifecycle_state, description, created_at
)
SELECT
    s.structure_code,
    s.structure_name,
    CASE
        WHEN s.structure_name LIKE 'Cống %'     THEN 'CONG'
        WHEN s.structure_name LIKE 'Trạm bơm %' THEN 'TRAM_BOM'
    END,
    (SELECT id FROM org_units WHERE code = 'CTY'),
    'CONG_TY',
    -- ⛔ `count(DISTINCT …) = 1` chứ ⛔ không `max()`/`min()`: xem khối "TUYẾN
    --    SÔNG / LÝ TRÌNH" ở đầu tệp. `max()` sẽ lặng lẽ chọn "Sông Đáy" cho
    --    Cống Vân Đình và ⛔ không ai biết đó là một lựa chọn.
    CASE WHEN count(DISTINCT s.river_name) = 1 THEN max(s.river_name) END,
    CASE WHEN count(DISTINCT s.chainage)   = 1 THEN max(s.chainage)   END,
    'DANG_HOAT_DONG',
    'Hồ sơ rút từ danh mục điểm đo do Công ty cung cấp (bản chụp G8, '
        || 'V202609091073). ⛔ CHƯA CÓ: toạ độ · Xí nghiệp quản lý (OI-05) · '
        || 'năm xây dựng · đơn vị thiết kế/thi công · tổng mức đầu tư. '
        || 'Nhập toạ độ ở màn hình Công trình để công trình hiện lên bản đồ.',
    now()
  FROM stations s
 WHERE s.deleted_at IS NULL
   AND s.structure_code IS NOT NULL
   AND (s.structure_name LIKE 'Cống %' OR s.structure_name LIKE 'Trạm bơm %')
 GROUP BY s.structure_code, s.structure_name;

-- ---------------------------------------------------------------------------
-- Liên kết điểm đo ↔ công trình. `role` chép nguyên `position_role` — hai cột
-- ấy có CÙNG danh sách CHECK, cố ý, để phép chép này ⛔ không cần ánh xạ nào.
--
-- ⚠ `is_primary = TRUE` cho **mọi** hàng, và điều đó an toàn vì mỗi điểm đo
--   thuộc **đúng một** công trình ở đây (`ux_…_mot_ban_ghi_chinh` là UNIQUE
--   trên `station_id` WHERE `is_primary`). Ngày một điểm đo phục vụ hai công
--   trình, hàng thứ hai phải là `FALSE` — chỉ số ấy sẽ ném, ⛔ không im lặng.
--
-- ⛔⛔ `position_role <> 'MN_SONG'` — ĐỌC TRƯỚC KHI GỠ.
--
--   `ConstructionStatusPort` khai thẳng: *"MN_SONG (4/19 trạm) ⛔ không thuộc
--   công trình nào **theo thiết kế**"*, và `HydroCatalogueSeedTest` canh đúng
--   bốn điểm ấy vì *"một inner join hay một NOT NULL đặt sai chỗ sẽ làm rớt
--   đúng bốn điểm này khỏi mọi màn hình, và triệu chứng là 'bản đồ thiếu vài
--   chấm' chứ ⛔ không phải một lỗi"*.
--
--   ⚠ Bản đầu của tệp này nối theo `structure_code` **⛔ không xét vai trò**, và
--   nó kéo `DO-TB-HVAN-MN` (Trạm bơm Hồng Vân — Mực nước sông) vào một công
--   trình. Nghe thì hợp lý — mực nước sông TẠI trạm bơm đúng là số mà người vận
--   hành trạm bơm ấy cần. Nhưng nó **đảo một quyết định thiết kế đã ghi**, và
--   đảo trong im lặng: ⛔ không màn hình nào báo, chỉ có một dòng thừa trong
--   bảng liên kết.
--
--   ⇒ Giữ nguyên thiết kế. Hồ sơ *Trạm bơm Hồng Vân* vẫn được lập (nó **là**
--   một công trình Công ty vận hành), nó chỉ ⛔ không có điểm đo nào gắn vào —
--   một trạng thái hợp lệ và đọc được. Ngày Công ty nói MN_SONG **nên** gắn
--   công trình thì đó là một quyết định nghiệp vụ, sửa ở đây kèm ngày.
-- ---------------------------------------------------------------------------
INSERT INTO station_constructions (
    station_id, construction_id, construction_public_id, role, is_primary, created_at
)
SELECT s.id, c.id, c.public_id, s.position_role, TRUE, now()
  FROM stations s
  JOIN constructions c
    ON c.code = s.structure_code
   AND c.deleted_at IS NULL
 WHERE s.deleted_at IS NULL
   AND s.position_role <> 'MN_SONG';

-- ===========================================================================
-- KHỐI KIỂM — ⛔ đây là phần chịu lực của tệp, ⛔ đừng bỏ khi "dọn dẹp"
--
-- Một câu INSERT … SELECT ghi **0 hàng** vẫn thoát 0. Không có khối này thì
-- một lượt migration "thành công" có thể để lại đúng cái bảng rỗng mà nó sinh
-- ra để lấp — và triệu chứng chỉ hiện ra ở một bản đồ trống, thứ trông y hệt
-- "chưa ai nhập toạ độ" (luật 7 · §11.19).
-- ===========================================================================
DO $$
DECLARE
    so_ct        integer;
    so_lien_ket  integer;
    so_toa_do    integer;
    so_song      integer;
    so_ly_trinh  integer;
    so_khong_loai integer;
    so_mn_song_bi_noi integer;
BEGIN
    SELECT count(*) INTO so_ct FROM constructions WHERE deleted_at IS NULL;
    SELECT count(*) INTO so_lien_ket FROM station_constructions WHERE deleted_at IS NULL;
    SELECT count(*) INTO so_toa_do FROM constructions
     WHERE deleted_at IS NULL AND (latitude IS NOT NULL OR longitude IS NOT NULL);
    SELECT count(*) INTO so_song FROM constructions
     WHERE deleted_at IS NULL AND river_name IS NOT NULL;
    SELECT count(*) INTO so_ly_trinh FROM constructions
     WHERE deleted_at IS NULL AND chainage IS NOT NULL;
    SELECT count(*) INTO so_khong_loai FROM constructions
     WHERE deleted_at IS NULL AND construction_type IS NULL;

    IF so_ct <> 11 THEN
        RAISE EXCEPTION 'Mong đợi 11 công trình rút từ 19 điểm đo, thực tế %. '
            'Danh mục điểm đo đã đổi kể từ V202609091074 — ⛔ ĐỪNG nới con số này '
            'cho hết đỏ: hãy đọc lại ba nhóm bị loại có tên ở đầu tệp và xác nhận '
            'nhóm mới thuộc nhóm nào.', so_ct;
    END IF;

    IF so_lien_ket <> 15 THEN
        RAISE EXCEPTION 'Mong đợi 15 liên kết điểm đo–công trình (19 điểm − 3 điểm của '
            'TV-BATHA/TV-HNOI/ANCANH − 1 điểm MN_SONG của TB-HVAN), thực tế %.', so_lien_ket;
    END IF;

    -- ⛔⛔ Khẳng định về THIẾT KẾ, ⛔ không về số lượng: `ConstructionStatusPort` khai
    --    MN_SONG ⛔ không thuộc công trình nào. Một dòng MN_SONG lọt vào đây nghĩa là
    --    mệnh đề WHERE ở trên đã bị gỡ — và nó ⛔ không làm màn hình nào đỏ.
    SELECT count(*) INTO so_mn_song_bi_noi
      FROM station_constructions sc JOIN stations s ON s.id = sc.station_id
     WHERE sc.deleted_at IS NULL AND s.position_role = 'MN_SONG';
    IF so_mn_song_bi_noi <> 0 THEN
        RAISE EXCEPTION '% điểm MN_SONG đã bị nối vào công trình. Đó là ĐẢO một quyết định '
            'thiết kế đã ghi (ConstructionStatusPort), và nó đảo trong im lặng.', so_mn_song_bi_noi;
    END IF;

    -- ⛔⛔ Khẳng định NẶNG NHẤT của tệp. Nó ⛔ không canh một con số nghiệp vụ —
    --    nó canh rằng ⛔ KHÔNG AI bịa toạ độ vào đây, hôm nay hay trong một lượt
    --    sửa về sau.
    IF so_toa_do <> 0 THEN
        RAISE EXCEPTION '% công trình đã mang toạ độ. Bản chụp G8 của Công ty ⛔ KHÔNG '
            'có cột toạ độ, nên mọi giá trị ở đây là BỊA. Một điểm sai trên bản đồ tệ '
            'hơn hẳn một bản đồ trống — bản đồ trống còn nằm trong danh sách nhắc việc.',
            so_toa_do;
    END IF;

    IF so_khong_loai <> 0 THEN
        RAISE EXCEPTION '% công trình ⛔ không suy ra được loại hình. Bộ lọc WHERE và '
            'biểu thức CASE đã lệch nhau — sửa CẢ HAI, ⛔ đừng nới CHECK.', so_khong_loai;
    END IF;

    IF so_song <> 7 OR so_ly_trinh <> 7 THEN
        RAISE EXCEPTION 'Mong đợi 7 công trình có tuyến sông và 7 có lý trình, thực tế % / %. '
            'Con số này ⛔ không phải mục tiêu — nó là ẢNH của dữ liệu G8 hôm nay. Nếu '
            'Công ty vừa gửi thêm, hãy sửa số ở đây và ghi ngày.', so_song, so_ly_trinh;
    END IF;

    RAISE NOTICE 'Danh mục công trình: % hồ sơ · % liên kết điểm đo (0 điểm MN_SONG, đúng thiết kế) '
        '· % có tuyến sông · % có lý trình · 0 có toạ độ (chờ Công ty gửi bảng toạ độ — G8).',
        so_ct, so_lien_ket, so_song, so_ly_trinh;
END $$;
