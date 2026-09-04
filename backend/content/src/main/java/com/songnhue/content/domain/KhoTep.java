package com.songnhue.content.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * Hai kho tệp của CMS — <b>chung bộ máy, khác phạm vi công bố</b> (WS-40).
 *
 * <h2>⭐ Vì sao tách {@code owner_type} thay vì lọc theo định dạng</h2>
 *
 * QuanTran chốt 04/09/2026 hai điều cùng lúc: <i>dùng lại thư viện media</i> và <i>siết phạm vi
 * công bố — chỉ tài liệu thuộc bài ĐÃ xuất bản mới tải được</i>. Hai điều đó <b>mâu thuẫn</b> nếu
 * tài liệu vẫn mang {@code owner_type = 'MEDIA_FOLDER'}: loại ấy nằm trong
 * {@code PublicPortalService.LOAI_TEP_CONG_KHAI}, nên tệp <b>công khai ngay khi tải lên</b> và
 * không đường hẹp nào giấu được nó — {@code /api/v1/public/files/&#123;id&#125;} vẫn phục vụ.
 *
 * <p>Cách hoà giải: giữ nguyên <b>mọi thứ khác</b> — cùng bảng {@code attachments}, cùng cây thư
 * mục {@code media_folders}, cùng magic-bytes, cùng hạn mức, cùng quét virus, cùng màn hình duyệt
 * — và tách đúng một thứ: loại chủ sở hữu. {@code owner_id} của cả hai kho vẫn trỏ vào
 * {@code media_folders.id}, nên cây thư mục dùng chung không phải chép lại.
 *
 * <p>⇒ Luật giải thích được cho người vận hành: <b>ảnh và video là thứ để hiện — công khai ngay;
 * tài liệu là thứ để phát hành — chỉ ra cổng qua một bài đã xuất bản.</b>
 *
 * <h2>⚠ Đây là một lần THU HẸP hành vi đang có</h2>
 *
 * Trước 04/09 đường tải lên của thư viện media nhận cả ba nhóm định dạng vào cùng
 * {@code MEDIA_FOLDER} ({@code MediaService.TAT_CA}). Từ nay đường ấy chỉ còn ảnh + video. Tệp tài
 * liệu <b>đã nằm sẵn</b> trong kho với {@code owner_type = 'MEDIA_FOLDER'} vẫn công khai như cũ —
 * ⛔ không migration nào tự ý chuyển chúng: đổi phạm vi công bố của dữ liệu đang chạy là việc phải
 * hỏi trước (xem {@code master-tracking.md} T40.14).
 */
public enum KhoTep {

    /**
     * Ảnh và video của cổng — công khai ngay khi tải lên, đúng như từ WS-14.
     *
     * <p>⛔ <b>Không có {@code image/svg+xml}</b> — điểm nghiệp vụ 7. SVG chạy được JavaScript, nên
     * nó chỉ vào hệ thống qua màn hình cấu hình giao diện (người tải là Quản trị viên) và phải qua
     * {@code SvgSanitizer}. Ảnh trong bài viết thì không nhận SVG, chấm hết.
     */
    MEDIA(MediaFolder.OWNER_TYPE, "MEDIA", hop(DinhDang.ANH, DinhDang.VIDEO)),

    /**
     * Tài liệu phát hành kèm bài viết — <b>cố ý KHÔNG</b> nằm trong {@code LOAI_TEP_CONG_KHAI}.
     *
     * <p>Đường ra cổng duy nhất là {@code GET /api/v1/public/article-documents/&#123;id&#125;}, và
     * đường ấy đòi tệp có mặt trong bản chụp phiên bản <b>đang được xuất bản</b> của một bài đang
     * thật sự công khai. Bài còn Nháp ⇒ 404. Gỡ bài ⇒ 404 theo.
     */
    TAI_LIEU("TAI_LIEU", "TAI_LIEU", DinhDang.TAI_LIEU);

    /**
     * Trần dung lượng một tài liệu <b>phục vụ ra cổng công khai</b>, tính bằng MB.
     *
     * <h2>⛔⛔ Đây là chốt chặn một nút tắt máy chủ, không phải một con số cho đẹp</h2>
     *
     * Số đo đã có đủ, 04/09/2026:
     *
     * <ul>
     *   <li>{@code AttachmentService.readForPublic} đọc <b>toàn bộ</b> tệp vào một {@code byte[]};
     *   <li>trần <i>tải lên</i> cho nhóm document là <b>50MB</b> ({@code V202608131009});
     *   <li>production {@code mem_limit: 3g} + {@code MaxRAMPercentage=70} ⇒ heap ~2,1GB, <b>và
     *       {@code -XX:+ExitOnOutOfMemoryError}: hết bộ nhớ thì container CHẾT, không suy giảm</b>;
     *   <li>hạn mức tần suất công khai đếm <b>request</b>, không đếm byte; nginx
     *       {@code proxy_buffering off} nên không có lớp đệm nào ở biên.
     * </ul>
     *
     * ⇒ 20 lượt tải song song một PDF 50MB ≈ <b>1GB {@code byte[]} sống cùng lúc</b>. Không cần ác
     * ý, chỉ cần một văn bản được nhiều người mở.
     *
     * <p>⚠ Hằng số trong mã, <b>không</b> trong bảng {@code settings}: quy tắc 12 nói về <i>tham số
     * nghiệp vụ</i> (giờ hành chính, retention, ngưỡng), còn đây là giới hạn an toàn bộ nhớ — cùng
     * loại với {@code PublicPortalService.TRAN_MOI_TRANG}. Người vận hành không có thông tin để đặt
     * số này đúng, và đặt sai thì hậu quả là container chết.
     *
     * <p>⭐ Ép ở <b>hai</b> chỗ, và chỗ thứ nhất mới là chỗ quan trọng: lúc <i>đính kèm</i>
     * ({@code ArticleService}) người biên tập còn sửa được — nén lại, tách nhỏ. Ép mỗi lúc phục vụ
     * thì tệp lên cổng rồi mới hỏng, và người đính kèm không bao giờ biết.
     *
     * <p>📌 Nợ T40.19 — đường sạch hơn <b>đã đo được là khả thi</b>: {@code deploy/nginx} có sẵn
     * server block {@code ${FILES_DOMAIN}} chuyển tiếp vào MinIO và {@code MINIO_ENDPOINT} là tên
     * miền công khai, nên 302 → presigned URL chạy được và trần này biến mất. Nó cần
     * {@code ObjectStorage} nhận thêm {@code response-content-disposition} để giữ tên tệp.
     */
    public static final int TRAN_PHUC_VU_CONG_KHAI_MB = 25;

    private final String ownerType;
    private final String purpose;
    private final List<String> dinhDang;

    KhoTep(String ownerType, String purpose, List<String> dinhDang) {
        this.ownerType = ownerType;
        this.purpose = purpose;
        this.dinhDang = dinhDang;
    }

    /** Giá trị cột {@code attachments.owner_type}. */
    public String ownerType() {
        return ownerType;
    }

    /** Giá trị cột {@code attachments.purpose} — nền cho đánh số phiên bản trong cùng thư mục. */
    public String purpose() {
        return purpose;
    }

    /** Định dạng nhận được ở kho này — kiểm bằng magic bytes ở {@code AttachmentPort}. */
    public List<String> dinhDangChoPhep() {
        return dinhDang;
    }

    /**
     * Đọc từ tham số HTTP; thiếu hoặc rỗng ⇒ {@link #MEDIA}.
     *
     * <p>Mặc định là MEDIA để mọi nơi gọi có từ trước 04/09 giữ nguyên hành vi — thêm một tham số
     * mà đổi mặc định là đổi hành vi của những màn hình không ai sửa.
     *
     * <p>⛔ Giá trị lạ ném {@link IllegalArgumentException} chứ không âm thầm rơi về MEDIA: gõ sai
     * {@code kho=tailieu} mà nhận về danh sách ảnh là đúng loại lỗi im lặng dự án đã trả giá nhiều
     * lần.
     */
    public static KhoTep tuThamSo(String giaTri) {
        if (giaTri == null || giaTri.isBlank()) {
            return MEDIA;
        }
        return valueOf(giaTri.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * Tệp này có vượt trần phục vụ công khai không.
     *
     * <p>Một hàm chứ không hai lần so sánh rải ở hai lớp: hai chỗ ép cùng một luật thì luật phải
     * nằm ở một chỗ, nếu không thì lượt đổi số kế tiếp chỉ sửa được một nửa (quy tắc 14).
     */
    public static boolean vuotTranPhucVu(long sizeBytes) {
        return sizeBytes > TRAN_PHUC_VU_CONG_KHAI_MB * 1024L * 1024L;
    }

    private static List<String> hop(List<String> a, List<String> b) {
        return Stream.of(a, b).flatMap(List::stream).toList();
    }

    /**
     * Ba nhóm định dạng — tách khỏi hằng số của enum vì hằng số của enum không tham chiếu được nhau
     * trong danh sách hằng.
     */
    private static final class DinhDang {

        private static final List<String> ANH = List.of("image/jpeg", "image/png", "image/gif", "image/webp");

        private static final List<String> VIDEO = List.of("video/mp4", "video/webm");

        private static final List<String> TAI_LIEU = List.of(
                "application/pdf",
                "application/zip",
                "application/msword",
                "application/vnd.ms-excel",
                "application/x-ole-storage",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private DinhDang() {}
    }
}
