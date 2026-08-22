import { ConfigProvider, theme } from 'antd';
import { statusColors, wallColors } from 'design-tokens';
import { useEffect, useState, type ReactNode } from 'react';

import { formatDateTime } from '@/shared/format';

/**
 * Khung chế độ màn hình lớn phòng điều hành — T23.10, CN-02.5.
 *
 * <h3>⛔ Co giãn thật, không phải hai bộ bố cục</h3>
 *
 * Thiết bị đã chốt (B8) là **TV 85 inch 4K**, kèm khả năng có máy chiếu 2K/Full-HD. Cách
 * làm sai mà dễ rơi vào là thiết kế riêng cho 3840×2160 rồi thêm một bản "cho laptop":
 * hai bộ bố cục thì mọi thay đổi phải nhớ làm hai lần, và bản bị quên luôn là bản không
 * ai mở hằng ngày — tức là bản treo trên tường phòng trực.
 *
 * <p>Nên **một** cây component, cỡ chữ đi bằng `clamp(nhỏ nhất, vw, lớn nhất)` và số cột
 * đi bằng {@code boCucTheoBeRong}. `vw` cho chữ lớn dần theo màn hình; cận dưới của
 * `clamp` giữ cho nó vẫn đọc được trên laptop; cận trên chặn không cho chữ phình quá khổ
 * ở 4K tới mức một ô KPI chiếm hết chiều cao.
 *
 * <h3>Tự chuyển khối — và vì sao chỉ CUỘN chứ không đổi trang</h3>
 *
 * CN-02.5 đòi auto-rotate. Cách thường thấy là thay hẳn nội dung theo chu kỳ, nhưng ở
 * phòng trực điều đó có nghĩa là **có những phút không nhìn thấy được số sự cố** — trong
 * khi đó chính là con số người ta treo màn hình lên để nhìn. Ở đây khối KPI luôn nằm
 * trên, chỉ phần dưới cuộn qua lại. Không mất khối nào, và mắt vẫn có chuyển động để
 * không "chết" trên một khung hình tĩnh.
 *
 * <h3>Không thao tác chuột/bàn phím</h3>
 *
 * Màn hình treo tường không có ai ngồi bấm. Con trỏ bị khoá bằng `pointer-events: none`
 * để một cú chạm vô tình không kéo bản đồ đi rồi để nguyên như thế cả ngày.
 */
export function WallFrame({
  capNhatLuc,
  rotateSeconds,
  mat = false,
  children,
}: {
  capNhatLuc: string | undefined;
  rotateSeconds: number;
  /** Mất kết nối tới máy chủ — hiện dải "Dữ liệu chưa cập nhật". */
  mat?: boolean;
  children: ReactNode;
}) {
  useAutoScroll(rotateSeconds);

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorBgContainer: wallColors.surface,
          colorBgElevated: wallColors.surface,
          colorBorderSecondary: wallColors.border,
          colorText: wallColors.textBase,
          colorTextSecondary: wallColors.textSecondary,
        },
      }}
    >
      <div
        style={{
          position: 'fixed',
          inset: 0,
          background: wallColors.bg,
          color: wallColors.textBase,
          overflowY: 'auto',
          padding: 'clamp(12px, 0.8vw, 32px)',
          // ⛔ Khoá tương tác: xem giải thích ở đầu file.
          pointerEvents: 'none',
        }}
        data-testid="khung-wall"
      >
        <WallHeader capNhatLuc={capNhatLuc} mat={mat} />
        {children}
      </div>
    </ConfigProvider>
  );
}

function WallHeader({ capNhatLuc, mat }: { capNhatLuc: string | undefined; mat: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'baseline',
        justifyContent: 'space-between',
        gap: 16,
        marginBottom: 'clamp(8px, 0.6vw, 24px)',
      }}
    >
      <span style={{ fontSize: 'clamp(18px, 1.1vw, 40px)', fontWeight: 700 }}>
        Điều hành công trình thuỷ lợi
      </span>
      <span
        style={{
          fontSize: 'clamp(12px, 0.6vw, 22px)',
          color: mat ? statusColors.danger : wallColors.textSecondary,
          fontWeight: mat ? 700 : 400,
        }}
      >
        {mat ? 'Dữ liệu chưa cập nhật · ' : 'Cập nhật '}
        {formatDateTime(capNhatLuc)}
      </span>
    </div>
  );
}

/**
 * Cuộn chậm xuống rồi quay lại đầu, mỗi chặng cách nhau `rotateSeconds`.
 *
 * <p>⚠ `rotateSeconds` đọc từ `settings` (`system.wall.auto-rotate-seconds`) và đi xuống
 * qua phản hồi dashboard — không ghi cứng ở đây. Ghi cứng thì tham số đó là ô nhập không
 * nối vào đâu, đúng lỗi đã trả giá ở WS-12.
 *
 * <p>Trả về sớm khi tài liệu không đủ cao để cuộn: gọi `scrollTo` trên một trang không
 * cuộn được thì không có gì xảy ra, nhưng bộ hẹn giờ vẫn chạy vô ích cả ngày.
 */
function useAutoScroll(rotateSeconds: number) {
  const [, setChang] = useState(0);

  useEffect(() => {
    const giay = rotateSeconds > 0 ? rotateSeconds : 30;
    const hen = window.setInterval(() => {
      setChang((truoc) => {
        const khung = document.querySelector<HTMLElement>('[data-testid="khung-wall"]');
        if (!khung || khung.scrollHeight <= khung.clientHeight + 8) {
          return truoc;
        }
        const sau =
          khung.scrollTop + khung.clientHeight * 0.9 >= khung.scrollHeight ? 0 : truoc + 1;
        khung.scrollTo({
          top: sau === 0 ? 0 : khung.scrollTop + khung.clientHeight * 0.85,
          behavior: 'smooth',
        });
        return sau;
      });
    }, giay * 1000);

    return () => window.clearInterval(hen);
  }, [rotateSeconds]);
}
