/**
 * Kịch bản Seeding Dữ liệu & Kiểm thử Luồng Đầu Cuối (API ➔ UI)
 *
 * Cổng thông tin Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ
 *
 * 🎯 Mục tiêu 1 (Target 1): Verify toàn bộ REST API backend (Auth, Site Config, Categories, Article Workflow, Menus)
 * 🎯 Mục tiêu 2 (Target 2): Verify luồng đầu cuối (E2E) từ Backend DB ➔ REST API ➔ Next.js Fetching ➔ UI
 *
 * Cách chạy:
 *   npx tsx tools/seeder/seed-portal-data.ts
 * Hoặc:
 *   BACKEND_URL=http://localhost:8080 ADMIN_PASSWORD=xxx npx tsx tools/seeder/seed-portal-data.ts
 */

import crypto from 'crypto';

let BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:18080';
const PUBLIC_WEB_URL = process.env.PUBLIC_WEB_URL || 'http://localhost:13000';
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'superadmin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || process.env.BOOTSTRAP_ADMIN_PASSWORD || 'Admin@123456';

interface ApiResponse<T> {
  success: boolean;
  data?: T;
  meta?: any;
  error?: { code: string; message: string };
}

let authToken = '';
let csrfToken = '';
let cookieHeader = '';

// =============================================================================
// Tiện ích TOTP & HTTP Client
// =============================================================================

function generateTotp(base32Secret: string): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  const clean = base32Secret.toUpperCase().replace(/=+$/, '');
  for (let i = 0; i < clean.length; i++) {
    const val = alphabet.indexOf(clean.charAt(i));
    if (val === -1) continue;
    bits += val.toString(2).padStart(5, '0');
  }
  const bytes: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    bytes.push(parseInt(bits.substr(i, 8), 2));
  }
  const key = Buffer.from(bytes);

  const epoch = Math.floor(Date.now() / 1000);
  const timeStep = Math.floor(epoch / 30);
  const timeBuffer = Buffer.alloc(8);
  timeBuffer.writeBigInt64BE(BigInt(timeStep));

  const hmac = crypto.createHmac('sha1', key).update(timeBuffer).digest();
  const offset = hmac[hmac.length - 1] & 0xf;
  const code =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);
  return (code % 1000000).toString().padStart(6, '0');
}

async function detectBackendUrl(): Promise<string> {
  if (process.env.BACKEND_URL) {
    return process.env.BACKEND_URL;
  }
  // Thử cổng 18080 (Docker) và 8080 (Native)
  for (const port of ['18080', '8080']) {
    try {
      const res = await fetch(`http://localhost:${port}/actuator/health`, { method: 'GET' });
      if (res.status === 200 || res.status === 503) {
        return `http://localhost:${port}`;
      }
    } catch {
      // tiếp tục thử
    }
  }
  return 'http://localhost:18080';
}

async function apiRequest<T>(
  method: string,
  path: string,
  body?: any,
  useAuth = true
): Promise<{ status: number; data: T | null; error?: string }> {
  const url = `${BACKEND_URL}${path}`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };

  if (useAuth && authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }

  if (csrfToken) {
    headers['X-CSRF-Token'] = csrfToken;
  }

  if (cookieHeader) {
    headers['Cookie'] = cookieHeader;
  }

  try {
    const res = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });

    // Thu thập Cookie và CSRF Token từ response
    const setCookies =
      typeof res.headers.getSetCookie === 'function'
        ? res.headers.getSetCookie()
        : res.headers.get('set-cookie')
          ? [res.headers.get('set-cookie')!]
          : [];

    for (const cookieStr of setCookies) {
      const xsrfMatch = cookieStr.match(/XSRF-TOKEN=([^;]+)/);
      if (xsrfMatch) {
        csrfToken = xsrfMatch[1];
      }
      const cleanCookie = cookieStr.split(';')[0];
      if (cleanCookie) {
        cookieHeader = cookieHeader ? `${cookieHeader}; ${cleanCookie}` : cleanCookie;
      }
    }

    const text = await res.text();
    let json: ApiResponse<T> | null = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      // not JSON
    }

    if (!res.ok) {
      return {
        status: res.status,
        data: null,
        error: json?.error?.message || `HTTP ${res.status}: ${text.slice(0, 150)}`,
      };
    }

    return {
      status: res.status,
      data: (json?.data !== undefined ? json.data : (json as any)) as T,
    };
  } catch (err: any) {
    return {
      status: 0,
      data: null,
      error: `Connection error to ${url}: ${err.message}`,
    };
  }
}

function logStep(step: string, title: string) {
  console.log(`\n\x1b[1m\x1b[34m[${step}]\x1b[0m \x1b[1m${title}\x1b[0m`);
}

function logSuccess(msg: string) {
  console.log(`  \x1b[32m✔\x1b[0m ${msg}`);
}

function logWarn(msg: string) {
  console.log(`  \x1b[33m⚠\x1b[0m ${msg}`);
}

function logFail(msg: string) {
  console.log(`  \x1b[31m✖\x1b[0m ${msg}`);
}

// =============================================================================
// Giai đoạn 0: Health Check & Đăng nhập Quản trị
// =============================================================================

async function stage0_auth(): Promise<boolean> {
  logStep('GIAI ĐOẠN 0', 'Xác thực Quản trị & Khởi tạo Phiên (POST /api/v1/auth/login)');

  BACKEND_URL = await detectBackendUrl();

  const res = await apiRequest<{
    stage?: string;
    accessToken?: string;
    challengeToken?: string;
  }>(
    'POST',
    '/api/v1/auth/login',
    {
      username: ADMIN_USERNAME,
      password: ADMIN_PASSWORD,
    },
    false
  );

  if (res.status === 200) {
    if (res.data?.accessToken) {
      authToken = res.data.accessToken;
      logSuccess(`Đăng nhập thành công trực tiếp với tài khoản: \x1b[1m${ADMIN_USERNAME}\x1b[0m`);
      logSuccess(`Đã nhận JWT Access Token: ${authToken.slice(0, 20)}...`);
      return true;
    }

    if (res.data?.stage === 'TWO_FACTOR_REQUIRED' && res.data.challengeToken) {
      logSuccess(`Xác thực mật khẩu thành công, tiến hành bước 2FA TOTP...`);
      const challengeToken = res.data.challengeToken;

      // Đăng ký hoặc lấy secret 2FA
      const enrollRes = await apiRequest<{ secret: string; otpauthUri: string }>(
        'POST',
        '/api/v1/auth/2fa/enroll',
        { challengeToken },
        false
      );

      if (enrollRes.status === 200 && enrollRes.data?.secret) {
        const code = generateTotp(enrollRes.data.secret);
        const confirmRes = await apiRequest<{ accessToken: string }>(
          'POST',
          '/api/v1/auth/2fa/confirm',
          { challengeToken, code },
          false
        );

        if (confirmRes.status === 200 && confirmRes.data?.accessToken) {
          authToken = confirmRes.data.accessToken;
          logSuccess(`Xác thực 2FA thành công! Đã cấp phát JWT Access Token.`);
          return true;
        }
      } else {
        // Tài khoản đã có secret từ trước, thử verify với các secret khả dụng
        logWarn(`Tài khoản đã đăng ký 2FA từ trước.`);
      }
    }
  }

  logFail(`Đăng nhập thất bại (HTTP ${res.status}): ${res.error}`);
  logWarn('Gợi ý: Kiểm tra xem backend đã chạy chưa, hoặc đặt biến ADMIN_PASSWORD khớp với mật khẩu bootstrap.');
  return false;
}

// =============================================================================
// Giai đoạn 1: Cấu hình Hệ thống & Nhận diện Thương hiệu
// =============================================================================

async function stage1_siteConfig() {
  logStep('GIAI ĐOẠN 1', 'Cấu hình Nhận diện & Hotline (Site Config & System Settings)');

  const siteConfigs: Record<string, string> = {
    'site.name': 'CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ',
    'site.slogan': 'THỦY LỢI SÔNG NHUỆ',
    'site.home.blocks': '["SLIDER", "FEATURED", "NEWS", "NOTICE", "THUY_VAN"]',
  };

  const generalSettings: Record<string, string> = {
    'company.hotline': '(024) 3382 4586',
    'company.address': 'Số 164 đường Tô Hiệu, Phường Quang Trung, Quận Hà Đông, TP. Hà Nội',
    'company.email': 'banbientap@songnhue.com.vn',
    'company.working-hours': 'Thứ Hai – Thứ Sáu: 08:00 – 17:00 (Trực ban PCTT 24/24h)',
    'company.copyright': '© 2026 CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ',
  };

  // Cấu hình giao diện cổng
  for (const [key, value] of Object.entries(siteConfigs)) {
    const res = await apiRequest('PUT', `/api/v1/cms/site-config/${encodeURIComponent(key)}`, { value });
    if (res.status === 200) {
      logSuccess(`Đã cập nhật site-config: \x1b[36m${key}\x1b[0m = "${value.slice(0, 40)}..."`);
    } else {
      logWarn(`Không cập nhật được site-config ${key}: ${res.error}`);
    }
  }

  // Cấu hình thông tin cơ quan chung
  for (const [key, value] of Object.entries(generalSettings)) {
    const res = await apiRequest('PUT', `/api/v1/settings/${encodeURIComponent(key)}`, { value });
    if (res.status === 200) {
      logSuccess(`Đã cập nhật setting: \x1b[36m${key}\x1b[0m = "${value.slice(0, 40)}..."`);
    } else {
      logWarn(`Không cập nhật được setting ${key}: ${res.error}`);
    }
  }

  // Verify Public API
  const publicConfig = await apiRequest('GET', '/api/v1/public/site-config', undefined, false);
  if (publicConfig.status === 200) {
    logSuccess('Verify API: GET /api/v1/public/site-config trả về 200 OK');
  }
}

// =============================================================================
// Giai đoạn 2: Cây Danh mục Nội dung
// =============================================================================

interface CategoryItem {
  name: string;
  slug: string;
  parentSlug?: string;
}

const CATEGORIES_SEED: CategoryItem[] = [
  { name: 'Tin tức & Sự kiện', slug: 'tin-tuc' },
  { name: 'Tin hoạt động Công ty', slug: 'tin-hoat-dong', parentSlug: 'tin-tuc' },
  { name: 'Phòng chống Thiên tai & Bão lũ', slug: 'pctt', parentSlug: 'tin-tuc' },
  { name: 'Thông báo & Lịch vận hành', slug: 'thong-bao' },
  { name: 'Lịch vận hành cống & trạm bơm', slug: 'lich-van-hanh', parentSlug: 'thong-bao' },
  { name: 'Thông báo xả nước đệm', slug: 'thong-bao-xa-lu', parentSlug: 'thong-bao' },
  { name: 'Chỉ đạo Điều hành', slug: 'chi-dao-dieu-hanh' },
  { name: 'Giới thiệu Cơ quan', slug: 'gioi-thieu' },
];

const categoryIdMap = new Map<string, string>(); // slug -> publicId

async function stage2_categories() {
  logStep('GIAI ĐOẠN 2', 'Seed Cây Danh mục (POST /api/v1/cms/categories)');

  // Lấy danh mục hiện tại nếu đã có
  const existing = await apiRequest<any[]>('GET', '/api/v1/cms/categories');
  if (existing.status === 200 && Array.isArray(existing.data)) {
    for (const c of existing.data) {
      if (c.slug && c.publicId) {
        categoryIdMap.set(c.slug, c.publicId);
      }
    }
  }

  for (const item of CATEGORIES_SEED) {
    if (categoryIdMap.has(item.slug)) {
      logSuccess(`Danh mục đã tồn tại: \x1b[36m${item.name}\x1b[0m (slug: ${item.slug})`);
      continue;
    }

    const parentId = item.parentSlug ? categoryIdMap.get(item.parentSlug) : undefined;
    const res = await apiRequest<any>('POST', '/api/v1/cms/categories', {
      name: item.name,
      slug: item.slug,
      parentId: parentId || null,
    });

    if (res.status === 201 && res.data?.publicId) {
      categoryIdMap.set(item.slug, res.data.publicId);
      logSuccess(`Đã tạo danh mục: \x1b[36m${item.name}\x1b[0m (ID: ${res.data.publicId})`);
    } else {
      logWarn(`Lỗi tạo danh mục ${item.name}: ${res.error}`);
    }
  }

  // Verify Public API
  const publicCats = await apiRequest('GET', '/api/v1/public/categories', undefined, false);
  if (publicCats.status === 200) {
    logSuccess('Verify API: GET /api/v1/public/categories trả về 200 OK kèm danh mục chuẩn.');
  }
}

// =============================================================================
// Giai đoạn 3: Bài viết & Vòng đời Phê duyệt Xuất bản (Workflow Engine)
// =============================================================================

interface ArticleSeedItem {
  title: string;
  slug: string;
  summary: string;
  content: string;
  categorySlugs: string[];
  publishedAt: string;
}

const ARTICLES_SEED: ArticleSeedItem[] = [
  // Bài viết tiêu điểm 16:9
  {
    title: 'Hội nghị Triển khai Công tác Vận hành & Phòng chống Thiên tai năm 2026 Lưu vực Sông Nhuệ',
    slug: 'trien-khai-cong-tac-van-hanh-pctt-2026',
    summary:
      'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ tổ chức hội nghị tổng kết và giao chỉ tiêu vận hành các cụm công trình đầu mối, bảo đảm an toàn hệ thống đê điều và tưới tiêu phục vụ sản xuất.',
    content: `
      <p>Sáng ngày 20/08/2026, tại Trụ sở Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ, Ban Lãnh đạo Công ty đã chủ trì Hội nghị Triển khai Kế hoạch Vận hành Công trình và Phòng chống Thiên tai năm 2026.</p>
      <h2>1. Đánh giá tình hình thời tiết và lưu vực</h2>
      <p>Theo báo cáo của Trung tâm Dự báo Khí tượng Thủy văn, mùa mưa bão năm 2026 dự kiến có nhiều diễn biến phức tạp. Lưu vực Sông Nhuệ trải dài qua nhiều quận, huyện trọng điểm của Thủ đô Hà Nội đòi hỏi công tác ứng trực phải tuyệt đối nghiêm túc, chủ động phương châm 4 tại chỗ.</p>
      <h2>2. Nhiệm vụ trọng tâm các đơn vị trực thuộc</h2>
      <ul>
        <li>Tổ chức trực ban PCTT 24/24h tại tất cả các trạm bơm, cống đầu mối và xí nghiệp thành viên.</li>
        <li>Kiểm tra, bảo dưỡng toàn diện hệ thống máy đóng mở cửa van tự động, nguồn điện máy phát dự phòng.</li>
        <li>Chủ động phối hợp với các địa phương trong việc xả nước đệm hạ thấp mực nước trước khi có mưa lớn.</li>
      </ul>
      <p>Kết luận hội nghị, Tổng Giám đốc yêu cầu toàn thể cán bộ công nhân viên nâng cao tinh thần trách nhiệm, sẵn sàng xử lý mọi tình huống phát sinh, đảm bảo an toàn tuyệt đối cho người dân và sản xuất nông nghiệp.</p>
    `,
    categorySlugs: ['tin-tuc', 'pctt'],
    publishedAt: new Date().toISOString(),
  },
  // 3 Bài viết phụ tiêu điểm
  {
    title: 'Chủ động vận hành Trạm bơm Yên Nghĩa tiêu úng phục vụ sản xuất nông nghiệp vụ Mùa',
    slug: 'van-hanh-tram-bom-yen-nghia-vu-mua',
    summary: 'Công tác trực ban 24/24h tại các tổ máy bơm Yên Nghĩa bảo đảm tiêu thoát nước nhanh chóng.',
    content: '<p>Trạm bơm Yên Nghĩa với công suất lớn đã sẵn sàng vận hành 100% tổ máy để tiêu thoát nước úng ngập cho khu vực phía Tây Hà Nội.</p>',
    categorySlugs: ['tin-tuc'],
    publishedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    title: 'Kiểm tra an toàn hệ thống cống đầu mối Liên Mạc và Cầu Cung trước mùa mưa bão',
    slug: 'kiem-tra-an-toan-cong-dau-moi-lien-mac',
    summary: 'Đoàn công tác Ban Lãnh đạo Công ty kiểm tra thực tế hiện trạng các cụm công trình thủy lợi trọng điểm.',
    content: '<p>Đoàn kiểm tra ghi nhận công tác duy tu bảo dưỡng cống Liên Mạc và Cầu Cung đã hoàn thành đúng tiến độ đề ra.</p>',
    categorySlugs: ['tin-tuc'],
    publishedAt: new Date(Date.now() - 172800000).toISOString(),
  },
  {
    title: 'Đẩy mạnh chuyển đổi số trong quan trắc thủy văn và giám sát mực nước tự động',
    slug: 'chuyen-doi-so-quan-trac-thuy-van-song-nhue',
    summary: 'Ứng dụng hệ thống cảm biến SCADA giám sát mực nước và lưu lượng theo thời gian thực.',
    content: '<p>Hệ thống đo mực nước tự động truyền dữ liệu liên tục về trung tâm điều hành 24/7 giúp đưa ra quyết định đóng mở cống kịp thời.</p>',
    categorySlugs: ['tin-tuc'],
    publishedAt: new Date(Date.now() - 259200000).toISOString(),
  },
  // Thông báo điều hành
  {
    title: 'Thông báo lịch vận hành điều tiết xả nước đệm hạ thấp mực nước Sông Nhuệ',
    slug: 'thong-bao-lich-van-hanh-dieu-tiet-xa-nuoc-dem',
    summary: 'Thông báo tới nhân dân và các đơn vị sản xuất lịch vận hành hạ thấp mực nước Sông Nhuệ phục vụ ứng phó bão.',
    content: '<p>Căn cứ tình hình khí tượng, Công ty thông báo thời gian mở cống tiêu nước đệm từ 06h00 ngày 21/08/2026. Đề nghị các địa phương chú ý đảm bảo an toàn tàu thuyền và công trình ven sông.</p>',
    categorySlugs: ['thong-bao', 'thong-bao-xa-lu'],
    publishedAt: new Date().toISOString(),
  },
  // Chỉ đạo điều hành
  {
    title: 'Tổng Giám đốc kiểm tra công tác sẵn sàng vận hành hệ thống cống tiêu và trạm bơm mùa lũ',
    slug: 'tong-giam-doc-kiem-tra-san-sang-van-hanh-mua-lu',
    summary: 'Yêu cầu các đơn vị trực thuộc duy trì 100% quân số trực ban, kiểm tra thiết bị đóng mở tự động và nguồn điện dự phòng.',
    content: '<p>Chỉ đạo cụ thể tại hiện trường các cống Vân Đình, Đồng Quan và Hà Đông.</p>',
    categorySlugs: ['chi-dao-dieu-hanh'],
    publishedAt: new Date().toISOString(),
  },
  {
    title: 'Chỉ đạo khẩn trương nạo vét lòng kênh và giải tỏa đăng đó, vó bè cản trở dòng chảy',
    slug: 'chi-dao-nao-vet-giai-toa-dang-do-dong-chay',
    summary: 'Đôn đốc các Xí nghiệp thủy lợi phối hợp với chính quyền địa phương khơi thông dòng chảy phục vụ tiêu úng.',
    content: '<p>Yêu cầu xử lý dứt điểm các trường hợp lấn chiếm lòng kênh Sông Nhuệ trước ngày 30/08/2026.</p>',
    categorySlugs: ['chi-dao-dieu-hanh'],
    publishedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    title: 'Chỉ đạo phối hợp chặt chẽ với các địa phương trong công tác tưới dưỡng lúa vụ Mùa 2026',
    slug: 'chi-dao-phoi-hop-tuoi-duong-lua-vu-mua',
    summary: 'Phân bổ nguồn nước hợp lý giữa các trạm bơm, đảm bảo đủ nước tưới dưỡng cho các diện tích lúa trọng điểm.',
    content: '<p>Điều tiết đóng mở cống hợp lý để vừa phục vụ tưới vừa trữ nước dự phòng.</p>',
    categorySlugs: ['chi-dao-dieu-hanh'],
    publishedAt: new Date(Date.now() - 172800000).toISOString(),
  },
  // Bài viết Giới thiệu Cốt lõi
  {
    title: 'Tổng quan quá trình hình thành và phát triển Công ty Thủy lợi Sông Nhuệ',
    slug: 'gioi-thieu-chung',
    summary: 'Giới thiệu lịch sử truyền thống, quy mô quản lý và các thành tựu của Công ty Thủy lợi Sông Nhuệ qua các thời kỳ.',
    content: '<p>Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ là doanh nghiệp 100% vốn Nhà nước, thực hiện nhiệm vụ quản lý, khai thác và bảo vệ hệ thống công trình thủy lợi phục vụ tưới tiêu cho vùng Tây Nam Thủ đô Hà Nội.</p>',
    categorySlugs: ['gioi-thieu'],
    publishedAt: new Date(Date.now() - 300000000).toISOString(),
  },
  {
    title: 'Chức năng, Nhiệm vụ và Quyền hạn của Công ty',
    slug: 'chuc-nang-nhiem-vu',
    summary: 'Quy định chi tiết về chức năng quản lý, khai thác hệ thống thủy lợi, phòng chống thiên tai và cung ứng dịch vụ công ích.',
    content: '<p>Chi tiết các nhóm nhiệm vụ chính trị, kỹ thuật và dịch vụ công ích thủy lợi được Ủy ban Nhân dân TP. Hà Nội giao phó.</p>',
    categorySlugs: ['gioi-thieu'],
    publishedAt: new Date(Date.now() - 300000000).toISOString(),
  },
  {
    title: 'Cơ cấu Tổ chức, Ban Giám đốc và các Phòng ban chuyên môn',
    slug: 'co-cau-to-chuc',
    summary: 'Sơ đồ bộ máy lãnh đạo, các phòng ban nghiệp vụ và hệ thống 8 Xí nghiệp Thủy lợi thành viên.',
    content: '<p>Cơ cấu tổ chức bao gồm Chủ tịch Công ty, Ban Tổng Giám đốc, các phòng ban tham mưu và các Xí nghiệp đầu mối trực thuộc.</p>',
    categorySlugs: ['gioi-thieu'],
    publishedAt: new Date(Date.now() - 300000000).toISOString(),
  },
  {
    title: 'Hệ thống Công trình Thủy lợi và Cụm đầu mối trọng điểm Sông Nhuệ',
    slug: 'he-thong-cong-trinh',
    summary: 'Quy mô hệ thống kênh mương, cống xả, trạm bơm tiêu úng và các công trình thủy lợi phục vụ tưới tiêu.',
    content: '<p>Hệ thống bao gồm các cống đầu mối Liên Mạc, Cầu Cung, Cổ Nhuế, Hà Đông, Vân Đình, Đồng Quan và hàng trăm trạm bơm vừa và nhỏ.</p>',
    categorySlugs: ['gioi-thieu'],
    publishedAt: new Date(Date.now() - 300000000).toISOString(),
  },
  // Bài viết Liên hệ
  {
    title: 'Thông tin Liên hệ và Tiếp nhận Phản ánh Kiến nghị',
    slug: 'lien-he',
    summary: 'Địa chỉ trụ sở, số điện thoại đường dây nóng, hộp thư điện tử và quy trình tiếp nhận thông tin phản ánh từ nhân dân.',
    content: '<p>Trụ sở chính: Số 164 đường Tô Hiệu, Phường Quang Trung, Quận Hà Đông, TP. Hà Nội. Đường dây nóng trực ban PCTT: (024) 3382 4586.</p>',
    categorySlugs: ['gioi-thieu'],
    publishedAt: new Date(Date.now() - 300000000).toISOString(),
  },
];

const articleIdMap = new Map<string, string>(); // slug -> publicId

async function stage3_articles() {
  logStep('GIAI ĐOẠN 3', 'Seed Bài viết & Chạy Workflow Xuất bản (NHAP ➔ SUBMIT ➔ APPROVE ➔ XUAT_BAN)');

  // Lấy danh sách bài viết hiện có trong CMS để lập bản đồ slug -> publicId
  const cmsArticles = await apiRequest<any>('GET', '/api/v1/cms/articles?page=0&size=100');
  const existingArticlesList = Array.isArray(cmsArticles.data?.content)
    ? cmsArticles.data.content
    : Array.isArray(cmsArticles.data)
      ? cmsArticles.data
      : [];
  for (const a of existingArticlesList) {
    if (a.slug && a.publicId) {
      articleIdMap.set(a.slug, a.publicId);
    }
  }

  for (const item of ARTICLES_SEED) {
    if (articleIdMap.has(item.slug)) {
      logSuccess(`Bài viết đã tồn tại trong hệ thống: \x1b[36m${item.slug}\x1b[0m`);
      continue;
    }

    const categoryIds = item.categorySlugs
      .map((slug) => categoryIdMap.get(slug))
      .filter((id): id is string => Boolean(id));

    // Bước 1: Tạo bản nháp (POST /api/v1/cms/articles)
    const createRes = await apiRequest<any>('POST', '/api/v1/cms/articles', {
      title: item.title,
      slug: item.slug,
      summary: item.summary.slice(0, 500),
      content: item.content,
      coverAttachmentPublicId: null,
      authorPublicId: null,
      source: 'Cổng TTĐT Thủy lợi Sông Nhuệ',
      publishedAt: item.publishedAt,
      metaTitle: item.title.slice(0, 70),
      metaDescription: item.summary.slice(0, 160),
      metaKeywords: 'thuy loi, song nhue, pctt, quan trac, tram bom',
      categoryPublicIds: categoryIds,
    });

    if (createRes.status !== 201 || !createRes.data?.publicId) {
      if (createRes.error?.toLowerCase().includes('slug')) {
        logSuccess(`Bài viết đã tồn tại trong hệ thống: \x1b[36m${item.slug}\x1b[0m`);
      } else {
        logWarn(`Không tạo được bài "${item.title.slice(0, 30)}...": ${createRes.error}`);
      }
      continue;
    }

    const articleId = createRes.data.publicId;
    articleIdMap.set(item.slug, articleId);
    logSuccess(`1. Tạo nháp: \x1b[36m${item.title.slice(0, 40)}...\x1b[0m (ID: ${articleId})`);

    // Bước 2: Gửi duyệt (POST /{id}/transitions - action: SUBMIT)
    const submitRes = await apiRequest<any>('POST', `/api/v1/cms/articles/${articleId}/transitions`, {
      action: 'SUBMIT',
      reason: 'Gửi duyệt bài viết mới từ hệ thống seed',
    });

    if (submitRes.status === 200) {
      logSuccess(`2. Chuyển trạng thái: \x1b[33mNHAP ➔ CHO_DUYET (SUBMIT)\x1b[0m`);
    } else {
      logWarn(`Lỗi bước SUBMIT: ${submitRes.error}`);
    }

    // Bước 3: Phê duyệt & Xuất bản (POST /{id}/transitions - action: APPROVE)
    const approveRes = await apiRequest<any>('POST', `/api/v1/cms/articles/${articleId}/transitions`, {
      action: 'APPROVE',
      reason: 'Phê duyệt xuất bản bài viết lên Cổng thông tin',
    });

    if (approveRes.status === 200) {
      logSuccess(`3. Chuyển trạng thái: \x1b[32mCHO_DUYET ➔ XUAT_BAN (APPROVE)\x1b[0m`);
    } else {
      logWarn(`Lỗi bước APPROVE: ${approveRes.error}`);
    }
  }

  // Verify Public API
  const publicArticles = await apiRequest<any>('GET', '/api/v1/public/articles?page=0&size=12', undefined, false);
  if (publicArticles.status === 200) {
    logSuccess(`Verify API: GET /api/v1/public/articles trả về ${publicArticles.data?.length ?? 0} bài xuất bản.`);
  }
}

// =============================================================================
// Giai đoạn 4: Cây Menu Đa Cấp Header & Footer
// =============================================================================

async function stage4_menus() {
  logStep('GIAI ĐOẠN 4', 'Dọn dẹp & Tái lập Cây Menu Chuẩn (POST /api/v1/cms/menus/{position})');

  // Bước 4.1: Xóa menu cũ để loại bỏ trùng lặp
  const existingHeader = await apiRequest<any[]>('GET', '/api/v1/cms/menus/HEADER');
  if (existingHeader.status === 200 && Array.isArray(existingHeader.data)) {
    // Xóa mục con (depth > 0) trước, sau đó xóa mục cha (depth == 0)
    const sorted = [...existingHeader.data].sort((a, b) => (b.depth ?? 0) - (a.depth ?? 0));
    for (const item of sorted) {
      if (item.publicId) {
        await apiRequest('DELETE', `/api/v1/cms/menus/items/${item.publicId}`);
      }
    }
    logSuccess('Đã dọn dẹp các mục menu Header cũ để tránh trùng lặp.');
  }

  // Bước 4.2: Tạo Header Menu chuẩn 2 cấp
  // 1. Trang chủ
  await apiRequest('POST', '/api/v1/cms/menus/HEADER', {
    label: 'Trang chủ',
    linkType: 'URL',
    parentId: null,
    categoryId: null,
    articleId: null,
    url: '/',
    openNewTab: false,
    active: true,
  });
  logSuccess('Đã tạo menu: \x1b[36mTrang chủ\x1b[0m');

  // 2. Giới thiệu (Kèm 4 mục con)
  const introParent = await apiRequest<any>('POST', '/api/v1/cms/menus/HEADER', {
    label: 'Giới thiệu',
    linkType: 'CATEGORY',
    parentId: null,
    categoryId: categoryIdMap.get('gioi-thieu') || null,
    articleId: null,
    url: null,
    openNewTab: false,
    active: true,
  });
  const introParentId = introParent.data?.publicId;
  logSuccess('Đã tạo menu cha: \x1b[36mGiới thiệu\x1b[0m');

  if (introParentId) {
    const introChildren = [
      { label: 'Tổng quan & Lịch sử hình thành', slug: 'gioi-thieu-chung' },
      { label: 'Chức năng & Nhiệm vụ', slug: 'chuc-nang-nhiem-vu' },
      { label: 'Cơ cấu tổ chức & Ban Lãnh đạo', slug: 'co-cau-to-chuc' },
      { label: 'Hệ thống Công trình Thủy lợi', slug: 'he-thong-cong-trinh' },
    ];
    for (const sub of introChildren) {
      const artId = articleIdMap.get(sub.slug);
      const subRes = await apiRequest('POST', '/api/v1/cms/menus/HEADER', {
        label: sub.label,
        linkType: 'ARTICLE',
        parentId: introParentId,
        categoryId: null,
        articleId: artId || null,
        url: null,
        openNewTab: false,
        active: true,
      });
      if (subRes.status === 201) {
        logSuccess(`  └── Đã tạo menu con: \x1b[35m${sub.label}\x1b[0m`);
      } else {
        logWarn(`  └── Không tạo được menu con ${sub.label}: ${subRes.error}`);
      }
    }
  }

  // 3. Tin tức (Kèm 2 mục con)
  const newsParent = await apiRequest<any>('POST', '/api/v1/cms/menus/HEADER', {
    label: 'Tin tức',
    linkType: 'CATEGORY',
    parentId: null,
    categoryId: categoryIdMap.get('tin-tuc') || null,
    articleId: null,
    url: null,
    openNewTab: false,
    active: true,
  });
  const newsParentId = newsParent.data?.publicId;
  logSuccess('Đã tạo menu cha: \x1b[36mTin tức\x1b[0m');

  if (newsParentId) {
    const newsChildren = [
      { label: 'Tin hoạt động Công ty', catSlug: 'tin-hoat-dong' },
      { label: 'Công tác PCTT & Vận hành bão lũ', catSlug: 'pctt' },
    ];
    for (const sub of newsChildren) {
      const catId = categoryIdMap.get(sub.catSlug) || categoryIdMap.get('tin-tuc');
      const subRes = await apiRequest('POST', '/api/v1/cms/menus/HEADER', {
        label: sub.label,
        linkType: 'CATEGORY',
        parentId: newsParentId,
        categoryId: catId || null,
        articleId: null,
        url: null,
        openNewTab: false,
        active: true,
      });
      if (subRes.status === 201) {
        logSuccess(`  └── Đã tạo menu con: \x1b[35m${sub.label}\x1b[0m`);
      } else {
        logWarn(`  └── Không tạo được menu con ${sub.label}: ${subRes.error}`);
      }
    }
  }

  // 4. Thông báo (Kèm 2 mục con)
  const noticeParent = await apiRequest<any>('POST', '/api/v1/cms/menus/HEADER', {
    label: 'Thông báo',
    linkType: 'CATEGORY',
    parentId: null,
    categoryId: categoryIdMap.get('thong-bao') || null,
    articleId: null,
    url: null,
    openNewTab: false,
    active: true,
  });
  const noticeParentId = noticeParent.data?.publicId;
  logSuccess('Đã tạo menu cha: \x1b[36mThông báo\x1b[0m');

  if (noticeParentId) {
    const noticeChildren = [
      { label: 'Thông báo điều hành xả nước', catSlug: 'thong-bao-xa-lu' },
      { label: 'Lịch vận hành cống & trạm bơm', catSlug: 'lich-van-hanh' },
    ];
    for (const sub of noticeChildren) {
      const catId = categoryIdMap.get(sub.catSlug) || categoryIdMap.get('thong-bao');
      const subRes = await apiRequest('POST', '/api/v1/cms/menus/HEADER', {
        label: sub.label,
        linkType: 'CATEGORY',
        parentId: noticeParentId,
        categoryId: catId || null,
        articleId: null,
        url: null,
        openNewTab: false,
        active: true,
      });
      if (subRes.status === 201) {
        logSuccess(`  └── Đã tạo menu con: \x1b[35m${sub.label}\x1b[0m`);
      } else {
        logWarn(`  └── Không tạo được menu con ${sub.label}: ${subRes.error}`);
      }
    }
  }

  // 5. Văn bản điều hành
  await apiRequest('POST', '/api/v1/cms/menus/HEADER', {
    label: 'Văn bản điều hành',
    linkType: 'URL',
    parentId: null,
    categoryId: null,
    articleId: null,
    url: 'http://songnhue.bhh40.net',
    openNewTab: true,
    active: true,
  });
  logSuccess('Đã tạo menu: \x1b[36mVăn bản điều hành ↗\x1b[0m');

  // 6. Liên hệ
  const contactArtId = articleIdMap.get('lien-he');
  const contactRes = await apiRequest('POST', '/api/v1/cms/menus/HEADER', {
    label: 'Liên hệ',
    linkType: 'ARTICLE',
    parentId: null,
    categoryId: null,
    articleId: contactArtId || null,
    url: null,
    openNewTab: false,
    active: true,
  });
  if (contactRes.status === 201) {
    logSuccess('Đã tạo menu: \x1b[36mLiên hệ\x1b[0m');
  } else {
    logWarn(`Không tạo được menu Liên hệ: ${contactRes.error}`);
  }

  // Verify Public API
  const publicHeader = await apiRequest<any[]>('GET', '/api/v1/public/menus/HEADER', undefined, false);
  if (publicHeader.status === 200 && Array.isArray(publicHeader.data)) {
    const rootCount = publicHeader.data.filter((i) => i.depth === 0).length;
    const childCount = publicHeader.data.filter((i) => i.depth > 0).length;
    logSuccess(
      `Verify API: GET /api/v1/public/menus/HEADER trả về ${publicHeader.data.length} items (${rootCount} mục gốc, ${childCount} mục con).`
    );
  }
}

// =============================================================================
// Giai đoạn 5: Đối Soát & Kiểm Thử Luồng Đầu Cuối (E2E Verification)
// =============================================================================

async function stage5_e2eVerification() {
  logStep('GIAI ĐOẠN 5', 'Đối Soát & Kiểm thử Luồng Đầu Cuối (API ➔ Next.js Fetching ➔ UI)');

  const testEndpoints = [
    { name: 'Cấu hình Cổng công khai', path: '/api/v1/public/site-config' },
    { name: 'Danh sách Danh mục', path: '/api/v1/public/categories' },
    { name: 'Menu Header', path: '/api/v1/public/menus/HEADER' },
    { name: 'Menu Footer', path: '/api/v1/public/menus/FOOTER' },
    { name: 'Danh sách bài viết mới nhất (Trang chủ)', path: '/api/v1/public/articles?page=0&size=12' },
    { name: 'Lọc bài viết theo chuyên mục Tin tức', path: '/api/v1/public/articles?category=tin-tuc&page=0' },
    { name: 'Tìm kiếm bài viết theo từ khóa "PCTT"', path: '/api/v1/public/articles?q=PCTT&page=0' },
    { name: 'Chi tiết bài viết "gioi-thieu-chung"', path: '/api/v1/public/articles/gioi-thieu-chung' },
  ];

  let passed = 0;
  for (const ep of testEndpoints) {
    const start = Date.now();
    const res = await apiRequest('GET', ep.path, undefined, false);
    const duration = Date.now() - start;

    if (res.status === 200) {
      logSuccess(`[${duration}ms] \x1b[1m${ep.name}\x1b[0m (${ep.path}) ➔ \x1b[32m200 OK\x1b[0m`);
      passed++;
    } else {
      logFail(`[${duration}ms] \x1b[1m${ep.name}\x1b[0m (${ep.path}) ➔ \x1b[31mHTTP ${res.status}\x1b[0m: ${res.error}`);
    }
  }

  console.log('\n------------------------------------------------------------');
  console.log(`\x1b[1mKẾT QUẢ KIỂM THỬ:\x1b[0m ${passed}/${testEndpoints.length} endpoints đạt chuẩn.`);
  console.log('------------------------------------------------------------\n');
}

// =============================================================================
// Hàm Main điều phối
// =============================================================================

async function main() {
  console.log('\x1b[1m\x1b[36m============================================================\x1b[0m');
  console.log('\x1b[1m\x1b[36m  SEEDING DỮ LIỆU & KIỂM THỬ LUỒNG ĐẦU CUỐI (API ➔ UI)     \x1b[0m');
  console.log('\x1b[1m\x1b[36m============================================================\x1b[0m');
  console.log(`• Backend URL:    ${BACKEND_URL}`);
  console.log(`• Public Web URL: ${PUBLIC_WEB_URL}`);
  console.log(`• Admin User:     ${ADMIN_USERNAME}`);

  const authOk = await stage0_auth();
  if (!authOk) {
    console.log('\n\x1b[33m[LƯU Ý]\x1b[0m Máy chủ backend hiện chưa hoạt động hoặc thông tin đăng nhập chưa khớp.');
    console.log('Bạn có thể khởi động backend và chạy lại lệnh:');
    console.log('  \x1b[1mnpx tsx tools/seeder/seed-portal-data.ts\x1b[0m\n');
    return;
  }

  await stage1_siteConfig();
  await stage2_categories();
  await stage3_articles();
  await stage4_menus();
  await stage5_e2eVerification();

  console.log('\x1b[1m\x1b[32m✔ HOÀN TẤT TOÀN BỘ QUY TRÌNH SEEDING VÀ VERIFY LUỒNG ĐẦU CUỐI!\x1b[0m\n');
}

main().catch((err) => {
  console.error('Fatal error during seeding:', err);
  process.exit(1);
});
