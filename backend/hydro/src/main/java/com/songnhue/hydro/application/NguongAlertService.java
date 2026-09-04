package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.ConstructionStatusPort;
import com.songnhue.hydro.domain.AlertEventStatus;
import com.songnhue.hydro.domain.CanhBaoDangMo;
import com.songnhue.hydro.domain.DanhGiaNguong;
import com.songnhue.hydro.domain.KetLuanNguong;
import com.songnhue.hydro.domain.NguongApDung;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.SoDoTruoc;
import com.songnhue.hydro.infra.AlertEngineRepository;
import com.songnhue.hydro.infra.AlertEventWriter;
import com.songnhue.hydro.infra.StationConstructionRepository;

/**
 * ⭐⭐ Máy cảnh báo ngưỡng — <b>WS-33</b>, và là nguồn thật của <b>mắt xích 3</b>.
 *
 * <h2>Việc này đóng cái gì</h2>
 *
 * <p>Từ Phase 1 tới hôm nay, {@code DummyHydroAlertService.hasActiveAlert()} trả {@code false} ghi
 * cứng. Nghĩa là trạng thái {@code CANH_BAO} của một công trình <b>chưa bao giờ có công trình nào
 * chạm tới được</b>, trong khi chuỗi 6 mắt xích của {@code ConstructionStatusService.tinh()} trông
 * như đã phủ — bài kiểm mock cổng ấy nên nó luôn trả lời đúng thứ bài kiểm dặn (luật 19: <i>việc làm
 * xong nửa đường trông y hệt việc làm xong</i>).
 *
 * <h2>⛔ Ba điều lớp này KHÔNG làm, và mỗi điều là một bẫy đã trả giá</h2>
 *
 * <ol>
 *   <li>⛔ <b>Không bao giờ đọc {@code hydro_latest}.</b> Nó chỉ đánh giá số đo <i>vừa được ghi
 *       trong chính giao dịch này</i>. Đó là cách {@code HYD-2004} (<i>"điểm đo đang mất tín hiệu —
 *       không dùng giá trị cũ để đánh giá ngưỡng"</i>) được bảo đảm ở tầng <b>cấu trúc</b> thay vì
 *       bằng một vế {@code if} mà ai đó phải nhớ: không có đường nào để một giá trị cũ đi vào đây.
 *   <li>⛔ <b>Không đánh giá số đo {@code NGHI_NGO}.</b> Quy tắc 14 — bẫy sai số liệu dễ mắc nhất
 *       của dự án. Một cảm biến hỏng báo 9,99 m ⛔ không được đánh thức Ban điều hành lúc 2 giờ sáng.
 *   <li>⛔ <b>Không tự sinh {@code maintenance_logs}.</b> Cảnh báo là một quan sát; bản ghi khắc phục
 *       là một quyết định của con người (T33.10).
 * </ol>
 *
 * <h2>⭐ Vì sao {@code Propagation.MANDATORY}</h2>
 *
 * <p>T33.5 đòi đánh giá <b>trong cùng giao dịch ghi số đo</b>. {@code MANDATORY} biến yêu cầu ấy từ
 * một lời dặn thành một lỗi lúc chạy: gọi lớp này ngoài giao dịch là
 * {@code IllegalTransactionStateException} ngay lượt đầu, ⛔ không phải một cảnh báo bị mất trong im
 * lặng vào một ngày nào đó. <i>"Số đo được ghi mà cảnh báo không được ghi"</i> vì thế là một trạng
 * thái <b>không tồn tại được</b>, không phải một trạng thái hiếm.
 *
 * <h2>⚠ Đường vào: BA nơi, và một bộ canh đếm đủ chúng</h2>
 *
 * <p>Luật 12 nói đặt bảo đảm ở <i>chỗ dữ liệu đi qua</i>. Ở đây ⛔ không có một chỗ như vậy: ba
 * đường tạo ra một số đo {@code HOP_LE} đi qua ba cơ chế khác nhau —
 * {@code TelemetryIngestService} (poller, JDBC hàng loạt), {@code SoDoNhapTayService} (nhập tay,
 * JDBC một dòng) và {@code HydroReviewService} (duyệt {@code NGHI_NGO → HOP_LE}, JPA + workflow).
 * ⇒ Áp vế thứ hai của luật 12: <b>một phép kiểm đếm đủ các đường vào</b> —
 * {@code AlertHookCoverageTest}. Nó tồn tại vì T27.7 đã trả nợ đệm cổng ở <i>ba</i> điểm ghi và
 * điểm ghi thứ tư ra đời cùng đợt mang lại đúng lỗi cũ.
 */
@Service
public class NguongAlertService {

    private static final Logger log = LoggerFactory.getLogger(NguongAlertService.class);

    /** Mã sự kiện của {@code notifications} — ⛔ đừng đổi, lịch sử thông báo tra theo nó. */
    public static final String SU_KIEN_VUOT_NGUONG = "HYDRO_THRESHOLD_BREACHED";

    private final AlertEngineRepository nguongs;
    private final AlertEventWriter events;
    private final AlertNotifier notifier;
    private final StationConstructionRepository lienKets;
    private final ConstructionStatusPort trangThaiCongTrinh;

    public NguongAlertService(
            AlertEngineRepository nguongs,
            AlertEventWriter events,
            AlertNotifier notifier,
            StationConstructionRepository lienKets,
            ConstructionStatusPort trangThaiCongTrinh) {
        this.nguongs = nguongs;
        this.events = events;
        this.notifier = notifier;
        this.lienKets = lienKets;
        this.trangThaiCongTrinh = trangThaiCongTrinh;
    }

    /**
     * Đánh giá <b>một</b> số đo vừa ghi.
     *
     * <p>⚠ Gọi <b>sau</b> lượt ghi, ⛔ không trước: mốc so sánh của {@code RATE_OF_CHANGE} đọc
     * {@code hydro_readings}, và dòng vừa ghi ⛔ không được tự làm mốc cho chính nó — câu
     * {@code SQL_SO_DO_TRUOC} vì thế dùng {@code measured_at < ?} chứ không {@code <=}.
     *
     * @param chatLuong ⛔ chỉ {@link ReadingQuality#HOP_LE} được đánh giá; mọi giá trị khác thoát
     *     ngay, ⛔ không log ở mức cảnh báo (nghi ngờ là chuyện bình thường, không phải sự cố)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void danhGia(
            long stationId, long measurementTypeId, Instant mocDo, BigDecimal giaTri, ReadingQuality chatLuong) {
        if (chatLuong != ReadingQuality.HOP_LE) {
            return;
        }
        List<NguongApDung> apDung = nguongs.nguongCua(stationId, measurementTypeId);
        if (apDung.isEmpty()) {
            // T33.6 — ⛔ KHÔNG phải lỗi, ⛔ không log mức cảnh báo. Điểm đo chưa cấu hình ngưỡng là
            //   trạng thái hợp lệ khi G9-a chưa chốt; danh sách nhắc việc đọc từ `alert_rules`, và
            //   một dòng log mỗi 2 phút × 28 trạm là 20 nghìn dòng rác mỗi ngày.
            return;
        }

        // ⚠ Nạp LƯỜI: chỉ RATE_OF_CHANGE cần mốc so sánh, và nó là loại hiếm nhất. Truy vấn vô điều
        //   kiện ở đây là một lượt đọc `hydro_readings` cho MỌI số đo — 28 trạm × 720 lượt/ngày.
        SoDoTruoc truoc = null;
        boolean daNap = false;

        for (NguongApDung nguong : apDung) {
            if (nguong.dieuKien().canMocSoSanh() && !daNap) {
                truoc = nguongs.soDoHopLeTruoc(stationId, measurementTypeId, mocDo)
                        .orElse(null);
                daNap = true;
            }
            KetLuanNguong ketLuan = DanhGiaNguong.danhGia(giaTri, mocDo, nguong.dieuKien(), truoc);
            xuLyKetLuan(nguong, ketLuan, mocDo, giaTri);
        }
    }

    private void xuLyKetLuan(NguongApDung nguong, KetLuanNguong ketLuan, Instant mocDo, BigDecimal giaTri) {
        Optional<CanhBaoDangMo> dangMo = nguongs.canhBaoDangMo(nguong.ruleId());

        switch (ketLuan.trangThai()) {
            case VI_PHAM -> {
                if (dangMo.isPresent()) {
                    tiepTuc(nguong, dangMo.get(), mocDo, giaTri);
                } else {
                    moMoi(nguong, mocDo, giaTri, ketLuan.moTa());
                }
            }
            case KHONG_VI_PHAM -> dangMo.ifPresent(mo -> ketThuc(nguong, mo, mocDo));
            case KHONG_KET_LUAN_DUOC -> {
                // ⛔⛔ KHÔNG đóng cảnh báo đang mở. "Không kết luận được" nghĩa là THIẾU BẰNG CHỨNG
                //   (chưa có số đo hợp lệ nào trước đó để tính tốc độ đổi), ⛔ không phải "đã hết
                //   vượt ngưỡng". Gộp hai thứ này là để một lượt thiếu dữ liệu tự tay tắt một cảnh
                //   báo đang thật — đúng hình dạng "số 0 là một câu khẳng định" (quy tắc 16).
                log.debug(
                        "Ngưỡng #{} không kết luận được tại mốc {}: {}",
                        nguong.ruleId(),
                        mocDo,
                        ketLuan.lyDoKhongKetLuan());
            }
            // ⛔ Nhánh này KHÔNG bao giờ chạy với ba giá trị hiện có — nó tồn tại để một giá trị
            //   thứ tư thêm vào `KetLuanNguong.TrangThai` sau này NỔ LỚN TIẾNG thay vì lặng lẽ rơi
            //   xuống "không làm gì", tức lặng lẽ không bắn cảnh báo. Đây là chỗ quy tắc 4 áp vào
            //   một câu `switch`: giá trị lạ phải bị từ chối, ⛔ không được bỏ qua.
            default -> throw new IllegalStateException("Kết luận ngưỡng chưa có nhánh xử lý: " + ketLuan.trangThai());
        }
    }

    private void moMoi(NguongApDung nguong, Instant mocDo, BigDecimal giaTri, String lyDo) {
        // Trễ 0 phút ⇒ xác nhận ngay tại mốc đo, và thông báo đi trong cùng lượt này.
        boolean xacNhanNgay = nguong.treTrongPhut() == 0;
        Optional<Long> id = events.mo(
                nguong.ruleId(),
                nguong.stationId(),
                nguong.measurementTypeId(),
                nguong.alertLevelId(),
                mocDo,
                xacNhanNgay ? mocDo : null,
                giaTri,
                lyDo);

        if (id.isEmpty()) {
            // ⚠ Kết quả BÌNH THƯỜNG, ⛔ không phải lỗi: một dòng đã chiếm chỗ (lượt đánh giá chạy
            //   lại trên cùng mốc, hoặc một giao dịch song song vừa mở). Chỉ mục là thứ quyết định,
            //   ⛔ không phải lượt đọc ở trên — giữa đọc và ghi luôn có một khe hở.
            log.debug("Ngưỡng #{} đã có cảnh báo chiếm chỗ tại mốc {} — bỏ qua lượt mở", nguong.ruleId(), mocDo);
            return;
        }
        if (xacNhanNgay) {
            notifier.baoVuotNguong(id.get(), nguong, giaTri, lyDo, mocDo);
            tinhLaiTrangThai(nguong.stationId());
        } else {
            log.info(
                    "Ngưỡng #{} bắt đầu vượt tại {} — theo dõi {} phút trước khi báo động",
                    nguong.ruleId(),
                    mocDo,
                    nguong.treTrongPhut());
        }
    }

    private void tiepTuc(NguongApDung nguong, CanhBaoDangMo mo, Instant mocDo, BigDecimal giaTri) {
        if (AlertEngineRepository.nangHon(giaTri, mo.dinh(), nguong.loai())) {
            events.napDinh(mo.id(), giaTri, mocDo);
        }
        if (mo.daXacNhan()) {
            return;
        }
        // ⭐ Xác nhận bằng MỘT QUAN SÁT KHÁC vẫn còn vượt, ⛔ không bằng đồng hồ treo tường. Trạm tắt
        //   giữa chừng thì cảnh báo ở lại trạng thái theo dõi và ⛔ không ai bị đánh thức — ta không
        //   có bằng chứng nào rằng nó còn vượt.
        Duration daGiu = Duration.between(mo.batDau(), mocDo);
        if (daGiu.toMinutes() < nguong.treTrongPhut()) {
            return;
        }
        if (events.xacNhan(mo.id(), mocDo)) {
            String lyDo = "Vượt ngưỡng liên tục %d phút (đỉnh %s)".formatted(daGiu.toMinutes(), mo.dinh());
            notifier.baoVuotNguong(mo.id(), nguong, giaTri, lyDo, mocDo);
            tinhLaiTrangThai(nguong.stationId());
        }
    }

    private void ketThuc(NguongApDung nguong, CanhBaoDangMo mo, Instant mocDo) {
        // Chưa từng xác nhận ⇒ chưa ai nhận thông báo nào ⇒ đây là một cú nhiễu, ⛔ không phải một
        // cảnh báo đã được xử lý. Hai kết cục khác nhau, và màn hình lịch sử phải phân biệt được.
        AlertEventStatus ket = mo.daXacNhan() ? AlertEventStatus.DA_XU_LY : AlertEventStatus.FALSE_ALARM;
        // resolvedBy = null: MÁY đóng vì giá trị về dưới ngưỡng. ⛔ Không lấy người đang đăng nhập —
        // lượt này thường chạy trong job poller, nơi không có ai đăng nhập cả.
        if (events.dong(mo.id(), ket, mocDo, null, null) && mo.daXacNhan()) {
            notifier.baoHetVuotNguong(mo.id(), nguong, mo, mocDo);
            tinhLaiTrangThai(nguong.stationId());
        }
    }

    /**
     * ⭐⭐ Tính lại trạng thái dẫn xuất của các công trình gắn với điểm đo này — <b>T33.9</b>.
     *
     * <h2>Vì sao bước này phải có, và vì sao nó suýt không có</h2>
     *
     * <p>{@code constructions.operational_status} là một cột <b>vật chất hoá</b>: mọi màn hình, mọi
     * marker bản đồ và cổng công khai đọc cột ấy chứ ⛔ không gọi lại hàm tính. Máy cảnh báo ghi vào
     * {@code alert_events} và ⛔ <b>không có gì</b> bảo cột kia tính lại.
     *
     * <p>⚠ Lượt chạy đầu tiên của {@code AlertEngineHttpTest} đo được đúng chuyện đó: cảnh báo mở
     * đúng, dòng {@code alert_events} đúng, {@code reason} đúng — và {@code operational_status} vẫn
     * là {@code BINH_THUONG}. Nhìn vào bảng {@code alert_events} thì mọi thứ hoàn hảo; thứ người
     * dùng nhìn thì không đổi gì. Đúng luật 27, và nó chỉ lộ ra vì bài kiểm đo <b>vòng khép kín</b>
     * chứ không đo từng mảnh.
     *
     * <p>⚠ Gọi ở <b>cả hai</b> chiều — lúc xác nhận và lúc đóng. Chỉ gọi lúc xác nhận thì công
     * trình kẹt ở {@code CANH_BAO} vĩnh viễn sau khi nước đã rút, và đó là nửa cặp đọc–ghi ở dạng
     * tinh vi hơn: nó <i>có</i> chạy, chỉ là chạy một chiều.
     */
    private void tinhLaiTrangThai(long stationId) {
        List<Long> congTrinhIds = lienKets.findByStationIdAndDeletedAtIsNull(stationId).stream()
                .map(com.songnhue.hydro.domain.StationConstruction::getConstructionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        trangThaiCongTrinh.recomputeFor(congTrinhIds);
    }
}
