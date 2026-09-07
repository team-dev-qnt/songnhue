package com.songnhue.core.spi;

import java.time.Duration;
import java.util.Optional;

/**
 * Kho tệp <b>do máy sinh ra</b> — bản kết xuất báo cáo của việc nền (WS-34/T34.7).
 *
 * <h2>⚠⚠ Vì sao KHÔNG dùng {@link AttachmentPort}</h2>
 *
 * <p>{@code AttachmentPort} là đường <b>tải lên</b>: nó kiểm magic bytes, xếp hàng quét virus, tính
 * hạn mức, và buộc mỗi tệp thuộc về một bản ghi nghiệp vụ ({@code ownerType} + {@code ownerId}).
 * Cả bốn thứ ấy đều <b>vô nghĩa hoặc sai</b> với một tệp CSV do chính máy chủ vừa dựng ra:
 *
 * <ul>
 *   <li>ta biết chính xác từng byte trong đó — ⛔ không có gì để quét;
 *   <li>nó ⛔ <b>không thuộc về</b> bản ghi nào: nó là kết quả của một <i>câu hỏi</i>, và câu hỏi ấy
 *       hết hạn sau 24 giờ;
 *   <li>đi qua hàng đợi quét virus nghĩa là bản kết xuất ⛔ <b>không tải xuống được</b> cho tới khi
 *       một việc nền thứ hai chạy xong — người dùng bấm Xuất rồi nhìn một nút chết.
 * </ul>
 *
 * <p>⭐ Tiền lệ đã có trong chính kho này: {@code AuditArchiveHandler} ghi bản kết xuất nhật ký
 * <b>thẳng vào {@code ObjectStorage}</b>, ⛔ không qua {@code AttachmentPort}. Cổng này chỉ mở đúng
 * con đường ấy cho module nghiệp vụ — {@code ObjectStorage} nằm ở {@code core.infra} nên
 * {@code hydro} ⛔ không import được (ArchUnit chặn), và đó là ranh giới đúng.
 *
 * <h2>⭐ Bucket này đã được cấp phát từ 13/8 và cho tới WS-34 thì KHÔNG AI ĐỌC</h2>
 *
 * <p>Đo được ngày 4/9: {@code MINIO_BUCKET_REPORT} khai ở <b>4 tệp env</b>, được {@code minio-init}
 * tạo ở cả compose hạ tầng lẫn compose production, được {@code push-offsite.sh} sao lưu ra ngoài
 * hằng đêm, và mang {@code @NotBlank(message = "Thiếu MINIO_BUCKET_REPORT")} nên <b>thiếu nó là ứng
 * dụng ⛔ không khởi động được</b>. {@code StorageProperties.getBucketReport()} thì ⛔ <b>không một
 * dòng mã nào gọi</b>.
 *
 * <p>Đúng luật 15 — <i>một công tắc chưa ai đọc là một lỗi, không phải việc để dành</i> — và ở dạng
 * đắt nhất: nó có fail-fast, có sao lưu, có tài liệu, nên mọi lượt rà đều đọc nó là <i>đã xong</i>.
 * Cổng này là vế đọc.
 */
public interface ReportFilePort {

    /**
     * Ghi một bản kết xuất và trả về <b>khoá đối tượng</b>.
     *
     * <p>⛔ Khoá trả về là thứ được lưu vào {@code jobs.result} — ⛔ <b>không</b> lưu nội dung, ⛔
     * <b>không</b> lưu đường dẫn có chữ ký. Cột {@code jobs.result} nằm nguyên văn trong mọi bản sao
     * lưu CSDL, và một đường dẫn có chữ ký thì hết hạn trong khi bản sao lưu thì không.
     *
     * @param khoa khoá đối tượng, ⛔ không được chứa {@code ..} hay ký tự đường dẫn tuyệt đối
     */
    void luu(String khoa, byte[] noiDung, String contentType);

    /** @return rỗng khi khoá ⛔ không tồn tại — gồm cả trường hợp bản kết xuất đã bị dọn vì quá hạn */
    Optional<byte[]> doc(String khoa);

    /**
     * Dọn bản kết xuất quá hạn.
     *
     * <p>⚠ Thiếu bước này thì "TTL 24 giờ" chỉ là một câu kiểm ở endpoint tải: tệp vẫn nằm nguyên
     * trong bucket và vẫn được {@code push-offsite.sh} sao lưu ra ngoài <b>mỗi đêm, mãi mãi</b>. Một
     * hạn dùng chỉ được thi hành ở tầng đọc là một hạn dùng ⛔ không có thật.
     *
     * @return số đối tượng đã xoá
     */
    int donQuaHan(String tienTo, Duration hanDung);
}
