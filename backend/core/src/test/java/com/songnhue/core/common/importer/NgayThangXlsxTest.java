package com.songnhue.core.common.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Ô NGÀY của tệp {@code .xlsx} — T74.12.</b>
 *
 * <h2>Vì sao bài này tồn tại</h2>
 *
 * <p>Excel ⛔ lưu ngày tháng như chữ: nó lưu một <b>số sê-ri</b> (05/01/2015 = 42009) kèm một
 * <i>định dạng</i> ở {@code styles.xml}. Javadoc của {@link SpreadsheetReader} tự khai giới hạn ấy từ
 * WS-17 và dặn đúng một câu: <i>"cột ngày đầu tiên xuất hiện thì <b>phải xử lý ở đây</b>, đừng để nơi
 * gọi tự đoán"</i>. Bộ nhập danh sách CBNV (T68.23) là cột ngày đầu tiên — ngày sinh, ngày vào làm,
 * ngày ký và hết hạn hợp đồng — nên nếu ⛔ đổi ở bộ đọc thì người nhập điền ngày trong Excel như mọi
 * bảng khác và nhận về bốn dòng lỗi <i>"ngày ⛔ hợp lệ: 42009"</i>.
 *
 * <p>⚠ Tệp dựng bằng {@code ZipOutputStream} THẬT (luật 4) — thứ cần đo là hành vi của bộ đọc trên
 * một tệp xlsx đúng cấu trúc, gồm cả {@code styles.xml} mà trước đây nó ⛔ đọc tới.
 */
class NgayThangXlsxTest {

    /** {@code 14} là định dạng ngày DỰNG SẴN của Excel; {@code 164} là ô tự đặt {@code dd/mm/yyyy}. */
    private static final String STYLES =
            """
            <styleSheet><numFmts count="2">\
            <numFmt numFmtId="164" formatCode="dd/mm/yyyy"/>\
            <numFmt numFmtId="165" formatCode="0.00"/>\
            </numFmts><cellXfs count="4">\
            <xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="164"/><xf numFmtId="165"/>\
            </cellXfs></styleSheet>""";

    private static byte[] xlsx(String workbook, String sheet) throws Exception {
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(ra)) {
            them(zip, "xl/workbook.xml", workbook);
            them(zip, "xl/styles.xml", STYLES);
            them(zip, "xl/worksheets/sheet1.xml", sheet);
        }
        return ra.toByteArray();
    }

    private static void them(ZipOutputStream zip, String ten, String noiDung) throws Exception {
        zip.putNextEntry(new ZipEntry(ten));
        zip.write(noiDung.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Một sheet hai dòng: tiêu đề (chuỗi nội tuyến) + dữ liệu (ô số có/không định dạng ngày). */
    private static String sheet(String... oDuLieu) {
        StringBuilder b = new StringBuilder("<worksheet><sheetData><row r=\"1\">");
        String[] tieuDe = {"ngay_sinh", "ngay_vao", "dien_thoai", "he_so"};
        for (int i = 0; i < tieuDe.length; i++) {
            b.append("<c r=\"%s1\" t=\"inlineStr\"><is><t>%s</t></is></c>".formatted((char) ('A' + i), tieuDe[i]));
        }
        b.append("</row><row r=\"2\">");
        for (String o : oDuLieu) {
            b.append(o);
        }
        return b.append("</row></sheetData></worksheet>").toString();
    }

    private static String o(char cot, String style, String giaTri) {
        String s = style == null ? "" : " s=\"" + style + "\"";
        return "<c r=\"%s2\"%s><v>%s</v></c>".formatted(cot, s, giaTri);
    }

    @Test
    @DisplayName("⛔⛔ Ô định dạng NGÀY (dựng sẵn 14 và tự đặt dd/mm/yyyy) ⇒ đọc ra dd/MM/yyyy, ⛔ phải số sê-ri")
    void oNgayDocRaNgay() throws Exception {
        byte[] tep = xlsx(
                "<workbook><workbookPr/></workbook>",
                sheet(o('A', "1", "32915"), o('B', "2", "42009"), o('C', null, "912345678"), o('D', "3", "1.50")));

        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(tep);

        assertThat(rows).hasSize(1);
        SpreadsheetReader.Row dong = rows.get(0);
        assertThat(dong.get("ngay_sinh")).as("định dạng DỰNG SẴN 14").isEqualTo("11/02/1990");
        assertThat(dong.get("ngay_vao")).as("định dạng TỰ ĐẶT dd/mm/yyyy").isEqualTo("05/01/2015");
        assertThat(dong.get("dien_thoai"))
                .as("⛔⛔ ô số THƯỜNG ⛔ được biến thành ngày — số điện thoại, mã, năm xây dựng đều là số")
                .isEqualTo("912345678");
        assertThat(dong.get("he_so")).as("định dạng số 0.00 ⛔ phải ngày").isEqualTo("1.50");
    }

    @Test
    @DisplayName("⚠ Hệ ngày 1904 (Excel bản Mac cũ) lệch 1462 ngày — cùng số sê-ri, HAI ngày khác nhau")
    void heNgay1904() throws Exception {
        byte[] tep1900 = xlsx("<workbook><workbookPr/></workbook>", sheet(o('A', "2", "42009")));
        byte[] tep1904 = xlsx("<workbook><workbookPr date1904=\"1\"/></workbook>", sheet(o('A', "2", "40547")));

        assertThat(SpreadsheetReader.read(tep1900).get(0).get("ngay_sinh")).isEqualTo("05/01/2015");
        assertThat(SpreadsheetReader.read(tep1904).get(0).get("ngay_sinh"))
                .as("⛔ bỏ qua cờ date1904 thì cùng một ngày lệch đúng 4 năm 1 ngày, và ⛔ gì báo")
                .isEqualTo("05/01/2015");
    }

    @Test
    @DisplayName("⚠ Tệp ⛔ có styles.xml (bảng do máy sinh) vẫn đọc được — ô số giữ nguyên chữ số")
    void khongCoStylesVanDoc() throws Exception {
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(ra)) {
            them(zip, "xl/worksheets/sheet1.xml", sheet(o('A', "1", "32915")));
        }
        assertThat(SpreadsheetReader.read(ra.toByteArray()).get(0).get("ngay_sinh"))
                .as("⛔ có bảng định dạng thì ⛔ có cách biết ô ấy là ngày — giữ nguyên giá trị, ⛔ đoán")
                .isEqualTo("32915");
    }
}
