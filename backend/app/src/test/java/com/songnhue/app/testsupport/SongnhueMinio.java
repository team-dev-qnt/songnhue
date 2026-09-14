package com.songnhue.app.testsupport;

import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * MinIO thật cho test tích hợp — WS-14, đóng <b>Definition of Done mục 11 của Phase 0</b>.
 *
 * <h2>Vì sao việc này quan trọng hơn vẻ ngoài của nó</h2>
 *
 * Từ WS-6 tới hết Phase 0, {@code IntegrationTestBase} trỏ {@code app.storage.endpoint} vào
 * {@code http://minio.invalid:9000} — một địa chỉ không tồn tại. {@code MinioClient} không mở kết
 * nối lúc khởi tạo bean nên context vẫn lên, mọi bài kiểm vẫn xanh, và <b>chưa một lượt tải tệp nào
 * đi tới kho lưu trữ</b>. Nghĩa là toàn bộ pattern P3 — kiểm magic bytes, bóc EXIF, đặt tên ngẫu
 * nhiên, presigned URL — mới chỉ được kiểm ở phần <i>trước</i> khi chạm ra ngoài.
 *
 * <p>Đây đúng là dạng lỗi đã trả giá nhiều lần trong dự án: cơ chế có mặt, xanh, và chưa ai đi qua.
 * {@code AttachmentQuotaTest} của WS-12 phải viết vòng vèo (chèn thẳng dòng SQL rồi khẳng định trên
 * mã lỗi) chính vì lý do này.
 *
 * <h2>Hai lựa chọn có chủ đích</h2>
 *
 * <ul>
 *   <li><b>{@code GenericContainer} chứ không phải module {@code org.testcontainers:minio}</b> —
 *       tránh thêm một phụ thuộc nữa phải theo dõi CVE, trong khi thứ cần chỉ là "chạy image này,
 *       chờ cổng 9000 sẵn sàng".
 *   <li><b>Cùng image với {@code compose.infra.yml}</b> — đổi ở một nơi thì phải đổi cả hai. Test
 *       chạy trên một bản MinIO khác bản sẽ triển khai là kiểm chứng một hệ thống khác. ⭐ Câu dặn
 *       này đứng đây từ WS-4 và <b>chưa bao giờ là một cổng kiểm</b>; nay nó là
 *       {@link com.songnhue.app.deploy.AnhMinioDongBoTest}.
 * </ul>
 *
 * <h2>⛔⛔ Vì sao là {@code quay.io} chứ không phải Docker Hub — đổi 14/9/2026</h2>
 *
 * Lượt CI của PR Phase 3 đỏ ở <b>86 lớp</b>, tất cả là nạn nhân dây chuyền của một dòng:
 * {@code pull access denied for minio/minio, repository does not exist}. Đo lại cùng ngày:
 * <b>cả repository đã biến mất khỏi Docker Hub</b>, không phải một tag bị dọn —
 * {@code hub.docker.com/v2/repositories/minio/minio/} trả {@code object not found}.
 *
 * <p>Ở máy vẫn xanh vì ảnh nằm sẵn trong <b>đệm Docker cục bộ</b> (kéo về 12 tháng trước), nên
 * Testcontainers không hỏi registry lần nào. Một biến thể mới của <i>"xanh ở máy không phải bằng
 * chứng"</i>: lần trước là {@code .env.local}, lần này là <b>đệm ảnh</b>.
 *
 * <p>⚠⚠ Bản {@code quay.io} chỉ có manifest <b>linux/amd64</b>. <b>Đo được</b>: máy dev Apple
 * Silicon chạy qua giả lập ({@code docker run --platform linux/amd64 … --version} thoát 0), chậm
 * hơn chứ không hỏng. ⛔ <b>CHƯA đo: kiến trúc hai VPS</b> — kho ⛔ không ghi nó ở đâu cả ⇒ chạy
 * {@code uname -m} trên máy chủ TRƯỚC lượt đề bạt (T60.10).
 */
public final class SongnhueMinio {

    /** Khớp {@code deploy/compose.infra.yml}. */
    private static final DockerImageName IMAGE =
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z.hotfix.7aa24e772");

    private static final String ACCESS_KEY = "songnhue-test";
    private static final String SECRET_KEY = "test_only_not_a_secret";

    public static final String BUCKET_MEDIA = "test-media";
    public static final String BUCKET_REPORT = "test-report";
    public static final String BUCKET_AUDIT = "test-audit";

    @SuppressWarnings("resource") // Ryuk đóng container khi JVM tắt
    private static final GenericContainer<?> INSTANCE = new GenericContainer<>(IMAGE)
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(9000)
            // ⚠ Chờ theo endpoint sức khoẻ của chính MinIO, không chờ "cổng mở". Cổng mở trước khi
            // dịch vụ nhận lệnh được vài trăm mili-giây, và khoảng đó đủ để lượt tạo bucket đầu
            // tiên hỏng — một lỗi chỉ thỉnh thoảng xuất hiện, tức là loại tệ nhất.
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200));

    static {
        INSTANCE.start();
        taoBucket();
    }

    private SongnhueMinio() {}

    public static String endpoint() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(9000);
    }

    public static String accessKey() {
        return ACCESS_KEY;
    }

    public static String secretKey() {
        return SECRET_KEY;
    }

    /** Bảo đảm container đã dựng — gọi từ {@code @DynamicPropertySource}. */
    public static void start() {
        // Việc dựng nằm ở khối static; hàm này chỉ để nơi gọi đọc ra ý định.
    }

    /**
     * Tạo sẵn ba bucket, giống việc dịch vụ {@code minio-init} làm ở compose.
     *
     * <p>Ứng dụng <b>không tự tạo bucket</b> — cố ý, vì quyền tạo bucket là quyền quản trị mà tài
     * khoản ứng dụng không nên có ở môi trường thật. Nên test phải tự dựng, đúng như production.
     */
    private static void taoBucket() {
        try (MinioClient client = MinioClient.builder()
                .endpoint(endpoint())
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build()) {

            for (String bucket : List.of(BUCKET_MEDIA, BUCKET_REPORT, BUCKET_AUDIT)) {
                boolean daCo = client.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!daCo) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được bucket cho MinIO của test", e);
        }
    }
}
