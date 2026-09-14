package com.songnhue.core.spi;

/**
 * Một bảng mang cột mã hoá AES-256-GCM tự khai với job xoay khoá — T61.11 (nợ T51.9).
 *
 * <p>Mỗi module tự hiện thực cho bảng CỦA NÓ (quy tắc 6 — {@code core} ⛔ đọc repository của module
 * khác). Job {@code CRYPTO_REENCRYPT} ở {@code core} chỉ đi qua danh sách các bean này.
 *
 * <h2>⛔ Hợp đồng bắt buộc cho người hiện thực</h2>
 *
 * <ul>
 *   <li><b>Mọi</b> cột bản mã của bảng, <b>kể cả hàng đã xoá mềm</b> — hàng xoá mềm vẫn là dữ liệu
 *       lưu 5 năm, và gỡ khoá cũ sau đó là làm nó ⛔ đọc lại được nữa.
 *   <li>Cột <b>vân tay</b> ({@code CryptoService.fingerprint}) phụ thuộc khoá ⇒ tính lại CÙNG LƯỢT.
 *   <li>Ghi có điều kiện {@code WHERE <cột> = <bản mã vừa đọc>}: người dùng lưu đè giữa chừng thì lượt
 *       ghi của job trúng 0 hàng và hàng ấy được đếm lại ở vòng sau — ⛔ bao giờ đè dữ liệu mới hơn.
 *   <li>Một hàng ⛔ giải mã được ⇒ đếm vào {@code soHong} và đi tiếp, ⛔ ném: một hàng hỏng ⛔ được
 *       giữ mọi hàng khác ở khoá cũ.
 * </ul>
 */
public interface MaHoaLaiPort {

    /** Tên bảng — để log và kết quả job đọc được. */
    String bang();

    /**
     * Mọi cột job sẽ đổi (bản mã + vân tay). {@code MaHoaLaiPhuDuTest} đối chiếu tập này với mọi cột mang
     * CHECK dạng bản mã trên CSDL THẬT — một cột mã hoá mới ⛔ khai ở đây là một cột mà gỡ khoá cũ sẽ làm
     * ⛔ đọc lại được.
     */
    java.util.List<String> cot();

    /** Số hàng còn ít nhất một cột (bản mã hoặc vân tay) mang khoá KHÁC {@code khoaDangDung}. */
    long demConKhoaCu(String khoaDangDung);

    /**
     * Mã hoá lại tối đa {@code toiDa} hàng có {@code id > sauId} còn mang khoá cũ, mỗi hàng một giao dịch.
     *
     * @return {@link Lo#idCuoi()} là con trỏ cho lượt kế; {@link Lo#soHang()} = 0 nghĩa là hết
     */
    Lo maHoaLai(String khoaDangDung, long sauId, int toiDa);

    /**
     * @param soHang số hàng đã đọc trong lô
     * @param soDoi số hàng đã ghi lại thành công
     * @param soHong số hàng ⛔ giải mã được (hoặc vướng ràng buộc) — ⛔ đổi gì
     * @param idCuoi id lớn nhất đã đọc
     */
    record Lo(int soHang, int soDoi, int soHong, long idCuoi) {}
}
