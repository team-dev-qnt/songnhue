package com.songnhue.hydro.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryReading;

/**
 * Mười quy tắc parse của {@code function-spec.md} CN-03.2 — <b>mỗi quy tắc một bài kiểm</b> (T30.4).
 *
 * <h2>⭐ Dữ liệu là bản ĐO THẬT, không phải bản dựng cho vừa bài kiểm</h2>
 *
 * <p>{@code src/test/resources/bhh40/response-mau.txt} là <b>nguyên văn từng byte</b> response đo
 * lúc 10:24 giờ VN ngày 01/09/2026 — kể cả trang ASP.NET rỗng ở đuôi, kể cả {@code __VIEWSTATE}, kể
 * cả thẻ {@code <br>} thừa sau bản ghi cuối. Thay đổi duy nhất: <b>mã số được đổi sang giá trị kiểm
 * thử</b>, vì chính response mang credential trong thuộc tính {@code action} của thẻ form (xem
 * {@code Bhh40Adapter}) và ⛔ một credential thật không bao giờ được nằm trong kho mã.
 *
 * <p>Bài kiểm dựng từ chuỗi tự viết chỉ chứng minh bộ parse đọc được thứ người viết bài kiểm nghĩ
 * nguồn gửi. Ở đây con số phải khớp phép đo: {@code F01652 = 493 cm = 4,930 m}.
 *
 * <h2>⚠ Phạm vi của lớp này — luật 28</h2>
 *
 * <p>Chỉ phủ <b>quy tắc 2, 3, 4, 6, 7, 8</b>. Ba quy tắc còn lại nằm ngoài tầm với của một hàm thuần
 * và <b>không</b> được đọc cái xanh ở đây thành đã phủ:
 *
 * <ul>
 *   <li><b>Quy tắc 1</b> (ghi raw trước parse) — thứ tự giữa hai lời gọi, kiểm ở
 *       {@code TelemetryProbeService} / poller;
 *   <li><b>Quy tắc 5</b> (mã lạ → bỏ qua, ⛔ không tự tạo điểm đo) — cần CSDL. Ở đây chỉ khẳng định
 *       <i>vế ngược</i>: parser <b>không</b> tự lọc mã, nó trả về đủ (xem
 *       {@link #parserKhongTuLocMaLa()});
 *   <li><b>Quy tắc 9, 10</b> — {@code TelemetryBatchTest} và poller.
 * </ul>
 */
class Bhh40ParserTest {

    private static String mau;

    @BeforeAll
    static void docMau() throws IOException {
        try (InputStream in = Bhh40ParserTest.class.getResourceAsStream("/bhh40/response-mau.txt")) {
            assertThat(in)
                    .as("thiếu bản mẫu đo thật — bài kiểm này vô nghĩa nếu không có nó")
                    .isNotNull();
            mau = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ==== Quy tắc 3 — cắt HTML, tách <br> =====================================

    @Test
    @DisplayName("⭐⭐ Quy tắc 3+4: bản mẫu ĐO THẬT cho ra ĐÚNG 28 bản ghi, 0 dòng rác")
    void banMauThatChoDung28BanGhi() {
        TelemetryBatch me = Bhh40Parser.boc(mau);

        assertThat(me.soDo())
                .as("28 mã trong response đo lúc 10:24 ngày 01/09/2026 — con số này là phép đo, "
                        + "không phải một hằng số đẹp")
                .hasSize(28);
        assertThat(me.soDongRac())
                .as("⚠ Thẻ <br> cuối cùng sinh ra một phần tử RỖNG. Nếu nó bị tính là rác thì bộ đếm "
                        + "nhảy lên 1 ở MỌI lượt gọi thành công — và một bộ đếm luôn khác 0 là một bộ "
                        + "đếm không ai đọc nữa.")
                .isZero();
        assertThat(me.soDongTrung()).isZero();
        assertThat(me.nguonBaoHong()).isFalse();
    }

    @Test
    @DisplayName("⭐ Quy tắc 3: trang HTML ở đuôi bị cắt — không dòng nào của nó lọt vào bộ đếm rác")
    void catTrangHtmlODuoi() {
        assertThat(mau).contains("<!DOCTYPE").contains("__VIEWSTATE").contains("</html>");

        TelemetryBatch me = Bhh40Parser.boc(mau);

        // ⚠ Vế PHÂN BIỆT (luật 9): nếu không cắt, trang HTML có >10 dòng và tất cả đều là rác. Khẳng
        //   định soDongRac == 0 ở trên một mình có thể xanh vì lý do khác; con số dưới đây nói rằng
        //   phần bị cắt THẬT SỰ có nội dung đáng kể.
        assertThat(mau.substring(mau.indexOf("<!DOCTYPE")).lines().count())
                .as("phần bị cắt phải đủ lớn để việc cắt là một khẳng định có ý nghĩa")
                .isGreaterThan(5);
        assertThat(me.soDongRac()).isZero();
    }

    @Test
    @DisplayName("Quy tắc 3: <!doctype chữ thường cũng bị cắt — HTML không phân biệt hoa thường")
    void catCaDoctypeChuThuong() {
        String than = "F01527;01/09/2026;10:20;value=231;<br>\n<!doctype html><html><body>x</body></html>";

        TelemetryBatch me = Bhh40Parser.boc(than);

        assertThat(me.soDo()).hasSize(1);
        assertThat(me.soDongRac()).isZero();
    }

    @Test
    @DisplayName("⚠ Quy tắc 3: nhận cả <BR>, <br/>, <br /> — một lượt nâng cấp phía nguồn không được làm mất cả mẻ")
    void nhanMoiBienTheCuaTheNgatDong() {
        String than = "F01527;01/09/2026;10:20;value=231;<BR>"
                + "F01519;01/09/2026;10:20;value=192;<br/>"
                + "F01532;01/09/2026;10:20;value=439;<br />"
                + "F01652;01/09/2026;10:20;value=493;<br>";

        TelemetryBatch me = Bhh40Parser.boc(than);

        assertThat(me.soDo())
                .as("⛔ Thắt chặt thành đúng '<br>' là mất TRỌN một response vào ngày nguồn đổi cách "
                        + "sinh thẻ — mà số đo mất là mất vĩnh viễn, nguồn không có API lịch sử")
                .hasSize(4);
        assertThat(me.soDongRac()).isZero();
    }

    // ==== Quy tắc 4 — regex, dòng rác =========================================

    @Test
    @DisplayName("⭐⭐ Quy tắc 4: MỘT dòng rác KHÔNG làm hỏng cả mẻ — 3 dòng tốt vẫn về, rác đếm riêng")
    void dongRacKhongLamHongCaMe() {
        String than = "F01527;01/09/2026;10:20;value=231;<br>"
                + "rác hoàn toàn không theo định dạng nào<br>"
                + "F01519;01/09/2026;10:20;value=192;<br>"
                + "F01532;01/09/2026;10:20;value=439<br>" // thiếu dấu ';' cuối
                + "F01652;01/09/2026;10:20;value=493;<br>";

        TelemetryBatch me = Bhh40Parser.boc(than);

        assertThat(me.soDo())
                .as("bỏ cả mẻ vì một ký tự lạ là vứt 3 số đo tốt để phản ứng với 2 số đo xấu")
                .hasSize(3);
        assertThat(me.soDongRac()).isEqualTo(2);
    }

    @Test
    @DisplayName("⭐ Quy tắc 4: dòng KHỚP regex nhưng mốc không tồn tại (32/13) vẫn là rác, ⛔ không ném")
    void mocThoiGianKhongTonTaiLaRacChuKhongPhaiNgoaiLe() {
        String than = "F01527;32/13/2026;10:20;value=231;<br>"
                + "F01519;01/09/2026;25:70;value=192;<br>"
                + "F01532;29/02/2026;10:20;value=439;<br>" // 2026 không nhuận
                + "F01652;01/09/2026;10:20;value=493;<br>";

        TelemetryBatch me = Bhh40Parser.boc(than);

        assertThat(me.soDongRac())
                .as("⚠ 29/02 của một năm không nhuận là bẫy đắt nhất: ResolverStyle mặc định (SMART) "
                        + "IM LẶNG nắn nó về 28/02, và một mốc bịa khi ấy đi thẳng vào khoá chống trùng "
                        + "của hydro_readings — trông hợp lệ mãi mãi")
                .isEqualTo(3);
        assertThat(me.soDo()).hasSize(1);
    }

    @Test
    @DisplayName("⛔ Quy tắc 4: regex GIỮ ^[A-Z]\\d+ của spec — mã ngoài dạng F##### vẫn phải bóc được")
    void khongThatRegexThanhFNamChuSo() {
        String than = "F01527;01/09/2026;10:20;value=231;<br>"
                + "M0152;01/09/2026;10:20;value=100;<br>" // chữ cái khác, ít chữ số hơn
                + "F0152700;01/09/2026;10:20;value=101;<br>" // nhiều chữ số hơn
                + "AB12;01/09/2026;10:20;value=102;<br>"; // HAI chữ cái — spec chỉ cho MỘT

        TelemetryBatch me = Bhh40Parser.boc(than);

        assertThat(me.soDo())
                .extracting(TelemetryReading::apiCode)
                .as("28 mã hôm nay đều F + 5 chữ số, nhưng đó là MỘT LƯỢT ĐO, không phải cam kết của "
                        + "nguồn. Ràng buộc ^F[0-9]{5}$ đứng ở cột stations.api_code — chỗ KHAI BÁO "
                        + "được phép nghiêm khắc, chỗ NHẬN dữ liệu thì không.")
                .containsExactly("F01527", "M0152", "F0152700");
        assertThat(me.soDongRac())
                .as("⚠ Vế PHÂN BIỆT (luật 9): regex của spec là ^([A-Z]\\d+) — ĐÚNG MỘT chữ cái. Không "
                        + "có khẳng định này thì bài kiểm trên vẫn xanh với một regex nới hẳn thành "
                        + "'nhận mọi thứ', tức là nó không nói được ranh giới nằm ở đâu.")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Quy tắc 4: nhận giá trị âm và giá trị thập phân — spec khai vậy, dấu ',' đọc như dấu '.'")
    void nhanSoAmVaSoThapPhan() {
        String than = "F01527;01/09/2026;10:20;value=-15;<br>" + "F01519;01/09/2026;10:20;value=4,93;<br>"
                + "F01532;01/09/2026;10:20;value=4.93;<br>";

        List<TelemetryReading> soDo = Bhh40Parser.boc(than).soDo();

        assertThat(soDo).hasSize(3);
        assertThat(soDo.get(0).giaTriTho()).isEqualByComparingTo("-15");
        assertThat(soDo.get(1).giaTriTho())
                .as("⚠ Đọc '4,93' theo locale Mỹ cho ra 493 — sai 100 lần. Quy đổi bằng thay ký tự "
                        + "rồi giao cho BigDecimal, ⛔ không dùng NumberFormat phụ thuộc locale máy chủ.")
                .isEqualByComparingTo("4.93");
        assertThat(soDo.get(2).giaTriTho()).isEqualByComparingTo("4.93");
    }

    // ==== Quy tắc 2 — not.working =============================================

    @Test
    @DisplayName("⭐⭐ Quy tắc 2: not.working ⇒ 0 bản ghi, cờ nguồn hỏng bật — ⛔ không ghi reading nào")
    void notWorkingKhongChoRaBanGhiNao() {
        TelemetryBatch me = Bhh40Parser.boc("not.working");

        assertThat(me.nguonBaoHong()).isTrue();
        assertThat(me.soDo()).isEmpty();
    }

    @Test
    @DisplayName("⚠ Quy tắc 2 thắng: body vừa có not.working VỪA có dòng số đo ⇒ vẫn KHÔNG lấy dòng nào")
    void notWorkingThangCaKhiCoDongSoDo() {
        String than = "F01527;01/09/2026;10:20;value=231;<br>not.working";

        TelemetryBatch me = Bhh40Parser.boc(than);

        // Vế này phân biệt hai cài đặt: "kiểm not.working TRƯỚC khi tách dòng" và "tách dòng rồi mới
        // kiểm". Bản thứ hai trả về 1 số đo — một mẻ vừa 'nguồn hỏng' vừa có dữ liệu, và không ai
        // biết phải tin nửa nào.
        assertThat(me.nguonBaoHong()).isTrue();
        assertThat(me.soDo()).isEmpty();
    }

    @Test
    @DisplayName("Quy tắc 2: nhận diện không phân biệt hoa thường, và một chỗ nhận biết DUY NHẤT")
    void nhanDienNguonHongKhongPhanBietHoaThuong() {
        assertThat(Bhh40Parser.nguonBaoHong("NOT.WORKING")).isTrue();
        assertThat(Bhh40Parser.nguonBaoHong("<html>Not.Working</html>")).isTrue();
        assertThat(Bhh40Parser.nguonBaoHong(null)).isFalse();
        assertThat(Bhh40Parser.nguonBaoHong(mau)).isFalse();
    }

    // ==== Quy tắc 6 — múi giờ =================================================

    @Test
    @DisplayName("⭐⭐ Quy tắc 6: '01/09/2026 10:20' giờ VN = 03:20 UTC — lệch 7 tiếng, ⛔ không lưu giờ địa phương")
    void mocThoiGianDoiVeUtc() {
        TelemetryBatch me = Bhh40Parser.boc(mau);

        Instant mongDoi = java.time.LocalDateTime.of(2026, 9, 1, 3, 20).toInstant(ZoneOffset.UTC);
        assertThat(me.soDo())
                .extracting(TelemetryReading::measuredAt)
                .as("⚠ Lưu 10:20 như thể là UTC thì mọi biểu đồ lệch 7 tiếng, và lệch ĐỀU nên không "
                        + "ai thấy — cho tới lúc đối chiếu với một báo cáo giấy")
                .containsOnly(mongDoi);
    }

    @Test
    @DisplayName("⚠ Quy tắc 6: cả 28 dòng cùng MỘT mốc — đó là mốc KHUNG của nguồn, ⛔ không phải giờ ta gọi")
    void caMeCungMotMocKhung() {
        List<Instant> moc = Bhh40Parser.boc(mau).soDo().stream()
                .map(TelemetryReading::measuredAt)
                .distinct()
                .toList();

        assertThat(moc)
                .as("ta gọi lúc 10:24, nguồn trả mốc 10:20 — lấy giờ gọi làm mốc đo là ghi sai thời "
                        + "điểm cho MỌI bản ghi")
                .hasSize(1);
    }

    // ==== Quy tắc 7 — đơn vị ==================================================

    @Test
    @DisplayName("⭐⭐ Quy tắc 7: 493 cm ⇒ 4.930 m — giá trị THẬT của F01652, BigDecimal scale 3")
    void quyDoiCmSangMetTheoGiaTriThat() {
        List<TelemetryReading> soDo = Bhh40Parser.boc(mau).soDo();

        assertThat(giaTriCua(soDo, "F01652")).isEqualTo(new BigDecimal("4.930"));
        assertThat(giaTriCua(soDo, "F01532")).isEqualTo(new BigDecimal("4.390"));
        assertThat(giaTriCua(soDo, "F01705")).isEqualTo(new BigDecimal("3.620"));
        assertThat(giaTriCua(soDo, "F01707")).isEqualTo(new BigDecimal("2.730"));
        assertThat(giaTriCua(soDo, "F01672")).isEqualTo(new BigDecimal("1.570"));
        assertThat(giaTriCua(soDo, "F01700")).isEqualTo(new BigDecimal("0.790"));
    }

    // ==== Quy tắc 8 — chống trùng =============================================

    @Test
    @DisplayName("⭐ Quy tắc 8: cùng (mã, mốc) hai lần trong MỘT response ⇒ giữ bản đầu, đếm riêng")
    void bocTrungTrongCungMotResponse() {
        String than = "F01527;01/09/2026;10:20;value=231;<br>"
                + "F01527;01/09/2026;10:20;value=999;<br>" // trùng khoá, khác giá trị
                + "F01527;01/09/2026;10:30;value=232;<br>"; // khác mốc ⇒ KHÔNG trùng

        TelemetryBatch me = Bhh40Parser.boc(than);

        assertThat(me.soDo()).hasSize(2);
        assertThat(me.soDongTrung()).isEqualTo(1);
        assertThat(me.soDo().get(0).giaTriTho())
                .as("giữ bản ĐẦU: nguồn gửi hai giá trị cho cùng một mốc là nguồn tự mâu thuẫn, và "
                        + "'bản sau thắng' làm kết quả phụ thuộc thứ tự — thứ không ai điều khiển")
                .isEqualByComparingTo("231");
    }

    // ==== Vế ngược của quy tắc 5 ==============================================

    @Test
    @DisplayName("⛔ Quy tắc 5 KHÔNG thuộc parser: mã lạ vẫn được trả về đủ, việc lọc là của poller")
    void parserKhongTuLocMaLa() {
        List<String> ma = Bhh40Parser.boc(mau).soDo().stream()
                .map(TelemetryReading::apiCode)
                .toList();

        assertThat(ma)
                .as("⚠ 9 mã trong bản mẫu CHƯA được khai ở stations (G8). Parser mà tự lọc chúng là "
                        + "vứt số đo thật đi — nguồn không có API lịch sử, nên mất là vĩnh viễn. "
                        + "Chúng phải về tới poller để vào hydro_unmapped_readings.")
                .contains("F01535", "F01613", "F01659", "F01696", "F01700", "F01706", "F01811", "F01830", "F01863");
    }

    // ==== Biên ================================================================

    @Test
    @DisplayName("Thân null / rỗng / chỉ có HTML ⇒ mẻ rỗng, ⛔ không ném, ⛔ không tính rác")
    void thanRongChoMeRong() {
        for (String than : new String[] {null, "", "   ", "<!DOCTYPE html><html></html>"}) {
            TelemetryBatch me = Bhh40Parser.boc(than);
            assertThat(me.soDo()).isEmpty();
            assertThat(me.soDongRac()).isZero();
            assertThat(me.nguonBaoHong()).isFalse();
        }
    }

    private static BigDecimal giaTriCua(List<TelemetryReading> soDo, String apiCode) {
        return soDo.stream()
                .filter(r -> r.apiCode().equals(apiCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không thấy mã " + apiCode + " trong bản mẫu đo thật"))
                .giaTri();
    }
}
