package com.songnhue.app.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.images.builder.Transferable;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.SongnhuePostgres;
import com.songnhue.core.application.backup.BackupService;
import com.songnhue.core.application.backup.KeHoachKhoiPhuc;

/**
 * <b>Bản sao lưu khôi phục được vào một CSDL TRẮNG — và ứng dụng đọc được nó</b> (T37.8 · T68.3).
 *
 * <h2>Vì sao bài này tồn tại</h2>
 *
 * <p>Tới 19/09/2026 job sao lưu đêm ({@code BackupService}) dump với {@code --no-privileges}. Khôi phục
 * vào một cluster mới: Flyway ⛔ chạy lại (lịch sử của nó nằm trong bản dump) ⇒ GRANT cấp bảng ⛔ ai cấp
 * ⇒ {@code songnhue_app} ⛔ đọc nổi bảng nào (§10.58). Sổ ghi việc kiểm GRANT là *"cần SSH"* — sai:
 * một CSDL trắng trong container dựng lại đúng điều kiện ấy.
 *
 * <p>⭐ Bài gọi <b>đúng</b> {@link BackupService#CO_DUMP} và các bước của {@link KeHoachKhoiPhuc} — bản
 * chép tay chuỗi cờ thì chỉ canh chính nó (T51.15). Khác production DUY NHẤT ở chỗ công cụ chạy: ở đây
 * chạy trong container (phiên bản khớp máy chủ, CI ⛔ cần cài postgresql-client — khuôn {@code
 * BackupRoleTest}); ở {@code RestoreService} chạy trong ảnh app.
 *
 * <h2>Ba vế — và vế thứ hai là vế cho phép tin vế thứ nhất</h2>
 *
 * <ol>
 *   <li>ACL của {@code songnhue_app} trên CSDL khôi phục <b>trùng khớp từng bảng/sequence</b> với nguồn.
 *   <li>Cùng đường khôi phục, bản dump CÓ {@code --no-privileges} ⇒ ứng dụng ⛔ đọc được. ⛔ có vế này thì
 *       vế 1 có thể xanh vì một lý do khác (luật 9).
 *   <li>Khôi phục <b>ĐÈ</b> lên CSDL đã có dữ liệu + bảng phân mảnh thoát 0 — còn đường cũ của nút M5.11
 *       ({@code pg_restore --clean} thẳng vào CSDL) thì đỏ. Lỗi ấy CHỈ hiện khi đích đã có dữ liệu,
 *       nên mọi lượt diễn tập trên cluster vừa dựng lại đều ⛔ thấy nó (§10.80). ⛔⛔ Và ACL sau lượt ĐÈ
 *       phải khớp nguồn: vế này viết TRƯỚC bản vá và đỏ trên khuyết tật thật ngày 19/09 — <b>72 quyền
 *       thừa</b> trên đúng các bảng append-only, vì bảng dựng lại nhận quyền mặc định của đích (lỗi 4 ở
 *       {@link KeHoachKhoiPhuc}). Script container đã đo 08/09 cũng mang lỗi ấy.
 * </ol>
 */
class KhoiPhucVaoCsdlTrangTest extends IntegrationTestBase {

    private static final String NGUON = "songnhue";

    private static final List<String> VAI_TRO =
            List.of("songnhue_owner", "songnhue_app", "songnhue_archiver", "songnhue_readonly");

    @Test
    @DisplayName(
            "⛔⛔ T37.8 — dump bằng đúng cờ BackupService, khôi phục vào CSDL TRẮNG: ACL của songnhue_app khớp nguồn TỪNG bảng")
    void khoiPhucVaoCsdlTrangGiuQuyen() throws Exception {
        String dump = dump(BackupService.CO_DUMP, "/tmp/ws69-giu-acl.dump");
        taoCsdlTrang("songnhue_ws69_trang");

        Container.ExecResult nap = khoiPhuc(dump, "songnhue_ws69_trang", "giu");
        assertThat(nap.getExitCode()).as("nạp thất bại:%n%s", nap.getStderr()).isZero();

        long nguoiDungNguon = demBangVaiTroUngDung(NGUON, "users");
        assertThat(nguoiDungNguon)
                .as("tiền đề: nguồn phải CÓ người dùng — tập rỗng ⛔ chứng minh gì (luật 7)")
                .isPositive();
        assertThat(demBangVaiTroUngDung("songnhue_ws69_trang", "users")).isEqualTo(nguoiDungNguon);

        Set<String> aclNguon = quyenCuaUngDung(NGUON);
        assertThat(aclNguon)
                .as("tiền đề: phép đo ACL phải thấy các bảng append-only")
                .contains("audit_logs:SELECT", "hydro_raw_logs:SELECT", "hydro_readings:SELECT")
                .doesNotContain("audit_logs:UPDATE", "audit_logs:DELETE", "hydro_raw_logs:DELETE");
        assertThat(quyenCuaUngDung("songnhue_ws69_trang"))
                .as("⛔ ACL của songnhue_app lệch nguồn sau khôi phục — GRANT ⛔ đi theo bản dump (§10.58)")
                .isEqualTo(aclNguon);
    }

    @Test
    @DisplayName(
            "⭐ Vế PHÂN BIỆT (luật 9) — cùng đường khôi phục, bản dump có --no-privileges ⇒ songnhue_app ⛔ đọc được users")
    void banDumpTuocAclThiUngDungKhongDocDuoc() throws Exception {
        List<String> coTuocAcl = new ArrayList<>(BackupService.CO_DUMP);
        coTuocAcl.add("--no-" + "privileges");
        String dump = dump(coTuocAcl, "/tmp/ws69-tuoc-acl.dump");
        taoCsdlTrang("songnhue_ws69_tuoc");

        Container.ExecResult nap = khoiPhuc(dump, "songnhue_ws69_tuoc", "tuoc");
        assertThat(nap.getExitCode())
                .as("bản dump tước ACL vẫn phải NẠP được — thứ hỏng là quyền, ⛔ phải cú pháp")
                .isZero();

        Container.ExecResult doc = psql("songnhue_app", "songnhue_ws69_tuoc", "SELECT count(*) FROM users");
        assertThat(doc.getExitCode())
                .as("nếu ứng dụng vẫn đọc được thì bài chính ⛔ chứng minh được rằng CO_DUMP giữ ACL")
                .isNotZero();
        assertThat(doc.getStderr()).contains("permission denied");
    }

    @Test
    @DisplayName(
            "⛔⛔ T68.3 — khôi phục ĐÈ lên CSDL ĐÃ có dữ liệu + bảng phân mảnh: đường mới thoát 0, đường cũ của M5.11 đỏ")
    void khoiPhucDeLenCsdlCoDuLieu() throws Exception {
        String dump = dump(BackupService.CO_DUMP, "/tmp/ws69-de.dump");
        taoCsdlTrang("songnhue_ws69_de");
        assertThat(khoiPhuc(dump, "songnhue_ws69_de", "de1").getExitCode()).isZero();
        assertThat(soBangPhanManh("songnhue_ws69_de"))
                .as("tiền đề: đích phải CÓ bảng phân mảnh — lỗi chỉ hiện khi có chúng")
                .isPositive();
        assertThat(quyenMacDinh("songnhue_ws69_de"))
                .as("tiền đề: đích phải mang ALTER DEFAULT PRIVILEGES như một CSDL đã migrate (V202608131006) — "
                        + "bảng dựng lại chỉ nhận quyền mặc định khi có chúng")
                .isNotEmpty()
                .isEqualTo(quyenMacDinh(NGUON));

        // Đường CŨ của nút M5.11 (trước 19/09): pg_restore --clean thẳng vào CSDL, một giao dịch.
        Container.ExecResult cu = trongContainer(
                "PGPASSWORD='%s' pg_restore --host=127.0.0.1 --username=songnhue_owner --dbname=songnhue_ws69_de --no-password"
                                .formatted(SongnhuePostgres.password())
                        + " --clean --if-exists --no-owner --exit-on-error --single-transaction"
                        + " --use-list=/tmp/ws69-de1.loc.toc " + dump);
        assertThat(cu.getExitCode())
                .as(
                        "tiền đề của vế so sánh: đường cũ phải ĐỎ trên đích có dữ liệu — nếu ⛔ thì khối bỏ bảng phân mảnh ⛔ chứng minh gì")
                .isNotZero();

        Container.ExecResult moi = khoiPhuc(dump, "songnhue_ws69_de", "de2");
        assertThat(moi.getExitCode())
                .as("đường mới (khối bỏ bảng phân mảnh + một giao dịch) phải nạp ĐÈ được:%n%s", moi.getStderr())
                .isZero();
        assertThat(demBangVaiTroUngDung("songnhue_ws69_de", "users")).isEqualTo(demBangVaiTroUngDung(NGUON, "users"));

        // ⛔⛔ Vế ACL của lượt ĐÈ. `--clean` DROP rồi CREATE lại từng bảng bằng songnhue_owner, nên bảng mới nhận
        //   quyền MẶC ĐỊNH của đích (V202608131006: arwd cho songnhue_app); phần ACL của bản dump chỉ GRANT so
        //   với acldefault, ⛔ bao giờ REVOKE quyền đến từ mặc định. Hỏng thì nhật ký kiểm toán sửa/xoá được bằng
        //   vai trò runtime — đúng trạng thái staging đã sống 13 ngày (§10.80), và ⛔ một dòng lỗi nào.
        Set<String> aclNguon = quyenCuaUngDung(NGUON);
        assertThat(aclNguon)
                .as("tiền đề: nguồn phải siết append-only — nếu ⛔ thì vế so sánh ⛔ chứng minh gì")
                .doesNotContain("audit_logs:UPDATE", "audit_logs:DELETE", "hydro_raw_logs:UPDATE");
        assertThat(quyenCuaUngDung("songnhue_ws69_de"))
                .as(
                        "⛔ khôi phục ĐÈ làm lệch ACL của songnhue_app — bảng dựng lại nhận quyền mặc định thay vì ACL của bản dump")
                .isEqualTo(aclNguon);
        assertThat(quyenMacDinh("songnhue_ws69_de"))
                .as(
                        "quyền MẶC ĐỊNH phải quay về đúng như nguồn — mất chúng là bảng của migration KẾ TIẾP ⛔ ai đọc được")
                .isEqualTo(quyenMacDinh(NGUON));
    }

    // ---- các bước, gọi đúng KeHoachKhoiPhuc ------------------------------------------------------

    private String dump(List<String> co, String dich) throws Exception {
        Container.ExecResult r = trongContainer(
                "PGPASSWORD='%s' pg_dump --host=127.0.0.1 --username=songnhue_readonly --dbname=%s %s --file=%s"
                        .formatted(SongnhuePostgres.password(), NGUON, String.join(" ", co), dich));
        assertThat(r.getExitCode()).as("pg_dump thất bại:%n%s", r.getStderr()).isZero();
        return dich;
    }

    /** Dựng một CSDL trắng đúng như {@code deploy/postgres/init/10-bootstrap.sh} (vai trò đã có sẵn trong cluster). */
    private void taoCsdlTrang(String ten) throws Exception {
        sieuNguoiDung("postgres", "DROP DATABASE IF EXISTS " + ten);
        sieuNguoiDung("postgres", "CREATE DATABASE " + ten);
        sieuNguoiDung("postgres", "REVOKE ALL ON DATABASE %s FROM PUBLIC".formatted(ten));
        sieuNguoiDung("postgres", "GRANT CONNECT ON DATABASE %s TO %s".formatted(ten, String.join(", ", VAI_TRO)));
        sieuNguoiDung("postgres", "GRANT CREATE ON DATABASE %s TO songnhue_owner".formatted(ten));
        sieuNguoiDung(ten, "REVOKE CREATE ON SCHEMA public FROM PUBLIC");
        sieuNguoiDung(ten, "GRANT ALL ON SCHEMA public TO songnhue_owner");
        sieuNguoiDung(ten, "GRANT USAGE ON SCHEMA public TO songnhue_app, songnhue_archiver, songnhue_readonly");
        for (String ext : KeHoachKhoiPhuc.PHAN_MO_RONG) {
            sieuNguoiDung(ten, "CREATE EXTENSION IF NOT EXISTS " + ext);
        }
    }

    /** Đúng trình tự {@code RestoreService.performRestore} — liệt kê → lọc → sinh SQL → ghép → nạp một giao dịch. */
    private Container.ExecResult khoiPhuc(String dump, String csdl, String nhan) throws Exception {
        String mucLuc = "/tmp/ws69-" + nhan + ".toc";
        String daLoc = "/tmp/ws69-" + nhan + ".loc.toc";
        String than = "/tmp/ws69-" + nhan + ".than.sql";
        String dau = "/tmp/ws69-" + nhan + ".dau.sql";
        String nap = "/tmp/ws69-" + nhan + ".nap.sql";

        Container.ExecResult ds =
                trongContainer("pg_restore --list --file=%s %s && cat %s".formatted(mucLuc, dump, mucLuc));
        assertThat(ds.getExitCode())
                .as("pg_restore --list thất bại:%n%s", ds.getStderr())
                .isZero();

        KeHoachKhoiPhuc.MucLucDaLoc loc =
                KeHoachKhoiPhuc.locMucLuc(Arrays.asList(ds.getStdout().split("\\R", -1)));
        KeHoachKhoiPhuc.kiemMucLuc(loc);
        SongnhuePostgres.instance()
                .copyFileToContainer(
                        Transferable.of(String.join("\n", loc.dong()).getBytes(StandardCharsets.UTF_8)), daLoc);

        Container.ExecResult sinh = trongContainer("pg_restore %s --use-list=%s --file=%s %s"
                .formatted(String.join(" ", KeHoachKhoiPhuc.CO_SINH_SQL), daLoc, than, dump));
        assertThat(sinh.getExitCode())
                .as("sinh SQL thất bại:%n%s", sinh.getStderr())
                .isZero();

        SongnhuePostgres.instance()
                .copyFileToContainer(
                        Transferable.of(KeHoachKhoiPhuc.khoiTruocKhiNap().getBytes(StandardCharsets.UTF_8)), dau);
        assertThat(trongContainer("cat %s %s > %s".formatted(dau, than, nap)).getExitCode())
                .isZero();

        return trongContainer(
                "PGPASSWORD='%s' psql --host=127.0.0.1 --username=songnhue_owner --dbname=%s --no-password %s --file=%s"
                        .formatted(SongnhuePostgres.password(), csdl, String.join(" ", KeHoachKhoiPhuc.CO_NAP), nap));
    }

    // ---- phép đo ---------------------------------------------------------------------------------

    /**
     * Tập {@code bảng:QUYỀN} mà {@code songnhue_app} có trên schema {@code public} — bảng, bảng phân mảnh,
     * view, sequence. Loại đối tượng do superuser sở hữu (của extension, VD {@code spatial_ref_sys}): chúng
     * do {@code 10-bootstrap.sh} tạo lại, ⛔ do bản dump — mục lục đã lọc chúng ra có chủ đích.
     */
    private Set<String> quyenCuaUngDung(String csdl) throws Exception {
        String sql =
                """
                SELECT c.relname || ':' || p.quyen
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE')) p(quyen)
                 WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm')
                   AND pg_get_userbyid(c.relowner) <> 'postgres'
                   AND has_table_privilege('songnhue_app', c.oid, p.quyen)
                UNION ALL
                SELECT c.relname || ':' || p.quyen
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 CROSS JOIN (VALUES ('USAGE'), ('SELECT'), ('UPDATE')) p(quyen)
                 WHERE n.nspname = 'public' AND c.relkind = 'S'
                   AND pg_get_userbyid(c.relowner) <> 'postgres'
                   AND has_sequence_privilege('songnhue_app', c.oid, p.quyen)
                 ORDER BY 1""";
        Container.ExecResult r = sieuNguoiDung(csdl, sql);
        return new LinkedHashSet<>(Arrays.stream(r.getStdout().split("\\R"))
                .filter(s -> !s.isBlank())
                .toList());
    }

    /** Tập {@code vai-trò:schema:loại:acl} của {@code pg_default_acl} — thứ quyết định quyền của bảng TẠO MỚI. */
    private Set<String> quyenMacDinh(String csdl) throws Exception {
        Container.ExecResult r = sieuNguoiDung(
                csdl,
                """
                SELECT pg_get_userbyid(d.defaclrole) || ':' || coalesce(n.nspname, '*') || ':' || d.defaclobjtype::text
                       || ':' || array_to_string(ARRAY(SELECT x::text FROM unnest(d.defaclacl) x ORDER BY 1), ',')
                  FROM pg_default_acl d
                  LEFT JOIN pg_namespace n ON n.oid = d.defaclnamespace
                 ORDER BY 1""");
        return new LinkedHashSet<>(Arrays.stream(r.getStdout().split("\\R"))
                .filter(s -> !s.isBlank())
                .toList());
    }

    private long demBangVaiTroUngDung(String csdl, String bang) throws Exception {
        Container.ExecResult r = psql("songnhue_app", csdl, "SELECT count(*) FROM " + bang);
        assertThat(r.getExitCode())
                .as("songnhue_app ⛔ đọc được %s trên %s:%n%s", bang, csdl, r.getStderr())
                .isZero();
        return Long.parseLong(r.getStdout().strip());
    }

    private long soBangPhanManh(String csdl) throws Exception {
        Container.ExecResult r = sieuNguoiDung(
                csdl,
                "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname = 'public' AND c.relkind = 'p'");
        return Long.parseLong(r.getStdout().strip());
    }

    private Container.ExecResult sieuNguoiDung(String csdl, String sql) throws Exception {
        Container.ExecResult r = psql("postgres", csdl, sql);
        assertThat(r.getExitCode())
                .as("SQL siêu người dùng thất bại trên %s: %s%n%s", csdl, sql, r.getStderr())
                .isZero();
        return r;
    }

    private Container.ExecResult psql(String vaiTro, String csdl, String sql) throws Exception {
        return SongnhuePostgres.instance()
                .execInContainer(
                        "sh",
                        "-c",
                        "PGPASSWORD='%s' psql --host=127.0.0.1 --username=%s --dbname=%s --no-password -At -c \"%s\""
                                .formatted(SongnhuePostgres.password(), vaiTro, csdl, sql.replace("\"", "\\\"")));
    }

    private Container.ExecResult trongContainer(String lenh) throws Exception {
        return SongnhuePostgres.instance().execInContainer("sh", "-c", lenh);
    }
}
