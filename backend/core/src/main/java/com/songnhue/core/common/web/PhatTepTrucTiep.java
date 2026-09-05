package com.songnhue.core.common.web;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.songnhue.core.spi.AttachmentContent;

/**
 * Chuyển một {@link AttachmentContent} thành thân phản hồi <b>phát trực tiếp</b> — <b>T28.35</b>.
 *
 * <h2>⛔ Vì sao một lớp dùng chung, ⛔ không phải một phương thức riêng ở mỗi controller</h2>
 *
 * <p>Ba endpoint công khai cùng làm đúng một việc, và việc ấy có <b>một chi tiết chịu lực dễ quên
 * nhất</b>: <b>đóng luồng</b>. Chép khối này ra ba chỗ thì chỗ thứ tư — cái ra đời sáu tháng nữa —
 * sẽ chép từ một trong ba và có xác suất chép hụt {@code try-with-resources}. Rò một luồng mỗi lượt
 * tải thì pool kết nối MinIO cạn sau vài trăm lượt, và triệu chứng là <b>toàn hệ treo lúc gọi
 * kho</b> — ⛔ không phải một lỗi ở đường tải, nên nó ⛔ không chỉ vào chỗ sai.
 *
 * <p>⇒ Đúng luật 12: đặt bảo đảm ở <b>chỗ dữ liệu đi qua</b>, ⛔ không ở nơi gọi.
 *
 * <h2>⚠ Ngoại lệ ném ra ở ĐÂY ⛔ KHÔNG đổi được mã trạng thái</h2>
 *
 * <p>{@code StreamingResponseBody} chạy <b>sau khi</b> header đã gửi đi. Một lỗi kho lúc này cho ra
 * một phản hồi <b>200 bị cắt ngang</b>, ⛔ không phải 500 — trình duyệt hiện "tải hỏng", ⛔ không
 * hiện trang lỗi. Vì thế mọi quyết định <i>có phục vụ tệp này không</i> (không tồn tại · sai loại
 * chủ sở hữu · chưa quét virus xong) phải nằm <b>trước</b> lượt gọi này, và ở
 * {@code AttachmentService.readForPublic} nó đúng như vậy.
 *
 * <p>⇒ Ở đây chỉ còn lỗi <b>hạ tầng</b>, và nó được ghi WARN kèm tên tệp: một lượt tải cụt ⛔ không
 * được im lặng, vì người dùng ⛔ không có cách nào báo lại điều gì hữu ích.
 */
public final class PhatTepTrucTiep {

    private static final Logger log = LoggerFactory.getLogger(PhatTepTrucTiep.class);

    /**
     * ⚠ 8 KB — bằng bộ đệm mặc định của {@code InputStream.transferTo}, ghi ra ở đây để con số là
     * một quyết định đọc được chứ ⛔ không phải một mặc định vô hình.
     */
    private static final int BO_DEM = 8 * 1024;

    private PhatTepTrucTiep() {}

    public static StreamingResponseBody cua(AttachmentContent tep) {
        return out -> {
            // ⭐ `try-with-resources` là TOÀN BỘ lý do lớp này tồn tại — xem javadoc lớp.
            try (InputStream nguon = tep.content()) {
                byte[] dem = new byte[BO_DEM];
                int n;
                while ((n = nguon.read(dem)) != -1) {
                    out.write(dem, 0, n);
                }
            } catch (IOException e) {
                // ⚠ Ghi WARN rồi ném lại: header đã gửi nên ⛔ không đổi được mã trạng thái, và một
                //   lượt tải cụt trong im lặng là thứ ⛔ không ai truy được. Ném lại để container
                //   đóng kết nối thay vì trả một tệp thiếu byte mà trình duyệt tưởng là đủ.
                log.warn("Lượt tải tệp '{}' bị đứt giữa chừng: {}", tep.originalName(), e.toString());
                throw e;
            }
        };
    }
}
