package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.hydro.domain.CheDoXemLuoi;
import com.songnhue.hydro.infra.HydroGridRepository;

/**
 * Bảng lưới mực nước theo <b>tuyến sông → công trình → chỉ tiêu</b> — WS-43 / T43.5,
 * spec-description.md §5.2 và §6.1.2.
 *
 * <h2>Vì sao đây ⛔ không phải một cái cây theo nghĩa {@code TreeBuilder}</h2>
 *
 * <p>Kho có {@code core/common/tree/TreeBuilder} và nó ghép <b>cha–con theo {@code parent_id}</b>
 * (org_units · categories · media_folders · menu_items — đúng 4 bảng có cột ấy). Cấu trúc ở đây là
 * <b>gộp nhóm trên một phép nối</b>: ba tầng đến từ ba <i>cột khác nhau</i> của cùng một hàng
 * ({@code river_name} → {@code structure_code} → {@code position_role}), ⛔ không từ một quan hệ tự
 * tham chiếu.
 *
 * <p>⛔ Đừng thêm {@code parent_id} vào {@code stations} để dùng lại {@code TreeBuilder}: một điểm
 * đo ⛔ không phải con của một điểm đo khác, và cột ấy sẽ phải được giữ đồng bộ bằng tay với ba cột
 * đã mang sẵn thông tin — đúng hình dạng luật 14 (hai nơi phải nhớ cùng một sự thật).
 *
 * <h2>⛔⛔ Lưới dựng TRƯỚC, dữ liệu rót SAU</h2>
 *
 * <p>{@link CheDoXemLuoi#dungLuoi} sinh danh sách mốc <b>độc lập với dữ liệu</b>, rồi mỗi ô được
 * tra theo mốc. Đó là chỗ chữa <b>T43.13</b>: dựng trục từ chính mảng điểm trả về thì mốc mất dữ
 * liệu bị <b>nuốt</b> thay vì để lại một ô trống, và một khoảng mất tín hiệu ba giờ trông như hai
 * mốc liền kề.
 *
 * <h2>Dòng "Chênh lệch" là dòng TÍNH, và nó tính ở đây</h2>
 *
 * <p>Quy tắc 3: mọi giá trị tính toán tính ở BE. Spec §3.2 thêm hai ràng buộc, cả hai ép ở
 * {@link DongChiSo}: chỉ tính khi <b>cả hai</b> vế khác {@code null}, và ⛔ <b>không suy diễn,
 * ⛔ không gán 0</b>.
 */
@Service
public class HydroGridService {

    /** Mã loại chỉ số mực nước — cùng một chuỗi với {@code TelemetryIngestService}. */
    public static final String MA_MUC_NUOC = "MUC_NUOC";

    /** Số cột mặc định của chế độ 10 phút — spec §6.1.1 (<i>"cửa sổ 12 mốc gần nhất"</i>). */
    public static final int SO_COT_MAC_DINH = 12;

    /**
     * Số mốc mặc định của <b>biểu đồ</b> — 144 = trọn một ngày ở nhịp 10 phút (§7.1).
     *
     * <p>⛔ Cố ý KHÁC {@link #SO_COT_MAC_DINH}. Bảng và biểu đồ trả lời hai câu hỏi khác nhau:
     * bảng hỏi <i>"bây giờ bao nhiêu"</i> nên 12 mốc là đủ và 144 cột thì ⛔ không đọc nổi; biểu đồ
     * hỏi <i>"nước lên từ lúc mấy giờ"</i> nên nó cần cả ngày. Dùng chung một hằng số là ép một
     * trong hai câu hỏi phải sai.
     */
    public static final int SO_COT_BIEU_DO = 144;

    private static final String CHUA_PHAN_TUYEN = "Chưa phân tuyến";

    private final HydroGridRepository kho;
    private final HydroSettings settings;

    public HydroGridService(HydroGridRepository kho, HydroSettings settings) {
        this.kho = kho;
        this.settings = settings;
    }

    // =========================================================================
    // DTO — bất biến ép ở HÀM DỰNG, ⛔ không ở lời dặn (quy tắc 16)
    // =========================================================================

    /**
     * Khối {@code meta} của spec §10.
     *
     * <p>⚠ <b>Cố ý nằm TRONG {@code data}</b>, ⛔ không phải ở {@code meta} của envelope — quyết
     * định Q5 ngày 09/09/2026 ({@code phase3-plan.md} §5.1). Envelope dùng chung cho cả 5 module và
     * {@code ApiResponse.meta} là {@code PageMeta} bốn trường cứng; nới nó để phục vụ một module là
     * đổi rủi ro của năm module lấy sự đúng-chữ của một tài liệu. ⇒ Hình dạng ở đây <b>lệch chữ</b>
     * spec §10 một tầng, và dòng này là chỗ ghi lại điều đó.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MetaLuoi(
            Instant lanLayCuoi, Instant mocDoGanNhat, String trangThaiNguon, String donVi, String lyDoLuongMua) {

        /** Nguồn đang trả số đúng nhịp. */
        public static final String OK = "OK";

        /** Có dữ liệu, nhưng mốc đo gần nhất đã quá hạn — §8.2 hiện dải cảnh báo. */
        public static final String CHAM = "DEGRADED";

        /** Chưa từng có số đo nào — §8.1. */
        public static final String MAT = "DOWN";

        /**
         * Suy trạng thái nguồn từ <b>mốc ĐO gần nhất</b> — hàm <b>thuần</b>, cố ý.
         *
         * <p>⛔ Vì sao ⛔ không để phép suy này nằm trong service: bài kiểm ba trạng thái khi ấy
         * phải <b>xoá sạch {@code hydro_latest}</b> để dựng được nhánh {@code DOWN}, mà bảng ấy là
         * fixture dùng chung của nhiều lớp kiểm khác — và triệu chứng sẽ là "bài kiểm đỏ theo thứ
         * tự chạy", đúng thứ {@code make ci-order} sinh ra để bắt (§11.19). Hàm thuần thì cả ba
         * nhánh kiểm được mà ⛔ không chạm CSDL.
         *
         * @param mocDoGanNhat {@code null} = chưa từng có số đo nào
         * @param hanCu quá hạn này thì coi là CHẬM; truyền từ {@code khungNguon × soKhungMatTinHieu}
         */
        public static String trangThai(Instant mocDoGanNhat, Instant bayGio, Duration hanCu) {
            if (mocDoGanNhat == null) {
                return MAT;
            }
            return mocDoGanNhat.isBefore(bayGio.minus(hanCu)) ? CHAM : OK;
        }

        public MetaLuoi {
            if (!OK.equals(trangThaiNguon) && !CHAM.equals(trangThaiNguon) && !MAT.equals(trangThaiNguon)) {
                throw new IllegalArgumentException(
                        "trangThaiNguon phải là OK | DEGRADED | DOWN, nhận: " + trangThaiNguon);
            }
            // ⛔ MAT nghĩa là CHƯA TỪNG có số đo. Một `mocDoGanNhat` khác null đi cùng MAT là hai
            //    khẳng định trái nhau ra dây cùng lúc, và cổng sẽ hiện §8.1 ("chưa đấu nối") trên
            //    một hệ đang có dữ liệu — luật 9.
            if (MAT.equals(trangThaiNguon) != (mocDoGanNhat == null)) {
                throw new IllegalArgumentException(
                        "DOWN ⇔ chưa có mốc đo nào. Đang có trangThaiNguon=%s mà mocDoGanNhat=%s"
                                .formatted(trangThaiNguon, mocDoGanNhat));
            }
        }
    }

    /**
     * Một ô của lưới.
     *
     * <p>⛔ <b>Hoặc</b> có số, <b>hoặc</b> có lý do — quy tắc 16. Một ô trống không lý do sẽ bị đọc
     * thành <i>"bằng không"</i>, và spec §6.2 nói thẳng hai trạng thái ấy khác nhau về nghiệp vụ.
     *
     * @param chatLuong {@code HOP_LE} | {@code NGHI_NGO}; {@code null} khi ô ⛔ không có số. Ô
     *     {@code NGHI_NGO} ra dây <b>kèm số</b> để §6.1.2 tô vàng + dấu ⚠ — xem ngoại lệ có tên ở
     *     {@code HydroGridRepository}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OLuoi(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri,
            String chatLuong,
            String lyDo,
            String khoaMauCanhBao,
            String tenMucCanhBao) {

        static final OLuoi TRONG = new OLuoi(null, null, "Không có dữ liệu tại mốc này", null, null);

        /** Ô có số, chưa xét ngưỡng. */
        static OLuoi coSo(BigDecimal giaTri, String chatLuong) {
            return new OLuoi(giaTri, chatLuong, null, null, null);
        }

        /** Bản sao mang thêm bậc ngưỡng — §5.3. */
        OLuoi voiMuc(String khoaMau, String tenMuc) {
            return new OLuoi(giaTri, chatLuong, lyDo, khoaMau, tenMuc);
        }

        public OLuoi {
            if ((giaTri == null) == (lyDo == null)) {
                throw new IllegalArgumentException(
                        "Ô lưới: hoặc CÓ giá trị, hoặc CÓ lý do trống — ⛔ không được cả hai, ⛔ không được không cái nào");
            }
            if ((giaTri != null) != (chatLuong != null)) {
                throw new IllegalArgumentException("Ô có số thì phải có nhãn chất lượng đi kèm, và ngược lại");
            }
            // ⛔ Ô ⛔ không có số mà mang màu cảnh báo là tô một ô TRỐNG thành đỏ — người đọc sẽ tin
            //    chỗ ấy đang vượt ngưỡng, trong khi hệ thống ⛔ không đo được gì.
            if (giaTri == null && khoaMauCanhBao != null) {
                throw new IllegalArgumentException("Ô ⛔ không có số thì ⛔ không được mang màu cảnh báo");
            }
            // Hai nửa của một nhãn: thiếu một nửa thì hoặc có màu mà ⛔ không nói được mức nào,
            // hoặc có tên mức mà ⛔ không tô được gì (luật 27).
            if ((khoaMauCanhBao == null) != (tenMucCanhBao == null)) {
                throw new IllegalArgumentException("`khoaMauCanhBao` và `tenMucCanhBao` đi thành cặp");
            }
        }
    }

    /** Loại dòng — phân biệt số ĐO được với số TÍNH ra, vì §6.1.2 in chúng khác nhau. */
    public enum LoaiDong {
        /** Số đo đọc từ cảm biến. */
        DO,
        /** Dòng tự tính (Chênh lệch) — in nghiêng, nền xám, ⛔ không tô màu ngưỡng. */
        TINH
    }

    /**
     * Một dòng của bảng — Thượng lưu · Hạ lưu · <i>Chênh lệch</i> · MN Bể hút · MN sông.
     *
     * @param o đúng bằng số mốc của lưới; xem ràng buộc ở {@link LuoiMucNuoc}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DongChiSo(String chiTieu, LoaiDong loai, List<OLuoi> o) {
        public DongChiSo {
            o = List.copyOf(Objects.requireNonNull(o, "`o` ⛔ không được null"));
        }
    }

    /** Một công trình — gộp ô 3 cột đầu ở §6.1.2, và mang 1–3 dòng chỉ tiêu. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CongTrinh(
            String maCongTrinh, String tenCongTrinh, String lyTrinh, boolean trucChinh, List<DongChiSo> dong) {
        public CongTrinh {
            dong = List.copyOf(Objects.requireNonNull(dong, "`dong` ⛔ không được null"));
            if (dong.isEmpty()) {
                throw new IllegalArgumentException(
                        "Công trình '%s' ⛔ không có dòng chỉ tiêu nào — một công trình rỗng ⛔ không được lên bảng"
                                .formatted(maCongTrinh));
            }
        }
    }

    /** Nhóm tuyến sông — cột được gộp ô ở §5.2. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NhomTuyenSong(String tenTuyen, List<CongTrinh> congTrinh) {
        public NhomTuyenSong {
            congTrinh = List.copyOf(Objects.requireNonNull(congTrinh, "`congTrinh` ⛔ không được null"));
        }
    }

    /**
     * Toàn bộ bảng lưới.
     *
     * @param lyDoTrong vì sao ⛔ không có nhóm nào; {@code null} khi có dữ liệu — cùng khuôn với
     *     {@code HydroChartService.BieuDoMucNuoc}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LuoiMucNuoc(MetaLuoi meta, List<Instant> moc, List<NhomTuyenSong> tuyenSong, String lyDoTrong) {

        public LuoiMucNuoc {
            moc = List.copyOf(Objects.requireNonNull(moc, "`moc` ⛔ không được null"));
            tuyenSong = List.copyOf(Objects.requireNonNull(tuyenSong, "`tuyenSong` ⛔ không được null"));

            if (tuyenSong.isEmpty() == (lyDoTrong == null)) {
                throw new IllegalArgumentException(
                        "Lưới: hoặc CÓ nhóm tuyến sông, hoặc CÓ lý do trống — ⛔ không được cả hai, ⛔ không được không cái nào");
            }

            // ⛔⛔ LƯỚI PHẢI CHỮ NHẬT. Một dòng thiếu ô là các cột lệch nhau kể từ chỗ thiếu, và
            //    triệu chứng của nó là một con số đúng nằm dưới một nhãn giờ SAI — thứ ⛔ không ai
            //    nhìn ra bằng mắt, trên đúng một bảng mà người ta đọc để ra quyết định vận hành.
            //    Ép ở đây vì đây là nơi DUY NHẤT biết cả số mốc lẫn từng dòng.
            int soMoc = moc.size();
            for (NhomTuyenSong nhom : tuyenSong) {
                for (CongTrinh ct : nhom.congTrinh()) {
                    for (DongChiSo d : ct.dong()) {
                        if (d.o().size() != soMoc) {
                            throw new IllegalArgumentException(
                                    "Dòng '%s' của công trình '%s' có %d ô trong khi lưới có %d mốc — bảng sẽ lệch cột"
                                            .formatted(d.chiTieu(), ct.maCongTrinh(), d.o().size(), soMoc));
                        }
                    }
                }
            }
        }
    }

    /**
     * Một đường ngưỡng ngang trên biểu đồ — §7.1 (<i>"3 đường ngang đứt nét BĐ1/BĐ2/BĐ3"</i>).
     *
     * <p>⚠ Ngưỡng thuộc <b>từng điểm đo</b>, ⛔ không thuộc công trình: thượng lưu và hạ lưu của
     * cùng một cống có thể khai hai bộ ngưỡng khác nhau (§5.3 — <i>"⛔ không dùng ngưỡng của điểm
     * khác"</i>). Nên mỗi đường mang theo {@code chiTieu} của nó, và biểu đồ vẽ chúng trên đúng
     * đường cong tương ứng.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DuongNguong(
            String chiTieu,
            String tenMuc,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri,
            String khoaMau) {}

    /**
     * Dữ liệu vẽ biểu đồ diễn biến của <b>một công trình</b> — spec §7.1.
     *
     * <p>⛔ Dùng lại {@link CongTrinh} thay vì dựng một hình dạng riêng cho biểu đồ. Cùng một
     * {@code dong[].o[]} đã căn theo {@code moc}, cùng nhãn chất lượng, cùng bậc ngưỡng — nên bảng
     * và biểu đồ ⛔ không thể vẽ hai con số khác nhau về cùng một mốc. Một DTO riêng cho biểu đồ là
     * chỗ hai đường đọc lệch nhau vào ngày ai đó sửa một bên.
     *
     * @param nguong đường ngưỡng của <b>mọi</b> chỉ tiêu trong công trình; rỗng khi chưa khai
     *     (G9-a) — biểu đồ khi ấy vẽ đường cong mà ⛔ không vẽ đường ngang nào
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BieuDoCongTrinh(
            MetaLuoi meta, List<Instant> moc, CongTrinh congTrinh, List<DuongNguong> nguong, String lyDoTrong) {

        public BieuDoCongTrinh {
            moc = List.copyOf(Objects.requireNonNull(moc, "`moc` ⛔ không được null"));
            nguong = List.copyOf(Objects.requireNonNull(nguong, "`nguong` ⛔ không được null"));
            // ⛔ Cùng khuôn với `BieuDoMucNuoc` của T35.4: hoặc CÓ công trình, hoặc CÓ lý do trống.
            //    §7.3 cấm vẽ một khung trục rỗng — nó trông y hệt một biểu đồ mà mọi giá trị bằng 0.
            if ((congTrinh == null) != (lyDoTrong != null)) {
                throw new IllegalArgumentException(
                        "Biểu đồ: hoặc CÓ công trình, hoặc CÓ lý do trống — ⛔ không được cả hai, ⛔ không được không cái nào");
            }
            if (congTrinh != null) {
                for (DongChiSo d : congTrinh.dong()) {
                    if (d.o().size() != moc.size()) {
                        throw new IllegalArgumentException(
                                "Dòng '%s' có %d điểm trong khi trục thời gian có %d mốc — đường cong sẽ lệch trục"
                                        .formatted(d.chiTieu(), d.o().size(), moc.size()));
                    }
                }
            }
        }
    }

    // =========================================================================
    // Dựng bảng
    // =========================================================================

    /**
     * @param cheDo chế độ xem — quyết định bước của lưới
     * @param soCot số cột; {@code <= 0} thì dùng {@link #SO_COT_MAC_DINH}
     * @param chiTrucChinh {@code true} = chỉ công trình trên trục chính (khối trang chủ §5.2);
     *     {@code false} = toàn bộ (trang chi tiết §6).
     *     <p>⭐ <b>⛔ Không điểm đo nào được tick trục chính ⇒ công bố TẤT CẢ.</b> Cùng quy ước với
     *     khoá {@code hydro.portal.station-codes} (V202609041064: <i>"để trống nghĩa là công bố tất
     *     cả, ⛔ không phải không công bố gì"</i>). OI-C — <i>10 cống trục chính nào</i> — chưa có
     *     câu trả lời, nên hôm nay {@code is_main_axis} còn {@code FALSE} ở cả 19 điểm đo; hiểu nó
     *     là "⛔ không công bố gì" sẽ cho ra một khối trang chủ <b>rỗng</b> — hình dạng §10.79, thứ
     *     chỉ lộ ra trên production.
     */
    @Transactional(readOnly = true)
    public LuoiMucNuoc luoi(CheDoXemLuoi cheDo, int soCot, boolean chiTrucChinh) {
        List<Instant> moc = cheDo.dungLuoi(Instant.now(), soCot <= 0 ? SO_COT_MAC_DINH : soCot);
        MetaLuoi meta = meta();

        List<HydroGridRepository.DiemDoLuoi> dangChay = kho.danhMucCongTrinh(MA_MUC_NUOC).stream()
                .filter(HydroGridRepository.DiemDoLuoi::active)
                .toList();

        // ⭐ Quyết định MỘT LẦN trên danh sách đã có trong tay — ⛔ không hỏi CSDL lượt thứ hai
        //    bên trong một `filter` (nó sẽ chạy lại cho TỪNG điểm đo).
        boolean chuaAiTick = dangChay.stream().noneMatch(HydroGridRepository.DiemDoLuoi::mainAxis);
        List<HydroGridRepository.DiemDoLuoi> diemDo = !chiTrucChinh || chuaAiTick
                ? dangChay
                : dangChay.stream()
                        .filter(HydroGridRepository.DiemDoLuoi::mainAxis)
                        .toList();

        if (diemDo.isEmpty()) {
            return new LuoiMucNuoc(meta, moc, List.of(), "Chưa có điểm đo mực nước nào đang hoạt động");
        }

        // Cửa sổ đọc: từ mốc cũ nhất của lưới tới mốc mới nhất, nới một bước ở đầu để bắt được số
        // đo rơi hơi lệch mốc (nguồn trả rải rác trong cửa sổ x1:30 → x8:30 — quy tắc 17).
        Instant den = moc.get(0).plus(cheDo.buoc());
        Instant tu = moc.get(moc.size() - 1).minus(cheDo.buoc());
        int tran = Math.max(1, diemDo.size() * moc.size() * 2);

        long idLoaiChiSo = diemDo.get(0).measurementTypeId();

        // ⚠ Thang ngưỡng lấy MỘT LẦN cho cả bảng, ⛔ không tra lại theo từng ô: 19 điểm đo × 144
        //   mốc = 2.736 lượt tra, và nó sẽ là một câu SQL nằm trong vòng lặp mà ⛔ không ai nhìn ra.
        Map<Long, List<HydroGridRepository.BacNguong>> nguong = new HashMap<>();
        for (HydroGridRepository.BacNguong b : kho.nguongTheoDiemDo(idLoaiChiSo)) {
            nguong.computeIfAbsent(b.stationId(), k -> new ArrayList<>()).add(b);
        }

        Map<Long, Map<Instant, OLuoi>> theoDiemDo = ropVaoLuoi(
                kho.soDoTrongKhung(
                        diemDo.stream().map(HydroGridRepository.DiemDoLuoi::id).toList(), idLoaiChiSo, tu, den, tran),
                cheDo,
                nguong);

        return new LuoiMucNuoc(meta, moc, gopNhom(diemDo, moc, theoDiemDo), null);
    }

    /**
     * Dữ liệu biểu đồ diễn biến của <b>một công trình</b> — spec §7.1, <b>WS-45</b>.
     *
     * <p>⛔ Đi qua đúng đường dựng lưới: {@link CheDoXemLuoi#dungLuoi} sinh trục <b>trước</b>, rồi
     * số đo được rót vào. Đó là thứ làm đường cong <b>ngắt</b> ở mốc mất dữ liệu thay vì nối liền
     * qua nó — §7.1 ghi thẳng <i>"điểm MISSING: ngắt đường, ⛔ không nội suy"</i>, và một đường nối
     * liền qua ba giờ mất tín hiệu là vẽ ra một đoạn số liệu chưa ai đo.
     *
     * <p>⚠ Cửa sổ ở đây <b>rộng hơn</b> bảng (mặc định 144 mốc = trọn một ngày, so với 12 của
     * §6.1.1), vì một biểu đồ 12 điểm ⛔ không trả lời được câu người ta mở nó ra để hỏi —
     * <i>"nước lên từ lúc mấy giờ"</i>. Trần vẫn là {@code 288} ở {@code dungLuoi} và
     * {@code TRAN_HANG} ở SQL, hai lớp độc lập.
     *
     * @param maCongTrinh {@code structure_code}; ⛔ không tìm thấy thì trả biểu đồ rỗng <b>kèm lý
     *     do</b>, ⛔ không phải 404 — một mã gõ sai và một công trình chưa có số cho ra hai câu khác
     *     nhau, và cả hai đều ⛔ không phải lỗi hệ thống
     */
    @Transactional(readOnly = true)
    public BieuDoCongTrinh bieuDo(String maCongTrinh, CheDoXemLuoi cheDo, int soCot) {
        List<Instant> moc = cheDo.dungLuoi(Instant.now(), soCot <= 0 ? SO_COT_BIEU_DO : soCot);
        MetaLuoi meta = meta();

        List<HydroGridRepository.DiemDoLuoi> cua = kho.danhMucCongTrinh(MA_MUC_NUOC).stream()
                .filter(HydroGridRepository.DiemDoLuoi::active)
                .filter(d -> maCongTrinh.equals(d.structureCode()))
                .toList();

        if (cua.isEmpty()) {
            return new BieuDoCongTrinh(
                    meta, moc, null, List.of(), "⛔ Không tìm thấy công trình đang hoạt động với mã " + maCongTrinh);
        }

        long idLoaiChiSo = cua.get(0).measurementTypeId();
        Map<Long, List<HydroGridRepository.BacNguong>> nguong = new HashMap<>();
        for (HydroGridRepository.BacNguong b : kho.nguongTheoDiemDo(idLoaiChiSo)) {
            nguong.computeIfAbsent(b.stationId(), k -> new ArrayList<>()).add(b);
        }

        Instant den = moc.get(0).plus(cheDo.buoc());
        Instant tu = moc.get(moc.size() - 1).minus(cheDo.buoc());
        Map<Long, Map<Instant, OLuoi>> soDo = ropVaoLuoi(
                kho.soDoTrongKhung(
                        cua.stream().map(HydroGridRepository.DiemDoLuoi::id).toList(),
                        idLoaiChiSo,
                        tu,
                        den,
                        Math.max(1, cua.size() * moc.size() * 2)),
                cheDo,
                nguong);

        CongTrinh ct = dungCongTrinh(maCongTrinh, cua, moc, soDo);

        // Đường ngưỡng đi theo TỪNG chỉ tiêu — thượng lưu và hạ lưu của cùng một cống có thể khai
        // hai bộ ngưỡng khác nhau (§5.3). Gộp chúng làm một là vẽ ngưỡng của điểm này lên đường
        // cong của điểm kia.
        List<DuongNguong> duong = new ArrayList<>();
        for (HydroGridRepository.DiemDoLuoi d : cua) {
            for (HydroGridRepository.BacNguong b : nguong.getOrDefault(d.id(), List.of())) {
                duong.add(new DuongNguong(nhanVaiTro(d.positionRole()), b.tenMuc(), b.nguong(), b.khoaMau()));
            }
        }

        return new BieuDoCongTrinh(meta, moc, ct, duong, null);
    }

    /** Snap từng số đo vào mốc lưới gần nhất; mốc nào có hai số thì số MỚI hơn thắng. */
    private Map<Long, Map<Instant, OLuoi>> ropVaoLuoi(
            List<HydroGridRepository.SoDoLuoi> soDo,
            CheDoXemLuoi cheDo,
            Map<Long, List<HydroGridRepository.BacNguong>> nguong) {

        Map<Long, Map<Instant, OLuoi>> ket = new HashMap<>();
        for (HydroGridRepository.SoDoLuoi s : soDo) {
            if (s.moc() == null || s.giaTri() == null) {
                continue;
            }
            OLuoi o = xetNguong(OLuoi.coSo(s.giaTri(), s.quality()), nguong.getOrDefault(s.stationId(), List.of()));
            ket.computeIfAbsent(s.stationId(), k -> new HashMap<>()).put(cheDo.catXuong(s.moc()), o);
        }
        return ket;
    }

    /**
     * Xếp một giá trị vào bậc ngưỡng — <b>spec §5.3</b>, phép so chạy ở <b>BE</b> (quy tắc 3).
     *
     * <p>⛔ Vì sao ⛔ không đẩy sang FE: <i>"ô này thuộc mức báo động nào"</i> là một <b>phép tính
     * trên số đo</b>, ⛔ không phải một lựa chọn trình bày. Đẩy sang FE là để hai màn hình (cổng và
     * bảng quản trị) tự so lấy, rồi chúng lệch nhau vào đúng ngày một mức ngưỡng được sửa.
     *
     * <p><b>Bậc cao nhất mà giá trị vượt qua</b> thắng — {@code alarm_1 ≤ v < alarm_2} ⇒ BĐ1. SQL
     * đã sắp theo {@code severity_rank} tăng dần, nên duyệt xuôi và giữ cái cuối cùng thoả.
     *
     * <p>⚠ Thang <b>rỗng</b> — điểm đo chưa khai ngưỡng — trả về chính ô ấy, ⛔ không màu. §5.3 ghi
     * rõ: <i>"⛔ không tô màu, ⛔ không dùng ngưỡng của điểm khác"</i>. Hôm nay {@code alert_levels}
     * có <b>0 hàng</b> (cố ý, chờ G9-a) nên đây là nhánh mà <b>mọi</b> ô đang đi qua — nó phải đúng
     * trên tập rỗng trước đã (quy tắc 7).
     */
    private static OLuoi xetNguong(OLuoi o, List<HydroGridRepository.BacNguong> thang) {
        HydroGridRepository.BacNguong trung = null;
        for (HydroGridRepository.BacNguong b : thang) {
            if (b.nguong() != null && o.giaTri().compareTo(b.nguong()) >= 0) {
                trung = b;
            }
        }
        return trung == null ? o : o.voiMuc(trung.khoaMau(), trung.tenMuc());
    }

    private List<NhomTuyenSong> gopNhom(
            List<HydroGridRepository.DiemDoLuoi> diemDo, List<Instant> moc, Map<Long, Map<Instant, OLuoi>> soDo) {

        // LinkedHashMap ở cả hai tầng: thứ tự do SQL quyết định (ORDER BY tuyến → lý trình →
        // vai trò) và ⛔ không được xáo lại ở Java — xem javadoc SQL_DANH_MUC.
        Map<String, Map<String, List<HydroGridRepository.DiemDoLuoi>>> cay = new LinkedHashMap<>();
        for (HydroGridRepository.DiemDoLuoi d : diemDo) {
            String tuyen = d.riverName() == null || d.riverName().isBlank() ? CHUA_PHAN_TUYEN : d.riverName();
            // ⛔ `structure_code` NULL ⇒ điểm đo đứng RIÊNG một nhóm, ⛔ không bị loại khỏi bảng.
            String maCt = d.structureCode() == null || d.structureCode().isBlank() ? "#" + d.code() : d.structureCode();
            cay.computeIfAbsent(tuyen, k -> new LinkedHashMap<>())
                    .computeIfAbsent(maCt, k -> new ArrayList<>())
                    .add(d);
        }

        List<NhomTuyenSong> ket = new ArrayList<>();
        cay.forEach((tuyen, congTrinhs) -> {
            List<CongTrinh> dsCt = new ArrayList<>();
            congTrinhs.forEach((maCt, ds) -> dsCt.add(dungCongTrinh(maCt, ds, moc, soDo)));
            ket.add(new NhomTuyenSong(tuyen, dsCt));
        });
        return ket;
    }

    private CongTrinh dungCongTrinh(
            String maCt,
            List<HydroGridRepository.DiemDoLuoi> ds,
            List<Instant> moc,
            Map<Long, Map<Instant, OLuoi>> soDo) {

        HydroGridRepository.DiemDoLuoi dau = ds.get(0);
        List<DongChiSo> dong = new ArrayList<>();
        Map<String, List<OLuoi>> theoVaiTro = new LinkedHashMap<>();

        for (HydroGridRepository.DiemDoLuoi d : ds) {
            Map<Instant, OLuoi> cua = soDo.getOrDefault(d.id(), Map.of());
            List<OLuoi> o =
                    moc.stream().map(m -> cua.getOrDefault(m, OLuoi.TRONG)).toList();
            theoVaiTro.put(d.positionRole(), o);
            dong.add(new DongChiSo(nhanVaiTro(d.positionRole()), LoaiDong.DO, o));
        }

        // ⭐ Dòng "Chênh lệch" — spec §3.2 + §6.1.2. CHỈ khi có ĐỦ cả thượng lưu lẫn hạ lưu.
        List<OLuoi> tl = theoVaiTro.get("THUONG_LUU");
        List<OLuoi> hl = theoVaiTro.get("HA_LUU");
        if (tl != null && hl != null) {
            List<OLuoi> chenh = new ArrayList<>(moc.size());
            for (int i = 0; i < moc.size(); i++) {
                BigDecimal a = tl.get(i).giaTri();
                BigDecimal b = hl.get(i).giaTri();
                // ⛔ Thiếu MỘT vế thì ⛔ KHÔNG có chênh lệch — spec §3.2: "chỉ tính khi cả hai giá
                //    trị khác NULL. Không suy diễn, không gán 0." Gán 0 ở đây là công bố "hai bên
                //    bằng nhau", một khẳng định hoàn toàn khác với "chưa đo được".
                // ⛔ Dòng Chênh lệch KHÔNG mang màu ngưỡng — spec §6.1.2: "tô màu ngưỡng báo động
                //    áp cho dòng Thượng lưu / Hạ lưu; ⛔ KHÔNG áp cho dòng Chênh lệch". Ngưỡng đo
                //    ĐỘ CAO mực nước; một hiệu số 0,75 m ⛔ không có nghĩa gì trên thang ấy, và tô
                //    nó đỏ là công bố một mức báo động chưa từng tồn tại.
                chenh.add(
                        a == null || b == null
                                ? new OLuoi(
                                        null,
                                        null,
                                        "Thiếu số đo ở một trong hai phía — ⛔ không tính được chênh lệch",
                                        null,
                                        null)
                                : OLuoi.coSo(a.subtract(b), gopChatLuong(tl.get(i), hl.get(i))));
            }
            dong.add(new DongChiSo("Chênh lệch", LoaiDong.TINH, chenh));
        }

        return new CongTrinh(
                maCt,
                dau.structureName() == null ? dau.name() : dau.structureName(),
                dau.chainage(),
                dau.mainAxis(),
                dong);
    }

    /**
     * Chất lượng của một giá trị TÍNH = chất lượng <b>tệ nhất</b> trong các vế góp vào nó.
     *
     * <p>⛔ Một chênh lệch tính từ một số nghi ngờ <b>là</b> một số nghi ngờ. Gắn cho nó nhãn
     * {@code HOP_LE} là rửa sạch nguồn gốc của con số — người đọc thấy ô trắng và tin nó, trong khi
     * một trong hai vế đang mang dấu ⚠ ngay phía trên.
     */
    private static String gopChatLuong(OLuoi a, OLuoi b) {
        return "NGHI_NGO".equals(a.chatLuong()) || "NGHI_NGO".equals(b.chatLuong()) ? "NGHI_NGO" : "HOP_LE";
    }

    private static String nhanVaiTro(String vaiTro) {
        return switch (vaiTro) {
            case "THUONG_LUU" -> "Thượng lưu";
            case "HA_LUU" -> "Hạ lưu";
            case "BE_HUT" -> "MN Bể hút";
            case "MN_SONG" -> "MN sông";
            case "MUA" -> "Lượng mưa";
            default -> vaiTro;
        };
    }

    /**
     * {@code source_status} suy từ <b>mốc ĐO gần nhất</b>, ⛔ không từ mốc lấy dữ liệu.
     *
     * <p>Ngưỡng dùng lại {@code khungNguon()} × {@code soKhungMatTinHieu()} — <b>đúng bộ ngưỡng</b>
     * mà {@code StationDisplayStatus.suyRa} dùng cho từng trạm. ⛔ Đừng đặt một hằng số riêng ở
     * đây: hai ngưỡng cho cùng một câu hỏi <i>"bao lâu thì coi là cũ"</i> sẽ lệch nhau vào ngày ai
     * đó chỉnh một cái, và triệu chứng là bảng nói OK trong khi từng trạm đều xám.
     */
    private MetaLuoi meta() {
        HydroGridRepository.MocDongBo m = kho.mocDongBo();
        Duration hanCu = settings.khungNguon().multipliedBy(settings.soKhungMatTinHieu());

        return new MetaLuoi(
                m.lanLayCuoi(),
                m.mocDoGanNhat(),
                MetaLuoi.trangThai(m.mocDoGanNhat(), Instant.now(), hanCu),
                "m",
                // ⛔ Lý do cột lượng mưa trống đến từ BACKEND, ⛔ không phải một chuỗi ghi ở FE:
                //    G3-a là một sự thật về NGUỒN DỮ LIỆU, và cổng ⛔ không phải nơi biết nó. Dùng
                //    lại đúng MỘT câu với `PublicHydroService` để hai bảng ⛔ không nói hai kiểu.
                PublicHydroService.LY_DO_LUONG_MUA);
    }
}
