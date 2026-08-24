package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.SettingPort;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;

@ExtendWith(MockitoExtension.class)
class StatusReconcileJobTest {

    @Mock
    private ConstructionRepository constructionRepository;

    @Mock
    private ConstructionOperationStatusRepository operationStatuses;

    @Mock
    private ConstructionStatusService statusService;

    @Mock
    private SettingPort settings;

    @Mock
    private NotificationPort notifications;

    @InjectMocks
    private StatusReconcileJob job;

    @Test
    void handleCoSuLecBaoCaoVaProgress() throws Exception {
        Construction c1 = congTrinh(1L, "C01");
        Construction c2 = congTrinh(2L, "C02");

        when(constructionRepository.findByDeletedAtIsNull()).thenReturn(List.of(c1, c2));

        // c1 lệch: CSDL BÌNH THƯỜNG, nhưng tính ra SỰ CỐ
        when(statusService.recompute(c1)).thenReturn(OperationalStatus.SU_CO);
        // c2 không lệch
        when(statusService.recompute(c2)).thenReturn(OperationalStatus.BINH_THUONG);

        khongCoCongTrinhQuaHan();

        job.handle(mock(JobContext.class));

        verify(statusService).recompute(c1);
        verify(statusService).recompute(c2);
    }

    @Test
    void handleRongKhongLamGi() throws Exception {
        when(constructionRepository.findByDeletedAtIsNull()).thenReturn(List.of());

        JobContext context = mock(JobContext.class);

        job.handle(context);

        verify(context, never()).progress(anyInt());
    }

    @Test
    @DisplayName("⛔ KHÔNG dùng findAll() — câu đó gồm cả hồ sơ đã xoá mềm")
    void softDeletedConstructionsAreNotReconciled() throws Exception {
        when(constructionRepository.findByDeletedAtIsNull()).thenReturn(List.of());

        job.handle(mock(JobContext.class));

        verify(constructionRepository).findByDeletedAtIsNull();
        verify(constructionRepository, never()).findAll();
    }

    @Test
    @DisplayName("⭐ Tham số ops.operation-status.stale-days ĐƯỢC ĐỌC — công tắc chưa ai đọc là một lỗi")
    void theStaleDaysSettingIsActuallyRead() throws Exception {
        when(constructionRepository.findByDeletedAtIsNull()).thenReturn(List.of());
        when(settings.getInt(eq("ops.operation-status.stale-days"), anyInt())).thenReturn(3);
        when(operationStatuses.congTrinhQuaHanCapNhat(any())).thenReturn(List.of(11L, 12L));

        job.handle(mock(JobContext.class));

        // Mốc phải tính theo GIÁ TRỊ ĐÃ GIẢI (3), không theo mặc định (7) — luật 3: canh giá trị đã
        // giải, đừng canh giá trị mặc định.
        ArgumentCaptor<OffsetDateTime> moc = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(operationStatuses).congTrinhQuaHanCapNhat(moc.capture());
        assertThat(moc.getValue())
                .isBefore(OffsetDateTime.now().minusDays(2))
                .isAfter(OffsetDateTime.now().minusDays(4));

        ArgumentCaptor<NotifyRequest> canhBao = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notifications).notify(canhBao.capture());
        assertThat(canhBao.getValue().body()).contains("2 công trình").contains("3 ngày");
        assertThat(canhBao.getValue().targetPermission()).isEqualTo("ops:operation-status:update");
    }

    @Test
    @DisplayName("Không có công trình quá hạn thì không gửi thông báo rỗng")
    void noNotificationWhenNothingIsStale() throws Exception {
        when(constructionRepository.findByDeletedAtIsNull()).thenReturn(List.of());
        khongCoCongTrinhQuaHan();

        job.handle(mock(JobContext.class));

        verify(notifications, never()).notify(any());
    }

    // -------------------------------------------------------------------------

    private void khongCoCongTrinhQuaHan() {
        lenient().when(settings.getInt(anyString(), anyInt())).thenReturn(7);
        lenient().when(operationStatuses.congTrinhQuaHanCapNhat(any())).thenReturn(List.of());
    }

    private static Construction congTrinh(long id, String ma) {
        Construction c = new Construction();
        ReflectionTestUtils.setField(c, "id", id);
        c.apDungTrangThai(OperationalStatus.BINH_THUONG);
        ReflectionTestUtils.setField(c, "code", ma);
        return c;
    }
}
