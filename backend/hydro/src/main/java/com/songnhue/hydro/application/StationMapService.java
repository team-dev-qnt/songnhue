package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.hydro.api.HydroMapDtos;
import com.songnhue.hydro.domain.StationDisplayStatus;
import com.songnhue.hydro.infra.StationMapRepository;

/**
 * Lớp GIS "Điểm đo thuỷ văn" — <b>T35.1</b> + danh sách "chưa số hoá vị trí" <b>T35.2</b>.
 *
 * <h2>⭐ Một ảnh chụp, hai nhóm</h2>
 *
 * <p>Điểm đo <b>có</b> toạ độ ra marker; điểm đo <b>chưa</b> có toạ độ ra danh sách chờ số hoá. Cả
 * hai tách từ <b>cùng một</b> lượt đọc, ⛔ không phải hai truy vấn: hai câu riêng sẽ lệch nhau đúng
 * vào lúc ai đó thêm một điều kiện vào một trong hai, và triệu chứng là một điểm đo <i>biến mất
 * khỏi cả hai danh sách</i> — không ai đi tìm một thứ không xuất hiện ở đâu cả.
 *
 * <h2>⛔ Định nghĩa trạng thái ⛔ KHÔNG viết lại</h2>
 *
 * <p>Đi qua đúng {@link StationDisplayStatus#suyRa} mà job rà tín hiệu và ô KPI dashboard cùng
 * dùng. Ba màn hình, một định nghĩa — nếu không, chỉnh {@code hydro.station.signal-loss-frames} sẽ
 * làm ba nơi nói ba con số.
 *
 * <h2>⚠ Hôm nay lớp này vẽ ĐÚNG 0 chấm, và đó là trạng thái đúng</h2>
 *
 * <p>Cả <b>19/19</b> điểm đo seed có {@code latitude}/{@code longitude} NULL — toạ độ thuộc
 * <b>G8</b>, Công ty chưa cung cấp. ⛔ Không suy toạ độ từ công trình liên kết: thượng lưu và hạ lưu
 * của cùng một cống là <b>hai vị trí khác nhau</b>, và một chấm đặt sai chỗ trên bản đồ điều hành
 * còn tệ hơn không có chấm nào.
 *
 * <p>⇒ Giá trị dùng được ngay của lớp này hôm nay nằm ở <b>vế thứ hai</b>: danh sách 19 điểm đo cần
 * toạ độ, để Công ty biết đích xác phải cấp những gì.
 */
@Service
public class StationMapService {

    private final StationMapRepository repository;
    private final HydroSettings settings;

    public StationMapService(StationMapRepository repository, HydroSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public HydroMapDtos.LopDiemDoView lopDiemDo() {
        Duration khung = settings.khungNguon();
        int soKhung = settings.soKhungMatTinHieu();
        // ⚠ MỘT mốc cho cả lượt: gọi `Instant.now()` trong vòng lặp là mỗi điểm đo một mốc, và một
        //   trạm nằm đúng biên sẽ đổi trạng thái giữa chừng danh sách.
        Instant bayGio = Instant.now();

        List<HydroMapDtos.DiemDoMarkerView> marker = new ArrayList<>();
        List<HydroMapDtos.DiemDoChuaSoHoaView> chuaSoHoa = new ArrayList<>();

        for (StationMapRepository.DiemDoBanDoRow r : repository.diemDoBanDo()) {
            StationDisplayStatus trangThai =
                    StationDisplayStatus.suyRa(r.active(), r.mocGanNhat(), bayGio, khung, soKhung);

            // ⚠ `ck_stations_coords_paired` ép hai cột NULL cùng nhau, nên kiểm một cột là đủ —
            //   nhưng kiểm cả hai để lớp này ⛔ không phụ thuộc vào một ràng buộc ở tệp khác.
            if (r.latitude() == null || r.longitude() == null) {
                chuaSoHoa.add(new HydroMapDtos.DiemDoChuaSoHoaView(
                        r.publicId(), r.code(), r.name(), r.positionRole(), r.riverName(), r.chainage()));
                continue;
            }

            marker.add(new HydroMapDtos.DiemDoMarkerView(
                    r.publicId(),
                    r.code(),
                    r.name(),
                    r.positionRole(),
                    r.latitude(),
                    r.longitude(),
                    r.riverName(),
                    r.chainage(),
                    trangThai,
                    // ⭐ Chỉ NGHI_NGO mới đáng đánh dấu. `null` (chưa có bản ghi) đã được nói bằng
                    //   `trangThai = CHUA_CO_DU_LIEU`; nói thêm một lần nữa ở cột này là hai cách
                    //   diễn đạt cho một sự việc, và chúng sẽ lệch nhau.
                    "NGHI_NGO".equals(r.chatLuong()),
                    r.giaTri(),
                    r.donVi(),
                    r.tenChiSo(),
                    r.mocDo(),
                    r.khoaMauCanhBao(),
                    r.tenMucCanhBao()));
        }
        return new HydroMapDtos.LopDiemDoView(marker, chuaSoHoa);
    }
}
