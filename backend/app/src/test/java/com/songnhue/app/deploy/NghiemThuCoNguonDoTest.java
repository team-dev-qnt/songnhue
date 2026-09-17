package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `DOD4.10` — mọi con số nghiệm thu phải khai <b>ngày đo</b> và <b>nguồn đo</b>.
 *
 * <h2>Vì sao mục DoD này cần một cổng chứ ⛔ một lời dặn</h2>
 *
 * Trước lượt này, câu ấy sống trong {@code phase4-plan.md} và ⛔ ở đâu khác:
 * {@code grep -c "ngày đo|nguồn đo" .claude/conventions.md} = <b>0</b>. Dự án đã trả giá ba lần cho
 * đúng hình dạng ấy — {@code T11.83} (đếm tay, sai, kế hoạch trỏ nhầm hướng một ngày) · {@code T51.0}
 * (một dòng số đo hết hạn trong {@code CLAUDE.md} lây sang <b>ba</b> agent cùng lúc, mỗi agent dựng
 * sẵn một lượt CI đỏ) · {@code T63.10} (mẫu số đo lại <b>lần thứ tư</b>, ba lượt trước sai ba kiểu).
 *
 * <h2>⛔⛔ Vì sao {@code máy dev} bị từ chối</h2>
 *
 * {@code make ci-local} là cổng để <b>⛔ đẩy mã hỏng lên</b> — nó ⛔ phải một phép đo nghiệm thu.
 * Ba thứ ở máy dev ⛔ dựng lại được điều kiện thật, và cả ba đã gây ra sự cố: hai job chỉ sống trên
 * runner (quét CVE · đóng gói image) · {@code .env.local} chỉ có ở máy · và <b>múi giờ máy dev đúng
 * bằng múi giờ sản phẩm</b> nên nó <b>giấu</b> hẳn một lớp lỗi (§11.27 — bộ kiểm FE đỏ trên runner
 * UTC mà {@code ci-local} về nguyên tắc ⛔ thấy được).
 *
 * <h2>Bộ canh này ⛔ đo gì cả</h2>
 *
 * Nó chỉ bắt mỗi hàng khai <b>nó đến từ đâu</b>. Một ô {@code CHƯA ĐO} là <b>hợp lệ</b> và là một
 * câu khẳng định (quy tắc 16) — thứ ⛔ hợp lệ là một hàng <b>im lặng</b> về nguồn của nó.
 */
class NghiemThuCoNguonDoTest {

    /** Nguồn đo được chấp nhận. ⛔ {@code máy dev} — xem javadoc lớp. */
    private static final List<String> NGUON_HOP_LE = List.of("CI", "VPS-1", "VPS-2", "nguồn ngoài", "CHƯA ĐO");

    private static final String SO_DO = "docs/nghiem-thu-nfr.md";

    @Test
    @DisplayName("⛔⛔ Mỗi hàng số đo nghiệm thu phải khai NGUỒN ĐO, và nguồn ấy phải nằm trong tập đã biết")
    void moiHangPhaiKhaiNguonDo() {
        List<String[]> hang = hangDuLieu();
        Set<String> xau = new TreeSet<>();

        for (String[] o : hang) {
            String ma = o[0];
            String nguon = o[o.length - 2].trim();
            if (NGUON_HOP_LE.stream().noneMatch(nguon::startsWith)) {
                xau.add("%s → nguồn đo `%s`".formatted(ma, nguon));
            }
        }

        assertThat(xau)
                .as(
                        """
                        `%s` có hàng khai nguồn đo ⛔ hợp lệ:
                        %s
                        Nguồn hợp lệ: %s.
                        ⛔⛔ `máy dev` ⛔ phải một nguồn: `make ci-local` là cổng để ⛔ ĐẨY MÃ HỎNG LÊN, \
                        ⛔ phải một phép đo nghiệm thu — hai job chỉ sống trên runner, `.env.local` chỉ \
                        có ở máy, và múi giờ máy dev đúng bằng múi giờ sản phẩm nên nó GIẤU cả một lớp \
                        lỗi (§11.27).
                        Chưa đo thì viết `CHƯA ĐO` — đó là một câu khẳng định, ⛔ phải chỗ trống.""",
                        SO_DO, String.join("\n", xau), NGUON_HOP_LE)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Một hàng CÓ số đo thì phải CÓ ngày đo — một con số ⛔ ngày là con số ⛔ ai biết còn đúng ⛔")
    void coSoDoThiPhaiCoNgayDo() {
        Set<String> xau = new TreeSet<>();

        for (String[] o : hangDuLieu()) {
            String ma = o[0];
            String so = o[2].trim();
            String ngay = o[3].trim();
            boolean coSo = !so.isEmpty() && !"—".equals(so);
            boolean coNgay = ngay.matches(".*\\d{2}/\\d{2}/\\d{4}.*");
            if (coSo && !coNgay) {
                xau.add("%s → có số đo `%s` mà ngày đo là `%s`".formatted(ma, rutGon(so), ngay));
            }
        }

        assertThat(xau)
                .as(
                        """
                        `%s`: những hàng sau ghi một SỐ ĐO mà ⛔ ghi NGÀY đo:
                        %s
                        Một con số ⛔ ngày thì ⛔ ai biết nó còn đúng ⛔ — đúng thứ đã xảy ra ở `T51.0`, \
                        nơi một dòng số đo hết hạn trong CLAUDE.md lây sang BA agent cùng lúc.""",
                        SO_DO, String.join("\n", xau))
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Chống xanh-trên-tập-rỗng: phải đọc ra đủ hàng của CẢ HAI bảng (9 NFR + 13 DoD)")
    void phaiDocRaDuHang() {
        List<String[]> hang = hangDuLieu();

        // ⛔ Vế này là thứ ngăn bộ canh chết âm thầm khi ai đó đổi cấu trúc tệp: một bộ đọc trả
        //    mảng rỗng làm HAI bài trên xanh trọn vẹn mà ⛔ canh gì (luật 7).
        assertThat(hang)
                .as("⛔ đọc được hàng nào từ `%s` — bộ đọc hỏng, hoặc tệp đã đổi cấu trúc bảng", SO_DO)
                .hasSizeGreaterThanOrEqualTo(22);

        // Và phải thấy được CẢ HAI bảng: `NFR-` lẫn `DOD4.`.
        assertThat(hang.stream()
                        .map(o -> o[0])
                        .filter(m -> m.startsWith("NFR-"))
                        .count())
                .as("bảng A (NFR) ⛔ đọc được")
                .isGreaterThanOrEqualTo(9);
        assertThat(hang.stream()
                        .map(o -> o[0])
                        .filter(m -> m.startsWith("DOD4."))
                        .count())
                .as("bảng B (DoD Phase 4) ⛔ đọc được")
                .isGreaterThanOrEqualTo(13);
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM — bộ đọc phân biệt được hàng ĐÚNG với hàng thiếu nguồn/thiếu ngày")
    void tuKiemBoDoc() {
        // Hàng đúng: có đủ 6 ô, nguồn hợp lệ, ngày đúng dạng.
        List<String[]> tot = bocBang("| NFR-99 | ngưỡng | 42 | 18/09/2026 | CI 123 | ghi chú |");
        assertThat(tot).hasSize(1);
        assertThat(tot.get(0)[0]).isEqualTo("NFR-99");
        assertThat(tot.get(0)[4]).isEqualTo("CI 123");

        // ⛔ Vế PHÂN BIỆT: nếu bộ đọc bóc sai cột thì hai bài trên xanh vì lý do sai (luật 9).
        assertThat(tot.get(0)[2]).isEqualTo("42");
        assertThat(tot.get(0)[3]).isEqualTo("18/09/2026");

        // Dòng tiêu đề và dòng kẻ ⛔ được tính là dữ liệu.
        assertThat(bocBang("| Mã | Ngưỡng | Số đo | Ngày đo | Nguồn đo | Ghi chú |"))
                .isEmpty();
        assertThat(bocBang("|---|---|---|---|---|---|")).isEmpty();
        // Dòng ⛔ mở đầu bằng một mã NFR/DOD4 cũng ⛔ phải dữ liệu.
        assertThat(bocBang("| Ô | Luật |")).isEmpty();
    }

    // =========================================================================

    /** Mọi hàng dữ liệu của hai bảng, mỗi hàng là mảng ô đã trim. */
    private static List<String[]> hangDuLieu() {
        return bocBang(doc(timTuGocKho(SO_DO)));
    }

    /**
     * Bóc các hàng bảng Markdown mà ô đầu là một mã {@code NFR-…} hoặc {@code DOD4.…}.
     *
     * <p>⚠ Lọc theo <b>ô đầu</b> chứ ⛔ theo "dòng có dấu `|`": tệp còn hai bảng hướng dẫn khác
     * (cách điền, nguồn hợp lệ) và một bảng ba lượt trả giá — đếm cả chúng là dương tính giả.
     */
    private static List<String[]> bocBang(String noiDung) {
        List<String[]> ra = new ArrayList<>();
        for (String dong : noiDung.split("\n")) {
            String t = dong.trim();
            if (!t.startsWith("|")) {
                continue;
            }
            // ⛔ Split THÔ theo `|`: Markdown cho phép `\|` để viết một dấu ống TRONG một ô, và
            //    lượt chạy đầu của bộ canh này đỏ đúng vì chuyện ấy — một ô ghi chú chứa
            //    `git ls-files \| grep` bị cắt làm đôi ⇒ mọi cột sau nó lệch một bậc ⇒ nó tố cáo
            //    một hàng hoàn toàn đúng. Một bộ đọc bảng mà `\|` làm cho sai là bộ đọc đang canh
            //    văn bản (luật 2).
            String[] o = t.split("(?<!\\\\)\\|", -1);
            if (o.length < 7) {
                continue;
            }
            String[] sach = new String[o.length - 2];
            for (int i = 0; i < sach.length; i++) {
                sach[i] = o[i + 1].replace("\\|", "|").trim();
            }
            if (sach[0].startsWith("NFR-") || sach[0].startsWith("DOD4.")) {
                ra.add(sach);
            }
        }
        return ra;
    }

    private static String rutGon(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ tìm thấy %s tính từ %s"
                .formatted(duongDanTuongDoi, Paths.get("").toAbsolutePath()));
    }
}
