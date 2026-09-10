package com.songnhue.hr.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hr.application.PositionForm;
import com.songnhue.hr.application.PositionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục chức vụ — {@code /api/v1/hr/positions/**} (CN-04.2).
 *
 * <h2>Vì sao đường ĐỌC gác bằng {@code hr:employee:view}, ⛔ không bằng một quyền riêng</h2>
 *
 * <p>Ô "Chức vụ" của biểu mẫu hồ sơ nạp danh sách bằng đúng endpoint này. Nếu nó đòi một quyền mà
 * người dựng hồ sơ ⛔ không có thì danh sách vĩnh viễn rỗng và ⛔ <b>không tạo nổi một hồ sơ đầy
 * đủ nào</b> — đúng sự cố đã đo được ở WS-28 ({@code ApiSourceController:60-77}): quyền cấp đúng,
 * màn hình có thật, endpoint có thật, chỉ là đường nạp dữ liệu cho một ô lại nằm sau một quyền khác.
 *
 * <p>⛔ Chỉ đường ĐỌC được nới. Thêm/sửa/xoá vẫn đòi {@code hr:employee:create|update|delete} — chỉ
 * ADMIN_HR / ADMIN / SUPER_ADMIN có.
 */
/*
 * ⛔⛔ CỐ Ý ⛔ KHÔNG có `GET /{publicId}`.
 *
 * Bản đầu của lớp này có nó — và nó có **0 nơi gọi**: `PositionsPage` sửa từ dòng đã nằm trên
 * bảng, vì `PositionView` của danh sách đã mang đủ trường. Một endpoint ⛔ không ai gọi là một
 * lỗi, ⛔ không phải một việc để dành (quy tắc 15): nó vẫn có bề mặt tấn công, vẫn phải bảo trì,
 * vẫn xuất hiện trong tài liệu API như một lời hứa. T49.5 là một nạn nhân cùng hình dạng —
 * `getWaterLevels()` sống sót qua chính lượt thay thế của nó, backend vẫn phục vụ, bài kiểm HTTP
 * vẫn xanh, và HAI javadoc trỏ vào nó suốt hai tuần.
 *
 * ⇒ Ngày nào màn hình cần một đường đọc chi tiết thì thêm lại **cùng lượt** với nơi gọi.
 */
@RestController
@RequestMapping("/api/v1/hr/positions")
@Tag(name = "04-hr · Chức vụ", description = "Danh mục chức vụ chuẩn hoá — dùng chung toàn Công ty")
public class PositionController {

    private final PositionService positions;

    public PositionController(PositionService positions) {
        this.positions = positions;
    }

    @GetMapping
    @Operation(summary = "Danh sách chức vụ")
    @RequirePermission("hr:employee:view")
    public List<HrDtos.PositionView> list() {
        return positions.list().stream().map(HrDtos.PositionView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm chức vụ")
    @RequirePermission("hr:employee:create")
    public HrDtos.PositionView create(@Valid @RequestBody HrDtos.PositionRequest request) {
        return HrDtos.PositionView.of(positions.create(toForm(request)));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa chức vụ")
    @RequirePermission("hr:employee:update")
    public HrDtos.PositionView update(@PathVariable UUID publicId, @Valid @RequestBody HrDtos.PositionRequest request) {
        return HrDtos.PositionView.of(positions.update(publicId, toForm(request)));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm chức vụ — từ chối khi còn hồ sơ đang giữ (HR-2002)")
    @RequirePermission("hr:employee:delete")
    public void delete(@PathVariable UUID publicId) {
        positions.delete(publicId);
    }

    private static PositionForm toForm(HrDtos.PositionRequest r) {
        return new PositionForm(r.code(), r.name(), r.positionGroup(), r.description(), r.sortOrder(), r.active());
    }
}
