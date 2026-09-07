-- =============================================================================
-- WS-2 / T2.9 — Danh mục quyền + vai trò + ma trận phân quyền khởi tạo
--
-- Nguồn: function-spec.md §6 (ma trận RBAC) + §0.3 (danh sách vai trò)
--        + CN-05.x (MOD-05). Permission dạng module:resource:action, deny by default.
--
-- ⚠ THÊM QUYỀN/VAI TRÒ MỚI = THÊM FILE MIGRATION MỚI, không sửa file này
--   (conventions.md §1.2 — cấm sửa migration đã merge). Chính vì vậy danh mục
--   quyền để ở migration V chứ không dùng repeatable R__: lịch sử thay đổi
--   quyền phải truy vết được, không được ghi đè âm thầm.
--
-- ⚠ Ma trận dưới đây là CẤU HÌNH KHỞI TẠO. Sau khi hệ thống chạy, Admin sửa
--   role_permissions qua UI (CN-05.2) và migration sau KHÔNG được ghi đè lại.
--
-- 📌 Độ chi tiết của quyền MOD-01 (cms) và MOD-04 (hr) bám theo phần tóm tắt
--   cuối §6; Phase 1 / Phase 3 sẽ bổ sung quyền mịn hơn bằng migration mới.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Danh mục quyền
-- -----------------------------------------------------------------------------
INSERT INTO permissions (code, module, resource, action, name)
SELECT v.module || ':' || v.resource || ':' || v.action, v.module, v.resource, v.action, v.name
FROM (VALUES
    -- === MOD-05 Quản trị hệ thống (adm) ======================================
    ('adm', 'user',           'view',           'Xem danh sách tài khoản'),
    ('adm', 'user',           'create',         'Tạo tài khoản'),
    ('adm', 'user',           'update',         'Sửa tài khoản'),
    ('adm', 'user',           'lock',           'Khóa / mở khóa tài khoản'),
    ('adm', 'user',           'reset-password', 'Đặt lại mật khẩu'),
    ('adm', 'user',           'assign-role',    'Gán vai trò cho tài khoản'),
    ('adm', 'role',           'view',           'Xem vai trò và ma trận quyền'),
    ('adm', 'role',           'manage',         'Tạo/sửa vai trò và phân quyền'),
    ('adm', 'org-unit',       'view',           'Xem sơ đồ đơn vị'),
    ('adm', 'org-unit',       'manage',         'Tạo/sửa/sắp xếp đơn vị'),
    ('adm', 'setting',        'view',           'Xem cấu hình hệ thống'),
    ('adm', 'setting',        'update',         'Sửa cấu hình hệ thống'),
    ('adm', 'setting',        'export',         'Xuất bộ cấu hình (M5.17)'),
    ('adm', 'setting',        'import',         'Nhập bộ cấu hình (M5.17)'),
    ('adm', 'audit',          'view',           'Tra cứu nhật ký hoạt động'),
    ('adm', 'audit',          'verify',         'Kiểm tra tính toàn vẹn chuỗi hash nhật ký'),
    ('adm', 'backup',         'view',           'Xem trạng thái sao lưu'),
    ('adm', 'backup',         'create',         'Sao lưu theo yêu cầu (M5.10)'),
    ('adm', 'backup',         'restore',        'Khôi phục dữ liệu (M5.11 — yêu cầu 2FA)'),
    ('adm', 'health',         'view',           'Xem tình trạng dịch vụ và tích hợp'),
    ('adm', 'notification',   'broadcast',      'Gửi thông báo hệ thống (M5.13)'),
    ('adm', 'session',        'view',           'Xem phiên đăng nhập (M5.14)'),
    ('adm', 'session',        'revoke',         'Đăng xuất từ xa một phiên'),
    ('adm', 'security-event', 'view',           'Xem sự kiện bảo mật'),

    -- === MOD-01 Cổng thông tin điện tử (cms) =================================
    ('cms', 'article',        'view',           'Xem bài viết'),
    ('cms', 'article',        'create',         'Soạn bài viết'),
    ('cms', 'article',        'update',         'Sửa bài viết'),
    ('cms', 'article',        'delete',         'Xóa bài viết'),
    ('cms', 'article',        'submit',         'Gửi duyệt bài viết'),
    ('cms', 'article',        'approve',        'Duyệt bài viết'),
    ('cms', 'article',        'publish',        'Xuất bản bài viết'),
    ('cms', 'article',        'unpublish',      'Gỡ bài viết đã xuất bản'),
    ('cms', 'category',       'manage',         'Quản lý danh mục nội dung'),
    ('cms', 'media',          'manage',         'Quản lý thư viện media'),
    ('cms', 'banner',         'manage',         'Quản lý banner'),
    ('cms', 'layout',         'manage',         'Cấu hình giao diện cổng thông tin'),
    ('cms', 'contact',        'manage',         'Xử lý liên hệ từ người dân'),
    ('cms', 'feedback',       'manage',         'Xử lý phản hồi / đánh giá'),
    ('cms', 'external-doc',   'view',           'Xem liên kết hệ thống văn bản điều hành'),
    ('cms', 'external-doc',   'link',           'Tự liên kết mã số cá nhân (CN-01.7)'),
    ('cms', 'external-doc',   'manage-flag',    'Đánh dấu văn bản công khai (cán bộ văn thư)'),

    -- === MOD-02 Vận hành công trình (ops) ====================================
    ('ops', 'construction',   'view',           'Xem danh mục công trình'),
    ('ops', 'construction',   'create',         'Thêm hồ sơ công trình'),
    ('ops', 'construction',   'update',         'Sửa hồ sơ công trình'),
    ('ops', 'construction',   'delete',         'Xóa / thanh lý công trình'),
    ('ops', 'maintenance',    'view',           'Xem lịch sử sửa chữa / bảo trì / sự cố'),
    ('ops', 'maintenance',    'create',         'Ghi lịch sử sửa chữa / bảo trì'),
    ('ops', 'maintenance',    'update',         'Sửa bản ghi sửa chữa đã lưu'),
    ('ops', 'maintenance',    'delete',         'Xóa bản ghi sửa chữa đã lưu'),
    ('ops', 'maintenance',    'report-incident', 'Ghi nhận sự cố'),
    ('ops', 'maintenance',    'close-incident', 'Đóng bản ghi sự cố (chuyển Đã xử lý)'),
    ('ops', 'operation-status', 'view',         'Xem tình hình vận hành công trình'),
    ('ops', 'operation-status', 'update',       'Cập nhật tình hình vận hành cống (CN-02.11)'),
    ('ops', 'operation-status-code', 'manage',  'Quản lý danh mục mã tình hình vận hành'),
    ('ops', 'document',       'view',           'Xem tài liệu / hình ảnh công trình'),
    ('ops', 'document',       'upload',         'Tải lên tài liệu công trình'),
    ('ops', 'document',       'delete',         'Xóa tài liệu công trình'),
    ('ops', 'gis-layer',      'view',           'Xem bản đồ GIS'),
    ('ops', 'gis-layer',      'manage',         'Tải lên / quản lý lớp bản đồ GIS'),
    ('ops', 'dashboard',      'view',           'Xem dashboard điều hành'),
    ('ops', 'change-log',     'view',           'Xem nhật ký thay đổi hồ sơ công trình'),
    ('ops', 'report',         'view',           'Xem báo cáo vận hành công trình'),
    ('ops', 'report',         'export',         'Tạo / xuất báo cáo vận hành công trình'),

    -- === MOD-03 Thủy văn (hyd) ===============================================
    ('hyd', 'station',        'view',           'Xem danh mục điểm đo'),
    ('hyd', 'station',        'manage',         'Quản lý điểm đo và loại chỉ số'),
    ('hyd', 'api-source',     'manage',         'Cấu hình API nguồn dữ liệu'),
    ('hyd', 'measurement',    'view',           'Xem dữ liệu quan trắc'),
    ('hyd', 'measurement',    'review',         'Duyệt / xóa bản ghi Nghi ngờ'),
    ('hyd', 'threshold',      'view',           'Xem ngưỡng cảnh báo'),
    ('hyd', 'threshold',      'manage',         'Cấu hình ngưỡng cảnh báo'),
    ('hyd', 'alert',          'view',           'Xem cảnh báo thủy văn'),
    ('hyd', 'alert',          'handle',         'Đóng / xử lý cảnh báo'),
    ('hyd', 'alert-group',    'manage',         'Quản lý nhóm nhận cảnh báo Ban điều hành'),
    ('hyd', 'report',         'view',           'Xem báo cáo thủy văn'),
    ('hyd', 'report',         'export',         'Tạo / xuất báo cáo thủy văn'),

    -- === MOD-04 Nhân sự (hr) =================================================
    ('hr',  'employee',       'view',           'Xem hồ sơ cán bộ nhân viên'),
    ('hr',  'employee',       'create',         'Thêm hồ sơ nhân viên'),
    ('hr',  'employee',       'update',         'Sửa hồ sơ nhân viên'),
    ('hr',  'employee',       'delete',         'Xóa hồ sơ nhân viên'),
    ('hr',  'employee',       'view-sensitive', 'Xem trường nhạy cảm (CCCD, lương, sức khỏe)'),
    ('hr',  'org-chart',      'view',           'Xem sơ đồ tổ chức'),
    ('hr',  'directory',      'view',           'Xem danh bạ nội bộ'),
    ('hr',  'contract',       'manage',         'Quản lý hợp đồng và tài liệu nhân sự'),
    ('hr',  'leave',          'request',        'Đăng ký nghỉ phép'),
    ('hr',  'leave',          'view-all',       'Xem đơn nghỉ phép của đơn vị'),
    ('hr',  'leave',          'approve',        'Duyệt / từ chối đơn nghỉ phép'),
    ('hr',  'report',         'view',           'Xem báo cáo nhân sự'),
    ('hr',  'report',         'export',         'Xuất báo cáo nhân sự')
) AS v(module, resource, action, name);

-- -----------------------------------------------------------------------------
-- 2. Vai trò (function-spec.md §0.3)
-- is_system = TRUE → Admin không sửa quyền, không xóa được.
-- -----------------------------------------------------------------------------
INSERT INTO roles (code, name, description, is_system)
VALUES
    ('SUPER_ADMIN',     'Quản trị tối cao',
     'Toàn quyền hệ thống, kể cả khôi phục dữ liệu. Không sửa/xóa được. Bắt buộc 2FA.', TRUE),
    ('ADMIN',           'Quản trị hệ thống',
     'Cấu hình, tài khoản, phân quyền, API nguồn, audit, backup. KHÔNG xem được trường nhạy cảm HR.', FALSE),
    ('ADMIN_HR',        'Quản trị nhân sự',
     'Toàn quyền dữ liệu HRM, gồm cả trường nhạy cảm 🔒. Bắt buộc 2FA.', FALSE),
    ('CONTENT_EDITOR',  'Biên tập viên',
     'Soạn thảo bài viết và media — không tự xuất bản.', FALSE),
    ('CONTENT_MANAGER', 'Quản trị nội dung',
     'Duyệt, xuất bản, gỡ bài; quản lý danh mục, banner, liên hệ, phản hồi.', FALSE),
    ('CLERK',           'Cán bộ văn thư',
     'Đánh dấu văn bản điều hành công khai.', FALSE),
    ('TECHNICIAN',      'Cán bộ kỹ thuật',
     'Hồ sơ công trình, GIS, điểm đo, ngưỡng, ghi nhận và khắc phục sự cố.', FALSE),
    ('XN_MANAGER',      'Quản lý Xí nghiệp',
     'Duyệt hồ sơ, đóng sự cố, báo cáo trong phạm vi Xí nghiệp mình.', FALSE),
    ('XN_OPERATOR',     'Cán bộ vận hành Xí nghiệp',
     'Ghi nhận sự cố và cập nhật tình hình vận hành trong phạm vi Xí nghiệp mình.', FALSE),
    ('DUTY_OFFICER',    'Trực ban điều hành',
     'Nhận cảnh báo ngưỡng, theo dõi dashboard màn hình lớn.', FALSE),
    ('EXECUTIVE',       'Ban giám đốc / Điều hành',
     'Xem dashboard điều hành và báo cáo tổng hợp đa chiều.', FALSE),
    ('VIEWER',          'Cán bộ nội bộ',
     'Chỉ xem theo phạm vi đơn vị được phân.', FALSE);

-- -----------------------------------------------------------------------------
-- 3. Ma trận phân quyền khởi tạo
-- -----------------------------------------------------------------------------

-- SUPER_ADMIN: toàn bộ quyền. Là role hệ thống nên gán trọn danh mục.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- ADMIN: toàn quyền TRỪ trường nhạy cảm HR — "trường 🔒 chỉ Admin HR + chính NV" (§6).
-- Đây là ngoại lệ dễ bị bỏ sót nhất khi hiểu "Admin = toàn quyền".
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r CROSS JOIN permissions p
 WHERE r.code = 'ADMIN'
   AND p.code <> 'hr:employee:view-sensitive';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'hr:employee:view', 'hr:employee:create', 'hr:employee:update', 'hr:employee:delete',
    'hr:employee:view-sensitive', 'hr:org-chart:view', 'hr:directory:view',
    'hr:contract:manage', 'hr:leave:view-all', 'hr:leave:approve',
    'hr:report:view', 'hr:report:export', 'adm:org-unit:view'
) WHERE r.code = 'ADMIN_HR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'cms:article:view', 'cms:article:create', 'cms:article:update', 'cms:article:submit',
    'cms:media:manage', 'cms:external-doc:view', 'cms:external-doc:link',
    'hr:directory:view'
) WHERE r.code = 'CONTENT_EDITOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'cms:article:view', 'cms:article:create', 'cms:article:update', 'cms:article:delete',
    'cms:article:submit', 'cms:article:approve', 'cms:article:publish', 'cms:article:unpublish',
    'cms:category:manage', 'cms:media:manage', 'cms:banner:manage', 'cms:layout:manage',
    'cms:contact:manage', 'cms:feedback:manage',
    'cms:external-doc:view', 'cms:external-doc:link', 'hr:directory:view'
) WHERE r.code = 'CONTENT_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'cms:external-doc:view', 'cms:external-doc:link', 'cms:external-doc:manage-flag',
    'hr:directory:view'
) WHERE r.code = 'CLERK';

-- TECHNICIAN — cột "Kỹ thuật" của ma trận §6
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ops:construction:view', 'ops:construction:create', 'ops:construction:update',
    'ops:maintenance:view', 'ops:maintenance:create', 'ops:maintenance:report-incident',
    'ops:operation-status:view', 'ops:operation-status:update',
    'ops:document:view', 'ops:document:upload',
    'ops:gis-layer:view', 'ops:gis-layer:manage',
    'ops:dashboard:view', 'ops:change-log:view',
    'ops:report:view', 'ops:report:export',
    'hyd:station:view', 'hyd:station:manage',
    'hyd:measurement:view', 'hyd:measurement:review',
    'hyd:threshold:view', 'hyd:threshold:manage',
    'hyd:alert:view', 'hyd:alert:handle',
    'hyd:report:view', 'hyd:report:export',
    'hr:directory:view', 'hr:leave:request'
) WHERE r.code = 'TECHNICIAN';

-- XN_MANAGER — cột "Quản lý XN". Phạm vi dữ liệu (chỉ XN mình) do scope filter
-- tầng 3 lo, KHÔNG thể hiện bằng permission (conventions.md §4.2).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ops:construction:view',
    'ops:maintenance:view', 'ops:maintenance:create', 'ops:maintenance:update',
    'ops:maintenance:delete', 'ops:maintenance:report-incident', 'ops:maintenance:close-incident',
    'ops:operation-status:view', 'ops:operation-status:update',
    'ops:document:view', 'ops:document:upload',
    'ops:gis-layer:view', 'ops:dashboard:view', 'ops:change-log:view',
    'ops:report:view', 'ops:report:export',
    'hyd:station:view', 'hyd:measurement:view',
    'hyd:threshold:view', 'hyd:threshold:manage',
    'hyd:alert:view', 'hyd:alert:handle',
    'hyd:report:view', 'hyd:report:export',
    'hr:directory:view', 'hr:leave:request', 'hr:leave:view-all', 'hr:leave:approve'
) WHERE r.code = 'XN_MANAGER';

-- XN_OPERATOR — cột "Cán bộ vận hành". KHÔNG có ops:maintenance:create
-- (chỉ ghi nhận sự cố); §6 ghi rõ quyền đó "cấp được qua ops:maintenance:create"
-- khi cần, nhưng mặc định là không.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ops:construction:view', 'ops:maintenance:view', 'ops:maintenance:report-incident',
    'ops:operation-status:view', 'ops:operation-status:update',
    'ops:document:view', 'ops:document:upload',
    'ops:gis-layer:view', 'ops:dashboard:view',
    'ops:report:view',
    'hyd:station:view', 'hyd:measurement:view', 'hyd:alert:view', 'hyd:report:view',
    'hr:directory:view', 'hr:leave:request'
) WHERE r.code = 'XN_OPERATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ops:construction:view', 'ops:maintenance:view', 'ops:operation-status:view',
    'ops:gis-layer:view', 'ops:dashboard:view', 'ops:report:view',
    'hyd:station:view', 'hyd:measurement:view',
    'hyd:threshold:view', 'hyd:alert:view', 'hyd:alert:handle', 'hyd:report:view',
    'hr:directory:view', 'hr:leave:request'
) WHERE r.code = 'DUTY_OFFICER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ops:construction:view', 'ops:maintenance:view', 'ops:operation-status:view',
    'ops:gis-layer:view', 'ops:dashboard:view', 'ops:report:view', 'ops:report:export',
    'hyd:station:view', 'hyd:measurement:view', 'hyd:alert:view',
    'hyd:report:view', 'hyd:report:export',
    'cms:article:view', 'hr:org-chart:view', 'hr:directory:view',
    'hr:report:view', 'hr:leave:request'
) WHERE r.code = 'EXECUTIVE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ops:construction:view', 'ops:maintenance:view', 'ops:operation-status:view',
    'ops:gis-layer:view', 'ops:report:view',
    'hyd:station:view', 'hyd:measurement:view', 'hyd:report:view',
    'cms:article:view', 'cms:external-doc:view', 'cms:external-doc:link',
    'hr:org-chart:view', 'hr:directory:view', 'hr:leave:request'
) WHERE r.code = 'VIEWER';

-- Chốt hạ: mọi vai trò seed phải có ít nhất 1 quyền. Vai trò rỗng nghĩa là
-- danh sách code ở trên có typo — bắt ngay tại migration thay vì để lộ ra khi
-- người dùng bị 403 không giải thích được.
DO $$
DECLARE
    v_empty TEXT;
BEGIN
    SELECT string_agg(r.code, ', ' ORDER BY r.code) INTO v_empty
      FROM roles r
     WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id);

    IF v_empty IS NOT NULL THEN
        RAISE EXCEPTION 'Vai trò không có quyền nào: % — nhiều khả năng sai mã quyền trong seed', v_empty;
    END IF;
END $$;
