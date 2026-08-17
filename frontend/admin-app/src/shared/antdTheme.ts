import { theme, type ThemeConfig } from 'antd';

import { brandColors, neutralColors, sizing, statusColors } from './tokens';

/**
 * Theme AntD dựng **từ** `tokens.ts` — không có màu nào tự khai ở đây.
 *
 * Ánh xạ đáng lưu ý: `colorError` lấy từ `statusColors.danger`, nên nút xoá, thông báo
 * lỗi và huy hiệu "sự cố đang mở" cùng một sắc đỏ. Người trực nhìn đỏ ở đâu cũng hiểu
 * cùng một mức nghiêm trọng.
 */
export const antdTheme: ThemeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: brandColors.primary,
    colorLink: brandColors.link,
    colorInfo: brandColors.info,
    colorSuccess: statusColors.normal,
    colorWarning: statusColors.warning,
    colorError: statusColors.danger,
    colorTextBase: neutralColors.textBase,
    colorBgLayout: neutralColors.bgLayout,
    colorBgContainer: neutralColors.bgContainer,
    colorBorder: neutralColors.border,
    fontFamily: sizing.fontFamily,
    fontSize: sizing.fontSize,
    borderRadius: sizing.borderRadius,
    wireframe: false,
  },
  components: {
    Layout: {
      headerHeight: sizing.headerHeight,
      headerBg: neutralColors.bgContainer,
      siderBg: neutralColors.bgSider,
      bodyBg: neutralColors.bgLayout,
    },
    Menu: {
      darkItemBg: neutralColors.bgSider,
      darkSubMenuItemBg: neutralColors.bgSider,
    },
    Table: {
      headerBg: neutralColors.bgLayout,
      // Bảng quản trị nào cũng nhiều cột; giảm đệm để bớt phải cuộn ngang.
      cellPaddingBlock: 10,
    },
  },
};
