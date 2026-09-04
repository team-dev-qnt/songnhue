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
     * @param body thân đã ghi xuống {@code hydro_raw_logs}; {@code null} hoặc rỗng ⇒ mẻ rỗng
     */
    TelemetryBatch boc(String body);
}
