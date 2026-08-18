-- =============================================================================
-- WS-2 / T2.3 + T2.8 — Định danh, đơn vị, RBAC, phiên đăng nhập
--
-- QUY ƯỚC CỘT CHUẨN (T2.8, conventions.md §1.2) — áp cho mọi bảng nghiệp vụ:
--   id          BIGINT GENERATED ALWAYS AS IDENTITY  (khóa chính nội bộ)
--   public_id   UUID NOT NULL DEFAULT gen_random_uuid()  (định danh ra API — chống IDOR)
--   created_at  timestamptz NOT NULL DEFAULT now()   ·  created_by BIGINT
--   updated_at  timestamptz                          ·  updated_by BIGINT
--   deleted_at  timestamptz                          (soft delete)
--   version     INTEGER NOT NULL DEFAULT 0           (optimistic lock — @Version)
--
-- created_by/updated_by CỐ Ý không có FK sang users: giữ được vết thao tác kể cả
-- khi bản ghi user bị soft-delete, và tránh vòng phụ thuộc khi seed.
--
-- Enum nghiệp vụ: lưu VARCHAR + CHECK constraint (không dùng Postgres enum type —
-- thêm giá trị mới không phải ALTER TYPE trên bảng lớn).
-- Timestamp: LUÔN timestamptz (UTC). Hiển thị UTC+7 làm ở tầng FE.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- org_units — MỘT bảng dùng chung cho Xí nghiệp (MOD-02) và phòng ban (MOD-04)
-- CLAUDE.md quy tắc 7. Cây ≥ 5 cấp, materialized path để truy vấn cây con nhanh.
-- -----------------------------------------------------------------------------
CREATE TABLE org_units (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID        NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    short_name      VARCHAR(100),
    unit_type       VARCHAR(30) NOT NULL,
    parent_id       BIGINT REFERENCES org_units (id),
    -- Materialized path dạng '/1/4/9/' — tìm cây con bằng LIKE '/1/4/%'
    path            VARCHAR(500) NOT NULL,
    depth           SMALLINT    NOT NULL DEFAULT 0,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    -- Người đứng đầu / cấp phó: nguồn để resolver cảnh báo G11 tìm người nhận
    head_user_id    BIGINT,
    deputy_user_id  BIGINT,
    phone           VARCHAR(30),
    email           VARCHAR(255),
    address         VARCHAR(500),
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT ck_org_units_unit_type CHECK (
        unit_type IN ('CONG_TY', 'PHONG_BAN', 'XI_NGHIEP', 'TO_DOI', 'KHAC')
    ),
    CONSTRAINT ck_org_units_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX uq_org_units_public_id ON org_units (public_id);
-- Mã đơn vị chỉ unique trong phạm vi bản ghi còn sống — soft delete rồi tái dùng mã được
CREATE UNIQUE INDEX uq_org_units_code ON org_units (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_org_units_parent_id ON org_units (parent_id);
CREATE INDEX ix_org_units_path ON org_units (path varchar_pattern_ops);

COMMENT ON TABLE org_units IS
    'Cây đơn vị dùng chung: Công ty → Phòng ban / Xí nghiệp → Tổ đội (CLAUDE.md quy tắc 7)';
COMMENT ON COLUMN org_units.path IS
    'Materialized path /id/id/… — dùng cho scope filter theo cây con (conventions.md §4.2 tầng 3)';

-- -----------------------------------------------------------------------------
-- users
-- CN-05.1: KHÔNG xóa vĩnh viễn tài khoản đã có lịch sử thao tác — chỉ khóa.
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    username              VARCHAR(100) NOT NULL,
    email                 VARCHAR(255),
    full_name             VARCHAR(255) NOT NULL,
    phone                 VARCHAR(30),
    -- BCrypt cost ≥ 12 (conventions.md §4.1). Giá trị '!' = không có mật khẩu
    -- hợp lệ nào khớp → tài khoản chưa kích hoạt.
    password_hash         VARCHAR(100) NOT NULL,
    password_changed_at   timestamptz,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    org_unit_id           BIGINT       NOT NULL REFERENCES org_units (id),
    -- Liên kết hồ sơ nhân viên (MOD-04). CỐ Ý không đặt FK: bảng employees thuộc
    -- module hr, migration hai module độc lập nhau — ràng buộc chéo module giữ ở
    -- tầng service (CLAUDE.md quy tắc 6).
    employee_id           BIGINT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    two_factor_required   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count    SMALLINT     NOT NULL DEFAULT 0,
    last_failed_login_at  timestamptz,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    last_login_ip         INET,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            BIGINT,
    updated_at            timestamptz,
    updated_by            BIGINT,
    deleted_at            timestamptz,
    version               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_status CHECK (
        status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'DISABLED')
    )
);

CREATE UNIQUE INDEX uq_users_public_id ON users (public_id);
-- Username so sánh không phân biệt hoa/thường để tránh 2 tài khoản "Admin"/"admin"
CREATE UNIQUE INDEX uq_users_username ON users (lower(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_email ON users (lower(email))
    WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE INDEX ix_users_org_unit_id ON users (org_unit_id);
CREATE INDEX ix_users_employee_id ON users (employee_id) WHERE employee_id IS NOT NULL;

COMMENT ON COLUMN users.password_hash IS
    'BCrypt cost ≥ 12. ''!'' = tài khoản chưa đặt mật khẩu, mọi lần đăng nhập đều trượt';
COMMENT ON COLUMN users.employee_id IS
    'Trỏ sang hr.employees — KHÔNG có FK, ràng buộc ở tầng service (ranh giới module)';

ALTER TABLE org_units
    ADD CONSTRAINT fk_org_units_head_user FOREIGN KEY (head_user_id) REFERENCES users (id),
    ADD CONSTRAINT fk_org_units_deputy_user FOREIGN KEY (deputy_user_id) REFERENCES users (id);

-- -----------------------------------------------------------------------------
-- RBAC — permission dạng `module:resource:action`, deny by default
-- conventions.md §4.2 · ma trận nguồn: function-spec.md §6
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    -- Role hệ thống: Admin KHÔNG được sửa quyền/xóa (VD SUPER_ADMIN)
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_roles_public_id ON roles (public_id);
CREATE UNIQUE INDEX uq_roles_code ON roles (code) WHERE deleted_at IS NULL;

CREATE TABLE permissions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    module      VARCHAR(20)  NOT NULL,
    resource    VARCHAR(50)  NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_permissions_module CHECK (module IN ('cms', 'ops', 'hyd', 'hr', 'adm')),
    -- Ép đúng định dạng module:resource:action — chặn typo lúc seed
    CONSTRAINT ck_permissions_code_format CHECK (code = module || ':' || resource || ':' || action)
);

CREATE UNIQUE INDEX uq_permissions_code ON permissions (code);
CREATE INDEX ix_permissions_module ON permissions (module);

COMMENT ON TABLE permissions IS
    'Danh mục quyền — do lập trình sinh ra (R__core_seed_permissions.sql), Admin không tự thêm';

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    granted_at    timestamptz NOT NULL DEFAULT now(),
    granted_by    BIGINT,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

CREATE TABLE user_roles (
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id    BIGINT NOT NULL REFERENCES roles (id),
    granted_at timestamptz NOT NULL DEFAULT now(),
    granted_by BIGINT,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

-- -----------------------------------------------------------------------------
-- sessions — refresh token rotation + reuse detection (conventions.md §4.1)
-- CHỈ lưu SHA-256 của refresh token, không bao giờ lưu token gốc.
-- -----------------------------------------------------------------------------
CREATE TABLE sessions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id             BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Cả chuỗi rotation của 1 lần đăng nhập dùng chung family_id. Phát hiện dùng
    -- lại token cũ → thu hồi TOÀN BỘ family (dấu hiệu token bị đánh cắp).
    family_id           UUID        NOT NULL,
    refresh_token_hash  CHAR(64)    NOT NULL,
    parent_session_id   BIGINT REFERENCES sessions (id),
    issued_at           timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz NOT NULL,
    last_used_at        timestamptz,
    rotated_at          timestamptz,
    revoked_at          timestamptz,
    revoked_reason      VARCHAR(50),
    ip_address          INET,
    user_agent          VARCHAR(500),
    device_label        VARCHAR(120),
    CONSTRAINT ck_sessions_revoked_reason CHECK (
        revoked_reason IS NULL OR revoked_reason IN (
            'LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'PASSWORD_CHANGED',
            'ACCOUNT_LOCKED', 'ADMIN_REVOKED', 'EXPIRED', 'REMOTE_LOGOUT'
        )
    )
);

CREATE UNIQUE INDEX uq_sessions_public_id ON sessions (public_id);
CREATE UNIQUE INDEX uq_sessions_refresh_token_hash ON sessions (refresh_token_hash);
CREATE INDEX ix_sessions_family_id ON sessions (family_id);
-- Danh sách phiên đang hoạt động của 1 user (M5.14 — đăng xuất từ xa)
CREATE INDEX ix_sessions_user_active ON sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_sessions_expires_at ON sessions (expires_at);

COMMENT ON COLUMN sessions.refresh_token_hash IS
    'SHA-256 hex của refresh token — token gốc chỉ tồn tại trong cookie httpOnly';

-- -----------------------------------------------------------------------------
-- token_denylist — thu hồi access token còn hạn (đổi mật khẩu, khóa tài khoản)
-- Không dùng Redis ở v1 → denylist nằm ở DB (architecture-review.md §3).
-- -----------------------------------------------------------------------------
CREATE TABLE token_denylist (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jti        UUID        NOT NULL,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    reason     VARCHAR(50) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_token_denylist_jti ON token_denylist (jti);
-- Job dọn dẹp quét theo expires_at; bản ghi hết hạn thì token cũng hết hạn
CREATE INDEX ix_token_denylist_expires_at ON token_denylist (expires_at);
CREATE INDEX ix_token_denylist_user_id ON token_denylist (user_id);

-- -----------------------------------------------------------------------------
-- 2FA TOTP — bắt buộc cho Admin / Admin HR (conventions.md §4.1, chốt G12)
-- Secret mã hóa AES-256-GCM qua CryptoService, KHÓA NẰM NGOÀI DB.
-- -----------------------------------------------------------------------------
CREATE TABLE user_totp (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    secret_encrypted  TEXT        NOT NULL,
    key_id            VARCHAR(20) NOT NULL,
    enrolled_at       timestamptz NOT NULL DEFAULT now(),
    confirmed_at      timestamptz,
    -- Chống replay: 1 mã TOTP chỉ dùng được đúng 1 lần
    last_used_step    BIGINT,
    created_at        timestamptz NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        timestamptz,
    updated_by        BIGINT,
    version           INTEGER     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_user_totp_user_id ON user_totp (user_id);

COMMENT ON COLUMN user_totp.secret_encrypted IS
    'AES-256-GCM. Khóa ở /opt/songnhue/keys/ — NGOÀI bản backup DB (§9.3). Cấm log, cấm trả ra API';

CREATE TABLE user_recovery_codes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Chỉ lưu hash: mã gốc hiện đúng 1 lần lúc enroll rồi không lấy lại được
    code_hash  CHAR(64)    NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_recovery_codes_hash ON user_recovery_codes (code_hash);
CREATE INDEX ix_user_recovery_codes_user_id ON user_recovery_codes (user_id)
    WHERE used_at IS NULL;
