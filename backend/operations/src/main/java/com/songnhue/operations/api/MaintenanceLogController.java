package com.songnhue.operations.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.UserDirectoryPort;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.MaintenanceAttachmentService;
import com.songnhue.operations.application.MaintenanceFilter;
import com.songnhue.operations.application.MaintenanceLogService;
import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.MaintenanceLog;
import com.songnhue.operations.domain.MaintenanceType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lịch sử sửa chữa / bảo trì / khắc phục sự cố — {@code /api/v1/ops/maintenance-logs/**} (CN-02.2).
 *
 * <h2>⭐ Hai đường tạo, vì ma trận §6 tách hai quyền</h2>
 *
 * <ul>
 *   <li>{@code POST /maintenance-logs} — {@code ops:maintenance:create}: Admin, Quản lý XN, Kỹ thuật
 *   <li>{@code POST /maintenance-logs/incidents} — {@code ops:maintenance:report-incident}: thêm cả
 *       Cán bộ vận hành. Đường này <b>ép</b> loại "Khắc phục sự cố"; nhận {@code workType} tự do
 *       nghĩa là quyền hẹp mở được cả đường ghi bảo trì.
 * </ul>
 *
 * <p>Gộp làm một endpoint thì không diễn đạt được điều đó bằng annotation, và luật sẽ phải sống
 * trong một câu {@code if} — nơi mà "quên một trường hợp" là chuyện im lặng.
 *
 * <h2>⚠ Sửa và xoá KHÔNG dùng chung quyền</h2>
 *
 * {@code PUT} đòi {@code ops:maintenance:create} ở tầng annotation vì luật thật phụ thuộc dữ liệu
 * (ai tạo, tạo lúc nào — cửa sổ tự sửa T18.9) mà annotation không đọc được dữ liệu; chốt chặn nằm ở
 * service. {@code DELETE} thì không có cửa sổ nào cả — đòi thẳng {@code ops:maintenance:delete}.
 */
@RestController
@RequestMapping("/api/v1/ops/maintenance-logs")
@Tag(name = "02-ops · Lịch sử sửa chữa", description = "Sửa chữa, bảo trì và khắc phục sự cố — CN-02.2")
public class MaintenanceLogController {

    private final MaintenanceLogService logs;
    private final MaintenanceAttachmentService attachments;
    private final ConstructionService constructions;
    private final OrgUnitPort orgUnits;
    private final UserDirectoryPort users;

    public MaintenanceLogController(
            MaintenanceLogService logs,
            MaintenanceAttachmentService attachments,
            ConstructionService constructions,
            OrgUnitPort orgUnits,
            UserDirectoryPort users) {
        this.logs = logs;
        this.attachments = attachments;
        this.constructions = constructions;
        this.orgUnits = orgUnits;
        this.users = users;
    }

    // === Đọc =================================================================

    // CHECKSTYLE.OFF: ParameterNumber - mỗi tham số là một ô lọc của CN-02.2; gói thành object
    // binding thì Swagger mất mô tả từng ô và FE mất gợi ý kiểu.
    @GetMapping
    @Operation(summary = "Timeline lịch sử sửa chữa — lọc theo công trình, loại, mức độ, trạng thái, kỳ")
    @RequirePermission("ops:maintenance:view")
    public Page<MaintenanceDtos.MaintenanceRow> search(
            @RequestParam(required = false) UUID constructionId,
            @RequestParam(required = false) MaintenanceType workType,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID performerOrgUnitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        Pageable pageable = PageUtils.toPageable(page, size, sort, MaintenanceLogService.allowedSortFields());
        MaintenanceFilter filter =
                new MaintenanceFilter(constructionId, workType, severity, status, performerOrgUnitId, from, to, q);
        return trang(logs.search(filter, pageable));
    }
    // CHECKSTYLE.ON: ParameterNumber

    @GetMapping("/{publicId}")
    @Operation(summary = "Một bản ghi + danh sách nút được phép bấm")
    @RequirePermission("ops:maintenance:view")
    public MaintenanceDtos.MaintenanceDetail detail(@PathVariable UUID publicId) {
        MaintenanceLog banGhi = logs.get(publicId);
        return new MaintenanceDtos.MaintenanceDetail(
                toRow(banGhi, briefs(List.of(banGhi))), logs.allowedActions(publicId));
    }

    /**
     * Sự cố chưa xử lý — T18.8.
     *
     * <p>Sắp theo mức độ rồi tới ngày ghi nhận: đây là danh sách <b>việc phải làm</b>, không phải một
     * kho lưu trữ. Nguồn của ô KPI cùng tên trên dashboard điều hành.
     */
    @GetMapping("/open-incidents")
    @Operation(summary = "Danh sách sự cố chưa xử lý — nặng nhất và cũ nhất lên trước")
    @RequirePermission("ops:maintenance:view")
    public List<MaintenanceDtos.MaintenanceRow> openIncidents(
            @RequestParam(required = false, defaultValue = "20") int limit) {
        List<MaintenanceLog> items = logs.openIncidents(limit);
        Map<Long, ConstructionService.ConstructionBrief> danhMuc = briefs(items);
        return items.stream().map(m -> toRow(m, danhMuc)).toList();
    }

    /**
     * Tổng chi phí theo kỳ — <b>tính ở BE</b> (quy tắc 3, T18.7).
     *
     * <p>Endpoint riêng chứ không nhét vào trang danh sách: tổng của một <i>kỳ</i> khác tổng của một
     * <i>trang</i>, và để FE cộng những dòng đang hiển thị là cách chắc chắn nhất để hai màn hình
     * đưa ra hai con số cùng tên gọi.
     */
    @GetMapping("/cost-summary")
    @Operation(summary = "Tổng chi phí + số công việc trong kỳ (null = chưa ai điền chi phí, khác 0 đồng)")
    @RequirePermission("ops:maintenance:view")
    public MaintenanceLogService.CostSummary costSummary(
            @RequestParam(required = false) UUID constructionId,
            @RequestParam(required = false) MaintenanceType workType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return logs.costSummary(new MaintenanceFilter(constructionId, workType, null, null, null, from, to, null));
    }

    /** Ô chọn "Trạng thái ban đầu" của biểu mẫu — điểm nghiệp vụ 15, FE không tự liệt kê. */
    @GetMapping("/initial-actions")
    @Operation(summary = "Các đường vào đời hợp lệ của một loại công việc, đã lọc theo quyền")
    @RequirePermission("ops:maintenance:view")
    public List<AllowedAction> initialActions(@RequestParam MaintenanceType workType) {
        return logs.initialActions(workType);
    }

    // === Ghi =================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ghi nhận công việc sửa chữa / bảo trì")
    @RequirePermission("ops:maintenance:create")
    public MaintenanceDtos.MaintenanceRow create(@Valid @RequestBody MaintenanceDtos.SaveRequest request) {
        MaintenanceLog saved = logs.create(request.toForm());
        return toRow(saved, briefs(List.of(saved)));
    }

    /**
     * Ghi nhận một sự cố — quyền rộng hơn, phạm vi hẹp hơn.
     *
     * <p>Ma trận §6 cho Cán bộ vận hành ghi nhận sự cố nhưng không cho ghi bảo trì. Đường riêng là
     * cách duy nhất diễn đạt điều đó bằng annotation thay vì bằng một câu {@code if}.
     */
    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ghi nhận sự cố — ép loại Khắc phục sự cố, bắt buộc có mức độ")
    @RequirePermission("ops:maintenance:report-incident")
    public MaintenanceDtos.MaintenanceRow reportIncident(@Valid @RequestBody MaintenanceDtos.SaveRequest request) {
        MaintenanceLog saved = logs.create(request.toIncidentForm());
        return toRow(saved, briefs(List.of(saved)));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa bản ghi đã lưu — quyền thật kiểm ở service (T18.9)")
    @RequirePermission("ops:maintenance:create")
    public MaintenanceDtos.MaintenanceRow update(
            @PathVariable UUID publicId, @Valid @RequestBody MaintenanceDtos.SaveRequest request) {
        MaintenanceLog saved = logs.update(publicId, request.toForm());
        return toRow(saved, briefs(List.of(saved)));
    }

    /**
     * Chuyển trạng thái xử lý — đường duy nhất (quy tắc 4).
     *
     * <p>Quyền của <i>từng bước chuyển</i> nằm ở {@code workflow_transitions.required_permission},
     * không ở đây: đóng một bản ghi sự cố đòi {@code close-incident}, đóng một công việc bảo trì đòi
     * {@code update}. Annotation ở đây chỉ là cửa vào chung.
     */
    @PostMapping("/{publicId}/actions")
    @Operation(summary = "Bấm một nút của quy trình — START / COMPLETE / RESOLVE / REOPEN")
    @RequirePermission("ops:maintenance:view")
    public MaintenanceDtos.MaintenanceDetail execute(
            @PathVariable UUID publicId, @Valid @RequestBody MaintenanceDtos.ActionRequest request) {
        MaintenanceLog saved = logs.execute(publicId, request.action(), request.completedOn(), request.note());
        return new MaintenanceDtos.MaintenanceDetail(
                toRow(saved, briefs(List.of(saved))), logs.allowedActions(publicId));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm — tính lại trạng thái công trình ngay sau đó")
    @RequirePermission("ops:maintenance:delete")
    public void delete(@PathVariable UUID publicId) {
        logs.delete(publicId);
    }

    // === Tệp đính kèm (T18.6) ================================================

    @GetMapping("/{publicId}/attachments")
    @Operation(summary = "Biên bản nghiệm thu, ảnh trước / sau")
    @RequirePermission("ops:maintenance:view")
    public List<MaintenanceDtos.AttachmentView> listAttachments(@PathVariable UUID publicId) {
        return attachments.list(publicId).stream()
                .map(MaintenanceLogController::toView)
                .toList();
    }

    @PostMapping(path = "/{publicId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải biên bản / ảnh lên; cùng nhãn thì thành phiên bản kế tiếp")
    @RequirePermission("ops:document:upload")
    public MaintenanceDtos.AttachmentView uploadAttachment(
            @PathVariable UUID publicId, @RequestPart("file") MultipartFile file, @RequestParam String docType) {
        return toView(attachments.upload(publicId, docType, file.getOriginalFilename(), noiDung(file)));
    }

    @GetMapping("/{publicId}/attachments/{attachmentId}/download-url")
    @Operation(summary = "Đường dẫn tải có hạn")
    @RequirePermission("ops:maintenance:view")
    public DownloadUrl attachmentUrl(@PathVariable UUID publicId, @PathVariable UUID attachmentId) {
        return new DownloadUrl(attachments.downloadUrl(publicId, attachmentId));
    }

    public record DownloadUrl(String url) {}

    @DeleteMapping("/{publicId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm một tệp đính kèm")
    @RequirePermission("ops:document:delete")
    public void deleteAttachment(@PathVariable UUID publicId, @PathVariable UUID attachmentId) {
        attachments.delete(publicId, attachmentId);
    }

    // === Nội bộ ==============================================================

    /**
     * Nạp mã + tên công trình cho cả trang trong <b>một</b> lượt truy vấn.
     *
     * <p>Tra từng dòng thì một trang 50 bản ghi thành 50 lượt — kiểu N+1 không gây lỗi nào, chỉ làm
     * màn hình chậm dần theo dữ liệu, tức là chậm dần theo thời gian sử dụng.
     */
    private Map<Long, ConstructionService.ConstructionBrief> briefs(List<MaintenanceLog> items) {
        return constructions.briefsByIds(
                items.stream().map(MaintenanceLog::getConstructionId).distinct().toList());
    }

    private Page<MaintenanceDtos.MaintenanceRow> trang(Page<MaintenanceLog> page) {
        Map<Long, ConstructionService.ConstructionBrief> danhMuc = briefs(page.getContent());
        return page.map(m -> toRow(m, danhMuc));
    }

    private MaintenanceDtos.MaintenanceRow toRow(
            MaintenanceLog m, Map<Long, ConstructionService.ConstructionBrief> danhMuc) {
        ConstructionService.ConstructionBrief ct = danhMuc.get(m.getConstructionId());
        return MaintenanceDtos.MaintenanceRow.of(
                m,
                ct == null ? null : ct.code(),
                ct == null ? null : ct.name(),
                ct == null ? null : ct.publicId(),
                donViThucHien(m),
                users.publicIdOf(m.getAssigneeUserId()).orElse(null));
    }

    /** Gộp hai cột thành một tên để hiển thị — xem chú thích ở {@code MaintenanceRow.performer}. */
    private String donViThucHien(MaintenanceLog m) {
        if (m.getPerformerOrgUnitId() == null) {
            return m.getPerformerName();
        }
        return orgUnits.findRefById(m.getPerformerOrgUnitId())
                .map(ref -> ref.name())
                .orElse(null);
    }

    private static MaintenanceDtos.AttachmentView toView(AttachmentRef ref) {
        return new MaintenanceDtos.AttachmentView(
                ref.publicId(),
                ref.originalName(),
                ref.purpose(),
                ref.contentType(),
                ref.sizeBytes(),
                ref.fileVersion(),
                ref.downloadable(),
                ref.createdAt());
    }

    private static byte[] noiDung(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.SYS_0003, e.getMessage());
        }
    }
}
