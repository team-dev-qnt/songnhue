package com.songnhue.core.common.importer;

import java.util.List;

/**
 * Kết quả một lượt nhập tệp — <b>dùng chung cho mọi màn hình nhập</b>.
 *
 * <h2>⭐ Vì sao một kiểu chung chứ ⛔ không mỗi module một bản</h2>
 *
 * <p>Tới 09/09/2026 kiểu này là hai {@code record} lồng trong {@code ConstructionImportService} của
 * module {@code operations}. Hệ quả: module {@code hydro} muốn có màn hình nhập <b>⛔ không dùng lại
 * được</b> (luật 6 — module ⛔ không import nhau), nên nó sẽ phải chép một bản gần giống. Hai bản
 * gần giống thì giao diện phải viết hai hộp thoại, và hai hộp thoại sẽ lệch nhau ở đúng chỗ ⛔ không
 * ai nhìn — thường là nhánh hiển thị lỗi, nhánh hiếm chạy nhất.
 *
 * <p>⚠ Tên trường giữ <b>nguyên</b> ({@code totalRows}, {@code toCreate}, {@code toUpdate},
 * {@code errors}) vì chúng là <b>hợp đồng JSON</b> mà {@code api-types.ts} đang đọc. Đổi tên ở đây
 * là đổi hợp đồng với giao diện, và trình biên dịch ⛔ không bắt được.
 *
 * @param applied {@code false} = mới chỉ chạy khô, ⛔ chưa ghi gì
 * @param totalRows tổng số dòng dữ liệu ĐỌC ĐƯỢC. ⛔ Phải là số dòng THẬT của tệp — đếm sau một lượt
 *     cắt cụt là cách bản báo cáo nói dối mà ⛔ không ai phát hiện (xem {@code SYS-0012})
 * @param toCreate số dòng sẽ thêm mới
 * @param toUpdate số dòng sẽ cập nhật lên bản ghi đang có
 */
public record KetQuaNhap(boolean applied, int totalRows, int toCreate, int toUpdate, List<LoiDong> errors) {

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Một lỗi gắn với một dòng cụ thể của tệp.
     *
     * @param rowNumber số dòng <b>như người dùng thấy trong Excel</b> (dòng tiêu đề là 1). ⛔ Báo lỗi
     *     theo số dòng nội bộ thì người sửa tệp phải tự cộng trừ, và họ sẽ sửa nhầm dòng
     * @param column {@code null} khi lỗi thuộc cả dòng chứ ⛔ không thuộc một ô
     */
    public record LoiDong(int rowNumber, String column, String message) {}
}
