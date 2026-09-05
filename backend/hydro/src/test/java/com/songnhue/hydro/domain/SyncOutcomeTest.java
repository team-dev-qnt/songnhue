package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hai bất biến của {@link SyncOutcome} — ép ở <b>hàm dựng</b>, không ở lời dặn (quy tắc 16).
 *
 * <p>Cùng hai bất biến ấy còn được ép lần nữa ở CSDL ({@code ck_sync_logs_failed_co_ly_do} và
 * {@code ck_sync_logs_success_khong_loi}). Ép ở cả hai nơi là cố ý và ⛔ không phải thừa: CSDL chặn
 * <b>mọi</b> đường ghi, kể cả đường chưa ai nghĩ tới, còn hàm dựng cho lỗi ở <b>đúng dòng mã sai</b>
 * thay vì ở giữa một câu SQL cách đó năm lớp gọi.
 */
class SyncOutcomeTest {

    private static final Instant LUC = Instant.parse("2026-09-02T03:20:45Z");

    private static SyncOutcome dung(SyncStatus status, SyncFailureKind kind) {
        return new SyncOutcome(1L, LUC, LUC, LUC, status, kind, null, 28, 28, 0, 0, 7L);
    }

    @Test
    @DisplayName("⛔ FAILED không có failure_kind bị chặn — §10.68-B: cùng một vân tay cho mọi nguyên nhân")
    void failedBatBuocCoLyDo() {
        assertThatThrownBy(() -> dung(SyncStatus.FAILED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureKind");
    }

    @Test
    @DisplayName("⛔ SUCCESS mà mang failure_kind bị chặn — hai khẳng định trái nhau trên một dòng")
    void successKhongDuocMangLyDo() {
        assertThatThrownBy(() -> dung(SyncStatus.SUCCESS, SyncFailureKind.TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trái nhau");
    }

    @Test
    @DisplayName("⭐ PARTIAL ĐƯỢC PHÉP mang lý do — và cũng được phép không mang")
    void partialLinhHoat() {
        // ⚠ Vế này tồn tại để một bản vá sau đừng "làm chặt cho đều" bằng cách bắt mọi trạng thái
        //   khác SUCCESS phải có lý do. PARTIAL nghĩa là nguồn đẩy dở dữ liệu của khung — gọi được,
        //   parse được, chỉ chưa đủ trạm. Đó không phải một lỗi có tên.
        assertThatCode(() -> dung(SyncStatus.PARTIAL, null)).doesNotThrowAnyException();
        assertThatCode(() -> dung(SyncStatus.PARTIAL, SyncFailureKind.EMPTY_BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⛔ Bộ đếm âm bị chặn — số 0 đã là một câu khẳng định, số âm thì không nói gì cả")
    void boDemKhongDuocAm() {
        assertThatThrownBy(() -> new SyncOutcome(1L, LUC, LUC, LUC, SyncStatus.SUCCESS, null, null, 28, -1, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("âm");
    }

    @Test
    @DisplayName("⭐ Lượt bỏ qua vì đã đủ dữ liệu là SUCCESS-hạng-riêng, không phải FAILED")
    void boQuaViDuDuLieu() {
        SyncOutcome bo = SyncOutcome.boQuaVoiDuDuLieu(1L, LUC, LUC.minusSeconds(600));

        assertThat(bo.status())
                .as("4/5 lượt polling rơi vào đây — trộn nó vào FAILED là dạy người vận hành bỏ qua màu đỏ")
                .isEqualTo(SyncStatus.SKIPPED_UP_TO_DATE);
        assertThat(bo.failureKind()).isNull();
        assertThat(bo.receivedCount()).isZero();
        assertThat(bo.writtenCount()).isZero();
        assertThat(bo.rawLogId())
                .as("⛔ không mở kết nối thì không có bản ghi raw nào — trỏ vào một id là bịa")
                .isNull();
    }

    @Test
    @DisplayName("⚠ Lý do quá dài bị CẮT ở hàm dựng, ⛔ không để CSDL từ chối cả lượt ghi")
    void lyDoQuaDaiBiCat() {
        String daiQua = "x".repeat(5000);
        SyncOutcome o = new SyncOutcome(
                1L, LUC, LUC, LUC, SyncStatus.FAILED, SyncFailureKind.HTTP_ERROR, daiQua, 0, 0, 0, 0, null);

        // Cột là VARCHAR(1000). Nếu để nguyên, INSERT ném — và lượt ghi nhật ký ấy là thứ DUY NHẤT
        // ghi lại việc nguồn đang hỏng. Mất nó là mất luôn dấu vết của sự cố.
        assertThat(o.failureDetail()).hasSize(1000);
    }
}
