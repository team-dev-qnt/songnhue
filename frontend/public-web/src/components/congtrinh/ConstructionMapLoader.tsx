'use client';

import dynamic from 'next/dynamic';

import type { DiemCongTrinh } from './ConstructionMap';

/**
 * Ranh giới tách gói cho {@link ConstructionMap}.
 *
 * <p>Tệp này tồn tại vì một lý do kỹ thuật duy nhất: {@code ssr: false} chỉ khai được trong
 * Client Component. Khối trên trang chủ là Server Component, nên nó cần một lớp mỏng ở giữa.
 *
 * <p>Đổi lại: Leaflet nằm trong một gói RIÊNG, chỉ tải khi khối này được vẽ. Nhập thẳng
 * {@code ConstructionMap} từ phía máy chủ thì thư viện vào gói của tuyến đường ngay cả những
 * lượt render không có điểm nào — tức mọi lượt, cho tới khi G8 về.
 */
const BanDo = dynamic(() => import('./ConstructionMap'), {
  ssr: false,
  loading: () => (
    <div className="h-[340px] w-full animate-pulse rounded-lg border border-surface-border bg-surface-bgLayout" />
  ),
});

export function ConstructionMapLoader({ diem }: { diem: DiemCongTrinh[] }) {
  return <BanDo diem={diem} />;
}
