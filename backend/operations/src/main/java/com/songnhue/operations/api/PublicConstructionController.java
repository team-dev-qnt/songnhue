package com.songnhue.operations.api;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.PublicEndpoint;
import com.songnhue.core.common.util.HttpHeaderText;
import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.operations.application.PublicConstructionCatalogService;
import com.songnhue.operations.application.PublicOperationStatusService;

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
    private final PublicOperationStatusService operationStatuses;

    public PublicConstructionController(
            PublicConstructionCatalogService catalog, PublicOperationStatusService operationStatuses) {
        this.catalog = catalog;
        this.operationStatuses = operationStatuses;
    }

    /** Tệp tài liệu công trình đổi rất hiếm và địa chỉ gắn với {@code publicId}, nên đệm dài. */
    private static final long CACHE_TEP_GIAY = 86_400L;

    @GetMapping
    @Operation(summary = "Danh mục công trình gom theo Xí nghiệp — bảng 7 cột của CR-28")
    @PublicEndpoint(reason = "Trang Quản lý, vận hành > Danh mục công trình — §6 nhóm Tất cả người dùng")
    public List<PublicConstructionCatalogService.UnitCatalog> catalog() {
        return catalog.catalogByUnit();
    }

    /**
     * Tình hình vận hành hiện hành của các cống — khối 6 cột trên trang chủ và trang
     * "Vận hành công trình" (CN-02.11, chốt G4).
     *
     * <p>⚠ Đây là dữ liệu <b>nhập tay</b> của trực ban. Nó <b>không</b> trả lời hết §5.3 của văn
     * bản nghiệm thu — bốn trường theo ngày của trạm bơm cần một API nguồn chưa tồn tại (OI-02).
     *
     * <p>⛔ Trường {@code note} và người cập nhật không có mặt trong DTO: phạm vi công bố, không
     * phải cột thiếu. Xem {@link PublicOperationStatusService}.
     */
    @GetMapping("/operation-statuses")
    @Operation(summary = "Tình hình vận hành hiện hành của từng công trình — 6 cột của CN-02.11")
    @PublicEndpoint(reason = "Khối Vận hành công trình trên cổng — §5.3 văn bản nghiệm thu 27/08")
    public List<PublicOperationStatusService.OperationStatusRow> operationStatuses() {
        return operationStatuses.hienHanh();
    }

    /**
     * Tải một tệp tài liệu <b>đã công bố</b> của công trình — hai cột "Quy trình vận hành" và
     * "Phương án bảo vệ" của bảng §5.1.
     *
     * <h2>⛔ Vì sao không dùng {@code /api/v1/public/files/{id}}</h2>
     *
     * <p>Đường tệp của cổng chỉ phục vụ bốn loại chủ sở hữu, và {@code CONSTRUCTION} <b>cố ý</b>
     * không nằm trong đó — có bài kiểm đóng đinh. Cho tới 31/08/2026 cổng vẫn dựng liên kết trỏ vào
     * đường ấy cho hai cột này, nên nó sẽ trả <b>404 câm</b> ngay lượt đầu có dữ liệu thật. Xem
     * {@link PublicConstructionCatalogService#publishedDocument}.
     *
     * <p>Trả thẳng {@code ResponseEntity<byte[]>} nên không bị bọc envelope — đây là byte của một
     * tệp, không phải tài nguyên JSON (§10.52).
     *
     * <p>{@code attachment} chứ không phải {@code inline}: ô chọn tệp ở màn hình quản trị hứa
     * <i>"liên kết tải về"</i>, và đây là văn bản quy trình chứ không phải ảnh minh hoạ.
     */
    @GetMapping("/documents/{publicId}")
    @Operation(summary = "Tệp Quy trình vận hành / Phương án bảo vệ đã công bố của một công trình")
    @PublicEndpoint(reason = "Hai cột tài liệu của bảng Danh mục công trình — CR-28, §6 nhóm Tất cả người dùng")
    public ResponseEntity<byte[]> document(@PathVariable UUID publicId) {
        AttachmentContent tep = catalog.publishedDocument(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tep.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(CACHE_TEP_GIAY))
                        .cachePublic()
                        .immutable())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + HttpHeaderText.tenTepAnToan(tep.originalName()) + "\"")
                .body(tep.content());
    }
}
