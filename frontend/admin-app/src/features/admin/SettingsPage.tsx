import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  ColorPicker,
  Input,
  InputNumber,
  Select,
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
import { HopThoaiMaXacThuc } from '@/components/business/HopThoaiMaXacThuc';
import { type SettingView, type Station, type UserView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { luuTep } from '@/shared/luuTep';

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
   * T61.42 — lượt ghi đang chờ mã 2FA. Nhóm SECURITY/AUDIT/BACKUP: mở hộp thoại TRƯỚC khi gửi (biết từ
   * `canXacThucLai`). Nhập cấu hình: máy chủ mới biết bộ JSON có đổi khoá nhạy cảm không ⇒ mở khi nhận `ADM-2023`.
   */
  const [choXacThuc, setChoXacThuc] = useState<
    | { loai: 'sua'; key: string; value: string }
    | { loai: 'nhap'; values: Record<string, string> }
    | null
  >(null);
  const [loiXacThuc, setLoiXacThuc] = useState<string | null>(null);

  /** Lỗi của lượt ghi có thể là "cần mã" / "mã sai" — giữ hộp thoại mở và hiện câu lỗi ngay trong đó. */
  const xuLyLoiGhi = (
    caught: unknown,
    choLai: NonNullable<typeof choXacThuc>,
    macDinh: string,
  ): void => {
    if (
      caught instanceof ApiClientError &&
      (caught.code === 'ADM-2023' || caught.code === 'ADM-2024')
    ) {
      setChoXacThuc(choLai);
      setLoiXacThuc(caught.code === 'ADM-2024' ? caught.message : null);
      return;
    }
    setChoXacThuc(null);
    setLoiXacThuc(null);
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);
  };

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
      luuTep(tep, `cau-hinh-songnhue-${new Date().toISOString().slice(0, 10)}.json`);
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
    mutationFn: ({ values, maXacThuc }: { values: Record<string, string>; maXacThuc?: string }) =>
      api.post<{ changed: number; skippedKeys: string[] }>('/settings/import', {
        values,
        maXacThuc,
      }),
    onSuccess: (kq) => {
      setChoXacThuc(null);
      setLoiXacThuc(null);
      setModalNhap(false);
      setVanBanNhap('');
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      message.success(
        kq.skippedKeys.length === 0
          ? `Đã cập nhật ${kq.changed} tham số`
          : `Đã cập nhật ${kq.changed} tham số — bỏ qua ${kq.skippedKeys.length}: ${kq.skippedKeys.join(', ')}`,
      );
    },
    onError: (error, bien) =>
      xuLyLoiGhi(error, { loai: 'nhap', values: bien.values }, 'Không nhập được cấu hình'),
  });
  const canEdit = hasPermission('adm:setting:update');
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  const settings = useQuery({
    queryKey: ['settings'],
    queryFn: () => api.get<SettingView[]>('/settings'),
  });

  const update = useMutation({
    mutationFn: ({ key, value, maXacThuc }: { key: string; value: string; maXacThuc?: string }) =>
      api.put<SettingView>(`/settings/${encodeURIComponent(key)}`, { value, maXacThuc }),
    onSuccess: async (_result, variables) => {
      setChoXacThuc(null);
      setLoiXacThuc(null);
      message.success('Đã lưu — có hiệu lực ngay, không cần khởi động lại');
      setDrafts((current) => {
        const next = { ...current };
        delete next[variables.key];
        return next;
      });
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: (caught: unknown, bien) =>
      xuLyLoiGhi(
        caught,
        { loai: 'sua', key: bien.key, value: bien.value },
        'Không lưu được tham số',
      ),
  });

  const groups = useMemo(() => groupByCode(settings.data ?? []), [settings.data]);

  /** Nhóm nhạy cảm ⇒ hỏi mã trước; nhóm khác ⇒ gửi ngay. */
  const luu = (row: SettingView, value: string) => {
    if (row.canXacThucLai) {
      setLoiXacThuc(null);
      setChoXacThuc({ loai: 'sua', key: row.key, value });
    } else {
      update.mutate({ key: row.key, value });
    }
  };

  const columns: ColumnsType<SettingView> = [
    {
      title: 'Tham số',
      dataIndex: 'label',
      width: '32%',
      render: (label: string, row) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{label}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.key}
          </Typography.Text>
          {row.canXacThucLai && (
            <Tooltip title="Nhóm nhạy cảm — lưu phải nhập lại mã xác thực hai bước và để lại sự kiện bảo mật">
              <Tag color="orange" style={{ width: 'fit-content' }}>
                Cần mã 2FA
              </Tag>
            </Tooltip>
          )}
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
              onClick={() => luu(row, draft ?? '')}
            >
              Lưu
            </Button>
            {/* Xóa giá trị = quay về mặc định của danh mục (backend cố ý không bắt @NotBlank). */}
            <Button
              type="link"
              disabled={!canEdit || row.value === null}
              onClick={() => luu(row, '')}
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
            nhap.mutate({ values: doc });
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

      <HopThoaiMaXacThuc
        open={choXacThuc !== null}
        title={
          choXacThuc?.loai === 'nhap' ? 'Xác nhận nhập cấu hình' : 'Xác nhận sửa tham số nhạy cảm'
        }
        moTa={
          choXacThuc?.loai === 'nhap'
            ? 'Bộ cấu hình này đổi tham số nhóm Bảo mật / Nhật ký / Sao lưu.'
            : `Tham số "${choXacThuc?.loai === 'sua' ? choXacThuc.key : ''}" thuộc nhóm nhạy cảm.`
        }
        loi={loiXacThuc}
        dangGui={update.isPending || nhap.isPending}
        onHuy={() => {
          setChoXacThuc(null);
          setLoiXacThuc(null);
        }}
        onXacNhan={(maXacThuc) => {
          if (choXacThuc?.loai === 'sua') {
            update.mutate({ key: choXacThuc.key, value: choXacThuc.value, maXacThuc });
          } else if (choXacThuc?.loai === 'nhap') {
            nhap.mutate({ values: choXacThuc.values, maXacThuc });
          }
        }}
      />
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
  LIMIT: 'Hạn mức',
  AUDIT: 'Nhật ký kiểm toán',
  INTEGRATION: 'Tích hợp',
};

/**
 * Khoá nhóm nhận cảnh báo G11 — phải khớp {@code RecipientResolver.KEY_EXECUTIVE_BOARD} ở backend.
 *
 * ⚠ Hai nơi phải nhớ cùng một chuỗi (luật 14). Gõ sai ở đây ⛔ làm gì đỏ — ô chọn chỉ lặng lẽ ⛔ hiện
 * ra và người quản trị lại gặp ô JSON thô, đúng trạng thái T76.3 sinh ra để bỏ.
 */
const KHOA_NHOM_CANH_BAO = 'notification.alert-group.executive-board';

/**
 * Khoá danh sách điểm đo lên cổng — phải khớp {@code HydroSettings.KHOA_DIEM_DO_LEN_CONG}.
 *
 * ⭐ Khác dòng ngay trên: chuỗi này **được một bộ canh giữ**. `oChonMaDiemDoLenCong.test.tsx` đọc
 * hằng Java từ đĩa rồi dựng mọi bài bằng chính giá trị ấy, nên gõ sai ở đây là năm bài đỏ — ⛔ phải
 * một ô chọn lặng lẽ ⛔ hiện ra. (Lỗ ấy vẫn còn ở `KHOA_NHOM_CANH_BAO`; nó ⛔ gây hại hôm nay nhưng
 * vẫn là một lỗ ⇒ nợ **T85.15**.)
 */
const KHOA_DIEM_DO_LEN_CONG = 'hydro.portal.station-codes';

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
      <Space orientation="vertical" size={2} style={{ width: '100%' }}>
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

  if (setting.valueType === 'COLOR') {
    // ⚠⚠ Ô CHỮ là nguồn sự thật, bảng chọn màu chỉ là lối vào thứ hai.
    //
    // Công ty cầm bộ nhận diện dạng văn bản (`1758bf`, `fac036`) nên thao tác tự nhiên nhất là
    // DÁN, ⛔ phải rê chuột trên vòng tròn màu — bỏ ô chữ đi là bắt họ dò lại bằng mắt đúng mã
    // mình đang cầm trên tay. Cùng họ T46.6: con đường tự nhiên nhất mà ⛔ đi được thì người dùng
    // sẽ đi đường sai.
    //
    // ⛔ Chuẩn hoá NGAY tại `onChange` chứ ⛔ lúc gửi: `ColorPicker` trả chuỗi khi chưa ai động
    // vào và trả OBJECT khi đã đổi, nên nơi nào nhận giá trị cũng phải nhớ ép kiểu —
    // `OperationStatusCodesPage:115` đang phải mang một `@ts-expect-error` vì đúng chuyện đó.
    // Đối số thứ hai của `onChange` đã là chuỗi hex, dùng thẳng thì ⛔ còn hai dạng nào để nhớ.
    const hopLe = /^#[0-9a-fA-F]{6}$/.test(value);
    return (
      <Space size={8} style={{ width: '100%' }}>
        <ColorPicker
          format="hex"
          disabledAlpha
          disabled={disabled}
          // ⚠ Giá trị rỗng = "chưa đặt, dùng màu bộ nhận diện". ColorPicker ⛔ biểu diễn được trạng
          //   thái ấy nên nó hiện mặc định của design-tokens — ô chữ bên cạnh mới là chỗ nói thật.
          value={hopLe ? value : undefined}
          onChange={(_, hex) => onChange(hex.toLowerCase())}
        />
        <Input
          value={value}
          disabled={disabled}
          placeholder="#rrggbb"
          status={value !== '' && !hopLe ? 'error' : undefined}
          onChange={(event) => onChange(event.target.value)}
        />
      </Space>
    );
  }

  // ⛔⛔ T76.3 — khoá này là mảng `publicId` tài khoản, mà màn hình Tài khoản ⛔ hiện `publicId` ở
  //    đâu cả ⇒ ô JSON thô bên dưới là một đường ghi **tồn tại trên giấy mà ⛔ đi được**. Đo 20/09:
  //    giá trị giữ nguyên `'[]'` từ 13/08 ⇒ chốt G11 của khách chưa từng chạy một lần nào.
  //    ⚠ Giá trị HỎNG thì rơi xuống ô thô bên dưới — một mảng ⛔ đọc được phải NHÌN THẤY được để
  //    sửa, ⛔ bị ô chọn âm thầm quy về rỗng rồi ghi đè (luật 9).
  if (setting.key === KHOA_NHOM_CANH_BAO && docMangPublicId(value) !== null) {
    return (
      <ONhomNhanCanhBao
        nhan={setting.label}
        value={value}
        disabled={disabled}
        onChange={onChange}
      />
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

  // ⛔⛔ T28.48 — ⛔ có nhánh ba trạng thái như `KHOA_NHOM_CANH_BAO`, và đó là một khác biệt THẬT:
  //    một chuỗi ngăn phẩy LUÔN đọc được, ⛔ có hình dạng "hỏng cú pháp" nào để rơi xuống ô thô.
  //    Trạng thái nguy hiểm ở đây khác hẳn — **mã ⛔ khớp điểm đo nào** — và nó được xử lý bên
  //    trong ô chọn chứ ⛔ bằng cách từ chối dựng ô (xem javadoc của component).
  if (setting.key === KHOA_DIEM_DO_LEN_CONG) {
    return (
      <ODanhSachMaDiemDo
        nhan={setting.label}
        value={value}
        disabled={disabled}
        onChange={onChange}
      />
    );
  }

  return (
    <Input value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} />
  );
}

/**
 * Nhóm <i>"Ban điều hành"</i> của chốt G11 — người nhận mặc định của cảnh báo vận hành.
 *
 * <p>⛔⛔ Ô chọn này <b>phải đi cùng</b> bản vá T74.7. Tới 20/09/2026 `RecipientResolver` CỘNG nhóm
 * này vào mọi lượt gửi ⛔ nhắm đích theo quyền — gồm 17 hàng `notify_owner` của quy trình duyệt và
 * sáu mã sự kiện an ninh <b>của một cá nhân</b> (<i>"tài khoản của bạn đã bị khoá"</i>). Hôm nay
 * ⛔ ai thấy vì nhóm rỗng, nên dựng ô chọn mà ⛔ vá vế kia là <b>bật một lỗi đang ngủ</b> đúng ngày
 * Công ty điền danh sách.
 */
function ONhomNhanCanhBao({
  nhan,
  value,
  disabled,
  onChange,
}: {
  nhan: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  // ⛔ Chỉ tài khoản ĐANG HOẠT ĐỘNG — `RecipientResolver.locNguoiNhan` lọc `findActiveIdsIn` cho
  //   nhóm suy ra, nên bày tài khoản khoá ra là dựng một lựa chọn chắc chắn ⛔ nhận được thư.
  //   Cùng MỘT luật với `OTruongPho` ở `OrgUnitsPage`, ⛔ hai luật khác nhau.
  const { data: taiKhoan } = useQuery({
    queryKey: ['admin-users', 'chon-nhom-canh-bao'],
    queryFn: () => api.get<UserView[]>('/admin/users'),
  });
  const chon = useMemo(
    () =>
      (taiKhoan ?? [])
        .filter((u) => u.status === 'ACTIVE')
        .map((u) => ({ value: u.publicId, label: `${u.fullName} (${u.username})` })),
    [taiKhoan],
  );
  // ⚠ `?? []` ⛔ che được gì: nhánh giá trị hỏng đã bị chặn ở nơi gọi, nên tới đây luôn đọc được.
  const daChon = useMemo(() => docMangPublicId(value) ?? [], [value]);

  return (
    <Select
      mode="multiple"
      // ⚠ Ô này nằm trong một ô bảng, ⛔ có <label> nào trỏ tới ⇒ trình đọc màn hình đọc thành
      //   "combobox" trống rỗng và bài kiểm ⛔ gọi tên được nó (bài học T63.9).
      aria-label={nhan}
      allowClear
      disabled={disabled}
      showSearch={{ optionFilterProp: 'label' }}
      options={chon}
      value={daChon}
      placeholder="Chọn tài khoản nhận cảnh báo"
      style={{ width: '100%' }}
      onChange={(ids: string[]) => onChange(JSON.stringify(ids))}
    />
  );
}

/**
 * Danh sách điểm đo công bố lên cổng — khoá {@code hydro.portal.station-codes}, T28.48.
 *
 * <h2>⛔⛔ Ba thứ của ô này ⛔ suy ra được từ *"một danh sách mã"*</h2>
 *
 * <ol>
 *   <li><b>Rỗng nghĩa là CÔNG BỐ TẤT CẢ</b>, ⛔ phải ⛔ công bố gì ({@code V202609041064} +
 *       {@code HydroSettings.maDiemDoLenCong()}). Hai trạng thái ấy trông y hệt nhau trên một ô
 *       trống, nên dòng chữ dưới ô là chỗ DUY NHẤT nói được — bỏ nó đi là để người quản trị xoá
 *       hết tag hòng "tạm ẩn bảng" rồi công bố trọn 19 điểm đo.
 *   <li><b>Thứ tự chọn LÀ thứ tự hiển thị trên cổng.</b> Máy chủ cố ý dùng {@code LinkedHashSet};
 *       {@code PublicHydroService} còn viết sẵn rằng một danh sách <i>"chọn được nhưng ⛔ xếp
 *       được"</i> sẽ phải mở lại mã ngay lần đầu Công ty dùng. {@code mode="multiple"} của antd
 *       giữ đúng thứ tự bấm, và bỏ tag rồi chọn lại là cách đẩy một mã xuống cuối.
 *   <li><b>Trạm đã NGỪNG ⛔ bày ra để chọn mới.</b> {@code PublicHydroService.mucNuoc()} loại
 *       {@code NGUNG} <b>trước</b> bộ lọc này, nên một mã đã ngừng ⛔ bao giờ lên cổng dù nằm
 *       trong danh sách — bày nó ra là dựng một lựa chọn chắc chắn ⛔ có tác dụng. Cùng MỘT luật
 *       với {@link ONhomNhanCanhBao} (tài khoản đã khoá).
 * </ol>
 *
 * <h2>⛔⛔ Mã ⛔ khớp điểm đo nào vẫn PHẢI nhìn thấy được</h2>
 *
 * <p>Đây là trạng thái hỏng thật của khoá này, và hôm nay nó im lặng hoàn toàn: bảng mực nước trên
 * cổng <b>ngắn đi một dòng</b>, lý do nằm trong một dòng {@code log.warn} ⛔ ai đọc. Một ô chọn
 * lặng lẽ bỏ những mã ấy còn tệ hơn ô chữ cũ — lượt <i>Lưu</i> kế tiếp sẽ <b>ghi đè mất</b> cấu
 * hình của Công ty mà ⛔ ai bấm nút xoá (luật 9).
 *
 * <p>⇒ Chúng ở lại dưới dạng tag có nhãn nói rõ <b>vì sao</b>, và <b>⛔ đặt {@code disabled}</b>:
 * một option bị vô hiệu thì antd bỏ luôn dấu ✕ trên tag, tức người quản trị nhìn thấy lỗi mà ⛔
 * sửa được. Ba trạng thái, ba câu chữ khác nhau (T59.0) — <i>gõ nhầm</i> và <i>trạm đã thôi dùng</i>
 * dẫn tới hai việc khác hẳn nhau.
 */
function ODanhSachMaDiemDo({
  nhan,
  value,
  disabled,
  onChange,
}: {
  nhan: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  const { data: diemDo } = useQuery({
    queryKey: ['hyd-stations', 'chon-len-cong'],
    queryFn: () => api.get<Station[]>('/hyd/stations'),
  });
  const daChon = useMemo(() => docMaDiemDo(value), [value]);
  const chon = useMemo(() => {
    const tatCa = diemDo ?? [];
    const theoMa = new Map(tatCa.map((s) => [s.code.toUpperCase(), s]));
    const dangChay = tatCa
      .filter((s) => s.active)
      .map((s) => ({ value: s.code.toUpperCase(), label: `${s.code} — ${s.name}` }));
    const daCo = new Set(dangChay.map((o) => o.value));
    // Mã đã lưu mà ⛔ nằm trong danh sách chọn được — giữ lại kèm LÝ DO, xem javadoc.
    const conLai = daChon
      .filter((ma) => !daCo.has(ma))
      .map((ma) => {
        const s = theoMa.get(ma);
        return {
          value: ma,
          label: s ? `${ma} — ${s.name} (trạm đã ngừng)` : `${ma} — ⛔ khớp điểm đo nào`,
        };
      });
    return [...dangChay, ...conLai];
  }, [diemDo, daChon]);

  return (
    <Space orientation="vertical" size={2} style={{ width: '100%' }}>
      <Select
        mode="multiple"
        // ⚠ Ô nằm trong một ô bảng, ⛔ có <label> nào trỏ tới (bài học T63.9).
        aria-label={nhan}
        allowClear
        disabled={disabled}
        showSearch={{ optionFilterProp: 'label' }}
        options={chon}
        value={daChon}
        placeholder="Để trống = công bố tất cả"
        style={{ width: '100%' }}
        onChange={(ma: string[]) => onChange(ma.join(','))}
      />
      {/*
        Hai chỗ nói cùng một quy ước, và chúng ⛔ trùng lặp — chúng hiện ở HAI LÚC khác nhau:
        placeholder chỉ hiện khi ô đã RỖNG (đúng khoảnh khắc nguy hiểm, ngay sau khi xoá hết tag),
        còn dòng này luôn hiện, cho người ĐANG CÓ tag và sắp xoá.
      */}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Ô rỗng nghĩa là công bố tất cả điểm đo đang hoạt động — không phải ẩn hết. Thứ tự chọn là
        thứ tự hiển thị trên cổng.
      </Typography.Text>
    </Space>
  );
}

/**
 * `'f01519, F01520 ,'` → `['F01519','F01520']` — bản sao ĐÚNG của
 * {@code HydroSettings.maDiemDoLenCong()}: tách theo dấu phẩy, cắt khoảng trắng, HOA hoá, bỏ rỗng,
 * khử trùng mà <b>giữ nguyên thứ tự</b>.
 *
 * <p>⚠ Luật 14 — hai nơi phải nhớ cùng một phép tách. Chênh nhau ở đây ⛔ làm gì đỏ, nó chỉ làm ô
 * chọn hiển thị một tập khác với tập máy chủ thật sự dùng; bộ canh giữ sự khớp ấy là bài
 * <i>"PUT chuỗi ngăn phẩy theo ĐÚNG thứ tự đã chọn"</i>.
 */
function docMaDiemDo(raw: string): string[] {
  const daGap = new Set<string>();
  const ket: string[] = [];
  for (const phan of raw.split(',')) {
    const ma = phan.trim().toUpperCase();
    if (ma !== '' && !daGap.has(ma)) {
      daGap.add(ma);
      ket.push(ma);
    }
  }
  return ket;
}

/**
 * `'["u-1","u-2"]'` → `['u-1','u-2']`; rỗng → `[]`; <b>⛔ đọc được → `null`</b>.
 *
 * <p>⚠ Ba trạng thái, ⛔ phải hai: gộp *"hỏng"* vào *"rỗng"* thì một giá trị JSON sai cú pháp sẽ
 * hiện ra như một nhóm trống rồi bị lượt Lưu kế tiếp **ghi đè mất** (luật 9).
 */
function docMangPublicId(raw: string): string[] | null {
  if (raw.trim() === '') {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.every((x) => typeof x === 'string')
      ? (parsed as string[])
      : null;
  } catch {
    return null;
  }
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
