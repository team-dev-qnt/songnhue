-- =============================================================================
-- WS-19 / T19.1 + T19.2 — Tình hình vận hành (CN-02.11)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Danh mục mã tình hình vận hành
--
-- ⛔ Bảng này KHÔNG có org_unit_id (không phải ScopedEntity). Danh mục mã do
--    Công ty vận hành chung (giống categories), gắn phạm vi vào đây là Xí nghiệp
--    này không thấy mã của Xí nghiệp kia.
-- -----------------------------------------------------------------------------
CREATE TABLE operation_status_codes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    -- Mã có tham số đi kèm không (ví dụ "Mở x cm" thì cần nhập x, còn "Đóng kín" thì không)
    has_parameter   BOOLEAN      NOT NULL DEFAULT FALSE,
    parameter_unit  VARCHAR(50),
    -- Màu hex để hiển thị badge, ví dụ: #ff0000
    color_hex       VARCHAR(7)   NOT NULL,
    -- Trạng thái dẫn xuất map sang OperationalStatus. Để trống = giữ nguyên trạng thái hiện tại.
    mapped_status   VARCHAR(20),
    
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    
    public_id       UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      timestamptz,
    updated_by      BIGINT,
    deleted_at      timestamptz,
    version         INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_operation_status_codes_color CHECK (
        color_hex ~ '^#[0-9A-Fa-f]{6}$'
    ),
    -- Phải khớp với tập của OperationalStatus ở bảng constructions
    CONSTRAINT ck_operation_status_codes_mapped CHECK (
        mapped_status IS NULL OR mapped_status IN ('BINH_THUONG', 'CANH_BAO', 'SU_CO', 'BAO_TRI', 'NGUNG_MUA_VU', 'DA_THANH_LY')
    )
);

CREATE UNIQUE INDEX ux_operation_status_codes_code ON operation_status_codes (code) WHERE deleted_at IS NULL;

-- SEED 4 mã cơ bản theo yêu cầu WS-19 (MT / ĐK / ĐTTL / ĐTHL)
INSERT INTO operation_status_codes (code, name, has_parameter, parameter_unit, color_hex, mapped_status, sort_order)
VALUES 
    ('MT', 'Mở tự do', FALSE, NULL, '#10b981', 'BINH_THUONG', 10),
    ('ĐK', 'Đóng kín', FALSE, NULL, '#64748b', 'NGUNG_MUA_VU', 20),
    ('ĐTTL', 'Đóng tiêu thuỷ lợi', FALSE, NULL, '#f59e0b', 'BINH_THUONG', 30),
    ('ĐTHL', 'Đóng xả thuỷ điện', FALSE, NULL, '#ef4444', 'BINH_THUONG', 40);

-- -----------------------------------------------------------------------------
-- Tình hình vận hành của công trình (bảng log append-only)
-- 
-- ⚠ Bảng này THUỘC phạm vi đơn vị (org_unit_id NOT NULL), copy từ công trình
--    xuống để giữ nguyên đơn vị lúc ghi nhận.
-- -----------------------------------------------------------------------------
CREATE TABLE construction_operation_status (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    construction_id     BIGINT       NOT NULL REFERENCES constructions (id) ON DELETE CASCADE,
    org_unit_id         BIGINT       NOT NULL REFERENCES org_units (id),
    
    operation_code_id   BIGINT       NOT NULL REFERENCES operation_status_codes (id),
    parameter_value     NUMERIC(10,2),
    note                TEXT,
    
    -- Thời điểm có hiệu lực. Trực ban có thể nhập bù dữ liệu cho quá khứ, 
    -- nên lấy effective_at để tìm "bản ghi mới nhất" (T19.2).
    effective_at        timestamptz  NOT NULL,
    
    public_id           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          timestamptz,
    updated_by          BIGINT,
    deleted_at          timestamptz,
    version             INTEGER      NOT NULL DEFAULT 0,
    
    CONSTRAINT ck_construction_operation_status_param CHECK (
        -- Việc kiểm tra mã không có tham số mà gửi giá trị lên sẽ được kiểm tra chặt thêm ở Java (T19.3).
        parameter_value IS NULL OR parameter_value >= 0
    )
);

-- Chỉ mục để lấy tình hình hiện hành nhanh nhất (T19.2)
CREATE INDEX ix_construction_operation_status_effective ON construction_operation_status (construction_id, effective_at DESC);
CREATE INDEX ix_construction_operation_status_org ON construction_operation_status (org_unit_id);
