package com.songnhue.core.domain.attachment;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Tệp đính kèm — pattern P3 (implement.md §2). Nội dung tệp nằm ở MinIO, đây chỉ là siêu dữ liệu.
 *
 * <p><b>Bảng đa hình, cố ý không có khoá ngoại.</b> Cùng một bảng phục vụ ảnh bài viết, hồ sơ công
 * trình, tài liệu nhân sự — ba module khác nhau. Đặt khoá ngoại thì phải có một cột cho mỗi bảng
 * chủ, và thêm một loại tài liệu mới là phải sửa schema. Đổi lại, {@code owner_type} phải được đặt
 * đúng: sai giá trị đó thì tệp vẫn nằm nguyên trong kho nhưng không màn hình nào tìm ra.
 *
 * <p>{@code storageKey} là tên <b>ngẫu nhiên</b>, không phải tên gốc người dùng đặt (§4.4). Tên gốc
 * giữ riêng ở {@code originalName} để hiển thị và để đặt tên lúc tải về.
 */
@Entity
@Table(name = "attachments")
@Audited(module = "core", entityType = "Tệp đính kèm")
public class Attachment extends BaseEntity {

    @Column(name = "owner_type", nullable = false, length = 50)
    private String ownerType;

    @Column(name = "owner_id")
    private Long ownerId;

    /** Phân loại trong cùng một chủ sở hữu, VD {@code ANH_DAI_DIEN}, {@code HO_SO_THIET_KE}. */
    @Column(name = "purpose", length = 50)
    private String purpose;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /** Phiên bản tài liệu: cùng {@code (owner, purpose)} thì số tăng dần. */
    @Column(name = "file_version", nullable = false)
    private int fileVersion = 1;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Hạn hiệu lực (giấy phép, chứng chỉ) — nguồn cho cảnh báo sắp hết hạn. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttachmentStatus status = AttachmentStatus.UPLOADING;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 20)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    @Column(name = "scan_at")
    private Instant scanAt;

    @Column(name = "scan_result", length = 255)
    private String scanResult;

    @Column(name = "org_unit_id")
    private Long orgUnitId;

    protected Attachment() {}

    public Attachment(String ownerType, Long ownerId, String originalName, String bucket, String storageKey) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.originalName = originalName;
        this.storageBucket = bucket;
        this.storageKey = storageKey;
    }

    /** Quét xong và sạch — đây là lúc duy nhất tệp được phép chuyển sang tải xuống được. */
    public void markClean() {
        this.scanStatus = ScanStatus.CLEAN;
        this.status = AttachmentStatus.READY;
        this.scanAt = Instant.now();
        this.scanResult = null;
    }

    public void markInfected(String detail) {
        this.scanStatus = ScanStatus.INFECTED;
        this.status = AttachmentStatus.QUARANTINED;
        this.scanAt = Instant.now();
        this.scanResult = detail;
    }

    /**
     * Chưa cấu hình trình quét.
     *
     * <p>Vẫn cho tệp sang {@code READY} — nếu không thì môi trường không có ClamAV sẽ không tải được
     * tệp nào. Nhưng ghi rõ {@code SKIPPED} chứ không ghi {@code CLEAN}: khác biệt đó là thứ người
     * kiểm thử bảo mật cần thấy, và ghi "sạch" cho tệp chưa hề quét là nói dối trong dữ liệu.
     */
    public void markScanSkipped(String reason) {
        this.scanStatus = ScanStatus.SKIPPED;
        this.status = AttachmentStatus.READY;
        this.scanAt = Instant.now();
        this.scanResult = reason;
    }

    public boolean isDownloadable() {
        return status == AttachmentStatus.READY;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public int getFileVersion() {
        return fileVersion;
    }

    public void setFileVersion(int fileVersion) {
        this.fileVersion = fileVersion;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public AttachmentStatus getStatus() {
        return status;
    }

    public ScanStatus getScanStatus() {
        return scanStatus;
    }

    public Instant getScanAt() {
        return scanAt;
    }

    public String getScanResult() {
        return scanResult;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }
}
