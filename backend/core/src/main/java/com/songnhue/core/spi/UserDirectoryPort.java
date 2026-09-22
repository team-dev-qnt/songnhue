package com.songnhue.core.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tra cứu tài khoản người dùng cho module nghiệp vụ.
 *
 * <p>Sinh ra vì một nhu cầu lặp lại ở mọi module: bảng nghiệp vụ giữ khoá ngoại tới {@code users}
 * (tác giả bài viết, người thực hiện bảo trì, người phụ trách), mà API thì chỉ nhận và trả
 * {@code publicId}. Ai đó phải dịch giữa hai thứ, và chỗ đó không thể là repository của Core —
 * module khác không được import.
 *
 * <p>⚠ <b>Cố ý rất hẹp.</b> Đây không phải cổng "quản lý người dùng": không tạo, không sửa, không
 * đọc thông tin cá nhân. Mở rộng nó là mời module nghiệp vụ đi vòng qua MOD-05 để đụng vào hồ sơ
 * người dùng.
 */
public interface UserDirectoryPort {

    /**
     * Đổi {@code publicId} thành khoá nội bộ để lưu khoá ngoại.
     *
     * @return rỗng khi không có tài khoản nào như vậy, hoặc tài khoản đã xoá mềm
     */
    Optional<Long> internalIdOf(UUID publicId);

    /** Chiều ngược lại — dựng phản hồi API từ khoá ngoại đang lưu. */
    Optional<UUID> publicIdOf(Long internalId);

    /** Tải hàng loạt ID công khai từ ID nội bộ — chống N+1 query. */
    Map<Long, UUID> publicIdsOf(Collection<Long> internalIds);

    /**
     * Một tài khoản kèm <b>họ tên</b> — T84.1.
     *
     * <h2>Vì sao ba hàm dưới đây ⛔ phá lời dặn "cố ý rất hẹp" ở trên</h2>
     *
     * <p>Lời dặn ấy cấm mở cổng này thành <i>"quản lý người dùng"</i> — tạo, sửa, đọc hồ sơ. Ba hàm
     * này trả về một {@link UserRef}, và {@code UserRef} chỉ mang những trường <b>đã hiện công khai ở
     * nơi khác</b>: {@code full_name} là cột {@code NOT NULL} có trên mọi màn hình quản trị và trên
     * danh bạ nội bộ, ⛔ phải trường 🔒. Ranh giới đúng là ranh giới {@link EmployeeRef} đã vạch cho
     * module {@code hr} — nơi dữ liệu còn nhạy cảm hơn hẳn.
     *
     * <p>Thứ buộc phải có chúng: đặc tả ĐÃ KÝ đòi in <b>tên tác giả</b> ra cổng công khai và cho
     * <b>lọc danh sách bài theo tác giả</b> ở admin. ⛔ Có đường này thì nửa ĐỌC của cặp đọc–ghi ⛔
     * bao giờ dựng được, vì quy tắc 6 cấm {@code content} import repository của {@code core}.
     *
     * @return rỗng khi ⛔ có tài khoản nào như vậy, hoặc tài khoản đã xoá mềm
     */
    Optional<UserRef> timTheoId(Long internalId);

    /**
     * Tra hàng loạt — <b>chống N+1</b> ở danh sách bài viết.
     *
     * <p>⚠ Khoá vắng mặt trong map nghĩa là <i>⛔ tra được</i> (⛔ tồn tại hoặc đã xoá mềm), ⛔ phải
     * <i>tên rỗng</i>. Nơi gọi phải phân biệt hai trạng thái ấy — một bài viết của tài khoản đã xoá
     * vẫn là một bài viết hợp lệ, nó chỉ ⛔ còn tên để in.
     */
    Map<Long, UserRef> timTheoIds(Collection<Long> internalIds);

    /**
     * Ai được phép <b>đứng tên</b> một bản ghi — tài khoản đang hoạt động giữ mã quyền ấy.
     *
     * <p>⚠ Đo bằng {@code findActiveIdsByPermission} — <b>cùng một câu</b> mà {@code RecipientResolver}
     * và {@link #dangHoatDongVaCoQuyen} dùng. Cố ý: <i>"ai đang có quyền này"</i> phải là MỘT câu trả
     * lời cho cả hệ (luật 14). Dùng một câu khác ở đây là làm lời khẳng định ấy thành sai.
     *
     * <p>⛔ Đặt tên riêng chứ ⛔ nạp chồng {@link #dangHoatDongVaCoQuyen}: hai phương thức cùng tên
     * khác kiểu trả về là chỗ lượt rà sau đọc nhầm.
     *
     * @return sắp theo họ tên — thứ tự ổn định để ô chọn ⛔ nhảy giữa hai lượt tải
     */
    List<UserRef> danhSachTheoQuyen(String maQuyen);

    /**
     * Tài khoản này <b>đang hoạt động</b> và đang giữ mã quyền ấy ⛔ — một câu hỏi <b>yes/no</b>.
     *
     * <h2>Vì sao ⛔ phá lời dặn "cố ý rất hẹp" ở trên</h2>
     *
     * <p>Lời dặn ấy cấm mở cổng này thành <i>"quản lý người dùng"</i> — đọc hồ sơ, sửa, tạo. Hàm
     * này ⛔ trả về gì của người ấy: nó trả một {@code boolean} cho một câu hỏi <b>phân quyền</b>
     * mà module nghiệp vụ buộc phải hỏi được.
     *
     * <p>Chỗ gọi thật là {@code hr.application.UyQuyenDuyetPhepService}: chốt B3 cho phép trưởng
     * đơn vị <b>giao</b> thẩm quyền duyệt, và QuanTran chốt 20/09/2026 rằng người nhận phải
     * <b>sẵn có</b> {@code hr:leave:approve}. ⛔ Hỏi được câu ấy thì biểu mẫu uỷ quyền trở thành
     * một <b>đường cấp quyền ẩn</b> nằm ngoài màn hình Vai trò &amp; phân quyền.
     */
    boolean dangHoatDongVaCoQuyen(UUID publicId, String maQuyen);

    /**
     * Đơn vị của một tài khoản — khoá nội bộ, để so với cây đơn vị.
     *
     * <p>Đây là <b>khoá phạm vi</b> chứ ⛔ phải thông tin cá nhân: mọi module nghiệp vụ đã cầm
     * {@code org_unit_id} trên chính bảng của mình, và {@code AuthenticatedUser} cũng mang nó. Thứ
     * còn thiếu là tra nó cho một tài khoản <b>khác</b> người đang đăng nhập — đúng câu mà chốt B3
     * hỏi: <i>"người được uỷ quyền cùng đơn vị hoặc cấp trên"</i>.
     *
     * @return rỗng khi ⛔ có tài khoản nào như vậy, hoặc tài khoản đã xoá mềm
     */
    Optional<Long> orgUnitIdCua(UUID publicId);
}
