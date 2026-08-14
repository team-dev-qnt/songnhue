package com.songnhue.core.common.util;

import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.songnhue.core.common.exception.ValidationException;

/**
 * Kiểm tra tệp tải lên (conventions.md §4.4).
 *
 * <p><b>Không tin phần mở rộng, cũng không tin {@code Content-Type}</b> — cả hai đều do client đặt.
 * Kiểm theo <i>magic bytes</i>: vài byte đầu tệp do chính định dạng quy định, đổi được thì tệp cũng
 * hỏng luôn.
 *
 * <p>Tên tệp lưu xuống MinIO được random hoá ({@link #randomStorageName}): tên gốc do người dùng
 * đặt có thể chứa {@code ../} để thoát thư mục, ký tự điều khiển, hoặc chính nó đã là dữ liệu cá
 * nhân ("hop-dong-nguyen-van-a.pdf").
 *
 * <p>⚠ Còn hai lớp nữa <b>chưa</b> làm ở đây, thuộc WS-6/T6.4: quét virus ClamAV bất đồng bộ và
 * mã hoá lại ảnh để bóc EXIF/payload. Qua được lớp này mới chỉ là "đúng định dạng", chưa phải "an
 * toàn".
 */
public final class FileValidator {

    private FileValidator() {}

    /** Nhóm tệp — giới hạn dung lượng lấy từ bảng {@code settings}, không hard-code ở đây. */
    public enum Category {
        IMAGE,
        DOCUMENT,
        GIS
    }

    /**
     * Bảng chữ ký. Cố ý viết tay thay vì kéo Apache Tika về: danh sách định dạng của dự án rất
     * ngắn, còn Tika nặng hơn 10MB và mang theo cả một cây phụ thuộc phải theo dõi CVE.
     */
    private static final List<Signature> SIGNATURES = List.of(
            new Signature("image/jpeg", "ffd8ff", 0),
            new Signature("image/png", "89504e470d0a1a0a", 0),
            new Signature("image/gif", "474946383761", 0),
            new Signature("image/gif", "474946383961", 0),
            // WebP: "RIFF" ở byte 0 và "WEBP" ở byte 8
            new Signature("image/webp", "52494646", 0),
            new Signature("application/pdf", "255044462d", 0),
            // Định dạng Office hiện đại (docx/xlsx/pptx) và KMZ đều là tệp ZIP
            new Signature("application/zip", "504b0304", 0),
            new Signature("application/zip", "504b0506", 0),
            // Định dạng Office cũ (doc/xls) — cấu trúc OLE2
            new Signature("application/x-ole-storage", "d0cf11e0a1b11ae1", 0));

    /**
     * Kiểm tra nội dung tệp khớp với một định dạng được phép.
     *
     * @param content vài KB đầu là đủ, nhưng phải là dữ liệu thật của tệp
     * @param originalName tên gốc do người dùng đặt — chỉ dùng để báo lỗi, không dùng để quyết định
     * @param allowedMimeTypes danh sách MIME cho phép của nhóm tệp đó
     * @throws ValidationException khi tệp rỗng hoặc chữ ký không nằm trong danh sách
     */
    public static String detectAndValidate(byte[] content, String originalName, List<String> allowedMimeTypes) {
        if (content == null || content.length == 0) {
            throw (ValidationException) new ValidationException().withDetail("file", "FILE_EMPTY", originalName);
        }
        String detected = detect(content);

        if (detected == null || !allowedMimeTypes.contains(detected)) {
            // Báo về định dạng PHÁT HIỆN ĐƯỢC, không phải phần mở rộng người dùng gửi —
            // để người dùng hiểu vì sao đổi đuôi tệp không giúp gì
            throw (ValidationException) new ValidationException()
                    .withDetail("file", "FILE_TYPE_NOT_ALLOWED", detected == null ? "unknown" : detected);
        }
        return detected;
    }

    /** Trả về MIME phát hiện được, hoặc {@code null} nếu không khớp chữ ký nào. */
    public static String detect(byte[] content) {
        String head = HexFormat.of().formatHex(content, 0, Math.min(content.length, 16));

        for (Signature signature : SIGNATURES) {
            if (signature.matches(head)) {
                // RIFF còn dùng cho WAV/AVI — phải xem thêm 4 byte ở vị trí 8 mới chắc là WebP
                if ("image/webp".equals(signature.mimeType()) && !isWebp(content)) {
                    continue;
                }
                return signature.mimeType();
            }
        }
        return null;
    }

    public static void validateSize(long sizeBytes, long maxBytes, String originalName) {
        if (sizeBytes > maxBytes) {
            throw (ValidationException) new ValidationException().withDetail("file", "FILE_TOO_LARGE", sizeBytes);
        }
    }

    /**
     * Tên tệp lưu xuống MinIO: UUID + phần mở rộng lấy theo MIME đã xác thực.
     *
     * <p>Phần mở rộng suy ra từ MIME PHÁT HIỆN ĐƯỢC chứ không phải từ tên gốc — nếu không thì
     * {@code anh.jpg.exe} vẫn giữ nguyên đuôi {@code .exe} trong kho lưu trữ.
     */
    public static String randomStorageName(String extension) {
        String safe = extension == null
                ? ""
                : extension.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return safe.isEmpty() ? UUID.randomUUID().toString() : UUID.randomUUID() + "." + safe;
    }

    private static boolean isWebp(byte[] content) {
        if (content.length < 12) {
            return false;
        }
        return content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    private record Signature(String mimeType, String hexPrefix, int offset) {

        boolean matches(String headHex) {
            int start = offset * 2;
            int end = start + hexPrefix.length();
            return headHex.length() >= end && headHex.regionMatches(true, start, hexPrefix, 0, hexPrefix.length());
        }
    }
}
