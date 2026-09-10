-- =============================================================================
-- MOD-04 (HRM) — migration ĐẦU TIÊN của module `hr`. CN-04.2.
--
-- Ba bảng: `positions` (danh mục chức vụ) · `employees` (hồ sơ CBNV) ·
-- `employee_sensitive` (trường 🔒, mã hoá AES-256-GCM).
--
-- ⛔⛔ TÊN BẢNG `employees` KHÔNG PHẢI LỰA CHỌN CỦA MIGRATION NÀY.
--    `V202608131002:115-116` đã khai bằng `COMMENT ON COLUMN users.employee_id`
--    rằng cột ấy trỏ sang **`hr.employees`**, và chú thích ấy nằm trong CSDL
--    production từ ngày dựng. Đặt tên khác là để một câu trong CSDL nói sai mà
--    ⛔ không cổng kiểm nào đọc được.
--
-- ⛔ `org_units` DÙNG CHUNG với MOD-02 (quy tắc 7) — ⛔ không bảng phòng ban riêng.
-- ⛔ `users.employee_id` CỐ Ý ⛔ không có FK (ranh giới module, quy tắc 6).
--
-- ⛔⛔ ⛔ KHÔNG SEED MỘT DÒNG CBNV NÀO. `G6-a` (danh sách cán bộ nhân viên) còn
--    mở — CLAUDE.md cấm seed dữ liệu "cho đẹp demo", và một bảng RỖNG nói đúng
--    sự thật hôm nay: chưa có nguồn. Màn hình sẽ trống, và đó là câu trả lời
--    đúng chứ ⛔ không phải một khiếm khuyết cần che.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. `positions` — danh mục chức vụ chuẩn hoá (CN-04.2, gạch đầu dòng 3)
--
-- Dùng chung TOÀN CÔNG TY ⇒ kế thừa `BaseEntity`, ⛔ KHÔNG `ScopedEntity`:
-- "Trưởng phòng" ở Xí nghiệp 1 và Xí nghiệp 2 là cùng một chức vụ. Bọc phạm vi
-- vào đây là chia danh mục thành N bản sao ⛔ không ai đồng bộ được.
-- -----------------------------------------------------------------------------
CREATE TABLE positions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID         NOT NULL DEFAULT gen_random_uuid(),

    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(255) NOT NULL,

    -- "Nhóm" của đặc tả. ⛔ CỐ Ý KHÔNG PHẢI ENUM và ⛔ không có CHECK: đặc tả
    -- ⛔ không liệt kê giá trị nào, và quy tắc 16 xếp danh mục do khách vận hành
    -- vào loại DỮ LIỆU. Bịa ra một danh sách ở đây là ép Công ty phải deploy để
    -- thêm một nhóm — đúng thứ quy tắc 16 sinh ra để chặn.
    position_group  VARCHAR(100),

    description     VARCHAR(500),
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_positions_public_id ON positions (public_id);
CREATE UNIQUE INDEX uq_positions_code      ON positions (code) WHERE deleted_at IS NULL;
CREATE INDEX        ix_positions_sort      ON positions (sort_order, name) WHERE deleted_at IS NULL;

COMMENT ON TABLE positions IS
    'Danh mục chức vụ chuẩn hoá (CN-04.2). Dùng chung toàn Công ty — không có org_unit_id.';
COMMENT ON COLUMN positions.position_group IS
    'Nhóm chức vụ do Công ty tự đặt (quy tắc 16) — cố ý KHÔNG enum, KHÔNG CHECK: đặc tả chưa chốt danh sách.';


-- -----------------------------------------------------------------------------
-- 2. `employees` — hồ sơ cán bộ nhân viên (CN-04.2)
--
-- Kế thừa `ScopedEntity` ⇒ `org_unit_id NOT NULL`. Quản lý cấp Xí nghiệp chỉ
-- thấy hồ sơ đơn vị mình (CN-04.7 / SRS M4.13), và bảo đảm ấy nằm ở bộ lọc
-- Hibernate — ⛔ không ở một câu `WHERE` mà ai đó phải nhớ viết (quy tắc 5).
--
-- ⛔⛔ `org_unit_id` ⛔ KHÔNG ĐƯỢC NULLable. `stations` từng để NULL "cho tiện
--    lúc chưa chốt OI-05", và cái giá là mọi bộ lọc phạm vi phải mang thêm một
--    vế `IS NULL` — tức một cửa mở thường trực. Hồ sơ nhân sự là dữ liệu cá
--    nhân theo NĐ 13/2023; một hàng ⛔ không thuộc đơn vị nào là một hàng ⛔
--    không ai chịu trách nhiệm và MỌI người đọc được.
--
-- ⛔ Trường 🔒 ⛔ KHÔNG nằm ở bảng này — xem bảng 3 (quy tắc 10).
-- -----------------------------------------------------------------------------
CREATE TABLE employees (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Mã NV `NV-2019-001` — đặc tả: "không đổi suốt quá trình công tác".
    code                     VARCHAR(50)  NOT NULL,
    full_name                VARCHAR(255) NOT NULL,

    -- ⚠ "Bản không dấu auto" của đặc tả là một CHỈ MỤC HÀM, ⛔ không phải một
    --   cột. Một cột thứ hai chứa cùng một sự thật sẽ lệch đúng vào lần ai đó
    --   sửa tên bằng một đường ghi quên cập nhật nó (luật 27 — đã trả giá 6 lần).
    date_of_birth            DATE,
    gender                   VARCHAR(10),
    ethnicity                VARCHAR(100),
    hometown                 VARCHAR(255),
    address                  VARCHAR(500),
    phone                    VARCHAR(30),
    work_email               VARCHAR(255),
    personal_email           VARCHAR(255),
    marital_status           VARCHAR(20),
    emergency_contact_name   VARCHAR(255),
    emergency_contact_phone  VARCHAR(30),

    -- === Thông tin công tác ==================================================
    org_unit_id              BIGINT       NOT NULL REFERENCES org_units (id),
    position_id              BIGINT       REFERENCES positions (id),
    job_title                VARCHAR(255),
    hired_at                 DATE,
    contract_type            VARCHAR(30),
    contract_signed_at       DATE,
    contract_expires_at      DATE,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'THU_VIEC',
    terminated_at            DATE,
    termination_reason       VARCHAR(500),

    created_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               BIGINT,
    updated_at               timestamptz,
    updated_by               BIGINT,
    deleted_at               timestamptz,
    version                  INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_employees_gender CHECK (gender IS NULL OR gender IN ('NAM', 'NU', 'KHAC')),
    CONSTRAINT ck_employees_marital CHECK (
        marital_status IS NULL OR marital_status IN ('DOC_THAN', 'DA_KET_HON', 'KHAC')
    ),
    -- BLLĐ 2019 Điều 20 chỉ có HAI loại hợp đồng lao động; `THU_VIEC` là hợp
    -- đồng thử việc (Điều 24) và `KHAC` giữ chỗ cho hợp đồng dịch vụ/khoán việc.
    CONSTRAINT ck_employees_contract_type CHECK (
        contract_type IS NULL
        OR contract_type IN ('KHONG_XAC_DINH_THOI_HAN', 'XAC_DINH_THOI_HAN', 'THU_VIEC', 'KHAC')
    ),
    CONSTRAINT ck_employees_status CHECK (
        status IN ('DANG_LAM', 'THU_VIEC', 'THAI_SAN', 'KHONG_LUONG', 'NGHI_VIEC', 'NGHI_HUU')
    ),
    -- ⛔ Đặc tả: 18 ≤ tuổi ≤ 70. Ép ở CSDL vì đây là chỗ MỌI đường ghi đi qua
    --   (quy tắc 12) — biểu mẫu chỉ đỡ được người dùng biểu mẫu ấy.
    --   Dùng mốc tuyệt đối chứ ⛔ không `now()`: CHECK phải IMMUTABLE, và một
    --   ràng buộc phụ thuộc ngày hệ thống sẽ biến hàng hợp lệ hôm nay thành
    --   hàng ⛔ không khôi phục được từ bản sao lưu ngày mai.
    CONSTRAINT ck_employees_dob_range CHECK (
        date_of_birth IS NULL OR date_of_birth BETWEEN DATE '1930-01-01' AND DATE '2015-12-31'
    ),
    -- Hai chiều: có ngày nghỉ việc ⟺ trạng thái là NGHI_VIEC hoặc NGHI_HUU.
    -- ⛔ Không để lọt tổ hợp "đã nghỉ mà vẫn DANG_LAM" — nó làm mọi phép đếm
    -- quân số sai mà ⛔ không màn hình nào báo.
    CONSTRAINT ck_employees_terminated_pairs CHECK (
        (terminated_at IS NOT NULL) = (status IN ('NGHI_VIEC', 'NGHI_HUU'))
    ),
    CONSTRAINT ck_employees_contract_dates CHECK (
        contract_expires_at IS NULL
        OR contract_signed_at IS NULL
        OR contract_expires_at >= contract_signed_at
    )
);

CREATE UNIQUE INDEX uq_employees_public_id ON employees (public_id);
CREATE UNIQUE INDEX uq_employees_code      ON employees (code) WHERE deleted_at IS NULL;
CREATE INDEX        ix_employees_org_unit  ON employees (org_unit_id) WHERE deleted_at IS NULL;
CREATE INDEX        ix_employees_position  ON employees (position_id) WHERE deleted_at IS NULL;
CREATE INDEX        ix_employees_status    ON employees (status) WHERE deleted_at IS NULL;

-- "Họ tên + bản không dấu auto" (CN-04.2) và ô tìm CBNV (CN-04.6, CN-04.7).
-- `sn_khong_dau` khai IMMUTABLE ở `V202608191014` chính là để dùng được ở đây.
CREATE INDEX ix_employees_ten_khong_dau
    ON employees (sn_khong_dau(full_name) varchar_pattern_ops)
    WHERE deleted_at IS NULL;

-- Cảnh báo hết hạn HĐLĐ (CN-04.5 / M4.9) quét theo cột này.
CREATE INDEX ix_employees_contract_expires
    ON employees (contract_expires_at)
    WHERE deleted_at IS NULL AND contract_expires_at IS NOT NULL;

COMMENT ON TABLE employees IS
    'Hồ sơ CBNV (CN-04.2). Trường 🔒 ở bảng riêng employee_sensitive. org_unit_id NOT NULL — không có hàng vô chủ.';
COMMENT ON COLUMN employees.code IS
    'Mã nhân viên, KHÔNG đổi suốt quá trình công tác (CN-04.2). Unique trong phạm vi bản ghi còn sống.';


-- -----------------------------------------------------------------------------
-- 3. `employee_sensitive` — trường 🔒 (CLAUDE.md quy tắc 10 + 13, NĐ 13/2023)
--
-- ⛔⛔ MỌI cột giá trị ở đây là BẢN MÃ AES-256-GCM `<key_id>:<base64>`, ⛔ KHÔNG
--    BAO GIỜ là giá trị thô. Khoá nằm NGOÀI CSDL (`/opt/songnhue/keys/`), ⛔
--    không nằm trong bản sao lưu — bản sao lưu đi ra khỏi phòng máy, khoá thì ⛔
--    không.
--
-- ⛔⛔ ⛔ KHÔNG có cột `key_id` — T47.19 CHỐT 10/09/2026 BẰNG PHÉP ĐO, ⛔ không
--    bằng ý kiến. Hai tiền lệ của kho mâu thuẫn nhau; lượt đo phân xử:
--      • `CryptoService.encrypt()` nhúng id khoá VÀO bản mã (`CryptoService:26-28`),
--        và `keyIdOf()` (`:110`) đọc ra được — có người dùng thật ở
--        `ApiSourceService:215`.
--      • `user_totp.key_id` — tiền lệ "có cột riêng" — đo được là một cột CHẾT:
--        `setKeyId` gọi ĐÚNG MỘT lần (`TotpService:127`), còn `getKeyId()` có
--        **0 nơi gọi** trong toàn kho. Và "job xoay khoá" mà cột ấy sinh ra để
--        phục vụ có **0 tệp**. Một tiền lệ chưa ai đi qua ⛔ không phải một tiền
--        lệ (luật 7, luật 15).
--    ⇒ Theo `api_sources`. Một cột thứ hai chứa cùng một sự thật sẽ lệch đúng
--      vào lần xoay khoá — lúc bản mã đã đổi mà cột chưa.
--
-- ⛔⛔ VÌ SAO "CCCD unique" CẦN THÊM MỘT CỘT VÂN TAY, ⛔ không phải một UNIQUE
--    trên cột mã hoá: GCM dùng IV ngẫu nhiên ⇒ cùng một số CCCD mã hoá hai lần
--    cho ra hai chuỗi KHÁC NHAU. `UNIQUE (national_id)` sẽ tồn tại, trông như
--    một bảo đảm, và ⛔ KHÔNG BAO GIỜ bắt được một bản trùng nào — đúng hình
--    dạng "một cơ chế chưa ai đi qua" đã trả giá nhiều lần ở dự án này.
--    ⇒ `national_id_fingerprint` = HMAC-SHA256 khoá dẫn xuất, xác định
--      (`CryptoService.fingerprint()`), mang tiền tố `<key_id>:` như bản mã.
--    ⚠⚠ HỆ QUẢ PHẢI GHI RA: vân tay phụ thuộc KHOÁ. Một lượt xoay khoá làm
--      cùng một CCCD cho vân tay khác ⇒ phép chống trùng câm lặng. Job xoay
--      khoá (chưa tồn tại) BẮT BUỘC tính lại cột này cùng lượt với bản mã;
--      `HoSoNhanSuMaHoaTest` khẳng định cả cột chỉ mang MỘT `key_id`.
-- -----------------------------------------------------------------------------
CREATE TABLE employee_sensitive (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                 UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- Quan hệ 1–1 với hồ sơ. ⛔ Không ON DELETE CASCADE: `employees` xoá MỀM,
    -- nên cascade ⛔ không bao giờ chạy và để nó ở đây chỉ tạo cảm giác an toàn.
    employee_id               BIGINT      NOT NULL REFERENCES employees (id),

    national_id               TEXT,
    national_id_issued_on     TEXT,
    national_id_issued_place  TEXT,
    base_salary               TEXT,
    salary_coefficient        TEXT,
    bank_account              TEXT,
    tax_code                  TEXT,
    social_insurance_no       TEXT,

    -- Vân tay xác định của CCCD — chỉ để chống trùng, ⛔ không giải ngược được.
    national_id_fingerprint   VARCHAR(80),

    created_at                timestamptz NOT NULL DEFAULT now(),
    created_by                BIGINT,
    updated_at                timestamptz,
    updated_by                BIGINT,
    deleted_at                timestamptz,
    version                   INTEGER     NOT NULL DEFAULT 0,

    -- Mọi cột giá trị phải mang tiền tố id khoá. Một chuỗi ⛔ không có tiền tố
    -- là một giá trị THÔ đã lọt vào bảng — và nó chỉ lộ ra ở lượt đọc đầu tiên,
    -- rất xa chỗ đã ghi sai. Tiền lệ: `ck_api_sources_credential_format`.
    CONSTRAINT ck_employee_sensitive_banma CHECK (
        (national_id              IS NULL OR national_id              ~ '^[A-Za-z0-9_-]+:.+$')
        AND (national_id_issued_on    IS NULL OR national_id_issued_on    ~ '^[A-Za-z0-9_-]+:.+$')
        AND (national_id_issued_place IS NULL OR national_id_issued_place ~ '^[A-Za-z0-9_-]+:.+$')
        AND (base_salary              IS NULL OR base_salary              ~ '^[A-Za-z0-9_-]+:.+$')
        AND (salary_coefficient       IS NULL OR salary_coefficient       ~ '^[A-Za-z0-9_-]+:.+$')
        AND (bank_account             IS NULL OR bank_account             ~ '^[A-Za-z0-9_-]+:.+$')
        AND (tax_code                 IS NULL OR tax_code                 ~ '^[A-Za-z0-9_-]+:.+$')
        AND (social_insurance_no      IS NULL OR social_insurance_no      ~ '^[A-Za-z0-9_-]+:.+$')
    ),
    -- Vân tay: `<key_id>:<64 ký tự hex>`. Ràng buộc chặt hơn bản mã vì độ dài
    -- của SHA-256 là hằng số — một chuỗi lệch độ dài là một phép băm khác.
    CONSTRAINT ck_employee_sensitive_vantay CHECK (
        national_id_fingerprint IS NULL OR national_id_fingerprint ~ '^[A-Za-z0-9_-]+:[0-9a-f]{64}$'
    ),
    -- Có vân tay ⟺ có CCCD. Hai chiều, ⛔ không để lọt "có vân tay mà ⛔ không
    -- có số" (một bản ghi chiếm chỗ chống trùng mà ⛔ không ai đọc lại được).
    CONSTRAINT ck_employee_sensitive_vantay_cap CHECK (
        (national_id IS NULL) = (national_id_fingerprint IS NULL)
    )
);

CREATE UNIQUE INDEX uq_employee_sensitive_public_id ON employee_sensitive (public_id);
CREATE UNIQUE INDEX uq_employee_sensitive_employee  ON employee_sensitive (employee_id)
    WHERE deleted_at IS NULL;
-- ⭐ Đây là chỗ "CCCD unique" của CN-04.2 THỰC SỰ được ép.
CREATE UNIQUE INDEX uq_employee_sensitive_cccd ON employee_sensitive (national_id_fingerprint)
    WHERE deleted_at IS NULL AND national_id_fingerprint IS NOT NULL;

COMMENT ON TABLE employee_sensitive IS
    '🔒 Trường nhạy cảm HR (NĐ 13/2023). Mọi cột giá trị là bản mã AES-256-GCM; khoá NGOÀI CSDL. Không endpoint nào trả về khi thiếu hr:employee:view-sensitive.';
COMMENT ON COLUMN employee_sensitive.national_id_fingerprint IS
    '⛔ HMAC-SHA256 khoá dẫn xuất, dạng <key_id>:<hex64>. CHỈ để ép CCCD unique — không giải ngược. Job xoay khoá PHẢI tính lại cột này cùng lượt với bản mã.';
COMMENT ON COLUMN employee_sensitive.base_salary IS
    '⛔ Bản mã AES-256-GCM. Chốt C4: hệ thống chỉ LƯU TRỮ lương/hệ số — không có module tính lương, không chấm công.';
