package com.songnhue.hydro.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Ảnh chụp tín hiệu của một điểm đo — đầu vào của job phát hiện <b>mất tín hiệu</b> (T31.8).
 *
 * <p>Ghép đúng hai thứ CSDL thật sự lưu: quyết định của con người ({@code stations.active}) và mốc
 * bản ghi gần nhất ({@code hydro_latest.last_seen_at}). ⛔ Không có cột trạng thái nào — lý do đầy
 * đủ nằm ở {@link StationDisplayStatus}, và nó là lý do <i>duy nhất</i> khiến hệ này nói đúng khi
 * poller chết: không ai ghi trạng thái thì cũng không có trạng thái cũ để tin nhầm.
 *
 * <p>⚠ {@code lastSeenAt} cố ý là {@code last_seen_at} (<b>bất kể chất lượng</b>), ⛔ không phải
 * {@code valid_measured_at}: câu hỏi ở đây là <i>"trạm còn phát tín hiệu không"</i>. Một trạm chỉ
 * gửi về số nghi ngờ <b>vẫn đang phát</b>, và báo nó "mất tín hiệu" là huy động sai người.
 *
 * @param lastSeenAt {@code null} = <b>chưa từng</b> có bản ghi nào — khác hẳn "im lặng đã lâu"
 */
public record TinHieuDiemDo(Long stationId, String code, String name, boolean active, Instant lastSeenAt) {

    public TinHieuDiemDo {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(code, "code");
    }

    /** Trạng thái hiển thị suy ra tại thời điểm {@code now} — vế đọc của {@link StationDisplayStatus#suyRa}. */
    public StationDisplayStatus trangThai(Instant now, Duration khungNguon, int soKhungMatTinHieu) {
        return StationDisplayStatus.suyRa(active, lastSeenAt, now, khungNguon, soKhungMatTinHieu);
    }
}
