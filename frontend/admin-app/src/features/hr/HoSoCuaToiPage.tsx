import { IdcardOutlined, LockOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Descriptions, Empty, Skeleton, Space, Tag, Typography } from 'antd';

import { api } from '@/shared/apiClient';
import { formatDate } from '@/shared/format';

import {
  GIOI_TINH,
  HOC_VAN,
  LOAI_HOP_DONG,
  TINH_TRANG_HON_NHAN,
  TRANG_THAI_CONG_TAC,
  type EmployeeDetail,
  type SensitiveFields,
} from './hrVocabulary';

/** Phản hồi của {@code GET /hr/ho-so-cua-toi} — CN-04.7 vế hai, T51.8. */
interface HoSoCuaToiView {
  hoSo: EmployeeDetail;
  truongBaoMat: SensitiveFields;
}

/**
 * **Hồ sơ của tôi** — vế thứ hai của CN-04.7, thứ chỉ dựng được sau khi T51.8 mở đường ghi cho
 * `users.employee_id`.
 *
 * ## ⛔ Màn hình này ⛔ KHÔNG có nút Sửa, và đó là một quyết định
 *
 * Đặc tả cho *"chính nhân viên đó"* quyền **xem** trường 🔒 của mình. Quyền **sửa** thì ⛔ không:
 * backend giữ nguyên `hr:employee:view-sensitive` ở đường ghi, và `HoSoCuaToiController` ⛔ không
 * có một động từ ghi nào (có bài kiểm cấu trúc khẳng định điều đó). Vẽ một nút Sửa ở đây là hứa
 * một thứ backend sẽ trả 403 — đúng lớp lỗi *"màn hình nói dối"* mà §10.69 đã trả giá.
 *
 * ## ⚠ Mỗi lần mở màn hình này là một dòng `security_events`
 *
 * Backend ghi `HR_SENSITIVE_FIELDS_READ` cho **mọi** lượt đọc trường 🔒, kể cả lượt tự đọc. Nên
 * ⛔ **không** đặt `refetchInterval` hay `refetchOnWindowFocus` cho truy vấn dưới đây: mỗi lượt
 * làm mới tự động là một dòng nhật ký bảo mật ⛔ không ai thực hiện.
 */
export function HoSoCuaToiPage() {
  const hoSo = useQuery({
    queryKey: ['hr', 'ho-so-cua-toi'],
    queryFn: () => api.get<HoSoCuaToiView>('/hr/ho-so-cua-toi'),
    // ⛔ Xem javadoc: mỗi lượt gọi ghi một dòng nhật ký bảo mật.
    refetchOnWindowFocus: false,
    retry: false,
  });

  if (hoSo.isLoading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  if (hoSo.isError || !hoSo.data) {
    return (
      <Card>
        <Empty
          description={
            <Space direction="vertical" size={4}>
              <Typography.Text strong>
                Tài khoản của bạn chưa được liên kết với hồ sơ cán bộ
              </Typography.Text>
              <Typography.Text type="secondary">
                Liên hệ quản trị hệ thống để liên kết — thao tác nằm ở màn hình Quản trị › Tài
                khoản.
              </Typography.Text>
            </Space>
          }
        />
      </Card>
    );
  }

  const { hoSo: chiTiet, truongBaoMat } = hoSo.data;
  const trangThai = TRANG_THAI_CONG_TAC[chiTiet.status];

  return (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Card
        title={
          <Space>
            <IdcardOutlined />
            <span>
              {chiTiet.fullName} · {chiTiet.code}
            </span>
            {trangThai ? <Tag color={trangThai.color}>{trangThai.label}</Tag> : null}
          </Space>
        }
      >
        <Descriptions column={{ xs: 1, sm: 1, md: 2 }} size="small" bordered>
          <Descriptions.Item label="Ngày sinh">{formatDate(chiTiet.dateOfBirth)}</Descriptions.Item>
          <Descriptions.Item label="Giới tính">
            {chiTiet.gender ? GIOI_TINH[chiTiet.gender] : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Học vấn">
            {chiTiet.educationLevel ? HOC_VAN[chiTiet.educationLevel] : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Dân tộc">{chiTiet.ethnicity ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Quê quán">{chiTiet.hometown ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Nơi ở hiện nay">{chiTiet.address ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Điện thoại">{chiTiet.phone ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Email cơ quan">{chiTiet.workEmail ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Tình trạng hôn nhân">
            {chiTiet.maritalStatus ? TINH_TRANG_HON_NHAN[chiTiet.maritalStatus] : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Liên hệ khẩn cấp">
            {chiTiet.emergencyContactName
              ? `${chiTiet.emergencyContactName} · ${chiTiet.emergencyContactPhone ?? '—'}`
              : '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Công tác">
        <Descriptions column={{ xs: 1, sm: 1, md: 2 }} size="small" bordered>
          <Descriptions.Item label="Đơn vị">{chiTiet.orgUnitName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Chức vụ">{chiTiet.positionName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Chức danh">{chiTiet.jobTitle ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Ngày vào làm">{formatDate(chiTiet.hiredAt)}</Descriptions.Item>
          <Descriptions.Item label="Loại hợp đồng">
            {chiTiet.contractType ? LOAI_HOP_DONG[chiTiet.contractType] : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Hợp đồng đến">
            {formatDate(chiTiet.contractExpiresAt)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        title={
          <Space>
            <LockOutlined />
            <span>Thông tin bảo mật của tôi</span>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="Chỉ mình bạn và Quản trị nhân sự xem được mục này"
          description={
            'Mỗi lượt mở đều được ghi vào nhật ký bảo mật (Nghị định 13/2023/NĐ-CP). ' +
            'Cần sửa thông tin ở đây thì đề nghị phòng Tổ chức — Hành chính cập nhật hộ.'
          }
        />
        <Descriptions column={{ xs: 1, sm: 1, md: 2 }} size="small" bordered>
          <Descriptions.Item label="Số CCCD">{truongBaoMat.nationalId ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Ngày cấp">
            {truongBaoMat.nationalIdIssuedOn ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Nơi cấp" span={2}>
            {truongBaoMat.nationalIdIssuedPlace ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Lương cơ bản">
            {truongBaoMat.baseSalary ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Hệ số lương">
            {truongBaoMat.salaryCoefficient ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Số tài khoản">
            {truongBaoMat.bankAccount ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Mã số thuế">{truongBaoMat.taxCode ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Số BHXH" span={2}>
            {truongBaoMat.socialInsuranceNo ?? '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}
