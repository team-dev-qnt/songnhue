package com.songnhue.core.spi;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Thứ một {@link JobHandler} nhận được khi chạy.
 *
 * <p>Cố ý <b>không</b> đưa cả entity {@code Job} vào đây: handler mà sửa được trạng thái job thì
 * việc "job này đã xong hay chưa" có hai nơi quyết định, và nơi thua cuộc là nơi im lặng. Handler
 * chỉ đọc dữ liệu đầu vào, báo tiến độ và ghi <i>con trỏ kết quả</i>; kết luận thành/bại do
 * {@link JobWorker} rút ra từ việc handler có ném ngoại lệ hay không.
 *
 * <h2>⚠⚠ {@link #resultSink} nối một nửa cặp đọc–ghi đã hở từ Phase 0 — WS-34/T34.7</h2>
 *
 * <p>Cột {@code jobs.result} có <b>ba nơi đọc</b> ({@code JobDtos}, {@code JobService.findJob},
 * và chính {@code JobWorker.succeed()} — câu {@code fresh.markSucceeded(fresh.getResult())} đọc lại
 * giá trị ấy để giữ nó qua lượt ghi trạng thái) và, cho tới đợt này, <b>không một nơi ghi nào</b>.
 * Javadoc của {@code JobRef.resultRef} thì mô tả đích danh tính năng chưa từng tồn tại:
 * <i>"con trỏ tới kết quả khi đã xong (VD khoá tệp báo cáo trong kho)"</i>.
 *
 * <p>Đó là luật 15 ở dạng khó thấy nhất: một cột <b>có người đọc</b>, có DTO phát ra dây, có javadoc
 * giải thích — nên mọi lượt rà đều kết luận là "đã xong". Thứ duy nhất vắng mặt là đường ghi, và
 * triệu chứng của nó là một trường luôn {@code null} mà ⛔ không ai coi là bất thường.
 */
public record JobContext(
        UUID jobPublicId,
        String jobType,
        String payload,
        Long requestedBy,
        IntConsumer progressSink,
        Consumer<String> resultSink) {

    /**
     * Báo tiến độ 0–100 để người dùng nhìn thấy trên UI.
     *
     * <p>Việc dài mà không báo tiến độ thì người dùng chỉ thấy "đang chạy" hàng phút và sẽ bấm lại —
     * tạo thêm job trùng.
     */
    public void progress(int percent) {
        progressSink.accept(percent);
    }

    /**
     * Ghi <b>con trỏ</b> tới thứ việc nền vừa tạo ra — VD khoá tệp báo cáo trong kho.
     *
     * <h2>⚠⚠ Tham số PHẢI là một tài liệu JSON hợp lệ</h2>
     *
     * <p>Cột {@code jobs.result} là {@code JSONB} và {@code Job.result} mang
     * {@code @JdbcTypeCode(SqlTypes.JSON)}. Truyền một chuỗi trần — {@code "hyd/abc.csv"} — làm
     * PostgreSQL từ chối với <i>"invalid input syntax for type json"</i>, và lượt việc nền hỏng
     * <b>sau khi đã làm xong toàn bộ công việc</b>.
     *
     * <p>⭐ Tên phương thức mang chữ {@code Json} chính vì thế: kiểu tham số là {@code String} nên
     * trình biên dịch ⛔ không đỡ được gì, và đây là chỗ duy nhất còn nói ra ràng buộc ấy trước khi
     * người viết handler gõ dòng gọi. Đo được ngày 4/9: lời gọi đầu tiên trong lịch sử kho này
     * truyền một chuỗi trần và hỏng ở đúng đây.
     *
     * <p>⛔ Đây là <b>con trỏ</b>, ⛔ không phải nội dung: cột {@code jobs.result} nằm nguyên văn
     * trong mọi bản sao lưu CSDL, nên một byte dữ liệu nghiệp vụ đặt vào đây là một byte đi ra khỏi
     * vòng đời mà nó được thiết kế cho. Cùng luật với {@code jobs.payload} (⛔ không bao giờ chứa
     * credential — {@code conventions.md} §4.7).
     *
     * <p>⚠ Gọi <b>trước</b> khi handler trả về. {@code JobWorker.succeed()} đọc lại giá trị từ CSDL
     * ngay sau đó, nên một lời gọi muộn hơn sẽ ⛔ không kịp và trường ấy im lặng ở lại {@code null}.
     */
    public void resultJson(String json) {
        resultSink.accept(json);
    }
}
