package com.songnhue.operations.application;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.AttachmentUsagePort;

/**
 * Công trình nào đang dẫn tới một tệp — nửa {@code operations} của {@link AttachmentUsagePort}
 * (T40.26).
 *
 * <h3>⚠ Chỉ soi HAI cột tài liệu CÔNG BỐ, ⛔ không soi quan hệ sở hữu</h3>
 *
 * Tệp nằm trong <i>danh sách tài liệu</i> của một công trình thì xoá nó ở đó là thao tác bình
 * thường — chặn lại là làm hỏng chính tính năng. Thứ phải chặn là hai cột
 * {@code operating_procedure_attachment_public_id} và {@code protection_plan_attachment_public_id}:
 * chúng là <b>tham chiếu từ nơi khác</b>, và cổng công khai dựng liên kết từ chúng.
 *
 * <p>⛔ Đây đúng là cặp cột mà {@code ConstructionDocumentRefCleaner} gỡ về NULL <i>sau khi</i> tệp
 * bị xoá. Hai lớp bù nhau chứ ⛔ không thừa: cổng này <b>chặn</b> lượt xoá cố ý, lớp kia <b>dọn</b>
 * cho những tệp đã bị xoá từ trước khi có chốt chặn. Danh sách cột nằm ở hai nơi, nên
 * {@code ConstructionAttachmentUsageTest} đối chiếu cả hai với lược đồ thật (quy tắc 14).
 *
 * <h3>⛔ Đọc bằng native query, ⛔ không lọc phạm vi đơn vị</h3>
 *
 * Cùng lý do đã ghi ở {@code ConstructionStatusService}: người ngoài Xí nghiệp gọi lượt xoá thì câu
 * có lọc phạm vi trả rỗng, và tệp bị xoá <b>trong khi vẫn đang được dẫn</b>. Một câu hỏi về tính
 * toàn vẹn dữ liệu ⛔ không được phụ thuộc vào người đang đăng nhập (quy tắc 13).
 */
@Component
public class ConstructionAttachmentUsage implements AttachmentUsagePort {

    private static final int TRAN_KE = 5;

    /**
     * ⚠ Hai cột, và <b>chỉ</b> hai. Thêm cột tài liệu công bố thứ ba mà quên dòng ở đây thì lỗ
     * 404-trần mở lại đúng ở chỗ mới — bài kiểm đối chiếu danh sách này với lược đồ thật.
     */
    private static final String SQL =
            """
            SELECT name
              FROM constructions
             WHERE deleted_at IS NULL
               AND (operating_procedure_attachment_public_id = ?
                 OR protection_plan_attachment_public_id = ?)
             ORDER BY name
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public ConstructionAttachmentUsage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangDuocDanBoi(UUID attachmentPublicId) {
        return jdbc.queryForList(SQL, String.class, attachmentPublicId, attachmentPublicId, TRAN_KE).stream()
                .map(ten -> "công trình \"%s\"".formatted(ten))
                .toList();
    }
}
