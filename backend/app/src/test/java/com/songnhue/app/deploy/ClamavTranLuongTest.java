package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Trần của clamd phải phủ trần tải lên của ứng dụng — T61.4.</b>
 *
 * <p>Đo 14/09/2026 trên {@code clamav/clamav:1.4.6-debian}: {@code StreamMaxLength} mặc định
 * <b>100M</b> trong khi {@code spring.servlet.multipart.max-file-size} là <b>120MB</b>. ZIP SẠCH
 * 115 MB nhận {@code INSTREAM size limit exceeded. ERROR}; ở bản {@code VirusScanHandler} cũ chuỗi ấy
 * đọc thành <i>"nhiễm mã độc"</i> ⇒ tệp sạch bị cách ly. Nâng lên 130M thì ZIP 115 MB chứa EICAR ra
 * {@code FOUND}.
 *
 * <p>Hai con số nằm ở hai tệp, người sửa một bên ⛔ có lý do nhìn bên kia (luật 14) — bài này nhớ hộ.
 *
 * <p>⚠ Giới hạn (luật 28): so với mặc định trong {@code application.yml} và ba tệp env MẪU. {@code
 * .env} thật trên máy chủ ⛔ nằm trong kho; ai đặt {@code UPLOAD_MAX_FILE_MB} ở đó phải tự đối chiếu.
 */
class ClamavTranLuongTest {

    private static final Pattern MAC_DINH_TAI_LEN =
            Pattern.compile("max-file-size:\\s*\\$\\{UPLOAD_MAX_FILE_MB:(\\d+)}MB");
    private static final Pattern BIEN_ENV = Pattern.compile("(?m)^\\s*UPLOAD_MAX_FILE_MB\\s*=\\s*(\\d+)\\s*$");

    @Test
    @DisplayName("⛔⛔ StreamMaxLength và MaxFileSize của clamd ≥ trần tải lên — tệp hợp lệ ⛔ bị cắt")
    void tranClamdPhuTranTaiLen() {
        String compose = doc("deploy/compose.prod.yml");
        long taiLen = tranTaiLenMb();

        long luong = giaTriMb(compose, "CLAMD_CONF_StreamMaxLength")
                .orElseGet(() ->
                        fail("compose.prod.yml ⛔ khai CLAMD_CONF_StreamMaxLength — mặc định 100M < %dMB", taiLen));
        long tep = giaTriMb(compose, "CLAMD_CONF_MaxFileSize")
                .orElseGet(() -> fail("compose.prod.yml ⛔ khai CLAMD_CONF_MaxFileSize — mặc định 100M < %dMB", taiLen));

        assertThat(luong)
                .as("StreamMaxLength %dM < trần tải lên %dMB ⇒ tệp sạch lớn nhận `size limit exceeded`", luong, taiLen)
                .isGreaterThanOrEqualTo(taiLen);
        assertThat(tep)
                .as("MaxFileSize %dM < trần tải lên %dMB ⇒ tệp lớn ⛔ được quét hết", tep, taiLen)
                .isGreaterThanOrEqualTo(taiLen);
    }

    @Test
    @DisplayName("⭐ Chống tập rỗng: đọc được trần tải lên ≥ 1MB từ application.yml")
    void docDuocTranTaiLen() {
        assertThat(tranTaiLenMb()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Tự kiểm: bộ đọc hiểu đơn vị và bỏ qua dòng chú thích")
    void tuKiem() {
        assertThat(giaTriMb("      CLAMD_CONF_StreamMaxLength: 130M\n", "CLAMD_CONF_StreamMaxLength"))
                .hasValue(130);
        assertThat(giaTriMb("      CLAMD_CONF_StreamMaxLength: 1G\n", "CLAMD_CONF_StreamMaxLength"))
                .hasValue(1024);
        assertThat(giaTriMb("      # CLAMD_CONF_StreamMaxLength: 900M\n", "CLAMD_CONF_StreamMaxLength"))
                .as("chú thích ⛔ phải cấu hình")
                .isEmpty();
    }

    /** Lớn nhất giữa mặc định của ứng dụng và mọi giá trị ghi đè trong tệp env mẫu. */
    private static long tranTaiLenMb() {
        Matcher m = MAC_DINH_TAI_LEN.matcher(doc("backend/app/src/main/resources/application.yml"));
        long lonNhat =
                m.find() ? Long.parseLong(m.group(1)) : fail("⛔ tìm thấy `max-file-size: ${UPLOAD_MAX_FILE_MB:…}MB`");
        for (String tep : new String[] {"deploy/env/prod.env.example", "deploy/env/staging.env.example"}) {
            Matcher e = BIEN_ENV.matcher(doc(tep));
            while (e.find()) {
                lonNhat = Math.max(lonNhat, Long.parseLong(e.group(1)));
            }
        }
        return lonNhat;
    }

    static OptionalLong giaTriMb(String yml, String khoa) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(khoa) + ":\\s*\"?(\\d+)([KMG])\"?\\s*$")
                .matcher(yml);
        if (!m.find()) {
            return OptionalLong.empty();
        }
        long so = Long.parseLong(m.group(1));
        return OptionalLong.of(
                switch (m.group(2)) {
                    case "K" -> Math.max(1, so / 1024);
                    case "G" -> so * 1024;
                    default -> so;
                });
    }

    private static String doc(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return fail("Không đọc được %s", ungVien);
                }
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s", duongDanTuongDoi);
    }
}
