package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Một lượt triển khai dùng MỘT kết nối SSH, không phải một kết nối mỗi lệnh.</b>
 *
 * <h2>Chuyện đã xảy ra</h2>
 *
 * CD Staging ngày 27/8 đỏ ở bước <i>"Ghi lại bản đang chạy"</i>:
 *
 * <pre>
 *   kex_exchange_identification: read: Connection reset by peer
 *   Connection reset by &lt;host&gt; port 22
 *   Process completed with exit code 255
 * </pre>
 *
 * Không phải lỗi logic. Đo trên VPS-2 cùng lúc: <b>một IP lạ giữ 32 kết nối SSH đồng thời</b> (67 tiến
 * trình sshd), trong khi {@code MaxStartups} mặc định là {@code 10:30:100} — vượt 10 kết nối chưa xác
 * thực thì sshd <b>thả ngẫu nhiên 30%</b>. Đo tỉ lệ thật: SSH 7/10 đạt (hỏng 30%), HTTPS cùng máy cùng
 * lúc 5/5. Con số 30 đúng bằng chữ số giữa của {@code 10:30:100}.
 *
 * <p>Thân workflow mở ~10 kết nối rời rạc, và bước đỏ ấy mở <b>ba</b> cái liên tiếp — nên nó là bước
 * có xác suất trúng cao nhất. Ghép kênh đưa cả lượt về một kết nối.
 *
 * <p>⛔ Bài này canh phần <b>giảm mặt tiếp xúc</b>. Bản vá GỐC nằm ở máy chủ (fail2ban +
 * {@code PerSourceMaxStartups}) và <b>không</b> kiểm được từ đây — {@code deploy-guideline.md} §2.2-b.
 * Đừng đọc bài xanh này rồi tưởng chuyện đã xong.
 */
class DeploySshMultiplexTest {

    private static final Pattern GOI_SSH = Pattern.compile("(?<![\\w-])ssh\\s+[\"$]");

    @Test
    @DisplayName("⭐⭐ `deploy.yml` khai ghép kênh SSH — ControlMaster + ControlPath + ControlPersist")
    void phaiKhaiGhepKenh() {
        String w = doc(timTuGocKho(".github/workflows/deploy.yml"));

        for (String khoa : new String[] {"ControlMaster auto", "ControlPath", "ControlPersist"}) {
            assertThat(w)
                    .as(
                            """
                            `deploy.yml` không khai `%s`.

                            Thiếu ghép kênh thì mỗi lệnh mở một kết nối SSH mới — ~10 lượt bắt tay cho \
                            một lần deploy, và mỗi lượt là một lần rút thăm với `MaxStartups` của máy \
                            chủ. Đã đỏ đúng vì vậy ngày 27/8.""",
                            khoa)
                    .contains(khoa);
        }
    }

    @Test
    @DisplayName("⭐⭐ Bước 'Ghi lại bản đang chạy' chỉ được gọi `ssh` MỘT lần")
    void ghiLaiBanDangChayChiMotLuotSsh() {
        String than = thanBuoc("Ghi lại bản đang chạy");
        long soLan = GOI_SSH.matcher(boChuThich(than)).results().count();

        assertThat(soLan)
                .as(
                        """
                        Bước 'Ghi lại bản đang chạy' gọi `ssh` %d lần.

                        Bản cũ hỏi ba container bằng ba lượt `ssh` liên tiếp, và đó chính là bước đã \
                        đỏ. `docker inspect` nhận NHIỀU đối số và in mỗi container một dòng đúng thứ \
                        tự — đo trên VPS-2 27/8, kèm cả trường hợp container không tồn tại thì nó BỎ \
                        HẲN dòng chứ không in dòng trống.""",
                        soLan)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Kết nối đầu tiên phải có thử lại — nó là kết nối duy nhất đi qua cửa hẹp")
    void ketNoiDauPhaiCoThuLai() {
        String than = thanBuoc("Mở đường SSH");

        assertThat(than)
                .as("Ghép kênh không cứu được lượt bắt tay ĐẦU TIÊN; thiếu thử lại là vẫn rút thăm một lần")
                .contains("for lan in");
        assertThat(than)
                .as("Thử lại mà không giãn cách chỉ là gõ cửa nhanh hơn")
                .contains("sleep");
        assertThat(than)
                .as("Hết lượt thử phải ĐỎ kèm chẩn đoán, không im lặng đi tiếp")
                .contains("::error::");
    }

    @Test
    @DisplayName("⛔ Và bài này KHÔNG được nhận là đã vá gốc — phải trỏ sang chỗ vá thật")
    void phaiTroSangBanVaGoc() {
        // CLAUDE.md: một bộ canh xanh dễ bị đọc thành "chuyện đã xong". Ghép kênh chỉ giảm số lần rút
        // thăm; máy chủ vẫn đang bị quét. Bắt workflow phải mang con trỏ tới nơi vá thật.
        assertThat(doc(timTuGocKho(".github/workflows/deploy.yml")))
                .as("`deploy.yml` phải trỏ tới mục hướng dẫn siết SSH của máy chủ")
                .contains("§2.2-b");
        assertThat(doc(timTuGocKho("docs/deploy-guideline.md")))
                .as("`deploy-guideline.md` phải CÓ mục ấy — con trỏ trỏ vào chỗ trống là con trỏ hỏng")
                .contains("2.2-b");
    }

    @Test
    @DisplayName("Đọc được thân hai bước — chặn xanh-trên-tập-rỗng")
    void docDuocThanBuoc() {
        assertThat(thanBuoc("Mở đường SSH")).hasSizeGreaterThan(300);
        assertThat(thanBuoc("Ghi lại bản đang chạy")).hasSizeGreaterThan(200);
    }

    // -------------------------------------------------------------------------

    /** Thân `run:` của một bước, cắt từ dòng `- name: <tên>` tới dòng `- name:` kế tiếp. */
    private static String thanBuoc(String ten) {
        String w = doc(timTuGocKho(".github/workflows/deploy.yml"));
        int dau = w.indexOf("- name: " + ten);
        if (dau < 0) {
            return fail("Không tìm thấy bước `%s` trong deploy.yml — đổi tên bước thì SỬA bài kiểm, đừng xoá", ten);
        }
        Matcher ke = Pattern.compile("^      - name: ", Pattern.MULTILINE).matcher(w);
        int cuoi = w.length();
        while (ke.find()) {
            if (ke.start() > dau) {
                cuoi = ke.start();
                break;
            }
        }
        return w.substring(dau, cuoi);
    }

    /** Bỏ dòng chú thích YAML — bài kiểm nói về LỆNH chạy, không nói về đoạn văn giải thích nó. */
    private static String boChuThich(String noiDung) {
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
