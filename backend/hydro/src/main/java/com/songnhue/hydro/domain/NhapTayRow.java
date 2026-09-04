package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Một số đo <b>người trực nhập tay</b>, sẵn sàng ghi xuống {@code hydro_readings} — T32.7.
 *
 * <h2>⭐ Vì sao là một kiểu RIÊNG, ⛔ không phải {@code ReadingRow} có thêm hai trường</h2>
 *
 * <p>{@link ReadingRow} ⛔ <b>từ chối</b> {@link ReadingSource#MANUAL} ngay ở hàm dựng, và javadoc
 * của nó đã hẹn kiểu này ra đời ở WS-32. Lý do không phải khẩu vị:
 *
 * <ul>
 *   <li>Thêm {@code createdBy}/{@code note} vào {@code ReadingRow} là thêm hai trường mà <b>mọi</b>
 *       lời gọi của đường ingest truyền {@code null} — một nửa cặp đọc–ghi ngay từ lúc sinh ra
 *       (luật 15). Ràng buộc {@code ck_hydro_readings_nguoi_nhap} khi ấy chỉ còn được ép ở CSDL, và
 *       nó sẽ vỡ ở <i>giữa một lượt ingest</i>, cách chỗ viết sai rất xa.
 *   <li>Ngược lại, kiểu này ⛔ <b>không có</b> {@code rawLogId}: không có lượt gọi nào thì không có
 *       nguyên văn response nào. Một trường {@code rawLogId} ở đây sẽ luôn {@code null}, và cột
 *       {@code raw_log_id} rỗng chính là <b>cách phân biệt</b> dòng máy ghi với dòng người ghi khi
 *       truy ngược sau này.
 * </ul>
 *
 * <h2>⛔ Chất lượng luôn {@code HOP_LE} — và đó là một quyết định, không phải thiếu sót</h2>
 *
 * <p>Số đo của <b>máy</b> thì quý và không lấy lại được, nên một giá trị lạ vẫn được GHI kèm cờ
 * {@code NGHI_NGO} để người xem xét. Số đo của <b>người</b> thì ngược lại: một giá trị ngoài khoảng
 * vật lý gần như chắc chắn là <b>lỗi gõ</b>, và người gõ đang ngồi ngay đó để sửa. ⇒ Đường nhập tay
 * <b>từ chối lớn tiếng</b> ({@code HYD-2001}) thay vì lặng lẽ tạo ra một dòng chờ duyệt mà chính
 * người vừa gõ sẽ phải đi duyệt.
 *
 * @param createdBy ⛔ bắt buộc — {@code ck_hydro_readings_nguoi_nhap} đòi mọi dòng {@code MANUAL}
 *     phải có người chịu trách nhiệm. Máy ghi thì không mượn tên ai (quy tắc 18)
 * @param note lời của <b>người nhập</b> (vì sao phải nhập tay); ⛔ khác {@code review_note} là lời
 *     của người <i>duyệt</i>, và khác {@code quality_reason} là lời của <i>máy</i>
 */
public record NhapTayRow(
        Long stationId, Long measurementTypeId, Instant measuredAt, BigDecimal value, Long createdBy, String note) {

    /** Khớp {@code hydro_readings.note VARCHAR(500)}. */
    public static final int DAI_TOI_DA_GHI_CHU = 500;

    public NhapTayRow {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(measurementTypeId, "measurementTypeId");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(value, "value");
        // ⛔ Không có nhánh nào cho một dòng nhập tay vô danh: nếu để lọt, ràng buộc CSDL sẽ bắt —
        //   nhưng bắt ở đó thì thông điệp là một lỗi ràng buộc thô, và người dùng chỉ thấy 500.
        Objects.requireNonNull(createdBy, "createdBy — dòng MANUAL phải có người chịu trách nhiệm");

        if (note != null && note.length() > DAI_TOI_DA_GHI_CHU) {
            throw new IllegalArgumentException("Ghi chú dài " + note.length() + " ký tự, vượt " + DAI_TOI_DA_GHI_CHU);
        }
    }
}
