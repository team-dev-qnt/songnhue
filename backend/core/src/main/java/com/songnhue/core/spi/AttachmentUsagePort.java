package com.songnhue.core.spi;

import java.util.List;
import java.util.UUID;

/**
 * <b>Ai đang DẪN tới tệp này</b> — T40.26.
 *
 * <h2>Vì sao cần một cổng nhiều bên cài</h2>
 *
 * Trước bản này, <b>3 trong 4</b> cửa xoá tệp không hỏi câu ấy. Chỉ đường CMS
 * ({@code MediaService.deleteFile}) tra tham chiếu rồi ném {@code CMS-2009}; ba cửa còn lại —
 * {@code DELETE /api/v1/attachments/&#123;id&#125;}, tài liệu công trình, đính kèm nhật ký bảo trì —
 * xoá thẳng và chỉ <i>gỡ</i> tham chiếu <b>sau khi đã xoá</b>.
 *
 * <p>Hệ quả đo được: gỡ một tệp mà bài viết đang dẫn trong HTML ⇒ liên kết trên cổng thành
 * <b>404 trần</b>. Người dân bấm "Quyết định phê duyệt" và nhận trang lỗi; quản trị viên mở màn
 * hình thì thấy mọi thứ bình thường.
 *
 * <h2>⛔ Chốt chặn nằm ở chỗ dữ liệu ĐI QUA, không ở từng nơi gọi (quy tắc 12)</h2>
 *
 * Đặt phép tra vào từng controller là ba bản sao phải nhớ, và cửa thứ tư ra đời sẽ lại quên — đúng
 * hình dạng đã lặp lại năm lần với đệm cổng (§10.70). Nên nó nằm ở
 * {@code AttachmentService.delete}, và mỗi module tự khai phần của mình qua cổng này.
 *
 * <p>Spring gom mọi bean cài cổng này thành {@code List} — cùng khuôn {@code JobHandler} của
 * {@code JobWorker}. Module mới chỉ cần thêm một bean, ⛔ không phải sửa {@code core}.
 *
 * <h2>⚠ "Dẫn tới" khác "sở hữu"</h2>
 *
 * Một tệp <b>thuộc về</b> danh sách tài liệu của một công trình thì xoá nó ở đó là thao tác bình
 * thường — chặn lại là làm hỏng chính tính năng. Thứ phải chặn là <b>tham chiếu từ nơi khác</b>:
 * HTML của bài viết dẫn tới tệp, hoặc cột "Quy trình vận hành" của công trình trỏ vào nó.
 * Bên cài ⛔ không được báo quan hệ sở hữu.
 */
public interface AttachmentUsagePort {

    /**
     * Mô tả ngắn những chỗ đang dẫn tới tệp — rỗng nghĩa là ⛔ không ai dẫn.
     *
     * <p>Trả <b>mô tả cho người đọc</b> (<i>"bài viết \"Thông báo lịch tưới\""</i>) chứ ⛔ không trả
     * mã: câu lỗi đi thẳng ra màn hình, và một danh sách UUID ⛔ không giúp người vận hành biết phải
     * đi gỡ ở đâu.
     *
     * <p>⚠ Bên cài nên tự giới hạn số dòng trả về. Một tệp bị 400 bài dẫn thì câu lỗi ⛔ không cần
     * kể hết — nó cần nói đủ để người dùng biết chuyện gì đang xảy ra.
     */
    List<String> dangDuocDanBoi(UUID attachmentPublicId);
}
