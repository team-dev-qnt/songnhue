-- ---------------------------------------------------------------------------
-- Báo cáo nhanh (18/09/2026) — danh mục MÁY BƠM: cỡ máy + nhóm máy của từng trạm
--
-- Nguồn: `docs_origin/bao-cao/` — mẫu Word *Báo cáo nhanh* + sheet `TB Tiêu (KH)`
-- của *Danh mục TB Cty SN 2026.xlsx*. Phân tích ở `.claude/master-tracking.md` WS-66.
--
-- ⛔⛔ `nhom_may_bom` GIAO ĐI RỖNG. Cấm seed dữ liệu công trình (CLAUDE.md), và
--    ba sheet của workbook mâu thuẫn nhau về cùng một trạm (Ngoại Độ: 11 máy
--    vs 15+5) ⇒ vào bằng ĐƯỜNG NHẬP TỆP, ⛔ vào bằng migration.
--
-- ⭐ `co_may_bom` CÓ seed 9 hàng: đó là CẤU TRÚC của biểu mẫu hành chính (9 cột
--    của Bảng 1, nhãn chép nguyên văn từ mẫu Word), ⛔ phải số liệu nghiệp vụ.
--    ⚠ BIÊN là ĐỀ XUẤT (OI-BC8) — mẫu chỉ có nhãn, sheet `Sheet3` được khai là
--    "bảng phân loại chuẩn" thì RỖNG. Đọc nhãn theo nghĩa chặt thì 35/830 máy
--    (25.200 · 7.300 · 1.950 m³/h) ⛔ thuộc cột nào. Biên dưới đây cho mọi máy
--    một nhà, và cột "12" ra 0 máy — khớp ô TRỐNG của mẫu. Sửa được trên màn
--    hình (`ops:construction:update`), ⛔ cần deploy.
-- ---------------------------------------------------------------------------

-- ===========================================================================
-- 1. Cỡ máy — 9 cột cố định của Bảng 1
--
-- ⛔ SỐ HÀNG cố định: mẫu Word có đúng 9 cột. Thêm/xoá một cỡ là đổi bố cục
--    văn bản gửi UBND (G10) ⇒ màn hình chỉ SỬA BIÊN, ⛔ thêm, ⛔ xoá.
-- ⛔ Nửa mở [q_tu, q_den): một Q nằm đúng biên thuộc cột TRÊN. NULL = vô cực.
-- ===========================================================================
CREATE TABLE co_may_bom (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID          NOT NULL DEFAULT gen_random_uuid(),

    -- Nhãn in ra cột Bảng 1, nguyên văn mẫu (đơn vị 1.000 m³/h).
    nhan          VARCHAR(20)   NOT NULL,
    q_tu_m3h      NUMERIC(12,2),
    q_den_m3h     NUMERIC(12,2),
    -- = thứ tự cột trong mẫu, 1 = "43" (trái nhất).
    sort_order    INTEGER       NOT NULL,

    created_at    timestamptz   NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    timestamptz,
    updated_by    BIGINT,
    deleted_at    timestamptz,
    version       INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_co_may_bom_khoang CHECK (
        q_tu_m3h IS NULL OR q_den_m3h IS NULL OR q_tu_m3h < q_den_m3h
    ),
    CONSTRAINT ck_co_may_bom_duong CHECK (
        (q_tu_m3h IS NULL OR q_tu_m3h >= 0) AND (q_den_m3h IS NULL OR q_den_m3h > 0)
    )
);

CREATE UNIQUE INDEX ux_co_may_bom_public_id ON co_may_bom (public_id);
CREATE UNIQUE INDEX ux_co_may_bom_thu_tu ON co_may_bom (sort_order) WHERE deleted_at IS NULL;

INSERT INTO co_may_bom (nhan, q_tu_m3h, q_den_m3h, sort_order) VALUES
    ('43',      32500, NULL,  1),
    ('22',      17000, 32500, 2),
    ('12',      10000, 17000, 3),
    ('8',        6000, 10000, 4),
    ('4',        3500,  6000, 5),
    ('2÷3',      2000,  3500, 6),
    ('1,1 ÷1,9', 1100,  2000, 7),
    ('1',        1000,  1100, 8),
    ('< 1',      NULL,  1000, 9);

COMMENT ON TABLE co_may_bom IS
    '9 cột cỡ máy của Bảng 1 Báo cáo nhanh. Nửa mở [q_tu, q_den) m³/h. Biên là ĐỀ XUẤT chờ Công ty (OI-BC8).';


-- ===========================================================================
-- 2. Nhóm máy của một trạm bơm — mỗi dòng Bảng 2 là một nhóm
--
-- ⛔⛔ Q lưu m³/h NGUYÊN BẢN, ⛔ tái dùng `pump_station_specs.flow_per_pump_m3s`.
--    Cột ấy là m³/s NUMERIC(10,3); vòng khứ hồi m³/h → m³/s → m³/h làm hỏng
--    23/27 cỡ Q (801/830 máy = 96,5%, ví dụ 2.500 → 2.498,4), còn TỔNG chỉ lệch
--    −240/2.554.152 vì sai số triệt tiêu nhau ⇒ một phép kiểm tổng ⛔ bao giờ đỏ.
--
-- ⭐ UNIQUE (construction_id, Q): đo trên 830 máy, 178 trạm — 0 trạm có hai nhóm
--    cùng Q. Đây cũng là KHOÁ của đường nhập tệp (mã công trình + Q).
-- ===========================================================================
CREATE TABLE nhom_may_bom (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID          NOT NULL DEFAULT gen_random_uuid(),

    construction_id  BIGINT        NOT NULL REFERENCES constructions (id),
    so_may           SMALLINT      NOT NULL,
    q_mot_may_m3h    NUMERIC(12,2) NOT NULL,
    sort_order       INTEGER       NOT NULL DEFAULT 0,

    created_at       timestamptz   NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       timestamptz,
    updated_by       BIGINT,
    deleted_at       timestamptz,
    version          INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_nhom_may_bom_so_may CHECK (so_may > 0),
    CONSTRAINT ck_nhom_may_bom_q CHECK (q_mot_may_m3h > 0)
);

CREATE UNIQUE INDEX ux_nhom_may_bom_public_id ON nhom_may_bom (public_id);
CREATE UNIQUE INDEX ux_nhom_may_bom_cap ON nhom_may_bom (construction_id, q_mot_may_m3h)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE nhom_may_bom IS
    'Nhóm máy (số máy × Q m³/h) của một trạm bơm — dòng Bảng 2 Báo cáo nhanh. Giao đi RỖNG; vào bằng đường nhập tệp.';

DO $$
DECLARE
    so_co    INTEGER;
    so_nhom  INTEGER;
    so_mo    INTEGER;
BEGIN
    SELECT count(*) INTO so_co FROM co_may_bom WHERE deleted_at IS NULL;
    IF so_co <> 9 THEN
        RAISE EXCEPTION 'co_may_bom phải có đúng 9 cỡ (9 cột của mẫu), đang có %', so_co;
    END IF;

    -- ⛔ Đúng MỘT cận dưới mở và MỘT cận trên mở — hai thì hai cỡ chồng lên nhau ở vô cực.
    SELECT count(*) INTO so_mo FROM co_may_bom
     WHERE deleted_at IS NULL AND (q_tu_m3h IS NULL OR q_den_m3h IS NULL);
    IF so_mo <> 2 THEN
        RAISE EXCEPTION 'co_may_bom phải có đúng 2 biên mở (dưới cùng + trên cùng), đang có %', so_mo;
    END IF;

    SELECT count(*) INTO so_nhom FROM nhom_may_bom;
    IF so_nhom <> 0 THEN
        RAISE EXCEPTION 'nhom_may_bom phải RỖNG khi giao — cấm seed danh mục trạm bơm (đang có % dòng)', so_nhom;
    END IF;
END $$;
