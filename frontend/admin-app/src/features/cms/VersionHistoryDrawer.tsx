import { useMutation, useQuery } from '@tanstack/react-query';
import { App, Alert, Button, Drawer, List, Popconfirm, Select, Space, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';

import { ApiClientError } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

import { cmsApi, cmsKeys } from './api';
import { diffBlocks, summarizeDiff, toBlocks } from './diff';

/**
 * Lịch sử phiên bản: so sánh và phục hồi — T20.5, CN-01.1.
 *
 * <h3>Bản "đang phục vụ cổng" không nhất thiết là bản mới nhất</h3>
 *
 * Cơ chế copy-on-write (điểm nghiệp vụ 1): sửa một bài đã xuất bản tạo ra bản mới ở dạng
 * nháp, còn cổng vẫn phục vụ bản đã duyệt. Nên danh sách này phải **chỉ rõ bản nào đang
 * lên cổng** — thiếu dấu đó thì người biên tập tưởng bản mới nhất đang hiển thị và không
 * hiểu vì sao sửa xong cổng không đổi.
 */
export function VersionHistoryDrawer({
  articleId,
  open,
  onClose,
  onRestored,
}: {
  articleId: string;
  open: boolean;
  onClose: () => void;
  onRestored: () => Promise<void> | void;
}) {
  const { message } = App.useApp();
  const [leftId, setLeftId] = useState<string | null>(null);
  const [rightId, setRightId] = useState<string | null>(null);

  const versions = useQuery({
    queryKey: cmsKeys.versions(articleId),
    queryFn: () => cmsApi.versions(articleId),
    enabled: open,
  });

  // Mặc định so hai bản gần nhất — đó là câu hỏi hay gặp nhất ("lần sửa vừa rồi đổi gì").
  //
  // ⚠ Tính khi dựng giao diện, KHÔNG đặt bằng `useEffect`. Đặt state trong effect làm màn
  // hình vẽ một lượt với ô chọn trống rồi vẽ lại — người dùng thấy nó nhấp nháy, và React 19
  // gọi đúng đây là "cascading render". `??` diễn đạt trọn ý: chưa chọn thì lấy mặc định.
  const list = versions.data ?? [];
  const activeLeft = leftId ?? list[1]?.publicId ?? null;
  const activeRight = rightId ?? list[0]?.publicId ?? null;

  const left = useQuery({
    queryKey: cmsKeys.versionContent(articleId, activeLeft ?? ''),
    queryFn: () => cmsApi.versionContent(articleId, activeLeft as string),
    enabled: open && activeLeft !== null,
  });

  const right = useQuery({
    queryKey: cmsKeys.versionContent(articleId, activeRight ?? ''),
    queryFn: () => cmsApi.versionContent(articleId, activeRight as string),
    enabled: open && activeRight !== null,
  });

  const rows = useMemo(() => {
    if (!left.data || !right.data) {
      return [];
    }
    return diffBlocks(toBlocks(left.data.content), toBlocks(right.data.content));
  }, [left.data, right.data]);

  const tomTat = summarizeDiff(rows);

  const restore = useMutation({
    mutationFn: (versionId: string) => cmsApi.restoreVersion(articleId, versionId),
    onSuccess: async () => {
      message.success('Đã phục hồi nội dung từ phiên bản cũ');
      await onRestored();
      onClose();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không phục hồi được'),
  });

  const options = list.map((v) => ({
    value: v.publicId,
    label: `Bản ${v.versionNo} — ${formatDateTime(v.createdAt)}${v.servingPublic ? ' (đang lên cổng)' : ''}`,
  }));

  return (
    <Drawer title="Lịch sử phiên bản" open={open} onClose={onClose} width={860} destroyOnHidden>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Space wrap>
          <Select
            style={{ width: 320 }}
            placeholder="Bản trước"
            value={activeLeft}
            options={options}
            onChange={setLeftId}
          />
          <Typography.Text type="secondary">so với</Typography.Text>
          <Select
            style={{ width: 320 }}
            placeholder="Bản sau"
            value={activeRight}
            options={options}
            onChange={setRightId}
          />
        </Space>

        {activeLeft === null || activeRight === null ? (
          <Alert type="info" showIcon message="Chọn hai phiên bản để so sánh" />
        ) : tomTat.khongDoi ? (
          <Alert
            type="info"
            showIcon
            message="Hai phiên bản có nội dung giống nhau"
            description="Lần lưu đó chỉ đổi phần siêu dữ liệu (tiêu đề SEO, danh mục, ảnh đại diện…) chứ không đổi nội dung bài."
          />
        ) : (
          <>
            <Space>
              <Tag color="green">+{tomTat.them} đoạn thêm</Tag>
              <Tag color="red">−{tomTat.bot} đoạn bỏ</Tag>
            </Space>
            <div
              style={{
                border: '1px solid #f0f0f0',
                borderRadius: 6,
                maxHeight: 380,
                overflow: 'auto',
              }}
            >
              {rows.map((row, index) => (
                <div
                  key={`${row.kind}-${index}`}
                  style={{
                    padding: '6px 12px',
                    background:
                      row.kind === 'them'
                        ? '#f6ffed'
                        : row.kind === 'bot'
                          ? '#fff1f0'
                          : 'transparent',
                    borderLeft: `3px solid ${
                      row.kind === 'them'
                        ? '#52c41a'
                        : row.kind === 'bot'
                          ? '#f5222d'
                          : 'transparent'
                    }`,
                    color: row.kind === 'giu' ? '#8c8c8c' : undefined,
                    fontSize: 13,
                  }}
                >
                  <span style={{ userSelect: 'none', marginRight: 8, fontWeight: 600 }}>
                    {row.kind === 'them' ? '+' : row.kind === 'bot' ? '−' : ' '}
                  </span>
                  {row.text}
                </div>
              ))}
            </div>
          </>
        )}

        <Typography.Title level={5} style={{ marginBottom: 0 }}>
          Tất cả phiên bản
        </Typography.Title>
        <List
          size="small"
          loading={versions.isLoading}
          dataSource={list}
          locale={{ emptyText: 'Chưa có phiên bản nào' }}
          renderItem={(v) => (
            <List.Item
              actions={[
                <Popconfirm
                  key="restore"
                  title={`Phục hồi nội dung từ bản ${v.versionNo}?`}
                  description="Tạo một phiên bản mới mang nội dung cũ. Không có phiên bản nào bị xoá."
                  okText="Phục hồi"
                  cancelText="Huỷ"
                  onConfirm={() => restore.mutate(v.publicId)}
                >
                  <Button type="link" disabled={restore.isPending}>
                    Phục hồi
                  </Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <span>Bản {v.versionNo}</span>
                    {v.servingPublic && <Tag color="blue">Đang lên cổng</Tag>}
                  </Space>
                }
                description={
                  <Space split="·" wrap>
                    <span>{formatDateTime(v.createdAt)}</span>
                    <span>{v.title}</span>
                    {v.note && <span>{v.note}</span>}
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Space>
    </Drawer>
  );
}
