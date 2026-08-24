/**
 * Common Platform — nền chung mà MỌI module bắt buộc dùng (conventions.md §2).
 *
 * <p><b>Đây là ngoại lệ của quy tắc "chỉ import spi/ của module khác."</b> Module nghiệp vụ được
 * phép import trực tiếp {@code com.songnhue.core.common.*}, vì đây không phải dịch vụ nghiệp vụ mà
 * là hạ tầng dùng chung: envelope, exception, mã lỗi, filter, utils, base entity.
 *
 * <p>Rule ArchUnit (WS-10 / T10.2) phải phản ánh đúng điều này: cho phép import chéo tới
 * {@code <module>.spi.*} <b>và</b> {@code core.common.*}, chặn mọi thứ còn lại.
 *
 * <p>Nguyên tắc: <b>cái gì lặp ≥ 2 lần thì nằm ở đây, module không tự chế bản riêng.</b> Envelope
 * riêng, hàm bỏ dấu tiếng Việt riêng, cách làm tròn BigDecimal riêng — mỗi bản sao là một chỗ để
 * hành vi lệch nhau mà không ai phát hiện.
 */
package com.songnhue.core.common;
