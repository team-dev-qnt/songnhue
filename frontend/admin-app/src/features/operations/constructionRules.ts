import type dayjs from 'dayjs';

import {
  type ConstructionType,
  type MaintenanceType,
  type OperationStatusBatchItem,
} from '@/shared/api-types';

import { type ConstructionFilterValues } from './components/ConstructionFilter';

/**
 * Quy tắc thuần của MOD-02 — tách khỏi component để **kiểm được**.
 *
 * Bốn hàm dưới đây trước đây nằm rải trong thân component: một cái là biểu thức inline trong
 * `mutationFn`, một cái không tồn tại, hai cái là vòng lặp trong `useMemo`. Ở dạng đó, bài kiểm duy
 * nhất viết được là một bài render — và bài render sẽ xanh cả khi hàm trả về sai, vì nó chỉ khẳng
 * định "có một cái bảng hiện ra".
 *
 * ⚠ Đây là **bản sao thứ hai** của những luật đã có ở backend (OPS-2009, OPS-2011, OPS-2006).
 * Bản ở đây chỉ để người dùng thấy lỗi ngay tại ô nhập; **backend vẫn là nơi chốt**. Chỗ nào hai bản
 * có thể lệch nhau thì ghi rõ mã lỗi tương ứng để lần sau còn dò.
 */

/** Khối thông số kỹ thuật của từng loại công trình — CN-02.1. */
export type KhoiThongSo = 'pump' | 'sluice' | 'linear' | null;

/**
 * Loại công trình quyết định khối thông số nào được nhập — `OPS-2009` ở backend.
 *
 * Trả `null` cho {@code KHAC}: loại "Khác" không có bộ thông số riêng, và đưa nó về một khối bất kỳ
 * là mở đường cho dữ liệu vào nhầm bảng.
 */
export function khoiThongSoTheoLoai(loai: ConstructionType | undefined): KhoiThongSo {
  switch (loai) {
    case 'TRAM_BOM':
      return 'pump';
    case 'CONG':
      return 'sluice';
    case 'KENH_MUONG':
    case 'DE_DIEU':
      return 'linear';
    default:
      return null;
  }
}

/**
 * Xoá các khối thông số không thuộc loại đang chọn trước khi gửi lên.
 *
 * ⚠ Cần thiết vì AntD **giữ nguyên giá trị của ô đã bị ẩn** trong form store: người dùng nhập thông
 * số trạm bơm, đổi loại sang cống, bấm Lưu — payload mang theo cả `pump` lẫn `sluice`, và backend
 * trả `OPS-2009`. Không xoá ở đây thì lỗi hiện ra ở một ô người dùng không còn nhìn thấy.
 */
export function locKhoiThongSo<T extends { constructionType?: ConstructionType }>(
  values: T,
): T & { pump: unknown; sluice: unknown; linear: unknown } {
  const khoi = khoiThongSoTheoLoai(values.constructionType);
  const v = values as T & { pump?: unknown; sluice?: unknown; linear?: unknown };
  return {
    ...values,
    pump: khoi === 'pump' ? (v.pump ?? null) : null,
    sluice: khoi === 'sluice' ? (v.sluice ?? null) : null,
    linear: khoi === 'linear' ? (v.linear ?? null) : null,
  };
}

/**
 * Lý trình theo dạng {@code K<km>+<m>} — `OPS-2011`.
 *
 * Phần mét là **ba chữ số** và nhỏ hơn 1000: `K0+390` hợp lệ, `K0+1390` thì không — 1390 mét là
 * `K1+390`. Không chặn điều đó thì hai cách viết cùng một vị trí cùng tồn tại trong CSDL, và mọi
 * phép so sánh/sắp xếp theo lý trình về sau đều sai một cách im lặng.
 */
const LY_TRINH = /^K\d+\+\d{1,3}$/;

export function hopLeLyTrinh(value: string | undefined | null): boolean {
  if (!value) {
    return true; // để trống là hợp lệ — lý trình không bắt buộc
  }
  return LY_TRINH.test(value.trim());
}

/**
 * Triệu VNĐ → VNĐ, đi qua **chuỗi**.
 *
 * ⛔ Không dùng `trieu * 1_000_000`: quy tắc 2 cấm float cho mọi số tiền, và JS chỉ có float.
 * `1.001 * 1_000_000` cho ra `1000999.9999999999` — thiếu 1 đồng, đủ để một bảng quyết toán lệch.
 */
export function trieuSangVnd(trieu: number | string | undefined | null): string | null {
  if (trieu === undefined || trieu === null || trieu === '') {
    return null;
  }
  const text = String(trieu).trim();
  if (!/^-?\d+(\.\d+)?$/.test(text)) {
    return null;
  }
  const am = text.startsWith('-');
  const [nguyen, thapPhan = ''] = (am ? text.slice(1) : text).split('.');
  const sauDauPhay = (thapPhan + '000000').slice(0, 6);
  const ghep = `${nguyen}${sauDauPhay}`.replace(/^0+(?=\d)/, '');
  return `${am && ghep !== '0' ? '-' : ''}${ghep}`;
}

/** VNĐ → triệu VNĐ, để đổ ngược vào ô nhập khi mở biểu mẫu sửa. */
export function vndSangTrieu(vnd: string | number | undefined | null): number | undefined {
  if (vnd === undefined || vnd === null || vnd === '') {
    return undefined;
  }
  const so = Number(vnd);
  return Number.isFinite(so) ? so / 1_000_000 : undefined;
}

/** Một dòng đang nhập ở màn hình nhập nhanh tình hình vận hành. */
export interface DongNhapNhanh {
  code?: string;
  value?: string;
  note?: string;
}

/**
 * Dựng payload nhập nhanh tình hình vận hành — CN-02.11.
 *
 * Ba điều được giữ, mỗi điều tương ứng một cách hỏng đã gặp:
 *
 * 1. **Chỉ gửi dòng đã chọn mã.** Bản trước gửi *toàn bộ* danh sách công trình ở mỗi lượt Lưu, mỗi
 *    dòng mang sẵn trạng thái hiện tại — một lượt bấm sinh ra hàng trăm bản ghi nhật ký không ai nhập.
 * 2. **Khoá là `constructionPublicId`.** Khoá nội bộ kiểu số ở đây từng là một lỗ IDOR (§10.35).
 * 3. **Giá trị kèm theo là chuỗi**, cột `NUMERIC(10,2)` phía CSDL.
 */
export function dungPayloadNhapNhanh(
  nhap: Record<string, DongNhapNhanh>,
  effectiveAt: string,
): OperationStatusBatchItem[] {
  return Object.entries(nhap)
    .filter(([, dong]) => !!dong.code)
    .map(([constructionPublicId, dong]) => ({
      constructionPublicId,
      operationCode: dong.code as string,
      parameterValue: dong.value?.trim() ? dong.value.trim() : undefined,
      note: dong.note?.trim() ? dong.note.trim() : undefined,
      effectiveAt,
    }));
}

/**
 * Dịch query string của đường dẫn thành bộ lọc ban đầu.
 *
 * Hàm thuần, tách riêng để kiểm được mà không phải dựng router — nếu nó nằm trong component thì
 * bài kiểm duy nhất viết được là một bài render, và bài đó sẽ xanh cả khi hàm trả về `{}`.
 */
export function locTuDuongDan(params: URLSearchParams): ConstructionFilterValues {
  const loc: ConstructionFilterValues = {};
  const q = params.get('q');
  const type = params.get('type');
  const status = params.get('status');
  const level = params.get('level');
  const river = params.get('river');
  if (q) loc.q = q;
  if (type) loc.type = type as ConstructionFilterValues['type'];
  if (status) loc.status = status as ConstructionFilterValues['status'];
  if (level) loc.level = level as ConstructionFilterValues['level'];
  if (river) loc.river = river;
  if (params.get('withoutLocation') === 'true') loc.withoutLocation = true;
  return loc;
}

/** Giá trị người dùng nhập trên biểu mẫu — chưa phải payload gửi lên. */
export interface MaintenanceFormValues {
  workType: MaintenanceType;
  severity?: string;
  startedOn: dayjs.Dayjs;
  completedOn?: dayjs.Dayjs;
  content: string;
  itemOrEquipment?: string;
  /** Ai làm: chọn đơn vị nội bộ hay gõ tên nhà thầu — đúng một trong hai. */
  performerKind: 'INTERNAL' | 'EXTERNAL';
  performerOrgUnitId?: string;
  performerName?: string;
  costTrieu?: number;
  fundingSource?: string;
}

/**
 * Dựng payload gửi lên từ giá trị biểu mẫu.
 *
 * Hàm thuần, export riêng để kiểm được — ba quy tắc dưới đây đều là chỗ đã có mã lỗi riêng, và cả
 * ba đều không lộ ra nếu chỉ kiểm bằng một bài render:
 *
 * 1. **`OPS-2017`** — `performerOrgUnitId` và `performerName` loại trừ nhau. Gửi cả hai, hoặc gửi
 *    kèm giá trị cũ của ô vừa bị ẩn đi, đều bị backend từ chối.
 * 2. **`OPS-2003`** — `severity` chỉ thuộc về bản ghi "Khắc phục sự cố". Đổi loại công việc mà
 *    không xoá mức độ là mang theo một trường không được phép có.
 * 3. **Quy tắc 2** — người dùng nhập chi phí theo **triệu VNĐ** cho dễ đọc, backend nhận **VNĐ**.
 *    Quy đổi bằng chuỗi chứ không bằng `* 1e6` trên số thực: `1.001 * 1_000_000` trong JS ra
 *    `1000999.9999999999`.
 */
export function dungPayloadSuaChua(values: MaintenanceFormValues, constructionPublicId: string) {
  const laSuCo = values.workType === 'KHAC_PHUC_SU_CO';
  const noiBo = values.performerKind === 'INTERNAL';

  return {
    constructionId: constructionPublicId,
    workType: values.workType,
    severity: laSuCo ? (values.severity ?? null) : null,
    startedOn: values.startedOn.format('YYYY-MM-DD'),
    completedOn: values.completedOn ? values.completedOn.format('YYYY-MM-DD') : null,
    content: values.content,
    itemOrEquipment: values.itemOrEquipment || null,
    performerOrgUnitId: noiBo ? (values.performerOrgUnitId ?? null) : null,
    performerName: noiBo ? null : (values.performerName ?? null),
    cost: trieuSangVnd(values.costTrieu),
    fundingSource: values.fundingSource || null,
  };
}
