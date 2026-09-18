import {
  type BaoCaoNhanhKyView,
  type BcnChinO,
  type BcnDongBang5View,
  type BcnKhoiView,
  type BcnTramView,
} from '@/shared/api-types';

/**
 * Luật thuần của màn hình Báo cáo nhanh — ⛔ React, ⛔ gọi API, kiểm được bằng vitest trần.
 *
 * ⛔ **Không có phép CỘNG nào ở đây** (quy tắc 3): Bảng 1, Mục 1, Mục 3, cột "Cộng" đều do backend
 * tính và trả về sau mỗi lượt lưu. Tầng này chỉ quyết định **hiện dòng nào** và **gửi ô nào**.
 */

/** Nhãn trạng thái kỳ — `mau` là trạng thái dựng sẵn của AntD `Tag`, ⛔ mã màu. */
export const NHAN_TRANG_THAI: Record<
  BaoCaoNhanhKyView['trangThai'],
  { nhan: string; mau: string }
> = {
  NHAP: { nhan: 'Đang nhập', mau: 'processing' },
  DA_CHOT: { nhan: 'Đã chốt', mau: 'success' },
};

/** Bản nháp Bảng 2: nhomMayPublicId → số máy đang chạy (`null` = xoá về "chưa nhập"). */
export type NhapVanHanh = Record<string, number | null>;

/** Giá trị đang hiển thị của một nhóm: bản nháp thắng số đã lưu. */
export function giaTriVanHanh(
  nhap: NhapVanHanh,
  nhomMayPublicId: string,
  daLuu: number | null,
): number | null {
  return nhomMayPublicId in nhap ? (nhap[nhomMayPublicId] ?? null) : daLuu;
}

/**
 * Trạm "đang hoạt động" ⇔ ÍT NHẤT MỘT nhóm máy có số chạy > 0 (spec §4.4).
 *
 * ⛔ Đơn vị ẩn là CẢ TRẠM: trạm có hai nhóm, một nhóm chạy, thì hiện ĐỦ hai dòng — ẩn nhóm kia là để
 * người nhập tưởng trạm chỉ có một cỡ máy.
 */
export function tramDangHoatDong(tram: BcnTramView, nhap: NhapVanHanh): boolean {
  return tram.nhom.some((n) => (giaTriVanHanh(nhap, n.nhomMayPublicId, n.soMayVanHanh) ?? 0) > 0);
}

/**
 * Lọc Bảng 2 để HIỂN THỊ — ⛔ đụng dữ liệu: Bảng 1 vẫn tính đủ mọi trạm ở backend.
 *
 * @param tuKhoa so theo tên trạm, ⛔ phân biệt hoa thường/dấu
 */
export function locBang2(
  bang2: readonly BcnKhoiView[],
  chiHienHoatDong: boolean,
  tuKhoa: string,
  nhap: NhapVanHanh,
): BcnKhoiView[] {
  const k = boDau(tuKhoa.trim());
  return bang2
    .map((khoi) => ({
      ...khoi,
      tram: khoi.tram.filter(
        (t) =>
          (!chiHienHoatDong || tramDangHoatDong(t, nhap)) && (k === '' || boDau(t.ten).includes(k)),
      ),
    }))
    .filter((khoi) => khoi.tram.length > 0);
}

/** Payload `PUT /van-hanh` — CHỈ ô đã đổi so với số đã lưu (⛔ gửi 830 ô cho một lần gõ). */
export function payloadVanHanh(
  bang2: readonly BcnKhoiView[],
  nhap: NhapVanHanh,
): { o: { nhomMayPublicId: string; soMayVanHanh: number | null }[] } {
  const o: { nhomMayPublicId: string; soMayVanHanh: number | null }[] = [];
  for (const khoi of bang2) {
    for (const tram of khoi.tram) {
      for (const n of tram.nhom) {
        if (n.nhomMayPublicId in nhap && (nhap[n.nhomMayPublicId] ?? null) !== n.soMayVanHanh) {
          o.push({
            nhomMayPublicId: n.nhomMayPublicId,
            soMayVanHanh: nhap[n.nhomMayPublicId] ?? null,
          });
        }
      }
    }
  }
  return { o };
}

/** Bốn ô NHẬP của một xã — ba ô "Cộng" là dẫn xuất, ⛔ có mặt ở đây. */
export interface NhapXa {
  ngapTrangLua: number | null;
  ngapTrangRau: number | null;
  sauNuocLua: number | null;
  sauNuocRau: number | null;
}

export const O_NHAP_XA: readonly (keyof NhapXa)[] = [
  'ngapTrangLua',
  'ngapTrangRau',
  'sauNuocLua',
  'sauNuocRau',
];

export function nhapXaTu(o: BcnChinO): NhapXa {
  return {
    ngapTrangLua: o.ngapTrangLua,
    ngapTrangRau: o.ngapTrangRau,
    sauNuocLua: o.sauNuocLua,
    sauNuocRau: o.sauNuocRau,
  };
}

/**
 * Payload `PUT /ngap-ung` — mỗi xã ĐÃ ĐỔI gửi ĐỦ bốn ô (backend thay toàn phần bốn ô của xã ấy).
 *
 * ⛔ Gửi một ô thiếu là XOÁ nó — hình dạng §11.19. Nên bản nháp của một xã luôn khởi từ ĐỦ bốn ô
 * đã lưu ({@link nhapXaTu}), và hàm này gửi nguyên bốn ô ấy.
 */
export function payloadNgapUng(
  bang5: readonly BcnDongBang5View[],
  nhap: Record<string, NhapXa>,
): {
  dong: ({ xaPublicId: string } & NhapXa)[];
} {
  const dong: ({ xaPublicId: string } & NhapXa)[] = [];
  for (const x of bang5) {
    const ban = nhap[x.xaPublicId];
    if (!ban) {
      continue;
    }
    const goc = nhapXaTu(x.o);
    if (O_NHAP_XA.some((k) => ban[k] !== goc[k])) {
      dong.push({ xaPublicId: x.xaPublicId, ...ban });
    }
  }
  return { dong };
}

function boDau(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
}
