-- =============================================================================
-- WS-75 / T75.4 — Nới trần "Độ dài khung cập nhật của nguồn (phút)"
--
-- Công ty yêu cầu nhập ĐỘ DÀI KHUNG theo bất kỳ số phút nào, trong khi seed
-- `V202608131009` khai `validation = 'min=1;max=60'` ⇒ màn hình Cấu hình hệ thống
-- từ chối mọi giá trị > 60. Seed ấy là migration ĐÃ PHÁT HÀNH (bất biến, kể cả
-- chú thích) nên chỗ sửa là một lượt UPDATE ở đây.
--
-- ⭐ Vì sao trần mới là 1440, ⛔ phải "bỏ hẳn trần"
-- -----------------------------------------------------------------------------
-- Độ dài khung có HAI núm, và chúng phải nói cùng một điều:
--   * núm chung  — khoá `hydro.polling.source-frame-minutes` (bảng `settings`)
--   * núm riêng  — cột `api_sources.frame_minutes`, dùng khi một nguồn lệch nhịp
-- Núm riêng đã bị ràng `ck_api_sources_frame CHECK (… BETWEEN 1 AND 1440)` từ
-- `V202608311049` — cũng là một migration đã phát hành. Bỏ hẳn trần ở núm chung
-- là dựng đúng hình dạng dự án đã trả giá nhiều lần (§10.41 · luật 14): đặt 2000
-- ở màn hình Cấu hình thì được nhận, nhưng gõ 2000 vào một nguồn cụ thể lại bị
-- từ chối — hai nơi nói hai điều về CÙNG một đại lượng, và ⛔ màn hình nào giải
-- thích vì sao.
-- ⇒ 1440 phút = 24 giờ. Đây là trần của cả hai núm, và nó phủ mọi nhịp có nghĩa
--   cho một nguồn quan trắc (nguồn hiện tại đẩy số theo khung 10').
-- Bài `KhungNguonHaiNumTest` đối chiếu hai con số này mỗi lượt chạy ⇒ nới một
-- bên mà quên bên kia là một lượt CI đỏ, ⛔ phải một khoảng lệch nằm im.
--
-- ⚠ Hệ quả người vận hành phải biết TRƯỚC khi nâng số
-- -----------------------------------------------------------------------------
-- Ngưỡng "mất tín hiệu" là TÍCH của hai khoá:
--     khungNguon() × soKhungMatTinHieu()   (`HydroGridService:601`)
-- Đặt khung 240' mà giữ `hydro.station.signal-loss-frames = 3` là dời ngưỡng báo
-- mất tín hiệu từ 30 phút lên 12 giờ — trạm chết nửa ngày vẫn hiện bình thường
-- trên GIS. Câu cảnh báo ấy nay nằm trong `description`, tức ở ĐÚNG màn hình
-- người ta gõ số, ⛔ phải trong một tệp ⛔ ai đọc.
-- =============================================================================
UPDATE settings
   SET validation = 'min=1;max=1440',
       description =
           'Cơ sở để rate-limit: bỏ lượt gọi khi TOÀN BỘ trạm đã có bản ghi của khung hiện tại. '
           'Nhập theo PHÚT, từ 1 đến 1440 (24 giờ) — cùng trần với ô "Khung nguồn riêng" của từng '
           'nguồn dữ liệu. ⚠ Ngưỡng mất tín hiệu = số này × "Số khung không có dữ liệu", nên nâng '
           'khung lên mà giữ nguyên số khung là dời luôn thời điểm hệ báo trạm chết.'
 WHERE setting_key = 'hydro.polling.source-frame-minutes';

DO $$
DECLARE
    so_dong  INTEGER;
    luat     TEXT;
BEGIN
    SELECT count(*), max(validation) INTO so_dong, luat
      FROM settings
     WHERE setting_key = 'hydro.polling.source-frame-minutes';

    IF so_dong <> 1 THEN
        RAISE EXCEPTION 'Chờ đúng 1 hàng `hydro.polling.source-frame-minutes`, đếm được %', so_dong;
    END IF;
    IF luat <> 'min=1;max=1440' THEN
        RAISE EXCEPTION 'Cột validation ⛔ được nới: %', luat;
    END IF;

    -- Đối chiếu ngay tại chỗ với trần của núm riêng. Nếu ai đó đổi CHECK ở một
    -- migration sau mà quên khoá này, lượt migrate sẽ dừng thay vì để hai số lệch.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_api_sources_frame'
           AND pg_get_constraintdef(oid) LIKE '%1440%'
    ) THEN
        RAISE EXCEPTION '`ck_api_sources_frame` ⛔ còn mang trần 1440 — hai núm độ dài khung đã lệch';
    END IF;
END $$;
