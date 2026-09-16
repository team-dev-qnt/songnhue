-- =============================================================================
-- T61.41 / T61.44 — Cấu hình hệ thống từ giao diện: tình trạng cấu hình + bí mật tích hợp
--
-- Quyết định 15/09/2026 (architecture-review.md §12.1): phần lớn biến môi trường
-- PHẢI ở lại `.env` (cần trước khi có CSDL · tiến trình khác đọc · kênh cảnh báo ·
-- gác môi trường · SMTP · công tắc bảo mật). Hai thứ lên giao diện:
--
--   1. MÀN HÌNH TÌNH TRẠNG — chỉ báo "đã đặt / thiếu / ngoài tầm nhìn", ⛔ bao giờ
--      trả giá trị. Quyền `adm:system-config:view`.
--   2. BÍ MẬT TÍCH HỢP do Công ty cấp, ứng dụng là người đọc DUY NHẤT (hôm nay:
--      khoá bí mật reCAPTCHA). Ghi một chiều, AES-256-GCM, nhập lại mã 2FA.
--      Quyền `adm:system-config:secret`.
--
-- ⛔⛔ Cả hai quyền CHỈ cấp cho SUPER_ADMIN. ⛔ Không cấp ADMIN: vai trò ADMIN
--    `is_system = FALSE`, sửa được — mà người giữ khoá bí mật phải là vai trò ⛔
--    ai nới ra được. Mã còn kiểm vai trò SUPER_ADMIN TƯỜNG MINH (cùng khuôn khôi
--    phục CSDL) và T54.4 chặn tự cấp quyền mình ⛔ có.
--
-- ⛔ Bảng KHÔNG nằm trong `settings`: nhóm SITE của bảng ấy đi thẳng ra endpoint
--    công khai, và `SettingView` trả nguyên giá trị (RecaptchaKhoaBiMatTest).
-- =============================================================================

CREATE TABLE integration_secrets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- Mã trong danh mục `LoaiBiMat` của mã nguồn. ⛔ Danh mục ở MÃ chứ ⛔ ở bảng:
    -- thêm một bí mật mới là thêm một người đọc mới, tức phải deploy (quy tắc 15).
    secret_code  VARCHAR(80)  NOT NULL,
    -- `<key_id>:<base64(iv‖ct‖tag)>` — cùng dạng `api_sources.credential`.
    ciphertext   TEXT         NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   timestamptz,
    updated_by   BIGINT,
    deleted_at   timestamptz,
    version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_integration_secrets_public_id UNIQUE (public_id),
    CONSTRAINT ck_integration_secrets_code CHECK (secret_code ~ '^[A-Z][A-Z0-9_]{2,79}$'),
    CONSTRAINT ck_integration_secrets_ciphertext CHECK (ciphertext ~ '^[A-Za-z0-9_-]+:.+$')
);

-- Xoá là xoá CỨNG (bí mật đã gỡ ⛔ có lý do nằm lại); `deleted_at` giữ cho khớp BaseEntity.
CREATE UNIQUE INDEX uq_integration_secrets_code ON integration_secrets (secret_code);

COMMENT ON TABLE integration_secrets IS
    'Bí mật tích hợp do Công ty cấp, sửa trên giao diện (T61.44). ⛔ Không trả ra API, ⛔ không log, ⛔ không xuất cấu hình.';

-- -----------------------------------------------------------------------------
-- Quyền — ⛔ `CROSS JOIN` lúc seed chạy MỘT LẦN, quyền mới phải tự xin vai trò.
-- -----------------------------------------------------------------------------
INSERT INTO permissions (code, module, resource, action, name)
VALUES ('adm:system-config:view', 'adm', 'system-config', 'view', 'Xem tình trạng cấu hình hệ thống'),
       ('adm:system-config:secret', 'adm', 'system-config', 'secret', 'Đặt/xoá bí mật tích hợp')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE p.code IN ('adm:system-config:view', 'adm:system-config:secret')
   AND r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    v_so INT;
    v_ngoai INT;
BEGIN
    SELECT count(*) INTO v_so
      FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
      JOIN roles r ON r.id = rp.role_id
     WHERE p.code IN ('adm:system-config:view', 'adm:system-config:secret') AND r.code = 'SUPER_ADMIN';
    IF v_so <> 2 THEN
        RAISE EXCEPTION 'SUPER_ADMIN mới nhận % / 2 quyền cấu hình hệ thống', v_so;
    END IF;

    SELECT count(*) INTO v_ngoai
      FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
      JOIN roles r ON r.id = rp.role_id
     WHERE p.code LIKE 'adm:system-config:%' AND r.code <> 'SUPER_ADMIN';
    IF v_ngoai <> 0 THEN
        RAISE EXCEPTION 'Quyền cấu hình hệ thống lọt sang % dòng vai trò khác SUPER_ADMIN', v_ngoai;
    END IF;
END $$;
