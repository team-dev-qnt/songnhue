import { type EChartsOption } from 'echarts';

/** Một nút của sơ đồ tổ chức — khớp `SoDoToChucService.Nut` phía backend. */
export interface NutSoDo {
  publicId: string;
  code: string;
  name: string;
  shortName: string | null;
  unitType: string;
  depth: number;
  /** `false` = đơn vị đã tắt — PHẢI hiện kèm nhãn, ⛔ không được ẩn. */
  dangDung: boolean;
  lanhDao: { hoTen: string; chucDanh: string }[];
  /** Người có `org_unit_id` khớp ĐÚNG nút này. */
  soNhanSuTrucTiep: number;
  /** Cộng dồn cả cây con — **bằng** trực tiếp khi nút không có con. */
  soNhanSuCaNhanh: number;
  con: NutSoDo[];
}

/**
 * @param soNhanSuNgoaiSoDo bình thường là **0**. Khác 0 ⇒ có hồ sơ trỏ vào một đơn vị ⛔ không còn
 *   trên sơ đồ — giao diện PHẢI nói ra, vì triệu chứng còn lại là im lặng tuyệt đối.
 */
export interface SoDoView {
  goc: NutSoDo[];
  tongNhanSu: number;
  soNhanSuNgoaiSoDo: number;
  soDonVi: number;
}

/** Một nút ECharts `tree`. */
interface NutEcharts {
  name: string;
  value: number;
  children?: NutEcharts[];
  collapsed?: boolean;
  itemStyle?: Record<string, unknown>;
  label?: Record<string, unknown>;
}

/**
 * Nhãn của một nút — **ba dòng**: tên đơn vị · người đứng đầu · quân số.
 *
 * ⛔⛔ Quân số hiện dạng `trực tiếp / cả nhánh` khi hai số **khác nhau**, và chỉ một số khi chúng
 * bằng nhau. Hiện luôn hai số làm nút lá đọc thành `2/2` — một phân số vô nghĩa; hiện luôn một số
 * thì một Xí nghiệp có 4 Tổ đội đọc thành **0 người**. Cả hai đều là câu trả lời sai mà im lặng.
 *
 * ⚠ Người đứng đầu lấy **phần tử đầu** của danh sách đã sắp theo `sort_order` Công ty tự đặt —
 * ⛔ **không** suy ai cao hơn ai bằng cách so chuỗi chức danh (T50.6: `title` là ô tự do).
 */
export function nhanNut(nut: NutSoDo): string {
  const ten = nut.shortName?.trim() || nut.name;
  const nguoiDau = nut.lanhDao[0];
  const quanSo =
    nut.soNhanSuTrucTiep === nut.soNhanSuCaNhanh
      ? `${nut.soNhanSuCaNhanh} người`
      : `${nut.soNhanSuTrucTiep} / ${nut.soNhanSuCaNhanh} người`;

  const dong = [nut.dangDung ? ten : `${ten} (đã tắt)`];
  if (nguoiDau) {
    dong.push(`${nguoiDau.chucDanh}: ${nguoiDau.hoTen}`);
  }
  dong.push(quanSo);
  return dong.join('\n');
}

function toEcharts(nut: NutSoDo, moToiCap: number): NutEcharts {
  return {
    name: nhanNut(nut),
    value: nut.soNhanSuCaNhanh,
    // Thu gọn từ cấp 3 trở xuống: một cây tổ chức đầy đủ trải hết chiều ngang thì chữ chồng lên
    // nhau và màn hình vô dụng. ⛔ Người dùng bấm để mở — ⛔ không có nút nào bị GIẤU.
    collapsed: nut.depth >= moToiCap && nut.con.length > 0,
    itemStyle: nut.dangDung ? undefined : { opacity: 0.45 },
    children: nut.con.length > 0 ? nut.con.map((c) => toEcharts(c, moToiCap)) : undefined,
  };
}

/**
 * Cấu hình ECharts của sơ đồ tổ chức.
 *
 * ⛔ Tách khỏi component **⛔ không phải cho gọn**: `react-refresh/only-export-components` cấm một
 * tệp vừa export component vừa export hàm, và cổng `Frontend — lint` chạy với `--max-warnings 0`
 * (T55.8). Tách ra còn cho phép `nhanNut` có bài kiểm riêng — thứ ⛔ không làm được nếu nó nằm
 * trong thân một component.
 */
export function optionSoDo(goc: NutSoDo[], moToiCap = 3): EChartsOption {
  return {
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [
      {
        type: 'tree',
        data: goc.map((n) => toEcharts(n, moToiCap)),
        top: '4%',
        left: '10%',
        bottom: '4%',
        right: '18%',
        symbolSize: 10,
        orient: 'LR',
        label: { position: 'left', verticalAlign: 'middle', align: 'right', fontSize: 12 },
        leaves: { label: { position: 'right', verticalAlign: 'middle', align: 'left' } },
        emphasis: { focus: 'descendant' },
        expandAndCollapse: true,
        animationDuration: 400,
      },
    ],
  } as EChartsOption;
}

/** Chiều cao cần cho sơ đồ — suy từ **số nút lá**, ⛔ không phải một hằng số. */
export function chieuCaoSoDo(goc: NutSoDo[]): number {
  const demLa = (n: NutSoDo): number =>
    n.con.length === 0 ? 1 : n.con.reduce((t, c) => t + demLa(c), 0);
  const soLa = goc.reduce((t, n) => t + demLa(n), 0);
  // 48px mỗi lá là đủ cho nhãn ba dòng; kẹp dưới để một cây 2 nút ⛔ không thành một dải mỏng.
  return Math.max(360, soLa * 48);
}
