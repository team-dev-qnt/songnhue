import type { Metadata } from 'next';

import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { getCompanyLeaders } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Lãnh đạo Công ty - Thủy lợi Sông Nhuệ',
  description: 'Danh sách lãnh đạo Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ.',
  alternates: { canonical: ROUTES.gioiThieu.lanhDao },
};

/**
 * Giới thiệu &gt; **Lãnh đạo Công ty** — CR-25, bảng ba cột.
 *
 * <p>§8 xếp trang này vào danh sách "phần còn thiếu so với bố cục đã duyệt" — nó chưa từng tồn
 * tại trên bản dev.
 *
 * <h2>⛔ Nguồn là danh bạ công bố, KHÔNG phải hồ sơ nhân sự</h2>
 *
 * Dữ liệu đến từ {@code org_unit_leaders} — bảng chỉ chứa tên, chức danh và số máy công vụ mà
 * Công ty chủ động công bố. Nó <b>không</b> nối vào {@code employees} của MOD-04, nên endpoint
 * công khai đứng sau trang này không có đường nào chạm tới trường nhạy cảm (quy tắc 10,
 * NĐ 13/2023). Đó là lý do có một bảng riêng thay vì đọc hồ sơ nhân sự và lọc bớt cột: một
 * phép lọc là thứ có thể quên, còn một bảng không chứa dữ liệu nhạy cảm thì không rò được.
 *
 * <p>⛔ Không seed dòng nào — tên người thật và số điện thoại thật phải do Công ty nhập.
 */
export default async function LanhDaoPage() {
  const leaders = (await getCompanyLeaders()) ?? [];

  return (
    <PageShell
      title="Lãnh đạo Công ty"
      description="Họ và tên, chức danh và điện thoại liên hệ của Ban lãnh đạo Công ty."
      breadcrumb={[{ label: 'Giới thiệu' }, { label: 'Lãnh đạo Công ty' }]}
    >
      {leaders.length === 0 ? (
        <EmptyBlock>
          Danh sách lãnh đạo chưa được nhập. Nội dung này do Công ty nhập ở màn hình Sơ đồ tổ chức
          của trang quản trị và được công bố nguyên văn ra cổng.
        </EmptyBlock>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-surface-border bg-white shadow-xs">
          <table className="w-full min-w-[560px] border-collapse text-sm">
            <caption className="sr-only">
              Bảng lãnh đạo Công ty gồm họ và tên, chức danh, điện thoại liên hệ
            </caption>
            <thead>
              <tr className="bg-brand-primaryLight text-left text-xs text-brand-primary">
                <th scope="col" className="w-16 px-4 py-3 font-bold">
                  TT
                </th>
                <th scope="col" className="px-4 py-3 font-bold">
                  Họ và tên
                </th>
                <th scope="col" className="px-4 py-3 font-bold">
                  Chức danh
                </th>
                <th scope="col" className="px-4 py-3 font-bold">
                  Điện thoại liên hệ
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-border">
              {leaders.map((người, i) => (
                <tr key={`${người.fullName}-${i}`} className="hover:bg-surface-bgLayout/60">
                  <td className="px-4 py-3 text-surface-textSecondary">{i + 1}</td>
                  <td className="px-4 py-3 font-semibold text-surface-textBase">
                    {người.fullName}
                  </td>
                  <td className="px-4 py-3 text-surface-textBase">{người.title}</td>
                  <td className="px-4 py-3">
                    {/* ⛔ Chưa công bố số thì hiện dấu gạch, KHÔNG hiện số của người khác và
                        không hiện một chuỗi rỗng trông như lỗi hiển thị (quy tắc 16). */}
                    {người.phone ? (
                      <a
                        href={`tel:${người.phone.replace(/\D/g, '')}`}
                        className="font-medium text-brand-primary hover:underline"
                      >
                        {người.phone}
                      </a>
                    ) : (
                      <span className="text-surface-textSecondary" aria-label="Chưa công bố">
                        —
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </PageShell>
  );
}
