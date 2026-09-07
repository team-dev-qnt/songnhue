-- =============================================================================
-- KHỐI VÁ SAU KHÔI PHỤC — chạy NGAY SAU pg_restore, TRƯỚC khi bật ứng dụng
--
-- Bối cảnh: CSDL production vừa được ghi đè bằng bản chụp của staging. Bản chụp
-- ấy mang theo ba nhóm thứ KHÔNG được sống trên production. Khối này gỡ chúng ra
-- trong MỘT giao dịch: hoặc cả khối vào, hoặc không gì vào.
--
-- ⛔ Mỗi khối đều tự KIỂM số hàng nó vừa đụng và RAISE EXCEPTION khi số không
--    đúng kỳ vọng. Một câu DELETE khớp 0 hàng và một câu DELETE khớp 3 hàng
--    trông giống hệt nhau trong log; chỉ có phép đếm phân biệt được (luật 9).
-- =============================================================================
\set ON_ERROR_STOP on
BEGIN;

-- -----------------------------------------------------------------------------
-- ① SỔ MIGRATION SEED
--
-- staging đặt SEED_LOCATION=classpath:db/seed/portal nên Flyway ở đó giải và áp
-- 3 migration seed. production để SEED_LOCATION rỗng ⇒ giải về classpath:db/seed/none
-- (thư mục cố ý rỗng) ⇒ 3 tệp ấy KHÔNG TỒN TẠI với Flyway ở production.
--
-- Với validate-on-migrate=true, ba hàng mồ côi trong sổ làm Flyway đỏ ngay lúc
-- khởi động: "Detected applied migration not resolved locally" ⇒ app không lên.
--
-- ⛔ KHÔNG chữa bằng cách đặt SEED_LOCATION ở production: migration seed mở đầu
--    bằng DELETE FROM articles. Nó sẽ không chạy lần này (sổ đã có hàng), nhưng
--    để lại một khẩu súng lên đạn chĩa vào ĐÚNG đường khôi phục thảm hoạ — lượt
--    dựng lại từ CSDL rỗng nào sau này cũng sẽ xoá sạch bài của Công ty.
--
-- DỮ LIỆU mà 3 migration ấy tạo ra thì Ở LẠI (nó nằm trong các bảng nghiệp vụ,
-- đã được khôi phục). Chỉ xoá phần GHI SỔ.
-- -----------------------------------------------------------------------------
DO $$
DECLARE n INTEGER;
BEGIN
    DELETE FROM flyway_schema_history WHERE script LIKE '%seed_portal%';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 3 THEN
        RAISE EXCEPTION 'Cho doi xoa dung 3 hang seed trong flyway_schema_history, xoa duoc %', n;
    END IF;
    RAISE NOTICE '① flyway_schema_history: da xoa % hang seed', n;
END $$;

-- -----------------------------------------------------------------------------
-- ② DANH TÍNH — trả lại tài khoản quản trị CỦA PRODUCTION
--
-- ⛔⛔ ĐÂY LÀ BƯỚC CÓ THỂ KHOÁ BẠN RA KHỎI HỆ THỐNG VĨNH VIỄN NẾU BỎ QUA.
--
--    Bản chụp staging mang theo `superadmin` của staging: ACTIVE, 2FA bắt buộc,
--    và một bí mật TOTP mã hoá AES-256-GCM bằng khoá CỦA STAGING. Khoá AES nằm
--    NGOÀI CSDL, mỗi môi trường một khoá riêng; `key_id='v1'` chỉ là cái nhãn.
--    ⇒ production không giải mã nổi bí mật ấy ⇒ không sinh được mã 2FA hợp lệ.
--
--    Và `AdminBootstrapRunner` chỉ tác động khi status='PENDING_ACTIVATION', nên
--    BOOTSTRAP_ADMIN_PASSWORD trong .env KHÔNG mở lại được tài khoản đã ACTIVE.
--
--    Cách thoát: đè lại đúng các cột danh tính đã chụp từ production TRƯỚC khi
--    di trú (tệp danh-tinh-prod-20260907.sql). Mật khẩu và 2FA bạn vừa đặt lúc
--    23:12–23:14 hôm nay vẫn dùng được nguyên vẹn.
--
-- ⚠ Thứ tự bắt buộc: XOÁ TOTP/mã khôi phục của staging TRƯỚC, rồi mới nạp lại
--   của production. `user_totp.user_id` là UNIQUE nên nạp trước khi xoá sẽ đụng.
-- -----------------------------------------------------------------------------
DELETE FROM user_totp;
DELETE FROM user_recovery_codes;
DELETE FROM sessions;          -- phiên của staging; khoá JWT hai bên khác nhau nên chúng vô nghĩa
DELETE FROM token_denylist;    -- cùng lý do

-- @@CHEN_DANH_TINH@@
-- ⚠ Dòng trên là MỐC CHÈN, không phải chú thích trang trí. Nội dung tệp
--   /var/lib/songnhue/backup/danh-tinh-prod-<ngày>.sql được chèn vào ĐÚNG đây
--   trước khi nạp, bằng:
--
--     sed -e '/^-- @@CHEN_DANH_'\''TINH@@$/r <tệp danh tính>' \
--         sau-khoi-phuc-production.sql > .khoi-va.sql
--
--   ⛔ MẪU PHẢI NEO `^…$`. Bản đầu dùng mẫu không neo, và chính DÒNG CHÚ THÍCH
--      NÀY chứa nguyên văn chuỗi mốc ⇒ `sed` chèn tệp danh tính HAI lần ⇒
--      `duplicate key value violates unique constraint "uq_user_totp_user_id"`.
--      Bắt được ở lượt diễn tập trên CSDL nháp, không phải trên production.
--
--   KHÔNG dùng `\i` hay `\ir`: khối này được nạp qua stdin của `docker exec`,
--   nên `\ir` không có thư mục gốc để bám; và tệp danh tính để mode 600 của
--   người triển khai, còn postgres trong container chạy bằng uid khác — nó
--   KHÔNG đọc nổi tệp ấy. Ghép ở host là đường duy nhất đúng cả hai phía.

DO $$
DECLARE n INTEGER;
BEGIN
    SELECT count(*) INTO n FROM user_totp WHERE user_id = 1 AND confirmed_at IS NOT NULL;
    IF n <> 1 THEN RAISE EXCEPTION 'superadmin phai co dung 1 TOTP da xac nhan, dang co %', n; END IF;
    SELECT count(*) INTO n FROM user_recovery_codes WHERE user_id = 1;
    IF n <> 10 THEN RAISE EXCEPTION 'superadmin phai co 10 ma khoi phuc, dang co %', n; END IF;
    SELECT count(*) INTO n FROM users u JOIN user_roles ur ON ur.user_id=u.id
      JOIN roles r ON r.id=ur.role_id WHERE u.id=1 AND r.code='SUPER_ADMIN' AND u.status='ACTIVE';
    IF n <> 1 THEN RAISE EXCEPTION 'superadmin phai ACTIVE va giu vai tro SUPER_ADMIN'; END IF;
    RAISE NOTICE '② danh tinh superadmin cua production da duoc dat lai';
END $$;

-- -----------------------------------------------------------------------------
-- ③ HAI TÀI KHOẢN CỦA ĐỘI PHÁT TRIỂN
--
-- Bản chụp mang sang `nguyetmoon` và `huynq1` — cả hai ACTIVE, cả hai mang vai
-- trò SUPER_ADMIN (đo trên staging: user_roles 1→1, 2→1, 9→1), với mật khẩu
-- bcrypt dùng được nguyên vẹn ở mọi môi trường.
--
-- ⛔ KHÔNG XOÁ được: mọi hàng nội dung trỏ created_by/updated_by vào chúng.
--    Khoá lại là thao tác đảo ngược được bằng một cú bấm ở màn hình quản trị.
--    `huynq` vốn đã LOCKED trên staging — không đụng.
-- -----------------------------------------------------------------------------
DO $$
DECLARE n INTEGER;
BEGIN
    UPDATE users SET status='LOCKED', updated_at=now()
     WHERE username IN ('nguyetmoon','huynq1') AND status='ACTIVE';
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '③ da khoa % tai khoan doi phat trien', n;
END $$;

-- -----------------------------------------------------------------------------
-- ④ HÀNG ĐỢI VÀ SỔ SAO LƯU CỦA STAGING
--
-- jobs: 2 hàng PENDING của staging sẽ CHẠY THẬT trên production ngay khi app lên.
--       Giữ lại lịch sử SUCCEEDED (nó là dữ liệu), bỏ phần còn CHỜ.
-- system_backups: 36 hàng trỏ vào tệp nằm trên VPS-2. Trên VPS-1 chúng không tồn
--       tại, nên màn hình khôi phục sẽ mời người dùng khôi phục từ tệp không có.
-- -----------------------------------------------------------------------------
DO $$
DECLARE n INTEGER;
BEGIN
    DELETE FROM jobs WHERE status IN ('PENDING','FAILED');
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '④ da xoa % job dang cho/that bai cua staging', n;
    DELETE FROM system_backups;
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '④ da xoa % hang so sao luu tro vao VPS-2', n;
END $$;

-- -----------------------------------------------------------------------------
-- ⑤ LIÊN KẾT CÒN TRỎ VỀ STAGING
--
-- Đo trước khi di trú: articles.content = 0 hàng, article_versions.content = 0
-- hàng, settings = 0 khoá. Chỗ DUY NHẤT là 1 banner.
-- -----------------------------------------------------------------------------
DO $$
DECLARE n INTEGER;
BEGIN
    UPDATE banners
       SET link_url = replace(link_url,'https://staging.songnhue.com','https://songnhue.com'),
           updated_at = now()
     WHERE link_url ILIKE '%staging.songnhue.com%';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 1 THEN RAISE EXCEPTION 'Cho doi dung 1 banner tro ve staging, sua duoc %', n; END IF;
    RAISE NOTICE '⑤ da sua % banner tro ve staging', n;
END $$;

DO $$
DECLARE n INTEGER;
BEGIN
    SELECT count(*) INTO n FROM (
        SELECT 1 FROM articles          WHERE content  ILIKE '%staging.songnhue.com%'
        UNION ALL SELECT 1 FROM article_versions WHERE content  ILIKE '%staging.songnhue.com%'
        UNION ALL SELECT 1 FROM banners WHERE link_url ILIKE '%staging.songnhue.com%'
        UNION ALL SELECT 1 FROM settings WHERE setting_value ILIKE '%staging.songnhue.com%'
    ) t;
    IF n <> 0 THEN RAISE EXCEPTION 'Con % cho tro ve staging.songnhue.com', n; END IF;
    RAISE NOTICE '⑤ khong con cho nao tro ve staging';
END $$;

-- -----------------------------------------------------------------------------
-- ⑥ TÁI KHẲNG ĐỊNH QUYỀN APPEND-ONLY  ⛔⛔ ĐỪNG BỎ BƯỚC NÀY
--
-- ĐO ĐƯỢC 08/09/2026 khi diễn tập lượt di trú này trên một CSDL nháp: dữ liệu
-- staging mang `songnhue_app = arwd` (SỬA + XOÁ) trên ~35 bảng mà production cố
-- ý chỉ cho `ar` hoặc `r`:
--
--   audit_logs + 15 phân mảnh · audit_chain_head (production: app KHÔNG có quyền
--   nào) · audit_archive_anchors · hydro_raw_logs + 13 phân mảnh ·
--   security_events · flyway_schema_history
--
-- pg_dump mang ACL theo dữ liệu, nên khôi phục nguyên trạng là ÂM THẦM HẠ CẤP
-- bảo đảm append-only của production: vai trò runtime sửa và xoá được nhật ký
-- kiểm toán (phá luật 18 — hash chain đang ký tên vào lịch sử), sửa được
-- hydro_raw_logs (luật 8 — bản sao DUY NHẤT của nguồn không có API lịch sử), và
-- ghi được cả sổ migration.
--
-- Vì sao staging mất quyền siết: gần như chắc chắn từ lượt khôi phục 26/8 chạy
-- bản `restore.sh` còn `--no-privileges` — đúng thứ §10.58 ghi là "ALTER DEFAULT
-- PRIVILEGES cứu". Nó cứu app khỏi chết, và cùng lúc xoá mọi câu REVOKE.
--
-- ⭐ Khối dưới đây chép NGUYÊN VĂN phần "2. Siết các bảng append-only" của
--    V202608131006__core_db_role_grants.sql, cộng phần tương ứng của
--    V202609041059__hyd_time_series.sql. Tái khẳng định NGUỒN SỰ THẬT, không
--    chép lại một ảnh chụp — ảnh chụp cũng có thể đã sai.
-- -----------------------------------------------------------------------------
DO $$
DECLARE r RECORD; n INTEGER := 0;
BEGIN
    FOR r IN SELECT c.relname FROM pg_class c WHERE c.oid = 'public.audit_logs'::regclass
             UNION ALL
             SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
              WHERE i.inhparent = 'public.audit_logs'::regclass
    LOOP
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON public.%I FROM songnhue_app', r.relname);
        EXECUTE format('GRANT SELECT, DELETE ON public.%I TO songnhue_archiver', r.relname);
        n := n + 1;
    END LOOP;
    IF n < 2 THEN RAISE EXCEPTION 'audit_logs phai co cha + it nhat 1 phan manh, thay %', n; END IF;
    RAISE NOTICE '⑥ audit_logs: da siet % bang (cha + phan manh)', n;

    n := 0;
    FOR r IN SELECT c.relname FROM pg_class c WHERE c.oid = 'public.hydro_raw_logs'::regclass
             UNION ALL
             SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
              WHERE i.inhparent = 'public.hydro_raw_logs'::regclass
    LOOP
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON public.%I FROM songnhue_app', r.relname);
        n := n + 1;
    END LOOP;
    IF n < 2 THEN RAISE EXCEPTION 'hydro_raw_logs phai co cha + it nhat 1 phan manh, thay %', n; END IF;
    RAISE NOTICE '⑥ hydro_raw_logs: da siet % bang', n;
END $$;

REVOKE UPDATE, DELETE, TRUNCATE ON security_events FROM songnhue_app;
REVOKE ALL ON audit_chain_head FROM songnhue_app, songnhue_readonly;
GRANT SELECT ON audit_chain_head TO songnhue_archiver, songnhue_readonly;
REVOKE INSERT, UPDATE, DELETE ON audit_archive_anchors FROM songnhue_app;
GRANT SELECT, INSERT, UPDATE ON audit_archive_anchors TO songnhue_archiver;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM songnhue_app;

-- Phép chốt: hỏi thẳng hệ quyền, từng bảng một. `has_table_privilege` phân biệt
-- được hai trạng thái; đọc `relacl` bằng mắt thì không.
DO $$
DECLARE r RECORD; hong TEXT := '';
BEGIN
    FOR r IN SELECT c.oid::regclass::text AS t FROM pg_class c
              WHERE c.oid IN ('public.audit_logs'::regclass, 'public.hydro_raw_logs'::regclass,
                              'public.security_events'::regclass)
             UNION ALL
             SELECT c.oid::regclass::text FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
              WHERE i.inhparent IN ('public.audit_logs'::regclass, 'public.hydro_raw_logs'::regclass)
    LOOP
        IF has_table_privilege('songnhue_app', r.t, 'UPDATE') THEN hong := hong || ' ' || r.t || '.UPDATE'; END IF;
        IF has_table_privilege('songnhue_app', r.t, 'DELETE') THEN hong := hong || ' ' || r.t || '.DELETE'; END IF;
    END LOOP;
    IF has_table_privilege('songnhue_app','audit_chain_head','SELECT')      THEN hong := hong || ' audit_chain_head.SELECT'; END IF;
    IF has_table_privilege('songnhue_app','flyway_schema_history','INSERT') THEN hong := hong || ' flyway.INSERT'; END IF;
    IF has_table_privilege('songnhue_app','audit_archive_anchors','INSERT') THEN hong := hong || ' anchors.INSERT'; END IF;
    IF hong <> '' THEN RAISE EXCEPTION 'Quyen append-only VAN CON HO:%', hong; END IF;
    RAISE NOTICE '⑥ moi bang append-only da dung quyen';
END $$;

-- -----------------------------------------------------------------------------
-- ⑦ PHÂN MẢNH audit_logs cho các tháng tới
--
-- Bản chụp mang theo phân mảnh của staging (p202607..p202708). `--clean` xoá
-- bảng cha nên phân mảnh riêng của production (p202709) biến mất cùng. Gọi lại
-- hàm bảo trì để không tháng nào thiếu chỗ ghi.
-- -----------------------------------------------------------------------------
SELECT core_ensure_audit_partitions(6);

COMMIT;

-- =============================================================================
-- CỐ Ý KHÔNG LÀM — ghi ra để lần sau khỏi tưởng là bỏ sót
--
-- · KHÔNG xoá audit_logs (2223 hàng của staging). Chúng đi cùng audit_chain_head
--   nên chuỗi băm tự kiểm được; xoá là phá bất biến (luật 18). Và nội dung của
--   chúng đúng sự thật: những người ấy đã sửa những bài ấy vào những lúc ấy —
--   chỉ là trong giai đoạn dựng hệ. Ghi một dòng vào hồ sơ bàn giao là đủ.
--
-- · KHÔNG xoá hai thư mục media rác ("Staging test" 1 ảnh, "CongBoThongTin-TEST"
--   0 ảnh). `attachments` KHÔNG có cột folder — liên kết đi qua owner_type/owner_id
--   nên xoá thẳng sẽ để lại ảnh mồ côi mà không FK nào chặn. Xoá ở màn hình quản
--   trị đi qua soft-delete + audit đúng luật (luật 9). Việc của người, một phút.
--
-- · KHÔNG đụng vào `settings`. 114 khoá cả hai bên, khác đúng 9 giá trị, và cả 9
--   đều là cấu hình Công ty đã đặt trên staging (logo, favicon, bản đồ, thư mục
--   ảnh trang chủ, số tin, slider). Không khoá nào chứa tên miền staging.
-- =============================================================================
