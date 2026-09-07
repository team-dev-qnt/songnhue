-- =============================================================================
-- Vá lỗ hổng quyền làm `pg_dump` KHÔNG chạy được — phát hiện 17/8 khi rà soát.
--
-- ⚠ Triệu chứng thật: `make backup` dừng ở
--     pg_dump: error: query failed: ERROR: permission denied for sequence
--                     system_backups_id_seq
--   → không sinh ra tệp nào. Trong khi sao lưu là LƯỚI AN TOÀN DUY NHẤT của hệ
--   này (không PITR, không replica — architecture-review.md §6.5).
--
-- Nguyên nhân: V202608131006 §3 khai quyền mặc định cho bảng tạo sau, nhưng chỉ
-- có dòng TABLES cho songnhue_readonly, THIẾU dòng SEQUENCES:
--
--     ALTER DEFAULT PRIVILEGES … GRANT SELECT ON TABLES    TO songnhue_readonly;  ✔ có
--     ALTER DEFAULT PRIVILEGES … GRANT SELECT ON SEQUENCES TO songnhue_readonly;  ✘ thiếu
--
-- `GRANT … ON ALL SEQUENCES` ở dòng 64 của migration đó chỉ áp cho sequence
-- ĐANG TỒN TẠI lúc nó chạy. Bảng đầu tiên tạo sau (system_backups — chính bảng
-- sổ đăng ký sao lưu, V202608161010) sinh sequence không ai cấp quyền, và
-- pg_dump đọc mọi sequence để lấy last_value.
--
-- Nghĩa là lỗi này KHÔNG chỉ là một GRANT bị quên: mọi bảng mới của Phase 1+
-- (công trình, thủy văn, hồ sơ nhân sự…) đều làm hỏng lại sao lưu, mỗi lần đều
-- im lặng cho tới lần chạy sao lưu kế tiếp. Vì vậy sửa ở tầng QUYỀN MẶC ĐỊNH.
--
-- Canh bằng `BackupRoleTest`: chạy pg_dump THẬT bằng vai trò readonly trên
-- schema đầy đủ. Trước bản vá này bài kiểm đó đỏ.
-- =============================================================================

-- 1. Bù cho các sequence đã lỡ tạo sau V202608131006 (hôm nay: system_backups_id_seq)
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO songnhue_readonly;

-- 2. Chặn tái phát: mọi sequence tạo sau đều tự có quyền đọc cho vai trò dump.
--    Gắn theo current_user để đúng cả khi migrate bằng role khác, y như §3 của
--    V202608131006.
DO $$
BEGIN
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
        'GRANT SELECT ON SEQUENCES TO songnhue_readonly', current_user);
END $$;
