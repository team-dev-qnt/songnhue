import { Alert, Card, Col, Row, Statistic, Typography, theme } from 'antd';

import { type SoDuPhepView } from './hrVocabulary';

/**
 * Thẻ số dư phép năm — CN-04.9.
 *
 * <h2>⛔⛔ Số 0 của ô *"Chuyển từ năm trước"* mang HAI nghĩa, và chúng khác hẳn nhau</h2>
 *
 * `namTruocCoDuLieu = false` ⇒ số 0 nghĩa là **CHƯA BIẾT** (hệ chưa vận hành năm ấy), ⛔ không phải
 * *"đã dùng hết"*. In một số 0 trần cho cả hai là để người lao động đọc một câu khẳng định mà hệ
 * thống ⛔ không có cơ sở nào để nói — quy tắc 16: **số 0 là một khẳng định**.
 *
 * <h2>⚠ `conLai` ÂM được, và ⛔ không kẹp về 0</h2>
 *
 * Một số âm là một sự thật cần nhìn thấy: nó xảy ra khi Công ty hạ tham số phép năm sau lúc người
 * ta đã nghỉ, hoặc khi một đơn được duyệt bằng đường quản trị. Kẹp về 0 là giấu đúng trạng thái
 * cần ai đó xử lý.
 *
 * <h2>⛔ Mọi con số ở đây do BACKEND tính (quy tắc 3)</h2>
 *
 * Component này ⛔ không cộng trừ gì cả — kể cả phép `duocHuong - daDung` trông vô hại. Chuỗi
 * `NUMERIC(5,1)` mà đưa qua `Number()` là mở đúng cửa sai số mà quy tắc 2 cấm.
 */
export function SoDuPhepCard({ soDu, dangTai }: { soDu?: SoDuPhepView; dangTai?: boolean }) {
  // ⛔ Màu lấy từ `theme.useToken()`, ⛔ không ghi cứng mã hex — `noHardcodedColors.test.ts`
  //   là một bậc thang CHỈ ĐƯỢC GIẢM, và một mã hex ở đây là màu của AntD chứ ⛔ không
  //   phải màu thương hiệu nên nó cũng ⛔ không thuộc về `design-tokens`.
  const { token } = theme.useToken();

  return (
    <Card title={`Số dư phép năm ${soDu?.nam ?? ''}`} loading={dangTai}>
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Statistic title="Được hưởng" value={soDu?.duocHuong ?? '—'} suffix="ngày" />
        </Col>
        <Col xs={12} md={6}>
          <Statistic title="Đã nghỉ" value={soDu?.daDung ?? '—'} suffix="ngày" />
        </Col>
        <Col xs={12} md={6}>
          <Statistic title="Đang chờ duyệt" value={soDu?.dangChoDuyet ?? '—'} suffix="ngày" />
        </Col>
        <Col xs={12} md={6}>
          <Statistic
            title="Còn lại"
            value={soDu?.conLai ?? '—'}
            suffix="ngày"
            styles={{
              content:
                soDu && soDu.conLai.trim().startsWith('-')
                  ? { color: token.colorError }
                  : undefined,
            }}
          />
        </Col>
      </Row>

      {soDu ? (
        <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
          Theo thâm niên <b>{soDu.theoThamNien}</b> ngày
          {soDu.namTruocCoDuLieu ? (
            <>
              {' '}
              + chuyển từ năm {soDu.nam - 1} <b>{soDu.chuyenTuNamTruoc}</b> ngày
            </>
          ) : null}
          .
        </Typography.Paragraph>
      ) : null}

      {soDu && !soDu.namTruocCoDuLieu ? (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          title={`Hệ thống chưa có dữ liệu nghỉ phép của năm ${soDu.nam - 1}`}
          description={
            <>
              Vì thế ô <b>chuyển từ năm trước</b> đang là 0 — nghĩa là <b>chưa biết</b>, ⛔ không
              phải <b>đã dùng hết</b>. Số phép tồn của năm đầu vận hành phải do Phòng Tổ chức — Hành
              chính nhập, hệ thống ⛔ không suy ra được.
            </>
          }
        />
      ) : null}
    </Card>
  );
}
