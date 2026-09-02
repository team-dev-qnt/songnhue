package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.JobContext;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;

/** Job giữ runway partition — T29.6. */
@ExtendWith(MockitoExtension.class)
class HydroPartitionHandlerTest {

    @Mock
    private HydroMaintenanceRepository repository;

    @InjectMocks
    private HydroPartitionHandler handler;

    private static JobContext boiCanh() {
        return new JobContext(UUID.randomUUID(), HydroJobTypes.PARTITION, "{}", null, percent -> {});
    }

    @Test
    @DisplayName("⭐ Soi partition DEFAULT của CẢ HAI bảng — bỏ sót một bảng là mù một nửa")
    void soiCaHaiBang() {
        when(repository.ensurePartitions(6)).thenReturn(0);
        when(repository.countInDefaultPartition("hydro_raw_logs")).thenReturn(0L);
        when(repository.countInDefaultPartition("hydro_readings")).thenReturn(0L);

        handler.handle(boiCanh());

        // Số bản ghi ở partition DEFAULT là CHỈ SỐ DUY NHẤT cho biết job này đã chết: mọi thứ khác
        // vẫn chạy bình thường, dữ liệu vẫn ghi được, không có lỗi nào. Soi một bảng thôi thì nửa
        // còn lại im lặng.
        verify(repository).countInDefaultPartition("hydro_raw_logs");
        verify(repository).countInDefaultPartition("hydro_readings");
    }

    @Test
    @DisplayName("⭐ Có bản ghi ở DEFAULT vẫn KHÔNG ném lỗi — job dọn dẹp đổ vỡ kéo theo poller không chạy")
    void banGhiODefaultKhongLamJobDoVo() {
        when(repository.ensurePartitions(6)).thenReturn(2);
        when(repository.countInDefaultPartition("hydro_raw_logs")).thenReturn(1_234L);
        when(repository.countInDefaultPartition("hydro_readings")).thenReturn(0L);

        // Ghi ERROR vào log là đủ: hết runway KHÔNG làm hỏng việc ghi (bản ghi rơi vào DEFAULT), và
        // biến một cảnh báo thành một ngoại lệ ở đây là đổi một sự cố nhỏ lấy một sự cố lớn.
        handler.handle(boiCanh());

        verify(repository).ensurePartitions(6);
    }

    @Test
    @DisplayName("⚠ Mã loại việc phải giữ tiền tố HYDRO_ — runbook tra hàng đợi bằng LIKE 'HYDRO%'")
    void maLoaiViecGiuTienTo() {
        assertThat(handler.jobType()).isEqualTo("HYDRO_PARTITION").startsWith("HYDRO");
    }
}
