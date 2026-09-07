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
