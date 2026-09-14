package com.songnhue.core.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt cảnh báo ngưỡng thuỷ văn, dạng module khác đọc được — CN-02.10 (BC-06).
 *
 * <h2>⛔ {@code peakValue} và {@code triggerValue} là HAI con số khác nhau</h2>
 *
 * <p>{@code triggerValue} là số đo <b>làm cảnh báo bật lên</b>; {@code peakValue} là <b>đỉnh</b>
 * của cả đợt. Một báo cáo chỉ in giá trị kích hoạt sẽ nói *"vượt ngưỡng 0,2 m"* cho một trận lũ
 * mà đỉnh cao hơn ngưỡng 1,8 m — đúng câu người đọc báo cáo cần nhất.
 *
 * <p>⚠ {@code BigDecimal}, ⛔ không {@code double} (quy tắc 2). Mực nước là số đo.
 *
 * @param endedAt {@code null} = cảnh báo <b>đang xảy ra</b>. ⛔ Đừng thay bằng {@code now()} để
 *     *"cho có"*: một đợt chưa kết thúc và một đợt vừa kết thúc lúc này là hai sự thật khác nhau
 */
public record AlertEventRef(
        UUID publicId,
        String tenDiemDo,
        String maDiemDo,
        String loaiChiSo,
        String mucCanhBao,
        String trangThai,
        Instant startedAt,
        Instant endedAt,
        BigDecimal triggerValue,
        BigDecimal peakValue,
        String lyDo) {}
