-- =============================================================================
-- Bảng tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4
--
-- ⚠ VÌ SAO BẢNG NÀY RA ĐỜI CÙNG LÚC VỚI BIỂU MẪU, KHÔNG SAU
--
-- Chú thích cũ ở `app/lien-he/page.tsx` nói đúng: *"một form gửi đi mà không ai
-- nhận tệ hơn hẳn không có form: người dân tin là đã gửi được"*. Nên lượt này dựng
-- ĐỦ vòng khép kín — nhập → lưu → đọc được ở màn hình quản trị — chứ không dựng ô
-- nhập trước rồi hẹn phần sau.
--
-- ⛔ PHẦN CÒN LẠI CỦA CN-01.4 CHƯA DỰNG, VÀ ĐƯỢC GHI RA THAY VÌ ĐỂ IM
--
--   • reCAPTCHA v3 — chặn bởi G13 (Công ty chưa cấp khoá). Trong lúc chờ, chống
--     lạm dụng dựa vào `RateLimitPolicy.PUBLIC` sẵn có trên tiền tố /api/v1/public.
--   • Email báo có liên hệ mới + email xác nhận cho người gửi.
--   • Bốn trạng thái sau `DA_DOC` (đang xử lý / đã phản hồi / đóng / lưu trữ),
--     phân loại, chuyển phòng ban, ghi chú nội bộ, xuất Excel, nhắc SLA.
--
--   Bốn trạng thái ấy VẪN nằm trong ràng buộc CHECK dưới đây dù chưa dùng: thêm giá
--   trị vào một CHECK đang chạy tốn một migration nữa, còn để sẵn thì không tốn gì.
--   ⚠ Nhưng CHỈ enum là để sẵn — không dựng cột nào cho những chức năng chưa có
--   (luật 15: cột chưa ai đọc là một lỗi, không phải việc để dành).
--
-- ⛔ KHÔNG LƯU ĐỊA CHỈ IP NGƯỜI GỬI
--
-- IP là dữ liệu cá nhân theo NĐ 13/2023, và ở đây nó không phục vụ mục đích nào đã
-- công bố: chống lạm dụng đã do bộ lọc tần suất lo, ngay trong bộ nhớ, không lưu.
-- Thu thập "để đó phòng khi cần" chính là thứ nghị định ấy cấm.
-- =============================================================================

CREATE TABLE contacts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL DEFAULT gen_random_uuid(),

    full_name    VARCHAR(255) NOT NULL,
    -- Một trong hai phải có, ràng buộc ở dưới: không có đường liên lạc ngược thì
    -- bản ghi này là một lời nhắn không thể trả lời.
    email        VARCHAR(255),
    phone        VARCHAR(30),
    subject      VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,

    status       VARCHAR(20)  NOT NULL DEFAULT 'MOI',
    -- Người đọc bản ghi đầu tiên. NULL = chưa ai mở.
    read_by      BIGINT,
    read_at      timestamptz,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   timestamptz,
    updated_by   BIGINT,
    deleted_at   timestamptz,
    version      INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_contacts_status CHECK (
        status IN ('MOI', 'DA_DOC', 'DANG_XU_LY', 'DA_PHAN_HOI', 'DONG', 'LUU_TRU')
    ),
    -- Phải có ít nhất một đường liên lạc ngược.
    CONSTRAINT ck_contacts_lien_lac CHECK (
        (email IS NOT NULL AND length(btrim(email)) > 0)
        OR (phone IS NOT NULL AND length(btrim(phone)) > 0)
    ),
    CONSTRAINT uq_contacts_public_id UNIQUE (public_id)
);

-- Màn hình quản trị luôn mở theo "mới nhất trước, chưa đọc lên đầu".
CREATE INDEX ix_contacts_status_created ON contacts (status, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE contacts IS
    'Liên hệ / phản ánh gửi từ cổng công khai (CN-01.4). KHÔNG lưu IP người gửi — NĐ 13/2023.';
COMMENT ON COLUMN contacts.status IS
    'MOI → DA_DOC là hai trạng thái ĐANG dùng. Bốn giá trị còn lại để sẵn trong CHECK cho phần sau của CN-01.4, chưa có mã nào ghi vào.';
