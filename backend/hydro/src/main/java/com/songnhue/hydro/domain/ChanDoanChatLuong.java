package com.songnhue.hydro.domain;

/**
 * Kết luận của bộ phân loại cho <b>một</b> bản ghi — T32.1.
 *
 * <h2>⭐ Ba trường đi liền nhau hoặc không có trường nào — ép ở hàm dựng (quy tắc 16)</h2>
 *
 * <p>Một bản ghi {@code NGHI_NGO} mà không nói được vì sao là một nhãn đỏ không hành động được:
 * người duyệt mở màn hình, thấy cờ, và không biết nên bấm <i>Duyệt</i> hay <i>Xoá</i> — hai việc
 * ngược nhau. Ràng buộc ấy còn được ép lần nữa ở tầng CSDL
 * ({@code ck_hydro_readings_nghi_ngo_co_ly_do}), vì đường ghi không chỉ có một.
 *
 * <p>Chiều ngược lại cũng ép: một bản ghi {@code HOP_LE} mang lý do là một mâu thuẫn — hoặc nó đáng
 * ngờ, hoặc không.
 *
 * @param chatLuong kết luận; ⚠ {@link ReadingQuality#NGHI_NGO} <b>vẫn được ghi vào bảng chính</b>
 *     (chốt F2), ⛔ không bị vứt
 * @param lyDo {@code null} khi và chỉ khi {@code chatLuong == HOP_LE}
 * @param moTa câu giải thích cho người duyệt đọc; ⛔ tối đa 200 ký tự — khớp
 *     {@code hydro_readings.quality_reason}
 */
public record ChanDoanChatLuong(ReadingQuality chatLuong, LyDoNghiNgo lyDo, String moTa) {

    /** Khớp {@code hydro_readings.quality_reason VARCHAR(200)}. */
    public static final int DAI_TOI_DA_MO_TA = 200;

    private static final ChanDoanChatLuong HOP_LE = new ChanDoanChatLuong(ReadingQuality.HOP_LE, null, null);

    public ChanDoanChatLuong {
        if (chatLuong == null) {
            throw new IllegalArgumentException("chatLuong");
        }
        boolean dangNgo = chatLuong == ReadingQuality.NGHI_NGO;
        if (dangNgo != (lyDo != null)) {
            throw new IllegalArgumentException(
                    "NGHI_NGO phải có lý do và HOP_LE thì không được có: chatLuong=" + chatLuong + ", lyDo=" + lyDo);
        }
        if (dangNgo != (moTa != null && !moTa.isBlank())) {
            throw new IllegalArgumentException("NGHI_NGO phải có mô tả cho người duyệt đọc: chatLuong=" + chatLuong);
        }
        if (moTa != null && moTa.length() > DAI_TOI_DA_MO_TA) {
            // ⛔ Cắt ngắn trong im lặng là hình dạng A3 (PageUtils kẹp size về 100 không một dòng chữ).
            //    Một mô tả bị cắt mất vế "vượt giới hạn bao nhiêu" là một mô tả nói dối.
            throw new IllegalArgumentException("Mô tả dài " + moTa.length() + " ký tự, vượt " + DAI_TOI_DA_MO_TA
                    + " — viết ngắn lại, " + "⛔ đừng cắt: vế bị cắt luôn là vế cuối câu, tức vế mang con số.");
        }
    }

    /** Hợp lệ — ⛔ không mang lý do, xem hàm dựng. */
    public static ChanDoanChatLuong hopLe() {
        return HOP_LE;
    }

    public static ChanDoanChatLuong nghiNgo(LyDoNghiNgo lyDo, String moTa) {
        return new ChanDoanChatLuong(ReadingQuality.NGHI_NGO, lyDo, moTa);
    }

    public boolean dangNgo() {
        return chatLuong == ReadingQuality.NGHI_NGO;
    }
}
