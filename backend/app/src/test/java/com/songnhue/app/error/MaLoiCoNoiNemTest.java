package com.songnhue.app.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mỗi mã lỗi phải có nơi NÉM — hoặc một lời khai vì sao chưa</b> — T28.55.
 *
 * <h2>Lỗ mà bài này lấp</h2>
 *
 * {@code error-map.test.ts} so <b>hai tập hợp mã</b> (BE ↔ FE, 98 mã hai phía) và đếm. Nó ⛔ không
 * bao giờ hỏi <i>"ai ném mày?"</i>. Một mã có thể sống mãi ở cả ba nơi — {@code ErrorCode.java},
 * {@code error-messages.properties}, {@code error-map.ts} — mà ⛔ không dòng mã nào ném nó, và bộ
 * canh ấy vẫn xanh trọn vẹn.
 *
 * <p>Đó là <b>quy tắc 27 ở chiều ngược</b>: nửa <i>đọc</i> hoàn chỉnh, nửa <i>ghi</i> ⛔ không tồn
 * tại. Hệ quả: câu tiếng Việt ⛔ không bao giờ hiện ra, và nhánh trong {@code error-map.ts} là mã
 * chết mà ⛔ không ai biết.
 *
 * <h2>⭐ Vì sao sổ nợ ghi 3 mà đo được 7</h2>
 *
 * {@code master-tracking.md} T28.55 ghi <i>"ba mã lỗi có đủ nửa hiển thị mà ⛔ không nửa nào phát"</i>
 * và liệt {@code HYD-1001}, {@code HYD-2003}, {@code HYD-2004}. Phép đếm ngày 08/09 ra <b>7</b> —
 * thêm {@code ADM-2009}, {@code CMS-2002}, {@code CMS-5001}, {@code HR-2001}.
 *
 * <p>Đó chính là lý do bài này tồn tại: <b>sổ nợ cũng là dữ liệu chưa kiểm</b> (§10.75), và một con
 * số đếm tay bỏ sót hơn một nửa. Từ nay phép đếm chạy mỗi lượt CI.
 *
 * <h2>Khuôn: {@code RbacMatrixTest}</h2>
 *
 * Cùng hình dạng đã dùng cho <b>quyền</b>: một danh sách miễn kiểm có lý do, cộng một bài canh
 * <b>chiều ngược</b> để danh sách ⛔ không phình lên bằng thao tác thêm-một-dòng-cho-hết-đỏ.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * Đo <b>sự có mặt của {@code ErrorCode.X}</b> trong mã sản phẩm, ⛔ không đo <i>"có thật sự ném
 * không"</i>: một mã dùng làm tham số cho một hàm dựng cũng tính là có. Phép đo mạnh hơn cần phân
 * tích luồng, và nó ⛔ không đáng — thứ bài này chặn là <b>quên hẳn</b>, và quên hẳn đúng là chuyện
 * đã xảy ra bảy lần.
 */
class MaLoiCoNoiNemTest {

    /** Tên hằng trong {@code ErrorCode.java}, VD {@code CMS_2023("CMS-2023", …)}. */
    private static final Pattern KHAI_BAO = Pattern.compile("^\\s*([A-Z]+_\\d+)\\(\"", Pattern.MULTILINE);

    private static final Pattern NOI_DUNG = Pattern.compile("ErrorCode\\.([A-Z]+_\\d+)");

    private static final String[] MODULE = {"core", "content", "operations", "hydro", "hr", "app"};

    /**
     * Mã <b>chưa có nơi ném</b>, và <b>vì sao</b> — đo 08/09/2026, đúng 7 mục.
     *
     * <p>⚠ Mỗi dòng ở đây là một mã được miễn kiểm. Danh sách phình lên là chuyện dễ xảy ra — thêm
     * một dòng cho hết đỏ là thao tác một dòng — nên {@link #dongMienTruVanConDung()} canh chiều
     * ngược: mã nào đã có nơi ném thì phải bị gỡ khỏi đây.
     */
    private static final Map<String, String> CHUA_CO_NOI_NEM = new LinkedHashMap<>();

    static {
        // ── Đóng theo THIẾT KẾ: mã đặt tên cho một TRẠNG THÁI, ⛔ không cho một lượt từ chối ──
        CHUA_CO_NOI_NEM.put(
                "HYD_2003",
                "⛔ CỐ Ý ⛔ không ném. 'Chưa cấu hình ngưỡng' là trạng thái HỢP LỆ (G9-a chưa về), và nó "
                        + "có nửa ĐỌC đầy đủ: `GET /hyd/alert-rules/chua-cau-hinh` + khối đầu `AlertRulesPage` "
                        + "+ `AlertEngineHttpTest`. Khai ở 9 chỗ độc lập trong mã.");
        CHUA_CO_NOI_NEM.put(
                "HYD_2004",
                "⛔ CỐ Ý ⛔ không ném — bảo đảm ở tầng CẤU TRÚC: `NguongAlertService` ⛔ không bao giờ đọc "
                        + "`hydro_latest`, nó chỉ đánh giá số đo vừa ghi trong CHÍNH giao dịch ấy. Tình huống "
                        + "'dùng giá trị cũ để đánh giá ngưỡng' là trạng thái ⛔ không tồn tại được, ⛔ không "
                        + "phải trạng thái được phát hiện rồi từ chối.");

        // ── Tính năng CHƯA DỰNG: mã đã đặt trước, chờ nghiệp vụ ──
        CHUA_CO_NOI_NEM.put(
                "CMS_2002",
                "⬜ CN-01.7 (liên kết hệ thống văn bản điều hành) chưa dựng — chặn bởi G5, và G5 quyết "
                        + "định cả LƯỢC ĐỒ (mã số riêng từng người hay chung một mã).");
        CHUA_CO_NOI_NEM.put(
                "CMS_5001",
                "⬜ Cùng CN-01.7 / G5 với CMS-2002. Nó là lỗi 502 cho lượt tự đăng nhập sang "
                        + "`songnhue.bhh40.net`, và hệ nguồn ấy đăng nhập bằng DUY NHẤT một 'mã số' chạy trên "
                        + "HTTP — nên đường nối chưa dựng được cho tới khi Công ty trả lời G5.");
        CHUA_CO_NOI_NEM.put(
                "HR_2001",
                "⬜ CN-04.9 (quản lý nghỉ phép) chưa dựng. ⚠ Lý do CŨ của dòng này — `module hr mới có 6 "
                        + "tệp khung` — ĐÃ HẾT ĐÚNG từ WS-51 (10/09/2026): module `hr` nay có hồ sơ CBNV, danh "
                        + "mục chức vụ và trường 🔒 chạy thật. Sửa câu chữ thay vì để nguyên, vì một dòng miễn "
                        + "trừ mang lý do đã chết là thứ lượt rà sau sẽ đọc và tin (§11.14 — đã trả giá 2 lần).");

        // ── Khoảng trống THẬT, cần quyết ──
        CHUA_CO_NOI_NEM.put(
                "ADM_2009",
                "⬜ 'Đang có một lượt sao lưu chạy'. `BackupService` CÓ trạng thái RUNNING nhưng ⛔ không "
                        + "từ chối lượt thứ hai bằng mã này. Hai lượt `pg_dump` song song trên một máy 2 vCPU "
                        + "là một sự cố tài nguyên im lặng. ⇒ Nối hoặc gỡ, ⛔ đừng để lửng.");
        CHUA_CO_NOI_NEM.put(
                "HYD_1001",
                "⬜⬜ 'Điểm đo chưa ánh xạ nguồn API bên thứ 3' — trạng thái này ⛔ KHÔNG BIỂU DIỄN ĐƯỢC "
                        + "trong lược đồ: `stations.api_source_id` và `api_code` đều NOT NULL, `api_code` còn "
                        + "có CHECK `^F[0-9]{5}$`. Và mã này ⛔ không có MỘT dòng javadoc nào ở bất kỳ đâu "
                        + "trong kho. ⇒ Cần quyết: gỡ mã, hay nới lược đồ cho điểm đo nhập tay ⛔ không gắn "
                        + "nguồn. ⛔ Đừng phát minh một nơi ném để cứu một mã lỗi.");
    }

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Mọi mã lỗi có nơi ném, hoặc có một lời khai vì sao chưa")
    void moiMaLoiCoNoiNemHoacCoLoiKhai() {
        TreeSet<String> khongNem = new TreeSet<>(khaiBao());
        khongNem.removeAll(duocNhacTrongMaSanPham());
        khongNem.removeAll(CHUA_CO_NOI_NEM.keySet());

        assertThat(khongNem)
                .as(
                        """
                        Những mã này khai trong `ErrorCode.java` (và gần như chắc chắn có cả câu tiếng Việt \
                        lẫn một dòng ở `error-map.ts`) nhưng ⛔ KHÔNG dòng mã sản phẩm nào nhắc tới.

                        Đó là quy tắc 27 ở chiều ngược: nửa ĐỌC hoàn chỉnh, nửa GHI ⛔ không tồn tại. Câu \
                        tiếng Việt ⛔ không bao giờ hiện ra, và nhánh ở `error-map.ts` là mã chết.

                        ⇒ Nối nó vào chỗ thật sự cần, HOẶC gỡ hẳn, HOẶC khai vào `CHUA_CO_NOI_NEM` kèm lý \
                        do đọc được. ⛔ Đừng để lửng — và ⛔ đừng phát minh một nơi ném chỉ để cứu một mã.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⭐ Chiều ngược: mã ĐÃ có nơi ném phải bị gỡ khỏi danh sách miễn trừ")
    void dongMienTruVanConDung() {
        // Khuôn `RbacMatrixTest#ngoaiLeQuyenPhaseSauVanConDung`. ⛔ Không có vế này thì danh sách mục
        // dần: một mã được nối về sau vẫn nằm mãi ở nhóm "chưa có nơi ném", và lời khai thành sai —
        // đúng thứ đã xảy ra với chính dòng nợ T28.55 (ghi 3, thật là 7).
        TreeSet<String> daCoNoiNem = new TreeSet<>(CHUA_CO_NOI_NEM.keySet());
        daCoNoiNem.retainAll(duocNhacTrongMaSanPham());

        assertThat(daCoNoiNem)
                .as("Những mã này ĐÃ có nơi ném — gỡ khỏi `CHUA_CO_NOI_NEM` để danh sách ⛔ không mục")
                .isEmpty();
    }

    @Test
    @DisplayName("Mọi dòng miễn trừ phải mang lý do, và phải là mã CÓ THẬT")
    void dongMienTruPhaiCoLyDoVaCoThat() {
        TreeSet<String> khai = new TreeSet<>(khaiBao());
        for (Map.Entry<String, String> e : CHUA_CO_NOI_NEM.entrySet()) {
            assertThat(e.getValue())
                    .as("%s: dòng miễn trừ phải nói RÕ vì sao, ⛔ không để trống", e.getKey())
                    .isNotBlank()
                    .hasSizeGreaterThan(40);
            assertThat(khai)
                    .as("%s ⛔ không còn trong `ErrorCode.java` — dòng miễn trừ đã mồ côi", e.getKey())
                    .contains(e.getKey());
        }
    }

    @Test
    @DisplayName("Phải quét ra ≥ 90 mã và ≥ 5 module — chặn xanh-trên-tập-rỗng")
    void quetRaTapKhacRong() {
        // conventions.md §1.5. Đổi cách khai enum (xuống dòng khác chẳng hạn) làm `KHAI_BAO` trả tập
        // rỗng, và bài đầu tiên xanh mà ⛔ không so gì.
        assertThat(khaiBao()).hasSizeGreaterThanOrEqualTo(90);
        assertThat(duocNhacTrongMaSanPham()).hasSizeGreaterThanOrEqualTo(80);
        long coMa = java.util.Arrays.stream(MODULE)
                .filter(m -> Files.isDirectory(timTuGocKho("backend").resolve(m).resolve("src/main/java")))
                .count();
        assertThat(coMa).as("Bỏ sót một module là bỏ sót mọi nơi ném trong đó").isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: một mã bịa KHÔNG được coi là đã có nơi ném (luật 9)")
    void tuKiemChung() {
        // Nếu `duocNhacTrongMaSanPham()` khớp quá rộng thì bài đầu tiên xanh với mọi thứ.
        assertThat(duocNhacTrongMaSanPham()).doesNotContain("XYZ_9999");
        // Và một mã ai cũng biết là có nơi ném thì phải nằm trong tập ấy — vế tiền đề.
        assertThat(duocNhacTrongMaSanPham()).contains("SYS_0004");
    }

    // =========================================================================

    private static TreeSet<String> khaiBao() {
        Path p = timTuGocKho("backend").resolve("core/src/main/java/com/songnhue/core/common/error/ErrorCode.java");
        TreeSet<String> ket = new TreeSet<>();
        Matcher m = KHAI_BAO.matcher(doc(p));
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    private static TreeSet<String> duocNhacTrongMaSanPham() {
        TreeSet<String> ket = new TreeSet<>();
        Path backend = timTuGocKho("backend");
        for (String mod : MODULE) {
            Path goc = backend.resolve(mod).resolve("src/main/java");
            if (!Files.isDirectory(goc)) {
                continue;
            }
            try {
                Files.walkFileTree(goc, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                        if (tep.toString().endsWith(".java")) {
                            Matcher m = NOI_DUNG.matcher(doc(tep));
                            while (m.find()) {
                                ket.add(m.group(1));
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path tep, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Không quét được " + goc, e);
            }
        }
        // ⚠ Đã cân nhắc TRỪ `ErrorCode.java` ra — nếu javadoc của nó tự nhắc `ErrorCode.X` thì mọi mã
        //   đều "có nơi ném" và bài này ⛔ không khẳng định gì. Đo 08/09: javadoc ở đó viết theo dạng
        //   gạch nối (`{@code CMS-2023}`), ⛔ không theo dạng `ErrorCode.CMS_2023`, nên phép quét vẫn
        //   phân biệt được — chứng minh bằng `tuKiemChung()`, nơi 7 mã KHÔNG lọt qua.
        //   ⇒ ⛔ Không thêm phép trừ, vì một phép trừ ⛔ không cần thiết cũng là một chỗ có thể sai.
        return ket;
    }

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
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
