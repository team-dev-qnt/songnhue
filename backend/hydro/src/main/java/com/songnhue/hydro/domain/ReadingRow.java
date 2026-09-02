package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Một số đo <b>đã ánh xạ xong</b>, sẵn sàng ghi xuống {@code hydro_readings}.
 *
 * <p>Đây là điểm hẹn giữa WS-30 (adapter bóc {@code F#####} + cm) và WS-29 (lưu trữ): tới đây thì mã
 * nguồn đã tra ra {@code stationId}, đơn vị đã quy đổi, chất lượng đã phân loại. ⛔ Không lớp nào ở
 * dưới điểm này còn nhìn thấy {@code apiCode} nữa — nếu còn, nghĩa là việc tra cứu đang bị làm hai
 * lần ở hai chỗ.
 *
 * <p>Ràng buộc ép ở <b>hàm dựng</b> chứ không ở lời dặn (quy tắc 16). Ba thứ dưới đây nếu để lọt
 * xuống CSDL sẽ thành lỗi ràng buộc ở giữa một lượt ingest — mà một lượt ingest hỏng là mất dữ liệu
 * vĩnh viễn, vì nguồn không có API lịch sử.
 *
 * @param stationId điểm đo đã tra ra từ {@code api_code}
 * @param measurementTypeId loại chỉ số — quyết định đơn vị và số chữ số thập phân
 * @param measuredAt mốc <b>nguồn đo</b> (mốc khung 10 phút), ⛔ không phải mốc ta ghi
 * @param value giá trị đã quy đổi về đơn vị chuẩn hoá của loại chỉ số
 * @param quality ⚠ {@link ReadingQuality#NGHI_NGO} vẫn được ghi vào bảng chính (quy tắc 14)
 * @param source {@link ReadingSource#API} khi poller ghi
 * @param rawLogId truy ngược về nguyên văn response; {@code null} khi ghi tay
 */
public record ReadingRow(
        Long stationId,
        Long measurementTypeId,
        Instant measuredAt,
        BigDecimal value,
        ReadingQuality quality,
        ReadingSource source,
        Long rawLogId) {

    public ReadingRow {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(measurementTypeId, "measurementTypeId");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(source, "source");

        // ⛔ Kiểu này là dòng của ĐƯỜNG INGEST TỰ ĐỘNG và chỉ của đường ấy: nó không có chỗ nào để
        //   mang `created_by`. Ràng buộc `ck_hydro_readings_nguoi_nhap` đòi mọi dòng `MANUAL` phải
        //   có người chịu trách nhiệm, nên một `ReadingRow(… MANUAL …)` đi tới CSDL là chắc chắn
        //   vỡ — vỡ ở giữa một lượt ingest, cách chỗ viết sai rất xa.
        //
        //   ⚠ Cách sai mà rất dễ chọn: thêm hai trường `createdBy`/`note` vào đây "cho đủ". Chúng
        //   sẽ là hai trường mà mọi lời gọi hôm nay truyền null — một nửa cặp đọc–ghi ngay từ lúc
        //   sinh ra (luật 15). Đường nhập tay có bối cảnh riêng (ai nhập, vì sao, duyệt bởi ai) và
        //   nó ra đời cùng màn hình của nó ở WS-32/T32.7.
        if (source == ReadingSource.MANUAL) {
            throw new IllegalArgumentException("ReadingRow chỉ dành cho đường ingest tự động (source=API). "
                    + "Đường nhập tay có kiểu riêng và ra đời ở WS-32 cùng màn hình của nó.");
        }
    }

    /** Bản ghi này có được phép hiện lên cổng / đem đi so ngưỡng không (quy tắc 14). */
    public boolean hopLe() {
        return quality == ReadingQuality.HOP_LE;
    }
}
