package com.songnhue.core.spi;

import java.io.InputStream;

/**
 * Nội dung một tệp, đọc thẳng từ kho để phục vụ cho người xem.
 *
 * <h2>⛔⛔ {@code InputStream}, ⛔ KHÔNG phải {@code byte[]} — T28.35, sửa 04/09/2026</h2>
 *
 * <p>Bản cũ mang {@code byte[] content}, nên ba endpoint <b>công khai, không đăng nhập</b>
 * ({@code /public/files/…} · {@code /public/article-documents/…} ·
 * {@code /public/constructions/documents/…}) nạp <b>trọn tệp vào heap</b> ở mỗi lượt tải. Trần dung
 * lượng đang seed là <b>120 MB</b> cho video và 50 MB cho tài liệu, và hệ chạy <b>một node</b>.
 *
 * <p>⚠ Phép nhân là chỗ đau: mười lượt tải đồng thời một tệp 120 MB là <b>1,2 GB</b> heap cho một
 * việc lẽ ra ⛔ không cần bộ nhớ nào. Bề mặt ấy <b>không đăng nhập</b>, nên ⛔ không có gì hạn chế số
 * người bấm — rate-limit đếm theo IP, mà một trang tin dẫn link thì các IP là khác nhau.
 *
 * <p>⭐ Nghịch lý đáng ghi: javadoc của {@code ObjectStorage#presignedGetUrl} — trong <b>cùng kho
 * này</b>, từ Phase 0 — đã viết đúng câu ấy: <i>"Tệp đi qua ứng dụng thì mỗi lượt tải chiếm một
 * luồng và một connection suốt thời gian truyền"</i>. Lý lẽ có sẵn, ⛔ chỉ là ⛔ không ai áp nó cho
 * đường công khai.
 *
 * <h2>⛔ Vì sao ⛔ KHÔNG chuyển hẳn sang presigned URL</h2>
 *
 * <p>Presigned URL trỏ <b>thẳng vào MinIO</b>, và MinIO ở triển khai này ⛔ không lộ ra Internet —
 * nó nằm trong mạng nội bộ của compose. Một lượt chuyển hướng sang địa chỉ ấy là một liên kết
 * <b>chết</b> với mọi trình duyệt bên ngoài. Phát trực tiếp giữ nguyên URL, nguyên cấu hình nginx,
 * nguyên hành vi cache — và bỏ đúng phần buffer.
 *
 * <h2>⚠ Người gọi PHẢI đóng {@link #content()}</h2>
 *
 * <p>Đây là hợp đồng, ⛔ không phải lời khuyên: một {@code InputStream} của MinIO giữ một kết nối
 * HTTP trong pool. Rò một lượt mỗi lần tải thì pool cạn sau vài trăm lượt và triệu chứng là
 * <b>toàn hệ treo lúc gọi kho</b>, ⛔ không phải một lỗi ở đường tải. {@code StreamingResponseBody}
 * ở các controller dùng {@code try-with-resources}.
 *
 * @param content ⚠ <b>chỉ đọc được MỘT lần</b>, và người gọi chịu trách nhiệm đóng
 * @param contentType MIME <b>đã xác thực bằng magic bytes lúc tải lên</b>, không phải thứ trình
 *     duyệt khai. Trả nguyên nó vào header là cách duy nhất để trình duyệt không phải tự đoán
 * @param originalName chỉ dùng cho {@code Content-Disposition}; tên trong kho là chuỗi ngẫu nhiên
 * @param sizeBytes lấy từ CSDL, ⛔ không từ kho — để đặt được {@code Content-Length} <b>trước</b> khi
 *     byte đầu tiên chảy. Thiếu nó thì phản hồi rơi về {@code chunked}, và trình duyệt ⛔ không hiện
 *     được thanh tiến trình cho một tệp 100 MB
 */
public record AttachmentContent(InputStream content, String contentType, String originalName, long sizeBytes) {}
