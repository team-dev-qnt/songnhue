-- =============================================================================
-- Hạn mức dung lượng tệp đính kèm theo bản ghi (WS-12 / T12.6)
--
-- CN-02.3: mỗi công trình tối đa 500MB tài liệu. Khoá đặt theo LOẠI CHỦ SỞ HỮU
-- (`owner_type` của bảng `attachments`), nên loại nào không khai khoá thì loại
-- đó không giới hạn — hạn mức là ngoại lệ cho vài loại hồ sơ nặng, không phải
-- luật chung.
--
-- ⚠ Ghi lại một lỗi im lặng phát hiện khi làm task này. `AttachmentService` từ
--   WS-6 đọc khoá `limit.upload.max-file-mb` — khoá đó CHƯA TỪNG ĐƯỢC SEED, nên
--   mọi lượt tải rơi về giá trị dự phòng 20MB cứng trong mã. Cùng lúc, ba tham
--   số `limits.upload.max-mb.*` seed ở V202608131009 KHÔNG có dòng mã nào đọc.
--
--   Triệu chứng phía người dùng: quản trị viên sửa "Dung lượng tối đa mỗi tài
--   liệu = 50MB", tải một hồ sơ hoàn công 30MB, vẫn bị từ chối — và không thông
--   báo nào chỉ ra vì sao. Đã sửa ở tầng mã; bài kiểm `AttachmentQuotaTest`
--   chứng minh đổi tham số thì hành vi đổi theo, thay vì chỉ chứng minh nó đọc
--   được một con số nào đó.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, v.exportable, v.ord
FROM (VALUES
    ('limits.attachment.quota-mb.CONSTRUCTION', '500', 'INTEGER',
     'LIMIT', 'Tổng dung lượng tài liệu mỗi công trình (MB)',
     'CN-02.3. 0 = không giới hạn', 'min=0;max=10000', TRUE, 70)
) AS v(k, val, vtype, grp, label, descr, validation, exportable, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);
