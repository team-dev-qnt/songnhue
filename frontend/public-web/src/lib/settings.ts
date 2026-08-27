/**
 * Đọc một giá trị từ `settings` — nơi DUY NHẤT ép kiểu cho tham số cấu hình của cổng.
 *
 * <h3>Vì sao cần một tệp riêng cho hai hàm ba dòng</h3>
 *
 * `GET /public/site-config` trả `Record<string, string>` — mọi giá trị là chuỗi, kể cả thứ
 * khai `INTEGER` hay `BOOLEAN` trong CSDL. Ép kiểu ngay tại nơi dùng thì mỗi nơi dùng tự chọn
 * cách xử lý chuỗi hỏng, và chỗ thứ ba sẽ chọn khác hai chỗ đầu: `Number('')` là `0`,
 * `Number('abc')` là `NaN`, và `NaN` truyền xuống `setInterval` thì bộ đếm chạy ngay lập tức
 * mỗi tick. Đó là quy tắc 12 ở dạng nhỏ nhất: đặt phép chuyển đổi ở chỗ dữ liệu đi qua.
 *
 * ⚠⚠ **"Rỗng" khác "chưa đặt"** — luật 3 của dự án, và đây đúng chỗ nó cắn. Người quản trị xoá
 * trắng một ô số trên màn hình cấu hình thì `settings` giữ chuỗi rỗng, không phải NULL; `??`
 * sẽ giữ nguyên chuỗi rỗng ấy và `Number('')` cho `0`. Nên hai hàm dưới đây kiểm **giá trị đã
 * giải**, không kiểm sự tồn tại của khoá.
 */

/**
 * Số nguyên không âm từ `settings`.
 *
 * @param macDinh lưới an toàn khi khoá chưa có trong CSDL hoặc giá trị không đọc được — KHÔNG
 *   phải nơi chốt giá trị nghiệp vụ. Giá trị thật nằm ở migration seed, để người quản trị sửa
 *   được mà không cần deploy.
 */
export function docSo(raw: string | undefined, macDinh: number): number {
  if (raw === undefined || raw.trim() === '') {
    return macDinh;
  }
  const so = Number(raw);
  // `0` là một giá trị HỢP LỆ và mang nghĩa (tắt tự làm mới, tắt autoplay), nên không được
  // rơi về mặc định. Chỉ chuỗi không phải số, số âm hay số vô hạn mới bị từ chối.
  return Number.isFinite(so) && so >= 0 ? so : macDinh;
}

/** Cờ bật/tắt từ `settings`. Chỉ đúng chuỗi `'true'`/`'false'`; thứ khác rơi về mặc định. */
export function docBool(raw: string | undefined, macDinh: boolean): boolean {
  if (raw === 'true') return true;
  if (raw === 'false') return false;
  return macDinh;
}
