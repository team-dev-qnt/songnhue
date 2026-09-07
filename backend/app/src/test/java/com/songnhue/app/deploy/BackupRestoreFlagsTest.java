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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Bản dump phải mang theo GRANT, và lượt khôi phục phải bỏ được các mục của extension.</b>
 *
 * <h2>Lỗi này làm hỏng đường quay lui DỮ LIỆU duy nhất của hệ</h2>
 *
 * Tìm ra ngày 26/8 bằng một lượt <b>khôi phục thật</b> trên staging, không tìm ra bằng đọc mã.
 *
 * <p>GRANT cấp bảng của dự án do <b>migration Flyway</b> cấp, không do {@code 10-bootstrap.sh}. Khi
 * khôi phục vào một cluster mới:
 *
 * <ol>
 *   <li>{@code flyway_schema_history} được nạp lại cùng dữ liệu → Flyway báo <i>"Schema is up to
 *       date. No migration necessary"</i> → migration cấp quyền <b>không chạy</b>;
 *   <li>{@code --no-privileges} đã tước ACL khỏi bản dump.
 * </ol>
 *
 * Kết quả: một CSDL đầy đủ dữ liệu mà {@code songnhue_app} <b>không đọc nổi một bảng nào</b>. App
 * chết ngay lúc khởi động với {@code ERROR: permission denied for table users}.
 *
 * <p>Hệ này cố ý không có PITR (architecture-review §6.5), nên bản dump là đường quay lui dữ liệu
 * <b>duy nhất</b> — và nó hỏng ở đúng tình huống nó tồn tại để phục vụ.
 *
 * <p>⚠ Chú ý cách nó ẩn: khôi phục vào một CSDL <b>đã migrate</b> thì {@code ALTER DEFAULT
 * PRIVILEGES} có sẵn nên bảng dựng lại tự có GRANT — tức đường hay được thử thì chạy tốt, còn đường
 * dùng lúc thảm hoạ thì hỏng. Cùng hình dạng luật 3.
 */
class BackupRestoreFlagsTest {

    /** Ghép từ hai mảnh để chính tệp này không tự khớp vào bộ quét (đã mắc ba lượt trong dự án). */
    private static final String CO_CAM = "--no-" + "privileges";

    private static final List<String> SCRIPT =
            List.of("deploy/backup/backup.sh", "deploy/backup/pre-deploy-dump.sh", "deploy/backup/restore.sh");

    @Test
    @DisplayName("⭐⭐ Không script sao lưu/khôi phục nào được dùng `--no-privileges`")
    void khongScriptNaoTuocAcl() {
        for (String ten : SCRIPT) {
            assertThat(boChuThich(doc(timTuGocKho(ten))))
                    .as(
                            """
                            `%s` dùng `%s`.

                            Bản dump mất ACL, mà GRANT cấp bảng do migration Flyway cấp — và Flyway \
                            KHÔNG chạy lại trên CSDL vừa khôi phục vì `flyway_schema_history` nói đã \
                            áp đủ. Khôi phục ra một CSDL `songnhue_app` không đọc nổi, app chết ở \
                            `permission denied for table users`.

                            Đây là đường quay lui DỮ LIỆU duy nhất của hệ (không có PITR).""",
                            ten, CO_CAM)
                    .doesNotContain(CO_CAM);
        }
    }

    @Test
    @DisplayName("⭐ `restore.sh` lọc mục lục — nếu không thì pg_restore đỏ ở mục của extension")
    void restorePhaiLocMucLuc() {
        String than = boChuThich(doc(timTuGocKho("deploy/backup/restore.sh")));

        assertThat(than)
                .as("Phải truyền mục lục đã lọc cho pg_restore, không thì `--exit-on-error` dừng ở extension")
                .contains("--use-list");
        assertThat(than)
                .as("`COMMENT ON EXTENSION` đòi quyền chủ sở hữu extension — songnhue_owner không có")
                .contains("COMMENT - EXTENSION");
        assertThat(than)
                .as("Đối tượng do superuser tạo qua extension (spatial_ref_sys) phải bị bỏ theo CHỦ SỞ HỮU, "
                        + "không theo danh sách tên — liệt kê tên là danh sách sẽ mục khi thêm extension thứ tư")
                .contains("!= \"postgres\"");
    }

    @Test
    @DisplayName("⭐ `restore.sh` dừng nếu bộ lọc ăn quá tay — bỏ nhầm hết là khôi phục ra CSDL rỗng")
    void restoreChanBoLocAnQuaTay() {
        // Một bộ lọc hỏng (đổi định dạng `pg_restore --list`, awk sai cột) cho ra mục lục gần rỗng,
        // và pg_restore vẫn thoát 0. "Khôi phục thành công" ra một CSDL trống là hỏng câm nhất.
        assertThat(boChuThich(doc(timTuGocKho("deploy/backup/restore.sh")))).contains("-gt 100");
    }

    @Test
    @DisplayName("⛔ `restore.sh` phải dặn đọc thử BẰNG VAI TRÒ ỨNG DỤNG — chủ sở hữu luôn đọc được")
    void restorePhaiDanDocBangVaiTroUngDung() {
        // CLAUDE.md luật 9: một khẳng định không phân biệt được hai trạng thái thì không khẳng định
        // gì. `psql -U songnhue_owner -c 'SELECT count(*) FROM users'` XANH trong cả hai trường hợp.
        assertThat(doc(timTuGocKho("deploy/backup/restore.sh")))
                .as("Bước nghiệm thu sau khôi phục phải hỏi bằng `songnhue_app`")
                .contains("songnhue_app");
    }

    @Test
    @DisplayName("⭐⭐ Không script `deploy/` nào có dòng chú thích NẰM GIỮA một lệnh nối dòng bằng `\\`")
    void khongChuThichGiuaLenhNoiDong() {
        // ⚠ `bash -n` KHÔNG bắt được lỗi này: chèn `#` vào giữa một lệnh nối dòng cắt lệnh làm đôi,
        //   nhưng phần còn lại vẫn hợp lệ cú pháp — nên nó XANH trong khi lệnh chạy đã là lệnh khác.
        //   Mắc ngày 26/8 khi thêm chú thích vào `pre-deploy-dump.sh`; chỉ lộ ra khi chạy thật, ở
        //   `--format=custom: command not found`. CLAUDE.md luật 9: một phép kiểm không phân biệt
        //   được hai trạng thái thì không kiểm gì.
        List<String> viPham = new ArrayList<>();

        for (Path tep : scriptTrongDeploy()) {
            String[] dong = doc(tep).split("\n", -1);
            for (int i = 0; i + 1 < dong.length; i++) {
                if (dong[i].stripTrailing().endsWith("\\")
                        && dong[i + 1].stripLeading().startsWith("#")) {
                    viPham.add("%s:%d  %s".formatted(tep.getFileName(), i + 2, dong[i + 1].strip()));
                }
            }
        }

        assertThat(viPham)
                .as(
                        """
                        Dòng chú thích nằm ngay sau một dòng kết thúc bằng `\\`:

                        %s

                        Bash coi dấu `\\` cuối dòng là nối với dòng KẾ TIẾP. Dòng kế tiếp là chú                         thích thì lệnh bị cắt tại đó, phần đối số còn lại trở thành một LỆNH MỚI, và                         `bash -n` vẫn xanh. Đưa chú thích ra TRƯỚC cả lệnh.""",
                        String.join("\n", viPham))
                .isEmpty();
    }

    @Test
    @DisplayName("Quét ra ít nhất 5 script trong deploy/ — chặn xanh-trên-tập-rỗng")
    void quetRaDuScript() {
        assertThat(scriptTrongDeploy()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Ba script đều tồn tại — chặn xanh-trên-tập-rỗng")
    void baScriptDeuTonTai() {
        for (String ten : SCRIPT) {
            assertThat(doc(timTuGocKho(ten))).as("`%s` phải có nội dung", ten).hasSizeGreaterThan(500);
        }
    }

    // -------------------------------------------------------------------------

    private static List<Path> scriptTrongDeploy() {
        Path goc = timTuGocKho("deploy");
        try (Stream<Path> cay = Files.walk(goc)) {
            return cay.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".sh"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được " + goc, e);
        }
    }

    /** Bỏ chú thích shell — bài kiểm nói về LỆNH được chạy, không nói về đoạn văn giải thích nó. */
    static String boChuThich(String noiDung) {
        StringBuilder ket = new StringBuilder();
        for (String dong : noiDung.split("\n", -1)) {
            if (!dong.stripLeading().startsWith("#")) {
                ket.append(dong).append('\n');
            }
        }
        return ket.toString();
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
