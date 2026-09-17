package com.songnhue.operations.domain;

/**
 * Danh mục báo cáo vận hành công trình — CN-02.10 (chốt 12/8/2026).
 *
 * <h2>⛔⛔ BỐN mã đã BỎ VĨNH VIỄN vẫn có mặt ở đây, và đó là điểm chính của lớp này</h2>
 *
 * <p>BC-01/02/03 (vận hành ngày/tuần/tháng) và BC-04 (kết quả vụ tưới/tiêu) <b>mất nguồn dữ
 * liệu</b>: nhật ký vận hành bị loại khỏi phạm vi (B1/F1) và kế hoạch vụ mùa bị loại (A1). Chốt
 * <b>G2</b> nói Công ty ⛔ không cần chỉ tiêu giờ chạy máy/điện năng/m³ bơm.
 *
 * <p>⇒ Nếu danh mục chỉ liệt kê ba mã còn sống thì người vận hành mở màn hình, đếm ba nút, và
 * <b>⛔ không có chỗ nào</b> nói cho họ biết BC-01..04 đã bỏ hay chưa làm. Câu hỏi ấy sẽ quay lại ở
 * mọi lượt nghiệm thu, và mỗi lần lại phải đi tra tài liệu. ⇒ Khai đủ, và mã đã bỏ mang <b>lý do
 * kèm mã chốt</b>.
 *
 * <p>⚠ Phân biệt với {@code MaBaoCaoNhanSu.BCNS_07}: mã kia <b>chưa làm được</b> (chờ G6, sẽ làm);
 * bốn mã ở đây <b>⛔ không bao giờ làm</b>. Hai trạng thái khác nhau ⇒ hai câu chữ khác nhau, và
 * giao diện ⛔ không được trộn chúng thành một nhãn *"chưa có"*.
 */
public enum MaBaoCaoVanHanh {
    BC_01(
            "BC-01",
            "Vận hành ngày",
            "Nhật ký vận hành tổng hợp theo ngày",
            false,
            "⛔ BỎ VĨNH VIỄN — mất nguồn: nhật ký vận hành đã loại khỏi phạm vi (chốt B1/F1), và "
                    + "chốt G2 xác nhận Công ty ⛔ không cần chỉ tiêu giờ chạy máy / điện năng / m³ bơm."),
    BC_02(
            "BC-02",
            "Vận hành tuần",
            "Nhật ký vận hành tổng hợp theo tuần",
            false,
            "⛔ BỎ VĨNH VIỄN — cùng lý do BC-01 (chốt B1/F1 + G2)."),
    BC_03(
            "BC-03",
            "Vận hành tháng",
            "Nhật ký vận hành tổng hợp theo tháng",
            false,
            "⛔ BỎ VĨNH VIỄN — cùng lý do BC-01 (chốt B1/F1 + G2)."),
    BC_04(
            "BC-04",
            "Kết quả vụ tưới / tiêu",
            "Kết quả thực hiện kế hoạch vụ tưới / tiêu",
            false,
            "⛔ BỎ — mất nguồn: kế hoạch vụ mùa đã loại khỏi phạm vi (chốt A1)."),
    BC_06(
            "BC-06",
            "Cảnh báo & sự cố",
            "Cảnh báo ngưỡng thuỷ văn (toàn hệ) + bản ghi khắc phục sự cố (theo phạm vi đơn vị)",
            true,
            null),
    BC_09("BC-09", "Tổng hợp sửa chữa / bảo trì", "Từng bản ghi trong kỳ kèm chi phí, có dòng tổng", true, null),
    BC_10(
            "BC-10",
            "Danh mục & hiện trạng công trình",
            "Toàn bộ công trình kèm loại, cấp quản lý, vòng đời, tình trạng vận hành và toạ độ",
            true,
            null);

    private final String ma;
    private final String ten;
    private final String moTa;
    private final boolean khaDung;
    private final String lyDo;

    MaBaoCaoVanHanh(String ma, String ten, String moTa, boolean khaDung, String lyDo) {
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

    public boolean khaDung() {
        return khaDung;
    }

    /**
     * ⛔ Khác null <b>khi và chỉ khi</b> {@link #khaDung()} là {@code false}.
     *
     * <p>⚠⚠ Trường này <b>cùng tên</b> với {@code MaBaoCaoNhanSu.lyDo()} và đó là cố ý: một kiểu
     * dùng chung ở giao diện ⛔ không thể khớp hai tên khác nhau, và bắt màn hình phân biệt bằng
     * <i>tên trường</i> là bắt nó biết mình đang đọc danh mục nào.
     *
     * <p>⛔ Nhưng <b>NGHĨA</b> thì khác hẳn, và khác biệt ấy nằm trong <b>câu chữ</b>:
     * {@code BCNS-07} là *chưa làm được* (chờ G6 — <i>sẽ</i> có), còn {@code BC-01..04} là *bỏ vĩnh
     * viễn* (mất nguồn — <i>⛔ không bao giờ</i> có). Gộp hai trạng thái thành một nhãn *"chưa có"*
     * là để người vận hành đi chờ một thứ ⛔ không bao giờ tới.
     */
    public String lyDo() {
        return lyDo;
    }

    public static MaBaoCaoVanHanh tuMa(String ma) {
        for (MaBaoCaoVanHanh m : values()) {
            if (m.ma.equalsIgnoreCase(ma) || m.name().equalsIgnoreCase(ma)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Mã báo cáo ⛔ không có trong danh mục: " + ma);
    }
}
