-- Khoi nap TRUOC phan than cua ban dump, cung MOT giao dich (T68.3).
-- Nguon: KeHoachKhoiPhuc.khoiTruocKhiNap(). Tep deploy/backup/truoc-khi-nap.sql phai
-- trung tung byte (BackupRestoreFlagsTest) - sua mot noi la do CI.

-- (1) Bo bang phan manh: --clean phat DROP INDEX cho tung phan manh, ma chi muc phan
--     manh khong xoa le duoc khi bang cha con (architecture-review 10.80).
DO $$
DECLARE r record; n int := 0;
BEGIN
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
              WHERE ns.nspname = 'public' AND c.relkind = 'p'
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname);
        n := n + 1;
    END LOOP;
    RAISE NOTICE 'da bo % bang phan manh truoc khi nap', n;
END $$;

-- (2) Go quyen MAC DINH cap schema cua vai tro dang nap: bang dung lai boi --clean nhan
--     quyen mac dinh cua DICH (V202608131006 cap arwd cho songnhue_app), ma ACL cua ban
--     dump chi GRANT, khong REVOKE => bang append-only mat REVOKE. Muc DEFAULT ACL cua
--     ban dump nam o luot ACL cuoi cung, nen dat lai chung SAU khi moi bang da dung xong.
DO $$
DECLARE r record; n int := 0;
BEGIN
    FOR r IN SELECT DISTINCT ns.nspname, d.defaclobjtype::text AS ma, a.grantee,
                    CASE d.defaclobjtype WHEN 'r' THEN 'TABLES' WHEN 'S' THEN 'SEQUENCES'
                                         WHEN 'f' THEN 'FUNCTIONS' WHEN 'T' THEN 'TYPES' END AS loai
               FROM pg_default_acl d
               JOIN pg_namespace ns ON ns.oid = d.defaclnamespace
               CROSS JOIN LATERAL aclexplode(d.defaclacl) a
              WHERE d.defaclrole = (SELECT oid FROM pg_roles WHERE rolname = current_user)
                AND a.grantee <> d.defaclrole
    LOOP
        IF r.loai IS NULL THEN
            RAISE EXCEPTION 'loai quyen mac dinh la: % (schema %)', r.ma, r.nspname;
        END IF;
        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I REVOKE ALL ON %s FROM %s',
                       current_user, r.nspname, r.loai,
                       CASE WHEN r.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(r.grantee)) END);
        n := n + 1;
    END LOOP;
    RAISE NOTICE 'da go % quyen mac dinh cua % truoc khi nap', n, current_user;
END $$;
