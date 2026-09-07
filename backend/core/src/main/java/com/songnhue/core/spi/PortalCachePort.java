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
 * <p>Chỉ ba phương thức, đúng ba tập dữ liệu <b>đang</b> có người gọi. Không có
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

    /**
     * <b>Danh mục</b> thuỷ văn vừa đổi — điểm đo, liên kết công trình, mức cảnh báo, số đo
     * <b>nhập tay</b>, hoặc danh sách điểm đo công bố lên cổng (T35.8). <b>T35.9</b>.
     *
     * <p>Chạm bảng "Mực nước, lượng mưa" ở trang chủ và trang
     * {@code /quan-ly-van-hanh/muc-nuoc-luong-mua}.
     *
     * <h2>⛔⛔ Đường ghi SỐ ĐO ⛔ KHÔNG được gọi phương thức này</h2>
     *
     * <p>Đây là ranh giới chịu lực của cả T35.9, và nó ⛔ không hiển nhiên từ tên phương thức — nên
     * nó phải nằm trong hợp đồng chứ ⛔ không nằm trong trí nhớ người viết lời gọi tiếp theo.
     *
     * <p>Đo được 04/09/2026: {@code hydro} có <b>năm</b> đường ghi, và chúng chia làm hai loại khác
     * hẳn nhau về <i>tần suất</i>:
     *
     * <ul>
     *   <li><b>Biên tập / danh mục</b> — con người bấm Lưu, vài lượt một ngày:
     *       {@code StationService}, {@code AlertLevelService}, {@code SoDoNhapTayService}, và lượt
     *       ghi khoá {@code hydro.portal.*}. ⇒ <b>gọi</b>.
     *   <li><b>Số đo</b> — máy ghi, <b>2 phút/lần vĩnh viễn</b>: {@code TelemetryIngestService},
     *       {@code HydroLatestRecomputer}, {@code NguongAlertService}, {@code AlertEventService}.
     *       ⇒ ⛔ <b>không gọi</b>.
     * </ul>
     *
     * <p>⚠ Vì sao vế thứ hai ⛔ không phải là "quên nối": {@code PortalCache} có {@code dedupKey},
     * nhưng hàng đợi chỉ gộp <b>khi việc cũ còn đang chờ</b> — mà worker rút việc mỗi 5 giây, nên
     * hai lượt cách nhau 2 phút ⛔ không bao giờ gộp. Nối đường ingest là đặt <b>~720 việc dựng lại
     * cổng mỗi ngày</b> để phục vụ một trang mà OI-09 đã cam kết với Công ty là làm mới
     * <b>5 phút/lần</b>. Cửa sổ ISR ấy <i>đã</i> là câu trả lời cho số đo mới; xoá đệm thêm chỉ đổi
     * 5 phút lấy 2 phút và trả bằng một hàng đợi không bao giờ rỗng.
     *
     * <p>📌 Ghi ở đây vì T27.7 đã trả giá đúng chỗ này theo chiều ngược lại: ba điểm ghi được nối,
     * điểm ghi thứ tư ra đời <b>cùng đợt</b> mang lại đúng lỗi cũ. Một ranh giới chỉ sống trong đầu
     * người viết thì lời gọi thứ năm sẽ đặt sai bên.
     */
    void hydroStationsChanged();
}
