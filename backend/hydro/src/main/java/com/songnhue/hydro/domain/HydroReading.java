package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.WorkflowAware;
import com.songnhue.core.common.persistence.WorkflowReasonAware;

/**
 * Một dòng {@code hydro_readings} <b>nhìn qua con mắt của quy trình duyệt</b> — T32.5.
 *
 * <h2>⚠⚠ Entity này ⛔ KHÔNG phải đường ghi của số đo</h2>
 *
 * <p>Số đo đi vào hệ thống bằng <b>một câu {@code INSERT} nhiều dòng</b> ở
 * {@code HydroTimeSeriesWriter} — 28 dòng, 2 phút/lần, suốt đời hệ thống. Cho lượt ấy đi qua JPA là
 * 28 lượt nạp entity + 28 lượt flush cho mỗi lần polling, đổi lấy đúng con số không.
 *
 * <p>⇒ Entity này chỉ tồn tại để {@code WorkflowEngine} có thứ để thao tác: quy tắc 4 của dự án nói
 * <b>đổi trạng thái chỉ qua Workflow engine</b>, và engine làm việc trên {@link WorkflowAware}. Nó
 * ⛔ không có hàm dựng công khai và ⛔ không được dùng để tạo dòng mới.
 *
 * <h2>⭐ {@code quality} vừa là cột chất lượng vừa là cột trạng thái quy trình</h2>
 *
 * <p>Một cột, một sự thật. Lý do đầy đủ ở {@link ReadingQuality}: dùng một cột
 * {@code deleted_at} riêng cho bước xoá mềm sẽ tạo ra <b>hai</b> câu trả lời cho <i>"dòng này còn
 * dùng được không"</i>, và mọi truy vấn báo cáo sẽ phải nhớ hai vế thay vì một (luật 14).
 *
 * <h2>⚠ Khoá chính của bảng là {@code (id, measured_at)}, ở đây chỉ khai {@code id}</h2>
 *
 * <p>{@code hydro_readings} phân mảnh theo tháng nên PostgreSQL <b>bắt buộc</b> khoá chính chứa cột
 * phân mảnh. Nhưng {@code id} sinh bởi {@code GENERATED ALWAYS AS IDENTITY} <b>trên bảng cha</b>,
 * nên nó vẫn là duy nhất toàn hệ — khai một mình vẫn đúng.
 *
 * <p>Cái giá là câu {@code WHERE id = ?} ⛔ không cắt được partition: nó dò <b>chỉ mục PK của từng
 * mảnh</b>. Với hạn lưu 5 năm thì đó là ~60 lượt dò chỉ mục cho một thao tác mà người dùng bấm tay
 * vài lần một ngày — rẻ hơn nhiều so với cái giá của việc bắt mọi API mang theo
 * {@code measured_at} chỉ để chiều một chi tiết lưu trữ. ⚠ Nếu ngày nào có màn hình duyệt <i>hàng
 * loạt</i> thì đo lại chỗ này, ⛔ đừng suy ra từ dòng chữ này rằng nó luôn rẻ.
 */
@Entity
@Table(name = "hydro_readings")
@Audited(module = "hyd", entityType = "Số đo thuỷ văn")
public class HydroReading implements WorkflowReasonAware {

    /** Khớp {@code workflow_definitions.entity_type} — sai chuỗi này là không tìm ra quy trình nào. */
    public static final String LOAI_QUY_TRINH = "HYDRO_READING";

    @Id
    @Column(name = "id", nullable = false, updatable = false, insertable = false)
    private Long id;

    @Column(name = "measured_at", nullable = false, updatable = false, insertable = false)
    private Instant measuredAt;

    @Column(name = "station_id", nullable = false, updatable = false, insertable = false)
    private Long stationId;

    @Column(name = "measurement_type_id", nullable = false, updatable = false, insertable = false)
    private Long measurementTypeId;

    @Column(name = "reading_value", nullable = false, updatable = false, insertable = false)
    private BigDecimal readingValue;

    /** ⭐ Cột trạng thái của quy trình {@code HYDRO_READING} — xem javadoc lớp. */
    @Column(name = "quality", nullable = false)
    private String quality;

    /** MÁY nói: vì sao bộ phân loại đánh dấu lúc ingest. ⛔ Không bị lượt duyệt ghi đè. */
    @Column(name = "quality_reason", length = 200, updatable = false, insertable = false)
    private String qualityReason;

    /** NGƯỜI nói: lý do người duyệt loại bỏ / duyệt lên hợp lệ — {@link #applyWorkflowReason}. */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    protected HydroReading() {}

    public Long getId() {
        return id;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getMeasurementTypeId() {
        return measurementTypeId;
    }

    public BigDecimal getReadingValue() {
        return readingValue;
    }

    public ReadingQuality getQuality() {
        return ReadingQuality.valueOf(quality);
    }

    public String getQualityReason() {
        return qualityReason;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    // ------------------------------------------------------------------ workflow

    @Override
    public String workflowEntityType() {
        return LOAI_QUY_TRINH;
    }

    @Override
    public String currentState() {
        return quality;
    }

    /**
     * ⛔ Chỉ {@code WorkflowEngine} gọi — quy tắc 4, canh bởi luật ArchUnit
     * {@code SilentFailureRuleTest#chi_workflow_engine_duoc_goi_applyState}.
     */
    @Override
    public void applyState(String newState) {
        this.quality = newState;
    }

    @Override
    public Long entityId() {
        return id;
    }

    /**
     * ⭐ Giữ lại lý do người duyệt nhập — bịt lỗ "reason bị kiểm rồi vứt" của engine dùng chung
     * (xem {@link WorkflowReasonAware}).
     *
     * <p>⚠ Ghi <b>vô điều kiện</b>, kể cả khi {@code reason} là {@code null}: bước {@code DUYET} cố
     * ý không đòi lý do, và một lượt duyệt phải xoá dấu vết của lượt trước nếu có. ⛔ Không giữ lại
     * một câu cũ đứng cạnh một trạng thái mới — đó là cách một dòng chữ bắt đầu nói dối.
     *
     * <p>⛔ ⛔ Cột {@code quality_reason} ⛔ <b>không</b> bị đụng tới: nó là lời của <i>máy</i> lúc
     * ingest, và câu hỏi <i>"hôm ấy nó bị bắt vì lý do gì"</i> phải còn trả lời được sau khi có
     * người duyệt. Đó cũng là lý do hai cột này tách nhau ngay từ migration.
     */
    @Override
    public void applyWorkflowReason(String action, String reason) {
        this.reviewNote = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
