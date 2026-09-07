package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.ApiSourceStatus;

/**
 * Nhịp đặt việc polling — T31.1 · T31.2 · T31.11.
 *
 * <h2>⭐⭐ Khẳng định đặt ở nơi con số CÓ HIỆU LỰC</h2>
 *
 * <p>Bài học của chính module này ({@code HydroMaintenanceSchedulerTest}): hỏi thẳng
 * {@code JobHandler.maxAttempts()} là hỏi một phương thức <b>không có người đọc trong toàn kho</b> —
 * một bài kiểm xanh canh một nửa đã chết. Thứ có hiệu lực ở đây là <b>chính đối tượng
 * {@link JobRequest} đi vào hàng đợi</b>, nên mọi khẳng định bắt vào nó.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HydroPollSchedulerTest {

    @Mock
    private JobPort jobs;

    @Mock
    private ApiSourceService sources;

    private ApiSource nguon;
    private HydroPollScheduler scheduler;

    @BeforeEach
    void chuanBi() {
        nguon = new ApiSource("BHH40", "Nguồn mực nước", AdapterType.BHH40, "http://songnhue.bhh40.net");
        ReflectionTestUtils.setField(nguon, "id", 3L);
        when(sources.nguonDangHoatDong()).thenReturn(List.of(nguon));
        datCron("45 1/2 * * * *");
        scheduler = new HydroPollScheduler(jobs, sources);
    }

    private void datCron(String cron) {
        when(sources.thamSoHieuLuc(any()))
                .thenReturn(new ThamSoNguon(
                        cron, true, Duration.ofMinutes(10), true, Duration.ofSeconds(30), true, 3, true));
    }

    /** Đặt "lần kiểm trước" lùi lại {@code giay} giây rồi gõ một nhịp. */
    private void nhipSauKhiLui(long giay) {
        ReflectionTestUtils.setField(
                scheduler,
                "lanKiemTruoc",
                new java.util.concurrent.atomic.AtomicReference<>(
                        java.time.Instant.now().minusSeconds(giay)));
        scheduler.nhipTim();
    }

    @Test
    @DisplayName("⭐ Cron 2 phút/lần — khoảng 3 phút CHẮC CHẮN chứa một mốc ⇒ đặt việc")
    void khoangChuaMotMocThiDatViec() {
        nhipSauKhiLui(180);

        verify(jobs).enqueue(any());
    }

    @Test
    @DisplayName("⭐ Khoảng 1 giây với cron 2 phút — gần như luôn RỖNG ⇒ ⛔ không đặt việc")
    void khoangKhongChuaMocNaoThiThoi() {
        // ⚠ Bài này về nguyên tắc có thể rơi đúng vào giây 45 của một phút lẻ. Xác suất ~1/120 nên
        //   ⛔ không dùng đồng hồ thật: đặt một cron KHÔNG BAO GIỜ khớp trong khoảng đo được.
        datCron("0 0 4 1 1 *"); // 04:00:00 ngày 1 tháng 1
        nhipSauKhiLui(60);

        verify(jobs, never()).enqueue(any());
    }

    @Test
    @DisplayName("⭐⭐ Cron RIÊNG của nguồn được tôn trọng — bốn cái núm trên màn hình điều khiển thật (luật 15)")
    void cronRiengCuaNguonDuocTonTrong() {
        datCron("*/1 * * * * *"); // mỗi giây
        nhipSauKhiLui(2);
        verify(jobs).enqueue(any());

        // Và chiều ngược lại: một cron thưa thì cùng khoảng ấy không đặt việc. Không có vế này thì
        // bài trên xanh cả khi cron bị bỏ qua hoàn toàn (luật 9 — phân biệt được hai trạng thái).
        org.mockito.Mockito.clearInvocations(jobs);
        datCron("0 0 4 1 1 *");
        nhipSauKhiLui(2);
        verify(jobs, never()).enqueue(any());
    }

    @Test
    @DisplayName("⛔ Cron sai cú pháp KHÔNG làm poller đứng im — lùi về mặc định của seed")
    void cronSaiCuPhapThiLuiVeMacDinh() {
        datCron("khong-phai-cron");

        // Mặc định `45 1/2 * * * *` khớp trong một khoảng 3 phút, nên nếu đã lùi được thì có việc.
        nhipSauKhiLui(180);

        verify(jobs).enqueue(any()); // một chuỗi gõ nhầm là MỘT cú bấm; poller ngừng hẳn là mất dữ liệu vĩnh viễn
    }

    @Test
    @DisplayName("⭐⭐ Khoá chống trùng là HYDRO_POLL:<mã nguồn> — ⛔ KHÔNG kèm mốc khung, và số lần thử là MỘT")
    void khoaChongTrungVaSoLanThu() {
        nhipSauKhiLui(180);

        ArgumentCaptor<JobRequest> bat = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobs).enqueue(bat.capture());
        JobRequest yeuCau = bat.getValue();

        assertThat(yeuCau.jobType()).isEqualTo(HydroJobTypes.POLL);
        assertThat(yeuCau.dedupKey())
                .as("bất biến là 'mỗi nguồn tối đa một lượt polling đang chạy'. Kèm mốc khung vào khoá "
                        + "là cho phép năm lượt của cùng một khung xếp hàng — đúng thứ khoá này sinh ra để chặn")
                .isEqualTo("HYDRO_POLL:BHH40");
        assertThat(yeuCau.maxAttempts())
                .as("backoff của worker là 1'/5'/15' còn lượt polling kế tiếp chỉ cách 2 phút — thử lại ở "
                        + "tầng job GIỮ khoá chống trùng suốt backoff, tức là CHẶN lượt đúng giờ. Với một "
                        + "nguồn không có API lịch sử, 15 phút bị chặn là một khung rưỡi mất vĩnh viễn")
                .isEqualTo((short) 1);
    }

    @Test
    @DisplayName("⛔⛔ Payload mang MÃ NGUỒN và KHÔNG mang gì khác — jobs.payload nằm trong mọi bản sao lưu")
    void payloadChiMangMaNguon() {
        nhipSauKhiLui(180);

        ArgumentCaptor<JobRequest> bat = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobs).enqueue(bat.capture());
        String payload = bat.getValue().payload();

        assertThat(payload).isEqualTo("{\"maNguon\":\"BHH40\"}");
        assertThat(HydroPollJobHandler.docMaNguon(payload))
                .as("⭐ vòng khứ hồi sinh–bóc: hai hàm ở hai lớp là đúng chỗ luật 14 gọi tên")
                .isEqualTo("BHH40");
        // ⛔ Khẳng định phủ định về credential: payload lưu nguyên văn và đi vào mọi bản sao lưu.
        assertThat(payload).doesNotContain("key").doesNotContain("credential").doesNotContain("maSo\"");
    }

    @Test
    @DisplayName("Nguồn TẠM DỪNG ⛔ không được đặt việc — quyết định của con người thắng")
    void nguonTamDungThiKhongDatViec() {
        nguon.setStatus(ApiSourceStatus.TAM_DUNG);
        when(sources.nguonDangHoatDong()).thenReturn(List.of());

        nhipSauKhiLui(180);

        verify(jobs, never()).enqueue(any());
    }

    @Test
    @DisplayName("Việc rà tín hiệu đặt riêng, khoá riêng, ⛔ không lẫn với lượt polling")
    void viecRaTinHieuDatRieng() {
        scheduler.datViecRaTinHieu();

        ArgumentCaptor<JobRequest> bat = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobs).enqueue(bat.capture());
        assertThat(bat.getValue().jobType()).isEqualTo(HydroJobTypes.SIGNAL_LOSS);
        assertThat(bat.getValue().dedupKey()).isEqualTo(HydroJobTypes.SIGNAL_LOSS);
        assertThat(bat.getValue().payload()).isEqualTo("{}");
    }

    @Test
    @DisplayName("⭐ T31.11 — dòng khởi động in vân tay mã, ⛔ không chỉ in một chuỗi phiên bản")
    void inVanTayLucKhoiDong() {
        // Hàm không ném là điều kiện cần (nó chạy trong @PostConstruct); thứ nó in đã có
        // `VanTayLopTest` canh ba tính chất. Ở đây chỉ khẳng định nó không làm hỏng lượt khởi động.
        scheduler.inVanTay();
    }

    @Test
    @DisplayName("⚠ Nhịp tim ≤ 10 giây — sai số so với mốc cron phải nhỏ hơn nhiều cửa sổ 7 phút của nguồn")
    void nhipTimDuNhanh() {
        assertThat(HydroPollScheduler.NHIP_MS)
                .as("cửa sổ nguồn đẩy dữ liệu là x1:30 → x8:30 (7 phút). ⛔ Nới nhịp lên phút là để sai "
                        + "số bắt đầu ăn vào cửa sổ thật")
                .isLessThanOrEqualTo(10_000L);
        assertThat(HydroPollScheduler.NHIP_RA_TIN_HIEU_MS)
                .as("rà tín hiệu cố ý THƯA HƠN ngưỡng mất tín hiệu — rà dày hơn không phát hiện sớm hơn")
                .isGreaterThan(HydroPollScheduler.NHIP_MS);
    }
}
