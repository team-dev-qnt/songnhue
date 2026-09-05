package com.songnhue.hydro.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.TelemetryAdapter;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;
import com.songnhue.hydro.domain.TelemetryReading;

/** Sổ tra adapter theo loại nguồn (T30.10) và bất biến cấu trúc của {@link MockAdapter} (T30.8). */
class TelemetryAdaptersTest {

    /**
     * ⭐⭐ Ràng buộc CSDL của {@code stations.api_code}, chép <b>nguyên văn</b> từ migration.
     *
     * <p>Chép thay vì tra CSDL là chủ ý: đây là bài kiểm đơn vị, và cái đáng canh là <i>hai nơi phải
     * nhớ giống nhau</i> (luật 14). Nếu ai đó nới ràng buộc ở migration mà quên ở đây thì
     * {@code HydroEnumSchemaTest} và bộ canh lược đồ là chỗ bắt — ⛔ không phải chỗ này.
     */
    private static final Pattern RANG_BUOC_API_CODE = Pattern.compile("^F[0-9]{5}$");

    @Test
    @DisplayName("⭐⭐ Mọi mã của MockAdapter KHÔNG khớp CHECK của stations.api_code — nó không thể tra ra điểm đo nào")
    void maGiaVeNguyenTacKhongTraRaDiemDoNao() {
        TelemetryBatch me = Bhh40Parser.boc(MockAdapter.THAN_GIA);

        assertThat(me.soDo())
                .as("⚠ Vế chống tập rỗng (luật 7): nếu thân giả không bóc ra dòng nào thì khẳng định "
                        + "bên dưới xanh vì không có gì để kiểm")
                .isNotEmpty();
        assertThat(me.soDo())
                .extracting(TelemetryReading::apiCode)
                .as("⛔⛔ §10.54: năm trạm thuỷ văn có mực nước, tất cả đều bịa, và tất cả đã lên "
                        + "staging. Một adapter giả sinh số đo TRÊN MÃ TRẠM CÓ THẬT là đúng lỗi ấy ở "
                        + "dạng nguy hiểm hơn — số bịa khi đó nằm trong hydro_readings và không màn "
                        + "hình nào phân biệt được nó với số thật. Bảo đảm phải ở tầng CẤU TRÚC.")
                .allSatisfy(ma -> assertThat(RANG_BUOC_API_CODE.matcher(ma).matches())
                        .as("mã giả %s KHỚP ràng buộc mã thật — nó có thể tra ra một điểm đo", ma)
                        .isFalse());
    }

    @Test
    @DisplayName("⚠ Mốc thời gian của dữ liệu giả nằm ở quá khứ xa — dữ liệu giả 'tươi' làm tắt chuông poller chết")
    void mocDuLieuGiaONamXaXua() {
        assertThat(Bhh40Parser.boc(MockAdapter.THAN_GIA).soDo()).allSatisfy(r -> assertThat(r.measuredAt())
                .as("§10.42: một dòng hydro_latest tươi giả là thứ làm im đúng cái cảnh báo "
                        + "NguonDuLieuImLang mà runbook poller-chet.md dựa vào")
                .isBefore(java.time.Instant.parse("2001-01-01T00:00:00Z")));
    }

    @Test
    @DisplayName("Sổ tra trả đúng adapter theo loại nguồn")
    void traDungAdapterTheoLoai() {
        TelemetryAdapters so = new TelemetryAdapters(List.of(new MockAdapter()));

        assertThat(so.cho(AdapterType.MOCK)).isInstanceOf(MockAdapter.class);
        assertThat(so.daNap()).containsExactly(AdapterType.MOCK);
    }

    @Test
    @DisplayName("⭐ Thiếu adapter ⇒ lỗi nói ra VIỆC PHẢI LÀM, và nhắc công tắc mock ⛔ không bật ở prod")
    void thieuAdapterThiNoiRaViecPhaiLam() {
        TelemetryAdapters so = new TelemetryAdapters(List.of(new MockAdapter()));

        assertThatThrownBy(() -> so.cho(AdapterType.BHH40))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BHH40")
                .hasMessageContaining("adapter_type");

        TelemetryAdapters khongCoMock = new TelemetryAdapters(List.of());
        assertThatThrownBy(() -> khongCoMock.cho(AdapterType.MOCK))
                .as("javadoc AdapterType cảnh báo: một giá trị không có lớp tương ứng chỉ lộ ra ở lượt "
                        + "polling ĐẦU TIÊN — dòng lỗi này là thứ duy nhất người trực đọc được lúc ấy")
                .hasMessageContaining("app.hydro.api.mock");
    }

    @Test
    @DisplayName("⭐ Hai adapter cùng một loại ⇒ DỪNG ở lúc dựng — ⛔ không để thứ tự quét classpath quyết định")
    void haiAdapterCungLoaiThiDung() {
        assertThatThrownBy(() -> new TelemetryAdapters(List.of(new MockAdapter(), new MockAdapter())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hai adapter cùng khai MOCK");
    }

    @Test
    @DisplayName("MockAdapter dùng CHUNG bộ bóc với nguồn thật — ⛔ không có parser thứ hai 'cho mock'")
    void mockDungChungBoBoc() {
        TelemetryAdapter mock = new MockAdapter();
        TelemetryFetch fetch = mock.goi(new TelemetryCall("http://gia/", "x;", Duration.ofSeconds(1)));

        assertThat(fetch.thanhCong()).isTrue();
        assertThat(mock.boc(fetch.body()).soDo())
                .as("một parser riêng cho mock là một đường mã mà production không đi, và mọi bài "
                        + "kiểm chạy trên nó không nói gì về đường thật (luật 5)")
                .hasSize(2);

        TelemetryFetch hong = mock.goi(new TelemetryCall("http://gia/hong", "x;", Duration.ofSeconds(1)));
        assertThat(hong.failureKind()).isEqualTo(com.songnhue.hydro.domain.SyncFailureKind.NOT_WORKING);
        assertThat(mock.boc(hong.body()).nguonBaoHong()).isTrue();
    }
}
