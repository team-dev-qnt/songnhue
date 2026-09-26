package com.songnhue.app.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⭐⭐ <b>Mọi số hiệu mã lỗi KHUYẾT phải được xếp loại — và mã NGHỈ HƯU ⛔ được cấp lại (T42.28).</b>
 *
 * <h2>Khe hở</h2>
 *
 * <p>Luật <i>"một mã lỗi đã nghỉ hưu ⛔ được dùng lại"</i> ra đời ở {@code T59.1} khi {@code OPS-2022}
 * đổi thành {@code SYS-0012}, và từ đó tới nay nó sống bằng <b>hai dòng chú thích</b> — một ở javadoc
 * {@code ErrorCode}, một ở {@code error-map.ts}. ⛔ Có cổng kiểm nào. Một chú thích ⛔ phải một cổng
 * kiểm (bài học lặp lại của kho: §11.14 · T41.21 · T50.12), và lượt này nhân đôi bề mặt ấy —
 * {@code OPS-2015} và {@code OPS-2016} cùng nghỉ hưu.
 *
 * <h2>Vì sao hậu quả IM LẶNG</h2>
 *
 * <p>Cấp lại một số đã nghỉ hưu biên dịch sạch, mọi bài kiểm xanh, danh mục vẫn đếm đủ. Thứ hỏng nằm
 * <b>ngoài</b> kho: mọi dòng nhật ký, ảnh chụp màn hình và phiếu hỗ trợ cũ mang mã ấy bắt đầu <b>đọc
 * sai nghĩa</b>. ⛔ Có bộ kiểm nào của dự án nhìn thấy một phiếu hỗ trợ.
 *
 * <h2>Bộ canh này ĐO vế trái</h2>
 *
 * <p>Nó ⛔ so với một danh sách gõ tay (luật 28): nó đọc {@code ErrorCode.java} trên đĩa, dựng tập mã
 * <b>sống</b>, tự tính các số <b>khuyết</b> theo từng dải {@code <PREFIX>-<nghìn>xxx}, rồi bắt mỗi
 * khuyết phải mang đúng một nhãn. Một mã mới bị xoá đi ngày mai là một lượt CI đỏ gọi đích danh số.
 *
 * <p>⚠ Tính khuyết phải theo <b>dải nghìn</b>, ⛔ theo prefix: {@code AUTH} có {@code 0001..0010} và
 * {@code 3001..3002}, nên đếm theo prefix cho ra gần <b>3000</b> số khuyết — một kết quả
 * <i>"mọi thứ đều hỏng"</i> gần như luôn là một <b>phép đo hỏng</b>, và bản đầu của bài này đúng thế.
 */
class MaLoiNghiHuuTest {

    private static final Pattern MA_SONG = Pattern.compile("(?m)^\\s{4}[A-Z]+_\\d{4}\\(\"([A-Z]+-\\d{4})\"");

    /** Nhãn cố ý ⛔ có khoảng trắng ⇒ bộ định dạng mã ⛔ thể tách nó làm bộ canh sai (§11.13). */
    private static final Pattern NGHI_HUU = Pattern.compile("NGHI_HUU:([A-Z]+-\\d{4})");

    private static final Pattern CHUA_DUNG = Pattern.compile("CHUA_DUNG:([A-Z]+-\\d{4})");

    @Test
    @DisplayName("⭐⭐ Mọi số hiệu KHUYẾT đều được xếp loại NGHI_HUU hoặc CHUA_DUNG — ⛔ có khuyết nào ⛔ lời giải thích")
    void moiKhuyetDeuDuocXepLoai() throws IOException {
        String nguon = Files.readString(nguonErrorCode(), StandardCharsets.UTF_8);
        Set<String> song = maSong(nguon);
        Set<String> daKhai = new LinkedHashSet<>();
        daKhai.addAll(bat(NGHI_HUU, nguon));
        daKhai.addAll(bat(CHUA_DUNG, nguon));

        // Chống tập rỗng (luật 7): mẫu hỏng thì cả hai vế cùng rỗng và bài xanh vì lý do sai.
        assertThat(song)
                .as("bộ đọc mã sống hỏng ⇒ mọi khẳng định dưới chạy trên tập rỗng")
                .hasSizeGreaterThan(100);
        assertThat(daKhai)
                .as("bộ đọc nhãn bia mộ hỏng ⇒ mọi khuyết sẽ bị báo là chưa xếp loại")
                .isNotEmpty();

        List<String> chuaXepLoai =
                khuyet(song).stream().filter(ma -> !daKhai.contains(ma)).toList();

        assertThat(chuaXepLoai)
                .as(
                        "⛔⛔ Số hiệu khuyết mà ⛔ ai nói vì sao. Mỗi số phải mang đúng MỘT nhãn trong javadoc"
                                + " `ErrorCode`: `NGHI_HUU:<mã>` (từng sống, ⛔ BAO GIỜ được cấp lại) hoặc"
                                + " `CHUA_DUNG:<mã>` (nhảy qua, chưa từng ra khỏi kho ⇒ cấp lại được).%n"
                                + "   mã sống: %d · đã khai: %s · khuyết: %s",
                        song.size(), daKhai, khuyet(song))
                .isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ Mã đã NGHỈ HƯU ⛔ được cấp lại — cấp lại làm mọi phiếu hỗ trợ cũ đọc sai nghĩa")
    void maNghiHuuKhongDuocCapLai() throws IOException {
        String nguon = Files.readString(nguonErrorCode(), StandardCharsets.UTF_8);
        Set<String> song = maSong(nguon);
        Set<String> nghiHuu = bat(NGHI_HUU, nguon);

        assertThat(nghiHuu)
                .as("chống tập rỗng: ⛔ có mã nghỉ hưu nào thì bài này ⛔ khẳng định gì")
                .isNotEmpty();

        assertThat(song)
                .as("⛔⛔ số hiệu đã nghỉ hưu được cấp lại cho một nghĩa MỚI — hỏng ở ngoài kho, ⛔ dòng đỏ nào báo")
                .doesNotContainAnyElementsOf(nghiHuu);
    }

    @Test
    @DisplayName("⭐ Vế FE: `error-map.ts` cũng ⛔ được mang mã đã nghỉ hưu — hai phía phải nghỉ hưu CÙNG lúc")
    void giaoDienCungKhongGiuMaNghiHuu() throws IOException {
        String nguon = Files.readString(nguonErrorCode(), StandardCharsets.UTF_8);
        Set<String> nghiHuu = bat(NGHI_HUU, nguon);
        String banDo = Files.readString(
                gocKho().resolve("frontend/admin-app/src/shared/error-map.ts"), StandardCharsets.UTF_8);

        // Chỉ soi KHOÁ của bảng (`'OPS-2022': {`), ⛔ soi cả tệp: chính chỗ này đang có một chú thích
        // nhắc tên mã đã nghỉ hưu, và đó là thứ PHẢI giữ (T46.7 — bộ canh ⛔ được phạt người viết tài liệu).
        List<String> conGiu = nghiHuu.stream()
                .filter(ma -> Pattern.compile("(?m)^\\s*'" + Pattern.quote(ma) + "'\\s*:")
                        .matcher(banDo)
                        .find())
                .toList();

        assertThat(conGiu)
                .as("⛔ giao diện còn ánh xạ mã đã nghỉ hưu ⇒ hai phía nói hai nghĩa cho cùng một số")
                .isEmpty();
    }

    @Test
    @DisplayName("⭐ Tự kiểm: bộ canh PHÂN BIỆT được hai trạng thái — ⛔ thì cái xanh của nó ⛔ nói gì")
    void tuKiemPhanBietDuocHaiTrangThai() {
        // Vế 1: một khuyết ⛔ khai ⇒ phải lọt vào danh sách.
        Set<String> coKhuyet = new LinkedHashSet<>(List.of("OPS-2001", "OPS-2002", "OPS-2004"));
        assertThat(khuyet(coKhuyet)).containsExactly("OPS-2003");

        // Vế 2: dãy liên tục ⇒ ⛔ có khuyết nào. Thiếu vế này thì một bộ tính LUÔN trả rỗng cũng xanh.
        Set<String> lienTuc = new LinkedHashSet<>(List.of("OPS-2001", "OPS-2002", "OPS-2003"));
        assertThat(khuyet(lienTuc)).isEmpty();

        // Vế 3: hai DẢI khác nhau ⛔ được nối vào nhau — đây là chỗ bản đầu của bài này đo sai.
        Set<String> haiDai = new LinkedHashSet<>(List.of("AUTH-0001", "AUTH-0002", "AUTH-3001", "AUTH-3002"));
        assertThat(khuyet(haiDai))
                .as("gộp hai dải cho ra ~3000 số khuyết — một phép đo hỏng đọc y hệt một phát hiện")
                .isEmpty();

        // Vế 4: mẫu nhãn đọc đúng, và ⛔ nuốt nhãn đứng sát chữ khác.
        assertThat(bat(NGHI_HUU, "{@code NGHI_HUU:OPS-2022} → SYS-0012")).containsExactly("OPS-2022");
        assertThat(bat(CHUA_DUNG, "x{@code CHUA_DUNG:HR-2006} (14/09)")).containsExactly("HR-2006");
    }

    // ---- bộ đọc ----------------------------------------------------------------

    /** Số hiệu khuyết, tính THEO TỪNG DẢI {@code <PREFIX>-<nghìn>xxx}. */
    private static List<String> khuyet(Set<String> song) {
        Map<String, List<Integer>> theoDai = new LinkedHashMap<>();
        for (String ma : song) {
            String[] phan = ma.split("-");
            int so = Integer.parseInt(phan[1]);
            theoDai.computeIfAbsent(phan[0] + "-" + (so / 1000), k -> new ArrayList<>())
                    .add(so);
        }
        List<String> ra = new ArrayList<>();
        for (var muc : new TreeMap<>(theoDai).entrySet()) {
            List<Integer> v = muc.getValue().stream().sorted().toList();
            String prefix = muc.getKey().substring(0, muc.getKey().lastIndexOf('-'));
            for (int i = v.get(0); i <= v.get(v.size() - 1); i++) {
                if (!v.contains(i)) {
                    ra.add("%s-%04d".formatted(prefix, i));
                }
            }
        }
        return ra;
    }

    private static Set<String> maSong(String nguon) {
        return bat(MA_SONG, nguon);
    }

    private static Set<String> bat(Pattern mau, String van) {
        Set<String> ra = new LinkedHashSet<>();
        Matcher m = mau.matcher(van);
        while (m.find()) {
            ra.add(m.group(1));
        }
        return ra;
    }

    private static Path nguonErrorCode() {
        return gocKho().resolve("backend/core/src/main/java/com/songnhue/core/common/error/ErrorCode.java");
    }

    private static Path gocKho() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("⛔ tìm thấy gốc kho");
        }
        return p;
    }
}
