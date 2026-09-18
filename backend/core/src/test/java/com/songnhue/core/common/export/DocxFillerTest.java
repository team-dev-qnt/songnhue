package com.songnhue.core.common.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link DocxFiller} trên một .docx tối giản dựng tại chỗ — ⛔ phụ thuộc tệp mẫu của Công ty. */
class DocxFillerTest {

    private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    /** Một đoạn văn bị Word CẮT VỤN run + một bảng 1×2, ô đầu in đậm và tô nền vàng. */
    private static final String DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document " + W + "><w:body>"
            + "<w:p><w:r><w:t>Tính đến </w:t></w:r><w:r><w:t>16</w:t></w:r><w:r><w:t xml:space=\"preserve\">h ngày </w:t></w:r>"
            + "<w:r><w:t>24/8/2026</w:t></w:r><w:r><w:t>, như sau:</w:t></w:r></w:p>"
            + "<w:tbl><w:tr>"
            + "<w:tc><w:p><w:r><w:rPr><w:b/><w:highlight w:val=\"yellow\"/><w:color w:val=\"FF0000\"/></w:rPr>"
            + "<w:t>200</w:t></w:r><w:r><w:t>9</w:t></w:r></w:p></w:tc>"
            + "<w:tc><w:p><w:pPr><w:rPr><w:i/></w:rPr></w:pPr></w:p></w:tc>"
            + "</w:tr></w:tbl></w:body></w:document>";

    static byte[] docx(String documentXml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("[Content_Types].xml"));
            z.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("word/document.xml"));
            z.write(documentXml.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    static String documentXmlCua(byte[] docx) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(z.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    @Test
    @DisplayName("⭐⭐ Thay cụm chữ bị Word cắt qua 3 run — ghép cả đoạn rồi mới so")
    void thayQuaRanhGioiRun() throws IOException {
        DocxFiller f = DocxFiller.mo(docx(DOCUMENT));
        assertThat(f.thayTrongDoan("16h ngày 24/8/2026", "9h ngày 1/7/2027")).isEqualTo(1);
        String xml = documentXmlCua(f.ghi());
        assertThat(xml.replaceAll("<[^>]+>", "")).contains("Tính đến 9h ngày 1/7/2027, như sau:");
    }

    @Test
    @DisplayName("⛔ Chuỗi mới CHỨA cụm cũ ⇒ vẫn dừng, đếm đúng 1 (⛔ vòng lặp vô hạn)")
    void chuoiMoiChuaCumCu() throws IOException {
        DocxFiller f = DocxFiller.mo(docx(DOCUMENT));
        assertThat(f.thayTrongDoan("24/8/2026", "24/8/2026 (bản sửa)")).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Đặt chữ vào ô: giữ định dạng run đầu (đậm), gỡ dấu đánh vị trí (vàng/đỏ), bỏ run thừa")
    void datOGiuDinhDang() throws IOException {
        DocxFiller f = DocxFiller.mo(docx(DOCUMENT));
        f.datO(0, 0, 0, "1,40");
        f.datO(0, 0, 1, "x");
        DocxFiller doc = DocxFiller.mo(f.ghi());
        assertThat(doc.docO(0, 0, 0)).as("run thứ hai ('9') phải biến mất").isEqualTo("1,40");
        assertThat(doc.docO(0, 0, 1)).as("ô rỗng ⛔ có run ⇒ dựng run mới").isEqualTo("x");
        String xml = documentXmlCua(f.ghi());
        assertThat(xml)
                .contains("<w:b/>")
                .doesNotContain("highlight")
                .doesNotContain("FF0000")
                .contains("<w:i/>");
    }

    @Test
    @DisplayName("⭐ Nhân bản dòng — bảng tăng đúng số dòng, ô mang chữ mới")
    void nhanBanDong() throws IOException {
        DocxFiller f = DocxFiller.mo(docx(DOCUMENT));
        var khuon = f.khuonDong(0, 0);
        f.xoaDong(0, 0);
        f.themDong(0, khuon, "a", "b");
        f.themDong(0, khuon, "c", null);
        assertThat(f.soDong(0)).isEqualTo(2);
        assertThat(f.docO(0, 1, 0)).isEqualTo("c");
        assertThat(f.docO(0, 1, 1)).isEmpty();
    }

    @Test
    @DisplayName("⭐ Gộp dọc — restart ở dòng đầu, nối tiếp ở dòng sau; vMerge đứng SAU tcW trong tcPr")
    void gopDoc() throws IOException {
        String xml = DOCUMENT.replace(
                "<w:tc><w:p><w:r><w:rPr><w:b/>",
                "<w:tc><w:tcPr><w:tcW w:w=\"500\"/><w:shd w:val=\"clear\"/></w:tcPr><w:p><w:r><w:rPr><w:b/>");
        DocxFiller f = DocxFiller.mo(docx(xml));
        var khuon = f.khuonDong(0, 0);
        f.themDong(0, khuon, "x", "y");
        f.themDong(0, khuon, null, "z");
        f.gopDoc(0, 1, 2, 0);
        assertThat(f.gopDocCua(0, 1, 0)).isEqualTo("restart");
        assertThat(f.gopDocCua(0, 2, 0)).isEqualTo("continue");
        assertThat(f.gopDocCua(0, 1, 1)).as("cột ⛔ khai thì ⛔ gộp").isNull();
        assertThat(f.gopDocCua(0, 0, 0)).as("dòng ngoài khoảng ⛔ gộp").isNull();

        f.gopDoc(0, 0, 0, 1);
        assertThat(f.gopDocCua(0, 0, 1)).as("một dòng thì ⛔ có gì để gộp").isNull();

        String ra = documentXmlCua(f.ghi());
        assertThat(ra).contains("<w:tcW w:w=\"500\"/><w:vMerge w:val=\"restart\"/><w:shd");
    }

    @Test
    @DisplayName("⛔ DOCTYPE bị từ chối (XXE)")
    void chanDoctype() throws IOException {
        String doc = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>" + "<w:document "
                + W + "><w:body><w:p><w:r><w:t>&e;</w:t></w:r></w:p></w:body></w:document>";
        byte[] tep = docx(doc);
        assertThatThrownBy(() -> DocxFiller.mo(tep)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⛔ Toạ độ ngoài mẫu ⇒ NÉM, ⛔ lặng lẽ bỏ qua")
    void toaDoNgoaiMau() throws IOException {
        DocxFiller f = DocxFiller.mo(docx(DOCUMENT));
        assertThatThrownBy(() -> f.datO(0, 0, 5, "x")).hasMessageContaining("có 2 ô");
        assertThatThrownBy(() -> f.datO(3, 0, 0, "x")).hasMessageContaining("có 1 bảng");
    }
}
