package com.songnhue.hydro.application;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoDongBoView;
import com.songnhue.hydro.api.HydroReportDtos.ChatLuongNgayView;
import com.songnhue.hydro.api.HydroReportDtos.DongBoNgayView;
import com.songnhue.hydro.domain.ChatLuongNgayRow;
import com.songnhue.hydro.domain.DoDayDuKhung;
import com.songnhue.hydro.infra.HydroReportRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Báo cáo thuỷ văn — WS-34.
 *
 * <h2>BC-13 làm TRƯỚC, và lý do là một ràng buộc về thời gian chứ không về kỹ thuật</h2>
 *
 * <p>Cột <i>"số khung 10' bị bỏ sót"</i> của BC-13 là <b>phép đo duy nhất</b> của NFR-03, mà NFR-03
 * đòi <b>7 ngày liên tục / 1008 khung</b> (T37.1) và hỏng giữa chừng là đếm lại từ đầu. Làm báo cáo
 * ấy <i>sau</i> lượt quan sát là đo bằng một cái thước chưa tồn tại lúc đo — số liệu vẫn còn trong
 * bảng, nhưng ⛔ không ai biết trong bảy ngày ấy có ngày nào hỏng để mà dừng và làm lại.
 *
 * <h2>⛔ Đọc bảng tổng hợp, ⛔ không scan số đo thô</h2>
 *
 * <p>Quy tắc 8. Xem javadoc {@link HydroReportRepository}.
 */
@Service
public class HydroReportService {

    /**
     * Trần khoảng ngày của một lượt xem — xem javadoc {@link ErrorCode#HYD_2012}.
     *
     * <p>366 ⛔ không phải 365: một năm nhuận phải xem trọn được.
     */
    static final int TRAN_SO_NGAY = 366;

    private final HydroReportRepository kho;
    private final StationRepository diemDo;
    private final HydroSettings thamSo;

    public HydroReportService(HydroReportRepository kho, StationRepository diemDo, HydroSettings thamSo) {
        this.kho = kho;
        this.diemDo = diemDo;
        this.thamSo = thamSo;
    }

    /**
     * BC-13 — nhật ký đồng bộ &amp; chất lượng dữ liệu (T34.3).
     *
     * <p>⚠ Mốc "bây giờ" lấy theo <b>giờ VN</b>, cùng múi giờ với {@code hyd_ngay_vn} của CSDL. Đây
     * đúng chỗ {@code HydroRetentionHandler} từng hỏng tất định mọi đêm: container chạy
     * {@code -Duser.timezone=UTC} nên một {@code LocalDate.now()} trần lệch một ngày so với ngày mà
     * CSDL vừa dùng để gộp. Ở đây độ lệch ấy sẽ làm hàng của hôm nay tính mong đợi bằng <b>144</b>
     * thay vì số khung đã trôi qua — tức báo cả trăm khung "bỏ sót" mỗi sáng.
     */
    @Transactional(readOnly = true)
    public BaoCaoDongBoView baoCaoDongBo(LocalDate tuNgay, LocalDate denNgay, UUID stationPublicId) {
        kiemKhoang(tuNgay, denNgay);
        Long stationId = khoaNoiBo(stationPublicId);

        int khungPhut = (int) thamSo.khungNguon().toMinutes();
        ZonedDateTime bayGio = ZonedDateTime.now(DateTimeUtils.ZONE_VN);

        List<ChatLuongNgayView> chatLuong = kho.chatLuongTheoNgay(tuNgay, denNgay, stationId).stream()
                .map(hang -> dungHang(hang, khungPhut, bayGio))
                .toList();

        List<DongBoNgayView> dongBo = kho.dongBoTheoNgay(tuNgay, denNgay).stream()
                .map(hang -> new DongBoNgayView(
                        hang.ngay(),
                        hang.sourceCode(),
                        hang.sourceName(),
                        hang.soLuot(),
                        hang.soThanhCong(),
                        hang.soMotPhan(),
                        hang.soHong(),
                        hang.soBoQua(),
                        hang.soNhan(),
                        hang.soGhiMoi(),
                        hang.soTrung(),
                        hang.soMaLa(),
                        hang.hongGanNhat()))
                .toList();

        return new BaoCaoDongBoView(tuNgay, denNgay, khungPhut, chatLuong, dongBo);
    }

    private static ChatLuongNgayView dungHang(ChatLuongNgayRow hang, int khungPhut, ZonedDateTime bayGio) {
        DoDayDuKhung day = DoDayDuKhung.tinh(hang, khungPhut, bayGio);
        return new ChatLuongNgayView(
                hang.ngay(),
                hang.stationCode(),
                hang.stationName(),
                hang.stationActive(),
                hang.measurementTypeCode(),
                hang.measurementTypeName(),
                hang.soHopLe(),
                hang.soNghiNgo(),
                hang.soDaXoa(),
                day.soKhungMongDoi(),
                day.soKhungBoSot(),
                day.tyLeDayDu(),
                day.lyDoTrong(),
                hang.tinhLuc());
    }

    /**
     * ⚠ Điểm đo ⛔ <b>không tồn tại</b> phải trả 404, ⛔ không phải một báo cáo RỖNG.
     *
     * <p>Bỏ qua bộ lọc không giải được là cho ra một bảng trống trông y hệt <i>"trạm này chưa có số
     * đo nào"</i> — hai câu trả lời trái ngược nhau, cùng một màn hình (luật 9).
     */
    private Long khoaNoiBo(UUID stationPublicId) {
        if (stationPublicId == null) {
            return null;
        }
        return diemDo.findByPublicIdAndDeletedAtIsNull(stationPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004))
                .getId();
    }

    /**
     * ⭐ Hai phép kiểm <b>phân biệt được nhau</b> (luật 9), ⛔ không gộp thành một câu "khoảng ngày
     * không hợp lệ": người gõ ngược hai ô cần một câu khác hẳn người xin năm năm dữ liệu.
     */
    void kiemKhoang(LocalDate tuNgay, LocalDate denNgay) {
        if (tuNgay.isAfter(denNgay)) {
            throw new ValidationException(ErrorCode.HYD_2013);
        }
        long soNgay = denNgay.toEpochDay() - tuNgay.toEpochDay() + 1;
        if (soNgay > TRAN_SO_NGAY) {
            throw new ValidationException(ErrorCode.HYD_2012, TRAN_SO_NGAY);
        }
    }
}
