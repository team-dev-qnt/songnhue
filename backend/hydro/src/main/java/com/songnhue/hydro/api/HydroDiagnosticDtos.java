package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.songnhue.hydro.domain.LuotDongBo;
import com.songnhue.hydro.domain.MaLaTongHop;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;
import com.songnhue.hydro.domain.TongHopDongBo;

/** Kiểu dữ liệu ra của hai màn hình chẩn đoán MOD-03 — M3.16 và <i>Mã lạ từ nguồn</i> (T31.13). */
public final class HydroDiagnosticDtos {

    private HydroDiagnosticDtos() {}

    /**
     * Một lượt polling — xem {@link LuotDongBo} để biết vì sao bốn bộ đếm đi riêng.
     *
     * <p>⛔ Không có trường nào mang thân phản hồi của nguồn: thân thật của {@code bhh40} chứa chính
     * mã số. {@link #rawLogId} là con trỏ tới {@code hydro_raw_logs}, ⛔ không phải nội dung.
     */
    public record SyncLogView(
            long id,
            UUID nguonId,
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
            Long rawLogId) {

        public static SyncLogView cua(LuotDongBo l) {
            return new SyncLogView(
                    l.id(),
                    l.nguonPublicId(),
                    l.nguonCode(),
                    l.nguonName(),
                    l.batDau(),
                    l.ketThuc(),
                    l.durationMs(),
                    l.khungNhamToi(),
                    l.trangThai(),
                    l.loi(),
                    l.lyDo(),
                    l.soNhan(),
                    l.soGhiMoi(),
                    l.soTrungBoQua(),
                    l.soMaLa(),
                    l.rawLogId());
        }
    }

    /**
     * Dải tóm tắt sức khoẻ đường ingest.
     *
     * <p>⭐ {@link #soLuotGoiHong} tính ở <b>máy chủ</b>, ⛔ không để giao diện cộng lại: luật "lượt
     * gọi đã thật sự xảy ra chưa" nằm ở {@code SyncFailureKind.duocGhiVaoRawLog()} và nó đã có ba nơi
     * dùng. Cộng lại ở giao diện là nơi thứ tư — và là nơi duy nhất không có bài kiểm nào canh.
     *
     * @param soGio cửa sổ <b>đã kẹp</b>, để nhãn trên màn hình nói đúng số giờ thật sự được đo chứ
     *     không nói lại con số người dùng gửi lên
     */
    public record SyncSummaryView(
            Instant tuMoc,
            int soGio,
            long soLuot,
            Map<SyncStatus, Long> theoTrangThai,
            Map<SyncFailureKind, Long> theoLoi,
            long soLuotGoiHong,
            Instant mocGanNhat) {

        public static SyncSummaryView cua(TongHopDongBo t, int soGio) {
            return new SyncSummaryView(
                    t.tuMoc(), soGio, t.soLuot(), t.theoTrangThai(), t.theoLoi(), t.soLuotGoiHong(), t.mocGanNhat());
        }
    }

    /**
     * Một mã nguồn chưa khai — xem {@link MaLaTongHop}.
     *
     * <p>⚠⚠ {@link #giaTriGanNhat} là số <b>nguyên văn nguồn, chưa quy đổi</b>, và
     * {@link #donViNguon} là đơn vị nguồn khai. Giao diện <b>bắt buộc</b> hiện hai thứ cạnh nhau:
     * {@code 213} không kèm {@code cm} sẽ được đọc thành <i>213 mét</i>.
     *
     * <p>{@code @JsonFormat(STRING)}: {@code 2.30} tuần tự hoá thành số sẽ về {@code 2.3} và mất chữ
     * số cuối — đúng lỗi T28.27 đã vá ở đường công khai. Khai kiểu ở giao diện là một <i>lời khẳng
     * định</i>; annotation này là thứ làm cho nó đúng.
     */
    public record MaLaView(
            String apiCode,
            UUID nguonId,
            String nguonCode,
            long soBanGhi,
            Instant lanDau,
            Instant lanGanNhat,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTriGanNhat,
            String donViNguon,
            boolean daKhaiThanhDiemDo,
            String maDiemDo) {

        public static MaLaView cua(MaLaTongHop m) {
            return new MaLaView(
                    m.apiCode(),
                    m.nguonPublicId(),
                    m.nguonCode(),
                    m.soBanGhi(),
                    m.lanDau(),
                    m.lanGanNhat(),
                    m.giaTriGanNhat(),
                    m.donViNguon(),
                    m.daKhaiThanhDiemDo(),
                    m.maDiemDo());
        }
    }
}
