package com.songnhue.hydro.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Nguồn dữ liệu quan trắc bên thứ ba — CN-03.2.
 *
 * <h2>⚠⚠ {@code credential} — bốn điều bắt buộc ({@code conventions.md} §4.7)</h2>
 *
 * <ol>
 *   <li>Lưu <b>bản mã AES-256-GCM</b>, khoá nằm ngoài CSDL. Bản sao lưu của CSDL đi ra khỏi phòng
 *       máy; khoá thì không.
 *   <li>⛔ <b>Không endpoint nào trả cột này, kể cả cho Admin.</b> API chỉ trả
 *       {@link #isCredentialDaCauHinh()} — một boolean.
 *   <li>⛔ <b>Không đưa vào payload của {@code jobs}</b>: payload lưu nguyên văn trong bảng, và bảng
 *       đó nằm trong mọi bản sao lưu.
 *   <li>⛔ <b>Không log.</b> {@code @Audited(excludeFields)} thay giá trị bằng {@code ***} trong
 *       nhật ký kiểm toán — nhật ký nhiều người xem hơn bảng gốc và lưu 5 năm.
 * </ol>
 *
 * <h2>Bốn tham số nhịp: {@code null} là một giá trị CÓ NGHĨA</h2>
 *
 * <p>{@code cron} / {@code frameMinutes} / {@code timeoutSeconds} / {@code maxRetry} để {@code null}
 * nghĩa là <b>dùng tham số chung ở bảng {@code settings}</b> (nhóm HYDRO). Đây không phải hai nguồn
 * sự thật cho cùng một tham số: thứ tự ưu tiên được giải ở <b>đúng một hàm</b>,
 * {@code ApiSourceService.thamSoHieuLuc(...)}, và endpoint chi tiết trả về <b>giá trị đã giải</b>
 * kèm cờ cho biết đang chịu tham số nào — {@code architecture-review.md} §10.29-a: canh giá trị ĐÃ
 * GIẢI, đừng canh giá trị MẶC ĐỊNH.
 *
 * <p>Có cột riêng vì nguồn thứ hai (API lượng mưa, G3-a) gần như chắc chắn có nhịp khác nguồn mực
 * nước; ép mọi nguồn theo một cron chung là hẹn trước một lần phải sửa lược đồ.
 */
@Entity
@Table(name = "api_sources")
@Audited(module = "hyd", entityType = "Nguồn dữ liệu", excludeFields = "credential")
public class ApiSource extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_type", nullable = false, length = 30)
    private AdapterType adapterType;

    /**
     * Địa chỉ gốc của nguồn.
     *
     * <p>⚠ Nguồn của Công ty chỉ có <b>http://</b>. Hệ quả bắt buộc: trình duyệt TUYỆT ĐỐI không gọi
     * thẳng địa chỉ này — trang chạy https sẽ bị chặn mixed-content, và mã số sẽ nằm nguyên trong
     * DevTools của bất kỳ ai mở trang. Mọi lượt gọi đi từ backend.
     */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /** ⛔ Bản mã AES-256-GCM. Không getter công khai trả giá trị thô ra ngoài tầng dịch vụ. */
    @Column(name = "credential")
    private String credential;

    @Column(name = "cron", length = 100)
    private String cron;

    @Column(name = "frame_minutes")
    private Integer frameMinutes;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "max_retry")
    private Integer maxRetry;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_failure_reason", length = 500)
    private String lastFailureReason;

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApiSourceStatus status = ApiSourceStatus.HOAT_DONG;

    @Column(name = "description", length = 500)
    private String description;

    protected ApiSource() {}

    public ApiSource(String code, String name, AdapterType adapterType, String baseUrl) {
        this.code = code;
        this.name = name;
        this.adapterType = adapterType;
        this.baseUrl = baseUrl;
    }

    /**
     * Đã có mã số dùng được chưa — <b>đây là thứ duy nhất API được phép trả về</b> về credential.
     *
     * <p>Trạng thái "chưa cấu hình" phải nhìn thấy được trên màn hình: nó là chỗ fail-fast của MOD-03
     * sau khi {@code HydroApiProperties} thôi chặn khởi động.
     */
    public boolean isCredentialDaCauHinh() {
        return credential != null && !credential.isBlank();
    }

    /**
     * Đặt bản mã, hoặc {@code null} để xoá cấu hình.
     *
     * <p>⛔ Tham số là <b>bản mã</b> ({@code CryptoService.encrypt()} đã chạy), không phải mã số thô.
     * Entity cố ý không cầm {@code CryptoService}: nếu nó tự mã hoá thì sẽ có một đường ghi nhận
     * chuỗi thô, và đường ấy sớm muộn được gọi với một giá trị chưa mã hoá mà không gì báo — cột vẫn
     * đầy, API vẫn nói "đã cấu hình".
     *
     * <p>Id khoá không có cột riêng: nó đã nằm trong tiền tố {@code <key_id>:} của chính bản mã, và
     * CSDL canh dạng ấy bằng {@code ck_api_sources_credential_format}.
     */
    public void datCredential(String banMa) {
        if (banMa != null && !banMa.contains(":")) {
            throw new IllegalArgumentException("Chuỗi đặt vào credential phải là bản mã <key_id>:<base64>");
        }
        this.credential = banMa;
    }

    /** ⛔ Chỉ tầng dịch vụ được gọi, và chỉ để giải mã ngay tại chỗ dùng. Không log giá trị trả về. */
    public String getCredential() {
        return credential;
    }

    /** Ghi nhận một lượt gọi nguồn thành công — xoá chuỗi hỏng liên tiếp. */
    public void ghiNhanThanhCong(Instant when) {
        this.lastSuccessAt = when;
        this.consecutiveFailures = 0;
        this.lastFailureReason = null;
    }

    /** Ghi nhận một lượt gọi hỏng. {@code lyDo} là thông điệp cho người vận hành, ⛔ không kèm mã số. */
    public void ghiNhanThatBai(Instant when, String lyDo) {
        this.lastFailureAt = when;
        this.lastFailureReason = lyDo;
        this.consecutiveFailures = (consecutiveFailures == null ? 0 : consecutiveFailures) + 1;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AdapterType getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(AdapterType adapterType) {
        this.adapterType = adapterType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Integer getFrameMinutes() {
        return frameMinutes;
    }

    public void setFrameMinutes(Integer frameMinutes) {
        this.frameMinutes = frameMinutes;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public ApiSourceStatus getStatus() {
        return status;
    }

    public void setStatus(ApiSourceStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
