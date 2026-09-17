package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.export.BangCsv;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.AlertEventRef;
import com.songnhue.core.spi.HydroAlertPort;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.MaBaoCaoVanHanh;
import com.songnhue.operations.domain.MaintenanceLog;
import com.songnhue.operations.domain.MaintenanceType;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.MaintenanceLogRepository;

/**
 * Báo cáo vận hành công trình — CN-02.10 (BC-06 · BC-09 · BC-10).
 *
 * <h2>⛔⛔ Ba mã còn sống, BỐN mã đã bỏ vĩnh viễn — và cả bảy đều khai ra</h2>
 *
 * <p>BC-01/02/03 mất nguồn (nhật ký vận hành loại khỏi phạm vi — B1/F1, xác nhận bởi G2) và BC-04
 * mất nguồn (kế hoạch vụ mùa — A1). Xem {@link MaBaoCaoVanHanh}: khai đủ bảy mã kèm lý do là cách
 * duy nhất để câu hỏi *"BC-01 đâu?"* ⛔ không quay lại ở mọi lượt nghiệm thu.
 *
 * <h2>⛔ Báo cáo CẮT theo phạm vi đơn vị</h2>
 *
 * <p>Cùng luật với báo cáo nhân sự (CN-04.8): báo cáo là <b>đầu ra</b> của hồ sơ, và hồ sơ chịu
 * phạm vi. ⇒ Mọi truy vấn ở đây đi qua JPA, tức qua {@code @Filter}.
 *
 * <p>⚠ <b>Ngoại lệ có ý thức: BC-06 vế cảnh báo.</b> {@code alert_events} ⛔ <b>không</b> mang cột
 * đơn vị — một mực nước vượt ngưỡng ⛔ không thuộc về Xí nghiệp nào, nó thuộc về một <i>điểm đo</i>.
 * Cắt nó theo phạm vi là bịa ra một quan hệ ⛔ không có trong lược đồ. ⇒ Vế cảnh báo toàn hệ, vế sự
 * cố cắt theo phạm vi, và <b>bản báo cáo nói ra điều đó</b> ở ngay dòng đầu.
 *
 * <h2>⛔ CSV — quyết định T34.8, ⛔ không phải lựa chọn mới</h2>
 *
 * <p>⬜ Bản in <b>PDF</b> của các báo cáo này chờ <b>G10</b> (duyệt bố cục) và <b>T42.14</b> (kho ⛔
 * không có bộ kết xuất nào). ⛔ Chọn thư viện trước khi có tệp mẫu là chọn trước khi biết khổ giấy,
 * cách gộp ô và phông tiếng Việt.
 */
@Service
public class BaoCaoVanHanhService {

    private final MaintenanceLogRepository maintenance;
    private final ConstructionRepository constructions;
    private final OrgUnitPort orgUnits;
    private final HydroAlertPort canhBao;

    public BaoCaoVanHanhService(
            MaintenanceLogRepository maintenance,
            ConstructionRepository constructions,
            OrgUnitPort orgUnits,
            HydroAlertPort canhBao) {
        this.maintenance = maintenance;
        this.constructions = constructions;
        this.orgUnits = orgUnits;
        this.canhBao = canhBao;
    }

    public record TepXuat(String tenTep, byte[] noiDung) {}

    /**
     * @throws IllegalStateException khi mã báo cáo đã <b>bỏ vĩnh viễn</b> — kèm nguyên văn lý do
     */
    @Transactional(readOnly = true)
    public TepXuat xuat(MaBaoCaoVanHanh ma, LocalDate tu, LocalDate den) {
        if (!ma.khaDung()) {
            throw new IllegalStateException(ma.lyDo());
        }
        BangCsv bang =
                switch (ma) {
                    case BC_06 -> canhBaoVaSuCo(tu, den);
                    case BC_09 -> tongHopBaoTri(tu, den);
                    case BC_10 -> hienTrangCongTrinh();
                    default ->
                        throw new IllegalStateException(
                                "⛔ Mã " + ma.ma() + " khai `khaDung = true` mà ⛔ không có nhánh dựng bảng");
                };
        return new TepXuat("%s-%s.csv".formatted(ma.ma(), LocalDate.now(DateTimeUtils.ZONE_VN)), bang.byteUtf8Bom());
    }

    /**
     * BC-09 — tổng hợp sửa chữa/bảo trì theo công trình, kèm chi phí.
     *
     * <p>⚠ Dòng <b>TỔNG</b> ở cuối cộng đúng những dòng phía trên, ⛔ không gọi một câu {@code SUM}
     * riêng: hai nguồn cho một con số tổng là đúng hình dạng <b>quy tắc 13</b> — chúng lệch nhau
     * vào ngày một trong hai đổi vị từ lọc, và người đọc báo cáo ⛔ không có cách nào biết.
     */
    private BangCsv tongHopBaoTri(LocalDate tu, LocalDate den) {
        List<MaintenanceLog> ds = maintenance.trongKy(null, tu, den);
        Map<Long, Construction> theoId = congTrinhTheoId(ds);
        Map<Long, OrgUnitRef> donVi = donViCua(ds);

        // ⛔⛔ MỌI dòng phải có ĐÚNG ngần này ô. `BangCsv.dong` ném khi lệch — và ràng buộc ấy có
        //    lý do: một tệp CSV lệch số cột vẫn MỞ ĐƯỢC trong Excel, nó chỉ đẩy dữ liệu sang cột
        //    bên cạnh từ dòng ấy trở đi, **im lặng**. Bản đầu của phương thức này viết dòng "Kỳ báo
        //    cáo" 2 ô và một dòng trống 0 ô ⇒ ném ngay ở lượt kết xuất đầu tiên.
        final int soCot = 11;
        BangCsv b = new BangCsv();
        b.dong(
                "Mã bản ghi",
                "Công trình",
                "Đơn vị",
                "Loại công việc",
                "Ngày bắt đầu",
                "Ngày hoàn thành",
                "Trạng thái",
                "Nội dung",
                "Đơn vị thực hiện",
                "Chi phí (VND)",
                "Nguồn vốn");
        dongDay(b, soCot, "Kỳ báo cáo: " + moTaKy(tu, den));

        BigDecimal tong = BigDecimal.ZERO;
        for (MaintenanceLog m : ds) {
            Construction ct = theoId.get(m.getConstructionId());
            OrgUnitRef dv = donVi.get(m.getOrgUnitId());
            b.dong(
                    m.getCode(),
                    ct == null ? "(công trình đã xoá)" : ct.getName(),
                    dv == null ? "(đơn vị đã xoá)" : dv.name(),
                    m.getWorkType(),
                    m.getStartedOn(),
                    m.getCompletedOn(),
                    m.getStatus(),
                    m.getContent(),
                    m.getPerformerName() != null ? m.getPerformerName() : tenDonVi(m.getPerformerOrgUnitId()),
                    BangCsv.so(m.getCost() == null ? null : m.getCost().toPlainString()),
                    m.getFundingSource());
            if (m.getCost() != null) {
                tong = tong.add(m.getCost());
            }
        }

        // ⚠ Dòng TỔNG cộng đúng những dòng phía trên, ⛔ không gọi một câu `SUM` riêng — hai nguồn
        //   cho một con số tổng lệch nhau vào ngày một trong hai đổi vị từ lọc (quy tắc 13).
        dongDay(
                b,
                soCot,
                "TỔNG",
                "%d bản ghi".formatted(ds.size()),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                BangCsv.so(tong.toPlainString()));
        return b;
    }

    /**
     * BC-10 — danh mục &amp; hiện trạng công trình.
     *
     * <p>⛔ Cột <b>Toạ độ</b> hiện *"chưa số hoá"* thay vì để trống. Đo 10/09/2026:
     * {@code constructions} có <b>11 hàng / 0 toạ độ</b> ⇒ mọi lớp GIS rỗng (nợ <b>G8</b>). Một ô
     * trống đọc như *"quên điền"*; câu *"chưa số hoá"* đọc đúng như nó là — và đó là thông tin duy
     * nhất trên bản báo cáo này nói cho Công ty biết vì sao bản đồ chưa có gì.
     */
    private BangCsv hienTrangCongTrinh() {
        List<Construction> ds = constructions.findByDeletedAtIsNull();
        Map<Long, OrgUnitRef> donVi = orgUnits.findRefsByIds(
                ds.stream().map(Construction::getOrgUnitId).toList());

        BangCsv b = new BangCsv();
        b.dong(
                "Mã công trình",
                "Tên công trình",
                "Loại",
                "Mục đích",
                "Cấp quản lý",
                "Đơn vị quản lý",
                "Tuyến sông",
                "Lý trình",
                "Vòng đời",
                "Tình trạng vận hành",
                "Toạ độ",
                "Năm xây dựng");

        List<Construction> sap = ds.stream()
                .sorted(Comparator.comparing(Construction::getCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        for (Construction c : sap) {
            OrgUnitRef dv = donVi.get(c.getOrgUnitId());
            b.dong(
                    c.getCode(),
                    c.getName(),
                    c.getConstructionType(),
                    c.getPurpose(),
                    c.getManagementLevel(),
                    dv == null ? "(đơn vị đã xoá)" : dv.name(),
                    c.getRiverName(),
                    c.getChainage(),
                    c.getLifecycleState(),
                    c.getOperationalStatus(),
                    toaDo(c),
                    c.getBuiltYear());
        }
        return b;
    }

    /**
     * BC-06 — cảnh báo ngưỡng + bản ghi khắc phục sự cố, <b>một bảng, hai khối</b>.
     *
     * <h2>⛔⛔ Hai khối có PHẠM VI khác nhau, và bản báo cáo phải NÓI RA</h2>
     *
     * <p>Khối <i>sự cố</i> đọc {@code maintenance_logs} qua JPA ⇒ <b>cắt</b> theo đơn vị của người
     * xuất. Khối <i>cảnh báo</i> đọc {@code alert_events} qua {@code HydroAlertPort} ⇒ <b>toàn
     * hệ</b>, vì bảng ấy ⛔ không có cột đơn vị: một mực nước vượt ngưỡng thuộc về một
     * <i>điểm đo</i>, ⛔ không thuộc về một Xí nghiệp.
     *
     * <p>Hai người ở hai đơn vị xuất cùng bản này sẽ thấy khối cảnh báo <b>giống nhau</b> và khối
     * sự cố <b>khác nhau</b>. Im lặng ở đây là để họ đối chiếu hai bản rồi kết luận hệ thống sai —
     * nên dòng đầu của bảng khai thẳng điều đó.
     */
    private BangCsv canhBaoVaSuCo(LocalDate tu, LocalDate den) {
        // ⛔⛔ Một bảng, hai khối, nên số cột lấy theo khối RỘNG hơn và khối kia đệm rỗng. Xem chú
        //    thích ở `tongHopBaoTri`: `BangCsv` ném khi một dòng lệch số cột, và ràng buộc ấy giữ
        //    cho Excel ⛔ không đẩy dữ liệu sang cột bên cạnh trong im lặng.
        final int soCot = 10;
        BangCsv b = new BangCsv();
        b.dong(
                "Điểm đo / Mã bản ghi",
                "Mã điểm đo / Công trình",
                "Chỉ số / Mức độ",
                "Mức cảnh báo",
                "Trạng thái",
                "Bắt đầu",
                "Kết thúc",
                "Giá trị kích hoạt",
                "Giá trị đỉnh",
                "Lý do / Nội dung");
        dongDay(b, soCot, "Kỳ báo cáo: " + moTaKy(tu, den));
        dongDay(
                b,
                soCot,
                "Phạm vi: khối CẢNH BÁO tính trên TOÀN HỆ THỐNG (alert_events ⛔ không gắn với đơn vị); "
                        + "khối SỰ CỐ chỉ gồm công trình thuộc phạm vi đơn vị của người xuất báo cáo.");
        dongDay(b, soCot, "");

        dongDay(b, soCot, "— KHỐI 1: CẢNH BÁO NGƯỠNG THUỶ VĂN —");
        List<AlertEventRef> canhBaoDs = canhBao.suKienTrongKy(mocDau(tu), mocCuoi(den));
        for (AlertEventRef a : canhBaoDs) {
            b.dong(
                    a.tenDiemDo(),
                    a.maDiemDo(),
                    a.loaiChiSo(),
                    a.mucCanhBao(),
                    a.trangThai(),
                    a.startedAt(),
                    // ⛔ Rỗng = ĐANG XẢY RA. ⛔ Đừng điền `now()` cho "đẹp bảng": một đợt chưa kết
                    //   thúc và một đợt vừa kết thúc lúc này là hai sự thật khác nhau.
                    a.endedAt(),
                    BangCsv.so(
                            a.triggerValue() == null ? null : a.triggerValue().toPlainString()),
                    BangCsv.so(a.peakValue() == null ? null : a.peakValue().toPlainString()),
                    a.lyDo());
        }
        dongDay(b, soCot, "Số lượt cảnh báo", String.valueOf(canhBaoDs.size()));

        dongDay(b, soCot, "");
        dongDay(b, soCot, "— KHỐI 2: BẢN GHI KHẮC PHỤC SỰ CỐ —");
        List<MaintenanceLog> suCo = maintenance.trongKy(MaintenanceType.KHAC_PHUC_SU_CO, tu, den);
        Map<Long, Construction> theoId = congTrinhTheoId(suCo);
        for (MaintenanceLog m : suCo) {
            Construction ct = theoId.get(m.getConstructionId());
            dongDay(
                    b,
                    soCot,
                    m.getCode(),
                    ct == null ? "(công trình đã xoá)" : ct.getName(),
                    String.valueOf(m.getSeverity()),
                    "",
                    m.getStatus(),
                    String.valueOf(m.getStartedOn()),
                    m.getCompletedOn() == null ? "" : String.valueOf(m.getCompletedOn()),
                    "",
                    "",
                    m.getContent());
        }
        dongDay(b, soCot, "Số bản ghi sự cố", String.valueOf(suCo.size()));
        return b;
    }

    // ---- Nội bộ ---------------------------------------------------------------

    /**
     * Ghi một dòng và <b>đệm rỗng cho đủ số cột</b>.
     *
     * <p>⛔⛔ {@code BangCsv.dong} <b>ném</b> khi một dòng lệch số cột so với tiêu đề, và ràng buộc
     * ấy ⛔ không phải sự cầu kỳ: một tệp CSV lệch cột vẫn <b>mở được</b> trong Excel — nó chỉ đẩy
     * dữ liệu sang cột bên cạnh từ dòng ấy trở đi, <b>im lặng</b>. Bản đầu của lớp này viết dòng
     * *"Kỳ báo cáo"* hai ô và dòng trống ⛔ không ô nào, và nó ném ngay ở lượt kết xuất đầu tiên.
     *
     * <p>⇒ Mọi dòng <i>siêu dữ liệu</i> (kỳ báo cáo, tiêu đề khối, dòng tổng) đi qua đây.
     */
    private static void dongDay(BangCsv b, int soCot, Object... o) {
        Object[] day = new Object[soCot];
        java.util.Arrays.fill(day, "");
        System.arraycopy(o, 0, day, 0, Math.min(o.length, soCot));
        b.dong(day);
    }

    /**
     * ⛔ *"Chưa số hoá"* chứ ⛔ không phải một ô trống — xem javadoc {@link #hienTrangCongTrinh()}.
     */
    private static String toaDo(Construction c) {
        if (c.getLatitude() == null || c.getLongitude() == null) {
            return "chưa số hoá";
        }
        return c.getLatitude().toPlainString() + ", " + c.getLongitude().toPlainString();
    }

    private static String moTaKy(LocalDate tu, LocalDate den) {
        if (tu == null && den == null) {
            // ⛔ Nói THẲNG là toàn bộ dữ liệu, ⛔ không để trống: một bản báo cáo ⛔ không ghi kỳ là
            //   một bản báo cáo ⛔ không đối chiếu được với bản nào khác.
            return "Toàn bộ dữ liệu (không giới hạn thời gian)";
        }
        return "%s → %s".formatted(tu == null ? "không giới hạn" : tu, den == null ? "không giới hạn" : den);
    }

    private Map<Long, Construction> congTrinhTheoId(List<MaintenanceLog> ds) {
        // ⛔ Một lượt nạp cho cả bảng — tra từng dòng là N+1, và số dòng là số bản ghi của cả kỳ.
        return constructions
                .findAllById(ds.stream()
                        .map(MaintenanceLog::getConstructionId)
                        .distinct()
                        .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Construction::getId, c -> c));
    }

    private Map<Long, OrgUnitRef> donViCua(List<MaintenanceLog> ds) {
        return orgUnits.findRefsByIds(
                ds.stream().map(MaintenanceLog::getOrgUnitId).toList());
    }

    private String tenDonVi(Long id) {
        return id == null ? "" : orgUnits.findRefById(id).map(OrgUnitRef::name).orElse("");
    }

    /** ⚠ Đầu ngày theo giờ Việt Nam, ⛔ không theo UTC — quy tắc 1. */
    private static Instant mocDau(LocalDate ngay) {
        return ngay == null ? null : DateTimeUtils.startOfDay(ngay);
    }

    /** ⚠ Cận trên NỬA MỞ — xem {@code DateTimeUtils.endOfDayExclusive}. */
    private static Instant mocCuoi(LocalDate ngay) {
        return ngay == null ? null : DateTimeUtils.endOfDayExclusive(ngay);
    }
}
