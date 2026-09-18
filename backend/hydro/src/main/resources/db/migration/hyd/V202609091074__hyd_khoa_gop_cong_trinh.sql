-- =============================================================================
-- WS-43 / T43.3 — Khoá gộp cấp CÔNG TRÌNH cho tầng trình bày MOD-03
--                 (spec-description.md §3.1, §5.2, §6.1.2)
--
-- Bốn cột, và mỗi cột trả lời một câu mà bảng §6.1.2 hỏi:
--   structure_code / structure_name — "hai điểm đo này thuộc CÙNG một công
--                                      trình?"  → gộp ô 3 cột đầu + cặp TL/HL
--   display_order                   — "thứ tự thượng nguồn xuống hạ nguồn?"
--   is_main_axis                    — "cống nào lên TRANG CHỦ?"
--
-- ⛔⛔ VÌ SAO KHOÁ GỘP NẰM Ở ĐÂY, KHÔNG PHẢI Ở `constructions`
--
-- `station_constructions` (V202608311049) đã là đường nối điểm đo → công trình
-- của MOD-02, và nó vẫn là đường đúng cho GIS/bảo trì/dashboard. Nhưng nó
-- **rỗng** (0 hàng, cùng `constructions` 0 hàng — G8 chưa về), nên mọi phép gộp
-- đi qua nó hôm nay chạy trên tập rỗng (quy tắc 7).
--
-- spec-description.md §3.1 đặt `structure_code`/`structure_name` **trên chính
-- bảng điểm đo**. Đó không phải sự cẩu thả của người viết spec: hệ quan trắc
-- biết mã điểm đo thuộc cống nào, và tri thức ấy độc lập với việc Công ty đã
-- lập xong danh mục công trình hay chưa.
--
-- ⇒ HAI khoá công trình cùng tồn tại, CỐ Ý, và chúng trả lời hai câu khác nhau:
--
--   | khoá                        | câu nó trả lời                | ai đọc |
--   |-----------------------------|-------------------------------|--------|
--   | `stations.structure_code`   | gộp điểm đo để TRÌNH BÀY      | MOD-03 |
--   | `station_constructions`     | điểm đo gắn với CÔNG TRÌNH    | MOD-02 |
--
-- ⛔ ĐỪNG HỢP NHẤT HAI KHOÁ NÀY. Ngày G8 về, `station_constructions` được điền
--    và `structure_code` vẫn đúng vai của nó — một bảng thuỷ văn phải dựng được
--    kể cả khi danh mục công trình còn trống. Hợp nhất chúng là buộc tầng trình
--    bày của MOD-03 vào tiến độ nhập liệu của MOD-02.
--
-- ⚠ BẢNG ÁNH XẠ DƯỚI ĐÂY LÀ BẢNG KHAI, KHÔNG PHẢI BẢNG SUY.
--   Mã điểm đo có dạng `DO-<công trình>-<vai trò>`, nên bóc chuỗi ra được. ⛔
--   Không làm thế: cùng lỗi §10.54 ở dạng khác — một quy tắc bóc chuỗi đúng cho
--   19 dòng hôm nay sẽ im lặng gán sai cho dòng thứ 20 mang cách đặt tên khác
--   (và §2.7 của phase3-plan.md cho thấy còn ~9 mã lạ đang chờ vào danh mục).
--   Viết thẳng ra thì sai một dòng là thấy một dòng.
-- =============================================================================

ALTER TABLE stations
    ADD COLUMN structure_code VARCHAR(50),
    ADD COLUMN structure_name VARCHAR(255),
    ADD COLUMN display_order  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN is_main_axis   BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN stations.structure_code IS
    'Mã CÔNG TRÌNH để gộp cặp thượng lưu/hạ lưu khi trình bày (spec §3.1). '
    'NULL = chưa biết điểm đo này thuộc công trình nào ⇒ nó đứng một mình một nhóm, '
    'KHÔNG bị bỏ khỏi bảng. ⛔ Khác `station_constructions` — xem đầu file.';

COMMENT ON COLUMN stations.structure_name IS
    'Tên công trình hiện trên bảng. NULL thì tầng đọc lùi về `stations.name`.';

COMMENT ON COLUMN stations.display_order IS
    'Thứ tự hiển thị thượng nguồn → hạ nguồn, do Công ty cấp (spec §3.1). '
    '⛔ 0 = CHƯA CẤP, không phải "đứng đầu": tầng đọc sắp theo '
    '(river_name, display_order, chainage_m NULLS LAST, code) nên khi cả tuyến '
    'còn 0 thì lý trình quyết định — và lý trình là số đo thật, không phải số bịa.';

COMMENT ON COLUMN stations.is_main_axis IS
    'Cống trên trục chính, hiện ở khối trang chủ (spec §5.2, OI-C chưa chốt). '
    '⛔ KHÔNG có điểm đo nào TRUE nghĩa là "công bố TẤT CẢ", KHÔNG phải "không '
    'công bố gì" — cùng quy ước với khoá settings `hydro.portal.station-codes` '
    '(V202609041064). Một trang chủ rỗng vì chưa ai tick ô nào là hình dạng '
    '§10.79, và nó chỉ lộ ra trên production.';

CREATE INDEX ix_stations_gop_cong_trinh
    ON stations (structure_code, display_order)
    WHERE deleted_at IS NULL AND structure_code IS NOT NULL;

CREATE INDEX ix_stations_truc_chinh
    ON stations (is_main_axis)
    WHERE deleted_at IS NULL AND is_main_axis = TRUE;


-- =============================================================================
-- Ánh xạ điểm đo → công trình cho 19 điểm đo đang có.
--
-- Nguồn: chính bảng seed V202608311049 (bảng Công ty cấp, chốt G8b) + lượt sửa
-- F01519 ở V202609091073. Tên công trình lấy đúng cách gọi trên "Biểu tổng hợp"
-- của hệ quan trắc, đo 09/09/2026 (phase3-plan.md §2.3) — để bảng của ta và tờ
-- biểu Công ty đang dùng gọi cùng một cái tên cùng một kiểu.
--
-- ⛔ `display_order` KHÔNG điền ở đây. Spec §3.1 ghi cột này do Công ty cấp và
--    ta chưa có. Điền một con số "cho có thứ tự" là đúng thứ quy tắc 16 cấm:
--    nó biến "chưa biết" thành một khẳng định. Thứ tự hôm nay do `chainage_m`
--    quyết định — 10/19 điểm có lý trình thật, và đó là số đo, không phải số bịa.
--
-- ⛔ `is_main_axis` KHÔNG điền: OI-C ("10 cống trục chính nào") chưa có câu trả
--    lời. Để FALSE hết ⇒ tầng đọc coi là "tất cả" (xem COMMENT ở trên). Ngày
--    OI-C về thì tick 10 ô trên màn hình quản trị, ⛔ không cần migration.
-- =============================================================================
UPDATE stations s
   SET structure_code = v.structure_code,
       structure_name = v.structure_name,
       updated_at     = now()
  FROM (VALUES
    -- api_code   structure_code        structure_name
    ('F01771'::varchar, 'LMAC'::varchar,        'Cống Liên Mạc'::varchar),
    ('F01672',          'LMAC',                 'Cống Liên Mạc'),
    ('F01965',          'LMAC2',                'Cống Liên Mạc 2'),
    ('F01794',          'HDONG',                'Cống Hà Đông'),
    ('F01905',          'DQUAN',                'Cống Đồng Quan'),
    ('F01527',          'DQUAN',                'Cống Đồng Quan'),
    ('F02031',          'NTUU',                 'Cống Nhật Tựu'),
    ('F02030',          'NTUU',                 'Cống Nhật Tựu'),
    ('F01519',          'LCO',                  'Cống Lương Cổ'),
    ('F01657',          'VDINH',                'Cống Vân Đình'),
    ('F01705',          'VDINH',                'Cống Vân Đình'),
    ('F02039',          'HMY',                  'Cống Hòa Mỹ'),
    ('F01820',          'CTTC-YNGHIA',          'Cống tiêu tự chảy Yên Nghĩa'),
    ('F01652',          'CTTC-YNGHIA',          'Cống tiêu tự chảy Yên Nghĩa'),
    ('F01707',          'TB-YNGHIA',            'Trạm bơm Yên Nghĩa'),
    ('F01732',          'TB-HVAN',              'Trạm bơm Hồng Vân'),
    ('F01559',          'TV-HNOI',              'Trạm thuỷ văn Hà Nội'),
    ('F01812',          'ANCANH',               'An Cảnh'),
    ('F01532',          'TV-BATHA',             'Trạm thuỷ văn Ba Thá')
  ) AS v(api_code, structure_code, structure_name)
 WHERE s.api_code = v.api_code
   AND s.deleted_at IS NULL;


-- =============================================================================
-- Khối kiểm — Flyway hỏng ngay tại đây nếu bảng ánh xạ lệch khỏi thực tế.
--
-- ⛔ Vì sao kiểm ở migration chứ không chỉ ở bài kiểm Java: bộ test chạy trên
--    CSDL RỖNG, nơi 19 dòng seed này CÓ mặt; còn môi trường thật có CSDL đã đầy,
--    nơi một lượt sửa mã điểm đo (đã xảy ra: V202609091073 đổi F01519) có thể
--    làm bảng ánh xạ trượt mà không ai thấy — quy tắc 30 · §11.19.
-- =============================================================================
DO $$
DECLARE
    so_gan       INTEGER;
    so_thieu     INTEGER;
    so_cap_du    INTEGER;
    so_truc      INTEGER;
    so_thu_tu    INTEGER;
BEGIN
    SELECT count(*) INTO so_gan
      FROM stations WHERE deleted_at IS NULL AND structure_code IS NOT NULL;
    IF so_gan <> 19 THEN
        RAISE EXCEPTION
            'V202609091074: phải gán khoá công trình cho đủ 19 điểm đo, đang có %. '
            'Nhiều khả năng một api_code đã đổi mà bảng ánh xạ chưa theo.', so_gan;
    END IF;

    SELECT count(*) INTO so_thieu
      FROM stations WHERE deleted_at IS NULL AND structure_name IS NULL;
    IF so_thieu <> 0 THEN
        RAISE EXCEPTION
            'V202609091074: % điểm đo có structure_code mà thiếu structure_name — '
            'hai cột này đi thành cặp.', so_thieu;
    END IF;

    -- ⭐ Vế CHỐNG TẬP RỖNG (quy tắc 7 · §11.19). Khối kiểm chỉ đếm "đã gán đủ 19"
    --    sẽ XANH cả khi mọi điểm đo rơi vào 19 nhóm RIÊNG BIỆT — tức là khoá gộp
    --    tồn tại mà ⛔ không gộp được gì, và bảng §6.1.2 vẫn ra 19 dòng một dòng
    --    một công trình y như hôm nay. Đây đúng là hình dạng lỗi mà cả cột này
    --    sinh ra để chữa, nên nó phải có một khẳng định riêng.
    SELECT count(*) INTO so_cap_du
      FROM (SELECT structure_code
              FROM stations
             WHERE deleted_at IS NULL AND structure_code IS NOT NULL
             GROUP BY structure_code
            HAVING count(*) FILTER (WHERE position_role = 'THUONG_LUU') > 0
               AND count(*) FILTER (WHERE position_role = 'HA_LUU')     > 0) t;
    IF so_cap_du < 5 THEN
        RAISE EXCEPTION
            'V202609091074: chờ ít nhất 5 công trình có ĐỦ cặp thượng lưu + hạ lưu '
            '(Liên Mạc · Đồng Quan · Nhật Tựu · Vân Đình · CTTC Yên Nghĩa), đang có %. '
            'Dưới ngưỡng này thì dòng "Chênh lệch" của §6.1.2 chạy trên tập rỗng.', so_cap_du;
    END IF;

    -- Hai cột còn lại CỐ Ý chưa có dữ liệu — khẳng định điều đó, để ngày ai đó
    -- điền "cho đẹp" thì bài kiểm đối chứng ở HydroCatalogueSeedTest đỏ, chứ
    -- không phải một con số bịa lặng lẽ vào CSDL (§10.54).
    SELECT count(*) INTO so_truc
      FROM stations WHERE deleted_at IS NULL AND is_main_axis = TRUE;
    IF so_truc <> 0 THEN
        RAISE EXCEPTION
            'V202609091074: is_main_axis phải còn 0 điểm đo — OI-C (10 cống trục chính) '
            'chưa có câu trả lời, đang có % điểm đã tick.', so_truc;
    END IF;

    SELECT count(*) INTO so_thu_tu
      FROM stations WHERE deleted_at IS NULL AND display_order <> 0;
    IF so_thu_tu <> 0 THEN
        RAISE EXCEPTION
            'V202609091074: display_order phải còn 0 ở tất cả điểm đo — Công ty chưa cấp '
            'thứ tự, đang có % điểm khác 0.', so_thu_tu;
    END IF;
END $$;
