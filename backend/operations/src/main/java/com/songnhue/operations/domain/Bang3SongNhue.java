package com.songnhue.operations.domain;

import java.util.List;

/**
 * Mục 11 *"Hệ thống sông Nhuệ (cống)"* của Bảng 3 — 7 cống × {TL, HL}, đúng thứ tự dòng của mẫu Word.
 *
 * <p>Nhãn (tên cống, lý trình, *"TL (hồng)"*…) chép NGUYÊN VĂN mẫu; mã API theo danh mục điểm đo
 * ({@code V202608311049}, F01519 về Thượng lưu ở {@code V202609181085}). {@code null} = cống ⛔ có
 * điểm đo ở vế ấy — 3/14 ô (Hà Đông HL · Hòa Mỹ TL · Lương Cổ HL), OI-BC14: ô TRỐNG là trạng thái ĐÚNG.
 *
 * <p>⚠ Mã API là định danh BẤT BIẾN sau seed ({@code stations.api_code}) ⇒ an toàn để khai ở đây. Đổi
 * mẫu Word ⇒ đổi lớp này cùng bộ canh vân tay mẫu.
 */
public final class Bang3SongNhue {

    public record Dong(String nhanCong, String lyTrinh, String nhanTl, String apiTl, String nhanHl, String apiHl) {}

    /** ⛔ ĐỪNG sắp lại — thứ tự = thứ tự dòng của Bảng 3 trong mẫu. */
    public static final List<Dong> DONG = List.of(
            new Dong("Liên Mạc", "H-K53+450", "TL (hồng)", "F01771", "HL (nhuệ)", "F01672"),
            new Dong("Hà Đông", "K18+182", "TL (nhuệ)", "F01794", "HL (nhuệ)", null),
            new Dong("Đồng Quan", "K43+694", "TL (nhuệ)", "F01905", "HL (nhuệ)", "F01527"),
            new Dong("Hòa Mỹ", "K1+446", "TL (nhuệ)", null, "HL (v.đình)", "F02039"),
            new Dong("Vân Đình", "Đ-K65+348", "TL (v.đình)", "F01657", "HL (đáy)", "F01705"),
            new Dong("Nhật Tựu", "K63+405", "TL (nhuệ)", "F02031", "HL (nhuệ)", "F02030"),
            new Dong("Lương Cổ", "K72+506", "TL (nhuệ)", "F01519", "HL (đáy)", null));

    private Bang3SongNhue() {}
}
