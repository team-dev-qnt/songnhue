package com.songnhue.operations.domain;

import java.util.List;

/**
 * Ba trạng thái xử lý của một bản ghi sửa chữa — CN-02.2.
 *
 * <h2>Vì sao là hằng chuỗi chứ không phải enum</h2>
 *
 * {@code WorkflowAware.currentState()} trả {@code String}, vì luật chuyển trạng thái nằm ở
 * <b>dữ liệu</b> ({@code workflow_transitions}) chứ không ở mã. Dựng một enum rồi chuyển đổi qua lại
 * sẽ tạo ra nơi thứ hai định nghĩa tập trạng thái, và nơi thứ hai đó sẽ lệch với migration vào ngày
 * ai đó thêm một trạng thái bằng SQL — đúng thứ mà luật "chỗ nào con người phải nhớ hai nơi thì chỗ
 * đó cần một phép kiểm nhớ hộ" cảnh báo.
 *
 * <p>Lớp này chỉ gom lại những chuỗi mà <b>mã Java buộc phải biết</b> — cụ thể là câu hỏi "bản ghi
 * này còn mở không", thứ mà chuỗi suy ra trạng thái công trình hỏi ở mọi lượt tính lại. Có bài kiểm
 * đối chiếu tập này với ràng buộc CHECK của CSDL, nên hai nơi không thể lệch âm thầm.
 */
public final class MaintenanceState {

    /** Vừa ghi nhận, chưa ai tiếp nhận. */
    public static final String MOI = "MOI";

    /** Đã tiếp nhận, đang làm. */
    public static final String DANG_XU_LY = "DANG_XU_LY";

    /** Đã làm xong — bắt buộc có ngày hoàn thành ({@code OPS-2004}). */
    public static final String DA_XU_LY = "DA_XU_LY";

    /**
     * Các trạng thái tính là <b>đang mở</b>.
     *
     * <p>Đây là định nghĩa dùng chung cho: cờ đỏ sự cố trên bản đồ điều hành, ô KPI "Sự cố chưa xử
     * lý", và câu chặn xoá công trình. Ba chỗ đó phải hiểu "đang mở" giống hệt nhau, nếu không thì
     * bản đồ đỏ mà danh sách rỗng.
     */
    public static final List<String> DANG_MO = List.of(MOI, DANG_XU_LY);

    /** Toàn bộ trạng thái hợp lệ — đối chiếu với CHECK {@code ck_maintenance_logs_status}. */
    public static final List<String> TAT_CA = List.of(MOI, DANG_XU_LY, DA_XU_LY);

    private MaintenanceState() {}

    public static boolean dangMo(String state) {
        return DANG_MO.contains(state);
    }
}
