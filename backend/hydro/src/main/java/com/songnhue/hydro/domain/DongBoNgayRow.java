package com.songnhue.hydro.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một hàng thô của BC-13, phần dưới — tổng hợp {@code sync_logs} theo (ngày × nguồn). T34.3.
 *
 * <h2>⭐ Vì sao bốn kết cục đếm RIÊNG, ⛔ không gộp thành "tỷ lệ thành công"</h2>
 *
 * <p>{@link #soBoQua} là số lượt <b>đã bỏ vì mọi điểm đo đã có bản ghi của khung hiện tại</b>.
 * Con số ấy cao là <i>tốt</i> — rate-limit đang làm đúng việc, và với một nguồn cập nhật 10 phút mà
 * ta hỏi 2 phút một lần thì phần lớn lượt gọi <b>phải</b> rơi vào đây. Gộp nó vào "thành công" làm
 * biến mất đúng sự phân biệt mà người trực cần: một ngày 720 lượt "thành công" với
 * {@link #soGhiMoi} bằng 0 là một ngày <b>mất trắng</b>, còn 600 lượt bỏ qua + 120 lượt ghi mới là
 * một ngày hoàn hảo. Hai tình huống ấy cho cùng một tỷ lệ.
 *
 * <p>⚠ Cùng bài học §10.68-B: bản cũ của bước SSH trong CD cho <i>cùng một vân tay</i> với ba nguyên
 * nhân cần ba cách xử lý ngược nhau.
 *
 * @param soTrung số bản ghi nhận về nhưng đã có trong bảng — bình thường, ⛔ không phải lỗi
 * @param soMaLa số bản ghi mang {@code api_code} chưa ai khai (⬜ G8) — số này &gt; 0 là một việc
 *     phải làm, ⛔ không phải một sự cố
 * @param hongGanNhat mốc lượt hỏng gần nhất trong ngày, hoặc {@code null} nếu ngày ấy ⛔ không có
 *     lượt nào hỏng
 */
public record DongBoNgayRow(
        LocalDate ngay,
        String sourceCode,
        String sourceName,
        int soLuot,
        int soThanhCong,
        int soMotPhan,
        int soHong,
        int soBoQua,
        long soNhan,
        long soGhiMoi,
        long soTrung,
        long soMaLa,
        Instant hongGanNhat) {}
