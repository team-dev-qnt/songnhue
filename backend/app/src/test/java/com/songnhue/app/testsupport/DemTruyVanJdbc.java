package com.songnhue.app.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Đếm câu lệnh ở <b>tầng JDBC</b> — nửa còn lại của {@link DemTruyVan}, trả nợ T63.6.
 *
 * <h2>Vì sao cần nửa này</h2>
 *
 * Bản đầu chỉ đọc {@code Statistics} của Hibernate nên chỉ thấy câu lệnh đi <b>qua Hibernate</b>.
 * Đo ngày 17/09: <b>33 tệp</b> ở {@code src/main} dùng {@code JdbcTemplate} — trong đó <b>toàn
 * bộ</b> {@code hydro/infra} (báo cáo, biểu đồ, lưới mực nước, alert engine) và {@code hr/infra}
 * ({@code QuanSoRepository}, {@code DanhBaRepository}). Lỗ hổng ⛔ phải một lớp lẻ: nó là <b>tầng
 * đọc nặng nhất của hệ</b>, đúng nơi một N+1 gây hại nhất, và cái xanh của bộ canh N+1 đọc như một
 * lời bảo đảm cho phạm vi nó ⛔ soi (luật 28).
 *
 * <h2>⛔⛔ Vì sao bọc {@code DataSource} chứ ⛔ bọc {@code JdbcTemplate}</h2>
 *
 * Bản nháp đầu tiên cho {@code JdbcTemplateDem extends JdbcTemplate} ghi đè ba phương thức
 * {@code execute} công khai. Nó <b>đếm ra 0</b>, và bài tự-kiểm bắt được: {@code queryForObject}
 * đi qua một overload <b>private</b> {@code execute(StatementCallback, boolean)}, nên bản ghi đè
 * ⛔ bao giờ được gọi. Bean ĐÃ bị thay (thông điệp đỏ in ra {@code JdbcTemplateDem}) mà con số vẫn
 * 0 — một bộ đếm *"đã cài đúng chỗ"* mà đếm sai, đúng hình dạng luật 9.
 *
 * <p>Và cách sửa *"bọc riêng {@code DataSource} của {@code JdbcTemplate}"* thì <b>tệ hơn cả hỏng</b>:
 * Spring bind connection vào {@code TransactionSynchronizationManager} <b>theo danh tính của
 * {@code DataSource}</b>, nên một {@code JdbcTemplate} cầm {@code DataSource} KHÁC sẽ mở kết nối
 * riêng, <b>ngoài giao dịch đang chạy</b> — tức bộ đo làm đổi ngữ nghĩa giao dịch của chính thứ nó
 * đang đo.
 *
 * <p>⇒ Bọc <b>một</b> bean {@code DataSource} mà mọi bên cùng dùng: Hibernate, {@code JdbcTemplate},
 * và transaction manager vẫn thấy đúng một đối tượng, nên ngữ nghĩa ⛔ đổi, còn mọi câu lệnh đều đi
 * qua {@code Connection.prepareStatement} / {@code createStatement} / {@code prepareCall}.
 *
 * <p>⚠ {@code unwrap} / {@code isWrapperFor} chuyển tiếp nguyên vẹn — Spring Boot dùng chúng để bóc
 * ra {@code HikariDataSource} cho metrics; nuốt hai phương thức ấy là làm hỏng một thứ vô can.
 *
 * <p>⚠ Bộ đếm là <b>toàn cục</b>, y như {@code Statistics}: truy vấn của một bài kiểm qua HTTP chạy
 * trên luồng Tomcat chứ ⛔ phải luồng gọi, nên {@code ThreadLocal} ⛔ đếm được gì. ⇒ Con số chỉ đúng
 * khi bộ kiểm chạy <b>tuần tự</b>; kho hôm nay ⛔ khai {@code parallel} nào cho surefire.
 */
@TestConfiguration
public class DemTruyVanJdbc {

    /** Ba cửa duy nhất sinh ra một câu lệnh trên một {@link Connection}. */
    private static final Set<String> CUA_SINH_CAU_LENH = Set.of("prepareStatement", "createStatement", "prepareCall");

    private static final AtomicLong SO_CAU_LENH = new AtomicLong();

    static void datLai() {
        SO_CAU_LENH.set(0);
    }

    static long daDem() {
        return SO_CAU_LENH.get();
    }

    /** Thay mọi bean {@link DataSource} bằng bản có đếm. */
    @Bean
    static BeanPostProcessor bocDataSourceDeDem() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSource goc && !Proxy.isProxyClass(bean.getClass())) {
                    return boc(DataSource.class, goc, (phuongThuc, ketQua) -> {
                        if ("getConnection".equals(phuongThuc.getName()) && ketQua instanceof Connection ket) {
                            return boc(Connection.class, ket, (m, r) -> {
                                if (CUA_SINH_CAU_LENH.contains(m.getName())) {
                                    SO_CAU_LENH.incrementAndGet();
                                }
                                return r;
                            });
                        }
                        return ketQua;
                    });
                }
                return bean;
            }
        };
    }

    /** Nhìn kết quả của mỗi lời gọi rồi trả về thứ sẽ giao cho nơi gọi. */
    @FunctionalInterface
    private interface SauLoiGoi {
        Object xuLy(Method phuongThuc, Object ketQua);
    }

    @SuppressWarnings("unchecked")
    private static <T> T boc(Class<T> giaoDien, T that, SauLoiGoi sau) {
        InvocationHandler handler = (proxy, phuongThuc, doiSo) -> {
            try {
                Object ketQua = phuongThuc.invoke(that, doiSo);
                return sau.xuLy(phuongThuc, ketQua);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (T) Proxy.newProxyInstance(giaoDien.getClassLoader(), new Class<?>[] {giaoDien}, handler);
    }
}
