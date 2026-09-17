import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa hồ sơ CBNV → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 24 trường** — T47.17, biểu mẫu **to
 * nhất** của cả nhóm.
 *
 * <h2>Vì sao 24 trường là một hạng rủi ro khác, ⛔ chỉ là "nhiều hơn"</h2>
 *
 * `dungPayload()` dựng payload bằng cách **liệt kê tay 24 khoá** — đúng hình dạng đã gây ra
 * `T63.8` (menu) và `T63.12` (đơn vị). Ở một biểu mẫu 6 ô, người sửa còn nhìn hết được; ở 24 ô rải
 * trên bốn khối thì một khoá thiếu **⛔ ai nhìn ra** khi đọc lại mã.
 *
 * <p>Và hậu quả là hồ sơ nhân sự: quê quán, dân tộc, người liên hệ khẩn cấp, ngày ký hợp đồng —
 * thứ nhập MỘT LẦN lúc lập hồ sơ rồi ⛔ ai mở lại. Người sửa hôm nay chỉ định đổi số điện thoại;
 * nếu một khoá rơi khỏi payload thì `EmployeeService.apDung` ghi đè ô ấy bằng `null`, **⛔ một dòng
 * lỗi nào**, và ⛔ ai đếm được đã mất gì. NĐ 13/2023 gọi tên đúng nhóm dữ liệu này.
 *
 * <h2>Hai ô có điều kiện — ngoại lệ CÓ TÊN</h2>
 *
 * `terminatedAt` và `terminationReason` chỉ render khi trạng thái thuộc nhóm *đã nghỉ*, và
 * `dungPayload` **cố ý** ép chúng về `null` ở các trạng thái khác (ô ẩn của AntD vẫn giữ giá trị cũ
 * trong kho ⇒ tổ hợp bị `ck_employees_terminated_pairs` từ chối). ⇒ Hồ sơ thử dùng trạng thái
 * **NGHỈ VIỆC** để cả hai ô cùng có mặt: một hồ sơ *đang làm* làm bài mù trước đúng hai trường ấy.
 *
 * <h2>Vế thứ hai: ⛔ được trộn hai con người</h2>
 *
 * Đây chính là màn hình T51.12 đã xảy ra lần ĐẦU (hộp thoại 🔒 hiện CCCD của người A dưới tên B).
 * Vế A → B ở đây khoá lại bản vá ấy: đổi `publicId` mà ⛔ đóng trang là đúng luồng người dùng đi
 * khi họ sửa lần lượt nhiều hồ sơ.
 *
 * <h2>⚠ Phạm vi vế ấy — ĐO ĐƯỢC, ⛔ suy đoán</h2>
 *
 * Lượt phá thử cho ra hai kết quả khác nhau, và chúng nói ra chính xác bài này canh cái gì:
 *
 * <ul>
 *   <li>gỡ <b>{@code clearOnDestroy}</b> khỏi {@code BieuMauHoSo} ⇒ bài <b>ĐỎ</b> (cùng với hai bài
 *       của {@code hoSoKhongTronDuLieu.test.tsx}) — tức nó bắt đúng cơ chế đang giữ bảo đảm;
 *   <li>gỡ <b>{@code key={publicId}}</b> ⇒ bài vẫn <b>XANH</b>. ⛔ Vì `key` thừa, mà vì ở màn hình
 *       này có một lượt unmount THỨ HAI che nó: đổi hồ sơ làm {@code chiTiet.data} về `undefined`
 *       trong lúc tải ⇒ {@code sanSang} false ⇒ cả biểu mẫu bị tháo, và {@code clearOnDestroy} dọn
 *       kho ở đúng lượt ấy.
 * </ul>
 *
 * <p>⇒ **⛔ đọc cái xanh của bài này thành *"`key` còn tác dụng"***. Nếu ai đó bỏ luôn vế skeleton
 * (cho biểu mẫu sống suốt lượt tải) thì `key` trở lại thành cơ chế duy nhất, và lúc ấy bài này mới
 * phân biệt được nó. Một bộ canh phải nói ra phạm vi của chính nó (luật 28).
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hr/src/main/java/com/songnhue/hr/api/HrDtos.java'),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong HrDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Hồ sơ **đã nghỉ việc**, giá trị ở MỌI ô — xem javadoc: ô trống làm bài mù trước chính thứ nó canh. */
const NV_A = {
  publicId: 'nv-1',
  code: 'CB001',
  fullName: 'Nguyễn Văn An',
  dateOfBirth: '1985-04-12',
  gender: 'NAM',
  educationLevel: 'DAI_HOC',
  ethnicity: 'Kinh',
  hometown: 'Xã Tân Ước, huyện Thanh Oai, Hà Nội',
  address: 'Số 12 ngõ 5 Quang Trung, Hà Đông, Hà Nội',
  phone: '0912345678',
  workEmail: 'an.nv@thuyloisongnhue.vn',
  personalEmail: 'anvn1985@gmail.com',
  maritalStatus: 'DA_KET_HON',
  emergencyContactName: 'Trần Thị Bình',
  emergencyContactPhone: '0987654321',
  orgUnitId: 'dv-1',
  orgUnitName: 'Xí nghiệp Thuỷ lợi Hà Đông',
  positionId: 'cv-1',
  positionName: 'Kỹ thuật viên',
  jobTitle: 'Phụ trách cụm cống Đồng Quan',
  hiredAt: '2010-03-01',
  contractType: 'KHONG_XAC_DINH',
  contractSignedAt: '2010-03-01',
  contractExpiresAt: '2026-03-01',
  status: 'NGHI_VIEC',
  terminatedAt: '2026-08-31',
  terminationReason: 'Nghỉ hưu theo chế độ',
  sensitive: { daCauHinh: false, capNhatLuc: null },
};

const NV_B = {
  ...NV_A,
  publicId: 'nv-2',
  code: 'CB002',
  fullName: 'Lê Thị Hạnh',
  dateOfBirth: '1992-11-03',
  gender: 'NU',
  ethnicity: 'Mường',
  hometown: 'Xã Kim An, huyện Thanh Oai, Hà Nội',
  address: 'Số 4 phố Ngô Quyền, Hà Đông, Hà Nội',
  phone: '0934567890',
  workEmail: 'hanh.lt@thuyloisongnhue.vn',
  personalEmail: 'hanhlt92@gmail.com',
  emergencyContactName: 'Lê Văn Cường',
  emergencyContactPhone: '0901234567',
  jobTitle: 'Cán bộ kỹ thuật tuyến kênh N4',
  terminationReason: 'Chuyển công tác theo nguyện vọng',
};

const CHUC_VU = [
  { publicId: 'cv-1', code: 'KTV', name: 'Kỹ thuật viên', active: true },
  { publicId: 'cv-2', code: 'CV', name: 'Chuyên viên', active: true },
];

const CAY_DON_VI = [
  {
    publicId: 'dv-1',
    code: 'XN01',
    name: 'Xí nghiệp Thuỷ lợi Hà Đông',
    shortName: null,
    unitType: 'XI_NGHIEP',
    path: '/dv-1',
    depth: 0,
    sortOrder: 0,
    active: true,
    address: null,
    phone: null,
    email: null,
    children: [],
  },
];

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hr/employees/nv-1') return NV_A;
      if (duong === '/hr/employees/nv-2') return NV_B;
      if (duong === '/hr/positions') return CHUC_VU;
      if (duong === '/org-units/selectable') return CAY_DON_VI;
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return NV_A;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { EmployeeFormModal } = await import('./EmployeeFormModal');

function authGia(): AuthContextValue {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  return {
    status: 'authenticated' as const,
    user: null,
    hasPermission: () => true,
    hasRole: () => true,
    maintenance: false,
    login: chuaKhai,
    verifyTwoFactor: chuaKhai,
    confirmEnrollment: chuaKhai,
    logout: chuaKhai,
    endSession: chuaKhai,
    reloadProfile: chuaKhai,
  } as unknown as AuthContextValue;
}

/**
 * ⚠⚠ Cây phải dựng **một lần** rồi chỉ đổi `publicId` — ⛔ dựng lại `QueryClientProvider` mới.
 *
 * Bản đầu của bài này dựng cây mới ở lượt `rerender`, và lượt phá thử **bác nó**: gỡ hẳn
 * {@code key={publicId}} khỏi `EmployeeFormModal` mà bài vẫn **XANH**, vì provider mới làm cả cây
 * unmount ⇒ `Form.useForm()` cũng dựng lại ⇒ bài đang đo một thứ **⛔ phải** cơ chế nó định canh
 * (luật 9 + luật 1). Giữ nguyên provider và chỉ đổi prop mới là đúng luồng người dùng: nơi gọi
 * render hộp thoại vô điều kiện và chỉ thay `publicId` khi họ mở hồ sơ khác.
 */
function cay(publicId: string, qc: QueryClient, auth: AuthContextValue) {
  return (
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <EmployeeFormModal open publicId={publicId} onClose={() => {}} onSaved={() => {}} />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

function dung(publicId: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const auth = authGia();
  const ket = render(cay(publicId, qc, auth));
  return { ...ket, doiHoSo: (moi: string) => ket.rerender(cay(moi, qc, auth)) };
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Hồ sơ CBNV — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 24 trường của EmployeeRequest', () => {
    const truong = truongCuaRecord('EmployeeRequest');
    expect(truong.length).toBeGreaterThanOrEqual(24);
    expect(truong).toEqual(
      expect.arrayContaining([
        'ethnicity',
        'hometown',
        'emergencyContactName',
        'contractSignedAt',
        'terminatedAt',
      ]),
    );
  });

  it('⭐⭐ sửa hồ sơ → Lưu ⛔ đổi gì ⇒ cả 24 trường quay lại nguyên vẹn', async () => {
    const nguoiDung = userEvent.setup();
    dung('nv-1');

    await screen.findByDisplayValue(NV_A.fullName);
    await screen.findByDisplayValue(NV_A.hometown);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('EmployeeRequest')
      .map((t) => ({ t, kyVong: (NV_A as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `dungPayload()` liệt kê tay 24 khoá — một ' +
        'danh sách gõ tay ⛔ tự biết nó thiếu gì (T63.8 · T63.12). Quê quán, dân tộc, người liên hệ ' +
        'khẩn cấp là thứ nhập MỘT LẦN rồi ⛔ ai mở lại, nên mất là mất im lặng.',
    ).toEqual([]);

    expect(duongCuoi).toBe(`/hr/employees/${NV_A.publicId}`);
  });

  it('⭐⭐ đang sửa hồ sơ A → chuyển sang hồ sơ B ⇒ ⛔ ô nào còn dữ liệu của A', async () => {
    const nguoiDung = userEvent.setup();
    const { doiHoSo } = dung('nv-1');
    await screen.findByDisplayValue(NV_A.fullName);

    doiHoSo('nv-2');

    await screen.findByDisplayValue(NV_B.fullName);
    for (const oCuaA of [NV_A.fullName, NV_A.hometown, NV_A.phone, NV_A.emergencyContactName]) {
      expect(
        screen.queryByDisplayValue(oCuaA),
        `⛔ Ô còn mang dữ liệu của hồ sơ TRƯỚC ("${oCuaA}") — đây đúng màn hình T51.12 đã xảy ra ` +
          'lần đầu, và một lượt Lưu sẽ ghi hồ sơ người này lên người kia.',
      ).toBeNull();
    }

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi).toBe(`/hr/employees/${NV_B.publicId}`);
    expect(thanCuoi).toMatchObject({
      code: NV_B.code,
      fullName: NV_B.fullName,
      hometown: NV_B.hometown,
      phone: NV_B.phone,
      emergencyContactName: NV_B.emergencyContactName,
      terminationReason: NV_B.terminationReason,
    });
  });
});
