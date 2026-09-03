package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.UpstreamException;
import com.songnhue.core.spi.JobContext;
import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.ApiSourceStatus;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;

/**
 * Luật <b>ném hay không ném</b> của lượt polling — T31.1, và là <b>nơi ném SYS-0006 đầu tiên</b> của
 * dự án.
 *
 * <h2>⭐⭐ Luật được canh ở đây</h2>
 *
 * <p><i>Ném khi lượt gọi ĐÃ XẢY RA và hỏng; ⛔ không ném khi chưa hề có lượt gọi nào.</i> Đó chính là
 * đường phân chia mà lược đồ đã vẽ — {@code ck_hydro_raw_logs_failure_kind} nhận bốn giá trị,
 * {@code ck_sync_logs_failure_kind} nhận năm — nên bài này hỏi qua đúng vị ngữ
 * {@link SyncFailureKind#duocGhiVaoRawLog()}, ⛔ không tự liệt kê lại danh sách (luật 14).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HydroPollJobHandlerTest {

    @Mock
    private ApiSourceService sources;

    @Mock
    private TelemetryIngestService ingest;

    private ApiSource nguon;
    private HydroPollJobHandler handler;

    @BeforeEach
    void chuanBi() {
        nguon = new ApiSource("BHH40", "Nguồn mực nước", AdapterType.BHH40, "http://songnhue.bhh40.net");
        when(sources.timTheoMa("BHH40")).thenReturn(Optional.of(nguon));
        handler = new HydroPollJobHandler(sources, ingest);
    }

    private void chay() {
        handler.handle(new JobContext(
                UUID.randomUUID(),
                HydroJobTypes.POLL,
                HydroPollJobHandler.payloadCho("BHH40"),
                null,
                p -> {},
                conTro -> {}));
    }

    private void ketQua(SyncStatus trangThai, SyncFailureKind loi) {
        when(ingest.chayTheoLich(any()))
                .thenReturn(new KetQuaDongBo(
                        trangThai,
                        loi == null ? 200 : null,
                        12,
                        loi,
                        loi == null ? null : "lý do đo được",
                        0,
                        Instant.now(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        19,
                        0,
                        0,
                        null,
                        null,
                        7L));
    }

    @Test
    @DisplayName("⭐⭐ THIEU_MA_SO ⛔ KHÔNG ném — chưa hề có lượt gọi nào, và nó là một TRẠNG THÁI CẤU HÌNH")
    void thieuMaSoKhongNem() {
        ketQua(SyncStatus.FAILED, SyncFailureKind.THIEU_MA_SO);

        assertThatCode(this::chay)
                .as("một nguồn chưa ai dán mã số vào ⛔ không được sinh 720 job FAILED mỗi ngày: nó đã "
                        + "hiện đỏ trên màn hình Nguồn dữ liệu, đã có dòng sync_logs, đã có thông báo — "
                        + "và một màn hình việc nền đỏ rực vì một lý do ai cũng biết là một màn hình sẽ "
                        + "không còn ai đọc (§10.42)")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⭐⭐ Bốn lý do 'đã gọi rồi' ĐỀU ném UpstreamException — SYS-0006 nay có nơi ném thật (luật 7)")
    void bonLyDoDaGoiDeuNem() {
        List<SyncFailureKind> daGoi = java.util.Arrays.stream(SyncFailureKind.values())
                .filter(SyncFailureKind::duocGhiVaoRawLog)
                .toList();

        // ⭐ Khẳng định về SỐ LƯỢNG trước khi lặp: nếu enum co lại về rỗng thì vòng lặp dưới xanh mà
        //   không kiểm gì (luật 7 — phép kiểm chạy qua tập rỗng vẫn xanh trọn vẹn).
        assertThat(daGoi).hasSize(4);

        for (SyncFailureKind kieu : daGoi) {
            ketQua(SyncStatus.FAILED, kieu);
            assertThatThrownBy(this::chay)
                    .as("lý do %s", kieu)
                    .isInstanceOf(UpstreamException.class)
                    .extracting(e -> ((UpstreamException) e).errorCode())
                    .isEqualTo(ErrorCode.SYS_0006);
        }
    }

    @Test
    @DisplayName("⛔ Thông điệp SYS-0006 KHÔNG mang văn bản của nguồn — nó có thể ra tới người dùng cuối")
    void thongDiepKhongMangVanBanCuaNguon() {
        ketQua(SyncStatus.FAILED, SyncFailureKind.NOT_WORKING);

        assertThatThrownBy(this::chay).isInstanceOf(UpstreamException.class).satisfies(e -> assertThat(
                        ((UpstreamException) e).messageArgs())
                .as("`lyDo` tuy đã qua bộ che mã số vẫn là văn bản của nguồn — nó đã nằm ở sync_logs, ở "
                        + "last_failure_reason và ở log, ba nơi có phân quyền")
                .containsExactly("BHH40"));
    }

    @Test
    @DisplayName("Lượt SUCCESS / PARTIAL / bỏ qua ⛔ đều không ném")
    void luotBinhThuongKhongNem() {
        for (SyncStatus trangThai : List.of(SyncStatus.SUCCESS, SyncStatus.PARTIAL, SyncStatus.SKIPPED_UP_TO_DATE)) {
            ketQua(trangThai, null);
            assertThatCode(this::chay).as("trạng thái %s", trangThai).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Nguồn TẠM DỪNG ⇒ ⛔ không gọi ingest, ⛔ không ghi sync_logs — quyết định của con người thắng")
    void nguonTamDungThiBoQua() {
        nguon.setStatus(ApiSourceStatus.TAM_DUNG);

        chay();

        verify(ingest, never()).chayTheoLich(any()); // một dòng nhật ký mỗi 2 phút cho nguồn đã tắt làm bảng ấy vô dụng
    }

    @Test
    @DisplayName("Nguồn biến mất giữa lúc đặt việc và lúc chạy ⇒ NÉM, ⛔ không bỏ qua trong im lặng")
    void nguonBienMatThiNem() {
        when(sources.timTheoMa(any())).thenReturn(Optional.empty());

        assertThatThrownBy(this::chay).isInstanceOf(IllegalStateException.class).hasMessageContaining("BHH40");
    }

    @Test
    @DisplayName("⭐ Vòng khứ hồi payload — hai hàm ở hai lớp là đúng chỗ luật 14 gọi tên")
    void vongKhuHoiPayload() {
        assertThat(HydroPollJobHandler.docMaNguon(HydroPollJobHandler.payloadCho("BHH40")))
                .isEqualTo("BHH40");
        assertThat(HydroPollJobHandler.docMaNguon(HydroPollJobHandler.payloadCho("NGUON-2")))
                .isEqualTo("NGUON-2");
    }

    @Test
    @DisplayName("Payload rỗng / sai định dạng ⇒ ném với thông điệp đọc được, ⛔ không NPE ở giữa lượt chạy")
    void payloadSaiThiNemRoRang() {
        assertThatThrownBy(() -> HydroPollJobHandler.docMaNguon(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rỗng");
        assertThatThrownBy(() -> HydroPollJobHandler.docMaNguon("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maNguon");
        assertThatThrownBy(() -> HydroPollJobHandler.docMaNguon("{\"maNguon\":"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sai định dạng");
    }

    @Test
    @DisplayName("⛔ maxAttempts() KHÔNG được ghi đè ở đây — nó không có người đọc trong toàn kho (luật 15)")
    void khongGhiDeMaxAttempts() {
        assertThat(handler.maxAttempts())
                .as("con số thật nằm ở HydroPollScheduler, nơi JobService.enqueue ghi nó vào cột "
                        + "jobs.max_attempts. Ghi đè ở đây là khai một con số không điều khiển gì — và "
                        + "TRÔNG NHƯ đã điều khiển, đó mới là phần đắt")
                .isEqualTo((short) 3);
    }
}
