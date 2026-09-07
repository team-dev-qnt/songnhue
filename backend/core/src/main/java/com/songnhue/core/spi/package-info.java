/**
 * Tầng SPI — hợp đồng công khai của {@code core} cho các module nghiệp vụ gọi.
 *
 * <p><b>Đây là package DUY NHẤT của {@code core} được phép import chéo giữa các module</b>, cùng với
 * ngoại lệ hạ tầng {@code core.common.*}. Module khác cấm import {@code domain/}, {@code infra/},
 * {@code application/} — {@code ModuleBoundaryTest} chặn trong CI (conventions.md §1.1, quy tắc 6).
 *
 * <h2>Luật viết chữ ký ở đây</h2>
 *
 * <p><b>Mọi kiểu xuất hiện trong chữ ký của package này chỉ được thuộc: chính {@code core.spi},
 * {@code core.common}, JDK, hoặc thư viện ngoài.</b> Không bao giờ là {@code core.domain.*}.
 *
 * <p>Lý do không hiển nhiên và đã suýt làm hỏng cả thiết kế: một interface đặt đúng chỗ nhưng trả về
 * entity domain thì <b>vẫn</b> vi phạm ranh giới — module nghiệp vụ nhận giá trị trả về là phải
 * import lớp đó. Nói cách khác, chuyển sáu dịch vụ sang {@code spi} <i>không phải</i> là thêm sáu
 * interface mỏng: phải kèm một bộ record riêng ({@link AttachmentRef}, {@link JobRef},
 * {@link OrgUnitRef}…) làm lớp dịch giữa hai bên.
 *
 * <p>Cái giá phải trả là vài lớp record và một chỗ ánh xạ. Cái mua được là {@code core} đổi mô hình
 * dữ liệu bên trong mà không làm vỡ module nghiệp vụ — đúng thứ một hợp đồng sinh ra để làm, và là
 * điều kiện để tách module thành service riêng về sau (architecture-review.md §6.4, §9.14).
 *
 * <h2>SPI mỏng là cố ý</h2>
 *
 * <p>Chỉ khai những phương thức <b>đang có người gọi</b>. Thêm phương thức "cho đủ bộ" là tạo ra mã
 * không ai chạy và không ai kiểm — thứ sẽ sai lặng lẽ tới lần đầu có người dùng tới. Cần thêm thì
 * thêm lúc cần, kèm bài kiểm của chỗ gọi.
 */
package com.songnhue.core.spi;
