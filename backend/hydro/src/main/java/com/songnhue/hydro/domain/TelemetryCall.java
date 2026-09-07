package com.songnhue.hydro.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Đủ thứ để mở <b>một</b> lượt gọi tới nguồn — và không hơn một lượt.
 *
 * <h2>⛔⛔ Bản ghi này mang credential. Đọc ba dòng dưới trước khi sửa.</h2>
 *
 * <ol>
 *   <li>⛔ <b>Không log</b>. {@code toString()} mặc định của record in <i>mọi</i> thành phần, và một
 *       dòng {@code log.debug("gọi {}", call)} là đủ để mã số nằm vĩnh viễn trong tệp log — nơi
 *       nhiều người xem hơn bảng {@code api_sources} và không mã hoá. Vì vậy {@link #toString()} ở
 *       đây <b>ghi đè</b>, giống {@code HydroApiProperties}.
 *   <li>⛔ <b>Không lưu vào trường nào</b>, không đưa vào payload {@code jobs} (payload nằm nguyên
 *       văn trong bảng và lọt vào mọi bản sao lưu). Vòng đời của bản ghi này là một lời gọi HTTP.
 *   <li>⚠ <b>Dấu {@code ;} cuối mã số là một phần của giá trị.</b> {@code getmn.aspx} đòi
 *       {@code ?key=<mã số>;}; thiếu dấu ấy nguồn trả {@code not.working}, <b>trông y hệt lỗi sai
 *       mã số</b>. Nên ở đây ⛔ không {@code trim()}, ⛔ không {@code strip()} — bốn tầng phía trên
 *       ({@code deploy/env}, {@code HydroApiProperties}, {@code ApiSourceService.datMaSo},
 *       {@code ApiSourceCredentialBootstrap}) đã có bài kiểm hồi quy giữ nguyên dấu ấy; đây là tầng
 *       thứ năm và là tầng cuối cùng trước khi giá trị lên dây.
 * </ol>
 *
 * @param baseUrl địa chỉ gốc của nguồn, lấy từ {@code api_sources.base_url} — ⛔ không bao giờ từ
 *     tham số của người dùng cuối (SSRF, {@code conventions.md} §4.6 A10)
 * @param maSo mã số nguyên văn, <b>kể cả dấu {@code ;}</b>
 * @param timeout thời gian chờ tối đa, đã giải qua {@code ApiSourceService.thamSoHieuLuc}
 */
public record TelemetryCall(String baseUrl, String maSo, Duration timeout) {

    public TelemetryCall {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(timeout, "timeout");
        if (maSo == null || maSo.isBlank()) {
            // ⚠ Ép ở hàm dựng, không ở lời dặn (quy tắc 16): "chưa cấu hình mã số" là một trạng thái
            //   nghiệp vụ hợp lệ và phải dừng TRƯỚC khi mở HTTP — gọi nguồn bằng chuỗi rỗng cho ra
            //   `not.working`, tức là biến một trạng thái biết rõ thành một lỗi không phân biệt được
            //   với "mã số sai". Đó đúng là điều `SyncFailureKind.THIEU_MA_SO` sinh ra để tránh.
            throw new IllegalArgumentException("Mã số rỗng — nguồn chưa cấu hình thì phải dừng ở "
                    + "SyncFailureKind.THIEU_MA_SO, ⛔ không mở HTTP rồi đọc not.working");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout phải dương, nhận " + timeout);
        }
    }

    /** ⛔ Cố ý che: bản mặc định của record in cả {@link #maSo}. */
    @Override
    public String toString() {
        return "TelemetryCall{baseUrl=" + baseUrl + ", maSo=***, timeout=" + timeout + "}";
    }
}
