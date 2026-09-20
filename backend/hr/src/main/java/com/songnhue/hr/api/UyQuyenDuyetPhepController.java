package com.songnhue.hr.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.UserDirectoryPort;
import com.songnhue.hr.application.UyQuyenDuyetPhepService;
import com.songnhue.hr.domain.UyQuyenDuyetPhep;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Uỷ quyền duyệt nghỉ phép — {@code /api/v1/hr/nghi-phep/uy-quyen} (chốt B3, T80.5).
 *
 * <h2>Vì sao một quyền RIÊNG {@code hr:leave:delegate}</h2>
 *
 * <p>{@code hr:leave:approve} trả lời <i>"tôi duyệt được"</i>; giao thẩm quyền ấy cho người khác là
 * một việc <b>khác</b> và nặng hơn — nó đặt một người ⛔ giữ chức vụ vào vai người duyệt trong một
 * khoảng thời gian. Dùng chung một mã quyền thì ⛔ có cách nào cho phép duyệt mà ⛔ cho phép giao.
 *
 * <p>⚠ Mã quyền chỉ là <b>cổng năng lực</b>. Ai được giao cho đơn vị NÀO là câu hỏi <b>quan hệ</b>,
 * và nó do {@code ThamQuyenDuyetPhep.giaoDuoc} trả lời ở tầng service — đúng cặp cơ chế mà T80.1 đã
 * dựng cho đường duyệt.
 */
@RestController
@RequestMapping("/api/v1/hr/nghi-phep/uy-quyen")
@Tag(name = "HR — Uỷ quyền duyệt nghỉ phép")
public class UyQuyenDuyetPhepController {

    private final UyQuyenDuyetPhepService uyQuyen;
    private final UserDirectoryPort taiKhoan;
    private final com.songnhue.core.spi.OrgUnitPort orgUnits;

    public UyQuyenDuyetPhepController(
            UyQuyenDuyetPhepService uyQuyen, UserDirectoryPort taiKhoan, com.songnhue.core.spi.OrgUnitPort orgUnits) {
        this.uyQuyen = uyQuyen;
        this.taiKhoan = taiKhoan;
        this.orgUnits = orgUnits;
    }

    @GetMapping
    @Operation(summary = "Uỷ quyền của một đơn vị — kèm bản đã thu hồi/hết hạn, vì lịch sử duyệt trỏ vào chúng")
    @RequirePermission("hr:leave:delegate")
    public List<UyQuyenView> cuaDonVi(@RequestParam UUID donVi) {
        return toViews(uyQuyen.cuaDonVi(donVi));
    }

    @PostMapping
    @Operation(summary = "Giao thẩm quyền duyệt của đơn vị cho một người, có thời hạn (chốt B3)")
    @RequirePermission("hr:leave:delegate")
    public UyQuyenView giao(@Valid @RequestBody GiaoRequest request) {
        return toView(uyQuyen.giao(
                request.orgUnitPublicId(),
                request.nguoiDuocUyQuyenPublicId(),
                request.tuNgay(),
                request.denNgay(),
                request.lyDo()));
    }

    @DeleteMapping("/{publicId}")
    @Operation(summary = "Thu hồi — KHÔNG xoá: một lượt duyệt cũ có thể đang trỏ vào bản ghi này")
    @RequirePermission("hr:leave:delegate")
    public UyQuyenView thuHoi(@PathVariable UUID publicId) {
        return toView(uyQuyen.thuHoi(publicId));
    }

    /**
     * ⚠ {@code denNgay >= tuNgay} ⛔ khai ở đây được bằng một annotation chuẩn — ràng buộc ở CSDL
     * ({@code ck_leave_deleg_dates}) là lưới cuối, và service trả mã lỗi đọc được.
     *
     * @param tuNgay được phép ở <b>quá khứ</b>: trưởng đơn vị đi công tác đột xuất hôm qua, hôm nay
     *     mới có người ngồi khai hộ — chuyện thật, và chặn nó ⛔ bảo vệ ai
     */
    public record GiaoRequest(
            @NotNull UUID orgUnitPublicId,
            @NotNull UUID nguoiDuocUyQuyenPublicId,
            @NotNull LocalDate tuNgay,
            @NotNull LocalDate denNgay,
            @Size(max = 500) String lyDo) {}

    /**
     * ⛔ Trả {@code publicId} chứ ⛔ tên người: {@code UserDirectoryPort} cố ý ⛔ đọc hồ sơ, và giao
     * diện vốn đã nạp danh sách tài khoản cho ô chọn (cùng cách {@code OTruongPho} của H24 làm).
     */
    public record UyQuyenView(
            UUID publicId,
            UUID orgUnitPublicId,
            UUID nguoiUyQuyenPublicId,
            UUID nguoiDuocUyQuyenPublicId,
            LocalDate tuNgay,
            LocalDate denNgay,
            String lyDo,
            boolean daThuHoi,
            boolean dangHieuLuc) {}

    /** ⚠ MỘT lượt tra cho cả danh sách — {@code publicIdsOf}/{@code findRefsByIds} sinh ra để chặn N+1. */
    private List<UyQuyenView> toViews(List<UyQuyenDuyetPhep> ban) {
        Map<Long, UUID> nguoi = taiKhoan.publicIdsOf(ban.stream()
                .flatMap(u -> java.util.stream.Stream.of(u.getNguoiUyQuyenId(), u.getNguoiDuocUyQuyenId()))
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<Long, com.songnhue.core.spi.OrgUnitRef> donVi = orgUnits.findRefsByIds(
                ban.stream().map(UyQuyenDuyetPhep::getOrgUnitId).toList());
        return ban.stream().map(u -> dung(u, nguoi, donVi)).toList();
    }

    private UyQuyenView toView(UyQuyenDuyetPhep u) {
        return dung(
                u,
                taiKhoan.publicIdsOf(List.of(u.getNguoiUyQuyenId(), u.getNguoiDuocUyQuyenId())),
                orgUnits.findRefsByIds(List.of(u.getOrgUnitId())));
    }

    private static UyQuyenView dung(
            UyQuyenDuyetPhep u, Map<Long, UUID> nguoi, Map<Long, com.songnhue.core.spi.OrgUnitRef> donVi) {
        com.songnhue.core.spi.OrgUnitRef ref = donVi.get(u.getOrgUnitId());
        return new UyQuyenView(
                u.getPublicId(),
                ref == null ? null : ref.publicId(),
                nguoi.get(u.getNguoiUyQuyenId()),
                nguoi.get(u.getNguoiDuocUyQuyenId()),
                u.getTuNgay(),
                u.getDenNgay(),
                u.getLyDo(),
                u.getThuHoiLuc() != null,
                u.coHieuLuc(LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN)));
    }
}
