package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.exception.AppException;

/**
 * Bất biến của hai kiểu chẩn đoán — T31.13.
 *
 * <p>Hai thứ được canh ở đây đều là <b>bảo đảm ở hàm dựng</b>, ⛔ không phải lời dặn nơi gọi: một
 * câu {@code GROUP BY} trên bảng rỗng trả về không dòng nào, nên "bản đồ thiếu khoá" là kết quả
 * <i>mặc định</i> của đường đi tự nhiên nhất — và thứ gì là mặc định thì phải bị chặn ở chỗ hẹp
 * nhất (quy tắc 16).
 */
class ChanDoanHopDongTest {

    private static final Instant MOC = Instant.parse("2026-09-02T00:00:00Z");

    private static TongHopDongBo tongHop(Map<SyncStatus, Long> trangThai, Map<SyncFailureKind, Long> loi) {
        return new TongHopDongBo(MOC, 0L, trangThai, loi, null);
    }

    @Test
    @DisplayName("⭐⭐ Bản đồ thiếu khoá được BÙ về 0 — “0 lượt NOT_WORKING” là một câu khẳng định")
    void banDoThieuKhoaDuocBuVe0() {
        TongHopDongBo t = tongHop(Map.of(SyncStatus.SUCCESS, 5L), Map.of());

        assertThat(t.theoTrangThai())
                .as("bốn kết cục phải có mặt đủ: thiếu khoá thì màn hình chỉ còn im lặng, mà im lặng "
                        + "đọc giống hệt “chưa ai đo” (quy tắc 16)")
                .hasSize(SyncStatus.values().length)
                .containsEntry(SyncStatus.SUCCESS, 5L)
                .containsEntry(SyncStatus.FAILED, 0L)
                .containsEntry(SyncStatus.PARTIAL, 0L)
                .containsEntry(SyncStatus.SKIPPED_UP_TO_DATE, 0L);
        assertThat(t.theoLoi())
                .as("⭐ khẳng định về SỐ LƯỢNG, không chia sẻ giả định nào với danh sách tên ở trên "
                        + "(luật 29) — thêm một giá trị vào enum mà quên bù khoá là đỏ ngay")
                .hasSize(SyncFailureKind.values().length)
                .containsValue(0L);
    }

    @Test
    @DisplayName("Bản đồ rỗng hoàn toàn vẫn ra đủ khoá — đây chính là trạng thái của một bảng RỖNG")
    void banDoRongVanRaDuKhoa() {
        TongHopDongBo t = tongHop(new EnumMap<>(SyncStatus.class), new EnumMap<>(SyncFailureKind.class));

        assertThat(t.theoTrangThai().values()).allMatch(so -> so == 0L);
        assertThat(t.theoTrangThai()).hasSize(SyncStatus.values().length);
        assertThat(t.theoLoi()).hasSize(SyncFailureKind.values().length);
    }

    @Test
    @DisplayName("⛔ Bản đồ mang khoá null (khoá lạ) bị TỪ CHỐI — ⛔ không bỏ qua trong im lặng")
    void khoaLaBiTuChoi() {
        Map<SyncStatus, Long> lech = new HashMap<>();
        lech.put(null, 3L);

        assertThatThrownBy(() -> tongHop(lech, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("theoTrangThai");
    }

    @Test
    @DisplayName("⭐⭐ soLuotGoiHong ⛔ KHÔNG đếm THIEU_MA_SO — nó là trạng thái cấu hình, chưa hề gọi lần nào")
    void soLuotGoiHongKhongDemLuotChuaGoi() {
        Map<SyncFailureKind, Long> loi = new EnumMap<>(SyncFailureKind.class);
        for (SyncFailureKind k : SyncFailureKind.values()) {
            loi.put(k, 10L);
        }

        TongHopDongBo t = tongHop(Map.of(), loi);

        long soChuaGoi = java.util.Arrays.stream(SyncFailureKind.values())
                .filter(k -> !k.duocGhiVaoRawLog())
                .count();
        assertThat(soChuaGoi)
                .as("⭐ Vế chống tập rỗng: nếu KHÔNG giá trị nào là “chưa gọi” thì khẳng định dưới xanh "
                        + "một cách vô nghĩa — hai vế trở nên bằng nhau (luật 7)")
                .isEqualTo(1);
        assertThat(t.soLuotGoiHong())
                .as("máy chủ tính con số này để giao diện ⛔ không cộng lại — cộng lại ở giao diện là "
                        + "nơi thứ tư giữ cùng một luật, và là nơi duy nhất không bài kiểm nào canh")
                .isEqualTo(10L * (SyncFailureKind.values().length - soChuaGoi));
    }

    @Test
    @DisplayName("⭐ Bộ lọc: mốc bắt đầu ≥ mốc kết thúc bị từ chối KÈM TÊN TRƯỜNG — ⛔ không phải toast chung")
    void bolocMocNguocBiTuChoi() {
        Instant sau = MOC.plusSeconds(1);

        assertThatThrownBy(() -> new BoLocNhatKy(null, null, null, sau, MOC, false))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).details())
                        .as("F1 của lượt rà: sáu hàm ném SYS-0003 KHÔNG kèm tên trường nên giao diện chỉ "
                                + "hiện toast chung. Chỉ đúng ô sai còn hữu ích hơn mọi câu hướng dẫn")
                        .singleElement()
                        .extracting(v -> v.field() + "/" + v.rule())
                        .isEqualTo("tu/TRUOC_MOC_KET_THUC"));
    }

    @Test
    @DisplayName("⚠ Hai mốc BẰNG NHAU cũng bị từ chối — nửa khoảng mở nên khoảng ấy chắc chắn rỗng")
    void haiMocBangNhauCungBiTuChoi() {
        assertThatThrownBy(() -> new BoLocNhatKy(null, null, null, MOC, MOC, false))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Thiếu một trong hai mốc, hoặc không mốc nào — hợp lệ")
    void thieuMocThiKhongKiem() {
        assertThatCode(() -> new BoLocNhatKy(null, null, null, MOC, null, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> new BoLocNhatKy(null, null, null, null, MOC, false))
                .doesNotThrowAnyException();
        assertThatCode(BoLocNhatKy::khongLoc).doesNotThrowAnyException();
        assertThat(BoLocNhatKy.khongLoc().chiHong()).isFalse();
    }
}
