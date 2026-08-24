-- =============================================================================
-- WS-18 / T18.1 + T18.3 + T18.5 + T18.9 + T18.10 — Lịch sử sửa chữa / bảo trì /
-- khắc phục sự cố (CN-02.2)
--
-- ⛔ QUY TẮC 15 CỦA DỰ ÁN: SỰ CỐ KHÔNG PHẢI ENTITY RIÊNG.
--
-- Không bảng `incidents`, không mã `SC-`, không vòng đời bảy trạng thái. Chốt G1
-- (PA A, 12/8/2026): một sự cố là một dòng của bảng này với
-- `work_type = 'KHAC_PHUC_SU_CO'` cộng thêm `severity`. Lý do không phải là tiết
-- kiệm một bảng, mà là: hai bảng gần giống nhau thì sẽ có hai màn hình, hai bộ
-- lọc, hai công thức tính tổng chi phí — và tới lúc lập BC-09 sẽ có hai con số
-- cùng tên gọi "chi phí sửa chữa trong kỳ".
--
-- Đây cũng là **chức năng ghi nhận hoạt động duy nhất** của MOD-02 sau khi Nhật
-- ký vận hành (B1/F1) và Phiếu sự cố riêng (G1) bị loại khỏi phạm vi.
-- =============================================================================

CREATE TABLE maintenance_logs (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id             UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- `BT-<năm>-xxxx`, sinh qua `code_sequences` của Core (T18.5). Không dùng
    -- MAX(mã)+1 — xem CodeGenerator.
    code                  VARCHAR(30)  NOT NULL,

    construction_id       BIGINT       NOT NULL REFERENCES constructions (id),

    -- === Phạm vi đơn vị — tầng 3 phân quyền ================================
    --
    -- ⚠ SAO CHÉP từ công trình lúc TẠO, không phải khoá ngoại đọc xuyên bảng
    --   (T18.2). Hệ quả phải nói ra để không bị coi là lỗi: công trình đổi đơn vị
    --   phụ trách thì các bản ghi CŨ giữ nguyên đơn vị lúc phát sinh. Đó là điều
    --   đúng cho một hồ sơ lịch sử — chi phí sửa chữa năm ngoái thuộc về Xí
    --   nghiệp đã bỏ tiền ra, không thuộc về Xí nghiệp mới nhận bàn giao.
    org_unit_id           BIGINT       NOT NULL REFERENCES org_units (id),

    -- === Phân loại ==========================================================
    work_type             VARCHAR(30)  NOT NULL,
    -- Chỉ có nghĩa với loại "Khắc phục sự cố" — xem CHECK bên dưới.
    severity              VARCHAR(20),
    -- Trạng thái xử lý. ⛔ Chỉ WorkflowEngine ghi (quy tắc 4). Không cột nào ở
    -- bảng này được UPDATE thẳng từ service.
    status                VARCHAR(20)  NOT NULL DEFAULT 'MOI',

    -- === Nội dung ===========================================================
    started_on            DATE         NOT NULL,
    completed_on          DATE,
    content               TEXT         NOT NULL,
    -- "Tổ máy số 3", "Cánh van khoang 2" — để văn bản, vì danh mục thiết bị của
    -- Công ty chưa được số hoá và ép chọn từ danh mục rỗng thì không ai nhập được.
    item_or_equipment     VARCHAR(255),

    -- === Đơn vị thực hiện — điểm nghiệp vụ 17 ===============================
    --
    -- HAI cột, đúng một cột có giá trị. Spec ghi "Text / FK" và cách đọc thứ ba
    -- — một cột lưu cả tên nhà thầu lẫn id đơn vị nội bộ — là bảo đảm sẽ có dữ
    -- liệu bẩn: tới lúc lọc "công việc do nội bộ làm" sẽ phải đoán bằng cách xem
    -- chuỗi có phải số không.
    performer_org_unit_id BIGINT       REFERENCES org_units (id),
    performer_name        VARCHAR(255),

    -- === Chi phí ============================================================
    -- Đơn vị VND, NUMERIC (điểm nghiệp vụ 18 + quy tắc 2). Cấm float: BC-09 cộng
    -- dồn chi phí theo kỳ, và sai số dấu phẩy động cộng dồn thì không ai đối
    -- chiếu được với chứng từ.
    cost                  NUMERIC(18,2),
    funding_source        VARCHAR(255),

    -- === Nghiệm thu =========================================================
    acceptance_result     VARCHAR(20),
    acceptance_note       TEXT,

    -- Người phụ trách — mặc định là người nhập, cho đổi. Đây là người nhận thông
    -- báo khi bản ghi chuyển trạng thái (WorkflowAware.ownerUserId).
    assignee_user_id      BIGINT       NOT NULL REFERENCES users (id),

    -- === Cảnh báo ngưỡng liên quan — điểm nghiệp vụ 16 ======================
    --
    -- ⛔ KHÔNG khoá ngoại. `alert_events` thuộc module `hydro`; đặt FK ở đây là
    --    trói hai module lại với nhau ở tầng CSDL — đúng thứ mà ranh giới module
    --    sinh ra để tránh, và là thứ sẽ chặn việc tách `hydro` ra CSDL riêng nếu
    --    có ngày cần. Tra ngược đi qua `hydro.spi`.
    --
    -- ⚠ Ở Phase 1 cột này CHƯA có đường điền: nút "Tạo bản ghi khắc phục" trên
    --   màn hình cảnh báo là việc của Phase 2 (nợ #59). Ở đây chỉ chừa cột và
    --   một tham số API đã nhận sẵn, để Phase 2 không phải đổi lược đồ.
    alert_event_public_id UUID,

    created_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            BIGINT,
    updated_at            timestamptz,
    updated_by            BIGINT,
    deleted_at            timestamptz,
    version               INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_maintenance_logs_work_type CHECK (
        work_type IN ('SUA_CHUA', 'BAO_TRI_DINH_KY', 'NANG_CAP', 'THAY_THE_THIET_BI', 'KHAC_PHUC_SU_CO')
    ),
    CONSTRAINT ck_maintenance_logs_status CHECK (
        status IN ('MOI', 'DANG_XU_LY', 'DA_XU_LY')
    ),
    CONSTRAINT ck_maintenance_logs_acceptance CHECK (
        acceptance_result IS NULL OR acceptance_result IN ('DAT', 'CHUA_DAT', 'DANG_THEO_DOI')
    ),

    -- Mức độ đi CÙNG CHIỀU với loại sự cố, cả hai phía:
    --   · sự cố mà không có mức độ → không xếp được thứ tự ưu tiên xử lý (OPS-2003)
    --   · bảo trì định kỳ mà mang mức độ "Nghiêm trọng" → danh sách sự cố đếm nhầm
    -- Ràng buộc hai chiều nên không có tổ hợp nào lọt.
    CONSTRAINT ck_maintenance_logs_severity CHECK (
        (work_type = 'KHAC_PHUC_SU_CO') = (severity IS NOT NULL)
        AND (severity IS NULL OR severity IN ('NGHIEM_TRONG', 'CAO', 'TRUNG_BINH', 'THAP'))
    ),

    -- Đúng MỘT trong hai cột đơn vị thực hiện — điểm nghiệp vụ 17.
    CONSTRAINT ck_maintenance_logs_performer CHECK (
        (performer_org_unit_id IS NULL) <> (performer_name IS NULL)
    ),

    CONSTRAINT ck_maintenance_logs_dates CHECK (
        completed_on IS NULL OR completed_on >= started_on
    ),
    -- "Đã xử lý" mà không có ngày hoàn thành thì BC-09 không xếp được vào kỳ nào
    -- (OPS-2004). Ép ở CSDL vì đây là chỗ mọi đường ghi đều đi qua — quy tắc
    -- "đặt bảo đảm ở chỗ dữ liệu đi qua, đừng đặt ở nơi gọi".
    CONSTRAINT ck_maintenance_logs_completed_when_done CHECK (
        status <> 'DA_XU_LY' OR completed_on IS NOT NULL
    ),
    CONSTRAINT ck_maintenance_logs_cost_nonneg CHECK (cost IS NULL OR cost >= 0)
);

CREATE UNIQUE INDEX ux_maintenance_logs_public_id ON maintenance_logs (public_id);
CREATE UNIQUE INDEX ux_maintenance_logs_code ON maintenance_logs (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_maintenance_logs_org_unit ON maintenance_logs (org_unit_id) WHERE deleted_at IS NULL;

-- Timeline theo công trình, mới nhất trước (T18.7).
CREATE INDEX ix_maintenance_logs_timeline ON maintenance_logs (construction_id, started_on DESC)
    WHERE deleted_at IS NULL;

-- ⭐ Chỉ mục phục vụ ĐÚNG câu hỏi của chuỗi suy ra trạng thái công trình:
-- "công trình này còn bản ghi nào đang mở không, và loại gì". Câu đó chạy ở mọi
-- lượt tính lại trạng thái, tức là ở mọi lượt tạo/sửa bản ghi và mọi lượt sửa hồ
-- sơ công trình — nên nó phải rẻ.
CREATE INDEX ix_maintenance_logs_open ON maintenance_logs (construction_id, work_type)
    WHERE deleted_at IS NULL AND status IN ('MOI', 'DANG_XU_LY');

-- Danh sách "Sự cố chưa xử lý" toàn hệ thống cho dashboard (T18.8).
CREATE INDEX ix_maintenance_logs_open_incidents ON maintenance_logs (started_on DESC)
    WHERE deleted_at IS NULL AND work_type = 'KHAC_PHUC_SU_CO' AND status IN ('MOI', 'DANG_XU_LY');

COMMENT ON TABLE maintenance_logs IS
    'CN-02.2 — sửa chữa/bảo trì VÀ sự cố trong cùng một bảng (chốt G1). '
    'Không có bảng incidents, không có mã SC-.';
COMMENT ON COLUMN maintenance_logs.org_unit_id IS
    'Sao chép từ công trình lúc TẠO. Công trình đổi đơn vị thì bản ghi cũ giữ nguyên — hồ sơ lịch sử.';
COMMENT ON COLUMN maintenance_logs.status IS
    'Chỉ WorkflowEngine ghi (quy tắc 4). Hai quy trình: MAINTENANCE_LOG và MAINTENANCE_INCIDENT.';
COMMENT ON COLUMN maintenance_logs.alert_event_public_id IS
    'UUID cảnh báo ngưỡng của module hydro — CỐ Ý không có FK (điểm nghiệp vụ 16).';

-- =============================================================================
-- Quy trình xử lý — HAI định nghĩa trên cùng MỘT bảng
--
-- ⭐ VÌ SAO HAI, KHÔNG PHẢI MỘT.
--
-- Ma trận §6 tách hai dòng khác nhau, và chúng khác nhau ở đúng cột "Kỹ thuật":
--
--   · Ghi lịch sử sửa chữa/bảo trì  → Admin ✔ · QL XN ✔ · Kỹ thuật ✔ · Vận hành ✘
--   · Đóng bản ghi sự cố ("Đã xử lý") → Admin ✔ · QL XN ✔ · Kỹ thuật ✘ · Vận hành ✘
--
-- Tức là "chuyển sang Đã xử lý" đòi quyền KHÁC NHAU tuỳ bản ghi là sự cố hay
-- không. `workflow_transitions.required_permission` gắn theo (from_state, action)
-- chứ không theo loại công việc, nên một quy trình duy nhất không diễn đạt được
-- điều này — muốn thi hành thì phải viết một câu `if` trong service.
--
-- Mà `ops:maintenance:close-incident` ĐÃ được seed từ WS-2. Không dùng tới thì
-- nó là một quyền chưa ai đọc — đúng loại lỗi mà dự án đã trả giá ba lần
-- (`limits.upload.max-mb.*`, `company.*`, `attachments.valid_from`).
--
-- Hai định nghĩa thì tách vai trò nằm ở DỮ LIỆU, giống hệt cách quy trình bài
-- viết ngăn Biên tập viên tự duyệt bài mình. `MaintenanceLog.workflowEntityType()`
-- trả về tên quy trình theo `work_type` — cùng một bảng, cùng một entity, cùng
-- ba trạng thái, khác nhau ở ai được bấm nút nào.
--
-- ⛔ Cả hai quy trình đều KHÔNG có đường tự tạo thẳng ở `DANG_XU_LY`. "Đang xử
--    lý" là điều xảy ra SAU khi có người tiếp nhận; khai nó lúc tạo là ghi vào
--    lịch sử một bước chưa từng có ai làm.
-- =============================================================================

INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES
    ('MAINTENANCE_LOG', 'MAINTENANCE_LOG', 'Công việc sửa chữa / bảo trì', 'MOI',
     'Mới → Đang xử lý → Đã xử lý. Nhập sau khi đã làm xong thì vào thẳng Đã xử lý.'),
    ('MAINTENANCE_INCIDENT', 'MAINTENANCE_INCIDENT', 'Khắc phục sự cố', 'MOI',
     'Cùng bảng maintenance_logs, khác ở chỗ đóng bản ghi đòi ops:maintenance:close-incident.');

-- -----------------------------------------------------------------------------
-- Công việc sửa chữa / bảo trì
-- -----------------------------------------------------------------------------
INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, v.notify_event, v.notify_permission, v.notify_owner, v.label, v.sort_order
FROM workflow_definitions d,
     (VALUES
         -- ⭐ Đường vào đời thứ hai — điểm nghiệp vụ 15. Spec: "mặc định Đã xử lý
         --    với công việc nhập sau khi hoàn thành". Khai bằng một dòng `__NEW__`
         --    thay vì tạo ở MOI rồi chạy hai transition giả: nhật ký kiểm toán có
         --    chuỗi băm, bịa một bước chuyển là ký tên vào một lịch sử chưa xảy ra.
         ('__NEW__', 'LOG_COMPLETED', 'DA_XU_LY',
          'ops:maintenance:create', NULL, NULL, FALSE,
          'Nhập công việc đã hoàn thành', 5),

         ('MOI', 'START', 'DANG_XU_LY',
          'ops:maintenance:create', 'MAINTENANCE_STARTED', NULL, TRUE,
          'Bắt đầu thực hiện', 10),

         -- Đóng bản ghi = sửa một bản ghi đã lưu → ma trận §6 xếp cho Admin + QL XN.
         ('MOI', 'COMPLETE', 'DA_XU_LY',
          'ops:maintenance:update', 'MAINTENANCE_COMPLETED', NULL, TRUE,
          'Ghi nhận hoàn thành', 11),
         ('DANG_XU_LY', 'COMPLETE', 'DA_XU_LY',
          'ops:maintenance:update', 'MAINTENANCE_COMPLETED', NULL, TRUE,
          'Ghi nhận hoàn thành', 20),

         -- Nghiệm thu "Chưa đạt" thì phải làm lại — không có đường này thì người
         -- dùng sẽ tạo một bản ghi thứ hai cho cùng một công việc, và chi phí kỳ
         -- đó bị đếm hai lần.
         ('DA_XU_LY', 'REOPEN', 'DANG_XU_LY',
          'ops:maintenance:update', 'MAINTENANCE_REOPENED', NULL, TRUE,
          'Mở lại — nghiệm thu chưa đạt', 30)
     ) AS v(from_state, action, to_state,
            required_permission, notify_event, notify_permission, notify_owner, label, sort_order)
WHERE d.entity_type = 'MAINTENANCE_LOG';

-- -----------------------------------------------------------------------------
-- Khắc phục sự cố
--
-- Khác quy trình trên đúng ở hai chỗ, và cả hai đều là điều ma trận §6 đòi:
--   · đóng bản ghi đòi `close-incident` thay vì `update`
--   · tạo thẳng ở "Đã xử lý" cũng đòi `close-incident` — nếu chỉ đòi `create`
--     thì tuyên bố "sự cố đã xong" trở thành việc ai cũng làm được, chỉ cần lập
--     bản ghi mới thay vì đóng bản ghi cũ
-- -----------------------------------------------------------------------------
INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, v.notify_event, v.notify_permission, v.notify_owner, v.label, v.sort_order
FROM workflow_definitions d,
     (VALUES
         ('__NEW__', 'LOG_RESOLVED', 'DA_XU_LY',
          'ops:maintenance:close-incident', NULL, NULL, FALSE,
          'Nhập sự cố đã khắc phục xong', 5),

         -- Tiếp nhận sự cố: người sửa là Kỹ thuật, nên đòi `create` chứ không đòi
         -- `update`. Ma trận §6 không cấm Kỹ thuật động vào sự cố — nó chỉ cấm Kỹ
         -- thuật TUYÊN BỐ sự cố đã xong.
         ('MOI', 'START', 'DANG_XU_LY',
          'ops:maintenance:create', 'INCIDENT_STARTED', NULL, TRUE,
          'Tiếp nhận, bắt đầu khắc phục', 10),

         ('MOI', 'RESOLVE', 'DA_XU_LY',
          'ops:maintenance:close-incident', 'INCIDENT_RESOLVED', NULL, TRUE,
          'Đóng bản ghi sự cố', 11),
         ('DANG_XU_LY', 'RESOLVE', 'DA_XU_LY',
          'ops:maintenance:close-incident', 'INCIDENT_RESOLVED', NULL, TRUE,
          'Đóng bản ghi sự cố', 20),

         ('DA_XU_LY', 'REOPEN', 'DANG_XU_LY',
          'ops:maintenance:close-incident', 'INCIDENT_REOPENED', NULL, TRUE,
          'Mở lại — sự cố tái diễn', 30)
     ) AS v(from_state, action, to_state,
            required_permission, notify_event, notify_permission, notify_owner, label, sort_order)
WHERE d.entity_type = 'MAINTENANCE_INCIDENT';

-- =============================================================================
-- Tham số nghiệp vụ — T18.9
--
-- Quy tắc 12: tham số để trong `settings` có giao diện sửa. Nhưng cũng nhớ luật
-- "công tắc chưa ai đọc là một lỗi": khoá duy nhất ở đây ĐƯỢC ĐỌC THẬT trong
-- `MaintenanceLogService.duocSua(...)`, và có bài kiểm cho cả hai phía 0 / khác 0.
--
-- Mặc định 0 = TẮT, tức là đúng ma trận §6 hiện hành: sửa/xoá bản ghi đã lưu chỉ
-- Admin + Quản lý XN. Chừa sẵn vì đây là chỗ nghiệp vụ hay đổi ý — người vừa
-- nhập xong phát hiện gõ nhầm số tiền là tình huống chắc chắn xảy ra — và lúc đó
-- đổi một con số dễ hơn nhiều so với sửa mã rồi triển khai lại.
-- =============================================================================
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'ops.maintenance.author-edit-window-minutes', '0', 'INTEGER', '0',
    'OPERATION', 'Cửa sổ người nhập tự sửa bản ghi (phút)',
    'CN-02.2. Trong khoảng này, người ĐÃ TẠO bản ghi được sửa chính bản ghi đó dù không có quyền '
    || 'ops:maintenance:update. Đặt 0 để tắt — đúng ma trận phân quyền §6 hiện hành. '
    || '⚠ Không áp dụng cho xoá, và không áp dụng khi bản ghi đã chuyển khỏi trạng thái ban đầu.',
    'min=0;max=1440', TRUE, TRUE, 70
);
