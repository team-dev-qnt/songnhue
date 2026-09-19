package com.songnhue.core.application.backup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Các bước <b>thuần</b> của một lượt khôi phục từ bản dump — dùng chung cho {@link RestoreService}
 * (chạy bằng công cụ trong ảnh app) và cho bài kiểm tích hợp (chạy bằng công cụ trong container).
 *
 * <h2>Vì sao tách ra đây</h2>
 *
 * <p>Đường khôi phục đã được đo trên một bản sao production ngày 08/09/2026 và nằm ở
 * {@code deploy/backup/khoi-phuc-qua-container.sh}. Nút khôi phục M5.11 thì vẫn nạp
 * {@code pg_restore --clean --no-privileges} thẳng vào CSDL — mang đủ ba lỗi mà script đã vá (T68.3):
 *
 * <ol>
 *   <li><b>Mục EXTENSION lọt qua bộ lọc</b> ⇒ {@code DROP EXTENSION postgis} ⇒ <i>must be owner of
 *       extension</i>. {@code pg_dump} ⛔ ghi chủ sở hữu cho extension nên trường cuối của dòng là TÊN
 *       extension, ⛔ phải {@code postgres}.
 *   <li><b>{@code --clean} vấp bảng phân mảnh</b> khi đích ĐÃ có dữ liệu: nó phát {@code DROP INDEX} cho
 *       từng phân mảnh, mà chỉ mục phân mảnh ⛔ xoá lẻ được khi bảng cha còn (§10.80). Đích rỗng thì các
 *       câu ấy là no-op — nên đường hay được thử thì chạy, đường dùng thật thì hỏng.
 *   <li><b>{@code --no-privileges}</b> tước ACL ⇒ khôi phục vào cluster mới ra một CSDL mà
 *       {@code songnhue_app} ⛔ đọc nổi một bảng nào (§10.58, T37.8).
 * </ol>
 *
 * <h2>⛔⛔ Lỗi THỨ TƯ — cả script đã đo lẫn ba bản vá trên đều ⛔ chặn được (đo 19/09/2026)</h2>
 *
 * <p>Khôi phục ĐÈ lên một CSDL đã migrate: {@code --clean} DROP rồi CREATE lại từng bảng bằng
 * {@code songnhue_owner}, nên bảng mới nhận quyền MẶC ĐỊNH của đích — {@code V202608131006} khai
 * {@code ALTER DEFAULT PRIVILEGES … GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO songnhue_app}.
 * Phần ACL của bản dump chỉ GRANT so với {@code acldefault}, ⛔ bao giờ REVOKE quyền đến từ mặc định.
 * Đo trên Postgres 16 thật: <b>72 quyền thừa</b>, đúng các bảng append-only — {@code audit_logs} + 15
 * phân mảnh, {@code hydro_raw_logs} + 13 phân mảnh, {@code security_events}, {@code audit_chain_head}
 * (nguồn: app ⛔ quyền nào ⇒ đích: đủ bốn), {@code audit_archive_anchors}, {@code flyway_schema_history}.
 * ⛔ Một dòng lỗi nào.
 *
 * <p>Đây là cơ chế THẬT của việc staging sống 13 ngày với nhật ký kiểm toán sửa/xoá được (§10.80) — ⛔
 * phải {@code --no-privileges} như ghi chú lưu hành đoán. Lượt di trú 08/09 thoát được chỉ nhờ khối ⑥
 * của {@code deploy/backup/di-tru/sau-khoi-phuc-production.sql} tái khẳng định REVOKE bằng tay.
 *
 * <p>⇒ {@link #khoiTruocKhiNap()} gỡ quyền mặc định của chính vai trò đang nạp TRƯỚC phần thân, trong
 * cùng giao dịch; mục {@code DEFAULT ACL} của bản dump nằm ở lượt ACL cuối cùng nên đặt chúng lại SAU khi
 * mọi bảng đã dựng xong. Đích trắng thì khối ấy là no-op.
 *
 * <p>Một bài kiểm chép lại các bước ấy bằng tay thì chỉ canh chính nó (T51.15). Nên bài kiểm gọi
 * <b>đúng các hàm này</b> — chỉ khác ở chỗ công cụ chạy. Hai script shell đọc cùng khối SQL từ
 * {@code deploy/backup/truoc-khi-nap.sql}; {@code BackupRestoreFlagsTest} đòi tệp ấy trùng TỪNG BYTE với
 * {@link #khoiTruocKhiNap()} (luật 14).
 */
public final class KeHoachKhoiPhuc {

    /** Ba extension do {@code 10-bootstrap.sh} tạo bằng superuser — bản dump đã lọc nên ⛔ tạo lại được. */
    public static final List<String> PHAN_MO_RONG = List.of("postgis", "unaccent", "pg_trgm");

    /**
     * Sàn số mục sau khi lọc. Bộ lọc ăn quá tay thì khôi phục ra một CSDL rỗng — kiểu hỏng tệ nhất vì
     * nó trông như thành công. Cùng ngưỡng với {@code khoi-phuc-qua-container.sh}.
     */
    public static final int SO_MUC_TOI_THIEU = 100;

    /**
     * Cờ sinh SQL từ bản dump. ⛔ {@code --no-privileges}: GRANT cấp bảng do migration Flyway cấp, mà
     * Flyway ⛔ chạy lại trên CSDL vừa khôi phục (lịch sử của nó nằm ngay trong bản dump).
     */
    public static final List<String> CO_SINH_SQL = List.of("--clean", "--if-exists", "--no-owner");

    /**
     * Cờ nạp bằng {@code psql}: MỘT giao dịch, dừng ở lỗi ĐẦU TIÊN. Hỏng giữa chừng thì cả
     * {@link #khoiTruocKhiNap()} lẫn phần nạp cùng lùi — ⛔ để lại một CSDL mất sạch bảng phân mảnh hay mất
     * quyền mặc định.
     */
    public static final List<String> CO_NAP = List.of("--single-transaction", "--set=ON_ERROR_STOP=1", "--quiet");

    private static final Pattern DONG_EXTENSION = Pattern.compile("^\\d+; +\\d+ +\\d+ EXTENSION ");

    private KeHoachKhoiPhuc() {}

    /** Mục lục sau khi lọc, kèm số đếm để in ra nhật ký — một bộ lọc im lặng ⛔ kiểm lại được. */
    public record MucLucDaLoc(List<String> dong, int tong, int con) {

        public int boDi() {
            return tong - con;
        }

        public long soDongExtension() {
            return dong.stream().filter(d -> DONG_EXTENSION.matcher(d).find()).count();
        }
    }

    /**
     * Lọc mục lục của {@code pg_restore --list} — BA vế, cùng thứ tự với script container.
     *
     * <ol>
     *   <li>bỏ {@code COMMENT - EXTENSION …};
     *   <li>bỏ chính dòng {@code EXTENSION} (vế thêm 08/09 — xem javadoc lớp, lỗi 1);
     *   <li>bỏ mục có chủ sở hữu {@code postgres} (đối tượng extension tạo bằng superuser, VD
     *       {@code spatial_ref_sys}).
     * </ol>
     *
     * Dòng trống và dòng chú thích {@code ;} được giữ nguyên — {@code pg_restore --use-list} tự bỏ qua.
     */
    public static MucLucDaLoc locMucLuc(List<String> mucLuc) {
        List<String> giu = new ArrayList<>();
        int tong = 0;
        int con = 0;
        for (String dong : mucLuc) {
            boolean coNoiDung = !dong.isEmpty();
            if (coNoiDung) {
                tong++;
            }
            if (dong.contains("COMMENT - EXTENSION")
                    || DONG_EXTENSION.matcher(dong).find()
                    || "postgres".equals(truongCuoi(dong))) {
                continue;
            }
            giu.add(dong);
            if (coNoiDung) {
                con++;
            }
        }
        return new MucLucDaLoc(List.copyOf(giu), tong, con);
    }

    /**
     * Chốt của chính bộ lọc, đo trên THỨ sẽ được truyền cho {@code pg_restore}.
     *
     * @throws IllegalStateException nếu bộ lọc ăn quá tay, hoặc còn sót mục EXTENSION
     */
    public static void kiemMucLuc(MucLucDaLoc mucLuc) {
        if (mucLuc.con() <= SO_MUC_TOI_THIEU) {
            throw new IllegalStateException("Mục lục sau khi lọc chỉ còn %d/%d mục — bộ lọc đã ăn quá tay, DỪNG"
                    .formatted(mucLuc.con(), mucLuc.tong()));
        }
        long sot = mucLuc.soDongExtension();
        if (sot > 0) {
            throw new IllegalStateException(
                    "Mục lục còn %d mục EXTENSION — pg_restore sẽ phát DROP EXTENSION và đỏ, DỪNG".formatted(sot));
        }
    }

    /**
     * Khối SQL đặt TRƯỚC phần thân của bản dump, chạy trong CÙNG giao dịch — hỏng ở đâu thì cả hai cùng lùi.
     *
     * <ol>
     *   <li>bỏ mọi bảng phân mảnh của {@code public} để {@code --clean} ⛔ vấp chỉ mục phân mảnh (lỗi 2);
     *   <li>gỡ quyền MẶC ĐỊNH cấp schema của chính vai trò đang nạp, để bảng dựng lại chỉ mang ACL của bản dump
     *       (lỗi 4). ⚠ Phạm vi: chỉ mục cấp schema ({@code IN SCHEMA}) — kho chỉ khai loại ấy; mục toàn cục
     *       còn gồm cả quyền của chủ sở hữu nên REVOKE ALL ở đó là tự tước quyền mình. Gặp loại đối tượng lạ
     *       thì NÉM, ⛔ lặng lẽ bỏ qua.
     * </ol>
     *
     * Chỉ ký tự ASCII: khối này đứng TRƯỚC câu {@code SET client_encoding} của phần thân.
     */
    public static String khoiTruocKhiNap() {
        return String.join(
                        "\n",
                        "-- Khoi nap TRUOC phan than cua ban dump, cung MOT giao dich (T68.3).",
                        "-- Nguon: KeHoachKhoiPhuc.khoiTruocKhiNap(). Tep deploy/backup/truoc-khi-nap.sql phai",
                        "-- trung tung byte (BackupRestoreFlagsTest) - sua mot noi la do CI.",
                        "",
                        "-- (1) Bo bang phan manh: --clean phat DROP INDEX cho tung phan manh, ma chi muc phan",
                        "--     manh khong xoa le duoc khi bang cha con (architecture-review 10.80).",
                        "DO $$",
                        "DECLARE r record; n int := 0;",
                        "BEGIN",
                        "    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace",
                        "              WHERE ns.nspname = 'public' AND c.relkind = 'p'",
                        "    LOOP",
                        "        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname);",
                        "        n := n + 1;",
                        "    END LOOP;",
                        "    RAISE NOTICE 'da bo % bang phan manh truoc khi nap', n;",
                        "END $$;",
                        "",
                        "-- (2) Go quyen MAC DINH cap schema cua vai tro dang nap: bang dung lai boi --clean nhan",
                        "--     quyen mac dinh cua DICH (V202608131006 cap arwd cho songnhue_app), ma ACL cua ban",
                        "--     dump chi GRANT, khong REVOKE => bang append-only mat REVOKE. Muc DEFAULT ACL cua",
                        "--     ban dump nam o luot ACL cuoi cung, nen dat lai chung SAU khi moi bang da dung xong.",
                        "DO $$",
                        "DECLARE r record; n int := 0;",
                        "BEGIN",
                        "    FOR r IN SELECT DISTINCT ns.nspname, d.defaclobjtype::text AS ma, a.grantee,",
                        "                    CASE d.defaclobjtype WHEN 'r' THEN 'TABLES' WHEN 'S' THEN 'SEQUENCES'",
                        "                                         WHEN 'f' THEN 'FUNCTIONS' WHEN 'T' THEN 'TYPES' END AS loai",
                        "               FROM pg_default_acl d",
                        "               JOIN pg_namespace ns ON ns.oid = d.defaclnamespace",
                        "               CROSS JOIN LATERAL aclexplode(d.defaclacl) a",
                        "              WHERE d.defaclrole = (SELECT oid FROM pg_roles WHERE rolname = current_user)",
                        "                AND a.grantee <> d.defaclrole",
                        "    LOOP",
                        "        IF r.loai IS NULL THEN",
                        "            RAISE EXCEPTION 'loai quyen mac dinh la: % (schema %)', r.ma, r.nspname;",
                        "        END IF;",
                        "        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I REVOKE ALL ON %s FROM %s',",
                        "                       current_user, r.nspname, r.loai,",
                        "                       CASE WHEN r.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(r.grantee)) END);",
                        "        n := n + 1;",
                        "    END LOOP;",
                        "    RAISE NOTICE 'da go % quyen mac dinh cua % truoc khi nap', n, current_user;",
                        "END $$;")
                + "\n";
    }

    /** Câu hỏi đếm extension có sẵn trên đích — bản dump đã lọc nên ⛔ tạo lại được chúng. */
    public static String cauDemPhanMoRong() {
        return "SELECT count(*) FROM pg_extension WHERE extname IN ('" + String.join("','", PHAN_MO_RONG) + "')";
    }

    private static String truongCuoi(String dong) {
        String gon = dong.strip();
        if (gon.isEmpty()) {
            return "";
        }
        int cach = Math.max(gon.lastIndexOf(' '), gon.lastIndexOf('\t'));
        return cach < 0 ? gon : gon.substring(cach + 1);
    }
}
