package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Hợp đồng của ba kiểu WS-31 thêm vào tầng domain — {@link DiemDoDich} · {@link TinHieuDiemDo} ·
 * {@link SyncFailureKind#duocGhiVaoRawLog()}.
 *
 * <h2>⚠ Phạm vi bộ canh này — nói ra để lượt rà sau không đọc cái xanh rộng hơn nó (luật 28)</h2>
 *
 * <p><b>Có</b> phủ: bất biến ở hàm dựng, phép suy trạng thái, và vị ngữ quyết định "ném hay không
 * ném". <b>Không</b> phủ: đường ghi CSDL (thuộc {@code TelemetryIngestServiceTest} và bài HTTP ở
 * module {@code app}), và bộ quy tắc bóc tách ({@code Bhh40ParserTest}).
 */
class PollerHopDongTest {

    private static final Instant LUC = Instant.parse("2026-09-02T03:20:00Z");
    private static final Duration KHUNG = Duration.ofMinutes(10);

    @Nested
    @DisplayName("SyncFailureKind — vị ngữ quyết định ném hay không ném")
    class LyDoHong {

        @Test
        @DisplayName("⭐⭐ ĐÚNG MỘT giá trị không được ghi vào raw log — khớp chênh lệch 5 ↔ 4 của hai CHECK")
        void dungMotGiaTriKhongDuocGhiRawLog() {
            long soKhongGhi = Arrays.stream(SyncFailureKind.values())
                    .filter(k -> !k.duocGhiVaoRawLog())
                    .count();

            // ⭐ Khẳng định về SỐ LƯỢNG, ⛔ không chỉ liệt kê tên: `ck_sync_logs_failure_kind` nhận 5
            //   giá trị còn `ck_hydro_raw_logs_failure_kind` nhận 4. Chênh lệch ấy PHẢI bằng đúng 1.
            //   Một khẳng định về số lượng không chia sẻ giả định nào với một danh sách tên (luật 29
            //   — thứ đã cứu lượt rà 28/8).
            assertThat(soKhongGhi)
                    .as("chênh lệch giữa hai ràng buộc CHECK là đúng một giá trị")
                    .isEqualTo(1);
            assertThat(SyncFailureKind.values()).hasSize(5);
            assertThat(SyncFailureKind.THIEU_MA_SO.duocGhiVaoRawLog()).isFalse();
        }

        @Test
        @DisplayName("Bốn lý do còn lại đều là 'lượt gọi đã xảy ra' ⇒ đều ghi được raw log")
        void bonLyDoConLaiDeuGhiDuoc() {
            assertThat(Arrays.stream(SyncFailureKind.values())
                            .filter(SyncFailureKind::duocGhiVaoRawLog)
                            .toList())
                    .containsExactlyInAnyOrder(
                            SyncFailureKind.NOT_WORKING,
                            SyncFailureKind.TIMEOUT,
                            SyncFailureKind.HTTP_ERROR,
                            SyncFailureKind.EMPTY_BODY);
        }

        @Test
        @DisplayName("⛔ TelemetryFetch hỏi qua chính vị ngữ ấy — hai nơi không thể lệch nhau")
        void telemetryFetchTuChoiDungGiaTriKhongGhiDuoc() {
            assertThatThrownBy(() -> new TelemetryFetch(null, 5, null, SyncFailureKind.THIEU_MA_SO, "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TRƯỚC khi mở HTTP");

            // Vế thuận: một lý do 'đã gọi rồi' phải đi qua được — nếu không thì bài trên xanh vì mọi
            // thứ đều bị từ chối, và nó sẽ không phân biệt được hai trạng thái (luật 9).
            assertThat(new TelemetryFetch(200, 5, "x", SyncFailureKind.NOT_WORKING, "nguồn từ chối").thanhCong())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("DiemDoDich — bản đọc gọn của stations")
    class Dich {

        @Test
        @DisplayName("Ba trường khoá không được rỗng — bắt ở hàm dựng, ⛔ không ở lời dặn")
        void batBuocBaTruongKhoa() {
            assertThatThrownBy(() -> new DiemDoDich(null, "DO-X", 1L, true, true))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new DiemDoDich(1L, null, 1L, true, true)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new DiemDoDich(1L, "DO-X", null, true, true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("⚠ daKhaiLoaiChiSo=false vẫn dựng được — nó là CẢNH BÁO, ⛔ không phải cái cổng")
        void thieuLoaiChiSoVanDungDuoc() {
            DiemDoDich dich = new DiemDoDich(7L, "DO-LMAC-TL", 3L, true, false);

            // ⭐ Bất biến chịu lực của WS-31: một điểm đo chưa tích loại chỉ số VẪN là một đích hợp
            //   lệ. Bỏ số đo đi vì bảng nối thiếu một dòng là mất dữ liệu vĩnh viễn (quy tắc 18) để
            //   bảo vệ một danh mục con người sửa được trong mười giây.
            assertThat(dich.daKhaiLoaiChiSo()).isFalse();
            assertThat(dich.stationId()).isEqualTo(7L);
            assertThat(dich.active()).isTrue();
        }
    }

    @Nested
    @DisplayName("TinHieuDiemDo — ảnh chụp tín hiệu, đầu vào job rà mất tín hiệu")
    class TinHieu {

        @Test
        @DisplayName("⭐ Bốn trạng thái phân biệt được — và 'chưa từng có' KHÁC 'im lặng đã lâu'")
        void bonTrangThaiPhanBietDuoc() {
            Instant tuoi = LUC.minus(Duration.ofMinutes(5));
            Instant cu = LUC.minus(Duration.ofHours(2));

            assertThat(new TinHieuDiemDo(1L, "A", "A", true, tuoi).trangThai(LUC, KHUNG, 3))
                    .isEqualTo(StationDisplayStatus.HOAT_DONG);
            assertThat(new TinHieuDiemDo(2L, "B", "B", true, cu).trangThai(LUC, KHUNG, 3))
                    .isEqualTo(StationDisplayStatus.MAT_TIN_HIEU);
            assertThat(new TinHieuDiemDo(3L, "C", "C", true, null).trangThai(LUC, KHUNG, 3))
                    .isEqualTo(StationDisplayStatus.CHUA_CO_DU_LIEU);
            // Quyết định của con người thắng, kể cả khi trạm đang im lặng hai tiếng.
            assertThat(new TinHieuDiemDo(4L, "D", "D", false, cu).trangThai(LUC, KHUNG, 3))
                    .isEqualTo(StationDisplayStatus.NGUNG);
        }

        @Test
        @DisplayName("Ngưỡng đúng bằng soKhung × khung — bản ghi ngay TRÊN biên vẫn là HOAT_DONG")
        void bienNguongChinhXac() {
            // 3 khung × 10 phút = 30 phút. Mốc đúng 30 phút trước KHÔNG phải "trước" hạn tươi
            // (`isBefore` là so sánh nghiêm ngặt), nên nó vẫn hoạt động.
            Instant dungBien = LUC.minus(Duration.ofMinutes(30));
            assertThat(new TinHieuDiemDo(1L, "A", "A", true, dungBien).trangThai(LUC, KHUNG, 3))
                    .isEqualTo(StationDisplayStatus.HOAT_DONG);

            assertThat(new TinHieuDiemDo(1L, "A", "A", true, dungBien.minusSeconds(1)).trangThai(LUC, KHUNG, 3))
                    .isEqualTo(StationDisplayStatus.MAT_TIN_HIEU);
        }

        @Test
        @DisplayName("Hai trường khoá không được rỗng")
        void batBuocKhoa() {
            assertThatThrownBy(() -> new TinHieuDiemDo(null, "A", "A", true, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new TinHieuDiemDo(1L, null, "A", true, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
