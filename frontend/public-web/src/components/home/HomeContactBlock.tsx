import Link from 'next/link';

import { ContactForm } from '@/components/ContactForm';
import { ROUTES } from '@/lib/routes';
import { SectionTitle } from './SectionTitle';

/**
 * Khối **GỬI Ý KIẾN TỚI CÔNG TY** trên trang chủ — CN-01.4.
 *
 * <h2>⭐ 29/08: khối bỏ hẳn cột thông tin liên hệ, biểu mẫu chiếm trọn bề rộng</h2>
 *
 * Bản trước chia 7/5: biểu mẫu bên trái, "Trụ sở &amp; đầu mối liên hệ" bên phải (địa chỉ, điện
 * thoại, thư điện tử, giờ làm việc, thẻ trực ban). Công ty yêu cầu dồn <b>toàn bộ</b> thông tin
 * ấy về trang {@link ROUTES#lienHe} — và yêu cầu ấy sửa một lỗi có thật chứ không chỉ là sở
 * thích bố cục: cùng bộ khoá {@code company.*} đang hiển thị ở <b>ba</b> nơi (chân trang, khối
 * này, trang Liên hệ). Ba nơi đọc một nguồn thì không sai dữ liệu, nhưng người đọc phải tự
 * đoán chỗ nào là chỗ đầy đủ — và mỗi lần thêm một trường (fax, số máy lẻ, đầu mối từng Xí
 * nghiệp) lại phải nhớ sửa mấy chỗ (luật 14 ở dạng bố cục).
 *
 * <p>Nay: một chỗ đầy đủ (trang Liên hệ), một lối vào ngắn ở đây. Khối này chỉ còn biểu mẫu —
 * và biểu mẫu ấy <b>không nhận props nào</b>, nên không còn khả năng lệch với trang Liên hệ.
 *
 * <h2>Cùng một endpoint với trang Liên hệ, không phải đường thứ hai</h2>
 *
 * {@link ContactForm} dùng chung cho cả trang chủ lẫn {@code /lien-he}. Dựng hai biểu mẫu gọi
 * cùng một API là hai nơi phải nhớ luật kiểm tra giống nhau — và sớm muộn một bên sẽ quên cập
 * nhật khi CN-01.4 dựng nốt phần captcha.
 */
export function HomeContactBlock() {
  return (
    <section className="mt-5">
      <SectionTitle
        href={ROUTES.lienHe}
        phu={
          <Link
            href={ROUTES.lienHe}
            className="text-xs font-semibold text-brand-primary hover:underline"
          >
            Địa chỉ, điện thoại, bản đồ trụ sở ➔
          </Link>
        }
      >
        Gửi ý kiến tới Công ty
      </SectionTitle>

      <div className="mt-5 rounded-lg border border-surface-border bg-white p-5 shadow-xs sm:p-6">
        <ContactForm />
      </div>
    </section>
  );
}
