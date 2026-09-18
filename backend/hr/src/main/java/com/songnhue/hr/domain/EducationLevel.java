package com.songnhue.hr.domain;

/**
 * Học vấn cao nhất — CN-04.3.
 *
 * <h2>⛔ Vì sao đây là ENUM trong khi {@code positions.position_group} ngay cạnh là chữ TỰ DO</h2>
 *
 * <p>Quy tắc 16 của dự án nói *danh mục do khách vận hành là dữ liệu có CRUD*. Bậc trình độ ⛔
 * không thuộc loại ấy: nó là <b>Khung trình độ quốc gia Việt Nam</b> (QĐ 1982/QĐ-TTg 2016, bậc 1–8)
 * cộng ba bậc phổ thông. Công ty ⛔ không có thẩm quyền thêm một bậc mới, và một ô chữ tự do sẽ cho
 * ra <i>"Đại học"</i>, <i>"đại học"</i>, <i>"ĐH"</i> trong cùng một bảng rồi BCNS-05 đếm ra ba nhóm.
 *
 * <p>⇒ Hai cột cạnh nhau, hai quyết định ngược nhau, cùng một luật. Thứ phân biệt là
 * <b>ai có thẩm quyền quyết định tập giá trị</b>.
 *
 * <h2>{@link #bac()} — thứ tự, và vì sao nó ở đây chứ ⛔ không ở CSDL</h2>
 *
 * <p>BCNS-05 *"cơ cấu theo trình độ"* phải xếp được từ cao xuống thấp. Một cột {@code rank} trong
 * CSDL là một giá trị <b>dẫn xuất</b> phải giữ đồng bộ bằng tay — dự án đã trả giá cho đúng chuyện
 * ấy và chọn cột SINH ở {@code geom} và {@code chainage_m}. Ở đây thứ tự là thuộc tính <i>của chính
 * enum</i>, nên ⛔ không có chỗ nào để lệch.
 *
 * <p>⚠ {@link #KHAC} mang bậc <b>0</b> chứ ⛔ không phải bậc cao nhất hay thấp nhất trong thang: nó
 * ⛔ không nằm trên thang nào cả. Xếp nó bằng {@code THCS} là khẳng định một điều ⛔ không ai biết.
 */
public enum EducationLevel {
    /** Bậc 8 khung trình độ quốc gia. */
    TIEN_SI(8),
    /** Bậc 7. */
    THAC_SI(7),
    /** Bậc 6. */
    DAI_HOC(6),
    /** Bậc 5. */
    CAO_DANG(5),
    /** Bậc 4. */
    TRUNG_CAP(4),
    /** Bậc 3. */
    SO_CAP(3),
    /** Phổ thông trung học — ngoài khung 8 bậc, xếp dưới Sơ cấp. */
    THPT(2),
    /** Phổ thông cơ sở. */
    THCS(1),
    /** ⛔ Ngoài thang — xem javadoc lớp. */
    KHAC(0);

    private final int bac;

    EducationLevel(int bac) {
        this.bac = bac;
    }

    /** Bậc để XẾP THỨ TỰ ở BCNS-05. {@code 0} = ⛔ không nằm trên thang. */
    public int bac() {
        return bac;
    }
}
