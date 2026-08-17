import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';

import 'dayjs/locale/vi';

import { AuthProvider } from '@/app/auth/AuthProvider';
import { queryClient } from '@/app/queryClient';
import { router } from '@/app/router';
import { antdTheme } from '@/shared/antdTheme';

/**
 * Điểm vào.
 *
 * Thứ tự bọc có ý nghĩa: `ConfigProvider` → `AntdApp` → `QueryClientProvider` →
 * `AuthProvider` → router. `AuthProvider` dùng `App.useApp()` để bắn thông báo khi mất
 * phiên, nên nó **bắt buộc** nằm trong `AntdApp`; đảo lại thì thông báo không hiện mà
 * cũng chẳng có lỗi nào, chỉ là im lặng.
 */
const container = document.getElementById('root');
if (!container) {
  throw new Error('Không tìm thấy phần tử #root trong index.html');
}

createRoot(container).render(
  <StrictMode>
    <ConfigProvider locale={viVN} theme={antdTheme}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <RouterProvider router={router} />
          </AuthProvider>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  </StrictMode>,
);
