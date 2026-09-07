package com.songnhue.core.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin một tệp đính kèm trả ra cho module nghiệp vụ — thay cho entity {@code Attachment}.
 *
 * <p>Không có {@code id} chạy số: mọi lời gọi tiếp theo dùng {@code publicId} (§4.2 chống IDOR).
 *
 * @param ownerType loại chủ sở hữu ({@code MEDIA_FOLDER}, {@code TAI_LIEU}, {@code CONSTRUCTION}…).
 *     ⭐ Thêm 04/09/2026 (WS-40) vì <b>phạm vi công bố gắn vào loại này</b>: chỉ
 *     {@code PublicPortalService.LOAI_TEP_CONG_KHAI} ra được cổng thẳng, còn {@code TAI_LIEU} phải
 *     đi đường hẹp. Module nghiệp vụ cầm một {@code publicId} người dùng gửi lên thì <b>phải kiểm
 *     được nó là loại gì</b> trước khi đưa vào DTO — không kiểm thì một ảnh media gắn nhầm làm tài
 *     liệu sẽ ra một dòng có tên mà bấm vào là 404, đúng hình dạng §10.52.
 *     <p>⚠ Đây là hợp đồng NỘI BỘ giữa các module, không phải trường của API — ⛔ đừng trả nó
 *     nguyên si ra ngoài
 * @param downloadable {@code false} khi tệp còn đang chờ quét virus hoặc đã bị cách ly. Module
 *     nghiệp vụ phải hiện đúng trạng thái đó thay vì đưa ra một đường tải sẽ bị từ chối
 * @param validFrom ngày lập tài liệu (CN-02.3) — khác {@code createdAt} là ngày tải tệp lên. Hồ sơ
 *     hoàn công lập năm 2018 vẫn có thể được số hoá vào hôm nay, và báo cáo hỏi ngày lập chứ không
 *     hỏi ngày ai đó bấm nút tải
 * @param validUntil hạn hiệu lực tài liệu (giấy phép, chứng chỉ) — {@code null} nghĩa là không hạn
 */
public record AttachmentRef(
        UUID publicId,
        String ownerType,
        String originalName,
        String contentType,
        long sizeBytes,
        int fileVersion,
        String purpose,
        boolean downloadable,
        Instant createdAt,
        java.time.LocalDate validFrom,
        java.time.LocalDate validUntil) {}
