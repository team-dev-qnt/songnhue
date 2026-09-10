import type { LeaderRow, OrgChartNode } from '@/lib/api';

/**
 * Một hộp trên sơ đồ tổ chức — **CR-24 · CR-25**.
 *
 * <p>Kiểu trung gian giữa dữ liệu API và phần vẽ. Có nó thì mọi suy luận về hình dạng cây nằm ở
 * `src/lib` — nơi vitest của `public-web` <b>thật sự chạy được</b>. Cấu hình vitest của ứng dụng
 * này cố ý ⛔ không dựng DOM (*"dựng một tầng mock nửa vời chỉ tạo ra thứ xanh mà ⛔ không chứng
 * minh gì"*), nên đặt logic trong component là đặt nó ngoài tầm mọi phép kiểm.
 */
export interface NutSoDo {
  /** Khoá React — mã đơn vị, hoặc `<mã cha>:<thứ tự>` cho hộp người. */
  khoa: string;
  ten: string;
  /** Nhãn nhỏ trong hộp: loại đơn vị, hoặc chức danh. `null` = ⛔ không hiện nhãn nào. */
  nhan: string | null;
  /** Các dòng phụ trong hộp — hiện chỉ dùng cho Ban lãnh đạo ở hộp gốc. */
  phu: DongPhu[];
  con: NutSoDo[];
}

export interface DongPhu {
  ten: string;
  chucDanh: string;
}

/** Nhãn hiển thị của từng loại đơn vị — `OrgUnitType` phía backend là enum trong mã, ⛔ không phải danh mục CRUD. */
export const NHAN_LOAI: Record<string, string> = {
  CONG_TY: 'Công ty',
  PHONG_BAN: 'Phòng ban',
  XI_NGHIEP: 'Xí nghiệp',
  TO_DOI: 'Tổ, đội',
  KHAC: 'Khác',
};

/**
 * Cây cơ cấu tổ chức, có gắn Ban lãnh đạo vào **hộp gốc**.
 *
 * <h2>⛔⛔ Vì sao lãnh đạo chỉ gắn vào nút gốc, và chỉ nút gốc ĐẦU TIÊN</h2>
 *
 * <p>Vì đó đúng là thứ endpoint trả về. {@code PublicOrgDirectoryService.companyLeaders()} gọi
 * {@code findFirstByParentIdIsNullAndDeletedAtIsNull()} rồi đọc danh bạ của <b>chính nút ấy</b>.
 * {@code LeaderRow} ⛔ <b>không</b> mang mã đơn vị, nên rải danh sách này cho nút nào khác là bịa
 * ra một khẳng định mà dữ liệu ⛔ không nói.
 *
 * <h2>⛔⛔ Vì sao ⛔ KHÔNG dựng cây phân cấp cho Ban lãnh đạo</h2>
 *
 * <p>Yêu cầu là *"sơ đồ hình cây"*, và cám dỗ tự nhiên là suy ra tầng từ chức danh: *Chủ tịch* trên
 * *Tổng Giám đốc* trên *Phó Tổng Giám đốc*. ⛔ Đó là **suy diễn từ chuỗi ký tự**, ⛔ không phải dữ
 * liệu: {@code org_unit_leaders} ⛔ không có cột cha–con, và {@code title} là ô văn bản tự do người
 * dùng gõ. Một cây dựng bằng cách so chuỗi sẽ **sai lặng lẽ** ngay lần Công ty đổi cách viết chức
 * danh — đúng họ với §11.12 (*một phép sắp xếp suy ra từ dữ liệu ⛔ không phải một thứ tự*).
 *
 * <p>⇒ Thứ tự hiển thị lấy nguyên {@code sort_order} — thứ **Công ty tự sắp** ở màn hình quản trị.
 * Muốn Chủ tịch đứng đầu thì kéo dòng ấy lên đầu; hệ ⛔ không đoán hộ.
 */
export function tuCoCauToChuc(chart: OrgChartNode[], lanhDao: LeaderRow[]): NutSoDo[] {
  return chart.map((nut, i) => doiNut(nut, i === 0 ? lanhDao : []));
}

function doiNut(nut: OrgChartNode, lanhDao: LeaderRow[]): NutSoDo {
  return {
    khoa: nut.code,
    ten: nut.name,
    nhan: NHAN_LOAI[nut.unitType] ?? nut.unitType,
    phu: lanhDao.map((n) => ({ ten: n.fullName, chucDanh: n.title })),
    con: nut.children.map((con) => doiNut(con, [])),
  };
}

/**
 * Sơ đồ **Ban lãnh đạo**: hộp Công ty ở trên, mỗi lãnh đạo một hộp ở dưới.
 *
 * <p>Đây là một cây <b>thật</b>, ⛔ không phải một cây bịa: nó khẳng định đúng điều dữ liệu nói —
 * *"những người này là ban lãnh đạo của đơn vị này"* — và ⛔ không khẳng định ai trên ai.
 *
 * @param tenCongTy tên nút gốc, lấy từ chính sơ đồ tổ chức. Rỗng thì trả `[]`: một hộp gốc ⛔ không
 *   có tên là một hộp ⛔ không nói gì (quy tắc 16), và cây lãnh đạo mà thiếu gốc thì ⛔ không phải cây.
 */
export function tuBanLanhDao(tenCongTy: string | null, lanhDao: LeaderRow[]): NutSoDo[] {
  if (!tenCongTy || lanhDao.length === 0) {
    return [];
  }
  return [
    {
      khoa: 'goc',
      ten: tenCongTy,
      nhan: NHAN_LOAI.CONG_TY,
      phu: [],
      con: lanhDao.map((n, i) => ({
        khoa: `lanh-dao-${i}`,
        ten: n.fullName,
        nhan: n.title,
        phu: [],
        con: [],
      })),
    },
  ];
}

/** Tên nút gốc của sơ đồ — `null` khi chưa nhập đơn vị nào. */
export function tenGoc(chart: OrgChartNode[]): string | null {
  return chart.length > 0 ? chart[0].name : null;
}

/**
 * Số hộp trên một hàng rộng nhất.
 *
 * <p>Dùng để trang tự quyết có cần khung cuộn ngang hay ⛔ không — 9 Xí nghiệp trên một hàng là
 * ~1.6 ngàn pixel, rộng hơn mọi màn hình xách tay. Đây là con số đo được từ **dữ liệu thật**, ⛔
 * không phải một ngưỡng đoán: hôm nay 9, ngày Công ty nhập phòng ban thì nó tự đổi.
 */
export function beRongToiDa(nut: NutSoDo[]): number {
  if (nut.length === 0) {
    return 0;
  }
  const conGop = nut.flatMap((n) => n.con);
  return Math.max(nut.length, beRongToiDa(conGop));
}

/** Chiều sâu của cây — 0 khi rỗng, 1 khi chỉ có gốc. */
export function doSau(nut: NutSoDo[]): number {
  if (nut.length === 0) {
    return 0;
  }
  return 1 + Math.max(...nut.map((n) => doSau(n.con)));
}
