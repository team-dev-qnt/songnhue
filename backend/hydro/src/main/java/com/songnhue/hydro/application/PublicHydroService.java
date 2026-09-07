package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.hydro.domain.StationDisplayStatus;
import com.songnhue.hydro.infra.StationMapRepository;

/**
 * Mực nước cho <b>cổng công khai</b> — T35.5 · T35.7.
 *
 * <h2>⛔⛔ Đây là chỗ bài học nặng nhất của dự án nằm (§10.54)</h2>
 *
 * <p>Bản cũ của khối "Mực nước, lượng mưa" trên trang chủ có <b>5 trạm quan trắc viết cứng</b>, kèm
 * mực nước và một mức "Cảnh báo BĐ I" gắn <b>tên cống có thật</b>, kèm chấm "live" nhấp nháy — tất
 * cả đều <b>bịa</b>, và chúng đã lên staging. Không ai nhìn ra đường dữ liệu đã chết hoàn toàn, vì
 * một trang đầy trông như một trang đang chạy.
 *
 * <p>⇒ Mọi con số lớp này trả về đến từ {@code hydro_latest}, ⛔ không từ một hằng số nào. Trạm
 * chưa có số ra <b>ô rỗng kèm lý do</b>, ⛔ không ra {@code 0} và ⛔ không ra một dấu gạch giả làm
 * một phép đo.
 *
 * <h2>Một dòng / ĐIỂM ĐO — chốt với QuanTran 04/09/2026</h2>
 *
 * <p>Tám tiêu đề cột của {@code COT_MUC_NUOC} (CR-13/CR-33, Công ty đã duyệt) mô tả một bảng
 * <i>theo công trình</i>: mỗi dòng một cặp thượng lưu – hạ lưu. Nhưng ghép cặp cần
 * {@code station_constructions}, mà {@code constructions} hôm nay <b>0 dòng</b> (G8) ⇒ bảng theo
 * công trình sẽ ra <b>0 dòng</b>, y hệt hiện trạng.
 *
 * <p>⇒ Mỗi <b>điểm đo</b> một dòng; giá trị rơi vào cột thượng lưu <i>hoặc</i> hạ lưu theo
 * {@code position_role}, cột kia để trống. Bộ cột đã duyệt <b>giữ nguyên</b> — ⛔ không đổi phạm vi
 * công bố mà không báo Công ty. Cổng vì thế có ngay <b>số thật</b> thay vì một khung rỗng.
 *
 * <h2>⛔ Ba trường KHÔNG bao giờ ra tới dây</h2>
 *
 * <p>{@code org_unit_id} · {@code api_code} · {@code api_source_id}. Mã API là <b>khoá đối soát với
 * nguồn bên thứ ba</b>; công bố nó là công bố cách gọi thẳng nguồn của Công ty. Bài kiểm phản chiếu
 * đếm <b>chính xác</b> số thành phần của record để trường thứ tư ⛔ không lặng lẽ đi theo.
 */
@Service
public class PublicHydroService {

    /**
     * Một dòng của bảng "Mực nước, lượng mưa" trên cổng.
     *
     * @param luongMua ⛔ <b>LUÔN null</b> — loại chỉ số "lượng mưa" đã khai nhưng ⛔ chưa gắn cho
     *     điểm đo nào (mục <b>G3-a</b>). ⛔ Đừng {@code ?? 0}: {@code 0 mm} là một câu khẳng định về
     *     thời tiết, và nó sai.
     * @param lyDoTrong vì sao ô số liệu trống; {@code null} khi có số. ⛔ Quy tắc 16 ép ở hàm dựng.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MucNuocRow(
            String tuyenSong,
            String tenDiemDo,
            String maDiemDo,
            String lyTrinh,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mucNuocThuongLuu,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal mucNuocHaLuu,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal luongMua,
            String donVi,
            Instant thoiDiemDo,
            String chatLuong,
            String lyDoTrong,
            String lyDoLuongMua) {

        public MucNuocRow {
            // ⛔ Ép ở HÀM DỰNG, ⛔ không ở lời dặn (quy tắc 16): ô không có số mà không nói được vì
            //    sao sẽ bị đọc thành "bằng không". Dòng thứ hai mươi — thêm bởi người khác, tháng
            //    sau — cũng phải đi qua đúng ràng buộc này mà ⛔ không cần ai nhớ.
            boolean coSo = mucNuocThuongLuu != null || mucNuocHaLuu != null;
            if (coSo == (lyDoTrong != null)) {
                throw new IllegalArgumentException(
                        "Dòng '%s': hoặc CÓ số đo, hoặc CÓ lý do trống — ⛔ không được cả hai, ⛔ không được không cái nào"
                                .formatted(maDiemDo));
            }
            // ⛔ Lượng mưa chưa có nguồn ⇒ luôn phải kèm lý do. Bỏ ràng buộc này là mở đường cho
            //    một cột trống vĩnh viễn mà ⛔ không ai nhớ vì sao nó trống.
            if (luongMua != null || lyDoLuongMua == null) {
                throw new IllegalArgumentException(
                        "Cột lượng mưa chưa có nguồn (G3-a) ⇒ phải rỗng KÈM lý do — xem javadoc");
            }
        }
    }

    /** Lý do cột lượng mưa trống — một chỗ khai, để cổng và báo cáo nói cùng một câu. */
    static final String LY_DO_LUONG_MUA =
            "Chưa có nguồn lượng mưa: loại chỉ số đã khai nhưng chưa gắn cho điểm đo nào (mục G3-a)";

    private static final String CHUA_PHAN_TUYEN = "Chưa phân tuyến";

    private static final Logger log = LoggerFactory.getLogger(PublicHydroService.class);

    private final StationMapRepository repository;
    private final HydroSettings settings;

    public PublicHydroService(StationMapRepository repository, HydroSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /**
     * ⚠ Điểm đo <b>mất tín hiệu vẫn CÓ MẶT</b>, ô số liệu trống kèm lý do.
     *
     * <p>Đó chính là thứ bảng này sinh ra để chỉ ra. Lọc nó đi là để lại một bảng sạch sẽ đúng vào
     * lúc nó phải kêu — và người đọc cổng ⛔ không có cách nào biết một trạm đã im lặng ba ngày.
     *
     * <p>⛔ Điểm đo đã {@code NGUNG} thì ⛔ KHÔNG ra cổng: đó là quyết định của người vận hành
     * ("thôi không dùng trạm này nữa"), khác hẳn "trạm hỏng".
     *
     * <p>⭐ <b>T35.8</b> — khoá {@code hydro.portal.station-codes} lọc thêm một tầng nữa, và
     * ⛔ <b>rỗng nghĩa là TẤT CẢ</b>: xem {@link HydroSettings#maDiemDoLenCong()}. Hai bộ lọc xếp
     * chồng có thứ tự có nghĩa — {@code NGUNG} bị loại <b>trước</b>, nên gõ một mã đã ngừng vào danh
     * sách công bố ⛔ không hồi sinh nó, và mã ấy sẽ nằm trong dòng WARN cuối hàm.
     */
    @Transactional(readOnly = true)
    public List<MucNuocRow> mucNuoc() {
        java.time.Duration khung = settings.khungNguon();
        int soKhung = settings.soKhungMatTinHieu();
        Instant bayGio = Instant.now();

        // ⭐ T35.8 — danh sách công bố. RỖNG nghĩa là TẤT CẢ; xem javadoc `maDiemDoLenCong()`.
        List<String> danhSach = settings.maDiemDoLenCong();
        Set<String> loc = new HashSet<>(danhSach);
        Set<String> daGap = new HashSet<>();

        List<MucNuocRow> ket = new ArrayList<>();
        for (StationMapRepository.DiemDoBanDoRow r : repository.diemDoBanDo()) {
            StationDisplayStatus tt = StationDisplayStatus.suyRa(r.active(), r.mocGanNhat(), bayGio, khung, soKhung);
            if (tt == StationDisplayStatus.NGUNG) {
                continue;
            }
            String ma = r.code() == null ? "" : r.code().toUpperCase(Locale.ROOT);
            if (!loc.isEmpty() && !loc.contains(ma)) {
                continue;
            }
            daGap.add(ma);

            boolean thuongLuu = "THUONG_LUU".equals(r.positionRole());
            BigDecimal gt = r.giaTri();
            String lyDo = null;

            // ⭐⭐ Trạm MẤT TÍN HIỆU ⛔ KHÔNG công bố số cuối như một số HIỆN TẠI.
            //
            // Bản đầu chỉ đặt lý do khi `gt == null`, và bài kiểm bắt ngay: một trạm im lặng 10
            // ngày vẫn còn `valid_value` trong `hydro_latest`, nên cổng hiện mực nước của mười ngày
            // trước — kèm một mốc thời gian mà người đọc vội ⛔ không nhìn. Đó đúng là hình dạng
            // §10.54 ở dạng mới: ⛔ không phải số BỊA, mà là số THẬT đặt sai thì hiện tại.
            //
            // ⚠ Trên một trang thông tin phòng chống thiên tai, một mực nước cũ trông như mực nước
            //   bây giờ là thứ người ta ra quyết định dựa vào.
            //
            // ⛔ Cố ý KHÁC popup bản đồ (T35.1), nơi chấm xám VẪN hiện giá trị cuối: ở đó người đọc
            //    là cán bộ vận hành và có hẳn một cột trạng thái tín hiệu bên cạnh; ở đây người đọc
            //    là người dân và bảng ⛔ không có cột nào nói trạm còn sống hay không.
            if (tt == StationDisplayStatus.MAT_TIN_HIEU) {
                lyDo = gt == null
                        ? "Điểm đo đang mất tín hiệu — chưa từng có số liệu nào"
                        : "Điểm đo đang mất tín hiệu — số liệu chưa cập nhật, ⛔ không dùng số cũ làm mực nước hiện tại";
                gt = null;
            } else if (gt == null) {
                lyDo = tt == StationDisplayStatus.CHUA_CO_DU_LIEU
                        ? "Điểm đo chưa gửi về số liệu nào"
                        : "Chưa có số đo hợp lệ";
            }

            ket.add(new MucNuocRow(
                    // ⛔ `river_name` NULL là G8 chưa về, ⛔ không phải một tuyến tên rỗng.
                    r.riverName() == null || r.riverName().isBlank() ? CHUA_PHAN_TUYEN : r.riverName(),
                    r.name(),
                    r.code(),
                    r.chainage(),
                    thuongLuu ? gt : null,
                    thuongLuu ? null : gt,
                    null,
                    r.donVi(),
                    r.mocDo(),
                    // ⚠ Cổng chỉ công bố giá trị HỢP LỆ (`valid_value`), nên cột này luôn là HOP_LE
                    //   khi có số. Nó vẫn ra dây để người đọc biết con số đã qua kiểm chất lượng.
                    gt == null ? null : "HOP_LE",
                    lyDo,
                    LY_DO_LUONG_MUA));
        }

        // ⭐⭐ Mã gõ nhầm phải KÊU — ⛔ không được lặng lẽ làm bảng ngắn đi.
        //
        // ⚠ Đây là hình dạng lỗi im lặng đặc trưng của khoá này: gõ nhầm một mã trong danh sách 10
        //   mã cho ra 9 dòng, và 9 dòng SỐ THẬT trông y hệt một bảng đúng — ⛔ không có ô rỗng nào,
        //   ⛔ không có dấu gạch nào, ⛔ không có gì để người đọc nghi ngờ. Quy tắc 16 nói "số 0 là
        //   một câu khẳng định"; ở đây cả một DÒNG VẮNG MẶT cũng vậy.
        //
        // ⛔ Cố ý ⛔ KHÔNG ném và ⛔ KHÔNG đưa ra thân phản hồi: đây là endpoint công khai, và một
        //    danh sách mã điểm đo là cấu hình nội bộ. Chỗ đúng của câu này là nhật ký hệ thống, và
        //    mô tả của khoá (người vận hành đọc được) đã trỏ thẳng tới đó.
        if (!loc.isEmpty()) {
            List<String> khongKhop =
                    danhSach.stream().filter(m -> !daGap.contains(m)).toList();
            if (!khongKhop.isEmpty()) {
                log.warn(
                        "⛔ Khoá `{}` có {} mã ⛔ KHÔNG khớp điểm đo nào đang hoạt động: {}. Bảng mực nước "
                                + "trên cổng vì thế thiếu {} dòng — kiểm lại chính tả ở Cấu hình hệ thống › nhóm HYDRO.",
                        HydroSettings.KHOA_DIEM_DO_LEN_CONG,
                        khongKhop.size(),
                        khongKhop,
                        khongKhop.size());
            }
        }

        // ⚠ Sắp theo ĐÚNG thứ tự người vận hành gõ — mô tả của khoá hứa như vậy, và một danh sách
        //   "chọn được nhưng không xếp được" thì lần đầu Công ty dùng đã phải mở lại mã. Danh sách
        //   rỗng ⇒ giữ nguyên thứ tự của câu truy vấn.
        if (!loc.isEmpty()) {
            ket.sort(Comparator.comparingInt(
                    row -> danhSach.indexOf(row.maDiemDo().toUpperCase(Locale.ROOT))));
        }
        return ket;
    }
}
