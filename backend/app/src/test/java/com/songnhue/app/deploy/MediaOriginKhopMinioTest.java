package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>{@code MEDIA_ORIGIN} phải bằng {@code MINIO_ENDPOINT}</b> — T84.17.
 *
 * <h2>Vì sao cần một bộ canh cho hai dòng YAML</h2>
 *
 * {@code GET /api/v1/public/videos/&#123;id&#125;} trả <b>302</b> sang gốc kho đối tượng, còn CSP
 * của cổng khai {@code media-src 'self' $MEDIA_ORIGIN}. Hai giá trị lệch nhau ⇒ <b>mọi video bị
 * chặn Ở TRÌNH DUYỆT</b>: máy chủ trả 302 đúng, MinIO phục vụ đúng, ⛔ log nào có gì — lỗi chỉ hiện
 * trong console của người đọc, nơi ⛔ cổng kiểm nào của kho nhìn thấy (T46.7 · §10.61).
 *
 * <p>Đây là luật 14 ở dạng thuần: hai nơi con người phải nhớ cùng một giá trị, ⇒ một phép kiểm nhớ hộ.
 *
 * <h2>⚠ So BIỂU THỨC, ⛔ so giá trị đã giải</h2>
 *
 * Cả hai đều là {@code https://$&#123;FILES_DOMAIN&#125;}. So biểu thức thì bộ canh chạy được ở mọi
 * môi trường mà ⛔ cần biết {@code FILES_DOMAIN} là gì — và nó vẫn bắt được đúng lỗi cần bắt: ai đó
 * gõ một tên miền khác vào một trong hai chỗ.
 */
class MediaOriginKhopMinioTest {

    private static final Pattern MINIO = Pattern.compile("MINIO_ENDPOINT:\\s*(\\S+)");
    private static final Pattern MEDIA = Pattern.compile("MEDIA_ORIGIN:\\s*(\\S+)");

    private static String compose;

    @BeforeAll
    static void doc() throws Exception {
        Path goc = Path.of("").toAbsolutePath();
        while (goc != null && !Files.isDirectory(goc.resolve("deploy"))) {
            goc = goc.getParent();
        }
        assertThat(goc).as("⛔ tìm thấy gốc kho").isNotNull();
        compose = Files.readString(goc.resolve("deploy/compose.prod.yml"));
    }

    @Test
    @DisplayName("⚠ chống tập rỗng: đọc được cả hai khai báo trong compose.prod.yml")
    void docDuocCaHai() {
        // ⛔ Thiếu vế này thì một lượt đổi tên biến làm cả hai regex trả 0 kết quả, và bài chính
        //    bên dưới so hai tập RỖNG với nhau rồi xanh (luật 7).
        assertThat(giaTri(MINIO)).as("`MINIO_ENDPOINT` — service `app`").hasSize(1);
        assertThat(giaTri(MEDIA))
                .as("`MEDIA_ORIGIN` phải khai ở CẢ HAI service FE (public-web và admin-app)")
                .hasSize(2);
    }

    @Test
    @DisplayName("⭐⭐ MEDIA_ORIGIN của cả hai service FE = MINIO_ENDPOINT của app")
    void haiGiaTriPhaiKhop() {
        String minio = giaTri(MINIO).get(0);
        assertThat(giaTri(MEDIA))
                .as("lệch nhau ⇒ mọi video bị CSP chặn ở trình duyệt, ⛔ một dòng log nào ở máy chủ")
                .allMatch(minio::equals, "bằng MINIO_ENDPOINT (" + minio + ")");
    }

    @Test
    @DisplayName("⛔ Dockerfile admin PHẢI có bước envsubst — snippet CSP là COPY TĨNH (luật 17)")
    void dockerfileAdminCoBuocThe() throws Exception {
        Path goc = Path.of("").toAbsolutePath();
        while (goc != null && !Files.isDirectory(goc.resolve("deploy"))) {
            goc = goc.getParent();
        }
        String df = Files.readString(goc.resolve("deploy/docker/admin-app.Dockerfile"));

        assertThat(df).as("CSP của admin phải khai `media-src`").contains("media-src 'self' ${MEDIA_ORIGIN}");
        // ⛔⛔ Entrypoint của image nginx chỉ envsubst `/etc/nginx/templates/*.template`. Thiếu
        //    script này thì chuỗi `${MEDIA_ORIGIN}` đi NGUYÊN VĂN vào header: nginx lên bình thường,
        //    trang chạy bình thường, và mọi `<video>` trong khung soạn thảo bị chặn — VĨNH VIỄN,
        //    ⛔ lệnh nào báo sai. Cùng hình dạng §10.56 (collation) và §10.81 (cron TLS).
        assertThat(df).contains("/docker-entrypoint.d/15-csp-media-origin.sh");
        assertThat(df)
                .as("`envsubst` phải có DANH SÁCH BIẾN — gọi trần thì nó nuốt luôn $uri/$host của nginx")
                .contains("envsubst '${MEDIA_ORIGIN}'");
    }

    private static java.util.List<String> giaTri(Pattern p) {
        Matcher m = p.matcher(compose);
        java.util.List<String> ra = new java.util.ArrayList<>();
        while (m.find()) {
            ra.add(m.group(1));
        }
        return ra;
    }
}
