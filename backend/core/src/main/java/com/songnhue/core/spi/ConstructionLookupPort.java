package com.songnhue.core.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ⭐⭐ Tra công trình từ ngoài module {@code operations} — <b>T28.19, đóng 03/09/2026</b>.
 *
 * <h2>Cổng này được viện dẫn từ 31/08 mà chưa từng tồn tại</h2>
 *
 * <p>Javadoc của {@code StationConstruction} và chú thích của migration {@code V202608311049} đều
 * nói rằng tính toàn vẹn của liên kết điểm đo ↔ công trình <i>"do tầng dịch vụ giữ, qua
 * ConstructionLookupPort"</i> — trong khi {@code find} toàn kho trả <b>0 tệp</b>. Hệ quả đo được:
 * bảng {@code station_constructions} có đủ lược đồ, entity, repository, 4 chỉ mục và một ràng buộc
 * {@code is_primary}, mà ⛔ <b>không một dòng mã nào tạo được một hàng</b> (luật 27 ở dạng nặng
 * nhất: cả một cơ chế chỉ có nửa đọc).
 *
 * <h2>⚠ Vì sao cổng nằm ở {@code core.spi} chứ không ở {@code operations.spi}</h2>
 *
 * <p>Vì {@code hydro} <b>không phụ thuộc</b> {@code operations} — đo được ở {@code hydro/pom.xml}:
 * dependency duy nhất là {@code songnhue-core}. Một cổng đặt ở {@code operations.spi} thì
 * {@code hydro} ⛔ không compile nổi. Chú thích viện dẫn nó suốt hai tuần vì không ai thử viết dòng
 * import đầu tiên.
 *
 * <p>Khuôn đúng đã có sẵn và chạy thật: {@link HydroAlertPort} (hợp đồng ở {@code core.spi}, cài đặt
 * ở {@code hydro}) và {@link PortalCachePort} (cài đặt ở {@code content}). Spring nối hai đầu lúc
 * dựng context trong {@code app}, nên hai module không thấy nhau ở tầng biên dịch.
 *
 * <h2>⛔ Cổng này CÓ lọc phạm vi đơn vị — và đó là chủ ý, không phải sơ suất</h2>
 *
 * <p>Đây là đường phục vụ <b>người dùng đang thao tác</b>: chọn công trình để khai một liên kết.
 * Nên cài đặt đi qua đúng con đường mọi đường ghi khác của MOD-02 đã đi
 * ({@code ConstructionService.get} → {@code ScopeGuard.require}) và ném {@code AUTH-3002} khi vượt
 * phạm vi.
 *
 * <p>⚠⚠ ⛔ <b>Cấm dùng cổng này cho mắt xích 3 của trạng thái công trình.</b> Câu hỏi <i>"công trình
 * này có cảnh báo đang mở không"</i> là một <b>sự thật về công trình</b>, không phụ thuộc ai đang
 * nhìn — và {@code operational_status} là một cột <b>ghi xuống CSDL</b>. Trộn một câu có lọc vào
 * chuỗi bốn mắt xích không lọc thì kết quả phụ thuộc <i>ai bấm F5 sau cùng</i>, và giá trị sai được
 * ghi cho <b>tất cả mọi người</b> (luật 13, §10.35 lỗi 2 — đã trả giá một lần ở đúng hàm ấy).
 */
public interface ConstructionLookupPort {

    /**
     * Tra một công trình theo định danh công khai, <b>có kiểm phạm vi đơn vị</b>.
     *
     * @return rỗng khi công trình không tồn tại hoặc đã xoá mềm
     * @throws com.songnhue.core.common.exception.PermissionDeniedException khi công trình có thật nhưng
     *     ngoài phạm vi của người đang gọi — ⛔ <b>không</b> trả {@code Optional.empty()}: gộp
     *     "không có" với "không được xem" là biến một tín hiệu an ninh thành một lỗi nhập liệu, và
     *     xoá luôn dòng sự kiện an ninh mà {@code ScopeGuard} ghi
     */
    Optional<ConstructionRef> timTheoPublicId(UUID publicId);

    /**
     * Tải hàng loạt theo khoá nội bộ — chống N+1 trên màn hình danh sách liên kết.
     *
     * <p>⛔ <b>Không</b> kiểm phạm vi: đây là đường <i>hiển thị kèm</i> của một bản ghi người dùng đã
     * được phép xem. Bỏ sót một công trình ngoài phạm vi ở đây không giấu được gì (họ đã thấy dòng
     * liên kết rồi) mà chỉ làm ô "Công trình" trống rỗng không lý do.
     *
     * @return chỉ chứa những khoá tra được; khoá trỏ vào công trình đã xoá mềm ⛔ không có mặt
     */
    Map<Long, ConstructionRef> timTheoIds(Collection<Long> ids);

    /**
     * ⭐ Tình hình vận hành <b>hiện hành</b> của một tập công trình — T34.4 (BC-11).
     *
     * <h2>⛔ MỘT định nghĩa "hiện hành", ⛔ không hai</h2>
     *
     * <p>Cài đặt <b>phải</b> dùng lại đúng nguồn sự thật mà cổng công khai đang dùng
     * ({@code PublicOperationStatusService.hienHanh()}), ⛔ không viết một câu truy vấn thứ hai. Lý
     * do đã được ghi từ lượt rà kế hoạch: hai định nghĩa lệch nhau <i>một bản ghi</i> là cổng nói
     * cống đang mở trong khi biểu tổng hợp nói đang đóng — cùng một thời điểm, hai màn hình, và ⛔
     * không có gì báo sai.
     *
     * <p>⛔ Cổng này ⛔ <b>không</b> lọc phạm vi đơn vị, cùng lý do với {@code timTheoIds}: nó nuôi
     * một biểu tổng hợp vận hành, và một cột trạng thái đổi theo người đang đăng nhập là đúng lỗi
     * quy tắc 13 (<i>cột dẫn xuất trộn hai nguồn khác chiều lọc</i>) — thứ đã làm trạng thái công
     * trình bị hạ xuống "Bình thường" cho tất cả mọi người chỉ vì một người ngoài đơn vị mở màn hình.
     *
     * @return chỉ chứa công trình <b>đã có</b> ít nhất một bản ghi tình hình vận hành. Công trình
     *     chưa ghi nhận lần nào ⛔ không có mặt — đó là <i>"chưa bắt đầu ghi nhận"</i>, ⛔ không phải
     *     "mã rỗng", và nơi gọi phải hiện hai thứ ấy khác nhau (quy tắc 16)
     */
    java.util.Map<Long, TinhHinhVanHanhRef> tinhHinhHienHanh(java.util.Collection<Long> constructionIds);
}
