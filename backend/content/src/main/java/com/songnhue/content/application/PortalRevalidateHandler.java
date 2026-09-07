package com.songnhue.content.application;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.content.infra.PortalRevalidateClient;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * Bắn yêu cầu dựng lại trang tới cổng công khai — T16.5.
 *
 * <h2>Vì sao đi qua hàng đợi thay vì gọi thẳng trong luồng xuất bản</h2>
 *
 * <ul>
 *   <li><b>Không gọi mạng bên trong giao dịch.</b> Cổng chậm hoặc chết thì lượt bấm Duyệt treo theo,
 *       và cùng lúc giữ một giao dịch CSDL mở suốt thời gian đó.
 *   <li><b>Có thử lại.</b> Cổng đang khởi động lại lúc biên tập viên duyệt bài là chuyện bình
 *       thường; gọi thẳng thì bài đã duyệt xong mà trang không bao giờ được dựng lại, và không ai
 *       biết vì việc chính đã báo thành công.
 *   <li><b>Có dấu vết.</b> Bảng {@code jobs} ghi lại từng lượt, kể cả lượt hỏng.
 * </ul>
 *
 * <p>⚠ <b>Chạy lại được</b> — điều kiện của {@link JobHandler}: gọi dựng lại cùng một đường dẫn hai
 * lần chỉ tốn thêm một lượt dựng, không sinh dữ liệu trùng.
 *
 * <p>⭐ Đây là {@code JobHandler} <b>đầu tiên nằm ngoài Core</b>, và cũng là lý do {@code JobHandler}
 * phải chuyển sang {@code core.spi} ở WS-16.
 */
@Component
public class PortalRevalidateHandler implements JobHandler {

    private final PortalRevalidateClient portal;
    private final ObjectMapper objectMapper;

    public PortalRevalidateHandler(PortalRevalidateClient portal, ObjectMapper objectMapper) {
        this.portal = portal;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return CmsJobTypes.PORTAL_REVALIDATE;
    }

    /** Gọi ra ngoài mạng nên để cao hơn mặc định: cổng khởi động lại là chuyện thường. */
    @Override
    public short maxAttempts() {
        return 5;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        JsonNode path = payload.get("path");
        JsonNode tag = payload.get("tag");

        if (path != null && !path.isNull()) {
            portal.revalidatePath(path.asText());
        }
        if (tag != null && !tag.isNull()) {
            portal.revalidateTag(tag.asText());
        }
    }
}
