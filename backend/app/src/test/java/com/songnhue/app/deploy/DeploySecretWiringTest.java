package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Bốn nơi phải cùng biết về một secret, và chúng phải KHỚP NHAU.</b>
 *
 * <h2>Chuyện đã xảy ra</h2>
 *
 * Ngày 29/8, `SSH_KNOWN_HOSTS` được thêm vào {@code deploy.yml} (khai ở {@code workflow_call}) và vào
 * {@code kiem-secret-may-chu.sh} (cổng kiểm), rồi secret được đặt trên GitHub. Nhưng <b>hai workflow
 * GỌI nó thì không truyền vào</b>. Lượt CD Staging kế tiếp đỏ với:
 *
 * <pre>
 *   SSH_KNOWN_HOSTS:
 *   ##[error]Cấu hình máy chủ staging DỞ DANG — thiếu: SSH_KNOWN_HOSTS
 * </pre>
 *
 * Cổng kiểm hành xử đúng — chặn sớm, nói rõ thiếu gì. Nhưng nó chặn một lỗi <i>lẽ ra không nên tồn
 * tại</i>: đúng hình dạng luật 27 (nửa cặp đọc–ghi chạy hoàn hảo vẫn cho ra số không).
 *
 * <p>⛔ Và bài kiểm ĐÃ CÓ lúc ấy không bắt được, vì nó chỉ soi {@code deploy.yml} + script — <b>hẹp
 * hơn nơi nó phải chặn</b> (luật 28). Một chuỗi ký tự có mặt ở hai tệp không chứng minh được đường dây
 * đã nối. Bài này thay bằng phép đối chiếu <b>tập hợp</b>, hai chiều, trên cả bốn nơi — nên secret thứ
 * sáu không thể nối nửa vời được nữa.
 */
class DeploySecretWiringTest {

    private static final Path THAN = timTuGocKho(".github/workflows/deploy.yml");
    private static final Path GOI_STAGING = timTuGocKho(".github/workflows/deploy-staging.yml");
    private static final Path GOI_PROD = timTuGocKho(".github/workflows/deploy-prod.yml");
    private static final Path CONG = timTuGocKho(".github/scripts/kiem-secret-may-chu.sh");

    @Test
    @DisplayName("⭐⭐ Mọi secret `deploy.yml` khai đều phải được CẢ HAI workflow gọi truyền vào")
    void moiSecretPhaiDuocTruyenVao() {
        Set<String> khai = boSecretYaml(THAN);

        for (Path goi : new Path[] {GOI_STAGING, GOI_PROD}) {
            Set<String> truyen = boSecretYaml(goi);

            assertThat(truyen)
                    .as(
                            """
                            `%s` không truyền đủ secret cho `deploy.yml`.

                            Khai ở thân: %s
                            Truyền ở đây: %s

                            Thiếu một cái thì nó tới cổng kiểm dưới dạng CHUỖI RỖNG — giống hệt \
                            secret chưa đặt trên GitHub, nên người đọc log sẽ đi đặt lại một secret \
                            vốn đã có. Đúng chuyện đã xảy ra 29/8 với `SSH_KNOWN_HOSTS`.""",
                            goi.getFileName(), khai, truyen)
                    .containsExactlyInAnyOrderElementsOf(khai);
        }
    }

    @Test
    @DisplayName("⭐⭐ Cổng secret phải kiểm ĐÚNG BỘ ẤY — không thừa, không thiếu")
    void congPhaiKiemDungBo() {
        Set<String> khai = boSecretYaml(THAN);
        Set<String> kiem = boSecretScript();

        assertThat(kiem)
                .as(
                        """
                        Cổng `kiem-secret-may-chu.sh` kiểm một bộ khác với bộ `deploy.yml` khai.

                        Khai: %s
                        Kiểm: %s

                        Kiểm THIẾU → secret rỗng đi lọt, hỏng muộn ở `ssh` với câu không nhắc gì tới \
                        secret. Kiểm THỪA → cổng đòi một secret không ai truyền, chặn vĩnh viễn.""",
                        khai, kiem)
                .containsExactlyInAnyOrderElementsOf(khai);
    }

    @Test
    @DisplayName("⛔ Bộ đọc phải ĐỌC ĐƯỢC THẬT — bốn tập rỗng cũng khớp nhau hoàn hảo")
    void boDocKhongDuocRong() {
        // Luật 7: phép so hai tập rỗng luôn xanh. Không có bài này thì một regex hỏng sẽ biến cả hai
        // khẳng định trên thành lời khen suông.
        assertThat(boSecretYaml(THAN))
                .as("không đọc được secret nào từ deploy.yml")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(boSecretYaml(GOI_STAGING)).hasSizeGreaterThanOrEqualTo(5);
        assertThat(boSecretYaml(GOI_PROD)).hasSizeGreaterThanOrEqualTo(5);
        assertThat(boSecretScript()).hasSizeGreaterThanOrEqualTo(5);

        assertThat(boSecretYaml(THAN))
                .as("Tên quen thuộc phải có mặt — nếu không thì bộ đọc đang bắt nhầm thứ khác")
                .contains("HOST", "USER", "SSH_KEY", "BASE_URL", "SSH_KNOWN_HOSTS");
    }

    // -------------------------------------------------------------------------

    /** Tên các secret trong khối `secrets:` đầu tiên của một workflow. */
    private static Set<String> boSecretYaml(Path tep) {
        String[] dong = doc(tep).split("\n", -1);
        Set<String> ket = new LinkedHashSet<>();
        boolean trongKhoi = false;
        Pattern muc = Pattern.compile("^ {6}([A-Z][A-Z0-9_]*):");
        for (String d : dong) {
            if (!trongKhoi) {
                if (d.matches("^ {4}secrets:\\s*$")) {
                    trongKhoi = true;
                }
                continue;
            }
            if (d.isBlank() || d.stripLeading().startsWith("#")) {
                continue;
            }
            Matcher m = muc.matcher(d);
            if (m.find()) {
                ket.add(m.group(1));
            } else {
                break; // hết khối — thụt lề đã đổi
            }
        }
        return ket;
    }

    /** Tên các secret trong vòng lặp `for ten in … ; do` của cổng kiểm. */
    private static Set<String> boSecretScript() {
        Matcher m = Pattern.compile("for ten in ([A-Z0-9_ ]+); do").matcher(doc(CONG));
        if (!m.find()) {
            return fail(
                    "Không tìm thấy vòng `for ten in …` trong kiem-secret-may-chu.sh — đổi cách viết thì SỬA bài kiểm");
        }
        Set<String> ket = new LinkedHashSet<>();
        for (String t : m.group(1).trim().split("\\s+")) {
            ket.add(t);
        }
        return ket;
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
