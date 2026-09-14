import { GIOI_TINH, HOC_VAN } from './hrVocabulary';

/** KPI của màn hình thống kê nhân sự — khớp `BaoCaoNhanSuService.Kpi`. */
export interface KpiNhanSu {
  tongDangLamViec: number;
  tuyenMoiThangNay: number;
  nghiViecThangNay: number;
  /** ⛔ **Chuỗi**, ⛔ không phải `number` — `NUMERIC` phía BE (quy tắc 2). */
  tyLeNghiViec: string;
  nguongNgayHopDong: number;
  hopDongSapHetHan: number;
  nguongNgayChungChi: number;
  chungChiSapHetHan: number;
  /**
   * Số tháng hệ **thật sự** có mốc nhân sự.
   *
   * ⛔⛔ `0` ⇒ mọi tỉ lệ ở trên là **chưa biết**, ⛔ không phải `0%`. Đây là năm đầu vận hành nên
   * con số này sẽ nhỏ — in một tỉ lệ tính trên một tháng duy nhất là một con số **đúng công thức**
   * mà ⛔ không nói được điều gì (quy tắc 16).
   */
  soThangCoDuLieu: number;
}

export interface MucDem {
  khoa: string;
  nhan: string;
  soLuong: number;
}

export interface BienDongThang {
  thang: string;
  tuyenMoi: number;
  nghiViec: number;
  dieuDong: number;
}

export interface TongQuanNhanSu {
  kpi: KpiNhanSu;
  theoDonVi: MucDem[];
  theoGioiTinh: MucDem[];
  theoHocVan: MucDem[];
  theoNhomTuoi: MucDem[];
  bienDong: BienDongThang[];
}

/**
 * Một dòng của danh mục tám báo cáo BCNS.
 *
 * ⛔⛔ `khaDung = false` ⇒ `lyDoChuaCo` **bắt buộc** khác null, và giao diện phải **hiện lý do**.
 * Ẩn hẳn dòng ấy đi thì lượt nghiệm thu đếm bảy nút rồi tick đủ — và *"BCNS-07 chưa có"* trở thành
 * một sự thật ⛔ không nơi nào ghi.
 */
export interface MucBaoCao {
  ma: string;
  ten: string;
  moTa: string;
  khaDung: boolean;
  lyDoChuaCo: string | null;
}

/**
 * Từ điển nhãn cho hai biểu đồ cơ cấu — **dùng lại** `GIOI_TINH` / `HOC_VAN` của `hrVocabulary`.
 *
 * ⛔⛔ Bản đầu của tệp này khai lại hai bảng ấy *"cho gần chỗ dùng"*. Đó là luật 14 ở dạng rẻ nhất
 * để mắc: hai nơi cùng dịch một enum, và ngày Công ty đổi *"Đại học"* thành *"Cử nhân/Kỹ sư"* thì
 * biểu mẫu hồ sơ đổi còn biểu đồ thống kê **⛔ không** — hai màn hình nói hai chữ cho cùng một giá
 * trị, và ⛔ không dòng lỗi nào.
 *
 * ⚠ Chỉ thêm đúng khoá `CHUA_NHAP` — nó ⛔ không phải một giá trị enum mà là một **nhóm** do
 * backend gộp lại, nên nó ⛔ không thuộc về `hrVocabulary`.
 */
const CHUA_NHAP = 'Chưa nhập';

export const NHAN_GIOI_TINH: Record<string, string> = { ...GIOI_TINH, CHUA_NHAP };

export const NHAN_HOC_VAN: Record<string, string> = { ...HOC_VAN, CHUA_NHAP };

/**
 * Đổi danh sách đếm của backend sang `Bucket` của bộ dựng biểu đồ.
 *
 * ⚠ `nhan` rơi về **khoá** khi từ điển ⛔ không có mục — ⛔ không rơi về chuỗi rỗng: một lát bánh
 * ⛔ không nhãn đọc như một lỗi vẽ, còn `DAI_HOC` đọc như *"thiếu bản dịch"*, và câu thứ hai chỉ
 * đúng chỗ.
 */
export function toBucket(muc: MucDem[], tuDien?: Record<string, string>) {
  return muc.map((m) => ({
    key: m.khoa,
    label: tuDien?.[m.khoa] ?? m.nhan ?? m.khoa,
    count: m.soLuong,
  }));
}
