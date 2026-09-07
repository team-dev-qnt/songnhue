package com.songnhue.operations.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.AttachmentDeletedEvent;
import com.songnhue.core.spi.PortalCachePort;

/**
 * Gỡ tham chiếu tài liệu công bố khi tệp bị xoá — <b>T28.34</b>, vế {@code operations}.
 *
 * <p>Bối cảnh đầy đủ ở javadoc {@link AttachmentDeletedEvent}. Tóm tắt: hai cột dưới đây khai
 * {@code ON DELETE SET NULL} và ràng buộc ấy <b>chưa từng bắn</b>, vì tệp chỉ bị xoá <i>mềm</i>.
 * Hệ quả là cổng công khai dựng một liên kết tải về trả <b>404 câm</b>.
 *
 * <h2>⚠ Dùng {@code JdbcTemplate}, ⛔ không nạp entity</h2>
 *
 * <p>Đây là <b>đúng câu lệnh</b> mà {@code ON DELETE SET NULL} lẽ ra chạy — một lượt {@code UPDATE}
 * theo điều kiện, ⛔ không phải một lượt sửa hồ sơ do người dùng thực hiện. Nạp entity ra để gọi
 * setter sẽ kéo theo audit log của một <i>hành vi người dùng</i> ⛔ không có thật, và kéo theo cả
 * bộ lọc phạm vi đơn vị — mà tệp bị xoá thì phải được gỡ khỏi <b>mọi</b> công trình, kể cả công
 * trình ngoài phạm vi người đang bấm nút. Đó chính là hình dạng lỗi luật 13 đã trả giá.
 *
 * <p>⚠ Vì thế lượt gỡ này <b>ghi INFO kèm số hàng</b>: một thay đổi dữ liệu ⛔ không đi qua audit
 * thì phải để lại dấu vết ở chỗ khác, ⛔ không được im lặng.
 */
@Component
public class ConstructionDocumentRefCleaner {

    private static final Logger log = LoggerFactory.getLogger(ConstructionDocumentRefCleaner.class);

    /**
     * ⚠ Một câu cho <b>cả hai</b> cột, ⛔ không hai câu.
     *
     * <p>Cùng một tệp có thể được khai ở cả hai ô của cùng một công trình (người nhập chọn nhầm), và
     * hai câu {@code UPDATE} nối tiếp thì lượt đếm hàng của câu sau nói dối. Quan trọng hơn: hai câu
     * là hai chỗ phải nhớ khi thêm cột thứ ba.
     */
    private static final String SQL_GO =
            """
            UPDATE constructions
               SET operating_procedure_attachment_public_id =
                       CASE WHEN operating_procedure_attachment_public_id = ? THEN NULL
                            ELSE operating_procedure_attachment_public_id END,
                   protection_plan_attachment_public_id =
                       CASE WHEN protection_plan_attachment_public_id = ? THEN NULL
                            ELSE protection_plan_attachment_public_id END
             WHERE operating_procedure_attachment_public_id = ?
                OR protection_plan_attachment_public_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final PortalCachePort portalCache;

    public ConstructionDocumentRefCleaner(JdbcTemplate jdbc, PortalCachePort portalCache) {
        this.jdbc = jdbc;
        this.portalCache = portalCache;
    }

    /**
     * ⚠ {@code @EventListener} thường, ⛔ <b>không</b> {@code @TransactionalEventListener}.
     *
     * <p>Việc này phải <b>nguyên tử</b> với lượt xoá — xem javadoc {@link AttachmentDeletedEvent}.
     * Chạy sau commit là để lại một khe hở trong đó cổng trỏ vào tệp đã chết, tức khuyết tật đang
     * được sửa chỉ thu nhỏ lại chứ ⛔ không biến mất.
     *
     * <p>⛔ ⛔ Cố ý <b>không</b> lọc theo {@code ownerType}: một tệp tải lên với
     * {@code ownerType = "ARTICLE"} vẫn có thể được ai đó khai vào ô "Quy trình vận hành" — lược đồ
     * ⛔ không cấm điều đó, nên bộ lọc sẽ bỏ sót đúng trường hợp khó thấy nhất. Câu {@code UPDATE}
     * dưới đây tự nó đã là bộ lọc chính xác.
     */
    @EventListener
    public void onAttachmentDeleted(AttachmentDeletedEvent event) {
        int soHang = jdbc.update(SQL_GO, event.publicId(), event.publicId(), event.publicId(), event.publicId());
        if (soHang > 0) {
            log.info(
                    "Gỡ tham chiếu tài liệu công bố của {} công trình sau khi xoá tệp {} — cổng sẽ hiện dấu "
                            + "gạch thay vì một liên kết trả 404",
                    soHang,
                    event.publicId());
            // ⚠ Cột này được cổng công bố ⇒ lượt gỡ LÀ một thay đổi nội dung cổng (T35.9, nhánh biên
            //   tập). ⛔ Không xoá đệm thì cổng còn dựng liên kết chết thêm tối đa 5 phút nữa.
            portalCache.constructionsChanged();
        }
    }
}
