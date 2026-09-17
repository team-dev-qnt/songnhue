package com.songnhue.hr.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.HttpHeaderText;
import com.songnhue.hr.application.BaoCaoNhanSuService;
import com.songnhue.hr.domain.MaBaoCaoNhanSu;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Thống kê và báo cáo nhân sự — {@code /api/v1/hr/bao-cao} (CN-04.8, SRS M4.14–M4.17).
 *
 * <h2>⭐ Đầu nhận ĐẦU TIÊN của {@code hr:report:view} và {@code hr:report:export}</h2>
 *
 * <p>Đo 14/09/2026: cả hai mã quyền được seed từ 13/08 và có <b>0 endpoint</b> suốt 32 ngày.
 * {@code RbacMatrixTest} giữ hai dòng miễn kiểm kèm ghi chú *"Phase 3 (CN-04.8)"* — nay hết lý do.
 *
 * <h2>⛔⛔ HAI quyền, ⛔ không phải một — và khác biệt là có thật</h2>
 *
 * <p><b>Xem</b> số tổng hợp trên màn hình và <b>mang cả danh sách cán bộ ra khỏi hệ thống</b> là
 * hai việc khác nhau: tệp trích ngang rời máy chủ thì hệ ⛔ không còn kiểm soát được gì nữa. Đặc tả
 * tách sẵn hai mã quyền; gộp chúng *"cho gọn"* là xoá một ranh giới mà khách đã vẽ.
 *
 * <h2>⚠ Màn hình này CẮT theo phạm vi đơn vị</h2>
 *
 * <p>Ngược với danh bạ (CN-04.6) và sơ đồ tổ chức (CN-04.1). Báo cáo là <b>đầu ra của hồ sơ</b>, mà
 * hồ sơ chịu M4.13 — rộng hơn nguồn là một đường vòng qua chính luật ấy. Xem
 * {@link BaoCaoNhanSuService}.
 */
@RestController
@RequestMapping("/api/v1/hr/bao-cao")
@Tag(name = "04-hr · Báo cáo nhân sự", description = "KPI, biểu đồ và 8 báo cáo BCNS — CN-04.8")
public class BaoCaoNhanSuController {

    private final BaoCaoNhanSuService baoCao;

    public BaoCaoNhanSuController(BaoCaoNhanSuService baoCao) {
        this.baoCao = baoCao;
    }

    @GetMapping("/tong-quan")
    @Operation(summary = "KPI + dữ liệu ba biểu đồ — CẮT theo phạm vi đơn vị của người xem")
    @RequirePermission("hr:report:view")
    public BaoCaoNhanSuService.TongQuan tongQuan() {
        return baoCao.tongQuan();
    }

    /**
     * @param khaDung {@code false} ⇒ {@code lyDo} <b>bắt buộc</b> khác null
     */
    public record MucBaoCao(String ma, String ten, String moTa, boolean khaDung, String lyDo) {}

    /**
     * Danh mục <b>đủ TÁM</b> báo cáo, kèm mã nào chưa dựng được và <b>vì sao</b>.
     *
     * <p>⛔⛔ Bảy mã khả dụng cộng một dòng im lặng thì lượt nghiệm thu đếm bảy nút rồi tick đủ —
     * và *"BCNS-07 chưa có"* trở thành một sự thật ⛔ không nơi nào ghi. Xem
     * {@link MaBaoCaoNhanSu}.
     */
    @GetMapping("/danh-muc")
    @Operation(summary = "Tám báo cáo BCNS — kèm lý do với mã chưa dựng được")
    @RequirePermission("hr:report:view")
    public List<MucBaoCao> danhMuc() {
        return java.util.Arrays.stream(MaBaoCaoNhanSu.values())
                .map(m -> new MucBaoCao(m.ma(), m.ten(), m.moTa(), m.khaDung(), m.lyDo()))
                .toList();
    }

    /**
     * Kết xuất một báo cáo ra <b>CSV</b>.
     *
     * <p>⛔ CSV chứ ⛔ không phải {@code .xlsx} — quyết định T34.8: Apache POI kéo ~12 MB phụ thuộc
     * và một bề mặt CVE mới trên VPS 2 nhân, trong khi Excel mở CSV được. {@code BangCsv} mang sẵn
     * ba quy ước để Excel bản tiếng Việt đọc đúng.
     *
     * <p>⬜ <b>PDF thì chưa</b> (T42.14) — và nó ⛔ không phải chuyện chọn thư viện: bố cục bản in
     * chờ <b>G10</b>.
     */
    @GetMapping("/xuat/{ma}")
    @Operation(summary = "Tải một báo cáo dạng CSV — mã chưa dựng được trả HR-2009")
    @RequirePermission("hr:report:export")
    public ResponseEntity<Resource> xuat(@PathVariable String ma) {
        MaBaoCaoNhanSu maBaoCao;
        try {
            maBaoCao = MaBaoCaoNhanSu.tuMa(ma);
        } catch (IllegalArgumentException khong) {
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
        if (!maBaoCao.khaDung()) {
            // ⛔ Mã lỗi RIÊNG, ⛔ không phải 404: báo cáo này CÓ trong danh mục, nó chỉ chưa dựng
            //   được — và câu lỗi mang nguyên văn LÝ DO để người vận hành đọc được ngay.
            throw new BusinessRuleException(ErrorCode.HR_2009, maBaoCao.ma(), maBaoCao.lyDo());
        }
        BaoCaoNhanSuService.TepXuat tep = baoCao.xuat(maBaoCao);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, HttpHeaderText.contentDisposition(tep.tenTep()))
                // ⚠ `text/csv` kèm charset UTF-8 — tệp đã có BOM, nhưng một trình duyệt đọc header
                //   sai vẫn làm hỏng tiếng Việt trước khi người dùng kịp mở bằng Excel.
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(new ByteArrayResource(tep.noiDung()));
    }
}
