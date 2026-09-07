import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Descriptions, Image, Input, Space, Switch, Typography, Upload } from 'antd';
import { useState } from 'react';

import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { type SiteSettingItem } from './types';

/**
 * Cấu hình nhận diện cổng — T20.8, CN-01.5.
 *
 * <h3>Không có bảng riêng, và cũng không có màn hình "sửa tất cả rồi bấm Lưu"</h3>
 *
 * Dữ liệu nằm ở `settings` nhóm `SITE`. Mỗi tham số lưu **ngay khi rời ô nhập**, không gom
 * thành một nút Lưu chung: cụm này có hơn hai mươi tham số thuộc nhiều nhóm ý nghĩa khác
 * nhau (tên cơ quan, địa chỉ, mạng xã hội, mã theo dõi), và một nút Lưu chung nghĩa là sửa
 * một ô rồi bấm Lưu sẽ ghi lại cả hai mươi ô — kể cả những ô người khác vừa sửa ở màn hình
 * cấu hình hệ thống.
 *
 * <h3>Ảnh nhận diện là nơi DUY NHẤT trong hệ nhận SVG</h3>
 *
 * Và nó vẫn phải đi qua `SvgSanitizer` ở backend — SVG là định dạng ảnh duy nhất chạy được
 * JavaScript. Ở đây không cần biết điều đó; việc khử trùng nằm ở tầng đính kèm nên không
 * đường tải lên nào quên được.
 */
export function SiteConfigTab() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const config = useQuery({ queryKey: cmsKeys.siteConfig(), queryFn: () => cmsApi.siteConfig() });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: cmsKeys.siteConfig() });

  const update = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      cmsApi.updateSiteConfig(key, value),
    onSuccess: async () => {
      message.success('Đã lưu');
      await invalidate();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được tham số'),
  });

  const uploadImage = useMutation({
    mutationFn: ({ key, file }: { key: string; file: File }) => cmsApi.uploadBrandImage(key, file),
    onSuccess: async () => {
      message.success('Đã đổi ảnh');
      await invalidate();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tải được ảnh lên'),
  });

  const items = config.data ?? [];
  const anhNhanDien = items.filter((item) => item.key.endsWith('.attachment-id'));
  const thamSo = items.filter((item) => !item.key.endsWith('.attachment-id'));

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <div>
        <Typography.Title level={5}>Ảnh nhận diện</Typography.Title>
        <Space wrap size={24}>
          {anhNhanDien.map((item) => (
            <Space key={item.key} direction="vertical" align="center">
              <Typography.Text strong>{item.label}</Typography.Text>
              {item.effectiveValue ? (
                <Image
                  src={`/api/v1/public/files/${item.effectiveValue}`}
                  alt={item.label}
                  height={64}
                  style={{ objectFit: 'contain', background: '#fafafa', padding: 4 }}
                />
              ) : (
                <Typography.Text type="secondary">Chưa đặt</Typography.Text>
              )}
              <Upload
                showUploadList={false}
                accept="image/png,image/jpeg,image/webp,image/svg+xml"
                beforeUpload={(file) => {
                  uploadImage.mutate({ key: item.key, file });
                  return false;
                }}
              >
                <Button size="small" loading={uploadImage.isPending}>
                  Đổi ảnh
                </Button>
              </Upload>
            </Space>
          ))}
        </Space>
      </div>

      <div>
        <Typography.Title level={5}>Thông tin cổng</Typography.Title>
        <Descriptions bordered column={1} size="small" styles={{ label: { width: 280 } }}>
          {thamSo.map((item) => (
            <Descriptions.Item
              key={item.key}
              label={
                <Space direction="vertical" size={0}>
                  <span>{item.label}</span>
                  {item.description && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.description}
                    </Typography.Text>
                  )}
                </Space>
              }
            >
              <SettingEditor
                item={item}
                disabled={!item.editable || update.isPending}
                onSave={(value) => update.mutate({ key: item.key, value })}
              />
            </Descriptions.Item>
          ))}
        </Descriptions>
      </div>
    </Space>
  );
}

/**
 * Ô sửa một tham số, chọn theo kiểu dữ liệu.
 *
 * ⚠ Lưu khi **rời ô** (`onBlur`) chứ không phải mỗi lần gõ: gõ một dòng địa chỉ mà bắn ba
 * chục lượt ghi thì mỗi lượt là một dòng nhật ký kiểm toán, và nhật ký đó sẽ không còn đọc
 * được nữa. Công tắc thì lưu ngay vì nó chỉ có hai giá trị.
 */
function SettingEditor({
  item,
  disabled,
  onSave,
}: {
  item: SiteSettingItem;
  disabled: boolean;
  onSave: (value: string) => void;
}) {
  const [draft, setDraft] = useState(item.effectiveValue ?? '');

  if (item.valueType === 'BOOLEAN') {
    return (
      <Switch
        disabled={disabled}
        checked={item.effectiveValue === 'true'}
        onChange={(checked) => onSave(String(checked))}
      />
    );
  }

  const nhieuDong =
    item.key.includes('address') || item.key.includes('info') || item.key.includes('map');

  const Control = nhieuDong ? Input.TextArea : Input;
  return (
    <Control
      value={draft}
      disabled={disabled}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => {
        if (draft !== (item.effectiveValue ?? '')) {
          onSave(draft);
        }
      }}
      placeholder={item.defaultValue ?? undefined}
      {...(nhieuDong ? { autoSize: { minRows: 2, maxRows: 6 } } : {})}
    />
  );
}
