package com.songnhue.operations.domain;

/**
 * Loại công việc trong lịch sử sửa chữa — CN-02.2.
 *
 * <h2>⛔ Đây là chỗ sự cố và bảo trì phân biệt nhau — quy tắc 15</h2>
 *
 * Chốt G1 (PA A): <b>không có entity sự cố riêng</b>. Một sự cố là một
 * {@link MaintenanceLog} mang {@link #KHAC_PHUC_SU_CO}, cộng thêm {@link IncidentSeverity}. Không
 * bảng {@code incidents}, không mã {@code SC-}, không vòng đời bảy trạng thái.
 *
 * <p>Giá trị của enum này quyết định ba thứ, nên đổi nó không bao giờ là đổi một nhãn:
 *
 * <ol>
 *   <li><b>Mức độ</b> bắt buộc có hay bắt buộc rỗng (CHECK hai chiều ở CSDL)
 *   <li><b>Quy trình xử lý nào</b> có hiệu lực — {@code MAINTENANCE_INCIDENT} đòi
 *       {@code ops:maintenance:close-incident} để đóng, quy trình còn lại đòi
 *       {@code ops:maintenance:update}
 *   <li><b>Trạng thái công trình</b> suy ra là {@code SU_CO} (đỏ) hay {@code BAO_TRI} (vàng)
 * </ol>
 *
 * <p>⛔ Là enum trong mã chứ không phải danh mục có CRUD, khác với "mã tình hình vận hành" (quy tắc
 * 16). Lý do giống {@link ConstructionType}: thêm một loại ở đây kéo theo một nhánh trong chuỗi suy
 * ra trạng thái và một bộ quyền riêng — tức là phải sửa mã dù có bảng danh mục hay không.
 */
public enum MaintenanceType {
    SUA_CHUA,
    BAO_TRI_DINH_KY,
    NANG_CAP,
    THAY_THE_THIET_BI,

    /** Sự cố — bắt buộc có {@link IncidentSeverity}, và làm công trình mang cờ đỏ khi còn mở. */
    KHAC_PHUC_SU_CO;

    public boolean laSuCo() {
        return this == KHAC_PHUC_SU_CO;
    }
}
