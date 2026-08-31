import { useQuery } from '@tanstack/react-query';
import { Select } from 'antd';

import { type ClusterView } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

/**
 * Ô chọn cụm công trình — CN-02.1 (G15: cụm là **bảng riêng**, không phải một cột trên
 * `constructions`).
 *
 * <h2>Vì sao component này ra đời ngày 31/08/2026</h2>
 *
 * <p>Trước đó ô "Cụm công trình" trong biểu mẫu hồ sơ là một `<Input>` với placeholder
 * <i>"Nhập ID cụm (tạm thời)"</i>, kèm chú thích tiếng Anh duy nhất còn sót trong `admin-app`:
 * <i>"Will need a ClusterSelect if implemented"</i>. Nhưng backend nhận `clusterId` kiểu
 * <b>UUID</b> — nghĩa là ô ấy yêu cầu người vận hành **gõ tay một UUID 36 ký tự**. Bốn endpoint
 * quản lý cụm có đủ từ WS-17 và <b>không lời gọi nào</b> từ giao diện: nửa cặp đọc–ghi, luật 27.
 *
 * <p>⚠ Danh sách cụm đứng sau `ops:construction:view` — cùng quyền với biểu mẫu chứa nó, nên
 * không lặp lại lỗi §10.36 (ô chọn phụ trợ đòi một quyền mà vai trò sở hữu biểu mẫu không có).
 *
 * <p>⬜ Chưa có màn hình quản lý cụm (thêm/sửa/xoá) — ba endpoint ghi vẫn chưa ai gọi. Ghi nợ ở
 * `master-tracking.md`; ô chọn này chỉ đóng vế **dùng** cụm, không đóng vế **tạo** cụm.
 */
export function ClusterSelect({
  value,
  onChange,
  placeholder = 'Chọn cụm công trình',
  disabled,
  allowClear = true,
}: {
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'construction-clusters'],
    queryFn: () => api.get<ClusterView[]>('/ops/construction-clusters'),
    // Cụm đổi vài lần một năm — tải lại mỗi lần mở ô chọn là phí (cùng lý lẽ `OrgUnitTreeSelect`).
    staleTime: 10 * 60 * 1000,
  });

  return (
    <Select
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      disabled={disabled}
      allowClear={allowClear}
      loading={isLoading}
      showSearch
      optionFilterProp="label"
      // ⛔ Chưa có cụm nào thì nói thẳng là chưa có, đừng để ô rỗng trông như đang tải.
      notFoundContent={isLoading ? 'Đang tải…' : 'Chưa khai báo cụm công trình nào'}
      options={(data ?? [])
        .filter((cum) => cum.active)
        .map((cum) => ({
          // Giá trị gửi lên là `publicId` — backend nhận `UUID`, và mọi tra cứu ra ngoài đi bằng
          // `public_id` chứ không bằng khoá số (§9.4, chống IDOR).
          value: cum.publicId,
          label: `${cum.code} — ${cum.name}`,
        }))}
    />
  );
}
