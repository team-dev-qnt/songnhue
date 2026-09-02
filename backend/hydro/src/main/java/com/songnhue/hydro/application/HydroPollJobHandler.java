package com.songnhue.hydro.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.exception.UpstreamException;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.ApiSourceStatus;
import com.songnhue.hydro.domain.SyncFailureKind;

/**
 * Chạy <b>một</b> lượt polling — T31.1 · T31.5 · T31.6 · T31.12.
 *
 * <p>Toàn bộ phần việc nằm ở {@link TelemetryIngestService}; lớp này chỉ làm ba việc mà tầng ingest
 * cố ý không làm: đọc payload, tìm nguồn, và <b>quyết định lượt này có đáng là một job đỏ không</b>.
 *
 * <h2>⭐⭐ Luật ném: ném khi lượt gọi ĐÃ XẢY RA và hỏng; ⛔ không ném khi chưa hề có lượt gọi nào</h2>
 *
 * <p>Đây là <b>nơi ném SYS-0006 đầu tiên và duy nhất</b> của dự án — mã lỗi ấy đã tồn tại từ Phase 0
 * ({@code UpstreamException}, HTTP 502) mà chưa dòng nào ném nó, đúng hình dạng luật 7: <i>một cơ
 * chế chưa ai đi qua thì chưa biết nó đúng hay sai</i>.
 *
 * <p>Luật phân nhánh ⛔ không phải một khẩu vị: nó là <b>chính đường phân chia mà lược đồ đã vẽ</b>.
 * {@code ck_hydro_raw_logs_failure_kind} nhận bốn giá trị, {@code ck_sync_logs_failure_kind} nhận
 * năm — chênh nhau đúng {@link SyncFailureKind#THIEU_MA_SO}, vì một dòng raw <i>là một lượt gọi HTTP
 * đã xảy ra</i>. Ở đây hỏi cùng một vị ngữ ({@link SyncFailureKind#duocGhiVaoRawLog()}) nên hai nơi
 * không thể lệch nhau (luật 14).
 *
 * <p>Hệ quả cụ thể: một nguồn <b>chưa ai dán mã số vào</b> ⛔ không sinh 720 job FAILED mỗi ngày. Nó
 * đã hiện đỏ trên màn hình <i>Nguồn dữ liệu</i>, đã có dòng {@code sync_logs}, đã có thông báo tới
 * người có quyền — và một màn hình việc nền đỏ rực vì một lý do ai cũng biết là một màn hình sẽ
 * không còn ai đọc (§10.42).
 *
 * <h2>⛔ Số lần thử KHÔNG khai ở đây, và con số thật là MỘT</h2>
 *
 * <p>{@link JobHandler#maxAttempts()} <b>không có người đọc trong toàn kho</b> ({@code JobWorker}
 * lấy {@code max_attempts} từ cột của bảng {@code jobs}, do {@code JobRequest} ghi). Ghi đè nó ở đây
 * là khai một con số không điều khiển gì — và <i>trông như</i> đã điều khiển (luật 15).
 *
 * <p>Con số thật đặt ở {@link HydroPollScheduler} và nó bằng <b>1</b>. Lý do đo được: backoff của
 * worker là 1' → 5' → 15', mà lượt polling kế tiếp chỉ cách <b>2 phút</b>. Thử lại ở tầng job vì thế
 * không mua thêm được gì, mà lại giữ khoá chống trùng suốt thời gian backoff — tức là <b>chặn</b>
 * chính lượt polling đúng giờ. Với một nguồn không có API lịch sử, mười lăm phút bị chặn là một
 * khung rưỡi mất vĩnh viễn. ⇒ lượt polling kế tiếp <b>chính là</b> lượt thử lại, và nó sớm hơn.
 */
@Component
public class HydroPollJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroPollJobHandler.class);

    /** Khoá duy nhất của payload — ⛔ và là thứ duy nhất được phép nằm trong đó (xem {@link #docMaNguon}). */
    private static final String KHOA_MA_NGUON_TRAN = "maNguon";

    private static final String KHOA_MA_NGUON = "\"" + KHOA_MA_NGUON_TRAN + "\"";

    private final ApiSourceService sources;
    private final TelemetryIngestService ingest;

    public HydroPollJobHandler(ApiSourceService sources, TelemetryIngestService ingest) {
        this.sources = sources;
        this.ingest = ingest;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.POLL;
    }

    /**
     * ⛔ Cố ý <b>không</b> {@code @Transactional}: lượt gọi HTTP nằm trong thân phương thức này. Một
     * nguồn treo 30 giây trong một giao dịch mở là một kết nối CSDL bị giữ 30 giây, và bốn luồng
     * worker làm thế cùng lúc là hồ kết nối cạn — triệu chứng khi ấy là <i>toàn hệ thống</i> chậm.
     * Ranh giới giao dịch nằm bên trong {@link TelemetryIngestService}, quanh những lời gọi ngắn.
     */
    @Override
    public void handle(JobContext context) {
        String maNguon = docMaNguon(context.payload());
        Optional<ApiSource> tim = sources.timTheoMa(maNguon);
        if (tim.isEmpty()) {
            // ⚠ Ném, ⛔ không bỏ qua trong im lặng: một nguồn biến mất giữa lúc đặt việc và lúc chạy
            //   nghĩa là ai đó vừa xoá nó, và người vận hành cần thấy điều đó ở đâu đó.
            throw new IllegalStateException("Không còn nguồn dữ liệu mã '" + maNguon + "' — job polling bỏ dở");
        }
        ApiSource nguon = tim.get();
        if (nguon.getStatus() != ApiSourceStatus.HOAT_DONG) {
            // Quyết định của con người, và nó thắng. ⛔ Không ghi sync_logs: không có lượt đồng bộ nào
            // xảy ra, và một dòng nhật ký mỗi 2 phút cho một nguồn đã tắt là cách làm bảng ấy vô dụng.
            // ⚠ So với HOAT_DONG chứ ⛔ không so với TAM_DUNG: thêm một giá trị vào enum sau này thì
            //   nhánh này phải mặc định là "không gọi", không phải "cứ gọi".
            log.info("Bỏ lượt polling: nguồn {} đang ở trạng thái {}", nguon.getCode(), nguon.getStatus());
            return;
        }

        KetQuaDongBo ket = ingest.chayTheoLich(nguon);
        if (ket.loi() == null || !ket.loi().duocGhiVaoRawLog()) {
            return;
        }
        // ⛔ Chi tiết kỹ thuật KHÔNG đi vào thông điệp của UpstreamException: SYS-0006 là mã lỗi có
        //   thể ra tới người dùng cuối, và `lyDo` tuy đã qua bộ che mã số vẫn là văn bản của nguồn.
        //   Nó đã nằm ở sync_logs, ở last_failure_reason và ở log — ba nơi có phân quyền.
        log.error(
                "Lượt polling nguồn {} hỏng ({}): {} — job sẽ ghi FAILED, lượt kế tiếp là lượt thử lại",
                nguon.getCode(),
                ket.loi(),
                ket.lyDo());
        throw new UpstreamException(nguon.getCode());
    }

    /**
     * ⛔ Payload chỉ mang <b>mã nguồn</b> — ⛔ tuyệt đối không mang mã số truy cập.
     *
     * <p>{@code jobs.payload} lưu nguyên văn và nằm trong <b>mọi bản sao lưu</b>
     * ({@code conventions.md} §4.7, và {@code JobRequest} đã ghi cảnh báo ấy ngay trên tham số).
     * Handler nhận mã nguồn rồi tự giải mã tại chỗ dùng, qua {@code ApiSourceService.maSoDeGoi}.
     *
     * <p>⚠ Tự bóc bằng tay chứ không dùng Jackson: payload ở đây là một chuỗi <b>do chính
     * {@link HydroPollScheduler} sinh ra</b> ba dòng phía trên trong cùng một kho — dựng cả một
     * {@code ObjectMapper} cho nó là thêm một phụ thuộc mà không thêm một bảo đảm nào. ⚠ Nhưng
     * <i>phải</i> có bài kiểm cho vòng khứ hồi sinh–bóc, vì hai hàm ở hai lớp là đúng chỗ luật 14
     * gọi tên.
     */
    static String docMaNguon(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload rỗng — job polling không biết gọi nguồn nào");
        }
        int i = payload.indexOf(KHOA_MA_NGUON);
        if (i < 0) {
            throw new IllegalArgumentException("Payload thiếu khoá '" + KHOA_MA_NGUON + "': " + payload);
        }
        int dau = payload.indexOf('"', i + KHOA_MA_NGUON.length());
        int cuoi = dau < 0 ? -1 : payload.indexOf('"', dau + 1);
        if (dau < 0 || cuoi < 0) {
            throw new IllegalArgumentException("Payload sai định dạng: " + payload);
        }
        return payload.substring(dau + 1, cuoi);
    }

    /** Sinh payload — <b>cặp đọc–ghi của {@link #docMaNguon}</b>, để hai vế ở cạnh nhau (luật 27). */
    static String payloadCho(String maNguon) {
        return "{\"" + KHOA_MA_NGUON_TRAN + "\":\"" + maNguon + "\"}";
    }
}
