interface KhoaDangNhapProps {
  tieuDe: string;
  moTa: string;
}

/**
 * Ô nói rõ một phần nội dung **yêu cầu đăng nhập**, và rằng chức năng đăng nhập chưa dựng.
 *
 * <h2>⛔ Vì sao không dựng sẵn nội dung rồi ẩn đi</h2>
 *
 * §2 của "YÊU CẦU CHỈNH SỬA WEBSITE": *"Phân quyền phải xử lý ở tầng route/API, không chỉ
 * ẩn/hiện ở giao diện."* Dựng bảng tuần/tháng rồi che bằng CSS thì dữ liệu đã nằm trong HTML
 * gửi tới trình duyệt — ai mở DevTools cũng đọc được, và tệ hơn là <b>trông như đã phân
 * quyền</b>. Đó là ảo giác đắt tiền: nó làm người nghiệm thu tick vào một ô chưa có gì đứng
 * sau.
 *
 * <h2>⛔ Và vì sao không có nút "Đăng nhập" ở đây</h2>
 *
 * Cổng công khai chưa có tầng xác thực nào — thêm nó là CR-08, gồm cả trang đăng nhập, vòng
 * xoay refresh token, vai trò "người xem cổng", và các endpoint chi tiết <b>không</b> mang
 * {@code @PublicEndpoint}. Một nút dẫn tới trang chưa tồn tại là đúng hình dạng §10.54: cổng
 * quảng cáo một khu vực mà bấm vào là 404, và nó chỉ lộ ra khi người dùng thật bấm.
 *
 * <p>Ô này biến mất trong lượt làm CR-08; tới lúc đó nó được thay bằng đường dẫn thật, và
 * chính endpoint phía sau mới là chỗ chặn.
 */
export function KhoaDangNhap({ tieuDe, moTa }: KhoaDangNhapProps) {
  return (
    <section className="rounded-xl border border-surface-border bg-surface-bgLayout/60 p-5">
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-surface-textSecondary shadow-2xs"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
            />
          </svg>
        </span>
        <div>
          <h2 className="text-sm font-bold text-surface-textBase">{tieuDe}</h2>
          <p className="mt-1 text-xs text-surface-textSecondary">{moTa}</p>
          <p className="mt-2 text-xs text-surface-textSecondary">
            Chức năng đăng nhập trên cổng chưa được dựng trong đợt chỉnh sửa này. Khi có, đường dẫn
            xem chi tiết sẽ xuất hiện tại đây và phần dữ liệu chi tiết được chặn ở tầng API — gọi
            thẳng API khi chưa đăng nhập vẫn bị từ chối.
          </p>
        </div>
      </div>
    </section>
  );
}
