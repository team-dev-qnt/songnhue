package com.songnhue.core.common.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Khai báo cho Hibernate biết các hàm SQL của riêng dự án.
 *
 * <h2>Vì sao cần lớp này</h2>
 *
 * HQL cho gọi hàm lạ bằng {@code function('ten', ...)}, nhưng Hibernate 6 không đoán kiểu trả về —
 * nó coi kết quả là {@code Object}, và câu nào so sánh chuỗi sẽ chết ngay lúc <b>dựng repository</b>:
 *
 * <pre>Operand of 'like' is of type 'java.lang.Object' which is not a string</pre>
 *
 * <p>Điểm đáng nói: lỗi này nổ lúc khởi động chứ không phải lúc chạy truy vấn — Spring Data biên
 * dịch mọi {@code @Query} khi tạo bean. Đó là tin tốt (không có bản build nào lên được với câu truy
 * vấn hỏng), nhưng nó cũng làm thông báo lỗi hiện ra dưới dạng "không tạo được bean" và dễ bị đọc
 * nhầm thành lỗi cấu hình Spring.
 *
 * <h2>Cách Hibernate tìm thấy lớp này</h2>
 *
 * Qua {@link java.util.ServiceLoader} — tệp {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 * ⚠ Xoá tệp đó thì lớp này <b>không chạy và không báo gì cả</b>; triệu chứng là lỗi kiểu ở trên.
 * {@code CoreFunctionContributorTest} canh đúng chỗ đó.
 */
public class CoreFunctionContributor implements FunctionContributor {

    /** Khớp tên hàm tạo ở migration {@code V202608191014__core_text_search_function.sql}. */
    public static final String KHONG_DAU = "sn_khong_dau";

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions
                .getFunctionRegistry()
                .registerPattern(
                        KHONG_DAU,
                        KHONG_DAU + "(?1)",
                        contributions
                                .getTypeConfiguration()
                                .getBasicTypeRegistry()
                                .resolve(StandardBasicTypes.STRING));
    }
}
