import Link from 'next/link';

import type { UnitCatalog } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import type { DiemCongTrinh } from './ConstructionMap';
import { ConstructionMapLoader } from './ConstructionMapLoader';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

/**
 * Khối **BẢN ĐỒ HỆ THỐNG CÔNG TRÌNH** trên trang chủ — CN-02.4.
 *
 * <h2>⛔ Không có điểm nào thì KHÔNG dựng bản đồ</h2>
 *
 * Toạ độ công trình thuộc **G8** và Công ty chưa cung cấp, nên hôm nay danh sách điểm là rỗng.
 * Rỗng thì khối nói thẳng lý do — và quan trọng hơn, {@link ConstructionMapLoader} không được
 * vẽ, nên Leaflet không tải. Dựng một bản đồ trống rồi bảo "chờ dữ liệu" là trả bằng cân nặng
 * trang chủ để hiển thị đúng con số không.
 *
 * <p>Ngày G8 về thì khối tự sống dậy: không có công tắc nào phải bật, không có deploy nào phải
 * chờ. Đó là ràng buộc thật sự của khối này — số điểm quyết định, không phải một cờ cấu hình.
 *
 * <h2>Lọc ở đây, không lọc trong component bản đồ</h2>
 *
 * `latitude`/`longitude` là chuỗi và cho phép `null` (một công trình có hồ sơ nhưng chưa đo
 * toạ độ là chuyện bình thường). Chuyển đổi và loại bỏ dòng hỏng nằm ở tầng này để component
 * bản đồ chỉ nhận số đã sạch — nó không phải biết dữ liệu từng thiếu thế nào.
 */
export function HomeConstructionMap({ catalog }: { catalog: UnitCatalog[] }) {
  const diem: DiemCongTrinh[] = catalog.flatMap((donVi) =>
    // `?? []`: một Xí nghiệp chưa có công trình nào có thể về mà không kèm mảng. Đọc thẳng
    // `.flatMap` trên `undefined` là ném lỗi ở phía máy chủ và **cả trang chủ trắng** — một
    // khối rỗng không được phép kéo theo cả trang.
    (donVi.constructions ?? []).flatMap((ct) => {
      const lat = Number(ct.latitude);
      const lng = Number(ct.longitude);
      // ⚠ `Number(null)` là 0, không phải NaN — kiểm `null` TRƯỚC. Thiếu bước này thì mọi công
      //   trình chưa có toạ độ rơi xuống đảo Null ở vịnh Guinea, và bản đồ trông như có dữ liệu.
      if (ct.latitude === null || ct.longitude === null) return [];
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) return [];
      return [{ code: ct.code, name: ct.name, unitName: donVi.unitName, lat, lng }];
    }),
  );

  return (
    <section className="mt-5">
      <SectionTitle
        href={ROUTES.quanLyVanHanh.danhMucCongTrinh}
        phu={
          <Link
            href={ROUTES.quanLyVanHanh.danhMucCongTrinh}
            className="text-xs font-semibold text-brand-primary hover:underline"
          >
            Danh mục công trình ➔
          </Link>
        }
      >
        Bản đồ hệ thống công trình
      </SectionTitle>

      <div className="mt-5">
        {diem.length === 0 ? (
          <EmptyBlock>
            Chưa vẽ được điểm nào trên bản đồ: tuyến sông, lý trình và toạ độ công trình thuộc nhóm
            dữ liệu Công ty chưa cung cấp (G8). Bản đồ sẽ tự hiện khi danh mục công trình có toạ độ
            — không cần bật công tắc nào.
          </EmptyBlock>
        ) : (
          <>
            <ConstructionMapLoader diem={diem} />
            <p className="mt-2.5 text-xs text-surface-textSecondary">
              {diem.length} công trình có toạ độ. Danh sách đọc được ở{' '}
              <Link
                href={ROUTES.quanLyVanHanh.danhMucCongTrinh}
                className="font-semibold text-brand-primary hover:underline"
              >
                Danh mục công trình
              </Link>
              .
            </p>
          </>
        )}
      </div>
    </section>
  );
}
