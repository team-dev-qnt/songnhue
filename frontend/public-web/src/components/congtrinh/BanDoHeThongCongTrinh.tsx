import type { UnitCatalog } from '@/lib/api';
import { PortalImage } from '@/components/PortalImage';
import { fileUrl } from '@/lib/routes';
import type { DiemCongTrinh } from './ConstructionMap';
import { ConstructionMapLoader } from './ConstructionMapLoader';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import { SectionTitle } from '@/components/home/SectionTitle';

/**
 * Khối **BẢN ĐỒ HỆ THỐNG CÔNG TRÌNH** — CN-02.4 · CR-29.
 *
 * <h2>⭐⭐ 01/09: khối này RỜI TRANG CHỦ, về Danh mục công trình</h2>
 *
 * Yêu cầu QuanTran: *"Bản đồ hệ thống công trình hiện tại đã có trong danh mục công trình,
 * phần quản lý vận hành. Do đó, move phần display map khỏi homepage và đưa vào luôn trong
 * phần danh mục công trình."*
 *
 * <p>Và nó đúng ở một chỗ sâu hơn thẩm mỹ: trang {@code /quan-ly-van-hanh/danh-muc-cong-trinh}
 * đã có sẵn một mục *"Bản đồ hệ thống"* (CR-29) nói *chưa được đăng*, trong khi trang chủ đang
 * hiện đúng cái bản đồ ấy. **Hai chỗ trả lời khác nhau về cùng một câu hỏi** — đúng thứ quy tắc
 * 14 cấm, và là thứ sẽ lệch tiếp ngay lần đầu ai đó sửa một bên.
 *
 * <p>⚠ Tên tệp đổi theo ({@code HomeConstructionMap} → {@code BanDoHeThongCongTrinh}) và tệp
 * chuyển khỏi {@code components/home/}. Giữ nguyên tiền tố {@code Home} cho một khối không còn
 * ở trang chủ là để lại một cái bẫy đọc-hiểu cho lượt rà sau.
 *
 * <h2>⭐ Hai nguồn, và chúng KHÔNG loại trừ nhau</h2>
 *
 * <ol>
 *   <li><b>Ảnh sơ đồ</b> Công ty tải lên ở màn hình Cấu hình giao diện
 *       ({@code site.home.map-image.attachment-id}). Đây là thứ Công ty <i>đã có sẵn</i> — một
 *       tấm sơ đồ hệ thống thuỷ nông, treo lên được ngay.
 *   <li><b>Bản đồ tương tác</b> vẽ từ toạ độ trong danh mục công trình. Toạ độ thuộc <b>G8</b>,
 *       Công ty chưa cung cấp, nên hôm nay danh sách điểm là rỗng.
 * </ol>
 *
 * <p>Có cả hai thì hiện cả hai: ảnh là <i>sơ đồ tổng thể</i> (tuyến sông, quan hệ giữa các công
 * trình — thứ một lớp marker không nói được), bản đồ là <i>vị trí tra cứu được</i>. Chúng trả
 * lời hai câu hỏi khác nhau, nên chọn một cái để giấu cái kia là mất thông tin.
 *
 * <p>⛔ Và ô này <b>không</b> được rỗng chỉ vì G8 chưa về: trước 29/08 nó rỗng ở mọi môi trường
 * và câu giải thích duy nhất là "chờ dữ liệu" — trong khi thứ chặn thật sự chỉ là <i>chưa có ô
 * nào để Công ty tải ảnh lên</i>. Đó là quy tắc 15 ở dạng đắt nhất: một khối hoàn chỉnh nằm
 * chờ một đường nhập liệu không tồn tại.
 *
 * <h2>⛔ Không có điểm nào thì KHÔNG dựng bản đồ tương tác</h2>
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
interface BanDoHeThongCongTrinhProps {
  catalog: UnitCatalog[];
  /** Ảnh sơ đồ hệ thống — `site.home.map-image.attachment-id`. Rỗng là trạng thái hợp lệ. */
  anhSoDo?: string | null;
}

export function BanDoHeThongCongTrinh({ catalog, anhSoDo }: BanDoHeThongCongTrinhProps) {
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

  const anhSoDoUrl = fileUrl(anhSoDo ?? null);

  return (
    <section className="mt-5">
      {/* ⚠ 01/09: `href` và ô `phụ` ĐÃ GỠ. Cả hai trỏ về `danh-muc-cong-trinh` — trang mà khối
          này nay đứng trên đó. Một liên kết tự trỏ về chính trang đang xem không phải lỗi hiển
          thị, nó là một lối đi cụt: người dùng bấm, không thấy gì xảy ra, và bấm tiếp. Đây cũng
          là mảnh dễ sót nhất khi chuyển một khối sang trang khác. */}
      <SectionTitle>Bản đồ hệ thống công trình</SectionTitle>

      <div className="mt-5 space-y-6">
        {/* Ảnh sơ đồ đứng TRƯỚC bản đồ tương tác: nó là cái nhìn tổng thể, và hôm nay nó là thứ
            duy nhất có nội dung.

            ⚠ `phuKhung={false}` — sơ đồ là bản vẽ kỹ thuật. Cắt nó cho vừa khung (`object-cover`,
              mặc định của `PortalImage`) là cắt mất một đoạn tuyến sông, và không ai biết đoạn
              nào vừa mất. Chừa nền hai bên là cái giá đúng để trả. */}
        {anhSoDoUrl ? (
          <figure className="overflow-hidden rounded-lg border border-surface-border bg-white p-2 shadow-xs">
            <PortalImage
              src={anhSoDoUrl}
              alt="Sơ đồ hệ thống công trình thuỷ lợi Sông Nhuệ"
              ratio="aspect-[16/9]"
              phuKhung={false}
            />
          </figure>
        ) : null}

        {diem.length === 0 ? (
          <EmptyBlock>
            {anhSoDoUrl
              ? 'Bản đồ tương tác chưa vẽ được điểm nào: tuyến sông, lý trình và toạ độ công trình thuộc nhóm dữ liệu Công ty chưa cung cấp (G8) — cột “Vị trí” của bảng phía trên cũng vì thế còn trống. Ảnh sơ đồ phía trên là bản Công ty đã tải lên.'
              : 'Chưa có gì để hiển thị. Hai đường đưa nội dung vào ô này: tải ảnh sơ đồ hệ thống ở màn hình Cấu hình giao diện (mục “Ảnh sơ đồ hệ thống công trình”), hoặc nhập toạ độ cho danh mục công trình (G8) để bản đồ tương tác tự hiện.'}
          </EmptyBlock>
        ) : (
          <>
            <ConstructionMapLoader diem={diem} />
            <p className="mt-2.5 text-xs text-surface-textSecondary">
              {diem.length} công trình có toạ độ — trên tổng số{' '}
              {catalog.reduce((tong, dv) => tong + (dv.constructions?.length ?? 0), 0)} công trình
              trong bảng phía trên.
            </p>
          </>
        )}
      </div>
    </section>
  );
}
