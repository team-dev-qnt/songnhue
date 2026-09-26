package com.songnhue.hydro.domain;

/**
 * Biết đọc <b>một</b> nguồn quan trắc — T30.1.
 *
 * <h2>Vì sao ở {@code hydro.domain} chứ không ở {@code hydro.spi}</h2>
 *
 * <p>{@code spi} là nơi khai <b>những gì module KHÁC gọi</b>. Không module nào ngoài {@code hydro}
 * gọi một adapter thuỷ văn — đặt vào {@code spi} là mời {@code operations} hoặc {@code content} tự
 * mở một lượt polling của riêng nó, và khi ấy quy tắc parse 1 (ghi raw trước) có hai đường đi mà chỉ
 * một đường có bài kiểm.
 *
 * <h2>⭐ Hai bước tách rời — và thứ tự ấy là bất biến của cả MOD-03</h2>
 *
 * <pre>
 *   goi()  ──► TelemetryFetch (nguyên văn) ──► GHI hydro_raw_logs ──► boc() ──► TelemetryBatch
 *                                              ▲
 *                                              └── quy tắc parse 1: KHÔNG có bước nào chen vào đây
 * </pre>
 *
 * <p>Gộp hai bước thành một hàm "gọi rồi trả về danh sách số đo" là cách chắc chắn nhất để đánh mất
 * response: parse ném ⇒ chưa ai kịp ghi ⇒ nguồn <b>không có API lịch sử</b> ⇒ mất vĩnh viễn. Đo
 * 01/09/2026: {@code getmn.aspx} bỏ qua hoàn toàn {@code &date}/{@code &from}/{@code &to} và luôn
 * trả snapshot hiện tại.
 *
 * <h2>{@link #boc} là hàm THUẦN — cố ý</h2>
 *
 * <p>Không I/O, không CSDL, không đồng hồ. Nhờ vậy toàn bộ 10 quy tắc parse kiểm được bằng bài kiểm
 * đơn vị chạy trong mili-giây, và một response lạ từ production tái hiện được bằng cách dán chuỗi
 * vào một bài kiểm. ⛔ Đừng cho nó tra {@code stations}: quy tắc 5 (mã lạ → bỏ qua) thuộc bước ánh
 * xạ ở poller, nơi <i>đã</i> có CSDL.
 */
public interface TelemetryAdapter {

    /** Nguồn nào thì lớp nào — {@code TelemetryAdapters} tra bằng giá trị này. */
    AdapterType kieu();

    /**
     * <b>Loại chỉ số</b> mà nguồn này giao — khoá tra {@code measurement_types.code}.
     *
     * <h2>⛔⛔ Vì sao nó phải sống ở ĐÂY — WS-87 · T87.4</h2>
     *
     * <p>Tới 26/09/2026 giá trị này là một hằng số của {@code TelemetryIngestService}
     * ({@code MA_LOAI_CHI_SO = "MUC_NUOC"}), tức <b>mọi</b> nguồn đều được cho là giao mực nước dù
     * hàng {@code api_sources} nói gì. Nguồn lượng mưa làm câu ấy sai.
     *
     * <p>Ba sự thật của một nguồn — <b>đường dẫn · đơn vị nguồn · loại chỉ số</b> — phải nằm
     * <b>cùng một lớp</b>. Tách chúng ra (ví dụ: đường dẫn ở hằng số, loại chỉ số ở một cột
     * {@code api_sources}) là dựng sẵn chỗ để chúng lệch nhau, và lệch ở đây ⛔ hỏng lớn tiếng:
     * một hàng khai {@code LUONG_MUA} trỏ vào adapter giao {@code cm} sẽ ghi
     * <b>0,0 mm ÷ 100 = 0,000 m</b> — một mực nước <b>hoàn toàn hợp lý</b> — vào bảng chính. Đúng
     * hình dạng luật 14, và ⛔ có cổng nào bắt được một hàng DỮ LIỆU lệch một nhánh MÃ.
     *
     * <p>⭐ Khai ở interface thì {@code javac} bắt <b>mọi</b> lớp cài đặt phải trả lời — một lỗi
     * biên dịch, ⛔ phải một bất ngờ lúc chạy.
     */
    String maLoaiChiSo();

    /**
     * Mở một lượt gọi tới nguồn.
     *
     * <p>⛔ <b>Không ném</b> khi <i>nguồn</i> hỏng: mọi tình trạng của nguồn đi ra bằng
     * {@link TelemetryFetch#failureKind()}, để người gọi kịp ghi thân phản hồi xuống trước. Chỉ ném
     * khi lỗi thuộc về <i>ta</i> và không có gì để ghi — địa chỉ không hợp lệ (SSRF), mã số rỗng.
     *
     * @throws IllegalArgumentException địa chỉ nguồn không qua được {@link DiaChiNguon}
     */
    TelemetryFetch goi(TelemetryCall yeuCau);

    /**
     * Bóc thân phản hồi thành số đo — quy tắc parse 2, 3, 4, 6, 7, 8.
     *
     * <p>⛔ Không ném vì một dòng rác: một dòng hỏng ⇒ <b>bỏ dòng ấy</b>, cả mẻ vẫn đi tiếp (quy tắc
     * 4). Bỏ cả mẻ vì một ký tự lạ là mất 27 số đo tốt để phản ứng với 1 số đo xấu, và số đo mất là
     * mất vĩnh viễn.
     *
     * <p>⚠ WS-87: mỗi lớp cài đặt tự biết <b>đơn vị nguồn</b> của mình và đóng dấu đơn vị ấy lên
     * từng {@link TelemetryReading}. ⛔ Đoán đơn vị từ hình dạng con số — {@code 292} và {@code 0.0}
     * chỉ khác nhau ở chỗ hôm ấy ⛔ mưa.
     *
     * @param body thân đã ghi xuống {@code hydro_raw_logs}; {@code null} hoặc rỗng ⇒ mẻ rỗng
     */
    TelemetryBatch boc(String body);
}
