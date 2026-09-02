package com.songnhue.hydro.domain;

/**
 * Kết quả thô của một lượt gọi HTTP — <b>trước khi</b> ai đó nhìn vào nội dung.
 *
 * <h2>⭐⭐ Vì sao lớp này KHÔNG ném ngoại lệ khi nguồn hỏng</h2>
 *
 * <p>Quy tắc parse 1: <i>ghi nguyên văn response vào {@code hydro_raw_logs} trước khi parse</i>. Nếu
 * {@code TelemetryAdapter.goi()} ném khi thấy {@code not.working} hay HTTP 500, thì <b>đúng cái thân
 * phản hồi cần giữ lại nhất</b> — cái giải thích vì sao nguồn hỏng — biến mất trước khi tới được
 * bảng. Nguồn không có API lịch sử (đo 01/09/2026: gọi kèm {@code &date=…&from=…&to=…} trả về byte y
 * hệt), nên mất là mất vĩnh viễn.
 *
 * <p>⇒ Mọi tình trạng của <i>nguồn</i> đi ra bằng {@link #failureKind}; ngoại lệ chỉ dành cho lỗi
 * <i>của ta</i> (URL không hợp lệ, mã số rỗng — cả hai chặn trước khi mở kết nối). Nơi biến một
 * {@code failureKind} thành {@code UpstreamException} (SYS-0006) là <b>sau</b> lượt ghi raw.
 *
 * <h2>⚠⚠ {@link #body} đã ĐƯỢC CHE MÃ SỐ — và đây là một phát hiện, không phải một lựa chọn</h2>
 *
 * <p>Đo trên response thật ngày 01/09/2026: trang ASP.NET rỗng nối ở đuôi mang thẻ
 * {@code <form action="./getmn.aspx?key=<mã số>%3b">} — tức <b>nguồn trả chính credential về trong
 * thân phản hồi</b>. Ghi "nguyên văn" xuống {@code hydro_raw_logs} khi ấy là chép mã số vào một bảng
 * mà {@code songnhue_app} và {@code songnhue_readonly} đều {@code SELECT} được và nằm trong mọi bản
 * sao lưu — đúng ba điều {@code conventions.md} §4.7 cấm.
 *
 * <p>Hai luật va nhau (quy tắc 13 "không log credential" ↔ quy tắc 18 "ghi nguyên văn"), và cách
 * hoà giải rẻ nhất là <b>phép thay thế nhỏ nhất chứng minh được</b>: thay đúng các byte của mã số —
 * ở cả dạng nguyên văn lẫn dạng {@code %3b}/{@code %3B} mà URL-encoding sinh ra — bằng một dấu cố
 * định. Không cắt, không chuẩn hoá, không đụng một ký tự nào khác. Thứ mất đi là một giá trị ta
 * <i>đã</i> giữ ở nơi khác (cột {@code credential}, mã hoá AES-256-GCM), nên giá trị pháp y của bản
 * ghi không suy giảm.
 *
 * <p>⚠ Hệ quả phải nói ra: thân bị che <b>ngắn hơn</b> thân trên dây. {@code hydro_raw_logs
 * .body_bytes} vì thế đo <i>số byte đã lưu</i>, ⛔ không phải số byte nguồn gửi — nó là thước đo của
 * bản ghi, không phải của lượt truyền.
 *
 * @param httpStatus {@code null} khi chưa nhận được phản hồi nào (timeout, lỗi mạng, DNS)
 * @param durationMs thời gian lượt gọi, mili-giây — đo cả nhánh hỏng, vì "hỏng sau 30 s" và "hỏng
 *     sau 12 ms" là hai sự cố khác nhau (một cái là nguồn treo, một cái là không có đường mạng)
 * @param body ⚠ nguyên văn <b>trừ mã số đã che</b>; {@code null} khi không nhận được byte nào
 * @param failureKind {@code null} = thành công
 * @param failureDetail câu ngắn cho người trực; ⛔ tuyệt đối không chứa mã số
 */
public record TelemetryFetch(
        Integer httpStatus, int durationMs, String body, SyncFailureKind failureKind, String failureDetail) {

    /** Dấu thay chỗ mã số trong thân đã lưu — cố định, để {@code grep} tìm được mọi chỗ đã che. */
    public static final String DAU_CHE_MA_SO = "***MA_SO_DA_CHE***";

    public TelemetryFetch {
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs không âm, nhận " + durationMs);
        }
        if (failureKind == SyncFailureKind.THIEU_MA_SO) {
            // ⚠ Không phải sự khắt khe cho vui: `hydro_raw_logs.failure_kind` có CHECK BỐN giá trị,
            //   cố ý thiếu THIEU_MA_SO, vì thiếu mã số nghĩa là KHÔNG có lượt gọi nào — không có
            //   response để ghi. Trạng thái ấy chỉ sống ở `sync_logs` (năm giá trị). Để nó lọt tới
            //   đây là dựng sẵn một lượt INSERT chắc chắn vỡ vì ràng buộc, ở giữa một lượt ingest.
            throw new IllegalArgumentException("THIEU_MA_SO là trạng thái TRƯỚC khi mở HTTP — nó thuộc sync_logs, "
                    + "⛔ không thuộc một TelemetryFetch (hydro_raw_logs chỉ nhận 4 giá trị)");
        }
        if (failureKind == null && failureDetail != null) {
            throw new IllegalArgumentException("Thành công thì không có lý do hỏng: " + failureDetail);
        }
        if (failureKind != null && (failureDetail == null || failureDetail.isBlank())) {
            throw new IllegalArgumentException("Hỏng kiểu " + failureKind + " mà không nói vì sao — "
                    + "§10.68-B: cùng một vân tay cho ba nguyên nhân cần ba cách xử lý ngược nhau");
        }
    }

    public boolean thanhCong() {
        return failureKind == null;
    }
}
