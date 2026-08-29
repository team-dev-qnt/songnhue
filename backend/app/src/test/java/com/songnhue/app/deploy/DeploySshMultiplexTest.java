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

    /** Lượt DÒ khoá máy chủ — thứ đã tự làm runner bị fail2ban cấm (§10.68-C). */
    private static final Pattern DO_KHOA = Pattern.compile("(?<![\\w-])ssh-keyscan\\b");

    /** Một lượt `ssh` đẩy stderr vào /dev/null — tức vứt đi lý do đỏ. */
    private static final Pattern SSH_NUOT_LOI = Pattern.compile("(?<![\\w-])ssh\\s+[^\\n]*2>\\s*/dev/null");

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
    @DisplayName("⭐⭐ Lượt `ssh` thử lại KHÔNG được nuốt stderr — đó là thứ duy nhất nói vì sao đỏ")
    void khongDuocNuotLyDoSsh() {
        String than = boChuThich(thanBuoc("Mở đường SSH"));

        assertThat(SSH_NUOT_LOI.matcher(than).find())
                .as(
                        """
                        Bước 'Mở đường SSH' đẩy stderr của `ssh` vào /dev/null.

                        Lượt CD đỏ 29/8 in ra SÁU dòng "không mở được kênh" và không một chữ nào nói \
                        vì sao — trong khi client SSH đã viết lý do ra stderr. Ba nguyên nhân cần ba \
                        cách xử lý ngược nhau: bị CHẶN (Connection refused) · bị THẢ vì quá tải (im \
                        lặng, hết giờ) · SAI KHOÁ (Permission denied — thử lại vô nghĩa). Đo được: \
                        bản nuốt stderr cho ra đầu ra TRÙNG TỪNG BYTE ở cả ba, nên nó không phân \
                        biệt được gì (luật 9).""")
                .isFalse();

        assertThat(than).as("Phải GIỮ lý do lại (`2>&1`) thì mới in ra được").contains("2>&1");
    }

    @Test
    @DisplayName("⭐ Sai khoá thì phải ĐỎ NGAY, đừng thử lại sáu lượt rồi báo nhầm là lỗi mạng")
    void saiKhoaPhaiDoNgay() {
        String than = boChuThich(thanBuoc("Mở đường SSH"));

        assertThat(than)
                .as("Không có nhánh nhận diện lỗi xác thực thì sai khoá vẫn tiêu hai phút của mọi người")
                .contains("Permission denied");
        assertThat(than)
                .as("Nhận ra rồi thì phải thoát, không rơi tiếp vào `sleep`")
                .containsPattern("Permission denied[\\s\\S]{0,400}?exit 1");
    }

    @Test
    @DisplayName("⛔ Và bộ dò ấy phải BẮT ĐƯỢC bản cũ — nếu không thì nó chỉ đang canh tập rỗng")
    void boDoNuotLoiBatDuocViPham() {
        // Nguyên văn dòng đã nằm trong `deploy.yml` tới 29/8. Bộ dò phải đỏ trước nó; một mẫu regex
        // chỉ xanh trên cây hiện tại thì không chứng minh được điều gì (luật 1, luật 29).
        String banCu = "            if ssh -o BatchMode=yes \"$HOST\" true 2>/dev/null; then";

        assertThat(SSH_NUOT_LOI.matcher(banCu).find())
                .as("Bộ dò KHÔNG bắt được chính dòng đã gây ra lượt đỏ 29/8 — mẫu regex sai")
                .isTrue();
    }

    @Test
    @DisplayName("⭐⭐ KHÔNG được dò khoá máy chủ — lượt dò ấy làm fail2ban cấm chính runner")
    void khongDuocDoKhoaMayChu() {
        String than = boChuThich(thanBuoc("Mở đường SSH"));

        assertThat(DO_KHOA.matcher(than).find())
                .as(
                        """
                        Bước 'Mở đường SSH' còn gọi `ssh-keyscan`.

                        Nó mở ~5 kết nối song song để dò từng kiểu khoá, tất cả đóng lại TRƯỚC khi                         xác thực. fail2ban của VPS-2 đặt `mode = aggressive` + `maxretry = 3`                         + `findtime = 600` (đo trên máy 29/8), mà `aggressive` tính cả dòng                         `Connection closed … [preauth]` — nên lượt deploy TỰ CẤM CHÍNH NÓ ngay ở                         lệnh đầu tiên. Đo được: 5 kết nối từ `52.230.251.196` (dải Azure) lúc                         19:27:03–06, đúng giây bước này khởi động, rồi IP ấy vào `Banned IP list`.

                        Và nó còn TIN BẤT KỲ khoá nào máy chủ đưa ra — dò lại mỗi lượt nghĩa là                         không lượt nào thật sự xác minh mình đang nói chuyện với đúng máy.""")
                .isFalse();

        assertThat(than)
                .as("Bỏ dò rồi thì phải GHIM — khoá đọc từ secret `SSH_KNOWN_HOSTS`")
                .contains("SSH_KNOWN_HOSTS")
                .contains("known_hosts");
        assertThat(doc(timTuGocKho(".github/workflows/deploy.yml")))
                .as("Khoá lạ phải bị TỪ CHỐI, và nói thẳng ra chứ đừng dựa vào mặc định")
                .contains("StrictHostKeyChecking yes");
    }

    @Test
    @DisplayName("⛔ Bộ dò `ssh-keyscan` phải BẮT ĐƯỢC dòng cũ — không thì nó chỉ canh tập rỗng")
    void boDoDoKhoaBatDuocViPham() {
        // Nguyên văn dòng đã nằm trong `deploy.yml` tới 29/8.
        String banCu = "          ssh-keyscan -H \"$HOST\" >> ~/.ssh/known_hosts 2>/dev/null";

        assertThat(DO_KHOA.matcher(banCu).find())
                .as("Bộ dò KHÔNG bắt được chính dòng đã làm runner bị cấm — mẫu regex sai")
                .isTrue();
    }

    @Test
    @DisplayName("⭐ Cổng secret phải hỏi cả `SSH_KNOWN_HOSTS` — thiếu nửa cặp là hỏng muộn")
    void congSecretPhaiHoiKhoaGhim() {
        // Luật 27: một nửa cặp chạy hoàn hảo vẫn cho ra số không. Workflow đọc secret ấy, nên cổng
        // secret phải kiểm nó — nếu không, thiếu khoá sẽ hỏng ở `ssh` với câu không nhắc gì tới secret.
        assertThat(doc(timTuGocKho(".github/scripts/kiem-secret-may-chu.sh")))
                .as("`kiem-secret-may-chu.sh` không kiểm `SSH_KNOWN_HOSTS`")
                .contains("SSH_KNOWN_HOSTS");
        assertThat(doc(timTuGocKho(".github/workflows/deploy.yml")))
                .as("`deploy.yml` phải KHAI secret ấy ở `workflow_call`, không thì caller không truyền được")
                .containsPattern("secrets:[\\s\\S]{0,600}?SSH_KNOWN_HOSTS");
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
