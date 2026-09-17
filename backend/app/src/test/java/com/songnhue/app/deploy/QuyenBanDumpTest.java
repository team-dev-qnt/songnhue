package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi script sinh bản dump phải hạ quyền tệp — ⛔ user khác trên máy đọc được toàn bộ CSDL.</b>
 *
 * <p>Đo 08/09/2026 trên VPS-1: mọi {@code *.dump} ở {@code /var/lib/songnhue/backup} mang quyền {@code 644}.
 * Mỗi tệp là TOÀN BỘ CSDL — {@code users.password_hash}, {@code user_totp.secret_encrypted}, bảng
 * {@code employee_sensitive} (T11.96 → T61.8).
 *
 * <h2>⛔ Dòng nợ gốc mô tả SAI cách vá, theo hai cách</h2>
 *
 * <ol>
 *   <li><i>"{@code umask 077} trong {@code pre-deploy-dump.sh}"</i> — tệp {@code .dump} ở đó do {@code pg_dump}
 *       tạo <b>bên trong container</b> qua {@code docker exec}, nên umask của shell host ⛔ chạm tới nó. Phải
 *       {@code chmod} bên trong container.
 *   <li><i>"{@code chmod 600}"</i> — host đọc lại bản dump ({@code sha256sum}, {@code verify-no-keys.sh}) bằng
 *       user SSH qua <b>nhóm</b> của thư mục (setgid), và VPS-2 kéo về cũng qua nhóm ấy. {@code 600} là mọi
 *       lượt deploy đỏ ở bước băm. Quyền đúng là {@code 640}.
 * </ol>
 *
 * <p>Phạm vi do bộ canh ĐO (luật 28): mọi {@code deploy/**}{@code /*.sh} có lệnh {@code pg_dump} ngoài chú
 * thích. Đường trong ứng dụng ({@code BackupService}) canh riêng ở {@code BackupServiceTest}.
 */
class QuyenBanDumpTest {

    private static final Pattern UMASK_CHAT = Pattern.compile("^\\s*umask\\s+0?[0-7][27]7\\s*$");
    private static final Pattern CHMOD_TRONG_CONTAINER = Pattern.compile("docker\\s+exec\\b.*\\bchmod\\s+640\\b");

    @Test
    @DisplayName("⛔ Mọi script có `pg_dump` đặt umask chặt TRƯỚC lệnh dump; dump trong container thì chmod 640")
    void moiScriptSinhDumpDeuHaQuyen() {
        List<String> viPham = new ArrayList<>();
        for (Path tep : scriptCoPgDump()) {
            viPham.addAll(kiem(tep.getFileName().toString(), doc(tep)));
        }

        assertThat(viPham)
                .as(
                        """
                        Script sinh bản dump mà ⛔ hạ quyền tệp:

                        %s

                        Bản dump là TOÀN BỘ CSDL (password_hash, employee_sensitive). Dump chạy trên HOST ⇒ \
                        `umask 027` trước lệnh `pg_dump`. Dump chạy qua `docker exec` ⇒ umask host ⛔ chạm \
                        tới, phải `docker exec <ct> chmod 640 <tệp>` sau khi dump. ⛔ 600: host băm tệp bằng \
                        user SSH qua NHÓM, và VPS-2 kéo về cũng qua nhóm (T61.8).""",
                        String.join("\n", viPham))
                .isEmpty();
    }

    @Test
    @DisplayName("Phạm vi đo được gồm cả hai script sinh dump đang biết — chặn xanh-trên-tập-rỗng")
    void phamViDoDuocKhongRong() {
        assertThat(scriptCoPgDump().stream().map(p -> p.getFileName().toString()))
                .as("Bộ quét phải thấy các script đang gọi pg_dump; thiếu là bộ lọc `pg_dump` đã hỏng")
                .contains("backup.sh", "pre-deploy-dump.sh");
    }

    @Test
    @DisplayName("Tự kiểm: bộ canh BẮT được bốn dạng vi phạm và ⛔ bắt dạng đúng (luật 1)")
    void tuKiem() {
        String dung =
                "set -e\numask 027\ndocker exec -i \"$CT\" pg_dump --file=\"$F\"\ndocker exec -i \"$CT\" chmod 640 \"$F\"\n";
        assertThat(kiem("dung.sh", dung)).isEmpty();
        assertThat(kiem("dung-host.sh", "umask 077\npg_dump --file=x\n")).isEmpty();

        assertThat(kiem("thieu-umask.sh", "pg_dump --file=x\n")).hasSize(1);
        assertThat(kiem("umask-sau.sh", "pg_dump --file=x\numask 027\n")).hasSize(1);
        assertThat(kiem("umask-long.sh", "umask 022\npg_dump --file=x\n")).hasSize(1);
        assertThat(kiem("umask-trong-chu-thich.sh", "# umask 027\npg_dump --file=x\n"))
                .hasSize(1);
        assertThat(kiem("container-thieu-chmod.sh", "umask 027\ndocker exec -i ct pg_dump --file=x\n"))
                .hasSize(1);
        assertThat(kiem(
                        "container-nhieu-dong.sh",
                        "umask 027\nif ! docker exec -i \\\n    \"$CT\" pg_dump \\\n    --file=x; then exit 1; fi\n"))
                .as("docker exec và pg_dump ở hai dòng nối bằng `\\` vẫn là dump TRONG container")
                .hasSize(1);
        assertThat(kiem("chi-echo.sh", "echo \"→ pg_dump xong\"\n"))
                .as("chuỗi trong echo ⛔ phải lệnh")
                .isEmpty();
        assertThat(kiem("chmod-600.sh", "umask 027\ndocker exec -i ct pg_dump --file=x\ndocker exec ct chmod 600 x\n"))
                .as("600 làm host ⛔ băm được bản dump ⇒ phải coi là vi phạm")
                .hasSize(1);
    }

    // -------------------------------------------------------------------------

    /**
     * Trả danh sách vi phạm của một script (rỗng = đạt).
     *
     * <p>Xét trên <b>lệnh logic</b>: bỏ chú thích, ghép các dòng nối bằng {@code \\} — lệnh dump thật của
     * {@code pre-deploy-dump.sh} trải qua bốn dòng, và {@code docker exec} với {@code pg_dump} nằm ở hai dòng
     * khác nhau. Bỏ chuỗi trong ngoặc trước khi tìm {@code pg_dump} để {@code echo "→ pg_dump …"} ⛔ bị
     * đếm là một lời gọi (cùng họ T46.7: chú thích/chuỗi bị đếm là mã).
     */
    static List<String> kiem(String ten, String noiDung) {
        List<String> lenh = lenhLogic(noiDung);

        int viTriDump = -1;
        int viTriUmask = -1;
        boolean dumpTrongContainer = false;
        boolean coChmod = false;
        for (int i = 0; i < lenh.size(); i++) {
            String l = lenh.get(i);
            String boChuoi = l.replaceAll("\"[^\"]*\"", " ").replaceAll("'[^']*'", " ");
            if (viTriUmask < 0 && UMASK_CHAT.matcher(l).matches()) {
                viTriUmask = i;
            }
            if (LENH_PG_DUMP.matcher(boChuoi).find()) {
                if (viTriDump < 0) {
                    viTriDump = i;
                }
                if (boChuoi.matches(".*\\bdocker\\s+exec\\b.*")) {
                    dumpTrongContainer = true;
                }
            }
            if (CHMOD_TRONG_CONTAINER.matcher(l).find()) {
                coChmod = true;
            }
        }

        List<String> viPham = new ArrayList<>();
        if (viTriDump < 0) {
            return viPham;
        }
        if (viTriUmask < 0 || viTriUmask > viTriDump) {
            viPham.add("%s: ⛔ có `umask 027`/`077` TRƯỚC lệnh pg_dump".formatted(ten));
        }
        if (dumpTrongContainer && !coChmod) {
            viPham.add("%s: pg_dump chạy qua `docker exec` mà ⛔ có `docker exec … chmod 640`".formatted(ten));
        }
        return viPham;
    }

    private static final Pattern LENH_PG_DUMP = Pattern.compile("(^|\\s)pg_dump(\\s|$)");

    private static List<String> lenhLogic(String noiDung) {
        List<String> lenh = new ArrayList<>();
        StringBuilder dangGhep = new StringBuilder();
        for (String dong : noiDung.split("\n", -1)) {
            if (dong.stripLeading().startsWith("#")) {
                continue;
            }
            String d = dong.stripTrailing();
            if (d.endsWith("\\")) {
                dangGhep.append(d, 0, d.length() - 1).append(' ');
                continue;
            }
            dangGhep.append(d);
            lenh.add(dangGhep.toString().strip());
            dangGhep.setLength(0);
        }
        if (!dangGhep.isEmpty()) {
            lenh.add(dangGhep.toString().strip());
        }
        return lenh;
    }

    private static List<Path> scriptCoPgDump() {
        Path goc = timTuGocKho("deploy");
        try (Stream<Path> cay = Files.walk(goc)) {
            return cay.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".sh"))
                    .filter(p -> lenhLogic(doc(p)).stream()
                            .map(l -> l.replaceAll("\"[^\"]*\"", " ").replaceAll("'[^']*'", " "))
                            .anyMatch(l -> LENH_PG_DUMP.matcher(l).find()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được " + goc, e);
        }
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
