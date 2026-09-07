-- =============================================================================
-- CN-01.6 — Phản hồi / đánh giá của người dùng cổng. WS-36 / T36.8.
--
-- ⛔⛔ CHỐT D1 (12/8/2026): TẮT bình luận công khai tự do. Chỉ khảo sát/góp ý,
--    **kiểm duyệt 100%** — mọi mục gửi lên vào `CHO_DUYET`, và chỉ hiện trên
--    cổng sau khi Quản trị nội dung duyệt.
--
--    ⇒ Bảng này ⛔ KHÔNG phải `comments`: ⛔ không có `parent_id`, ⛔ không có
--      `article_id`, ⛔ không có cây trả lời. Thêm bất kỳ cột nào trong ba cột ấy
--      là dựng lại đúng thứ D1 đã loại — chỉ khác cái tên.
--
-- ⭐⭐ VÌ SAO KIỂM DUYỆT PHẢI CÓ NƠI HIỂN THỊ TRONG CÙNG LƯỢT NÀY
--
--   "Duyệt" chỉ có nghĩa nếu có một chỗ mà *đã duyệt* khác *chưa duyệt*. Dựng
--   màn hình kiểm duyệt mà ⛔ không dựng nơi công bố thì nút Duyệt ⛔ không quyết
--   định điều gì — đúng hình dạng luật 27 (nửa cặp đọc–ghi) mà lượt 28/8 tìm ra
--   sáu lần trong một buổi. Vì thế tệp này seed **hai** khoá `SITE`, và cả hai
--   có nơi đọc thật ở `frontend/public-web/src/app/gop-y/page.tsx` ngay trong
--   commit này (`PortalSettingsReadTest` canh).
--
-- ⚠ `rating` CHO PHÉP NULL, và con số trung bình phải nói ra phạm vi của nó
--
--   CN-01.6 nói "đánh giá mức độ hài lòng **hoặc** góp ý". Ép sao là ép người
--   chỉ muốn viết một câu phải chấm điểm; ép nội dung rỗng là để màn hình kiểm
--   duyệt nhận về những bản ghi ⛔ không có gì để duyệt. ⇒ `content` NOT NULL,
--   `rating` NULL được.
--
--   Hệ quả phải nói ra: điểm trung bình tính trên **tập con** có chấm điểm, và
--   chỉ trên các mục **ĐÃ DUYỆT**. `FeedbackModerationService.tongHop()` vì thế
--   trả kèm cả `soCoDiem` lẫn số lượng từng trạng thái — một con số trung bình
--   đứng một mình ⛔ không phân biệt được "4,2 trên 5 phiếu" với "4,2 trên 500"
--   (luật 9), và ⛔ không cho ai thấy phần đã bị lọc ra.
-- =============================================================================


-- ═════════════════════════════════════════════════════════════════════════════
-- [1] Bảng `feedbacks`
--
-- ⛔ ⛔ KHÔNG lưu địa chỉ IP — cùng lý lẽ với `contacts` (NĐ 13/2023): chống lạm
--    dụng đã do `RateLimitFilter` lo, ngay trong bộ nhớ. Thu thập "để đó phòng
--    khi cần" đúng là thứ nghị định ấy cấm.
-- ═════════════════════════════════════════════════════════════════════════════
CREATE TABLE feedbacks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Ẩn danh được: đây là phiếu khảo sát mức độ hài lòng, ⛔ không phải kênh
    -- khiếu nại. Kênh khiếu nại là `contacts`, và ở đó `ck_contacts_lien_lac`
    -- BẮT BUỘC có đường liên hệ ngược vì Công ty phải trả lời được.
    full_name       VARCHAR(255),
    email           VARCHAR(255),

    -- 1..5 sao. SMALLINT chứ ⛔ không NUMERIC: đây là một thứ hạng đếm được, ⛔
    -- không phải một số đo — quy tắc 2 cấm float/double cho số đo, ⛔ không đòi
    -- NUMERIC cho một ô chọn năm mức.
    rating          SMALLINT,

    content         TEXT         NOT NULL,

    status          VARCHAR(20)  NOT NULL DEFAULT 'CHO_DUYET',

    -- Lý do của bước chuyển gần nhất, ghi qua `WorkflowReasonAware`. ⛔ KHÔNG
    -- BAO GIỜ ra cổng công khai: đây là chỗ cán bộ viết *về* người gửi.
    moderation_note TEXT,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_feedbacks_public_id UNIQUE (public_id),
    CONSTRAINT ck_feedbacks_rating  CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_feedbacks_content CHECK (length(btrim(content)) > 0),
    CONSTRAINT ck_feedbacks_status  CHECK (status IN ('CHO_DUYET', 'DA_DUYET', 'TU_CHOI', 'AN'))
);

-- Màn hình kiểm duyệt mở mặc định ở "Chờ duyệt"; cổng công khai đọc "Đã duyệt".
-- Hai truy vấn ấy là toàn bộ tải của bảng này.
CREATE INDEX ix_feedbacks_status ON feedbacks (status, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE feedbacks IS
    'Phản hồi/đánh giá từ cổng (CN-01.6). Kiểm duyệt 100% — chốt D1. ⛔ KHÔNG phải bảng bình luận.';
COMMENT ON COLUMN feedbacks.status IS
    'Bốn trạng thái, đổi DUY NHẤT qua Workflow engine (entity_type = FEEDBACK).';
COMMENT ON COLUMN feedbacks.rating IS
    '1..5 sao, NULL khi người gửi chỉ viết góp ý. Điểm trung bình vì thế tính trên TẬP CON.';
COMMENT ON COLUMN feedbacks.moderation_note IS
    'Lý do duyệt/từ chối/ẩn của bước gần nhất. ⛔ KHÔNG ra cổng công khai.';


-- ═════════════════════════════════════════════════════════════════════════════
-- [2] Quy trình kiểm duyệt
--
-- ⭐ TU_CHOI và AN là HAI trạng thái khác nhau, ⛔ không phải một
--
--   `TU_CHOI` = chưa từng hiện trên cổng. `AN` = đã hiện rồi bị gỡ xuống. Gộp
--   hai cái làm một thì lịch sử ⛔ không trả lời được câu hỏi duy nhất người ta
--   sẽ hỏi lúc có khiếu nại: *"nội dung ấy đã từng công khai chưa?"*
--
-- ⛔ `notify_owner = FALSE` ở TẤT CẢ: người gửi là người dân ⛔ không có
--    `user_id` — y hệt CONTACT.
-- ═════════════════════════════════════════════════════════════════════════════
INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES ('FEEDBACK', 'FEEDBACK', 'Kiểm duyệt phản hồi từ cổng', 'CHO_DUYET',
        'Chờ duyệt → Đã duyệt (hiện trên cổng) / Từ chối. Đã duyệt có thể Ẩn; Ẩn và Từ chối '
        'đều duyệt lại được. Chốt D1: kiểm duyệt 100%, ⛔ không có bình luận tự do.');

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
         -- Duyệt: bước làm một mục trở thành nội dung CÔNG KHAI. ⛔ Không đòi lý
         -- do — đòi lý do cho bước thường xuyên nhất là dạy người dùng gõ "ok",
         -- và khi ô lý do đã thành phản xạ thì bước thật sự cần nó cũng nhận "ok".
         ('CHO_DUYET', 'APPROVE', 'DA_DUYET', 'cms:feedback:manage', FALSE, 'Duyệt & công bố', 10),

         -- ⭐ Từ chối — BẮT BUỘC lý do. Đây là bước ⛔ không cho một ý kiến của
         --   người dân lên cổng, và nó là bước sẽ bị hỏi lại.
         ('CHO_DUYET', 'REJECT', 'TU_CHOI', 'cms:feedback:manage', TRUE, 'Từ chối (nêu lý do)', 20),

         -- ⭐ Ẩn — BẮT BUỘC lý do. Gỡ một nội dung ĐÃ công khai xuống.
         ('DA_DUYET', 'HIDE', 'AN', 'cms:feedback:manage', TRUE, 'Ẩn khỏi cổng (nêu lý do)', 30),

         -- Hiện lại / duyệt lại. ⛔ Không đòi lý do: cả hai đều là bước quay về
         -- trạng thái công khai, và bước ấy đã có bước phủ định đứng trước nó
         -- trong nhật ký kèm lý do.
         ('AN',      'SHOW',    'DA_DUYET', 'cms:feedback:manage', FALSE, 'Hiện lại',   40),
         ('TU_CHOI', 'APPROVE', 'DA_DUYET', 'cms:feedback:manage', FALSE, 'Duyệt lại',  50)
     ) AS v(from_state, action, to_state, required_permission, requires_reason, label, sort_order)
WHERE d.entity_type = 'FEEDBACK';


-- ═════════════════════════════════════════════════════════════════════════════
-- [3] Hai khoá `SITE` — và cả hai CÓ NƠI ĐỌC trong chính commit này
--
-- ⭐ Nhóm `SITE` chứ ⛔ không `CMS`: `SiteConfigService.effectiveValues()` chỉ
--   trả SITE + COMPANY ra `/api/v1/public/site-config`. Ở nhóm CMS thì trang
--   cổng ⛔ không có đường nào đọc, và hai công tắc này ⛔ không bao giờ có hiệu
--   lực — nửa cặp đọc–ghi ngay từ dòng đầu (cùng bài học với T36.7).
--
-- ⚠ Hai khoá chứ ⛔ không một: "có nhận góp ý" và "có công bố góp ý đã duyệt" là
--   HAI quyết định khác nhau của Công ty. Gộp lại thì tắt công bố kéo theo tắt
--   cả việc thu thập — mà thu thập để làm báo cáo nội bộ là một cách dùng hợp lệ.
-- ═════════════════════════════════════════════════════════════════════════════
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, 'SITE', v.label, v.descr, NULL, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.feedback.enabled', 'true', 'BOOLEAN',
     'Nhận góp ý / đánh giá từ người dùng cổng',
     'CN-01.6. Tắt thì trang Góp ý ⛔ không hiện biểu mẫu nữa (các mục đã gửi vẫn giữ nguyên). '
     'Mọi mục gửi lên đều vào trạng thái Chờ duyệt — chốt D1, kiểm duyệt 100%.',
     40),
    ('site.feedback.public-list.enabled', 'true', 'BOOLEAN',
     'Công bố các góp ý ĐÃ DUYỆT trên cổng',
     'CN-01.6. Tắt thì góp ý vẫn thu thập và vẫn kiểm duyệt được, nhưng ⛔ không mục nào hiện ra '
     'cho người đọc — dùng khi Công ty chỉ muốn số liệu nội bộ.',
     41)
) AS v(k, val, vtype, label, descr, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);


-- ═════════════════════════════════════════════════════════════════════════════
-- [4] Kiểm ngay trong migration — §10.66
--
-- `INSERT ... SELECT ... WHERE d.entity_type = 'FEEDBACK'` khớp hụt chèn 0 hàng
-- và Flyway vẫn xanh trọn vẹn.
-- ═════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_buoc     INTEGER;
    so_bat_buoc INTEGER;
    so_khoa     INTEGER;
BEGIN
    SELECT count(*) INTO so_buoc
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'FEEDBACK';
    IF so_buoc <> 5 THEN
        RAISE EXCEPTION 'Quy trình FEEDBACK có % bước chuyển, phải là 5', so_buoc;
    END IF;

    SELECT count(*) INTO so_bat_buoc
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'FEEDBACK' AND t.requires_reason;
    IF so_bat_buoc <> 2 THEN
        RAISE EXCEPTION 'Phải có đúng 2 bước đòi lý do (REJECT, HIDE), đang có %', so_bat_buoc;
    END IF;

    -- Mọi trạng thái trong CHECK phải tới được — nếu không thì `ck_feedbacks_status`
    -- là một lời hứa suông và enum Java lệch với CSDL.
    IF EXISTS (
        SELECT 1 FROM unnest(ARRAY['DA_DUYET','TU_CHOI','AN']) AS s(v)
        WHERE NOT EXISTS (
            SELECT 1 FROM workflow_transitions t
              JOIN workflow_definitions d ON d.id = t.definition_id
             WHERE d.entity_type = 'FEEDBACK' AND t.to_state = s.v)
    ) THEN
        RAISE EXCEPTION 'Có trạng thái trong ck_feedbacks_status không có đường nào đi tới';
    END IF;

    SELECT count(*) INTO so_khoa
      FROM settings WHERE setting_key LIKE 'site.feedback.%' AND group_code = 'SITE';
    IF so_khoa <> 2 THEN
        RAISE EXCEPTION 'Phải có đủ 2 khoá site.feedback.*, đang có %', so_khoa;
    END IF;
END $$;
