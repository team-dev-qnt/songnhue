package com.songnhue.hydro.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng {@code sync_logs} đã đọc lên — nguồn của màn hình <i>Nhật ký đồng bộ</i> (M3.16, T31.13).
 *
 * <h2>⭐ Vì sao đây là màn hình đáng dựng nhất của WS-31</h2>
 *
 * <p>Tới trước T31.13, mọi thứ poller ghi xuống chỉ tra được <b>bằng SQL</b> — runbook
 * {@code poller-chet.md} §2 có sẵn câu truy vấn, và một câu truy vấn trong tài liệu là thứ chỉ người
 * viết nó gõ lại được lúc 2 giờ sáng. Bảng {@code sync_logs} sinh ra để trả lời đúng một câu hỏi vận
 * hành: <b>lượt vừa rồi không ghi được gì — vì nguồn hỏng, vì đã đủ dữ liệu, hay vì ta chưa kịp
 * gọi?</b> Ba câu trả lời ấy đòi ba việc phải làm ngược nhau (§10.68-B).
 *
 * <h2>⚠ Bốn bộ đếm đi RIÊNG, ⛔ không cộng lại thành một</h2>
 *
 * <p>{@code soGhiMoi = 0} là kết cục <b>bình thường của 4/5 lượt chạy</b>: poller gọi 2 phút một lần
 * trên một nguồn cập nhật 10 phút một lần, nên phần lớn lượt gọi mang về đúng dữ liệu của lượt
 * trước. Gộp bốn con số thành một cột "kết quả" là biến trạng thái bình thường nhất của hệ thống
 * thành một dòng trông như lỗi — và một màn hình đỏ vì lý do ai cũng biết là một màn hình sẽ không
 * còn ai đọc (§10.42).
 *
 * <p>⛔ <b>Không có trường nào mang thân phản hồi.</b> Cùng lý do với {@code KetQuaDongBo}: thân thật
 * của {@code bhh40} chứa chính mã số trong {@code <form action="…?key=…%3b">} (đo 01/09/2026). Nguyên
 * văn nằm ở {@code hydro_raw_logs} — nơi có phân quyền, có hạn lưu và có bộ che. {@link #rawLogId}
 * chỉ là con trỏ tới đó.
 *
 * @param loi {@code null} khi lượt chạy không hỏng — quy ước <b>NULL = thành công</b> dùng chung với
 *     {@code hydro_raw_logs}; xem {@link SyncFailureKind}
 * @param rawLogId {@code null} nghĩa là <b>chưa hề mở kết nối</b> ({@link SyncFailureKind#THIEU_MA_SO}
 *     hoặc lượt bỏ qua vì đã đủ dữ liệu), ⛔ không phải "ghi hỏng"
 */
public record LuotDongBo(
        long id,
        UUID nguonPublicId,
        String nguonCode,
        String nguonName,
        Instant batDau,
        Instant ketThuc,
        Integer durationMs,
        Instant khungNhamToi,
        SyncStatus trangThai,
        SyncFailureKind loi,
        String lyDo,
        int soNhan,
        int soGhiMoi,
        int soTrungBoQua,
        int soMaLa,
        Long rawLogId) {}
