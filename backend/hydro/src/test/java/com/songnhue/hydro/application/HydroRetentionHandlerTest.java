package com.songnhue.hydro.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.JobContext;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;

/**
 * ⭐ Phép tính <b>mốc cắt</b> của job dọn dữ liệu — chỗ duy nhất trong MOD-03 mà một lỗi số học biến
 * thành mất dữ liệu không phục hồi được.
 *
 * <p>Bài này cố ý là bài kiểm <b>đơn vị</b>, không phải tích hợp: thứ cần khẳng định là
 * <i>"đọc tham số nào, trừ bao nhiêu, gọi bảng nào"</i>, và ba câu hỏi ấy trả lời được mà không cần
 * CSDL. Bài tích hợp {@code HydroRetentionTest} lo phần còn lại — rằng CSDL thật sự từ chối một mốc
 * cắt quá gần.
 *
 * <p>⚠ Đồng hồ được tiêm vào qua hàm dựng gói-riêng. Không có nó thì bài kiểm phải tự tính
 * {@code LocalDate.now().minusDays(90)} — tức là <b>chép lại chính công thức đang được kiểm</b>, và
 * một bài kiểm chép lại lỗi thay vì bắt lỗi là đúng thứ §10.62 đã gặp.
 */
@ExtendWith(MockitoExtension.class)
class HydroRetentionHandlerTest {

    /** 02/09/2026 lúc 04:30 giờ VN — đúng khung giờ job này chạy thật. */
    private static final Clock DONG_HO = Clock.fixed(Instant.parse("2026-09-01T21:30:00Z"), ZoneOffset.ofHours(7));

    @Mock
    private HydroMaintenanceRepository repository;

    @Mock
    private HydroSettings settings;

    private static JobContext boiCanh() {
        return new JobContext(java.util.UUID.randomUUID(), HydroJobTypes.RETENTION, "{}", null, percent -> {});
    }

    @Test
    @DisplayName("⭐⭐ Hai hạn lưu KHÁC NHAU: raw tính bằng NGÀY, số đo tính bằng NĂM")
    void haiHanLuuKhacDonVi() {
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);

        new HydroRetentionHandler(repository, settings, DONG_HO).handle(boiCanh());

        // 02/09/2026 − 90 ngày = 04/06/2026. Nhầm đơn vị ở đây (ngày ↔ tháng) là cách nhanh nhất để
        // xoá mất dữ liệu của cả năm — và sàn an toàn phía CSDL KHÔNG bắt được, vì mốc sai theo
        // hướng CŨ HƠN thì vẫn qua sàn.
        verify(repository).dropPartitionsBefore("hydro_raw_logs", LocalDate.of(2026, 6, 4));
        verify(repository).dropPartitionsBefore("hydro_readings", LocalDate.of(2021, 9, 2));
    }

    @Test
    @DisplayName("⭐ Nhật ký đồng bộ đi theo nhịp của RAW; mã chưa khai đi theo nhịp của SỐ ĐO")
    void haiBangPhuDiTheoDungNhip() {
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);

        new HydroRetentionHandler(repository, settings, DONG_HO).handle(boiCanh());

        verify(repository).purgeSyncLogsBefore(any());
        // ⛔ `hydro_unmapped_readings` KHÔNG được dọn theo nhịp raw (90 ngày). Đó là số đo THẬT của
        //   những trạm có thật, chỉ thiếu mỗi phần khai báo — xoá chúng sau 90 ngày là vứt đúng thứ
        //   mà cả bảng ấy sinh ra để giữ, và nguồn không có API lịch sử nên mất là mất vĩnh viễn kể
        //   cả sau khi Công ty trả lời G8.
        verify(repository)
                .purgeUnmappedBefore(LocalDate.of(2021, 9, 2)
                        .atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toInstant());
    }

    @Test
    @DisplayName("⭐ Đọc tham số từ `settings` mỗi lượt chạy — đổi trên UI là có tác dụng ngay đêm sau")
    void docThamSoMoiLuotChay() {
        when(settings.soNgayGiuRawLog()).thenReturn(7);
        when(settings.soNamGiuDuLieu()).thenReturn(1);

        new HydroRetentionHandler(repository, settings, DONG_HO).handle(boiCanh());

        // 7 ngày là biên dưới của ràng buộc seed `min=7`, và nó chạm ĐÚNG sàn an toàn 7 ngày của
        // hàm trong CSDL. Biên phải đi qua được — nếu không thì giá trị nhỏ nhất mà UI cho phép đặt
        // lại là giá trị làm job đỏ mỗi đêm.
        verify(repository).dropPartitionsBefore("hydro_raw_logs", LocalDate.of(2026, 8, 26));
        verify(repository).dropPartitionsBefore("hydro_readings", LocalDate.of(2025, 9, 2));
    }

    @Test
    @DisplayName("⛔ Việc XOÁ chỉ thử MỘT lần — thử lại tự động một việc xoá là xoá nhiều lần trong một đêm")
    void khongThuLaiViecXoa() {
        org.assertj.core.api.Assertions.assertThat(
                        new HydroRetentionHandler(repository, settings, DONG_HO).maxAttempts())
                .isEqualTo((short) 1);
    }
}
