package com.songnhue.hydro.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryReading;

/**
 * Nguồn <b>lượng mưa</b> đi qua đúng bộ bóc của nguồn mực nước — WS-87 · T87.3.
 *
 * <h2>⭐ Dữ liệu là bản ĐO THẬT (26/09/2026)</h2>
 *
 * <p>Nguồn trả <b>15</b> bản ghi qua {@code getluongmua.aspx}, định dạng dòng y hệt nguồn mực nước,
 * giá trị <b>thập phân</b> ({@code value=0.0}), mốc chung {@code 10:00} (nhịp 1 giờ). Năm mã dưới
 * đây là năm mã đầu tiên của response thật.
 *
 * <h2>⛔⛔ Hai kiểu hỏng mà lớp này canh — và CẢ HAI đều IM LẶNG</h2>
 *
 * <p>Định dạng dòng của hai nguồn <b>giống hệt nhau</b>, nên ⛔ có gì trong thân phản hồi nói đơn vị
 * là gì. Hai lối làm ẩu vì thế ⛔ sinh ra một lượt đỏ nào:
 *
 * <ol>
 *   <li><b>Dùng lại hằng {@code cm}</b> ⇒ số đo mưa mang nhãn {@code cm} xuống
 *       {@code hydro_unmapped_readings}. ⛔ Triệu chứng nào hôm nay; ngày Công ty khai 15 mã, job
 *       nâng cấp chia 100 ⇒ <b>12,5 mm thành 0,125 m</b>.
 *   <li><b>Truyền một đơn vị lạ</b> ⇒ {@code TelemetryReading} ném, mà {@code bocMotDong}
 *       <b>BẮT</b> {@code IllegalArgumentException} rồi đếm dòng vào {@code soDongRac} ⇒ cả 15 dòng
 *       thành rác ⇒ mẻ rỗng ⇒ {@code TelemetryIngestService} kết luận
 *       <i>"nhiều khả năng nguồn đổi định dạng"</i> — hệ <b>đổ lỗi cho nguồn</b> trong khi lỗi là
 *       một hằng số của ta, và quy tắc 18 nói số liệu mất là mất vĩnh viễn.
 * </ol>
 */
class Bhh40ParserLuongMuaTest {

    /** Năm bản ghi đầu của response thật 26/09/2026, giữ nguyên định dạng (kể cả {@code <br>} cuối). */
    private static final String THAN_THAT = "F01930;26/09/2026;10:00;value=0.0;<br>"
            + "F01929;26/09/2026;10:00;value=0.0;<br>"
            + "F01927;26/09/2026;10:00;value=0.0;<br>"
            + "F01920;26/09/2026;10:00;value=0.0;<br>"
            + "F01918;26/09/2026;10:00;value=0.0;<br>";

    @Test
    @DisplayName("⭐⭐ Thân THẬT của getluongmua.aspx bóc ra đủ bản ghi, 0 rác — số thập phân ⛔ làm hỏng mẻ")
    void thanThatBocDuocHet() {
        TelemetryBatch me = Bhh40Parser.boc(THAN_THAT, TelemetryReading.DON_VI_MM);

        assertThat(me.soDo()).hasSize(5);
        assertThat(me.soDongRac())
                .as("⛔ Một con số > 0 ở đây nghĩa là regex quy tắc 4 ⛔ nhận số thập phân — và khi ấy "
                        + "MỌI bản ghi mưa đều là rác, im lặng")
                .isZero();
        assertThat(me.nguonBaoHong()).isFalse();
    }

    @Test
    @DisplayName("⭐⭐ Số đo mưa mang đơn vị mm và giữ NGUYÊN giá trị — ⛔ chia 100")
    void soDoMuaMangDonViMm() {
        TelemetryBatch me = Bhh40Parser.boc("F01930;26/09/2026;10:00;value=12.5;<br>", TelemetryReading.DON_VI_MM);

        TelemetryReading r = me.soDo().get(0);
        assertThat(r.donViTho()).isEqualTo("mm");
        assertThat(r.giaTriTho())
                .as("⚠ Giá trị THÔ giữ nguyên văn nguồn — đây là thứ đi xuống hydro_unmapped_readings")
                .isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(r.giaTri())
                .as("⛔⛔ 0.125 là con số của một lượt chia 100 nhầm, và nó hợp lệ ở CẢ HAI loại chỉ "
                        + "số nên ⛔ có gì báo")
                .isEqualByComparingTo(new BigDecimal("12.5"));
    }

    /**
     * ⭐⭐ <b>Vế phân biệt của cả lớp này</b> — cùng một thân, hai đơn vị, hai kết quả.
     *
     * <p>Thiếu bài này thì mọi khẳng định ở trên vẫn xanh trong trạng thái hỏng số 1 (adapter khai
     * nhầm {@code cm}), vì thân phản hồi ⛔ mang thông tin nào về đơn vị — luật 9.
     */
    @Test
    @DisplayName("⭐⭐ CÙNG một thân, khai cm thay vì mm ⇒ giá trị lệch 100 lần — đơn vị đến từ ADAPTER")
    void cungThanKhaiSaiDonViThiLech100Lan() {
        String than = "F01930;26/09/2026;10:00;value=12.5;<br>";

        BigDecimal theoMm =
                Bhh40Parser.boc(than, TelemetryReading.DON_VI_MM).soDo().get(0).giaTri();
        BigDecimal theoCm =
                Bhh40Parser.boc(than, TelemetryReading.DON_VI_CM).soDo().get(0).giaTri();

        assertThat(theoMm).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(theoCm)
                .as("⛔⛔ Đây là cái giá của việc khai sai đơn vị ở adapter — và ⛔ có lượt đỏ nào "
                        + "báo, vì thân phản hồi của hai nguồn giống hệt nhau")
                .isEqualByComparingTo(new BigDecimal("0.125"));
    }

    /**
     * ⛔⛔ Đơn vị lạ phải <b>NÉM</b>, ⛔ được biến thành {@code soDongRac}.
     *
     * <p>Đây là bài canh đúng cơ chế đã mô tả ở javadoc lớp: {@code bocMotDong} bắt
     * {@code IllegalArgumentException} (đúng — cho dòng rác thật), nên nếu phép kiểm đơn vị nằm
     * <i>bên trong</i> vòng lặp thì một hằng số gõ sai biến cả mẻ thành rác trong im lặng. Phép kiểm
     * vì thế phải đứng <b>trước</b> vòng lặp.
     */
    @Test
    @DisplayName("⛔⛔ Đơn vị lạ NÉM ở đầu boc() — ⛔ được lặng lẽ thành 15 dòng rác")
    void donViLaNemChuKhongThanhRac() {
        assertThatThrownBy(() -> Bhh40Parser.boc(THAN_THAT, "inch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("⛔ bóc một mẻ nào bằng đơn vị lạ");
    }

    @Test
    @DisplayName("⚠ Mốc nguồn giữ NGUYÊN VĂN — quy ước khung giờ chưa chốt (xem Bhh40LuongMuaAdapter)")
    void mocGiuNguyenVan() {
        TelemetryReading r =
                Bhh40Parser.boc(THAN_THAT, TelemetryReading.DON_VI_MM).soDo().get(0);

        // 10:00 giờ VN (UTC+7) = 03:00Z. ⛔ Dịch một giờ nào ở tầng parse: ba cách đọc quy ước bucket
        // vẫn còn đứng được, và chọn nhầm là lệch một giờ trên MỌI bản ghi mưa. Giữ nguyên văn thì
        // quyết định vẫn lấy lại được từ hydro_unmapped_readings.
        assertThat(r.measuredAt()).isEqualTo(Instant.parse("2026-09-26T03:00:00Z"));
    }
}
