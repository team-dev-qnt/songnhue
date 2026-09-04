-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  WS-40 · Công tắc khối "Điều hành & số liệu công trình" nay khoá CẢ TRANG, không riêng trang chủ
--
--  Migration này KHÔNG đổi giá trị, KHÔNG đổi tên khoá — chỉ sửa hai cột chữ (`label`,
--  `description`) cho chúng nói đúng thứ mã đang làm.
--
--  ── Vì sao phải sửa mô tả, chứ không phải "để đó cũng được" ─────────────────────────────────
--
--  Mô tả seed ở `V202609011051` khẳng định nguyên văn:
--
--      "Hai trang chi tiết dưới mục Quản lý vận hành KHÔNG bị ảnh hưởng."
--
--  Từ 04/09 câu ấy SAI: công tắc nay ẩn cả mục menu và làm hai trang trả 404. Người vận hành đọc
--  mô tả trên màn hình quản trị để quyết định có gạt hay không — để nguyên là để một câu nói dối
--  ở đúng chỗ người ta tra cứu trước khi bấm. §10.69: một tham số cấu hình *nói dối* khó thấy hơn
--  hẳn một tham số không ai đọc.
--
--  ⛔ CẤM sửa `V202609011051` để chữa câu ấy tại chỗ — Flyway băm CẢ TỆP, nên sửa một dòng chú
--     thích cũng làm ứng dụng không khởi động được (§10.65). Chữa bằng một migration mới là đường
--     duy nhất.
--
--  ── Vì sao KHÔNG đổi tên khoá, dù chữ `home` nay hẹp hơn phạm vi ────────────────────────────
--
--  Đã cân nhắc `site.van-hanh.show` và bỏ, vì hai cái giá đo được:
--
--   1. `PortalSettingsReadTest` quét MỌI khoá xuất hiện trong migration và đòi nó có nơi đọc ở
--      `public-web`. Khoá cũ vẫn nằm trong khối VALUES của `V202609011051` — mà tệp ấy cấm sửa —
--      nên chỉ `UPDATE` tên là bài kiểm đỏ vì một lý do sai; muốn đúng phải kèm `DELETE`.
--   2. Khoá này `exportable = TRUE`. Một bản xuất cấu hình lấy TRƯỚC đợt này, nhập lại SAU, sẽ rơi
--      khoá cũ vào `skippedKeys` — `SettingService.importConfiguration` bỏ qua khoá lạ TRONG IM
--      LẶNG. Công tắc âm thầm về mặc định `true` và khối/trang hiện lại, không một triệu chứng nào.
--
--  ⇒ Giữ tên, sửa nghĩa. Đây là món nợ đặt tên đã cân nhắc và cố ý không trả — ghi ở
--    `master-tracking.md` WS-40 và ở javadoc `lib/khoiVanHanh.ts` để lượt sau không ai "dọn dẹp"
--    nó rồi vấp lại đúng hai điều trên.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

UPDATE settings
   SET label       = 'Hiện mục "Điều hành & số liệu công trình" trên cổng',
       description = 'Bật/tắt CẢ CỤM, không riêng trang chủ. Tắt thì: (1) nhãn nhóm và hai bảng '
                     || 'Mực nước, Vận hành công trình biến mất khỏi trang chủ; (2) hai mục menu '
                     || '"Mực nước, lượng mưa" và "Vận hành công trình" biến mất khỏi thanh điều '
                     || 'hướng, chân trang, dải điều hướng trong mục và cột bên; (3) hai trang ấy '
                     || 'trả về "không tìm thấy" nếu gõ thẳng địa chỉ. Hai mục "Danh mục công '
                     || 'trình" và "Tiến độ sản xuất" KHÔNG bị ảnh hưởng.'
 WHERE setting_key = 'site.home.show-dieu-hanh';

-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  ⛔ ĐẾM LẠI THỨ VỪA GHI — `UPDATE` khớp 0 hàng vẫn thoát 0 và Flyway vẫn báo thành công.
--     Cùng lỗi §10.66: một bộ seed ghi 0 hàng vào một khoá chưa tồn tại, không một dòng log nào.
-- ═══════════════════════════════════════════════════════════════════════════════════════════
DO $$
DECLARE
    so_hang int;
BEGIN
    SELECT count(*) INTO so_hang FROM settings
     WHERE setting_key = 'site.home.show-dieu-hanh'
       AND group_code  = 'SITE'
       AND value_type  = 'BOOLEAN'
       AND editable IS TRUE
       AND description LIKE '%trả về "không tìm thấy"%';
    IF so_hang <> 1 THEN
        RAISE EXCEPTION 'Cần đúng 1 công tắc mang mô tả mới, đếm được % — mô tả cũ khẳng định hai trang chi tiết KHÔNG bị ảnh hưởng, và từ WS-40 câu đó sai', so_hang;
    END IF;
END $$;
