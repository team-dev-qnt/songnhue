package com.songnhue.core.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tra cứu hồ sơ CBNV cho {@code core} — T51.8, CN-05.1.
 *
 * <h2>Vì sao hợp đồng nằm ở {@code core.spi} chứ ⛔ không ở {@code hr.spi}</h2>
 *
 * <p>{@code hr.spi} <b>đúng về mặt sở hữu</b> — dữ liệu là của {@code hr}, và {@code ModuleBoundaryTest}
 * cho phép {@code core} import {@code hr.spi}. Nhưng {@code core/pom.xml} ⛔ <b>không</b> khai một
 * phụ thuộc Maven nào; mọi module khác phụ thuộc vào nó. Đặt interface ở {@code hr.spi} là thêm
 * {@code core → hr}, tức một <b>chu trình</b> ở tầng build — thứ ArchUnit ⛔ không thấy vì nó ⛔
 * không bao giờ biên dịch được để mà kiểm. ⇒ Đi theo tiền lệ {@code ConstructionLookupPort}: hợp
 * đồng ở {@code core.spi}, cài đặt ở tầng {@code application} của module <b>sở hữu dữ liệu</b>,
 * Spring nối hai đầu lúc dựng context.
 *
 * <h2>⚠ Cố ý rất hẹp — hai phương thức, cả hai chỉ ĐỌC</h2>
 *
 * <p>Đây ⛔ không phải cổng "quản lý nhân sự". ⛔ Không tạo, ⛔ không sửa, ⛔ không xoá, và ⛔ không
 * một trường 🔒 nào ({@link EmployeeRef} nói vì sao). Nới nó là mời {@code core} đi vòng qua MOD-04
 * để đụng vào dữ liệu cá nhân — đúng cảnh báo mà {@link UserDirectoryPort} đã viết cho chiều ngược
 * lại.
 *
 * <p>⛔⛔ Và ⛔ <b>không</b> thêm một phương thức trả trường 🔒 vào đây kể cả khi "chỉ dùng cho màn
 * hình hồ sơ của tôi": lượt tự đọc ấy sống ở {@code hr}, nơi có {@code security_events} ghi từng
 * lượt. Một đường đọc ở {@code core} sẽ ⛔ không ghi dòng nào.
 */
public interface EmployeeDirectoryPort {

    /**
     * Tra một hồ sơ theo {@code publicId} — <b>đi qua {@code ScopeGuard}</b>.
     *
     * <p>Người quản trị đang <i>chọn</i> một hồ sơ, nên phạm vi đơn vị phải áp đúng như mọi đường
     * vào khác của MOD-04. {@code PermissionDeniedException} ({@code AUTH-3002}) đi thẳng ra ngoài,
     * ⛔ không bị nuốt thành {@link Optional#empty()}: <i>"⛔ không tồn tại"</i> và <i>"tồn tại nhưng
     * ngoài phạm vi của anh"</i> là hai câu trả lời khác nhau (luật 9), và gộp chúng lại là cách một
     * tín hiệu an ninh biến thành một dòng <i>"⛔ không tìm thấy"</i> trên biểu mẫu.
     *
     * @return rỗng khi ⛔ không có hồ sơ nào như vậy, hoặc hồ sơ đã xoá mềm
     */
    Optional<EmployeeRef> timTheoPublicId(UUID publicId);

    /**
     * Chiều ngược lại — dựng phản hồi API từ khoá đang lưu ở {@code users.employee_id}.
     *
     * <p>⚠ ⛔ <b>Không</b> lọc phạm vi, có chủ đích: nơi gọi đang hiển thị một liên kết <b>đã tồn
     * tại</b> trên chính màn hình tài khoản mà người dùng vừa mở được. Lọc ở đây sẽ làm ô "Hồ sơ
     * liên kết" <b>trống rỗng</b> thay vì báo ngoài phạm vi — tức người quản trị đọc thành *"tài
     * khoản này chưa liên kết"* rồi liên kết lại sang người khác.
     *
     * @return rỗng khi khoá là {@code null}, ⛔ không trỏ tới hồ sơ nào, hoặc hồ sơ đã xoá mềm
     */
    Optional<EmployeeRef> timTheoId(Long id);

    /**
     * Tra hàng loạt — <b>chống N+1</b> ở màn hình danh sách tài khoản.
     *
     * <p>⛔ Ở đây ⛔ không phải tối ưu sớm: danh sách tài khoản trả <b>mọi</b> tài khoản nội bộ
     * trong một lượt (⛔ không phân trang), nên gọi {@link #timTheoId} trong vòng lặp là một câu
     * truy vấn cho mỗi hàng — và số hàng là số CBNV của Công ty, đúng con số mà chốt C3 sắp đẩy lên.
     *
     * <p>⚠ Cùng hợp đồng {@link #timTheoId}: ⛔ không lọc phạm vi, có lọc xoá mềm. Khoá thiếu trong
     * map nghĩa là ⛔ không tra được — nơi gọi hiểu là "chưa liên kết", ⛔ không được suy ra gì khác.
     */
    Map<Long, EmployeeRef> timTheoIds(Collection<Long> ids);
}
