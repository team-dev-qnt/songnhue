package com.songnhue.core.application.attachment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.StorageProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.util.FileValidator;
import com.songnhue.core.common.util.HashUtils;
import com.songnhue.core.common.util.ImageSanitizer;
import com.songnhue.core.common.util.SvgSanitizer;
import com.songnhue.core.domain.attachment.Attachment;
import com.songnhue.core.infra.attachment.AttachmentRepository;
import com.songnhue.core.infra.storage.ObjectStorage;
import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;

/**
 * Tải lên và tra cứu tệp đính kèm — pattern P3 (T6.3).
 *
 * <p><b>Thứ tự các bước là phần quan trọng nhất của lớp này</b> (conventions.md §4.4):
 *
 * <ol>
 *   <li>Kiểm <b>magic bytes</b> — không tin phần mở rộng hay {@code Content-Type} do client gửi;
 *       cả hai đều do người tải lên đặt.
 *   <li>Kiểm dung lượng theo tham số cấu hình.
 *   <li>Ảnh thì mã hoá lại để bỏ EXIF và mọi dữ liệu gắn kèm.
 *   <li>Đặt tên ngẫu nhiên rồi mới ghi lên MinIO — tên gốc do người dùng đặt không bao giờ trở
 *       thành đường dẫn.
 *   <li>Ghi bản ghi ở trạng thái <b>chờ quét</b>, và xếp việc quét virus vào hàng đợi.
 * </ol>
 *
 * <p>Tệp chỉ tải xuống được sau khi quét xong. Cho tải một tệp chưa quét là biến hệ thống thành nơi
 * phát tán — mà đây là hệ có Cổng thông tin điện tử công khai.
 */
@Service
public class AttachmentService implements AttachmentPort {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /**
     * Dung lượng tối đa mỗi tệp, đọc từ {@code settings} theo <b>nhóm định dạng</b> (quy tắc 12).
     *
     * <p>⚠⚠ <b>Bản đầu đọc khoá {@code limit.upload.max-file-mb} — một khoá CHƯA TỪNG ĐƯỢC SEED.</b>
     * Hệ quả im lặng suốt từ WS-6: mọi lượt tải rơi về giá trị dự phòng 20MB cứng trong mã, trong khi
     * màn hình cấu hình bày ra ba tham số {@code limits.upload.max-mb.*} mà <b>không dòng mã nào
     * đọc</b>. Quản trị viên sửa "Dung lượng tối đa mỗi tài liệu = 50MB" rồi tải một hồ sơ hoàn công
     * 30MB vẫn bị từ chối, và không có lỗi nào chỉ ra vì sao.
     *
     * <p>Đây là biến thể của bài học lặp lại nhiều lần ở Phase 0: cơ chế còn sống nhưng <i>không nối
     * vào đâu cả</i>. Tham số cấu hình chỉ có nghĩa khi có bài kiểm chứng minh đổi giá trị thì hành
     * vi đổi theo — xem {@code AttachmentQuotaTest}.
     */
    private static final String KEY_MAX_UPLOAD_MB_PREFIX = "limits.upload.max-mb.";

    private static final int DEFAULT_MAX_UPLOAD_MB = 20;

    /**
     * Hạn mức dung lượng cho MỘT bản ghi, tra theo loại chủ sở hữu (T12.6).
     *
     * <p>VD {@code limits.attachment.quota-mb.CONSTRUCTION = 500} — CN-02.3 yêu cầu 500MB/công trình.
     * Không khai khoá cho một loại nào thì loại đó <b>không giới hạn</b>: hạn mức là ngoại lệ có chủ
     * đích cho vài loại hồ sơ nặng, không phải luật chung.
     */
    private static final String KEY_OWNER_QUOTA_MB_PREFIX = "limits.attachment.quota-mb.";

    /**
     * Hạn của đường dẫn tải.
     *
     * <p>Ngắn có chủ đích: presigned URL bỏ qua mọi tầng phân quyền, ai cầm được cũng tải được. Đủ
     * để bấm tải, không đủ để chuyền tay.
     */
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);

    private final AttachmentRepository repository;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final SettingService settings;
    private final JobService jobs;

    public AttachmentService(
            AttachmentRepository repository,
            ObjectStorage storage,
            StorageProperties storageProperties,
            SettingService settings,
            JobService jobs) {
        this.repository = repository;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.settings = settings;
        this.jobs = jobs;
    }

    /**
     * @param allowedMimeTypes danh sách định dạng chấp nhận cho loại tài liệu này
     * @return bản ghi tệp ở trạng thái chờ quét — <b>chưa</b> tải xuống được
     */
    @Transactional
    public Attachment upload(
            String ownerType,
            Long ownerId,
            String purpose,
            String originalName,
            byte[] content,
            List<String> allowedMimeTypes) {

        String mimeType = FileValidator.detectAndValidate(content, originalName, allowedMimeTypes);
        long maxBytes = settings.getInt(KEY_MAX_UPLOAD_MB_PREFIX + sizeGroupOf(mimeType), DEFAULT_MAX_UPLOAD_MB)
                * 1024L
                * 1024L;
        FileValidator.validateSize(content.length, maxBytes, originalName);
        requireQuota(ownerType, ownerId, content.length);

        // Khử trùng TRƯỚC khi tính checksum và ghi lên kho: checksum phải khớp đúng thứ đã lưu, nếu
        // không thì lần kiểm tra toàn vẹn nào cũng báo lệch.
        //
        // ⚠ SVG đi đường riêng vì nó KHÔNG phải ảnh raster: `javax.imageio` không đọc được nên
        // ImageSanitizer trả nguyên bản về — tức là tệp duy nhất trong nhóm ảnh có thể chạy
        // JavaScript lại là tệp duy nhất không được bóc gì. Đặt nhánh này ở đây, chứ không ở nơi
        // gọi, để mọi đường tải lên đều đi qua: chọn được định dạng nào là việc của
        // `allowedMimeTypes`, còn khử trùng thì không ai được phép quên.
        byte[] stored = SvgSanitizer.MIME.equals(mimeType)
                ? SvgSanitizer.sanitize(content, originalName)
                : ImageSanitizer.stripMetadata(content, mimeType);

        // Đuôi lấy theo MIME đã xác thực, KHÔNG theo tên gốc — nếu không thì `anh.jpg.exe` giữ
        // nguyên đuôi `.exe` trong kho, đúng thứ mà việc đổi tên ngẫu nhiên sinh ra để chặn.
        String objectKey = FileValidator.randomStorageName(FileValidator.extensionOf(mimeType));
        String bucket = storageProperties.getBucketMedia();

        storage.put(bucket, objectKey, stored, mimeType);

        Attachment attachment = new Attachment(ownerType, ownerId, originalName, bucket, objectKey);
        attachment.setPurpose(purpose);
        attachment.setContentType(mimeType);
        attachment.setSizeBytes(stored.length);
        attachment.setChecksumSha256(HashUtils.sha256Hex(stored));
        attachment.setFileVersion(nextVersion(ownerType, ownerId, purpose));
        AuthContext.current().ifPresent(user -> attachment.setOrgUnitId(user.orgUnitId()));

        Attachment saved = repository.saveAndFlush(attachment);
        jobs.enqueue(JobTypes.VIRUS_SCAN, "{\"attachmentId\":%d}".formatted(saved.getId()), null, (short) 3);

        log.info(
                "Nhận tệp {} ({} byte, {}) → {}/{} — chờ quét virus",
                originalName,
                stored.length,
                mimeType,
                bucket,
                objectKey);
        return saved;
    }

    /**
     * Đường dẫn tải có hạn.
     *
     * <p>Từ chối tệp chưa {@code READY}: đó là tệp còn đang chờ quét hoặc đã bị cách ly.
     */
    @Override
    @Transactional(readOnly = true)
    public String downloadUrl(UUID publicId) {
        Attachment attachment = require(publicId);
        if (!attachment.isDownloadable()) {
            throw new BusinessRuleException(
                    ErrorCode.SYS_0009, attachment.getScanStatus().name());
        }
        return storage.presignedGetUrl(attachment.getStorageBucket(), attachment.getStorageKey(), DOWNLOAD_URL_TTL);
    }

    /**
     * Đọc nội dung tệp cho cổng công khai — WS-16/T16.6.
     *
     * <p>Ba điều kiện phải đúng <b>cùng lúc</b>, và cả ba được kiểm ở đây chứ không ở nơi gọi: tệp
     * còn sống, thuộc loại chủ sở hữu công khai, và đã qua bước quét. Lý do đặt chốt chặn ở đây nằm
     * trong javadoc của {@link AttachmentPort#readForPublic}.
     *
     * <p>Trả {@link Optional#empty()} cho mọi trường hợp từ chối — không phân biệt "không có" với
     * "có nhưng không công khai". Phân biệt được là biến endpoint này thành máy dò xem UUID nào tồn
     * tại trong kho.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentContent> readForPublic(UUID publicId, List<String> allowedOwnerTypes) {
        Optional<Attachment> found = repository.findByPublicIdAndDeletedAtIsNull(publicId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Attachment attachment = found.get();
        if (!allowedOwnerTypes.contains(attachment.getOwnerType())) {
            // WARN chứ không DEBUG: hoặc là có người đang dò, hoặc là một đường dẫn công khai đang
            // trỏ nhầm vào tệp nội bộ. Cả hai đều đáng nhìn.
            log.warn(
                    "Từ chối phục vụ công khai tệp {} — loại chủ sở hữu '{}' không nằm trong danh sách cho phép",
                    publicId,
                    attachment.getOwnerType());
            return Optional.empty();
        }
        if (!attachment.isDownloadable()) {
            return Optional.empty();
        }
        byte[] content = storage.get(attachment.getStorageBucket(), attachment.getStorageKey());
        return Optional.of(new AttachmentContent(content, attachment.getContentType(), attachment.getOriginalName()));
    }

    @Transactional(readOnly = true)
    public Attachment get(UUID publicId) {
        return require(publicId);
    }

    @Transactional(readOnly = true)
    public List<Attachment> listOf(String ownerType, Long ownerId) {
        return repository.findByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByFileVersionDesc(ownerType, ownerId);
    }

    // ---- Hợp đồng cho module nghiệp vụ (core.spi) -------------------------------
    //
    // Ba phương thức dưới đây là bản dịch của ba phương thức ngay trên, khác đúng một điểm: chúng
    // trả `AttachmentRef` chứ không trả entity. Đó không phải trùng lặp thừa — module nghiệp vụ
    // nhận entity `Attachment` về là phải import `core.domain.attachment`, mà luật ranh giới module
    // cấm đúng điều đó (core/spi/package-info.java).
    //
    // `downloadUrl` và `delete` không cần bản dịch: chữ ký của chúng vốn chỉ có UUID và String.

    @Override
    @Transactional
    public AttachmentRef upload(AttachmentUploadCommand command) {
        return toRef(upload(
                command.ownerType(),
                command.ownerId(),
                command.purpose(),
                command.originalName(),
                command.content(),
                command.allowedMimeTypes()));
    }

    /** Không ném lỗi khi không tìm thấy: nơi gọi thường muốn hiện "tệp đã bị xoá", không phải 404. */
    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentRef> findRef(UUID publicId) {
        return repository.findByPublicIdAndDeletedAtIsNull(publicId).map(AttachmentService::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentRef> refsOf(String ownerType, Long ownerId) {
        return listOf(ownerType, ownerId).stream().map(AttachmentService::toRef).toList();
    }

    private static AttachmentRef toRef(Attachment attachment) {
        return new AttachmentRef(
                attachment.getPublicId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getFileVersion(),
                attachment.getPurpose(),
                attachment.isDownloadable(),
                attachment.getCreatedAt(),
                attachment.getValidUntil());
    }

    /**
     * Xoá mềm.
     *
     * <p><b>Không xoá tệp trên MinIO.</b> Bản ghi còn thì tệp còn: nhật ký kiểm toán và các bản ghi
     * nghiệp vụ vẫn trỏ tới nó, xoá thật là để lại những liên kết chỉ vào khoảng không. Dọn kho là
     * việc của job rà soát riêng, chạy sau thời gian giữ.
     */
    @Override
    @Transactional
    public void delete(UUID publicId) {
        Attachment attachment = require(publicId);
        attachment.markDeleted(Instant.now());
        repository.save(attachment);
    }

    private Attachment require(UUID publicId) {
        return repository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Nhóm định dạng để tra hạn mức dung lượng — khớp ba khoá đã seed ở {@code V202608131009}.
     *
     * <p>Định dạng lạ rơi vào nhóm {@code document}: đó là nhóm chặt vừa phải, và quan trọng hơn là
     * <b>một định dạng chưa phân nhóm không được hưởng hạn mức rộng nhất</b>.
     */
    private static String sizeGroupOf(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return "image";
        }
        if (mimeType.startsWith("video/")) {
            return "video";
        }
        if (mimeType.contains("json") || mimeType.contains("kml") || mimeType.contains("zip")) {
            return "gis";
        }
        return "document";
    }

    /**
     * Dung lượng một bản ghi đang dùng, tính bằng byte.
     *
     * <p>Giao diện hiện "đã dùng 120/500 MB" từ số này — người dùng phải thấy mình sắp chạm trần
     * <i>trước</i> khi bấm tải, chứ không phải nhận thông báo từ chối sau khi đã chọn xong tệp.
     */
    @Override
    @Transactional(readOnly = true)
    public long usedBytes(String ownerType, Long ownerId) {
        return repository.sumSizeBytesByOwner(ownerType, ownerId);
    }

    /**
     * Chặn khi bản ghi đã hết hạn mức.
     *
     * <p>⚠ Đây là kiểm tra <b>đọc rồi ghi</b>, nên hai lượt tải song song vẫn có thể cùng lọt qua và
     * vượt trần một chút. Chấp nhận có ý thức: cái giá để chặn tuyệt đối là khoá dòng hoặc ràng buộc
     * ở tầng CSDL cho một con số vốn là <i>chính sách vận hành</i>, không phải bất biến dữ liệu.
     * Vượt vài MB không hỏng gì; khoá nhầm một lượt tải hợp lệ thì có.
     */
    private void requireQuota(String ownerType, Long ownerId, long incomingBytes) {
        if (ownerId == null) {
            return;
        }
        int quotaMb = settings.getInt(KEY_OWNER_QUOTA_MB_PREFIX + ownerType, 0);
        if (quotaMb <= 0) {
            return;
        }

        long quotaBytes = quotaMb * 1024L * 1024L;
        long used = repository.sumSizeBytesByOwner(ownerType, ownerId);
        if (used + incomingBytes > quotaBytes) {
            throw new BusinessRuleException(ErrorCode.SYS_0010, (used + incomingBytes) / (1024 * 1024), quotaMb);
        }
    }

    /** Phiên bản kế tiếp trong cùng {@code (owner, purpose)} — nền cho lịch sử tài liệu (P3). */
    private int nextVersion(String ownerType, Long ownerId, String purpose) {
        return repository.findMaxVersion(ownerType, ownerId, purpose).orElse(0) + 1;
    }
}
