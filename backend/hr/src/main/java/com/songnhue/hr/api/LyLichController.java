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
import com.songnhue.hr.application.LyLichForm;
import com.songnhue.hr.application.LyLichService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lý lịch & chuyên môn của một CBNV — {@code /api/v1/hr/employees/{id}/ly-lich} (CN-04.3).
 *
 * <h2>Đường dẫn LỒNG dưới hồ sơ, ⛔ không phải một tài nguyên gốc</h2>
 *
 * <p>{@code /hr/ly-lich/{id}} sẽ ⛔ không mang thông tin nào về hồ sơ chủ, nên phép kiểm phạm vi
 * phải tra ngược từ mục lên nhân viên — một bước dễ quên và ⛔ không để lại hình dạng nào cho bộ
 * canh nhìn thấy. Lồng dưới hồ sơ thì <b>⛔ không có cách nào gọi mà bỏ qua</b> {@code publicId}
 * của hồ sơ, và {@code LyLichService} bắt đầu mọi phương thức bằng {@code ScopeGuard}.
 *
 * <h2>Quyền: dùng lại {@code hr:employee:*}, ⛔ không thêm mã mới</h2>
 *
 * <p>Lý lịch là một phần của hồ sơ, ⛔ không phải một chức năng riêng — ai sửa được hồ sơ thì sửa
 * được lý lịch. Thêm {@code hr:qualification:*} là thêm bốn dòng vào ma trận phân quyền mà ⛔ không
 * ai gán khác đi, và mỗi mã quyền ⛔ không ai dùng là một dòng người quản trị phải đọc rồi bỏ qua.
 *
 * <p>⛔ Trường 🔒 ⛔ không đi qua đây: lý lịch/chuyên môn ⛔ không thuộc nhóm 🔒 của CN-04.2.
 */
@RestController
@RequestMapping("/api/v1/hr/employees/{hoSoId}/ly-lich")
@Tag(name = "04-hr · Lý lịch & chuyên môn", description = "Bằng cấp, chứng chỉ, ngoại ngữ, tin học, phần mềm")
public class LyLichController {

    private final LyLichService lyLich;

    public LyLichController(LyLichService lyLich) {
        this.lyLich = lyLich;
    }

    @GetMapping
    @Operation(summary = "Danh sách mục lý lịch & chuyên môn của một hồ sơ")
    @RequirePermission("hr:employee:view")
    public List<HoSoConDtos.LyLichView> danhSach(@PathVariable UUID hoSoId) {
        return lyLich.danhSach(hoSoId).stream().map(HoSoConDtos.LyLichView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm một mục lý lịch")
    @RequirePermission("hr:employee:update")
    public HoSoConDtos.LyLichView them(
            @PathVariable UUID hoSoId, @Valid @RequestBody HoSoConDtos.LyLichRequest request) {
        return HoSoConDtos.LyLichView.of(lyLich.them(hoSoId, toForm(request)));
    }

    @PutMapping("/{mucId}")
    @Operation(summary = "Sửa một mục lý lịch — THAY TOÀN PHẦN")
    @RequirePermission("hr:employee:update")
    public HoSoConDtos.LyLichView sua(
            @PathVariable UUID hoSoId,
            @PathVariable UUID mucId,
            @Valid @RequestBody HoSoConDtos.LyLichRequest request) {
        return HoSoConDtos.LyLichView.of(lyLich.sua(hoSoId, mucId, toForm(request)));
    }

    @DeleteMapping("/{mucId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá một mục lý lịch (xoá mềm)")
    @RequirePermission("hr:employee:update")
    public void xoa(@PathVariable UUID hoSoId, @PathVariable UUID mucId) {
        lyLich.xoa(hoSoId, mucId);
    }

    private static LyLichForm toForm(HoSoConDtos.LyLichRequest r) {
        return new LyLichForm(
                r.kind(),
                r.name(),
                r.grade(),
                r.major(),
                r.institution(),
                r.certificateNo(),
                r.issuedOn(),
                r.expiresOn(),
                r.note());
    }
}
