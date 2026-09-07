package com.songnhue.core.infra.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.UpstreamException;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.http.Method;
import io.minio.messages.Item;

/**
 * Đọc/ghi tệp tin trên MinIO — <b>nơi duy nhất</b> hệ thống chạm vào kho đối tượng (T6.3).
 *
 * <p>Ràng buộc thiết kế #3 của Phase 0: <b>app stateless tuyệt đối</b>, không ghi tệp xuống đĩa cục
 * bộ ngay từ v1. Ghi tạm ra đĩa là thứ chạy tốt trên một node rồi vỡ đúng lúc thêm node thứ hai —
 * người dùng tải lên ở node A, tải xuống ở node B, và tệp "không tồn tại".
 *
 * <p>Mọi lỗi của kho đối tượng gói thành {@code SYS-0006} (lỗi dịch vụ ngoài): thông báo của MinIO
 * chứa tên bucket, endpoint nội bộ và đôi khi cả access key — không được lọt ra API (§2.2).
 */
@Component
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final MinioClient client;

    public ObjectStorage(MinioClient client) {
        this.client = client;
    }

    public void put(String bucket, String objectKey, byte[] content, String contentType) {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(stream, content.length, -1)
                    .contentType(contentType)
                    .build());
            log.debug("Đã ghi {}/{} ({} byte)", bucket, objectKey, content.length);
        } catch (Exception e) {
            throw new UpstreamException(ErrorCode.SYS_0006, e, "MinIO");
        }
    }

    public byte[] get(String bucket, String objectKey) {
        try (InputStream stream = openStream(bucket, objectKey)) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new UpstreamException(ErrorCode.SYS_0006, e, "MinIO");
        }
    }

    /**
     * Mở luồng đọc — <b>T28.35</b>, đường phục vụ tệp mà ⛔ không nạp trọn vào heap.
     *
     * <h2>⚠⚠ Người gọi PHẢI đóng luồng này</h2>
     *
     * <p>Đây là hợp đồng, ⛔ không phải lời khuyên: luồng của MinIO giữ một kết nối HTTP trong pool.
     * Rò một lượt mỗi lần tải thì pool cạn sau vài trăm lượt, và triệu chứng là <b>toàn hệ treo lúc
     * gọi kho</b> — ⛔ không phải một lỗi ở đường tải, nên nó ⛔ không chỉ vào đây.
     *
     * <p>⭐ Vì sao {@link #get} vẫn ở lại: đường tải <b>có đăng nhập</b> ({@code downloadUrl},
     * {@code readForPublic} của các luồng nội bộ) và các lượt đọc nhỏ trong job nền vẫn cần nguyên
     * mảng byte, và ở đó số lượt đồng thời bị chặn bởi phân quyền. Bề mặt ⛔ không đăng nhập mới là
     * chỗ ⛔ không có gì hạn chế số người bấm.
     */
    public InputStream openStream(String bucket, String objectKey) {
        try {
            return client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new UpstreamException(ErrorCode.SYS_0006, e, "MinIO");
        }
    }

    /**
     * Đường dẫn tải có hạn.
     *
     * <p><b>Vì sao dùng presigned URL thay vì cho tệp đi qua ứng dụng.</b> Tệp đi qua ứng dụng thì
     * mỗi lượt tải chiếm một luồng và một connection suốt thời gian truyền — vài người tải bản đồ
     * GIS cùng lúc là đủ chiếm hết. Presigned URL để trình duyệt lấy thẳng từ MinIO.
     *
     * <p>TTL <b>ngắn</b> có chủ đích (§4.3): đường dẫn này bỏ qua mọi tầng phân quyền của ứng dụng,
     * ai cầm được cũng tải được. Ngắn thì cửa sổ đó gần như không dùng lại được sau khi bị chuyền
     * tay hoặc lọt vào lịch sử trình duyệt.
     */
    public String presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new UpstreamException(ErrorCode.SYS_0006, e, "MinIO");
        }
    }

    /**
     * Liệt kê đối tượng theo tiền tố, kèm <b>mốc sửa đổi</b> — WS-34/T34.7.
     *
     * <p>⚠ Mốc lấy từ <b>MinIO</b>, ⛔ không phải từ đồng hồ ứng dụng. Đó là chủ ý: nó là mốc của
     * chính đối tượng sắp bị phán xét, nên không có độ lệch múi giờ hay lệch đồng hồ giữa hai máy —
     * cùng bài học với {@code HydroRetentionHandler} (mốc cắt lấy từ {@code current_date} của phiên
     * CSDL, ⛔ không từ {@code LocalDate.now()}).
     *
     * @return khoá đối tượng → mốc sửa đổi gần nhất; rỗng khi ⛔ không có đối tượng nào khớp
     */
    public Map<String, Instant> list(String bucket, String prefix) {
        Map<String, Instant> ket = new LinkedHashMap<>();
        try {
            Iterable<Result<Item>> ds = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> r : ds) {
                Item it = r.get();
                if (!it.isDir()) {
                    ZonedDateTime moc = it.lastModified();
                    ket.put(it.objectName(), moc == null ? null : moc.toInstant());
                }
            }
        } catch (Exception e) {
            throw new UpstreamException(ErrorCode.SYS_0006, e, "MinIO");
        }
        return ket;
    }

    public void delete(String bucket, String objectKey) {
        try {
            client.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new UpstreamException(ErrorCode.SYS_0006, e, "MinIO");
        }
    }
}
