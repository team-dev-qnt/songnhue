-- ============================================================================
-- WS-53 — LỚP HỒ SƠ CON CỦA MỘT CBNV: lý lịch/chuyên môn · timeline · tài liệu
--
--   CN-04.3  Lý lịch & Chuyên môn        → `employee_qualifications` + `employees.education_level`
--   CN-04.4  Lịch sử công tác (timeline) → `employee_events`
--   CN-04.5  Hợp đồng, tài liệu, cảnh báo hết hạn
--                                        → ⛔ KHÔNG bảng mới. Dùng `attachments`.
--
-- ⛔ 0 dòng dữ liệu CBNV được seed (G6-a chưa có dữ liệu) — như V202609101076.
--   Thứ DUY NHẤT bản này seed là **tham số cấu hình**, và mọi khoá nó thêm đều
--   có ít nhất một nơi ĐỌC trong cùng đợt (quy tắc 15 — xem khối cuối tệp).
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. `employees.education_level` — "Học vấn cao nhất" (CN-04.3)
--
-- ⛔⛔ Vì sao đây là ENUM chứ ⛔ không phải một bảng danh mục có CRUD, trong khi
--    quy tắc 16 nói *"danh mục do khách vận hành là dữ liệu có CRUD"*:
--
--    Quy tắc 16 nói về danh mục **do Công ty vận hành** (mã tình hình vận hành,
--    mức ngưỡng, nhóm nhận cảnh báo) — thứ Công ty thêm/bớt theo nghiệp vụ của
--    họ. Bậc trình độ thì ⛔ không: nó là **Khung trình độ quốc gia Việt Nam**
--    (QĐ 1982/QĐ-TTg 2016, bậc 1–8) cộng ba bậc phổ thông. Công ty ⛔ không có
--    thẩm quyền thêm một bậc, và BCNS-05 *"cơ cấu theo trình độ"* phải xếp được
--    thứ tự — thứ một ô chữ tự do ⛔ không làm được.
--
--    ⇒ Đối chiếu có ý thức với `positions.position_group`, cột NGAY TRÊN trong
--      cùng module, cố ý để **chữ tự do ⛔ không CHECK**: ở đó đặc tả chưa chốt
--      danh sách và Công ty tự đặt. Hai cột, hai quyết định ngược nhau, cùng một
--      luật — khác nhau ở **ai có thẩm quyền quyết định giá trị**.
--
-- ⚠ Thứ tự bậc (để BCNS-05 xếp) nằm ở enum Java `EducationLevel.bac()`, ⛔ không
--   ở CSDL: một cột `rank` là một giá trị dẫn xuất phải giữ đồng bộ bằng tay, và
--   dự án đã trả giá cho đúng chuyện ấy (`geom`, `chainage_m` đều là cột SINH).
-- ---------------------------------------------------------------------------
ALTER TABLE employees
    ADD COLUMN education_level VARCHAR(30);

ALTER TABLE employees
    ADD CONSTRAINT ck_employees_education_level CHECK (
        education_level IS NULL
        OR education_level IN (
            'TIEN_SI', 'THAC_SI', 'DAI_HOC', 'CAO_DANG',
            'TRUNG_CAP', 'SO_CAP', 'THPT', 'THCS', 'KHAC'
        )
    );

COMMENT ON COLUMN employees.education_level IS
    'Học vấn cao nhất (CN-04.3). Khung trình độ quốc gia QĐ 1982/QĐ-TTg + bậc phổ thông. Thứ tự bậc ở EducationLevel.bac().';

-- BCNS-05 gộp theo cột này; ⛔ không quét cả bảng.
CREATE INDEX ix_employees_education
    ON employees (education_level)
    WHERE deleted_at IS NULL AND education_level IS NOT NULL;


-- ---------------------------------------------------------------------------
-- 2. `employee_qualifications` — bằng cấp · chứng chỉ · ngoại ngữ · tin học ·
--    phần mềm chuyên dụng (CN-04.3)
--
-- ⛔⛔ MỘT bảng với cột `kind`, ⛔ KHÔNG năm bảng. Ba lý do, lý do thứ ba là lý
--    do bắt buộc:
--
--    1. Năm loại có **cùng một hình dạng**: tên, nơi cấp, số hiệu, ngày cấp,
--       ngày hết hiệu lực, tệp gốc. Năm bảng là năm bản sao của cùng bộ cột.
--    2. Giao diện là **một tab** với một bộ lọc theo loại — năm bảng nghĩa là
--       năm lời gọi và một phép trộn ở FE.
--    3. ⛔⛔ **Cảnh báo hết hiệu lực (M4.9) phải quét MỘT chỗ.** Năm bảng nghĩa
--       là năm câu `UNION`, và ngày ai đó thêm bảng thứ sáu thì cảnh báo sẽ im
--       lặng bỏ sót đúng loại mới — ⛔ không một dòng lỗi. Đây là luật 12 (đặt
--       bảo đảm ở chỗ dữ liệu ĐI QUA) áp cho lược đồ.
--
-- ⚠ `expires_on` NULL là **hợp lệ và phổ biến**: bằng đại học ⛔ không hết hạn.
--   ⛔ Đừng ép NOT NULL rồi điền một ngày xa — nó biến "vĩnh viễn" thành một
--   ngày sẽ tới, và cảnh báo sẽ kêu vào năm 2099 (quy tắc 3: một giá trị mặc
--   định ⛔ không phải một giá trị đã giải).
-- ---------------------------------------------------------------------------
CREATE TABLE employee_qualifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT gen_random_uuid(),

    employee_id     BIGINT       NOT NULL REFERENCES employees (id),

    -- BANG_CAP · CHUNG_CHI · NGOAI_NGU · TIN_HOC · PHAN_MEM
    kind            VARCHAR(20)  NOT NULL,
    -- Tên bằng/chứng chỉ/ngôn ngữ/phần mềm. VD "Kỹ sư Thuỷ lợi", "IELTS", "AutoCAD".
    name            VARCHAR(255) NOT NULL,
    -- Xếp loại / trình độ đạt được. VD "Giỏi", "6.5", "B1", "Thành thạo".
    -- ⛔ Chữ tự do có chủ đích: thang điểm của IELTS, của khung châu Âu và của
    --   một chứng chỉ nội bộ ⛔ không quy về một danh sách chung được.
    grade           VARCHAR(100),
    -- Chuyên ngành — chỉ có nghĩa với BANG_CAP, để NULL với các loại khác.
    major           VARCHAR(255),
    institution     VARCHAR(255),
    certificate_no  VARCHAR(100),
    issued_on       DATE,
    -- NULL = ⛔ không hết hiệu lực. Xem javadoc khối trên.
    expires_on      DATE,
    note            TEXT,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_employee_qualifications_kind CHECK (
        kind IN ('BANG_CAP', 'CHUNG_CHI', 'NGOAI_NGU', 'TIN_HOC', 'PHAN_MEM')
    ),
    -- Hết hiệu lực ⛔ không thể trước ngày cấp. Ép ở CSDL vì đây là chỗ MỌI
    -- đường ghi đi qua — biểu mẫu chỉ đỡ được người dùng biểu mẫu ấy.
    CONSTRAINT ck_employee_qualifications_dates CHECK (
        expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on
    )
);

CREATE UNIQUE INDEX uq_employee_qualifications_public_id
    ON employee_qualifications (public_id);
CREATE INDEX ix_employee_qualifications_employee
    ON employee_qualifications (employee_id, kind)
    WHERE deleted_at IS NULL;

-- ⛔⛔ Chỉ mục CHỊU LỰC của cảnh báo hết hiệu lực (M4.9). Thiếu nó thì lượt quét
--    hằng ngày đọc toàn bảng, và nó sẽ chậm dần theo số nhân viên × số chứng chỉ
--    — kiểu chậm ⛔ không ai thấy cho tới khi đã muộn.
CREATE INDEX ix_employee_qualifications_expires
    ON employee_qualifications (expires_on)
    WHERE deleted_at IS NULL AND expires_on IS NOT NULL;

COMMENT ON TABLE employee_qualifications IS
    'Bằng cấp/chứng chỉ/ngoại ngữ/tin học/phần mềm (CN-04.3). MỘT bảng + cột kind — cảnh báo hết hạn M4.9 quét một chỗ.';
COMMENT ON COLUMN employee_qualifications.expires_on IS
    'NULL = không hết hiệu lực (bằng đại học). ⛔ Đừng điền một ngày xa để né NULL.';


-- ---------------------------------------------------------------------------
-- 3. `employee_events` — lịch sử công tác, timeline (CN-04.4)
--
-- ⛔⛔ ĐÚNG 10 loại sự kiện, nguyên văn đặc tả CN-04.4. ⛔ Đừng tách
--    `BO_NHIEM_MIEN_NHIEM` thành hai: đặc tả liệt kê nó là MỘT trong mười, và
--    con số 10 ấy đi vào giao diện lọc lẫn báo cáo. Hành vi cụ thể (bổ nhiệm hay
--    miễn nhiệm) nằm ở `title` và số quyết định.
--
-- ⚠ *"⛔ Không ghi đè mất dấu vết cũ"* (SRS §3.4.3) được bảo đảm bằng **hình
--   dạng lược đồ**, ⛔ không bằng một lời dặn: mỗi sự kiện là MỘT HÀNG, nên một
--   lượt điều động ⛔ không thể ghi đè lượt trước. Sửa một hàng vẫn cho phép (gõ
--   nhầm số quyết định là chuyện có thật) và nó đi qua `@Audited` như mọi entity
--   nghiệp vụ khác — giá trị cũ/mới vào `audit_logs`.
--
-- ⛔ `effective_on` là ngày HIỆU LỰC của quyết định, ⛔ không phải ngày nhập
--   liệu và ⛔ không phải `decision_date`. Ba mốc khác nhau, và timeline sắp xếp
--   theo mốc hiệu lực — một quyết định ký tháng 3 có hiệu lực từ tháng 1 phải
--   nằm ở tháng 1.
-- ---------------------------------------------------------------------------
CREATE TABLE employee_events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT gen_random_uuid(),

    employee_id     BIGINT       NOT NULL REFERENCES employees (id),

    event_type      VARCHAR(30)  NOT NULL,
    -- Ngày quyết định có HIỆU LỰC — trục của timeline.
    effective_on    DATE         NOT NULL,
    -- Số hiệu văn bản và ngày ký. Cả hai để trống được: một số sự kiện
    -- (đào tạo nội bộ) ⛔ không có quyết định bằng văn bản.
    decision_no     VARCHAR(100),
    decision_date   DATE,
    title           VARCHAR(255) NOT NULL,
    detail          TEXT,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_employee_events_type CHECK (
        event_type IN (
            'TUYEN_DUNG', 'HOP_DONG', 'DIEU_DONG', 'BO_NHIEM_MIEN_NHIEM', 'NANG_LUONG',
            'KHEN_THUONG', 'KY_LUAT', 'DAO_TAO', 'NGHI_DAI_HAN', 'NGHI_VIEC_HUU'
        )
    ),
    -- Ngày ký ⛔ không thể sau ngày hiệu lực quá xa thì ⛔ không kiểm ở đây —
    -- quyết định hồi tố là chuyện bình thường. Chỉ chặn mốc phi lý tuyệt đối,
    -- cùng lý lẽ IMMUTABLE với `ck_employees_dob_range`.
    CONSTRAINT ck_employee_events_effective_range CHECK (
        effective_on BETWEEN DATE '1960-01-01' AND DATE '2100-12-31'
    )
);

CREATE UNIQUE INDEX uq_employee_events_public_id ON employee_events (public_id);

-- Timeline đọc theo nhân viên, mới nhất trước — chỉ mục phục vụ ĐÚNG câu ấy.
CREATE INDEX ix_employee_events_timeline
    ON employee_events (employee_id, effective_on DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_employee_events_type
    ON employee_events (event_type, effective_on DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE employee_events IS
    'Lịch sử công tác — timeline 10 loại sự kiện (CN-04.4). Mỗi sự kiện MỘT HÀNG ⇒ không ghi đè dấu vết cũ (SRS §3.4.3).';
COMMENT ON COLUMN employee_events.effective_on IS
    'Ngày HIỆU LỰC — trục timeline. Khác decision_date (ngày ký) và khác created_at (ngày nhập).';


-- ---------------------------------------------------------------------------
-- 4. Tài liệu CBNV (CN-04.5) — ⛔ KHÔNG có bảng ở đây, và đó là điểm chính
--
-- Tệp nằm ở `attachments` với `owner_type = 'EMPLOYEE'` và `purpose` = một
-- trong 7 thư mục cố định. Dựng bảng thứ hai nghĩa là hai chỗ kiểm magic bytes,
-- hai chỗ quét virus, hai chỗ tính hạn mức (architecture-review.md §10.6) — và
-- `AttachmentPort` đã có sẵn đúng ba thứ CN-04.5 cần:
--     · `purpose`            → 7 thư mục
--     · `nextVersion(owner, purpose)` → versioning ⛔ không ghi đè
--     · `setValidity(from, until)`    → ngày hiệu lực/hết hạn cho M4.9
--
-- ⇒ Việc duy nhất còn lại ở tầng CSDL là **hạn mức**, và nó là tham số.
-- ---------------------------------------------------------------------------

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, v.exportable, v.ord
FROM (VALUES
    -- Hạn mức dung lượng cả hồ sơ một CBNV. Đọc bởi `AttachmentService` qua
    -- `limits.attachment.quota-mb.<OWNER_TYPE>` — cơ chế ĐÃ CÓ, đây chỉ là khai
    -- giá trị cho loại chủ sở hữu mới.
    ('limits.attachment.quota-mb.EMPLOYEE', '200', 'INTEGER',
     'LIMIT', 'Dung lượng tối đa hồ sơ một CBNV (MB)',
     'CN-04.5. 0 = không giới hạn.', 'min=0;max=5000', TRUE, 70),

    -- ⛔⛔ Bảy mức riêng, đúng đặc tả CN-04.5. Cơ chế hạn mức của `core` chia
    --    theo NHÓM ĐỊNH DẠNG (ảnh/tài liệu/GIS), ⛔ không theo thư mục — nên
    --    bảy mức này là luật của HR và được đọc ở `HoSoTaiLieuService`.
    -- ⚠ Giá trị = đúng con số đặc tả ghi. ⛔ Đừng "làm tròn cho gọn": đặc tả là
    --   nguồn sự thật nghiệp vụ, và một con số lệch ở đây ⛔ không có triệu
    --   chứng nào cho tới ngày có người bị từ chối một tệp hợp lệ.
    ('hr.document.max-mb.GIAY_TO_TUY_THAN', '10', 'INTEGER',
     'HR', 'Dung lượng tối đa — Giấy tờ tuỳ thân (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 200),
    ('hr.document.max-mb.BANG_CAP', '10', 'INTEGER',
     'HR', 'Dung lượng tối đa — Bằng cấp (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 210),
    ('hr.document.max-mb.HOP_DONG', '20', 'INTEGER',
     'HR', 'Dung lượng tối đa — Hợp đồng lao động (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 220),
    ('hr.document.max-mb.QUYET_DINH', '10', 'INTEGER',
     'HR', 'Dung lượng tối đa — Quyết định nhân sự (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 230),
    ('hr.document.max-mb.ANH', '5', 'INTEGER',
     'HR', 'Dung lượng tối đa — Ảnh (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 240),
    ('hr.document.max-mb.HO_SO_Y_TE', '20', 'INTEGER',
     'HR', 'Dung lượng tối đa — Hồ sơ y tế (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 250),
    ('hr.document.max-mb.KHAC', '10', 'INTEGER',
     'HR', 'Dung lượng tối đa — Khác (MB)', 'CN-04.5', 'min=1;max=500', TRUE, 260),

    -- ⬜⬜ ĐIỂM NGHIỆP VỤ CHƯA CHỐT, và nó được khai ra chứ ⛔ không đoán ngầm.
    --
    --   CN-04.5 đòi *"% hoàn thiện hồ sơ + danh sách tài liệu thiếu"* nhưng
    --   ⛔ KHÔNG nói **thư mục nào là bắt buộc**. Ghi cứng một danh sách trong
    --   mã là bịa một luật nhân sự rồi in tỉ lệ % của nó lên màn hình Ban giám
    --   đốc — đúng thứ quy tắc 16 cấm (*"số 0 là một câu khẳng định"*).
    --
    --   ⇒ Danh sách là **tham số**, và giá trị khởi tạo là **RỖNG** — ⛔ không phải một "đề xuất
    --     tối thiểu". Điền sẵn hai thư mục rồi in % của chúng lên màn hình Ban giám đốc là công bố
    --     một con số dựa trên một luật ⛔ KHÔNG AI duyệt.
    --
    -- ⛔⛔ Và rỗng ở đây là **bắt buộc về mặt kỹ thuật**, ⛔ không chỉ về nguyên tắc:
    --     `Setting.effectiveValue()` rơi về `default_value` khi `setting_value` **rỗng hoặc
    --     trắng**, và `changeValue()` quy chuỗi rỗng về NULL — cố ý, để "xoá ô" và "khôi phục mặc
    --     định" cùng nghĩa. Hệ quả: một tham số có **mặc định khác rỗng** thì ⛔ **KHÔNG BAO GIỜ**
    --     đặt rỗng được qua giao diện. Nếu seed 'GIAY_TO_TUY_THAN,HOP_DONG' làm mặc định thì trạng
    --     thái *"chưa cấu hình"* trở thành **⛔ không biểu diễn được** — đúng luật 3 (*"rỗng" khác
    --     "chưa đặt"*), và bài `phanTramHoanThienNullKhiChuaCauHinh` đã bắt được điều đó.
    ('hr.document.required-folders', '', 'STRING',
     'HR', 'Thư mục tài liệu BẮT BUỘC (tính % hoàn thiện hồ sơ)',
     '⬜ CHƯA CHỐT — Công ty tự khai. Ngăn cách bằng dấu phẩy, ví dụ: GIAY_TO_TUY_THAN,HOP_DONG. '
        || 'Để TRỐNG = chưa cấu hình ⇒ màn hình hiện "chưa cấu hình" thay vì 0%.',
     NULL, TRUE, 270)
) AS v(k, val, vtype, grp, label, descr, validation, exportable, ord);


-- ===========================================================================
-- KHỐI KIỂM — một `INSERT … SELECT` ghi 0 hàng vẫn thoát 0 (§11.19)
-- ===========================================================================
DO $$
DECLARE
    so_khoa    integer;
    so_bat_buoc integer;
BEGIN
    SELECT count(*) INTO so_khoa FROM settings
     WHERE setting_key LIKE 'hr.document.max-mb.%';
    IF so_khoa <> 7 THEN
        RAISE EXCEPTION 'Mong đợi 7 khoá hr.document.max-mb.* (7 thư mục cố định của CN-04.5), thực tế %. '
            'Enum HoSoThuMuc và khối seed này phải khai CÙNG một tập — bài kiểm '
            'HoSoTaiLieuHttpTest đối chiếu hai bên.', so_khoa;
    END IF;

    SELECT count(*) INTO so_bat_buoc FROM settings
     WHERE setting_key = 'hr.document.required-folders';
    IF so_bat_buoc <> 1 THEN
        RAISE EXCEPTION 'Thiếu khoá hr.document.required-folders — %% hoàn thiện hồ sơ sẽ ⛔ không '
            'tính được, và màn hình sẽ hiện 0%% cho MỌI hồ sơ (quy tắc 16: số 0 là một khẳng định).';
    END IF;

    RAISE NOTICE 'CN-04.3/04.4/04.5: 2 bảng mới · 0 dòng dữ liệu CBNV · % khoá hạn mức thư mục · '
        '1 khoá thư mục bắt buộc (⬜ Công ty chưa chốt).', so_khoa;
END $$;
