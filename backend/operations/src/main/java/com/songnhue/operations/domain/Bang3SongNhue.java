package com.songnhue.operations.domain;

import java.util.List;

/**
 * Mục 11 *"Hệ thống sông Nhuệ (cống)"* của Bảng 3 — 7 cống × {TL, HL}, đúng thứ tự dòng của mẫu Word.
 *
 * <p>Lớp này chỉ giữ phần CỐ ĐỊNH của mẫu: nhãn (tên cống, lý trình, *"TL (hồng)"*…) chép NGUYÊN VĂN, và
 * mã vị trí nối sang {@code bao_cao_nhanh_vi_tri}. Công trình gắn vào từng cống và điểm đo từng vế là
 * DỮ LIỆU Công ty chọn trên giao diện (18/09/2026) — trước đó 14 mã điểm đo nằm ngay trong lớp này, đổi
 * một điểm đo là phải deploy (quy tắc 16).
 *
 * <p>Đổi mẫu Word ⇒ đổi lớp này cùng bộ canh vân tay mẫu.
 */
public final class Bang3SongNhue {

    public record Dong(String maViTri, String nhanCong, String lyTrinh, String nhanTl, String nhanHl) {}

    /** ⛔ ĐỪNG sắp lại — thứ tự = thứ tự dòng của Bảng 3 trong mẫu. */
    public static final List<Dong> DONG = List.of(
            new Dong("B3_LIEN_MAC", "Liên Mạc", "H-K53+450", "TL (hồng)", "HL (nhuệ)"),
            new Dong("B3_HA_DONG", "Hà Đông", "K18+182", "TL (nhuệ)", "HL (nhuệ)"),
            new Dong("B3_DONG_QUAN", "Đồng Quan", "K43+694", "TL (nhuệ)", "HL (nhuệ)"),
            new Dong("B3_HOA_MY", "Hòa Mỹ", "K1+446", "TL (nhuệ)", "HL (v.đình)"),
            new Dong("B3_VAN_DINH", "Vân Đình", "Đ-K65+348", "TL (v.đình)", "HL (đáy)"),
            new Dong("B3_NHAT_TUU", "Nhật Tựu", "K63+405", "TL (nhuệ)", "HL (nhuệ)"),
            new Dong("B3_LUONG_CO", "Lương Cổ", "K72+506", "TL (nhuệ)", "HL (đáy)"));

    private Bang3SongNhue() {}
}
