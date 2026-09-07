package com.songnhue.hydro.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.ReportFilePort;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;

/**
 * ⭐ Phép tính <b>mốc cắt</b> của job dọn dữ liệu — chỗ duy nhất trong MOD-03 mà một lỗi số học biến
 * thành mất dữ liệu không phục hồi được.
 *
 * <h2>⚠⚠ Phạm vi tự khai (luật 28) — bài này KHÔNG kiểm được điều gì</h2>
 *
 * <p>Bài này mock {@link HydroMaintenanceRepository}, tức mock <b>đúng chỗ mã chạm tới CSDL</b>. Nó
 * khẳng định được <i>"đọc tham số nào, trừ bao nhiêu, gọi bảng nào"</i> và ⛔ <b>không</b> khẳng
 * định được rằng CSDL chấp nhận mốc cắt ấy.
 *
 * <p>Đó không phải một giới hạn lý thuyết. Bản đầu của bài này khẳng định
 * {@code dropPartitionsBefore("hydro_raw_logs", 2026-08-26)} với hạn lưu 7 ngày và <b>ghi hẳn vào
 * chú thích</b> rằng <i>"biên phải đi qua được"</i> — trong khi tại đúng khoảnh khắc ấy sàn an toàn
 * phía CSDL từ chối nó. Bài kiểm <b>phát biểu ra</b> một bất biến rồi mock mất đường duy nhất thấy
 * bất biến ấy vỡ (luật 4). Vế thật nằm ở {@code HydroRetentionTest.bienDuoiCuaUiPhaiDiQuaDuoc}, và
 * nó gọi hàm CSDL thật ở đúng biên.
 *
 * <p>⚠ Đồng hồ không còn tiêm qua {@code Clock}: mốc cắt nay lấy từ
 * {@link HydroMaintenanceRepository#ngayHienTai()} — {@code current_date} của chính phiên CSDL sắp
 * phán xét nó. Một quyết định, một cái đồng hồ.
 */
@ExtendWith(MockitoExtension.class)
class HydroRetentionHandlerTest {

    /** "Hôm nay" theo CSDL — bài kiểm điều khiển nó qua repository, không qua đồng hồ hệ thống. */
    private static final LocalDate HOM_NAY = LocalDate.of(2026, 9, 2);

    @Mock
    private HydroMaintenanceRepository repository;

    @Mock
    private HydroSettings settings;

    /**
     * ⭐ Mock CÓ TÊN cho kho tệp báo cáo — ⛔ không để nó là {@code null}.
     *
     * <p>{@code donBanKetXuatQuaHan()} bắt {@code RuntimeException} và đi tiếp (xem javadoc của nó),
     * nên một trường {@code null} sẽ làm bước dọn ném NPE rồi <b>bị nuốt</b> — bộ kiểm vẫn xanh
     * trong khi nhánh đang kiểm ⛔ chưa từng chạy. Đúng luật 7 ở dạng khó thấy: xanh vì đi qua nhánh
     * xử lý lỗi, ⛔ không phải vì làm đúng.
     */
    @Mock
    private ReportFilePort khoBaoCao;

    @InjectMocks
    private HydroRetentionHandler handler;

    private static JobContext boiCanh() {
        return new JobContext(UUID.randomUUID(), HydroJobTypes.RETENTION, "{}", null, percent -> {}, conTro -> {});
    }

    @Test
    @DisplayName("⭐⭐ Hai hạn lưu KHÁC ĐƠN VỊ: raw tính bằng NGÀY, số đo tính bằng NĂM")
    void haiHanLuuKhacDonVi() {
        when(repository.ngayHienTai()).thenReturn(HOM_NAY);
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);

        handler.handle(boiCanh());

        // 02/09/2026 − 90 NGÀY = 04/06/2026. Nhầm đơn vị ở đây (ngày ↔ tháng) là cách nhanh nhất để
        // xoá mất dữ liệu của cả năm — và sàn an toàn phía CSDL KHÔNG bắt được, vì mốc sai theo
        // hướng CŨ HƠN thì vẫn qua sàn.
        verify(repository).dropPartitionsBefore("hydro_raw_logs", LocalDate.of(2026, 6, 4));
        verify(repository).dropPartitionsBefore("hydro_readings", LocalDate.of(2021, 9, 2));
    }

    @Test
    @DisplayName("⭐ Nhật ký đồng bộ đi theo nhịp RAW; mã chưa khai đi theo nhịp SỐ ĐO — cả hai khẳng định GIÁ TRỊ")
    void haiBangPhuDiTheoDungNhip() {
        when(repository.ngayHienTai()).thenReturn(HOM_NAY);
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);

        handler.handle(boiCanh());

        // ⚠ Bản đầu viết `purgeSyncLogsBefore(any())` — nó xanh kể cả khi nhật ký đồng bộ bị dọn
        //   theo nhịp 5 NĂM thay vì 90 ngày, tức xanh trong khi đúng thứ nó mang tên đã sai
        //   (luật 9: một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì).
        verify(repository).purgeSyncLogsBefore(LocalDate.of(2026, 6, 4));

        // ⛔ `hydro_unmapped_readings` KHÔNG dọn theo nhịp raw. Đó là số đo THẬT của những trạm có
        //   thật, chỉ thiếu mỗi phần khai báo — xoá sau 90 ngày là vứt đúng thứ mà cả bảng ấy sinh
        //   ra để giữ, và nguồn không có API lịch sử nên mất là mất vĩnh viễn kể cả sau khi Công ty
        //   trả lời G8.
        verify(repository).purgeUnmappedBefore(LocalDate.of(2021, 9, 2));
    }

    @Test
    @DisplayName("⭐ Mốc cắt lấy từ CSDL, ⛔ không từ đồng hồ JVM — một quyết định, một cái đồng hồ")
    void mocCatLayTuCsdl() {
        // Trả về một ngày KHÔNG THỂ là ngày hôm nay của máy chạy test. Nếu ai đó quay lại
        // `LocalDate.now(...)` thì hai khẳng định dưới đây đỏ ngay — chứ không đợi tới lượt deploy
        // rồi mới lộ ra bằng một job đỏ mỗi đêm.
        when(repository.ngayHienTai()).thenReturn(LocalDate.of(2031, 3, 17));
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);

        handler.handle(boiCanh());

        verify(repository).dropPartitionsBefore("hydro_raw_logs", LocalDate.of(2030, 12, 17));
        verify(repository).dropPartitionsBefore("hydro_readings", LocalDate.of(2026, 3, 17));
    }

    @Test
    @DisplayName("⭐ Biên dưới của UI (7 ngày) cho ra ĐÚNG mốc mà sàn an toàn chấp nhận")
    void bienDuoiChoRaMocDungBang() {
        when(repository.ngayHienTai()).thenReturn(HOM_NAY);
        when(settings.soNgayGiuRawLog()).thenReturn(7);
        when(settings.soNamGiuDuLieu()).thenReturn(1);

        handler.handle(boiCanh());

        // Sàn phía CSDL từ chối mọi mốc `> current_date - 7`. Với cùng một `current_date`, mốc này
        // BẰNG sàn ⇒ đi qua. ⚠ Đây mới chỉ là phép tính; việc CSDL thật sự chấp nhận nó do
        // `HydroRetentionTest.bienDuoiCuaUiPhaiDiQuaDuoc` chứng minh.
        verify(repository).dropPartitionsBefore("hydro_raw_logs", HOM_NAY.minusDays(7));
        verify(repository).dropPartitionsBefore("hydro_readings", LocalDate.of(2025, 9, 2));
    }

    @Test
    @DisplayName("⭐⭐ Lượt dọn CÓ gọi kho tệp báo cáo — TTL 24h không được chỉ sống ở endpoint tải")
    void luotDonGoiKhoTepBaoCao() {
        when(repository.ngayHienTai()).thenReturn(HOM_NAY);
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);

        handler.handle(boiCanh());

        // ⚠⚠ Khẳng định ĐÚNG hai tham số, ⛔ không phải `any()`. Thiếu bước dọn này thì "TTL 24 giờ"
        //    chỉ là một câu kiểm ở endpoint tải: tệp vẫn nằm nguyên trong bucket và vẫn được
        //    `push-offsite.sh` sao lưu ra ngoài mỗi đêm, mãi mãi. Một hạn dùng chỉ thi hành ở tầng
        //    đọc là một hạn dùng ⛔ không có thật.
        verify(khoBaoCao).donQuaHan(HydroReportExportHandler.TIEN_TO_KHOA, HydroReportExportHandler.HAN_TAI);
    }

    @Test
    @DisplayName("⛔ Kho tệp hỏng ⛔ KHÔNG được làm hỏng lượt dọn CSDL — nó vừa DROP PARTITION xong")
    void khoTepHongKhongLamHongLuotDon() {
        when(repository.ngayHienTai()).thenReturn(HOM_NAY);
        when(settings.soNgayGiuRawLog()).thenReturn(90);
        when(settings.soNamGiuDuLieu()).thenReturn(5);
        when(khoBaoCao.donQuaHan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("MinIO tạm không với tới"));

        // ⛔ Job này chỉ thử MỘT lần (xoá là không phục hồi được). Ném ở đây là nói rằng phần dọn
        //   CSDL cũng hỏng — một câu SAI, và nó chặn luôn lượt dọn của ngày hôm sau.
        handler.handle(boiCanh());

        verify(repository).dropPartitionsBefore("hydro_readings", LocalDate.of(2021, 9, 2));
    }
}
