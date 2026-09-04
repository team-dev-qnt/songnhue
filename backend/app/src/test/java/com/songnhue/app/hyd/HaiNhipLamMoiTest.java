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
 * <h2>⚠ Phạm vi của chính bộ canh này (quy tắc 28)</h2>
 *
 * <p>Nó soi <b>ba</b> tệp có tên gọi đích danh dưới đây. Một nhịp làm mới thứ tư ra đời ở một tệp
 * khác ⛔ không được bài này thấy — nếu thêm màn hình realtime mới thì phải thêm tên tệp vào
 * {@link #TEP_NOI_BO}.
 */
class HaiNhipLamMoiTest {

    /** Nơi CHỐT nhịp công khai — ⛔ không phải mã, mà là một dòng {@code settings}. */
    private static final String SEED_KHOA_CONG =
            "backend/content/src/main/resources/db/migration/cms/V202608271032__cms_portal_settings_v2.sql";

    private static final String KHOA_NHIP_CONG = "site.home.realtime.refresh-seconds";

    /** Nơi CHỐT nhịp nội bộ — hằng số trong mã admin-app. */
    private static final List<String> TEP_NOI_BO = List.of(
            "frontend/admin-app/src/features/hydro/RiverBoardPage.tsx",
            "frontend/admin-app/src/features/dashboard/useDashboard.ts");

    private static final Pattern HANG_SO_NHIP =
            Pattern.compile("const\\s+(NHIP_\\w+)\\s*=\\s*(\\d+)\\s*\\*\\s*60\\s*\\*\\s*1000");

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
        List<String> trang = List.of(
                "frontend/public-web/src/app/page.tsx",
                "frontend/public-web/src/app/quan-ly-van-hanh/muc-nuoc-luong-mua/page.tsx");

        assertThat(trang).as("⚠ vế chống tập rỗng (luật 7)").isNotEmpty();

        for (String t : trang) {
            assertThat(doc(t))
                    .as(
                            """
                            ⛔ `%s` phải lấy nhịp từ khoá `%s`. Ghi cứng ở đây là dựng một nguồn sự thật \
                            thứ hai cho một con số ĐÃ CAM KẾT — và ô nhập trên màn hình Cấu hình trở thành \
                            một công tắc không nối đi đâu (luật 15).""",
                            t, KHOA_NHIP_CONG)
                    .contains(KHOA_NHIP_CONG);
        }
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
