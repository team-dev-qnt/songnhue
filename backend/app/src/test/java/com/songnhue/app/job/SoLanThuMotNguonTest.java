package com.songnhue.app.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.CmsJobTypes;
import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.domain.job.Job;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.JobRequest;

/**
 * {@code T68.30} — vế HÀNH VI: con số handler khai phải <b>đi tới cột {@code jobs.max_attempts}</b>.
 *
 * <h2>Vì sao cần cả bài này lẫn bộ canh bytecode</h2>
 *
 * <p>{@code SoLanThuCoNguoiDocRuleTest} trả lời <i>"có ai GỌI {@code maxAttempts()} ⛔"</i> — cần
 * thiết nhưng ⛔ đủ: một lời gọi vẫn có thể lấy số rồi <b>vứt đi</b>. Bài này trả lời câu còn lại,
 * <i>"con số ấy có tới được chỗ quyết định thử lại ⛔"</i>, và chỗ ấy là một <b>cột trong CSDL</b>:
 * {@code JobRepository} quyết định thử lại bằng SQL {@code attempts < max_attempts}. ⇒ Khẳng định
 * đọc thẳng cột ấy, ⛔ hỏi lại đối tượng Java vừa ghi nó.
 *
 * <h2>⚠ Vì sao chọn {@code DB_RESTORE} làm ca chính</h2>
 *
 * <p>Vì nó là ca mà sai số phải trả giá đắt nhất: {@code RestoreJobHandler} khai <b>1</b> kèm câu
 * <i>"tuyệt đối ⛔ thử lại"</i>, và một lượt thử thứ hai là một lượt <b>ghi đè toàn bộ CSDL</b> lần
 * nữa. Mặc định SPI là <b>3</b> ⇒ hai trạng thái <i>có đọc handler</i> và <i>⛔ đọc</i> cho hai con
 * số khác nhau — đúng vế phân biệt mà luật 9 đòi.
 */
class SoLanThuMotNguonTest extends IntegrationTestBase {

    /** ⚠ Tiền tố riêng để mẫu dọn ⛔ chạm được hàng THẬT — bài học T74.1. */
    private static final String TIEN_TO = "T68.30-test:";

    @Autowired
    private JobService viec;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM jobs WHERE dedup_key LIKE ?", TIEN_TO + "%");
    }

    @Test
    @DisplayName("⛔ Nơi đặt việc ⛔ khai số ⇒ cột max_attempts mang số của HANDLER, ⛔ phải mặc định SPI")
    void khongKhaiThiLayTuHandler() {
        short macDinhSpi = JobHandler.MAC_DINH_SO_LAN_THU;
        String khoaKhoiPhuc = TIEN_TO + "restore";
        viec.enqueue(JobTypes.DB_RESTORE, "{}", khoaKhoiPhuc);

        assertThat(cotMaxAttempts(khoaKhoiPhuc))
                .as(
                        """
                        ⛔⛔ `RestoreJobHandler` khai 1 kèm câu "tuyệt đối ⛔ thử lại" — lượt thử thứ hai là một
                        lượt GHI ĐÈ TOÀN BỘ CSDL lần nữa. Nếu ô này ra %d thì `JobService` ⛔ hề hỏi handler và
                        lời khai kia lại thành một công tắc chết (T68.30)."""
                                .formatted(macDinhSpi))
                .isEqualTo((short) 1);

        assertThat(macDinhSpi)
                .as("⚠ VẾ PHÂN BIỆT (luật 9): nếu mặc định SPI TRÙNG con số handler khai thì khẳng định trên "
                        + "xanh ở CẢ HAI trạng thái — đúng cái bẫy đã để 4 cặp (2=2 · 1=1 · 1=1 · 1=1) trùng "
                        + "nhau nhờ may suốt và ⛔ ai phát hiện.")
                .isNotEqualTo((short) 1);
    }

    @Test
    @DisplayName("⚠ VẾ CHỐNG HẰNG SỐ — hai loại việc khác nhau phải ra hai con số của CHÍNH chúng")
    void moiLoaiViecLayDungSoCuaNo() {
        String khoaCong = TIEN_TO + "revalidate";
        viec.enqueue(JobRequest.theoHandler(CmsJobTypes.PORTAL_REVALIDATE, "{\"path\":\"/\"}", khoaCong));

        assertThat(cotMaxAttempts(khoaCong))
                .as("`PortalRevalidateHandler` khai 5. Cùng với ca DB_RESTORE ra 1 ở bài trên, hai con số khác "
                        + "nhau chứng minh phép tra đi theo `jobType` — một bản vá trả HẰNG SỐ sẽ đỏ ở đúng "
                        + "một trong hai bài.")
                .isEqualTo((short) 5);
    }

    @Test
    @DisplayName("⛔ Nơi đặt việc KHAI số thì số ấy THẮNG — lượt hâm nóng cổng đứng trên đúng vế này")
    void noiDatViecKhaiThiThang() {
        String khoa = TIEN_TO + "ghi-de";
        viec.enqueue(JobTypes.DB_RESTORE, "{}", khoa, (short) 9);

        assertThat(cotMaxAttempts(khoa))
                .as("`PortalCache.warmUp` cần 10 lượt trong khi handler khai 5. Mất vế này thì bản vá T68.30 "
                        + "lặng lẽ hạ lượt hâm nóng cổng xuống 5 — tức nó ĐÓNG dòng nợ bằng cách gây ra đúng "
                        + "loại hại mà dòng nợ nói tới (cùng hình dạng T82.5).")
                .isEqualTo((short) 9);
    }

    @Test
    @DisplayName("⛔ Số lần thử ⛔ được ≤ 0 — một việc ⛔ bao giờ chạy là trạng thái ⛔ ai đọc ra")
    void soLanThuKhongDuocBangKhongHoacAm() {
        assertThatThrownBy(() -> new JobRequest("X", "{}", null, (short) 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("theoHandler");

        assertThatThrownBy(() -> new JobRequest("X", "{}", null, (short) -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(JobRequest.theoHandler("X", "{}", null).maxAttempts())
                .as("⚠ Vế phân biệt: `null` là lời khai *theo handler*, ⛔ phải một giá trị hỏng. Nếu hàm "
                        + "dựng ném MỌI LÚC thì hai khẳng định trên xanh mà ⛔ nói gì (luật 9).")
                .isNull();
    }

    @Test
    @DisplayName("⚠ LUẬT 14 — mặc định của SPI và của entity phải bằng nhau, ⛔ hai lời khai rời nhau")
    void macDinhCuaSpiVaCuaEntityPhaiBangNhau() {
        assertThat(new Job("X", "{}").getMaxAttempts())
                .as("`Job.maxAttempts` khởi tạo ở tầng entity là lưới an toàn cho hàng ghi thẳng; lệch với "
                        + "`JobHandler.MAC_DINH_SO_LAN_THU` thì cùng một việc nhận hai con số tuỳ đường đi.")
                .isEqualTo(JobHandler.MAC_DINH_SO_LAN_THU);
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private Short cotMaxAttempts(String dedupKey) {
        return jdbc.queryForObject("SELECT max_attempts FROM jobs WHERE dedup_key = ?", Short.class, dedupKey);
    }
}
