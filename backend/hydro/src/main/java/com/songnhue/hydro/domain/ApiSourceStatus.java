package com.songnhue.hydro.domain;

/**
 * Trạng thái nguồn dữ liệu — do <b>con người</b> quyết định.
 *
 * <p>⛔ Không có giá trị "LOI" ở đây, và đó là điểm chính. Sức khoẻ của nguồn là thứ <i>máy</i> quan
 * sát được, và nó đã nằm ở {@code last_success_at} / {@code consecutive_failures} — những cột mang
 * <b>sự kiện đã xảy ra</b>, luôn đúng kể cả khi poller chết. Một cột trạng thái do máy ghi thì đứng
 * yên đúng lúc nó quan trọng nhất: poller chết ⇒ không ai ghi ⇒ nguồn vẫn hiện "bình thường" trong
 * khi không có số nào về.
 */
public enum ApiSourceStatus {
    /** Còn dùng — poller sẽ gọi theo lịch. */
    HOAT_DONG,

    /** Người vận hành tạm ngừng. Poller bỏ qua và ghi rõ lý do, không im lặng. */
    TAM_DUNG
}
