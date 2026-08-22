package com.songnhue.app.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyChannel;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;

/**
 * Cửa vào thông báo dành cho module nghiệp vụ — <b>trả nợ #67</b>.
 *
 * <h2>Vì sao bài kiểm này tồn tại, và vì sao nó phải tiêm {@link NotificationPort} chứ không phải
 * {@code NotificationService}</h2>
 *
 * Cơ chế thông báo dựng ở WS-6 và tới WS-16 vẫn <b>chưa có một bài kiểm tích hợp nào</b>. Nó vẫn
 * "chạy" trong suốt thời gian đó, nhưng chỉ qua một đường duy nhất: {@code WorkflowEngine} nằm trong
 * {@code core} nên nó gọi thẳng {@code NotificationService.notify(NotificationRequest)}. Cửa dành
 * cho module nghiệp vụ — {@link NotificationPort} — thì <b>chưa ai đi qua</b>, vì bốn module nghiệp vụ
 * chưa có nhu cầu gửi thông báo trước WS-17.
 *
 * <p>Và đúng cửa đó đã hỏng, im lặng, từ WS-12 tới WS-16: một khối tài liệu chèn vào giữa
 * {@code @Transactional(readOnly = true)} và hàm nó thuộc về làm chú thích rơi nhầm sang
 * {@code notify(NotifyRequest)}. Hệ quả là mọi lượt ghi của một thông báo chạy trong giao dịch chỉ
 * đọc — Hibernate chuyển sang flush thủ công và <b>không dòng nào xuống được CSDL, không lỗi nào nổi
 * lên</b> ({@code architecture-review.md} §10.21).
 *
 * <p>Luật ArchUnit {@code KHONG_TU_GOI_HAM_TRANSACTIONAL} lôi lỗi đó ra và bản sửa là đúng theo suy
 * luận. Nhưng bài học lặp đi lặp lại của dự án này là <b>suy luận đúng chưa phải bằng chứng</b> — một
 * bản sửa không có ai đi qua thì đứng đúng chỗ bản lỗi vừa đứng. Vì vậy phải tiêm
 * {@code NotificationPort}: tiêm lớp cài đặt là gọi đúng phương thức mà production <i>không</i> gọi, tức là
 * kiểm một đường khác — chính xác cái sai của bài kiểm bộ đếm lượt xem ở WS-16.
 */
class NotificationPortTest extends IntegrationTestBase {

    /** ⚠ Kiểu là INTERFACE của SPI, cố ý. Đây là thứ module nghiệp vụ nhìn thấy và gọi. */
    @Autowired
    private NotificationPort notifications;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String EVENT = "TEST_SPI_NOTIFY";

    /** Người nhận do bài kiểm tự tạo — không mượn tài khoản seed, xem {@link #taoNguoiNhan}. */
    private List<Long> nguoiNhan;

    @BeforeEach
    void chuanBi() {
        donDep();
        nguoiNhan = List.of(taoNguoiNhan("nhan-thu-1"), taoNguoiNhan("nhan-thu-2"));
    }

    @AfterEach
    void donDep() {
        jdbc.update(
                "DELETE FROM notification_recipients WHERE notification_id IN "
                        + "(SELECT id FROM notifications WHERE event_type = ?)",
                EVENT);
        jdbc.update("DELETE FROM notifications WHERE event_type = ?", EVENT);
        jdbc.update("DELETE FROM jobs WHERE job_type = 'NOTIFICATION_DISPATCH'");
        jdbc.update("DELETE FROM users WHERE username LIKE 'nhan-thu-%'");
    }

    @Test
    @DisplayName("⭐⭐ Module nghiệp vụ gọi NotificationPort.broadcast → cả ba vế đều xuống CSDL")
    void moduleNghiepVuGuiDuocThongBao() {
        notifications.broadcast(yeuCau(), nguoiNhan);

        Long idThongBao = jdbc.queryForObject("SELECT id FROM notifications WHERE event_type = ?", Long.class, EVENT);
        assertThat(idThongBao)
                .as("dòng ở `notifications` — vế đầu tiên, và là vế duy nhất mà lỗi readOnly cũ để lại dấu vết")
                .isNotNull();

        // 2 người × 2 kênh (IN_APP + EMAIL) = 4 dòng.
        Integer soDongNhan = jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients WHERE notification_id = ?", Integer.class, idThongBao);
        assertThat(soDongNhan)
                .as("⚠ Đây mới là phép khẳng định quan trọng: một thông báo không người nhận thì không "
                        + "ai đọc được, mà bảng `notifications` vẫn có dòng nên nhìn từ giao diện quản trị "
                        + "vẫn thấy 'đã gửi'")
                .isEqualTo(4);

        Integer soDongTrongUngDung = jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients WHERE notification_id = ? AND channel = 'IN_APP' "
                        + "AND sent_at IS NOT NULL",
                Integer.class,
                idThongBao);
        assertThat(soDongTrongUngDung)
                .as("dòng IN_APP CHÍNH LÀ thông báo, nên nó phải được đánh dấu đã gửi ngay tại đây")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⚠⚠ NotificationPort.notify — đúng hàm từng chạy trong giao dịch readOnly")
    void cuaVaoNotifyGhiDuocXuongCsdl() {
        // ⚠ Bài kiểm này KHÔNG thay thế được bài broadcast ở trên, và ngược lại. Lỗi readOnly chỉ
        // rơi vào `notify`; `broadcast` khi đó không mang chú thích nào nên nó ghi được (chỉ là ghi
        // bằng ba giao dịch rời rạc). Kiểm nhầm hàm là kiểm một đường không hỏng — đúng cái sai của
        // bài kiểm bộ đếm lượt xem. Kiểm chứng ngược đã chạy: đặt lại
        // `@Transactional(readOnly = true)` lên `notify(NotifyRequest)` thì bài này đỏ, bài kia xanh.
        notifications.notify(yeuCauGuiRieng(nguoiNhan));

        Long idThongBao = jdbc.queryForObject("SELECT id FROM notifications WHERE event_type = ?", Long.class, EVENT);
        assertThat(idThongBao).isNotNull();

        Integer soDongNhan = jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients WHERE notification_id = ?", Integer.class, idThongBao);
        assertThat(soDongNhan)
                .as("2 người × 2 kênh — người nhận chỉ định thẳng qua `extraUserIds` để không phụ "
                        + "thuộc vào cơ cấu tổ chức của môi trường kiểm thử")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Lượt gửi đặt luôn việc gửi email — ba bảng trong cùng một giao dịch")
    void hangDoiGuiEmailCungDuocDatViec() {
        notifications.broadcast(yeuCau(), nguoiNhan);

        Integer soViec = jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE job_type = 'NOTIFICATION_DISPATCH' AND status IN ('PENDING','RUNNING')",
                Integer.class);
        assertThat(soViec)
                .as("kênh EMAIL bật sẵn theo seed, nên phải có việc gửi nằm trong hàng đợi; thiếu nó thì "
                        + "thông báo chỉ hiện trong ứng dụng và không ai nhận được thư")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------

    private NotifyRequest yeuCau() {
        return yeuCau(List.of());
    }

    /** Bản chỉ đích danh người nhận — {@code notify} tự phân giải người nhận, {@code broadcast} thì không. */
    private NotifyRequest yeuCauGuiRieng(List<Long> userIds) {
        return yeuCau(userIds);
    }

    private NotifyRequest yeuCau(List<Long> extraUserIds) {
        return new NotifyRequest(
                EVENT,
                "Thông báo thử từ module nghiệp vụ",
                "Nội dung kiểm chứng cửa vào SPI.",
                NotifySeverity.INFO,
                null,
                "TEST",
                1L,
                List.of(),
                extraUserIds,
                null,
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL));
    }

    /**
     * Tự tạo người nhận thay vì lấy tài khoản có sẵn.
     *
     * <p>CSDL kiểm thử chỉ seed đúng {@code superadmin}, nên một bài kiểm đòi "hai tài khoản đang
     * hoạt động" sẽ xanh hay đỏ tuỳ vào bài kiểm nào chạy trước và tạo thêm tài khoản — đúng công
     * thức của loại đỏ-thỉnh-thoảng.
     */
    private Long taoNguoiNhan(String username) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (username, full_name, password_hash, org_unit_id, status, must_change_password)
                VALUES (?, ?, 'x', (SELECT min(id) FROM org_units), 'ACTIVE', false)
                RETURNING id
                """,
                Long.class,
                username,
                "Người nhận thử " + username);
    }
}
