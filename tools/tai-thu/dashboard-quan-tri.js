// =============================================================================
// NFR-02 — Dashboard điều hành: P95 < 3s với 50 người dùng (T37.2 · T61.6)
//
//   Chạy:  docker run --rm -i \
//            -e API_URL=https://admin-staging.songnhue.com/api/v1 \
//            -e TAI_KHOAN='taithu01:<mật khẩu>,taithu02:<mật khẩu>' \
//            grafana/k6:0.57.0 run - < tools/tai-thu/dashboard-quan-tri.js
//
// ⛔⛔ README.md §2 — hạn mức khoá theo IP, và ở đây nó cắn NẶNG hơn cổng công khai:
//   xô API = 100 lượt/phút cho MỘT IP. Một tab dashboard tự làm mới ~2–3 lượt/phút lúc ⛔ ai bấm gì
//   (`useDashboard` · `useStationLayer` · `unread-count`), mở trang là 4 lượt song song. 50 người sau
//   MỘT IP vượt trần ngay ở trạng thái nghỉ. Bài đo này vì vậy trả lời HAI câu, và in cả hai:
//     (1) `bi_chan_429` > 0 ⇒ hệ ⛔ phục vụ được 50 người sau một IP — ĐÓ LÀ KẾT QUẢ, ⛔ phải lỗi bài đo
//         (T61.17: Công ty ra Internet qua một IP NAT?).
//     (2) P95 của `mo_dashboard_ms` CHỈ có nghĩa khi (1) = 0.
//
// ⚠ Đăng nhập làm ở `setup()`, MỘT lần mỗi tài khoản: xô LOGIN 30/15' và nginx `api_auth` 20/phút
//   (burst 10) — 50 VU tự đăng nhập là đo tốc độ trả 429 của đường đăng nhập. Access token sống 30'
//   ⇒ tổng thời lượng mặc định 20'.
// ⚠ Tài khoản đo KHÔNG được bật 2FA (Admin/Admin HR bắt buộc 2FA — NFR-05) ⇒ dùng vai trò
//   XN_MANAGER hoặc tương đương có `ops:dashboard:view`. Tạo riêng, xoá sau lượt đo.
// =============================================================================
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const API_URL = (__ENV.API_URL || '').replace(/\/+$/, '');
if (!API_URL) fail('Thiếu API_URL (VD https://admin-staging.songnhue.com/api/v1).');
const TAI_KHOAN = (__ENV.TAI_KHOAN || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean)
  .map((cap) => {
    const i = cap.indexOf(':');
    return { username: cap.slice(0, i), password: cap.slice(i + 1) };
  });
if (TAI_KHOAN.length === 0) fail('Thiếu TAI_KHOAN="user:pass,user2:pass2" — xem đầu tệp.');
if (TAI_KHOAN.length > 10) fail('⛔ Quá 10 tài khoản: đăng nhập dồn trong setup() sẽ chạm nginx api_auth (burst 10).');

const SO_NGUOI = Number(__ENV.SO_NGUOI || 50);

export const options = {
  setupTimeout: '2m',
  scenarios: {
    dashboard: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: SO_NGUOI },
        { duration: __ENV.GIU || '15m', target: SO_NGUOI },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // NFR-02 — "P95 dashboard < 3s @ 50 users": thời gian MỞ màn hình = lượt chậm nhất của 4 lượt song song.
    mo_dashboard_ms: ['p(95)<3000'],
    bi_chan_429: ['rate==0'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const moDashboard = new Trend('mo_dashboard_ms', true);
const biChan429 = new Rate('bi_chan_429');

export function setup() {
  const phien = TAI_KHOAN.map(({ username, password }) => {
    const r = http.post(`${API_URL}/auth/login`, JSON.stringify({ username, password }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { buoc: 'dang-nhap' },
    });
    const body = r.json() || {};
    const token = body.data && body.data.accessToken;
    if (r.status !== 200 || !token) {
      fail(`Đăng nhập ${username} hỏng: HTTP ${r.status} — ${String(r.body).slice(0, 200)}`);
    }
    if (body.data.stage && body.data.stage !== 'AUTHENTICATED') {
      fail(`${username} dừng ở bước ${body.data.stage} — tài khoản đo ⛔ được bật 2FA.`);
    }
    sleep(4); // ⛔ dồn lượt đăng nhập: nginx api_auth 20/phút
    return token;
  });
  return { phien };
}

function moManHinh(token) {
  const h = { headers: { Authorization: `Bearer ${token}` } };
  const batDau = Date.now();
  const kq = http.batch([
    ['GET', `${API_URL}/ops/dashboard`, null, { ...h, tags: { api: 'dashboard' } }],
    ['GET', `${API_URL}/ops/dashboard/map-points`, null, { ...h, tags: { api: 'map-points' } }],
    ['GET', `${API_URL}/hyd/stations/map-points`, null, { ...h, tags: { api: 'station-layer' } }],
    ['GET', `${API_URL}/notifications/unread-count`, null, { ...h, tags: { api: 'unread' } }],
  ]);
  moDashboard.add(Date.now() - batDau);
  for (const r of kq) biChan429.add(r.status === 429);
  check(kq[0], {
    'dashboard 200': (r) => r.status === 200,
    // Đọc JSON, ⛔ so chuỗi `"success":true` — dấu cách hợp lệ trong JSON làm phép so chuỗi đỏ oan (luật 2).
    'dashboard có envelope success': (r) => {
      try {
        return r.json('success') === true;
      } catch (_) {
        return false;
      }
    },
  });
}

export default function (du) {
  const token = du.phien[(__VU - 1) % du.phien.length];
  moManHinh(token);
  // Ở lại màn hình như người trực: nhịp làm mới nền của `useDashboard`/`unread-count` (~60s).
  for (let i = 0; i < 3; i++) {
    sleep(55 + Math.random() * 10);
    const r = http.get(`${API_URL}/notifications/unread-count`, {
      headers: { Authorization: `Bearer ${token}` },
      tags: { api: 'unread' },
    });
    biChan429.add(r.status === 429);
  }
}
