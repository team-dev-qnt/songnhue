package com.songnhue.operations.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.HttpHeaderText;
import com.songnhue.operations.application.BaoCaoVanHanhService;
import com.songnhue.operations.domain.MaBaoCaoVanHanh;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Báo cáo vận hành công trình — {@code /api/v1/ops/bao-cao} (CN-02.10).
 *
 * <h2>⭐ Đầu nhận ĐẦU TIÊN của {@code ops:report:view} và {@code ops:report:export}</h2>
 *
 * <p>Đo 14/09/2026: cả hai mã quyền được seed từ 13/08 và có <b>0 endpoint</b>. Cùng với
 * {@code ops:gis-layer:*}, đây là bốn dòng miễn kiểm *"Phase 3"* cuối cùng của {@code RbacMatrixTest}.
 *
 * <h2>⛔ Hai quyền, ⛔ không phải một</h2>
 *
 * <p>Cùng lý lẽ CN-04.8: <b>xem</b> danh mục báo cáo và <b>mang danh sách công trình + chi phí sửa
 * chữa ra khỏi hệ thống</b> là hai việc khác nhau. Đặc tả tách sẵn hai mã quyền.
 *
 * <h2>⛔⛔ Danh mục khai ĐỦ BẢY mã, kể cả BỐN mã đã bỏ vĩnh viễn</h2>
 *
 * <p>BC-01/02/03 mất nguồn (nhật ký vận hành loại khỏi phạm vi — B1/F1, xác nhận bởi G2), BC-04 mất
 * nguồn (kế hoạch vụ mùa — A1). Liệt kê ba mã còn sống là để câu hỏi *"BC-01 đâu?"* quay lại ở mọi
 * lượt nghiệm thu, mỗi lần lại phải đi tra tài liệu.
 *
 * <p>⚠ Và nó là <b>một trạng thái khác</b> với {@code BCNS-07} của CN-04.8: mã kia <b>chưa làm
 * được</b> (chờ G6, sẽ làm); bốn mã ở đây <b>⛔ không bao giờ làm</b>. Giao diện ⛔ không được trộn
 * chúng thành một nhãn *"chưa có"*.
 */
@RestController
@RequestMapping("/api/v1/ops/bao-cao")
@Tag(name = "02-ops · Báo cáo vận hành", description = "BC-06 · BC-09 · BC-10 — CN-02.10")
public class BaoCaoVanHanhController {

    private final BaoCaoVanHanhService baoCao;

    public BaoCaoVanHanhController(BaoCaoVanHanhService baoCao) {
        this.baoCao = baoCao;
    }

    /**
     * @param khaDung {@code false} ⇒ {@code lyDo} <b>bắt buộc</b> khác null
     */
    public record MucBaoCao(String ma, String ten, String moTa, boolean khaDung, String lyDo) {}

    @GetMapping("/danh-muc")
    @Operation(summary = "Bảy mã BC — kèm lý do với bốn mã đã bỏ vĩnh viễn")
    @RequirePermission("ops:report:view")
    public List<MucBaoCao> danhMuc() {
        return Arrays.stream(MaBaoCaoVanHanh.values())
                .map(m -> new MucBaoCao(m.ma(), m.ten(), m.moTa(), m.khaDung(), m.lyDo()))
                .toList();
    }

    /**
     * Kết xuất một báo cáo ra <b>CSV</b>.
     *
     * <p>⛔ CSV chứ ⛔ không {@code .xlsx} — quyết định T34.8. ⬜ Bản in <b>PDF</b> chờ <b>G10</b>
     * (duyệt bố cục) và <b>T42.14</b> (kho ⛔ không có bộ kết xuất nào).
     *
     * @param tu {@code null} = ⛔ không giới hạn dưới; bản báo cáo <b>ghi rõ</b> điều đó ở dòng kỳ
     */
    @GetMapping("/xuat/{ma}")
    @Operation(summary = "Tải một báo cáo dạng CSV — mã đã bỏ vĩnh viễn trả OPS-2023")
    @RequirePermission("ops:report:export")
    public ResponseEntity<Resource> xuat(
            @PathVariable String ma,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den) {
        MaBaoCaoVanHanh maBaoCao;
        try {
            maBaoCao = MaBaoCaoVanHanh.tuMa(ma);
        } catch (IllegalArgumentException khong) {
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
        if (!maBaoCao.khaDung()) {
            // ⛔ Mã lỗi RIÊNG, ⛔ không phải 404: báo cáo này CÓ trong danh mục, nó đã bị bỏ khỏi
            //   phạm vi vì mất nguồn dữ liệu — và câu lỗi mang nguyên văn lý do kèm mã chốt.
            throw new BusinessRuleException(ErrorCode.OPS_2023, maBaoCao.ma(), maBaoCao.lyDo());
        }
        BaoCaoVanHanhService.TepXuat tep = baoCao.xuat(maBaoCao, tu, den);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, HttpHeaderText.contentDisposition(tep.tenTep()))
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(new ByteArrayResource(tep.noiDung()));
    }
}
