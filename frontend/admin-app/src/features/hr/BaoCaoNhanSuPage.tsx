import { DownloadOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  List,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
  theme,
} from 'antd';
import { useMemo } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { BaseChart } from '@/components/charts/BaseChart';
import { optionCotNgang, optionDuong, optionTron } from '@/components/charts/chartOptions';
import { ApiClientError, api } from '@/shared/apiClient';
import { luuTep } from '@/shared/luuTep';

import { type MucBaoCaoView } from '@/shared/api-types';

import { NHAN_GIOI_TINH, NHAN_HOC_VAN, toBucket, type TongQuanNhanSu } from './baoCaoNhanSu';

/**
 * Thống kê và báo cáo nhân sự — CN-04.8 (SRS M4.14–M4.17, WS-58).
 *
 * <h2>⛔⛔ Màn hình này CẮT theo phạm vi đơn vị — và nó phải NÓI RA</h2>
 *
 * Ngược với *Danh bạ* và *Sơ đồ tổ chức* (toàn Công ty). Báo cáo là **đầu ra của hồ sơ**, mà hồ sơ
 * chịu M4.13. Hệ quả: hai người ở hai đơn vị mở cùng màn hình thấy **hai bộ số khác nhau**, và cả
 * hai đều **đúng**. Im lặng ở đây là để họ so số rồi kết luận hệ thống sai.
 *
 * <h2>⛔ Tám báo cáo, và BCNS-07 hiện KÈM LÝ DO thay vì biến mất</h2>
 *
 * Ẩn dòng chưa dựng được thì lượt nghiệm thu đếm bảy nút rồi tick đủ. Nút của nó bị vô hiệu và
 * đứng cạnh nguyên văn lý do — mẫu 2C-BNV/2008 là biểu mẫu quy định của Bộ Nội vụ, Công ty chưa
 * gửi tệp gốc (**G6**).
 */
export function BaoCaoNhanSuPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const coXuat = hasPermission('hr:report:export');

  const tongQuan = useQuery({
    queryKey: ['hr', 'bao-cao', 'tong-quan'],
    queryFn: () => api.get<TongQuanNhanSu>('/hr/bao-cao/tong-quan'),
  });

  const danhMuc = useQuery({
    queryKey: ['hr', 'bao-cao', 'danh-muc'],
    queryFn: () => api.get<MucBaoCaoView[]>('/hr/bao-cao/danh-muc'),
  });

  const kpi = tongQuan.data?.kpi;
  const d = tongQuan.data;

  const optionDonVi = useMemo(() => optionCotNgang(toBucket(d?.theoDonVi ?? [])), [d?.theoDonVi]);
  const optionGioiTinh = useMemo(
    () => optionTron(toBucket(d?.theoGioiTinh ?? [], NHAN_GIOI_TINH)),
    [d?.theoGioiTinh],
  );
  const optionHocVan = useMemo(
    () => optionTron(toBucket(d?.theoHocVan ?? [], NHAN_HOC_VAN)),
    [d?.theoHocVan],
  );
  const optionTuoi = useMemo(() => optionTron(toBucket(d?.theoNhomTuoi ?? [])), [d?.theoNhomTuoi]);
  const optionBienDong = useMemo(
    () =>
      optionDuong(
        (d?.bienDong ?? []).map((t) => t.thang),
        [
          { ten: 'Tuyển mới', giaTri: (d?.bienDong ?? []).map((t) => t.tuyenMoi) },
          { ten: 'Nghỉ việc', giaTri: (d?.bienDong ?? []).map((t) => t.nghiViec) },
          { ten: 'Điều động', giaTri: (d?.bienDong ?? []).map((t) => t.dieuDong) },
        ],
      ),
    [d?.bienDong],
  );

  const tai = async (ma: string) => {
    try {
      // ⛔ `api.getTep` — thân là BYTE, ⛔ không đi qua `unwrap` envelope (§10.52: envelope bọc
      //   `byte[]` làm ảnh cổng im lặng suốt bốn ngày).
      // ⛔⛔ Tên tệp lấy từ `Content-Disposition`, ⛔ KHÔNG tự đặt ở đây: tên là thứ người dùng lưu
      //    lại rồi gửi đi, và hai nơi đặt tên là hai cách gọi cùng một báo cáo.
      const { blob, tenTep } = await api.getTep(`/hr/bao-cao/xuat/${ma}`);
      luuTep(blob, tenTep ?? `${ma}.csv`);
    } catch (caught: unknown) {
      message.error(caught instanceof ApiClientError ? caught.message : `Không tải được ${ma}`);
    }
  };

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        title="Số liệu trên màn hình này giới hạn trong phạm vi đơn vị của bạn"
        description={
          <>
            Khác với <b>Danh bạ nội bộ</b> và <b>Sơ đồ tổ chức</b> (toàn Công ty), báo cáo nhân sự
            chỉ tính những hồ sơ bạn được xem. Hai người ở hai đơn vị mở cùng màn hình sẽ thấy hai
            bộ số khác nhau — cả hai đều đúng.
          </>
        }
      />

      <Card loading={tongQuan.isLoading}>
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}>
            <Statistic title="Đang làm việc" value={kpi?.tongDangLamViec ?? '—'} suffix="người" />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Tuyển mới tháng này" value={kpi?.tuyenMoiThangNay ?? '—'} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Nghỉ việc tháng này" value={kpi?.nghiViecThangNay ?? '—'} />
          </Col>
          <Col xs={12} md={6}>
            {/* ⛔⛔ Tỉ lệ chỉ in ra khi hệ CÓ dữ liệu nhiều hơn một tháng. Một tỉ lệ tính trên một
                tháng duy nhất là con số ĐÚNG CÔNG THỨC mà ⛔ không nói được gì — và nó sẽ đứng trên
                màn hình Ban giám đốc như một sự thật (quy tắc 16). */}
            {kpi && kpi.soThangCoDuLieu > 1 ? (
              <Statistic title="Tỷ lệ nghỉ việc" value={kpi.tyLeNghiViec} suffix="%" />
            ) : (
              <Statistic
                title="Tỷ lệ nghỉ việc"
                value="Chưa đủ dữ liệu"
                styles={{ content: { fontSize: 16 } }}
              />
            )}
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title={`Hợp đồng hết hạn ≤ ${kpi?.nguongNgayHopDong ?? '—'} ngày`}
              value={kpi?.hopDongSapHetHan ?? '—'}
              styles={{
                content:
                  kpi && kpi.hopDongSapHetHan > 0 ? { color: token.colorWarning } : undefined,
              }}
            />
          </Col>
          <Col xs={12} md={6}>
            <Statistic
              title={`Chứng chỉ hết hiệu lực ≤ ${kpi?.nguongNgayChungChi ?? '—'} ngày`}
              value={kpi?.chungChiSapHetHan ?? '—'}
              styles={{
                content:
                  kpi && kpi.chungChiSapHetHan > 0 ? { color: token.colorWarning } : undefined,
              }}
            />
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="Nhân sự theo đơn vị">
            <BaseChart
              option={optionDonVi}
              empty={(d?.theoDonVi ?? []).length === 0}
              loading={tongQuan.isLoading}
              height={320}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Biến động 12 tháng gần nhất">
            {/* ⛔ Trục thời gian gồm ĐỦ 12 tháng, kể cả tháng ⛔ không có gì — dựng trục từ chính
                dữ liệu làm một khoảng lặng ba tháng thành hai điểm liền nhau, và đường nối chúng
                nói dối về tốc độ (T46.1). */}
            <BaseChart
              option={optionBienDong}
              empty={(d?.bienDong ?? []).length === 0}
              loading={tongQuan.isLoading}
              height={320}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Cơ cấu theo giới tính">
            <BaseChart
              option={optionGioiTinh}
              empty={(d?.theoGioiTinh ?? []).length === 0}
              loading={tongQuan.isLoading}
              height={280}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Cơ cấu theo học vấn">
            <BaseChart
              option={optionHocVan}
              empty={(d?.theoHocVan ?? []).length === 0}
              loading={tongQuan.isLoading}
              height={280}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Cơ cấu theo nhóm tuổi">
            <BaseChart
              option={optionTuoi}
              empty={(d?.theoNhomTuoi ?? []).length === 0}
              loading={tongQuan.isLoading}
              height={280}
            />
          </Card>
        </Col>
      </Row>

      <Card title="Báo cáo BCNS" loading={danhMuc.isLoading}>
        <Typography.Paragraph type="secondary">
          Tệp kết xuất ở định dạng <b>CSV</b>, mở trực tiếp bằng Excel (có BOM UTF-8 và dấu tách
          <Typography.Text code>;</Typography.Text> để bản tiếng Việt đọc đúng). Bản in <b>PDF</b>{' '}
          chưa có: bố cục in của các báo cáo này còn chờ Công ty duyệt.
        </Typography.Paragraph>

        <List
          dataSource={danhMuc.data ?? []}
          renderItem={(muc) => (
            <List.Item
              actions={[
                muc.khaDung ? (
                  <Button
                    key="tai"
                    icon={<DownloadOutlined />}
                    disabled={!coXuat}
                    onClick={() => void tai(muc.ma)}
                  >
                    {coXuat ? 'Tải CSV' : 'Không có quyền xuất'}
                  </Button>
                ) : (
                  <Button key="chua" icon={<StopOutlined />} disabled>
                    Chưa xuất được
                  </Button>
                ),
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Tag color={muc.khaDung ? 'blue' : 'default'}>{muc.ma}</Tag>
                    {muc.ten}
                  </Space>
                }
                description={
                  <>
                    <div>{muc.moTa}</div>
                    {/* ⛔⛔ Lý do hiện NGUYÊN VĂN. Một dòng bị vô hiệu mà ⛔ không nói vì sao đọc
                        như một lỗi hệ thống, và người vận hành sẽ đi báo hỏng. */}
                    {muc.lyDo ? <Typography.Text type="warning">{muc.lyDo}</Typography.Text> : null}
                  </>
                }
              />
            </List.Item>
          )}
        />
      </Card>
    </Space>
  );
}
