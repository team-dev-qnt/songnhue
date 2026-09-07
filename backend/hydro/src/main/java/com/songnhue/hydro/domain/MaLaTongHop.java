package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một <b>mã nguồn chưa khai</b>, đã gộp toàn bộ số đo đã tích được — T31.13.
 *
 * <h2>Vì sao bảng này tồn tại</h2>
 *
 * <p>Đo 01/09/2026: nguồn trả <b>28 mã</b>, hệ thống khai <b>19</b>. Chín mã còn lại
 * ({@code F01535 F01613 F01659 F01696 F01700 F01706 F01811 F01830 F01863}) ta không biết là trạm
 * nào, ở đâu, thuộc công trình nào — đó là <b>G8, thuộc Công ty</b>.
 *
 * <ul>
 *   <li>⛔ <b>Cấm tự tạo điểm đo từ mã lạ</b> (quy tắc parse 5). Bản suy đoán trước đó dò theo giá
 *       trị đo đã <b>sai 1/4 mã</b>.
 *   <li>⭐ <b>Nhưng cũng không vứt số đo đi</b>: nguồn không có API lịch sử, nên bỏ hai tháng là mất
 *       hai tháng của 9 trạm ấy <i>ngay cả sau khi</i> Công ty khai báo (quy tắc 18).
 * </ul>
 *
 * <p>Màn hình này là chỗ hai vế trên gặp nhau: nó <b>chỉ liệt kê</b>, và để con người bấm nút khai.
 *
 * <h2>⚠⚠ {@link #giaTriGanNhat} là số NGUYÊN VĂN NGUỒN, CHƯA QUY ĐỔI</h2>
 *
 * <p>Chưa biết mã này là loại chỉ số gì thì cũng chưa biết quy đổi về đâu — quy đổi bây giờ là đoán.
 * Nguồn {@code bhh40} trả mực nước bằng <b>cm</b> còn hệ thống lưu bằng <b>m</b>, nên một ô hiện
 * {@code 213} mà không kèm {@link #donViNguon} sẽ được đọc thành <i>213 mét</i>. Giao diện <b>bắt
 * buộc</b> hiện đơn vị cạnh con số.
 *
 * @param daKhaiThanhDiemDo đã có điểm đo mang đúng {@code api_code} này chưa. ⚠ {@code true} <b>không
 *     có nghĩa là xong</b>: số đo <i>mới</i> từ nay đi thẳng vào {@code hydro_readings}, nhưng
 *     {@link #soBanGhi} bản ghi <i>lịch sử</i> vẫn nằm lại ở bảng chưa khai cho tới khi có job
 *     chuyển. Nói ra được sự thật ấy là lý do cột này tồn tại.
 * @param maDiemDo mã nội bộ của điểm đo đã khai, {@code null} khi chưa khai
 */
public record MaLaTongHop(
        String apiCode,
        UUID nguonPublicId,
        String nguonCode,
        long soBanGhi,
        Instant lanDau,
        Instant lanGanNhat,
        BigDecimal giaTriGanNhat,
        String donViNguon,
        boolean daKhaiThanhDiemDo,
        String maDiemDo) {}
