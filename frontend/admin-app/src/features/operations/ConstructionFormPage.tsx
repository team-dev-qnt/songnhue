import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Space, Steps, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';

import {
  type ConstructionDetail,
  type ConstructionPurpose,
  type ConstructionType,
  type ManagementLevel,
  type PumpSpecView,
  type SluiceSpecView,
  type LinearSpecView,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

// We will extract these steps into separate files if they grow too large,
// but for now, we define them here.
import { StepBasicInfo } from './components/form-steps/StepBasicInfo';
import { StepLocation } from './components/form-steps/StepLocation';
import { StepTechnical } from './components/form-steps/StepTechnical';
import { StepFinance } from './components/form-steps/StepFinance';
import { locKhoiThongSo } from './constructionRules';
import { ConstructionChangeLogDrawer } from './components/ConstructionChangeLogDrawer';
import { ConstructionDocumentsPanel } from './components/ConstructionDocumentsPanel';
import { ConstructionMaintenancePanel } from './components/ConstructionMaintenancePanel';

export interface ConstructionFormValues {
  code: string;
  name: string;
  constructionType: ConstructionType;
  purpose?: ConstructionPurpose;
  orgUnitId: string;
  managementLevel?: ManagementLevel;
  clusterId?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  riverName?: string;
  chainage?: string;
  basinNote?: string;
  builtYear?: number;
  commissionedYear?: number;
  designer?: string;
  contractor?: string;
  totalInvestment?: number; // In VND, UI handles million conversion
  /** Hai tài liệu công bố ra cổng — CR-28. Xem `OTaiLieuCongBo` trong `StepFinance`. */
  operatingProcedureAttachmentId?: string;
  protectionPlanAttachmentId?: string;
  description?: string;
  pump?: PumpSpecView;
  sluice?: SluiceSpecView;
  linear?: LinearSpecView;
}

const steps = [
  { title: 'Thông tin chung' },
  { title: 'Vị trí & Lưu vực' },
  { title: 'Thông số kỹ thuật' },
  { title: 'Tổ chức & Tài chính' },
];

export function ConstructionFormPage() {
  const { hasPermission } = useAuth();
  const { publicId } = useParams<{ publicId: string }>();
  const isEdit = !!publicId;
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [form] = Form.useForm<ConstructionFormValues>();
  const [currentStep, setCurrentStep] = useState(0);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);

  const { data: construction, isLoading } = useQuery({
    queryKey: ['ops', 'constructions', publicId],
    queryFn: () => api.get<ConstructionDetail>(`/ops/constructions/${publicId}`),
    enabled: isEdit,
  });

  useEffect(() => {
    if (construction) {
      form.setFieldsValue({
        code: construction.summary.code,
        name: construction.summary.name,
        constructionType: construction.summary.constructionType,
        purpose: construction.purpose ?? undefined,
        orgUnitId: construction.orgUnitId ?? undefined,
        managementLevel: construction.summary.managementLevel ?? undefined,
        clusterId: construction.clusterId ?? undefined,
        address: construction.address ?? undefined,
        latitude: construction.summary.latitude ?? undefined,
        longitude: construction.summary.longitude ?? undefined,
        riverName: construction.summary.riverName ?? undefined,
        chainage: construction.summary.chainage ?? undefined,
        basinNote: construction.basinNote ?? undefined,
        builtYear: construction.builtYear ?? undefined,
        commissionedYear: construction.commissionedYear ?? undefined,
        designer: construction.designer ?? undefined,
        contractor: construction.contractor ?? undefined,
        totalInvestment: construction.totalInvestment ?? undefined,
        // ⚠ Phải nạp lại: biểu mẫu nạp thiếu một trường thì mỗi lượt Lưu ghi đè giá trị đang có
        //   bằng `undefined`, và hai liên kết trên cổng lặng lẽ biến mất sau lần sửa hồ sơ kế tiếp.
        operatingProcedureAttachmentId: construction.operatingProcedureAttachmentId ?? undefined,
        protectionPlanAttachmentId: construction.protectionPlanAttachmentId ?? undefined,
        description: construction.description ?? undefined,
        pump: construction.pump ?? undefined,
        sluice: construction.sluice ?? undefined,
        linear: construction.linear ?? undefined,
      });
    }
  }, [construction, form]);

  const save = useMutation({
    mutationFn: (values: ConstructionFormValues) => {
      // Xoá khối thông số không thuộc loại đang chọn — xem javadoc `locKhoiThongSo`.
      const payload = locKhoiThongSo(values);

      if (isEdit) {
        return api.put(`/ops/constructions/${publicId}`, payload);
      }
      return api.post('/ops/constructions', payload);
    },
    onSuccess: () => {
      message.success('Đã lưu hồ sơ công trình');
      queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
      navigate('/van-hanh/cong-trinh');
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && caught.details.length > 0) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        form.setFields(caught.fieldErrors() as any);
        return;
      }
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được hồ sơ');
    },
  });

  const next = async () => {
    try {
      // Validate current step fields before proceeding
      // We will rely on simple field validation for now
      await form.validateFields();
      setCurrentStep(currentStep + 1);
    } catch {
      // Validation failed, do not proceed
    }
  };

  const prev = () => {
    setCurrentStep(currentStep - 1);
  };

  const onFinish = (values: ConstructionFormValues) => {
    save.mutate(values);
  };

  const type = Form.useWatch('constructionType', form);

  // ⚠⚠ Ba tab của trang này thuộc BA quyền khác nhau, và tới 31/08 cả trang bị canh bằng riêng
  //    `ops:construction:update`. Đo được: XN_MANAGER (8 quyền vận hành) và XN_OPERATOR (3 quyền)
  //    không mở nổi trang ⇒ 11 quyền chưa từng dùng được, và cán bộ Xí nghiệp không có đường tải
  //    tệp Quy trình vận hành lên — đúng hai cột mà cổng công khai đang chờ (CR-28).
  //
  //    Nay tuyến nhận MẢNG quyền (HOẶC), còn đây quyết định từng tab. Nguyên tắc lấy nguyên từ
  //    chú thích nút "Nhập nhanh" ở `ConstructionsPage`: canh đúng quyền mà việc ấy đòi, không
  //    phải quyền của màn hình chứa nó.
  const coSuaHoSo = hasPermission('ops:construction:update');
  const coXemTaiLieu = hasPermission('ops:document:view');
  const coXemSuaChua = hasPermission('ops:maintenance:view');

  if (isEdit && isLoading) {
    return <div>Loading...</div>;
  }

  const formContent = (
    <>
      <Card
        title={isEdit ? 'Sửa hồ sơ công trình' : 'Thêm hồ sơ công trình'}
        extra={
          isEdit && <Button onClick={() => setHistoryDrawerOpen(true)}>Lịch sử thay đổi</Button>
        }
      >
        <Steps current={currentStep} items={steps} style={{ marginBottom: 32 }} />

        {isEdit && !coSuaHoSo ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Bạn đang xem hồ sơ ở chế độ chỉ đọc"
            description="Tài khoản của bạn không có quyền sửa hồ sơ công trình (ops:construction:update). Các tab Tài liệu đính kèm và Lịch sử sửa chữa vẫn dùng được bình thường."
          />
        ) : null}

        {/* ⛔ `disabled` ở đây là tầng 1 — chỉ để UX. Chốt thật vẫn là `@RequirePermission` ở
            controller (tầng 2) + bộ lọc phạm vi đơn vị (tầng 3): tầng 1 gỡ được bằng DevTools
            trong ba giây (§9.10.4). */}
        <Form<ConstructionFormValues>
          form={form}
          layout="vertical"
          onFinish={onFinish}
          disabled={isEdit && !coSuaHoSo}
          initialValues={{ constructionType: 'TRAM_BOM' }}
        >
          <div style={{ display: currentStep === 0 ? 'block' : 'none' }}>
            <StepBasicInfo form={form} />
          </div>
          <div style={{ display: currentStep === 1 ? 'block' : 'none' }}>
            <StepLocation />
          </div>
          <div style={{ display: currentStep === 2 ? 'block' : 'none' }}>
            <StepTechnical type={type} />
          </div>
          <div style={{ display: currentStep === 3 ? 'block' : 'none' }}>
            <StepFinance />
          </div>

          <div style={{ marginTop: 24, textAlign: 'right' }}>
            <Space>
              {currentStep > 0 && <Button onClick={() => prev()}>Quay lại</Button>}
              {currentStep < steps.length - 1 && (
                <Button type="primary" onClick={() => next()}>
                  Tiếp theo
                </Button>
              )}
              {currentStep === steps.length - 1 && (!isEdit || coSuaHoSo) && (
                <Button type="primary" htmlType="submit" loading={save.isPending}>
                  Lưu hồ sơ
                </Button>
              )}
            </Space>
          </div>
        </Form>
      </Card>

      <ConstructionChangeLogDrawer
        publicId={publicId ?? null}
        open={historyDrawerOpen}
        onClose={() => setHistoryDrawerOpen(false)}
      />
    </>
  );

  if (!isEdit) {
    return formContent;
  }

  // ⛔ Tab nào người dùng không có quyền xem thì KHÔNG dựng — dựng rồi để nó trả 403 khi bấm là
  //    mời người ta đi vào một cánh cửa khoá. Và tab đầu tiên còn lại phải là tab mặc định, nếu
  //    không AntD chọn `items[0]` — tức một tab không tồn tại.
  const tabs = [
    { key: 'form', label: 'Hồ sơ công trình', children: formContent },
    coXemTaiLieu
      ? {
          key: 'attachments',
          label: 'Tài liệu đính kèm',
          children: (
            <Card>
              <ConstructionDocumentsPanel publicId={publicId as string} />
            </Card>
          ),
        }
      : null,
    coXemSuaChua
      ? {
          key: 'history',
          label: 'Lịch sử sửa chữa',
          children: (
            <Card>
              <ConstructionMaintenancePanel constructionPublicId={publicId as string} />
            </Card>
          ),
        }
      : null,
  ].filter((tab) => tab !== null);

  return <Tabs defaultActiveKey={tabs[0].key} items={tabs} />;
}
