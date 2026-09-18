package com.songnhue.core.common.util;

/**
 * Tên tệp do NGƯỜI DÙNG đặt — cắt về phần dùng được — <b>T61.40</b> (ASVS 12.3.2).
 *
 * <h2>Vì sao nó nằm ở {@code core} chứ ⛔ cạnh nơi gọi</h2>
 *
 * <p>{@code attachments.original_name} lưu <b>nguyên văn</b> tên người dùng đặt lúc tải lên:
 * {@code AttachmentService} chỉ ngẫu nhiên hoá KHOÁ lưu trữ, ⛔ đụng vào tên. Hôm nay có <b>một</b>
 * nơi ghép tên ấy vào một đường dẫn (mục trong bản nén hồ sơ CBNV), nhưng mọi module đều có thể là
 * nơi thứ hai — bản nén tài liệu công trình, bản xuất kèm tệp đính kèm… ⇒ luật đặt ở chỗ dùng chung,
 * ⛔ ở một lớp của {@code hr} (luật 12).
 */
public final class TenTep {

    private TenTep() {}

    /**
     * Bỏ mọi đoạn đường dẫn, chỉ giữ phần TÊN.
     *
     * <p>Một tệp đặt tên {@code ../../../../.bashrc} ⇒ mục trong ZIP mang đúng đường dẫn ấy, và một
     * công cụ giải nén ngây thơ trên máy người nhận sẽ ghi <b>ra ngoài</b> thư mục đích ("zip-slip").
     *
     * <p>⚠ Bỏ cả {@code \} lẫn {@code /}: tệp tải lên từ Windows mang dấu gạch chéo ngược, và một bộ
     * lọc chỉ biết {@code /} sẽ để lọt {@code ..\..\Windows\system32\hosts}.
     *
     * @return tên đã cắt; {@code "khong-ten"} cho mọi ca rỗng — <b>⛔ chuỗi rỗng</b>, vì một
     *     {@code ZipEntry} tên rỗng là một mục hỏng
     */
    public static String chiPhanTen(String tenGoc) {
        if (tenGoc == null || tenGoc.isBlank()) {
            return "khong-ten";
        }
        String ten = tenGoc.replace('\\', '/');
        ten = ten.substring(ten.lastIndexOf('/') + 1).trim();
        if (ten.isEmpty() || ".".equals(ten) || "..".equals(ten)) {
            return "khong-ten";
        }
        return ten;
    }
}
