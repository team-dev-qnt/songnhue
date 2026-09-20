package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Hai nhịp tự làm mới — hai con số, HAI NGUỒN</b>. T35.12.
 *
 * <h2>Vì sao đây là một bài kiểm chứ không phải một dòng chú thích</h2>
 *
 * <p>Hệ có <b>hai</b> nhịp làm mới, cả hai đều đúng:
 *
 * <ul>
 *   <li><b>Cổng công khai — 5 phút.</b> Con số đã <b>cam kết với Công ty</b> ở mục <b>OI-09</b>,
 *       nên nó phải <b>cấu hình được</b> (quy tắc 12): khoá {@code site.home.realtime.refresh-seconds},
 *       seed {@code 300}. Công ty đổi ý thì đổi ô nhập, ⛔ không đòi deploy.
 *   <li><b>Màn hình vận hành nội bộ — 2 phút.</b> Con số này <b>bám vào chu kỳ poller</b> (2
 *       phút/lần, chốt G3), ⛔ không bám vào ý muốn của Công ty. Làm mới nhanh hơn poller là vẽ lại
 *       cùng một dữ liệu; chậm hơn là để cán bộ trực nhìn số cũ. Nó là <b>hằng số kỹ thuật</b>, và
 *       cấu hình được nó là mời người ta đặt sai.
 * </ul>
 *
 * <h2>⛔⛔ Hai con số giống nhau về HÌNH DẠNG nên rất dễ bị "dọn dẹp" thành một</h2>
 *
 * <p>Cả hai đều là <i>"bao nhiêu lâu thì gọi lại API"</i>. Người dọn mã tiếp theo nhìn thấy hai chỗ
 * nói cùng một loại việc và gộp — <b>và bản gộp sẽ chạy đúng</b>: màn hình vẫn làm mới, cổng vẫn làm
 * mới, ⛔ không bài kiểm nào đỏ. Cái mất chỉ lộ ra vào ngày Công ty hạ nhịp cổng xuống 15 phút cho
 * đỡ tải, và màn hình trực ban im lặng đi theo — đúng lúc người ta cần nó nhất.
 *
 * <p>⇒ Bài này khẳng định <b>sự tách rời</b>, ⛔ không khẳng định hai con số. Nó là một cơ chế canh
 * gác cho một <i>quyết định thiết kế</i>, đúng hình dạng quy tắc 14.
 *
 * <h2>⚠ Phạm vi của chính bộ canh này (quy tắc 28) — HAI vế, HAI cách</h2>
 *
 * <ul>
 *   <li><b>Vế cổng công khai: phạm vi do bài ĐO</b> ({@link #trangCongDungNhip}). Mọi
 *       {@code page.tsx} dưới {@code public-web/src/app} có dùng nhịp realtime đều tự vào tầm quét
 *       ⇒ trang thứ tư ra đời ⛔ cần ai nhớ thêm tên.
 *   <li><b>Vế nội bộ: vẫn là danh sách gõ tay</b> ({@link #TEP_NOI_BO}, 3 tệp). ⛔ đo được như vế
 *       trên vì dấu hiệu ở đây là một <i>hằng số</i> chứ ⛔ phải một component — quét theo mẫu
 *       {@code NHIP_*} sẽ lôi về mọi hằng thời gian của admin-app. Thêm màn hình realtime nội bộ
 *       thì <b>phải</b> thêm tên tệp vào đó; đây là khoảng trống đã khai, ⛔ phải khoảng trống mù.
 * </ul>
 *
 * <p>⚠ Bản trước liệt tay <b>hai</b> trang cổng trong khi kho có <b>ba</b>: {@code
 * van-hanh-cong-trinh/page.tsx} nằm ngoài tầm quét kể từ ngày nó ra đời (T68.34). Nó tình cờ
 * <i>đúng</i>, nên ⛔ có triệu chứng nào — đúng hình dạng <i>"cái xanh của một bộ canh hẹp đọc như
 * lời bảo đảm cho phạm vi nó ⛔ soi"</i>.
 */
class HaiNhipLamMoiTest {

    /** Nơi CHỐT nhịp công khai — ⛔ không phải mã, mà là một dòng {@code settings}. */
    private static final String SEED_KHOA_CONG =
            "backend/content/src/main/resources/db/migration/cms/V202608271032__cms_portal_settings_v2.sql";

    private static final String KHOA_NHIP_CONG = "site.home.realtime.refresh-seconds";

    /** Nơi CHỐT nhịp nội bộ — hằng số trong mã admin-app. */
    private static final List<String> TEP_NOI_BO = List.of(
            "frontend/admin-app/src/features/hydro/RiverBoardPage.tsx",
            "frontend/admin-app/src/features/dashboard/useDashboard.ts",
            // ⚠⚠ Thêm 04/09/2026 — và lượt thiếu nó là một bài học đắt.
            //
            // Javadoc lớp này tự khai "soi BA tệp" ngay từ bản đầu, trong khi danh sách có HAI. Tệp
            // thứ ba — `WaterLevelChartPage` — ra đời CÙNG NGÀY, ở CÙNG đợt WS-35, và nó mang đúng
            // hằng `NHIP_LAM_MOI_MS` mà bài này sinh ra để canh.
            //
            // ⛔ Đây là luật 28 tái diễn NGAY TRONG bộ canh viện dẫn luật 28: bộ canh tự khai một
            //    phạm vi RỘNG HƠN phạm vi nó thật sự soi, và cái xanh của nó đọc như một lời bảo
            //    đảm cho cả ba tệp. Người viết màn hình realtime thứ tư phải thêm tên vào ĐÂY.
            "frontend/admin-app/src/features/hydro/WaterLevelChartPage.tsx");

    private static final Pattern HANG_SO_NHIP =
            Pattern.compile("const\\s+(NHIP_\\w+)\\s*=\\s*(\\d+)\\s*\\*\\s*60\\s*\\*\\s*1000");

    /**
     * Dấu hiệu một trang cổng <b>dùng</b> nhịp tự làm mới. Hai hình dạng vì component và prop có thể
     * đổi tên rời nhau; bắt cả hai thì một lượt đổi tên ⛔ làm bộ canh mù trong im lặng.
     */
    private static final Pattern DUNG_NHIP_REALTIME = Pattern.compile("RealtimeFrame|refreshSeconds\\s*=");

    // === 1. Nhịp công khai đến từ `settings`, ⛔ không từ mã ===================

    @Test
    @DisplayName("⭐ Nhịp CÔNG KHAI được seed trong `settings` — Công ty đổi được mà ⛔ không cần deploy")
    void thePublicRhythmLivesInSettings() {
        String seed = doc(SEED_KHOA_CONG);

        assertThat(seed)
                .as("⛔ OI-09 đã cam kết 5 phút với Công ty ⇒ quy tắc 12: con số ấy phải có ô nhập")
                .contains(KHOA_NHIP_CONG);
        assertThat(seed)
                .as("giá trị seed phải là 300 giây — khớp đúng con số đã trả lời Công ty ở OI-09")
                .contains("'" + KHOA_NHIP_CONG + "', '300'");
    }

    @Test
    @DisplayName("⭐ Cổng ĐỌC khoá ấy — ⛔ không ghi cứng một con số nào của riêng nó")
    void thePortalReadsThatKey() {
        List<Path> trang = trangCongDungNhip();

        // ⚠ Vế chống tập rỗng (luật 7 + 29). Con số 3 là số đo ngày 20/09/2026, ⛔ không phải một
        //    ngưỡng thẩm mỹ: `page.tsx` · `muc-nuoc-luong-mua` · `van-hanh-cong-trinh`. Phép quét
        //    chết (đổi tên `RealtimeFrame`, dời thư mục `app/`) làm vòng lặp dưới chạy 0 lần và cả
        //    bài xanh trọn vẹn — đúng tình huống bộ canh sinh ra để bắt.
        assertThat(trang)
                .as("⛔ phép ĐO trả %d trang — nó đã mù. Sửa phép quét, ⛔ đừng hạ con số.", trang.size())
                .hasSizeGreaterThanOrEqualTo(3);

        for (Path t : trang) {
            assertThat(viPham(docTep(t)))
                    .as(
                            """
                            ⛔ `%s` dùng nhịp tự làm mới mà ⛔ đọc khoá `%s`. Ghi cứng ở đây là dựng một \
                            nguồn sự thật thứ hai cho một con số ĐÃ CAM KẾT ở OI-09 — và ô nhập trên màn \
                            hình Cấu hình trở thành một công tắc không nối đi đâu (luật 15).""",
                            t, KHOA_NHIP_CONG)
                    .isFalse();
        }
    }

    /**
     * ⭐ <b>Tự-kiểm-chứng</b> (luật 1 · T37.10): vị từ của luật trên phải <b>phân biệt được</b> ba
     * trạng thái. Thiếu bài này thì {@link #viPham} hỏng đi là luật chính xanh trên mọi trang.
     */
    @Test
    @DisplayName("⭐ Tự-kiểm: vị từ phân biệt được trang VI PHẠM · trang ĐÚNG · trang ⛔ liên quan")
    void tuKiemViTu() {
        String viPham = "<RealtimeFrame refreshSeconds={300}><BangMucNuoc /></RealtimeFrame>";
        String dung =
                "const n = docSo(config?.['" + KHOA_NHIP_CONG + "'], 300);\n" + "<RealtimeFrame refreshSeconds={n} />";
        String khongLienQuan = "export default function Trang() { return <article>Giới thiệu</article>; }";

        assertThat(viPham(viPham))
                .as("trang dùng nhịp mà ⛔ đọc khoá ⇒ phải bị bắt")
                .isTrue();
        assertThat(viPham(dung)).as("trang đọc khoá ⇒ ⛔ được báo vi phạm").isFalse();
        assertThat(viPham(khongLienQuan))
                .as("trang ⛔ dùng nhịp ⇒ ⛔ thuộc phạm vi luật, ⛔ được báo vi phạm")
                .isFalse();
    }

    // === 2. ⛔⛔ Nhịp nội bộ là HẰNG SỐ, và ⛔ KHÔNG được đọc khoá của cổng ======

    /**
     * ⛔⛔ Đây là vế chịu lực — vế phủ định, và là vế duy nhất bắt được lượt "dọn dẹp" nói ở javadoc
     * lớp.
     */
    @Test
    @DisplayName("⛔⛔ admin-app ⛔ KHÔNG được đọc khoá nhịp của cổng — hai nhịp phải ở HAI nguồn")
    void theAdminAppNeverReadsThePortalKey() {
        for (String t : TEP_NOI_BO) {
            assertThat(doc(t))
                    .as(
                            """
                            ⛔ `%s` tham chiếu `%s`. Gộp hai nhịp làm một CHẠY ĐÚNG ở mọi bài kiểm hiện có \
                            — cái mất chỉ lộ ra ngày Công ty hạ nhịp cổng xuống 15 phút cho đỡ tải và màn \
                            hình trực ban im lặng đi theo, đúng lúc người ta cần nó nhất.""",
                            t, KHOA_NHIP_CONG)
                    .doesNotContain(KHOA_NHIP_CONG);
        }
    }

    @Test
    @DisplayName("⭐ Nhịp NỘI BỘ là hằng số 2 phút, bám chu kỳ poller (chốt G3) — ⛔ không phải 5 phút")
    void theInternalRhythmIsTwoMinutes() {
        int soTimThay = 0;
        for (String t : TEP_NOI_BO) {
            Matcher m = HANG_SO_NHIP.matcher(doc(t));
            while (m.find()) {
                soTimThay++;
                assertThat(Integer.parseInt(m.group(2)))
                        .as(
                                """
                                `%s` ở `%s` phải là 2 phút — nó bám chu kỳ poller (2'/lần, chốt G3), ⛔ không \
                                bám nhịp cổng. Nhanh hơn poller là vẽ lại cùng một dữ liệu; chậm hơn là để \
                                cán bộ trực nhìn số cũ.""",
                                m.group(1), t)
                        .isEqualTo(2);
            }
        }

        // ⚠ Vế chống xanh-trên-tập-rỗng (luật 7 + 29): mẫu khớp hụt ⇒ vòng lặp trên chạy 0 lần và cả
        //   bài xanh trọn vẹn. Khẳng định về SỐ LƯỢNG ⛔ không chia sẻ giả định nào với mẫu regex.
        assertThat(soTimThay)
                .as(
                        """
                        ⛔ Mẫu `%s` ⛔ không khớp hằng số nào trong %d tệp. Đổi cách viết hằng số (ví dụ \
                        `120_000`) làm bộ canh này mù mà ⛔ không đỏ — và một bộ canh mù đọc như một lời \
                        bảo đảm. Sửa mẫu, ⛔ đừng bỏ bài.""",
                        HANG_SO_NHIP.pattern(), TEP_NOI_BO.size())
                .isGreaterThanOrEqualTo(TEP_NOI_BO.size());
    }

    // -------------------------------------------------------------------------

    /**
     * Vị từ của luật cổng: một trang <b>dùng</b> nhịp tự làm mới mà <b>⛔ đọc</b> khoá {@code settings}
     * là vi phạm. Tách ra thành hàm để {@link #tuKiemViTu} kiểm chứng được nó — một luật chỉ chạy
     * trên tệp thật ⛔ bao giờ chứng minh được là nó phân biệt được hai trạng thái (luật 9).
     */
    private static boolean viPham(String noiDung) {
        return DUNG_NHIP_REALTIME.matcher(noiDung).find() && !noiDung.contains(KHOA_NHIP_CONG);
    }

    /**
     * ⭐ Phạm vi do bài <b>ĐO</b>, ⛔ do ai gõ tay (luật 28).
     *
     * <p>Bản cũ liệt tay hai tệp, trong khi kho có <b>ba</b> — {@code van-hanh-cong-trinh/page.tsx}
     * (T75) nằm ngoài tầm quét suốt từ ngày nó ra đời, và cái xanh của bộ canh đọc như một lời bảo
     * đảm cho cả cổng. Nay trang realtime thứ tư ra đời là <b>tự</b> vào phạm vi.
     */
    private static List<Path> trangCongDungNhip() {
        Path goc = timGoc("frontend/public-web/src/app");
        try (Stream<Path> duyet = Files.walk(goc)) {
            return duyet.filter(p -> p.getFileName().toString().equals("page.tsx"))
                    .filter(p -> DUNG_NHIP_REALTIME.matcher(docTep(p)).find())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("⛔ không duyệt được " + goc, e);
        }
    }

    private static String docTep(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + p, e);
        }
    }

    private static Path timGoc(String duongDanTuongDoi) {
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

    private static String doc(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Không đọc được " + duongDanTuongDoi, e);
                }
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
