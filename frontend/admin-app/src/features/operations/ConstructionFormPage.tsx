import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Steps, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { AttachmentPanel } from '@/components/business/AttachmentPanel';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import {
  CONSTRUCTION_TYPE,
  MANAGEMENT_LEVEL,
} from '@/components/business/statusVocabulary';
import {
  type ConstructionDetail,
  type ConstructionPurpose,
  type ConstructionType,
  type ManagementLevel,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

// We will extract these steps into separate files if they grow too large,
// but for now, we define them here.
import { StepBasicInfo } from './components/form-steps/StepBasicInfo';
import { StepLocation } from './components/form-steps/StepLocation';
import { StepTechnical } from './components/form-steps/StepTechnical';
import { StepFinance } from './components/form-steps/StepFinance';
import { ConstructionChangeLogDrawer } from './components/ConstructionChangeLogDrawer';

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
  description?: string;
  pump?: Record<string, any>;
  sluice?: Record<string, any>;
  linear?: Record<string, any>;
}

const steps = [
  { title: 'Thông tin chung' },
  { title: 'Vị trí & Lưu vực' },
  { title: 'Thông số kỹ thuật' },
  { title: 'Tổ chức & Tài chính' },
];

export function ConstructionFormPage() {
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
        description: construction.description ?? undefined,
        pump: construction.pump ?? undefined,
        sluice: construction.sluice ?? undefined,
        linear: construction.linear ?? undefined,
      });
    }
  }, [construction, form]);

  const save = useMutation({
    mutationFn: (values: ConstructionFormValues) => {
      // Ensure only the correct spec block is sent
      const payload = {
        ...values,
        pump: values.constructionType === 'TRAM_BOM' ? values.pump : null,
        sluice: values.constructionType === 'CONG' ? values.sluice : null,
        linear: ['KENH_MUONG', 'DE_DIEU'].includes(values.constructionType) ? values.linear : null,
      };

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
        form.setFields(caught.fieldErrors());
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
    } catch (e) {
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

  if (isEdit && isLoading) {
    return <div>Loading...</div>;
  }

  const formContent = (
    <>
      <Card
        title={isEdit ? 'Sửa hồ sơ công trình' : 'Thêm hồ sơ công trình'}
        extra={
          isEdit && (
            <Button onClick={() => setHistoryDrawerOpen(true)}>
              Lịch sử thay đổi
            </Button>
          )
        }
      >
        <Steps current={currentStep} items={steps} style={{ marginBottom: 32 }} />

      <Form<ConstructionFormValues>
        form={form}
        layout="vertical"
        onFinish={onFinish}
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
            {currentStep > 0 && (
              <Button onClick={() => prev()}>Quay lại</Button>
            )}
            {currentStep < steps.length - 1 && (
              <Button type="primary" onClick={() => next()}>
                Tiếp theo
              </Button>
            )}
            {currentStep === steps.length - 1 && (
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

  return (
    <Tabs
      items={[
        {
          key: 'form',
          label: 'Hồ sơ công trình',
          children: formContent,
        },
        {
          key: 'attachments',
          label: 'Tài liệu đính kèm',
          children: (
            <Card>
              <AttachmentPanel
                ownerType="CONSTRUCTION"
                ownerId={publicId as any} // backend expects UUID for construction? The AttachmentPanel expects number. We pass publicId string
                canUpload={hasPermission('ops:construction:update')}
                canDelete={hasPermission('ops:construction:update')}
              />
            </Card>
          ),
        },
        {
          key: 'history',
          label: 'Lịch sử sửa chữa',
          children: (
            <Card>
              <div style={{ color: '#595959', padding: '24px 0', textAlign: 'center' }}>
                Lịch sử sửa chữa sẽ được tích hợp trong phiên bản sau.
              </div>
            </Card>
          ),
        },
      ]}
    />
  );
}
