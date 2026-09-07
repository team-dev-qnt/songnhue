package com.songnhue.hydro.infra;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.HydroReading;

/**
 * Nạp <b>một</b> dòng {@code hydro_readings} để {@code WorkflowEngine} thao tác — T32.5.
 *
 * <p>⛔ Cố ý chỉ có <b>đúng một</b> phương thức truy vấn: đường đọc danh sách là
 * {@code SuspectReadingRepository} (JDBC, gộp tên điểm đo và loại chỉ số trong một lượt), còn đường
 * ghi số đo là {@code HydroTimeSeriesWriter}.
 *
 * <h2>⚠ Khoảng mù phải nói ra (luật 28)</h2>
 *
 * <p>SQL do Spring Data sinh ra ⛔ <b>không</b> nằm trong một hằng chuỗi nào, nên
 * {@code QualityFilterGuardTest} <b>không nhìn thấy</b> phương thức dưới đây. Nó an toàn vì nó là
 * một lượt <b>tra một dòng theo khoá tự nhiên</b> để đổi trạng thái — ⛔ không phải một truy vấn
 * báo cáo — và nó <b>phải</b> thấy được dòng {@code NGHI_NGO}, vốn là toàn bộ lý do nó tồn tại.
 *
 * <p>⇒ Mọi truy vấn <i>đọc số liệu</i> mới phải viết bằng SQL trong một hằng có tên, ⛔ không thêm
 * {@code findBy…} ở đây: một đường đọc mà bộ canh mù là một đường đọc sẽ quên lọc mà không ai biết.
 */
public interface HydroReadingRepository extends JpaRepository<HydroReading, Long> {

    /**
     * ⭐⭐ Tra theo <b>khoá tự nhiên</b>, ⛔ không theo khoá tự tăng.
     *
     * <p>Hai lý do, và cả hai đều đo được:
     *
     * <ol>
     *   <li><b>Chống IDOR</b> — {@code ApiSurfaceRuleTest} cấm mọi {@code @PathVariable} kiểu số:
     *       khoá tự tăng thì gõ 1, 2, 3 là quét hết bảng. Thay vì xin ngoại lệ, đường duyệt đổi sang
     *       địa chỉ hoá bằng {@code (điểm đo public_id, mã loại chỉ số, mốc đo)} — đúng cùng bộ khoá
     *       mà ô nhập tay đã dùng.
     *       ⛔ Thêm một cột {@code public_id} vào {@code hydro_readings} <b>không</b> giải quyết được:
     *       chỉ mục UNIQUE trên bảng phân mảnh <i>bắt buộc</i> chứa cột phân mảnh, nên nó sẽ là
     *       {@code (public_id, measured_at)} — ⛔ không ép được duy nhất toàn cục, mà vẫn phải trả
     *       16 byte × hàng triệu dòng cho bảng lớn nhất hệ thống.
     *   <li><b>Cắt được partition</b> — {@code measured_at} là khoá phân mảnh, nên câu này chạm đúng
     *       <b>một</b> mảnh. Tra bằng {@code id} thì dò chỉ mục của <i>mọi</i> mảnh (~60 với hạn lưu
     *       5 năm).
     * </ol>
     */
    Optional<HydroReading> findByStationIdAndMeasurementTypeIdAndMeasuredAt(
            Long stationId, Long measurementTypeId, Instant measuredAt);
}
