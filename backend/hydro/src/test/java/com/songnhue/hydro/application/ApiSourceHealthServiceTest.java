package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.infra.ApiSourceRepository;

/**
 * Vế ghi sức khoẻ nguồn và luật phát cảnh báo — T30.6.
 *
 * <h2>⭐⭐ Khẳng định đặt ở nơi con số CÓ HIỆU LỰC</h2>
 *
 * <p>Bài học 02/09/2026 ({@code HydroMaintenanceSchedulerTest}): một bài kiểm hỏi thẳng phương thức
 * Java thay vì hỏi thứ đi vào hàng đợi đã canh <b>một nửa đã chết</b> suốt — {@code maxAttempts()}
 * không có người đọc trong toàn kho. Ở đây thứ có hiệu lực là <b>số lời gọi
 * {@code NotificationPort}</b> và <b>giá trị các cột sau khi lưu</b>, nên khẳng định bắt vào đúng hai
 * thứ ấy, ⛔ không bắt vào biến trung gian.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiSourceHealthServiceTest {

    private static final Instant LUC = Instant.parse("2026-09-02T03:20:00Z");

    @Mock
    private ApiSourceRepository sources;

    @Mock
    private HydroSettings settings;

    @Mock
    private NotificationPort notifications;

    private ApiSourceHealthService service;
    private ApiSource nguon;

    @BeforeEach
    void chuanBi() {
        service = new ApiSourceHealthService(sources, settings, notifications);
        nguon = new ApiSource("BHH40", "Nguồn mực nước", AdapterType.BHH40, "http://songnhue.bhh40.net/");
        when(settings.soLanHongTruocKhiCanhBao()).thenReturn(3);
        when(sources.findById(any())).thenReturn(Optional.of(nguon));
        when(sources.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void hong(SyncFailureKind kieu) {
        service.ghiNhanThatBai(nguon, LUC, kieu, "lý do đo được");
    }

    @Test
    @DisplayName("⭐⭐ Bốn cột RỖNG VĨNH VIỄN từ 31/08 nay có người ghi — đây là vế ghi còn thiếu (luật 27)")
    void bonCotSucKhoeNayCoNguoiGhi() {
        assertThat(nguon.getLastFailureAt()).isNull();
        assertThat(nguon.getLastSuccessAt()).isNull();

        hong(SyncFailureKind.TIMEOUT);

        assertThat(nguon.getLastFailureAt()).isEqualTo(LUC);
        assertThat(nguon.getLastFailureReason()).isEqualTo("lý do đo được");
        assertThat(nguon.getConsecutiveFailures()).isEqualTo(1);
        verify(sources).save(nguon);
    }

    @Test
    @DisplayName("⭐⭐ Cảnh báo phát ĐÚNG MỘT LẦN — ở lượt hỏng thứ 3, ⛔ không phát ở lượt 4, 5, 6…")
    void canhBaoPhatDungMotLanOLuotThuBa() {
        hong(SyncFailureKind.NOT_WORKING);
        hong(SyncFailureKind.NOT_WORKING);
        verify(notifications, never()).notify(any());

        hong(SyncFailureKind.NOT_WORKING);
        verify(notifications).notify(any());

        hong(SyncFailureKind.NOT_WORKING);
        hong(SyncFailureKind.NOT_WORKING);
        hong(SyncFailureKind.NOT_WORKING);
        verifyNoMoreInteractions(notifications);

        assertThat(nguon.getConsecutiveFailures())
                .as("bộ đếm vẫn chạy tiếp — chỉ CHUÔNG là im. Poller gọi 2 phút/lần, nên 'hỏng ≥ ngưỡng "
                        + "thì cảnh báo' là 720 thông báo mỗi ngày, và một chuông kêu liên tục vì một "
                        + "lý do ai cũng biết là một chuông sẽ bị tắt (§10.42)")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("⭐⭐ Nội dung cảnh báo NOT_WORKING nhắc dấu ';' — hai nguyên nhân không phân biệt được từ phía ta")
    void noiDungCanhBaoNoiRaViecPhaiLam() {
        hong(SyncFailureKind.NOT_WORKING);
        hong(SyncFailureKind.NOT_WORKING);
        hong(SyncFailureKind.NOT_WORKING);

        ArgumentCaptor<NotifyRequest> bat = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifications).notify(bat.capture());
        NotifyRequest tin = bat.getValue();

        assertThat(tin.targetPermission())
                .as("một nguồn dữ liệu là tài sản toàn Công ty, ⛔ không thuộc Xí nghiệp nào — nên gửi "
                        + "theo QUYỀN, không theo đơn vị")
                .isEqualTo("hyd:api-source:manage");
        assertThat(tin.severity()).isEqualTo(NotifySeverity.WARNING);
        assertThat(tin.title()).contains("BHH40").contains("3");
        assertThat(tin.body())
                .as("người nhận cảnh báo lúc 2 giờ sáng phải đọc thấy CÂU HỎI ĐÚNG ngay dòng đầu")
                .contains("';'")
                .contains("mất vĩnh viễn");
    }

    @Test
    @DisplayName("⭐ Mỗi loại hỏng có một câu VIỆC PHẢI LÀM khác nhau — ⛔ không một câu chung cho năm nguyên nhân")
    void moiLoaiHongCoMotCauRieng() {
        java.util.Set<String> cauDaThay = new java.util.HashSet<>();
        for (SyncFailureKind kieu : SyncFailureKind.values()) {
            ApiSource rieng = new ApiSource("S" + kieu, "n", AdapterType.BHH40, "http://x/");
            when(sources.findById(any())).thenReturn(Optional.of(rieng));
            for (int i = 0; i < 3; i++) {
                service.ghiNhanThatBai(rieng, LUC, kieu, "chi tiết");
            }
        }
        ArgumentCaptor<NotifyRequest> bat = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifications, org.mockito.Mockito.times(SyncFailureKind.values().length))
                .notify(bat.capture());
        bat.getAllValues().forEach(t -> cauDaThay.add(t.body().split("\n")[0]));

        assertThat(cauDaThay)
                .as("⚠ Khẳng định về SỐ LƯỢNG (luật 29): năm giá trị enum ⇒ năm câu KHÁC NHAU. Một "
                        + "switch trả cùng một chuỗi cho mọi nhánh vẫn qua được mọi khẳng định 'contains'")
                .hasSize(SyncFailureKind.values().length);
    }

    @Test
    @DisplayName("⭐⭐ Nguồn trở lại ⇒ báo phục hồi ĐÚNG MỘT LẦN; lượt thành công thứ hai im lặng")
    void nguonTroLaiThiBaoPhucHoiMotLan() {
        hong(SyncFailureKind.TIMEOUT);
        hong(SyncFailureKind.TIMEOUT);

        service.ghiNhanThanhCong(nguon, LUC);

        ArgumentCaptor<NotifyRequest> bat = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifications).notify(bat.capture());
        assertThat(bat.getValue().eventType()).isEqualTo(ApiSourceHealthService.SU_KIEN_PHUC_HOI);
        assertThat(bat.getValue().severity()).isEqualTo(NotifySeverity.INFO);
        assertThat(nguon.getConsecutiveFailures()).isZero();
        assertThat(nguon.getLastSuccessAt()).isEqualTo(LUC);

        service.ghiNhanThanhCong(nguon, LUC.plusSeconds(120));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    @DisplayName("⭐ Nguồn chưa từng hỏng thì lượt thành công đầu tiên KHÔNG báo 'đã trở lại'")
    void chuaTungHongThiKhongBaoPhucHoi() {
        service.ghiNhanThanhCong(nguon, LUC);

        verify(notifications, never()).notify(any());
    }

    @Test
    @DisplayName("⚠ Thành công KHÔNG xoá lý do hỏng gần nhất — 'hỏng lúc 3:07' mà không nói vì sao là vô dụng")
    void thanhCongKhongXoaLyDoHongGanNhat() {
        hong(SyncFailureKind.HTTP_ERROR);

        service.ghiNhanThanhCong(nguon, LUC.plusSeconds(120));

        assertThat(nguon.getLastFailureReason())
                .as("tên cột là 'lần hỏng GẦN NHẤT', không phải 'lỗi hiện tại'. Xoá lý do mà giữ "
                        + "lastFailureAt cho ra một màn hình tự mâu thuẫn.")
                .isEqualTo("lý do đo được");
        assertThat(nguon.getLastFailureAt()).isEqualTo(LUC);
    }

    @Test
    @DisplayName("⭐ Ngưỡng đọc từ settings mỗi lượt — đổi trên UI có tác dụng ngay, ⛔ không chờ khởi động lại")
    void nguongDocTuSettingsMoiLuot() {
        when(settings.soLanHongTruocKhiCanhBao()).thenReturn(1);

        hong(SyncFailureKind.TIMEOUT);

        verify(notifications).notify(any());
        verify(settings, org.mockito.Mockito.atLeastOnce()).soLanHongTruocKhiCanhBao();
    }

    @Test
    @DisplayName("⚠ Lý do dài hơn cột 500 ký tự bị CẮT — để CSDL từ chối giữa sự cố là mất luôn bộ đếm")
    void lyDoQuaDaiBiCat() {
        service.ghiNhanThatBai(nguon, LUC, SyncFailureKind.HTTP_ERROR, "x".repeat(900));

        assertThat(nguon.getLastFailureReason()).hasSize(500);
    }
}
