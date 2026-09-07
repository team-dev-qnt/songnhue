import type { Metadata } from 'next';

import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import { getSubsidiaries } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Xí nghiệp trực thuộc - Thủy lợi Sông Nhuệ',
  description:
    'Danh sách các Xí nghiệp trực thuộc Công ty: địa chỉ, điện thoại, email và Giám đốc Xí nghiệp.',
  alternates: { canonical: ROUTES.gioiThieu.xiNghiep },
};

/** Sáu cột đúng thứ tự CR-26 — bảng ở đây và bảng trong tài liệu phải đọc ra cùng một thứ. */
/**
 * ⚠⚠ 01/09/2026 — bỏ cột cuối "Điện thoại liên hệ" (số của **giám đốc**, tức của một cá nhân).
 *
 * ⛔ Cột "Điện thoại" thứ ba GIỮ NGUYÊN: đó là tổng đài của **đơn vị**. Ranh giới của cả đợt gỡ
 * này là gỡ số của người, giữ số của tổ chức.
 */
const COT = ['Tên Xí nghiệp', 'Địa chỉ', 'Điện thoại', 'Email', 'Giám đốc Xí nghiệp'];

/**
 * Giới thiệu &gt; **Xí nghiệp trực thuộc** — CR-26, bảng sáu cột.
 *
 * <p>§8 xếp trang này vào "phần còn thiếu so với bố cục đã duyệt".
 *
 * <p>⚠ <b>OI-05 còn mở</b>: tài liệu Bố cục liệt kê <b>7</b> Xí nghiệp cho mục Vận hành công
 * trình (Liên Mạc, Từ Liêm, Hà Đông, Thanh Trì, Hồng Vân, Phú Xuyên, Ứng Hoà), trong khi bộ dữ
 * liệu Danh mục công trình có <b>8</b> (thêm XNTL Nhật Tựu). Trang này không chọn hộ: nó hiện
 * đúng những đơn vị {@code XI_NGHIEP} đang có trong {@code org_units}, nên câu trả lời của
 * Công ty vào thẳng dữ liệu mà không cần lượt sửa mã nào.
 */
export default async function XiNghiepPage() {
  const rows = (await getSubsidiaries()) ?? [];

  return (
    <PageShell
      title="Xí nghiệp trực thuộc"
      description="Địa chỉ, đầu mối liên hệ và Giám đốc của từng Xí nghiệp thủy lợi trực thuộc Công ty."
      breadcrumb={[{ label: 'Giới thiệu' }, { label: 'Xí nghiệp trực thuộc' }]}
    >
      {rows.length === 0 ? (
        <EmptyBlock>
          Chưa có Xí nghiệp trực thuộc nào trong danh mục tổ chức. Danh sách được nhập ở màn hình Sơ
          đồ tổ chức của trang quản trị.
        </EmptyBlock>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-surface-border bg-white shadow-xs">
          <table className="w-full min-w-[900px] border-collapse text-sm">
            <caption className="sr-only">
              Bảng Xí nghiệp trực thuộc gồm tên, địa chỉ, điện thoại, email và Giám đốc
            </caption>
            <thead>
              <tr className="bg-brand-primaryLight text-left text-xs text-brand-primary">
                <th scope="col" className="w-14 px-4 py-3 font-bold">
                  TT
                </th>
                {COT.map((ten) => (
                  <th key={ten} scope="col" className="px-4 py-3 font-bold">
                    {ten}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-border">
              {rows.map((xn, i) => (
                <tr key={xn.code} className="align-top hover:bg-surface-bgLayout/60">
                  <td className="px-4 py-3 text-surface-textSecondary">{i + 1}</td>
                  <td className="px-4 py-3 font-semibold text-surface-textBase">{xn.name}</td>
                  <td className="px-4 py-3 text-surface-textBase">
                    <OTrong giaTri={xn.address} />
                  </td>
                  <td className="px-4 py-3">
                    <SoDienThoai so={xn.phone} />
                  </td>
                  <td className="px-4 py-3">
                    {xn.email ? (
                      <a
                        href={`mailto:${xn.email}`}
                        className="font-medium text-brand-primary hover:underline"
                      >
                        {xn.email}
                      </a>
                    ) : (
                      <Gach />
                    )}
                  </td>
                  <td className="px-4 py-3 text-surface-textBase">
                    <OTrong giaTri={xn.directorName} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <SectionNav duongDan={ROUTES.gioiThieu.xiNghiep} />
    </PageShell>
  );
}

/*
  ⛔ Ba thành phần dưới đây tồn tại để "chưa có dữ liệu" trông KHÁC "có dữ liệu".

  Một chuỗi rỗng trong ô bảng trông y hệt một ô lỗi hiển thị, và một chuỗi `null` in ra màn
  hình thì tệ hơn nữa. Dấu gạch kèm nhãn cho trình đọc màn hình nói đúng điều đang xảy ra —
  quy tắc 16 ở mức một ô bảng.
*/
function Gach() {
  return (
    <span className="text-surface-textSecondary" aria-label="Chưa có thông tin">
      —
    </span>
  );
}

function OTrong({ giaTri }: { giaTri: string | null }) {
  return giaTri ? <>{giaTri}</> : <Gach />;
}

function SoDienThoai({ so }: { so: string | null }) {
  if (!so) return <Gach />;
  return (
    <a
      href={`tel:${so.replace(/\D/g, '')}`}
      className="whitespace-nowrap font-medium text-brand-primary hover:underline"
    >
      {so}
    </a>
  );
}
