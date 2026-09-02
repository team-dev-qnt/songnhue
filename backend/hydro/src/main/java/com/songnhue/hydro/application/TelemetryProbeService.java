package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.RawFetch;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.TelemetryAdapter;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;
import com.songnhue.hydro.domain.TelemetryReading;
import com.songnhue.hydro.infra.HydroRawLogWriter;
import com.songnhue.hydro.infra.StationRepository;
import com.songnhue.hydro.infra.TelemetryAdapters;

/**
 * <b>Gọi thử</b> một nguồn ngay lập tức — WS-30, và là <b>người gọi thật đầu tiên</b> của adapter.
 *
 * <h2>Vì sao WS-30 cần một màn hình, không chỉ cần một adapter</h2>
 *
 * <p>Poller ra đời ở WS-31. Nếu WS-30 dừng ở "đã viết adapter + đã có bài kiểm" thì adapter là một
 * cơ chế <b>chưa ai đi qua</b>, và luật 7 nói thẳng: <i>chưa ai đi qua thì chưa biết nó đúng hay
 * sai</i>. Bốn cột sức khoẻ nguồn cũng vậy — chúng đã có vế đọc từ 31/08 và tới hôm nay chưa có vế
 * ghi. Nút <i>Gọi thử</i> đóng cả hai nửa ấy, và nó là thứ quản trị viên thật sự cần: sau khi dán mã
 * số vào, câu hỏi đầu tiên của họ là <i>"mã này có đúng không"</i>, không phải <i>"chờ hai phút xem
 * poller nói gì"</i>.
 *
 * <h2>⭐⭐ Ba quyết định chịu lực</h2>
 *
 * <ol>
 *   <li><b>⛔ Không mở giao dịch quanh lượt gọi HTTP.</b> Một nguồn treo 30 giây × vài lượt gọi là
 *       đủ khoá cạn hồ kết nối CSDL, và triệu chứng khi ấy là <i>toàn hệ thống</i> chậm — cách rất
 *       xa nguyên nhân. Ghi CSDL nằm ở hai lời gọi ngắn <b>trước và sau</b> lượt gọi mạng.
 *   <li><b>⭐ VẪN ghi {@code hydro_raw_logs}</b> dù đây chỉ là một lượt thử. Quy tắc 18: nguồn không
 *       có API lịch sử, nên một response chứa 28 số đo thật là <b>không lấy lại được</b>. Vứt nó đi
 *       vì "chỉ là gọi thử" là vứt dữ liệu thật. Ghi bằng {@code REQUIRES_NEW} để nó sống sót kể cả
 *       khi bước sau hỏng.
 *   <li><b>⛔ KHÔNG ghi {@code hydro_readings}.</b> Phân loại chất lượng (WS-32) chưa có; đẩy số đo
 *       chưa phân loại vào bảng chính là chèn thẳng vào lớp lỗi mà quy tắc 14 gọi là <i>bẫy sai số
 *       liệu dễ mắc nhất của dự án</i>. Nguyên văn đã nằm trong raw log, nên không mất gì.
 *       ⬜ Vế ghi số đo thuộc WS-31.
 * </ol>
 *
 * <h2>⛔ Không trả thân phản hồi ra ngoài</h2>
 *
 * <p>Thân chứa chính mã số (đo 01/09/2026). {@link KetQuaGoiThu} <b>không có chỗ</b> để đặt nó vào —
 * bảo đảm ở tầng cấu trúc, không phải ở lời dặn.
 */
@Service
public class TelemetryProbeService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProbeService.class);

    /** Số mã lạ tối đa liệt kê ra màn hình — phần còn lại đếm được ở {@code hydro_unmapped_readings}. */
    private static final int TRAN_MA_LA_LIET_KE = 50;

    private final ApiSourceService sources;
    private final TelemetryAdapters adapters;
    private final StationRepository stations;
    private final HydroRawLogWriter rawLogs;
    private final ApiSourceHealthService health;
    private final TransactionTemplate giaoDichRieng;

    public TelemetryProbeService(
            ApiSourceService sources,
            TelemetryAdapters adapters,
            StationRepository stations,
            HydroRawLogWriter rawLogs,
            ApiSourceHealthService health,
            PlatformTransactionManager txManager) {
        this.sources = sources;
        this.adapters = adapters;
        this.stations = stations;
        this.rawLogs = rawLogs;
        this.health = health;
        // ⛔⛔ TransactionTemplate, ⛔ KHÔNG phải @Transactional(REQUIRES_NEW) trên một phương thức
        //    của chính lớp này: Spring chặn lời gọi ở PROXY, nên một lời gọi nội bộ đi thẳng vào
        //    phương thức và chú thích ấy KHÔNG có tác dụng nào — mà mã vẫn biên dịch, bài kiểm vẫn
        //    xanh, và giao dịch "riêng" thật ra là giao dịch chung. §10.20: dự án này đã sập 2 lần
        //    vì đúng chỗ đó.
        this.giaoDichRieng = new TransactionTemplate(txManager);
        this.giaoDichRieng.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Gọi nguồn một lượt ngay bây giờ và kể lại đã thấy gì. */
    public KetQuaGoiThu goiThu(UUID publicId) {
        ApiSource nguon = sources.get(publicId);
        ThamSoNguon thamSo = sources.thamSoHieuLuc(nguon);
        String maSo = sources.maSoDeGoi(nguon);
        Instant mocGoi = Instant.now();

        if (maSo == null) {
            // ⚠ Dừng TRƯỚC khi mở HTTP. Gọi bằng chuỗi rỗng cho ra `not.working` — trạng thái "chưa
            //   cấu hình mã số" khi ấy biến thành "mã số sai", và người vận hành đi tìm nhầm chỗ.
            //   ⛔ Cũng KHÔNG ghi hydro_raw_logs: không có lượt gọi nào thì không có response nào,
            //   và ràng buộc CHECK của bảng ấy cố ý không nhận THIEU_MA_SO.
            health.ghiNhanThatBai(nguon, mocGoi, SyncFailureKind.THIEU_MA_SO, "Nguồn chưa cấu hình mã số");
            return new KetQuaGoiThu(
                    false,
                    null,
                    0,
                    SyncFailureKind.THIEU_MA_SO,
                    "Nguồn chưa cấu hình mã số — đặt mã số rồi gọi thử lại",
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    demDiemDoDangHoatDong(nguon),
                    false,
                    null,
                    null);
        }

        TelemetryAdapter adapter = adapters.cho(nguon.getAdapterType());
        TelemetryFetch fetch = adapter.goi(new TelemetryCall(nguon.getBaseUrl(), maSo, thamSo.timeout()));
        Long rawLogId = ghiRawLog(nguon, mocGoi, thamSo.khungNguon(), fetch);

        if (!fetch.thanhCong()) {
            health.ghiNhanThatBai(nguon, mocGoi, fetch.failureKind(), fetch.failureDetail());
            return new KetQuaGoiThu(
                    false,
                    fetch.httpStatus(),
                    fetch.durationMs(),
                    fetch.failureKind(),
                    fetch.failureDetail(),
                    soByte(fetch),
                    0,
                    0,
                    0,
                    List.of(),
                    demDiemDoDangHoatDong(nguon),
                    false,
                    null,
                    rawLogId);
        }

        TelemetryBatch me = adapter.boc(fetch.body());
        health.ghiNhanThanhCong(nguon, mocGoi);
        int dangHoatDong = demDiemDoDangHoatDong(nguon);
        return new KetQuaGoiThu(
                true,
                fetch.httpStatus(),
                fetch.durationMs(),
                null,
                null,
                soByte(fetch),
                me.soDo().size(),
                me.soDongRac(),
                me.soDongTrung(),
                maChuaKhai(me),
                dangHoatDong,
                me.thieuDuLieu(dangHoatDong),
                mocDoGanNhat(me),
                rawLogId);
    }

    /**
     * Ghi nguyên văn xuống {@code hydro_raw_logs} — <b>quy tắc parse 1</b>.
     *
     * <p>{@code REQUIRES_NEW}: lượt ghi này phải sống sót độc lập với mọi thứ diễn ra sau nó. Đó là
     * bản sao <b>duy nhất</b> của response.
     *
     * <p>⚠ Nuốt ngoại lệ ở đây là cố ý và có giới hạn: một lượt <i>gọi thử</i> hỏng vì không ghi
     * được log thô vẫn phải trả lời được câu hỏi "mã số có đúng không". Nhưng ⛔ không nuốt im lặng —
     * {@code rawLogId} trả về {@code null} và màn hình nói ra, vì "đã gọi mà không lưu được" là một
     * sự cố CSDL đáng biết ngay.
     */
    private Long ghiRawLog(ApiSource nguon, Instant mocGoi, Duration khung, TelemetryFetch fetch) {
        RawFetch ban = new RawFetch(
                nguon.getId(),
                mocGoi,
                dauKhung(mocGoi, khung),
                fetch.httpStatus(),
                fetch.durationMs(),
                fetch.body(),
                fetch.failureKind(),
                fetch.failureDetail());
        try {
            return giaoDichRieng.execute(tx -> rawLogs.write(ban));
        } catch (RuntimeException e) {
            log.error("⛔ Không ghi được hydro_raw_logs cho nguồn {} — nguyên văn response đã MẤT", nguon.getCode(), e);
            return null;
        }
    }

    /**
     * Mốc đầu khung mà lượt gọi này nhắm tới — {@code floor(now / khung)}.
     *
     * <p>⚠ Chia trên <b>epoch giây</b>, ⛔ không trên giờ địa phương: khung 10 phút chia hết epoch nên
     * hai cách cho cùng kết quả hôm nay, nhưng một khung 90 phút thì không — và ta không muốn kết quả
     * phụ thuộc múi giờ của JVM (đúng cái bẫy hai-đồng-hồ đã cắn {@code HydroRetentionHandler}).
     */
    static Instant dauKhung(Instant moc, Duration khung) {
        long giay = khung.getSeconds();
        return giay <= 0 ? moc : Instant.ofEpochSecond(Math.floorDiv(moc.getEpochSecond(), giay) * giay);
    }

    private int demDiemDoDangHoatDong(ApiSource nguon) {
        return (int) stations.findByApiSourceIdAndDeletedAtIsNullOrderByCodeAsc(nguon.getId()).stream()
                .filter(Station::isActive)
                .count();
    }

    /**
     * Mã nguồn trả về mà chưa ai khai — <b>quy tắc parse 5</b>.
     *
     * <p>⛔⛔ Chỉ <i>liệt kê</i>, tuyệt đối không tự tạo điểm đo. Ta không biết mã ấy là trạm nào, ở
     * đâu, thuộc công trình gì — đó là G8, thuộc Công ty. Bản suy đoán trước đó từ biểu tổng hợp đã
     * <b>sai 1/4 mã</b> ({@code F01705} đoán là Cống Phủ Lý, thực tế là Vân Đình hạ lưu).
     *
     * <p>⚠ Tra theo <b>toàn bộ</b> {@code stations}, ⛔ không theo riêng nguồn này: một mã đã khai
     * cho nguồn khác vẫn là "đã khai", và báo nó là lạ sẽ dẫn người dùng tới việc tạo bản trùng —
     * mà {@code ux_stations_api_code} sẽ từ chối, ở một màn hình khác, với một lỗi khó hiểu.
     */
    private List<String> maChuaKhai(TelemetryBatch me) {
        Set<String> daKhai = stations.findByDeletedAtIsNullOrderByCodeAsc().stream()
                .map(Station::getApiCode)
                .collect(Collectors.toSet());
        return me.soDo().stream()
                .map(TelemetryReading::apiCode)
                .filter(ma -> !daKhai.contains(ma))
                .distinct()
                .sorted()
                .limit(TRAN_MA_LA_LIET_KE)
                .toList();
    }

    private static Instant mocDoGanNhat(TelemetryBatch me) {
        return me.soDo().stream()
                .map(TelemetryReading::measuredAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static int soByte(TelemetryFetch fetch) {
        return fetch.body() == null ? 0 : fetch.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
