package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.spi.SecurityEventPort;
import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.ApiSourceStatus;
import com.songnhue.hydro.infra.ApiSourceRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Nguồn dữ liệu bên thứ ba — T28.2 / T28.8.
 *
 * <h2>⛔ Ba việc lớp này KHÔNG làm, và không được thêm về sau</h2>
 *
 * <ol>
 *   <li><b>Không trả mã số ra ngoài</b> — kể cả cho Admin, kể cả dạng che một phần. Endpoint chỉ
 *       biết {@link ApiSource#isCredentialDaCauHinh()}. Che một phần vẫn là lộ: bốn ký tự cuối của
 *       một mã số ngắn thu hẹp không gian tìm kiếm rất nhiều.
 *   <li><b>Không log mã số</b>, không ở mức DEBUG, không trong thông điệp lỗi.
 *   <li><b>Không đưa mã số vào payload của {@code jobs}</b> — payload lưu nguyên văn và nằm trong
 *       mọi bản sao lưu. Handler nhận {@code sourceId} rồi tự giải mã tại chỗ dùng.
 * </ol>
 */
@Service
public class ApiSourceService {

    private static final Logger log = LoggerFactory.getLogger(ApiSourceService.class);

    private final ApiSourceRepository sources;
    private final StationRepository stations;
    private final HydroSettings settings;
    private final CryptoService crypto;
    private final SecurityEventPort securityEvents;

    public ApiSourceService(
            ApiSourceRepository sources,
            StationRepository stations,
            HydroSettings settings,
            CryptoService crypto,
            SecurityEventPort securityEvents) {
        this.sources = sources;
        this.stations = stations;
        this.settings = settings;
        this.crypto = crypto;
        this.securityEvents = securityEvents;
    }

    @Transactional(readOnly = true)
    public List<ApiSource> list() {
        return sources.findByDeletedAtIsNullOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public ApiSource get(UUID publicId) {
        return tim(publicId);
    }

    /**
     * Tra nguồn theo <b>mã</b> — đường vào của poller (T31.1).
     *
     * <p>⚠ Poller tra bằng {@code code}, ⛔ không bằng {@code public_id}, và đó là chủ ý: mã nguồn là
     * thứ nằm trong {@code jobs.payload}, trong dòng log và trong runbook — người trực đọc
     * {@code BHH40} thì biết ngay đang nói về cái gì, còn một UUID thì phải tra thêm một lượt. Payload
     * là văn bản người đọc, không chỉ là dữ liệu máy đọc.
     *
     * @return rỗng khi nguồn đã bị xoá mềm giữa lúc đặt việc và lúc chạy — nơi gọi phải nói ra
     */
    @Transactional(readOnly = true)
    public Optional<ApiSource> timTheoMa(String code) {
        return code == null || code.isBlank()
                ? Optional.empty()
                : sources.findByCodeAndDeletedAtIsNull(code.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Các nguồn poller phải gọi theo lịch — ⛔ đã loại nguồn người vận hành tạm dừng.
     *
     * <p>⚠ Lọc {@link ApiSourceStatus#HOAT_DONG} bằng phép <b>bằng</b>, ⛔ không bằng phép khác
     * {@code TAM_DUNG}: thêm một giá trị vào enum về sau thì nhánh mặc định phải là <i>"không gọi"</i>,
     * không phải <i>"cứ gọi"</i>. Cùng vị ngữ với {@code HydroPollJobHandler}, và cả hai đều kiểm —
     * trạng thái có thể đổi giữa lúc đặt việc và lúc chạy.
     */
    @Transactional(readOnly = true)
    public List<ApiSource> nguonDangHoatDong() {
        return sources.findByDeletedAtIsNullOrderByCodeAsc().stream()
                .filter(n -> n.getStatus() == ApiSourceStatus.HOAT_DONG)
                .toList();
    }

    /**
     * ⭐ Giải bốn tham số nhịp — <b>nơi duy nhất</b> quyết định "cột riêng hay tham số chung".
     *
     * <p>Có đúng một hàm làm việc này là điều kiện để cột nullable của {@code api_sources} và bảng
     * {@code settings} không thành hai nguồn sự thật. Poller, màn hình chi tiết và bài kiểm đều gọi
     * hàm này; ⛔ không nơi nào được tự đọc {@code source.getCron()} rồi tự chọn mặc định.
     */
    @Transactional(readOnly = true)
    public ThamSoNguon thamSoHieuLuc(ApiSource nguon) {
        String cron = nguon.getCron();
        Integer khung = nguon.getFrameMinutes();
        Integer timeout = nguon.getTimeoutSeconds();
        Integer thuLai = nguon.getMaxRetry();
        return new ThamSoNguon(
                cron == null || cron.isBlank() ? settings.cronPolling() : cron,
                cron == null || cron.isBlank(),
                khung == null ? settings.khungNguon() : Duration.ofMinutes(khung),
                khung == null,
                timeout == null ? settings.timeoutGoiNguon() : Duration.ofSeconds(timeout),
                timeout == null,
                thuLai == null ? settings.soLanThuLai() : thuLai,
                thuLai == null);
    }

    @Transactional
    public ApiSource create(String code, String name, AdapterType adapterType, String baseUrl, String description) {
        String ma = chuanHoaMa(code);
        if (sources.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HYD_1002, ma);
        }
        ApiSource nguon = new ApiSource(ma, batBuoc(name), batBuoc(adapterType), diaChi(baseUrl));
        nguon.setDescription(description);
        log.info("Thêm nguồn dữ liệu {}", ma);
        return sources.save(nguon);
    }

    @Transactional
    public ApiSource update(UUID publicId, ApiSourceForm form) {
        ApiSource nguon = tim(publicId);
        String lich = form.cron();
        nguon.setName(batBuoc(form.name()));
        nguon.setBaseUrl(diaChi(form.baseUrl()));
        nguon.setFrameMinutes(trongKhoang(form.frameMinutes(), 1, 1440));
        nguon.setTimeoutSeconds(trongKhoang(form.timeoutSeconds(), 5, 300));
        nguon.setMaxRetry(trongKhoang(form.maxRetry(), 0, 10));
        nguon.setCron(lich == null || lich.isBlank() ? null : lich.trim());
        nguon.setStatus(batBuoc(form.status()));
        nguon.setDescription(form.description());
        return sources.save(nguon);
    }

    /**
     * Đặt hoặc thay mã số truy cập.
     *
     * <p>Mã hoá ngay tại đây và <b>không giữ lại chuỗi thô ở bất kỳ trường nào</b>. Ghi một sự kiện
     * bảo mật cho cả hai trường hợp — người xem nhật ký cần phân biệt "lần đầu cấu hình" với "có
     * người thay mã số đang chạy", vì vế sau là thứ đáng hỏi lại.
     *
     * @param maSoTho mã số nguyên văn từ giao diện. ⚠ Giữ nguyên dấu {@code ;} cuối nếu nguồn đòi —
     *     ⛔ không {@code trim()} ở đây, vì với {@code bhh40.net} dấu ấy là một phần của giá trị.
     */
    @Transactional
    public void datMaSo(UUID publicId, String maSoTho) {
        if (maSoTho == null || maSoTho.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        ApiSource nguon = tim(publicId);
        boolean daCo = nguon.isCredentialDaCauHinh();
        nguon.datCredential(crypto.encrypt(maSoTho));
        sources.save(nguon);
        securityEvents.externalCredentialChanged(nguon.getCode(), daCo ? "THAY" : "DAT_LAN_DAU");
        log.info("Đã {} mã số nguồn {}", daCo ? "thay" : "đặt", nguon.getCode());
    }

    /** Gỡ mã số — nguồn quay về trạng thái "chưa cấu hình" và poller từ chối chạy, có ghi lý do. */
    @Transactional
    public void xoaMaSo(UUID publicId) {
        ApiSource nguon = tim(publicId);
        if (!nguon.isCredentialDaCauHinh()) {
            return;
        }
        nguon.datCredential(null);
        sources.save(nguon);
        securityEvents.externalCredentialChanged(nguon.getCode(), "XOA");
        log.info("Đã gỡ mã số nguồn {}", nguon.getCode());
    }

    /**
     * Giải mã mã số <b>ngay tại thời điểm dùng</b> — chỉ poller/adapter gọi.
     *
     * <p>⛔ Giá trị trả về không được lưu vào trường nào, không được log, không được đưa vào payload
     * job. Vòng đời của nó là một lời gọi HTTP.
     *
     * <p>Giải mã hỏng nghĩa là bản mã và khoá AES hiện tại không khớp — khoá vừa xoay mà chưa mã hoá
     * lại, hoặc CSDL khôi phục từ bản sao lưu cũ hơn lần xoay khoá. Cả hai đều im lặng ở tầng ứng
     * dụng và trông y hệt "nguồn không phản hồi", nên phải để lại một sự kiện bảo mật.
     *
     * @return mã số nguyên văn, hoặc {@code null} khi nguồn chưa cấu hình
     */
    @Transactional(readOnly = true)
    public String maSoDeGoi(ApiSource nguon) {
        if (!nguon.isCredentialDaCauHinh()) {
            return null;
        }
        try {
            return crypto.decrypt(nguon.getCredential());
        } catch (RuntimeException e) {
            securityEvents.externalCredentialDecryptFailed(nguon.getCode(), crypto.keyIdOf(nguon.getCredential()));
            log.error("Không giải mã được mã số nguồn {} — kiểm tra khoá AES đang hoạt động", nguon.getCode(), e);
            return null;
        }
    }

    /**
     * Xoá mềm một nguồn.
     *
     * <p>Chặn khi còn điểm đo trỏ vào: gỡ tự động thì 19 điểm đo mất nguồn cùng lúc và poller im
     * lặng — triệu chứng giống hệt "nguồn hỏng", nhưng nguyên nhân nằm ở một cú bấm từ nhiều ngày
     * trước.
     */
    @Transactional
    public void delete(UUID publicId) {
        ApiSource nguon = tim(publicId);
        int dangDung = stations.findByApiSourceIdAndDeletedAtIsNullOrderByCodeAsc(nguon.getId())
                .size();
        if (dangDung > 0) {
            throw new ConflictException(ErrorCode.HYD_1002, nguon.getCode());
        }
        nguon.markDeleted(Instant.now());
        sources.save(nguon);
        log.info("Xoá nguồn dữ liệu {}", nguon.getCode());
    }

    private ApiSource tim(UUID publicId) {
        return sources.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static String chuanHoaMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ma.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T batBuoc(T value) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return value;
    }

    /**
     * Địa chỉ nguồn — chỉ nhận {@code http}/{@code https}.
     *
     * <p>Danh sách cho phép, không phải danh sách cấm: một ô nhập tự do đi thẳng vào lời gọi HTTP
     * của server là chỗ để {@code file://} hay {@code gopher://} chui vào (SSRF). Nguồn hiện tại chỉ
     * có {@code http://} nên không thể ép {@code https}.
     */
    private static String diaChi(String baseUrl) {
        String url = (String) batBuoc(baseUrl);
        String rut = url.trim();
        if (!rut.startsWith("http://") && !rut.startsWith("https://")) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return rut;
    }

    /** {@code null} giữ nguyên là {@code null} — đó là "dùng tham số chung", không phải thiếu dữ liệu. */
    private static Integer trongKhoang(Integer value, int min, int max) {
        if (value == null) {
            return null;
        }
        if (value < min || value > max) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return value;
    }
}
