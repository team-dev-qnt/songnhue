-- T11.34 — `system_backups.trigger_type` nhận thêm 'PRE_DEPLOY'.
--
-- =============================================================================
-- ⛔⛔ VÌ SAO MỘT GIÁ TRỊ THIẾU LẠI LÀ MỘT KHUYẾT TẬT, KHÔNG PHẢI VIỆC ĐỂ DÀNH
-- =============================================================================
--
-- `pre-deploy-dump.sh` chụp CSDL ngay trước mỗi lượt triển khai — đó là **điểm
-- quay lui duy nhất** của một lượt deploy hỏng. Nó ghi sổ đăng ký với
-- `trigger_type = 'MANUAL'` vì ràng buộc chưa có giá trị nào đúng nghĩa, và
-- chú thích ở chính script ấy khai thẳng:
--
--   "Ghi 'MANUAL' cho đúng ràng buộc; tên tệp `predeploy-*` và `file_path` vẫn
--    phân biệt được."
--
-- ⛔ *"Vẫn phân biệt được"* là đúng với **người** đọc màn hình, và sai với **máy**:
--    mọi truy vấn lọc theo `trigger_type` — thống kê, cảnh báo "quá 26 giờ", và
--    bất kỳ chính sách giữ/dọn nào viết sau này — đều trộn bản chụp trước deploy
--    vào cùng rổ với bản người dùng bấm tay. Phân biệt bằng **tiền tố tên tệp**
--    là một quy ước sống trong trí nhớ con người, đúng hình dạng mà quy tắc 16
--    và luật 14 nói tới.
--
-- =============================================================================
-- ⚠ RÀNG BUỘC PHẢI DỰNG LẠI, KHÔNG "SỬA" ĐƯỢC
-- =============================================================================
--
-- Postgres không cho ALTER một CHECK tại chỗ. Bỏ rồi thêm lại là đường duy nhất,
-- và nó chạy được cả trên CSDL rỗng lẫn CSDL đã có dữ liệu — hàng cũ mang
-- 'MANUAL'/'SCHEDULED'/'PRE_RESTORE' vẫn thoả ràng buộc mới (nó chỉ NỚI RỘNG).
--
-- ⛔ Cố ý KHÔNG cập nhật ngược các hàng `predeploy-*` cũ thành 'PRE_DEPLOY':
--    lịch sử phải giữ nguyên thứ đã thật sự được ghi. Viết lại quá khứ để bảng
--    trông sạch là xoá mất bằng chứng rằng cột này từng không phân biệt được.
--
-- ⚠ Định dạng khối `IN (...)` bên dưới KHÔNG tuỳ tiện: `EnumBaNoiTest` bóc danh
--   sách giá trị từ chính tệp này để đối chiếu với enum Java và union TypeScript.
--   Bài `boDocKhongChayQuaTapRong` sẽ ĐỎ nếu bộ đọc không bóc được giá trị nào.

ALTER TABLE system_backups DROP CONSTRAINT IF EXISTS ck_system_backups_trigger;

ALTER TABLE system_backups
    ADD CONSTRAINT ck_system_backups_trigger CHECK (
        trigger_type IN ('SCHEDULED', 'MANUAL', 'PRE_RESTORE', 'PRE_DEPLOY')
    );

COMMENT ON COLUMN system_backups.trigger_type IS
    'Nguồn gốc bản chụp: SCHEDULED (job 02:00) · MANUAL (người bấm nút) · '
    'PRE_RESTORE (chụp bắt buộc trước khi khôi phục ghi đè) · '
    'PRE_DEPLOY (chụp trước mỗi lượt triển khai — điểm quay lui của lượt deploy).';

-- =============================================================================
-- Kiểm NGAY TRONG migration — một ALTER khớp hụt vẫn để Flyway xanh (§10.66).
-- =============================================================================
DO $$
DECLARE
    dinh_nghia text;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO dinh_nghia
      FROM pg_constraint WHERE conname = 'ck_system_backups_trigger';

    IF dinh_nghia IS NULL THEN
        RAISE EXCEPTION 'T11.34: ck_system_backups_trigger biến mất sau lượt dựng lại';
    END IF;
    IF position('PRE_DEPLOY' IN dinh_nghia) = 0 THEN
        RAISE EXCEPTION 'T11.34: ràng buộc chưa nhận PRE_DEPLOY — đang là %', dinh_nghia;
    END IF;
    -- Ba giá trị cũ phải CÒN NGUYÊN. Một lượt "nới rộng" mà đánh rơi giá trị cũ
    -- sẽ không lộ ra cho tới lần ghi kế tiếp của job 02:00.
    IF position('SCHEDULED' IN dinh_nghia) = 0
       OR position('MANUAL' IN dinh_nghia) = 0
       OR position('PRE_RESTORE' IN dinh_nghia) = 0 THEN
        RAISE EXCEPTION 'T11.34: lượt dựng lại đánh rơi giá trị cũ — đang là %', dinh_nghia;
    END IF;
END $$;
