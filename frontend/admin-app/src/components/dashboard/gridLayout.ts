/**
 * Số cột của lưới dashboard — **hàm thuần**, và đó là điều kiện để kiểm được.
 *
 * <h3>Vì sao tính bằng JS chứ không để CSS `auto-fit` lo</h3>
 *
 * `repeat(auto-fit, minmax(300px, 1fr))` làm đúng việc này và không bao giờ tràn. Nhưng
 * nó chỉ được tính bởi **bộ dựng bố cục của trình duyệt**, mà jsdom không có bộ dựng bố
 * cục — nên yêu cầu T23.11 *"khẳng định cả hai vế: không tràn ngang và không mất khối ở
 * ba bề rộng 3840/1920/1366"* sẽ không có cách nào kiểm ở CI. Một bài kiểm gọi
 * `render()` rồi đọc `style` chỉ chứng minh chuỗi CSS được viết ra, không chứng minh
 * bố cục đúng — đúng loại "xanh mà không kiểm gì" mà dự án đã trả giá nhiều lần.
 *
 * <p>Đưa quyết định về một hàm thì bài kiểm chạy **đúng đoạn mã production chạy**, và
 * kiểm được cả những bề rộng không ai ngồi thử tay (một khe hẹp giữa hai điểm ngắt).
 *
 * <h3>Hai bất biến mà bài kiểm giữ</h3>
 *
 * <ol>
 *   <li><b>Luôn ≥ 1 cột</b> — 0 cột là lưới không vẽ gì, tức là "mất khối" ở dạng tệ nhất.
 *   <li><b>Không tràn ngang</b> — nhiều hơn một cột thì tổng bề rộng tối thiểu của các
 *       cột phải vừa trong khung. Đúng một cột thì thẻ tự co (các thẻ đặt
 *       {@code minWidth: 0}), nên vẫn không tràn.
 * </ol>
 */

/** Bề rộng tối thiểu đọc được của một ô KPI (px) — dưới mức này thì số bị xuống dòng. */
export const RONG_TOI_THIEU_KPI = 220;

/** Bề rộng tối thiểu của một khối biểu đồ — hẹp hơn thì nhãn trục chồng lên nhau. */
export const RONG_TOI_THIEU_KHOI = 420;

/**
 * ⛔ Trần số cột, kể cả trên màn hình 4K.
 *
 * Không phải để tiết kiệm chỗ mà vì khoảng cách đọc: màn hình 85" treo tường được đọc từ
 * 4–6 m, và mười hai ô trên một hàng thì mỗi ô hẹp tới mức chữ không còn đọc được ở
 * khoảng cách đó — tức là thêm cột lại làm mất thông tin. Thà để ô rộng ra.
 */
const TRAN_COT_KPI = 5;
const TRAN_COT_KHOI = 3;

export function soCot(beRong: number, rongToiThieu: number, tranCot: number): number {
  if (!Number.isFinite(beRong) || beRong <= 0) {
    // Lượt render đầu tiên chưa đo được khung (ResizeObserver chưa chạy). Trả 1 cột: một
    // cột trên màn hình rộng chỉ xấu trong vài mili giây, còn 0 cột là màn hình trắng.
    return 1;
  }
  const cot = Math.floor(beRong / rongToiThieu);
  return Math.min(Math.max(cot, 1), tranCot);
}

export interface BoCucDashboard {
  cotKpi: number;
  cotKhoi: number;
}

export function boCucTheoBeRong(beRong: number): BoCucDashboard {
  return {
    cotKpi: soCot(beRong, RONG_TOI_THIEU_KPI, TRAN_COT_KPI),
    cotKhoi: soCot(beRong, RONG_TOI_THIEU_KHOI, TRAN_COT_KHOI),
  };
}

/** Chuỗi `grid-template-columns` tương ứng — `minmax(0, 1fr)` để thẻ co được, không tràn. */
export function cotThanhCss(soLuong: number): string {
  return `repeat(${Math.max(1, soLuong)}, minmax(0, 1fr))`;
}
