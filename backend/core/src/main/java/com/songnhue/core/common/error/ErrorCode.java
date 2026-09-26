package com.songnhue.core.common.error;

import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi toàn hệ thống — bản mã hoá của {@code conventions.md} §2.3.
 *
 * <p>Định dạng {@code <PREFIX>-<4 số>}. Prefix theo module: {@code SYS} (hệ thống/chung),
 * {@code AUTH}, {@code CMS}, {@code OPS}, {@code HYD}, {@code HR}, {@code ADM}. Dải số: {@code 0xxx}
 * hệ thống/chung · {@code 1xxx} not-found/conflict · {@code 2xxx} rule nghiệp vụ · {@code 3xxx}
 * quyền/phạm vi · {@code 5xxx} lỗi hệ thống ngoài.
 *
 * <p><b>Thêm mã mới = thêm hằng ở đây + thêm dòng vào {@code error-messages.properties} + cập
 * nhật {@code conventions.md} §2.3 và {@code shared/error-map.ts} bên FE.</b> Không có message thì
 * {@code ErrorCatalogTest} làm build đỏ — cố ý, để mã lỗi không bao giờ lọt ra ngoài dưới dạng khoá
 * thô.
 *
 * <p>Message nằm ở file properties chứ không nằm trong enum: sửa câu chữ tiếng Việt là việc của
 * người viết tài liệu, không nên bắt biên dịch lại mã nguồn.
 *
 * <h2>⛔ BIA MỘ — mọi số hiệu KHUYẾT phải được xếp loại ở đây (T42.28)</h2>
 *
 * <p>Dãy số của mỗi dải là <b>liên tục</b>; một số hiệu khuyết luôn có đúng một trong hai lý do, và
 * hai lý do ấy có hậu quả <b>ngược nhau</b>:
 *
 * <ul>
 *   <li><b>{@code NGHI_HUU}</b> — mã từng <i>sống</i> rồi bị đổi tên. <b>⛔ BAO GIỜ được cấp lại.</b>
 *       Cấp lại làm mọi dòng nhật ký, ảnh chụp màn hình và phiếu hỗ trợ cũ mang mã ấy <b>đọc sai
 *       nghĩa</b>, ⛔ một dòng đỏ nào báo. Một mã lỗi là một <b>định danh</b>, ⛔ phải một ô trống để lấp.
 *   <li><b>{@code CHUA_DUNG}</b> — số bị <i>nhảy qua</i>, chưa từng ra khỏi kho. Cấp lại được bình
 *       thường; nó nằm đây chỉ để lượt rà sau ⛔ phải đi hỏi <i>"số này mất đi đâu"</i>.
 * </ul>
 *
 * <ul>
 *   <li>{@code NGHI_HUU:OPS-2022} → {@link #SYS_0012} (09/09/2026, T59.1) — trần dòng của bộ đọc tệp;
 *       đổi khi bộ đọc dời lên {@code core}, vì một mã {@code OPS} hiện trên màn hình của module khác.
 *   <li>{@code NGHI_HUU:OPS-2015} → {@link #SYS_0016} và {@code NGHI_HUU:OPS-2016} → {@link #SYS_0015}
 *       (23/09/2026, T42.28) — cùng lý lẽ: {@code OPS-2015} ném 6 lần TOÀN BỘ trong {@code core}, còn
 *       {@code OPS-2016} ném 4 lần ở 3 module khác nhau ⇒ một tiền tố module hiện trên màn hình module khác.
 *   <li>{@code NGHI_HUU:OPS-2025} (24/09/2026, T59.14) — <i>"tệp là KML/KMZ, kho chưa có bộ đọc"</i>.
 *       ⛔ đổi tên: <b>trạng thái ấy ⛔ còn tồn tại</b> — kho nay đọc được KML/KMZ, nên mã này ⛔ có
 *       cách nào bắn ra nữa. ⛔ Dùng lại số cho ca <i>"KML hỏng"</i> (đó là {@link #OPS_2033}): mọi
 *       ảnh chụp và phiếu hỗ trợ cũ mang {@code OPS-2025} nghĩa là <i>hệ ⛔ đọc được định dạng</i>,
 *       và người đọc chúng sẽ đi chuyển tệp sang GeoJSON — một việc nay ⛔ còn cần làm.
 *   <li>{@code CHUA_DUNG:HR-2006} (14/09/2026, T57.7) — bản đầu của CN-04.9 định đúc mã này cho
 *       <i>"vượt số dư phép"</i>, rồi đo ra {@link #HR_2001} đã giữ đúng trạng thái ấy từ 13/08 và
 *       đang mồ côi. Hai mã cho một trạng thái là hai câu trả lời cho cùng một câu hỏi ⇒ bỏ mã mới.
 * </ul>
 *
 * <p>⚠ Nhãn cố ý <b>⛔ có khoảng trắng</b> ({@code NGHI_HUU:<mã>}): bộ canh đọc chúng bằng mẫu, và
 * một bộ canh mà <b>bộ định dạng mã</b> làm cho sai sẽ đỏ giả vào ngày ⛔ ai đoán trước (§11.13).
 * Cổng giữ khối này là {@code MaLoiNghiHuuTest} — thiếu một dòng xếp loại là CI đỏ, gọi đích danh số.
 */
public enum ErrorCode {

    // ---- Hệ thống / dùng chung -------------------------------------------------
    /** Lỗi không lường trước. Chỉ lộ traceId ra ngoài, chi tiết nằm ở log. */
    SYS_0001("SYS-0001", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_0002("SYS-0002", HttpStatus.TOO_MANY_REQUESTS),
    /** Input sai định dạng / thiếu trường — mặc định của {@code ValidationException}. */
    SYS_0003("SYS-0003", HttpStatus.BAD_REQUEST),
    /** Mặc định của {@code ResourceNotFoundException} khi module chưa có mã riêng. */
    SYS_0004("SYS-0004", HttpStatus.NOT_FOUND),
    /** Optimistic lock hoặc trùng unique — mặc định của {@code ConflictException}. */
    SYS_0005("SYS-0005", HttpStatus.CONFLICT),
    /** Hệ thống bên ngoài lỗi — mặc định của {@code UpstreamException}. */
    SYS_0006("SYS-0006", HttpStatus.BAD_GATEWAY),
    /** Chế độ bảo trì đang bật (chặn ghi lúc khôi phục dữ liệu — M5.11). */
    SYS_0007("SYS-0007", HttpStatus.SERVICE_UNAVAILABLE),
    /** Vi phạm rule nghiệp vụ chưa có mã riêng — mặc định của {@code BusinessRuleException}. */
    SYS_0008("SYS-0008", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Tệp chưa quét virus xong hoặc đã bị cách ly — {0} là trạng thái quét. */
    SYS_0009("SYS-0009", HttpStatus.CONFLICT),
    /** Bản ghi đã dùng hết hạn mức dung lượng tệp đính kèm (CN-02.3: 500MB/công trình). */
    SYS_0010("SYS-0010", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Thân yêu cầu vượt trần multipart của máy chủ — {0} là trần tính bằng MB.
     *
     * <p>⚠ Đây <b>không</b> phải hạn mức nghiệp vụ. Hạn mức nghiệp vụ nằm ở {@code settings}
     * ({@code limits.upload.max-mb.*}), sửa được trên giao diện, và nó trả {@code SYS-0003} kèm
     * chi tiết {@code FILE_TOO_LARGE}. Mã này chỉ nổ khi tệp vượt trần <i>hạ tầng</i> — thứ chặn
     * trước cả controller, ở {@code DispatcherServlet.checkMultipart}.
     *
     * <p>Trước 30/08/2026 không ai bắt {@code MaxUploadSizeExceededException}, nên nó rơi vào lưới
     * an toàn cuối và trả <b>500</b>: người dùng nhận "Lỗi hệ thống, vui lòng thử lại" cho một tệp
     * chỉ cần nén nhỏ lại. Xem {@code UploadSizeCeilingTest}.
     */
    SYS_0011("SYS-0011", HttpStatus.CONTENT_TOO_LARGE),
    /**
     * Tệp nhập vượt trần {@code SpreadsheetReader.MAX_ROWS} dòng dữ liệu.
     *
     * <p>⛔ Mã này thay cho một hành vi <b>cắt cụt im lặng</b> sống từ T17.9 tới 09/09/2026: vòng lặp
     * dừng ở dòng thứ 5000 và bản báo cáo đếm <i>sau khi</i> cắt, nên một tệp 8000 dòng nhập "thành
     * công" đúng 5000 bản ghi mà ⛔ không có gì nói ra 3000 dòng còn lại chưa từng được đọc.
     *
     * <p>⚠ Mã <b>SYS</b> chứ ⛔ không phải OPS: {@code SpreadsheetReader} nằm ở {@code core} và mọi
     * module đều nhập tệp qua nó (danh mục công trình, vị trí điểm đo, …). Một mã mang tiền tố của
     * một module sẽ hiện ra trên màn hình của module khác — cùng họ với {@code SYS-0011} (trần
     * multipart), và đó là chỗ đúng của nó.
     *
     * <p>Tham số: {0} trần, {1} số dòng đầu tiên bị bỏ — đánh số <b>như người dùng thấy trong
     * Excel</b>, để họ mở đúng chỗ mà tách tệp.
     */
    SYS_0012("SYS-0012", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Sai ĐỘNG TỪ HTTP — T61.40 (ASVS 14.5.1).
     *
     * <p>Tách khỏi {@code SYS-0003}: gộp 405 vào 400 làm người tích hợp đi soi payload thay vì động từ,
     * và làm mọi khẳng định *"endpoint này ⛔ có động từ ghi"* thành khẳng định RỖNG (T54.7).
     */
    SYS_0013("SYS-0013", HttpStatus.METHOD_NOT_ALLOWED),
    /**
     * Tệp nhập NỞ quá trần khi giải nén — T61.40 (ASVS 5.5.2, "zip bomb").
     *
     * <p>⛔⛔ Mã RIÊNG chứ ⛔ dùng lại {@code SYS-0012} (vượt trần DÒNG): hai trạng thái ấy khác hẳn nhau
     * — một cái bảo *"tách tệp ra"*, cái kia bảo *"tệp này ⛔ phải bảng tính bình thường"*. Dùng chung một
     * mã còn làm mọi bài kiểm về trần giải nén thành khẳng định RỖNG: bản đầu của bài kiểm T61.40 XANH cả
     * khi đã gỡ chốt, vì thứ ném ra là trần dòng (luật 9).
     *
     * <p>Tham số: {0} trần tính bằng MB, {1} tên mục trong tệp nén.
     */
    SYS_0014("SYS-0014", HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Còn dòng lỗi trong tệp nhập — chạy khô báo đủ, và ⛔ dòng nào được ghi. Tham số {0}: số dòng lỗi.
     *
     * <p>⚠ Mã <b>SYS</b> chứ ⛔ phải {@code OPS} — đổi từ {@code OPS-2016} ngày 23/09/2026 (T42.28). Nó
     * được ném ở <b>4</b> chỗ thuộc <b>3</b> module khác nhau ({@code operations} ×2, {@code hydro},
     * {@code hr}), nên một mã mang tiền tố của MỘT module đang hiện trên màn hình <i>thuỷ văn</i> và
     * <i>nhân sự</i>. Cùng lý lẽ đã dời {@code OPS-2022} → {@link #SYS_0012}.
     */
    SYS_0015("SYS-0015", HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Tệp nhập ⛔ đọc được, hoặc thiếu cột bắt buộc.
     *
     * <p>⚠ Đổi từ {@code OPS-2015} ngày 23/09/2026 (T42.28), và ca này lệch còn <b>rõ hơn</b> {@code SYS_0015}:
     * cả <b>6</b> nơi ném đều nằm trong {@link com.songnhue.core.common.importer.SpreadsheetReader} —
     * tức một lớp của {@code core} phát ra mã mang tiền tố {@code OPS}, cho MỌI module nhập tệp.
     */
    SYS_0016("SYS-0016", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- Xác thực & phân quyền -------------------------------------------------
    /** Message cố ý mơ hồ: không tiết lộ tài khoản có tồn tại hay không (§4.1). */
    AUTH_0001("AUTH-0001", HttpStatus.UNAUTHORIZED),
    AUTH_0002("AUTH-0002", HttpStatus.UNAUTHORIZED),
    AUTH_0003("AUTH-0003", HttpStatus.LOCKED),
    /** Mã 2FA sai, hết hiệu lực, hoặc đã dùng rồi (chống replay). */
    AUTH_0004("AUTH-0004", HttpStatus.UNAUTHORIZED),
    /** Thiếu hoặc sai {@code X-CSRF-Token} — double-submit không khớp (§4.1). */
    AUTH_0005("AUTH-0005", HttpStatus.FORBIDDEN),
    /** Mật khẩu mới không đạt chính sách đọc từ bảng {@code settings} (M5.15). */
    AUTH_0006("AUTH-0006", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Đang bắt buộc đổi mật khẩu — chặn mọi thao tác khác cho tới khi đổi xong. */
    AUTH_0007("AUTH-0007", HttpStatus.FORBIDDEN),
    /** Phiên bị thu hồi vì phát hiện dùng lại refresh token cũ — buộc đăng nhập lại (§4.1). */
    AUTH_0008("AUTH-0008", HttpStatus.UNAUTHORIZED),
    /** Tài khoản đã có 2FA xác nhận — ⛔ đăng ký lại qua vé challenge (T61.30). */
    AUTH_0009("AUTH-0009", HttpStatus.FORBIDDEN),
    /** Mật khẩu tạm do quản trị phát đã quá hạn — T73.8 (ASVS 2.3.1). Chỉ nói ra sau khi mật khẩu ĐÚNG. */
    AUTH_0010("AUTH-0010", HttpStatus.FORBIDDEN),
    AUTH_3001("AUTH-3001", HttpStatus.FORBIDDEN),
    /** Dữ liệu ngoài phạm vi đơn vị — scope filter tầng 3 chặn (§4.2). */
    AUTH_3002("AUTH-3002", HttpStatus.FORBIDDEN),

    // ---- MOD-01 Cổng thông tin điện tử -----------------------------------------
    CMS_2001("CMS-2001", HttpStatus.UNPROCESSABLE_CONTENT),
    CMS_2002("CMS-2002", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Xoá danh mục còn bài viết — CN-01.2 bắt chuyển bài đi trước. */
    CMS_2003("CMS-2003", HttpStatus.CONFLICT),
    /** Xoá danh mục còn danh mục con. */
    CMS_2004("CMS-2004", HttpStatus.CONFLICT),
    /** Cây danh mục vượt quá 3 cấp. */
    CMS_2005("CMS-2005", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Bài viết phải thuộc ít nhất một danh mục. */
    CMS_2006("CMS-2006", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Sửa nội dung khi bài đang chờ duyệt — CN-01.1 khoá chỉnh sửa ở trạng thái này. */
    CMS_2007("CMS-2007", HttpStatus.CONFLICT),
    /** Xoá thư mục media còn tệp bên trong. */
    CMS_2008("CMS-2008", HttpStatus.CONFLICT),
    /** Xoá tệp media đang được bài viết tham chiếu. */
    CMS_2009("CMS-2009", HttpStatus.CONFLICT),
    /** Cây menu vượt quá 3 cấp. */
    CMS_2010("CMS-2010", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Xoá mục menu còn mục con. */
    CMS_2011("CMS-2011", HttpStatus.CONFLICT),
    /** Đích của mục menu không tồn tại hoặc đã bị xoá. */
    CMS_2012("CMS-2012", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Mục con phải cùng vị trí (Header/Footer) với mục cha. */
    CMS_2013("CMS-2013", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Lịch hiển thị banner có ngày kết thúc không sau ngày bắt đầu. */
    CMS_2014("CMS-2014", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Tải logo cho mục menu không thuộc dải "Liên kết website" (vị trí LIEN_KET). */
    CMS_2015("CMS-2015", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Gắn vào bài viết một tệp <b>không nằm trong Kho tài liệu</b> hoặc <b>chưa quét virus xong</b>.
     *
     * <p>Một mã cho hai lý do là có chủ đích: cả hai đều cho ra cùng một hệ quả — một dòng có tên
     * trên cổng mà bấm vào là 404 (§10.52) — và người biên tập làm cùng một việc để chữa: chọn lại
     * tệp trong Kho tài liệu, hoặc chờ vài giây rồi thử lại.
     *
     * <p>⛔ Đường công khai {@code /public/article-documents/&#123;id&#125;} <b>không</b> dùng mã
     * này: nó trả 404 trần. Nói <i>"bài chưa xuất bản"</i> là xác nhận tệp có tồn tại.
     */
    CMS_2016("CMS-2016", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Tài liệu đính kèm vượt trần dung lượng <b>phục vụ ra cổng công khai</b>
     * ({@code KhoTep.TRAN_PHUC_VU_CONG_KHAI_MB}).
     *
     * <p>⛔ Cố ý <b>không</b> gộp vào 404 của đường công khai. Ba vế kia (chưa xuất bản · đã gỡ ·
     * sai kho) im lặng vì phân biệt được là tiết lộ tệp có tồn tại; vế này thì không — lúc nó bắn ra
     * thì tệp <i>đã</i> công khai, và biến "quá lớn" thành "không tồn tại" là để người biên tập
     * không bao giờ biết vì sao độc giả tải không được.
     *
     * <p>413 chứ không 422: đây đúng nghĩa là {@code CONTENT_TOO_LARGE}, cùng họ với
     * {@link #SYS_0011} ở chiều tải lên.
     */
    CMS_2017("CMS-2017", HttpStatus.CONTENT_TOO_LARGE),
    /**
     * Xoá một liên hệ đang ở {@code DANG_XU_LY} — CN-01.4 cấm đích danh.
     *
     * <p>⚠ Đây là ràng buộc <b>nghiệp vụ</b>, ⛔ không phải kỹ thuật: bản ghi xoá được về mặt kỹ
     * thuật ở mọi trạng thái. Lý do cấm là một việc đang dở dang thì có người đang chờ câu trả lời,
     * và xoá nó là làm mất luôn dấu vết rằng đã từng có ai đó hỏi.
     */
    CMS_2018("CMS-2018", HttpStatus.CONFLICT),
    /** Mã phân loại liên hệ trùng với một phân loại còn sống. */
    CMS_2019("CMS-2019", HttpStatus.CONFLICT),
    /**
     * Xoá một phân loại còn liên hệ đang gán.
     *
     * <p>⛔ Cố ý ⛔ không tự gỡ phân loại khỏi các liên hệ ấy: đó là sửa dữ liệu lịch sử của người
     * khác trong im lặng. Đường đúng là <b>tắt</b> phân loại — nó biến mất khỏi ô chọn mà bản ghi
     * cũ vẫn đọc lại được (xem {@code ContactCategory#active}).
     */
    CMS_2020("CMS-2020", HttpStatus.CONFLICT),
    /**
     * Biểu mẫu liên hệ ⛔ không qua được reCAPTCHA — CN-01.4 / T36.6.
     *
     * <p>⚠ Mã này chỉ bắn khi Google <b>trả lời là ⛔ không hợp lệ</b>. Google ⛔ không trả lời được
     * thì {@code RecaptchaClient} <b>cho qua</b> — một sự cố mạng phía ta ⛔ không được biến thành
     * "người dân ⛔ không báo được sạt kênh". Xem javadoc lớp ấy.
     */
    CMS_2021("CMS-2021", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Bản xuất danh sách liên hệ vượt trần số dòng — CN-01.4 / T36.5.
     *
     * <p>⛔ Cố ý <b>từ chối</b> thay vì cắt bớt trong im lặng. Một tệp Excel thiếu 4.000 dòng trông
     * y hệt một tệp đủ, và người nhận nó ⛔ không có cách nào biết — đúng thứ CLAUDE.md gọi là
     * "⛔ không có trần im lặng". Đường ra: lọc theo trạng thái, hoặc dựng đường kết xuất chạy nền
     * (khuôn {@code useXuatBaoCao}) khi khối lượng thật sự tới ngưỡng ấy.
     */
    CMS_2022("CMS-2022", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Nội dung bài viết <b>rỗng trên thực tế</b> — T41.21.
     *
     * <p>⚠ {@code @NotBlank} trên trường {@code content} <b>không bắt được</b> chuyện này, và đó là
     * lý do mã này phải tồn tại: một trình soạn thảo trống ⛔ không gửi lên chuỗi rỗng — nó gửi
     * {@code <p></p>}, một chuỗi 7 ký tự đi lọt mọi ràng buộc độ dài. Bài được lưu, quy trình duyệt
     * chạy bình thường, và cổng công khai đăng một trang trắng mang tiêu đề.
     *
     * <p>Phép đo là <i>có chữ hoặc có khối nội dung</i>, ⛔ không phải <i>chuỗi khác rỗng</i>: một
     * bài chỉ gồm ảnh, một bảng số liệu hay một video nhúng là bài hợp lệ.
     */
    CMS_2023("CMS-2023", HttpStatus.UNPROCESSABLE_CONTENT),
    CMS_2024("CMS-2024", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Vé biểu mẫu công khai thiếu/giả/quá hạn, hoặc gửi quá nhanh — T73.9 (ASVS 11.1.2). Một mã cho mọi nhánh. */
    CMS_2025("CMS-2025", HttpStatus.UNPROCESSABLE_CONTENT),
    CMS_5001("CMS-5001", HttpStatus.BAD_GATEWAY),

    // ---- MOD-02 Vận hành công trình --------------------------------------------
    OPS_2001("OPS-2001", HttpStatus.UNPROCESSABLE_CONTENT),
    OPS_2002("OPS-2002", HttpStatus.UNPROCESSABLE_CONTENT),
    OPS_2003("OPS-2003", HttpStatus.UNPROCESSABLE_CONTENT),
    OPS_2004("OPS-2004", HttpStatus.UNPROCESSABLE_CONTENT),
    OPS_2005("OPS-2005", HttpStatus.CONFLICT),
    OPS_2006("OPS-2006", HttpStatus.UNPROCESSABLE_CONTENT),
    OPS_2007("OPS-2007", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Mã công trình đã tồn tại — mã là duy nhất toàn hệ thống (CN-02.1). */
    OPS_2008("OPS-2008", HttpStatus.CONFLICT),
    /** Nhập thông số kỹ thuật không thuộc loại công trình đang lập hồ sơ. */
    OPS_2009("OPS-2009", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Toạ độ phải đủ cả vĩ độ và kinh độ — một nửa toạ độ là một điểm sai trên bản đồ. */
    OPS_2010("OPS-2010", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Lý trình sai định dạng {@code K<km>+<m>}, VD {@code K0+390}. */
    OPS_2011("OPS-2011", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Cụm công trình còn công trình bên trong — chuyển hết đi rồi mới xoá được. */
    OPS_2012("OPS-2012", HttpStatus.CONFLICT),
    /** Cấp quản lý "Cụm" bắt buộc chọn cụm. */
    OPS_2013("OPS-2013", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Mã cụm công trình đã tồn tại. */
    OPS_2014("OPS-2014", HttpStatus.CONFLICT),
    /** Đơn vị thực hiện: đúng MỘT trong hai cột nội bộ / nhà thầu ngoài (điểm nghiệp vụ 17). */
    OPS_2017("OPS-2017", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Mã tình hình vận hành đã ẩn — {@code OPS-2007} chỉ cho ẩn, nên ẩn rồi phải hết ghi được. */
    OPS_2018("OPS-2018", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Lô nhập nhanh còn dòng lỗi — báo đủ theo từng dòng, và không dòng nào được ghi. */
    OPS_2019("OPS-2019", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Thời điểm hiệu lực ở tương lai — V1/V3.
     *
     * <p>{@code banGhiMoiNhat} sắp theo {@code effective_at DESC}, nên một dòng đề ngày mai
     * <b>ghim</b> cả trạng thái dẫn xuất lẫn dòng trên cổng cho tới khi tới ngày ấy — và nó ghim
     * bằng cách <i>trông đúng</i>. Lùi ngày thì vẫn hợp lệ (bù nhật ký), chỉ chặn cận trên.
     */
    OPS_2020("OPS-2020", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * {@code maintenance_logs.alert_event_public_id} trỏ vào một cảnh báo không tồn tại — T33.4.
     *
     * <p>Cột ấy có từ 21/08, có setter, có trường trong form, và ⛔ <b>chưa bao giờ được đối chiếu
     * với bất cứ thứ gì</b>: một UUID bất kỳ lưu thành công. ⛔ Không chữa bằng khoá ngoại — hai
     * module không thấy nhau, nên tính toàn vẹn do tầng dịch vụ giữ, qua {@code HydroAlertPort}.
     */
    OPS_2021("OPS-2021", HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Báo cáo {0} <b>có trong danh mục</b> nhưng đã <b>bỏ vĩnh viễn</b> — {1} là lý do, nguyên văn.
     *
     * <p>⛔⛔ Mã riêng, và nó ⛔ <b>không</b> trùng nghĩa với {@code HR-2009}. Ba trạng thái khác
     * nhau, ba câu trả lời khác nhau:
     *
     * <ul>
     *   <li><b>404</b> — mã ⛔ không tồn tại: người gọi gõ sai.
     *   <li><b>{@code HR-2009}</b> — mã có thật, <b>chưa làm được</b> (BCNS-07 chờ G6): <i>sẽ</i> có.
     *   <li><b>Mã này</b> — mã có thật, <b>⛔ không bao giờ làm</b>: BC-01/02/03 mất nguồn vì nhật ký
     *       vận hành đã loại khỏi phạm vi (B1/F1, xác nhận bởi G2), BC-04 mất nguồn vì kế hoạch vụ
     *       mùa đã loại (A1).
     * </ul>
     *
     * <p>Gộp ba trạng thái thành một là để người vận hành đi chờ một thứ ⛔ không bao giờ tới.
     *
     * <p>⚠⚠ Số hiệu nhảy từ 2021 sang <b>2023</b>, ⛔ không dùng lại 2022 — và đó là cố ý:
     * {@code OPS-2022} từng tồn tại (trần dòng của bộ đọc tệp) rồi <b>đổi thành {@code SYS-0012}</b>
     * ngày 09/09/2026 khi bộ đọc dời lên {@code core}. Dùng lại một số hiệu đã nghỉ hưu làm mọi
     * dòng nhật ký, ảnh chụp màn hình và phiếu hỗ trợ cũ mang mã ấy <b>đọc sai nghĩa</b> — và ⛔
     * không có gì báo. Một mã lỗi là một <b>định danh</b>, ⛔ không phải một ô trống để lấp.
     */
    OPS_2023("OPS-2023", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- MOD-02 Lớp bản đồ GIS (CN-02.4 / M2.9, WS-59) --------------------------
    /** Tên lớp bản đồ {0} đã có — hai lớp cùng tên làm bảng chọn lớp ⛔ không phân biệt được. */
    OPS_2024("OPS-2024", HttpStatus.CONFLICT),
    /**
     * Tệp {0} ⛔ không có đối tượng hình học nào ({1} đối tượng đọc được).
     *
     * <p>⛔ Một tệp JSON <b>hợp lệ</b> mà rỗng hình học vẫn nạp được về mặt kỹ thuật — và nó cho ra
     * một lớp "đã nạp thành công" hiện một bản đồ trống. Đó đúng là câu người dùng đọc thành *"hệ
     * thống hỏng"*.
     */
    OPS_2026("OPS-2026", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Q = {0} m³/h ⛔ thuộc cỡ máy nào — Báo cáo nhanh, Bảng 1.
     *
     * <p>⛔ Bỏ im lặng một nhóm máy là để tổng 9 cột lệch "Tổng số máy" trên cùng một dòng của văn bản
     * gửi UBND. Xem {@code BangCoMayBom}.
     */
    OPS_2027("OPS-2027", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Trạm {0}: số máy vận hành {1} vượt số máy thiết kế {2} (spec Báo cáo nhanh §4.2). */
    OPS_2028("OPS-2028", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Kỳ báo cáo đã chốt — ⛔ sửa được. Mở lại bằng quyền {@code ops:quick-report:reopen} kèm lý do.
     *
     * <p>Kỳ đã chốt là văn bản ĐÃ GỬI đi; sửa lặng lẽ là để bản lưu và bản UBND nhận nói hai điều.
     */
    OPS_2029("OPS-2029", HttpStatus.CONFLICT),
    /** Khung giờ báo cáo ⛔ hợp lệ: "đến" phải sau "từ". */
    OPS_2030("OPS-2030", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Biên cỡ máy ⛔ liền nhau: {0}. Có khe thì Q rơi ra ngoài; có chồng thì Bảng 1 đếm hai lần. */
    OPS_2031("OPS-2031", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Công trình {0} khác loại mà vị trí {1} của mẫu Báo cáo nhanh đòi — danh mục có HAI công trình tên
     * "Yên Nghĩa" (trạm bơm và cống tiêu), gắn nhầm thì ghi chú luôn trống mà ⛔ ai hiểu vì sao.
     */
    OPS_2032("OPS-2032", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Tệp {0} ⛔ đọc được như KML/KMZ — lý do: {1}. <b>T59.14</b>.
     *
     * <p>⛔ Cố ý KHÁC {@link #OPS_2026} (<i>"⛔ có đối tượng hình học nào"</i>): một tệp đọc được mà
     * rỗng hình học và một tệp <b>⛔ đọc được</b> dẫn tới hai việc khác nhau — cái trước là *"bạn
     * chọn nhầm tệp"*, cái sau là *"tệp hỏng, hãy xuất lại từ QGIS"*. Gộp chúng là để người dùng đi
     * kiểm nhầm thứ (T59.0 — ba trạng thái phải nói ba câu).
     *
     * <p>⚠ {1} mang lý do ĐO ĐƯỢC (⛔ phải XML hợp lệ · KMZ ⛔ chứa tệp .kml nào · vượt trần …), ⛔
     * phải một câu chung chung: người vận hành cầm tệp trong tay và cần biết sửa cái gì.
     */
    OPS_2033("OPS-2033", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Trạng thái công trình là giá trị dẫn xuất — client sửa trực tiếp là từ chối. */
    OPS_3001("OPS-3001", HttpStatus.FORBIDDEN),

    // ---- MOD-03 Thủy văn --------------------------------------------------------
    HYD_1001("HYD-1001", HttpStatus.NOT_FOUND),
    /** Trùng mã trong một danh mục thuỷ văn — {0} là mã bị trùng. */
    HYD_1002("HYD-1002", HttpStatus.CONFLICT),
    HYD_2001("HYD-2001", HttpStatus.UNPROCESSABLE_CONTENT),
    HYD_2002("HYD-2002", HttpStatus.UNPROCESSABLE_CONTENT),
    HYD_2003("HYD-2003", HttpStatus.UNPROCESSABLE_CONTENT),
    HYD_2004("HYD-2004", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Vai trò của liên kết CHÍNH khác vai trò chính thức của điểm đo (A2b).
     *
     * <p>Hai giá trị này lệch nhau thì biểu tổng hợp xếp điểm đo vào nhầm cột TL/HL, và không có
     * triệu chứng nào ngoài một con số nằm sai chỗ.
     */
    HYD_2005("HYD-2005", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Liên kết điểm đo ↔ công trình đã tồn tại ở đúng vai trò ấy — T28.19.
     *
     * <p>Ứng với chỉ mục {@code ux_station_constructions_cap (station_id, construction_id, role)}.
     * ⚠ Cùng một cặp <b>hai vai trò khác nhau</b> là hợp lệ và có thật: một điểm đo là hạ lưu của
     * cống này đồng thời là thượng lưu của cống kế tiếp trên cùng tuyến.
     */
    HYD_2008("HYD-2008", HttpStatus.CONFLICT),
    /**
     * Sửa mã ánh xạ API của một điểm đo đã tồn tại — {0} là mã cũ, {1} là mã mới.
     *
     * <p>Mã API là khoá nối duy nhất giữa response của nguồn và điểm đo. Đổi nó là âm thầm gán số
     * liệu của trạm này sang trạm khác; biểu đồ vẫn vẽ đẹp, chỉ là của nhầm trạm.
     */
    HYD_2006("HYD-2006", HttpStatus.UNPROCESSABLE_CONTENT),
    HYD_2007("HYD-2007", HttpStatus.CONFLICT),
    /**
     * Điểm đo đã có ngưỡng cho cùng (loại chỉ số × mức cảnh báo) — T33.2.
     *
     * <p>Ứng với {@code ux_alert_rules_bo_ba}. Hai dòng <i>"BĐ I của mực nước tại Liên Mạc"</i> mang
     * hai con số khác nhau là một câu hỏi không có câu trả lời.
     */
    HYD_2009("HYD-2009", HttpStatus.CONFLICT),
    /**
     * Xoá một mức cảnh báo đang có ngưỡng trỏ vào — T33.1.
     *
     * <p>⛔ Không xoá lan sang {@code alert_rules}: mức cảnh báo là danh mục của Công ty, và xoá nó
     * âm thầm tắt một loạt ngưỡng ai đó đã cấu hình. Buộc người dùng gỡ ngưỡng trước là buộc họ
     * <b>nhìn thấy</b> cái mình sắp tắt.
     */
    HYD_2010("HYD-2010", HttpStatus.CONFLICT),
    /**
     * Đóng/bác bỏ một cảnh báo không còn ở trạng thái đang xảy ra — T33.11.
     *
     * <p>Hai người trực cùng mở màn hình lịch sử và cùng bấm "Đã xử lý" là chuyện bình thường; câu
     * {@code UPDATE … WHERE status = 'DANG_XAY_RA'} trả 0 dòng cho người bấm sau, và người ấy phải
     * được nói rõ thay vì thấy một thông báo thành công giả.
     */
    HYD_2011("HYD-2011", HttpStatus.CONFLICT),

    /**
     * Khoảng ngày của báo cáo thuỷ văn vượt trần — T34.3/T34.5.
     *
     * <p>⭐ Trần này ⛔ không phải để "bảo vệ máy chủ": báo cáo đọc bảng tổng hợp nên một năm dữ
     * liệu chỉ là vài nghìn hàng. Nó bảo vệ <b>người đọc</b> — BC-13 sinh một hàng cho mỗi (điểm đo
     * × chỉ số × ngày), nên 19 điểm đo × 2 chỉ số × 5 năm là <b>69 nghìn hàng</b> đổ vào một bảng
     * ⛔ không phân trang. Từ chối lớn tiếng kèm con số trần thì người dùng hẹp khoảng lại; trả về
     * 69 nghìn hàng thì trình duyệt đứng và triệu chứng đọc như "hệ thống hỏng".
     */
    HYD_2012("HYD-2012", HttpStatus.UNPROCESSABLE_CONTENT),

    /** Ngày bắt đầu sau ngày kết thúc — T34.3. Khoảng rỗng trả 0 hàng, và 0 hàng đọc như "không có dữ liệu". */
    HYD_2013("HYD-2013", HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Bản kết xuất đã quá hạn tải — T34.7.
     *
     * <p>⭐ {@code 410 GONE}, ⛔ <b>không</b> {@code 404}: hai câu trả lời khác hẳn nhau. 404 nói
     * <i>"chưa từng có"</i> và người dùng sẽ đi tìm xem mình bấm nhầm ở đâu; 410 nói <i>"có, và đã
     * hết hạn"</i> — việc phải làm là bấm Xuất lại. Một mã trạng thái ⛔ không phân biệt được hai
     * tình huống thì ⛔ không nói gì cả (luật 9).
     */
    HYD_2014("HYD-2014", HttpStatus.GONE),

    /** Bản kết xuất chưa sẵn sàng — việc nền còn đang chạy hoặc đã hỏng. T34.7. */
    HYD_2015("HYD-2015", HttpStatus.CONFLICT),

    /**
     * Địa chỉ gốc của nguồn mang một <b>mã số/khoá</b> trong chuỗi truy vấn — T50.1.
     *
     * <h2>⛔⛔ Sự cố thật, staging 01/09 → 10/09/2026</h2>
     *
     * <p>Mã số truy cập bị dán nguyên URL vào ô <i>Địa chỉ gốc</i>:
     * {@code http://songnhue.bhh40.net/api/getmn.aspx?key=<mã số>}. Hậu quả đo được sau 9 ngày:
     *
     * <ul>
     *   <li>{@code credential} vẫn {@code NULL} ⇒ poller hỏng <b>trước khi mở HTTP</b>,
     *       {@code consecutive_failures = 3323}, {@code hydro_raw_logs = 0} — ⛔ không một byte số
     *       liệu nào, và quy tắc 18 nói <b>mất dữ liệu là vĩnh viễn</b>;
     *   <li>mã số nằm <b>nguyên văn</b> ở một cột ⛔ không mã hoá, mà {@code ApiSourceView} trả
     *       {@code baseUrl} ra API cho cả {@code hyd:station:manage} ⇒ vi phạm quy tắc 13.
     * </ul>
     *
     * <p>⇒ Chặn ở {@code ApiSourceService.diaChi(...)} — chỗ <b>cả</b> {@code create} lẫn
     * {@code update} đi qua (quy tắc 12), ⛔ không chặn ở biểu mẫu: một ô nhập chỉ đỡ được người
     * dùng ô ấy, ⛔ không đỡ được lượt gọi API hay bản nhập cấu hình.
     */
    HYD_2016("HYD-2016", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- MOD-04 Nhân sự ---------------------------------------------------------
    /**
     * Số dư phép năm ⛔ không đủ: còn {0} ngày, đơn xin {1} ngày (CN-04.9).
     *
     * <p>⚠ Đây là một <b>chặn</b> chứ ⛔ không phải cảnh báo, và đó là lựa chọn có ý thức: đặc tả
     * nói <i>"cảnh báo vượt phép"</i> ở màn hình nhập, còn ở đường ghi thì một đơn vượt quỹ đi lọt
     * sẽ thành số dư ÂM mà ⛔ không ai duyệt cái âm ấy. Giao diện cảnh báo trước; backend từ chối.
     *
     * <h2>⛔⛔ Mã này đã nằm sẵn trong danh mục từ 13/08/2026 — và WS-57 suýt đúc thêm một mã trùng</h2>
     *
     * <p>Bản đầu của WS-57 đặt {@code HR-2006} với đúng nghĩa ấy, trong khi {@code HR-2001} —
     * <i>"Số ngày đăng ký vượt số phép còn lại"</i> — đã ngồi đó chờ CN-04.9 suốt <b>32 ngày</b>,
     * kèm một dòng miễn trừ trong {@code MaLoiCoNoiNemTest} ghi rõ <i>"chưa dựng"</i>. Hai mã cho
     * <b>một</b> trạng thái là hai câu trả lời cho cùng một câu hỏi: lượt rà sau ⛔ không biết mã
     * nào thật sự bắn ra, và bản đồ mã lỗi phía giao diện mang hai dòng nói cùng một điều.
     *
     * <p>⇒ Giữ mã CŨ, nâng <b>câu chữ</b> của nó lên để mang được hai con số. Đặt một mã mới rồi
     * để mã cũ mồ côi là cách chắc chắn nhất để dòng miễn trừ ấy sống thêm một phase nữa.
     */
    HR_2001("HR-2001", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Mã cán bộ {0} đã có hồ sơ khác dùng — mã NV ⛔ không đổi suốt quá trình công tác (CN-04.2). */
    HR_1001("HR-1001", HttpStatus.CONFLICT),
    /** Mã chức vụ {0} đã tồn tại trong danh mục. */
    HR_1002("HR-1002", HttpStatus.CONFLICT),
    /**
     * Số CCCD này đã thuộc về một hồ sơ khác — CN-04.2 khai <i>"CCCD 9/12 số unique 🔒"</i>.
     *
     * <p>⛔⛔ Phép chống trùng ⛔ <b>không</b> đứng trên một {@code UNIQUE} của cột mã hoá: GCM dùng
     * IV ngẫu nhiên nên cùng một số CCCD cho hai bản mã khác nhau, và một chỉ mục như vậy sẽ tồn
     * tại, đọc như bảo đảm, mà ⛔ không bao giờ bắt được bản trùng nào (luật 7). Thứ ép được là cột
     * vân tay {@code employee_sensitive.national_id_fingerprint} —
     * {@code CryptoService.fingerprint()}.
     */
    HR_1003("HR-1003", HttpStatus.CONFLICT),
    /**
     * Chức vụ còn {0} hồ sơ đang giữ — xoá nó là để lại từng ấy hồ sơ trỏ vào hư không, mà màn hình
     * hồ sơ ⛔ không lộ ra gì vì cột chức vụ đã tự về rỗng (cùng hình dạng T40.26).
     */
    HR_2002("HR-2002", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Tệp vượt hạn mức <b>riêng của thư mục</b> {0} — {1} MB, tệp gửi lên {2} MB (CN-04.5).
     *
     * <p>⚠ Đây ⛔ <b>không</b> trùng {@link #SYS_0010}: mã kia là hạn mức <i>tổng dung lượng một hồ
     * sơ</i> ({@code limits.attachment.quota-mb.EMPLOYEE}), còn mã này là trần <i>mỗi tệp theo thư
     * mục</i> (Ảnh 5MB, Hợp đồng 20MB…). Gộp hai mã là để người vận hành đọc một câu lỗi rồi đi sửa
     * nhầm tham số.
     */
    HR_2003("HR-2003", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- MOD-04 Nghỉ phép (CN-04.9, WS-57) --------------------------------------
    /**
     * Khoảng nghỉ {0} → {1} ⛔ không có <b>ngày công nào</b> — mọi ngày trong đó là cuối tuần hoặc
     * ngày lễ. Một đơn 0 ngày ⛔ không nghĩa lý gì, và ràng buộc
     * {@code ck_leave_requests_working_days} là lưới cuối; mã này để người dùng đọc được lý do.
     */
    HR_2004("HR-2004", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Đã có đơn nghỉ khác chồng lên khoảng {0} → {1}. Hai đơn chồng ngày là đếm <b>hai lần</b> cùng
     * những ngày ấy vào số dư — và người nộp ⛔ không cố ý, họ chỉ sửa ngày rồi nộp đơn mới thay vì
     * rút đơn cũ.
     */
    HR_2005("HR-2005", HttpStatus.CONFLICT),
    /**
     * ⛔ Không huỷ được đơn đã <b>bắt đầu nghỉ</b> ({0}). Những ngày ấy đã trôi qua; hoàn phép cho
     * chúng là bịa ra một ngày công ⛔ không ai làm.
     */
    HR_2007("HR-2007", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Ngày lễ {0} đã được khai — hai hàng cùng ngày làm phép đếm trừ hai lần cùng một ngày. */
    HR_2008("HR-2008", HttpStatus.CONFLICT),

    // ---- MOD-04 Báo cáo nhân sự (CN-04.8, WS-58) --------------------------------
    /**
     * Báo cáo {0} <b>có trong danh mục</b> nhưng chưa dựng được — {1} là lý do, nguyên văn.
     *
     * <p>⛔⛔ Mã riêng chứ ⛔ <b>không</b> phải 404. Hai trạng thái khác hẳn nhau và dẫn tới hai
     * việc khác hẳn nhau: <i>"mã ⛔ không tồn tại"</i> là người gọi gõ sai, còn <i>"mã có thật,
     * chưa dựng được"</i> là một <b>khoảng trống đã biết</b> mà người vận hành cần đọc được lý do.
     * Với {@code BCNS-07} lý do ấy là <b>G6</b>: mẫu 2C-BNV/2008 là biểu mẫu quy định của Bộ Nội
     * vụ, Công ty chưa gửi tệp gốc, và đặc tả ghi rõ <i>"cấm tự chế layout"</i>.
     */
    HR_2009("HR-2009", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- MOD-04 Thẩm quyền duyệt nghỉ phép (CN-04.9, WS-80) ---------------------
    /**
     * ⛔ Phải người duyệt của đơn vị này — T80.1.
     *
     * <p>⛔⛔ Mã riêng chứ ⛔ dùng lại {@code AUTH-3001}. Hai trạng thái khác hẳn nhau và dẫn tới
     * hai việc khác hẳn nhau: <i>"tài khoản ⛔ có quyền duyệt phép"</i> là việc của Admin (gán vai
     * trò), còn <i>"có quyền mà ⛔ phải người duyệt của ĐƠN VỊ này"</i> là việc của người dùng
     * (chuyển cho trưởng đơn vị, hoặc xin uỷ quyền). Gộp chúng là để người ta đi gõ cửa nhầm chỗ.
     */
    HR_2010("HR-2010", HttpStatus.FORBIDDEN),
    /** ⛔ Tự duyệt đơn nghỉ của chính mình — T80.2. Đơn của trưởng đơn vị do cấp trên quyết. */
    HR_2011("HR-2011", HttpStatus.FORBIDDEN),
    /**
     * Cấp 2 phải do <b>người khác</b> quyết — T80.3.
     *
     * <p>Chốt C2 mua một cấp duyệt thứ hai; để cùng một người bấm cả hai thì nó ⛔ tồn tại.
     */
    HR_2012("HR-2012", HttpStatus.FORBIDDEN),
    /** Chỉ người nộp hoặc người duyệt mới rút/huỷ được đơn — T80.4. */
    HR_2013("HR-2013", HttpStatus.FORBIDDEN),
    /**
     * Người được uỷ quyền phải <b>đang có</b> quyền duyệt nghỉ phép — T80.5.
     *
     * <p>Quyết định của QuanTran 20/09/2026. ⛔ Có vế này thì biểu mẫu nghỉ phép thành một
     * <b>đường cấp quyền ẩn</b> nằm ngoài màn hình Vai trò &amp; phân quyền, và màn hình ấy thôi là
     * bức tranh đầy đủ.
     */
    HR_2014("HR-2014", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Người được uỷ quyền phải thuộc <b>cùng đơn vị hoặc đơn vị cấp trên</b> — nguyên văn chốt B3. */
    HR_2015("HR-2015", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Chỉ trưởng/phó của đơn vị (hoặc cấp trên) mới giao được thẩm quyền mình đang giữ — T80.5.
     *
     * <p>⚠ <b>403</b> chứ ⛔ 422: đây là câu <i>"anh ⛔ có thứ đang định giao"</i>, ⛔ phải <i>"dữ
     * liệu anh gửi sai"</i>. Hai mã bên trên ({@code HR-2014} · {@code HR-2015}) thì ngược lại —
     * chúng nói về <b>người nhận</b>, và người gửi sửa được bằng cách chọn người khác.
     */
    HR_2016("HR-2016", HttpStatus.FORBIDDEN),

    // ---- MOD-05 Quản trị --------------------------------------------------------
    ADM_2001("ADM-2001", HttpStatus.UNPROCESSABLE_CONTENT),
    ADM_2002("ADM-2002", HttpStatus.CONFLICT),
    /** Chuyển đơn vị vào chính cây con của nó — cắt rời cả nhánh khỏi cây mà dữ liệu vẫn còn. */
    ADM_2003("ADM-2003", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * ⛔ Không giải thể được đơn vị vì còn thứ trỏ vào nó — CN-04.1.
     *
     * <p>{0} là <b>danh sách cụ thể</b> (<i>"3 hồ sơ cán bộ nhân viên, 2 công trình"</i>), ⛔ không
     * phải một lời từ chối trống: người vận hành cần biết <b>phải đi chuyển cái gì</b> trước khi
     * giải thể. Nguồn của danh sách là mọi bean cài {@code OrgUnitUsagePort} — mỗi module tự khai.
     */
    ADM_2004("ADM-2004", HttpStatus.CONFLICT),
    ADM_2005("ADM-2005", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Tham số {0} không nhận giá trị này — yêu cầu: {1}. */
    ADM_2006("ADM-2006", HttpStatus.UNPROCESSABLE_CONTENT),
    ADM_2007("ADM-2007", HttpStatus.FORBIDDEN),

    // ---- MOD-05 Sao lưu & khôi phục (WS-7) --------------------------------------
    /** Sao lưu chưa cấu hình được: thiếu mật khẩu vai trò đọc, hoặc thư mục không ghi được. */
    ADM_2008("ADM-2008", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Đã có lượt sao lưu đang chạy — hai lượt song song chỉ tổ đọc đĩa gấp đôi. */
    ADM_2009("ADM-2009", HttpStatus.CONFLICT),
    /** Khôi phục qua UI chưa được bật (thiếu {@code DB_RESTORE_PASSWORD}) — xem BackupProperties. */
    ADM_2010("ADM-2010", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Chuỗi xác nhận nhiều bước không khớp (architecture-review.md §7.3). */
    ADM_2011("ADM-2011", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Bản sao lưu không dùng được: mất tệp, hoặc checksum không khớp lúc ghi. */
    ADM_2012("ADM-2012", HttpStatus.UNPROCESSABLE_CONTENT),
    /** Khôi phục thất bại — CSDL có thể đang ở trạng thái dở dang, xem runbook. */
    ADM_2013("ADM-2013", HttpStatus.INTERNAL_SERVER_ERROR),

    // ---- MOD-05 Ma trận phân quyền sửa được (T27.31, CN-05.2) -------------------
    /**
     * Vai trò hệ thống {0} — ⛔ không sửa quyền được. Người đọc <b>đầu tiên</b> của cột {@code
     * roles.is_system}, vốn mang bảo đảm này trong một dòng chú thích SQL suốt 26 ngày mà ⛔ chưa mã
     * nào ép.
     */
    ADM_2014("ADM-2014", HttpStatus.FORBIDDEN),
    /**
     * Mã quyền ⛔ không có trong danh mục: {0}. ⚠ Cố ý ⛔ <b>không</b> bỏ qua trong im lặng như
     * {@code replaceRoles} — một mã gõ sai lặng lẽ biến mất tạo ra một vai trò khuyết quyền mà ⛔
     * không ai biết thiếu từ bao giờ.
     */
    ADM_2015("ADM-2015", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Đang tự gỡ quyền quản trị phân quyền của chính mình khỏi vai trò {0} — thao tác này ⛔ không
     * quay lui được bằng bất kỳ đường nào trong giao diện.
     */
    ADM_2016("ADM-2016", HttpStatus.UNPROCESSABLE_CONTENT),

    // ---- MOD-05 Liên kết tài khoản ↔ hồ sơ CBNV (T51.8, CN-05.1) ----------------
    /**
     * Hồ sơ CBNV {0} đã liên kết với tài khoản {1}. Hai tài khoản cùng trỏ một hồ sơ là hai con
     * người cùng khai mình <i>là</i> một nhân viên — mà quyền tự đọc trường 🔒 suy thẳng từ cột ấy.
     * Chỉ mục {@code uq_users_employee_id} ép cùng bất biến ở tầng CSDL; mã lỗi này tồn tại để người
     * dùng đọc được <b>tên tài khoản kia</b> thay vì một lỗi ràng buộc trần.
     */
    ADM_2017("ADM-2017", HttpStatus.CONFLICT),
    /**
     * ⛔ Không tự liên kết tài khoản của CHÍNH MÌNH tới một hồ sơ CBNV.
     *
     * <p>Liên kết ⛔ không phải một trường hồ sơ, nó là <b>một quyền</b>: vế thứ hai của CN-04.7 suy
     * quyền đọc CCCD/lương/số tài khoản thẳng từ {@code users.employee_id}. Tự trỏ tài khoản mình
     * sang một hồ sơ bất kỳ là <b>tự cấp cho mình</b> quyền đọc dữ liệu cá nhân nhạy cảm của người
     * ấy — mà quyền gác cửa ở đây ({@code adm:user:manage}) thì ADMIN <b>có</b>, trong khi
     * {@code hr:employee:view-sensitive} thì đặc tả loại trừ ADMIN tường minh.
     *
     * <p>⇒ Liên kết tài khoản người khác là việc quản trị bình thường; liên kết chính mình phải nhờ
     * một tài khoản quản trị thứ hai. Bất biến này áp <b>đều cho mọi vai trò, kể cả SUPER_ADMIN</b>:
     * một luật miễn trừ đúng vai trò mạnh nhất là một luật trang trí.
     */
    ADM_2018("ADM-2018", HttpStatus.FORBIDDEN),
    /**
     * Job mã hoá lại sang khoá {0} xong {1} hàng mà <b>còn {2} hàng</b> mang khoá cũ — T61.11.
     *
     * <p>⛔ Job phải HỎNG, ⛔ xanh kèm một dòng log: một job xanh là lời mời gỡ khoá cũ, và gỡ khoá cũ
     * khi còn bản mã dùng nó là mất dữ liệu vĩnh viễn (runbook {@code xoay-khoa.md} §A). Câu này nằm ở
     * {@code jobs.last_error}, nơi {@code JobStatus.FAILED} dặn người vận hành đọc.
     */
    ADM_2019("ADM-2019", HttpStatus.UNPROCESSABLE_CONTENT),
    /**
     * Tự xoá tài khoản của chính mình — T61.21.
     *
     * <p>Xoá là xoá mềm mà giao diện ⛔ có đường khôi phục, và tài khoản đang thao tác mất quyền NGAY
     * (`AuthorityLoader` lọc `deleted_at`). Cùng hình dạng `ADM-2016` (tự gỡ quyền phân quyền của mình).
     */
    ADM_2020("ADM-2020", HttpStatus.FORBIDDEN),
    /** Tự đặt lại 2FA của chính mình — đúng thao tác kẻ chiếm phiên muốn làm (T61.30). */
    ADM_2021("ADM-2021", HttpStatus.FORBIDDEN),
    /**
     * Cấp cho một vai trò/tài khoản quyền mà NGƯỜI THAO TÁC ⛔ có — T54.4.
     *
     * <p>ADMIN mang {@code adm:role:manage} và vai trò ADMIN {@code is_system = FALSE} ⇒ trước bản vá, ba cú bấm là
     * tự thêm {@code hr:employee:view-sensitive} (quyền đặc tả loại trừ ADMIN tường minh). Trần cấp quyền = tập quyền
     * của chính người cấp.
     */
    ADM_2022("ADM-2022", HttpStatus.FORBIDDEN),
    /**
     * Thao tác nhạy cảm đòi nhập lại mã xác thực hai bước NGAY LÚC NÀY — T61.42/T61.44.
     *
     * <p>Thiếu mã, hoặc tài khoản chưa đăng ký 2FA. Mã SAI vẫn là {@code AUTH-0004} (và bị đếm vào khoá tài khoản).
     */
    ADM_2023("ADM-2023", HttpStatus.FORBIDDEN),
    /**
     * Mã xác thực hai bước nhập lại cho thao tác nhạy cảm KHÔNG đúng — T61.42.
     *
     * <p>⛔⛔ Cố ý ⛔ dùng {@code AUTH-0004} (401) như bước đăng nhập: giao diện đọc MỌI 401 là "phiên hết hạn" ⇒ làm
     * mới token rồi GỬI LẠI cùng mã sai (lượt sai bị đếm HAI lần) ⇒ 401 lần nữa ⇒ xoá phiên, đá người dùng ra màn
     * hình đăng nhập. Đường khôi phục CSDL mang đúng khuyết tật ấy từ WS-7 tới 15/09/2026.
     */
    ADM_2024("ADM-2024", HttpStatus.FORBIDDEN),
    /**
     * Tự đặt lại mật khẩu của CHÍNH MÌNH qua cửa quản trị — T61.31.
     *
     * <p>⛔ Cửa ấy ⛔ hỏi mật khẩu cũ (đó là cả công dụng của nó), nên cho tự dùng là biến một phiên mượn được
     * thành một lượt chiếm tài khoản vĩnh viễn — đúng thứ {@code AUTH-0001} ở đường tự đổi mật khẩu đang chặn.
     * Cùng lý lẽ với {@code ADM-2021} ở đường đặt lại 2FA.
     */
    ADM_2025("ADM-2025", HttpStatus.FORBIDDEN),
    /**
     * Trưởng / phó đơn vị đặt ⛔ hợp lệ — H24.
     *
     * <p>Hai ô ấy quyết định <b>ai nhận cảnh báo ngưỡng</b> của G11, nên một giá trị sai ở đây ⛔ hỏng một
     * màn hình — nó làm cảnh báo tới 0 người trong im lặng. Ba ca gộp một mã vì cả ba dẫn tới cùng một việc
     * (chọn lại người): tài khoản ⛔ tồn tại · tài khoản ⛔ còn {@code ACTIVE} · trưởng trùng phó.
     */
    ADM_2026("ADM-2026", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    /** Mã hiển thị ra API và ghi vào log, VD {@code OPS-2001}. */
    public String code() {
        return code;
    }

    /** HTTP status trả về cho mã này — cố định, controller không tự chọn. */
    public HttpStatus status() {
        return status;
    }

    /** Khoá tra message trong {@code error-messages.properties} — trùng luôn với mã. */
    public String messageKey() {
        return code;
    }
}
