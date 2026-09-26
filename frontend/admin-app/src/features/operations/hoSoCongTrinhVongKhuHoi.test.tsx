import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type ConstructionDetail, type ConstructionType } from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * **Mở hồ sơ công trình → bấm Lưu ⛔ sửa gì → payload phải mang ĐỦ mọi trường đã nạp** — T61.12
 * (đóng T42.26 · T47.17 · T48.10).
 *
 * <h2>Vì sao phải là bài render, ⛔ không phải bài HTTP</h2>
 *
 * `PUT` của hồ sơ công trình là phép **thay toàn phần**: trường nào thân yêu cầu thiếu thì CSDL ghi
 * `NULL`. §11.19 đã trả giá đúng hình dạng này ở điểm đo (xoá trắng tuyến sông/lý trình G8). Bài BE
 * *"PUT nguyên văn thân GET"* **xanh ở cả hai trạng thái**: tầng HTTP khứ hồi hoàn hảo — thứ đánh rơi
 * trường là **BIỂU MẪU** (một ô ⛔ có `Form.Item`, hoặc `setFieldsValue` quên một dòng, là AntD bỏ
 * trường ấy khỏi `onFinish`). Với 11 hồ sơ thật, một lượt Lưu là một lượt xoá.
 *
 * <h2>⛔ Danh sách trường ĐỌC từ backend, ⛔ chép tay (luật 14 · luật 29)</h2>
 *
 * Tập trường phải có lấy từ record `SaveRequest` + ba record thông số trong `ConstructionDtos.java`.
 * Chép tay danh sách vào đây là để bài kiểm canh **chính nó**: backend thêm trường thứ 26 thì biểu mẫu
 * đánh rơi nó và bài vẫn xanh. Đọc từ nguồn thì trường mới đỏ ngay.
 *
 * Giá trị kỳ vọng tra theo **một luật chung** (tên trường ở cấp gốc của `ConstructionDetail`, không có
 * thì ở `summary`) — ⛔ không chép lại phép ánh xạ của `ConstructionFormPage`, để bài ⛔ sai theo đúng
 * cách mã sai.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(
    GOC_KHO,
    'backend/operations/src/main/java/com/songnhue/operations/api/ConstructionDtos.java',
  ),
  'utf8',
);

/** Tên các thành phần của một `record <ten>(...)` — bỏ annotation và kiểu. */
function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong ConstructionDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@\w+(\([^)]*\))?/g, ' ')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Trường client ⛔ được gửi — backend chặn bằng `chanTrangThaiTuClient` (quy tắc 4). */
const CAM_GUI = new Set(['operationalStatus']);
const KHOI: Record<string, string> = {
  pump: 'PumpSpecRequest',
  sluice: 'SluiceSpecRequest',
  linear: 'LinearSpecRequest',
};

const PUMP = {
  totalPowerKw: '1250.50',
  pumpCount: 6,
  standbyPumpCount: 1,
  flowPerPumpM3s: '2.750',
  totalFlowM3s: '16.500',
  headM: '4.20',
  powerSource: 'Lưới 35kV',
  voltageKv: '35.00',
  operatingLevelMinM: '1.10',
  operatingLevelMaxM: '3.80',
};
const SLUICE = {
  // ⛔ T68.28 — giá trị cũ ở đây là `CONG_HOP`, một giá trị `ck_sluice_specs_type` ⛔ NHẬN.
  //   Bài vẫn xanh vì nó mock đường mạng, nên fixture ⛔ bao giờ chạm CSDL (luật 9).
  sluiceType: 'HOP',
  bayCount: 3,
  bayWidthM: '4.00',
  sillElevationM: '-1.50',
  crestElevationM: '6.20',
  designFlowM3s: '45.000',
  gateOperation: 'THUY_LUC',
  upstreamWarningLevelM: '4.10',
  upstreamDangerLevelM: '5.30',
};
const LINEAR = {
  lengthKm: '12.345',
  startChainage: 'K0+000',
  endChainage: 'K12+345',
  designFlowM3s: '18.000',
  crestElevationM: '7.40',
  technicalGrade: 'CAP_III',
  crossSection: 'Hình thang, B=8m, m=1.5',
  specNote: 'Kênh bê tông hoá 2019',
};

/** Hồ sơ mang GIÁ TRỊ Ở MỌI Ô — một ô `null` là một trường bài này ⛔ nhìn thấy nếu bị đánh rơi. */
function hoSo(loai: ConstructionType): ConstructionDetail {
  return {
    summary: {
      publicId: 'ct-1',
      code: 'CT-KIEMTRA',
      name: 'Công trình kiểm thử khứ hồi',
      constructionType: loai,
      managementLevel: 'CONG_TY',
      orgUnitName: 'Xí nghiệp A',
      clusterName: 'Cụm 1',
      riverName: 'Sông Nhuệ',
      chainage: 'K15+470',
      latitude: 21.048201,
      longitude: 105.7825,
      located: true,
      lifecycleState: 'DANG_HOAT_DONG',
      operationalStatus: 'BINH_THUONG',
      updatedAt: '2026-09-14T00:00:00Z',
    },
    orgUnitId: '11111111-1111-4111-8111-111111111111',
    clusterId: '22222222-2222-4222-8222-222222222222',
    purpose: 'HON_HOP',
    address: 'Xã Liên Mạc, Bắc Từ Liêm',
    chainageM: 15470,
    basinNote: 'Khu tưới Liên Mạc',
    builtYear: 1995,
    commissionedYear: 1998,
    designer: 'Viện Quy hoạch Thuỷ lợi',
    contractor: 'Công ty Xây dựng Thuỷ lợi 1',
    totalInvestment: 1500000000,
    operatingProcedureAttachmentId: '33333333-3333-4333-8333-333333333333',
    protectionPlanAttachmentId: '44444444-4444-4444-8444-444444444444',
    description: 'Mô tả đầy đủ để bài kiểm thấy nếu nó biến mất',
    pump: loai === 'TRAM_BOM' ? PUMP : null,
    sluice: loai === 'CONG' ? SLUICE : null,
    linear: loai === 'KENH_MUONG' ? LINEAR : null,
  } as unknown as ConstructionDetail;
}

const trangThai = { hoSo: hoSo('TRAM_BOM') };
const putDaGui = vi.fn();

/**
 * Mock `api`, GIỮ nguyên phần còn lại của `apiClient` — thay cả mô-đun là thay luôn lớp
 * `ApiClientError`, và nhánh lỗi theo trường của màn hình sẽ nói về một lớp khác.
 */
vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) => {
        if (url === '/ops/constructions/ct-1') return trangThai.hoSo;
        if (url === '/ops/constructions/ct-1/documents') return { items: [] };
        // Ô chọn đơn vị / cụm: danh sách rỗng vẫn giữ GIÁ TRỊ đã nạp — thứ bài này đo.
        return [];
      }),
      put: vi.fn(async (url: string, than: unknown) => {
        putDaGui(url, than);
        return trangThai.hoSo;
      }),
      post: vi.fn(async () => {
        throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
      }),
    },
  };
});

// Leaflet ⛔ chạy trong jsdom (⛔ có kích thước khung, ⛔ canvas) — và bản đồ ⛔ giữ ô nào của biểu mẫu.
vi.mock('./components/LocationPickerMap', () => ({ LocationPickerMap: () => null }));

const { ConstructionFormPage } = await import('./ConstructionFormPage');

function dung() {
  const khongDung = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
    status: 'authenticated' as const,
    user: null,
    // ⚠ CHỈ quyền sửa hồ sơ: hai tab kia (tài liệu · lịch sử sửa chữa) có truy vấn riêng, ⛔ liên quan.
    hasPermission: (ma: string) => ma === 'ops:construction:update',
    hasRole: () => true,
    maintenance: false,
    login: khongDung,
    verifyTwoFactor: khongDung,
    confirmEnrollment: khongDung,
    logout: khongDung,
    endSession: khongDung,
    reloadProfile: khongDung,
  } as unknown as AuthContextValue;

  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/van-hanh/cong-trinh/:publicId', element: <ConstructionFormPage /> },
      { path: '/van-hanh/cong-trinh', element: <div>Danh sách công trình</div> },
    ],
    { initialEntries: ['/van-hanh/cong-trinh/ct-1'] },
  );
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <RouterProvider router={router} />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  putDaGui.mockReset();
});

/** Giá trị kỳ vọng — luật chung: cấp gốc trước, rồi `summary`. */
function kyVong(chiTiet: ConstructionDetail, truong: string): unknown {
  const goc = chiTiet as unknown as Record<string, unknown>;
  if (truong in goc) return goc[truong];
  return (chiTiet.summary as unknown as Record<string, unknown>)[truong];
}

describe('Hồ sơ công trình — bấm Lưu ⛔ sửa gì thì ⛔ đánh rơi trường nào (T61.12)', () => {
  it('bộ đọc record thấy đủ trường — chặn xanh-trên-tập-rỗng', () => {
    // ⚠ Ngưỡng dưới, ⛔ đếm đúng: đúng số là thứ backend được quyền đổi; RỖNG mới là bộ đọc hỏng.
    expect(truongCuaRecord('SaveRequest').length).toBeGreaterThanOrEqual(20);
    expect(truongCuaRecord('SaveRequest')).toContain('orgUnitId');
    for (const ten of Object.values(KHOI)) {
      expect(truongCuaRecord(ten).length, ten).toBeGreaterThanOrEqual(6);
    }
  });

  it.each([
    ['TRAM_BOM', 'pump'],
    ['CONG', 'sluice'],
    ['KENH_MUONG', 'linear'],
  ] as const)(
    '%s — payload PUT mang đủ mọi trường của SaveRequest, đúng giá trị đã nạp',
    async (loai, khoi) => {
      trangThai.hoSo = hoSo(loai);
      const nguoiDung = userEvent.setup();
      dung();

      // Đợi dữ liệu THẬT đổ vào ô — ⛔ đợi "trang hiện ra" (ô rỗng cũng là trang hiện ra).
      await waitFor(() => expect(screen.getByDisplayValue('CT-KIEMTRA')).toBeInTheDocument());

      for (let i = 0; i < 3; i++) {
        await nguoiDung.click(screen.getByRole('button', { name: /Tiếp theo/ }));
      }
      await nguoiDung.click(await screen.findByRole('button', { name: /Lưu hồ sơ/ }));
      await waitFor(() => expect(putDaGui).toHaveBeenCalledTimes(1));

      const [url, than] = putDaGui.mock.calls[0] as [string, Record<string, unknown>];
      expect(url).toBe('/ops/constructions/ct-1');

      const thieu: string[] = [];
      const sai: string[] = [];
      for (const truong of truongCuaRecord('SaveRequest')) {
        if (CAM_GUI.has(truong)) continue;
        if (truong in KHOI && truong !== khoi) {
          expect(than[truong] ?? null, `${truong} ⛔ thuộc loại ${loai} ⇒ phải null`).toBeNull();
          continue;
        }
        if (than[truong] === undefined || than[truong] === null) {
          thieu.push(truong);
          continue;
        }
        if (truong === khoi) {
          const khoiGui = than[truong] as Record<string, unknown>;
          const khoiNap = kyVong(trangThai.hoSo, truong) as Record<string, unknown>;
          for (const con of truongCuaRecord(KHOI[truong])) {
            if (khoiGui[con] === undefined || khoiGui[con] === null) thieu.push(`${truong}.${con}`);
            else if (String(khoiGui[con]) !== String(khoiNap[con])) sai.push(`${truong}.${con}`);
          }
          continue;
        }
        if (String(than[truong]) !== String(kyVong(trangThai.hoSo, truong))) sai.push(truong);
      }

      expect(
        thieu,
        `Biểu mẫu ĐÁNH RƠI các trường này ⇒ PUT ghi NULL đè giá trị đang có (§11.19). ` +
          `Kiểm \`setFieldsValue\` và \`Form.Item\` của từng trường.`,
      ).toEqual([]);
      expect(sai, 'Trường gửi lên KHÁC giá trị đã nạp dù người dùng ⛔ sửa gì').toEqual([]);
      expect(than, 'operationalStatus là giá trị dẫn xuất — client ⛔ được gửi').not.toHaveProperty(
        'operationalStatus',
      );
    },
  );
});
