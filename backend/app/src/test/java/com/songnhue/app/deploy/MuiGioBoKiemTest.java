package com.songnhue.app.deploy;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Hai đồng hồ trong một khẳng định — T83.1.</b>
 *
 * <h2>Chuyện đã xảy ra — 21–22/9/2026</h2>
 *
 * Ba PR Dependabot <b>chỉ đụng {@code frontend/}</b> (#198 · #199 · #200) đỏ ở job <b>BACKEND</b>,
 * trong khi ba PR backend cùng đợt (#195 → #197) xanh — <b>cùng một commit nền</b>
 * {@code 00daec1e}. Khác biệt duy nhất đo được là <b>giờ chạy</b>: nhóm xanh 19:04 UTC, nhóm đỏ
 * 20:26 UTC.
 *
 * <p>{@code PollerChangCuoiHttpTest} dựng mốc đồ gá bằng {@code LocalDate.now()} <b>TRẦN</b> (ngày
 * theo múi giờ JVM = UTC trên runner) rồi khẳng định qua một câu SQL lọc
 * {@code measured_at > now() - interval '1 day'} — cửa sổ do <b>CSDL</b> tính. Bản ghi nằm ở
 * {@code ngay} 03:30 <b>giờ VN</b> = {@code ngay-1} 20:30 UTC, nên nó rời khỏi cửa sổ đúng lúc
 * đồng hồ treo tường qua <b>20:30 UTC</b>. ⇒ ⛔ phải một bài chập chờn mà là một <b>khung giờ chết
 * lặp mỗi ngày</b>: 20:30 UTC → nửa đêm (VN 03:30–07:00).
 *
 * <h2>Bất biến bài này canh</h2>
 *
 * Tệp kiểm nào <b>tự viết một cửa sổ theo đồng hồ CSDL</b> ({@code now() - interval …}) thì ⛔ được
 * dựng mốc đồ gá bằng đồng hồ <b>môi trường</b>. Hai đồng hồ, hai múi giờ, và hậu quả ⛔ hiện ra ở
 * lượt chạy nào cũng được — nó đợi tới đúng giờ trong ngày.
 *
 * <p>⛔ Bài này <b>⛔ cấm</b> {@code LocalDate.now()} ở mọi tệp kiểm: phần lớn lời gọi ấy vô hại
 * (truyền thẳng làm đối số, so một mốc cách hàng tháng…). Đo 22/09: 5 tệp dùng đồng hồ trần, chỉ
 * <b>1</b> ghép với cửa sổ CSDL. Cấm cả cụm là đẻ ra bốn dòng miễn trừ và <b>tiếng ồn che mất dòng
 * có nghĩa duy nhất</b> (luật 28).
 *
 * <p>⚠ Phạm vi do bài này <b>ĐO</b> (quét cây {@code src/test} của mọi module), ⛔ do ai gõ tay.
 */
class MuiGioBoKiemTest {

    /**
     * ⚠ Ghép từ hai mảnh — bài này quét cả cây {@code src/test}, và một hằng viết liền biến chính
     * tệp này thành một "tệp có cửa sổ CSDL". Bộ canh tự khớp vào mình là bộ canh ⛔ canh gì.
     */
    private static final Pattern CUA_SO_CSDL = Pattern.compile("now\\(\\)\\s*-\\s*" + "interval");

    /** {@code LocalDate.now()} / {@code LocalDateTime.now()} ⛔ đối số — tức đọc múi giờ của MÁY. */
    private static final Pattern DONG_HO_TRAN = Pattern.compile("\\bLocal(?:Date|DateTime)\\.now\\(\\s*\\)");

    /**
     * ⛔⛔ <b>Tệp này tự loại mình — và đó là quyết định có chủ ý, ⛔ phải một lỗ hổng.</b>
     *
     * <p>Bộ canh bắt chính nó ở <b>hai</b> lượt chạy đầu. Lượt một: đồ gá của bài tự-kiểm chứa
     * nguyên văn hai mẫu (đã tách hằng). Lượt hai: chính <b>thông điệp chẩn đoán</b> chứa chúng —
     * nó phải gọi tên được thứ nó đang nói, nếu ⛔ thì người đọc log ⛔ biết phải sửa gì.
     *
     * <p>Bẻ cong câu chữ cho hết đỏ là **xoá bài học mà vẫn để bộ canh thủng** (T54.8 · luật 33 —
     * lời khuyên in ra từ một bộ canh cũng là mã). ⇒ Loại ĐÚNG một tệp, và
     * {@link #khongTronHaiDongHo()} khẳng định danh sách loại trừ luôn đúng <b>một</b> phần tử,
     * để nó ⛔ âm thầm rộng ra (luật 28).
     */
    private static final String TEP_CUA_CHINH_BAI_NAY = "MuiGioBoKiemTest.java";

    private static final Set<String> BO_QUA_THU_MUC = Set.of(".git", "target", "node_modules", "build");

    @Test
    @DisplayName("⭐⭐ T83.1 — tệp kiểm dùng cửa sổ đồng hồ CSDL thì ⛔ được dựng mốc bằng đồng hồ môi trường")
    void khongTronHaiDongHo() {
        List<String> viPham = new ArrayList<>();
        Set<String> coCuaSo = new TreeSet<>();
        List<String> tuLoai = new ArrayList<>();

        for (Path tep : tepKiem()) {
            String ma = boChuThich(doc(tep));
            if (!CUA_SO_CSDL.matcher(ma).find()) {
                continue;
            }
            String ten = ten(tep);
            if (ten.endsWith(TEP_CUA_CHINH_BAI_NAY)) {
                tuLoai.add(ten);
                continue;
            }
            coCuaSo.add(ten);
            if (DONG_HO_TRAN.matcher(ma).find()) {
                viPham.add("  · " + ten);
            }
        }

        assertThat(tuLoai)
                .as("Chỉ CHÍNH tệp này được tự loại — danh sách loại trừ ⛔ được âm thầm rộng ra (luật 28)")
                .hasSize(1);

        assertThat(coCuaSo)
                .as("⛔ Quét ra 0 tệp có cửa sổ đồng hồ CSDL — biểu thức đã mù, ⛔ phải kho ⛔ có")
                .isNotEmpty();

        assertThat(viPham)
                .as(
                        """
                        %d tệp kiểm trộn HAI đồng hồ:

                        %s
                        Mốc dựng bằng `LocalDate.now()` đọc múi giờ của MÁY (runner UTC · máy dev \
                        +07), còn `now() - interval` do CSDL tính. Cửa sổ 24 giờ khi ấy đóng lại \
                        đúng vào giờ trong ngày mà đồ gá dùng ⇒ một KHUNG GIỜ CHẾT lặp mỗi ngày, \
                        ⛔ phải một bài chập chờn.

                        ⇒ Dựng mốc bằng `LocalDate.now(DateTimeUtils.ZONE_VN)`.
                        ⛔ ĐỪNG nới cửa sổ thành `interval '2 day'` — cửa sổ ấy có mặt để ⛔ khớp \
                        nhầm dòng sót của lượt chạy trước (§11.17 · T58.6).""",
                        viPham.size(), String.join("\n", viPham))
                .isEmpty();
    }

    @Test
    @DisplayName("⭐⭐ Ghim `TZ=UTC` cho surefire phải còn nguyên — gỡ nó là làm lớp lỗi ấy vô hình trở lại")
    void ghimMuiGioPhaiConNguyen() {
        String pom = doc(timTuGocKho("backend/pom.xml"));

        assertThat(pom.replaceAll("\\s+", ""))
                .as(
                        """
                        `backend/pom.xml` ⛔ còn ghim <TZ>UTC</TZ> cho surefire.

                        Runner chạy UTC, máy dev chạy Asia/Ho_Chi_Minh. ⛔ ghim thì `make ci-local` \
                        VỀ NGUYÊN TẮC ⛔ dựng lại được lớp lỗi "đọc đồng hồ theo múi giờ của máy" — \
                        thứ đã làm đỏ #198/#199/#200 (conventions.md §1.5-d).

                        ⛔ Đổi sang Asia/Ho_Chi_Minh: ghim đúng múi giờ sản phẩm là làm lớp lỗi ấy \
                        VÔ HÌNH TRỞ LẠI.""")
                .contains("<TZ>UTC</TZ>");

        assertThat(pom)
                .as("Ghim phải nằm trong cấu hình surefire, ⛔ phải một chỗ nào khác")
                .contains("maven-surefire-plugin");
    }

    /**
     * Bộ canh phải <b>bắt được</b> vi phạm (CLAUDE.md luật 1) — và phải <b>⛔ bắt</b> một chú thích
     * mô tả vi phạm, vì javadoc của {@code PollerChangCuoiHttpTest} nay trích đúng chuỗi ấy để giải
     * thích sự cố. Bộ canh phạt người viết tài liệu tử tế là bộ canh sẽ bị sửa cho im (T46.7 · T49.6).
     */
    @Test
    @DisplayName("⭐ Tự kiểm chứng: bắt mã thật, tha chú thích, và phân biệt có/⛔ đối số múi giờ")
    void tuKiemChung() {
        // ⚠⚠ GHÉP TỪ MẢNH, ⛔ viết liền. Bản đầu viết nguyên văn và bộ canh **bắt chính tệp này**
        //    ở lượt chạy đầu: `boChuThich` CỐ Ý giữ chuỗi ký tự (T54.8 — một mẫu nằm trong chuỗi
        //    vẫn phải bị bắt), nên đồ gá của bài tự-kiểm trở thành một vi phạm thật. Cùng hình dạng
        //    `PostgresCollationParityTest` đã tách hằng `MOC`, và là lần thứ MƯỜI HAI một bộ canh
        //    của dự án bắt đúng người vừa viết ra nó.
        String thatSu = "String ngay = LocalDate" + ".now(); rồi lọc now() - " + "interval '1 day'";
        String daVa = "String ngay = LocalDate" + ".now(DateTimeUtils.ZONE_VN);";
        String trongChuThich = "Bản trước dùng LocalDate" + ".now() TRẦN.";

        assertThat(DONG_HO_TRAN.matcher(boChuThich(thatSu)).find())
                .as("Mã thật phải BỊ BẮT")
                .isTrue();
        assertThat(DONG_HO_TRAN.matcher(boChuThich(daVa)).find())
                .as("⛔ Dạng có múi giờ tường minh ⛔ được tính là vi phạm")
                .isFalse();
        assertThat(DONG_HO_TRAN
                        .matcher(boChuThich("/** " + trongChuThich + " */ int x = 1;"))
                        .find())
                .as("⛔ Một CHÚ THÍCH mô tả vi phạm ⛔ phải một vi phạm — javadoc của "
                        + "`PollerChangCuoiHttpTest` nay trích đúng chuỗi ấy để giải thích sự cố")
                .isFalse();

        assertThat(CUA_SO_CSDL.matcher("WHERE t > now() - interval '1 day'").find())
                .isTrue();
        assertThat(CUA_SO_CSDL.matcher("WHERE t > ?").find()).isFalse();
    }

    // -------------------------------------------------------------------------

    /** Bỏ chú thích mà <b>giữ</b> chuỗi ký tự — cùng phép của {@code CotPhase2CoDocGhiTest}. */
    static String boChuThich(String ma) {
        return ma.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)^\\s*//.*$", " ");
    }

    private static List<Path> tepKiem() {
        Path goc = timTuGocKho("backend");
        List<Path> ket = new ArrayList<>();
        try {
            Files.walkFileTree(goc, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    return BO_QUA_THU_MUC.contains(d.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    String p = tep.toString();
                    if (p.endsWith(".java") && p.contains("/src/test/")) {
                        ket.add(tep);
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
        return ket;
    }

    private static String ten(Path tep) {
        String p = tep.toString();
        int i = p.indexOf("backend/");
        return i < 0 ? p : p.substring(i);
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
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
        return fail("Không tìm thấy %s".formatted(duongDanTuongDoi));
    }
}
