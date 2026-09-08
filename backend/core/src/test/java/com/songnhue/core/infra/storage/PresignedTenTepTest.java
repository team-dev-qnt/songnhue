package com.songnhue.core.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.minio.MinioClient;

/**
 * <b>Presigned URL phải giữ TÊN TỆP GỐC</b> — T40.27.
 *
 * <h2>Cái đã hỏng</h2>
 *
 * Khoá đối tượng trong kho là một chuỗi ngẫu nhiên, và đó là <b>chủ ý</b>: tên do người dùng đặt có
 * thể chứa dấu phân cách đường dẫn, ký tự điều khiển, hoặc chính nó là thông tin nhạy cảm. Nhưng hệ
 * quả là tệp tải về qua presigned URL mang tên {@code a3f9c1…} — người vận hành lưu năm quyết định
 * là có năm chuỗi ngẫu nhiên trong thư mục Tải về, ⛔ không phân biệt được cái nào là cái nào.
 *
 * <h2>⚠ Bài này ⛔ KHÔNG cần MinIO sống — nhưng PHẢI khai `region`</h2>
 *
 * {@code getPresignedObjectUrl} chỉ <b>ký</b>: dựng chuỗi rồi tính HMAC tại chỗ. ⚠ Nhưng nếu ⛔ không
 * khai {@code region} thì client đi <b>hỏi máy chủ</b> ({@code GetBucketLocation}) trước khi ký, và
 * lượt gọi ấy chết ⇒ {@code SYS-0006}. Bản đầu của bài này quên nó và đỏ cả 4/4 — một lượt đỏ nói về
 * môi trường chứ ⛔ không nói gì về thứ đang kiểm.
 *
 * <p>Với region đã khai, bài chạy được ở mọi môi trường và đo đúng thứ cần đo: <b>chuỗi thật sự đi
 * ra dây</b>, ⛔ không phải một tham số ta tin là đã truyền.
 */
class PresignedTenTepTest {

    private static final String BUCKET = "kiem-thu";
    private static final String KHOA = "abc/9f1c2d3e";

    private final ObjectStorage storage = new ObjectStorage(MinioClient.builder()
            .endpoint("http://minio.kiem-thu.local:9000")
            .credentials("kiem-thu", "kiem-thu-secret")
            // ⚠ Bắt buộc — xem javadoc lớp: thiếu nó là một lượt gọi mạng, và bài kiểm đỏ vì lý do sai.
            .region("us-east-1")
            .build());

    @Test
    @DisplayName("⭐⭐ Tên tiếng Việt có dấu đi ra dây ở dạng `filename*=UTF-8''…`")
    void tenTiengVietGiuNguyen() {
        String url = storage.presignedGetUrl(BUCKET, KHOA, Duration.ofMinutes(10), "Quyết định 123.pdf");

        assertThat(url).contains("response-content-disposition");
        String daGiai = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertThat(daGiai)
                .as("Thiếu `filename*` thì tên tệp rụng hết dấu — hoặc tệ hơn, thành mojibake")
                .contains("filename*=UTF-8''")
                .contains("attachment;");
        // Tên thật nằm ở dạng mã hoá phần trăm; giải hai lượt vì nó nằm trong một tham số truy vấn.
        assertThat(URLDecoder.decode(daGiai, StandardCharsets.UTF_8)).contains("Quyết định 123.pdf");
    }

    @Test
    @DisplayName("⭐ Bản dự phòng ASCII ⛔ không mang dấu và ⛔ không mang nháy kép")
    void banDuPhongAsciiSach() {
        String url = storage.presignedGetUrl(BUCKET, KHOA, Duration.ofMinutes(10), "Báo \"cáo\".pdf");
        String daGiai = URLDecoder.decode(url, StandardCharsets.UTF_8);

        // ⛔ Một nháy kép lọt vào `filename="…"` là cắt đôi header — tên tệp là dữ liệu người dùng nhập.
        assertThat(daGiai).doesNotContain("filename=\"Báo");
        assertThat(daGiai).containsPattern("filename=\"[\\x20-\\x21\\x23-\\x7E]*\"");
    }

    @Test
    @DisplayName("⭐⭐ ⛔ KHÔNG truyền tên ⇒ ⛔ KHÔNG có disposition — vế phân biệt hai trạng thái (luật 9)")
    void khongTruyenTenThiKhongCoDisposition() {
        // Thiếu vế này thì một bản cài luôn gắn disposition cũng làm hai bài trên xanh, trong khi
        // mọi ảnh nhúng trong bài viết bỗng bị TẢI VỀ thay vì hiện trong trang.
        assertThat(storage.presignedGetUrl(BUCKET, KHOA, Duration.ofMinutes(10)))
                .doesNotContain("response-content-disposition");
        assertThat(storage.presignedGetUrl(BUCKET, KHOA, Duration.ofMinutes(10), "  "))
                .as("Chuỗi toàn khoảng trắng phải xử như KHÔNG truyền")
                .doesNotContain("response-content-disposition");
    }

    @Test
    @DisplayName("Chữ ký vẫn hợp lệ về hình dạng — thêm tham số ⛔ không được làm hỏng URL")
    void chuKyVanConHinhDangDung() {
        String url = storage.presignedGetUrl(BUCKET, KHOA, Duration.ofMinutes(10), "Quyết định.pdf");

        assertThat(url).startsWith("http://minio.kiem-thu.local:9000/" + BUCKET + "/");
        assertThat(url).contains("X-Amz-Signature=").contains("X-Amz-Expires=600");
        // ⚠ `response-content-disposition` phải nằm TRONG phần được ký (`X-Amz-SignedHeaders` không
        //   phủ tham số truy vấn, nhưng chữ ký AWS v4 ký cả canonical query string). Kiểm gián tiếp:
        //   nó là một tham số truy vấn, ⛔ không phải một mảnh nối sau dấu `#`.
        assertThat(url).doesNotContain("#");
    }
}
