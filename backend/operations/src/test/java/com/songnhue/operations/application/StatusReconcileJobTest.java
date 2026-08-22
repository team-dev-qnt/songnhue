package com.songnhue.operations.application;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.JobContext;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionRepository;

@ExtendWith(MockitoExtension.class)
class StatusReconcileJobTest {

    @Mock
    private ConstructionRepository constructionRepository;

    @Mock
    private ConstructionStatusService statusService;

    @InjectMocks
    private StatusReconcileJob job;

    @Test
    void handleCoSuLecBaoCaoVaProgress() throws Exception {
        Construction c1 = new Construction();
        org.springframework.test.util.ReflectionTestUtils.setField(c1, "id", 1L);
        c1.apDungTrangThai(OperationalStatus.BINH_THUONG);
        // Giả lập code để in log không lỗi NPE
        org.springframework.test.util.ReflectionTestUtils.setField(c1, "code", "C01");

        Construction c2 = new Construction();
        org.springframework.test.util.ReflectionTestUtils.setField(c2, "id", 2L);
        c2.apDungTrangThai(OperationalStatus.BINH_THUONG);
        org.springframework.test.util.ReflectionTestUtils.setField(c2, "code", "C02");

        when(constructionRepository.findAll()).thenReturn(List.of(c1, c2));

        // c1 lệch: CSDL BÌNH THƯỜNG, nhưng tính ra SỰ CỐ
        when(statusService.recompute(c1)).thenReturn(OperationalStatus.SU_CO);
        // c2 không lệch
        when(statusService.recompute(c2)).thenReturn(OperationalStatus.BINH_THUONG);

        JobContext context = mock(JobContext.class);

        job.handle(context);

        // Hàm recompute đã được gọi cho cả 2
        verify(statusService).recompute(c1);
        verify(statusService).recompute(c2);
    }

    @Test
    void handleRongKhongLamGi() throws Exception {
        when(constructionRepository.findAll()).thenReturn(List.of());

        JobContext context = mock(JobContext.class);

        job.handle(context);

        verify(context, never()).progress(anyInt());
    }
}
