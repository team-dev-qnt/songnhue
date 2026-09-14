-- ============================================================================
-- WS-57 — CN-04.9 NGHỈ PHÉP (SRS M4.10)
--
--   Bản này dựng BA thứ:
--     1. `holidays`        — danh mục ngày lễ do Công ty vận hành (quy tắc 16)
--     2. `leave_requests`  — đơn nghỉ phép, trạng thái đi qua Workflow engine
--     3. quy trình `LEAVE_REQUEST` trong `workflow_definitions/_transitions`
--
-- ⛔⛔ VÀ NÓ ⛔ KHÔNG DỰNG bảng `leave_balances` — dù `implement.md` có nhắc tên.
--
--    Đặc tả chốt thẳng: *"Còn lại = Được hưởng − Đã nghỉ − Đang chờ duyệt
--    (**tính lại từ đơn, ⛔ không cộng trừ tay**)"*. Một bảng số dư là một cột
--    DẪN XUẤT phải giữ đồng bộ bằng tay, và dự án đã trả giá đúng chuyện ấy ở
--    quy tắc 13 (`ConstructionStatusService` trộn hai nguồn khác chiều lọc ⇒
--    trạng thái phụ thuộc *ai bấm F5 sau cùng*). Số dư ở đây SINH từ
--    `leave_requests` mỗi lượt đọc — ⛔ không có gì để lệch.
--
-- ⛔ 0 dòng dữ liệu nghiệp vụ được seed. Thứ DUY NHẤT bản này ghi là **quy
--   trình duyệt** (đó là cấu hình của engine, ⛔ không phải dữ liệu của Công ty)
--   — và khối `DO $$` cuối tệp đếm lại, vì một `INSERT ... SELECT` khớp hụt
--   chèn 0 hàng mà Flyway vẫn xanh trọn vẹn (§10.66).
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. `holidays` — ⛔ KHÔNG tạo mới: bảng ĐÃ CÓ từ `V202608131003` (13/08/2026)
--
-- ⛔⛔ Bản nháp đầu của tệp này viết `CREATE TABLE holidays` và lượt migrate đỏ
--    với `relation "holidays" already exists`. Bảng nằm trong kho **một tháng**
--    với **0 nơi đọc** — cùng họ 15 khoá `settings` nhóm `hr.*`: một mảnh của
--    CN-04.9 được dựng sẵn ở Phase 0 rồi chờ. ⚠ Và phép đo đầu của lượt này ĐÃ
--    IN RA nó; người đọc (tôi) đọc nhầm thành *"chưa có"*. Một phép đo đúng mà
--    đọc sai thì tệ hơn ⛔ không đo — nó mang theo cảm giác đã kiểm.
--
-- Bảng cũ thiếu HAI cột mà mọi entity nghiệp vụ của kho đều có:
--
--   • `public_id` — §4.2 chống IDOR: API ⛔ không bao giờ nhận/trả khoá tự tăng.
--     Thiếu nó thì ⛔ không có cách nào viết một endpoint sửa/xoá hợp lệ.
--   • `deleted_at` — quy tắc 9 (xoá mềm + audit cho mọi entity nghiệp vụ). Một
--     ngày lễ gõ nhầm mà xoá CỨNG là mất luôn dòng nhật ký kiểm toán của nó.
--
-- ⚠⚠ `uq_holidays_date` cũ là chỉ mục duy nhất **TOÀN PHẦN**. Thêm xoá mềm vào
--    mà giữ nguyên nó thì một ngày lễ đã xoá **khoá vĩnh viễn** ngày ấy: người
--    dùng xoá nhầm 30/4 rồi ⛔ không bao giờ thêm lại được, và câu lỗi trỏ vào
--    một hàng ⛔ không hiện trên màn hình nào. ⇒ Dựng lại dạng **từng phần**.
-- ---------------------------------------------------------------------------
ALTER TABLE holidays
    ADD COLUMN public_id  UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN deleted_at timestamptz;

CREATE UNIQUE INDEX uq_holidays_public_id ON holidays (public_id);

DROP INDEX uq_holidays_date;
CREATE UNIQUE INDEX uq_holidays_date ON holidays (holiday_date) WHERE deleted_at IS NULL;

-- Phép đếm ngày công luôn hỏi theo KHOẢNG ngày ⇒ chỉ mục trên chính cột ấy.
CREATE INDEX ix_holidays_date ON holidays (holiday_date) WHERE deleted_at IS NULL;

COMMENT ON TABLE holidays IS
    'Ngày nghỉ lễ (CN-04.9) — danh mục do Công ty vận hành, quy tắc 16. Seed CHỈ ngày dương lịch cố định của Điều 112 BLLĐ; Tết/Giỗ Tổ phải nhập tay.';


-- ---------------------------------------------------------------------------
-- 2. `leave_requests` — đơn nghỉ phép
-- ---------------------------------------------------------------------------
CREATE TABLE leave_requests (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT gen_random_uuid(),

    employee_id     BIGINT       NOT NULL REFERENCES employees (id),

    -- ⛔⛔ Cột phạm vi — `ScopedEntity`. Nó là BẢN SAO đơn vị của nhân viên tại
    --    thời điểm nộp đơn, ⛔ không phải một khoá ngoại "sống".
    --
    --    Vì sao sao chép thay vì join sang `employees.org_unit_id`: bộ lọc phạm
    --    vi tầng 3 chạy bằng `@Filter` trên **chính bảng này**, và một người
    --    chuyển đơn vị giữa chừng ⛔ không được làm đơn cũ nhảy sang hộp duyệt
    --    của trưởng đơn vị mới — người đã duyệt là người cũ, và nhật ký phải
    --    khớp với ai thật sự quyết định.
    org_unit_id     BIGINT       NOT NULL REFERENCES org_units (id),

    leave_type      VARCHAR(30)  NOT NULL,
    from_date       DATE         NOT NULL,
    to_date         DATE         NOT NULL,

    -- ⛔⛔ Số ngày công GHI XUỐNG, ⛔ không tính lại lúc đọc — và đây là ngoại
    --    lệ CÓ CHỦ ĐÍCH với quy tắc 3 (*mọi giá trị tính toán tính ở BE*).
    --
    --    Quy tắc 3 nói *tính ở BE thay vì FE*; nó ⛔ không nói *phải tính lại mỗi
    --    lượt đọc*. Ở đây con số là một **sự thật lịch sử tại thời điểm quyết
    --    định**: Công ty thêm một ngày lễ vào tháng sau thì đơn đã duyệt tháng
    --    trước **⛔ không được** đổi số ngày — người lao động đã nghỉ đúng ngần
    --    ấy ngày và số dư đã trừ đúng ngần ấy.
    --
    --    ⚠ Phân biệt với quy tắc 13 (cột dẫn xuất trộn hai nguồn ⇒ phụ thuộc ai
    --      bấm F5 sau cùng): cột ấy mô tả TRẠNG THÁI HIỆN TẠI nên phải sinh;
    --      cột này ghi một QUYẾT ĐỊNH ĐÃ XẢY RA nên phải đóng băng.
    working_days    NUMERIC(5,1) NOT NULL,

    reason          VARCHAR(1000),

    -- ⭐ Chốt C3: *"nhân viên ⛔ không dùng máy tính → quản lý đơn vị tạo đơn hộ
    --   (lưu trường 'người tạo hộ', ghi audit)"*. NULL = tự người ấy nộp.
    --   ⛔ Cột này ⛔ không thay `created_by` của `BaseEntity`: `created_by` luôn
    --   là người bấm nút, còn cột này khai **rằng** lượt bấm ấy là nộp hộ — hai
    --   sự thật khác nhau, và gộp chúng thì ⛔ không phân biệt được "tự nộp" với
    --   "nộp hộ" nữa.
    created_for_by  BIGINT       REFERENCES users (id),

    -- ⛔⛔ Tài khoản NHẬN THÔNG BÁO về đơn này — `WorkflowAware.ownerUserId()`.
    --
    --    Vì sao phải là một CỘT chứ ⛔ không tra lúc chạy: `ownerUserId()` là một
    --    phương thức trên **entity**, nó ⛔ không có repository để đi từ
    --    `employee_id` sang `users.employee_id`. Thiếu cột này thì
    --    `notify_owner = TRUE` bắn thông báo vào **hư không** — đúng lỗi mà
    --    `CONTACT` đã phải TẮT cột ấy để tránh, chỉ là ở đây ta sửa được.
    --
    --    ⚠ NULL là một trạng thái HỢP LỆ và có thật: chốt C3 nói *"nhân viên ⛔
    --      không dùng máy tính → quản lý đơn vị tạo đơn hộ"*. Khi ấy người nhận
    --      thông báo rơi về `created_for_by` (xem `LeaveRequest.ownerUserId()`)
    --      — người duy nhất thật sự đọc được nó.
    requester_user_id BIGINT     REFERENCES users (id),

    state           VARCHAR(30)  NOT NULL,
    decided_by      BIGINT       REFERENCES users (id),
    decided_at      timestamptz,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_leave_requests_type CHECK (
        leave_type IN ('PHEP_NAM', 'THAI_SAN', 'CUOI', 'TANG', 'KHAM_SUC_KHOE')
    ),
    CONSTRAINT ck_leave_requests_state CHECK (
        state IN ('CHO_DUYET', 'CHO_DUYET_2', 'DA_DUYET', 'TU_CHOI', 'DA_HUY')
    ),
    -- ⛔ Ngày kết thúc ⛔ không được trước ngày bắt đầu. Ở CSDL chứ ⛔ không chỉ ở
    --   biểu mẫu: một đơn ngược ngày làm phép đếm ra số ÂM và trừ ngược vào số
    --   dư — tức cộng thêm phép cho người nộp.
    CONSTRAINT ck_leave_requests_dates CHECK (to_date >= from_date),
    -- ⛔ Số ngày công phải dương. 0 ngày là một đơn ⛔ không nghĩa lý gì (mọi ngày
    --   trong khoảng đều là cuối tuần hoặc lễ) và phải bị chặn ở tầng service
    --   kèm mã lỗi đọc được — ràng buộc này là lưới cuối.
    CONSTRAINT ck_leave_requests_working_days CHECK (working_days > 0),
    -- ⛔ Đã quyết thì phải có CẢ người quyết lẫn thời điểm — hai chiều.
    CONSTRAINT ck_leave_requests_decided_pairs CHECK (
        (decided_by IS NULL) = (decided_at IS NULL)
    ),
    -- ⛔⛔ Phải có ÍT NHẤT một người nhận thông báo. Cả hai cùng NULL nghĩa là
    --   một đơn mà ⛔ không ai được báo khi nó được duyệt hay bị từ chối — người
    --   lao động nộp đơn rồi ⛔ không bao giờ biết kết quả. Ràng buộc ở CSDL vì
    --   đây là thứ một đường ghi mới rất dễ quên (luật 12).
    CONSTRAINT ck_leave_requests_co_nguoi_nhan CHECK (
        requester_user_id IS NOT NULL OR created_for_by IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_leave_requests_public_id ON leave_requests (public_id);

-- Hai đường đọc nóng nhất: "đơn của tôi" và "hộp chờ duyệt của đơn vị".
CREATE INDEX ix_leave_requests_employee
    ON leave_requests (employee_id, from_date DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_leave_requests_cho_duyet
    ON leave_requests (org_unit_id, state)
    WHERE deleted_at IS NULL AND state IN ('CHO_DUYET', 'CHO_DUYET_2');

-- Phép tính số dư quét theo NĂM của `from_date`; phép cảnh báo trùng lịch quét
-- theo khoảng ngày trong một đơn vị.
CREATE INDEX ix_leave_requests_khoang
    ON leave_requests (org_unit_id, from_date, to_date)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE leave_requests IS
    'Đơn nghỉ phép (CN-04.9). Trạng thái đổi DUY NHẤT qua Workflow engine (entity_type = LEAVE_REQUEST).';
COMMENT ON COLUMN leave_requests.working_days IS
    'Số ngày công ĐÃ ĐẾM lúc nộp (trừ cuối tuần + ngày lễ). ĐÓNG BĂNG: thêm ngày lễ sau đó KHÔNG đổi đơn cũ.';
COMMENT ON COLUMN leave_requests.requester_user_id IS
    'Tài khoản nhận thông báo (WorkflowAware.ownerUserId). NULL khi CBNV chưa liên kết tài khoản — chốt C3.';
COMMENT ON COLUMN leave_requests.created_for_by IS
    'Người tạo đơn HỘ (chốt C3). NULL = chính nhân viên nộp. Khác created_by: created_by luôn là người bấm nút.';


-- ---------------------------------------------------------------------------
-- 3. Quy trình duyệt `LEAVE_REQUEST`
--
-- ⛔⛔ MỘT ĐIỀU MÀ BẢNG NÀY ⛔ KHÔNG DIỄN ĐẠT ĐƯỢC, và phải nói ra:
--
--    `required_permission` là một **mã quyền**, còn đặc tả nói *"**Quản lý** đơn
--    vị duyệt"* — một **quan hệ** giữa người duyệt và đơn vị của người nộp.
--    `hr:leave:approve` một mình sẽ cho một trưởng Xí nghiệp 3 duyệt đơn của Xí
--    nghiệp 5.
--
--    ⇒ Vế còn lại là **bộ lọc phạm vi tầng 3** trên `leave_requests.org_unit_id`
--      (`ScopedEntity`): người duyệt chỉ **nhìn thấy** đơn trong phạm vi của
--      mình, nên ⛔ không có gì để bấm. Hai cơ chế, hai vế của một bảo đảm —
--      cùng hình dạng CN-04.7 (*"chính nhân viên đó"* cũng ⛔ không biểu diễn
--      được bằng một mã quyền). Bài `khongDuyetDuocDonNgoaiDonVi` canh vế ấy.
--
-- ⚠ `notify_owner = TRUE` ở APPROVE/REJECT: `LeaveRequest.ownerUserId()` trả tài
--   khoản của người nộp — khác hẳn `CONTACT`, nơi người gửi là người dân ⛔ không
--   có `user_id` nên cột ấy phải TẮT.
-- ---------------------------------------------------------------------------
INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES ('LEAVE_REQUEST', 'LEAVE_REQUEST', 'Đơn nghỉ phép', 'CHO_DUYET',
        'Chờ duyệt → (Chờ duyệt cấp 2) → Đã duyệt / Từ chối / Đã huỷ. '
        'Cấp 2 chỉ bật khi hr.leave.second-level-threshold-days > 0 và đơn đủ dài.');

INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner,
    requires_reason, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, v.notify_event, v.notify_permission, v.notify_owner,
       v.requires_reason, v.label, v.sort_order
FROM workflow_definitions d,
     (VALUES
         -- ⭐ Đường VÀO ĐỜI.
         --
         -- ⛔⛔ HAI CỘT `notify_*` Ở HÀNG NÀY PHẢI LÀ NULL, và lý do đáng ghi lại.
         --
         --    Bản đầu của tệp này khai `notify_event = 'LEAVE_SUBMITTED'` kèm
         --    `notify_permission = 'hr:leave:approve'`, và javadoc của
         --    `DonNghiPhepService.nop()` khẳng định hàng này là *"chốt chặn thật
         --    của đường vào đời"*. **Cả hai đều sai**, và bài kiểm HTTP
         --    `nopDonThiNguoiDuyetNhanDuocThongBao` đo ra điều đó ở lượt chạy
         --    ĐẦU: người duyệt nhận **0** thông báo sau một lượt nộp thành công.
         --
         --    Nguyên nhân: `WorkflowEngine.resolveInitialState` trả về NGAY khi
         --    trạng thái xin bằng `workflow_definitions.initial_state` — nó ⛔
         --    không tra hàng `__NEW__` nào, ⛔ không kiểm quyền, và ⛔ không chạy
         --    `notifyAfterTransition`. Hàng `__NEW__` chỉ có hiệu lực cho các
         --    đường vào đời **KHÁC** mặc định (ví dụ `ops`: nhập thẳng một công
         --    việc *đã hoàn thành*). ⇒ Hai hàng `__NEW__` duy nhất có trước
         --    trong kho (`V202608211028`) đều để `notify_event = NULL` — đó là
         --    tiền lệ, và lượt này suýt dựng một tiền lệ thứ hai mâu thuẫn.
         --
         --    ⚠ Một cột khai một thông báo ⛔ KHÔNG BAO GIỜ sinh ra nguy hiểm hơn
         --      một cột để trống: nó làm lượt rà sau đọc thấy *"đã có chuông"*.
         --      Thông báo cho người duyệt là một yêu cầu THẬT, nên nó được phát
         --      tường minh ở `DonNghiPhepService.nop()` — xem lớp ấy.
         ('__NEW__', 'SUBMIT', 'CHO_DUYET', 'hr:leave:request',
          NULL, NULL, FALSE, FALSE, 'Gửi đơn', 10),

         -- Duyệt một cấp — đường mặc định (chốt C2: `approval-levels = 1`).
         ('CHO_DUYET', 'APPROVE', 'DA_DUYET', 'hr:leave:approve',
          'LEAVE_APPROVED', NULL, TRUE, FALSE, 'Duyệt', 20),

         -- ⭐ Chuyển cấp 2. Service quyết ĐI ĐƯỜNG NÀO dựa trên
         --   `hr.leave.second-level-threshold-days`; engine chỉ khai rằng cả hai
         --   đường đều hợp lệ từ `CHO_DUYET`. ⛔ Bảng bước chuyển ⛔ không diễn
         --   đạt được điều kiện "nếu số ngày ≥ N" — và ép nó làm việc ấy là dựng
         --   một ngôn ngữ luật trong một bảng SQL.
         ('CHO_DUYET', 'ESCALATE', 'CHO_DUYET_2', 'hr:leave:approve',
          'LEAVE_ESCALATED', 'hr:leave:approve', FALSE, FALSE, 'Duyệt cấp 1, chuyển cấp 2', 21),
         ('CHO_DUYET_2', 'APPROVE', 'DA_DUYET', 'hr:leave:approve',
          'LEAVE_APPROVED', NULL, TRUE, FALSE, 'Duyệt cấp 2', 22),

         -- ⭐ Từ chối BẮT BUỘC lý do ở cả hai cấp: đây là bước phủ nhận một yêu
         --   cầu của người lao động, và nó luôn bị hỏi lại.
         ('CHO_DUYET', 'REJECT', 'TU_CHOI', 'hr:leave:approve',
          'LEAVE_REJECTED', NULL, TRUE, TRUE, 'Từ chối (nêu lý do)', 30),
         ('CHO_DUYET_2', 'REJECT', 'TU_CHOI', 'hr:leave:approve',
          'LEAVE_REJECTED', NULL, TRUE, TRUE, 'Từ chối (nêu lý do)', 31),

         -- Người nộp tự rút đơn khi chưa ai quyết — ⛔ không cần lý do.
         ('CHO_DUYET', 'CANCEL', 'DA_HUY', 'hr:leave:request',
          'LEAVE_CANCELLED', 'hr:leave:approve', FALSE, FALSE, 'Rút đơn', 40),
         ('CHO_DUYET_2', 'CANCEL', 'DA_HUY', 'hr:leave:request',
          'LEAVE_CANCELLED', 'hr:leave:approve', FALSE, FALSE, 'Rút đơn', 41),

         -- ⭐ Huỷ một đơn ĐÃ DUYỆT — BẮT BUỘC lý do, vì nó phủ nhận một quyết
         --   định đã ghi và **hoàn lại số dư** cho người nộp. Service còn chặn
         --   thêm: ⛔ không huỷ được đơn đã bắt đầu nghỉ (ngày ấy đã trôi qua rồi,
         --   hoàn phép cho nó là bịa ra một ngày công ⛔ không ai làm).
         ('DA_DUYET', 'CANCEL', 'DA_HUY', 'hr:leave:request',
          'LEAVE_CANCELLED', 'hr:leave:approve', TRUE, TRUE, 'Huỷ đơn đã duyệt (nêu lý do)', 50)
     ) AS v(from_state, action, to_state, required_permission,
            notify_event, notify_permission, notify_owner, requires_reason, label, sort_order)
WHERE d.entity_type = 'LEAVE_REQUEST';


-- ---------------------------------------------------------------------------
-- 4. Kiểm NGAY TRONG migration — ⛔ không chờ bài kiểm Java (§10.66)
--
-- Một `INSERT ... SELECT ... WHERE d.entity_type = '…'` khớp hụt chèn **0 hàng**
-- và Flyway vẫn xanh trọn vẹn. Khối này biến chuyện đó thành một lượt deploy đỏ.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    so_buoc        INTEGER;
    so_trang_thai  INTEGER;
    so_ngay_le     INTEGER;
BEGIN
    SELECT count(*) INTO so_buoc
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'LEAVE_REQUEST';
    IF so_buoc <> 9 THEN
        RAISE EXCEPTION 'Quy trình LEAVE_REQUEST phải có đúng 9 bước chuyển, đang có %', so_buoc;
    END IF;

    -- ⛔ Mọi trạng thái trong CHECK phải TỚI ĐƯỢC. Một trạng thái khai ở ràng
    --   buộc mà ⛔ không bước nào dẫn tới là một nhánh chết — và nó đọc y hệt một
    --   tính năng đã dựng.
    SELECT count(DISTINCT t.to_state) INTO so_trang_thai
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'LEAVE_REQUEST';
    IF so_trang_thai <> 5 THEN
        RAISE EXCEPTION 'Phải tới được đúng 5 trạng thái, đang tới được %', so_trang_thai;
    END IF;

    -- ⛔⛔ CHỐNG TẬP RỖNG, ⛔ không phải chống seed.
    --
    --   Bản nháp đầu khẳng định *"holidays phải RỖNG"* — và **sai**. Seed của
    --   `V202608131008` có 8 hàng, và đó là seed ĐÚNG: chỉ những ngày lễ có
    --   **ngày dương lịch cố định** do Điều 112 BLLĐ 2019 ấn định (1/1 · 30/4 ·
    --   1/5 · 2/9 cho 2026 và 2027). Chú thích ngay trên khối seed ấy nói rõ vì
    --   sao Tết Nguyên đán và Giỗ Tổ **⛔ không** được seed: âm lịch, mỗi năm
    --   Chính phủ công bố khác.
    --
    --   ⇒ Đó ⛔ không phải *"seed cho đẹp demo"* mà CLAUDE.md cấm — nó là **luật**,
    --     y hệt lý do `EducationLevel` là enum chứ ⛔ không phải bảng CRUD.
    --     Khẳng định đúng ở đây là **chống tập rỗng**: một lượt dọn dẹp nào đó
    --     xoá mất seed pháp định thì mọi phép đếm ngày phép sai từ ngày ấy.
    SELECT count(*) INTO so_ngay_le FROM holidays WHERE deleted_at IS NULL;
    IF so_ngay_le < 8 THEN
        RAISE EXCEPTION 'Seed ngày lễ pháp định (Điều 112 BLLĐ) phải còn ≥ 8 hàng, đang có %', so_ngay_le;
    END IF;
END $$;
