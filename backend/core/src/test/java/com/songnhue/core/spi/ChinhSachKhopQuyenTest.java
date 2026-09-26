package com.songnhue.core.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.application.notification.NotificationRequest;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;

/**
 * Nửa thứ hai của {@code T85.4}: một {@code enum} ép nơi gọi <b>khai ra</b> chính sách, nhưng tự nó
 * ⛔ ngăn được một lời khai <b>⛔ khớp với dữ liệu đi kèm</b>.
 *
 * <h2>Hai trạng thái mâu thuẫn, cả hai hỏng trong IM LẶNG</h2>
 *
 * <ul>
 *   <li>{@link ChinhSachNguoiNhan#THEO_QUYEN} mà {@code targetPermission} rỗng ⇒ tập suy ra RỖNG ⇒
 *       thông báo tới <b>0 người</b>, để lại đúng một dòng {@code log.warn} mà ⛔ ai đọc. Đây đúng
 *       hình dạng đã cho <i>9 ngày / 3323 lượt hỏng / 0 byte</i> trôi qua ở T50.4 — một con số trong
 *       log ⛔ phải một cái chuông.
 *   <li>{@link ChinhSachNguoiNhan#DICH_DANH} mà LẠI có quyền ⇒ nơi gọi tưởng mình đã nhắm đích trong
 *       khi tập người nhận nở ra theo quyền.
 * </ul>
 *
 * <p>⇒ Ném <b>ngay tại nơi dựng</b>. Chỗ đúng để nói một lời khai mâu thuẫn là chỗ viết nó ra, ⛔
 * phải ba lớp sau đó ở một dòng log.
 *
 * <h2>⚠ Vì sao bài (3) phải có</h2>
 *
 * <p>Hai bài đầu xanh trọn vẹn với một hàm dựng ném <b>mọi lúc</b> — và khi ấy ⛔ lượt gửi thông báo
 * nào của hệ chạy được. Bài (3) là vế phân biệt (luật 9) kiêm vế chống tập rỗng (luật 29): năm
 * factory THẬT phải đi qua được, và nó đồng thời là phép đo <i>cả 5 ca đang dùng đều khớp</i> — tức
 * bản vá này ⛔ đổi hành vi của lượt gửi nào đang chạy.
 *
 * <h2>⚠ Vì sao kiểm CẢ HAI record</h2>
 *
 * <p>Luật nằm một chỗ ({@link ChinhSachNguoiNhan#kiemKhopVoiQuyen}) nhưng <b>lời gọi</b> nó thì có
 * hai — mỗi record một. Quên một bên là đúng thứ luật 14 gọi tên, và cái xanh của bên còn lại đọc
 * như bảo đảm cho cả hai (luật 28).
 */
class ChinhSachKhopQuyenTest {

    private static final String QUYEN = "adm:notification:broadcast";

    @Test
    @DisplayName("⛔ (1) Khai THEO_QUYEN mà ⛔ có quyền ⇒ ném ngay — ⛔ để nó thành 'thông báo tới 0 người'")
    void theoQuyenMaThieuQuyenThiNem() {
        assertThatThrownBy(() -> yeuCauSpi(null, ChinhSachNguoiNhan.THEO_QUYEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("THEO_QUYEN")
                .hasMessageContaining("0 người");

        assertThatThrownBy(() -> yeuCauApp("   ", ChinhSachNguoiNhan.THEO_QUYEN_TRONG_PHAM_VI))
                .as("⚠ Chuỗi TRẮNG phải bị bắt y như `null`: `RecipientResolver` coi nó là ⛔ có quyền "
                        + "(`isBlank`), nên một khoảng trắng lọt vào cấu hình cho ra đúng lỗi im lặng ấy.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⛔ (2) Khai DICH_DANH mà LẠI có quyền ⇒ ném — nơi gọi tưởng đã nhắm đích, tập lại nở ra")
    void dichDanhMaCoQuyenThiNem() {
        assertThatThrownBy(() -> yeuCauSpi(QUYEN, ChinhSachNguoiNhan.DICH_DANH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DICH_DANH")
                .hasMessageContaining(QUYEN);

        assertThatThrownBy(() -> yeuCauApp(QUYEN, ChinhSachNguoiNhan.NHOM_CANH_BAO))
                .as("⚠ NHOM_CANH_BAO cũng ⛔ đi với quyền: luật G11 là phép ĐOÁN, còn một mã quyền là "
                        + "một lời khai chính xác — trộn hai thứ là dựng lại đúng T74.7.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⚠ VẾ PHÂN BIỆT (luật 9 · 29) — cả năm factory THẬT phải đi qua được")
    void namFactoryThatDeuQuaDuoc() {
        assertThatCode(() -> {
                    NotifyRequest.alert("E", "t", "b", NotifySeverity.INFO, List.of(1L));
                    NotifyRequest.targeted("E", "t", "b", NotifySeverity.INFO, QUYEN, List.of(2L));
                    NotifyRequest.chiNhungNguoiNay("E", "t", "b", NotifySeverity.INFO, List.of(2L));
                    NotifyRequest.targetedWithUnits("E", "t", "b", NotifySeverity.INFO, QUYEN, List.of(1L), List.of());
                    NotifyRequest.targetedInUnitScope(
                            "E", "t", "b", NotifySeverity.INFO, QUYEN, List.of(1L), List.of());
                    NotificationRequest.alert("E", "t", "b", NotificationSeverity.INFO, List.of(1L));
                })
                .as("⛔ Hai bài trên xanh trọn vẹn với một hàm dựng ném MỌI LÚC — và khi ấy ⛔ lượt gửi "
                        + "thông báo nào của hệ chạy được. Vế này cũng là phép đo *cả 5 ca đang dùng đều "
                        + "khớp*, tức bản vá ⛔ đổi hành vi lượt gửi nào đang chạy.")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⛔ `chinhSach` ⛔ được null — người nhận phải là một lời khai, ⛔ phải một ô bỏ trống")
    void chinhSachKhongDuocNull() {
        assertThatThrownBy(() -> yeuCauSpi(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chinhSach");

        assertThat(ChinhSachNguoiNhan.values())
                .as("⚠ Vế chống tập rỗng: bốn ca của `RecipientResolver` phải còn đủ. Gỡ bớt một hằng là "
                        + "gỡ một nhánh `switch`, và trình biên dịch sẽ nói — nhưng chỉ khi còn ai đếm.")
                .hasSize(4);
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private static NotifyRequest yeuCauSpi(String quyen, ChinhSachNguoiNhan chinhSach) {
        return new NotifyRequest(
                "E",
                "t",
                "b",
                NotifySeverity.INFO,
                null,
                null,
                null,
                List.of(),
                List.of(),
                quyen,
                List.of(NotifyChannel.IN_APP),
                chinhSach);
    }

    private static NotificationRequest yeuCauApp(String quyen, ChinhSachNguoiNhan chinhSach) {
        return new NotificationRequest(
                "E",
                "t",
                "b",
                NotificationSeverity.INFO,
                null,
                null,
                null,
                List.of(),
                List.of(),
                quyen,
                List.of(NotificationChannel.IN_APP),
                chinhSach);
    }
}
