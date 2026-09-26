package com.songnhue.hydro.infra;

import org.springframework.stereotype.Component;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.TelemetryReading;

/**
 * Nguồn <b>lượng mưa</b> của {@code songnhue.bhh40.net} —
 * {@code GET /api/getluongmua.aspx?key=<mã số>;} (WS-87, mở lại G3-a ngày 26/09/2026).
 *
 * <h2>Số đo trên nguồn thật, 26/09/2026</h2>
 *
 * <ul>
 *   <li><b>15</b> bản ghi, định dạng dòng <b>y hệt</b> nguồn mực nước:
 *       {@code F#####;dd/MM/yyyy;HH:mm;value=<số>;<br>} + trang ASP.NET rỗng ở đuôi.
 *   <li>Giá trị <b>THẬP PHÂN</b> ({@code value=0.0}) — nguồn mực nước trả số nguyên. Regex quy tắc 4
 *       vốn đã nhận {@code -?\d+([.,]\d+)?} nên ⛔ phải đổi.
 *   <li>Đơn vị <b>mm</b>, khớp {@code measurement_types.LUONG_MUA.unit}.
 *   <li>Nhịp <b>1 giờ</b>: lúc 10:41 mốc trả về vẫn là {@code 10:00}.
 *   <li><b>15 mã ⛔ mã nào</b> nằm trong danh mục 19 điểm đo — hai tập RỜI NHAU.
 * </ul>
 *
 * <h2>⛔⛔ Số đo của nguồn này hôm nay đi vào {@code hydro_unmapped_readings}, và đó là ĐÚNG</h2>
 *
 * <p>15 mã chưa được khai thành điểm đo. Ta ⛔ biết chúng ở đâu, tên gì, thuộc công trình nào — đó
 * là <b>G8</b>, việc của Công ty, và bài học đã trả giá là <i>suy điểm đo từ giá trị đo thì sai
 * 1/4 mã</i> ({@code F01705} từng bị đoán là Cống Phủ Lý, thật ra là Vân Đình hạ lưu). ⇒ Số đo được
 * giữ <b>nguyên văn kèm đơn vị nguồn</b>, ⛔ quy đổi, ⛔ đoán: nguồn ⛔ có API lịch sử nên bỏ một
 * ngày là mất một ngày <b>kể cả sau khi</b> Công ty khai mã (quy tắc 18).
 *
 * <h2>⚠⚠ Quy ước KHUNG GIỜ của lượng mưa — CHƯA CHỐT, và cố ý ⛔ chốt ở đây</h2>
 *
 * <p>QuanTran xác nhận 26/09: <i>"lượng mưa tích luỹ theo giờ; bucket 08:00–08:59; gọi trong
 * 09:00–09:59 vẫn là data của 08:00–08:59"</i>. Nhưng phép đo cùng ngày cho mốc <b>{@code 10:00}
 * lúc 10:41</b> — theo câu trên thì lượt ấy phải mang bucket {@code 09:00–09:59}, tức <b>mốc nguồn
 * đi TRƯỚC bucket một giờ</b>. Ba cách đọc còn đứng được:
 *
 * <ol>
 *   <li>mốc = <b>nhãn phát hành</b>, số liệu là giờ liền trước (đúng lời QuanTran);
 *   <li>mốc = <b>đầu bucket đang chạy</b>, giá trị cộng dồn dần trong giờ;
 *   <li>mốc = <b>cuối bucket</b> đã đóng.
 * </ol>
 *
 * <p>Hôm đo <b>mọi giá trị đều {@code 0.0}</b> (⛔ mưa) nên ba cách ⛔ phân biệt được — luật 9.
 * Chọn nhầm là <b>lệch một giờ trên mọi bản ghi mưa</b>, và sai lặng lẽ: một trận mưa bị gán sang
 * giờ bên cạnh vẫn trông hoàn toàn bình thường trên mọi báo cáo.
 *
 * <p>⇒ <b>Hôm nay quy ước ⛔ có nhà, và nó ⛔ cần nhà</b>: {@code measuredAt} được giữ NGUYÊN VĂN
 * xuống {@code hydro_unmapped_readings} cùng {@code raw_value}/{@code raw_unit}/{@code raw_log_id},
 * nên quyết định vẫn lấy lại được đầy đủ về sau. Ngày Công ty khai 15 mã, quy ước phải sống ở
 * <b>ĐÚNG MỘT chỗ</b> — một giá trị miền trên lớp này, đọc bởi job chuyển
 * {@code hydro_unmapped_readings → hydro_readings} mà {@code V202609041059} đã hứa (<i>"dùng ĐÚNG
 * bộ quy đổi của adapter, ⛔ cài lại phép chia lần thứ hai"</i>). ⛔ Đặt ở tầng hiển thị, ⛔ đặt vào
 * một khoá {@code settings}: hai người đọc là hai quy ước.
 *
 * <p>Phép đo phân xử được ba cách đọc, <b>chạy được ngay khi có mưa thật</b>: lấy mẫu 10 phút/lần và
 * ghi {@code (giờ gọi, mốc nguồn, giá trị)} — giá trị của <b>cùng một mốc</b> lớn dần trong giờ ⇒
 * cách 2; đứng yên và mốc nhảy ở đầu giờ ⇒ cách 1 hoặc 3, phân biệt tiếp bằng mốc đầu tiên sau nửa
 * đêm. ⚠ Phải xong <b>trong mùa mưa</b> (T43.21 ghi hạn 31/10).
 */
@Component
public class Bhh40LuongMuaAdapter extends Bhh40Adapter {

    /** Loại chỉ số nguồn này giao — khớp {@code measurement_types.code} (seed V202608311049). */
    static final String MA_LOAI_CHI_SO = "LUONG_MUA";

    /** ⚠ Đường dẫn tương đối — {@code DiaChiNguon} lo dấu {@code /} và phần trùng đoạn. */
    static final String DUONG_DAN = "api/getluongmua.aspx";

    public Bhh40LuongMuaAdapter(HydroApiProperties properties) {
        super(properties);
    }

    @Override
    public AdapterType kieu() {
        return AdapterType.BHH40_MUA;
    }

    @Override
    public String maLoaiChiSo() {
        return MA_LOAI_CHI_SO;
    }

    @Override
    protected String duongDan() {
        return DUONG_DAN;
    }

    @Override
    protected String donViNguon() {
        return TelemetryReading.DON_VI_MM;
    }
}
