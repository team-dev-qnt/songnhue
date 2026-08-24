import { Card, Tabs } from 'antd';

import { BannersTab } from './BannersTab';
import { MenusTab } from './MenusTab';
import { SiteConfigTab } from './SiteConfigTab';

/**
 * Cấu hình giao diện cổng — T20.8, CN-01.4 + CN-01.5.
 *
 * Ba thứ khác nhau về bản chất nhưng cùng trả lời một câu hỏi của người dùng: *"cổng của
 * Công ty trông như thế nào"*. Tách thành ba mục menu riêng thì người vào sửa logo phải nhớ
 * banner nằm ở chỗ khác.
 */
export function SiteLayoutPage() {
  return (
    <Card title="Cấu hình giao diện cổng">
      <Tabs
        destroyOnHidden
        items={[
          { key: 'chung', label: 'Nhận diện & thông tin', children: <SiteConfigTab /> },
          { key: 'banner', label: 'Banner trang chủ', children: <BannersTab /> },
          { key: 'menu', label: 'Menu', children: <MenusTab /> },
        ]}
      />
    </Card>
  );
}
