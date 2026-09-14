package com.songnhue.operations.application;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;
import com.songnhue.operations.domain.GisGeometryType;
import com.songnhue.operations.domain.GisLayer;
import com.songnhue.operations.infra.GisLayerRepository;

/**
 * Lớp bản đồ GIS — CN-02.4 / M2.9 (WS-59).
 *
 * <h2>⛔⛔ GeoJSON — KMZ thì CHƯA, và nói thẳng</h2>
 *
 * <p>Đặc tả viết *"upload GeoJSON/KMZ ≤20MB"*. Đo 14/09/2026: kho <b>⛔ không có</b> bộ đọc KML/KMZ
 * nào (0 phụ thuộc trong cả 7 {@code pom.xml}), và KMZ là một tệp ZIP chứa KML — tức cần một bộ
 * phân tích XML theo lược đồ OGC, ⛔ không phải một phép giải nén.
 *
 * <p>⛔ Nhận tệp {@code .kmz} rồi lưu mà ⛔ không đọc được là tệ nhất trong ba phương án: người dùng
 * thấy *"nạp thành công"*, lớp hiện trong danh sách, và bản đồ ⛔ không vẽ gì. ⇒ Từ chối ngay ở
 * cổng nhận kèm câu lỗi nói rõ, và ghi nợ.
 *
 * <h2>⛔ Phân tích tệp bằng REGEX, ⛔ không bằng bộ phân tích JSON đầy đủ</h2>
 *
 * <p>Thứ duy nhất cần rút ra là <b>tập kiểu hình học</b> và <b>số đối tượng</b>. Nạp trọn một tệp
 * 20 MB thành cây đối tượng chỉ để đếm là đúng thứ VPS 2 nhân phải tránh (T28.35), và
 * {@code ObjectMapper} trên nội dung <b>do người dùng nạp</b> mở thêm một bề mặt tấn công
 * (deep-nesting / billion-laughs) mà ⛔ không đổi lại được gì.
 *
 * <p>⚠ Cái giá phải khai ra: phép kiểm này ⛔ <b>không</b> chứng minh tệp là GeoJSON <i>hợp lệ</i>
 * theo RFC 7946 — nó chỉ chứng minh tệp có ít nhất một {@code "type"} hình học. Một tệp sai lược đồ
 * vẫn qua được ở đây rồi hỏng ở trình duyệt. ⇒ Nợ, và giao diện phải bắt được lỗi vẽ.
 */
@Service
public class GisLayerService {

    private static final Logger log = LoggerFactory.getLogger(GisLayerService.class);

    /** ⚠ Đặc tả: ≤ 20 MB. Trần ở đây là lớp thứ hai — lớp thứ nhất là hạn mức của `attachments`. */
    static final int TRAN_MB = 20;

    /** Kiểu hình học GeoJSON — RFC 7946 §3.1. ⛔ `Feature`/`FeatureCollection` ⛔ không nằm ở đây. */
    private static final Pattern KIEU_HINH_HOC = Pattern.compile(
            "\"type\"\\s*:\\s*\"(Point|MultiPoint|LineString|MultiLineString|Polygon|MultiPolygon|GeometryCollection)\"");

    /**
     * ⛔ ĐÚNG MỘT mục: {@code application/json}.
     *
     * <p>{@code FileValidator.detect} nhìn <b>byte</b>, và một tệp GeoJSON ⛔ không khác một tệp
     * JSON thường ở byte nào — khai thêm {@code application/geo+json} là hứa một phép phân biệt ⛔
     * không tồn tại. ⛔ Và ⛔ không khai {@code application/octet-stream}: mục ấy là *"⛔ không nhận
     * ra định dạng gì"*, tức nó mở cửa cho <b>mọi</b> tệp nhị phân đi vào đường nạp bản đồ.
     */
    private static final List<String> MIME_CHO_PHEP = List.of(com.songnhue.core.common.util.FileValidator.MIME_JSON);

    private final GisLayerRepository layers;
    private final AttachmentPort attachments;

    public GisLayerService(GisLayerRepository layers, AttachmentPort attachments) {
        this.layers = layers;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public List<GisLayer> danhSach(boolean chiDangBat) {
        return chiDangBat
                ? layers.findByDeletedAtIsNullAndActiveTrueOrderBySortOrderAscIdAsc()
                : layers.findByDeletedAtIsNullOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public GisLayer get(UUID publicId) {
        return layers.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    @Transactional
    public GisLayer tao(GisLayerForm form) {
        if (layers.existsByNameIgnoreCaseAndDeletedAtIsNull(form.name().trim())) {
            throw new ConflictException(ErrorCode.OPS_2024, form.name());
        }
        // ⛔ Lớp ra đời ở `HON_HOP` và ⛔ không có tệp: đó là trạng thái CÓ THẬT (khai lớp trước, nạp
        //   tệp sau), và `ck_gis_layers_tep_va_so_doi_tuong` cho phép đúng cặp (NULL, NULL).
        GisLayer layer = new GisLayer(form.name().trim(), GisGeometryType.HON_HOP);
        apDung(layer, form);
        return layers.save(layer);
    }

    @Transactional
    public GisLayer sua(UUID publicId, GisLayerForm form) {
        GisLayer layer = get(publicId);
        if (layers.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(form.name().trim(), layer.getId())) {
            throw new ConflictException(ErrorCode.OPS_2024, form.name());
        }
        layer.setName(form.name().trim());
        apDung(layer, form);
        return layers.save(layer);
    }

    /**
     * Nạp tệp GeoJSON cho một lớp.
     *
     * <h2>⛔ Ba chốt chặn, và thứ tự của chúng là cố ý — rẻ trước, đắt sau</h2>
     *
     * <ol>
     *   <li>Đuôi tệp {@code .kmz}/{@code .kml} ⇒ từ chối ngay kèm lý do (chưa có bộ đọc).
     *   <li>Kích thước ⇒ từ chối trước khi đọc nội dung.
     *   <li>Nội dung có hình học ⇒ từ chối một tệp JSON hợp lệ mà rỗng hình học.
     * </ol>
     *
     * <p>⚠ Chỉ sau cả ba mới gọi {@code attachments.upload} — nếu ⛔ không thì một tệp bị từ chối vẫn
     * đã tiêu hạn mức dung lượng của lớp và nằm lại trong kho.
     */
    @Transactional
    public GisLayer napTep(UUID publicId, String tenTep, byte[] noiDung) {
        GisLayer layer = get(publicId);
        String ten = tenTep == null ? "" : tenTep.toLowerCase(java.util.Locale.ROOT);

        if (ten.endsWith(".kmz") || ten.endsWith(".kml")) {
            throw new BusinessRuleException(ErrorCode.OPS_2025, tenTep);
        }
        if (noiDung == null || noiDung.length == 0) {
            throw new BusinessRuleException(ErrorCode.OPS_2026, tenTep, 0);
        }
        int soMb = (int) Math.ceil(noiDung.length / 1024.0 / 1024.0);
        if (soMb > TRAN_MB) {
            throw new BusinessRuleException(ErrorCode.SYS_0010, TRAN_MB);
        }

        DocGeoJson doc = docGeoJson(noiDung);
        if (doc.soDoiTuong() == 0) {
            throw new BusinessRuleException(ErrorCode.OPS_2026, tenTep, 0);
        }

        AttachmentRef ref = attachments.upload(new AttachmentUploadCommand(
                GisLayer.OWNER_TYPE, layer.getId(), "GIS_LAYER", tenTep, noiDung, MIME_CHO_PHEP));

        layer.ganTep(ref.publicId(), doc.soDoiTuong(), GisGeometryType.tuTapKieu(doc.kieu()));
        log.info(
                "Nạp lớp bản đồ {} — {} đối tượng, loại {}",
                layer.getName(),
                doc.soDoiTuong(),
                layer.getGeometryType());
        return layers.save(layer);
    }

    /**
     * Nội dung GeoJSON của một lớp, để giao diện vẽ.
     *
     * <p>⛔ Đi qua {@code readForOwner} chứ ⛔ không {@code downloadUrl}: một presigned URL sống 10
     * phút và ⛔ không mang phiên đăng nhập, tức ai có chuỗi ấy đều tải được cả lớp bản đồ. Ở đây
     * nội dung đi thẳng qua máy chủ, sau cổng quyền {@code ops:gis-layer:view}.
     */
    @Transactional(readOnly = true)
    public AttachmentContent noiDung(UUID publicId) {
        GisLayer layer = get(publicId);
        if (layer.getAttachmentPublicId() == null) {
            // ⛔ 404 chứ ⛔ không trả một GeoJSON rỗng: "lớp chưa nạp tệp" và "lớp nạp rồi mà rỗng"
            //   là hai trạng thái khác nhau, và cái sau ⛔ không tồn tại (chốt chặn ở `napTep`).
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
        // ⛔⛔ Phân biệt *chưa quét virus xong* với *⛔ không tồn tại* — `readForOwner` trả rỗng cho
        //    CẢ HAI, và gộp chúng thành 404 là để giao diện nói *"lớp ⛔ không tồn tại"* về một lớp
        //    vừa nạp xong vài giây trước. Lượt chạy đầu của `GisLayerHttpTest` đỏ đúng ở đây.
        AttachmentRef ref = attachments
                .findRef(layer.getAttachmentPublicId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        if (!ref.downloadable()) {
            throw new BusinessRuleException(ErrorCode.SYS_0009, "đang quét virus hoặc đã bị cách ly");
        }
        return attachments
                .readForOwner(GisLayer.OWNER_TYPE, layer.getId(), layer.getAttachmentPublicId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    @Transactional
    public void xoa(UUID publicId) {
        GisLayer layer = get(publicId);
        layer.markDeleted(com.songnhue.core.common.util.DateTimeUtils.nowUtc());
        layers.save(layer);
    }

    /**
     * Sắp lại thứ tự chồng lớp — nhận <b>toàn bộ danh sách đã sắp</b>.
     *
     * <p>⛔ ⛔ Không nhận một phép dịch chuyển tương đối (*"lên một bậc"*): khi ấy hai nơi cùng phải
     * biết thứ tự hiện tại, và hai nơi biết một sự thật là hai nơi sẽ lệch. Cùng quyết định với
     * {@code OrgUnitService.reorder} (T25.x).
     */
    @Transactional
    public void sapThuTu(List<UUID> theoThuTu) {
        int i = 0;
        for (UUID id : theoThuTu) {
            GisLayer layer = get(id);
            layer.setSortOrder(i++);
            layers.save(layer);
        }
    }

    // ---- Nội bộ ---------------------------------------------------------------

    private static void apDung(GisLayer layer, GisLayerForm form) {
        layer.setDescription(form.description());
        if (form.color() != null) {
            layer.setColor(form.color());
        }
        if (form.opacity() != null) {
            layer.setOpacity(form.opacity());
        }
        if (form.sortOrder() != null) {
            layer.setSortOrder(form.sortOrder());
        }
        if (form.active() != null) {
            layer.setActive(form.active());
        }
    }

    record DocGeoJson(int soDoiTuong, Set<String> kieu) {}

    /**
     * Đếm đối tượng và gom tập kiểu hình học — xem javadoc lớp về lý do dùng regex.
     *
     * <p>⚠ Đọc bằng <b>UTF-8</b> tường minh: GeoJSON theo RFC 7946 <b>phải</b> là UTF-8, và đọc
     * bằng bảng mã mặc định của JVM là một khác biệt giữa máy dev và container.
     */
    private static DocGeoJson docGeoJson(byte[] noiDung) {
        String van = new String(noiDung, StandardCharsets.UTF_8);
        Matcher m = KIEU_HINH_HOC.matcher(van);
        Set<String> kieu = new LinkedHashSet<>();
        int dem = 0;
        while (m.find()) {
            kieu.add(m.group(1));
            dem++;
        }
        return new DocGeoJson(dem, kieu);
    }
}
