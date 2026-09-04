package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>{@code verify-no-keys.sh} không được thoát 0 khi nó KHÔNG kiểm được gì — T11.41.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Script là phép kiểm bảo mật <b>duy nhất</b> canh bản dump trước khi nó rời máy chủ (cam kết
 * {@code architecture-review.md} §6.5: khoá AES/JWT nằm ngoài CSDL). Nó có một nhánh:
 *
 * <pre>
 *   if ! command -v pg_restore; then
 *       echo "  ⚠ Không có pg_restore — BỎ QUA việc kiểm khoá trong bản dump."
 *       exit 0            ← ⛔
 *   fi
 * </pre>
 *
 * VPS staging <b>không cài postgresql-client</b>. Nên từ 26/8/2026, ở <b>mọi</b> lượt triển khai,
 * nhánh ấy chạy: phép kiểm in cảnh báo rồi thoát 0, {@code pre-deploy-dump.sh} coi là đạt và đi
 * tiếp, và bản dump ra kho ngoài nhà cung cấp mà chưa ai soi một byte.
 *
 * <p>Đây đúng hình dạng {@code sys.exit(0)} của bộ đọc tracking (T11.49) và của
 * {@code FrontendSameOriginTest}: <b>một cơ chế canh gác không chạy được phải nói ra bằng mã thoát
 * khác 0</b>. "Không kiểm được" và "kiểm rồi, sạch" là hai kết luận khác nhau và phải trông khác
 * nhau (luật 9).
 *
 * <h2>Vì sao chạy script thật thay vì đọc chữ trong nó</h2>
 *
 * Một bài khẳng định <i>"tệp không còn chuỗi {@code exit 0}"</i> canh văn bản chứ không canh hành vi
 * (luật 2), và sẽ xanh trọn vẹn với một nhánh viết là {@code exit $((0))}. Ở đây script được
 * <b>gọi thật</b> trong bốn môi trường dựng sẵn, và thứ được khẳng định là <b>mã thoát</b>.
 *
 * <h2>⚠ Giới hạn của chính bài kiểm này (luật 28)</h2>
 *
 * Nó chứng minh script <b>từ chối kết luận</b> khi không có công cụ, và <b>biết hỏi container</b>
 * khi host thiếu. Nó KHÔNG chứng minh máy chủ thật có container postgres đúng nhãn — nửa ấy chỉ đo
 * được bằng một lượt deploy thật, và phép đo là dòng {@code · Soi bằng pg_restore của: container …}
 * trong log lượt triển khai kế tiếp.
 */
class VerifyNoKeysGateTest {

    private static final String SCRIPT = "deploy/backup/verify-no-keys.sh";

    /** Một dòng đủ để {@code PATTERN_PEM} bắt — dùng làm nội dung bản dump "bẩn". */
    private static final String KHOA_GIA = "-----BEGIN RSA PRIVATE KEY-----";

    // ── Nhóm 1: bốn môi trường, bốn kết luận ───────────────────────────────────────────────────

    @Test
    @DisplayName("⭐⭐ A · KHÔNG có pg_restore và KHÔNG có docker → phải THOÁT KHÁC 0")
    void khongCongCuThiPhaiDo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, MoiTruong.TRONG_RONG, "-- bản dump sạch");

        assertThat(kq.maThoat())
                .as(
                        """
                        ⛔ Đây là cả nội dung của T11.41. Thoát 0 ở đây nghĩa là bản dump đi ra kho \
                        ngoài mà chưa ai soi nó, và lượt deploy vẫn in ✓. Đầu ra:
                        %s""",
                        kq.dauRa())
                .isNotZero();
        assertThat(kq.dauRa())
                .as("thông báo phải nói rõ là KHÔNG KIỂM ĐƯỢC, không phải một lời trấn an")
                .contains("KHÔNG CHẠY ĐƯỢC");
    }

    @Test
    @DisplayName("B · có docker nhưng KHÔNG có container postgres → phải THOÁT KHÁC 0")
    void dockerNhungKhongContainerThiPhaiDo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, MoiTruong.DOCKER_KHONG_CONTAINER, "-- bản dump sạch");

        assertThat(kq.maThoat())
                .as("có `docker` mà không có container thì vẫn là không kiểm được:\n%s", kq.dauRa())
                .isNotZero();
    }

    @Test
    @DisplayName("C · có pg_restore trên host, bản dump sạch → thoát 0, và nói rõ đã soi bằng gì")
    void hostCoPgRestoreThiSoiDuoc(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, MoiTruong.PG_TREN_HOST, "-- bản dump sạch");

        assertThat(kq.maThoat()).as("bản dump sạch phải đạt:\n%s", kq.dauRa()).isZero();
        assertThat(kq.dauRa()).contains("Soi bằng pg_restore của: host");
        assertThat(kq.dauRa()).contains("✓ Bản sao lưu không chứa khoá");
    }

    @Test
    @DisplayName("⭐ D · host KHÔNG có pg_restore nhưng container postgres có → soi qua container, thoát 0")
    void khongCoTrenHostThiHoiContainer(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, MoiTruong.PG_TRONG_CONTAINER, "-- bản dump sạch");

        assertThat(kq.maThoat())
                .as(
                        """
                        Đây là đường mà máy chủ THẬT đi: VPS không cài postgresql-client, còn \
                        container postgres thì luôn có pg_restore đúng phiên bản, và thư mục sao \
                        lưu được gắn vào nó ở cùng đường dẫn. Đầu ra:
                        %s""",
                        kq.dauRa())
                .isZero();
        assertThat(kq.dauRa()).contains("Soi bằng pg_restore của: container");
    }

    // ── Nhóm 2: bộ canh vẫn còn bắt được vi phạm ───────────────────────────────────────────────

    @Test
    @DisplayName("⭐ E · bản dump CÓ khoá PEM → thoát khác 0, ở CẢ hai nguồn pg_restore")
    void banDumpCoKhoaThiPhaiDo(@TempDir Path thuMuc) throws Exception {
        Path a = Files.createDirectories(thuMuc.resolve("a"));
        Path b = Files.createDirectories(thuMuc.resolve("b"));

        KetQua qHost = chay(a, MoiTruong.PG_TREN_HOST, KHOA_GIA);
        KetQua qCt = chay(b, MoiTruong.PG_TRONG_CONTAINER, KHOA_GIA);

        assertThat(qHost.maThoat())
                .as("bản vá T11.41 không được làm mất khả năng bắt vi phạm:\n%s", qHost.dauRa())
                .isNotZero();
        assertThat(qHost.dauRa()).contains("PHÁT HIỆN khoá riêng dạng PEM");

        assertThat(qCt.maThoat())
                .as(
                        """
                        ⛔ Đường qua container là đường máy chủ thật đi. Nó soi được mà không bắt \
                        được thì bản vá chỉ đổi một xanh giả này lấy một xanh giả khác. Đầu ra:
                        %s""",
                        qCt.dauRa())
                .isNotZero();
        assertThat(qCt.dauRa()).contains("PHÁT HIỆN khoá riêng dạng PEM");
    }

    /**
     * ⭐ Tự kiểm: chứng minh bộ khung thử phân biệt được hai trạng thái (luật 10 · luật 29).
     *
     * <p>Bài A đỏ vì <b>thiếu công cụ</b>. Nhưng một bộ khung dựng sai — PATH thiếu {@code grep},
     * script không tìm thấy, quyền thực thi sai — cũng cho ra "thoát khác 0", và khi đó bài A xanh
     * <i>mà không đo gì</i>. Khẳng định ở đây là về <b>sự khác nhau giữa hai môi trường</b>: cùng
     * một script, cùng một bản dump, chỉ khác việc có {@code pg_restore} hay không, thì mã thoát
     * phải khác nhau.
     */
    @Test
    @DisplayName("⭐ Tự kiểm: cùng script + cùng bản dump, chỉ khác công cụ ⇒ mã thoát phải khác nhau")
    void tuKiemBoKhungPhanBietDuocHaiTrangThai(@TempDir Path thuMuc) throws Exception {
        Path a = Files.createDirectories(thuMuc.resolve("khong"));
        Path b = Files.createDirectories(thuMuc.resolve("co"));

        int khongCongCu = chay(a, MoiTruong.TRONG_RONG, "-- bản dump sạch").maThoat();
        KetQua coCongCu = chay(b, MoiTruong.PG_TREN_HOST, "-- bản dump sạch");

        assertThat(coCongCu.maThoat())
                .as(
                        """
                        ⛔ Nếu môi trường ĐỦ công cụ cũng đỏ thì bộ khung thử đang hỏng, và bài A \
                        xanh vì lý do sai. Đầu ra:
                        %s""",
                        coCongCu.dauRa())
                .isZero();
        assertThat(khongCongCu)
                .as("hai môi trường phải cho hai kết luận khác nhau, nếu không bài A không đo gì")
                .isNotEqualTo(coCongCu.maThoat());
    }

    // ── Bộ khung ───────────────────────────────────────────────────────────────────────────────

    private enum MoiTruong {
        /** Không {@code pg_restore}, không {@code docker}. */
        TRONG_RONG,
        /** {@code pg_restore} nằm trên host. */
        PG_TREN_HOST,
        /** Không có trên host; có {@code docker} và container postgres đang chạy. */
        PG_TRONG_CONTAINER,
        /** Có {@code docker} nhưng {@code docker ps} không trả container nào. */
        DOCKER_KHONG_CONTAINER
    }

    private record KetQua(int maThoat, String dauRa) {}

    /**
     * Chạy script thật với một PATH <b>dựng từ đầu</b>.
     *
     * <p>⚠ Không thể chỉ chèn thêm thư mục giả vào đầu {@code $PATH} thật: runner
     * {@code ubuntu-24.04} có sẵn cả {@code docker} lẫn {@code psql}, nên môi trường "trống rỗng"
     * sẽ không bao giờ trống. PATH ở đây chỉ chứa đúng những gì script cần ({@code grep},
     * {@code dirname}, {@code head}, {@code cat}) cộng với các lệnh giả của từng kịch bản.
     */
    private static KetQua chay(Path thuMuc, MoiTruong moiTruong, String noiDungDump) throws Exception {
        Path bin = Files.createDirectories(thuMuc.resolve("bin"));
        for (String lenh : List.of("grep", "dirname", "head", "cat")) {
            lienKetLenhThat(bin, lenh);
        }

        // Bản dump phải nằm dưới một thư mục trông như thư mục sao lưu; nội dung không cần là định
        // dạng pg_dump thật vì `pg_restore` ở đây là bản giả — thứ đang đo là NHÁNH QUYẾT ĐỊNH.
        Path dump = thuMuc.resolve("predeploy-thu.dump");
        Files.writeString(dump, noiDungDump, StandardCharsets.UTF_8);

        switch (moiTruong) {
            case TRONG_RONG -> {
                /* không dựng lệnh nào */
            }
            case PG_TREN_HOST -> dungPgRestoreGia(bin.resolve("pg_restore"), dump);
            case PG_TRONG_CONTAINER -> {
                Path pgThat = thuMuc.resolve("pg_restore_that");
                dungPgRestoreGia(pgThat, dump);
                dungDockerGia(bin.resolve("docker"), pgThat, "cafebabe1234");
            }
            case DOCKER_KHONG_CONTAINER -> dungDockerGia(bin.resolve("docker"), null, "");
            default -> fail("môi trường chưa được dựng: " + moiTruong);
        }

        // ⚠ `/bin/bash` TUYỆT ĐỐI: PATH dựng tay ở trên không có bash, và nếu bash cũng phải tra
        //   qua PATH thì mọi bài đỏ vì lý do khác hẳn thứ nó đo. Cũng không dựa vào shebang —
        //   shell mặc định của máy dev là zsh (luật 20).
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", timTuGocKho(SCRIPT).toString(), dump.toString());
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", bin.toString());

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
        }
        return new KetQua(p.exitValue(), dauRa);
    }

    /** {@code pg_restore} giả: {@code --list} luôn đạt, {@code --file=-} đổ nội dung bản dump ra. */
    private static void dungPgRestoreGia(Path dich, Path dump) throws IOException {
        viet(
                dich,
                """
                #!/bin/bash
                case "$1" in
                  --list)   exit 0 ;;
                  --file=-) cat '%s' ;;
                  *)        exit 2 ;;
                esac
                """
                        .formatted(dump));
    }

    /**
     * {@code docker} giả. Chỉ trả lời ba câu script hỏi:
     *
     * <ul>
     *   <li>{@code docker ps -q --filter …} → id container (rỗng = không có container nào)
     *   <li>{@code docker exec <ct> test -f <dump>} → đạt
     *   <li>{@code docker exec <ct> pg_restore …} → uỷ quyền cho {@code pg_restore} giả
     * </ul>
     */
    private static void dungDockerGia(Path dich, Path pgGia, String idContainer) throws IOException {
        viet(
                dich,
                """
                #!/bin/bash
                case "$1" in
                  ps)
                    printf '%%s' '%s'
                    [ -n '%s' ] && printf '\\n'
                    exit 0 ;;
                  exec)
                    shift; shift            # bỏ `exec` và tên container
                    case "$1" in
                      test)       exit 0 ;;
                      pg_restore) shift; exec '%s' "$@" ;;
                    esac
                    exit 2 ;;
                esac
                exit 2
                """
                        .formatted(idContainer, idContainer, pgGia == null ? "/khong-ton-tai" : pgGia));
    }

    private static void viet(Path dich, String noiDung) throws IOException {
        Files.writeString(dich, noiDung, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(dich, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    /**
     * Nối một lệnh hệ thống thật vào thư mục {@code bin} giả.
     *
     * <p>⛔ Nếu không tìm thấy thì <b>đỏ ngay</b>, đừng bỏ qua: thiếu {@code grep} làm script hỏng
     * vì lý do không liên quan, và bài A sẽ xanh mà chưa đo gì (chính bẫy nó sinh ra để chặn).
     */
    private static void lienKetLenhThat(Path bin, String ten) throws IOException {
        for (String thuMuc : List.of("/usr/bin", "/bin", "/usr/local/bin")) {
            Path ungVien = Paths.get(thuMuc, ten);
            if (Files.isExecutable(ungVien)) {
                Files.createSymbolicLink(bin.resolve(ten), ungVien);
                return;
            }
        }
        fail("Không thấy lệnh hệ thống `%s` — bộ khung thử không dựng được PATH tối thiểu".formatted(ten));
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
