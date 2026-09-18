import { echarts } from '@/components/charts/setup';

/**
 * Kết xuất sơ đồ tổ chức ra tệp — CN-04.1.
 *
 * <h2>⛔⛔ PNG và SVG được; PDF A3 và Excel thì KHÔNG — và điều đó phải nói thẳng</h2>
 *
 * Đặc tả liệt kê *"export PNG/SVG/PDF A3/Excel"*. Đo ngày 14/09/2026: kho **⛔ không có** một bộ
 * kết xuất PDF/XLSX nào (`POI`/`jasper`/`openpdf` = 0 trong cả 7 `pom.xml`) — nợ **T42.14**.
 *
 * ⛔ Tự vẽ một PDF ở trình duyệt bằng cách chụp ảnh rồi nhét vào trang A3 là **giả** đúng thứ đặc
 * tả đòi: sản phẩm ra là một ảnh bitmap trong vỏ PDF, in khổ A3 thì vỡ chữ, và ⛔ không ai biết cho
 * tới lúc cầm bản in. ⇒ Nút PDF/Excel **⛔ không tồn tại**, và màn hình nói rõ vì sao.
 *
 * <h2>⭐ Cả hai lượt xuất đi qua MỘT thực thể TẠM, ⛔ không đọc biểu đồ đang hiện</h2>
 *
 * Hai lý do, và lý do thứ hai mới là lý do thật:
 *
 * 1. `BaseChart` ⛔ không nhả thực thể ECharts ra ngoài. Thêm một prop để lấy nó là sửa một
 *    component **dùng chung cho mọi biểu đồ** chỉ để phục vụ một màn hình.
 * 2. ⛔⛔ Biểu đồ trên màn hình đang **thu gọn từ cấp 3**. Xuất từ nó cho ra một tệp **thiếu
 *    nhánh** — đúng thứ người nhận ⛔ không có cách nào biết là thiếu. Thực thể tạm dựng lại sơ đồ
 *    với **cây mở hết** và kích thước do ta chọn, nên bản xuất ⛔ không phụ thuộc người dùng vừa
 *    bấm mở/đóng nhánh nào.
 *
 * ⚠ `SVGRenderer` chỉ sống ở đây; màn hình vẫn Canvas (wall mode 4K cần thế) — xem `charts/setup.ts`.
 */
export type DinhDangXuat = 'png' | 'svg';

export function xuatSoDo(
  option: Record<string, unknown>,
  dinhDang: DinhDangXuat,
  rong: number,
  cao: number,
  tenTep: string,
  /**
   * Màu nền của ảnh PNG.
   *
   * ⛔⛔ Là **tham số**, ⛔ không phải một hằng `'#fff'` trong tệp này. Hai lý do, và lý do thứ hai
   * mới là lý do thật: (1) `noHardcodedColors.test.ts` là một bậc thang **CHỈ ĐƯỢC GIẢM**; (2) một
   * bản PNG nền trắng cứng sẽ sai ở chế độ giao diện tối — và ⛔ không ai phát hiện cho tới lúc
   * dán ảnh vào một văn bản. Nơi gọi đọc nó từ `theme.useToken()`.
   */
  mauNen: string,
): void {
  const khung = document.createElement('div');
  khung.style.width = `${rong}px`;
  khung.style.height = `${cao}px`;
  const tam = echarts.init(khung, undefined, {
    renderer: dinhDang === 'svg' ? 'svg' : 'canvas',
    width: rong,
    height: cao,
  });
  try {
    // `animation: false` — `getDataURL` chụp NGAY, mà một biểu đồ đang chạy animation thì khung
    // hình ấy là nửa chừng: các nhánh còn đang bung ra. Triệu chứng là một tệp trông "gần đúng".
    tam.setOption({ ...option, animation: false });
    const url =
      dinhDang === 'svg'
        ? `data:image/svg+xml;charset=utf-8,${encodeURIComponent(tam.renderToSVGString())}`
        : // `pixelRatio: 2` — sơ đồ hay bị dán vào văn bản rồi in; 1x thì chữ nhoè.
          tam.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: mauNen });
    taiVe(url, `${tenTep}.${dinhDang}`);
  } finally {
    // ⛔ Bắt buộc: một thực thể ECharts ⛔ không `dispose` giữ nguyên bộ lắng nghe resize và cả
    //   khung DOM — mỗi lượt bấm Xuất là một lượt rò rỉ.
    tam.dispose();
  }
}

function taiVe(url: string, tenTep: string): void {
  const a = document.createElement('a');
  a.href = url;
  a.download = tenTep;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
