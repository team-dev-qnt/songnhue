import type { FormInstance } from 'antd';

import type { ApiClientError } from './apiClient';

/**
 * Đặt lỗi theo trường lên biểu mẫu — và **nói ra được là đã đặt được hay chưa**.
 *
 * <h2>⚠⚠ Sự cố 01/09/2026 mà tệp này ra đời để đóng lại</h2>
 *
 * QuanTran báo: *"lúc tạo tài khoản, error 422 trong network F12 không hiển thị lên màn hình"*.
 * Khuôn mã ở **cả sáu** màn hình lúc ấy là:
 *
 * ```ts
 * if (caught instanceof ApiClientError && caught.details.length > 0) {
 *   form.setFields(caught.fieldErrors());   // ← AntD BỎ QUA tên trường lạ, không báo gì
 *   return;                                  // ← nên toast bên dưới không bao giờ chạy
 * }
 * message.error(caught.message);
 * ```
 *
 * Backend trả `details[].field = "newPassword"`, còn biểu mẫu *Thêm tài khoản* khai trường là
 * `temporaryPassword`. `Form.setFields` với một tên không tồn tại **không phải lỗi** với AntD —
 * nó lặng lẽ không làm gì. Và vì nhánh ấy `return`, toast cũng không chạy. Kết quả: HTTP 422
 * mang lý do đầy đủ, màn hình **trắng trơn**.
 *
 * <h2>Vì sao bản vá nằm ở ĐÂY chứ không ở màn hình đã hỏng</h2>
 *
 * Sửa `UsersPage.tsx` thì đúng một màn hình hết hỏng, và **năm màn hình còn lại giữ nguyên
 * cùng cái bẫy** — chỉ chờ một tên trường lệch nữa. Đây là quy tắc 12 nguyên văn: *khi một bảo
 * đảm phải đúng ở nhiều đường vào, đặt nó ở chỗ dữ liệu đi qua*. Cùng bài học §10.40, nơi lỗi
 * 204 được vá ở `apiClient` thay vì ở 24 màn hình: *"sửa ở màn hình thì 23 màn hình còn lại vẫn
 * hỏng trong im lặng"*.
 *
 * <h2>Bảo đảm hàm này cho</h2>
 *
 * Trả `false` khi **không trường nào trên biểu mẫu nhận được lỗi** — nơi gọi bắt buộc phải hiện
 * thông báo. Trạng thái "không hiện gì cả" vì thế cần một nơi gọi **cố tình** bỏ qua giá trị
 * trả về, chứ không còn xảy ra được vì quên.
 *
 * <p>⚠ Danh sách trường có thật lấy từ `form.getFieldsError()` — AntD trả về đúng các trường
 * **đã đăng ký** (`<Form.Item name=…>` đang gắn trên cây). Đó là câu trả lời thật cho câu hỏi
 * *"đặt lỗi vào đây thì có ai nhìn thấy không"*, khác hẳn `getFieldsValue()` vốn chỉ trả những
 * trường đang có giá trị.
 */
export function datLoiTheoTruong<T extends object>(
  form: FormInstance<T>,
  loi: ApiClientError,
): boolean {
  const coTren = new Set(
    form.getFieldsError().map((o) => (Array.isArray(o.name) ? o.name.join('.') : String(o.name))),
  );

  const nhanDuoc = loi
    .fieldErrors()
    .map((f) => ({ ...f, errors: [moTaChiTiet(loi, f.name)] }))
    .filter((f) => coTren.has(f.name));

  if (nhanDuoc.length === 0) return false;

  // ⚠ Ép kiểu ở đây là CÓ CHỦ ĐÍCH và không tránh được: `FieldData<T>['name']` là một đường dẫn
  //   tên đã gõ kiểu theo `T`, còn tên trường ở đây đến từ **dây** (`error.details[].field`) nên
  //   không thể biết lúc biên dịch. Phép lọc `coTren.has(...)` ngay phía trên chính là phần thay
  //   thế cho bảo đảm mà trình biên dịch không cấp được — nó kiểm ở LÚC CHẠY rằng tên ấy có thật
  //   trên biểu mẫu. Đó là toàn bộ lý do hàm này tồn tại.
  form.setFields(nhanDuoc as Parameters<FormInstance<T>['setFields']>[0]);
  return true;
}

/**
 * Câu tiếng Việt cho MỘT trường — gộp mọi `rule` mà backend gửi kèm cho trường ấy.
 *
 * <h2>Vì sao không dùng thẳng `loi.message`</h2>
 *
 * `ApiClientError.fieldErrors()` gán **cùng một** `loi.message` cho mọi chi tiết. Với `AUTH-0006`
 * câu ấy là *"Mật khẩu chưa đạt yêu cầu an toàn"* — đúng, và vô dụng với người đang phải sửa
 * mật khẩu: nó không nói **yêu cầu là gì**. Thứ dùng được nằm ở `details[].rule`
 * (`MIN_LENGTH_12`, `REQUIRE_LETTER_AND_DIGIT`, `MUST_NOT_CONTAIN_USERNAME`) và trước lượt này
 * **không dòng mã nào đọc tới nó** — quy tắc 15: một trường dữ liệu bày ra mà không ai đọc.
 *
 * <p>⚠ `MIN_LENGTH_<n>` mang con số **đã giải** từ `settings`, nên câu hiển thị luôn khớp tham số
 * Admin đang đặt. Ghi cứng "ít nhất 10 ký tự" ở đây là dựng lại đúng §10.69 — một tham số cấu
 * hình *nói dối*.
 */
function moTaChiTiet(loi: ApiClientError, tenTruong: string): string {
  const cau = loi.details
    .filter((d) => d.field === tenTruong)
    .map((d) => moTaLuat(d.rule))
    .filter((c): c is string => c !== null);

  return cau.length > 0 ? cau.join(' · ') : loi.message;
}

/** `null` = luật chưa có bản dịch ⇒ nơi gọi rơi về câu chung của mã lỗi, không bịa chữ. */
export function moTaLuat(rule: string | null | undefined): string | null {
  if (!rule) return null;

  const doDai = /^MIN_LENGTH_(\d+)$/.exec(rule);
  if (doDai) return `Cần ít nhất ${doDai[1]} ký tự`;

  switch (rule) {
    case 'REQUIRE_LETTER_AND_DIGIT':
      return 'Phải có cả chữ và số';
    case 'MUST_NOT_CONTAIN_USERNAME':
      return 'Không được chứa tên đăng nhập';
    // Mã của Bean Validation — backend gửi nguyên tên annotation.
    case 'NotBlank':
    case 'NotNull':
    case 'NotEmpty':
      return 'Bắt buộc nhập';
    case 'Email':
      return 'Email không hợp lệ';
    case 'Size':
      return 'Độ dài không hợp lệ';
    default:
      return null;
  }
}
