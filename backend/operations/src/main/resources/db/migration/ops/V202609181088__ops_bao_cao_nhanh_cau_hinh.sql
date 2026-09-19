-- ---------------------------------------------------------------------------
-- Báo cáo nhanh — những ô CHỜ CÔNG TY chuyển thành DỮ LIỆU NHẬP TRÊN GIAO DIỆN
-- (18/09/2026, QuanTran: "mục chờ Công ty thì để trống data, cho Công ty nhập
-- trên UI để tránh chờ đợi").
--
-- Trước bản này ba thứ nằm TRONG MÃ:
--   · 14 mã điểm đo của Bảng 3 (`Bang3SongNhue.DONG`) — đổi một điểm đo là
--     phải deploy (quy tắc 16);
--   · mã trạm Yên Nghĩa `TB-YNGHIA` — một QUY ƯỚC NGẦM: đổi mã trạm trên màn
--     hình Công trình là ghi chú lặng lẽ về "chưa có trong danh mục", ⛔ màn
--     hình nào nói ra vì sao. Và danh mục có HAI công trình tên "Yên Nghĩa"
--     (trạm bơm TB-YNGHIA · cống tiêu tự chảy CTTC-YNGHIA — V1049 dòng 472);
--   · Bảng 4 lượng mưa — để trống cứng (chốt sáng 18/09, nay ĐẢO: xem §3).
-- ---------------------------------------------------------------------------


-- ===========================================================================
-- 1. Vị trí trên mẫu cần GẮN một công trình
--
-- ⭐ Hàng = một chỗ CỐ ĐỊNH của mẫu Word (7 cống của Bảng 3 mục 11 + trạm của
--    ghi chú Yên Nghĩa) ⇒ seed cấu trúc; thứ Công ty chọn là `construction_id`.
-- ⭐ Bảng 3 ⛔ lưu mã điểm đo ở đây: điểm đo của từng vế SUY RA từ
--    `station_constructions.role` (THUONG_LUU/HA_LUU) — màn hình liên kết điểm
--    đo–công trình đã có. Lưu thêm một bản ở đây là hai nơi cho một câu hỏi
--    (luật 14). Cống chưa có điểm đo ở một vế (OI-BC14) ⇒ Công ty thêm điểm đo
--    rồi liên kết, ⛔ chờ ai sửa mã.
-- ⚠ `construction_id` rút theo `code` từ danh mục có sẵn (V202609091075 —
--    dữ liệu Công ty, ⛔ bịa), đúng những mã mà mã nguồn đang ghi cứng trước
--    bản này ⇒ hành vi ⛔ đổi ngày deploy. ⛔ tìm thấy ⇒ NULL, Công ty chọn.
-- ===========================================================================
CREATE TABLE bao_cao_nhanh_vi_tri (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    ma                VARCHAR(30)  NOT NULL,
    nhan              VARCHAR(255) NOT NULL,
    loai_cong_trinh   VARCHAR(30)  NOT NULL,
    construction_id   BIGINT       REFERENCES constructions (id),
    sort_order        INTEGER      NOT NULL,

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    deleted_at        timestamptz,
    version           INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_bcn_vi_tri_loai CHECK (loai_cong_trinh IN ('CONG', 'TRAM_BOM'))
);

CREATE UNIQUE INDEX ux_bcn_vi_tri_public_id ON bao_cao_nhanh_vi_tri (public_id);
CREATE UNIQUE INDEX ux_bcn_vi_tri_ma ON bao_cao_nhanh_vi_tri (ma) WHERE deleted_at IS NULL;

INSERT INTO bao_cao_nhanh_vi_tri (ma, nhan, loai_cong_trinh, construction_id, sort_order)
SELECT v.ma, v.nhan, v.loai,
       (SELECT c.id FROM constructions c WHERE c.code = v.code AND c.deleted_at IS NULL),
       v.tt
  FROM (VALUES
    ('B3_LIEN_MAC',  'Bảng 3 — Cống Liên Mạc',  'CONG',     'LMAC',  1),
    ('B3_HA_DONG',   'Bảng 3 — Cống Hà Đông',   'CONG',     'HDONG', 2),
    ('B3_DONG_QUAN', 'Bảng 3 — Cống Đồng Quan', 'CONG',     'DQUAN', 3),
    ('B3_HOA_MY',    'Bảng 3 — Cống Hòa Mỹ',    'CONG',     'HMY',   4),
    ('B3_VAN_DINH',  'Bảng 3 — Cống Vân Đình',  'CONG',     'VDINH', 5),
    ('B3_NHAT_TUU',  'Bảng 3 — Cống Nhật Tựu',  'CONG',     'NTUU',  6),
    ('B3_LUONG_CO',  'Bảng 3 — Cống Lương Cổ',  'CONG',     'LCO',   7),
    ('YEN_NGHIA',    'Ghi chú — Trạm bơm Yên Nghĩa', 'TRAM_BOM', 'TB-YNGHIA', 8)
  ) AS v(ma, nhan, loai, code, tt);


-- ===========================================================================
-- 2. Ảnh chụp cấu hình của một kỳ — ghi lúc CHỐT
--
-- Văn bản đã gửi UBND ⛔ đổi khi Công ty đổi công trình gắn vào một vị trí, hay
-- đổi liên kết điểm đo, SAU khi chốt. Cùng lý lẽ ảnh chụp Bảng 2 (V1087 §3).
-- `api_tl`/`api_hl` NULL = vế ấy ⛔ có điểm đo LÚC CHỐT.
-- ===========================================================================
CREATE TABLE bao_cao_nhanh_vi_tri_ky (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    bao_cao_id        BIGINT       NOT NULL REFERENCES bao_cao_nhanh (id),
    vi_tri_id         BIGINT       NOT NULL REFERENCES bao_cao_nhanh_vi_tri (id),
    construction_id   BIGINT       REFERENCES constructions (id),
    api_tl            VARCHAR(50),
    api_hl            VARCHAR(50),

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    deleted_at        timestamptz,
    version           INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_bcn_vtk_public_id ON bao_cao_nhanh_vi_tri_ky (public_id);
CREATE UNIQUE INDEX ux_bcn_vtk_cap ON bao_cao_nhanh_vi_tri_ky (bao_cao_id, vi_tri_id) WHERE deleted_at IS NULL;


-- ===========================================================================
-- 3. Bảng 4 — lượng mưa NHẬP TAY theo kỳ
--
-- ⚠⚠ ĐẢO chốt sáng 18/09 ("Bảng 4 để trống, ⛔ nhập tay") theo yêu cầu cùng
--    ngày của QuanTran. Lý do: nguồn tự động (G3-a) chưa có ngày về, trong khi
--    Công ty CÓ số mưa lúc lập báo cáo. Ngày G3-a về: một nguồn tự động THAY
--    ô nhập — ⛔ trộn hai nguồn trong cùng một kỳ.
-- ⭐ Điểm đo mưa = CẤU TRÚC mẫu (STT in ra bản Word) ⇒ seed; chỉ 8 điểm của
--    Sông Nhuệ (STT 5–12) — 30 điểm còn lại thuộc ba công ty kia (OI-BC1),
--    bản Word để trống. Tên chép NGUYÊN VĂN mẫu (kể cả "Hòa mỹ", "Nhât Tựu")
--    vì bộ điền đối chiếu tên với ô của mẫu trước khi ghi.
-- NULL = chưa nhập (ô trống) — khác 0 mm.
-- ===========================================================================
CREATE TABLE diem_mua_bao_cao_nhanh (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    ten               VARCHAR(255) NOT NULL,
    sort_order        INTEGER      NOT NULL,

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    deleted_at        timestamptz,
    version           INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_diem_mua_bcn_public_id ON diem_mua_bao_cao_nhanh (public_id);
CREATE UNIQUE INDEX ux_diem_mua_bcn_thu_tu ON diem_mua_bao_cao_nhanh (sort_order) WHERE deleted_at IS NULL;

INSERT INTO diem_mua_bao_cao_nhanh (ten, sort_order)
SELECT v.ten, v.tt
  FROM (VALUES
    ('Liên Mạc', 5), ('Hà Đông', 6), ('Đồng Quan', 7), ('Hòa mỹ', 8),
    ('Vân Đình', 9), ('Nhât Tựu', 10), ('Lương Cổ', 11), ('Điệp Sơn', 12)
  ) AS v(ten, tt);

CREATE TABLE bao_cao_nhanh_luong_mua (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID          NOT NULL DEFAULT gen_random_uuid(),
    bao_cao_id        BIGINT        NOT NULL REFERENCES bao_cao_nhanh (id),
    diem_mua_id       BIGINT        NOT NULL REFERENCES diem_mua_bao_cao_nhanh (id),
    luong_mua_mm      NUMERIC(8,1),

    created_at        timestamptz   NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    deleted_at        timestamptz,
    version           INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_bcn_lm_khong_am CHECK (luong_mua_mm IS NULL OR luong_mua_mm >= 0)
);

CREATE UNIQUE INDEX ux_bcn_lm_public_id ON bao_cao_nhanh_luong_mua (public_id);
CREATE UNIQUE INDEX ux_bcn_lm_cap ON bao_cao_nhanh_luong_mua (bao_cao_id, diem_mua_id) WHERE deleted_at IS NULL;


-- ===========================================================================
-- KHỐI KIỂM (§10.66)
-- ===========================================================================
DO $$
DECLARE
    so_vi_tri      INTEGER;
    so_da_gan      INTEGER;
    so_co_ma       INTEGER;
    so_diem_mua    INTEGER;
    so_luong_mua   INTEGER;
BEGIN
    SELECT count(*) INTO so_vi_tri FROM bao_cao_nhanh_vi_tri WHERE deleted_at IS NULL;
    IF so_vi_tri <> 8 THEN
        RAISE EXCEPTION 'Mẫu có đúng 8 vị trí gắn công trình (7 cống Bảng 3 + Yên Nghĩa), đang có %', so_vi_tri;
    END IF;

    -- Số vị trí ĐÃ gắn phải bằng số công trình CÓ THẬT mang 8 mã ấy — ⛔ ghim 8:
    -- một môi trường đã xoá một cống thì NULL là đúng; một phép nối hụt thì ⛔.
    SELECT count(*) INTO so_da_gan FROM bao_cao_nhanh_vi_tri
     WHERE deleted_at IS NULL AND construction_id IS NOT NULL;
    SELECT count(*) INTO so_co_ma FROM constructions
     WHERE deleted_at IS NULL
       AND code IN ('LMAC', 'HDONG', 'DQUAN', 'HMY', 'VDINH', 'NTUU', 'LCO', 'TB-YNGHIA');
    IF so_da_gan <> so_co_ma THEN
        RAISE EXCEPTION 'Gắn được % vị trí trong khi danh mục có % công trình mang mã ấy', so_da_gan, so_co_ma;
    END IF;

    SELECT count(*) INTO so_diem_mua FROM diem_mua_bao_cao_nhanh WHERE deleted_at IS NULL;
    IF so_diem_mua <> 8 THEN
        RAISE EXCEPTION 'Bảng 4 có đúng 8 điểm mưa của Sông Nhuệ (STT 5–12), đang có %', so_diem_mua;
    END IF;

    SELECT count(*) INTO so_luong_mua FROM bao_cao_nhanh_luong_mua;
    IF so_luong_mua <> 0 THEN
        RAISE EXCEPTION 'Lượng mưa phải RỖNG khi giao — ⛔ seed số liệu, đang có % hàng', so_luong_mua;
    END IF;
END $$;
