package com.songnhue.core.spi;

/**
 * Cách cắm một loại việc nền vào hàng đợi — pattern P5.
 *
 * <p>Module nghiệp vụ chỉ cần khai một bean cài giao diện này; {@code JobWorker} tự tìm thấy qua
 * Spring, không phải đăng ký ở đâu cả. Đó là điều kiện để Phase 1+ "chỉ khai báo cấu hình".
 *
 * <p>⚠ <b>Chuyển từ {@code core.application.job} sang đây ở WS-16</b>, cùng lý do đã mở {@code spi}
 * ở WS-12 ({@code architecture-review.md} §9.14): đây là <i>điểm mở rộng cho module nghiệp vụ</i>,
 * mà ArchUnit chỉ cho module import {@code core.spi} và {@code core.common}. Để ở
 * {@code core.application} thì việc nền đầu tiên của một module nghiệp vụ làm CI đỏ — Phase 0 không
 * lộ ra vì mọi handler đều nằm trong chính {@code core}. Lại đúng dạng "một ranh giới chưa ai đi qua
 * thì chưa biết nó đúng hay sai".
 *
 * <p><b>Hợp đồng:</b>
 *
 * <ul>
 *   <li>Ném ngoại lệ = thất bại. Không nuốt lỗi rồi return bình thường — worker sẽ ghi SUCCEEDED cho
 *       một việc chưa làm xong, và không ai biết.
 *   <li>Phải <b>chạy lại được</b>. Job có thể được thử tới {@code max_attempts} lần, và job đang chạy
 *       dở lúc node chết sẽ được trả về hàng đợi. Handler nào không idempotent thì lần thử thứ hai
 *       tạo ra dữ liệu trùng.
 *   <li>Việc dài phải gọi {@link JobContext#progress(int)}.
 * </ul>
 */
public interface JobHandler {

    /** Khớp với cột {@code jobs.job_type}. Hai handler cùng mã là lỗi cấu hình, chặn lúc khởi động. */
    String jobType();

    /** @throws Exception bất kỳ lỗi nào — worker lo phần thử lại và ghi nhận */
    void handle(JobContext context) throws Exception;

    /**
     * Mặc định của SPI — <b>nơi DUY NHẤT</b> khai con số này kể từ T68.30.
     *
     * <p>⚠ Cột {@code jobs.max_attempts} và {@code Job.maxAttempts} có một giá trị khởi tạo riêng ở
     * tầng CSDL/entity. Chúng là lưới an toàn cho hàng được ghi thẳng, ⛔ phải một lời khai thứ hai —
     * {@code SoLanThuMotNguonTest#macDinhCuaSpiVaCuaEntityPhaiBangNhau} giữ hai bên khớp nhau
     * (luật 14: chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ).
     */
    short MAC_DINH_SO_LAN_THU = 3;

    /**
     * Số lần thử tối đa cho loại việc này. Việc gọi ra ngoài mạng nên để cao hơn việc thuần tính toán.
     *
     * <p><b>Người đọc: {@code JobService.enqueue}</b> — nó ghi con số này vào cột
     * {@code jobs.max_attempts} khi nơi đặt việc <b>⛔ khai</b> số nào ({@code JobRequest.theoHandler}
     * hoặc {@code enqueue} ba đối số). Nơi đặt việc khai một số thì số ấy <b>THẮNG</b>: đó là một lượt
     * ghi đè có chủ đích, và nó phải mang lý do — xem {@code PortalCache.warmUp}, nơi cần 10 lượt thử
     * trong khi handler khai 5.
     *
     * <p>⛔⛔ <b>T68.30 — trước 23/09/2026 phương thức này có ĐÚNG 0 người đọc.</b> Bảy lớp ghi đè nó,
     * và con số thật hoàn toàn đến từ nơi đặt việc gõ tay. Bốn cặp trùng nhau <i>nhờ may</i>
     * ({@code ContactSlaHandler} 2=2 · {@code AuditArchiveHandler} 1=1 · {@code RestoreJobHandler} 1=1
     * · {@code BackupJobHandler} 1=1) nên ⛔ có triệu chứng nào; cặp thứ năm thì <b>đã lệch</b>
     * ({@code PortalRevalidateHandler} khai 5, lượt hâm nóng cổng đặt 10) và ⛔ gì báo. Đây là luật 15
     * ở dạng đắt: một công tắc ⛔ ai đọc <i>trông y hệt</i> một công tắc đang điều khiển.
     *
     * <p>⚠ Vì sao mặc định thuộc về <b>handler</b> chứ ⛔ phải nơi đặt việc: <i>việc này có chạy lại
     * được ⛔</i> là tính chất của <b>công việc</b>, ⛔ phải của người gọi. {@code RestoreJobHandler}
     * khai 1 kèm câu <i>"tuyệt đối ⛔ thử lại"</i> — nếu con số ấy chỉ sống ở nơi đặt việc thì một
     * đường đặt việc MỚI (diễn tập quay lui {@code T71.6} chẳng hạn) có thể đặt 3 mà ⛔ ai thấy, và
     * lượt thử thứ hai là một lượt <b>ghi đè toàn bộ CSDL</b> lần nữa.
     *
     * <p>Bộ canh: {@code SoLanThuCoNguoiDocRuleTest} (bytecode — hỏi <i>có ai GỌI ⛔</i>, thứ mà một
     * bài hành vi ⛔ phân biệt nổi khi các con số đang trùng nhau nhờ may).
     */
    default short maxAttempts() {
        return MAC_DINH_SO_LAN_THU;
    }

    /**
     * Gọi ĐÚNG MỘT LẦN khi job đã hết lượt thử — T61.24.
     *
     * <p>⛔ Trước hook này, thứ duy nhất ghi nhận "hết lượt" là {@code jobs.status = FAILED}; bản ghi nghiệp
     * vụ mà job phục vụ thì đứng yên ở trạng thái trung gian VĨNH VIỄN (tệp quét virus hỏng 3 lần kẹt
     * {@code UPLOADING}, {@code ScanStatus.ERROR} có trong enum + CHECK mà 0 nơi ghi). Lỗi trong hook bị
     * worker nuốt kèm log — ⛔ được làm hỏng việc ghi {@code FAILED}.
     *
     * @param payload payload nguyên văn của job
     * @param loi câu đã ghi vào {@code jobs.last_error}
     */
    default void khiHetLuotThu(String payload, String loi) {}
}
