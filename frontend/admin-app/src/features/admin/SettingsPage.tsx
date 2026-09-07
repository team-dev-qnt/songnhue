import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Input,
  InputNumber,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { Modal } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type SettingView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

/**
 * Cấu hình hệ thống — M5.3.
 *
 * <h3>Vì sao màn hình này quan trọng hơn vẻ ngoài của nó</h3>
 *
 * CLAUDE.md quy tắc 12: **mọi tham số nghiệp vụ nằm trong bảng `settings` và phải sửa
 * được trên giao diện** — giờ hành chính, số ngày giữ bản sao lưu, chu kỳ hỏi dữ liệu
 * thủy văn, ngưỡng cảnh báo… Đây là cơ chế hấp thụ 6 mục nghiệp vụ Công ty còn chưa
 * chốt: khi có câu trả lời thì sửa một ô trên màn hình này, không phải migration và
 * cũng không phải phát hành bản mới.
 *
 * Ô nhập dựng theo `valueType` và `validation` backend trả về; kiểm ở đây chỉ để người
 * dùng biết sớm, chốt chặn thật vẫn ở `SettingValidator` phía máy chủ (§4.2 tầng 1).
 */
export function SettingsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const [modalNhap, setModalNhap] = useState(false);
  const [vanBanNhap, setVanBanNhap] = useState('');

  /**
   * Xuất bộ cấu hình ra tệp JSON — **M5.17**, và tới 31/08 nút này không tồn tại.
   *
   * Trước đó ô `extra` chỉ chứa một dòng chữ *"Bản xuất cấu hình không bao giờ chứa credential"* —
   * một câu giải thích cho một cái nút không có. Endpoint `GET /settings/export` đã có từ WS-6 và
   * **không lời gọi nào**: nửa cặp đọc–ghi, luật 27.
   *
   * ⛔ Tải về bằng Blob ngay tại trình duyệt, KHÔNG qua hàng đợi job: `/settings/export` trả JSON
   * đồng bộ, ⛔ không phải 202 + `jobId`.
   *
   * ⚠ Từng có `components/business/ExportButton.tsx` dựng cho luồng job bất đồng bộ; nó **đã bị
   * xoá 04/09/2026** (nợ T27.29) vì 0 nơi import, và vì endpoint mẫu ghi trong chính javadoc của
   * nó — `/audit-logs/export` — **chưa từng tồn tại**. Luồng job thật (T34.7) đã dựng riêng
   * `useXuatBaoCao`, có trần số lượt hỏi và đường dừng — thứ bản dùng chung không có.
   */
  const xuat = useMutation({
    mutationFn: () => api.get<Record<string, string>>('/settings/export'),
    onSuccess: (data) => {
      const tep = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(tep);
      const a = document.createElement('a');
      a.href = url;
      a.download = `cau-hinh-songnhue-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      message.success(`Đã xuất ${Object.keys(data).length} tham số cấu hình`);
    },
    onError: (error) =>
      message.error(error instanceof ApiClientError ? error.message : 'Không xuất được cấu hình'),
  });

  /**
   * Nhập bộ cấu hình.
   *
   * ⚠ Backend kiểm **toàn bộ rồi mới áp** (`SettingService.importConfiguration`) và trả về số khoá
   * đã đổi + danh sách khoá bị bỏ qua. Hiện cả hai con số: "đã nhập xong" mà không nói bỏ qua bao
   * nhiêu là để người vận hành tin rằng mọi thứ đã vào.
   */
  const nhap = useMutation({
    mutationFn: (values: Record<string, string>) =>
      api.post<{ changed: number; skippedKeys: string[] }>('/settings/import', { values }),
    onSuccess: (kq) => {
      setModalNhap(false);
      setVanBanNhap('');
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      message.success(
        kq.skippedKeys.length === 0
          ? `Đã cập nhật ${kq.changed} tham số`
          : `Đã cập nhật ${kq.changed} tham số — bỏ qua ${kq.skippedKeys.length}: ${kq.skippedKeys.join(', ')}`,
      );
    },
    onError: (error) =>
      message.error(error instanceof ApiClientError ? error.message : 'Không nhập được cấu hình'),
  });
  const canEdit = hasPermission('adm:setting:update');
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  const settings = useQuery({
    queryKey: ['settings'],
    queryFn: () => api.get<SettingView[]>('/settings'),
  });

  const update = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      api.put<SettingView>(`/settings/${encodeURIComponent(key)}`, { value }),
    onSuccess: async (_result, variables) => {
      message.success('Đã lưu — có hiệu lực ngay, không cần khởi động lại');
      setDrafts((current) => {
        const next = { ...current };
        delete next[variables.key];
        return next;
      });
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được tham số');
    },
  });

  const groups = useMemo(() => groupByCode(settings.data ?? []), [settings.data]);

  const columns: ColumnsType<SettingView> = [
    {
      title: 'Tham số',
      dataIndex: 'label',
      width: '32%',
      render: (label: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{label}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.key}
          </Typography.Text>
          {row.description && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {row.description}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Giá trị',
      key: 'gia-tri',
      width: '38%',
      render: (_value, row) => (
        <SettingEditor
          setting={row}
          draft={drafts[row.key]}
          disabled={!canEdit || !row.editable}
          onChange={(value) => setDrafts((current) => ({ ...current, [row.key]: value }))}
        />
      ),
    },
    {
      title: 'Mặc định',
      dataIndex: 'defaultValue',
      width: '15%',
      render: (value: string | null) => (
        <Typography.Text type="secondary">{value ?? '—'}</Typography.Text>
      ),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 150,
      render: (_value, row) => {
        if (!row.editable) {
          return (
            <Tooltip title="Tham số này chỉ sửa được qua biến môi trường hoặc migration">
              <Tag>Khóa</Tag>
            </Tooltip>
          );
        }
        const draft = drafts[row.key];
        const changed = draft !== undefined && draft !== (row.effectiveValue ?? '');
        return (
          <Space size={0}>
            <Button
              type="link"
              disabled={!changed || !canEdit}
              loading={update.isPending}
              onClick={() => update.mutate({ key: row.key, value: draft ?? '' })}
            >
              Lưu
            </Button>
            {/* Xóa giá trị = quay về mặc định của danh mục (backend cố ý không bắt @NotBlank). */}
            <Button
              type="link"
              disabled={!canEdit || row.value === null}
              onClick={() => update.mutate({ key: row.key, value: '' })}
            >
              Về mặc định
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title="Cấu hình hệ thống"
      loading={settings.isLoading}
      extra={
        <Space>
          {hasPermission('adm:setting:export') && (
            <Tooltip title="Bản xuất không bao giờ chứa credential (§4.7) — khoá API thủy văn và mã số hệ thống văn bản bị loại ở phía máy chủ.">
              <Button
                icon={<DownloadOutlined />}
                loading={xuat.isPending}
                onClick={() => xuat.mutate()}
              >
                Xuất cấu hình
              </Button>
            </Tooltip>
          )}
          {hasPermission('adm:setting:import') && (
            <Button icon={<UploadOutlined />} onClick={() => setModalNhap(true)}>
              Nhập cấu hình
            </Button>
          )}
        </Space>
      }
    >
      <Tabs
        items={Object.entries(groups).map(([group, rows]) => ({
          key: group,
          label: `${GROUP_LABELS[group] ?? group} (${rows.length})`,
          children: (
            <Table<SettingView>
              columns={columns}
              dataSource={rows}
              rowKey="key"
              // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
              // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
              scroll={{ x: 900 }}
              pagination={false}
              size="small"
            />
          ),
        }))}
      />

      <Modal
        title="Nhập bộ cấu hình"
        open={modalNhap}
        onCancel={() => setModalNhap(false)}
        okText="Áp dụng"
        confirmLoading={nhap.isPending}
        onOk={() => {
          try {
            const doc = JSON.parse(vanBanNhap) as Record<string, string>;
            nhap.mutate(doc);
          } catch {
            // ⛔ Bắt ở đây thay vì để `mutate` ném: JSON hỏng là lỗi của người dán, không phải lỗi
            //    máy chủ, và một thông báo "SYS-0001" cho chuyện ấy là chỉ sai hướng.
            message.error('Nội dung không phải JSON hợp lệ — dán nguyên tệp đã xuất vào đây.');
          }
        }}
      >
        <Typography.Paragraph type="secondary">
          Dán nội dung tệp JSON đã xuất. Máy chủ kiểm <b>toàn bộ</b> rồi mới áp — một khoá sai thì
          không khoá nào được ghi.
        </Typography.Paragraph>
        <Input.TextArea
          rows={12}
          value={vanBanNhap}
          onChange={(e) => setVanBanNhap(e.target.value)}
          placeholder='{"security.login.max-failed-attempts": "5", …}'
        />
      </Modal>
    </Card>
  );
}

const GROUP_LABELS: Record<string, string> = {
  // ⚠ Thiếu một nhãn ở đây không làm gì đỏ — tab chỉ hiện mã nhóm thô ("SITE (30)") cạnh các tab
  //    tiếng Việt. Đo 31/08: hai nhóm lớn nhất `SITE` và `COMPANY` đều thiếu.
  SITE: 'Cổng thông tin (giao diện)',
  COMPANY: 'Thông tin Công ty',
  SECURITY: 'Bảo mật',
  BACKUP: 'Sao lưu',
  NOTIFICATION: 'Thông báo',
  HYDRO: 'Thủy văn',
  OPERATION: 'Vận hành',
  HR: 'Nhân sự',
  SYSTEM: 'Hệ thống',
  CMS: 'Cổng thông tin',
};

/** Ô nhập dựng theo `valueType` — kiểu sai thì người dùng gõ được thứ backend chắc chắn từ chối. */
function SettingEditor({
  setting,
  draft,
  disabled,
  onChange,
}: {
  setting: SettingView;
  draft: string | undefined;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  const value = draft ?? setting.effectiveValue ?? '';

  if (setting.valueType === 'BOOLEAN') {
    return (
      <Switch
        checked={value === 'true'}
        disabled={disabled}
        onChange={(checked) => onChange(String(checked))}
      />
    );
  }

  if (setting.valueType === 'INTEGER' || setting.valueType === 'DECIMAL') {
    const bounds = parseBounds(setting.validation);
    return (
      <Space direction="vertical" size={2} style={{ width: '100%' }}>
        <InputNumber
          value={value === '' ? null : Number(value)}
          disabled={disabled}
          min={bounds.min}
          max={bounds.max}
          style={{ width: '100%' }}
          onChange={(next) => onChange(next === null ? '' : String(next))}
        />
        {setting.validation && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Ràng buộc: {setting.validation}
          </Typography.Text>
        )}
      </Space>
    );
  }

  if (setting.valueType === 'JSON') {
    return (
      <Input.TextArea
        rows={3}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    );
  }

  return (
    <Input value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} />
  );
}

/** `min=7;max=365` → `{min: 7, max: 365}`. Không đọc được thì bỏ qua, backend vẫn chặn. */
function parseBounds(validation: string | null): { min?: number; max?: number } {
  if (!validation) {
    return {};
  }
  const bounds: { min?: number; max?: number } = {};
  for (const part of validation.split(';')) {
    const [key, raw] = part.split('=');
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) {
      continue;
    }
    if (key === 'min') {
      bounds.min = parsed;
    }
    if (key === 'max') {
      bounds.max = parsed;
    }
  }
  return bounds;
}

function groupByCode(rows: readonly SettingView[]): Record<string, SettingView[]> {
  return rows.reduce<Record<string, SettingView[]>>((acc, row) => {
    (acc[row.groupCode] ??= []).push(row);
    return acc;
  }, {});
}
