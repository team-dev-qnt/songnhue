package com.songnhue.hr.domain;

/**
 * Tám báo cáo nhân sự — CN-04.8 (SRS M4.17), mã do đặc tả đặt.
 *
 * <h2>⛔⛔ `BCNS_07` CÓ MẶT ở đây và cố ý khai là CHƯA DỰNG ĐƯỢC</h2>
 *
 * <p>Cách rẻ nhất là ⛔ không khai nó: bảy mã, màn hình bảy nút, ⛔ không ai hỏi. Nhưng khi ấy
 * *"BCNS-07 chưa có"* trở thành một sự thật ⛔ không nơi nào ghi, và lượt nghiệm thu sẽ đếm bảy
 * báo cáo rồi tick đủ. ⇒ Khai đủ tám, và mã thứ bảy mang <b>lý do</b> của chính nó.
 *
 * <p>Lý do ấy ⛔ không phải chuyện kỹ thuật: mẫu <b>2C-BNV/2008</b> là <b>biểu mẫu quy định của Bộ
 * Nội vụ</b> và đặc tả ghi rõ <i>"cấm tự chế layout"</i>. Công ty chưa gửi tệp mẫu gốc — đó là mục
 * <b>G6</b> đang mở. Tự dựng một bố cục "gần giống" là in ra một văn bản hành chính <b>sai mẫu</b>,
 * và người nhận ⛔ không có cách nào biết cho tới lúc bị trả lại.
 */
public enum MaBaoCaoNhanSu {
    BCNS_01("BCNS-01", "Trích ngang cán bộ", "Danh sách cán bộ kèm các cột hồ sơ cơ bản", true, null),
    BCNS_02("BCNS-02", "Quân số theo đơn vị", "Số người còn làm việc của từng đơn vị", true, null),
    BCNS_03("BCNS-03", "Biến động nhân sự", "Tuyển mới · nghỉ việc · điều động theo từng tháng trong năm", true, null),
    BCNS_04(
            "BCNS-04",
            "Hợp đồng sắp hết hạn",
            "Hợp đồng hết hạn trong ngưỡng cấu hình (hr.contract.expiry-warning-days)",
            true,
            null),
    BCNS_05("BCNS-05", "Cơ cấu nhân sự", "Giới tính · học vấn · nhóm tuổi", true, null),
    BCNS_06(
            "BCNS-06",
            "Chứng chỉ sắp hết hiệu lực",
            "Chứng chỉ hết hiệu lực trong ngưỡng cấu hình (hr.certificate.expiry-warning-days)",
            true,
            null),
    BCNS_07(
            "BCNS-07",
            "Lý lịch mẫu 2C-BNV/2008",
            "Sơ yếu lý lịch cán bộ theo mẫu Bộ Nội vụ",
            false,
            "⛔ Mẫu 2C-BNV/2008 là biểu mẫu quy định của Bộ Nội vụ — cấm tự chế bố cục. Công ty chưa "
                    + "gửi tệp mẫu gốc (mục G6 đang mở), nên dựng một bố cục 'gần giống' là in ra "
                    + "một văn bản hành chính SAI MẪU mà người nhận chỉ biết khi bị trả lại."),
    BCNS_08("BCNS-08", "Tổng hợp năm", "Một dòng mỗi tháng: đầu kỳ · tăng · giảm · cuối kỳ", true, null);

    private final String ma;
    private final String ten;
    private final String moTa;
    private final boolean khaDung;
    private final String lyDo;

    MaBaoCaoNhanSu(String ma, String ten, String moTa, boolean khaDung, String lyDo) {
        this.ma = ma;
        this.ten = ten;
        this.moTa = moTa;
        this.khaDung = khaDung;
        this.lyDo = lyDo;
    }

    public String ma() {
        return ma;
    }

    public String ten() {
        return ten;
    }

    public String moTa() {
        return moTa;
    }

    /** ⛔ {@code false} ⇒ {@link #lyDo()} <b>bắt buộc</b> khác null — xem javadoc lớp. */
    public boolean khaDung() {
        return khaDung;
    }

    public String lyDo() {
        return lyDo;
    }

    public static MaBaoCaoNhanSu tuMa(String ma) {
        for (MaBaoCaoNhanSu m : values()) {
            if (m.ma.equalsIgnoreCase(ma) || m.name().equalsIgnoreCase(ma)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Mã báo cáo ⛔ không có trong danh mục: " + ma);
    }
}
