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
    SYS_0008("SYS-0008", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Tệp chưa quét virus xong hoặc đã bị cách ly — {0} là trạng thái quét. */
    SYS_0009("SYS-0009", HttpStatus.CONFLICT),
    /** Bản ghi đã dùng hết hạn mức dung lượng tệp đính kèm (CN-02.3: 500MB/công trình). */
    SYS_0010("SYS-0010", HttpStatus.UNPROCESSABLE_ENTITY),
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
    SYS_0011("SYS-0011", HttpStatus.PAYLOAD_TOO_LARGE),

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
    AUTH_0006("AUTH-0006", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Đang bắt buộc đổi mật khẩu — chặn mọi thao tác khác cho tới khi đổi xong. */
    AUTH_0007("AUTH-0007", HttpStatus.FORBIDDEN),
    /** Phiên bị thu hồi vì phát hiện dùng lại refresh token cũ — buộc đăng nhập lại (§4.1). */
    AUTH_0008("AUTH-0008", HttpStatus.UNAUTHORIZED),
    AUTH_3001("AUTH-3001", HttpStatus.FORBIDDEN),
    /** Dữ liệu ngoài phạm vi đơn vị — scope filter tầng 3 chặn (§4.2). */
    AUTH_3002("AUTH-3002", HttpStatus.FORBIDDEN),

    // ---- MOD-01 Cổng thông tin điện tử -----------------------------------------
    CMS_2001("CMS-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    CMS_2002("CMS-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Xoá danh mục còn bài viết — CN-01.2 bắt chuyển bài đi trước. */
    CMS_2003("CMS-2003", HttpStatus.CONFLICT),
    /** Xoá danh mục còn danh mục con. */
    CMS_2004("CMS-2004", HttpStatus.CONFLICT),
    /** Cây danh mục vượt quá 3 cấp. */
    CMS_2005("CMS-2005", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Bài viết phải thuộc ít nhất một danh mục. */
    CMS_2006("CMS-2006", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Sửa nội dung khi bài đang chờ duyệt — CN-01.1 khoá chỉnh sửa ở trạng thái này. */
    CMS_2007("CMS-2007", HttpStatus.CONFLICT),
    /** Xoá thư mục media còn tệp bên trong. */
    CMS_2008("CMS-2008", HttpStatus.CONFLICT),
    /** Xoá tệp media đang được bài viết tham chiếu. */
    CMS_2009("CMS-2009", HttpStatus.CONFLICT),
    /** Cây menu vượt quá 3 cấp. */
    CMS_2010("CMS-2010", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Xoá mục menu còn mục con. */
    CMS_2011("CMS-2011", HttpStatus.CONFLICT),
    /** Đích của mục menu không tồn tại hoặc đã bị xoá. */
    CMS_2012("CMS-2012", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Mục con phải cùng vị trí (Header/Footer) với mục cha. */
    CMS_2013("CMS-2013", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Lịch hiển thị banner có ngày kết thúc không sau ngày bắt đầu. */
    CMS_2014("CMS-2014", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Tải logo cho mục menu không thuộc dải "Liên kết website" (vị trí LIEN_KET). */
    CMS_2015("CMS-2015", HttpStatus.UNPROCESSABLE_ENTITY),
    CMS_5001("CMS-5001", HttpStatus.BAD_GATEWAY),

    // ---- MOD-02 Vận hành công trình --------------------------------------------
    OPS_2001("OPS-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2002("OPS-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2003("OPS-2003", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2004("OPS-2004", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2005("OPS-2005", HttpStatus.CONFLICT),
    OPS_2006("OPS-2006", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2007("OPS-2007", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Mã công trình đã tồn tại — mã là duy nhất toàn hệ thống (CN-02.1). */
    OPS_2008("OPS-2008", HttpStatus.CONFLICT),
    /** Nhập thông số kỹ thuật không thuộc loại công trình đang lập hồ sơ. */
    OPS_2009("OPS-2009", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Toạ độ phải đủ cả vĩ độ và kinh độ — một nửa toạ độ là một điểm sai trên bản đồ. */
    OPS_2010("OPS-2010", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Lý trình sai định dạng {@code K<km>+<m>}, VD {@code K0+390}. */
    OPS_2011("OPS-2011", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Cụm công trình còn công trình bên trong — chuyển hết đi rồi mới xoá được. */
    OPS_2012("OPS-2012", HttpStatus.CONFLICT),
    /** Cấp quản lý "Cụm" bắt buộc chọn cụm. */
    OPS_2013("OPS-2013", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Mã cụm công trình đã tồn tại. */
    OPS_2014("OPS-2014", HttpStatus.CONFLICT),
    /** Tệp nhập không đọc được, hoặc thiếu cột bắt buộc. */
    OPS_2015("OPS-2015", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Tệp nhập còn dòng lỗi — chạy khô báo lỗi thì không dòng nào được ghi. */
    OPS_2016("OPS-2016", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Đơn vị thực hiện: đúng MỘT trong hai cột nội bộ / nhà thầu ngoài (điểm nghiệp vụ 17). */
    OPS_2017("OPS-2017", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Mã tình hình vận hành đã ẩn — {@code OPS-2007} chỉ cho ẩn, nên ẩn rồi phải hết ghi được. */
    OPS_2018("OPS-2018", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Lô nhập nhanh còn dòng lỗi — báo đủ theo từng dòng, và không dòng nào được ghi. */
    OPS_2019("OPS-2019", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * Thời điểm hiệu lực ở tương lai — V1/V3.
     *
     * <p>{@code banGhiMoiNhat} sắp theo {@code effective_at DESC}, nên một dòng đề ngày mai
     * <b>ghim</b> cả trạng thái dẫn xuất lẫn dòng trên cổng cho tới khi tới ngày ấy — và nó ghim
     * bằng cách <i>trông đúng</i>. Lùi ngày thì vẫn hợp lệ (bù nhật ký), chỉ chặn cận trên.
     */
    OPS_2020("OPS-2020", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * {@code maintenance_logs.alert_event_public_id} trỏ vào một cảnh báo không tồn tại — T33.4.
     *
     * <p>Cột ấy có từ 21/08, có setter, có trường trong form, và ⛔ <b>chưa bao giờ được đối chiếu
     * với bất cứ thứ gì</b>: một UUID bất kỳ lưu thành công. ⛔ Không chữa bằng khoá ngoại — hai
     * module không thấy nhau, nên tính toàn vẹn do tầng dịch vụ giữ, qua {@code HydroAlertPort}.
     */
    OPS_2021("OPS-2021", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Trạng thái công trình là giá trị dẫn xuất — client sửa trực tiếp là từ chối. */
    OPS_3001("OPS-3001", HttpStatus.FORBIDDEN),

    // ---- MOD-03 Thủy văn --------------------------------------------------------
    HYD_1001("HYD-1001", HttpStatus.NOT_FOUND),
    /** Trùng mã trong một danh mục thuỷ văn — {0} là mã bị trùng. */
    HYD_1002("HYD-1002", HttpStatus.CONFLICT),
    HYD_2001("HYD-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    HYD_2002("HYD-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    HYD_2003("HYD-2003", HttpStatus.UNPROCESSABLE_ENTITY),
    HYD_2004("HYD-2004", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * Vai trò của liên kết CHÍNH khác vai trò chính thức của điểm đo (A2b).
     *
     * <p>Hai giá trị này lệch nhau thì biểu tổng hợp xếp điểm đo vào nhầm cột TL/HL, và không có
     * triệu chứng nào ngoài một con số nằm sai chỗ.
     */
    HYD_2005("HYD-2005", HttpStatus.UNPROCESSABLE_ENTITY),
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
    HYD_2006("HYD-2006", HttpStatus.UNPROCESSABLE_ENTITY),
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
    HYD_2012("HYD-2012", HttpStatus.UNPROCESSABLE_ENTITY),

    /** Ngày bắt đầu sau ngày kết thúc — T34.3. Khoảng rỗng trả 0 hàng, và 0 hàng đọc như "không có dữ liệu". */
    HYD_2013("HYD-2013", HttpStatus.UNPROCESSABLE_ENTITY),

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

    // ---- MOD-04 Nhân sự ---------------------------------------------------------
    HR_2001("HR-2001", HttpStatus.UNPROCESSABLE_ENTITY),

    // ---- MOD-05 Quản trị --------------------------------------------------------
    ADM_2001("ADM-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    ADM_2002("ADM-2002", HttpStatus.CONFLICT),
    /** Chuyển đơn vị vào chính cây con của nó — cắt rời cả nhánh khỏi cây mà dữ liệu vẫn còn. */
    ADM_2003("ADM-2003", HttpStatus.UNPROCESSABLE_ENTITY),
    ADM_2004("ADM-2004", HttpStatus.CONFLICT),
    ADM_2005("ADM-2005", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Tham số {0} không nhận giá trị này — yêu cầu: {1}. */
    ADM_2006("ADM-2006", HttpStatus.UNPROCESSABLE_ENTITY),
    ADM_2007("ADM-2007", HttpStatus.FORBIDDEN),

    // ---- MOD-05 Sao lưu & khôi phục (WS-7) --------------------------------------
    /** Sao lưu chưa cấu hình được: thiếu mật khẩu vai trò đọc, hoặc thư mục không ghi được. */
    ADM_2008("ADM-2008", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Đã có lượt sao lưu đang chạy — hai lượt song song chỉ tổ đọc đĩa gấp đôi. */
    ADM_2009("ADM-2009", HttpStatus.CONFLICT),
    /** Khôi phục qua UI chưa được bật (thiếu {@code DB_RESTORE_PASSWORD}) — xem BackupProperties. */
    ADM_2010("ADM-2010", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Chuỗi xác nhận nhiều bước không khớp (architecture-review.md §7.3). */
    ADM_2011("ADM-2011", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Bản sao lưu không dùng được: mất tệp, hoặc checksum không khớp lúc ghi. */
    ADM_2012("ADM-2012", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Khôi phục thất bại — CSDL có thể đang ở trạng thái dở dang, xem runbook. */
    ADM_2013("ADM-2013", HttpStatus.INTERNAL_SERVER_ERROR);

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
