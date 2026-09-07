package com.songnhue.operations.domain;

/**
 * Trạng thái vận hành công trình — CN-02.1, SRS §3.2.4.
 *
 * <p>⛔ <b>Giá trị dẫn xuất.</b> Không có màn hình nào cho sửa, không có endpoint nào nhận. Client
 * gửi lên thì bị từ chối bằng {@code OPS-3001} (quy tắc 4 của dự án). Nơi duy nhất ghi cột này là
 * {@code ConstructionStatusService}.
 *
 * <p>Thứ tự ưu tiên khi tính (CN-02.1, chốt G1 + G4):
 *
 * <ol>
 *   <li>Có bản ghi <b>Khắc phục sự cố</b> đang mở → {@link #SU_CO} — <i>WS-18</i>
 *   <li>Có bản ghi <b>Sửa chữa/Bảo trì</b> đang thực hiện → {@link #BAO_TRI} — <i>WS-18</i>
 *   <li>Có cảnh báo ngưỡng đang xảy ra tại điểm đo liên kết → {@link #CANH_BAO} — <i>Phase 2</i>
 *   <li>Ánh xạ từ mã tình hình vận hành hiện hành (CN-02.11) — <i>WS-19</i>
 *   <li>Mặc định {@link #BINH_THUONG}
 * </ol>
 *
 * <p>Vòng đời ({@link LifecycleState}) đứng <b>trên</b> cả năm mức đó: một công trình đã thanh lý thì
 * không có nghĩa lý gì khi hiện "Bình thường", và cũng không có sự cố nào để mở.
 */
public enum OperationalStatus {
    /** Xanh. */
    BINH_THUONG,

    /** Vàng — có cảnh báo ngưỡng thuỷ văn đang xảy ra. */
    CANH_BAO,

    /** Đỏ — có bản ghi khắc phục sự cố đang mở. */
    SU_CO,

    /** Vàng — đang sửa chữa / bảo trì. */
    BAO_TRI,

    /** Xám — ngừng theo mùa vụ. */
    NGUNG_MUA_VU,

    /** Đen — đã thanh lý, không còn vận hành. */
    DA_THANH_LY
}
