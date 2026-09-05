package com.songnhue.core.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Bỏ dấu tiếng Việt và sinh slug — MỘT bản cài đặt duy nhất cho toàn hệ thống.
 *
 * <p>Dùng cho: slug bài viết (CN-01.1), tìm kiếm không dấu trên tên người và tên công trình
 * (CN-04.6, CN-02.6).
 *
 * <p>Chữ <b>Đ/đ phải xử lý riêng</b>: nó không phải "D có dấu" theo chuẩn Unicode nên
 * {@code Normalizer} không tách ra được. Bỏ sót chỗ này thì "Đông Anh" ra slug "ng-anh" — lỗi rất
 * hay gặp khi mỗi người tự viết lại hàm bỏ dấu.
 */
public final class VietnameseUtils {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

    private VietnameseUtils() {}

    /** Bỏ toàn bộ dấu, giữ nguyên hoa/thường. VD "Nguyễn Đình Chiểu" → "Nguyen Dinh Chieu". */
    public static String removeDiacritics(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Đ/đ không phân rã được bằng Normalizer — thay thủ công TRƯỚC khi chuẩn hoá
        String replaced = input.replace('Đ', 'D').replace('đ', 'd');
        String normalized = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(normalized).replaceAll("");
    }

    /**
     * Sinh slug cho URL: bỏ dấu, về chữ thường, thay mọi thứ không phải chữ/số bằng dấu gạch nối.
     *
     * <p>VD "Thông báo Điều tiết nước vụ Đông Xuân 2026" → "thong-bao-dieu-tiet-nuoc-vu-dong-xuan-2026".
     *
     * <p>Slug KHÔNG tự bảo đảm duy nhất — nơi gọi phải kiểm tra trùng và trả {@code CMS-2001}.
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String ascii = removeDiacritics(input).toLowerCase(Locale.ROOT);
        String dashed = NON_SLUG_CHARS.matcher(ascii).replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    /**
     * Chuẩn hoá chuỗi để tìm kiếm: bỏ dấu, về chữ thường, gộp khoảng trắng thừa.
     *
     * <p>Nhờ vậy gõ "nguyen van an" tìm ra "Nguyễn Văn Ấn".
     */
    public static String normalizeForSearch(String input) {
        if (input == null) {
            return "";
        }
        return removeDiacritics(input)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
