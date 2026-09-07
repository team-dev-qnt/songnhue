package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.infra.HydroChartRepository;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Biểu đồ đường 24 giờ — <b>T35.4</b>, chuỗi thời gian đầu tiên của hệ thống.
 *
 * <h2>Vì sao {@code optionDuong} được NỐI chứ ⛔ không bị XOÁ</h2>
 *
 * <p>{@code chartOptions.ts} có hàm {@code optionDuong} từ Phase 1 với <b>0 nơi gọi</b> ngoài bài
 * kiểm của chính nó, và javadoc của nó tự đặt hạn: <i>"⛔ Nếu Phase 2 đến mà vẫn không ai gọi thì
 * phải XOÁ, không phải giữ"</i> (§10.33). Phase 2 đã đến <b>và mang theo người gọi thật</b> —
 * {@code hydro_readings} nay có số liệu. ⇒ nối, ⛔ không gia hạn thêm một lần nữa.
 *
 * <h2>⛔ Cửa sổ CHỐT 24 giờ, ⛔ không nhận khoảng ngày</h2>
 *
 * <p>Endpoint này ⛔ không có tham số {@code tuNgay}/{@code denNgay}, và đó là một <b>ràng buộc</b>
 * chứ ⛔ không phải một thiếu sót:
 *
 * <ul>
 *   <li>nó là thứ giữ cho ngoại lệ quy tắc 8 ở {@link HydroChartRepository} còn nhỏ — nhận khoảng
 *       ngày là mở đúng cánh cửa mà ngoại lệ ấy được cấp phép với điều kiện đóng lại;
 *   <li>biểu nhiều ngày là một <b>câu hỏi khác</b> và đọc một <b>bảng khác</b>
 *       ({@code hydro_agg_daily}). Gộp hai câu hỏi vào một endpoint là mời người sau nới cửa sổ
 *       thay vì viết câu SQL đúng.
 * </ul>
 *
 * <h2>⚠ Phạm vi đơn vị đi qua {@link ScopeGuard} như mọi tra cứu theo {@code public_id}</h2>
 *
 * <p>Một biểu đồ mực nước là <b>số liệu vận hành</b> của một Xí nghiệp. Bỏ qua {@code ScopeGuard} ở
 * đây là dựng một endpoint đọc rộng hơn mọi endpoint khác của cùng dữ liệu — đúng hình dạng lỗ tầng
 * 3 phân quyền mà {@code conventions.md} §4.2 mô tả.
 */
@Service
public class HydroChartService {

    /**
     * ⛔ 24 giờ, ghi <b>một chỗ</b> và dùng cho cả phép tra lẫn nhãn hiển thị.
     *
     * <p>Hai hằng số cho cùng một cửa sổ là chỗ biểu đồ ghi <i>"24 giờ qua"</i> trong khi truy vấn
     * lấy 12 — và ⛔ không ai nhìn ra, vì một đường cong ngắn hơn trông y hệt một trạm ít số liệu.
     */
    public static final Duration CUA_SO = Duration.ofHours(24);

    private final HydroChartRepository kho;
    private final StationRepository diemDo;
    private final MeasurementTypeRepository loaiChiSo;
    private final ScopeGuard scopeGuard;

    public HydroChartService(
            HydroChartRepository kho,
            StationRepository diemDo,
            MeasurementTypeRepository loaiChiSo,
            ScopeGuard scopeGuard) {
        this.kho = kho;
        this.diemDo = diemDo;
        this.loaiChiSo = loaiChiSo;
        this.scopeGuard = scopeGuard;
    }

    /**
     * Một điểm trên đường cong.
     *
     * <p>⚠ {@code giaTri} ra dây dưới dạng <b>chuỗi</b> ({@code Shape.STRING}): JSON number là
     * {@code double}, và một mực nước {@code 1.005} đi qua {@code double} có thể về thành
     * {@code 1.0049999999999999}. Quy tắc 2 cấm {@code float}/{@code double} cho số đo, và ranh giới
     * ấy ⛔ không dừng ở tầng Java.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DiemBieuDo(Instant moc, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri) {}

    /**
     * @param lyDoTrong vì sao ⛔ không có điểm nào; {@code null} khi có dữ liệu. ⛔ Quy tắc 16 ép ở
     *     hàm dựng — một biểu đồ rỗng ⛔ không được im lặng, vì <i>"trạm chưa gửi số"</i> và
     *     <i>"mọi số đều nghi ngờ"</i> vẽ ra <b>cùng một khung trắng</b>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BieuDoMucNuoc(
            String maDiemDo,
            String tenDiemDo,
            String tenChiSo,
            String donVi,
            Instant tu,
            Instant den,
            List<DiemBieuDo> diem,
            String lyDoTrong) {

        public BieuDoMucNuoc {
            if (diem == null) {
                throw new IllegalArgumentException("`diem` ⛔ không được null — rỗng thì là danh sách rỗng");
            }
            if (diem.isEmpty() == (lyDoTrong == null)) {
                throw new IllegalArgumentException(
                        "Biểu đồ '%s': hoặc CÓ điểm, hoặc CÓ lý do trống — ⛔ không được cả hai, ⛔ không được không cái nào"
                                .formatted(maDiemDo));
            }
        }
    }

    /**
     * Đường cong 24 giờ của <b>một</b> điểm đo và <b>một</b> loại chỉ số.
     *
     * <p>⛔ Danh sách rỗng ⛔ không phải lỗi và ⛔ không phải 404 — một trạm vừa khai chưa có số là
     * chuyện bình thường. Nó ra kèm <b>lý do</b>, và {@code BaseChart} có sẵn nhánh {@code empty}
     * để hiện câu ấy thay vì vẽ một khung trục rỗng (thứ trông y hệt một biểu đồ mà mọi giá trị
     * bằng 0).
     */
    @Transactional(readOnly = true)
    public BieuDoMucNuoc mucNuoc24h(UUID stationPublicId, String maLoaiChiSo) {
        Station tram = scopeGuard.require(
                diemDo.findByPublicIdAndDeletedAtIsNull(stationPublicId), Station.class, stationPublicId);
        MeasurementType loai = loaiChiSo
                .findByCodeAndDeletedAtIsNull(maLoaiChiSo)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        Instant den = Instant.now();
        Instant tu = den.minus(CUA_SO);
        List<DiemBieuDo> diem = kho.chuoi24h(tram.getId(), loai.getId(), tu, den).stream()
                .map(d -> new DiemBieuDo(d.moc(), d.giaTri()))
                .toList();

        // ⛔ Ba tình huống cho ra một biểu đồ rỗng, và chúng ⛔ không được nói cùng một câu — quy tắc
        //    16. Ở đây phân biệt được hai; tình huống thứ ba ("mọi bản ghi đều NGHI_NGO") cố ý gộp
        //    vào câu thứ hai vì phân biệt nó đòi một lượt truy vấn thứ hai KHÔNG lọc chất lượng, và
        //    một câu SQL không lọc chất lượng nằm cạnh câu có lọc là đúng thứ luật 13 cảnh báo.
        String lyDo = diem.isEmpty()
                ? "Điểm đo ⛔ chưa gửi về số liệu HỢP LỆ nào trong 24 giờ qua — có thể trạm mất tín hiệu, "
                        + "hoặc mọi bản ghi trong khung này đều bị đánh dấu NGHI_NGỜ"
                : null;

        return new BieuDoMucNuoc(tram.getCode(), tram.getName(), loai.getName(), loai.getUnit(), tu, den, diem, lyDo);
    }
}
