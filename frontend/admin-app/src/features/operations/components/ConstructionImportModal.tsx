import { ImportModal } from '@/components/business/ImportModal';

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * Nhập danh mục công trình — **G8**.
 *
 * ⭐ Toàn bộ hành vi nằm ở {@link ImportModal} dùng chung; ở đây chỉ còn phần **riêng của danh mục
 * công trình**: ba đường dẫn API và câu mô tả. Trước 09/09/2026 tệp này là 200 dòng giao diện viết
 * riêng, và mọi màn hình nhập tiếp theo sẽ phải chép lại nó.
 */
export function ConstructionImportModal({ open, onClose }: Props) {
  return (
    <ImportModal
      open={open}
      onClose={onClose}
      title="Nhập danh mục công trình từ tệp bảng tính"
      moTa="Nhập danh sách hồ sơ công trình từ tệp bảng tính."
      duongDan={{
        xemTruoc: '/ops/constructions/import/preview',
        nhap: '/ops/constructions/import',
        mau: '/ops/constructions/import/template',
      }}
      tenTepMau="mau-nhap-danh-muc-cong-trinh.csv"
      khoaCanLamMoi={['ops', 'constructions']}
    />
  );
}

export default ConstructionImportModal;
