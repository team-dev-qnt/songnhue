package com.songnhue.app.testsupport;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Đếm số câu lệnh JDBC một thao tác thật sự sinh ra — hạ tầng cho bộ canh N+1 (T63.5, nợ T58.18).
 *
 * <h2>Vì sao kho cần thứ này</h2>
 *
 * {@code BaoCaoNhanSuService.dieuDongTheoThang} bản đầu gọi {@code findByEmployeeId…} <b>trong một
 * vòng lặp</b> ⇒ ~200 lượt truy vấn cho MỘT lượt mở màn hình thống kê, trên VPS 2 nhân. Nó đã được
 * vá, nhưng hình dạng ấy ⛔ có bộ canh nào — và triệu chứng duy nhất của nó là <i>"trang hơi
 * chậm"</i>, thứ ⛔ ai đi đo. Đo tại chỗ: {@code grep "generate_statistics|QueryCount|
 * datasource-proxy"} toàn mọi cây {@code src} của backend = <b>0</b>.
 *
 * <h2>⛔⛔ Đếm ĐỘ DỐC, ⛔ đếm một NGƯỠNG</h2>
 *
 * Cám dỗ tự nhiên là {@code assertThat(soTruyVan).isLessThan(20)}. Đừng. Một ngưỡng tuyệt đối:
 *
 * <ul>
 *   <li>đỏ vì những thay đổi <b>vô can</b> (thêm một cột tra cứu, một lượt kiểm quyền), và
 *   <li>cách sửa rẻ nhất khi nó đỏ là <b>nâng con số</b> — tức tự tay tháo bộ canh. Dự án đã trả giá
 *       cho đúng hình dạng ấy ở §11.17 và T58.6.
 * </ul>
 *
 * <p>Bất biến thật của N+1 ⛔ phải <i>"ít truy vấn"</i> mà là <b>"số truy vấn ⛔ tăng theo số bản
 * ghi"</b>. Nên phép đo đúng là chạy cùng một thao tác ở <b>hai cỡ dữ liệu</b> rồi so. Nó miễn nhiễm
 * với mọi thay đổi vô can, và nó ⛔ im được bằng cách nới một con số.
 *
 * <h2>Hai điều phải biết khi đọc con số này</h2>
 *
 * <ul>
 *   <li>{@link Statistics} là bộ đếm của <b>cả {@code SessionFactory}</b>, ⛔ phải của một luồng ⇒
 *       chỉ đúng khi bộ kiểm chạy tuần tự. Kho ⛔ khai {@code parallel} nào cho surefire; nếu ngày
 *       nào đó có, bộ canh này phải đổi cách đo chứ ⛔ được nới.
 *   <li>Đếm {@code prepareStatementCount} chứ ⛔ {@code queryExecutionCount}: vế sau chỉ tính truy
 *       vấn HQL/Criteria, nên một vòng lặp gọi native query qua Hibernate sẽ <b>vô hình</b>.
 *   <li>⭐ <b>Phạm vi: câu lệnh qua Hibernate CỘNG câu lệnh qua {@code JdbcTemplate}</b> — nợ T63.6
 *       đã trả 17/09. Bản đầu chỉ đọc {@code Statistics} nên một vòng lặp
 *       {@code JdbcTemplate.queryForObject} cho ra <b>0</b>, trong khi đo được <b>33 tệp</b> ở
 *       {@code src/main} dùng {@code JdbcTemplate} — gồm TOÀN BỘ {@code hydro/infra} và
 *       {@code hr/infra}, tức tầng đọc nặng nhất của hệ, đúng nơi một N+1 gây hại nhất. Nửa thứ hai
 *       nằm ở {@link DemTruyVanJdbc}; phần <b>còn chưa phủ</b> — SQL trên một {@code Connection} lấy
 *       thẳng từ {@code DataSource} — khai ở javadoc lớp ấy kèm số đo: hôm nay <b>0</b> đường.
 * </ul>
 */
public final class DemTruyVan {

    private final Statistics thongKe;

    public DemTruyVan(EntityManagerFactory emf) {
        this.thongKe = emf.unwrap(SessionFactory.class).getStatistics();
        // Bật bằng MÃ chứ ⛔ bằng thuộc tính `hibernate.generate_statistics`: thuộc tính buộc lớp
        // kiểm phải khai @TestPropertySource riêng, và mỗi tập thuộc tính khác nhau là một context
        // Spring RIÊNG — cái giá đã ghi ở javadoc `IntegrationTestBase`.
        this.thongKe.setStatisticsEnabled(true);
    }

    /**
     * Số câu lệnh JDBC mà {@code hanhDong} sinh ra — <b>cả hai đường</b>.
     *
     * <p>⚠ Lấy {@code max} chứ ⛔ CỘNG: từ T63.6 bộ đếm JDBC bọc chính {@code DataSource} mà
     * Hibernate cũng dùng, nên một câu lệnh của Hibernate được **cả hai** bộ đếm ghi nhận — cộng
     * lại là đếm đôi. Vế Hibernate giữ nguyên để làm đối chứng: con số JDBC phải luôn ≥ nó, và
     * ngày nào nó nhỏ hơn thì bọc {@code DataSource} đã tuột khỏi một đường nào đó.
     */
    public long dem(Runnable hanhDong) {
        thongKe.clear();
        DemTruyVanJdbc.datLai();
        hanhDong.run();
        return Math.max(thongKe.getPrepareStatementCount(), DemTruyVanJdbc.daDem());
    }
}
