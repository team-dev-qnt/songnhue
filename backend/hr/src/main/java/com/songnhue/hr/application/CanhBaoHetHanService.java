package com.songnhue.hr.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmployeeQualification;
import com.songnhue.hr.infra.EmployeeQualificationRepository;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Cảnh báo hợp đồng và chứng chỉ sắp/đã hết hạn — CN-04.5 / SRS <b>M4.9</b>.
 *
 * <h2>⛔⛔ Đây là VẾ ĐỌC của hai khoá đã mồ côi 28 ngày</h2>
 *
 * <p>{@code hr.contract.expiry-warning-days} và {@code hr.certificate.expiry-warning-days} nằm
 * trong {@code settings} từ <b>13/08/2026</b> với <b>0 nơi đọc</b>. Người vận hành thấy hai ô trên
 * màn hình Cấu hình, sửa chúng, và ⛔ không có gì đổi — luật 15, và là chỗ thứ tám cùng hình dạng
 * trong dự án. Lớp này đóng đúng hai khoá ấy.
 *
 * <h2>⛔ "Sắp hết hạn" phải BAO GỒM "đã hết hạn"</h2>
 *
 * <p>Điều kiện là {@code ngày <= hôm nay + N}, ⛔ không phải khoảng {@code [hôm nay, hôm nay + N]}.
 * Lấy khoảng là để rơi mất đúng những mục <b>nguy hiểm nhất</b> — thứ đã quá hạn — và triệu chứng
 * là một danh sách <i>ngắn dần theo thời gian</i>, trông y hệt *"mọi thứ đang ổn"*. Trạng thái được
 * phân biệt bằng {@link Muc#daHetHan()}: giao diện tô vàng khi sắp, đỏ khi đã.
 *
 * <h2>⚠ "Hôm nay" luôn kèm MÚI GIỜ tường minh</h2>
 *
 * <p>{@code LocalDate.now(DateTimeUtils.ZONE_VN)} — khuôn dùng khắp kho, ⛔ không phải một bean
 * {@code Clock} riêng cho module này (hai khuôn cho cùng một việc là một chỗ lệch chờ ngày xảy ra).
 * Quy tắc 1: một lượt đọc đồng hồ ⛔ không kèm múi giờ cho kết quả đúng trên máy dev ở Việt Nam và
 * <b>sai 7 tiếng</b> trên container UTC — đủ để một hợp đồng hết hạn hôm nay bị xếp vào hôm qua.
 *
 * <p>⭐ Bảo đảm ấy ⛔ không phải một lời dặn: {@code CodingRuleTest.NoAmbientClock} chặn cả
 * {@code LocalDate.now()} lẫn đường vòng {@code LocalDate.now(ZoneId.systemDefault())} ở tầng
 * bytecode (T51.11, vá 10/09/2026).
 *
 * <p>⚠ Bài kiểm vì thế dựng dữ liệu <b>tương đối với hôm nay</b> (hết hạn sau 10 ngày ⇒ phải có;
 * sau 95 ngày ⇒ ⛔ không được có, với ngưỡng 90) chứ ⛔ không ghim một ngày cố định — một ngày ghim
 * sẽ hết đúng vào một hôm nào đó và bài kiểm đỏ vì lý do sai (§11.17).
 */
@Service
public class CanhBaoHetHanService {

    private final EmployeeRepository employees;
    private final EmployeeQualificationRepository qualifications;
    private final HrSettings hrSettings;

    public CanhBaoHetHanService(
            EmployeeRepository employees, EmployeeQualificationRepository qualifications, HrSettings hrSettings) {
        this.employees = employees;
        this.qualifications = qualifications;
        this.hrSettings = hrSettings;
    }

    /**
     * ⚠ Cả hai danh sách đi qua <b>bộ lọc phạm vi đơn vị</b> của {@link Employee}: quản lý Xí nghiệp
     * chỉ thấy hợp đồng và chứng chỉ của đơn vị mình (CN-04.7 / M4.13). Danh sách chứng chỉ lọc theo
     * đúng tập nhân viên ấy chứ ⛔ không quét cả bảng rồi lọc ở Java — quét cả bảng là đọc dữ liệu
     * ngoài phạm vi <b>trước</b> khi cắt, và một lượt đếm hay một dòng log sẽ để lộ nó.
     */
    @Transactional(readOnly = true)
    public KetQua canhBao() {
        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);

        int nguongHd = hrSettings.soNgayBaoTruocHopDong();
        List<Employee> hopDong = employees.hopDongSapHetHan(homNay.plusDays(nguongHd));

        int nguongCc = hrSettings.soNgayBaoTruocChungChi();
        List<Employee> trongPhamVi = employees.findByDeletedAtIsNull();
        Map<Long, Employee> theoId =
                trongPhamVi.stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        List<EmployeeQualification> chungChi = theoId.isEmpty()
                ? List.of()
                : qualifications.sapHetHieuLuc(homNay.plusDays(nguongCc), List.copyOf(theoId.keySet()));

        return new KetQua(
                nguongHd,
                nguongCc,
                hopDong.stream()
                        .map(e -> new Muc(
                                e.getPublicId(),
                                e.getCode(),
                                e.getFullName(),
                                "Hợp đồng lao động",
                                e.getContractExpiresAt(),
                                soNgayCon(homNay, e.getContractExpiresAt())))
                        .toList(),
                chungChi.stream()
                        .map(q -> new Muc(
                                theoId.get(q.getEmployeeId()).getPublicId(),
                                theoId.get(q.getEmployeeId()).getCode(),
                                theoId.get(q.getEmployeeId()).getFullName(),
                                q.getName(),
                                q.getExpiresOn(),
                                soNgayCon(homNay, q.getExpiresOn())))
                        .toList());
    }

    private static long soNgayCon(LocalDate homNay, LocalDate hetHan) {
        return java.time.temporal.ChronoUnit.DAYS.between(homNay, hetHan);
    }

    /**
     * @param nguongNgayHopDong giá trị ĐANG hiệu lực của {@code hr.contract.expiry-warning-days} —
     *     trả ra API để màn hình nói được *"sắp hết hạn trong 30 ngày"* thay vì một con số ghi cứng
     *     ở giao diện. Hai nơi cùng một sự thật thì sẽ lệch (luật 14)
     */
    public record KetQua(int nguongNgayHopDong, int nguongNgayChungChi, List<Muc> hopDong, List<Muc> chungChi) {}

    /**
     * @param soNgayCon âm nghĩa là <b>đã</b> hết hạn — giao diện tô đỏ. ⛔ Đừng kẹp về 0: mất đúng
     *     thông tin *"quá hạn bao lâu rồi"*, thứ quyết định việc nào làm trước
     */
    public record Muc(
            java.util.UUID hoSoPublicId, String maCanBo, String hoTen, String moTa, LocalDate hetHan, long soNgayCon) {

        public boolean daHetHan() {
            return soNgayCon < 0;
        }
    }
}
