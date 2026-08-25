package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Đụng tới gói GHCR thì phải ĐÃ đăng nhập, và ghi tag thì phải có quyền GHI.</b>
 *
 * <h2>Hai lỗi đã xảy ra, cách nhau vài giờ, và cả hai đều chỉ ra sai chỗ</h2>
 *
 * <b>24/8 (§10.43)</b> — CD Staging hỏng với <i>"Không tìm thấy image 'app'"</i> kèm ba bước chẩn
 * đoán trỏ vào job đóng gói · gói GHCR · bước đăng nhập. Cả ba đều là ngõ cụt: job xanh, gói đủ
 * tag, log ghi rõ {@code Login Succeeded!}. Nguyên nhân thật: lượt tra đi <b>ẩn danh</b>, mà gói
 * GHCR mặc định <b>riêng tư</b> kể cả khi kho mã công khai (kiểm chứng: manifest ẩn danh trả 403).
 *
 * <p><b>25/8 (§10.44)</b> — sửa xong phần trên, lượt chạy tra được image rồi hỏng ở bước GHI tag
 * với {@code 403 denied: installation not allowed to Write organization package}. Workflow khai
 * {@code packages: read}: đủ cho phần ĐỌC chiếm 90% số dòng, thiếu đúng cho một bước ở cuối.
 *
 * <h2>Bài này còn lại hai khẳng định — và một khẳng định mới của kiểu gọi chung</h2>
 *
 * Bộ canh cũ có bốn bài; hai bài đã bỏ cùng với cơ chế chúng canh (vòng quét 50 ứng viên và biến
 * {@code DOCKER_CONFIG} nay không còn ở workflow nào). Bài học vẫn nằm ở §10.43 — chỗ của nó — và
 * ở chú thích {@code ⛔} trong {@code deploy.yml}.
 *
 * <p>Khẳng định mới: từ 25/8 hai workflow triển khai gọi chung một thân
 * ({@code .github/workflows/deploy.yml}). Reusable workflow <b>không tự cấp quyền cho mình được</b>
 * — token nó nhận bị chặn trên bởi quyền của job GỌI. Nên khai {@code packages: write} ở thân chung
 * là chưa đủ; quên khai ở caller thì lỗi rơi đúng vào bước cuối, y hệt §10.44.
 *
 * <p>Danh sách tệp <b>tự tìm</b>, không viết cứng: bài kiểm phải theo được khi workflow đổi tên.
 */
class GhcrLookupAuthTest {

    private static final String TRA_IMAGE = "docker manifest inspect";

    /** Lệnh GHI tag mới lên gói GHCR — khác hẳn lượt tra, và cần quyền khác hẳn. */
    private static final String GHI_TAG = "docker buildx imagetools create";

    private static final String DANG_NHAP = "docker/login-action";

    private static final String THAN_CHUNG = "./.github/workflows/deploy.yml";

    /** Một mục trong khối {@code permissions:} — {@code   packages: write}. */
    private static final Pattern MUC_QUYEN = Pattern.compile("^\\s+([a-z-]+)\\s*:\\s*(\\S+)\\s*$");

    @Test
    @DisplayName("⭐ Bước đăng nhập GHCR phải đứng TRƯỚC lượt tra image đầu tiên")
    void dangNhapPhaiDungTruocLuotTra() {
        List<Path> tep = workflowChua(TRA_IMAGE);
        assertThat(tep)
                .as("không workflow nào còn tra image — bài kiểm này sẽ xanh trên tập rỗng")
                .isNotEmpty();

        for (Path duongDan : tep) {
            // ⚠ PHẢI bỏ dòng chú thích trước khi đo vị trí: các tệp này nhắc tên lệnh
            //   `docker manifest inspect` trong chú thích ở đầu tệp, đứng trước bước đăng nhập. Đo
            //   văn bản thay vì đo cấu trúc là đúng lỗi CLAUDE.md luật 2.
            String noiDung = boChuThich(doc(duongDan));
            int viTriTra = noiDung.indexOf(TRA_IMAGE);
            int viTriDangNhap = noiDung.indexOf(DANG_NHAP);

            assertThat(viTriDangNhap)
                    .as("`%s` tra image mà không có bước `%s` nào", duongDan.getFileName(), DANG_NHAP)
                    .isGreaterThan(-1);
            assertThat(viTriDangNhap)
                    .as(
                            """
                            `%s` đặt bước đăng nhập GHCR SAU lượt tra image.

                            Gói GHCR mặc định riêng tư, nên lượt tra ẩn danh trượt rồi báo "không tìm \
                            thấy image" — sai hẳn nguyên nhân (§10.43).""",
                            duongDan.getFileName())
                    .isLessThan(viTriTra);
        }
    }

    @Test
    @DisplayName("⭐⭐ Workflow GHI tag lên GHCR phải khai `packages: write`")
    void ghiTagThiPhaiKhaiQuyenGhi() {
        List<Path> tep = workflowChua(GHI_TAG);
        assertThat(tep)
                .as("không workflow nào còn ghi tag — bài kiểm này sẽ xanh trên tập rỗng")
                .isNotEmpty();

        for (Path duongDan : tep) {
            String noiDung = boChuThich(doc(duongDan));
            assertThat(quyenGhiGoi(noiDung))
                    .as(
                            """
                            `%s` chạy `%s` (GHI một tag mới lên gói GHCR) nhưng không khối \
                            `permissions:` nào trong tệp khai `packages: write`.

                            Phần ĐỌC image chiếm gần hết số dòng nên `packages: read` trông vừa đủ và \
                            lượt tra đi lọt hoàn toàn. Nó chỉ hỏng ở bước cuối, với `403 denied: \
                            installation not allowed to Write organization package` — một thông báo \
                            không nhắc gì tới khối `permissions:` (§10.44).""",
                            duongDan.getFileName(), GHI_TAG)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("⭐⭐ Job GỌI thân chung phải tự cấp `packages: write` — reusable không tự cấp được")
    void callerPhaiCapQuyenGhiChoThanChung() {
        List<Path> caller = workflowChua(THAN_CHUNG);
        assertThat(caller)
                .as("không workflow nào gọi `%s` — bài kiểm này sẽ xanh trên tập rỗng", THAN_CHUNG)
                .hasSize(2);

        for (Path duongDan : caller) {
            String khoi = khoiJobGoi(boChuThich(doc(duongDan)));
            assertThat(khoi)
                    .as(
                            """
                            Job gọi `%s` trong `%s` không khai `packages: write`.

                            Token của một reusable workflow bị chặn TRÊN bởi quyền của job gọi: khai \
                            `packages: write` ở thân chung là chưa đủ. Quên ở đây thì lượt chạy đi qua \
                            hết phần đọc rồi hỏng ở bước gắn tag cuối cùng — đúng hình dạng §10.44, chỉ \
                            khác chỗ đặt.""",
                            THAN_CHUNG, duongDan.getFileName())
                    .contains("packages: write");
        }
    }

    // -------------------------------------------------------------------------

    /** Cắt khối YAML của job có `uses: <thân chung>` — từ tên job tới job kế tiếp cùng mức. */
    private static String khoiJobGoi(String noiDung) {
        Matcher khop = Pattern.compile(
                        "^  [a-z][a-z0-9-]*:$(?:(?!^  [a-z]).)*?" + Pattern.quote(THAN_CHUNG) + "(?:(?!^  [a-z]).)*",
                        Pattern.MULTILINE | Pattern.DOTALL)
                .matcher(noiDung);
        return khop.find() ? khop.group() : fail("Không cắt được khối job gọi `%s`", THAN_CHUNG);
    }

    /** Có ít nhất một khối {@code permissions:} (bất kỳ mức nào) khai {@code packages: write}. */
    private static boolean quyenGhiGoi(String noiDungDaBoChuThich) {
        boolean trongKhoi = false;
        int thut = -1;
        for (String dong : noiDungDaBoChuThich.split("\n", -1)) {
            String cat = dong.stripTrailing();
            if (cat.stripLeading().equals("permissions:")) {
                trongKhoi = true;
                thut = cat.length() - cat.stripLeading().length();
                continue;
            }
            if (!trongKhoi || cat.isBlank()) {
                continue;
            }
            int thutHienTai = cat.length() - cat.stripLeading().length();
            if (thutHienTai <= thut) {
                trongKhoi = false; // hết khối
                continue;
            }
            Matcher m = MUC_QUYEN.matcher(cat);
            if (m.matches() && "packages".equals(m.group(1)) && "write".equals(m.group(2))) {
                return true;
            }
        }
        return false;
    }

    /** Mọi workflow có chứa một chuỗi — tìm động, để bài kiểm theo được khi tệp đổi tên. */
    private static List<Path> workflowChua(String chuoi) {
        Path thuMuc = timTuGocKho(".github/workflows");
        try (Stream<Path> duyet = Files.list(thuMuc)) {
            return duyet.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .filter(p -> boChuThich(doc(p)).contains(chuoi))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bỏ mọi dòng chú thích (YAML và shell đều dùng {@code #}) để đo CẤU TRÚC, không đo văn bản. */
    private static String boChuThich(String noiDung) {
        return noiDung.lines()
                .filter(dong -> !dong.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"));
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
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
