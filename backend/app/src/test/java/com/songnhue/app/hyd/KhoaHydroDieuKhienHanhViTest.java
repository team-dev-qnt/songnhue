package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mỗi khoá {@code hydro.*} phải được XẾP LOẠI: có bằng chứng hành vi, hoặc khai nợ kèm lý do</b>
 * — nợ T48.11, và đây là vế mà {@code HydroSettingsReadTest} (T27.5) ⛔ trả lời được.
 *
 * <h2>Hai câu hỏi khác nhau, đừng đọc câu này thành câu kia</h2>
 *
 * <ul>
 *   <li>T27.5 hỏi <b>"khoá này có hàm đọc ⛔"</b> — một câu về <b>cấu trúc</b>;
 *   <li>bài này hỏi <b>"giá trị người vận hành gõ vào có đổi điều gì ⛔"</b> — một câu về
 *       <b>hành vi</b>.
 * </ul>
 *
 * <p>T47.12 đo được rằng hai câu ấy tách rời nhau: {@code hydro.polling.max-retry} có hàm đọc, có ô
 * nhập, có ba bài đi qua nhánh của nó — mà <b>0 lượt nào chạm bảng {@code settings}</b>; tệ hơn,
 * giá trị dự phòng trong Java <b>trùng khít</b> giá trị seed, nên xoá hàng seed / đổi tên khoá /
 * {@code SettingService} trả rỗng đều cho ra <b>cùng một hành vi</b> (luật 3 + luật 9).
 *
 * <h2>Vì sao ⛔ để khoảng trống nằm im mà phải bắt XẾP LOẠI</h2>
 *
 * Cùng khuôn với {@code VongKhuHoiDuPhamViTest} · {@code CotPhase2CoDocGhiTest} ·
 * {@code PhuThuocNoiBoTest}: <b>đo vế trái từ mã nguồn</b> rồi đòi người viết chọn một trong hai
 * nhánh. Dòng nợ T48.11 cũ là một con số (*"1/9"*) và một danh sách gõ tay — nó ⛔ tự biết khi kho
 * thêm khoá thứ mười một. Nay một khoá {@code hydro.*} mới ra đời là một lượt CI đỏ.
 *
 * <p>⚠ Danh sách khoá <b>ĐO</b> từ {@code HydroSettings.java} chứ ⛔ chép tay — cùng lý do đã ghi ở
 * {@code MigrationNamingTest} sau T49.1: phạm vi do bộ canh đo, ⛔ do người viết sổ đếm (luật 28).
 */
class KhoaHydroDieuKhienHanhViTest {

    private static final String LOP_DOC =
            "backend/hydro/src/main/java/com/songnhue/hydro/application/HydroSettings.java";

    /** Hằng {@code … = "hydro.…"} trong {@code HydroSettings} — vế trái, ĐO chứ ⛔ gõ tay. */
    private static final Pattern KHOA_DUOC_KHAI = Pattern.compile("=\\s*\"(hydro\\.[a-z0-9.\\-]+)\"");

    /**
     * Khoá ĐÃ có bằng chứng hành vi → tệp bài kiểm (phải tồn tại trên đĩa).
     *
     * <p>⛔ Một dòng trỏ vào tệp ⛔ tồn tại là một khoá được miễn kiểm <b>trong im lặng</b>, mà lại
     * ĐỌC NHƯ một lời bảo đảm — nguy hiểm hơn hẳn việc ⛔ khai gì.
     */
    private static final Map<String, String> CO_BANG_CHUNG = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(
                    "hydro.polling.max-retry",
                    "backend/app/src/test/java/com/songnhue/app/hyd/PollerChangCuoiHttpTest.java"),
            Map.entry(
                    "hydro.polling.cron",
                    "backend/app/src/test/java/com/songnhue/app/hyd/PollerChangCuoiHttpTest.java"),
            Map.entry(
                    "hydro.polling.timeout-seconds",
                    "backend/app/src/test/java/com/songnhue/app/hyd/PollerChangCuoiHttpTest.java"),
            Map.entry(
                    "hydro.retention-years",
                    "backend/app/src/test/java/com/songnhue/app/hyd/HanLuuTheoCauHinhTest.java"),
            Map.entry(
                    "hydro.raw-retention-days",
                    "backend/app/src/test/java/com/songnhue/app/hyd/HanLuuTheoCauHinhTest.java"),
            Map.entry(
                    "hydro.source.alert-after-failures",
                    "backend/app/src/test/java/com/songnhue/app/hyd/NguongChuongNguonTheoCauHinhTest.java"),
            Map.entry(
                    "hydro.portal.station-codes",
                    "backend/app/src/test/java/com/songnhue/app/portal/HydroPortalCacheSplitTest.java"),
            Map.entry(
                    "hydro.polling.source-frame-minutes",
                    "backend/app/src/test/java/com/songnhue/app/hyd/PublicHydroHttpTest.java"),
            Map.entry(
                    "hydro.station.signal-loss-frames",
                    "backend/app/src/test/java/com/songnhue/app/hyd/PublicHydroHttpTest.java"),
            Map.entry(
                    "hydro.quality.suspect-rule",
                    "backend/app/src/test/java/com/songnhue/app/hyd/HydroQualityHttpTest.java")));

    /**
     * Khoá CHƯA có — <b>mỗi dòng một lý do ĐO ĐƯỢC, tối thiểu 40 ký tự</b>.
     *
     * <p>⚠ Ràng buộc độ dài ⛔ phải hình thức: nó chặn kiểu miễn trừ <i>"chưa làm"</i> — một câu
     * đúng với MỌI khoảng trống, nên nó ⛔ phân biệt được <i>có thứ tự ưu tiên</i> với <i>bị bỏ
     * quên</i>.
     *
     * <h2>⭐⭐ 17/09/2026 — danh sách này RỖNG, và chính lúc ấy nó sinh ra một luật-7</h2>
     *
     * <p>Nợ T48.11 đã trả trọn: <b>10/10</b> khoá {@code hydro.*} có bằng chứng hành vi. Nhưng một
     * phép kiểm chạy qua <b>tập rỗng</b> thì xanh trọn vẹn mà ⛔ khẳng định gì (luật 7) — khoá
     * {@code hydro.*} tiếp theo sẽ gặp một dòng mã đã <b>mục</b> chứ ⛔ một bộ canh đang sống. ⇒
     * {@link #lyDoKhaiNoPhaiDoDuoc} có một bài <b>tự-kiểm</b> chạy đúng phép kiểm ấy trên dữ liệu
     * GIẢ. Cùng khuôn với {@code VongKhuHoiDuPhamViTest} khi {@code CHUA_CO_BAI_KIEM} rỗng đi.
     *
     * <p>⚠⚠ Và lượt trả nợ ấy <b>bác một trong năm lý do cũ</b>: {@code hydro.polling.cron} từng
     * được miễn kiểm vì <i>"đăng ký một lần lúc khởi động"</i>. Đo lại {@code HydroPollScheduler}:
     * nó chạy nhịp tim cố định 10 giây và <b>đọc lại cron mỗi nhịp</b> — javadoc của chính lớp ấy
     * nói ra yêu cầu ngược lại. Một lý do khai nợ cũng là dữ liệu chưa kiểm.
     */
    private static final Map<String, String> CHUA_CO_BANG_CHUNG = new TreeMap<>();

    // =========================================================================

    private static Path gocKho() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("⛔ tìm được gốc kho (thư mục chứa `.claude`)");
        }
        return p;
    }

    private static Set<String> khoaDuocKhai() {
        try {
            String ma = Files.readString(gocKho().resolve(LOP_DOC), StandardCharsets.UTF_8);
            Set<String> ket = new TreeSet<>();
            Matcher m = KHOA_DUOC_KHAI.matcher(ma);
            while (m.find()) {
                ket.add(m.group(1));
            }
            return ket;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("⛔⛔ Mọi khoá `hydro.*` phải được XẾP LOẠI — có bằng chứng hành vi, hoặc khai nợ có lý do")
    void moiKhoaDeuDuocXepLoai() {
        Set<String> coThat = khoaDuocKhai();

        // ⚠ Chống xanh-trên-tập-rỗng (luật 7), và phải là một CON SỐ: một bộ tách hỏng một nửa vẫn
        //   "không rỗng". Mẫu regex hỏng hay tệp đổi chỗ đều làm bài này MÙ chứ ⛔ đỏ.
        assertThat(coThat)
                .as("⛔ ⛔ đọc được đủ hằng khoá trong %s ⇒ mọi khẳng định dưới đây nói về tập RỖNG", LOP_DOC)
                .hasSizeGreaterThanOrEqualTo(9);

        Set<String> daXep = new TreeSet<>(CO_BANG_CHUNG.keySet());
        daXep.addAll(CHUA_CO_BANG_CHUNG.keySet());

        List<String> chuaXep =
                coThat.stream().filter(k -> !daXep.contains(k)).sorted().toList();
        assertThat(chuaXep)
                .as(
                        """
                        Khoá `hydro.*` sau ⛔ nằm trong `CO_BANG_CHUNG` lẫn `CHUA_CO_BANG_CHUNG`: %s

                        Một khoá `settings` bày ra ô nhập trên màn hình Cấu hình hệ thống. Nếu giá trị \
                        người vận hành gõ vào ⛔ điều khiển gì thì màn hình ấy NÓI DỐI, và ⛔ dòng log \
                        nào báo (T47.12 — giá trị dự phòng trong Java trùng khít giá trị seed nên hai \
                        trạng thái ⛔ phân biệt được).

                        Hãy CHỌN: dựng bài đo hành vi (khuôn `HanLuuTheoCauHinhTest` — ghi qua \
                        `SettingService.update`, hai vế đều dùng số KHÁC mặc định, `finally` khôi phục) \
                        rồi khai ở `CO_BANG_CHUNG`; hoặc khai nợ kèm lý do ĐO ĐƯỢC ≥ 40 ký tự.""",
                        chuaXep)
                .isEmpty();

        List<String> khongConTonTai =
                daXep.stream().filter(k -> !coThat.contains(k)).sorted().toList();
        assertThat(khongConTonTai)
                .as("Khoá khai trong danh sách mà `HydroSettings` ⛔ còn khai (đổi tên? gỡ rồi?): %s", khongConTonTai)
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi bài kiểm khai ở `CO_BANG_CHUNG` phải CÓ THẬT trên đĩa và phải GHI qua SettingService")
    void baiKiemDaKhaiPhaiCoThatVaPhaiGhiSettings() {
        List<String> hong = CO_BANG_CHUNG.entrySet().stream()
                .map(e -> {
                    Path tep = gocKho().resolve(e.getValue());
                    if (!Files.exists(tep)) {
                        return e.getKey() + " → ⛔ có tệp " + e.getValue();
                    }
                    try {
                        // ⛔⛔ Vế thứ hai mới là vế khó: một bài kiểm CÓ THẬT vẫn có thể ⛔ chạm bảng
                        //    `settings` lần nào — đúng trạng thái mà T47.12 tìm ra. `UPDATE settings`
                        //    thẳng cũng ⛔ tính: `SettingService` mới là nơi dọn đệm Caffeine, ghi
                        //    thẳng vào bảng thì đệm toàn tiến trình giữ giá trị cũ (§10.67).
                        String ma = Files.readString(tep, StandardCharsets.UTF_8);
                        return ma.contains("settings.update(") || ma.contains("settingService.update(")
                                ? null
                                : e.getKey() + " → " + e.getValue() + " ⛔ có lượt ghi qua SettingService.update";
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .filter(x -> x != null)
                .sorted()
                .toList();
        assertThat(hong).as("Bằng chứng khai mà ⛔ đứng được: %s", hong).isEmpty();
    }

    @Test
    @DisplayName("Lý do khai nợ phải ĐO ĐƯỢC — *\"chưa làm\"* đúng với mọi khoảng trống nên nó ⛔ phải lý do")
    void lyDoKhaiNoPhaiDoDuoc() {
        List<String> hoiHot = CHUA_CO_BANG_CHUNG.entrySet().stream()
                .filter(e -> e.getValue().length() < 40)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        assertThat(hoiHot).as("Lý do ngắn hơn 40 ký tự: %s", hoiHot).isEmpty();
    }

    /**
     * ⭐⭐ <b>Tự-kiểm: phép kiểm ngay trên còn phân biệt được hai trạng thái ⛔</b>.
     *
     * <p>{@link #CHUA_CO_BANG_CHUNG} nay <b>RỖNG</b> (T48.11 đã trả trọn), nên
     * {@link #lyDoKhaiNoPhaiDoDuoc} chạy qua một tập rỗng và xanh <b>vì ⛔ có gì để xét</b> — đúng
     * luật 7. Bài này chạy cùng vị từ ấy trên dữ liệu GIẢ để chứng minh nó vẫn <b>bắt được</b> một
     * lý do hời hợt vào ngày ai đó khai nợ khoá {@code hydro.*} thứ mười một.
     */
    @Test
    @DisplayName("⭐ TỰ-KIỂM — luật *\"lý do ≥ 40 ký tự\"* vẫn bắt được vi phạm dù danh sách nợ đã RỖNG")
    void luatLyDoVanBatDuocViPham() {
        Map<String, String> gia = new TreeMap<>(Map.of(
                "hydro.gia.qua-ngan", "chưa làm",
                "hydro.gia.du-dai", "Cần một nguồn giả TRẢ LỜI CHẬM hơn timeout mới đo được khoá này."));

        List<String> hoiHot = gia.entrySet().stream()
                .filter(e -> e.getValue().length() < 40)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        assertThat(hoiHot)
                .as("⛔ Vị từ ⛔ còn phân biệt được *\"chưa làm\"* với một lý do đo được ⇒ dòng khai nợ "
                        + "tiếp theo sẽ đi lọt, và {@code lyDoKhaiNoPhaiDoDuoc} xanh vì lý do sai")
                .containsExactly("hydro.gia.qua-ngan");
    }
}
