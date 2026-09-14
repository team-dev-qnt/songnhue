package com.songnhue.hr.domain;

/**
 * Trạng thái đơn nghỉ phép — CN-04.9.
 *
 * <p>⛔⛔ Enum này ⛔ <b>KHÔNG</b> là nguồn sự thật của luồng. Nguồn sự thật là bảng
 * {@code workflow_transitions} (quy tắc 4), và enum ở đây chỉ để mã Java gọi tên trạng thái mà ⛔
 * không rải chuỗi khắp nơi. Thêm một hằng vào đây <b>⛔ không</b> tạo ra một trạng thái mới — phải
 * có một bước chuyển dẫn tới nó, và khối {@code DO $$} của {@code V202609141079} đếm lại đúng điều
 * đó lúc migrate.
 */
public enum LeaveState {
    CHO_DUYET,
    /** Chỉ tới được khi {@code hr.leave.second-level-threshold-days > 0} và đơn đủ dài (chốt C2). */
    CHO_DUYET_2,
    DA_DUYET,
    TU_CHOI,
    DA_HUY;

    /** Đơn còn đang chờ ai đó quyết — <b>vẫn GIỮ CHỖ</b> trong số dư phép. */
    public boolean dangChoDuyet() {
        return this == CHO_DUYET || this == CHO_DUYET_2;
    }

    /**
     * Đơn còn <b>tiêu tốn</b> số dư — đã duyệt <i>hoặc</i> đang chờ.
     *
     * <p>⛔⛔ Đặc tả: <i>"Còn lại = Được hưởng − <b>Đã nghỉ</b> − <b>Đang chờ duyệt</b>"</i>. Bỏ vế
     * <i>đang chờ</i> là cho người lao động nộp mười đơn cùng lúc, mỗi đơn nhìn thấy một số dư
     * <b>chưa trừ chín đơn kia</b> — và cả mười đều "hợp lệ" cho tới lúc duyệt.
     */
    public boolean conChiemSoDu() {
        return dangChoDuyet() || this == DA_DUYET;
    }
}
