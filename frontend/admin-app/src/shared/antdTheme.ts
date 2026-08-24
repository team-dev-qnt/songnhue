import { theme, type ThemeConfig } from 'antd';

import { brandColors, neutralColors, shadow, sizing, statusColors } from 'design-tokens';

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
    /* Motion — AntD dùng `motionDurationMid` cho phần lớn hiệu ứng component */
    motionDurationFast: '0.15s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.3s',
    /* Box-shadow cho card, dropdown — thay thế shadow mặc định quá nhạt của AntD */
    boxShadow: shadow.sm,
    boxShadowSecondary: shadow.md,
    boxShadowTertiary: shadow.lg,
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
    Card: {
      borderRadiusLG: 8,
    },
    Button: {
      borderRadius: sizing.borderRadius,
      controlHeight: 36,
      controlHeightLG: 42,
    },
    Input: {
      controlHeight: 36,
      controlHeightLG: 42,
    },
    Table: {
      headerBg: neutralColors.bgLayout,
      rowHoverBg: brandColors.primaryLight,
      // Bảng quản trị nào cũng nhiều cột; giảm đệm để bớt phải cuộn ngang.
      cellPaddingBlock: 10,
    },
    Modal: {
      borderRadiusLG: 12,
    },
    Tag: {
      borderRadiusSM: 12,
    },
    Notification: {
      borderRadiusLG: 8,
    },
    Message: {
      borderRadiusLG: 8,
    },
  },
};
