package com.songnhue.core.common.importer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;

/**
 * Đọc <b>KML / KMZ</b> và trả ra <b>GeoJSON</b> — <b>T59.14</b>, chốt <b>F7</b> (12/08: nhận KMZ ở
 * v1), CR-29 (Công ty có sẵn kho KMZ).
 *
 * <h2>⛔⛔ Vì sao CHUYỂN ĐỔI ở cổng nhận chứ ⛔ lưu nguyên rồi đổi lúc phục vụ</h2>
 *
 * <p>Đổi một lần lúc nạp thì <b>mọi tầng dưới ⛔ đổi một dòng nào</b>: bảng {@code gis_layers},
 * {@code attachments}, đường phục vụ {@code /gis-layers/{id}/noi-dung}, bộ đếm hình học, và
 * Leaflet ở giao diện đều tiếp tục chỉ biết GeoJSON. Đổi lúc phục vụ thì ngược lại — mỗi lượt xem
 * bản đồ trả giá một lượt phân tích XML, và <b>hai</b> định dạng phải sống song song trong mọi
 * nhánh mã từ đó về sau.
 *
 * <p>⚠ Cái giá phải nói ra: <b>bản gốc KML ⛔ được giữ</b>. Phép đổi là <b>có mất mát</b> — kiểu
 * dáng (màu, biểu tượng), ảnh phủ ({@code GroundOverlay}), liên kết mạng ({@code NetworkLink}) và
 * <b>cao độ</b> đều rơi. Đó là chủ ý: lớp bản đồ của hệ này chỉ vẽ <b>hình học</b>, và giữ lại
 * những thứ ⛔ ai đọc là dựng một lời hứa ⛔ có ai giữ (luật 15). Tên tệp lưu lại mang đuôi cũ để
 * người vận hành vẫn thấy nó <b>từ đâu ra</b> — {@code quy-hoach.kmz} ⇒ {@code quy-hoach.kmz.geojson}.
 *
 * <h2>Phạm vi phép đọc — nói ra thay vì để người sau đoán</h2>
 *
 * <ul>
 *   <li><b>Có đọc</b>: {@code Point} · {@code LineString} · {@code Polygon} (kể cả lỗ
 *       {@code innerBoundaryIs}) · {@code MultiGeometry}; {@code name} và {@code description} vào
 *       {@code properties}; {@code Folder}/{@code Document} lồng nhau được <b>trải phẳng</b>.
 *   <li><b>⛔ đọc</b>: {@code NetworkLink} (nó là một lượt tải từ URL ngoài — đúng thứ
 *       {@code SSRF} cấm, xem T61.38) · {@code GroundOverlay} · {@code Model} · kiểu dáng.
 *   <li><b>Cao độ bị bỏ</b>: KML ghi {@code kinhĐộ,vĩĐộ[,caoĐộ]}. Bản đồ của hệ là 2 chiều, và
 *       giữ chiều thứ ba làm mọi toạ độ dài thêm mà ⛔ ai đọc.
 * </ul>
 *
 * <h2>⚠⚠ Thứ tự toạ độ — chỗ dễ sai nhất và sai thì IM LẶNG</h2>
 *
 * <p>KML ghi <b>kinh độ trước</b> ({@code lon,lat}), GeoJSON cũng vậy (RFC 7946 §3.1.1:
 * <i>"position … longitude, latitude"</i>) ⇒ ở đây ⛔ phải đảo. Nhưng {@code L.marker} của Leaflet
 * và hầu hết API bản đồ lại nhận <b>vĩ độ trước</b>, nên người đọc mã rất dễ "sửa cho đúng" và đảo
 * nhầm. Đảo rồi thì một điểm ở Hà Nội (21,105) thành (105,21) — rơi xuống Ấn Độ Dương, ⛔ một lỗi
 * nào, và chỉ lộ ra khi có người mở bản đồ. ⇒ Có bài kiểm ghim đúng thứ tự này.
 *
 * <h2>Hai biện pháp bảo vệ dùng chung</h2>
 *
 * <p>XXE và trần giải nén đi qua {@link NenVaXml} — <b>một</b> bản cho cả {@code .xlsx} lẫn
 * {@code .kmz}. Thêm hai trần riêng ở đây ({@link #TRAN_DOI_TUONG}, {@link #TRAN_TOA_DO}) vì một
 * tệp KML <b>⛔ nén</b> vẫn có thể mang hàng triệu toạ độ mà ⛔ vượt trần nào ở trên.
 */
public final class DocKmlSangGeoJson {

    /**
     * Trần số đối tượng. Toàn bộ hệ thống kênh mương của Công ty đo được là <b>hàng nghìn</b>, nên
     * 50.000 là trần chống tai nạn chứ ⛔ phải một hạn mức nghiệp vụ.
     */
    static final int TRAN_DOI_TUONG = 50_000;

    /** Trần tổng số toạ độ — một đường bờ sông số hoá dày có thể mang hàng chục nghìn điểm. */
    static final int TRAN_TOA_DO = 2_000_000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private DocKmlSangGeoJson() {}

    /** Tệp này có phải KML/KMZ ⛔ — xét theo <b>TÊN</b>, đúng chỗ người dùng chọn tệp. */
    public static boolean laKmlHoacKmz(String tenTep) {
        String ten = tenTep == null ? "" : tenTep.toLowerCase(Locale.ROOT);
        return ten.endsWith(".kml") || ten.endsWith(".kmz");
    }

    /** Tên tệp sau khi đổi — giữ đuôi cũ để còn truy được nguồn gốc. */
    public static String tenSauKhiDoi(String tenTep) {
        return (tenTep == null ? "tep" : tenTep) + ".geojson";
    }

    /**
     * Đổi nội dung KML hoặc KMZ sang GeoJSON.
     *
     * @param tenTep tên do người dùng gửi lên — chỉ dùng để <b>chọn nhánh</b> và để ghi vào thông
     *     điệp lỗi; nội dung mới là thứ quyết định
     * @param noiDung byte thô
     * @return byte của một {@code FeatureCollection} UTF-8
     * @throws ValidationException {@code OPS-2033} khi ⛔ đọc được; {@code SYS-0014} khi vượt trần
     *     giải nén
     */
    public static byte[] doi(String tenTep, byte[] noiDung) {
        byte[] kml = laNen(noiDung) ? bocKmlTrongKmz(tenTep, noiDung) : noiDung;
        List<DacTrung> dacTrung = docPlacemark(tenTep, kml);
        return dungGeoJson(tenTep, dacTrung);
    }

    /**
     * ZIP ⛔ ⇒ xét <b>byte</b>, ⛔ phải đuôi tệp.
     *
     * <p>⚠ Cố ý ⛔ tin đuôi: một tệp tên {@code .kml} mà thật ra là KMZ (người dùng đổi tên) vẫn
     * phải đọc được, và ngược lại một {@code .kmz} chứa XML trần cũng vậy. Đuôi là <b>lời khai</b>
     * của người gửi; hai byte đầu là một <b>phép đo</b> — cùng doctrine với
     * {@code FileValidator.detect}.
     */
    private static boolean laNen(byte[] noiDung) {
        return noiDung != null && noiDung.length >= 2 && noiDung[0] == 'P' && noiDung[1] == 'K';
    }

    /**
     * Lấy tệp {@code .kml} ra khỏi KMZ.
     *
     * <p>Đặc tả OGC nói mục chính <b>nên</b> tên {@code doc.kml}, nhưng tệp thật do QGIS/Google
     * Earth xuất ra ⛔ luôn theo — nên: ưu tiên {@code doc.kml}, ⛔ có thì lấy <b>tệp {@code .kml}
     * đầu tiên</b>. ⚠ Các mục khác (ảnh, biểu tượng) bị bỏ qua và đó là đúng — chúng thuộc phần
     * kiểu dáng mà lớp này khai là ⛔ đọc.
     */
    private static byte[] bocKmlTrongKmz(String tenTep, byte[] noiDung) {
        byte[] dauTien = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(noiDung))) {
            ZipEntry muc;
            while ((muc = zip.getNextEntry()) != null) {
                if (muc.isDirectory()) {
                    continue;
                }
                String ten = muc.getName().toLowerCase(Locale.ROOT);
                if (!ten.endsWith(".kml")) {
                    continue;
                }
                byte[] than = NenVaXml.docCoTran(zip, muc.getName());
                if (ten.endsWith("doc.kml")) {
                    return than;
                }
                if (dauTien == null) {
                    dauTien = than;
                }
            }
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.OPS_2033, tenTep, "tệp nén hỏng hoặc ⛔ đọc được");
        }
        if (dauTien == null) {
            throw new ValidationException(ErrorCode.OPS_2033, tenTep, "tệp KMZ ⛔ chứa tệp .kml nào");
        }
        return dauTien;
    }

    /** Một Placemark đã đọc xong: nhãn + các hình học của nó. */
    private record DacTrung(String ten, String moTa, List<HinhHoc> hinh) {}

    /** Một hình học — {@code toaDo} đã ở dạng GeoJSON: {@code [[lon,lat], …]} theo từng vòng. */
    private record HinhHoc(String kieu, List<List<double[]>> vong) {}

    /**
     * Duyệt KML một lượt bằng StAX.
     *
     * <p>⛔ ⛔ Dùng DOM: một tệp 60 MB dựng cây DOM tốn hàng trăm MB heap, và lớp này chạy trong
     * cùng tiến trình với mọi việc nền khác. StAX đọc <b>một chiều</b>, giữ trong bộ nhớ đúng phần
     * đã trích ra.
     */
    private static List<DacTrung> docPlacemark(String tenTep, byte[] kml) {
        List<DacTrung> ket = new ArrayList<>();
        int soToaDo = 0;
        try {
            XMLStreamReader r = NenVaXml.xmlAnToan(new ByteArrayInputStream(kml));

            String ten = null;
            String moTa = null;
            List<HinhHoc> hinh = null;
            String kieuDangMo = null;
            List<List<double[]>> vongPolygon = null;

            while (r.hasNext()) {
                int su = r.next();
                if (su == XMLStreamConstants.START_ELEMENT) {
                    // ⛔ `getLocalName` — bỏ qua tiền tố không gian tên. KML 2.2 của OGC, bản 2.x
                    //    cũ của Google và tệp do QGIS xuất dùng BA URI khác nhau; so theo URI là
                    //    từ chối những tệp hoàn toàn đọc được.
                    switch (r.getLocalName()) {
                        case "Placemark" -> {
                            ten = null;
                            moTa = null;
                            hinh = new ArrayList<>();
                        }
                        case "name" -> {
                            if (hinh != null) {
                                ten = r.getElementText();
                            }
                        }
                        case "description" -> {
                            if (hinh != null) {
                                moTa = r.getElementText();
                            }
                        }
                        case "Point", "LineString" -> kieuDangMo = r.getLocalName();
                        case "Polygon" -> {
                            // ⚠ ⛔ cần theo dõi `outerBoundaryIs`/`innerBoundaryIs`: KML luôn ghi
                            //   vòng NGOÀI trước rồi mới tới các lỗ, và GeoJSON quy định ĐÚNG thứ
                            //   tự ấy (RFC 7946 §3.1.6). Thứ tự tài liệu đã là thứ tự cần.
                            kieuDangMo = "Polygon";
                            vongPolygon = new ArrayList<>();
                        }
                        case "coordinates" -> {
                            if (hinh == null || kieuDangMo == null) {
                                break; // toạ độ ngoài Placemark (ví dụ trong <LookAt>) — bỏ qua
                            }
                            List<double[]> diem = bocToaDo(r.getElementText());
                            soToaDo += diem.size();
                            if (soToaDo > TRAN_TOA_DO) {
                                throw new ValidationException(
                                        ErrorCode.OPS_2033, tenTep, "vượt trần " + TRAN_TOA_DO + " toạ độ");
                            }
                            if (diem.isEmpty()) {
                                break;
                            }
                            if ("Polygon".equals(kieuDangMo)) {
                                vongPolygon.add(diem);
                            } else {
                                hinh.add(new HinhHoc(kieuDangMo, List.of(diem)));
                            }
                        }
                        default -> {
                            // mọi thẻ khác ⛔ ảnh hưởng — xem "Phạm vi phép đọc" ở javadoc lớp
                        }
                    }
                } else if (su == XMLStreamConstants.END_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "Polygon" -> {
                            if (hinh != null && vongPolygon != null && !vongPolygon.isEmpty()) {
                                hinh.add(new HinhHoc("Polygon", vongPolygon));
                            }
                            vongPolygon = null;
                            kieuDangMo = null;
                        }
                        case "Point", "LineString" -> kieuDangMo = null;
                        case "Placemark" -> {
                            // ⛔ Placemark ⛔ hình học nào (chỉ có nhãn, hoặc chỉ có NetworkLink) thì
                            //    BỎ — một Feature ⛔ geometry là một chấm ⛔ vẽ được ở đâu cả.
                            if (hinh != null && !hinh.isEmpty()) {
                                if (ket.size() >= TRAN_DOI_TUONG) {
                                    throw new ValidationException(
                                            ErrorCode.OPS_2033, tenTep, "vượt trần " + TRAN_DOI_TUONG + " đối tượng");
                                }
                                ket.add(new DacTrung(ten, moTa, hinh));
                            }
                            hinh = null;
                        }
                        default -> {
                            // ⛔ làm gì
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new ValidationException(ErrorCode.OPS_2033, tenTep, "⛔ phải XML hợp lệ");
        }
        return ket;
    }

    /**
     * Bóc chuỗi {@code coordinates} của KML.
     *
     * <p>Định dạng: các bộ cách nhau bằng <b>khoảng trắng bất kỳ</b> (kể cả xuống dòng và tab —
     * tệp thật xuống dòng sau mỗi điểm), mỗi bộ là {@code lon,lat[,alt]}.
     *
     * <p>⚠ Bộ hỏng bị <b>bỏ qua</b> chứ ⛔ làm hỏng cả tệp: một tệp xuất từ công cụ cũ hay lẫn một
     * dòng rác thì phần còn lại vẫn dùng được, và ca "⛔ còn điểm nào" đã có {@code OPS-2026} ở
     * tầng trên nói đúng câu của nó.
     */
    private static List<double[]> bocToaDo(String tho) {
        List<double[]> ket = new ArrayList<>();
        if (tho == null) {
            return ket;
        }
        for (String bo : tho.trim().split("\\s+")) {
            if (bo.isEmpty()) {
                continue;
            }
            String[] phan = bo.split(",");
            if (phan.length < 2) {
                continue;
            }
            try {
                // ⛔⛔ KML ghi KINH ĐỘ TRƯỚC, và GeoJSON cũng vậy ⇒ ⛔ ĐẢO. Xem javadoc lớp.
                ket.add(new double[] {Double.parseDouble(phan[0].trim()), Double.parseDouble(phan[1].trim())});
            } catch (NumberFormatException e) {
                // bộ hỏng — bỏ qua, xem javadoc
            }
        }
        return ket;
    }

    /**
     * Sinh {@code FeatureCollection}.
     *
     * <p>⛔ Ghép chuỗi bằng tay: {@code description} của KML mang <b>HTML do người ngoài soạn</b>,
     * đầy dấu nháy và ký tự điều khiển. Một lượt ghép chuỗi ở đây là một lỗ chèn JSON, và nó sẽ
     * lộ ra ở tận giao diện dưới dạng "bản đồ ⛔ vẽ được".
     */
    private static byte[] dungGeoJson(String tenTep, List<DacTrung> dacTrung) {
        var ra = new java.io.ByteArrayOutputStream();
        try (JsonGenerator g = JSON.getFactory().createGenerator(ra, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
            g.writeStartObject();
            g.writeStringField("type", "FeatureCollection");
            g.writeArrayFieldStart("features");
            for (DacTrung d : dacTrung) {
                for (HinhHoc h : d.hinh()) {
                    g.writeStartObject();
                    g.writeStringField("type", "Feature");

                    g.writeObjectFieldStart("properties");
                    if (d.ten() != null && !d.ten().isBlank()) {
                        g.writeStringField("name", d.ten().trim());
                    }
                    if (d.moTa() != null && !d.moTa().isBlank()) {
                        g.writeStringField("description", d.moTa().trim());
                    }
                    g.writeEndObject();

                    g.writeObjectFieldStart("geometry");
                    g.writeStringField("type", h.kieu());
                    g.writeFieldName("coordinates");
                    switch (h.kieu()) {
                        case "Point" -> viDiem(g, h.vong().get(0).get(0));
                        case "LineString" -> viDay(g, h.vong().get(0));
                        default -> {
                            g.writeStartArray();
                            for (List<double[]> vong : h.vong()) {
                                viDay(g, vong);
                            }
                            g.writeEndArray();
                        }
                    }
                    g.writeEndObject();
                    g.writeEndObject();
                }
            }
            g.writeEndArray();
            g.writeEndObject();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.OPS_2033, tenTep, "⛔ dựng được GeoJSON");
        }
        return ra.toByteArray();
    }

    private static void viDiem(JsonGenerator g, double[] p) throws IOException {
        g.writeStartArray();
        g.writeNumber(p[0]);
        g.writeNumber(p[1]);
        g.writeEndArray();
    }

    private static void viDay(JsonGenerator g, List<double[]> diem) throws IOException {
        g.writeStartArray();
        for (double[] p : diem) {
            viDiem(g, p);
        }
        g.writeEndArray();
    }
}
