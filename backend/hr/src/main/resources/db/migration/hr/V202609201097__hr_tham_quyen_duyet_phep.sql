-- ============================================================================
-- WS-79 — AI được duyệt một đơn nghỉ phép (T79.1 → T79.6)
--
--   `V202609141079` mục 3 đã viết ra vấn đề và tin rằng nó đã giải xong:
--
--     *"`required_permission` là một MÃ QUYỀN, còn đặc tả nói một QUAN HỆ
--       (quản lý của đơn vị người nộp). ⇒ Vế còn lại là BỘ LỌC PHẠM VI tầng 3."*
--
--   Đo lại 20/09/2026: bộ lọc phạm vi trả **một nửa** của quan hệ. Nó cắt đúng
--   *đơn vị nào*, và ⛔ nói gì về *AI*. Bốn trạng thái đo được qua HTTP, cả bốn
--   trả 200 và đi trọn quy trình có chữ ký hash chain
--   (`ThamQuyenDuyetPhepHttpTest`, đỏ 4/4 trên mã trước bản này):
--
--     T79.1  bất kỳ ai có `hr:leave:approve` + phạm vi phủ là duyệt được —
--            kể cả người ⛔ giữ chức vụ nào ở đơn vị ấy
--     T79.2  trưởng đơn vị TỰ DUYỆT đơn nghỉ của chính mình
--     T79.3  người vừa duyệt cấp 1 duyệt luôn cấp 2 ⇒ chốt C2 mua một cấp
--            duyệt ⛔ tồn tại
--     T79.4  đồng nghiệp cùng đơn vị RÚT được đơn (kể cả đơn ĐÃ DUYỆT) của
--            người khác — `CANCEL` đòi `hr:leave:request`, quyền chốt C3 cấp
--            cho MỌI CBNV
--
--   ⛔⛔ Điều kiện để sửa được chỉ vừa có từ HÔM QUA. Trước H24 (`T76.1`,
--      20/09) hệ ⛔ có chỗ nào ghi *ai là trưởng đơn vị*: hai cột
--      `org_units.head_user_id`/`deputy_user_id` nằm trong lược đồ từ 13/08 với
--      **0 đường ghi**. Đó chính là lý do `T57.18(a)` bị hoãn và `T76.4` khai
--      *"chặn bởi H24, ⛔ bởi B3"* — thứ tự ấy nay đã trả xong.
--
--   Bản này dựng HAI thứ và sửa MỘT:
--     1. `leave_approval_delegations` — uỷ quyền duyệt CÓ THỜI HẠN (chốt B3)
--     2. quyền `hr:leave:delegate`
--     3. bốn cột vết trên `leave_requests` (ai duyệt cấp 1 · theo uỷ quyền nào)
--
-- ⛔ 0 dòng dữ liệu nghiệp vụ được seed. Khối `DO $$` cuối tệp đếm lại, vì một
--   `INSERT ... SELECT` khớp hụt chèn 0 hàng mà Flyway vẫn xanh (§10.66).
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. `leave_approval_delegations` — uỷ quyền duyệt có thời hạn
--
-- ⛔⛔ VÌ SAO LÀ MỘT BẢNG, ⛔ PHẢI MỘT LƯỢT CẤP QUYỀN
--
--    Cách "rẻ" là ghi `hr:leave:approve` vào vai trò người được uỷ quyền, hoặc
--    cộng nó vào token lúc đăng nhập. Cả hai đều hỏng, và hỏng ở chỗ ⛔ nhìn
--    thấy được:
--
--      • Ghi vào `user_roles` ⇒ quyền là TOÀN CỤC và VĨNH VIỄN. Hết hạn ⛔ ai
--        nhớ gỡ, và người ấy duyệt được mọi đơn trong phạm vi mình mãi mãi —
--        đúng hình dạng `T54.4` (trần cấp quyền).
--      • Cộng vào token ⇒ access token sống 30 phút, nên THU HỒI TRỄ TỚI 30
--        PHÚT; và một lượt uỷ quyền bắt đầu 00:00 ⛔ dùng được cho tới khi
--        người ấy đăng nhập lại. Một cơ chế bảo mật mà *thu hồi* ⛔ tức thì thì
--        ⛔ phải một cơ chế bảo mật.
--      • Và `AuthenticatedUser` CẤM TƯỜNG MINH cả hai: *"record bất biến có chủ
--        đích: ⛔ đoạn mã nghiệp vụ nào được phép thêm quyền cho chính mình giữa
--        chừng. Muốn đổi quyền thì phải đổi ở DB rồi đăng nhập lại."*
--
--    ⇒ Uỷ quyền ⛔ đụng tới tập quyền. Nó CHUYỂN VAI: một hàng ở đây được đọc
--      LẠI TỪ CSDL ở đúng khoảnh khắc người ta bấm nút, y hệt cách bộ lọc phạm
--      vi tầng 3 vẫn làm. Thu hồi có hiệu lực NGAY.
--
-- ⚠ `org_unit_id` là cột phạm vi (`ScopedEntity`): một lượt uỷ quyền thuộc về
--   đơn vị được uỷ quyền, nên trưởng Xí nghiệp khác ⛔ nhìn thấy nó.
-- ---------------------------------------------------------------------------
CREATE TABLE leave_approval_delegations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID        NOT NULL DEFAULT gen_random_uuid(),

    org_unit_id         BIGINT      NOT NULL REFERENCES org_units (id),

    -- Người GIAO. ⛔ NULL: đặc tả B3 nói *"duyệt theo uỷ quyền CỦA X"* — bỏ
    -- trống thì câu ấy ⛔ viết ra được, và nhật ký mất đúng phần mang trách nhiệm.
    delegator_user_id   BIGINT      NOT NULL REFERENCES users (id),
    delegate_user_id    BIGINT      NOT NULL REFERENCES users (id),

    -- Khoảng hiệu lực, so theo NGÀY ở múi giờ Việt Nam (quy tắc 1: cột giờ lưu
    -- UTC, còn *"hôm nay là ngày nào"* là một câu hỏi của người dùng ⇒ ngày lịch).
    from_date           DATE        NOT NULL,
    to_date             DATE        NOT NULL,

    reason              VARCHAR(500),

    -- Thu hồi = xoá MỀM có tên riêng. ⛔ dùng `deleted_at`: *người quản trị xoá
    -- nhầm một bản ghi* và *trưởng đơn vị đi công tác về sớm nên rút uỷ quyền*
    -- là hai sự kiện khác nhau, và chỉ cái thứ hai phải hiện trên lịch sử duyệt.
    revoked_at          timestamptz,
    revoked_by          BIGINT      REFERENCES users (id),

    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          timestamptz,
    updated_by          BIGINT,
    deleted_at          timestamptz,
    version             INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT ck_leave_deleg_dates CHECK (to_date >= from_date),
    -- ⛔ Tự uỷ quyền cho chính mình là một lượt ghi ⛔ nghĩa lý gì, và nó làm
    --   nhật ký *"duyệt theo uỷ quyền của X"* thành một câu tự quy chiếu.
    CONSTRAINT ck_leave_deleg_khac_nguoi CHECK (delegate_user_id <> delegator_user_id),
    -- ⛔ Đã thu hồi thì phải có CẢ người thu lẫn thời điểm — hai chiều, cùng
    --   khuôn `ck_leave_requests_decided_pairs`.
    CONSTRAINT ck_leave_deleg_revoked_pairs CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

CREATE UNIQUE INDEX uq_leave_deleg_public_id ON leave_approval_delegations (public_id);

-- Đường đọc NÓNG NHẤT và là đường duy nhất nằm trên lối bấm nút: *"tài khoản
-- này có đang được uỷ quyền cho đơn vị kia vào hôm nay ⛔"*.
CREATE INDEX ix_leave_deleg_hieu_luc
    ON leave_approval_delegations (delegate_user_id, org_unit_id, from_date, to_date)
    WHERE deleted_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_leave_deleg_don_vi
    ON leave_approval_delegations (org_unit_id, from_date DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE leave_approval_delegations IS
    'Uỷ quyền duyệt nghỉ phép có thời hạn (chốt B3, CN-04.9). KHÔNG cấp quyền: chuyển VAI người duyệt, đọc lại từ DB lúc bấm nút nên thu hồi có hiệu lực ngay.';
COMMENT ON COLUMN leave_approval_delegations.delegator_user_id IS
    'Người GIAO — phải là trưởng/phó của org_unit_id hoặc của một đơn vị cha lúc tạo. Nguồn của câu audit "duyệt theo uỷ quyền của X".';
COMMENT ON COLUMN leave_approval_delegations.revoked_at IS
    'Thu hồi (khác deleted_at): rút uỷ quyền là một sự kiện nghiệp vụ phải hiện trên lịch sử, không phải một lượt xoá nhầm.';


-- ---------------------------------------------------------------------------
-- 2. Quyền `hr:leave:delegate`
--
-- ⛔⛔ Nó gác MỘT màn hình có thật (CRUD uỷ quyền), ⛔ phải một cờ trôi nổi.
--    `RbacMatrixTest.everyCatalogPermissionIsDeclared` đòi mọi mã quyền phải có
--    ít nhất một `@RequirePermission` hoặc một `workflow_transitions` đứng sau —
--    một quyền ⛔ có đầu nhận là một quyền ⛔ bộ canh nào canh (T27.31).
--
-- ⭐ VÀ NÓ MANG VAI THỨ HAI, đã cân nhắc: người giữ quyền này duyệt được đơn của
--    một đơn vị mà CẢ CHUỖI LÃNH ĐẠO (đơn vị ấy + mọi đơn vị cha) ⛔ có trưởng/
--    phó nào đang hoạt động. Lý lẽ: *anh là người có thẩm quyền SẮP XẾP việc
--    duyệt phép* ⇒ anh cũng là người duyệt khi chưa ai được sắp xếp.
--
--    ⛔ Đường dự phòng ấy ⛔ phải *"rơi về luật cũ"*: nó hẹp hơn hẳn (phải giữ
--      thêm một quyền riêng), nó GHI LẠI trên chính đơn (`duyet_du_phong`), và
--      nó ⛔ im lặng. Rơi về luật cũ mới là thứ nguy hiểm — nó khôi phục đúng
--      hành vi rộng vừa bỏ, và cái xanh của bộ canh đọc như đã siết (quy tắc 7).
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, resource, action, name)
VALUES ('hr:leave:delegate', 'hr', 'leave', 'delegate', 'Uỷ quyền duyệt nghỉ phép')
ON CONFLICT (code) DO NOTHING;

-- ⚠ SUPER_ADMIN nhận MỌI quyền bằng một lượt CROSS JOIN ở `V202608131007` — lượt
--   ấy chạy MỘT LẦN, nên quyền sinh sau phải gán tay, ⛔ nó mồ côi và
--   `superAdminHoldsEveryPermission` đỏ. ADMIN nhận tất trừ trường 🔒.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE p.code = 'hr:leave:delegate'
   AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'ADMIN_HR', 'XN_MANAGER')
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- 3. Vết trên `leave_requests` — ai quyết, với tư cách gì
--
-- ⛔⛔ `decided_by` một mình ⛔ TRẢ LỜI ĐƯỢC câu hỏi của B3.
--
--    Đặc tả đòi *"audit ghi **duyệt theo uỷ quyền của X**"*. Một dòng log ⛔ đủ:
--    tranh chấp phép năm nổ ra hàng tháng sau, và thứ người ta mở ra là **cái
--    đơn**, ⛔ phải `audit_logs` (giữ 5 năm, ⛔ ai có quyền đọc). ⇒ Vết đi trên
--    chính hàng đơn, và nó là một KHOÁ NGOẠI chứ ⛔ phải một chuỗi chép lại tên
--    — đổi tên tài khoản ⛔ được làm lịch sử nói sai.
--
-- ⚠ `cap1_by` ⛔ trùng `decided_by`: `decided_by` là người ra quyết định CUỐI
--   (ghi lúc DA_DUYET/TU_CHOI), còn cột này ghi người đã bấm ở cấp 1 — và nó là
--   thứ DUY NHẤT làm cho "cấp 2" khác "cấp 1" (T79.3).
-- ---------------------------------------------------------------------------
ALTER TABLE leave_requests
    ADD COLUMN cap1_by        BIGINT REFERENCES users (id),
    ADD COLUMN cap1_at        timestamptz,
    ADD COLUMN uy_quyen_id    BIGINT REFERENCES leave_approval_delegations (id),
    ADD COLUMN duyet_du_phong BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_leave_requests_cap1_pairs CHECK ((cap1_by IS NULL) = (cap1_at IS NULL));

COMMENT ON COLUMN leave_requests.cap1_by IS
    'Người duyệt CẤP 1 (chốt C2). Thứ duy nhất làm cấp 2 khác cấp 1 — trước T79.3 cùng một người bấm được cả hai.';
COMMENT ON COLUMN leave_requests.uy_quyen_id IS
    'Lượt uỷ quyền mà người duyệt đã dùng (chốt B3). NULL = duyệt với tư cách trưởng/phó của chính mình.';
COMMENT ON COLUMN leave_requests.duyet_du_phong IS
    'TRUE = duyệt bằng đường dự phòng hr:leave:delegate vì cả chuỗi lãnh đạo của đơn vị chưa có trưởng/phó. Một ô hành chính bỏ trống KHÔNG được im lặng.';


-- ---------------------------------------------------------------------------
-- 4. Kiểm NGAY TRONG migration (§10.66) — ⛔ chờ bài kiểm Java
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    so_vai_tro INTEGER;
    so_cot     INTEGER;
BEGIN
    SELECT count(*) INTO so_vai_tro
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'hr:leave:delegate';
    IF so_vai_tro <> 4 THEN
        RAISE EXCEPTION 'hr:leave:delegate phải gán cho đúng 4 vai trò, đang có %', so_vai_tro;
    END IF;

    -- ⛔ Chống tập rỗng: một `ALTER TABLE ... ADD COLUMN` ⛔ hụt được, nhưng một
    --   lượt gộp nhánh CÓ THỂ nuốt mất cả khối. Đếm lại là rẻ.
    SELECT count(*) INTO so_cot
      FROM information_schema.columns
     WHERE table_name = 'leave_requests'
       AND column_name IN ('cap1_by', 'cap1_at', 'uy_quyen_id', 'duyet_du_phong');
    IF so_cot <> 4 THEN
        RAISE EXCEPTION 'leave_requests phải có đủ 4 cột vết thẩm quyền, đang có %', so_cot;
    END IF;
END $$;
