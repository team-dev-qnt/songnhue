package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Một số đo <b>vừa bóc khỏi dây</b> — chưa biết thuộc điểm đo nào.
 *
 * <p>⭐ Đây là điểm hẹn duy nhất giữa WS-30 (adapter) và WS-31 (poller). Khác biệt với
 * {@link ReadingRow} nằm ở đúng một chỗ và đó là toàn bộ lý do có hai kiểu: bản ghi này còn mang
 * {@code apiCode} — <b>một chuỗi vô nghĩa với con người</b> — trong khi {@code ReadingRow} đã tra ra
 * {@code stationId}. Gộp hai kiểu làm một là mời việc tra cứu xảy ra ở hai chỗ.
 *
 * <h2>⚠ Giữ giá trị THÔ, quy đổi bằng một hàm</h2>
 *
 * <p>Nguồn trả số nguyên <b>cm</b> ({@code 493} = 4,93 m). Hai người tiêu thụ cần hai thứ khác nhau:
 *
 * <ul>
 *   <li>{@code hydro_readings} cần <b>giá trị đã quy đổi</b> (m, scale 3) — {@link #giaTri()};
 *   <li>{@code hydro_unmapped_readings} cần <b>giá trị nguyên trạng kèm đơn vị của nguồn</b>
 *       ({@link #giaTriTho()} + {@link #donViTho()}): mã ấy chưa khai nên ta <i>chưa biết</i> loại
 *       chỉ số của nó, mà đơn vị chuẩn hoá là thuộc tính của loại chỉ số. Quy đổi sang mét một số đo
 *       chưa biết là lượng mưa hay mực nước là bịa ra một đơn vị.
 * </ul>
 *
 * <p>⇒ Bản ghi giữ <b>một</b> sự thật (số thô + đơn vị nguồn) và quy đổi là một <b>hàm dẫn xuất</b>.
 * Nếu giữ cả hai làm hai trường thì có hai chỗ để lệch nhau, và chỗ lệch ấy là sai số liệu —
 * {@code architecture-review.md} §10.32 đã trả giá đúng loại này với toạ độ.
 */
public record TelemetryReading(String apiCode, Instant measuredAt, BigDecimal giaTriTho, String donViTho) {

    /** Đơn vị duy nhất nguồn {@code bhh40} trả về hôm nay — {@code getmn.aspx} chỉ có mực nước. */
    public static final String DON_VI_CM = "cm";

    /**
     * Số chữ số thập phân của giá trị chuẩn hoá — quy ước B6, và là scale của
     * {@code hydro_readings.reading_value NUMERIC(12,3)}.
     */
    public static final int SO_LE_CHUAN = 3;

    private static final BigDecimal MOT_TRAM = new BigDecimal("100");

    public TelemetryReading {
        Objects.requireNonNull(apiCode, "apiCode");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(giaTriTho, "giaTriTho");
        if (!DON_VI_CM.equals(donViTho)) {
            // ⚠ Đây là ĐIỂM CẮM của nguồn thứ hai (T30.10 — lượng mưa, G3-a). Thêm một đơn vị thì
            //   thêm một nhánh ở ĐÂY và một nhánh ở `giaTri()`, chứ ⛔ không nới thành "nhận mọi
            //   chuỗi": một đơn vị không ai biết quy đổi thế nào mà vẫn đi tiếp là một số sai đi
            //   thẳng vào bảng chính. Từ chối lớn tiếng ở hàm dựng (quy tắc 16).
            throw new IllegalArgumentException("Chưa biết quy đổi đơn vị '" + donViTho
                    + "' — hôm nay nguồn duy nhất trả '" + DON_VI_CM + "'. Thêm nguồn mới thì thêm "
                    + "nhánh ở TelemetryReading, ⛔ đừng nới điều kiện này.");
        }
    }

    /**
     * Giá trị đã quy đổi về đơn vị chuẩn hoá — <b>quy tắc parse 7</b>.
     *
     * <p>⛔ {@code BigDecimal}, ⛔ cấm {@code double}: {@code 493 / 100.0} cho ra
     * {@code 4.930000000000000159872115546022541821002960205078125} và mọi ngưỡng cảnh báo sau đó so
     * sánh với một con số không phải con số ta nghĩ. {@code RoundingMode.HALF_UP} khớp thói quen làm
     * tròn của người Việt và khớp quy ước B6.
     *
     * <p>⚠ Hôm nay {@code cm → m} luôn chia hết nên {@code HALF_UP} chưa bao giờ phải làm gì. Vẫn
     * khai tường minh: {@code divide} không có {@code RoundingMode} sẽ <b>ném</b>
     * {@code ArithmeticException} ngay khi nguồn gửi một số thập phân (regex của quy tắc 4 <i>cho
     * phép</i> {@code -?\d+([.,]\d+)?}), và nó sẽ ném ở giữa một lượt ingest.
     */
    public BigDecimal giaTri() {
        return giaTriTho.divide(MOT_TRAM, SO_LE_CHUAN, RoundingMode.HALF_UP);
    }

    /** Khoá chống trùng trong phạm vi một response — quy tắc parse 8, vế phía adapter. */
    public String khoaTrung() {
        return apiCode + '@' + measuredAt;
    }
}
