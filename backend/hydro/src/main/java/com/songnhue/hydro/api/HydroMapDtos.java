package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.hydro.domain.StationDisplayStatus;

/** DTO của lớp GIS "Điểm đo thuỷ văn" — T35.1 · T35.2. */
public final class HydroMapDtos {

    private HydroMapDtos() {}

    /**
     * ⭐ Hai danh sách trong <b>một</b> phản hồi, cố ý.
     *
     * <p>Giao diện cần cả hai cùng lúc: chấm trên bản đồ, và câu <i>"còn N điểm đo chưa có toạ
     * độ"</i> ngay cạnh nó. Tách làm hai endpoint là để hai con số đến từ hai thời điểm — và ở
     * đúng màn hình này, "19 điểm đo, 0 chấm" là thông tin quan trọng nhất mà nó phải nói ra.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LopDiemDoView(List<DiemDoMarkerView> diemDo, List<DiemDoChuaSoHoaView> chuaSoHoaViTri) {}

    /**
     * Một chấm trên bản đồ.
     *
     * @param trangThai quyết định MÀU chấm — bốn giá trị, và {@code CHUA_CO_DU_LIEU} ⛔ khác hẳn
     *     {@code MAT_TIN_HIEU}
     * @param nghiNgo bản ghi gần nhất mang chất lượng {@code NGHI_NGO} ⇒ giao diện vẽ <b>viền nét
     *     đứt</b>. ⛔ Không đổi màu chấm: màu đã mang một ý nghĩa khác (trạng thái tín hiệu), và một
     *     kênh thị giác chở hai thông tin thì người đọc ⛔ không tách ra được.
     * @param giaTri giá trị <b>HỢP LỆ</b> gần nhất; {@code null} khi chưa có số nào hợp lệ
     * @param khoaMauCanhBao khoá màu của mức cảnh báo <b>nặng nhất đang mở</b>; {@code null} khi
     *     điểm đo không có cảnh báo nào. Giao diện tra sang bảng màu chung của {@code design-tokens}
     *     — ⛔ không có bảng ánh xạ thứ hai ở FE (T35.14)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DiemDoMarkerView(
            UUID publicId,
            String code,
            String name,
            String positionRole,
            // ⚠ BigDecimal ra dây dưới dạng CHUỖI — quy tắc 2 + §10.32: `21,023456` từng bị một lượt
            //   "bỏ hết dấu chấm" biến thành `21023456`, và CHECK chỉ bắt được khi vượt biên.
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal latitude,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal longitude,
            String riverName,
            String chainage,
            StationDisplayStatus trangThai,
            boolean nghiNgo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri,
            String donVi,
            String tenChiSo,
            Instant mocDo,
            String khoaMauCanhBao,
            String tenMucCanhBao) {}

    /**
     * Điểm đo <b>chưa số hoá vị trí</b> — T35.2.
     *
     * <p>⛔ Cố ý ⛔ không mang giá trị đo: đây là một <b>danh sách việc phải làm</b> (cấp toạ độ,
     * mục G8), ⛔ không phải một bảng số liệu thứ hai. Thêm cột số vào đây là mời người đọc dùng nó
     * thay cho bảng thật, rồi hai bảng sẽ lệch nhau.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DiemDoChuaSoHoaView(
            UUID publicId, String code, String name, String positionRole, String riverName, String chainage) {}
}
