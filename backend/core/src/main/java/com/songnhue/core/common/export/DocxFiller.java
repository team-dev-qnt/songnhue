package com.songnhue.core.common.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Điền số vào một tệp <b>.docx MẪU của khách</b> — ⛔ dựng văn bản từ đầu, ⛔ phụ thuộc thư viện mới.
 *
 * <h2>Vì sao ⛔ Apache POI / docx4j (trả lời T42.14 cho riêng mẫu Word)</h2>
 *
 * <p>Bài toán ở đây ⛔ phải "sinh một văn bản" mà là "đặt chữ vào những ô CỐ ĐỊNH của một văn bản Công
 * ty đã dàn trang" — gộp ô, phông, khối ký đều đã có sẵn. Một .docx là tệp ZIP; chỉ
 * {@code word/document.xml} bị sửa, mọi entry khác chép NGUYÊN BYTE. JDK có sẵn cả hai thứ cần dùng.
 *
 * <h2>⛔ Định vị ô theo CHỈ SỐ, ⛔ theo chuỗi</h2>
 *
 * <p>Word cắt một cụm như {@code "16h ngày 24/8/2026"} thành 12 run ({@code "16"}, {@code "h ngày "},
 * {@code "24"}, …) tuỳ lịch sử soạn thảo ⇒ tìm theo chuỗi trong một ô sẽ trượt im lặng. Ô được gọi bằng
 * {@code (bảng, dòng, ô)} — chỉ số của bảng CẤP GỐC trong {@code w:body}; chỉ các đoạn văn ngoài ô mới
 * thay theo chuỗi, và việc ấy ghép cả đoạn trước khi so ({@link #thayTrongDoan}).
 *
 * <h2>⛔ Giữ định dạng: nhân bản {@code w:rPr} có sẵn, ⛔ tự viết</h2>
 *
 * <p>Đặt chữ vào ô = giữ run ĐẦU TIÊN (phông/cỡ/đậm của nó), xoá các run khác. Ô rỗng ⛔ có run nào ⇒
 * dựng run mới lấy định dạng từ dấu đoạn ({@code w:pPr/w:rPr}). Nền vàng/chữ đỏ mà mẫu dùng để ĐÁNH DẤU
 * vị trí tự điền bị gỡ khỏi run vừa điền — đó là chú thích soạn thảo, ⛔ phải định dạng văn bản.
 *
 * <p>⚠ Chặn DOCTYPE (XXE): tệp mẫu nằm trong jar, nhưng lớp này ở {@code core} và ai đó sẽ đưa cho nó
 * một tệp người dùng tải lên.
 */
public final class DocxFiller {

    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
    private static final String DOCUMENT = "word/document.xml";

    private final Map<String, byte[]> entries;
    private final Document doc;
    private final Element body;

    private DocxFiller(Map<String, byte[]> entries, Document doc) {
        this.entries = entries;
        this.doc = doc;
        this.body = con(doc.getDocumentElement(), "body").get(0);
    }

    /** Mở một .docx. */
    public static DocxFiller mo(byte[] docx) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries.put(e.getName(), zin.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Tệp .docx hỏng", e);
        }
        byte[] xml = entries.get(DOCUMENT);
        if (xml == null) {
            throw new IllegalArgumentException("Tệp không phải .docx — thiếu " + DOCUMENT);
        }
        return new DocxFiller(entries, docBaoMat(new ByteArrayInputStream(xml)));
    }

    // ==== Đọc cấu trúc ======================================================

    /** Số bảng CẤP GỐC (⛔ tính bảng lồng). */
    public int soBang() {
        return con(body, "tbl").size();
    }

    public int soDong(int bang) {
        return dongCua(bang).size();
    }

    public int soO(int bang, int dong) {
        return con(dongCua(bang).get(dong), "tc").size();
    }

    /** Chữ của một ô — nối mọi {@code w:t}. */
    public String docO(int bang, int dong, int o) {
        return chu(oCua(bang, dong, o));
    }

    // ==== Ghi ===============================================================

    /** Đặt chữ vào một ô; {@code null} = làm rỗng ô (⛔ xoá ô). */
    public void datO(int bang, int dong, int o, String text) {
        datChuO(oCua(bang, dong, o), text == null ? "" : text);
    }

    /** Bản sao sâu của một dòng — khuôn để chèn (Bảng 2). */
    public Element khuonDong(int bang, int dong) {
        return (Element) dongCua(bang).get(dong).cloneNode(true);
    }

    /** Xoá các dòng {@code [tu, het)} của bảng. */
    public void xoaDong(int bang, int tu) {
        List<Element> dong = dongCua(bang);
        for (int i = dong.size() - 1; i >= tu; i--) {
            dong.get(i).getParentNode().removeChild(dong.get(i));
        }
    }

    /**
     * Thêm một dòng (nhân bản từ khuôn) vào CUỐI bảng rồi đặt chữ cho từng ô.
     *
     * @param chu chữ theo thứ tự ô; phần tử {@code null} = để trống
     */
    public void themDong(int bang, Element khuon, String... chu) {
        Element tbl = con(body, "tbl").get(bang);
        Element tr = (Element) khuon.cloneNode(true);
        tbl.appendChild(tr);
        List<Element> o = con(tr, "tc");
        for (int i = 0; i < o.size(); i++) {
            datChuO(o.get(i), i < chu.length && chu[i] != null ? chu[i] : "");
        }
    }

    /**
     * Thay một cụm chữ trong các ĐOẠN VĂN (kể cả đoạn trong ô), ghép qua ranh giới run.
     *
     * <p>Phần ngoài cụm giữ nguyên run của nó; cụm mới nằm trong run chứa ký tự ĐẦU của cụm cũ.
     *
     * @return số lần thay — nơi gọi PHẢI khẳng định con số này: mẫu đổi thì đỏ, ⛔ lặng lẽ bỏ qua
     */
    public int thayTrongDoan(String cu, String moi) {
        int dem = 0;
        NodeList doan = doc.getElementsByTagNameNS(W, "p");
        for (int i = 0; i < doan.getLength(); i++) {
            Element p = (Element) doan.item(i);
            // ⚠ Tìm tiếp từ SAU chỗ vừa thay: chuỗi mới có thể chứa chính cụm cũ (kỳ trùng đúng ngày
            //   của mẫu) — tìm lại từ đầu đoạn là vòng lặp ⛔ bao giờ dừng.
            int tu = 0;
            while ((tu = thayMotLan(p, cu, moi, tu)) >= 0) {
                dem++;
            }
        }
        return dem;
    }

    /** Ghi ra .docx — mọi entry ngoài {@code document.xml} chép NGUYÊN BYTE, giữ thứ tự. */
    public byte[] ghi() {
        byte[] xml = inXml(doc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zout.putNextEntry(new ZipEntry(e.getKey()));
                zout.write(DOCUMENT.equals(e.getKey()) ? xml : e.getValue());
                zout.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // ==== Nội bộ ============================================================

    private List<Element> dongCua(int bang) {
        List<Element> bangs = con(body, "tbl");
        if (bang >= bangs.size()) {
            throw new IllegalStateException("Mẫu có %d bảng, đòi bảng %d".formatted(bangs.size(), bang));
        }
        return con(bangs.get(bang), "tr");
    }

    private Element oCua(int bang, int dong, int o) {
        List<Element> tr = dongCua(bang);
        if (dong >= tr.size()) {
            throw new IllegalStateException("Bảng %d có %d dòng, đòi dòng %d".formatted(bang, tr.size(), dong));
        }
        List<Element> tc = con(tr.get(dong), "tc");
        if (o >= tc.size()) {
            throw new IllegalStateException("Bảng %d dòng %d có %d ô, đòi ô %d".formatted(bang, dong, tc.size(), o));
        }
        return tc.get(o);
    }

    private void datChuO(Element tc, String text) {
        List<Element> doan = con(tc, "p");
        Element p = doan.get(0);
        for (int i = 1; i < doan.size(); i++) {
            lamRongDoan(doan.get(i));
        }
        List<Element> runs = con(p, "r");
        Element run;
        if (runs.isEmpty()) {
            run = doc.createElementNS(W, "w:r");
            Element rPrDau = rPrCuaDauDoan(p);
            if (rPrDau != null) {
                Element rPr = doc.createElementNS(W, "w:rPr");
                NodeList con = rPrDau.getChildNodes();
                for (int i = 0; i < con.getLength(); i++) {
                    rPr.appendChild(con.item(i).cloneNode(true));
                }
                run.appendChild(rPr);
            }
            p.appendChild(run);
        } else {
            run = runs.get(0);
            for (int i = 1; i < runs.size(); i++) {
                p.removeChild(runs.get(i));
            }
            for (Element t : con(run, "t")) {
                run.removeChild(t);
            }
        }
        goDanhDau(run);
        Element t = doc.createElementNS(W, "w:t");
        t.setAttributeNS(XML_NS, "xml:space", "preserve");
        t.setTextContent(text);
        run.appendChild(t);
    }

    private void lamRongDoan(Element p) {
        for (Element r : con(p, "r")) {
            p.removeChild(r);
        }
    }

    /** Gỡ nền vàng + chữ đỏ — dấu đánh vị trí tự điền của mẫu. */
    private static void goDanhDau(Element run) {
        for (Element rPr : con(run, "rPr")) {
            for (Element x : con(rPr, "highlight")) {
                rPr.removeChild(x);
            }
            for (Element x : con(rPr, "color")) {
                rPr.removeChild(x);
            }
        }
    }

    private static Element rPrCuaDauDoan(Element p) {
        for (Element pPr : con(p, "pPr")) {
            for (Element rPr : con(pPr, "rPr")) {
                return rPr;
            }
        }
        return null;
    }

    /** @return vị trí ngay sau chuỗi mới trong đoạn, hoặc -1 khi ⛔ còn khớp nào từ {@code tu} */
    private int thayMotLan(Element p, String cu, String moi, int tu) {
        List<Element> t = new ArrayList<>();
        NodeList all = p.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < all.getLength(); i++) {
            t.add((Element) all.item(i));
        }
        StringBuilder full = new StringBuilder();
        int[] batDau = new int[t.size()];
        for (int i = 0; i < t.size(); i++) {
            batDau[i] = full.length();
            full.append(t.get(i).getTextContent());
        }
        int vt = full.indexOf(cu, tu);
        if (vt < 0) {
            return -1;
        }
        int het = vt + cu.length();
        boolean daDat = false;
        for (int i = 0; i < t.size(); i++) {
            String s = t.get(i).getTextContent();
            int a = batDau[i];
            int b = a + s.length();
            if (b <= vt || a >= het) {
                continue;
            }
            String truoc = a < vt ? s.substring(0, vt - a) : "";
            String sau = b > het ? s.substring(het - a) : "";
            String giua = daDat ? "" : moi;
            daDat = true;
            t.get(i).setTextContent(truoc + giua + sau);
            t.get(i).setAttributeNS(XML_NS, "xml:space", "preserve");
            if (!giua.isEmpty()) {
                goDanhDau((Element) t.get(i).getParentNode());
            }
        }
        return vt + moi.length();
    }

    private static String chu(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeList all = el.getElementsByTagNameNS(W, "t");
        for (int i = 0; i < all.getLength(); i++) {
            sb.append(all.item(i).getTextContent());
        }
        return sb.toString();
    }

    /** Con TRỰC TIẾP mang tên {@code w:<ten>} — ⛔ lấy cháu (bảng lồng, ô lồng). */
    private static List<Element> con(Element cha, String ten) {
        List<Element> ket = new ArrayList<>();
        for (Node n = cha.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && W.equals(e.getNamespaceURI()) && ten.equals(e.getLocalName())) {
                ket.add(e);
            }
        }
        return ket;
    }

    private static Document docBaoMat(InputStream in) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(in);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Không đọc được document.xml", e);
        }
    }

    private static byte[] inXml(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.STANDALONE, "yes");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("Không ghi được document.xml", e);
        }
    }
}
