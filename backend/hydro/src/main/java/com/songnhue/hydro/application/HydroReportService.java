package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.ConstructionLookupPort;
import com.songnhue.core.spi.TinhHinhVanHanhRef;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoDongBoView;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoTongHopView;
import com.songnhue.hydro.api.HydroReportDtos.BieuTuyenSongView;
import com.songnhue.hydro.api.HydroReportDtos.ChatLuongNgayView;
import com.songnhue.hydro.api.HydroReportDtos.ChiTietSoDoView;
import com.songnhue.hydro.api.HydroReportDtos.DiemDoTuyenView;
import com.songnhue.hydro.api.HydroReportDtos.DongBoNgayView;
import com.songnhue.hydro.api.HydroReportDtos.NhomTuyenView;
import com.songnhue.hydro.api.HydroReportDtos.TinhHinhVanHanhView;
import com.songnhue.hydro.api.HydroReportDtos.TongHopKyView;
import com.songnhue.hydro.domain.ChatLuongNgayRow;
import com.songnhue.hydro.domain.DoDayDuKhung;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.StationDisplayStatus;
import com.songnhue.hydro.domain.TongHopKyRow;
import com.songnhue.hydro.domain.TuyenSongRow;
import com.songnhue.hydro.infra.HydroReportRepository;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
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
    public static final int TRAN_SO_NGAY = 366;

    /**
     * ⛔ Trần khoảng ngày của BC-12 <b>hẹp hơn hẳn</b> — 31 ngày.
     *
     * <p>BC-12 là báo cáo <b>duy nhất</b> đọc {@code hydro_readings} thô (quy tắc 8). Một điểm đo ×
     * một chỉ số sinh 144 bản ghi/ngày, nên 31 ngày đã là ~4.500 dòng cho một lượt xem. 366 ngày
     * như các báo cáo khác sẽ là 52 nghìn — và đó chính là lượt quét mà bảng tổng hợp sinh ra để
     * tránh. Cận này ⛔ không phải sự cẩn thận thừa: nó là điều kiện để ngoại lệ kia còn hợp lệ.
     */
    public static final int TRAN_NGAY_CHI_TIET = 31;

    private final HydroReportRepository kho;
    private final StationRepository diemDo;
    private final MeasurementTypeRepository loaiChiSo;
    private final ConstructionLookupPort congTrinh;
    private final HydroSettings thamSo;

    public HydroReportService(
            HydroReportRepository kho,
            StationRepository diemDo,
            MeasurementTypeRepository loaiChiSo,
            ConstructionLookupPort congTrinh,
            HydroSettings thamSo) {
        this.kho = kho;
        this.diemDo = diemDo;
        this.loaiChiSo = loaiChiSo;
        this.congTrinh = congTrinh;
        this.thamSo = thamSo;
    }

    /**
     * ⭐⭐ BC-11 — biểu tổng hợp mực nước theo tuyến sông (T34.4).
     *
     * <p>⛔ Điểm đo ⛔ <b>không bao giờ</b> bị lọc bỏ khỏi biểu: một trạm mất tín hiệu là đúng thứ
     * một biểu tổng hợp vận hành sinh ra để chỉ ra. Ô số liệu khi ấy rỗng <b>kèm lý do</b>, và
     * {@code trangThaiTinHieu} nói ra <i>loại</i> im lặng (chưa từng có / đã ngừng / mất tín hiệu) —
     * ba tình huống cần ba hành động khác nhau.
     *
     * <p>⭐ Đây là <b>lời gọi production đầu tiên</b> của {@link StationDisplayStatus#suyRa} kể từ
     * WS-28: hàm ấy có 6 bài kiểm mà ⛔ không nơi nào gọi (nợ T28.20, luật 27 — nửa cặp đọc–ghi
     * trông y hệt cả cặp).
     */
    @Transactional(readOnly = true)
    public BieuTuyenSongView bieuTuyenSong(LocalDate ngay) {
        List<TuyenSongRow> hang = kho.tuyenSong(ngay);
        Map<Long, Long> congTrinhCuaDiemDo = kho.congTrinhChinhCuaDiemDo();
        Map<Long, TinhHinhVanHanhRef> tinhHinh = congTrinh.tinhHinhHienHanh(congTrinhCuaDiemDo.values());

        Instant bayGio = Instant.now();
        Duration khung = thamSo.khungNguon();
        int soKhungMat = thamSo.soKhungMatTinHieu();

        // ⚠ LinkedHashMap: thứ tự nhóm phải giữ nguyên thứ tự SQL đã sắp (tuyến, rồi lý trình).
        //   Một `HashMap` ở đây làm thứ tự tuyến sông đổi giữa hai lượt tải — trên màn hình tường
        //   thì đó là cả biểu nhảy chỗ mỗi 5 phút.
        Map<String, List<DiemDoTuyenView>> theoTuyen = new LinkedHashMap<>();
        for (TuyenSongRow r : hang) {
            theoTuyen
                    .computeIfAbsent(r.nhomTuyen(), k -> new ArrayList<>())
                    .add(dungDiemDo(r, congTrinhCuaDiemDo, tinhHinh, bayGio, khung, soKhungMat));
        }

        List<NhomTuyenView> tuyen = theoTuyen.entrySet().stream()
                .map(e -> new NhomTuyenView(e.getKey(), TuyenSongRow.CHUA_PHAN_TUYEN.equals(e.getKey()), e.getValue()))
                .toList();
        return new BieuTuyenSongView(ngay, tuyen);
    }

    private static DiemDoTuyenView dungDiemDo(
            TuyenSongRow r,
            Map<Long, Long> congTrinhCuaDiemDo,
            Map<Long, TinhHinhVanHanhRef> tinhHinh,
            Instant bayGio,
            Duration khung,
            int soKhungMat) {

        StationDisplayStatus tinHieu =
                StationDisplayStatus.suyRa(r.active(), r.lastSeenAt(), bayGio, khung, soKhungMat);

        String lyDo = null;
        if (r.validValue() == null) {
            lyDo = switch (tinHieu) {
                case NGUNG -> "Điểm đo đã ngừng sử dụng";
                case CHUA_CO_DU_LIEU -> "Chưa có số đo nào";
                case MAT_TIN_HIEU -> "Mất tín hiệu — chưa có số đo hợp lệ nào";
                case HOAT_DONG -> "Trạm đang phát nhưng chưa có số đo HỢP LỆ nào";
            };
        }

        Long idCongTrinh = congTrinhCuaDiemDo.get(r.stationId());
        TinhHinhVanHanhRef th = idCongTrinh == null ? null : tinhHinh.get(idCongTrinh);
        String lyDoTinhHinh = th != null
                ? null
                : idCongTrinh == null
                        ? "Điểm đo chưa liên kết công trình nào"
                        : "Công trình chưa được ghi nhận tình hình vận hành lần nào";

        return new DiemDoTuyenView(
                r.stationCode(),
                r.stationName(),
                r.positionRole(),
                r.chainage(),
                r.measurementTypeCode(),
                r.measurementTypeName(),
                r.unit(),
                r.validValue(),
                r.validMeasuredAt(),
                r.lastSeenAt(),
                r.minNgay(),
                r.maxNgay(),
                r.soBanGhiNgay(),
                tinHieu.name(),
                lyDo,
                // ⛔⛔ Lượng mưa LUÔN rỗng hôm nay và đó là câu trả lời ĐÚNG: loại chỉ số lượng mưa
                //    đã seed nhưng ⛔ CHƯA gắn cho điểm đo nào (G3-a). Trả 0 ở đây là khẳng định
                //    "trời không mưa" — một câu về thời tiết mà ta ⛔ không có nguồn nào để nói.
                null,
                "Chưa có nguồn lượng mưa (mục G3-a)",
                th == null
                        ? null
                        : new TinhHinhVanHanhView(
                                th.maTinhHinh(),
                                th.tenTinhHinh(),
                                th.mauTinhHinh(),
                                th.thamSo(),
                                th.donViThamSo(),
                                th.hieuLucTu()),
                lyDoTinhHinh);
    }

    /**
     * ⭐⭐ BC-05 — tổng hợp kỳ (T34.5).
     *
     * <p>⛔ Hàng ⛔ <b>không</b> bị lọc bỏ khi kỳ ấy rỗng: một điểm đo ⛔ không có số liệu hợp lệ là
     * <i>đúng thứ</i> người đọc báo cáo cần thấy. Ô số liệu khi ấy rỗng kèm lý do, và ràng buộc ấy
     * ép ở hàm dựng {@link TongHopKyView} chứ ⛔ không ở đây (quy tắc 16).
     */
    @Transactional(readOnly = true)
    public BaoCaoTongHopView tongHopKy(LocalDate tuNgay, LocalDate denNgay, UUID stationPublicId) {
        kiemKhoang(tuNgay, denNgay, TRAN_SO_NGAY);
        Long stationId = khoaNoiBo(stationPublicId);

        int soNgay = (int) (denNgay.toEpochDay() - tuNgay.toEpochDay() + 1);
        List<TongHopKyView> hang = kho.tongHopKy(tuNgay, denNgay, stationId).stream()
                .map(HydroReportService::dungHangTongHop)
                .toList();
        return new BaoCaoTongHopView(tuNgay, denNgay, soNgay, hang);
    }

    /**
     * ⭐ BC-12 — chi tiết theo yêu cầu (T34.6).
     *
     * <p>⚠ Bắt buộc chỉ đích danh <b>một</b> điểm đo và <b>một</b> loại chỉ số. Đó ⛔ không phải sự
     * bất tiện: nó là điều kiện để một báo cáo đọc bảng gốc còn có kích thước đọc được, và nó khớp
     * với cách người dùng thật mở báo cáo này — họ đang điều tra <i>một</i> trạm.
     */
    @Transactional(readOnly = true)
    public Page<ChiTietSoDoView> chiTiet(
            UUID stationPublicId, String maLoaiChiSo, LocalDate tuNgay, LocalDate denNgay, Pageable trang) {
        kiemKhoang(tuNgay, denNgay, TRAN_NGAY_CHI_TIET);

        Station tram = diemDo.findByPublicIdAndDeletedAtIsNull(stationPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        long idLoai = loaiChiSo
                .findByCodeAndDeletedAtIsNull(maLoaiChiSo)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004))
                .getId();

        long tong = kho.demChiTiet(tram.getId(), idLoai, tuNgay, denNgay);
        List<ChiTietSoDoView> noiDung = tong == 0
                ? List.of()
                : kho.chiTiet(tram.getId(), idLoai, tuNgay, denNgay, trang.getPageSize(), trang.getOffset()).stream()
                        .map(r -> new ChiTietSoDoView(
                                r.measuredAt(),
                                r.readingValue(),
                                r.quality(),
                                r.qualityReason(),
                                r.source(),
                                r.note(),
                                r.reviewNote()))
                        .toList();
        return new PageImpl<>(noiDung, trang, tong);
    }

    private static TongHopKyView dungHangTongHop(TongHopKyRow r) {
        String lyDo = r.rong() ? "Kỳ này ⛔ không có bản ghi hợp lệ nào" : null;
        return new TongHopKyView(
                r.stationCode(),
                r.stationName(),
                r.riverName(),
                r.positionRole(),
                r.measurementTypeCode(),
                r.measurementTypeName(),
                r.unit(),
                r.soBanGhi(),
                r.soNgayCoDuLieu(),
                r.rong() ? null : r.giaTriMin(),
                r.rong() ? null : r.mocMin(),
                r.rong() ? null : r.giaTriMax(),
                r.rong() ? null : r.mocMax(),
                r.rong() ? null : r.giaTriTb(),
                lyDo);
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
        kiemKhoang(tuNgay, denNgay, TRAN_SO_NGAY);
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
    public void kiemKhoang(LocalDate tuNgay, LocalDate denNgay, int tran) {
        if (tuNgay.isAfter(denNgay)) {
            throw new ValidationException(ErrorCode.HYD_2013);
        }
        long soNgay = denNgay.toEpochDay() - tuNgay.toEpochDay() + 1;
        if (soNgay > tran) {
            throw new ValidationException(ErrorCode.HYD_2012, tran);
        }
    }
}
