-- =============================================================================
-- WS-2 / T2.10 — Tham số nghiệp vụ (bảng `settings`)
--
-- Nguồn: function-spec.md CN-05.3 "Danh mục tham số cấu hình bắt buộc"
--        + CN-04.9 (thông số phép năm, chốt C1) + G3/G4/G7/G9/G11.
--
-- Nguyên tắc (CLAUDE.md quy tắc 12): cái gì khách nói "để config" thì phải nằm
-- ở đây — có UI sửa, có validate — CHỨ KHÔNG ở application.yml và không hard-code.
--
-- Đây cũng là cơ chế hấp thụ 6 mục nghiệp vụ còn mở: khi Công ty trả lời
-- (G3-a, G9-a…) chỉ cần đổi giá trị, không phải migration.
--
-- ⚠ Giá trị ở đây là MẶC ĐỊNH KHỞI TẠO. Migration sau CẤM ghi đè giá trị Admin
--   đã sửa — thêm tham số mới thì viết file migration mới với INSERT riêng.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, v.exportable, v.ord
FROM (VALUES
    -- === Thông tin Công ty (M5.4) — hiển thị trên cổng TTĐT ===================
    ('company.name', 'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ', 'STRING',
     'COMPANY', 'Tên Công ty', NULL, NULL, TRUE, 10),
    ('company.short-name', 'Thủy lợi Sông Nhuệ', 'STRING',
     'COMPANY', 'Tên viết tắt', NULL, NULL, TRUE, 20),
    ('company.address', '', 'STRING',
     'COMPANY', 'Địa chỉ', NULL, NULL, TRUE, 30),
    ('company.phone', '', 'STRING',
     'COMPANY', 'Điện thoại', NULL, NULL, TRUE, 40),
    ('company.email', '', 'STRING',
     'COMPANY', 'Email liên hệ', NULL, NULL, TRUE, 50),

    -- === Bảo mật (M5.15, M5.16 · chốt F5) ====================================
    ('security.office-hours.start', '08:00', 'TIME',
     'SECURITY', 'Giờ bắt đầu hành chính',
     'Đăng nhập ngoài khung giờ này sinh cảnh báo bất thường (M5.16)', NULL, TRUE, 10),
    ('security.office-hours.end', '17:00', 'TIME',
     'SECURITY', 'Giờ kết thúc hành chính', NULL, NULL, TRUE, 20),
    ('security.password.min-length', '10', 'INTEGER',
     'SECURITY', 'Độ dài mật khẩu tối thiểu', NULL, 'min=8;max=64', TRUE, 30),
    ('security.password.require-letter-and-digit', 'true', 'BOOLEAN',
     'SECURITY', 'Bắt buộc có cả chữ và số', NULL, NULL, TRUE, 40),
    ('security.password.max-age-days', '90', 'INTEGER',
     'SECURITY', 'Số ngày phải đổi mật khẩu', '0 = không bắt buộc đổi định kỳ', 'min=0;max=365', TRUE, 50),
    ('security.login.max-failed-attempts', '5', 'INTEGER',
     'SECURITY', 'Số lần đăng nhập sai bị khóa', NULL, 'min=3;max=20', TRUE, 60),
    ('security.login.failed-window-minutes', '15', 'INTEGER',
     'SECURITY', 'Khoảng thời gian đếm số lần sai (phút)', NULL, 'min=1;max=1440', TRUE, 70),
    ('security.login.lockout-minutes', '15', 'INTEGER',
     'SECURITY', 'Thời gian khóa tạm (phút)', NULL, 'min=1;max=1440', TRUE, 80),

    -- === Thủy văn (M5.5, M5.6 · chốt G3, D5, F2) =============================
    ('hydro.polling.cron', '45 1/2 * * * *', 'CRON',
     'HYDRO', 'Lịch gọi API nguồn',
     '2 phút/lần vào phút lẻ, giây 45. Nguồn cập nhật rải rác trong cửa sổ x1:30 → x8:30 (G3)',
     NULL, TRUE, 10),
    ('hydro.polling.source-frame-minutes', '10', 'INTEGER',
     'HYDRO', 'Độ dài khung cập nhật của nguồn (phút)',
     'Cơ sở để rate-limit: bỏ lượt gọi khi TOÀN BỘ trạm đã có bản ghi của khung hiện tại',
     'min=1;max=60', TRUE, 20),
    ('hydro.polling.timeout-seconds', '30', 'INTEGER',
     'HYDRO', 'Timeout gọi API (giây)', NULL, 'min=5;max=300', TRUE, 30),
    ('hydro.polling.max-retry', '3', 'INTEGER',
     'HYDRO', 'Số lần thử lại khi gọi API lỗi', NULL, 'min=0;max=10', TRUE, 40),
    ('hydro.station.signal-loss-frames', '3', 'INTEGER',
     'HYDRO', 'Số khung không có dữ liệu → coi là mất tín hiệu',
     '3 khung ≈ 30 phút. Trạm mất tín hiệu hiển thị màu XÁM trên GIS (G3)',
     'min=1;max=144', TRUE, 50),
    ('hydro.retention-years', '5', 'INTEGER',
     'HYDRO', 'Số năm lưu dữ liệu chi tiết', 'Chốt D5', 'min=1;max=50', TRUE, 60),
    ('hydro.threshold.default-set', '', 'JSON',
     'HYDRO', 'Bộ ngưỡng mặc định khi tạo điểm đo mới',
     '⬜ Chờ Công ty cung cấp — mục G9-a (business-open-questions.md Phần II)',
     NULL, TRUE, 70),
    ('hydro.quality.suspect-rule', '', 'JSON',
     'HYDRO', 'Quy tắc phân loại bản ghi "Nghi ngờ"',
     'Delta/giờ và khoảng vật lý theo từng loại chỉ số (chốt F2). '
     'Bản ghi NGHI_NGO vẫn nằm ở bảng chính — mọi báo cáo phải lọc quality = HOP_LE',
     NULL, TRUE, 80),

    -- === Vận hành công trình (chốt G4) =======================================
    ('ops.operation-status.stale-days', '7', 'INTEGER',
     'OPERATION', 'Số ngày chưa cập nhật tình hình vận hành → cảnh báo mềm',
     'CN-02.11', 'min=1;max=90', TRUE, 10),

    -- === Nhân sự — thông số phép năm (CN-04.9, chốt C1/C2) ===================
    ('hr.leave.annual-days.under-5-years', '12', 'INTEGER',
     'HR', 'Phép năm — thâm niên dưới 5 năm', 'Điều 113 BLLĐ 2019', 'min=0;max=60', TRUE, 10),
    ('hr.leave.annual-days.5-to-10-years', '13', 'INTEGER',
     'HR', 'Phép năm — thâm niên 5 đến 10 năm', NULL, 'min=0;max=60', TRUE, 20),
    ('hr.leave.annual-days.over-10-years', '14', 'INTEGER',
     'HR', 'Phép năm — thâm niên trên 10 năm', NULL, 'min=0;max=60', TRUE, 30),
    ('hr.leave.special.maternity-days', '180', 'INTEGER',
     'HR', 'Nghỉ thai sản (ngày)', NULL, 'min=0;max=365', TRUE, 40),
    ('hr.leave.special.marriage-days', '3', 'INTEGER',
     'HR', 'Nghỉ cưới (ngày)', NULL, 'min=0;max=30', TRUE, 50),
    ('hr.leave.special.bereavement-days', '3', 'INTEGER',
     'HR', 'Nghỉ tang (ngày)', NULL, 'min=0;max=30', TRUE, 60),
    ('hr.leave.special.health-check-days', '1', 'INTEGER',
     'HR', 'Nghỉ khám sức khỏe (ngày)', NULL, 'min=0;max=30', TRUE, 70),
    ('hr.leave.carry-over-max-days', '5', 'INTEGER',
     'HR', 'Số ngày phép được chuyển sang năm sau', NULL, 'min=0;max=30', TRUE, 80),
    ('hr.leave.prorata-rounding-step', '0.5', 'DECIMAL',
     'HR', 'Bước làm tròn khi tính phép pro-rata', '12 × số tháng / 12, làm tròn theo bước này',
     NULL, TRUE, 90),
    ('hr.leave.seniority-base', 'HIRE_DATE', 'STRING',
     'HR', 'Mốc tính thâm niên', NULL, 'in=HIRE_DATE,CONTRACT_DATE', TRUE, 100),
    ('hr.leave.approval-levels', '1', 'INTEGER',
     'HR', 'Số cấp duyệt đơn nghỉ phép', 'Chốt C2: mặc định 1 cấp (trưởng đơn vị)', 'min=1;max=3', TRUE, 110),
    ('hr.leave.second-level-threshold-days', '0', 'INTEGER',
     'HR', 'Số ngày nghỉ cần thêm cấp duyệt 2', '0 = tắt (mặc định, chốt C2)', 'min=0;max=365', TRUE, 120),
    ('hr.leave.overlap-warning-percent', '30', 'INTEGER',
     'HR', 'Ngưỡng % quân số nghỉ cùng lúc → cảnh báo trùng lịch',
     'Mặc định đề xuất, chưa có số của Công ty', 'min=1;max=100', TRUE, 130),
    ('hr.contract.expiry-warning-days', '30', 'INTEGER',
     'HR', 'Báo trước hợp đồng hết hạn (ngày)', 'M4.9', 'min=1;max=365', TRUE, 140),
    ('hr.certificate.expiry-warning-days', '90', 'INTEGER',
     'HR', 'Báo trước chứng chỉ hết hạn (ngày)', 'M4.9', 'min=1;max=365', TRUE, 150),

    -- === Giới hạn dung lượng (chốt E3) =======================================
    ('limits.max-stations', '0', 'INTEGER',
     'LIMIT', 'Số điểm đo tối đa', '0 = không giới hạn', 'min=0', TRUE, 10),
    ('limits.max-constructions', '0', 'INTEGER',
     'LIMIT', 'Số công trình tối đa', '0 = không giới hạn', 'min=0', TRUE, 20),
    ('limits.max-users', '0', 'INTEGER',
     'LIMIT', 'Số tài khoản tối đa', '0 = không giới hạn', 'min=0', TRUE, 30),
    ('limits.upload.max-mb.image', '10', 'INTEGER',
     'LIMIT', 'Dung lượng tối đa mỗi ảnh (MB)', NULL, 'min=1;max=200', TRUE, 40),
    ('limits.upload.max-mb.document', '50', 'INTEGER',
     'LIMIT', 'Dung lượng tối đa mỗi tài liệu (MB)', NULL, 'min=1;max=500', TRUE, 50),
    ('limits.upload.max-mb.gis', '100', 'INTEGER',
     'LIMIT', 'Dung lượng tối đa mỗi tệp GIS (MB)', 'GeoJSON/KMZ', 'min=1;max=1000', TRUE, 60),

    -- === Thông báo & cảnh báo (chốt B7, G11) =================================
    ('notification.channel.in-app.enabled', 'true', 'BOOLEAN',
     'NOTIFICATION', 'Bật thông báo trong hệ thống', NULL, NULL, TRUE, 10),
    ('notification.channel.email.enabled', 'true', 'BOOLEAN',
     'NOTIFICATION', 'Bật thông báo qua email', NULL, NULL, TRUE, 20),
    ('notification.channel.sms.enabled', 'false', 'BOOLEAN',
     'NOTIFICATION', 'Bật thông báo qua SMS',
     'Chốt B7: v1 TẮT — thông báo qua website và email', NULL, TRUE, 30),
    ('notification.channel.web-push.enabled', 'false', 'BOOLEAN',
     'NOTIFICATION', 'Bật web push', NULL, NULL, TRUE, 40),
    ('notification.alert-group.executive-board', '[]', 'JSON',
     'NOTIFICATION', 'Nhóm nhận cảnh báo "Ban điều hành"',
     'Danh sách publicId tài khoản, Admin sửa (chốt G11). Mặc định rỗng', NULL, TRUE, 50),
    ('notification.alert-group.auto-include-construction-owner', 'true', 'BOOLEAN',
     'NOTIFICATION', 'Tự thêm người phụ trách công trình vào danh sách nhận',
     'Chốt G11: người nhận = nhóm Ban điều hành ∪ người phụ trách công trình liên quan',
     NULL, TRUE, 60),

    -- === Nhật ký hoạt động (chốt G7) =========================================
    ('audit.retention-years', '5', 'INTEGER',
     'AUDIT', 'Số năm giữ nhật ký ở bảng nóng', 'Chốt G7, đồng bộ retention thủy văn D5',
     'min=1;max=50', TRUE, 10),
    ('audit.archive-enabled', 'true', 'BOOLEAN',
     'AUDIT', 'Bật kết xuất lưu trữ khi quá hạn',
     'Quá hạn thì kết xuất ra MinIO + verify checksum RỒI MỚI xóa — không xóa trắng (§4.3)',
     NULL, TRUE, 20),

    -- === Hệ thống ============================================================
    ('system.dashboard.auto-refresh-minutes', '5', 'INTEGER',
     'SYSTEM', 'Chu kỳ tự làm mới dashboard (phút)', 'M2.15', 'min=1;max=60', TRUE, 10),
    ('system.wall.auto-rotate-seconds', '30', 'INTEGER',
     'SYSTEM', 'Thời gian tự chuyển màn hình chế độ wall (giây)', 'M2.15', 'min=5;max=600', TRUE, 20),
    ('system.maintenance-mode', 'false', 'BOOLEAN',
     'SYSTEM', 'Chế độ bảo trì — chặn mọi thao tác ghi',
     'Bật tự động trong lúc khôi phục dữ liệu (M5.11). Chỉ Super Admin thao tác được khi đang bật',
     NULL, FALSE, 30),

    -- === Tích hợp (chốt E3) ==================================================
    ('integration.external-doc.enabled', 'false', 'BOOLEAN',
     'INTEGRATION', 'Bật liên kết hệ thống văn bản điều hành',
     'URL và credential nằm ở biến môi trường, KHÔNG lưu trong bảng này '
     '(CLAUDE.md quy tắc 11, conventions.md §4.7). ⬜ Cách đăng nhập chờ chốt G5',
     NULL, FALSE, 10)
) AS v(k, val, vtype, grp, label, descr, validation, exportable, ord);

-- Đối chiếu số lượng: bắt lỗi copy/paste làm mất dòng ngay tại migration.
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT count(*) INTO v_count FROM settings;
    RAISE NOTICE 'Đã nạp % tham số cấu hình', v_count;

    IF EXISTS (SELECT 1 FROM settings GROUP BY setting_key HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Có setting_key trùng trong seed';
    END IF;
END $$;
