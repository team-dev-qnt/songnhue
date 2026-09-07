package com.songnhue.content.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.FeedbackModerationService;
import com.songnhue.content.domain.Feedback;
import com.songnhue.content.domain.FeedbackStatus;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.AllowedAction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Kiểm duyệt góp ý / đánh giá — CN-01.6, chốt <b>D1</b> (kiểm duyệt 100%).
 *
 * <h2>Vì sao controller này ra đời CÙNG lượt với biểu mẫu trên cổng</h2>
 *
 * <p>⛔ Không có nó thì "kiểm duyệt 100%" nghĩa là <b>mọi góp ý biến mất vĩnh viễn</b>: bảng có,
 * biểu mẫu có, và ⛔ không ai duyệt được nên ⛔ không mục nào lên cổng. Vòng đọc–ghi phải đủ cả hai
 * nửa trong cùng một lượt (luật 27) — và ở đây "nửa đọc" gồm <b>cả</b> màn hình kiểm duyệt lẫn nơi
 * công bố, vì một nút Duyệt ⛔ không dẫn tới đâu thì cũng ⛔ không quyết định gì.
 *
 * <h2>⛔ Nội dung là văn bản do người lạ nhập</h2>
 *
 * <p>Trả về nguyên văn; nơi hiển thị bắt buộc dựng thành text. Ở đây nguy cơ <b>cao hơn</b>
 * {@code contacts}: sau khi duyệt nó còn đi tiếp ra cổng công khai.
 */
@RestController
@RequestMapping("/api/v1/cms/feedbacks")
@Tag(name = "01-cms · Góp ý", description = "Kiểm duyệt góp ý/đánh giá gửi từ cổng công khai")
public class FeedbackController {

    private final FeedbackModerationService feedbacks;

    public FeedbackController(FeedbackModerationService feedbacks) {
        this.feedbacks = feedbacks;
    }

    /**
     * ⚠ Có {@code moderationNote} — đây là bản dành cho <b>cán bộ</b>.
     *
     * <p>⛔ ⛔ Record này ⛔ <b>KHÔNG BAO GIỜ</b> được dùng ở một endpoint công khai. Bản công khai
     * là {@code PublicPortalController.PublicFeedbackView} và nó cố ý có <b>ít trường hơn</b>;
     * {@code FeedbackHttpTest} phản chiếu số trường của cả hai để giữ điều đó đúng.
     */
    public record FeedbackView(
            UUID publicId,
            String fullName,
            String email,
            Short rating,
            String content,
            FeedbackStatus status,
            Instant createdAt,
            String moderationNote) {

        static FeedbackView of(Feedback f) {
            return new FeedbackView(
                    f.getPublicId(),
                    f.getFullName(),
                    f.getEmail(),
                    f.getRating(),
                    f.getContent(),
                    f.getStatus(),
                    f.getCreatedAt(),
                    f.getModerationNote());
        }
    }

    public record TransitionForm(String action, String reason) {}

    /**
     * Số liệu tổng hợp — ⚠ {@code diemTrungBinh} ⛔ <b>không bao giờ</b> đi một mình.
     *
     * <p>Xem {@link FeedbackModerationService.ThongKe}: nó tính trên hai lớp lọc, nên mẫu số
     * ({@code soCoDiem}) và bốn con số trạng thái phải đi cùng để phần bị loại nhìn thấy được.
     * {@code null} ở {@code diemTrungBinh} nghĩa là <b>chưa ai chấm</b> — ⛔ không phải 0 sao.
     */
    public record ThongKeView(
            long tong, long choDuyet, long daDuyet, long tuChoi, long an, long soCoDiem, BigDecimal diemTrungBinh) {}

    @GetMapping
    @Operation(summary = "Danh sách góp ý, mới nhất trước")
    @RequirePermission("cms:feedback:manage")
    public Page<FeedbackView> list(
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return feedbacks.danhSach(status, page, size).map(FeedbackView::of);
    }

    @GetMapping("/pending-count")
    @Operation(summary = "Số góp ý đang chờ duyệt — cho huy hiệu trên thanh điều hướng quản trị")
    @RequirePermission("cms:feedback:manage")
    public long pendingCount() {
        return feedbacks.demChoDuyet();
    }

    @GetMapping("/summary")
    @Operation(summary = "Tổng hợp mức độ hài lòng — ⛔ điểm trung bình LUÔN kèm mẫu số")
    @RequirePermission("cms:feedback:manage")
    public ThongKeView summary() {
        var t = feedbacks.tongHop();
        return new ThongKeView(
                t.tong(), t.choDuyet(), t.daDuyet(), t.tuChoi(), t.an(), t.soCoDiem(), t.diemTrungBinh());
    }

    /**
     * Các nút được phép hiện — <b>giao diện ⛔ không tự suy</b> (conventions.md §3).
     *
     * <p>Đã lọc theo quyền và theo trạng thái hiện tại, kèm cờ {@code requiresReason}. Tự liệt kê
     * nút ở FE là bản sao thứ hai của một luật đang nằm trong CSDL — và bản sao ấy sẽ lệch.
     */
    @GetMapping("/{publicId}/actions")
    @Operation(summary = "Bước chuyển hợp lệ ở trạng thái hiện tại, đã lọc theo quyền")
    @RequirePermission("cms:feedback:manage")
    public List<AllowedAction> actions(@PathVariable UUID publicId) {
        return feedbacks.hanhDongChoPhep(publicId);
    }

    @PostMapping("/{publicId}/transitions")
    @Operation(summary = "Duyệt / từ chối / ẩn / hiện lại; bước đòi lý do mà thiếu là SYS-0003")
    @RequirePermission("cms:feedback:manage")
    public FeedbackView transition(@PathVariable UUID publicId, @RequestBody TransitionForm form) {
        return FeedbackView.of(feedbacks.chuyenTrangThai(publicId, form.action(), form.reason()));
    }

    /**
     * Xoá mềm. ⛔ Cố ý ⛔ <b>không</b> chặn ở trạng thái nào — xem
     * {@link FeedbackModerationService#xoa}.
     *
     * <p>⚠ Gác bằng cùng quyền {@code cms:feedback:manage} với mọi thao tác khác, ⛔ không dựng một
     * quyền {@code :delete} riêng: danh mục quyền hôm nay có <b>đúng một</b> mã cho góp ý, và
     * CN-05.2 (sửa ma trận quyền trên giao diện) <b>chưa tồn tại</b> — một mã mới chỉ gán được bằng
     * migration, tức đúng thứ luật 15 gọi là công tắc chưa ai đọc.
     */
    @DeleteMapping("/{publicId}")
    @Operation(summary = "Xoá mềm một góp ý")
    @RequirePermission("cms:feedback:manage")
    public ResponseEntity<Void> delete(@PathVariable UUID publicId) {
        feedbacks.xoa(publicId);
        return ResponseEntity.noContent().build();
    }
}
