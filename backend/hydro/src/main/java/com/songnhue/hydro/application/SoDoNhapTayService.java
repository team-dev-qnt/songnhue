package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.hydro.domain.ChanDoanChatLuong;
import com.songnhue.hydro.domain.LyDoNghiNgo;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.domain.NhapTayRow;
import com.songnhue.hydro.domain.PhanLoaiChatLuong;
import com.songnhue.hydro.domain.QuyTacNghiNgo;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.infra.HydroLatestRecomputer;
import com.songnhue.hydro.infra.HydroTimeSeriesWriter;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Nhập tay số đo khi API gián đoạn — CN-03.2, T32.7.
 *
 * <h2>⭐⭐ Đường này ⛔ KHÔNG phải bản sao của đường ingest — nó ngược nhau ở đúng chỗ quan trọng</h2>
 *
 * <table border="1">
 *   <caption>Ba khác biệt, cả ba đều cố ý</caption>
 *   <tr><th></th><th>Poller ghi</th><th>Người ghi (ở đây)</th></tr>
 *   <tr><td>Giá trị ngoài khoảng vật lý</td>
 *       <td>⭐ vẫn GHI, gắn cờ {@code NGHI_NGO}</td>
 *       <td>⛔ TỪ CHỐI, {@code HYD-2001}</td></tr>
 *   <tr><td>Trùng khoá</td>
 *       <td>bỏ qua im lặng ({@code ON CONFLICT DO NOTHING}) — 4/5 lượt là như vậy</td>
 *       <td>⛔ TỪ CHỐI, {@code HYD-2002} / {@code HYD-2007}</td></tr>
 *   <tr><td>{@code created_by}</td><td>⛔ luôn NULL — máy không mượn tên ai</td>
 *       <td>⛔ bắt buộc</td></tr>
 * </table>
 *
 * <p>Lý do của hàng đầu là chỗ dễ đọc nhầm nhất: số đo của <b>máy</b> không lấy lại được (nguồn ⛔
 * không có API lịch sử) nên một giá trị lạ vẫn phải được giữ; số đo của <b>người</b> thì gõ lại được
 * ngay, và một giá trị ngoài khoảng vật lý gần như chắc chắn là lỗi gõ. Lặng lẽ nhận nó rồi gắn cờ
 * là bắt chính người vừa gõ đi duyệt lỗi của mình ở một màn hình khác.
 *
 * <p>⬜ <b>Chưa nối cổng công khai.</b> Widget mực nước lên cổng ở WS-35; tới đó lượt ghi này phải
 * gọi {@code PortalCachePort} như mọi đường ghi khác, ⛔ không thì lặp lại đúng T27.7 (ba điểm ghi
 * xoá đệm, điểm thứ tư ra đời cùng đợt mang lại đúng lỗi cũ).
 */
@Service
public class SoDoNhapTayService {

    private static final Logger log = LoggerFactory.getLogger(SoDoNhapTayService.class);

    private final StationRepository stations;
    private final MeasurementTypeRepository loaiChiSo;
    private final HydroTimeSeriesWriter writer;
    private final HydroLatestRecomputer latest;
    private final HydroSettings settings;
    private final NguongAlertService nguongAlert;

    public SoDoNhapTayService(
            StationRepository stations,
            MeasurementTypeRepository loaiChiSo,
            HydroTimeSeriesWriter writer,
            HydroLatestRecomputer latest,
            HydroSettings settings,
            NguongAlertService nguongAlert) {
        this.stations = stations;
        this.loaiChiSo = loaiChiSo;
        this.writer = writer;
        this.latest = latest;
        this.settings = settings;
        this.nguongAlert = nguongAlert;
    }

    /**
     * Ghi một số đo nhập tay.
     *
     * @return khoá của dòng vừa ghi
     */
    @Transactional
    public long ghi(UUID diemDoPublicId, String maLoaiChiSo, Instant mocDo, BigDecimal giaTri, String ghiChu) {
        Station diemDo = stations.findByPublicIdAndDeletedAtIsNull(diemDoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        MeasurementType loai = loaiChiSo
                .findByCodeAndDeletedAtIsNull(maLoaiChiSo)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        kiemMocDo(mocDo);
        kiemKhoangVatLy(loai, mocDo, giaTri);
        kiemOTrong(diemDo.getId(), loai.getId(), mocDo);

        AuthenticatedUser nguoiNhap = AuthContext.require();
        long id;
        try {
            id = writer.writeManual(
                    new NhapTayRow(diemDo.getId(), loai.getId(), mocDo, giaTri, nguoiNhap.userId(), ghiChu));
        } catch (DuplicateKeyException e) {
            // ⚠ Giữa lượt kiểm ở trên và lượt ghi này có một khe hở, và poller chạy 2 phút/lần —
            //   nó ghi được đúng khung ấy trong khoảnh khắc người dùng đang bấm Lưu. Kiểm trước là
            //   để nói được VÌ SAO; bắt ở đây là để không bao giờ trả về 500.
            throw new BusinessRuleException(ErrorCode.HYD_2007, e, mocDo.toString());
        }

        // ⚠ DỰNG LẠI, ⛔ không UPSERT: một dòng nhập tay có thể mang mốc QUÁ KHỨ (bù dữ liệu cho
        //   quãng API chết), và lượt UPSERT của poller cố ý không lùi. Dựng lại từ toàn bộ lịch sử
        //   của cặp ấy là đường duy nhất luôn đúng — xem HydroLatestRecomputer.
        latest.dungLai(diemDo.getId(), loai.getId());

        // ⭐ WS-33 / T33.5 — đường ghi THỨ HAI của một số đo hợp lệ, và nó phải đánh giá ngưỡng y
        //   như đường poller. Một số đo nhập tay lúc API chết là một quan sát THẬT của người trực;
        //   bỏ qua nó ở đây nghĩa là đúng quãng nguồn hỏng — quãng nguy hiểm nhất — thì cảnh báo
        //   ngưỡng im lặng tắt.
        // ⚠ `writeManual` luôn ghi `quality = HOP_LE` (không có cột nào để ghi khác), nên hằng số
        //   dưới đây ⛔ không phải một giả định: nó là hình dạng của chính câu INSERT.
        nguongAlert.danhGia(diemDo.getId(), loai.getId(), mocDo, giaTri, ReadingQuality.HOP_LE);

        log.info(
                "Nhập tay số đo #{}: điểm đo {} · {} · mốc {} · {} {} · người nhập {}",
                id,
                diemDo.getCode(),
                loai.getCode(),
                mocDo,
                giaTri.toPlainString(),
                loai.getUnit(),
                nguoiNhap.username());
        return id;
    }

    /**
     * ⛔ Mốc đo ⛔ không được ở tương lai.
     *
     * <p>Cùng khuyết tật V3 đã tìm ra ở {@code ConstructionOperationStatusService}: một dòng đề ngày
     * tương lai <b>ghim</b> mọi thứ dẫn xuất từ nó cho tới khi tới ngày ấy. Ở đây hậu quả nặng hơn:
     * {@code hydro_latest.valid_measured_at} nhảy tới mốc tương lai, và <b>mọi số đo thật</b> về sau
     * đều "cũ hơn" nên ⛔ không bao giờ được hiển thị — widget cổng đứng im ở một con số gõ nhầm.
     *
     * <p>⚠ Cho một biên 5 phút: đồng hồ máy chủ và đồng hồ người dùng không bao giờ khớp tuyệt đối,
     * và một người trực gõ "bây giờ" ⛔ không đáng bị từ chối vì lệch vài giây.
     */
    private static void kiemMocDo(Instant mocDo) {
        if (mocDo.isAfter(Instant.now().plusSeconds(300))) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("mocDo", "KHONG_DUOC_O_TUONG_LAI", mocDo.toString());
        }
    }

    /**
     * ⭐ {@code HYD-2001} — nối vào đường chạy thật (T32.8).
     *
     * <p>⚠ Truyền {@code null} cho mốc so sánh: chỉ hỏi câu hỏi <b>tĩnh</b> ("con số này có thể là
     * số thật không"). Câu hỏi động ("nó nhảy nhanh không") ⛔ <b>không</b> áp cho đường nhập tay —
     * người trực nhập tay <i>chính vì</i> có chuyện bất thường đang xảy ra, và từ chối họ đúng lúc
     * ấy là làm chức năng này vô dụng đúng lúc cần nhất.
     */
    private void kiemKhoangVatLy(MeasurementType loai, Instant mocDo, BigDecimal giaTri) {
        QuyTacNghiNgo quyTac = settings.quyTacNghiNgo().cho(loai.getCode());
        ChanDoanChatLuong chanDoan = PhanLoaiChatLuong.danhGia(giaTri, mocDo, quyTac, null);
        if (chanDoan.lyDo() == LyDoNghiNgo.NGOAI_KHOANG_VAT_LY) {
            throw new BusinessRuleException(ErrorCode.HYD_2001, chanDoan.moTa());
        }
    }

    /**
     * ⭐ {@code HYD-2002} — nối vào đường chạy thật (T32.8), và nó nói được <b>phải làm gì tiếp</b>.
     *
     * <p>Ô đã bị chiếm bởi một bản ghi {@code NGHI_NGO} là tình huống <i>hay xảy ra nhất</i>: máy vừa
     * ghi một số đáng ngờ cho đúng khung ấy, và người trực nhập tay để sửa. Một thông báo "trùng dữ
     * liệu" chung chung để họ đứng đó không biết làm gì; {@code HYD-2002} chỉ thẳng sang màn hình
     * <i>Dữ liệu nghi ngờ</i> — nơi họ loại bỏ bản ghi kia rồi quay lại.
     *
     * <p>⛔ Và ⛔ tuyệt đối không tự ghi đè: bản ghi đang nằm đó là bằng chứng nguyên trạng của thứ
     * nguồn đã trả về. Xoá nó phải là một bước chuyển có người bấm và có lý do (quy tắc 18).
     */
    private void kiemOTrong(long stationId, long measurementTypeId, Instant mocDo) {
        writer.chatLuongTaiO(stationId, measurementTypeId, mocDo).ifPresent(dangCo -> {
            if (dangCo == ReadingQuality.NGHI_NGO) {
                throw new BusinessRuleException(ErrorCode.HYD_2002);
            }
            throw new BusinessRuleException(ErrorCode.HYD_2007, mocDo.toString());
        });
    }
}
