package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>{@code host-prepare.sh} phải đo quyền bằng cách GHI THẬT, không bằng {@code stat}.</b>
 *
 * <h2>Chuyện đã xảy ra — 7/9/2026, VPS-1</h2>
 *
 * Script chạy <b>hai lượt, thoát 0 cả hai</b>. Ba phép {@code kiem_quyen} in ra ba dòng xanh. Rồi
 * bước {@code rsync} của lượt triển khai thoát <b>23</b> với hàng chục dòng
 * {@code Permission denied}, vì {@code /opt/songnhue} thuộc {@code root:root} — người triển khai
 * không ghi vào được.
 *
 * <p>Nguyên nhân: {@code mkdir -p /opt/songnhue/keys} tạo <b>thư mục cha</b> bằng {@code root}, và
 * thư mục cha ấy <b>không nằm trong</b> {@code $THU_MUC} nên không phép đo nào chạm tới nó. Đối
 * chiếu staging — nơi CD chạy được suốt — thư mục ấy là {@code songnhue:songnhue}.
 *
 * <h2>Vì sao {@code stat} không đủ, dù có thêm dòng cho thư mục gốc</h2>
 *
 * {@code stat} nói thư mục đang <i>mang nhãn</i> gì. Câu hỏi duy nhất có ý nghĩa ở đây là
 * <i>người triển khai có ghi vào được không</i> — và hai câu ấy tách nhau ở ACL, ở
 * {@code nosuid}/{@code ro} mount, ở uid trùng tên khác số. Một khẳng định không phân biệt được hai
 * trạng thái thì không khẳng định gì (luật 9), nên phép đo phải là một lượt {@code touch} thật.
 *
 * <h2>Bài kiểm này canh hai điều, và điều thứ hai là điều đã hỏng</h2>
 *
 * <ol>
 *   <li><b>Cấu trúc</b> — mọi thư mục script tạo ra đều phải có ít nhất một phép đo đứng sau. Đây
 *       chính là bất biến bị vi phạm: {@code /opt/songnhue} không có phép nào.</li>
 *   <li><b>Hành vi</b> — trích {@code kiem_ghi_duoc} ra chạy thật trên hai thư mục khác nhau đúng
 *       một điều (ghi được / không), và đòi nó cho hai kết quả khác nhau. Chạy được không cần
 *       {@code root}: nhánh không-root của hàm dùng {@code touch} trực tiếp.</li>
 * </ol>
 */
class HostPrepareQuyenTest {

    private static final String THU_MUC_GOC = "/opt/songnhue";

    /** {@code THU_MUC="a b c"} — danh sách thư mục script tạo. */
    private static final Pattern KHAI_THU_MUC = Pattern.compile("^THU_MUC=\"([^\"]+)\"", Pattern.MULTILINE);

    /** {@code kiem_quyen <đường dẫn> …} hoặc {@code kiem_ghi_duoc <đường dẫn>}. */
    private static final Pattern GOI_PHEP_DO =
            Pattern.compile("^\\s*(kiem_quyen|kiem_ghi_duoc)\\s+(\\S+)", Pattern.MULTILINE);

    @Test
    @DisplayName("⭐⭐ Mọi thư mục script tạo ra đều có phép đo đứng sau — kể cả thư mục GỐC")
    void moiThuMucTaoRaDeuCoPhepDo() {
        String noiDung = doc(script());

        List<String> taoRa = thuMucTaoRa(noiDung);
        List<String> daDo = duongDanDuocDo(noiDung);

        // Tự kiểm: hai bộ trích phải tìm được thứ gì đó. Một regex hỏng cho ra tập rỗng, và
        // `containsAll` trên tập rỗng luôn ĐÚNG — đó là xanh giả kinh điển (luật 7).
        assertThat(taoRa)
                .as("phải trích được danh sách THU_MUC; rỗng nghĩa là regex hỏng, không phải script sạch")
                .hasSizeGreaterThanOrEqualTo(4);
        assertThat(daDo)
                .as("phải trích được các lời gọi phép đo; rỗng nghĩa là regex hỏng")
                .hasSizeGreaterThanOrEqualTo(4);

        assertThat(daDo)
                .as("thư mục được tạo mà không phép đo nào chạm tới — đúng hình dạng đã làm đỏ rsync 7/9")
                .containsAll(taoRa);
    }

    @Test
    @DisplayName("⭐⭐ Thư mục gốc phải đo bằng GHI THẬT, `stat` không phân biệt được")
    void thuMucGocPhaiDoBangGhiThat() {
        String noiDung = doc(script());

        assertThat(noiDung)
                .as("`/opt/songnhue` phải nằm trong THU_MUC — nó là thứ rsync ghi vào")
                .contains(THU_MUC_GOC + " ");
        assertThat(noiDung)
                .as("và phải được đo bằng kiem_ghi_duoc, không phải chỉ kiem_quyen")
                .contains("kiem_ghi_duoc " + THU_MUC_GOC);
    }

    @Test
    @DisplayName("⭐⭐ `kiem_ghi_duoc` PHÂN BIỆT được ghi-được với không-ghi-được (chạy thật)")
    void kiemGhiDuocPhanBietHaiTrangThai(@TempDir Path tam) throws Exception {
        Path ghiDuoc = Files.createDirectory(tam.resolve("ghi-duoc"));
        Path khongGhiDuoc = Files.createDirectory(tam.resolve("khong-ghi-duoc"));
        khongGhiDuoc.toFile().setWritable(false, false);

        // Nhánh không-root của hàm mới có nghĩa; bài kiểm chạy dưới user thường nên đi đúng nhánh ấy.
        if (khongGhiDuoc.toFile().canWrite()) {
            // Chạy dưới root (một số runner container) — chmod không cản được root, phép phản chứng
            // mất ý nghĩa. Nói ra thay vì báo xanh trên một thứ chưa kiểm (luật 7).
            fail("Bài kiểm này phải chạy dưới user thường; đang chạy dưới quyền ghi được mọi nơi");
        }

        String ket1 = chayKiemGhiDuoc(ghiDuoc);
        String ket2 = chayKiemGhiDuoc(khongGhiDuoc);

        assertThat(ket1).as("thư mục ghi được phải cho kết quả đạt").contains("ghi được");
        assertThat(ket2).as("thư mục KHÔNG ghi được phải cho kết quả hỏng").contains("KHONG");
        assertThat(ket1)
                .as("hai trạng thái phải cho hai kết quả KHÁC nhau — nếu giống thì hàm không đo gì")
                .isNotEqualTo(ket2);
    }

    @Test
    @DisplayName("Script phải có quyền chạy trong kho")
    void scriptPhaiCoQuyenChay() {
        assertThat(Files.isExecutable(script()))
                .as("host-prepare.sh phải executable; `chmod +x` rồi commit lại")
                .isTrue();
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    /**
     * Trích {@code kiem_ghi_duoc} ra một script độc lập rồi chạy trên {@code duongDan}. Không chép
     * lại logic sang Java — chép lại là kiểm bản chép, không kiểm bản chạy.
     */
    private static String chayKiemGhiDuoc(Path duongDan) throws IOException, InterruptedException {
        String than = trichHam(doc(script()), "kiem_ghi_duoc");
        assertThat(than)
                .as("không trích được thân hàm kiem_ghi_duoc — script đã đổi hình dạng")
                .isNotBlank();

        Path tam = Files.createTempFile("kiem-ghi-", ".sh");
        // ⚠ Nối chuỗi, KHÔNG `.formatted()`: thân hàm shell chứa `printf '%s'` và `String.formatted`
        //   sẽ hiểu đó là chỗ thay thế của Java rồi ném MissingFormatArgumentException. Đã mắc.
        Files.writeString(
                tam,
                "set -u\n"
                        + "NGUOI_SSH=\"${SUDO_USER:-$(id -un)}\"\n"
                        + "do_dac() { printf 'DAT %s %s\\n' \"$1\" \"$2\"; }\n"
                        + "hong()   { printf 'KHONG %s\\n' \"$*\"; }\n"
                        + than
                        + "\nkiem_ghi_duoc \"$1\"\n",
                StandardCharsets.UTF_8);

        // `bash` tường minh: shell mặc định của máy dev là zsh, runner chạy bash (luật 20).
        ProcessBuilder pb = new ProcessBuilder("bash", tam.toString(), duongDan.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        Files.deleteIfExists(tam);
        return ra.trim();
    }

    /** Cắt từ {@code <ten>() {} cho tới dòng {@code }} ở cột 0. */
    private static String trichHam(String noiDung, String ten) {
        Matcher m = Pattern.compile(
                        "^" + Pattern.quote(ten) + "\\(\\)\\s*\\{.*?^\\}", Pattern.MULTILINE | Pattern.DOTALL)
                .matcher(noiDung);
        return m.find() ? m.group() : "";
    }

    private static List<String> thuMucTaoRa(String noiDung) {
        Matcher m = KHAI_THU_MUC.matcher(noiDung);
        List<String> ra = new ArrayList<>();
        while (m.find()) {
            for (String d : m.group(1).trim().split("\\s+")) {
                if (!d.isBlank()) {
                    ra.add(d);
                }
            }
        }
        return ra;
    }

    private static List<String> duongDanDuocDo(String noiDung) {
        Matcher m = GOI_PHEP_DO.matcher(noiDung);
        List<String> ra = new ArrayList<>();
        while (m.find()) {
            // Bỏ định nghĩa hàm (`kiem_quyen() {`), chỉ giữ lời gọi thật.
            if (!m.group(2).startsWith("(")) {
                ra.add(m.group(2));
            }
        }
        return ra;
    }

    private static Path script() {
        return timTuGocKho("deploy/host-prepare.sh");
    }

    private static String doc(Path tep) {
        try {
            return Files.readString(tep, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
