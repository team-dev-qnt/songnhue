import { describe, expect, it } from 'vitest';

import { type AllowedAction } from '@/components/business/ApprovalActions';
import { type AllowedActionView } from '@/shared/api-types';

/**
 * Hai kiểu mô tả **cùng một payload** phải khớp nhau tuyệt đối.
 *
 * <h2>Lỗi đang canh</h2>
 *
 * `AllowedActionView` (hình dạng dây) và `AllowedAction` (hợp đồng prop của `ApprovalActions`)
 * là hai khai báo của cùng một thứ, đặt ở hai tệp. Chúng đã lệch nhau một lần và hậu quả không
 * nhỏ: kiểu phía component mang thêm `primary` / `danger` / `requiresReason` mà backend không
 * gửi, nên `requiresReason` luôn `undefined` và **hộp thoại nhập lý do không bao giờ mở**. Người
 * duyệt bấm "Yêu cầu chỉnh sửa" thì backend đòi lý do bằng `SYS-0003`, còn màn hình không có ô
 * nào để nhập — thao tác trả bài về sửa hỏng hẳn.
 *
 * Cả hai đều là `optional` nên TypeScript không kêu gì; chỗ nào con người phải nhớ hai nơi thì
 * chỗ đó cần một phép kiểm nhớ hộ (CLAUDE.md luật 14).
 *
 * <h2>Vì sao khẳng định ở tầng KIỂU chứ không ở tầng giá trị</h2>
 *
 * Kiểu TypeScript bị xoá sạch lúc chạy, nên không có cách nào so hai `interface` bằng mã chạy
 * thật. Khẳng định thật của bài này là dòng `const _khop: ... = true` bên dưới: lệch một trường
 * là `Exact<>` cho ra `false`, gán `true` vào đó thành lỗi biên dịch, và `npm run typecheck` /
 * `npm run build` đỏ.
 *
 * ⚠ Nghĩa là bài này **không** hiện ra ở `npm test` — nó bắt lỗi ở bước `tsc`. Cả hai bước đều
 * nằm trong job `frontend` của CI. Phần chạy được bên dưới chỉ giữ cho bộ test có một dòng nhắc.
 */

/** `true` chỉ khi A và B gán được cho nhau theo CẢ HAI chiều — thừa hay thiếu trường đều bắt. */
type Exact<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;

// ⭐⭐ ĐÂY là khẳng định thật. Thêm/bớt/đổi kiểu một trường ở bất kỳ bên nào → lỗi biên dịch:
//     "Type 'false' is not assignable to type 'true'".
const _khop: Exact<AllowedAction, AllowedActionView> = true;

describe('AllowedAction ↔ AllowedActionView', () => {
  it('⭐ hai khai báo của cùng một payload khớp nhau (khẳng định ở tầng biên dịch)', () => {
    expect(_khop).toBe(true);
  });

  it('một giá trị hợp lệ dùng được ở cả hai vai trò, không cần ép kiểu', () => {
    // Đi qua đúng đường mà mã thật đi: payload API → prop component.
    const tuDay: AllowedActionView = {
      action: 'REQUEST_CHANGES',
      label: 'Yêu cầu chỉnh sửa',
      toState: 'YEU_CAU_CHINH_SUA',
      requiresReason: true,
    };
    const lamProp: AllowedAction = tuDay;

    expect(lamProp.requiresReason).toBe(true);
    expect(lamProp.toState).toBe('YEU_CAU_CHINH_SUA');
  });
});
