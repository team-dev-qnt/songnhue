package com.songnhue.hr.domain;

/**
 * Bảy thư mục <b>cố định</b> của hồ sơ tài liệu CBNV — CN-04.5.
 *
 * <h2>⛔ ⛔ Không có bảng tài liệu riêng cho HR</h2>
 *
 * <p>Tệp nằm ở {@code attachments} với {@code owner_type = "EMPLOYEE"} và {@code purpose} = tên một
 * hằng ở đây. Dựng bảng thứ hai là hai chỗ kiểm magic bytes, hai chỗ quét virus, hai chỗ tính hạn
 * mức — và chúng sẽ lệch nhau ({@code architecture-review.md} §10.6). {@code AttachmentPort} đã có
 * sẵn đúng ba thứ CN-04.5 cần: {@code purpose} (7 thư mục), {@code nextVersion(owner, purpose)}
 * (versioning ⛔ không ghi đè), {@code setValidity(from, until)} (ngày hiệu lực/hết hạn cho M4.9).
 *
 * <h2>⚠ Dung lượng tối đa ⛔ KHÔNG nằm trong enum này</h2>
 *
 * <p>Cơ chế hạn mức của {@code core} chia theo <b>nhóm định dạng</b> (ảnh/tài liệu/GIS), ⛔ không
 * theo thư mục — mà đặc tả CN-04.5 đòi bảy mức riêng (10/10/20/10/5/20/10 MB). Ghi bảy con số ấy
 * vào mã là dựng bảy cái núm ⛔ không ai sửa được, đúng thứ quy tắc 12 cấm. ⇒ Chúng là <b>tham
 * số</b>, khoá {@link #khoaHanMuc()}, seed ở {@code V202609101077} và đọc ở
 * {@code HoSoTaiLieuService}.
 *
 * <p>⛔ Enum này và khối seed ấy phải khai <b>cùng một tập bảy</b>. Đó ⛔ không phải một lời dặn:
 * khối {@code DO $$} cuối migration ném nếu số khoá {@code hr.document.max-mb.*} khác 7, và
 * {@code HoSoTaiLieuHttpTest} đối chiếu từng tên một — hai nơi con người phải nhớ thì cần một phép
 * kiểm nhớ hộ (luật 14).
 */
public enum HoSoThuMuc {
    GIAY_TO_TUY_THAN,
    BANG_CAP,
    /** Hợp đồng lao động — thư mục mà M4.9 cảnh báo hết hạn quan tâm nhất. */
    HOP_DONG,
    QUYET_DINH,
    ANH,
    HO_SO_Y_TE,
    KHAC;

    /** Khoá {@code settings} chứa dung lượng tối đa mỗi tệp của thư mục này. */
    public String khoaHanMuc() {
        return "hr.document.max-mb." + name();
    }
}
