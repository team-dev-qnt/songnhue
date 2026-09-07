package com.songnhue.core.application.report;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.config.StorageProperties;
import com.songnhue.core.infra.storage.ObjectStorage;
import com.songnhue.core.spi.ReportFilePort;

/**
 * Cài đặt {@link ReportFilePort} — bản kết xuất báo cáo nằm ở bucket {@code MINIO_BUCKET_REPORT}.
 *
 * <p>⭐ Đây là <b>người đọc đầu tiên</b> của {@code StorageProperties.getBucketReport()} kể từ khi
 * cấu hình ấy ra đời (13/8/2026) — xem javadoc {@link ReportFilePort}.
 *
 * <p>⛔ Lớp này cố ý <b>mỏng</b>: nó chỉ ghép "bucket nào" với {@link ObjectStorage}. Mọi quyết định
 * nghiệp vụ — đặt tên khoá, dựng nội dung, ai được tải — nằm ở module gọi. Đặt chúng vào đây là
 * dựng một tầng nghiệp vụ thứ hai trong Core mà ⛔ không module nào nhìn thấy được.
 */
@Component
public class ReportFileStore implements ReportFilePort {

    private static final Logger log = LoggerFactory.getLogger(ReportFileStore.class);

    private final ObjectStorage storage;
    private final StorageProperties properties;

    public ReportFileStore(ObjectStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void luu(String khoa, byte[] noiDung, String contentType) {
        storage.put(properties.getBucketReport(), kiemKhoa(khoa), noiDung, contentType);
    }

    @Override
    public Optional<byte[]> doc(String khoa) {
        try {
            return Optional.of(storage.get(properties.getBucketReport(), kiemKhoa(khoa)));
        } catch (RuntimeException e) {
            // ⚠ MinIO ⛔ không phân biệt "chưa từng có" với "đã bị dọn" ở tầng ngoại lệ, và ở đây
            //   hai thứ ấy dẫn tới cùng một câu trả lời cho người dùng: bản kết xuất không còn.
            //   ⛔ Đừng để ngoại lệ hạ tầng nổi lên thành 502 — nó nói sai về nguyên nhân.
            log.debug("Không đọc được bản kết xuất {}", khoa, e);
            return Optional.empty();
        }
    }

    @Override
    public int donQuaHan(String tienTo, Duration hanDung) {
        Instant mocCat = Instant.now().minus(hanDung);
        String bucket = properties.getBucketReport();
        Map<String, Instant> ds = storage.list(bucket, kiemKhoa(tienTo));

        int daXoa = 0;
        for (Map.Entry<String, Instant> e : ds.entrySet()) {
            // ⛔ Mốc NULL ⇒ ⛔ KHÔNG xoá. Không biết tệp bao nhiêu tuổi thì phán nó quá hạn là đoán,
            //   và đoán sai ở đây nghĩa là xoá một bản kết xuất người dùng đang chờ tải.
            if (e.getValue() != null && e.getValue().isBefore(mocCat)) {
                storage.delete(bucket, e.getKey());
                daXoa++;
            }
        }
        if (daXoa > 0) {
            log.info("Đã dọn {}/{} bản kết xuất quá hạn dưới tiền tố {}", daXoa, ds.size(), tienTo);
        }
        return daXoa;
    }

    /**
     * ⛔⛔ Khoá đối tượng ⛔ <b>không</b> được chứa {@code ..} hay bắt đầu bằng {@code /}.
     *
     * <p>Hôm nay mọi khoá đều do máy chủ sinh ra từ một {@code UUID}, nên phép kiểm này ⛔ không
     * chặn được gì cả — và đó chính là lý do nó phải có <b>bây giờ</b>. Ngày nào có một endpoint
     * nhận tên tệp từ người dùng (bản xuất "đặt tên theo ý bạn" là một yêu cầu rất tự nhiên), khoá
     * {@code ../../songnhue-media/logo.png} sẽ đi thẳng qua đây. Đặt bảo đảm ở <b>chỗ dữ liệu đi
     * qua</b>, ⛔ không ở nơi gọi (luật 12).
     */
    private static String kiemKhoa(String khoa) {
        if (khoa == null || khoa.isBlank() || khoa.startsWith("/") || khoa.contains("..")) {
            throw new IllegalArgumentException("Khoá đối tượng không hợp lệ: " + khoa);
        }
        return khoa;
    }
}
