package com.songnhue.hydro.domain;

/**
 * Adapter nào biết đọc một nguồn dữ liệu.
 *
 * <p>Cố ý <b>không</b> là "loại nguồn" chung chung: mỗi giá trị ứng với đúng một lớp
 * {@code TelemetryAdapter} (WS-30). Một giá trị không có lớp tương ứng là một nguồn không ai gọi
 * được, và nó chỉ lộ ra ở lượt polling đầu tiên.
 */
public enum AdapterType {
    /**
     * {@code songnhue.bhh40.net} — <b>mực nước</b>, {@code GET /api/getmucnuoc.aspx?key=<mã số>;},
     * thân trả text, đơn vị <b>cm</b>.
     *
     * <p>⚠ Tên trần {@code BHH40} nghĩa là <i>mực nước</i> vì lý do lịch sử: tới 26/09/2026 nguồn
     * chỉ có một endpoint. ⛔ Đổi tên thành {@code BHH40_MUC_NUOC}: hằng số này nằm trong cột
     * {@code api_sources.adapter_type} của các hàng ĐANG CHẠY ở staging/production, nên đổi tên là
     * một lượt di trú dữ liệu — trả giá thật để đổi lấy sự đối xứng.
     *
     * <p>⛔⛔ Đường dẫn đổi {@code getmn.aspx} → {@code getmucnuoc.aspx} ngày 26/09/2026: đường cũ
     * nay trả {@code not.working}. Xem {@code V202609261099} — lượt đổi ấy <b>bắt buộc</b> phải đi
     * kèm phép cắt {@code base_url}, ⛔ thì {@code DiaChiNguon} nối ra một đường 404.
     */
    BHH40,

    /**
     * {@code songnhue.bhh40.net} — <b>lượng mưa</b>, {@code GET /api/getluongmua.aspx?key=<mã số>;},
     * đơn vị <b>mm</b>, nhịp <b>1 giờ</b>. Mở G3-a (WS-87, 26/09/2026).
     *
     * <p>⚠ Cùng host, cùng mã số, cùng định dạng dòng với {@link #BHH40} — khác đúng ba thứ: đường
     * dẫn, đơn vị, loại chỉ số. Đó là lý do hai lớp cài đặt dùng chung một lớp nền, ⛔ chép nhau.
     */
    BHH40_MUA,

    /**
     * Nguồn giả cho môi trường phát triển và bài kiểm.
     *
     * <p>⛔ Không bao giờ được là adapter của một nguồn ở prod: dữ liệu nó sinh ra <b>trông giống
     * hệt</b> dữ liệu thật trên mọi màn hình — chốt của dự án là không có số bịa nào trong CSDL
     * nghiệm thu.
     */
    MOCK
}
