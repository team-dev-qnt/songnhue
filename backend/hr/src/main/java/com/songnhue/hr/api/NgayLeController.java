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

import com.songnhue.core.common.security.AuthenticatedEndpoint;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hr.application.NgayLeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục ngày nghỉ lễ — {@code /api/v1/hr/ngay-le} (CN-04.9).
 *
 * <h2>⚠ Đường ĐỌC mở cho mọi người đăng nhập, đường GHI đòi {@code hr:contract:manage}</h2>
 *
 * <p>Mọi CBNV đều cần <b>đọc</b> lịch lễ: nó quyết định số ngày công của đơn họ sắp nộp, và một ô
 * *"còn 8 ngày công"* mà ⛔ không xem được vì sao là một con số ⛔ không ai tin. ⇒ Đường đọc chỉ
 * cần đăng nhập.
 *
 * <p>Đường ghi thì khác hẳn: thêm nhầm một ngày lễ là <b>cấp thêm phép</b> cho toàn Công ty, bớt
 * nhầm một ngày là <b>trừ oan</b> — nên nó đòi {@code hr:contract:manage}, quyền mà seed chỉ cấp
 * cho SUPER_ADMIN và ADMIN_HR. ⭐ Đây cũng là <b>đầu nhận đầu tiên</b> của mã quyền ấy: đo 14/09,
 * nó có <b>0 endpoint</b> kể từ 13/08.
 */
@RestController
@RequestMapping("/api/v1/hr/ngay-le")
@Tag(name = "04-hr · Ngày nghỉ lễ", description = "Danh mục do Công ty vận hành — GIAO ĐI RỖNG, cấm seed lễ bịa")
public class NgayLeController {

    private final NgayLeService ngayLe;

    public NgayLeController(NgayLeService ngayLe) {
        this.ngayLe = ngayLe;
    }

    @GetMapping
    @AuthenticatedEndpoint(reason = "Mọi CBNV cần xem lịch lễ để hiểu số ngày công của đơn mình sắp nộp")
    @Operation(summary = "Danh sách ngày nghỉ lễ — RỖNG khi Công ty chưa khai")
    public List<NghiPhepDtos.NgayLeView> danhSach() {
        return ngayLe.danhSach().stream().map(NghiPhepDtos.NgayLeView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm một ngày nghỉ lễ — trùng ngày trả HR-2008")
    @RequirePermission("hr:contract:manage")
    public NghiPhepDtos.NgayLeView tao(@Valid @RequestBody NghiPhepDtos.NgayLeRequest request) {
        return NghiPhepDtos.NgayLeView.of(ngayLe.tao(request.holidayDate(), request.name(), request.note()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa một ngày nghỉ lễ")
    @RequirePermission("hr:contract:manage")
    public NghiPhepDtos.NgayLeView sua(
            @PathVariable UUID publicId, @Valid @RequestBody NghiPhepDtos.NgayLeRequest request) {
        return NghiPhepDtos.NgayLeView.of(ngayLe.sua(publicId, request.holidayDate(), request.name(), request.note()));
    }

    /**
     * Xoá mềm.
     *
     * <p>⚠ Đơn nghỉ <b>đã nộp</b> giữ nguyên số ngày công đã đếm — xem
     * {@code LeaveRequest.workingDays}. Gỡ một ngày lễ ⛔ không làm đơn cũ đổi số ngày, và đó là
     * điều ĐÚNG: người lao động đã nghỉ đúng ngần ấy ngày.
     */
    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm — KHÔNG đổi số ngày của đơn đã nộp")
    @RequirePermission("hr:contract:manage")
    public void xoa(@PathVariable UUID publicId) {
        ngayLe.xoa(publicId);
    }
}
