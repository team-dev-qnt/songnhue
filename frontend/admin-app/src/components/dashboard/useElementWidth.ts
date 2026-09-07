import { useCallback, useRef, useState } from 'react';

/**
 * Bề rộng thật của một phần tử, theo dõi bằng `ResizeObserver`.
 *
 * <h3>⚠ Vì sao không dùng `window.innerWidth`</h3>
 *
 * Vùng nội dung của khung quản trị hẹp hơn cửa sổ đúng bằng bề rộng thanh bên, và thanh
 * bên **đóng/mở được**. Lấy theo cửa sổ thì thu thanh bên lại không làm lưới thêm cột, mà
 * mở thanh bên ra lại làm lưới tràn — trong khi cửa sổ chưa hề đổi kích thước nên không
 * có sự kiện `resize` nào để sửa.
 *
 * <h3>⚠⚠ Vì sao là ref DẠNG HÀM, không phải `useRef` + `useEffect`</h3>
 *
 * Bản đầu dùng `useRef` và đo trong một `useEffect` có danh sách phụ thuộc rỗng. Nó
 * **không chạy**, và bài kiểm bố cục ở bốn bề rộng thiết bị bắt được: lưới luôn ra
 * `repeat(1, …)`.
 *
 * <p>Nguyên nhân: trang hiện khung xương trong lúc chờ dữ liệu, nên ở lượt render đầu
 * — đúng lượt mà effect chạy — thẻ mang ref **chưa có trong cây DOM**, `ref.current` là
 * `null`, effect thoát sớm. Khi dữ liệu về và thẻ được gắn vào thì không có gì gọi lại
 * effect nữa: danh sách phụ thuộc rỗng nghĩa là "chạy đúng một lần", và lần đó đã trôi qua.
 *
 * <p>Hậu quả thật, không chỉ trong bài kiểm: dashboard hiện **một cột** trên mọi màn hình
 * cho tới khi người dùng đổi kích thước cửa sổ. Trên TV treo tường thì không bao giờ có
 * ai đổi kích thước cả.
 *
 * <p>Ref dạng hàm được React gọi đúng vào lúc nút gắn vào và lúc gỡ ra — tức là đúng sự
 * kiện cần, thay vì một thời điểm mà ta *đoán* là nút đã có ở đó.
 */
export function useElementWidth<T extends HTMLElement>() {
  const [beRong, setBeRong] = useState(0);
  const theoDoiRef = useRef<ResizeObserver | null>(null);

  const ref = useCallback((phanTu: T | null) => {
    theoDoiRef.current?.disconnect();
    theoDoiRef.current = null;

    if (!phanTu) {
      return;
    }
    // Đo ngay khi gắn. `ResizeObserver` có bắn một lượt đầu ở trình duyệt thật, nhưng đo
    // ở đây thì lượt render kế tiếp đã có số đúng thay vì phải chờ thêm một khung hình.
    setBeRong(phanTu.getBoundingClientRect().width);

    const theoDoi = new ResizeObserver((muc) => {
      const doDuoc = muc[0];
      if (doDuoc) {
        setBeRong(doDuoc.contentRect.width);
      }
    });
    theoDoi.observe(phanTu);
    theoDoiRef.current = theoDoi;
  }, []);

  return { ref, beRong };
}
