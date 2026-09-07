import { Modal } from 'antd';
import { useRef } from 'react';
import { useBeforeUnload, useBlocker } from 'react-router-dom';

/**
 * **Chặn rời trang khi còn thay đổi chưa lưu** — WS-41 (T41.10).
 *
 * <h3>Vì sao cần cả HAI cơ chế</h3>
 *
 * Chúng chặn hai đường **tách biệt**, không cái nào thay được cái nào:
 *
 * <ul>
 *   <li>{@code useBlocker} — điều hướng **trong** ứng dụng (nút ←, mục menu, mọi `<Link>`).
 *       React Router không rời trang thật nên trình duyệt chẳng biết gì.
 *   <li>{@code useBeforeUnload} — đóng tab, F5, gõ địa chỉ khác. Ngược lại: trình duyệt lo, còn
 *       router không thấy.
 * </ul>
 *
 * ⛔ **Không** dùng `unstable_usePrompt`: nó gọi `window.confirm` — hộp thoại của trình duyệt, khác
 * hẳn giao diện AntD của cả ứng dụng, và không đổi được chữ trên nút.
 *
 * <h3>⚠⚠ `useBlocker` đòi DATA ROUTER</h3>
 *
 * Nó **ném** khi không có `DataRouterContext`. Ứng dụng thật thoả (`createBrowserRouter` +
 * `RouterProvider`), nhưng bốn tệp `.test.tsx` khác trong kho dùng `<MemoryRouter>` — thứ *không*
 * cấp context ấy. Bài kiểm cho màn hình nào dùng hook này **phải** dựng `createMemoryRouter` +
 * `RouterProvider`.
 *
 * <h3>⛔⛔ Hộp thoại KHAI BÁO, không phải `Modal.confirm` trong effect</h3>
 *
 * Bản đầu gọi `Modal.confirm(...)` trong một `useEffect`. Đo được: **hai hộp thoại chồng nhau** —
 * `Modal.confirm` gắn thẳng vào `document.body` ngoài cây React, còn effect thì chạy hai lần dưới
 * StrictMode, và hàm dọn dẹp không kịp gỡ cái thứ nhất trước khi cái thứ hai dựng. Người dùng bấm
 * "Ở lại" một cái, vẫn còn một cái nằm đó.
 *
 * Hộp thoại khai báo thì React quản lý vòng đời của nó — không có bản sao nào để lạc.
 *
 * <h3>⛔⛔ `choPhepRoi` — cái bẫy bắt buộc phải xử lý</h3>
 *
 * Sau khi Lưu thành công, màn hình **tự** điều hướng (tạo bài mới → sang trang bài vừa tạo). Nếu
 * chỉ hạ cờ bẩn bằng `setState` rồi gọi `navigate()` ngay sau, React **gộp** hai việc và chỉ vẽ
 * lại ở lượt sau — nên hàm chặn mà router đang giữ **vẫn là bản cũ**, và nó chặn đúng cú chuyển
 * trang do chính ta thực hiện. Người dùng lưu xong thì bị hỏi *"rời trang không?"*.
 *
 * Một `ref` không đợi lượt vẽ nào, nên `choPhepRoi()` có hiệu lực **ngay lập tức**.
 */
export function useChanRoiTrang(coThayDoi: boolean, cauHoi: string) {
  /** Bật cho lượt điều hướng do chính màn hình thực hiện — xem javadoc. */
  const boQua = useRef(false);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      coThayDoi && !boQua.current && currentLocation.pathname !== nextLocation.pathname,
  );

  useBeforeUnload((event) => {
    if (coThayDoi && !boQua.current) {
      // ⚠ Trình duyệt hiện đại **không** hiện chuỗi của ta — chúng dùng câu mặc định của chính
      //   chúng. `preventDefault()` mới là thứ có tác dụng. Đừng viết một câu công phu ở đây rồi
      //   tưởng người dùng đọc được nó.
      event.preventDefault();
    }
  });

  return {
    /** Gọi TRƯỚC một lượt điều hướng do chính màn hình thực hiện (ví dụ sau khi lưu xong). */
    choPhepRoi: () => {
      boQua.current = true;
    },

    /**
     * Hộp thoại xác nhận — nơi gọi render nó.
     *
     * ⚠ JSX **nội tuyến**, ⛔ không tách thành một component cục bộ có tên: nhánh `localComponents`
     * của `react-refresh/only-export-components` bắn ngay cả khi tệp không xuất component nào, và
     * rule ấy ở mức **lỗi** với `--max-warnings=0`.
     */
    hopThoaiRoiTrang: (
      <Modal
        open={blocker.state === 'blocked'}
        title="Rời trang khi chưa lưu?"
        okText="Rời trang"
        okButtonProps={{ danger: true }}
        cancelText="Ở lại"
        onOk={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        {cauHoi}
      </Modal>
    ),
  };
}
