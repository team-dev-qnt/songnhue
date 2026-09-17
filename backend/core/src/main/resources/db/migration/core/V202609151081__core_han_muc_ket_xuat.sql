-- =============================================================================
-- T61.27 — Hạn mức kết xuất vào bảng `settings` (QuanTran chốt 15/09/2026)
--
-- Trước: `RateLimitPolicy.EXPORT` ghi cứng 10 lượt/giờ. Người lập 8 báo cáo BCNS
-- + báo cáo vận hành + thuỷ văn trong một buổi chạm trần ⇒ SYS-0002 giữa buổi.
-- Quy tắc 12: tham số nghiệp vụ nằm ở `settings`, có UI sửa.
--
-- ⚠ `max=100` phải BẰNG `RateLimitPolicy.TRAN_KET_XUAT` — mã kẹp lại ở trần ấy
--   dù cột validation có bị sửa (Admin bị chiếm ⛔ mở được đường rút dữ liệu hàng
--   loạt). `HanMucKetXuatCaiDatHttpTest` đối chiếu hai nơi.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, v.exportable, v.ord
FROM (VALUES
    ('limits.rate.export-per-hour', '30', 'INTEGER',
     'LIMIT', 'Số lượt kết xuất báo cáo mỗi giờ (mỗi người dùng)',
     'Áp cho mọi lượt xuất tệp: báo cáo nhân sự, vận hành, thuỷ văn, danh sách liên hệ, ZIP hồ sơ. Tối đa 100.',
     'min=1;max=100', TRUE, 80)
) AS v(k, val, vtype, grp, label, descr, validation, exportable, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);
