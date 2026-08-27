package com.songnhue.operations.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.PublicEndpoint;
import com.songnhue.operations.application.PublicConstructionCatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục công trình công bố cho cổng TTĐT — {@code /api/v1/public/constructions}.
 *
 * <p>CR-27 nói rõ điều bản dev làm sai: danh mục công trình <i>"đang xử lý như một bài viết"</i>.
 * Một bài viết thì người xem không lọc được theo Xí nghiệp, không mở được tệp Quyết định, và mỗi
 * lần thêm trạm bơm là một lượt sửa nội dung tay. Endpoint này đưa nó về đúng bản chất: dữ liệu.
 *
 * <h2>⛔ Không cần đăng nhập</h2>
 *
 * <p>§6 xếp "Quản lý, vận hành · Danh mục công trình" vào nhóm <b>Tất cả người dùng</b>. Riêng tệp
 * KMZ bản đồ hệ thống mới đòi đăng nhập (CR-29) — và nó <b>không</b> đi qua đây.
 *
 * <p>Không có tầng phân quyền nào phía sau, nên phép lọc "công bố cái gì" nằm trong
 * {@link PublicConstructionCatalogService}: công trình đã thanh lý và đã xoá mềm bị loại ngay ở
 * truy vấn, và DTO liệt kê đúng bảy cột của §5.1 chứ không mở nguyên hồ sơ công trình ra ngoài.
 */
@RestController
@RequestMapping("/api/v1/public/constructions")
@Tag(name = "06-public · Cổng thông tin", description = "Nội dung công khai — không cần đăng nhập")
public class PublicConstructionController {

    private final PublicConstructionCatalogService catalog;

    public PublicConstructionController(PublicConstructionCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Danh mục công trình gom theo Xí nghiệp — bảng 7 cột của CR-28")
    @PublicEndpoint(reason = "Trang Quản lý, vận hành > Danh mục công trình — §6 nhóm Tất cả người dùng")
    public List<PublicConstructionCatalogService.UnitCatalog> catalog() {
        return catalog.catalogByUnit();
    }
}
