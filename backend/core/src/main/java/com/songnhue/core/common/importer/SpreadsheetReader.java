package com.songnhue.core.common.importer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.util.VietnameseUtils;

/**
 * Đọc một bảng phẳng từ tệp CSV hoặc XLSX — nền của T17.9.
 *
 * <h2>⛔ Vì sao KHÔNG thêm Apache POI</h2>
 *
 * Tệp nhập danh mục công trình là một <b>bảng phẳng toàn chữ và số</b>: mã, tên, mã đơn vị, toạ độ,
 * năm xây dựng. Không công thức, không ô ngày tháng, không định dạng. Đọc được đúng chừng đó thì
 * XLSX chỉ là một tệp ZIP chứa XML, và JDK đã có sẵn cả hai ({@code java.util.zip} + StAX).
 *
 * <p>POI kéo theo {@code xmlbeans}, {@code commons-compress}, {@code commons-io} và là một trong
 * những nguồn CVE Java thường xuyên nhất — mà dự án này đã tự đặt luật "mỗi thành phần phải tự chứng
 * minh nó đáng nuôi" và đã <i>trả lại</i> một phiên bản MinIO cùng một module Testcontainers vì
 * đúng lý do đó. Khi nào cần <b>xuất</b> Excel có định dạng (CN-02.10, Phase 3) thì POI mới đáng.
 *
 * <h2>⚠ Giới hạn đã biết, ghi ra để không ai tưởng nó là bộ đọc Excel đầy đủ</h2>
 *
 * <ul>
 *   <li>Chỉ đọc <b>sheet đầu tiên</b>.
 *   <li>Đọc <b>giá trị đã lưu</b> của ô công thức, không tính lại công thức. Excel luôn lưu kèm giá
 *       trị nên việc này đúng với tệp do Excel/LibreOffice ghi ra; tệp do máy sinh mà không có
 *       {@code <v>} thì ô đó đọc ra rỗng — và lượt chạy khô sẽ báo thiếu, không nuốt lặng lẽ.
 *   <li><b>Không</b> đổi số sê-ri ngày của Excel sang ngày tháng. Bảng nhập công trình không có cột
 *       ngày nào (năm xây dựng là số nguyên), nên giới hạn này chưa chạm tới ai. Cột ngày đầu tiên
 *       xuất hiện thì <b>phải xử lý ở đây</b>, đừng để nơi gọi tự đoán.
 * </ul>
 */
public final class SpreadsheetReader {

    /** Chữ ký ZIP — mọi tệp XLSX bắt đầu bằng đúng bốn byte này. */
    private static final byte[] CHU_KY_ZIP = {0x50, 0x4B, 0x03, 0x04};

    /**
     * Trần số dòng: tệp danh mục công trình đếm bằng trăm, không phải bằng triệu.
     *
     * <p>⛔⛔ Chạm trần thì <b>NÉM</b> ({@code SYS-0012}), ⛔ không cắt cụt. Tới 09/09/2026 vòng lặp ở
     * {@link #dungRows} dừng im lặng ở dòng thứ 5000 — ⛔ không ngoại lệ, ⛔ không một
     * {@code RowError}, và {@code ConstructionImportService} lấy {@code tongDong = rows.size()} nên
     * bản báo cáo nói <i>"đã nhập 5000 hồ sơ"</i> cho một tệp 8000 dòng. Người nhập nhận đúng chữ
     * "thành công" và ⛔ không có gì nói ra rằng 3000 hồ sơ chưa bao giờ được đọc tới.
     *
     * <p>Cùng hình dạng §10.69 (trần multipart 1 MB không ai khai) và cùng luật 16 với
     * {@code HydroReportExportHandler#TRAN_DONG_CHI_TIET}, nơi chú thích đã ghi thẳng: <i>"một tệp bị
     * cắt cụt trông y hệt một tệp đầy đủ"</i>. Hai chỗ, cùng một câu, và chỉ một chỗ làm đúng.
     */
    public static final int MAX_ROWS = 5000;

    private SpreadsheetReader() {}

    /**
     * Một dòng dữ liệu.
     *
     * @param rowNumber số dòng <b>như người dùng thấy trong Excel</b> (dòng tiêu đề là 1) — báo lỗi
     *     theo số dòng nội bộ thì người sửa tệp phải tự cộng trừ, và họ sẽ sửa nhầm dòng
     */
    public record Row(int rowNumber, Map<String, String> cells) {

        public String get(String column) {
            String value = cells.get(column);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /** Nhận diện bằng <b>magic bytes</b>, không tin đuôi tệp — cùng luật với {@code FileValidator}. */
    public static List<Row> read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException(ErrorCode.SYS_0016);
        }
        return laXlsx(content) ? docXlsx(content) : docCsv(content);
    }

    private static boolean laXlsx(byte[] content) {
        if (content.length < CHU_KY_ZIP.length) {
            return false;
        }
        for (int i = 0; i < CHU_KY_ZIP.length; i++) {
            if (content[i] != CHU_KY_ZIP[i]) {
                return false;
            }
        }
        return true;
    }

    // === CSV =================================================================

    /**
     * Đọc CSV theo RFC 4180: dấu phẩy ngăn cột, ngoặc kép bọc ô, {@code ""} là một dấu ngoặc kép.
     *
     * <p>⚠ Bỏ BOM ở đầu tệp. Excel bản Windows luôn ghi BOM khi lưu CSV UTF-8, và nếu không bỏ thì
     * <b>tên cột đầu tiên</b> mang thêm một ký tự vô hình — hệ quả là "thiếu cột bắt buộc" ở một tệp
     * nhìn bằng mắt thì hoàn toàn đúng.
     */
    private static List<Row> docCsv(byte[] content) {
        // ⚠ Chặn tệp nhị phân sớm. Không có bước này thì một tệp .doc hoặc .pdf tải nhầm vẫn được
        // "đọc" thành một dòng ký tự rác, và người dùng nhận thông báo "tệp thiếu cột bắt buộc" —
        // câu đó dẫn họ đi sửa tiêu đề của một tệp không hề là bảng tính. Byte 0x00 là dấu hiệu đủ
        // chắc: văn bản UTF-8 hợp lệ không bao giờ chứa nó.
        for (byte b : content) {
            if (b == 0) {
                throw new ValidationException(ErrorCode.SYS_0016);
            }
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<List<String>> grid = tachCsv(text);
        if (grid.isEmpty()) {
            throw new ValidationException(ErrorCode.SYS_0016);
        }
        return dungRows(grid);
    }

    private static List<List<String>> tachCsv(String text) {
        List<List<String>> grid = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder o = new StringBuilder();
        boolean trongNgoac = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (trongNgoac) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        o.append('"');
                        i++;
                    } else {
                        trongNgoac = false;
                    }
                } else {
                    o.append(c);
                }
            } else if (c == '"') {
                trongNgoac = true;
            } else if (c == ',' || c == ';') {
                row.add(o.toString());
                o.setLength(0);
            } else if (c == '\n') {
                row.add(o.toString());
                o.setLength(0);
                grid.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                o.append(c);
            }
        }
        if (o.length() > 0 || !row.isEmpty()) {
            row.add(o.toString());
            grid.add(row);
        }
        return grid;
    }

    // === XLSX ================================================================

    private static List<Row> docXlsx(byte[] content) {
        try {
            List<String> chuoiDungChung = new ArrayList<>();
            byte[] sheet = null;

            byte[] styles = null;
            byte[] workbook = null;

            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String ten = entry.getName();
                    if ("xl/sharedStrings.xml".equals(ten)) {
                        chuoiDungChung = docSharedStrings(docCoTran(zip, ten));
                    } else if ("xl/worksheets/sheet1.xml".equals(ten)) {
                        sheet = docCoTran(zip, ten);
                    } else if ("xl/styles.xml".equals(ten)) {
                        styles = docCoTran(zip, ten);
                    } else if ("xl/workbook.xml".equals(ten)) {
                        workbook = docCoTran(zip, ten);
                    }
                }
            }
            if (sheet == null) {
                throw new ValidationException(ErrorCode.SYS_0016);
            }
            return dungRows(docSheet(sheet, chuoiDungChung, kieuNgay(styles), heNgay1904(workbook)));
        } catch (IOException | XMLStreamException e) {
            throw new ValidationException(ErrorCode.SYS_0016, e);
        }
    }

    /**
     * Bảng chuỗi dùng chung.
     *
     * <p>Excel không lưu chữ ngay trong ô mà lưu một chỉ số trỏ vào bảng này. Đọc thiếu nó thì mọi ô
     * chữ ra thành con số — và con số đó trông đủ hợp lệ để không ai nghi ngờ ngay.
     *
     * <p>Một mục {@code <si>} có thể vỡ thành nhiều đoạn {@code <t>} khi ô có nhiều kiểu chữ, nên
     * phải nối lại theo mục chứ không đếm từng {@code <t>}.
     */
    private static List<String> docSharedStrings(byte[] xml) throws XMLStreamException {
        List<String> ket = new ArrayList<>();
        XMLStreamReader reader = xmlReader(new ByteArrayInputStream(xml));
        StringBuilder dangGhep = null;
        while (reader.hasNext()) {
            int su = reader.next();
            if (su == XMLStreamConstants.START_ELEMENT) {
                String ten = reader.getLocalName();
                if ("si".equals(ten)) {
                    dangGhep = new StringBuilder();
                } else if ("t".equals(ten) && dangGhep != null) {
                    dangGhep.append(reader.getElementText());
                }
            } else if (su == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName())) {
                ket.add(dangGhep == null ? "" : dangGhep.toString());
                dangGhep = null;
            }
        }
        return ket;
    }

    private static List<List<String>> docSheet(
            byte[] xml, List<String> chuoiDungChung, boolean[] kieuLaNgay, boolean he1904) throws XMLStreamException {
        List<List<String>> grid = new ArrayList<>();
        XMLStreamReader reader = xmlReader(new ByteArrayInputStream(xml));

        Map<Integer, String> dong = new HashMap<>();
        int cotLonNhat = -1;
        int cotHienTai = -1;
        boolean laChuoiNoiTuyen = false;
        boolean laChiSoChuoi = false;
        boolean laOSo = false;
        int chiSoKieu = -1;

        while (reader.hasNext()) {
            int su = reader.next();
            if (su == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "row" -> {
                        dong = new HashMap<>();
                        cotLonNhat = -1;
                    }
                    case "c" -> {
                        cotHienTai = cotTuThamChieu(reader.getAttributeValue(null, "r"));
                        String kieu = reader.getAttributeValue(null, "t");
                        laChiSoChuoi = "s".equals(kieu);
                        laChuoiNoiTuyen = "inlineStr".equals(kieu);
                        laOSo = kieu == null || "n".equals(kieu);
                        chiSoKieu = soNguyen(reader.getAttributeValue(null, "s"), -1);
                        cotLonNhat = Math.max(cotLonNhat, cotHienTai);
                    }
                    case "v" -> {
                        String raw = reader.getElementText();
                        String value = laChiSoChuoi ? traChuoi(chuoiDungChung, raw) : raw;
                        if (laOSo && chiSoKieu >= 0 && chiSoKieu < kieuLaNgay.length && kieuLaNgay[chiSoKieu]) {
                            value = ngayTuSoSeri(value, he1904);
                        }
                        dong.put(cotHienTai, value);
                    }
                    case "t" -> {
                        if (laChuoiNoiTuyen) {
                            dong.put(cotHienTai, reader.getElementText());
                        }
                    }
                    default -> {
                        // phần tử định dạng — không liên quan tới dữ liệu
                    }
                }
            } else if (su == XMLStreamConstants.END_ELEMENT && "row".equals(reader.getLocalName())) {
                List<String> phang = new ArrayList<>();
                for (int i = 0; i <= cotLonNhat; i++) {
                    phang.add(dong.getOrDefault(i, ""));
                }
                grid.add(phang);
            }
        }
        return grid;
    }

    private static String traChuoi(List<String> bang, String chiSo) {
        try {
            int i = Integer.parseInt(chiSo.trim());
            return i >= 0 && i < bang.size() ? bang.get(i) : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** {@code "C7"} → cột 2. Chữ cái là hệ 26, phần số là số dòng nên bỏ qua. */
    private static int cotTuThamChieu(String reference) {
        if (reference == null || reference.isEmpty()) {
            return 0;
        }
        int cot = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = Character.toUpperCase(reference.charAt(i));
            if (c < 'A' || c > 'Z') {
                break;
            }
            cot = cot * 26 + (c - 'A' + 1);
        }
        return cot - 1;
    }

    /**
     * XML từ tệp người dùng tải lên là dữ liệu <b>không tin được</b>.
     *
     * <p>Tắt DTD và thực thể ngoài: một XLSX là ZIP chứa XML, nên nó là đường vào XXE hoàn chỉnh —
     * kẻ gửi tệp có thể đọc trộm tệp trên máy chủ hoặc bắt máy chủ gọi ra ngoài. Đây không phải rủi
     * ro lý thuyết ở đây: màn hình nhập danh mục nhận tệp từ người dùng đã đăng nhập, mà tài khoản
     * đăng nhập được không có nghĩa là được đọc {@code /opt/songnhue/keys/}.
     */
    /**
     * <b>Ô nào là NGÀY — T74.12.</b> Excel ⛔ lưu ngày như chữ: nó lưu một <b>số sê-ri</b> kèm một định dạng ở
     * {@code xl/styles.xml}. Javadoc lớp này đã dặn từ WS-17: cột ngày đầu tiên xuất hiện thì phải xử lý <b>ở đây</b>.
     *
     * @return mảng theo chỉ số {@code cellXfs}: phần tử {@code i} = kiểu thứ {@code i} có phải định dạng ngày ⛔.
     *     Tệp ⛔ có {@code styles.xml} (bảng do máy sinh) ⇒ mảng rỗng ⇒ mọi ô số giữ nguyên chữ số, ⛔ đoán.
     */
    private static boolean[] kieuNgay(byte[] styles) throws XMLStreamException {
        if (styles == null) {
            return new boolean[0];
        }
        Map<Integer, String> maTuDat = new HashMap<>();
        List<Integer> theoChiSo = new ArrayList<>();
        XMLStreamReader reader = xmlReader(new ByteArrayInputStream(styles));
        boolean trongCellXfs = false;
        while (reader.hasNext()) {
            int su = reader.next();
            if (su == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "numFmt" ->
                        maTuDat.put(
                                soNguyen(reader.getAttributeValue(null, "numFmtId"), -1),
                                reader.getAttributeValue(null, "formatCode"));
                    case "cellXfs" -> trongCellXfs = true;
                    case "xf" -> {
                        if (trongCellXfs) {
                            theoChiSo.add(soNguyen(reader.getAttributeValue(null, "numFmtId"), 0));
                        }
                    }
                    default -> {
                        // phần tử định dạng khác — ⛔ liên quan
                    }
                }
            } else if (su == XMLStreamConstants.END_ELEMENT && "cellXfs".equals(reader.getLocalName())) {
                trongCellXfs = false;
            }
        }
        boolean[] ra = new boolean[theoChiSo.size()];
        for (int i = 0; i < ra.length; i++) {
            int maDinhDang = theoChiSo.get(i);
            ra[i] = maDinhDang > 0 && (MA_NGAY_DUNG_SAN.contains(maDinhDang) || laMaNgay(maTuDat.get(maDinhDang)));
        }
        return ra;
    }

    /**
     * Mã định dạng ngày <b>dựng sẵn</b> của Excel (ECMA-376 §18.8.30). ⛔ gồm 45–47: chúng là {@code mm:ss} và
     * {@code [h]:mm:ss} — thời lượng, ⛔ phải ngày.
     */
    private static final Set<Integer> MA_NGAY_DUNG_SAN = Set.of(
            14, 15, 16, 17, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 50, 51, 52, 53, 54, 55, 56, 57,
            58);

    /**
     * Định dạng TỰ ĐẶT có phải ngày ⛔ — bỏ phần trong nháy và trong ngoặc vuông ({@code [$-42A]}, {@code [Red]})
     * rồi hỏi còn {@code d} hoặc {@code y} ⛔.
     *
     * <p>⛔ Hỏi {@code m}: trong mã định dạng của Excel, {@code m} vừa là THÁNG vừa là PHÚT — một ô {@code h:mm}
     * sẽ bị đọc thành ngày và 0,5 (mười hai giờ trưa) thành 30/12/1899.
     */
    private static boolean laMaNgay(String maDinhDang) {
        if (maDinhDang == null) {
            return false;
        }
        String con = maDinhDang.replaceAll("\\[[^\\]]*\\]", "").replaceAll("\"[^\"]*\"", "");
        return con.indexOf('d') >= 0 || con.indexOf('D') >= 0 || con.indexOf('y') >= 0 || con.indexOf('Y') >= 0;
    }

    /**
     * Số sê-ri Excel → {@code dd/MM/yyyy}.
     *
     * <p>⚠ Hệ 1900 có <b>lỗi năm nhuận của Lotus</b>: sê-ri 60 là "29/02/1900", một ngày ⛔ tồn tại. Nên mốc là
     * 30/12/1899 cho sê-ri ≥ 61, và 31/12/1899 cho sê-ri nhỏ hơn — dải ấy ⛔ bao giờ gặp trong dữ liệu nhân sự
     * nhưng để im thì một ô hỏng cho ra một ngày lệch một hôm, ⛔ một dòng lỗi.
     *
     * <p>Hệ 1904 (Excel bản Mac cũ, cờ {@code date1904} ở {@code workbook.xml}) lấy mốc 01/01/1904 — cùng một số
     * sê-ri ra hai ngày cách nhau 1462 hôm.
     *
     * <p>Giá trị ⛔ phải số (ô hỏng) thì trả nguyên văn: người nhập thấy đúng thứ họ gõ ở dòng lỗi.
     */
    private static String ngayTuSoSeri(String tho, boolean he1904) {
        double soSeri;
        try {
            soSeri = Double.parseDouble(tho.trim());
        } catch (NumberFormatException e) {
            return tho;
        }
        long ngay = (long) Math.floor(soSeri);
        LocalDate moc = he1904 ? LocalDate.of(1904, 1, 1) : LocalDate.of(1899, 12, ngay < 61 ? 31 : 30);
        return moc.plusDays(ngay).format(DINH_DANG_NGAY);
    }

    private static final DateTimeFormatter DINH_DANG_NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Cờ {@code date1904} của {@code xl/workbook.xml} — thiếu tệp ấy thì mặc định hệ 1900. */
    private static boolean heNgay1904(byte[] workbook) throws XMLStreamException {
        if (workbook == null) {
            return false;
        }
        XMLStreamReader reader = xmlReader(new ByteArrayInputStream(workbook));
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && "workbookPr".equals(reader.getLocalName())) {
                String co = reader.getAttributeValue(null, "date1904");
                return "1".equals(co) || "true".equalsIgnoreCase(co);
            }
        }
        return false;
    }

    private static int soNguyen(String tho, int duPhong) {
        if (tho == null || tho.isBlank()) {
            return duPhong;
        }
        try {
            return Integer.parseInt(tho.trim());
        } catch (NumberFormatException e) {
            return duPhong;
        }
    }

    /** ⚠ Một bản DUY NHẤT của biện pháp chống XXE — xem javadoc {@link NenVaXml} (T59.14). */
    private static XMLStreamReader xmlReader(InputStream input) throws XMLStreamException {
        return NenVaXml.xmlAnToan(input);
    }

    // === Chung ===============================================================

    /**
     * Dòng đầu là tiêu đề. Tên cột chuẩn hoá về <b>không dấu, chữ thường, gạch dưới</b> để tệp ghi
     * "Mã công trình", "ma_cong_trinh" hay "MÃ CÔNG TRÌNH" đều vào đúng một chỗ — người lập tệp
     * không nên phải đoán cách viết mà máy chấp nhận.
     */
    private static List<Row> dungRows(List<List<String>> grid) {
        if (grid.isEmpty()) {
            throw new ValidationException(ErrorCode.SYS_0016);
        }
        List<String> tieuDe =
                grid.get(0).stream().map(SpreadsheetReader::chuanHoaCot).toList();

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < grid.size(); i++) {
            List<String> raw = grid.get(i);
            if (raw.stream().allMatch(o -> o == null || o.isBlank())) {
                continue; // dòng trống giữa bảng là chuyện thường trong tệp Excel người dùng gửi
            }
            // ⛔ Kiểm TRƯỚC khi thêm, và ném — xem khối chú thích ở MAX_ROWS. Số dòng báo ra là số
            //   dòng NHƯ NGƯỜI DÙNG THẤY trong Excel, để họ mở đúng chỗ mà cắt tệp.
            if (rows.size() >= MAX_ROWS) {
                throw new ValidationException(ErrorCode.SYS_0012, MAX_ROWS, i + 1);
            }
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < tieuDe.size(); c++) {
                cells.put(tieuDe.get(c), c < raw.size() ? raw.get(c) : "");
            }
            rows.add(new Row(i + 1, cells));
        }
        return rows;
    }

    private static String chuanHoaCot(String ten) {
        if (ten == null) {
            return "";
        }
        return VietnameseUtils.removeDiacritics(ten.trim())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }
    /**
     * ⚠ Một bản DUY NHẤT của trần giải nén — xem javadoc {@link NenVaXml#docCoTran} (T59.14).
     *
     * <p>⛔ Bản trước giữ một bản sao ngay tại đây, và javadoc của nó khai <i>"ném SYS-0012"</i>
     * trong khi mã ném {@code SYS-0014} — hai mã tách nhau ở WS-62 và chú thích nằm lại. Đó đúng
     * là cách hai bản sao của một biện pháp bảo mật trôi khỏi nhau.
     */
    private static byte[] docCoTran(ZipInputStream zip, String ten) throws java.io.IOException {
        return NenVaXml.docCoTran(zip, ten);
    }
}
