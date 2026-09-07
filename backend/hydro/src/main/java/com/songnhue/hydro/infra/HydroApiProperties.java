package com.songnhue.hydro.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mã số truy cập nguồn thuỷ văn, <b>chỉ dùng để mồi lần đầu</b> — {@code HYDRO_API_KEY}.
 *
 * <h2>⚠ Lớp này đã đổi bản chất so với bản viết ngày 31/08 (WS-27). Đọc trước khi sửa lại.</h2>
 *
 * <p>Bản đầu khai hai trường {@code base-url} + {@code key}, cả hai {@code @NotBlank}, fail-fast
 * theo quy tắc 11. Ba điều phát hiện ngay sau đó làm cách ấy sai:
 *
 * <ol>
 *   <li><b>Nó chặn khởi động ở staging, rehearse và prod.</b> Ba tệp {@code deploy/env/*} ấy có
 *       đúng dòng {@code HYDRO_API_KEY=} — <i>khai nhưng rỗng</i>, vì Công ty chưa cấp mã số.
 *       {@code @NotBlank} biến việc "chưa có mã số của một tính năng Phase 2" thành "toàn bộ hệ
 *       thống không khởi động được", kể cả phần đã nghiệm thu Phase 1.
 *   <li><b>Nó chặn toàn bộ test tích hợp.</b> {@code IntegrationTestBase} không đặt biến này, nên
 *       {@code UnresolvedPlaceholderGuard} nhận nguyên chuỗi {@code "${HYDRO_API_KEY}"} và từ chối
 *       dựng context — mọi bài kiểm tích hợp của mọi module đỏ theo, chứ không riêng {@code hydro}.
 *   <li><b>Nó tạo bản sao thứ hai của một credential.</b> Nhà của mã số là
 *       {@code api_sources.credential} (mã hoá AES-256-GCM, {@code conventions.md} §4.7) và đổi được
 *       trên UI. Nếu biến môi trường cũng bắt buộc thì sau lần xoay khoá đầu tiên, tệp env mang một
 *       mã số cũ, không ai dùng, mà ai đọc cũng tưởng là mã đang chạy.
 * </ol>
 *
 * <h2>Cách đúng: một nhà, một đường mồi</h2>
 *
 * <p><b>Nguồn sự thật duy nhất là {@code api_sources.credential}.</b> Biến môi trường chỉ là đường
 * mồi cho lần triển khai đầu tiên: {@link ApiSourceCredentialBootstrap} đọc nó lúc khởi động, và
 * <b>chỉ ghi khi cột đang rỗng</b>. Đặt xong mã số trên UI rồi thì biến này có hay không cũng không
 * đổi gì — đúng như một giá trị mồi phải thế.
 *
 * <p>⛔ {@code base-url} <b>đã bỏ hẳn</b> khỏi cấu hình: địa chỉ nguồn nằm ở {@code api_sources
 * .base_url} (seed sẵn ở {@code V202608311049}), sửa được trên UI, và khác nhau theo từng nguồn khi
 * có nguồn thứ hai. Giữ thêm một biến môi trường cho cùng giá trị ấy là dựng sẵn một chỗ lệch. Biến
 * {@code HYDRO_API_BASE_URL} vì vậy đã được gỡ khỏi cả năm tệp {@code deploy/env/*} trong cùng
 * commit — luật 27: một biến không ai đọc là một lỗi.
 *
 * <h2>Fail-fast không mất, nó chuyển chỗ</h2>
 *
 * <p>Quy tắc 11 vẫn được giữ, nhưng ở đúng tầm ảnh hưởng của sự cố: <b>không có mã số thì lượt
 * polling từ chối chạy và nói rõ lý do</b> (một dòng {@code sync_logs} với {@code failure_kind =
 * THIEU_MA_SO}, nguồn hiện "Chưa cấu hình mã số" trên màn hình Nguồn dữ liệu), thay vì kéo cả ứng
 * dụng xuống. Trạng thái "chưa cấu hình" là một trạng thái <i>nhìn thấy được</i>, không phải im lặng.
 *
 * <h2>⚠ Dấu {@code ;} ở cuối mã số là MỘT PHẦN CỦA GIÁ TRỊ</h2>
 *
 * <p>Endpoint đòi {@code ?key=<mã số>;} — thiếu dấu chấm phẩy thì nguồn trả chuỗi
 * {@code not.working}, <b>trông y hệt lỗi sai mã số</b>. Nên ở đây không {@code trim()}, không
 * {@code strip()}, và {@link #getKey()} trả nguyên văn.
 *
 * <p>⛔ <b>Không log, không trả ra API, không đưa vào bản xuất cấu hình</b> (§4.7). Lớp này cố ý ghi
 * đè {@code toString()}: bản mặc định in hết trường, và một dòng log lúc khởi động là đủ để mã số
 * nằm vĩnh viễn trong tệp log.
 */
@ConfigurationProperties(prefix = "app.hydro.api")
public class HydroApiProperties {

    /**
     * Tiền tố của các giá trị mẫu trong {@code deploy/env/local.env*}.
     *
     * <p>{@code HYDRO_API_KEY=REPLACE_ME_MASO;} là chỗ điền, không phải mã số. Mồi giá trị đó vào
     * CSDL thì cột {@code credential} khác NULL ⇒ hệ thống báo "đã cấu hình", poller gọi nguồn bằng
     * một mã sai và nhận {@code not.working} — mất hẳn trạng thái "chưa cấu hình" vốn là thứ duy
     * nhất chỉ đúng chỗ cần làm.
     */
    static final String TIEN_TO_CHO_DIEN = "REPLACE_ME";

    /** ⚠ Rỗng là hợp lệ và có nghĩa: "chưa mồi, mã số đặt trên UI". */
    private String key = "";

    /**
     * Cho phép nguồn trỏ vào máy nội bộ ({@code 127.0.0.1}, {@code 10.*}, {@code localhost}…).
     *
     * <h2>⛔⛔ Mặc định {@code false}, và nó phải ở nguyên như vậy ngoài môi trường phát triển</h2>
     *
     * <p>{@code DiaChiNguon} chặn các dải nội bộ để một dòng {@code api_sources.base_url} sai —
     * dù do tài khoản quản trị bị chiếm, do một bản khôi phục cũ, hay do một câu {@code UPDATE} tay
     * lúc xử lý sự cố — không biến poller thành công cụ đọc endpoint metadata của máy ảo
     * ({@code 169.254.169.254}) hay gõ cửa các dịch vụ nội bộ. Đó là mục A10 của
     * {@code conventions.md} §4.6.
     *
     * <p>Công tắc này tồn tại vì đúng <b>một</b> lý do: bài kiểm dựng máy chủ HTTP thật (T30.9) bind
     * vào {@code 127.0.0.1}, và một nguồn giả trên máy lập trình viên cũng vậy. Không có nó thì hoặc
     * phép kiểm SSRF phải bị gỡ, hoặc bài kiểm phải mock đúng chỗ mã chạm ra ngoài — <b>luật 4</b>,
     * thứ đã làm {@code pg_dump} chưa từng chạy suốt 4 ngày mà {@code BackupServiceTest} vẫn xanh.
     *
     * <p>⚠ Một công tắc nới bảo mật mà không ai canh thì sáu tháng nữa nó bật ở prod và không ai
     * biết là từ lúc nào (§10.57 — cổng secret bỏ qua trong im lặng). Có hai vế canh:
     * {@code Bhh40AdapterHttpTest} chứng minh nó <b>chặn thật</b> khi tắt, và
     * {@code HydroEnvSwitchTest} chứng minh không tệp {@code deploy/env/{staging,prod,rehearse}} nào
     * đặt nó.
     */
    private boolean allowInternalHost = false;

    /** ⛔ Giá trị NGUYÊN VĂN, kể cả dấu {@code ;} cuối. Không log giá trị trả về. */
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key == null ? "" : key;
    }

    /** ⛔ Xem javadoc của trường: {@code true} chỉ hợp lệ ở máy lập trình viên và trong bài kiểm. */
    public boolean isAllowInternalHost() {
        return allowInternalHost;
    }

    public void setAllowInternalHost(boolean allowInternalHost) {
        this.allowInternalHost = allowInternalHost;
    }

    /**
     * Có mã số dùng được để mồi hay không.
     *
     * <p>Trả {@code false} cho cả chuỗi rỗng lẫn giá trị chỗ điền — xem {@link #TIEN_TO_CHO_DIEN}.
     */
    public boolean coMaSoDeMoi() {
        return !key.isBlank() && !key.startsWith(TIEN_TO_CHO_DIEN);
    }

    /** ⛔ Cố ý che: mặc định của Java in cả trường {@link #key} — một dòng log là lộ credential. */
    @Override
    public String toString() {
        return "HydroApiProperties{key=***, allowInternalHost=" + allowInternalHost + "}";
    }
}
