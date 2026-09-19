-- ---------------------------------------------------------------------------
-- Báo cáo nhanh (18/09/2026) — kỳ báo cáo, Bảng 2 (vận hành), Bảng 5 (ngập úng),
-- 84 xã/phường của mẫu, quy trình CHỐT/MỞ LẠI, hai mã quyền.
--
-- Nguồn: mẫu Word *Báo cáo nhanh* của Công ty (`docs_origin/bao-cao/`).
-- Chuỗi tính MỘT CHIỀU: Bảng 2 (nhập) → Bảng 1 → Mục 1; Bảng 5 (nhập) → Mục 3.
-- ⛔ Mọi ô "Cộng"/"Tổng cộng"/Bảng 1 là giá trị DẪN XUẤT, tính ở BE (quy tắc 3)
--    ⇒ ⛔ có cột nào cho chúng ở đây.
-- ---------------------------------------------------------------------------

-- ===========================================================================
-- 1. Đơn vị hành chính của Bảng 5 — 84 xã/phường, THỨ TỰ CỐ ĐỊNH theo mẫu
--
-- ⭐ CÓ seed: đây là CẤU TRÚC biểu mẫu hành chính (số TT 1..84 in ra bản Word,
--    nhóm theo 4 công ty thuỷ lợi), ⛔ phải số liệu nghiệp vụ. Chép nguyên văn
--    tên + thứ tự từ Bảng 5 của mẫu.
-- ⛔ Công ty Sông Nhuệ chỉ NHẬP được 20 xã của mình (TT 47–66); 64 xã còn lại
--    thuộc ba công ty khác (OI-BC1) — có mặt để bản Word giữ đúng số dòng.
-- ===========================================================================
CREATE TABLE don_vi_hanh_chinh (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    ten               VARCHAR(255) NOT NULL,
    cong_ty_thuy_loi  VARCHAR(20)  NOT NULL,
    sort_order        INTEGER      NOT NULL,

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    deleted_at        timestamptz,
    version           INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_don_vi_hanh_chinh_cong_ty CHECK (
        cong_ty_thuy_loi IN ('SONG_TICH', 'SONG_DAY', 'SONG_NHUE', 'HA_NOI')
    )
);

CREATE UNIQUE INDEX ux_don_vi_hanh_chinh_public_id ON don_vi_hanh_chinh (public_id);
CREATE UNIQUE INDEX ux_don_vi_hanh_chinh_thu_tu ON don_vi_hanh_chinh (sort_order) WHERE deleted_at IS NULL;

INSERT INTO don_vi_hanh_chinh (ten, cong_ty_thuy_loi, sort_order)
SELECT v.ten, v.cong_ty, v.tt
  FROM (VALUES
    ('Quảng Oai', 'SONG_TICH', 1), ('Vật Lại', 'SONG_TICH', 2), ('Cổ Đô', 'SONG_TICH', 3),
    ('Bất Bạt', 'SONG_TICH', 4), ('Suối Hai', 'SONG_TICH', 5), ('Ba Vì', 'SONG_TICH', 6),
    ('Yên Bài', 'SONG_TICH', 7), ('Phúc Thọ', 'SONG_TICH', 8), ('Phúc Lộc', 'SONG_TICH', 9),
    ('Hát Môn', 'SONG_TICH', 10), ('Thạch Thất', 'SONG_TICH', 11), ('Hạ Bằng', 'SONG_TICH', 12),
    ('Tây Phương', 'SONG_TICH', 13), ('Yên Xuân', 'SONG_TICH', 14), ('Quốc Oai', 'SONG_TICH', 15),
    ('Hưng Đạo', 'SONG_TICH', 16), ('Kiều Phú', 'SONG_TICH', 17), ('Phú Cát', 'SONG_TICH', 18),
    ('Sơn Tây', 'SONG_TICH', 19), ('Tùng Thiện', 'SONG_TICH', 20), ('Đoài Phương', 'SONG_TICH', 21),
    ('Chương Mỹ', 'SONG_DAY', 22), ('Quảng Bị', 'SONG_DAY', 23), ('Phú Nghĩa', 'SONG_DAY', 24),
    ('Xuân Mai', 'SONG_DAY', 25), ('Trần Phú', 'SONG_DAY', 26), ('Hòa Phú', 'SONG_DAY', 27),
    ('Mỹ Đức', 'SONG_DAY', 28), ('Hồng Sơn', 'SONG_DAY', 29), ('Phúc Sơn', 'SONG_DAY', 30),
    ('Hương Sơn', 'SONG_DAY', 31), ('Dương Nội', 'SONG_DAY', 32), ('Yên Nghĩa', 'SONG_DAY', 33),
    ('Phú Lương', 'SONG_DAY', 34), ('Kiến Hưng', 'SONG_DAY', 35), ('Thanh Oai', 'SONG_DAY', 36),
    ('Bình Minh', 'SONG_DAY', 37), ('Tam Hưng', 'SONG_DAY', 38), ('Dân Hòa', 'SONG_DAY', 39),
    ('Dương Hòa', 'SONG_DAY', 40), ('Hoài Đức', 'SONG_DAY', 41), ('Sơn Đồng', 'SONG_DAY', 42),
    ('An Khánh', 'SONG_DAY', 43), ('Đan Phượng', 'SONG_DAY', 44), ('Ô Diên', 'SONG_DAY', 45),
    ('Liên Minh', 'SONG_DAY', 46),
    ('Đông Ngạc', 'SONG_NHUE', 47), ('Thượng Cát', 'SONG_NHUE', 48), ('Đại Mỗ', 'SONG_NHUE', 49),
    ('Thanh Trì', 'SONG_NHUE', 50), ('Đại Thanh', 'SONG_NHUE', 51), ('Nam Phù', 'SONG_NHUE', 52),
    ('Ngọc Hồi', 'SONG_NHUE', 53), ('Thường Tín', 'SONG_NHUE', 54), ('Thượng Phúc', 'SONG_NHUE', 55),
    ('Chương Dương', 'SONG_NHUE', 56), ('Hồng Vân', 'SONG_NHUE', 57), ('Phú Xuyên', 'SONG_NHUE', 58),
    ('Phượng Dực', 'SONG_NHUE', 59), ('Chuyên Mỹ', 'SONG_NHUE', 60), ('Đại Xuyên', 'SONG_NHUE', 61),
    ('Ứng Hòa', 'SONG_NHUE', 62), ('Hòa Xá', 'SONG_NHUE', 63), ('Ứng Thiên', 'SONG_NHUE', 64),
    ('Vân Đình', 'SONG_NHUE', 65), ('Lĩnh Nam', 'SONG_NHUE', 66),
    ('Việt Hưng', 'HA_NOI', 67), ('Gia Lâm', 'HA_NOI', 68), ('Thuận An', 'HA_NOI', 69),
    ('Phù Đổng', 'HA_NOI', 70), ('Đông Anh', 'HA_NOI', 71), ('Thư Lâm', 'HA_NOI', 72),
    ('Phúc Thịnh', 'HA_NOI', 73), ('Thiên Lộc', 'HA_NOI', 74), ('Vĩnh Thanh', 'HA_NOI', 75),
    ('Mê Linh', 'HA_NOI', 76), ('Yên Lãng', 'HA_NOI', 77), ('Tiến Thắng', 'HA_NOI', 78),
    ('Quang Minh', 'HA_NOI', 79), ('Sóc Sơn', 'HA_NOI', 80), ('Đa Phúc', 'HA_NOI', 81),
    ('Nội Bài', 'HA_NOI', 82), ('Trung Giã', 'HA_NOI', 83), ('Kim Anh', 'HA_NOI', 84)
  ) AS v(ten, cong_ty, tt);


-- ===========================================================================
-- 2. Kỳ báo cáo — bản ghi CÓ VÒNG ĐỜI (NHAP → DA_CHOT → NHAP)
--
-- ⛔ `trang_thai` CHỈ Workflow engine ghi (quy tắc 4).
-- `ly_do_mo_lai` — WorkflowReasonAware: lý do của lượt MỞ LẠI đi vào
--    `audit_logs` (chuỗi băm) cùng ai bấm, lúc nào ⇒ biết ai sửa gì SAU KHI văn
--    bản đã gửi UBND.
-- ===========================================================================
CREATE TABLE bao_cao_nhanh (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID          NOT NULL DEFAULT gen_random_uuid(),
    tu_thoi_diem    timestamptz   NOT NULL,
    den_thoi_diem   timestamptz   NOT NULL,
    trang_thai      VARCHAR(20)   NOT NULL DEFAULT 'NHAP',
    ly_do_mo_lai    VARCHAR(1000),

    created_at      timestamptz   NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_bao_cao_nhanh_khung CHECK (den_thoi_diem > tu_thoi_diem),
    CONSTRAINT ck_bao_cao_nhanh_trang_thai CHECK (trang_thai IN ('NHAP', 'DA_CHOT'))
);

CREATE UNIQUE INDEX ux_bao_cao_nhanh_public_id ON bao_cao_nhanh (public_id);
CREATE INDEX ix_bao_cao_nhanh_den ON bao_cao_nhanh (den_thoi_diem DESC) WHERE deleted_at IS NULL;


-- ===========================================================================
-- 3. Bảng 2 — số máy ĐANG CHẠY của từng nhóm máy trong kỳ
--
-- ⭐ `so_may_thiet_ke` + `q_mot_may_m3h` là ẢNH CHỤP danh mục lúc ghi.
--    ⛔ Khoá ngoại ghép (nhom_may_id, so_may) sang danh mục như bản nháp plan:
--    nó CHẶN Công ty sửa số máy của một trạm khi đã có kỳ báo cáo cũ trỏ vào
--    — và một kỳ ĐÃ CHỐT (văn bản đã gửi) ⛔ được đổi số theo danh mục mới.
--    Ảnh chụp giữ văn bản đã gửi đúng như lúc gửi; CHECK dưới vẫn ép bất
--    biến "vận hành ≤ thiết kế" trên chính ảnh chụp ấy.
-- `so_may_van_hanh` NULL = CHƯA NHẬP — khác 0 ("đã kiểm, ⛔ máy nào chạy").
-- ===========================================================================
CREATE TABLE bao_cao_nhanh_van_hanh (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID          NOT NULL DEFAULT gen_random_uuid(),
    bao_cao_id        BIGINT        NOT NULL REFERENCES bao_cao_nhanh (id),
    nhom_may_id       BIGINT        NOT NULL REFERENCES nhom_may_bom (id),
    so_may_thiet_ke   SMALLINT      NOT NULL,
    q_mot_may_m3h     NUMERIC(12,2) NOT NULL,
    so_may_van_hanh   SMALLINT,

    created_at        timestamptz   NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    deleted_at        timestamptz,
    version           INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_bcn_vh_thiet_ke CHECK (so_may_thiet_ke > 0 AND q_mot_may_m3h > 0),
    CONSTRAINT ck_bcn_vh_khong_am CHECK (so_may_van_hanh IS NULL OR so_may_van_hanh >= 0),
    -- spec Báo cáo nhanh §4.2 mục 3 — service ném OPS-2028 TRƯỚC khi tới đây.
    CONSTRAINT ck_bcn_vh_khong_vuot CHECK (so_may_van_hanh IS NULL OR so_may_van_hanh <= so_may_thiet_ke)
);

CREATE UNIQUE INDEX ux_bcn_vh_public_id ON bao_cao_nhanh_van_hanh (public_id);
CREATE UNIQUE INDEX ux_bcn_vh_cap ON bao_cao_nhanh_van_hanh (bao_cao_id, nhom_may_id) WHERE deleted_at IS NULL;


-- ===========================================================================
-- 4. Bảng 5 — diện tích ngập úng (ha) theo xã
-- ⛔ Cột "Cộng" / "Tổng cộng" / dòng công ty: DẪN XUẤT, tính ở BE.
-- NULL = chưa nhập (ô trống trong bản Word) — khác 0.
-- ===========================================================================
CREATE TABLE bao_cao_nhanh_ngap_ung (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    bao_cao_id             BIGINT        NOT NULL REFERENCES bao_cao_nhanh (id),
    don_vi_hanh_chinh_id   BIGINT        NOT NULL REFERENCES don_vi_hanh_chinh (id),
    ngap_trang_lua         NUMERIC(12,2),
    ngap_trang_rau         NUMERIC(12,2),
    sau_nuoc_lua           NUMERIC(12,2),
    sau_nuoc_rau           NUMERIC(12,2),

    created_at             timestamptz   NOT NULL DEFAULT now(),
    created_by             BIGINT,
    updated_at             timestamptz,
    updated_by             BIGINT,
    deleted_at             timestamptz,
    version                INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT ck_bcn_nu_khong_am CHECK (
        (ngap_trang_lua IS NULL OR ngap_trang_lua >= 0) AND (ngap_trang_rau IS NULL OR ngap_trang_rau >= 0)
        AND (sau_nuoc_lua IS NULL OR sau_nuoc_lua >= 0) AND (sau_nuoc_rau IS NULL OR sau_nuoc_rau >= 0)
    )
);

CREATE UNIQUE INDEX ux_bcn_nu_public_id ON bao_cao_nhanh_ngap_ung (public_id);
CREATE UNIQUE INDEX ux_bcn_nu_cap ON bao_cao_nhanh_ngap_ung (bao_cao_id, don_vi_hanh_chinh_id)
    WHERE deleted_at IS NULL;


-- ===========================================================================
-- 5. Hai mã quyền — tên TIẾNG ANH theo quy ước của 19 mã `ops:*` đang có
--
--   ops:quick-report:manage — tạo kỳ, nhập Bảng 2/5, CHỐT kỳ
--   ops:quick-report:reopen — MỞ LẠI kỳ đã chốt (bắt buộc lý do). Tách riêng vì
--       đây là sửa một văn bản ĐÃ GỬI ⇒ chỉ SUPER_ADMIN + ADMIN.
-- Xem/tải Word dùng lại `ops:report:view` / `ops:report:export`.
-- ⚠ Người nhập (OI-BC2 chưa chốt) — mặc định TECHNICIAN + XN_MANAGER, hai vai
--   trò đang giữ `ops:report:export`; sửa được trên màn hình phân quyền.
-- ===========================================================================
INSERT INTO permissions (code, module, resource, action, name)
VALUES ('ops:quick-report:manage', 'ops', 'quick-report', 'manage', 'Lập và chốt Báo cáo nhanh'),
       ('ops:quick-report:reopen', 'ops', 'quick-report', 'reopen', 'Mở lại Báo cáo nhanh đã chốt')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE (p.code = 'ops:quick-report:manage' AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'TECHNICIAN', 'XN_MANAGER'))
    OR (p.code = 'ops:quick-report:reopen' AND r.code IN ('SUPER_ADMIN', 'ADMIN'))
ON CONFLICT DO NOTHING;


-- ===========================================================================
-- 6. Quy trình QUICK_REPORT — CHỐT / MỞ LẠI qua Workflow engine (quy tắc 4)
--
-- ⛔ ⛔ KHÔNG có hàng `__NEW__`: trạng thái khởi tạo = `initial_state`, và
--    `WorkflowEngine` ⛔ tra hàng `__NEW__` nào cho trạng thái ấy (T57.6) ⇒ một
--    hàng khai quyền/thông báo ở đó là TRANG TRÍ. Cổng tạo kỳ là
--    `@RequirePermission("ops:quick-report:manage")` ở controller.
-- ===========================================================================
INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES ('QUICK_REPORT', 'QUICK_REPORT', 'Báo cáo nhanh', 'NHAP',
        'Đang nhập → Đã chốt (văn bản đã gửi) → Mở lại (bắt buộc lý do).');

INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner,
    requires_reason, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, NULL, NULL, FALSE,
       v.requires_reason, v.label, v.sort_order
  FROM workflow_definitions d,
       (VALUES
           ('NHAP',    'CHOT',   'DA_CHOT', 'ops:quick-report:manage', FALSE, 'Chốt kỳ báo cáo', 10),
           ('DA_CHOT', 'MO_LAI', 'NHAP',    'ops:quick-report:reopen', TRUE,  'Mở lại (nêu lý do)', 20)
       ) AS v(from_state, action, to_state, required_permission, requires_reason, label, sort_order)
 WHERE d.entity_type = 'QUICK_REPORT';


-- ===========================================================================
-- KHỐI KIỂM (§10.66) — INSERT…SELECT khớp hụt chèn 0 hàng mà vẫn thoát 0.
-- ===========================================================================
DO $$
DECLARE
    so_xa        INTEGER;
    so_xa_nhue   INTEGER;
    so_buoc      INTEGER;
    so_quyen     INTEGER;
    so_bao_cao   INTEGER;
BEGIN
    SELECT count(*) INTO so_xa FROM don_vi_hanh_chinh WHERE deleted_at IS NULL;
    IF so_xa <> 84 THEN
        RAISE EXCEPTION 'Bảng 5 của mẫu có đúng 84 xã/phường, đang có %', so_xa;
    END IF;

    SELECT count(*) INTO so_xa_nhue FROM don_vi_hanh_chinh
     WHERE deleted_at IS NULL AND cong_ty_thuy_loi = 'SONG_NHUE';
    IF so_xa_nhue <> 20 THEN
        RAISE EXCEPTION 'Mục III của Bảng 5 (Công ty Sông Nhuệ) có đúng 20 xã, đang có %', so_xa_nhue;
    END IF;

    SELECT count(*) INTO so_buoc
      FROM workflow_transitions t JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'QUICK_REPORT';
    IF so_buoc <> 2 THEN
        RAISE EXCEPTION 'Quy trình QUICK_REPORT phải có đúng 2 bước (CHOT, MO_LAI), đang có %', so_buoc;
    END IF;

    SELECT count(*) INTO so_quyen
      FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
      JOIN roles r ON r.id = rp.role_id
     WHERE p.code = 'ops:quick-report:reopen';
    IF so_quyen <> 2 THEN
        RAISE EXCEPTION 'ops:quick-report:reopen chỉ gán SUPER_ADMIN + ADMIN (2 vai trò), đang gán %', so_quyen;
    END IF;

    SELECT count(*) INTO so_bao_cao FROM bao_cao_nhanh;
    IF so_bao_cao <> 0 THEN
        RAISE EXCEPTION 'bao_cao_nhanh phải RỖNG khi giao — cấm seed số liệu vận hành (đang có %)', so_bao_cao;
    END IF;
END $$;
