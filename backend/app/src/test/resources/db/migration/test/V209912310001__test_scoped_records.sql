-- =============================================================================
-- Bảng CHỈ DÙNG CHO TEST — T10.3, kiểm chứng tầng 3 phân quyền đầu-cuối.
--
-- Phase 0 chưa có entity nào thuộc phạm vi đơn vị (công trình, lịch sử sửa chữa,
-- hồ sơ nhân viên… đều thuộc Phase 1+), nên bộ lọc phạm vi chưa có gì để lọc và
-- AUTH-3002 chưa có đường nào chạy qua. Bảng này đóng vai entity nghiệp vụ đầu
-- tiên để chứng minh cơ chế hoạt động TRƯỚC khi Phase 1 dựa vào nó.
--
-- ⚠ Nằm trong `app/src/test/resources`, KHÔNG có trong jar chạy thật, và chỉ
--   được nạp khi test tự thêm `classpath:db/migration/test` vào
--   spring.flyway.locations.
--
-- Version đặt 2099 để luôn chạy SAU mọi migration thật, kể cả migration thêm
-- vào những năm tới — không bao giờ chen vào giữa và làm lệch thứ tự.
-- =============================================================================

CREATE TABLE test_scoped_records (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    title       VARCHAR(200) NOT NULL,
    -- Cột mà bộ lọc phạm vi bám vào. NOT NULL: bản ghi không thuộc đơn vị nào
    -- thì không ai nhìn thấy nó, kể cả người ở nút gốc.
    org_unit_id BIGINT       NOT NULL REFERENCES org_units (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  timestamptz,
    updated_by  BIGINT,
    deleted_at  timestamptz,
    version     INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_test_scoped_records_public_id ON test_scoped_records (public_id);

-- Vai trò runtime phải đọc/ghi được, y như với bảng nghiệp vụ thật.
GRANT SELECT, INSERT, UPDATE, DELETE ON test_scoped_records TO songnhue_app;
