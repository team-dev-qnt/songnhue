package com.songnhue.core.common.persistence;

/**
 * Entity <b>giữ lại</b> lý do người dùng nhập khi thực hiện một bước chuyển — bổ sung cho
 * {@link WorkflowAware}.
 *
 * <h2>⚠⚠ Lỗ hổng mà interface này bịt — đo được ngày 02/09/2026</h2>
 *
 * <p>Cột {@code workflow_transitions.requires_reason} (thêm 24/08) bắt người dùng nhập lý do, và
 * {@code WorkflowEngine} <b>ném {@code SYS-0003} nếu thiếu</b>. Nhưng sau lượt kiểm ấy, tham số
 * {@code reason} <b>không đi đâu cả</b>: nó không được ghi vào entity, không vào
 * {@code audit_logs} (bảng ấy ⛔ không có cột lý do), không vào thông báo. Đã kiểm bằng
 * {@code grep}: {@code reason} xuất hiện trong {@code WorkflowEngine} đúng ba lần, cả ba đều ở
 * nhánh <i>kiểm tra</i>.
 *
 * <p>⇒ Trước bản này, {@code requires_reason = TRUE} chỉ <b>bắt người dùng gõ một câu rồi ném đi</b>.
 * Đúng hình dạng luật 15/27: một nửa cặp đọc–ghi, và nó <i>trông</i> như cả cặp vì màn hình có ô
 * nhập, engine có kiểm, cột có trong lược đồ. Triệu chứng chỉ lộ ra khi có người đi tìm câu trả lời
 * cho <i>"vì sao bản ghi này bị loại bỏ"</i> — thường là nhiều tháng sau.
 *
 * <h2>Vì sao ghi vào ENTITY chứ không vào {@code audit_logs}</h2>
 *
 * <p>Ghi thẳng vào nhật ký kiểm toán đòi thêm một cột cho bảng có <b>chuỗi băm</b> — đổi lược đồ ấy
 * là đổi cách tính hash của toàn bộ lịch sử. Còn ghi vào một cột của chính entity thì
 * {@code AuditEventListener} bắt được lệnh {@code UPDATE} và lý do đi vào chuỗi băm <b>miễn phí</b>,
 * cùng lô với ai bấm và lúc nào — không đụng gì tới cơ chế đang chạy.
 *
 * <p>⛔ Interface này <b>không</b> bắt buộc: entity nào không quan tâm thì chỉ implement
 * {@link WorkflowAware} như cũ, và engine cư xử y hệt trước.
 */
public interface WorkflowReasonAware extends WorkflowAware {

    /**
     * Ghi lại lý do của bước chuyển vừa được engine chấp nhận.
     *
     * <p>⚠ Gọi <b>sau</b> khi kiểm quyền và kiểm "phải nêu lý do", <b>trước</b> {@code applyState}:
     * nhờ vậy một bước chuyển bị từ chối ⛔ không để lại lý do mồ côi trên bản ghi.
     *
     * <p>⚠ Engine gọi hàm này ở <b>mọi</b> bước chuyển, kể cả bước không đòi lý do — khi ấy
     * {@code reason} là {@code null}. Entity tự quyết định làm gì: bỏ qua, hay ghi đè bằng
     * {@code null} để một bước sau xoá dấu vết của bước trước. ⛔ Đừng để engine đoán hộ.
     *
     * @param action mã hành động vừa thực hiện — cùng một cột lý do có thể mang ý nghĩa khác nhau
     *     tuỳ bước, và entity là nơi biết điều đó
     * @param reason lý do người dùng nhập; {@code null} khi bước chuyển không đòi
     */
    void applyWorkflowReason(String action, String reason);
}
