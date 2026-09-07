package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.hydro.domain.TinHieuDiemDo;
import com.songnhue.hydro.infra.PollerRepository;

/**
 * Rà mất tín hiệu và <b>sự vắng mặt</b> của đường ingest — T31.8 · T31.9.
 *
 * <p>⭐ Bài chịu lực nhất của lớp này là {@link #canhBaoPhatOCHUYENTRANGTHAI}: job chạy mỗi 5 phút,
 * nên "đang mất tín hiệu thì báo" là <b>288 thông báo mỗi ngày</b> cho một trạm hỏng — và một chuông
 * kêu liên tục vì một lý do ai cũng biết là một chuông sẽ bị tắt, rồi vẫn tắt vào ngày trạm khác
 * hỏng thật (§10.42).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HydroSignalLossHandlerTest {

    private static final Duration KHUNG = Duration.ofMinutes(10);

    @Mock
    private PollerRepository poller;

    @Mock
    private HydroSettings settings;

    @Mock
    private NotificationPort notifications;

    private HydroSignalLossHandler handler;

    @BeforeEach
    void chuanBi() {
        when(settings.khungNguon()).thenReturn(KHUNG);
        when(settings.soKhungMatTinHieu()).thenReturn(3);
        when(poller.mocIngestThanhCongGanNhat()).thenReturn(Optional.of(Instant.now()));
        when(poller.tinHieuDiemDo()).thenReturn(List.of());
        handler = new HydroSignalLossHandler(poller, settings, notifications);
    }

    private static TinHieuDiemDo tram(long id, String ma, Instant ganNhat) {
        return new TinHieuDiemDo(id, ma, "Trạm " + ma, true, ganNhat);
    }

    private static Instant lau() {
        return Instant.now().minus(Duration.ofHours(2));
    }

    private void chay() {
        handler.handle(new JobContext(
                java.util.UUID.randomUUID(), HydroJobTypes.SIGNAL_LOSS, "{}", null, p -> {}, conTro -> {}));
    }

    private List<NotifyRequest> tinDaGui() {
        ArgumentCaptor<NotifyRequest> bat = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifications, Mockito.atLeast(0)).notify(bat.capture());
        return bat.getAllValues();
    }

    @Test
    @DisplayName("⭐⭐ Cảnh báo phát ở CHUYỂN TRẠNG THÁI — lượt thứ hai với cùng trạng thái ⛔ không báo lại")
    void canhBaoPhatOCHUYENTRANGTHAI() {
        when(poller.tinHieuDiemDo()).thenReturn(List.of(tram(1L, "DO-A", lau())));

        chay();
        assertThat(tinDaGui()).hasSize(1);

        Mockito.clearInvocations(notifications);
        chay();
        chay();
        chay();
        verify(notifications, never())
                .notify(any()); // 288 thông báo/ngày cho một trạm hỏng là một chuông sẽ bị tắt (§10.42)
    }

    @Test
    @DisplayName("⭐ Một thông báo GỘP cho cả lượt — ⛔ không phải một thông báo mỗi trạm")
    void motThongBaoGopChoCaLuot() {
        when(poller.tinHieuDiemDo())
                .thenReturn(List.of(tram(1L, "DO-A", lau()), tram(2L, "DO-B", lau()), tram(3L, "DO-C", lau())));

        chay();

        List<NotifyRequest> tin = tinDaGui();
        assertThat(tin)
                .as("nguồn hỏng thì cả 19 trạm cùng im — 19 email cùng lúc là cách chắc chắn nhất để "
                        + "không ai đọc email thứ hai")
                .hasSize(1);
        assertThat(tin.get(0).title()).contains("3 điểm đo");
        assertThat(tin.get(0).body()).contains("DO-A").contains("DO-B").contains("DO-C");
        assertThat(tin.get(0).severity()).isEqualTo(NotifySeverity.WARNING);
        assertThat(tin.get(0).targetPermission()).isEqualTo(HydroSignalLossHandler.QUYEN_TRAM);
    }

    @Test
    @DisplayName("Trạm trở lại ⇒ tin phục hồi — thiếu vế này thì người nhận không bao giờ biết chuyện đã xong")
    void tramTroLaiThiBaoPhucHoi() {
        when(poller.tinHieuDiemDo()).thenReturn(List.of(tram(1L, "DO-A", lau())));
        chay();
        Mockito.clearInvocations(notifications);

        when(poller.tinHieuDiemDo()).thenReturn(List.of(tram(1L, "DO-A", Instant.now())));
        chay();

        List<NotifyRequest> tin = tinDaGui();
        assertThat(tin).hasSize(1);
        assertThat(tin.get(0).eventType()).isEqualTo(HydroSignalLossHandler.SU_KIEN_TRO_LAI);
        assertThat(tin.get(0).severity()).isEqualTo(NotifySeverity.INFO);
    }

    @Test
    @DisplayName("⚠ 'Chưa từng có dữ liệu' ⛔ KHÔNG phải mất tín hiệu — gộp hai cái là 19 cảnh báo giả ngày đầu")
    void chuaCoDuLieuKhongPhaiMatTinHieu() {
        when(poller.tinHieuDiemDo()).thenReturn(List.of(tram(1L, "DO-A", null), tram(2L, "DO-B", null)));

        chay();

        assertThat(tinDaGui().stream()
                        .filter(t -> HydroSignalLossHandler.SU_KIEN_MAT.equals(t.eventType()))
                        .toList())
                .as("một điểm đo vừa seed mà chưa tới lượt polling đầu tiên KHÔNG phải một trạm hỏng")
                .isEmpty();
    }

    @Test
    @DisplayName("⭐⭐ T31.9 — CHƯA TỪNG ingest thành công ⇒ cảnh báo CRITICAL, đo SỰ VẮNG MẶT")
    void chuaTungIngestThanhCongThiBaoDong() {
        when(poller.mocIngestThanhCongGanNhat()).thenReturn(Optional.empty());

        chay();

        NotifyRequest tin = tinDaGui().stream()
                .filter(t -> HydroSignalLossHandler.SU_KIEN_IM_LANG.equals(t.eventType()))
                .findFirst()
                .orElseThrow();
        assertThat(tin.severity()).isEqualTo(NotifySeverity.CRITICAL);
        assertThat(tin.targetPermission()).isEqualTo(HydroSignalLossHandler.QUYEN_NGUON);
        assertThat(tin.body())
                .as("HydroFreshnessRegistrar về nguyên tắc CÂM ở trạng thái này (đăng ký muộn) — đây là "
                        + "chỗ trả nợ ấy, và nó phải nói ra việc phải làm")
                .contains("CHƯA TỪNG")
                .contains("mã số")
                .contains("poller-chet.md");
    }

    @Test
    @DisplayName("Lượt ingest gần nhất quá hạn ⇒ cảnh báo; và nó cũng chỉ báo MỘT lần")
    void ingestQuaHanThiBaoDongMotLan() {
        when(poller.mocIngestThanhCongGanNhat()).thenReturn(Optional.of(lau()));

        chay();
        assertThat(tinDaGui().stream()
                        .filter(t -> HydroSignalLossHandler.SU_KIEN_IM_LANG.equals(t.eventType()))
                        .toList())
                .hasSize(1);

        Mockito.clearInvocations(notifications);
        chay();
        chay();
        verify(notifications, never()).notify(any());
    }

    @Test
    @DisplayName("Ingest trở lại ⇒ tin phục hồi, và chuông được nạp lại cho lần sau")
    void ingestTroLaiThiBaoPhucHoi() {
        when(poller.mocIngestThanhCongGanNhat()).thenReturn(Optional.of(lau()));
        chay();
        Mockito.clearInvocations(notifications);

        when(poller.mocIngestThanhCongGanNhat()).thenReturn(Optional.of(Instant.now()));
        chay();

        assertThat(tinDaGui().stream()
                        .filter(t -> HydroSignalLossHandler.SU_KIEN_INGEST_TRO_LAI.equals(t.eventType()))
                        .toList())
                .hasSize(1);

        // ⭐ Và chuông đã nạp lại: hỏng lần nữa thì lại kêu. Thiếu vế này thì một sự cố thứ hai đi qua
        //   trong im lặng — đúng hình dạng §10.42 ở mức tệ nhất.
        Mockito.clearInvocations(notifications);
        when(poller.mocIngestThanhCongGanNhat()).thenReturn(Optional.of(lau()));
        chay();
        assertThat(tinDaGui().stream()
                        .filter(t -> HydroSignalLossHandler.SU_KIEN_IM_LANG.equals(t.eventType()))
                        .toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("⭐ Đọc ngưỡng từ settings MỖI LƯỢT — đổi trên màn hình là có hiệu lực, ⛔ không phải khởi động lại")
    void doiNguongTrenManHinhCoHieuLucNgay() {
        Instant hai5Phut = Instant.now().minus(Duration.ofMinutes(25));
        when(poller.tinHieuDiemDo()).thenReturn(List.of(tram(1L, "DO-A", hai5Phut)));

        // 3 khung × 10' = 30' ⇒ 25 phút vẫn là hoạt động.
        chay();
        assertThat(tinDaGui()).isEmpty();

        // Hạ ngưỡng xuống 2 khung = 20' ⇒ chính trạm ấy thành mất tín hiệu, KHÔNG dựng lại bean.
        when(settings.soKhungMatTinHieu()).thenReturn(2);
        chay();
        assertThat(tinDaGui().stream()
                        .filter(t -> HydroSignalLossHandler.SU_KIEN_MAT.equals(t.eventType()))
                        .toList())
                .as("⭐ đóng nợ T28.36: khoá hydro.station.signal-loss-frames nay có người đọc, và con "
                        + "số ấy thật sự điều khiển một quyết định")
                .hasSize(1);
    }

    @Test
    @DisplayName(
            "Điểm đo ĐANG NGỪNG ⛔ không sinh cảnh báo — báo nó 'mất tín hiệu' là cảnh báo cho chính quyết định của mình")
    void diemDoDangNgungKhongSinhCanhBao() {
        when(poller.tinHieuDiemDo()).thenReturn(List.of(new TinHieuDiemDo(1L, "DO-A", "Trạm A", false, lau())));

        chay();

        assertThat(tinDaGui().stream()
                        .filter(t -> HydroSignalLossHandler.SU_KIEN_MAT.equals(t.eventType()))
                        .toList())
                .isEmpty();
    }
}
