package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>nginx phải chặn mọi đường dẫn có đoạn bắt đầu bằng dấu chấm — T61.40</b> (ASVS 4.3.2 · 12.5.1).
 *
 * <p>Hôm nay ⛔ có thư mục tĩnh nào lộ {@code /.git/config} hay {@code /.env} ra ngoài — luật này là
 * để một lượt cấu hình SAU (thêm một {@code root}, một thư mục tải lên phục vụ trực tiếp) ⛔ mở được
 * cửa ấy trong im lặng.
 *
 * <p>⚠ Đối chứng bắt buộc: {@code /.well-known/acme-challenge/} <b>vẫn phải đi qua</b> — nó cũng bắt
 * đầu bằng dấu chấm, và chặn nhầm nó là <b>chứng chỉ TLS hết hạn ⛔ gia hạn được</b>. nginx xét tiền
 * tố {@code ^~} trước mọi regex, nên thứ tự trong tệp ⛔ quyết định; bài này canh cả hai vế tồn tại.
 */
class NginxChanTepAnTest {

    private static final Path TEP = timTuGocKho("deploy/nginx/templates/default.conf.template");

    @Test
    @DisplayName("⛔ Mỗi khối server phục vụ nội dung đều có luật chặn tệp ẩn")
    void moiKhoiServerDeuChanTepAn() throws Exception {
        String cauHinh = Files.readString(TEP);

        long soKhoiCoLuat =
                cauHinh.lines().filter(d -> d.contains("location ~ /\\.")).count();
        assertThat(soKhoiCoLuat)
                .as("bốn khối: catch-all, PUBLIC_DOMAIN, ADMIN_DOMAIN, FILES_DOMAIN — thiếu một là một cửa mở")
                .isEqualTo(4);
        assertThat(cauHinh).contains("deny all;");
    }

    @Test
    @DisplayName("⭐ Đối chứng: lối ACME vẫn còn — chặn nhầm nó là chứng chỉ TLS ⛔ gia hạn được")
    void loiAcmeVanCon() throws Exception {
        assertThat(Files.readString(TEP))
                .as("⛔ luật chặn tệp ẩn ⛔ được nuốt mất lối gia hạn chứng chỉ")
                .contains("location ^~ /.well-known/acme-challenge/");
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new AssertionError("⛔ tìm thấy " + duongDanTuongDoi);
    }
}
