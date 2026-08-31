package com.songnhue.core.spi;

/**
 * Báo cho cổng công khai rằng một tập dữ liệu nó đang hiển thị vừa đổi — <b>nợ T25.22</b>.
 *
 * <h2>Vì sao cổng này phải tồn tại</h2>
 *
 * <p>Bộ nhớ đệm của cổng do {@code PortalCache} ở module {@code content} điều khiển. Nhưng
 * {@code org_units} nằm ở {@code core} và {@code constructions} nằm ở {@code operations}, mà quy tắc
 * 6 không cho module gọi chéo ngoài {@code spi/}. Hệ quả đo được ngày 28/08 (§10.62): <b>sửa dữ liệu
 * tổ chức hoặc công trình không xoá được bộ đệm cổng — cổng trễ tới 5 phút</b>, và người nhập liệu
 * nhìn màn hình báo "lưu thành công" rồi mở cổng thấy y nguyên số cũ, tức là tưởng lưu hỏng.
 *
 * <p>⛔ Cách xử lý <b>sai</b> là nới ArchUnit cho {@code core} import {@code content.application}.
 * Cách đúng là đảo chiều phụ thuộc bằng một cổng ở {@code core.spi} — đúng khuôn
 * {@link HydroAlertPort}: hợp đồng ở {@code core}, cài đặt ở module sở hữu cơ chế.
 *
 * <h2>SPI mỏng là cố ý</h2>
 *
 * <p>Chỉ hai phương thức, đúng hai tập dữ liệu <b>đang</b> có người gọi. Không có
 * {@code evict(String tag)} tự do: một cổng nhận nhãn bất kỳ thì nhãn của cổng công khai trở thành
 * thứ mọi module phải nhớ đúng chính tả, và một nhãn gõ sai không có triệu chứng nào (cùng lý lẽ với
 * {@code SettingAdminPort} bắt buộc khai {@code groupCode} — §10.12).
 */
public interface PortalCachePort {

    /**
     * Sơ đồ tổ chức, lãnh đạo hoặc danh sách Xí nghiệp vừa đổi.
     *
     * <p>Chạm ba trang {@code /gioi-thieu/*}, khối "Đơn vị trực thuộc" và mục đầu mối liên hệ ở
     * trang chủ.
     */
    void orgUnitsChanged();

    /**
     * Danh mục công trình hoặc tình hình vận hành vừa đổi.
     *
     * <p>Chạm trang Danh mục công trình, bản đồ hệ thống trên trang chủ và khối Vận hành công trình.
     */
    void constructionsChanged();
}
