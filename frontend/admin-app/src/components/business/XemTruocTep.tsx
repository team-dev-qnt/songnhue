import { useQuery } from '@tanstack/react-query';
import { Empty, Modal, Skeleton } from 'antd';

/**
 * Xem trước một tệp trong hộp thoại — **dùng chung** cho mọi màn hình có tệp đính kèm (T84.6).
 *
 * <h2>Lai lịch: nó đã chạy 4 tháng ở đúng MỘT nơi</h2>
 *
 * Bản gốc là `XemTruocTaiLieu` trong `features/hr/HoSoConDrawer.tsx` (CN-04.5 · T53.12) — một hàm
 * **cục bộ chưa export**, khoá cứng dạng endpoint `{duong}/{id}/download-url`. CMS thì đi
 * `/cms/media/files/{id}/url`, nên chép sang là dựng bản sao thứ hai của một cơ chế đã trả giá để
 * làm đúng. ⇒ Tham số hoá đúng chỗ khác nhau: **cách lấy URL**.
 *
 * <h2>⛔⛔ Hộp thoại chỉ TỒN TẠI khi đang mở</h2>
 *
 * Nơi gọi dựng nó bằng `dangXem ? <XemTruocTep …/> : null`, nên đường dẫn có hạn của tệp trước ⛔
 * sống sót sang lượt mở sau — cơ chế tường minh, cùng khuôn đã vá T51.12 · T53.7. ⛔ Dựng sẵn rồi
 * bật/tắt bằng `open`: khi ấy `<iframe>` của tệp A còn trong DOM lúc mở tệp B.
 *
 * <h2>⚠ Đường dẫn có hạn 10 phút, và nó nằm trong DOM</h2>
 *
 * Presigned URL ⛔ đi kèm phiên đăng nhập — ai có chuỗi ấy trong 10 phút đều mở được; đó là đánh
 * đổi đã chốt của `AttachmentPort`. ⇒ `gcTime: 0` và `staleTime: 0`: một bản nằm lại trong đệm
 * react-query sẽ được dùng lại ở lượt mở sau rồi cho ra một **khung trắng ⛔ lý do**.
 */
export function XemTruocTep({
  tenHienThi,
  contentType,
  layUrl,
  khoaDem,
  onDong,
}: {
  /** Tên hiện trên tiêu đề hộp thoại và trong `alt`/`title` — thứ người dùng nhận ra. */
  tenHienThi: string;
  contentType: string | null;
  /**
   * Lấy URL có hạn. Nơi gọi quyết định đi đường nào — HR dùng `{duong}/{id}/download-url`, CMS
   * dùng `cmsApi.fileUrl`. Component này **⛔ được biết cả hai**.
   */
  layUrl: () => Promise<string>;
  /** Khoá đệm riêng từng tệp — hai tệp mở liên tiếp ⛔ đè kết quả của nhau. */
  khoaDem: readonly unknown[];
  onDong: () => void;
}) {
  const url = useQuery({
    queryKey: khoaDem,
    queryFn: layUrl,
    gcTime: 0,
    staleTime: 0,
  });

  const anh = (contentType ?? '').toLowerCase().startsWith('image/');

  return (
    <Modal open title={tenHienThi} onCancel={onDong} footer={null} width={900} destroyOnHidden>
      {url.isLoading ? (
        <Skeleton active />
      ) : url.data ? (
        anh ? (
          <img src={url.data} alt={tenHienThi} style={{ width: '100%' }} />
        ) : (
          <iframe
            src={url.data}
            title={tenHienThi}
            style={{ width: '100%', height: '70vh', border: 0 }}
          />
        )
      ) : (
        <Empty description="Không mở được tệp để xem trước" />
      )}
    </Modal>
  );
}
