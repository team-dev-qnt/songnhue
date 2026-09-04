package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.hydro.domain.StationDisplayStatus;
import com.songnhue.hydro.domain.TinHieuDiemDo;
import com.songnhue.hydro.infra.AlertEventQueryRepository;
import com.songnhue.hydro.infra.PollerRepository;
import com.songnhue.hydro.spi.HydroAlertQueryPort;
import com.songnhue.hydro.spi.HydroLatestQueryPort;

/**
 * Cài đặt hai cổng {@code hydro.spi} — <b>T35.6</b>, mở ranh giới {@code hydro} cho
 * {@code operations} đọc.
 *
 * <h2>⭐⭐ ⛔ KHÔNG viết truy vấn thứ hai cho "mất tín hiệu"</h2>
 *
 * <p>Cám dỗ hiển nhiên ở đây là một câu {@code SELECT count(*) … WHERE last_seen_at < now() - …}.
 * ⛔ Đừng. Định nghĩa "mất tín hiệu" <b>đã tồn tại</b> và đang được job rà tín hiệu 5 phút/lần dùng:
 * {@link StationDisplayStatus#suyRa} đọc {@link PollerRepository#tinHieuDiemDo()} với đúng hai tham
 * số {@code settings}. Viết câu SQL thứ hai là dựng một định nghĩa song song, và hai định nghĩa ấy
 * sẽ lệch nhau đúng vào ngày ai đó chỉnh {@code hydro.station.signal-loss-frames} — người trực nhìn
 * ô KPI thấy 3 trạm im lặng trong khi email cảnh báo nói 5.
 *
 * <p>⇒ Lớp này đi qua <b>cùng một hàm</b> mà {@code HydroSignalLossHandler} đi qua. Cái giá là đọc
 * cả danh mục (19 dòng hôm nay, và trần thực tế là vài trăm) thay vì để CSDL đếm — rẻ hơn nhiều so
 * với hai con số không khớp mà không ai dựng lại được.
 *
 * <h2>⚠ Một ảnh chụp, ⛔ không phải ba lượt đếm</h2>
 *
 * <p>{@code bayGio} lấy <b>một lần</b> rồi dùng cho cả ba phép phân loại. Gọi {@code Instant.now()}
 * ba lần là ba mốc, và một điểm đo nằm đúng biên có thể được đếm hai lần hoặc không lần nào — ô KPI
 * hiện "20 / 19" mà không ai dựng lại được.
 *
 * <h2>⛔ Vì sao MỘT lớp cài hai cổng</h2>
 *
 * <p>Hai cổng là hai câu hỏi của <b>cùng một người gọi</b> (bảng KPI dashboard) và luôn được hỏi
 * trong cùng một lượt. Tách làm hai bean là hai lần tiêm, hai lần dựng, không đổi lại được gì. ⛔
 * Nhưng chúng vẫn là <b>hai interface</b>: người gọi sau chỉ cần một trong hai thì không phải kéo
 * theo cái kia.
 */
@Service
public class HydroQueryAdapter implements HydroLatestQueryPort, HydroAlertQueryPort {

    private final PollerRepository poller;
    private final AlertEventQueryRepository alerts;
    private final HydroSettings settings;

    public HydroQueryAdapter(PollerRepository poller, AlertEventQueryRepository alerts, HydroSettings settings) {
        this.poller = poller;
        this.alerts = alerts;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public TinhTrangTinHieu tinhTrangTinHieu() {
        Duration khung = settings.khungNguon();
        int soKhung = settings.soKhungMatTinHieu();
        Instant bayGio = Instant.now();

        List<TinHieuDiemDo> tinHieu = poller.tinHieuDiemDo();

        long dangDung = 0;
        long matTinHieu = 0;
        long chuaCoDuLieu = 0;
        for (TinHieuDiemDo t : tinHieu) {
            StationDisplayStatus tt = t.trangThai(bayGio, khung, soKhung);
            // ⛔ NGUNG ⛔ không vào mẫu số: người vận hành đã quyết định không dùng điểm đo ấy nữa,
            //    nên đưa nó vào "19 điểm đo đang theo dõi" là báo cáo một phạm vi không có thật.
            if (tt == StationDisplayStatus.NGUNG) {
                continue;
            }
            dangDung++;
            if (tt == StationDisplayStatus.MAT_TIN_HIEU) {
                matTinHieu++;
            } else if (tt == StationDisplayStatus.CHUA_CO_DU_LIEU) {
                chuaCoDuLieu++;
            }
        }
        return new TinhTrangTinHieu(dangDung, matTinHieu, chuaCoDuLieu);
    }

    @Override
    @Transactional(readOnly = true)
    public long demCanhBaoDangXayRa() {
        return alerts.demDangCanhBao();
    }
}
