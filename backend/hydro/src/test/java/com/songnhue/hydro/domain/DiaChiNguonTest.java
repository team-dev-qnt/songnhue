package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SSRF — địa chỉ nguồn nào được phép mở kết nối tới (T30.12, {@code conventions.md} §4.6 A10).
 *
 * <p>⚠ Bộ chặn này còn được đo <b>trên dây thật</b> ở {@code Bhh40AdapterHttpTest
 * .mayNoiBoBiChanKhiCongTacTat}: cùng một máy chủ {@code 127.0.0.1}, chỉ khác cờ, và nhánh mặc định
 * chặn <b>trước khi mở socket</b>. Bài kiểm ở đây phủ các dạng địa chỉ; bài kia phủ chỗ nó được gọi.
 */
class DiaChiNguonTest {

    private static final String DUONG_DAN = "api/getmn.aspx?key=x;";

    @Test
    @DisplayName("⭐ Địa chỉ nguồn THẬT của Công ty đi qua được — bộ chặn không được chặn chính thứ nó phục vụ")
    void diaChiThatCuaCongTyDiQuaDuoc() {
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/", DUONG_DAN, false))
                .as("⚠ Nguồn của Công ty chỉ có http:// — ép https ở đây là chặn toàn bộ MOD-03")
                .hasToString("http://songnhue.bhh40.net/api/getmn.aspx?key=x;");
    }

    @Test
    @DisplayName("⚠ Base URL thiếu / thừa dấu '/' cho ra CÙNG một URL — nối chuỗi thì không")
    void thieuThuaDauGachChoCungKetQua() {
        String mongDoi = "http://songnhue.bhh40.net/api/getmn.aspx?key=x;";

        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net", DUONG_DAN, false))
                .hasToString(mongDoi);
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/", DUONG_DAN, false))
                .hasToString(mongDoi);
    }

    /**
     * ⛔⛔⛔ <b>T52.1 — sự cố THẬT, đo trên staging 10/09/2026.</b>
     *
     * <pre>
     *   base_url = http://songnhue.bhh40.net/api/getmn.aspx   (mã số ĐÃ đặt đúng chỗ, mã hoá đúng)
     *   consecutive_failures = 3576 · last_success_at = NULL · hydro_readings = 0
     *   last_failure_reason  = "Nguồn trả HTTP 404"
     * </pre>
     *
     * <p>Đối chứng trên <b>nguồn thật</b> cùng ngày — hai URL, hai trạng thái phân biệt được:
     *
     * <pre>
     *   GET /api/getmn.aspx      -> HTTP 200, thân "not.working"   (đường ĐÚNG, thiếu mã số)
     *   GET /api/api/getmn.aspx  -> HTTP 404                       (đường LẶP, poller đang gọi)
     * </pre>
     *
     * <p>⚠ Bài {@code thieuThuaDauGachChoCungKetQua} ngay trên mang tên <i>"thiếu / thừa dấu '/'"</i>
     * — một lời hứa rộng — nhưng dữ liệu của nó là <b>đúng hai ca vốn đã chạy</b>
     * ({@code host} và {@code host/}). Luật 7 + luật 28: cái xanh của nó đọc như một bảo đảm cho
     * một phạm vi nó ⛔ không soi.
     */
    @Test
    @DisplayName("⛔⛔ Base URL mang sẵn ĐƯỜNG DẪN endpoint ⇒ vẫn ra URL ĐÚNG, ⛔ không lặp thành /api/api/")
    void baseMangSanDuongDanEndpointThiKhongLap() {
        String dung = "http://songnhue.bhh40.net/api/getmn.aspx?key=x;";

        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/api/getmn.aspx", DUONG_DAN, false))
                .as("⛔ ĐÂY là giá trị staging đang mang — trước bản vá nó cho /api/api/getmn.aspx ⇒ 404")
                .hasToString(dung);
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/api/getmn.aspx/", DUONG_DAN, false))
                .hasToString(dung);
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/api", DUONG_DAN, false))
                .hasToString(dung);
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/api/", DUONG_DAN, false))
                .as("⚠ Trước bản vá ca này cũng cho /api/api/ — dấu '/' cuối ⛔ không cứu được")
                .hasToString(dung);
    }

    /**
     * ⛔⛔ Vế <b>IM LẶNG</b>, và nó nặng hơn vế 404.
     *
     * <p>{@code URI.resolve} bỏ đoạn cuối của base khi base ⛔ không kết thúc bằng {@code /}. Nên
     * một nguồn đặt trong thư mục con bị gọi <b>ở gốc máy chủ</b> — một đường dẫn có thật, trả 200,
     * ⛔ không một dòng lỗi nào. 404 còn nằm trong {@code last_failure_reason}; cái này thì ⛔ không.
     */
    @Test
    @DisplayName("⛔⛔ Base có thư mục con ⇒ GIỮ được thư mục ấy — trước bản vá nó bị NUỐT trong im lặng")
    void baseCoThuMucConThiKhongBiNuot() {
        String dung = "http://songnhue.bhh40.net/songnhue/api/getmn.aspx?key=x;";

        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/songnhue", DUONG_DAN, false))
                .as("⛔ Trước bản vá: http://songnhue.bhh40.net/api/getmn.aspx — mất hẳn '/songnhue'")
                .hasToString(dung);
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/songnhue/", DUONG_DAN, false))
                .hasToString(dung);
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/songnhue/api", DUONG_DAN, false))
                .as("Thư mục con + một nửa đường dẫn endpoint: cắt phần trùng, GIỮ thư mục con")
                .hasToString(dung);
    }

    /**
     * ⚠ Trạng thái <b>production</b> theo T50.1: {@code base_url} mang <b>cả</b> đường dẫn endpoint
     * <b>lẫn</b> một {@code ?key=} cũ đã lộ.
     *
     * <p>Bản vá T50.1 chặn giá trị ấy ở <i>đường ghi</i> ({@code HYD-2016}), nhưng nó ⛔ không dọn
     * hàng đã nằm sẵn trong CSDL — và {@code api_sources} còn <b>ba</b> đường vào khác ⛔ không đi
     * qua validator (seed · khôi phục từ sao lưu · một câu {@code UPDATE} tay lúc xử lý sự cố), đúng
     * ba đường javadoc của {@link DiaChiNguon} đã liệt kê.
     *
     * <p>⇒ Khẳng định ở đây là: mã số cũ trong {@code base_url} <b>bị THAY</b>, ⛔ không nối chồng.
     * Nếu nó nối chồng thì mã số đã lộ vẫn tiếp tục được gửi đi mỗi 2 phút.
     */
    @Test
    @DisplayName("⚠ base_url mang cả đường dẫn LẪN ?key= cũ ⇒ mã số cũ bị THAY, ⛔ không nối chồng")
    void maSoCuTrongBaseUrlBiThayChuKhongNoiChong() {
        assertThat(DiaChiNguon.kiemVaDung(
                        "http://songnhue.bhh40.net/api/getmn.aspx?key=MA_SO_CU_DA_LO;", DUONG_DAN, false))
                .as("⛔ Mã số cũ ⛔ không được sống sót — nó là giá trị đã lộ, phải bị mã số thật thay hẳn")
                .hasToString("http://songnhue.bhh40.net/api/getmn.aspx?key=x;");
    }

    /**
     * ⛔ Phép cắt so theo <b>ĐOẠN</b>, ⛔ không {@code endsWith} trên chuỗi (luật 2).
     *
     * <p>Thiếu vế này thì một bản cài đặt "gọn hơn" bằng {@code path.endsWith("api")} vẫn xanh ở cả
     * hai bài trên, trong khi nó <b>cắt hỏng</b> một địa chỉ đang chạy được. Đây là vế phân biệt —
     * ⛔ không có nó thì hai bài trên ⛔ không nói được cách cài đặt nào là đúng (luật 9).
     */
    @Test
    @DisplayName("⛔ '/xxxapi' kết thúc bằng 'api' theo CHUỖI nhưng ⛔ không theo ĐOẠN ⇒ ⛔ không bị cắt")
    void catTheoDoanChuKhongTheoChuoi() {
        assertThat(DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net/xxxapi", DUONG_DAN, false))
                .as("⛔ Cắt theo chuỗi sẽ cho /api/getmn.aspx và giết một nguồn hợp lệ")
                .hasToString("http://songnhue.bhh40.net/xxxapi/api/getmn.aspx?key=x;");
    }

    @Test
    @DisplayName("⭐⭐ Dải nội bộ bị chặn — 169.254.169.254 là endpoint metadata của gần như mọi nhà cung cấp")
    void daiNoiBoBiChan() {
        List<String> chan = List.of(
                "http://127.0.0.1:8080/",
                "http://localhost:8080/",
                "http://10.0.0.5/",
                "http://192.168.1.1/",
                "http://172.16.0.1/",
                "http://172.31.255.255/",
                "http://169.254.169.254/", // metadata đám mây
                "http://100.64.0.1/", // CGNAT, RFC 6598
                "http://0.0.0.0/",
                "http://metadata.google.internal/",
                "http://db.internal/",
                "http://may-chu.local/",
                "http://[::1]/",
                "http://[fd00::1]/");

        for (String url : chan) {
            assertThatThrownBy(() -> DiaChiNguon.kiemVaDung(url, DUONG_DAN, false))
                    .as("một nguồn trỏ %s biến poller thành công cụ gõ cửa mạng nội bộ", url)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(chan)
                .as("⚠ Khẳng định về SỐ LƯỢNG (luật 29): vòng lặp chạy trên danh sách RỖNG cũng xanh "
                        + "trọn vẹn — đó đúng là hình dạng luật 7")
                .hasSize(14);
    }

    @Test
    @DisplayName("⚠ 172.15 và 172.32 KHÔNG bị chặn — dải riêng chỉ là 172.16–172.31, chặn rộng là chặn nhầm")
    void bienCuaDai172ChinhXac() {
        assertThatCode(() -> DiaChiNguon.kiemVaDung("http://172.15.0.1/", DUONG_DAN, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> DiaChiNguon.kiemVaDung("http://172.32.0.1/", DUONG_DAN, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DiaChiNguon.kiemVaDung("http://172.16.0.1/", DUONG_DAN, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Scheme ngoài http/https bị chặn — file:/gopher: là ba đường kinh điển đọc tệp máy chủ")
    void schemeLaBiChan() {
        for (String url : List.of("file:///etc/passwd", "gopher://x/", "jar:file:///a.jar!/b", "ftp://x/")) {
            assertThatThrownBy(() -> DiaChiNguon.kiemVaDung(url, DUONG_DAN, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("http/https");
        }
    }

    @Test
    @DisplayName("userinfo bị chặn — dạng http://ai-do@host/ để đánh lừa người đọc về host thật")
    void userinfoBiChan() {
        assertThatThrownBy(() -> DiaChiNguon.kiemVaDung("http://songnhue.bhh40.net@evil.tld/", DUONG_DAN, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userinfo");
    }

    @Test
    @DisplayName("Rỗng / không phải URI / không có host ⇒ từ chối, ⛔ không im lặng đi tiếp")
    void diaChiKhongDungDuocThiTuChoi() {
        for (String url : new String[] {null, "", "   ", "khong phai uri", "http://"}) {
            assertThatThrownBy(() -> DiaChiNguon.kiemVaDung(url, DUONG_DAN, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("⭐ Công tắc BẬT: đúng những địa chỉ trên đi qua được — cửa nới có thật và hẹp đúng chỗ")
    void congTacBatThiMayNoiBoDiQuaDuoc() {
        assertThatCode(() -> DiaChiNguon.kiemVaDung("http://127.0.0.1:9999/", DUONG_DAN, true))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> DiaChiNguon.kiemVaDung("file:///etc/passwd", DUONG_DAN, true))
                .as("⛔ Công tắc chỉ nới TÊN MÁY. Nới cả scheme là biến một tiện ích phát triển thành "
                        + "một lỗ đọc tệp — và đó là thứ không ai đọc lại khi công tắc bật nhầm ở prod.")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
