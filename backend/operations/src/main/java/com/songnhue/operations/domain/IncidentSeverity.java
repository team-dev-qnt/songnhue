package com.songnhue.operations.domain;

/**
 * Mức độ nghiêm trọng của sự cố — CN-02.2, chốt G1 (PA A).
 *
 * <p>Chỉ có nghĩa với {@link MaintenanceType#KHAC_PHUC_SU_CO}. CSDL ép điều đó bằng một CHECK
 * <b>hai chiều</b>: sự cố thì bắt buộc có mức độ ({@code OPS-2003}), mà không phải sự cố thì bắt
 * buộc rỗng. Chiều thứ hai dễ bị bỏ qua nhưng cũng cần: một bản ghi bảo trì định kỳ mang mức độ
 * "Nghiêm trọng" sẽ làm mọi bộ lọc theo mức độ đếm nhầm.
 *
 * <p>⚠ Mức độ <b>không</b> quyết định trạng thái công trình. Sự cố nào còn mở cũng làm công trình
 * mang cờ đỏ, kể cả mức Thấp — CN-02.1 không xếp hạng cờ đỏ theo mức độ. Mức độ dùng để xếp thứ tự
 * xử lý và để lọc danh sách.
 */
public enum IncidentSeverity {
    NGHIEM_TRONG,
    CAO,
    TRUNG_BINH,
    THAP
}
