package com.songnhue.hr.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.hr.infra.DanhBaRepository;

/**
 * Danh bạ nội bộ — CN-04.6 (SRS M4.11).
 *
 * <h2>⛔⛔ Lớp này CỐ Ý ⛔ không đi qua {@code ScopeGuard} — và đó là khác biệt lớn nhất với CN-04.7</h2>
 *
 * <p>Hai màn hình, hai luật, và trộn chúng là hỏng theo cả hai chiều:
 *
 * <table border="1">
 *   <caption>Hai đường đọc hồ sơ CBNV</caption>
 *   <tr><th></th><th>Hồ sơ CBNV (CN-04.7)</th><th>Danh bạ (CN-04.6)</th></tr>
 *   <tr><td>Quyền</td><td>{@code hr:employee:view} — 3/12 vai trò</td>
 *       <td>{@code hr:directory:view} — <b>11/12</b> vai trò</td></tr>
 *   <tr><td>Phạm vi</td><td>Cắt theo đơn vị, ngoài phạm vi ⇒ {@code AUTH-3002} + nhật ký</td>
 *       <td>⛔ <b>Toàn Công ty</b></td></tr>
 *   <tr><td>Trường</td><td>27 trường + cờ 🔒</td><td><b>9</b> trường liên hệ công vụ</td></tr>
 * </table>
 *
 * <p>Gán phạm vi cho danh bạ là làm nó vô dụng (⛔ không gọi được sang Xí nghiệp khác); bỏ phạm vi
 * ở hồ sơ là vỡ M4.13. ⇒ Bảo đảm ⛔ không nằm ở *"nhớ gọi ScopeGuard hay ⛔ không"* mà ở chỗ hai
 * đường dùng <b>hai câu truy vấn khác nhau, trả hai kiểu khác nhau</b> —
 * {@link DanhBaRepository} ⛔ không bao giờ trả một {@code Employee}, nên ⛔ không có cách nào một
 * trường 🔒 đi lạc sang đây (luật 12: đặt bảo đảm ở chỗ dữ liệu đi qua).
 *
 * <h2>⛔ ⛔ Không có ảnh đại diện, và nói thẳng ra</h2>
 *
 * <p>Đặc tả vẽ thẻ danh bạ có <b>ảnh</b>. Đo 10/09/2026: {@code employees} ⛔ không có cột ảnh nào,
 * và {@code HoSoThuMuc.ANH} là <i>"ảnh trong hồ sơ nhân sự"</i> — có thể là bản chụp giấy tờ, ⛔
 * không phải ảnh chân dung để công bố. Lấy đại một tệp trong đó đem hiện cho toàn Công ty là một
 * quyết định ⛔ không ai duyệt, và CLAUDE.md cấm lấp chỗ trống bằng dữ liệu ⛔ không có nguồn. ⇒ Thẻ
 * hiện chữ cái đầu; nợ ghi ở T55.4 kèm hai phương án.
 */
@Service
public class DanhBaService {

    /** Trần một trang — danh bạ là màn hình cuộn, ⛔ không phải một lượt xuất dữ liệu. */
    private static final int CO_TRANG_TOI_DA = 60;

    /** Số đồng nghiệp hiện ở màn hình chi tiết. Đủ để nhận ra phòng, ⛔ không thành một trang thứ hai. */
    private static final int SO_DONG_NGHIEP = 20;

    private final DanhBaRepository danhBa;

    public DanhBaService(DanhBaRepository danhBa) {
        this.danhBa = danhBa;
    }

    /** Một trang danh bạ kèm tổng số — tổng số là thứ giao diện cần để nói *"tìm thấy N người"*. */
    @Transactional(readOnly = true)
    public Trang tim(DanhBaLoc loc, int trang, int co) {
        int coThat = Math.clamp(co, 1, CO_TRANG_TOI_DA);
        int trangThat = Math.max(trang, 0);
        return new Trang(danhBa.timTrang(loc, trangThat, coThat), danhBa.dem(loc), trangThat, coThat);
    }

    /**
     * Chi tiết một người trong danh bạ.
     *
     * <p>⚠ Phép tra ở {@link DanhBaRepository#timTheoPublicId} mang <b>đúng</b> vế
     * <i>"còn làm việc"</i> mà danh sách dùng. Thiếu vế ấy thì một người vừa nghỉ việc biến khỏi
     * danh sách nhưng <b>vẫn mở được bằng liên kết cũ</b> — nửa cặp đọc–ghi ở dạng khó thấy nhất,
     * vì màn hình danh sách trông hoàn toàn đúng.
     */
    @Transactional(readOnly = true)
    public ChiTiet chiTiet(UUID publicId) {
        DanhBaMuc muc =
                danhBa.timTheoPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        return new ChiTiet(muc, danhBa.duongDanDonVi(publicId), danhBa.dongNghiepCungDonVi(publicId, SO_DONG_NGHIEP));
    }

    /** @param tong tổng số người khớp bộ lọc — ⛔ không phải số phần tử của {@code muc} */
    public record Trang(List<DanhBaMuc> muc, long tong, int trang, int co) {}

    /**
     * @param duongDanDonVi từ gốc xuống đơn vị của người này — <i>"vị trí trên sơ đồ"</i> mà đặc tả
     *     đòi, dựng từ {@code org_units.path} chứ ⛔ không chờ CN-04.1
     */
    public record ChiTiet(DanhBaMuc muc, List<String> duongDanDonVi, List<DanhBaMuc> dongNghiep) {}
}
