-- =============================================================================
-- CN-01.4 phần còn lại — WS-36 / T36.1 + T36.2
--
-- Bốn trạng thái sau `DA_DOC` đã nằm trong `ck_contacts_status` **từ lượt tạo
-- bảng** (V202608291041) mà chưa một dòng mã nào ghi vào — chú thích của cột nói
-- thẳng điều đó. Tệp này biến chúng thành đường đi thật, và đường ấy **chỉ có
-- một**: Workflow engine (quy tắc 4).
--
-- ⭐⭐ VÌ SAO `MOI → DA_DOC` CŨNG PHẢI VÀO QUY TRÌNH, DÙ SỔ CHỈ ĐÒI "4 TRANSITION"
--
--   Hôm nay `Contact.danhDauDaDoc()` gán thẳng `this.status`. Luật ArchUnit
--   `SilentFailureRuleTest#chi_workflow_engine_duoc_goi_applyState` ⛔ KHÔNG bắt
--   được nó — luật soi lời gọi `applyState()`, mà đây là một phép gán field bên
--   trong chính entity. Để nguyên là giữ **hai** đường đổi trạng thái trên cùng
--   một cột, một đường có kiểm quyền/thông báo/nhật ký và một đường không, và
--   người đọc mã ⛔ không có cách nào thấy sự bất đối xứng ấy.
--
--   ⇒ Năm bước chuyển, ⛔ không phải bốn. `READ` là bước rẻ nhất và cũng là bước
--     dễ bỏ sót nhất.
--
-- ⛔ KHÔNG seed một dòng `contact_categories` nào
--   Danh sách phân loại là **của Công ty**, và chưa có văn bản nào cấp nó. Seed
--   "Góp ý / Khiếu nại / Hỏi đáp" cho đẹp màn hình là dựng đúng thứ CLAUDE.md
--   cấm: một ô có vẻ đã cấu hình xong trong khi chưa ai quyết. Bảng ra đời rỗng,
--   màn hình nói thẳng là rỗng, và Công ty tự thêm — ⛔ không cần deploy (luật 16).
-- =============================================================================


-- ═════════════════════════════════════════════════════════════════════════════
-- [1] Danh mục phân loại liên hệ — dữ liệu có CRUD, ⛔ không phải enum trong mã
-- ═════════════════════════════════════════════════════════════════════════════
CREATE TABLE contact_categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),

    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    -- Tắt một phân loại ⛔ không được làm mất phân loại đã gán cho liên hệ cũ:
    -- báo cáo theo phân loại phải đọc lại được lịch sử. Vì thế `active` là cờ
    -- hiển thị ở ô chọn, ⛔ không phải điều kiện của khoá ngoại.
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INTEGER      NOT NULL DEFAULT 0,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_contact_categories_public_id UNIQUE (public_id),
    CONSTRAINT ck_contact_categories_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$')
);

-- Mã chỉ duy nhất trong số bản ghi CÒN SỐNG: xoá mềm một phân loại rồi tạo lại
-- cùng mã là chuyện bình thường của người vận hành danh mục.
CREATE UNIQUE INDEX uq_contact_categories_code ON contact_categories (code)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE contact_categories IS
    'Phân loại liên hệ (CN-01.4). RỖNG lúc dựng — danh sách do Công ty tự nhập, luật 16.';


-- ═════════════════════════════════════════════════════════════════════════════
-- [2] Ghi chú nội bộ — bảng riêng, ⛔ không phải một cột TEXT nối chuỗi
--
-- Một cột `internal_note` nghe rẻ hơn, nhưng nó mất hai thứ ngay ở lượt dùng thứ
-- hai: **ai** viết và **khi nào**. Ghi chú nội bộ là thứ người ta đọc lại lúc
-- bàn giao ca hoặc lúc có khiếu nại — đúng lúc cần biết ai đã nói gì.
-- ═════════════════════════════════════════════════════════════════════════════
CREATE TABLE contact_notes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID        NOT NULL DEFAULT gen_random_uuid(),

    contact_id  BIGINT      NOT NULL REFERENCES contacts (id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,

    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT uq_contact_notes_public_id UNIQUE (public_id),
    CONSTRAINT ck_contact_notes_content CHECK (length(btrim(content)) > 0)
);

CREATE INDEX ix_contact_notes_contact ON contact_notes (contact_id, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE contact_notes IS
    'Ghi chú nội bộ của cán bộ xử lý một liên hệ (CN-01.4). ⛔ KHÔNG bao giờ ra cổng công khai.';


-- ═════════════════════════════════════════════════════════════════════════════
-- [3] Ba cột mới trên `contacts`
--
-- ⚠ Cả ba đều phải có **nửa GHI lẫn nửa ĐỌC** trong cùng lượt này (luật 27) —
--   đây đúng loại lỗi lượt 28/8 tìm ra sáu lần trong một buổi.
-- ═════════════════════════════════════════════════════════════════════════════
ALTER TABLE contacts
    ADD COLUMN category_id           BIGINT REFERENCES contact_categories (id),
    ADD COLUMN assigned_org_unit_id  BIGINT REFERENCES org_units (id),
    -- Lý do của bước chuyển, ghi qua `WorkflowReasonAware`. Nội dung trả lời
    -- người dân, hoặc lý do đóng/mở lại — cột này là chỗ DUY NHẤT giữ nó, vì
    -- `audit_logs` có chuỗi băm nên ⛔ không thêm cột được.
    ADD COLUMN resolution_note       TEXT;

CREATE INDEX ix_contacts_category ON contacts (category_id)
    WHERE deleted_at IS NULL AND category_id IS NOT NULL;
CREATE INDEX ix_contacts_assigned_unit ON contacts (assigned_org_unit_id)
    WHERE deleted_at IS NULL AND assigned_org_unit_id IS NOT NULL;

COMMENT ON COLUMN contacts.status IS
    'Sáu trạng thái, đổi DUY NHẤT qua Workflow engine (entity_type = CONTACT). Kể cả MOI → DA_DOC.';
COMMENT ON COLUMN contacts.assigned_org_unit_id IS
    'Phòng ban/Xí nghiệp được chuyển xử lý. NULL = chưa chuyển ai.';
COMMENT ON COLUMN contacts.resolution_note IS
    'Lý do/nội dung phản hồi của bước chuyển gần nhất — ghi bởi WorkflowReasonAware, ⛔ không ra cổng.';


-- ═════════════════════════════════════════════════════════════════════════════
-- [4] Quy trình xử lý liên hệ
--
-- ⭐ `requires_reason = TRUE` ở đúng bốn bước, và cả bốn có cùng một tính chất:
--    **người dân ở đầu kia đang chờ một câu trả lời**, hoặc **quyết định vừa rồi
--    sẽ bị hỏi lại**. Bật lý do cho mọi bước là dạy người dùng gõ "ok" — và khi
--    ô lý do đã thành phản xạ thì bước thật sự cần nó cũng nhận được "ok".
--
-- ⛔ `notify_owner = FALSE` ở TẤT CẢ: `Contact.ownerUserId()` là NULL — người gửi
--    là người dân ngoài hệ thống, ⛔ không có `user_id`. Bật cột này lên là bắn
--    thông báo vào hư không. Thư cho người gửi đi bằng **email** (T36.3), một
--    đường khác hẳn.
-- ═════════════════════════════════════════════════════════════════════════════
INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES ('CONTACT', 'CONTACT', 'Liên hệ từ cổng', 'MOI',
        'Mới → Đã đọc → Đang xử lý → Đã phản hồi → Đóng → Lưu trữ. '
        'Đóng có thể mở lại; Đang xử lý là trạng thái DUY NHẤT cấm xoá.');

INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner,
    requires_reason, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, v.notify_event, v.notify_permission, FALSE,
       v.requires_reason, v.label, v.sort_order
FROM workflow_definitions d,
     (VALUES
         -- Đọc. Bước rẻ nhất, và là bước hôm qua còn đi đường tắt.
         ('MOI', 'READ', 'DA_DOC', 'cms:contact:manage',
          NULL, NULL, FALSE, 'Đánh dấu đã đọc', 10),

         -- Nhận xử lý. Hai đường vào vì cán bộ có thể bấm thẳng từ hộp thư mà
         -- chưa mở chi tiết.
         ('MOI', 'START', 'DANG_XU_LY', 'cms:contact:manage',
          NULL, NULL, FALSE, 'Nhận xử lý', 20),
         ('DA_DOC', 'START', 'DANG_XU_LY', 'cms:contact:manage',
          NULL, NULL, FALSE, 'Nhận xử lý', 21),

         -- ⭐ Đã phản hồi — BẮT BUỘC lý do, và "lý do" ở đây là NỘI DUNG đã trả
         --   lời. Không có nó thì bản ghi chỉ nói "đã trả lời rồi" mà không ai
         --   biết đã trả lời cái gì, đúng lúc người dân gọi lại hỏi tiếp.
         ('DANG_XU_LY', 'REPLY', 'DA_PHAN_HOI', 'cms:contact:manage',
          NULL, NULL, TRUE, 'Ghi nhận đã phản hồi', 30),

         -- Đóng. Từ `DA_DOC` là đường cho lời nhắn không cần xử lý (quảng cáo,
         -- gửi nhầm) — ⛔ ép nó đi qua `DANG_XU_LY` là dạy người dùng bấm hai
         -- nút vô nghĩa, và làm hỏng luôn con số "đang xử lý" trên dashboard.
         ('DA_DOC', 'CLOSE', 'DONG', 'cms:contact:manage',
          NULL, NULL, TRUE, 'Đóng (nêu lý do)', 40),
         ('DANG_XU_LY', 'CLOSE', 'DONG', 'cms:contact:manage',
          NULL, NULL, TRUE, 'Đóng (nêu lý do)', 41),
         ('DA_PHAN_HOI', 'CLOSE', 'DONG', 'cms:contact:manage',
          NULL, NULL, FALSE, 'Đóng', 42),

         -- Mở lại. ⭐ BẮT BUỘC lý do: đây là bước phủ nhận một quyết định đã ghi,
         --   và nó luôn bị hỏi lại.
         ('DONG', 'REOPEN', 'DANG_XU_LY', 'cms:contact:manage',
          NULL, NULL, TRUE, 'Mở lại (nêu lý do)', 50),

         -- Lưu trữ: ẩn khỏi hộp thư, ⛔ không xoá. Trạng thái cuối.
         ('DONG', 'ARCHIVE', 'LUU_TRU', 'cms:contact:manage',
          NULL, NULL, FALSE, 'Lưu trữ', 60)
     ) AS v(from_state, action, to_state, required_permission,
            notify_event, notify_permission, requires_reason, label, sort_order)
WHERE d.entity_type = 'CONTACT';


-- ═════════════════════════════════════════════════════════════════════════════
-- [5] Kiểm ngay trong migration — ⛔ không chờ bài kiểm Java
--
-- Lý do: một `INSERT ... SELECT ... WHERE d.entity_type = 'CONTACT'` khớp hụt
-- chèn **0 hàng** và Flyway vẫn xanh trọn vẹn. Đúng khuôn §10.66: seed ghi vào
-- một khoá chưa tồn tại, 0 hàng, ⛔ không một dòng log.
-- ═════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_buoc     INTEGER;
    so_bat_buoc INTEGER;
BEGIN
    SELECT count(*) INTO so_buoc
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'CONTACT';
    IF so_buoc <> 9 THEN
        RAISE EXCEPTION 'Quy trình CONTACT có % bước chuyển, phải là 9', so_buoc;
    END IF;

    SELECT count(*) INTO so_bat_buoc
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'CONTACT' AND t.requires_reason;
    IF so_bat_buoc <> 4 THEN
        RAISE EXCEPTION 'Phải có đúng 4 bước đòi lý do, đang có %', so_bat_buoc;
    END IF;

    -- Mọi trạng thái trong CHECK của bảng phải tới được, nếu không thì enum và
    -- quy trình lệch nhau và cột CHECK trở thành một lời hứa suông.
    IF EXISTS (
        SELECT 1 FROM unnest(ARRAY['DA_DOC','DANG_XU_LY','DA_PHAN_HOI','DONG','LUU_TRU']) AS s(v)
        WHERE NOT EXISTS (
            SELECT 1 FROM workflow_transitions t
              JOIN workflow_definitions d ON d.id = t.definition_id
             WHERE d.entity_type = 'CONTACT' AND t.to_state = s.v)
    ) THEN
        RAISE EXCEPTION 'Có trạng thái trong ck_contacts_status không có đường nào đi tới';
    END IF;
END $$;
