package com.songnhue.core.api.org;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.org.OrgUnitNode;
import com.songnhue.core.application.org.OrgUnitService;
import com.songnhue.core.application.org.ThongTinDonVi;
import com.songnhue.core.common.security.AuthenticatedEndpoint;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API sơ đồ tổ chức — {@code /api/v1/org-units/**} (CN-05.2, CN-04.1).
 *
 * <p>Một cây dùng chung cho cả Xí nghiệp lẫn phòng ban (quy tắc 7). Quyền chia đôi rõ ràng: xem thì
 * gần như ai cũng cần (chọn đơn vị trong biểu mẫu), còn sửa cấu trúc là việc của quản trị —
 * {@code path} của cây chính là thứ quyết định phạm vi dữ liệu của mọi module.
 */
@RestController
@RequestMapping("/api/v1/org-units")
@Tag(name = "05-adm · Sơ đồ tổ chức", description = "Cây đơn vị dùng chung cho Xí nghiệp và phòng ban")
public class OrgUnitController {

    private final OrgUnitService service;

    public OrgUnitController(OrgUnitService service) {
        this.service = service;
    }

    /**
     * Cây đơn vị để <b>chọn trong biểu mẫu</b> — chỉ cần đăng nhập.
     *
     * <h2>Vì sao phải tách khỏi {@link #tree()}</h2>
     *
     * <p>Javadoc của lớp này vẫn ghi đúng chủ ý từ WS-6: *"xem thì gần như ai cũng cần (chọn đơn vị
     * trong biểu mẫu), còn sửa cấu trúc là việc của quản trị"*. Nhưng cả hai đường đọc đều đứng sau
     * {@code adm:org-unit:view} — một quyền chỉ nhóm quản trị có.
     *
     * <p>Hệ quả đo được: {@code TECHNICIAN} là vai trò <b>duy nhất</b> có
     * {@code ops:construction:create}, mà biểu mẫu tạo hồ sơ công trình bắt buộc chọn đơn vị quản lý
     * → ô chọn gọi {@code /tree} → <b>403</b>. Biểu mẫu tạo công trình chưa từng dùng được bởi đúng
     * vai trò sở hữu nó. Cùng hình dạng với {@code /ops/operation-status-codes/active}: một màn hình
     * nhập liệu bị buộc phải gọi đường quản trị, và cách "chữa" duy nhất còn lại là cấp quyền quản
     * trị cho người nhập liệu — tức là nới quyền để giao diện chạy được.
     *
     * <p><b>Lộ ra gì.</b> Tên và mã đơn vị, thứ đã in trên mọi văn bản nội bộ và hiện ở chân trang
     * cổng công khai. Không có số liệu, không có nhân sự, không sửa được gì. Đổi lại,
     * {@code adm:org-unit:view} giữ nguyên nghĩa "được xem màn hình quản trị sơ đồ tổ chức".
     */
    @GetMapping("/selectable")
    @Operation(summary = "Cây đơn vị cho ô chọn trong biểu mẫu — chỉ cần đăng nhập")
    @AuthenticatedEndpoint(reason = "Ô chọn đơn vị có mặt ở biểu mẫu của mọi module; chỉ trả mã và tên")
    public List<OrgUnitNode> selectable() {
        return service.tree();
    }

    @GetMapping("/tree")
    @Operation(summary = "Toàn bộ cây đơn vị, dạng lồng nhau")
    @RequirePermission("adm:org-unit:view")
    public List<OrgUnitNode> tree() {
        return service.tree();
    }

    // ⛔ BIA MỘ — `GET /org-units` (danh sách phẳng) và `GET /{id}/subtree` gỡ 14/09/2026 (T61.22):
    //    0 nơi gọi. Ô chọn đơn vị dùng `/selectable` (cây có phạm vi), sơ đồ tổ chức dùng `/tree`.

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết một đơn vị")
    @RequirePermission("adm:org-unit:view")
    public OrgUnitDtos.OrgUnitSummary get(@PathVariable UUID publicId) {
        return tomTat(service.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm đơn vị mới")
    @RequirePermission("adm:org-unit:manage")
    public OrgUnitDtos.OrgUnitSummary create(@Valid @RequestBody OrgUnitDtos.CreateRequest request) {
        return tomTat(service.create(
                request.code(),
                request.name(),
                request.unitType(),
                request.parentPublicId(),
                request.shortName(),
                new ThongTinDonVi(
                        request.address(),
                        request.phone(),
                        request.email(),
                        request.headUserPublicId(),
                        request.deputyUserPublicId())));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa thông tin đơn vị — không đổi vị trí trong cây")
    @RequirePermission("adm:org-unit:manage")
    public OrgUnitDtos.OrgUnitSummary update(
            @PathVariable UUID publicId, @Valid @RequestBody OrgUnitDtos.UpdateRequest request) {
        return tomTat(service.update(
                publicId,
                request.name(),
                request.shortName(),
                request.unitType(),
                new ThongTinDonVi(
                        request.address(),
                        request.phone(),
                        request.email(),
                        request.headUserPublicId(),
                        request.deputyUserPublicId())));
    }

    @PatchMapping("/{publicId}/parent")
    @Operation(summary = "Chuyển đơn vị (kèm toàn bộ cấp dưới) sang đơn vị cha khác")
    @RequirePermission("adm:org-unit:manage")
    public OrgUnitDtos.OrgUnitSummary move(
            @PathVariable UUID publicId, @Valid @RequestBody OrgUnitDtos.MoveRequest request) {
        return tomTat(service.move(publicId, request.newParentPublicId()));
    }

    @PatchMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sắp xếp lại thứ tự hiển thị giữa các đơn vị cùng cấp")
    @RequirePermission("adm:org-unit:manage")
    public void reorder(@Valid @RequestBody OrgUnitDtos.ReorderRequest request) {
        service.reorder(request.orderedPublicIds());
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Giải thể (xoá mềm) — từ chối kèm DANH SÁCH cụ thể những gì còn thuộc đơn vị")
    @RequirePermission("adm:org-unit:manage")
    public void delete(@PathVariable UUID publicId) {
        service.delete(publicId);
    }

    /**
     * Gắn {@code public_id} của trưởng/phó vào bản tóm tắt — H24.
     *
     * <p>⛔ Bốn endpoint trả về MỘT đơn vị đều đi qua đây: thiếu một chỗ thì màn hình đọc lại sau
     * lượt Lưu sẽ thấy hai ô rỗng và người dùng tưởng lượt lưu hỏng (quy tắc 27).
     */
    private OrgUnitDtos.OrgUnitSummary tomTat(com.songnhue.core.domain.org.OrgUnit unit) {
        OrgUnitService.LanhDaoPublicId lanhDao = service.lanhDaoCua(unit);
        return OrgUnitDtos.OrgUnitSummary.of(unit, lanhDao.truong(), lanhDao.pho());
    }
}
