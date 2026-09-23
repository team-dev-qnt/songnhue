package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

/**
 * {@code T68.30} — bộ canh <b>duy nhất</b> giữ cho {@code JobHandler.maxAttempts()} ⛔ quay lại làm
 * một công tắc chết.
 *
 * <h2>Vì sao phải là bytecode chứ ⛔ phải một bài hành vi</h2>
 *
 * <p>Một bài hành vi chỉ khẳng định được <i>hôm nay con số ra đúng</i>. Nó ⛔ phân biệt nổi hai
 * trạng thái đã sống cạnh nhau suốt: <b>(a)</b> {@code JobService} tra handler rồi lấy số, và
 * <b>(b)</b> ⛔ ai tra gì cả mà con số trùng nhau <i>nhờ may</i> vì nơi đặt việc gõ tay đúng con số
 * handler khai. Đo 23/09 trên {@code origin/dev}: 4 cặp trùng nhau kiểu (b) —
 * {@code ContactSlaHandler} 2=2 · {@code AuditArchiveHandler} 1=1 · {@code RestoreJobHandler} 1=1 ·
 * {@code BackupJobHandler} 1=1 — nên một bài hành vi viết hôm ấy sẽ <b>XANH TRỌN VẸN</b> trên một
 * cơ chế có <b>0 người đọc</b>. Đó đúng là luật 9.
 *
 * <p>Thứ phân biệt được hai trạng thái ấy là câu hỏi <i>có ai GỌI phương thức này ⛔</i>, và câu ấy
 * chỉ đọc được ở tầng bytecode.
 *
 * <h2>⚠ Vì sao phải loại chính các handler ra khỏi tập người gọi</h2>
 *
 * <p>{@code QuetLaiTepService} và {@code MaHoaLaiService} <b>đã</b> gọi {@code maxAttempts()} từ
 * trước — nhưng là gọi <i>chính mình</i> lúc tự đặt việc. Một lời gọi như thế ⛔ làm cho SPI sống:
 * năm lớp còn lại vẫn khai một con số ⛔ điều khiển gì. Nếu đếm cả chúng thì bộ canh này XANH ngay
 * hôm nay, tức xanh trong đúng tình huống nó sinh ra để bắt (luật 7).
 *
 * <p>⇒ Chỉ đếm lời gọi tới <b>phương thức của INTERFACE</b> phát ra từ một lớp <b>⛔ phải</b>
 * {@code JobHandler}. Trước bản vá: <b>0</b> ⇒ đỏ. Sau: {@code JobService.enqueue} ⇒ xanh.
 */
class SoLanThuCoNguoiDocRuleTest {

    private static final String JOB_HANDLER = "com.songnhue.core.spi.JobHandler";
    private static final String TEN_PHUONG_THUC = "maxAttempts";

    /** Số lớp ghi đè đo được 23/09 — vế chống tập rỗng (luật 29). */
    private static final int SO_LOP_GHI_DE_TOI_THIEU = 5;

    @Test
    @DisplayName("⛔ `JobHandler.maxAttempts()` phải có ÍT NHẤT một người đọc NGOÀI các handler")
    void phaiCoNguoiDocNgoaiHandler() {
        JavaMethod phuongThuc = phuongThucSpi();

        List<String> nguoiGoiNgoai = phuongThuc.getAccessesToSelf().stream()
                .map(truyCap -> truyCap.getOriginOwner())
                .filter(lop -> !laHandler(lop))
                .map(JavaClass::getName)
                .distinct()
                .sorted()
                .toList();

        assertThat(nguoiGoiNgoai)
                .as(
                        """
                        ⛔⛔ `JobHandler.maxAttempts()` ⛔ có người đọc nào ngoài chính các handler ⇒ nó là một
                        CÔNG TẮC CHẾT: %d lớp khai một con số, và con số thật nằm ở cột `jobs.max_attempts`
                        do NƠI ĐẶT VIỆC ghi. Hai nơi ấy chỉ trùng nhau chừng nào còn người nhớ hộ — đo 23/09
                        thì chúng ĐÃ lệch ở `PORTAL_REVALIDATE` (handler khai 5, lượt hâm nóng cổng đặt 10),
                        và ⛔ gì báo. Người đọc đúng là `JobService.enqueue`, nơi ghi cột ấy (luật 15).""",
                        phuongThuc.getOwner().getAllSubclasses().size())
                .isNotEmpty();
    }

    @Test
    @DisplayName("⚠ VẾ CHỐNG TẬP RỖNG (luật 29) — SPI và các lớp ghi đè phải còn đó")
    void spiVaCacLopGhiDePhaiConDo() {
        JavaMethod phuongThuc = phuongThucSpi();

        assertThat(phuongThuc.getModifiers())
                .as("⚠ Phải là `default`: gỡ thân mặc định đi thì mọi handler ⛔ khai gì sẽ ⛔ biên dịch nổi, "
                        + "và bài trên vẫn xanh — một lời khẳng định về người ĐỌC ⛔ nói gì về người KHAI.")
                .isNotEmpty();

        Set<String> lopGhiDe = phuongThuc.getOwner().getAllSubclasses().stream()
                .filter(lop -> lop.tryGetMethod(TEN_PHUONG_THUC).isPresent())
                .map(JavaClass::getSimpleName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(lopGhiDe)
                .as("⚠ Nếu tập này rỗng thì bài trên khẳng định về một thứ ⛔ ai dùng — xanh vì lý do sai. "
                        + "Đo 23/09: 7 lớp ghi đè (ContactSlaHandler · PortalRevalidateHandler · "
                        + "QuetLaiTepService · AuditArchiveHandler · BackupJobHandler · RestoreJobHandler · "
                        + "MaHoaLaiService).")
                .hasSizeGreaterThanOrEqualTo(SO_LOP_GHI_DE_TOI_THIEU);
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private static JavaMethod phuongThucSpi() {
        JavaClass spi = ProductionClasses.ALL.get(JOB_HANDLER);
        return spi.getMethod(TEN_PHUONG_THUC);
    }

    /**
     * ⚠ {@code getAllRawInterfaces} chứ ⛔ phải {@code isAssignableTo}: lời gọi có thể phát ra từ một
     * lớp lồng hoặc một lớp con của handler, và câu hỏi đúng là <i>lớp này CÓ PHẢI một handler ⛔</i>.
     */
    private static boolean laHandler(JavaClass lop) {
        return lop.getAllRawInterfaces().stream().anyMatch(giaoDien -> JOB_HANDLER.equals(giaoDien.getName()));
    }
}
