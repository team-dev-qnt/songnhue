-- =============================================================================
-- WS-2 / T2.9 — Dữ liệu khởi tạo tối thiểu: đơn vị gốc, tài khoản Super Admin,
-- ngày lễ cố định.
--
-- ⚠ CỐ Ý KHÔNG seed danh sách Xí nghiệp / phòng ban: cơ cấu tổ chức thật nằm
--   trong mục G8 còn chờ Công ty cung cấp (business-open-questions.md Phần II).
--   Seed số liệu đoán rồi sửa sau còn tệ hơn để trống — hồ sơ công trình và
--   phân quyền theo đơn vị sẽ bám vào đó.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Đơn vị gốc của cây org_units
-- -----------------------------------------------------------------------------
INSERT INTO org_units (code, name, short_name, unit_type, parent_id, path, depth, sort_order)
VALUES ('CTY', 'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ',
        'Thủy lợi Sông Nhuệ', 'CONG_TY', NULL, '/', 0, 0);

-- Materialized path của nút gốc là '/<id>/' — id do IDENTITY cấp nên phải cập
-- nhật sau khi INSERT.
UPDATE org_units SET path = '/' || id || '/' WHERE code = 'CTY';

-- -----------------------------------------------------------------------------
-- Tài khoản Super Admin
--
-- password_hash = '!' → KHÔNG có mật khẩu nào khớp (BCrypt luôn bắt đầu bằng
-- '$2'), nên tài khoản này chưa đăng nhập được. CỐ Ý như vậy:
--   • mật khẩu mặc định nằm trong repo là lỗ hổng, dù có bắt đổi lần đầu
--   • sinh hash lúc migrate thì mật khẩu thô đi qua log SQL của server
--
-- Kích hoạt: lệnh bootstrap đọc BOOTSTRAP_ADMIN_PASSWORD từ env, đặt mật khẩu
-- và chuyển status sang ACTIVE — hạng mục WS-5 / T5.7. Chạy đúng 1 lần lúc
-- dựng môi trường, sau đó gỡ biến env khỏi máy chủ.
-- -----------------------------------------------------------------------------
INSERT INTO users (
    username, full_name, email, password_hash, org_unit_id,
    status, must_change_password, two_factor_required
)
SELECT 'superadmin', 'Quản trị tối cao', NULL, '!', ou.id,
       'PENDING_ACTIVATION', TRUE, TRUE
  FROM org_units ou
 WHERE ou.code = 'CTY';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM users u CROSS JOIN roles r
 WHERE u.username = 'superadmin' AND r.code = 'SUPER_ADMIN';

-- -----------------------------------------------------------------------------
-- Ngày lễ có ngày dương lịch CỐ ĐỊNH (Điều 112 BLLĐ 2019).
--
-- ⚠ KHÔNG seed Tết Nguyên đán, Giỗ Tổ Hùng Vương (âm lịch) và ngày nghỉ bù
--   Quốc khánh — mỗi năm Chính phủ công bố khác nhau, đoán là sai. Admin nhập
--   qua UI danh mục ngày lễ; số ngày phép năm (CN-04.9) phụ thuộc bảng này.
-- -----------------------------------------------------------------------------
INSERT INTO holidays (holiday_date, name, is_recurring, note)
VALUES
    (DATE '2026-01-01', 'Tết Dương lịch',    TRUE, NULL),
    (DATE '2026-04-30', 'Ngày Giải phóng miền Nam', TRUE, NULL),
    (DATE '2026-05-01', 'Ngày Quốc tế Lao động',    TRUE, NULL),
    (DATE '2026-09-02', 'Quốc khánh',        TRUE, 'Nghỉ 2 ngày — ngày liền kề do Chính phủ công bố hằng năm'),
    (DATE '2027-01-01', 'Tết Dương lịch',    TRUE, NULL),
    (DATE '2027-04-30', 'Ngày Giải phóng miền Nam', TRUE, NULL),
    (DATE '2027-05-01', 'Ngày Quốc tế Lao động',    TRUE, NULL),
    (DATE '2027-09-02', 'Quốc khánh',        TRUE, 'Nghỉ 2 ngày — ngày liền kề do Chính phủ công bố hằng năm');
