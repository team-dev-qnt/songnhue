/**
 * Ảnh thay thế khi một bài viết chưa có ảnh đại diện.
 *
 * <h2>Vì sao có tệp này thay vì viết thẳng chuỗi ở nơi gọi</h2>
 *
 * Bốn nơi hiển thị thẻ tin đều cần cùng một ảnh. Viết chuỗi ở cả bốn là bốn chỗ phải sửa khi
 * Công ty đổi ảnh, và lần thứ năm sẽ lệch. Đặt ở `lib/` để **đếm được** — cùng lý lẽ với
 * `homeDataColumns.ts`: một hằng số nằm riêng thì bài kiểm khẳng định được về nó.
 *
 * <h2>⛔ Đây KHÔNG phải dữ liệu bịa</h2>
 *
 * `noFabricatedContent.test.ts` cấm hằng số mang **dữ liệu nghiệp vụ** (tiêu đề bài, mực nước,
 * số điện thoại) vì một mảng rỗng khi ấy cho ra một trang chủ đầy (§10.54). Ảnh mặc định thì
 * ngược lại: nó **nói ra rằng chưa có ảnh**, không giả vờ có. Nó không thêm một dòng dữ liệu
 * nào, không đổi số lượng bài, và không thể bị đọc nhầm thành một phép đo.
 *
 * <h2>Vì sao là logo chứ không phải một ảnh minh hoạ</h2>
 *
 * Một ảnh phong cảnh dùng chung cho mọi bài chưa có ảnh **trông như ảnh của bài đó** — đúng
 * kiểu nhầm mà §10.54 đã trả giá. Dấu hiệu nhận diện của Công ty thì không ai đọc thành nội
 * dung bài viết.
 *
 * ⚠ Vì là logo, nơi gọi phải để ảnh **vừa trọn khung** (`object-contain`) chứ không phủ khung —
 * `PortalImage` tự làm việc đó khi rơi về ảnh mặc định, xem `phuKhung` ở đó.
 */
export const ANH_BAI_VIET_MAC_DINH = '/thumbnail.png';
