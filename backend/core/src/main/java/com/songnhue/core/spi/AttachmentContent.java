package com.songnhue.core.spi;

/**
 * Nội dung một tệp, đọc thẳng từ kho để phục vụ cho người xem.
 *
 * @param contentType MIME <b>đã xác thực bằng magic bytes lúc tải lên</b>, không phải thứ trình
 *     duyệt khai. Trả nguyên nó vào header là cách duy nhất để trình duyệt không phải tự đoán
 * @param originalName chỉ dùng cho {@code Content-Disposition}; tên trong kho là chuỗi ngẫu nhiên
 */
public record AttachmentContent(byte[] content, String contentType, String originalName) {}
