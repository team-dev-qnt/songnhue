package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.hydro.domain.CheDoXemLuoi;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.infra.HydroChartRepository;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Biểu đồ đường 24 giờ của <b>một điểm đo</b> — T35.4, chuỗi thời gian đầu tiên của hệ thống.
 *
 * <h2>⛔⛔ T43.13 — trục thời gian nay dựng TRƯỚC và ĐỘC LẬP với dữ liệu</h2>
 *
 * <p>Trước 09/09/2026 hàm này trả <b>đúng những hàng có trong bảng</b>, và tầng vẽ dựng trục X
 * <i>từ chính mảng ấy</i> ({@code optionDuong(diem.map(d -> moc), …)}). Hệ quả: một mốc mất tín
 * hiệu ⛔ không sinh ra cột nào — nó bị <b>NUỐT</b>, hai mốc cách nhau ba giờ vẽ ra <b>liền kề
 * nhau</b>, và đường cong nối thẳng qua quãng trạm im lặng.
 *
 * <p>⚠ Thứ làm khuyết tật này sống lâu là <b>ba chú thích cùng khẳng định nó đã được xử lý</b>:
 * javadoc của {@code HydroChartRepository.chuoi24h} (<i>"{@code optionDuong} đặt
 * {@code connectNulls: false} chính để khoảng ấy nhìn thấy được"</i>), javadoc của
 * {@code WaterLevelChartPage} (<i>"một quãng trạm mất tín hiệu <b>phải nhìn thấy được</b>"</i>), và
 * chính {@code connectNulls: false} trong {@code chartOptions.ts}. Cả ba đều đúng <i>ý định</i> và
 * cả ba đều vô hiệu, vì <b>⛔ không có {@code null} nào để ngắt</b> — luật 9: một cơ chế ⛔ không
 * phân biệt được hai trạng thái thì ⛔ không bảo đảm điều gì.
 *
 * <p>⇒ Lưới mốc nay do {@link CheDoXemLuoi#dungLuoi} dựng, <b>y hệt</b> đường lưới/biểu đồ của cổng
 * (§6.1.2, §7.1). Mốc ⛔ không có số đo hợp lệ ra dây với {@code giaTri = null} — và <b>chỗ trống
 * ấy</b> mới là thứ {@code connectNulls: false} tác dụng lên.
 *
 * <h2>⭐ T45.9 — vì sao GIỮ hai đường đọc, và ranh giới giữa chúng</h2>
 *
 * <p>{@code HydroGridService.bieuDo()} (cổng công khai) và hàm này (quản trị) cùng đọc
 * {@code hydro_readings} bằng hai câu SQL khác nhau. Quyết định: <b>giữ hai đường, dùng chung một
 * bộ dựng trục</b> ({@link CheDoXemLuoi}) — ⛔ không gộp làm một. Lý do là nghiệp vụ, ⛔ không phải
 * tiện tay:
 *
 * <ul>
 *   <li>đường cổng hỏi <b>một CÔNG TRÌNH × cặp thượng lưu/hạ lưu</b>. Nó ⛔ không trả lời được cho
 *       <b>5/19</b> điểm đo ⛔ không thuộc cặp nào ({@code MN_SONG} ×4, {@code BE_HUT} ×1) — mà đó
 *       đúng là những điểm màn hình quản trị phải xem được;
 *   <li>đường quản trị hỏi <b>một ĐIỂM ĐO × một LOẠI CHỈ SỐ bất kỳ</b>, nên nó là chỗ lượng mưa
 *       (G3-a) cắm vào mà ⛔ không phải đụng đường cổng.
 * </ul>
 *
 * <p>⇒ Thứ <b>đã</b> gộp là cái duy nhất từng lệch được mà ⛔ không ai thấy: <b>trục thời gian</b>.
 * Hai câu SQL trả hai tập hàng khác nhau là đúng thiết kế; hai cách dựng trục là một khuyết tật chờ
 * xảy ra.
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

    /** Bước của trục — nguồn {@code bhh40} trả theo khung 10 phút, nên đây là độ phân giải thật. */
    public static final CheDoXemLuoi CHE_DO = CheDoXemLuoi.PHUT;

    /**
     * Số mốc của cửa sổ — <b>tính ra</b>, ⛔ không viết tay.
     *
     * <p>{@code 24h / 10 phút = 144}. Viết thẳng {@code 144} là dựng chỗ để {@link #CUA_SO} và số
     * cột lệch nhau ngày ai đó đổi cửa sổ; và lệch kiểu ấy ⛔ không đỏ ở đâu cả — nó chỉ làm đường
     * cong cụt bớt một quãng.
     */
    public static final int SO_MOC = (int) (CUA_SO.getSeconds() / CHE_DO.buoc().getSeconds());

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
     * Một <b>mốc</b> trên trục — ⛔ không phải "một số đo".
     *
     * <p>⚠ Khác biệt ấy là toàn bộ nội dung của T43.13: mảng {@code diem} nay là <b>trục thời
     * gian</b>, dựng đủ {@link #SO_MOC} phần tử bất kể có bao nhiêu hàng trong bảng.
     * {@code giaTri == null} nghĩa là <b>mốc ấy ⛔ không có số đo hợp lệ</b> — trạm im lặng, hoặc
     * mọi bản ghi của mốc bị đánh {@code NGHI_NGO}.
     *
     * <p>⛔ {@code null} ⛔ KHÔNG được đổi thành {@code 0} ở bất kỳ tầng nào (quy tắc 16): mực nước
     * {@code 0 m} là một khẳng định về mực nước, còn ⛔ không có số là một khẳng định về đường
     * truyền.
     *
     * <p>⚠ {@code giaTri} ra dây dưới dạng <b>chuỗi</b> ({@code Shape.STRING}): JSON number là
     * {@code double}, và một mực nước {@code 1.005} đi qua {@code double} có thể về thành
     * {@code 1.0049999999999999}. Quy tắc 2 cấm {@code float}/{@code double} cho số đo, và ranh giới
     * ấy ⛔ không dừng ở tầng Java.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DiemBieuDo(Instant moc, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri) {}

    /**
     * @param diem <b>trục thời gian đủ mốc</b>, cũ → mới; ⛔ không bao giờ rỗng
     * @param soMocCoSo số mốc thật sự có số đo hợp lệ — {@code 0} là một trạng thái hợp lệ
     * @param lyDoTrong vì sao ⛔ không có số nào; {@code null} khi có ít nhất một mốc có số. ⛔ Quy
     *     tắc 16 ép ở hàm dựng — một biểu đồ rỗng ⛔ không được im lặng, vì <i>"trạm chưa gửi số"</i>
     *     và <i>"mọi số đều nghi ngờ"</i> vẽ ra <b>cùng một khung trắng</b>
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
            int soMocCoSo,
            String lyDoTrong) {

        public BieuDoMucNuoc {
            if (diem == null) {
                throw new IllegalArgumentException("`diem` ⛔ không được null — rỗng thì là danh sách rỗng");
            }
            // ⛔⛔ T43.13 — bất biến ĐỔI HÌNH DẠNG so với bản trước, và đây là chỗ nói ra vì sao.
            //    Cũ: "diem rỗng ⇔ có lyDoTrong". Nó ⛔ không còn dùng được, vì `diem` nay là TRỤC —
            //    nó đầy ngay cả khi ⛔ không có lấy một số đo. Giữ nguyên bất biến cũ là giữ một
            //    khẳng định luôn đúng, tức ⛔ không khẳng định gì (luật 9).
            if (diem.isEmpty()) {
                throw new IllegalArgumentException(
                        "Biểu đồ '%s': trục thời gian ⛔ không được rỗng — nó dựng ".formatted(maDiemDo)
                                + "ĐỘC LẬP với dữ liệu, nên rỗng nghĩa là bộ dựng lưới hỏng, ⛔ không phải trạm im lặng");
            }
            long coSo = diem.stream().filter(d -> d.giaTri() != null).count();
            if (coSo != soMocCoSo) {
                throw new IllegalArgumentException("Biểu đồ '%s': `soMocCoSo`=%d ⛔ không khớp số ô có giá trị=%d"
                        .formatted(maDiemDo, soMocCoSo, coSo));
            }
            if ((soMocCoSo == 0) != (lyDoTrong != null)) {
                throw new IllegalArgumentException(
                        "Biểu đồ '%s': hoặc CÓ ít nhất một số đo, hoặc CÓ lý do trống — ⛔ không được cả hai, ⛔ không được không cái nào"
                                .formatted(maDiemDo));
            }
        }
    }

    /**
     * Đường cong 24 giờ của <b>một</b> điểm đo và <b>một</b> loại chỉ số.
     *
     * <p>⛔ ⛔ Không có số đo nào ⛔ không phải lỗi và ⛔ không phải 404 — một trạm vừa khai chưa có
     * số là chuyện bình thường. Nó ra kèm <b>lý do</b>, và {@code BaseChart} có sẵn nhánh
     * {@code empty} để hiện câu ấy thay vì vẽ một khung trục rỗng (thứ trông y hệt một biểu đồ mà
     * mọi giá trị bằng 0).
     */
    @Transactional(readOnly = true)
    public BieuDoMucNuoc mucNuoc24h(UUID stationPublicId, String maLoaiChiSo) {
        Station tram = scopeGuard.require(
                diemDo.findByPublicIdAndDeletedAtIsNull(stationPublicId), Station.class, stationPublicId);
        MeasurementType loai = loaiChiSo
                .findByCodeAndDeletedAtIsNull(maLoaiChiSo)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        // ⚠ Lưới dựng TRƯỚC, từ đồng hồ — ⛔ không từ dữ liệu. `dungLuoi` trả mới-nhất-trước (thứ tự
        //   CỘT của bảng §6.1.2); biểu đồ đường thì cần cũ → mới, nên đảo một lần ở đây.
        List<java.time.Instant> mocGiam = CHE_DO.dungLuoi(Instant.now(), SO_MOC);
        List<Instant> moc = new ArrayList<>(mocGiam);
        java.util.Collections.reverse(moc);

        Instant tu = moc.get(0);
        Instant den = moc.get(moc.size() - 1);

        // ⚠ Truy vấn dùng ĐÚNG hai đầu của lưới, ⛔ không dùng `now()` một lần nữa: hai phép đọc
        //   đồng hồ cách nhau vài mili giây đủ để mốc cuối rơi ra ngoài cửa sổ tra, và triệu chứng
        //   là "cột mới nhất thỉnh thoảng trống" — thứ trông y hệt một trạm trễ nhịp.
        // ⛔ Cận trên là mốc cuối CỘNG MỘT BƯỚC và loại trừ: ô 10:50 chứa mọi số đo trong
        //   [10:50, 11:00). Truyền thẳng `den` là loại mất chính những bản ghi thuộc ô mới nhất.
        Map<Instant, BigDecimal> theoMoc = new HashMap<>();
        Instant denLoaiTru = den.plus(CHE_DO.buoc());
        for (HydroChartRepository.DiemChuoi d : kho.chuoi24h(tram.getId(), loai.getId(), tu, denLoaiTru)) {
            // ⚠ Cắt xuống bước của lưới: nguồn trả mốc khung 10 phút, nhưng một bản ghi NHẬP TAY
            //   (`ReadingSource.MANUAL`) có thể mang mốc lẻ. Bỏ `catXuong` thì bản ghi tay ⛔ không
            //   bao giờ khớp một ô nào và biến mất khỏi biểu đồ — im lặng, và chỉ với đúng loại bản
            //   ghi mà người ta nhập vì nó quan trọng.
            // ⚠ `putIfAbsent` + câu SQL sắp DESC ⇒ trong một ô có nhiều hàng thì hàng MỚI NHẤT
            //   thắng. Đảo thứ tự sắp ở kho là lặng lẽ đổi luật ấy thành "cũ nhất thắng".
            theoMoc.putIfAbsent(CHE_DO.catXuong(d.moc()), d.giaTri());
        }

        List<DiemBieuDo> diem =
                moc.stream().map(m -> new DiemBieuDo(m, theoMoc.get(m))).toList();
        int coSo = (int) diem.stream().filter(d -> d.giaTri() != null).count();

        // ⛔ Ba tình huống cho ra một biểu đồ rỗng, và chúng ⛔ không được nói cùng một câu — quy tắc
        //    16. Ở đây phân biệt được hai; tình huống thứ ba ("mọi bản ghi đều NGHI_NGO") cố ý gộp
        //    vào câu thứ hai vì phân biệt nó đòi một lượt truy vấn thứ hai KHÔNG lọc chất lượng, và
        //    một câu SQL không lọc chất lượng nằm cạnh câu có lọc là đúng thứ luật 13 cảnh báo.
        String lyDo = coSo == 0
                ? "Điểm đo ⛔ chưa gửi về số liệu HỢP LỆ nào trong 24 giờ qua — có thể trạm mất tín hiệu, "
                        + "hoặc mọi bản ghi trong khung này đều bị đánh dấu NGHI_NGỜ"
                : null;

        return new BieuDoMucNuoc(
                tram.getCode(), tram.getName(), loai.getName(), loai.getUnit(), tu, den, diem, coSo, lyDo);
    }
}
