package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.DiemDoDich;
import com.songnhue.hydro.domain.KhoaSoDo;
import com.songnhue.hydro.domain.QuyTacNghiNgo;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingRow;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncOutcome;
import com.songnhue.hydro.domain.SyncStatus;
import com.songnhue.hydro.domain.TelemetryAdapter;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;
import com.songnhue.hydro.domain.TelemetryReading;
import com.songnhue.hydro.domain.UnmappedRow;
import com.songnhue.hydro.infra.HydroRawLogWriter;
import com.songnhue.hydro.infra.HydroTimeSeriesWriter;
import com.songnhue.hydro.infra.PollerRepository;
import com.songnhue.hydro.infra.SyncLogWriter;
import com.songnhue.hydro.infra.TelemetryAdapters;

/**
 * Đường ingest — WS-31: rate-limit ba nhánh, ánh xạ mã → điểm đo, bốn bộ đếm, mọi nhánh thoát.
 *
 * <h2>⚠ Phạm vi bộ canh này (luật 28)</h2>
 *
 * <p><b>Có</b> phủ: quyết định gọi/không gọi, ánh xạ, phân loại đích, các bộ đếm, và <b>dòng
 * {@code sync_logs} ở mọi nhánh thoát</b>. <b>Không</b> phủ: bộ bóc tách văn bản
 * ({@code Bhh40ParserTest} — 17 bài), lượt gọi HTTP thật ({@code Bhh40AdapterHttpTest} — 13 bài trên
 * một máy chủ thật), và câu SQL ({@code TelemetryPollHttpTest} ở module {@code app}, chạy trên CSDL
 * thật). ⛔ Adapter ở đây là một stub cố ý: thứ cần đo là <i>quyết định</i>, không phải <i>văn bản</i>.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryIngestServiceTest {

    private static final long ID_NGUON = 3L;
    private static final long ID_MUC_NUOC = 11L;
    private static final Instant MOC_DO = Instant.parse("2026-09-02T03:20:00Z");

    @Mock
    private ApiSourceService sources;

    @Mock
    private TelemetryAdapters adapters;

    @Mock
    private PollerRepository poller;

    @Mock
    private HydroRawLogWriter rawLogs;

    @Mock
    private HydroTimeSeriesWriter timeSeries;

    @Mock
    private SyncLogWriter syncLogs;

    @Mock
    private ApiSourceHealthService health;

    @Mock
    private ChatLuongSoDoService chatLuong;

    @Mock
    private PlatformTransactionManager txManager;

    private AdapterGia adapter;
    private ApiSource nguon;
    private TelemetryIngestService service;

    @BeforeEach
    void chuanBi() {
        nguon = new ApiSource("BHH40", "Nguồn mực nước", AdapterType.BHH40, "http://songnhue.bhh40.net");
        ReflectionTestUtils.setField(nguon, "id", ID_NGUON);
        adapter = new AdapterGia();

        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(sources.thamSoHieuLuc(any()))
                .thenReturn(new ThamSoNguon(
                        "45 1/2 * * * *", true, Duration.ofMinutes(10), true, Duration.ofSeconds(30), true, 3, true));
        when(sources.maSoDeGoi(any())).thenReturn("ma-so-kiem-thu;");
        when(adapters.cho(any())).thenReturn(adapter);
        when(poller.idLoaiChiSo(TelemetryIngestService.MA_LOAI_CHI_SO)).thenReturn(Optional.of(ID_MUC_NUOC));
        when(poller.demDiemDoDangHoatDong(anyLong())).thenReturn(2);
        when(poller.demDiemDoDaCoTrongKhung(anyLong(), any())).thenReturn(0);
        when(poller.dichTheoMaApi(anyLong())).thenReturn(Map.of("F01771", dich(7L, "DO-LMAC-TL", true, true)));
        when(rawLogs.write(any())).thenReturn(101L);
        when(syncLogs.write(any())).thenReturn(555L);
        // ⭐ writeReadings trả về KHOÁ của dòng đã ghi (T32.3) — mặc định "ghi được hết".
        when(timeSeries.writeReadings(anyList())).thenAnswer(i -> khoaCua(i.getArgument(0)));
        // ⛔ Phiên phân loại KHÔNG cấu hình quy tắc: các bài ở lớp này đo QUYẾT ĐỊNH của luồng đồng
        //    bộ, không đo bộ phân loại (`PhanLoaiChatLuongTest` mới đo cái đó). Mọi dòng ra HOP_LE.
        when(chatLuong.moPhien(anyLong(), any()))
                .thenReturn(new ChatLuongSoDoService.Phien(QuyTacNghiNgo.KHONG_KIEM, Map.of()));
        when(timeSeries.writeUnmapped(anyList())).thenAnswer(i -> ((List<?>) i.getArgument(0)).size());

        service = new TelemetryIngestService(
                sources, adapters, poller, rawLogs, timeSeries, syncLogs, health, chatLuong, txManager);
    }

    /** Khoá của mọi dòng trong lô — dùng làm câu trả lời mặc định của {@code writeReadings}. */
    private static List<KhoaSoDo> khoaCua(List<ReadingRow> lo) {
        return lo.stream().map(r -> new KhoaSoDo(r.stationId(), r.measuredAt())).toList();
    }

    private static DiemDoDich dich(long id, String ma, boolean active, boolean daKhai) {
        return new DiemDoDich(id, ma, ID_NGUON, active, daKhai);
    }

    private static TelemetryReading doDuoc(String ma, String cm) {
        return new TelemetryReading(ma, MOC_DO, new BigDecimal(cm), TelemetryReading.DON_VI_CM);
    }

    // ---------------------------------------------------------------- rate-limit (T31.4)

    @Test
    @DisplayName("⭐⭐ Nhánh (a) — ĐỦ TOÀN BỘ trạm của khung ⇒ bỏ lượt gọi, ⛔ không mở HTTP")
    void duToanBoThiBoLuotGoi() {
        when(poller.demDiemDoDaCoTrongKhung(anyLong(), any())).thenReturn(2);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.trangThai()).isEqualTo(SyncStatus.SKIPPED_UP_TO_DATE);
        assertThat(ket.boQua()).isTrue();
        assertThat(adapter.soLanGoi)
                .as("⛔ rate-limit phải chặn TRƯỚC khi mở HTTP — chặn sau là đã tốn lượt gọi rồi")
                .isZero();
        verify(rawLogs, never()).write(any());
        // ⭐ Vẫn ghi sync_logs: "không ghi dòng nào vì đã đủ" là một câu trả lời, và nó phải phân
        //   biệt được với "không ghi dòng nào vì hỏng" (T31.12).
        assertThat(batSyncLog().status()).isEqualTo(SyncStatus.SKIPPED_UP_TO_DATE);
    }

    @Test
    @DisplayName("⭐⭐ Nhánh (b) — MỚI CÓ 1/2 trạm ⇒ VẪN GỌI. Đây là chỗ dễ mất dữ liệu nhất của WS-31")
    void moiCoBanGhiDauTienThiVanGoi() {
        when(poller.demDiemDoDaCoTrongKhung(anyLong(), any())).thenReturn(1);
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(adapter.soLanGoi)
                .as("nguồn đẩy rải rác trong cửa sổ x1:30 → x8:30 — dừng khi mới có trạm đầu tiên là "
                        + "bỏ lỡ mọi trạm lên muộn, VĨNH VIỄN")
                .isEqualTo(1);
        assertThat(ket.boQua()).isFalse();
    }

    @Test
    @DisplayName("Nhánh (c) — chưa có bản ghi nào của khung ⇒ gọi")
    void chuaCoGiThiGoi() {
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        service.chayTheoLich(nguon);

        assertThat(adapter.soLanGoi).isEqualTo(1);
    }

    @Test
    @DisplayName("⚠ KHÔNG có điểm đo nào đang hoạt động ⇒ VẪN GỌI — ⛔ 'đủ toàn bộ' trên tập rỗng là đúng rỗng tuếch")
    void khongCoDiemDoNaoThiVanGoi() {
        when(poller.demDiemDoDangHoatDong(anyLong())).thenReturn(0);
        when(poller.demDiemDoDaCoTrongKhung(anyLong(), any())).thenReturn(0);
        adapter.me = new TelemetryBatch(List.of(doDuoc("F09999", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(adapter.soLanGoi)
                .as("luật 7 — im lặng vĩnh viễn ở đây còn mất luôn số đo của 9 mã chưa khai")
                .isEqualTo(1);
        assertThat(ket.soMaLa()).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Nút Gọi thử ⛔ KHÔNG chịu rate-limit — một con người vừa bấm nút và đang chờ trả lời")
    void goiThuKhongChiuRateLimit() {
        when(poller.demDiemDoDaCoTrongKhung(anyLong(), any())).thenReturn(2);
        when(sources.get(any())).thenReturn(nguon);
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.goiThu(java.util.UUID.randomUUID());

        assertThat(adapter.soLanGoi).isEqualTo(1);
        assertThat(ket.trangThai())
                .as("trả SKIPPED_UP_TO_DATE cho một cú bấm tay là trả lời một câu hỏi khác câu được hỏi")
                .isNotEqualTo(SyncStatus.SKIPPED_UP_TO_DATE);
    }

    // ---------------------------------------------------------------- nhánh hỏng

    @Test
    @DisplayName("⛔ Thiếu mã số ⇒ dừng TRƯỚC HTTP, ⛔ không ghi raw log, vẫn ghi sync_logs")
    void thieuMaSoDungTruocHttp() {
        when(sources.maSoDeGoi(any())).thenReturn(null);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.loi()).isEqualTo(SyncFailureKind.THIEU_MA_SO);
        assertThat(adapter.soLanGoi).isZero();
        verify(rawLogs, never())
                .write(any()); // hydro_raw_logs CHECK cố ý không nhận THIEU_MA_SO — không có lượt gọi nào
        assertThat(batSyncLog().status()).isEqualTo(SyncStatus.FAILED);
        verify(health).ghiNhanThatBai(eq(nguon), any(), eq(SyncFailureKind.THIEU_MA_SO), any());
    }

    @Test
    @DisplayName("Nguồn từ chối ⇒ raw log VẪN ghi (quy tắc 18) rồi mới tới sync_logs FAILED")
    void nguonTuChoiThiRawVanGhi() {
        adapter.fetch = new TelemetryFetch(200, 12, "not.working", SyncFailureKind.NOT_WORKING, "nguồn từ chối mã số");

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        verify(rawLogs).write(any());
        assertThat(ket.rawLogId()).isEqualTo(101L);
        assertThat(ket.loi()).isEqualTo(SyncFailureKind.NOT_WORKING);
        assertThat(ket.thanhCong()).isFalse();
        assertThat(batSyncLog().failureKind()).isEqualTo(SyncFailureKind.NOT_WORKING);
    }

    @Test
    @DisplayName("⚠ HTTP 200 mà bóc ra 0 số đo ⇒ EMPTY_BODY — kiểu hỏng nguy hiểm nhất vì mọi chỉ số đều bình thường")
    void haiTramMaKhongBocDuocGiLaEmptyBody() {
        adapter.me = new TelemetryBatch(List.of(), 4, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.loi()).isEqualTo(SyncFailureKind.EMPTY_BODY);
        assertThat(ket.lyDo()).contains("4 dòng rác").contains("đổi định dạng");
        verify(timeSeries, never()).writeReadings(anyList());
    }

    @Test
    @DisplayName("⛔ Không tìm thấy loại chỉ số ⇒ NÉM, ⛔ không đoán một id khác (lỗi của TA, không của nguồn)")
    void thieuLoaiChiSoThiNem() {
        when(poller.idLoaiChiSo(any())).thenReturn(Optional.empty());
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        assertThatThrownBy(() -> service.chayTheoLich(nguon))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MUC_NUOC");
        verify(rawLogs).write(any()); // nguyên văn vẫn được giữ — không mất gì
    }

    // ---------------------------------------------------------------- ánh xạ (quy tắc parse 5)

    @Test
    @DisplayName("⭐ Mã đã khai ⇒ hydro_readings với giá trị ĐÃ QUY ĐỔI (493 cm → 4.930 m)")
    void maDaKhaiThiGhiSoDoDaQuyDoi() {
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "493")), 0, 0, false);

        service.chayTheoLich(nguon);

        ReadingRow dong = batReadings().get(0);
        assertThat(dong.stationId()).isEqualTo(7L);
        assertThat(dong.measurementTypeId()).isEqualTo(ID_MUC_NUOC);
        assertThat(dong.value()).isEqualByComparingTo("4.930");
        assertThat(dong.quality())
                .as("⬜ WS-32 mới phân loại NGHI_NGO; hôm nay trùng khít mặc định của lược đồ")
                .isEqualTo(ReadingQuality.HOP_LE);
        assertThat(dong.source()).isEqualTo(ReadingSource.API);
        assertThat(dong.rawLogId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("⭐⭐ Mã CHƯA khai ⇒ hydro_unmapped_readings với giá trị NGUYÊN VĂN + đơn vị nguồn, ⛔ không quy đổi")
    void maChuaKhaiGiuNguyenTrang() {
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01613", "198")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        ArgumentCaptor<List<UnmappedRow>> bat = ArgumentCaptor.captor();
        verify(timeSeries).writeUnmapped(bat.capture());
        UnmappedRow dong = bat.getValue().get(0);

        assertThat(dong.apiCode()).isEqualTo("F01613");
        assertThat(dong.rawValue())
                .as("chưa biết mã ấy là loại chỉ số gì thì cũng chưa biết quy đổi về đâu — quy đổi bây "
                        + "giờ là ĐOÁN, và MOD-03 đã đoán sai 1/4 mã một lần rồi")
                .isEqualByComparingTo("198");
        assertThat(dong.rawUnit()).isEqualTo(TelemetryReading.DON_VI_CM);
        assertThat(ket.maChuaKhai()).containsExactly("F01613");
        verify(timeSeries).writeReadings(List.of());
    }

    @Test
    @DisplayName("⛔ Mã lạ KHÔNG tự sinh điểm đo — quy tắc parse 5, và đó là G8 thuộc Công ty")
    void maLaKhongTuSinhDiemDo() {
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01613", "198"), doDuoc("F01659", "210")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.soMaLa()).isEqualTo(2);
        assertThat(batReadings()).isEmpty();
    }

    @Test
    @DisplayName("⚠ Điểm đo CHƯA tích loại chỉ số ⇒ số đo VẪN ghi + đếm riêng, ⛔ không vứt đi")
    void chuaTichLoaiChiSoThiVanGhi() {
        when(poller.dichTheoMaApi(anyLong())).thenReturn(Map.of("F01771", dich(7L, "DO-LMAC-TL", true, false)));
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(batReadings())
                .as("bảng station_measurement_types nuôi biểu mẫu và báo cáo — nó ⛔ không phải cái van "
                        + "của đường ingest. Bỏ một số đo có thật vì bảng nối thiếu một dòng là mất dữ "
                        + "liệu vĩnh viễn để bảo vệ một danh mục sửa được trong mười giây")
                .hasSize(1);
        assertThat(ket.soThieuLoaiChiSo()).isEqualTo(1);
        assertThat(ket.soMaLa())
                .as("⛔ và nó KHÔNG phải 'mã lạ' — mã ấy đã được khai")
                .isZero();
    }

    @Test
    @DisplayName("⚠ Điểm đo ĐANG NGỪNG vẫn được ghi — 'active' là quyết định trưng bày, ⛔ không phải van ingest")
    void diemDoDangNgungVanGhi() {
        when(poller.dichTheoMaApi(anyLong())).thenReturn(Map.of("F01771", dich(7L, "DO-LMAC-TL", false, true)));
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        service.chayTheoLich(nguon);

        assertThat(batReadings()).hasSize(1);
    }

    @Test
    @DisplayName(
            "⚠ Mã của hồ sơ khai thuộc NGUỒN KHÁC ⇒ vẫn ghi + đếm riêng (ux_stations_api_code là duy nhất toàn hệ)")
    void maCuaNguonKhacVanGhiVaDem() {
        when(poller.dichTheoMaApi(anyLong()))
                .thenReturn(Map.of("F01771", new DiemDoDich(7L, "DO-LMAC-TL", 99L, true, true)));
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(batReadings()).hasSize(1);
        assertThat(ket.soKhacNguon()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- bộ đếm (T31.6, T31.7)

    @Test
    @DisplayName("⭐ written / skipped tách riêng — 'ghi 0 dòng' là kết cục BÌNH THƯỜNG của 4/5 lượt")
    void demGhiMoiVaTrungTachRieng() {
        when(poller.demDiemDoDangHoatDong(anyLong())).thenReturn(1);
        when(timeSeries.writeReadings(anyList())).thenReturn(List.of());
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.soBanGhi()).isEqualTo(1);
        assertThat(ket.soGhiMoi()).isZero();
        assertThat(ket.soTrungBoQua())
                .as("poll 2' trên nguồn 10' ⇒ 4/5 lượt trả dữ liệu trùng. Gộp hai số này là dạy người "
                        + "vận hành bỏ qua số 0 — đúng lúc số 0 ấy có ngày sẽ là thật")
                .isEqualTo(1);
        assertThat(ket.trangThai()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("⭐ upsertLatest nhận MỌI dòng, kể cả dòng vừa bị ON CONFLICT bỏ qua")
    void upsertLatestNhanMoiDong() {
        when(timeSeries.writeReadings(anyList())).thenReturn(List.of());
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        service.chayTheoLich(nguon);

        ArgumentCaptor<List<ReadingRow>> bat = ArgumentCaptor.captor();
        verify(timeSeries).upsertLatest(bat.capture());
        assertThat(bat.getValue())
                .as("một trạm gửi lại đúng giá trị cũ VẪN đang phát tín hiệu — chỉ upsert cho dòng ghi "
                        + "mới là tự dựng ra một trạm mất tín hiệu giả")
                .hasSize(1);
    }

    @Test
    @DisplayName("⭐ Quy tắc parse 9 — dưới 50% điểm đo đang hoạt động ⇒ PARTIAL, ⛔ không FAILED")
    void duoiNuaSoDiemDoLaPartial() {
        when(poller.demDiemDoDangHoatDong(anyLong())).thenReturn(19);
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.trangThai()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(ket.thieuDuLieu()).isTrue();
        assertThat(ket.thanhCong())
                .as("nguồn trả lời và ta ghi được — ⛔ đừng vẽ nó màu đỏ, màu đỏ dành cho FAILED")
                .isTrue();
        assertThat(batSyncLog().failureKind())
                .as("SyncOutcome cấm SUCCESS mang failureKind; PARTIAL cũng không có lý do hỏng")
                .isNull();
    }

    @Test
    @DisplayName("⭐⭐ MỌI nhánh thoát đều ghi đúng MỘT dòng sync_logs — quy tắc parse 10 / T31.12")
    void moiNhanhThoatDeuGhiSyncLog() {
        record Nhanh(String ten, Runnable dungCanh) {}
        List<Nhanh> nhanh = List.of(
                new Nhanh("bỏ qua vì đủ", () -> when(poller.demDiemDoDaCoTrongKhung(anyLong(), any()))
                        .thenReturn(2)),
                new Nhanh("thiếu mã số", () -> when(sources.maSoDeGoi(any())).thenReturn(null)),
                new Nhanh(
                        "nguồn từ chối",
                        () -> adapter.fetch =
                                new TelemetryFetch(200, 1, "not.working", SyncFailureKind.NOT_WORKING, "từ chối")),
                new Nhanh("thân rỗng", () -> adapter.me = new TelemetryBatch(List.of(), 0, 0, false)),
                new Nhanh(
                        "thành công",
                        () -> adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false)));

        for (Nhanh n : nhanh) {
            chuanBi();
            // ⚠ `chuanBi()` dựng lại service nhưng ⛔ KHÔNG xoá lịch sử lời gọi của mock — thiếu dòng
            //   này thì vòng lặp thứ hai thấy 2 lời gọi và bài kiểm đỏ vì một lý do không liên quan.
            org.mockito.Mockito.clearInvocations(syncLogs);
            n.dungCanh().run();
            service.chayTheoLich(nguon);
            verify(syncLogs).write(any());
        }
        // ⭐ Khẳng định về SỐ LƯỢNG nhánh: thêm một nhánh thoát mới mà quên ghi sync_logs thì con số
        //   này lệch, ⛔ không phải chờ ai đó nhớ thêm một bài kiểm (luật 29).
        assertThat(nhanh).hasSize(5);
    }

    @Test
    @DisplayName("⚠ Không ghi được sync_logs ⇒ syncLogId = null, ⛔ không nuốt im lặng, ⛔ không làm hỏng lượt ingest")
    void khongGhiDuocSyncLogThiNoiRa() {
        when(syncLogs.write(any())).thenThrow(new IllegalStateException("CSDL kẹt"));
        adapter.me = new TelemetryBatch(List.of(doDuoc("F01771", "240")), 0, 0, false);

        KetQuaDongBo ket = service.chayTheoLich(nguon);

        assertThat(ket.syncLogId()).isNull();
        assertThat(ket.soGhiMoi())
                .as("một lượt polling ĐÃ lấy được dữ liệu ⛔ không được coi là hỏng chỉ vì không ghi "
                        + "nổi dòng nhật ký của chính nó")
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------- tiện ích

    private SyncOutcome batSyncLog() {
        ArgumentCaptor<SyncOutcome> bat = ArgumentCaptor.forClass(SyncOutcome.class);
        verify(syncLogs).write(bat.capture());
        return bat.getValue();
    }

    private List<ReadingRow> batReadings() {
        ArgumentCaptor<List<ReadingRow>> bat = ArgumentCaptor.captor();
        verify(timeSeries).writeReadings(bat.capture());
        return bat.getValue();
    }

    /**
     * Adapter stub — ⛔ cố ý <b>không</b> phải Mockito mock.
     *
     * <p>Thứ bài này cần đo là <b>số lượt gọi</b> (rate-limit chặn trước hay sau khi mở HTTP), và một
     * bộ đếm thật đọc dễ hơn một chuỗi {@code verify}. Bộ bóc tách văn bản thật đã có 17 bài riêng
     * ({@code Bhh40ParserTest}) và lượt gọi HTTP thật có 13 bài trên máy chủ thật.
     */
    private static final class AdapterGia implements TelemetryAdapter {

        private int soLanGoi;
        private TelemetryFetch fetch = new TelemetryFetch(200, 12, "than-gia", null, null);
        private TelemetryBatch me = new TelemetryBatch(new ArrayList<>(), 0, 0, false);

        @Override
        public AdapterType kieu() {
            return AdapterType.BHH40;
        }

        @Override
        public TelemetryFetch goi(TelemetryCall yeuCau) {
            soLanGoi++;
            return fetch;
        }

        @Override
        public TelemetryBatch boc(String body) {
            return me;
        }
    }
}
