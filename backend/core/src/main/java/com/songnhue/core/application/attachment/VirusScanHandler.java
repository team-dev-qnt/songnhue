package com.songnhue.core.application.attachment;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.application.job.JobContext;
import com.songnhue.core.application.job.JobHandler;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.domain.attachment.Attachment;
import com.songnhue.core.infra.attachment.AttachmentRepository;
import com.songnhue.core.infra.storage.ObjectStorage;

/**
 * Quét virus tệp vừa tải lên (conventions.md §4.4) — nợ WS-4/T4.6, trả ở T6.4.
 *
 * <p><b>Quét bất đồng bộ, và tệp chưa quét thì chưa tải xuống được.</b> Quét đồng bộ trong request
 * làm người dùng chờ hàng giây cho mỗi tệp, mà ClamAV có lúc phải nạp lại bộ định nghĩa và chậm
 * hẳn. Đổi lại là một khoảng ngắn tệp ở trạng thái "đang chờ" — chấp nhận được, vì đó cũng chính là
 * trạng thái đúng: chưa ai biết tệp đó có sạch không.
 *
 * <p>Nói chuyện với ClamAV bằng <b>giao thức INSTREAM</b> qua socket, không kéo thư viện: giao thức
 * này chỉ vài dòng (gửi từng khối kèm độ dài 4 byte, kết thúc bằng khối rỗng), còn thư viện client
 * là thêm một phụ thuộc phải theo dõi CVE cho đúng phần việc này.
 *
 * <p>Chưa cấu hình ClamAV thì tệp vẫn sang {@code READY} nhưng ghi {@code SKIPPED} — <b>không</b>
 * ghi {@code CLEAN}. Khác biệt đó là thứ người kiểm thử bảo mật cần nhìn thấy; ghi "sạch" cho tệp
 * chưa hề quét là nói dối ngay trong dữ liệu.
 */
@Component
public class VirusScanHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(VirusScanHandler.class);

    /** Kích thước khối theo giao thức INSTREAM. ClamAV mặc định từ chối khối quá 64KB. */
    private static final int CHUNK_SIZE = 32 * 1024;

    private static final int TIMEOUT_MS = 30_000;

    private final AttachmentRepository repository;
    private final ObjectStorage storage;
    private final ObjectMapper objectMapper;
    private final String host;
    private final int port;

    public VirusScanHandler(
            AttachmentRepository repository,
            ObjectStorage storage,
            ObjectMapper objectMapper,
            @Value("${app.clamav.host:}") String host,
            @Value("${app.clamav.port:3310}") int port) {
        this.repository = repository;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.host = host;
        this.port = port;
    }

    @Override
    public String jobType() {
        return JobTypes.VIRUS_SCAN;
    }

    @Override
    @Transactional
    public void handle(JobContext context) throws IOException {
        long attachmentId =
                objectMapper.readTree(context.payload()).path("attachmentId").asLong();
        Attachment attachment = repository
                .findById(attachmentId)
                .orElseThrow(
                        () -> new IllegalStateException("Không tìm thấy tệp đính kèm " + attachmentId + " để quét"));

        if (host == null || host.isBlank()) {
            attachment.markScanSkipped("Chưa cấu hình ClamAV (app.clamav.host)");
            repository.save(attachment);
            log.warn("Bỏ qua quét virus tệp {} — chưa cấu hình ClamAV", attachment.getPublicId());
            return;
        }

        byte[] content = storage.get(attachment.getStorageBucket(), attachment.getStorageKey());
        String verdict = scan(content);

        if (verdict.contains("OK") && !verdict.contains("FOUND")) {
            attachment.markClean();
            log.info("Tệp {} sạch — chuyển sang tải xuống được", attachment.getPublicId());
        } else {
            attachment.markInfected(verdict);
            // Mức ERROR chứ không WARN: đây là sự kiện cần người xem, không phải nhiễu vận hành.
            log.error("⚠ Tệp {} nhiễm mã độc, đã cách ly: {}", attachment.getPublicId(), verdict);
        }
        repository.save(attachment);
    }

    /**
     * Giao thức INSTREAM: {@code zINSTREAM\0} rồi các khối {@code <độ dài 4 byte big-endian><dữ
     * liệu>}, kết thúc bằng độ dài 0.
     *
     * @return nguyên văn phản hồi của ClamAV, VD {@code stream: OK} hoặc {@code stream: Eicar-Test-Signature FOUND}
     */
    private String scan(byte[] content) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    InputStream in = socket.getInputStream()) {

                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
                    int length = Math.min(CHUNK_SIZE, content.length - offset);
                    out.writeInt(length);
                    out.write(content, offset, length);
                }
                out.writeInt(0);
                out.flush();

                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        }
    }
}
