package com.songnhue.hr.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hr.application.SoDoToChucService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Sơ đồ tổ chức — {@code /api/v1/hr/so-do-to-chuc} (CN-04.1, SRS UC4.1).
 *
 * <h2>⭐ Đầu nhận ĐẦU TIÊN của {@code hr:org-chart:view}</h2>
 *
 * <p>Đo 14/09/2026: mã quyền ấy được seed cho <b>3</b> vai trò từ 13/08 và có <b>0 endpoint</b>
 * suốt 32 ngày — một công tắc ⛔ không bật gì (luật 15). {@code RbacMatrixTest} giữ nó trong danh
 * sách miễn kiểm kèm ghi chú *"Phase 3 (CN-04.1)"*; dòng ấy nay hết lý do tồn tại.
 *
 * <h2>⛔ Vì sao endpoint ở module {@code hr} chứ ⛔ không ở {@code core}</h2>
 *
 * <p>Dữ liệu của sơ đồ chia hai nửa: cây + người đứng đầu thuộc {@code core}, <b>quân số</b> thuộc
 * {@code hr}. Cổng quyền thì là một mã <b>của MOD-04</b>. Đặt endpoint ở {@code core} sẽ bắt
 * {@code core} biết chính sách phân quyền của HRM — ⇒ đặt ở {@code hr}, và nửa của {@code core}
 * lấy qua {@code OrgUnitPort.cayPhang()} (quy tắc 6).
 *
 * <h2>⛔ ⛔ KHÔNG có động từ GHI ở đây</h2>
 *
 * <p>Sửa cây tổ chức là việc của MOD-05 ({@code /api/v1/org-units}, {@code adm:org-unit:manage}) —
 * kể cả thao tác kéo–thả trên sơ đồ cũng gọi sang đó. Một đường ghi ở đây sẽ cho <b>3</b> vai trò
 * chỉ có quyền <i>xem</i> sơ đồ sửa được cây tổ chức của cả Công ty.
 */
@RestController
@RequestMapping("/api/v1/hr/so-do-to-chuc")
@Tag(name = "04-hr · Sơ đồ tổ chức", description = "Cây đơn vị + người đứng đầu + quân số — CN-04.1")
public class SoDoToChucController {

    private final SoDoToChucService soDo;

    public SoDoToChucController(SoDoToChucService soDo) {
        this.soDo = soDo;
    }

    @GetMapping
    @Operation(summary = "Sơ đồ tổ chức — mỗi nút kèm quân số TRỰC TIẾP và CẢ NHÁNH")
    @RequirePermission("hr:org-chart:view")
    public SoDoToChucService.SoDo soDo() {
        return soDo.dung();
    }
}
