package com.songnhue.operations.domain;

/**
 * Vòng đời hồ sơ công trình — do <b>con người</b> quyết định.
 *
 * <p>Tách hẳn khỏi {@link OperationalStatus}, và đây là chỗ hay bị gộp làm một rồi hỏng cả hai. Vòng
 * đời trả lời câu "công trình này còn được vận hành không"; trạng thái vận hành trả lời câu "ngay lúc
 * này nó đang thế nào". Gộp lại thì hoặc một sự cố vừa đóng sẽ hồi sinh một công trình đã thanh lý,
 * hoặc người dùng phải sửa tay một cột lẽ ra do máy tính.
 */
public enum LifecycleState {
    /** Đang khai thác bình thường. */
    DANG_HOAT_DONG,

    /** Ngừng theo mùa vụ — vẫn thuộc quản lý, sẽ vận hành lại. */
    NGUNG_MUA_VU,

    /**
     * Đã thanh lý.
     *
     * <p>Không nhận bản ghi sửa chữa/bảo trì mới ({@code OPS-2002}, WS-18) và không tính vào các chỉ
     * tiêu "công trình đang hoạt động" của dashboard.
     */
    DA_THANH_LY
}
