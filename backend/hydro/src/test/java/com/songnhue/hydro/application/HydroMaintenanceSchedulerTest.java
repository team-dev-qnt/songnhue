package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRef;
import com.songnhue.core.spi.JobRequest;

/**
 * ⭐⭐ Số lần thử của hai việc bảo trì — <b>khẳng định ở nơi con số ấy có hiệu lực</b>.
 *
 * <h2>Khuyết tật thật, đo được 02/09/2026</h2>
 *
 * <p>Bản đầu ghi đè {@code JobHandler.maxAttempts()} trên {@code HydroRetentionHandler} và trả 1,
 * kèm một bài kiểm khẳng định đúng con số ấy. Cả hai đều <b>canh một nửa đã chết</b>:
 * {@code JobHandler.maxAttempts()} <b>không có người đọc trong toàn kho</b> — {@code JobWorker} lấy
 * {@code max_attempts} từ cột của bảng {@code jobs}, mà cột ấy do
 * {@code JobService.enqueue(…, request.maxAttempts())} ghi, tức từ {@code JobRequest} của <i>nơi đặt
 * việc</i>. Giá trị chạy thật khi ấy là <b>2</b>, do scheduler ghi.
 *
 * <p>Hệ quả: một việc <b>XOÁ KHÔNG PHỤC HỒI ĐƯỢC</b> được thử lại tự động sau backoff — đúng thứ mà
 * con số 1 kia sinh ra để chặn — và bài kiểm vẫn xanh, vì nó hỏi thẳng phương thức Java thay vì hỏi
 * thứ đi vào hàng đợi. Luật 15 ở dạng khó thấy nhất: công tắc không ai đọc <i>trông như</i> đang
 * điều khiển.
 *
 * <p>⬜ Nợ để mở: {@code JobHandler.maxAttempts()} là công tắc chết ở <b>tầng Core</b>, và
 * {@code AuditArchiveHandler} cũng đang ghi đè nó. Hoặc {@code JobWorker} đọc nó, hoặc gỡ khỏi SPI
 * — ⛔ không để nguyên một phương thức mà mọi lớp cài đặt đều tưởng là có tác dụng.
 */
@ExtendWith(MockitoExtension.class)
class HydroMaintenanceSchedulerTest {

    @Mock
    private JobPort jobs;

    @InjectMocks
    private HydroMaintenanceScheduler scheduler;

    private JobRequest datViec(Runnable luot) {
        when(jobs.enqueue(any()))
                .thenReturn(new JobRef(UUID.randomUUID(), "HYDRO", "PENDING", (short) 0, Instant.EPOCH, null));
        luot.run();
        ArgumentCaptor<JobRequest> bat = ArgumentCaptor.forClass(JobRequest.class);
        verify(jobs).enqueue(bat.capture());
        return bat.getValue();
    }

    @Test
    @DisplayName("⛔⛔ Việc XOÁ vào hàng đợi với ĐÚNG 1 lượt thử — con số đi vào JobRequest, không phải vào handler")
    void viecXoaChiThuMotLan() {
        JobRequest yeuCau = datViec(scheduler::scheduleRetention);

        assertThat(yeuCau.jobType()).isEqualTo("HYDRO_RETENTION");
        assertThat(yeuCau.maxAttempts())
                .as(
                        """
                        HYDRO_RETENTION chạy DROP PARTITION trên hai bảng và DELETE trên hai bảng nữa — \
                        không phục hồi được, và nguồn không có API lịch sử. Thử lại tự động một thao tác \
                        xoá là cách một lỗi cấu hình thành nhiều lượt xoá trong cùng một đêm.""")
                .isEqualTo((short) 1);
    }

    @Test
    @DisplayName("⭐ Việc TẠO partition thử 2 lượt — idempotent nên thử lại vô hại, và phân biệt được với việc xoá")
    void viecTaoPartitionThuHaiLan() {
        JobRequest yeuCau = datViec(scheduler::schedulePartition);

        assertThat(yeuCau.jobType()).isEqualTo("HYDRO_PARTITION");
        // ⚠ Vế phân biệt hai trạng thái (luật 9): nếu cả hai việc cùng một con số thì bài trên xanh
        //   kể cả khi lập trình viên đặt hằng số chung trở lại — tức xanh trong khi phân biệt đã mất.
        assertThat(yeuCau.maxAttempts()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("⚠ Khoá chống trùng theo NGÀY và mang tiền tố HYDRO_ — runbook tra hàng đợi bằng LIKE 'HYDRO%'")
    void khoaChongTrungTheoNgay() {
        JobRequest yeuCau = datViec(scheduler::scheduleRetention);

        assertThat(yeuCau.dedupKey()).startsWith("HYDRO_RETENTION:").matches("HYDRO_RETENTION:\\d{4}-\\d{2}-\\d{2}");
        assertThat(yeuCau.payload())
                .as("⛔ payload nằm nguyên văn trong bảng jobs và lọt vào bản sao lưu — không mang gì nhạy cảm")
                .isEqualTo("{}");
    }
}
