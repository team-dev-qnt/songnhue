package com.songnhue.hydro.domain;

/**
 * Adapter nào biết đọc một nguồn dữ liệu.
 *
 * <p>Cố ý <b>không</b> là "loại nguồn" chung chung: mỗi giá trị ứng với đúng một lớp
 * {@code TelemetryAdapter} (WS-30). Một giá trị không có lớp tương ứng là một nguồn không ai gọi
 * được, và nó chỉ lộ ra ở lượt polling đầu tiên.
 */
public enum AdapterType {
    /** {@code songnhue.bhh40.net} — {@code GET /api/getmn.aspx?key=<mã số>;}, thân trả text. */
    BHH40,

    /**
     * Nguồn giả cho môi trường phát triển và bài kiểm.
     *
     * <p>⛔ Không bao giờ được là adapter của một nguồn ở prod: dữ liệu nó sinh ra <b>trông giống
     * hệt</b> dữ liệu thật trên mọi màn hình — chốt của dự án là không có số bịa nào trong CSDL
     * nghiệm thu.
     */
    MOCK
}
