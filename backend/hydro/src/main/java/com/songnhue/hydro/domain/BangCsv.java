package com.songnhue.hydro.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dựng một bảng CSV cho bản kết xuất báo cáo — T34.7/T34.8.
 *
 * <h2>⛔ T34.8: KHÔNG thêm Apache POI ở phase này</h2>
 *
 * <p>Quyết định đã chốt, và lý do đo được: POI kéo theo ~12 MB phụ thuộc, mở thêm một bề mặt CVE
 * (POI/XMLBeans/commons-compress đều có lịch sử), và dựng workbook trong bộ nhớ cho một báo cáo
 * hàng nghìn dòng là đúng thứ VPS 2 nhân đang phải tiết kiệm (§8 của {@code hosting_recommendations}).
 * Excel mở CSV được, và mọi thứ Công ty làm với bản kết xuất — lọc, cộng, dán sang báo cáo giấy —
 * ⛔ không cần một tệp {@code .xlsx}.
 *
 * <h2>⚠⚠ Ba quy ước, và cả ba đều để Excel bản tiếng Việt đọc ĐÚNG</h2>
 *
 * <p>Một tệp CSV "chuẩn" (UTF-8 không BOM, phân tách bằng dấu phẩy, số dùng dấu chấm) mở trong Excel
 * cấu hình vi-VN cho ra <b>một cột duy nhất, chữ Việt thành ký tự lạ</b>. Đó ⛔ không phải lỗi của
 * Excel: vi-VN dùng dấu chấm phẩy làm dấu tách danh sách và dấu phẩy làm dấu thập phân.
 *
 * <ol>
 *   <li><b>BOM UTF-8</b> ở đầu tệp — thiếu nó thì Excel đoán bảng mã theo địa phương và
 *       <i>"Cống Liên Mạc"</i> thành <i>"CÃ´ng LiÃªn Máº¡c"</i>.
 *   <li><b>Dấu tách {@code ;}</b> — khớp dấu tách danh sách của vi-VN.
 *   <li><b>Dấu thập phân {@code ,}</b> — ⭐ và nó <b>bắt buộc</b> đi cùng mục 2: nếu giữ dấu chấm
 *       thì Excel vi-VN đọc {@code 4.93} thành <b>493</b>. Một con số sai gấp trăm lần, đúng định
 *       dạng, ⛔ không có cảnh báo nào — đúng hình dạng mọi vụ sai số liệu của dự án này.
 * </ol>
 *
 * <p>⚠ Hai quy ước 2 và 3 là <b>một cặp</b>: đổi một cái mà quên cái kia thì mỗi ô số trở thành một
 * ô trống hoặc một số sai. {@link #so(String)} là nơi duy nhất đổi dấu thập phân, ⛔ đừng làm phép
 * thay thế ấy ở nơi gọi.
 *
 * <p>⚠ Xuống dòng bằng {@code CRLF} theo RFC 4180 — Excel trên Windows đọc {@code LF} trần thành
 * một dòng duy nhất trong vài phiên bản.
 */
public final class BangCsv {

    /** ⚠ Đi thành CẶP với {@link #DAU_THAP_PHAN}. Xem javadoc lớp. */
    static final char DAU_TACH = ';';

    /** ⚠ Đi thành CẶP với {@link #DAU_TACH}. Xem javadoc lớp. */
    static final char DAU_THAP_PHAN = ',';

    private static final String XUONG_DONG = "\r\n";

    /** ⭐ Excel nhận diện bảng mã qua ba byte này. ⛔ Bỏ đi là mọi dấu tiếng Việt hỏng. */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final StringBuilder than = new StringBuilder();
    private int soCot = -1;
    private int soDong;

    /** Thêm một dòng. Dòng đầu tiên chốt số cột; dòng sau lệch số cột là ném ngay. */
    public BangCsv dong(Object... o) {
        if (soCot < 0) {
            soCot = o.length;
        } else if (o.length != soCot) {
            // ⛔ Một tệp CSV lệch số cột vẫn MỞ ĐƯỢC trong Excel — nó chỉ đẩy dữ liệu sang cột bên
            //   cạnh từ dòng ấy trở đi, im lặng. Bắt ở đây thì lỗi hiện ra ở lượt kết xuất, không
            //   hiện ra ở một cuộc họp.
            throw new IllegalStateException(
                    "Dòng " + (soDong + 1) + " có " + o.length + " ô nhưng tiêu đề có " + soCot);
        }
        List<String> o2 = new ArrayList<>(o.length);
        for (Object x : o) {
            o2.add(boc(x == null ? "" : String.valueOf(x)));
        }
        than.append(String.join(String.valueOf(DAU_TACH), o2)).append(XUONG_DONG);
        soDong++;
        return this;
    }

    /** Số dòng đã ghi, <b>kể cả</b> dòng tiêu đề — dùng cho khẳng định chống tập rỗng. */
    public int soDong() {
        return soDong;
    }

    public byte[] byteUtf8Bom() {
        byte[] noiDung = than.toString().getBytes(StandardCharsets.UTF_8);
        byte[] ra = new byte[BOM.length + noiDung.length];
        System.arraycopy(BOM, 0, ra, 0, BOM.length);
        System.arraycopy(noiDung, 0, ra, BOM.length, noiDung.length);
        return ra;
    }

    /**
     * ⭐ Ô số — đổi dấu thập phân sang dấu phẩy. <b>Nơi duy nhất</b> làm việc ấy.
     *
     * @param giaTri chuỗi số như backend phát ra dây ({@code "4.930"}), hoặc {@code null}
     * @return ô rỗng khi {@code null} — ⛔ <b>không</b> phải {@code "0"}: quy tắc 16 áp cho cả bản
     *     kết xuất, và một số 0 trong tệp Excel là thứ người ta cộng vào tổng
     */
    public static String so(String giaTri) {
        return giaTri == null ? "" : giaTri.replace('.', DAU_THAP_PHAN);
    }

    /**
     * ⛔⛔ Bọc ô — và <b>chặn công thức</b> (CSV injection).
     *
     * <p>Một ô bắt đầu bằng {@code =}, {@code +}, {@code -}, {@code @} được Excel thi hành như
     * <b>công thức</b> khi mở tệp, và {@code =cmd|'/c calc'!A1} là một cách chạy lệnh trên máy người
     * mở. Ở đây nguy cơ có thật chứ ⛔ không lý thuyết: cột <i>Lý do</i> của BC-12 mang
     * {@code review_note} — <b>chữ do người dùng gõ</b>.
     *
     * <p>Chặn bằng một dấu nháy đơn đứng trước: Excel hiểu đó là "ô này là văn bản" và ⛔ không hiện
     * dấu nháy ra. ⛔ Đừng chặn bằng cách xoá ký tự đầu — một ghi chú bắt đầu bằng dấu trừ là chuyện
     * bình thường, và xoá nó đi là làm sai nội dung.
     */
    private static String boc(String o) {
        String an = o;
        if (!an.isEmpty() && "=+-@\t\r".indexOf(an.charAt(0)) >= 0) {
            an = "'" + an;
        }
        return '"' + an.replace("\"", "\"\"") + '"';
    }

    @Override
    public String toString() {
        return than.toString();
    }
}
