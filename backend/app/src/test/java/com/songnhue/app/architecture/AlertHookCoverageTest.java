package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;

/**
 * ⭐⭐ <b>Mọi đường tạo ra một số đo hợp lệ đều phải đánh giá ngưỡng</b> — WS-33, vế thứ hai của
 * luật 12.
 *
 * <h2>Vì sao bài này tồn tại</h2>
 *
 * <p>Luật 12 nói: <i>đặt bảo đảm ở chỗ dữ liệu đi qua, đừng đặt ở nơi gọi; ⛔ không đặt được thì
 * phải có phép kiểm đếm đủ các đường vào</i>. Ở đây ⛔ <b>không có</b> một chỗ dữ liệu đi qua: ba
 * đường sinh ra một số đo {@code HOP_LE} đi qua ba cơ chế khác hẳn nhau —
 *
 * <ol>
 *   <li>{@code TelemetryIngestService} — poller, {@code INSERT} hàng loạt bằng JDBC
 *   <li>{@code SoDoNhapTayService} — nhập tay, {@code INSERT} một dòng bằng JDBC
 *   <li>{@code HydroReviewService} — duyệt {@code NGHI_NGO → HOP_LE}, ⛔ <b>không INSERT gì cả</b>,
 *       chỉ đổi một cột qua workflow engine
 * </ol>
 *
 * <p>⚠ Đường thứ ba là đường dễ quên nhất, đúng vì nó không ghi dòng mới. Nhưng hệ quả thì y hệt:
 * một giá trị trước đây bị quy tắc 14 loại khỏi mọi phép tính nay <b>được tính</b> — và nếu nó vượt
 * ngưỡng thì cảnh báo phải bắn.
 *
 * <h2>⚠ Đây là hình dạng đã tái phát, ⛔ không phải một lo xa</h2>
 *
 * <p>T27.7 trả nợ xoá đệm cổng ở <b>ba</b> điểm ghi. Điểm ghi <b>thứ tư</b> ra đời <i>cùng đợt</i>
 * và mang lại đúng lỗi cũ — tình hình vận hành lên cổng mà ⛔ không xoá đệm. Không có bài đếm thì
 * lần sau cũng vậy, và triệu chứng vẫn im lặng hoàn hảo: số đo lưu thành công, chuông không kêu.
 */
class AlertHookCoverageTest {

    private static final String GOI_APPLICATION = "com.songnhue.hydro.application";

    private static final String MAY_CANH_BAO = "com.songnhue.hydro.application.NguongAlertService";

    private static final String BO_GHI_TIME_SERIES = "com.songnhue.hydro.infra.HydroTimeSeriesWriter";

    private static final String CONG_WORKFLOW = "com.songnhue.core.spi.WorkflowPort";

    /**
     * ⭐ Ba đường vào, <b>nêu tên</b> — ⛔ không có mục "còn lại".
     *
     * <p>Một khoảng chênh có tên là một quyết định; một khoảng chênh im lặng là một chỗ quên. Thêm
     * một đường ghi thứ tư thì bài {@link #baDuongVaoVaKhongHonKem} đỏ <b>trước</b> khi ai kịp quên
     * gắn móc — và đỏ với một thông điệp chỉ thẳng vào việc phải làm.
     */
    private static final Set<String> DUONG_GHI_SO_DO = Set.of(
            "com.songnhue.hydro.application.TelemetryIngestService",
            "com.songnhue.hydro.application.SoDoNhapTayService",
            "com.songnhue.hydro.application.HydroReviewService");

    /** Lớp ở {@code hydro.application} có gọi tới một trong hai đường ghi số đo hợp lệ. */
    private static Set<String> lopGhiSoDo() {
        Set<String> ra = new TreeSet<>();
        for (JavaClass lop : ProductionClasses.ALL) {
            if (!lop.getPackageName().equals(GOI_APPLICATION)) {
                continue;
            }
            boolean ghi = lop.getMethodCallsFromSelf().stream().anyMatch(goi -> {
                String chu = goi.getTargetOwner().getName();
                String ten = goi.getName();
                return (BO_GHI_TIME_SERIES.equals(chu) && ("writeReadings".equals(ten) || "writeManual".equals(ten)))
                        || (CONG_WORKFLOW.equals(chu) && "execute".equals(ten));
            });
            if (ghi) {
                ra.add(lop.getName());
            }
        }
        return ra;
    }

    private static boolean goiMayCanhBao(String tenLop) {
        return ProductionClasses.ALL.get(tenLop).getMethodCallsFromSelf().stream()
                .anyMatch(goi -> MAY_CANH_BAO.equals(goi.getTargetOwner().getName())
                        && goi.getName().equals("danhGia"));
    }

    @Test
    @DisplayName("⭐⭐ Mọi đường ghi số đo hợp lệ đều gọi máy cảnh báo ngưỡng — luật 12")
    void moiDuongGhiDeuDanhGiaNguong() {
        Set<String> thieu = new TreeSet<>();
        for (String lop : lopGhiSoDo()) {
            if (!goiMayCanhBao(lop)) {
                thieu.add(lop);
            }
        }

        assertThat(thieu)
                .as(
                        """
                        ⛔ Lớp trên ghi một số đo mà ⛔ KHÔNG gọi `NguongAlertService.danhGia`.
                        Hệ quả im lặng hoàn hảo: số đo lưu thành công, ⛔ chuông không kêu, và
                        `alert_events` trông đúng vì nó rỗng.
                        ⇒ Gọi `danhGia(stationId, measurementTypeId, mocDo, giaTri, chatLuong)`
                          TRONG CÙNG giao dịch ghi (T33.5).""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Đúng BA đường vào — thêm đường thứ tư phải làm bài này ĐỎ (luật 29)")
    void baDuongVaoVaKhongHonKem() {
        // ⭐ Khẳng định VỀ SỐ LƯỢNG, ⛔ không chia sẻ giả định nào với phép quét ở bài trên. Đó chính
        //   là thứ đã cứu lượt 28/8: hai bài kiểm chứng ngược cùng sai theo đúng cách thứ chúng kiểm
        //   đang sai, và cái cứu được là một `hasSizeGreaterThanOrEqualTo` (luật 29).
        assertThat(lopGhiSoDo())
                .as(
                        """
                        ⛔ Tập đường ghi số đo đã đổi.
                        Thừa = có một đường ghi MỚI: nó phải gọi máy cảnh báo, rồi khai tên vào
                               `DUONG_GHI_SO_DO`. ⛔ Đừng nới bài kiểm cho hết đỏ.
                        Thiếu = một đường ghi đã biến mất; nếu đúng thì gỡ tên khỏi danh sách.""")
                .isEqualTo(DUONG_GHI_SO_DO);
    }

    @Test
    @DisplayName("⛔ Chống xanh-trên-tập-rỗng: bộ quét thật sự thấy lớp, ⛔ không chạy qua tập rỗng")
    void boQuetKhongChayQuaTapRong() {
        // Luật 7: một khẳng định chạy qua tập rỗng vẫn xanh trọn vẹn. Nếu ClassFileImporter đổi
        // cách nạp, hoặc tên gói đổi, thì `lopGhiSoDo()` trả rỗng — bài trên xanh mà ⛔ không đo gì.
        assertThat(ProductionClasses.ALL.stream()
                        .filter(c -> c.getPackageName().equals(GOI_APPLICATION))
                        .count())
                .as("⛔ Không nạp được lớp nào ở `%s` — bộ quét hỏng, ⛔ không phải mã sạch", GOI_APPLICATION)
                .isGreaterThan(10L);

        assertThat(ProductionClasses.ALL.stream().anyMatch(c -> c.getName().equals(MAY_CANH_BAO)))
                .as("⛔ ⛔ Không thấy `NguongAlertService` — bài trên khẳng định về một lớp không tồn tại")
                .isTrue();
    }

    /**
     * ⭐ Tự kiểm chứng — luật 1: <i>mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi
     * phạm</i>.
     *
     * <p>⛔ ⛔ Đây là chỗ dự án đã có <b>năm</b> cơ chế "xanh mà chưa từng chạy". Bài dưới ⛔ không
     * làm hỏng mã thật; nó chứng minh hai <b>vị từ</b> của bộ quét thật sự phân biệt được hai trạng
     * thái (luật 9), bằng cách soi một lớp <b>đã biết chắc</b> nằm ở mỗi bên.
     */
    @Nested
    @DisplayName("Bộ canh tự kiểm chứng")
    class TuKiemChung {

        @Test
        @DisplayName("⭐ Vị từ 'có ghi số đo' phân biệt được: TelemetryIngestService CÓ, StationService KHÔNG")
        void viTuGhiSoDoPhanBietDuocHaiTrangThai() {
            assertThat(lopGhiSoDo())
                    .contains("com.songnhue.hydro.application.TelemetryIngestService")
                    .as("⛔ `StationService` chỉ sửa DANH MỤC điểm đo, ⛔ không ghi số đo nào — "
                            + "nó lọt vào đây nghĩa là vị từ quá rộng và bài chính đang canh nhầm tập")
                    .doesNotContain("com.songnhue.hydro.application.StationService");
        }

        @Test
        @DisplayName("⭐ Vị từ 'có gọi máy cảnh báo' phân biệt được: cả ba đường CÓ, StationService KHÔNG")
        void viTuGoiMayCanhBaoPhanBietDuocHaiTrangThai() {
            for (String lop : DUONG_GHI_SO_DO) {
                assertThat(goiMayCanhBao(lop))
                        .as("`%s` phải gọi `NguongAlertService.danhGia`", lop)
                        .isTrue();
            }
            assertThat(goiMayCanhBao("com.songnhue.hydro.application.StationService"))
                    .as("⛔ Vị từ trả TRUE cho một lớp ⛔ KHÔNG gọi máy cảnh báo ⇒ nó luôn TRUE ⇒ "
                            + "bài chính ⛔ không khẳng định gì (luật 9)")
                    .isFalse();
        }
    }
}
