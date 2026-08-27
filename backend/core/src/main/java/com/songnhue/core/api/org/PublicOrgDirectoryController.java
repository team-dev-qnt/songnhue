package com.songnhue.core.api.org;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.org.PublicOrgDirectoryService;
import com.songnhue.core.common.security.PublicEndpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cơ cấu tổ chức và danh bạ công bố cho cổng TTĐT — {@code /api/v1/public/org-units/**}.
 *
 * <p>Đóng nợ <b>T11.30</b>: trước lượt này không có đường nào để cổng lấy {@code org_units}, nên
 * khối "Đơn vị trực thuộc" ở trang chủ báo *"chưa được đấu nối"* và hai trang CR-25 / CR-26 chưa
 * tồn tại.
 *
 * <h2>⛔ Không cần đăng nhập — và đó là quyết định nghiệp vụ, không phải sơ suất</h2>
 *
 * <p>§6 của "YÊU CẦU CHỈNH SỬA WEBSITE" 27/08/2026 xếp toàn bộ mục <i>Giới thiệu</i> vào nhóm
 * <b>Tất cả người dùng</b>. Hai hệ quả phải nhớ, cùng bộ với {@code PublicPortalController}:
 *
 * <ol>
 *   <li><b>Không có tầng phân quyền nào phía sau.</b> Phép lọc "công bố cái gì" nằm trong
 *       {@link PublicOrgDirectoryService} — đơn vị tắt và dòng danh bạ tắt bị loại ở truy vấn.
 *   <li><b>DTO liệt kê từng trường bằng tay.</b> Không trả entity, không trả {@code path} (chuỗi
 *       id chạy số), không trả {@code headUserId}. Thêm một cột vào {@code org_units} không tự
 *       động lộ nó ra đây.
 * </ol>
 *
 * <p>Vì sao nằm ở module {@code core} chứ không gộp vào {@code PublicPortalController} của
 * {@code content}: {@code org_units} là bảng của core, và quy tắc 6 cấm module này đọc repository
 * của module kia. Gộp lại thì {@code content} phải mượn một cổng SPI chỉ để chuyển tiếp nguyên
 * văn — thêm một tầng không mang quyết định nào.
 */
@RestController
@RequestMapping("/api/v1/public/org-units")
@Tag(name = "06-public · Cổng thông tin", description = "Nội dung công khai — không cần đăng nhập")
public class PublicOrgDirectoryController {

    private final PublicOrgDirectoryService directory;

    public PublicOrgDirectoryController(PublicOrgDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/chart")
    @Operation(summary = "Sơ đồ cây cơ cấu tổ chức Công ty (CR-24)")
    @PublicEndpoint(reason = "Trang Giới thiệu > Cơ cấu tổ chức — §6 xếp vào nhóm Tất cả người dùng")
    public List<PublicOrgDirectoryService.OrgChartNode> chart() {
        return directory.orgChart();
    }

    @GetMapping("/leaders")
    @Operation(summary = "Bảng Lãnh đạo Công ty — Họ tên / Chức danh / Điện thoại (CR-25)")
    @PublicEndpoint(reason = "Trang Giới thiệu > Lãnh đạo Công ty — §6 xếp vào nhóm Tất cả người dùng")
    public List<PublicOrgDirectoryService.LeaderRow> leaders() {
        return directory.companyLeaders();
    }

    @GetMapping("/subsidiaries")
    @Operation(summary = "Bảng Xí nghiệp trực thuộc — 6 cột (CR-26), cũng dùng cho khối trang chủ (CR-19)")
    @PublicEndpoint(reason = "Trang Giới thiệu > Xí nghiệp trực thuộc — §6 xếp vào nhóm Tất cả người dùng")
    public List<PublicOrgDirectoryService.SubsidiaryRow> subsidiaries() {
        return directory.subsidiaries();
    }
}
