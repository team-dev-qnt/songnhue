package com.songnhue.hydro.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.TelemetryAdapter;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;

/**
 * Nguồn giả cho máy lập trình viên và cho CI — T30.8. ⛔ Không bao giờ ở production.
 *
 * <h2>⛔⛔ Vì sao mọi mã của nó bắt đầu bằng {@code Z}, không phải {@code F}</h2>
 *
 * <p>Bài học đắt nhất của dự án (§10.54): năm trạm thuỷ văn có mực nước, mười chín bài viết, bốn văn
 * bản có số hiệu và người ký — <b>tất cả đều bịa, và tất cả đã lên staging</b>, vì một mảng dự phòng
 * làm một trang rỗng trông đầy. Một adapter giả sinh ra số đo <i>trên mã trạm có thật</i> là đúng lỗi
 * ấy ở dạng nguy hiểm hơn: số bịa khi đó nằm trong {@code hydro_readings} — bảng mà biểu đồ, ngưỡng
 * cảnh báo và cổng công khai đều đọc — và <b>không màn hình nào phân biệt được</b> nó với số thật.
 *
 * <p>⇒ Bảo đảm đặt ở chỗ <b>cấu trúc</b> chứ không ở lời dặn (luật 12): mã giả mang tiền tố
 * {@code Z}, trong khi {@code stations.api_code} có ràng buộc CSDL {@code CHECK (~ '^F[0-9]{5}$')}.
 * Nghĩa là một mã {@code Z9000x} <b>về nguyên tắc không thể</b> tra ra điểm đo nào — nó luôn rơi vào
 * {@code hydro_unmapped_readings} và hiện trên màn hình "mã lạ từ nguồn" đúng như nó là. Không phải
 * một lời hứa, là một bất biến, và {@code MockAdapterTest} khẳng định nó.
 *
 * <h2>Công tắc mặc định TẮT</h2>
 *
 * <p>{@code app.hydro.api.mock=true} mới có bean này. ⚠ Đó là lớp bảo vệ thứ hai, không phải lớp thứ
 * nhất: {@code AdapterType} là một cột trên từng nguồn, nên chỉ cần <i>không</i> có nguồn nào khai
 * {@code MOCK} là đủ. Nhưng "chỉ cần không ai làm X" chưa bao giờ là một bảo đảm — cùng lý lẽ với
 * {@code DiaChiNguon}, và {@code HydroEnvSwitchTest} canh cho cả hai công tắc.
 */
@Component
@ConditionalOnProperty(name = "app.hydro.api.mock", havingValue = "true")
public class MockAdapter implements TelemetryAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockAdapter.class);

    /**
     * Thân phản hồi giả — <b>đúng định dạng dây</b> của {@code bhh40}, ⛔ mã hoàn toàn không thể có
     * thật.
     *
     * <p>Giữ nguyên hình dạng thật (dấu {@code ;}, thẻ {@code <br>}, trang HTML rỗng ở đuôi) là chủ
     * ý: nếu mock trả một cấu trúc dễ hơn thì nó kiểm một đường mà production không đi.
     *
     * <p>⚠ Mốc thời gian cố định {@code 01/01/2000}. Dùng "bây giờ" thì dữ liệu giả trông tươi trên
     * mọi màn hình, và một dòng {@code hydro_latest} tươi là thứ làm tắt đúng cái chuông báo poller
     * chết (§10.42).
     */
    static final String THAN_GIA =
            """
            Z90001;01/01/2000;00:00;value=100;<br>Z90002;01/01/2000;00:00;value=200;<br>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN">
            <html><head><title></title></head><body><form id="form1"></form></body></html>
            """;

    @Override
    public AdapterType kieu() {
        return AdapterType.MOCK;
    }

    @Override
    public TelemetryFetch goi(TelemetryCall yeuCau) {
        // Mức WARN, mỗi lượt gọi: nếu lớp này chạy ở một nơi không ai định cho nó chạy thì dòng log
        // là thứ duy nhất nói ra điều đó — dữ liệu nó sinh ra thì không.
        log.warn("⛔ MockAdapter đang phục vụ nguồn {} — dữ liệu GIẢ, không dùng để nghiệm thu", yeuCau.baseUrl());
        if (yeuCau.baseUrl().endsWith("/hong")) {
            // Một đường đi thử nhánh hỏng mà không cần chờ nguồn thật chết. ⚠ Vẫn phải qua hàm dựng
            // của TelemetryFetch, tức vẫn phải có lý do — không có cửa hậu nào cho bản ghi thiếu vế.
            return new TelemetryFetch(
                    200,
                    1,
                    Bhh40Parser.CHUOI_NGUON_HONG,
                    SyncFailureKind.NOT_WORKING,
                    "Nguồn giả được yêu cầu trả not.working");
        }
        return new TelemetryFetch(200, 1, THAN_GIA, null, null);
    }

    @Override
    public TelemetryBatch boc(String body) {
        // ⭐ Dùng CHUNG bộ bóc với nguồn thật: một bộ parser thứ hai "cho mock" là một đường mã mà
        //   production không đi, và mọi bài kiểm chạy trên nó không nói gì về đường thật (luật 5).
        return Bhh40Parser.boc(body);
    }
}
