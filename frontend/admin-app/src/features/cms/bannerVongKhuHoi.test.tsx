import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa banner → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 7 trường, đúng khoảnh khắc** — T47.17.
 *
 * <h2>Vì sao biểu mẫu này khác sáu cái kia</h2>
 *
 * Sáu biểu mẫu trước ánh xạ **1-1**: một ô ↔ một trường. Banner thì <b>⛔</b>: giao diện gom
 * {@code startAt} và {@code endAt} thành MỘT ô {@code RangePicker} tên {@code khoang}, rồi tách lại
 * lúc {@code onOk} qua {@code dayjs} + {@code toApiInstant}. Hai lượt biến đổi ⇒ hai chỗ một giá trị
 * trôi được mà ⛔ ai thấy:
 *
 * <ul>
 *   <li><b>Trôi múi giờ</b> — {@code dayjs(chuỗi ISO)} đọc theo giờ máy, {@code toApiInstant} ghi
 *       lại theo UTC. Lệch 7 tiếng ⇒ một banner hẹn *"hiện từ 0h ngày 20"* bật từ **17h ngày 19**.
 *   <li><b>Mất nửa khoảng</b> — nếu chỉ một trong hai đầu được nạp, phép gom có thể nuốt đầu kia,
 *       và hậu quả là banner hiện <b>vĩnh viễn</b> hoặc <b>⛔ bao giờ hiện</b> trên trang chủ.
 * </ul>
 *
 * <p>⚠ Cả hai hỏng <b>lặng</b>: biểu mẫu vẫn lưu thành công, chỉ có lịch chiếu trên cổng công khai
 * là khác thứ người quản trị vừa nhập.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const CONTROLLER = readFileSync(
  join(GOC_KHO, 'backend/content/src/main/java/com/songnhue/content/api/BannerController.java'),
  'utf8',
);

function truongCuaBannerRequest(): string[] {
  const m = /record\s+BannerRequest\s*\(([\s\S]*?)\)\s*\{/.exec(CONTROLLER);
  if (!m) throw new Error('Không tìm thấy record BannerRequest trong BannerController.java');
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Banner có ĐỦ hai đầu lịch — một đầu để trống làm bài kiểm mù trước vế *"mất nửa khoảng"*. */
const BANNER = {
  publicId: 'bn-1',
  title: 'Thông báo lịch tưới vụ Đông Xuân',
  description: 'Mô tả banner để bài kiểm thấy nếu nó biến mất',
  imageAttachmentPublicId: 'anh-1',
  linkUrl: 'https://thuyloisongnhue.vn/thong-bao',
  openNewTab: true,
  sortOrder: 10,
  active: true,
  startAt: '2026-10-20T00:00:00Z',
  endAt: '2026-11-30T17:00:00Z',
  visibleNow: true,
};

let thanCuoi: Record<string, unknown> = {};

vi.mock('./api', () => ({
  cmsKeys: { banners: () => ['cms', 'banners'] as const },
  cmsApi: {
    banners: vi.fn(async () => [BANNER]),
    updateBanner: vi.fn(async (_id: string, than: unknown) => {
      thanCuoi = than as Record<string, unknown>;
      return BANNER;
    }),
    createBanner: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    deleteBanner: vi.fn(),
    reorderBanners: vi.fn(),
    replaceBannerImage: vi.fn(),
  },
}));

const { BannersTab } = await import('./BannersTab');

function dung() {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
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
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <BannersTab />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** Ô rỗng ⇒ `undefined` là CỐ Ý (xoá ô); so theo khoảnh khắc chứ ⛔ theo chuỗi. */
function bangNhau(truong: string, thucTe: unknown): boolean {
  const mong = (BANNER as Record<string, unknown>)[truong];
  if (truong === 'startAt' || truong === 'endAt') {
    return (
      typeof thucTe === 'string' && new Date(thucTe).getTime() === new Date(String(mong)).getTime()
    );
  }
  return thucTe === mong;
}

afterEach(() => {
  cleanup();
  thanCuoi = {};
});

describe('Banner — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 7 trường của BannerRequest', () => {
    const truong = truongCuaBannerRequest();
    expect(truong.length).toBeGreaterThanOrEqual(7);
    expect(truong).toEqual(expect.arrayContaining(['startAt', 'endAt', 'linkUrl', 'active']));
  });

  it('⭐⭐ sửa banner → Lưu ⛔ đổi gì ⇒ đủ 7 trường, và lịch chiếu đúng KHOẢNH KHẮC', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await screen.findByText(BANNER.title);
    await nguoiDung.click((await screen.findAllByRole('button', { name: 'Sửa' }))[0]);
    await screen.findByDisplayValue(BANNER.title);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaBannerRequest()
      .filter((t) => !bangNhau(t, thanCuoi[t]))
      .map((t) => ({ t, kyVong: (BANNER as Record<string, unknown>)[t], thucTe: thanCuoi[t] }));

    expect(
      lech,
      '⛔ Trường bị đánh rơi hoặc TRÔI sau một lượt Lưu ⛔ sửa gì. Giao diện gom `startAt`/`endAt` ' +
        'thành một ô `RangePicker` rồi tách lại qua dayjs — hai lượt biến đổi, hai chỗ trôi được. ' +
        'Lệch múi giờ ⇒ banner hẹn "hiện từ 0h ngày 20" bật từ 17h ngày 19, và ⛔ gì báo.',
    ).toEqual([]);
  });
});
