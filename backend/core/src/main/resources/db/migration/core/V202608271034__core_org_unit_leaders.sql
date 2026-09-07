-- =============================================================================
-- Danh bạ lãnh đạo công bố ra cổng — CR-25, CR-26
--
-- Nguồn: "YÊU CẦU CHỈNH SỬA WEBSITE" v1.0 ngày 27/08/2026.
--   • CR-25 "Lãnh đạo Công ty": bảng 3 cột — Họ và tên / Chức danh / Điện thoại.
--   • CR-26 "Xí nghiệp trực thuộc": bảng 6 cột — Tên XN / Địa chỉ / Điện thoại /
--     Email / Họ và tên Giám đốc XN / Điện thoại liên hệ.
--
-- ⚠ VÌ SAO MỘT BẢNG CON, KHÔNG PHẢI HAI CỘT `head_name` / `head_phone`
--
-- Hai cột chỉ chứa được MỘT người mỗi đơn vị, mà CR-25 là một danh sách: Chủ tịch,
-- Giám đốc, các Phó Giám đốc. Dựng hai cột cho CR-26 rồi dựng thêm một cơ chế khác
-- cho CR-25 là hai nơi trả lời cùng một câu hỏi "ai đứng đầu đơn vị này" — đúng
-- hình dạng mà luật 14 nói là phải tránh. Một bảng phục vụ cả hai: CR-25 đọc các
-- dòng của nút Công ty, CR-26 đọc dòng đầu của từng Xí nghiệp.
--
-- ⚠⚠ ĐÂY KHÔNG PHẢI HỒ SƠ NHÂN SỰ, VÀ SỰ TÁCH BIỆT ẤY LÀ CÓ CHỦ ĐÍCH
--
-- Toàn bộ nội dung bảng này là thông tin Công ty **chủ động công bố** trên cổng —
-- cùng loại với số điện thoại in trên bảng hiệu. Nó KHÔNG nối vào `employees` của
-- MOD-04, nên endpoint công khai đọc nó không có đường nào chạm tới trường nhạy cảm
-- (quy tắc 10, NĐ 13/2023). Nối hai thứ lại thì mỗi lần sửa lược đồ nhân sự đều
-- phải chứng minh lại rằng không có gì rò ra cổng.
--
-- ⚠ Phân biệt với `org_units.head_user_id`: cột ấy trỏ tới một TÀI KHOẢN, dùng cho
--   luồng duyệt và thông báo. Người đứng đầu có thể không có tài khoản (Giám đốc Xí
--   nghiệp), và một tài khoản không phải là một dòng danh bạ. Hai khái niệm khác
--   nhau, cố ý không gộp.
--
-- ⛔ KHÔNG seed dòng nào. Danh sách người thật và số điện thoại thật thuộc OI-05
--   (Công ty còn phải chốt 7 hay 8 Xí nghiệp). Bảng rỗng ⇒ cổng nói thẳng là chưa
--   có, đúng luật 16 — không bịa một cái tên cho bảng trông có nội dung (§10.54).
-- =============================================================================

CREATE TABLE org_unit_leaders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_unit_id  BIGINT       NOT NULL REFERENCES org_units (id) ON DELETE CASCADE,
    full_name    VARCHAR(255) NOT NULL,
    -- "Chủ tịch Công ty", "Giám đốc", "Phó Giám đốc", "Giám đốc Xí nghiệp"…
    title        VARCHAR(255) NOT NULL,
    -- Điện thoại liên hệ công vụ. Cho phép NULL: Công ty có thể công bố tên và chức
    -- danh mà chưa muốn công bố số — ô trống trung thực hơn một số cũ (luật 16).
    phone        VARCHAR(30),
    email        VARCHAR(255),
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    -- Tắt một dòng khi người đó chuyển công tác, giữ lại để đối chiếu lịch sử.
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   timestamptz,
    updated_by   BIGINT,
    deleted_at   timestamptz,
    version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_org_unit_leaders_name CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT ck_org_unit_leaders_title CHECK (length(btrim(title)) > 0)
);

CREATE UNIQUE INDEX uq_org_unit_leaders_public_id ON org_unit_leaders (public_id);
CREATE INDEX ix_org_unit_leaders_unit ON org_unit_leaders (org_unit_id, sort_order)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE org_unit_leaders IS
    'Danh bạ lãnh đạo Công ty & Giám đốc Xí nghiệp công bố trên cổng TTĐT (CR-25, CR-26). '
    'KHÔNG phải hồ sơ nhân sự MOD-04 — không có trường nhạy cảm, không nối employees.';
COMMENT ON COLUMN org_unit_leaders.title IS
    'Chức danh hiển thị nguyên văn ở cột 2 của bảng Lãnh đạo Công ty.';

-- ⚠ KHÔNG có khối GRANT ở đây, và đó là đúng: `V202608131006` đã đặt
--   ALTER DEFAULT PRIVILEGES cho vai trò chạy migration, nên mọi bảng sinh sau đó
--   tự có quyền cho `songnhue_app` / `songnhue_readonly`. Viết lại GRANT ở từng
--   migration là dựng một cơ chế thứ hai cho cùng việc — và cơ chế thứ hai ấy sẽ
--   im lặng lệch đi ngay lần đầu ai đó quên (cùng khuôn với `V202608161010` và
--   `V202608211026`, cả hai đều không GRANT).
