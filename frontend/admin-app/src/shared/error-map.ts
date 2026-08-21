/**
 * Bản sao danh mục mã lỗi của backend (conventions.md §2.3) — **57 mã**, tính đến hết WS-14.
 *
 * <h3>Vì sao FE cần bản sao, khi API đã trả sẵn câu tiếng Việt</h3>
 *
 * Không phải để hiển thị — {@link messageFor} vẫn **ưu tiên câu do API trả về**, vì đó mới
 * là câu có tham số đã điền ("Giá trị tham số *retention-days* không hợp lệ — yêu cầu: …").
 * Bản sao này tồn tại vì hai lý do khác:
 *
 * 1. **Quyết định hành vi.** Cùng là HTTP 403, `AUTH-3001` phải đưa sang trang "không có
 *    quyền" còn `AUTH-0005` (CSRF lệch) phải làm mới vé rồi thử lại — nhìn status code
 *    không phân biệt được. `handling` bên dưới là thứ `apiClient` đọc để chọn đường.
 * 2. **Khi chưa tới được máy chủ.** Mất mạng, timeout, nginx trả lỗi — lúc đó không có
 *    envelope nào để lấy câu chữ, mà vẫn phải nói được điều gì đó đúng.
 *
 * ⚠ **Đồng bộ có bài kiểm canh**: `error-map.test.ts` đọc thẳng
 * `backend/core/src/main/resources/error-messages.properties` và làm đỏ khi hai bên lệch
 * nhau — thêm mã ở BE mà quên ở đây là CI bắt được, không cần ai nhớ.
 */

/**
 * Cách FE xử lý một mã lỗi. Đây là phần **không suy ra được từ HTTP status**.
 *
 * - `toast` — hiện thông báo nổi, người dùng ở nguyên màn hình. Mặc định.
 * - `form` — lỗi theo trường, tô đỏ đúng ô nhập từ `error.details`.
 * - `reauth` — phiên không dùng được nữa: xoá trạng thái đăng nhập, về trang đăng nhập.
 * - `changePassword` — bắt buộc đổi mật khẩu trước khi làm gì khác.
 * - `maintenance` — hệ thống đang khôi phục dữ liệu; chặn thao tác ghi ở toàn ứng dụng.
 * - `forbidden` — thiếu quyền / ngoài phạm vi đơn vị: đưa sang trang 403 kèm traceId.
 * - `retryCsrf` — vé CSRF lệch, lấy vé mới rồi gửi lại **đúng một lần**.
 * - `caller` — màn hình gọi tự xử lý (đăng nhập sai, mã 2FA sai…): không bắn toast toàn cục,
 *   vì thông báo phải nằm ngay cạnh ô nhập chứ không phải ở góc màn hình.
 */
export type ErrorHandling =
  | 'toast'
  | 'form'
  | 'reauth'
  | 'changePassword'
  | 'maintenance'
  | 'forbidden'
  | 'retryCsrf'
  | 'caller';

export interface ErrorEntry {
  /** Câu dự phòng khi không lấy được message từ API. Giữ khớp với BE. */
  readonly message: string;
  readonly handling: ErrorHandling;
  /** Mức hiển thị của toast. `warning` cho lỗi do thao tác, `error` cho lỗi hệ thống. */
  readonly severity: 'info' | 'warning' | 'error';
}

export const ERROR_CATALOG = {
  // --- Hệ thống / dùng chung -------------------------------------------------
  'SYS-0001': {
    message: 'Lỗi hệ thống, vui lòng thử lại',
    handling: 'toast',
    severity: 'error',
  },
  'SYS-0002': {
    message: 'Thao tác quá nhanh, vui lòng thử lại sau',
    handling: 'toast',
    severity: 'warning',
  },
  'SYS-0003': { message: 'Dữ liệu gửi lên không hợp lệ', handling: 'form', severity: 'warning' },
  'SYS-0004': { message: 'Không tìm thấy dữ liệu', handling: 'toast', severity: 'warning' },
  'SYS-0005': {
    message: 'Dữ liệu vừa được người khác thay đổi, vui lòng tải lại rồi thao tác tiếp',
    handling: 'toast',
    severity: 'warning',
  },
  'SYS-0006': {
    message: 'Hệ thống bên ngoài không phản hồi, vui lòng thử lại sau',
    handling: 'toast',
    severity: 'error',
  },
  'SYS-0007': {
    message: 'Hệ thống đang bảo trì, tạm thời không thực hiện được thao tác thay đổi dữ liệu',
    handling: 'maintenance',
    severity: 'warning',
  },
  'SYS-0008': {
    message: 'Thao tác không hợp lệ với trạng thái hiện tại của dữ liệu',
    handling: 'toast',
    severity: 'warning',
  },
  'SYS-0009': {
    message: 'Tệp tin chưa sẵn sàng để tải xuống',
    handling: 'toast',
    severity: 'warning',
  },
  'SYS-0010': {
    message: 'Bản ghi đã hết dung lượng tệp đính kèm — xoá bớt tệp cũ trước khi tải thêm',
    handling: 'toast',
    severity: 'warning',
  },

  // --- Xác thực & phân quyền -------------------------------------------------
  'AUTH-0001': {
    message: 'Sai tên đăng nhập hoặc mật khẩu',
    handling: 'caller',
    severity: 'warning',
  },
  'AUTH-0002': {
    message: 'Phiên đăng nhập hết hạn, vui lòng đăng nhập lại',
    handling: 'reauth',
    severity: 'warning',
  },
  'AUTH-0003': {
    message: 'Tài khoản tạm khóa do đăng nhập sai nhiều lần',
    handling: 'caller',
    severity: 'error',
  },
  'AUTH-0004': {
    message: 'Mã xác thực hai bước không đúng hoặc đã hết hiệu lực',
    handling: 'caller',
    severity: 'warning',
  },
  'AUTH-0005': {
    message: 'Yêu cầu không hợp lệ, vui lòng tải lại trang rồi thao tác lại',
    handling: 'retryCsrf',
    severity: 'warning',
  },
  'AUTH-0006': {
    message: 'Mật khẩu chưa đạt yêu cầu an toàn',
    handling: 'caller',
    severity: 'warning',
  },
  'AUTH-0007': {
    message: 'Vui lòng đổi mật khẩu trước khi sử dụng hệ thống',
    handling: 'changePassword',
    severity: 'warning',
  },
  'AUTH-0008': {
    message: 'Phiên đăng nhập đã bị thu hồi vì lý do an toàn, vui lòng đăng nhập lại',
    handling: 'reauth',
    severity: 'error',
  },
  'AUTH-3001': {
    message: 'Không có quyền thực hiện thao tác này',
    handling: 'forbidden',
    severity: 'error',
  },
  'AUTH-3002': {
    message: 'Dữ liệu không thuộc phạm vi đơn vị của bạn',
    handling: 'forbidden',
    severity: 'error',
  },

  // --- MOD-01 Cổng thông tin điện tử -----------------------------------------
  'CMS-2001': { message: 'Slug đã tồn tại', handling: 'form', severity: 'warning' },
  'CMS-2002': {
    message: 'Chưa liên kết mã số hệ thống văn bản điều hành',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2003': {
    message: 'Danh mục còn bài viết — chuyển bài sang danh mục khác trước khi xoá',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2004': {
    message: 'Danh mục còn danh mục con — xoá hoặc chuyển các danh mục con trước',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2005': {
    message: 'Cây danh mục chỉ được sâu tối đa 3 cấp',
    handling: 'form',
    severity: 'warning',
  },
  'CMS-2006': {
    message: 'Bài viết phải thuộc ít nhất một danh mục',
    handling: 'form',
    severity: 'warning',
  },
  'CMS-2007': {
    message: 'Bài đang chờ duyệt nên không sửa được',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2008': {
    message: 'Thư mục còn tệp bên trong — chuyển hoặc xoá các tệp trước',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2009': {
    message: 'Tệp đang được bài viết sử dụng',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2010': {
    message: 'Menu chỉ được sâu tối đa 3 cấp',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2011': {
    message: 'Mục menu còn mục con — xoá các mục con trước',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2012': {
    message: 'Đích của mục menu không tồn tại hoặc đã bị xoá',
    handling: 'form',
    severity: 'warning',
  },
  'CMS-2013': {
    message: 'Mục con phải nằm cùng menu với mục cha',
    handling: 'toast',
    severity: 'warning',
  },
  'CMS-2014': {
    message: 'Ngày kết thúc hiển thị phải sau ngày bắt đầu',
    handling: 'form',
    severity: 'warning',
  },
  'CMS-5001': {
    message: 'Không đăng nhập được sang hệ thống văn bản điều hành',
    handling: 'toast',
    severity: 'error',
  },

  // --- MOD-02 Vận hành công trình --------------------------------------------
  'OPS-2001': {
    message: 'Ngày hoàn thành phải lớn hơn hoặc bằng ngày bắt đầu',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2002': {
    message: 'Công trình đã bị xóa hoặc thanh lý — không ghi nhận được công việc mới',
    handling: 'toast',
    severity: 'warning',
  },
  'OPS-2003': {
    message:
      'Mức độ chỉ dùng cho bản ghi "Khắc phục sự cố" — loại này bắt buộc có, loại khác phải để trống',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2004': {
    message: 'Không chuyển được sang "Đã xử lý" khi chưa có ngày hoàn thành',
    handling: 'toast',
    severity: 'warning',
  },
  'OPS-2005': {
    message: 'Mã tình hình vận hành đã tồn tại',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2006': {
    message: 'Mã tình hình vận hành này yêu cầu nhập giá trị kèm theo',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2007': {
    message: 'Mã tình hình vận hành đã được sử dụng — chỉ được ẩn, không được xóa',
    handling: 'toast',
    severity: 'warning',
  },
  'OPS-2008': {
    message: 'Mã công trình đã tồn tại — mã phải là duy nhất trong toàn hệ thống',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2009': {
    message: 'Thông số kỹ thuật không thuộc loại công trình đang lập hồ sơ',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2010': {
    message: 'Toạ độ phải nhập đủ cả vĩ độ và kinh độ',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2011': {
    message: 'Lý trình phải theo định dạng K<km>+<m>, ví dụ K0+390',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2012': {
    message: 'Cụm công trình còn công trình bên trong — chuyển hết đi rồi mới xoá được',
    handling: 'toast',
    severity: 'warning',
  },
  'OPS-2013': {
    message: 'Cấp quản lý "Cụm" bắt buộc phải chọn cụm công trình',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2014': {
    message: 'Mã cụm công trình đã tồn tại',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-2015': {
    message: 'Không đọc được tệp nhập, hoặc tệp thiếu cột bắt buộc',
    handling: 'toast',
    severity: 'error',
  },
  'OPS-2016': {
    message: 'Tệp nhập còn dòng lỗi — sửa hết lỗi rồi nhập lại, không dòng nào được ghi',
    handling: 'toast',
    severity: 'warning',
  },
  'OPS-2017': {
    message: 'Đơn vị thực hiện: chọn đơn vị nội bộ HOẶC nhập tên nhà thầu ngoài, đúng một trong hai',
    handling: 'form',
    severity: 'warning',
  },
  'OPS-3001': {
    message: 'Không được sửa trực tiếp trạng thái công trình — trạng thái được tính tự động',
    handling: 'toast',
    severity: 'error',
  },

  // --- MOD-03 Thủy văn --------------------------------------------------------
  'HYD-1001': {
    message: 'Điểm đo chưa ánh xạ nguồn API bên thứ 3',
    handling: 'toast',
    severity: 'warning',
  },
  'HYD-2001': {
    message: 'Giá trị đo ngoài khoảng vật lý cho phép',
    handling: 'form',
    severity: 'warning',
  },
  'HYD-2002': {
    message: 'Bản ghi đang ở trạng thái Nghi ngờ — cần duyệt trước khi sử dụng',
    handling: 'toast',
    severity: 'warning',
  },
  'HYD-2003': {
    message: 'Điểm đo chưa cấu hình ngưỡng cảnh báo',
    handling: 'toast',
    severity: 'warning',
  },
  'HYD-2004': {
    message: 'Điểm đo đang mất tín hiệu — không dùng giá trị cũ để đánh giá ngưỡng',
    handling: 'toast',
    severity: 'warning',
  },

  // --- MOD-04 Nhân sự ---------------------------------------------------------
  'HR-2001': {
    message: 'Số ngày đăng ký vượt số phép còn lại',
    handling: 'form',
    severity: 'warning',
  },

  // --- MOD-05 Quản trị --------------------------------------------------------
  'ADM-2001': {
    message: 'Kết xuất lưu trữ nhật ký thất bại — không xóa bản ghi nào',
    handling: 'toast',
    severity: 'error',
  },
  'ADM-2002': { message: 'Mã đơn vị đã tồn tại', handling: 'form', severity: 'warning' },
  'ADM-2003': {
    message: 'Không chuyển được đơn vị vào chính nó hoặc đơn vị cấp dưới của nó',
    handling: 'toast',
    severity: 'warning',
  },
  'ADM-2004': {
    message: 'Đơn vị còn đơn vị cấp dưới hoặc còn người dùng — không xóa được',
    handling: 'toast',
    severity: 'warning',
  },
  'ADM-2005': {
    message: 'Cây tổ chức vượt quá số cấp tối đa cho phép',
    handling: 'toast',
    severity: 'warning',
  },
  'ADM-2006': { message: 'Giá trị tham số không hợp lệ', handling: 'form', severity: 'warning' },
  'ADM-2007': {
    message: 'Tham số này không sửa được qua giao diện',
    handling: 'toast',
    severity: 'warning',
  },
  'ADM-2008': {
    message: 'Chưa cấu hình được sao lưu — kiểm tra thư mục lưu và tài khoản đọc CSDL',
    handling: 'toast',
    severity: 'error',
  },
  'ADM-2009': {
    message: 'Đang có một lượt sao lưu chạy — chờ lượt đó xong rồi thử lại',
    handling: 'toast',
    severity: 'warning',
  },
  'ADM-2010': {
    message: 'Khôi phục qua giao diện chưa được bật trên môi trường này',
    handling: 'toast',
    severity: 'warning',
  },
  'ADM-2011': {
    message: 'Chuỗi xác nhận không đúng — nhập lại theo hướng dẫn trên màn hình',
    handling: 'caller',
    severity: 'warning',
  },
  'ADM-2012': {
    message: 'Bản sao lưu này không dùng được: thiếu tệp hoặc mã kiểm tra không khớp',
    handling: 'toast',
    severity: 'error',
  },
  'ADM-2013': {
    message: 'Khôi phục thất bại — CSDL có thể đang dở dang, liên hệ quản trị hệ thống',
    handling: 'toast',
    severity: 'error',
  },
} as const satisfies Record<string, ErrorEntry>;

export type ErrorCode = keyof typeof ERROR_CATALOG;

/** Dùng khi lỗi chưa tới được máy chủ (mất mạng, timeout) — không có mã nào để tra. */
export const NETWORK_ERROR: ErrorEntry = {
  message: 'Không kết nối được máy chủ, kiểm tra đường truyền rồi thử lại',
  handling: 'toast',
  severity: 'error',
};

/** Mã lạ (BE mới hơn FE) vẫn phải hiện được gì đó — và phải hiện câu của BE, xem `messageFor`. */
const UNKNOWN_ERROR: ErrorEntry = {
  message: 'Thao tác không thành công',
  handling: 'toast',
  severity: 'error',
};

export function entryFor(code: string | null | undefined): ErrorEntry {
  if (!code) {
    return UNKNOWN_ERROR;
  }
  return ERROR_CATALOG[code as ErrorCode] ?? UNKNOWN_ERROR;
}

/**
 * Câu hiển thị cho người dùng.
 *
 * **Ưu tiên `apiMessage`** — câu của backend đã điền tham số ({0}, {1}) và luôn mới hơn bản
 * sao ở đây. Bảng trên chỉ đỡ khi backend không nói được câu nào.
 */
export function messageFor(code: string | null | undefined, apiMessage?: string | null): string {
  const trimmed = apiMessage?.trim();
  if (trimmed) {
    return trimmed;
  }
  return entryFor(code).message;
}
