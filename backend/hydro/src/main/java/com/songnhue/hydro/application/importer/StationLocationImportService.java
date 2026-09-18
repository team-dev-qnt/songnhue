package com.songnhue.hydro.application.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.importer.BieuMauCsv;
import com.songnhue.core.common.importer.CotMau;
import com.songnhue.core.common.importer.KetQuaNhap;
import com.songnhue.core.common.importer.KetQuaNhap.LoiDong;
import com.songnhue.core.common.importer.SpreadsheetReader;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.infra.StationRepository;

/**
 * ⭐⭐ Nhập <b>vị trí</b> 19 điểm đo từ tệp — mục G8 phần còn lại.
 *
 * <h2>Vì sao chức năng này tồn tại</h2>
 *
 * <p>Tính tới 09/09/2026, tuyến sông và lý trình đã có (bản chụp Công ty, {@code V202609091073})
 * nhưng <b>toạ độ vẫn 0/19</b> ⇒ lớp GIS điểm đo RỖNG, và đó là thứ chặn nghiệm thu C3. Đường sửa
 * duy nhất trước đây là mở màn hình điểm đo, sửa <b>từng bản ghi một</b>, 19 lượt.
 *
 * <p>⇒ Ngày Công ty gửi bảng toạ độ, người quản trị chỉ cần <b>tải tệp mẫu → điền → upload</b>, ⛔
 * không cần thêm một đợt lập trình nào. Đây cũng là đường nhập lại khi có đợt đo đạc mới.
 *
 * <h2>⛔ CHỈ cập nhật, ⛔ KHÔNG tạo điểm đo mới</h2>
 *
 * <p>{@code api_code} là <b>khoá nối duy nhất</b> giữa response của nguồn và điểm đo trong hệ, và
 * migration khai thẳng nó <i>BẤT BIẾN sau khi seed</i>. Cho một tệp tạo điểm đo mới là mở đường để
 * một mã gõ sai lặng lẽ sinh ra một trạm ma — nó ⛔ không bao giờ có số liệu, và ⛔ không ai biết nó
 * từ đâu ra. Mã ⛔ không khớp ⇒ <b>một dòng lỗi</b>, và người nhập thấy ngay ở bước chạy khô.
 *
 * <h2>⛔ Một dòng lỗi thì ⛔ không dòng nào được ghi</h2>
 *
 * <p>Cùng luật với nhập danh mục công trình: nhập một nửa rồi dừng là trạng thái tệ nhất — người
 * dùng ⛔ không biết đã vào tới đâu, sửa tệp rồi nhập lại thì phần đầu vào hai lần.
 */
@Service
public class StationLocationImportService {

    private static final Logger log = LoggerFactory.getLogger(StationLocationImportService.class);

    private static final String COT_MA_API = "ma_api";
    private static final String COT_TUYEN = "tuyen_song";
    private static final String COT_LY_TRINH = "ly_trinh";
    private static final String COT_VI_DO = "vi_do";
    private static final String COT_KINH_DO = "kinh_do";

    /**
     * ⚠ Chỉ {@code ma_api} bắt buộc. Bốn cột còn lại <b>bỏ trống được</b>, và ô trống nghĩa là
     * <i>"giữ nguyên giá trị đang có"</i> — ⛔ KHÔNG phải "xoá đi".
     *
     * <p>Đây là khác biệt cố ý so với {@code PUT} một điểm đo (thay-toàn-phần). Tệp nhập thường được
     * lập từng phần — Công ty gửi toạ độ trước, lý trình sau — nên hiểu ô trống là "xoá" sẽ khiến
     * lượt nhập thứ hai <b>xoá mất</b> thứ lượt nhập thứ nhất vừa điền, và ⛔ không có gì nói ra.
     * Muốn xoá một giá trị thì sửa trên màn hình điểm đo, nơi thao tác ấy là một chủ đích rõ ràng.
     */
    public static final List<CotMau> COT_MAU = List.of(
            new CotMau(COT_MA_API, true, "BẮT BUỘC · mã API dạng F##### — phải có sẵn trong danh mục điểm đo"),
            new CotMau(COT_TUYEN, false, "Tên tuyến sông, ví dụ: Sông Nhuệ. Bỏ trống = giữ nguyên"),
            new CotMau(COT_LY_TRINH, false, "Dạng K<km>+<m>, ví dụ K43+750. Bỏ trống = giữ nguyên"),
            new CotMau(COT_VI_DO, false, "Vĩ độ WGS-84, dấu chấm thập phân — ví dụ 21.023456"),
            new CotMau(COT_KINH_DO, false, "Kinh độ WGS-84 — phải có ĐỦ CẢ HAI hoặc bỏ trống cả hai"));

    private static final List<String> COT_BAT_BUOC = CotMau.tenBatBuoc(COT_MAU);

    /** Cùng ràng buộc với {@code ck_stations_chainage_format} — chặn ở đây để báo lỗi theo DÒNG. */
    private static final Pattern DANG_LY_TRINH = Pattern.compile("^K[0-9]+\\+[0-9]{1,3}$");

    private static final Pattern DANG_MA_API = Pattern.compile("^[Ff][0-9]{5}$");

    /** Nhóm hàng nghìn kiểu Việt Nam — xem {@link #so}. */
    private static final Pattern NHOM_HANG_NGHIN = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    private final StationRepository stations;

    public StationLocationImportService(StationRepository stations) {
        this.stations = stations;
    }

    /** Tệp mẫu — xem {@link BieuMauCsv} về vì sao dòng 2 là mô tả chứ ⛔ không phải ví dụ hợp lệ. */
    public static byte[] bieuMau() {
        return BieuMauCsv.dung(COT_MAU);
    }

    /** Xem trước — ⛔ <b>không ghi một dòng nào</b>, kể cả khi tệp hoàn toàn hợp lệ. */
    @Transactional(readOnly = true)
    public KetQuaNhap preview(byte[] content) {
        return lapKeHoach(content).baoCao(false);
    }

    /**
     * Nhập thật.
     *
     * <p>Chạy lại {@link #lapKeHoach} chứ ⛔ không nhận kế hoạch từ lượt xem trước: giữa hai lượt có
     * thể có người vừa sửa một điểm đo trên màn hình, và tin vào kế hoạch cũ là ghi đè thay đổi của
     * họ mà ⛔ không ai biết.
     */
    @Transactional
    public KetQuaNhap apply(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        if (!keHoach.loi.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2016, keHoach.loi.size());
        }
        for (DongKeHoach dong : keHoach.dong) {
            Station s = dong.station;
            if (dong.tuyen != null) {
                s.setRiverName(dong.tuyen);
            }
            if (dong.lyTrinh != null) {
                s.setChainage(dong.lyTrinh);
            }
            if (dong.viDo != null) {
                s.setLatitude(dong.viDo);
                s.setLongitude(dong.kinhDo);
            }
            stations.save(s);
        }
        log.info("Nhập vị trí điểm đo: cập nhật {} bản ghi", keHoach.dong.size());
        return keHoach.baoCao(true);
    }

    // =========================================================================

    private record DongKeHoach(Station station, String tuyen, String lyTrinh, BigDecimal viDo, BigDecimal kinhDo) {}

    private static final class KeHoach {
        private final List<DongKeHoach> dong = new ArrayList<>();
        private final List<LoiDong> loi = new ArrayList<>();
        private int tongDong;

        private KetQuaNhap baoCao(boolean applied) {
            // ⛔ `toCreate` luôn 0 — đường này CHỈ cập nhật. Trả 0 thay vì bỏ trường đi là cố ý:
            //    hộp thoại nhập dùng chung một kiểu, và một ô "Thêm mới: 0" nói đúng sự thật.
            return new KetQuaNhap(applied, tongDong, 0, dong.size(), List.copyOf(loi));
        }
    }

    private KeHoach lapKeHoach(byte[] content) {
        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(content);
        KeHoach keHoach = new KeHoach();
        keHoach.tongDong = rows.size();

        if (rows.isEmpty()) {
            keHoach.loi.add(new LoiDong(1, null, "Tệp không có dòng dữ liệu nào"));
            return keHoach;
        }
        Set<String> cotCo = rows.get(0).cells().keySet();
        List<String> thieu =
                COT_BAT_BUOC.stream().filter(c -> !cotCo.contains(c)).toList();
        if (!thieu.isEmpty()) {
            keHoach.loi.add(new LoiDong(1, String.join(", ", thieu), "Tệp thiếu cột bắt buộc"));
            return keHoach;
        }

        // ⛔ Trùng mã NGAY TRONG tệp: hai dòng cùng mã thì dòng sau ghi đè dòng trước và người nhập
        //    ⛔ không bao giờ biết mình vừa mất một dòng dữ liệu.
        Set<String> maDaGap = new HashSet<>();

        for (SpreadsheetReader.Row row : rows) {
            doc(row, keHoach, maDaGap);
        }
        return keHoach;
    }

    private void doc(SpreadsheetReader.Row row, KeHoach keHoach, Set<String> maDaGap) {
        int soDong = row.rowNumber();
        List<LoiDong> loi = new ArrayList<>();

        String ma = row.get(COT_MA_API);
        Station station = null;
        if (ma == null) {
            loi.add(new LoiDong(soDong, COT_MA_API, "Thiếu mã API"));
        } else if (!DANG_MA_API.matcher(ma).matches()) {
            loi.add(new LoiDong(soDong, COT_MA_API, "Mã API phải có dạng F##### — nhận được '%s'".formatted(ma)));
        } else if (!maDaGap.add(ma.toUpperCase(Locale.ROOT))) {
            loi.add(new LoiDong(soDong, COT_MA_API, "Mã '%s' xuất hiện nhiều lần trong tệp".formatted(ma)));
        } else {
            station = stations.findByApiCodeAndDeletedAtIsNull(ma.toUpperCase(Locale.ROOT))
                    .orElse(null);
            if (station == null) {
                // ⛔ ⛔ KHÔNG tạo mới — xem khối chú thích ở đầu lớp.
                loi.add(new LoiDong(
                        soDong,
                        COT_MA_API,
                        "Không có điểm đo mang mã '%s'. Đường này chỉ CẬP NHẬT vị trí, ⛔ không tạo điểm đo mới"
                                .formatted(ma)));
            }
        }

        String lyTrinh = row.get(COT_LY_TRINH);
        if (lyTrinh != null && !DANG_LY_TRINH.matcher(lyTrinh).matches()) {
            loi.add(new LoiDong(
                    soDong,
                    COT_LY_TRINH,
                    "Lý trình phải có dạng K<km>+<m>, ví dụ K43+750 — nhận được '%s'".formatted(lyTrinh)));
        }

        BigDecimal viDo = so(row.get(COT_VI_DO), soDong, COT_VI_DO, loi);
        BigDecimal kinhDo = so(row.get(COT_KINH_DO), soDong, COT_KINH_DO, loi);
        if ((viDo == null) != (kinhDo == null)) {
            // ⛔ Một nửa toạ độ là một điểm SAI trên bản đồ điều hành — tệ hơn hẳn chưa số hoá.
            loi.add(new LoiDong(soDong, COT_VI_DO, "Toạ độ phải có ĐỦ cả vĩ độ và kinh độ, hoặc bỏ trống cả hai"));
        }
        if (viDo != null && (viDo.compareTo(new BigDecimal("-90")) < 0 || viDo.compareTo(new BigDecimal("90")) > 0)) {
            loi.add(new LoiDong(soDong, COT_VI_DO, "Vĩ độ ngoài khoảng [-90, 90]: '%s'".formatted(viDo)));
        }
        if (kinhDo != null
                && (kinhDo.compareTo(new BigDecimal("-180")) < 0 || kinhDo.compareTo(new BigDecimal("180")) > 0)) {
            loi.add(new LoiDong(soDong, COT_KINH_DO, "Kinh độ ngoài khoảng [-180, 180]: '%s'".formatted(kinhDo)));
        }

        if (!loi.isEmpty() || station == null) {
            keHoach.loi.addAll(loi);
            return;
        }
        keHoach.dong.add(new DongKeHoach(station, row.get(COT_TUYEN), lyTrinh, viDo, kinhDo));
    }

    /**
     * Đọc số từ ô do người dùng gõ.
     *
     * <p>⚠⚠ <b>Dấu chấm là chỗ nguy hiểm nhất của cả lượt nhập.</b> Tiếng Việt dùng "." ngăn hàng
     * nghìn, trong khi toạ độ GPS luôn viết "21.023456" với "." là dấu thập phân. Quy tắc "bỏ hết
     * dấu chấm" biến vĩ độ 21,023456 thành <b>21023456</b> — một điểm ở giữa đại dương.
     *
     * <p>⚠ Ràng buộc CHECK ở CSDL bắt được vĩ độ ngoài [-90, 90], nhưng ⛔ đừng dựa vào đó: nó ⛔
     * không bắt được sai số nhỏ hơn, và một công trình đặt lệch vài trăm mét trên bản đồ điều hành
     * thì ⛔ không ai phát hiện bằng mắt. Cùng cách phân biệt với
     * {@code ConstructionImportService.so} — <b>theo hình dạng</b>, ⛔ không theo ngôn ngữ.
     */
    private static BigDecimal so(String value, int soDong, String cot, List<LoiDong> loi) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sach = value.replaceAll("[\\s\\u00a0]", "");
        if (sach.contains(".") && sach.contains(",")) {
            sach = sach.replace(".", "").replace(",", ".");
        } else if (NHOM_HANG_NGHIN.matcher(sach).matches()) {
            sach = sach.replace(".", "");
        } else {
            sach = sach.replace(",", ".");
        }
        try {
            return new BigDecimal(sach);
        } catch (NumberFormatException e) {
            loi.add(new LoiDong(soDong, cot, "Không phải số: '%s'".formatted(value)));
            return null;
        }
    }
}
