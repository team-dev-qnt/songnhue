package com.songnhue.hydro.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hydro.application.HydroDiagnosticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Mã nguồn <b>chưa khai thành điểm đo</b> — {@code /api/v1/hyd/ma-la} (T31.13).
 *
 * <h2>Màn hình này chỉ LIỆT KÊ — và đó là toàn bộ thiết kế</h2>
 *
 * <p>Nguồn trả 28 mã, ta khai 19. Chín mã còn lại ta <b>không biết là trạm nào</b>: không tên, không
 * toạ độ, không biết thuộc công trình nào. Đó là G8 và nó thuộc Công ty.
 *
 * <ul>
 *   <li>⛔ <b>Không có endpoint nào tạo điểm đo từ đây</b> (quy tắc parse 5). Bản suy đoán trước đó
 *       dò danh tính theo giá trị đo đã <b>sai 1/4 mã</b> — và một điểm đo gán nhầm mã là toàn bộ
 *       lịch sử của trạm này đi vào biểu đồ của trạm khác, vẽ vẫn đẹp.
 *   <li>⭐ Nút <i>"Khai thành điểm đo"</i> của giao diện chỉ mở biểu mẫu điểm đo với ô
 *       {@code api_code} <b>điền sẵn</b>. Người khai vẫn phải gõ tên, vai trò, nguồn — tức vẫn phải
 *       <b>biết</b> mã ấy là gì.
 * </ul>
 *
 * <p>Quyền {@code hyd:measurement:view} — cùng lý do đã viết ở {@link SyncLogController}. Việc
 * <i>khai</i> thì đòi {@code hyd:station:manage} ở endpoint tạo điểm đo, ⛔ không nới ở đây.
 */
@RestController
@RequestMapping("/api/v1/hyd/ma-la")
@Tag(name = "03-hyd · Mã lạ từ nguồn", description = "Số đo của mã chưa khai — giữ vì nguồn không có API lịch sử")
public class UnmappedCodeController {

    private final HydroDiagnosticsService chanDoan;

    public UnmappedCodeController(HydroDiagnosticsService chanDoan) {
        this.chanDoan = chanDoan;
    }

    /**
     * ⚠ Không phân trang — số dòng bằng <b>số mã khác nhau nguồn phát</b> (đo được: 28), ⛔ không
     * phải số bản ghi đã tích. Danh sách này teo dần theo tiến độ khai báo.
     */
    @GetMapping
    @Operation(summary = "Mã chưa khai, gộp theo mã — kèm số bản ghi đã tích và giá trị NGUYÊN VĂN nguồn")
    @RequirePermission({"hyd:measurement:view", "hyd:api-source:manage"})
    public List<HydroDiagnosticDtos.MaLaView> danhSach() {
        return chanDoan.maLa().stream().map(HydroDiagnosticDtos.MaLaView::cua).toList();
    }
}
