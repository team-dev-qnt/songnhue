-- =============================================================================
-- CN-01.4 — email báo/xác nhận + nhắc SLA. WS-36 / T36.3 + T36.4.
--
-- ⛔⛔ HAI KHOÁ, VÀ CẢ HAI ĐỀU CÓ NƠI ĐỌC TRONG CÙNG COMMIT NÀY
--
--   `cms.contact.ack-email-enabled` → `ContactService.tiepNhan()`
--   `cms.contact.sla-hours`         → `ContactScheduler.quetSla()` + `ContactSlaHandler`
--
--   Luật 15 của dự án: một công tắc chưa ai đọc là một LỖI, ⛔ không phải việc để
--   dành. Lượt 28/8 tìm ra sáu chỗ thiếu đúng một nửa cặp đọc–ghi, bốn trong số
--   đó ra đời một ngày trước từ một đợt cẩn thận, có bài kiểm, có nghiệm thu.
--
-- ⛔ ⛔ KHÔNG seed khoá reCAPTCHA (T36.6)
--   Nó bị chặn bởi G13 (Công ty chưa cấp khoá) và ⛔ chưa có dòng mã nào đọc.
--   Seed sẵn "cho đỡ phải nhớ" là dựng đúng thứ luật 15 cấm — và tệ hơn, nó làm
--   màn hình Cấu hình hiện một ô trông như đã bật xong một lớp chống spam ⛔
--   không tồn tại.
--
-- ⭐ VÌ SAO `sla-hours = 0` LÀ CÔNG TẮC TẮT, ⛔ KHÔNG PHẢI MỘT KHOÁ THỨ HAI
--   Một khoá `sla-enabled` riêng nghe sạch hơn, nhưng nó tạo ra một trạng thái
--   ⛔ không có nghĩa: `enabled = true, hours = 0`. Hai khoá cho một quyết định
--   là hai nơi phải nhớ, và cái ở dưới sẽ ⛔ không ai để ý khi cái ở trên đổi
--   (luật 14). Một khoá, một câu hỏi: *"bao lâu thì nhắc — 0 là không nhắc"*.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, 'CMS', v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    ('cms.contact.ack-email-enabled', 'true', 'BOOLEAN',
     'Gửi thư xác nhận cho người gửi liên hệ',
     'CN-01.4. Tắt khi máy chủ thư chưa sẵn sàng hoặc khi Công ty ⛔ không muốn trả lời tự động. '
     'Thư báo cho CÁN BỘ (kênh thông báo nội bộ) ⛔ không chịu ảnh hưởng của khoá này.',
     NULL, 20),
    ('cms.contact.sla-hours', '48', 'INTEGER',
     'Hạn xử lý một liên hệ (giờ) — 0 là không nhắc',
     'CN-01.4 / SRS UC1.3. Quá hạn mà liên hệ vẫn chưa tới Đã phản hồi / Đóng / Lưu trữ thì hệ '
     'thống nhắc người có quyền cms:contact:manage, MỖI NGÀY MỘT LẦN. Đặt 0 để tắt hẳn việc nhắc.',
     'min=0;max=720', 21)
) AS v(k, val, vtype, label, descr, validation, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);


-- -----------------------------------------------------------------------------
-- Chỉ mục cho câu quét SLA
--
-- ⚠ Câu quét chạy MỖI NGÀY và lọc theo `status IN (...) AND created_at < ?`.
--   `ix_contacts_status_created` (V202608291041) đã có `(status, created_at DESC)`
--   nên nó phục vụ được — ⛔ không thêm chỉ mục thứ hai cho cùng một câu.
--   Ghi ra đây thay vì để người sau tự hỏi và tạo thừa.
-- -----------------------------------------------------------------------------


-- =============================================================================
-- Kiểm ngay trong migration — một `INSERT ... WHERE NOT EXISTS` khớp hụt chèn 0
-- hàng và Flyway vẫn xanh trọn vẹn (§10.66).
-- =============================================================================
DO $$
DECLARE
    so_khoa INTEGER;
BEGIN
    SELECT count(*) INTO so_khoa
      FROM settings
     WHERE setting_key IN ('cms.contact.ack-email-enabled', 'cms.contact.sla-hours');
    IF so_khoa <> 2 THEN
        RAISE EXCEPTION 'Phải có đủ 2 khoá cấu hình liên hệ, đang có %', so_khoa;
    END IF;
END $$;
