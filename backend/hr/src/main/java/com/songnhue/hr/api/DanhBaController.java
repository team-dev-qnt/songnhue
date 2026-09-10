package com.songnhue.hr.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hr.application.DanhBaLoc;
import com.songnhue.hr.application.DanhBaService;
import com.songnhue.hr.domain.Gender;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh bạ nội bộ — {@code /api/v1/hr/danh-ba} (CN-04.6, SRS M4.11).
 *
 * <h2>⚠ Quyền ở đây là {@code hr:directory:view}, ⛔ KHÔNG phải {@code hr:employee:view}</h2>
 *
 * <p>Đo trên ma trận seed: {@code hr:directory:view} cấp cho <b>11/12</b> vai trò (gồm VIEWER,
 * CLERK, XN_OPERATOR — những người ⛔ không có một quyền {@code hr:employee:*} nào), còn
 * {@code hr:employee:view} chỉ 3 vai trò. Gộp hai màn hình về một quyền là hỏng theo <b>cả hai</b>
 * chiều: gác chặt thì cả Công ty mất danh bạ, gác lỏng thì hồ sơ nhân sự lộ cho mọi người.
 *
 * <p>⛔ Và đây là mã quyền có <b>0 endpoint</b> suốt từ 13/08/2026 — {@code RbacMatrixTest:167}
 * miễn trừ nó với ghi chú *"Danh bạ — Phase 3 (CN-04.6)"*. Lượt này là đầu nhận của nó.
 *
 * <h2>Ba tham số lọc đều NHẬN NHIỀU GIÁ TRỊ</h2>
 *
 * <p>{@code ?donVi=<uuid>&donVi=<uuid>} — Spring gộp thành {@code List}. Danh sách <b>rỗng</b> là
 * <i>⛔ không lọc</i>, ⛔ không phải <i>⛔ không khớp gì</i>: bỏ hết dấu tick phải ra <b>tất cả</b>,
 * ⛔ không phải một trang trắng.
 */
@RestController
@RequestMapping("/api/v1/hr/danh-ba")
@Tag(name = "04-hr · Danh bạ nội bộ", description = "Tra cứu liên hệ công vụ — mọi cán bộ đều xem được")
public class DanhBaController {

    private final DanhBaService danhBa;

    public DanhBaController(DanhBaService danhBa) {
        this.danhBa = danhBa;
    }

    @GetMapping
    @Operation(summary = "Tra cứu danh bạ — tìm bỏ dấu, lọc đa chọn đơn vị/chức vụ/giới tính")
    @RequirePermission("hr:directory:view")
    public HoSoConDtos.DanhBaTrangView tim(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> donVi,
            @RequestParam(required = false) List<UUID> chucVu,
            @RequestParam(required = false) List<Gender> gioiTinh,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        DanhBaService.Trang trang = danhBa.tim(
                new DanhBaLoc(q, donVi, chucVu, gioiTinh), page == null ? 0 : page, size == null ? 24 : size);

        return new HoSoConDtos.DanhBaTrangView(
                trang.muc().stream().map(HoSoConDtos.DanhBaView::of).toList(), trang.tong(), trang.trang(), trang.co());
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết một người — kèm vị trí trên sơ đồ và đồng nghiệp cùng đơn vị")
    @RequirePermission("hr:directory:view")
    public HoSoConDtos.DanhBaChiTietView chiTiet(@PathVariable UUID publicId) {
        DanhBaService.ChiTiet ct = danhBa.chiTiet(publicId);
        return new HoSoConDtos.DanhBaChiTietView(
                HoSoConDtos.DanhBaView.of(ct.muc()),
                ct.duongDanDonVi(),
                ct.dongNghiep().stream().map(HoSoConDtos.DanhBaView::of).toList());
    }
}
