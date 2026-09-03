/**
 * Bản sao kiểu dữ liệu của API Core (`com.songnhue.core.api.**`).
 *
 * Viết tay chứ không sinh từ OpenAPI: bộ sinh sẽ đổ ra cả trăm kiểu cho những endpoint
 * admin-app không đụng tới, và mỗi lần backend thêm module là một lượt diff khổng lồ.
 * Chép tay đúng phần đang dùng thì nhỏ, đọc được, và khi backend đổi hình dạng thì
 * TypeScript chỉ đúng chỗ hỏng.
 *
 * ⚠ Mọi mốc thời gian là **chuỗi ISO-8601 UTC** do backend trả (`Instant`). Hiển thị
 * phải đi qua `formatDateTime` để đổi sang UTC+7 — xem `format.ts`.
 */

// =============================================================================
// Envelope (conventions.md §2.1)
// =============================================================================

export interface PageMeta {
  /** Đếm từ **1** — backend đã +1 so với `Page.getNumber()` của Spring (§1.3). */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ErrorDetail {
  field: string;
  rule: string;
  rejectedValue: unknown;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: ErrorDetail[];
}

export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  meta?: PageMeta;
  error?: ApiErrorBody;
  /** Luôn có mặt, kể cả khi thành công — người dùng báo lỗi chỉ cần đọc mã này. */
  traceId: string;
}

/** Kết quả một truy vấn phân trang sau khi bóc envelope. */
export interface PageResult<T> {
  items: T[];
  meta: PageMeta;
}

// =============================================================================
// Xác thực — /api/v1/auth
// =============================================================================

/**
 * Bước tiếp theo của luồng đăng nhập, do backend quyết định.
 *
 * FE **không tự suy** ("tài khoản này là Admin nên chắc phải 2FA") — quy tắc bắt buộc
 * 2FA nằm ở backend và có thể đổi qua cấu hình.
 */
export type LoginStage = 'AUTHENTICATED' | 'TWO_FACTOR_REQUIRED' | 'TWO_FACTOR_ENROLL_REQUIRED';

export interface LoginResponse {
  stage: LoginStage;
  /** Chỉ có ở `AUTHENTICATED`. Giữ trong bộ nhớ, **không** localStorage. */
  accessToken: string | null;
  accessTokenExpiresAt: string | null;
  csrfToken: string | null;
  /** Vé đi tiếp hai bước 2FA; chỉ có ở hai nhánh còn lại. */
  challengeToken: string | null;
  mustChangePassword: boolean;
}

export interface EnrollResponse {
  /** ⛔ Chỉ hiện đúng một lần, backend không trả lại lần thứ hai. */
  secret: string;
  otpauthUri: string;
  recoveryCodes: string[];
}

export interface MeResponse {
  id: string;
  username: string;
  fullName: string;
  orgUnitId: number | null;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
  twoFactorEnrolled: boolean;
}

export interface SessionView {
  id: string;
  deviceLabel: string | null;
  ipAddress: string | null;
  issuedAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  /** Phiên đang dùng để gọi API — tô khác để người dùng khỏi tự đăng xuất mình. */
  current: boolean;
}

// =============================================================================
// Tài khoản & vai trò — /api/v1/admin/users
// =============================================================================

/** `PENDING_ACTIVATION` = đã tạo nhưng chưa đăng nhập lần nào (còn mật khẩu tạm). */
export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'LOCKED';

export interface UserView {
  publicId: string;
  username: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  status: string;
  mustChangePassword: boolean;
  twoFactorRequired: boolean;
  lastLoginAt: string | null;
}

/**
 * `GET /auth/password-policy` — chính sách độ mạnh mật khẩu đang có hiệu lực.
 *
 * ⚠ Hai trường này đọc từ bảng `settings`, Admin sửa được. Mọi con số ghi cứng ở giao diện sẽ
 * *nói dối* ngay lần đầu tham số đổi (§10.69) — nên chúng phải đi qua đây, không phải qua một
 * hằng số.
 */
export interface PasswordPolicyResponse {
  minLength: number;
  requireLetterAndDigit: boolean;
}

export interface CreateUserRequest {
  username: string;
  fullName: string;
  email?: string;
  orgUnitPublicId: string;
  temporaryPassword: string;
}

export interface UpdateUserRequest {
  fullName: string;
  email?: string;
  phone?: string;
}

export interface RoleSummary {
  code: string;
  name: string;
  description: string | null;
  permissionCount: number;
}

// =============================================================================
// Sơ đồ tổ chức — /api/v1/org-units
// =============================================================================

/** Một bảng `org_units` dùng chung cho Xí nghiệp (MOD-02) và phòng ban (MOD-04) — CLAUDE.md quy tắc 7. */
export type OrgUnitType = 'CONG_TY' | 'PHONG_BAN' | 'XI_NGHIEP' | 'TO_DOI';

export interface OrgUnitNode {
  publicId: string;
  code: string;
  name: string;
  shortName: string | null;
  unitType: OrgUnitType;
  path: string;
  depth: number;
  sortOrder: number;
  active: boolean;
  address: string | null;
  phone: string | null;
  email: string | null;
  children: OrgUnitNode[];
}

export interface OrgUnitSummary {
  publicId: string;
  code: string;
  name: string;
  shortName: string | null;
  unitType: OrgUnitType;
  path: string;
  depth: number;
  active: boolean;
  address: string | null;
  phone: string | null;
  email: string | null;
}

/**
 * Một dòng danh bạ lãnh đạo công bố trên cổng — CR-25 (bảng Lãnh đạo Công ty) và cột "Giám đốc XN"
 * của CR-26.
 *
 * ⛔ **Không phải hồ sơ nhân sự.** Bảng `org_unit_leaders` cố ý không nối `employees` của MOD-04,
 * nên đường công khai đọc nó không có lối nào chạm trường nhạy cảm (NĐ 13/2023).
 */
export interface OrgUnitLeaderRow {
  publicId: string;
  fullName: string;
  title: string;
  phone: string | null;
  email: string | null;
  sortOrder: number;
  active: boolean;
}

export interface CreateOrgUnitRequest {
  code: string;
  name: string;
  shortName?: string;
  unitType: OrgUnitType;
  /** Bỏ trống = nút gốc; toàn hệ thống chỉ được đúng một nút gốc. */
  parentPublicId?: string;
  /** Ba ô liên hệ — bảng 6 cột "Xí nghiệp trực thuộc" của cổng công khai đọc đúng ba cột này. */
  address?: string;
  phone?: string;
  email?: string;
}

/**
 * ⚠ `PUT /org-units/{publicId}` tồn tại từ WS-6 nhưng **không màn hình nào gọi** cho tới
 * 28/08/2026 — nên tên, tên tắt và loại đơn vị chưa bao giờ sửa được sau khi tạo. Cùng hình dạng
 * với `categories.visible` (T24.25): một nửa đường ghi có mặt, và cái nửa ấy đọc như đã xong.
 */
export interface UpdateOrgUnitRequest {
  name: string;
  shortName?: string;
  unitType: OrgUnitType;
  address?: string;
  phone?: string;
  email?: string;
}

// =============================================================================
// Cấu hình — /api/v1/settings
// =============================================================================

export type SettingValueType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'JSON' | 'CRON';

export interface SettingView {
  key: string;
  value: string | null;
  /** Giá trị đang có hiệu lực = `value` nếu có, không thì `defaultValue`. */
  effectiveValue: string | null;
  valueType: string;
  defaultValue: string | null;
  groupCode: string;
  label: string;
  description: string | null;
  /** Chuỗi kiểu `min=7;max=365` — FE dựng ô nhập theo đó, backend vẫn là nơi chốt. */
  validation: string | null;
  editable: boolean;
  exportable: boolean;
}

export interface SettingImportResult {
  changed: number;
  skippedKeys: string[];
}

// =============================================================================
// Nhật ký kiểm toán — /api/v1/audit-logs
// =============================================================================

export type AuditAction =
  | 'CREATE'
  | 'UPDATE'
  | 'DELETE'
  | 'RESTORE'
  | 'LOGIN'
  | 'LOGOUT'
  | 'LOGIN_FAILED'
  | 'PERMISSION_CHANGE'
  | 'EXPORT'
  | 'IMPORT'
  | 'APPROVE'
  | 'REJECT'
  | 'PUBLISH'
  | 'BACKUP'
  | 'DB_RESTORE';

export interface AuditLogView {
  seq: number;
  occurredAt: string;
  actorUserId: number | null;
  actorUsername: string | null;
  module: string | null;
  entityType: string | null;
  entityId: number | null;
  entityPublicId: string | null;
  action: AuditAction;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string | null;
  traceId: string | null;
}

export interface ChainBreak {
  seq: number;
  occurredAt: string;
  /** Câu tiếng Việt do hàm trong CSDL sinh — hiển thị thẳng. */
  reason: string;
}

export interface ChainVerification {
  intact: boolean;
  minSeq: number;
  maxSeq: number;
  totalRecords: number;
  breaks: ChainBreak[];
}

// =============================================================================
// Sao lưu & khôi phục — /api/v1/backups (M5.10, M5.11)
// =============================================================================

export type BackupStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type BackupTrigger = 'SCHEDULED' | 'MANUAL' | 'PRE_RESTORE';

export interface BackupView {
  id: string;
  fileName: string;
  sizeBytes: number | null;
  checksumSha256: string | null;
  status: BackupStatus;
  trigger: BackupTrigger;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  /** Chỉ có ở lượt THẤT BẠI — và đó là dòng đáng đọc nhất màn hình này. */
  errorMessage: string | null;
}

export interface BackupStatusView {
  lastSuccess: BackupView | null;
  ageSeconds: number | null;
  staleThresholdHours: number;
  /** Chưa từng sao lưu cũng tính là quá hạn — xem BackupController. */
  stale: boolean;
  scheduleEnabled: boolean;
  /** Môi trường này có bật khôi phục qua giao diện không (`DB_RESTORE_PASSWORD`). */
  restoreAvailable: boolean;
}

export interface RestoreRequest {
  confirmation: string;
  reason: string;
  totpCode: string;
}

// =============================================================================
// Việc chạy nền — /api/v1/jobs (conventions.md §1.3)
// =============================================================================

export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export interface JobAccepted {
  jobId: string;
  statusUrl: string;
}

export interface JobStatusView {
  jobId: string;
  jobType: string;
  status: JobStatus;
  progress: number;
  attempts: number;
  maxAttempts: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  result: string | null;
  lastError: string | null;
}

// =============================================================================
// Thông báo — /api/v1/notifications
// =============================================================================

export type NotificationSeverity = 'INFO' | 'WARNING' | 'DANGER';

export interface InboxEntry {
  recipientId: number;
  title: string;
  body: string | null;
  linkUrl: string | null;
  severity: NotificationSeverity;
  eventType: string;
  broadcast: boolean;
  createdAt: string;
  readAt: string | null;
}

export interface BroadcastRequest {
  title: string;
  body: string;
  severity?: NotificationSeverity;
  linkUrl?: string;
  /** Bỏ trống = gửi toàn bộ tài khoản đang hoạt động. */
  userIds?: number[];
}

// =============================================================================
// Tình trạng hệ thống — /api/v1/system/health (M5.12)
// =============================================================================

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';

export interface HealthComponentView {
  status: HealthStatus;
  details: Record<string, unknown>;
}

export interface HealthView {
  status: HealthStatus;
  /** Khoá: `db`, `storage`, `mail`, `backup`, `telemetry`. */
  components: Record<string, HealthComponentView>;
}

// =============================================================================
// Tệp đính kèm — /api/v1/attachments
// =============================================================================

export type ScanStatus = 'PENDING' | 'CLEAN' | 'INFECTED' | 'SKIPPED';
export type AttachmentStatus = 'UPLOADING' | 'READY' | 'QUARANTINED';

export interface AttachmentView {
  publicId: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  fileVersion: number;
  status: AttachmentStatus;
  scanStatus: ScanStatus;
  /** Ngày (không giờ) — chuỗi `yyyy-MM-dd`, không phải Instant. */
  validFrom: string | null;
  validUntil: string | null;
  /** Backend đã tính sẵn: còn hiệu lực + quét sạch. FE **không tự suy lại** (§1.4). */
  downloadable: boolean;
}

export interface DownloadUrl {
  /** Có hạn ngắn và bỏ qua phân quyền — không lưu lại, không chia sẻ. */
  url: string;
}

// =============================================================================
// Dashboard điều hành — /api/v1/ops/dashboard (CN-02.5, CN-02.6)
// =============================================================================

export type KpiTone = 'NORMAL' | 'WARNING' | 'DANGER' | 'UNKNOWN';

/**
 * Một ô KPI.
 *
 * ⛔ `value === null` nghĩa là **chưa có nguồn dữ liệu**, khác hẳn `0` (đã đo và bằng
 * không). Backend cố ý gửi `"value": null` tường minh thay vì bỏ khoá — bỏ khoá thì phía
 * này đọc ra `undefined`, không phân biệt được với "API đổi tên trường".
 */
export interface KpiView {
  key: string;
  label: string;
  value: number | null;
  /** Mẫu số khi ô là một tỉ lệ ("32 / 40"). */
  total: number | null;
  tone: KpiTone;
  /** Luôn có khi `value` rỗng — backend ép ở tầng kiểu. */
  unavailableReason: string | null;
  /** Hạng mục sẽ mang dữ liệu về, VD `"WS-18 (CN-02.2)"`. */
  availableIn: string | null;
}

export interface BucketView {
  key: string;
  label: string;
  count: number;
}

export interface ConstructionStatisticsView {
  total: number;
  withoutLocation: number;
  byType: BucketView[];
  byStatus: BucketView[];
  byOrgUnit: BucketView[];
  byManagementLevel: BucketView[];
}

/** Cấu hình bản đồ nền — đọc từ `settings`, đổi nguồn không phải dựng lại ảnh admin-app. */
export interface MapConfigView {
  tileUrl: string;
  attribution: string;
  centerLat: number;
  centerLng: number;
  defaultZoom: number;
  maxZoom: number;
}

export interface DashboardView {
  generatedAt: string;
  /** Chu kỳ tự làm mới (M2.15) — do backend đọc từ `settings` mỗi lượt gọi. */
  autoRefreshSeconds: number;
  /** Chu kỳ tự chuyển khối ở chế độ màn hình lớn. */
  wallRotateSeconds: number;
  kpis: KpiView[];
  statistics: ConstructionStatisticsView;
  map: MapConfigView;
}

export type ConstructionType = 'TRAM_BOM' | 'CONG' | 'KENH_MUONG' | 'DE_DIEU' | 'KHAC';
export type OperationalStatus =
  'BINH_THUONG' | 'CANH_BAO' | 'SU_CO' | 'BAO_TRI' | 'NGUNG_MUA_VU' | 'DA_THANH_LY';
export type LifecycleState = 'DANG_HOAT_DONG' | 'NGUNG_MUA_VU' | 'DA_THANH_LY';
export type ManagementLevel = 'CONG_TY' | 'XI_NGHIEP' | 'CUM';
/**
 * ⚠ Phải khớp ĐÚNG enum Java `ConstructionPurpose` và `ck_constructions_purpose`.
 *
 * Bản trước khai `TUOI_TIEU_KET_HOP` và `KHAC` — cả hai không tồn tại ở hai nơi kia, nên `tsc`
 * xanh trong khi lượt lưu trả 400. Khai kiểu là một **lời khẳng định**, không phải phép đo
 * (cùng bài học T27.22). `enumBaNoi.test.ts` nay canh cho ba nơi khớp nhau.
 */
export type ConstructionPurpose = 'TUOI' | 'TIEU' | 'HON_HOP';

/** Một dòng trên danh sách — cố ý gọn, không kéo theo thông số kỹ thuật. */
export interface ConstructionRow {
  publicId: string;
  code: string;
  name: string;
  constructionType: ConstructionType;
  managementLevel: ManagementLevel | null;
  orgUnitName: string | null;
  clusterName: string | null;
  riverName: string | null;
  chainage: string | null;
  latitude: number | null;
  longitude: number | null;
  located: boolean;
  lifecycleState: LifecycleState;
  operationalStatus: OperationalStatus;
  updatedAt: string;
}

export interface PumpSpecView {
  totalPowerKw: number | null;
  pumpCount: number | null;
  standbyPumpCount: number | null;
  flowPerPumpM3s: number | null;
  totalFlowM3s: number | null;
  headM: number | null;
  powerSource: string | null;
  voltageKv: number | null;
  operatingLevelMinM: number | null;
  operatingLevelMaxM: number | null;
}

export interface SluiceSpecView {
  sluiceType: string | null;
  bayCount: number | null;
  bayWidthM: number | null;
  sillElevationM: number | null;
  crestElevationM: number | null;
  designFlowM3s: number | null;
  gateOperation: string | null;
  upstreamWarningLevelM: number | null;
  upstreamDangerLevelM: number | null;
}

export interface LinearSpecView {
  lengthKm: number | null;
  startChainage: string | null;
  endChainage: string | null;
  designFlowM3s: number | null;
  crestElevationM: number | null;
  technicalGrade: string | null;
  crossSection: string | null;
  specNote: string | null;
}

/** Hồ sơ đầy đủ — kèm đúng khối thông số của loại công trình đó, ba khối kia là null. */
export interface ConstructionDetail {
  summary: ConstructionRow;
  orgUnitId: string | null;
  clusterId: string | null;
  purpose: ConstructionPurpose | null;
  address: string | null;
  chainageM: number | null;
  basinNote: string | null;
  builtYear: number | null;
  commissionedYear: number | null;
  designer: string | null;
  contractor: string | null;
  totalInvestment: number | null;
  /** Hai tài liệu công bố ra cổng công khai — CR-28, bảng 7 cột §5.1. */
  operatingProcedureAttachmentId: string | null;
  protectionPlanAttachmentId: string | null;
  description: string | null;
  pump: PumpSpecView | null;
  sluice: SluiceSpecView | null;
  linear: LinearSpecView | null;
}

/** Marker trên bản đồ — nội dung popup theo M2.10. */
export interface MapPointView {
  publicId: string;
  code: string;
  name: string;
  constructionType: ConstructionType;
  operationalStatus: OperationalStatus;
  orgUnitName: string | null;
  latitude: number;
  longitude: number;
}

// =============================================================================
// Danh mục Tình trạng Vận hành (Mã màu & Cấu hình)
// =============================================================================

/**
 * Mã tình hình vận hành (CN-02.11).
 *
 * ⛔ Không có trường `id`. Khoá nội bộ không đi ra tới trình duyệt — đường dẫn PUT/DELETE dựng từ
 * `publicId`. Bản trước nhận `id: number` và dùng nó làm `rowKey` lẫn tham số đường dẫn.
 */
export interface OperationStatusCode {
  publicId: string;
  code: string;
  name: string;
  hasParameter: boolean;
  parameterUnit: string | null;
  colorHex: string;
  mappedStatus: OperationalStatus | null;
  sortOrder: number;
  active: boolean;
}

export type MaintenanceType =
  'SUA_CHUA' | 'BAO_TRI_DINH_KY' | 'NANG_CAP' | 'THAY_THE_THIET_BI' | 'KHAC_PHUC_SU_CO';

export type IncidentSeverity = 'NGHIEM_TRONG' | 'CAO' | 'TRUNG_BINH' | 'THAP';

/** Một dòng lịch sử sửa chữa / bảo trì / khắc phục sự cố — CN-02.2. */
export interface MaintenanceRow {
  id: string;
  code: string;
  constructionId: string;
  constructionCode: string | null;
  constructionName: string | null;
  workType: MaintenanceType;
  severity: IncidentSeverity | null;
  status: string;
  startedOn: string | null;
  completedOn: string | null;
  content: string;
  itemOrEquipment: string | null;
  /** Đơn vị nội bộ HOẶC nhà thầu ngoài — backend đã gộp, giao diện không phải biết hai cột. */
  performer: string | null;
  performerIsInternal: boolean;
  cost: string | null;
  fundingSource: string | null;
  acceptanceResult: string | null;
  acceptanceNote: string | null;
  assigneeUserId: string | null;
  alertEventId: string | null;
  createdAt: string;
}

/**
 * Một bước chuyển được phép bấm LÚC NÀY — khớp `com.songnhue.core.spi.AllowedAction`,
 * do `WorkflowEngine.allowedActions()` lọc theo `workflow_transitions` + quyền của người
 * đang đăng nhập.
 *
 * ⚠ Khai ở đây chứ không mượn kiểu của `ApprovalActions.tsx`: `shared/` không được phụ
 * thuộc ngược vào `components/` — cả codebase đang đi một chiều `components → shared`.
 */
export interface AllowedActionView {
  action: string;
  label: string;
  /** Trạng thái sau khi bấm — để nói trước hệ quả cho người dùng. */
  toState: string;
  /**
   * Bước này bắt buộc kèm lý do → mở ô nhập trước khi gửi.
   *
   * ⚠⚠ Đọc từ `workflow_transitions.requires_reason`, **cùng một dòng** mà
   * `WorkflowEngine.execute` dùng để ép buộc. Trước đây cờ này chỉ tồn tại ở kiểu phía giao
   * diện và không nơi nào điền, nên nó luôn `undefined`: hộp thoại nhập lý do không bao giờ
   * mở, người duyệt bấm "Yêu cầu chỉnh sửa" thì backend trả `SYS-0003` đòi lý do mà màn hình
   * không có ô nào để nhập. Thao tác trả bài về sửa hỏng hẳn theo đúng cách đó.
   */
  requiresReason: boolean;
}

export interface MaintenanceDetail {
  record: MaintenanceRow;
  actions: AllowedActionView[];
}

/** Tổng chi phí kỳ — tính ở BE (quy tắc 3), FE chỉ hiển thị. Khớp `MaintenanceLogService.CostSummary`. */
export interface MaintenanceCostSummary {
  /** Chuỗi, không phải number: `BigDecimal` phía BE, và `number` của JS làm tròn sai tiền (quy tắc 2). */
  total: string;
  recordCount: number;
  from: string;
  to: string;
}

/** Một tài liệu của công trình (CN-02.3). Đi bằng `publicId`, không có khoá nội bộ. */
export interface ConstructionDocument {
  publicId: string;
  originalName: string;
  docType: string;
  contentType: string;
  sizeBytes: number;
  fileVersion: number;
  downloadable: boolean;
  uploadedAt: string;
  issuedDate: string | null;
  expiryDate: string | null;
}

export interface ConstructionDocumentList {
  usedBytes: number;
  items: ConstructionDocument[];
}

/**
 * Một cụm công trình — G15 chốt cụm là **bảng riêng**, không phải một cột trên `constructions`.
 *
 * ⚠ `publicId` là UUID và là thứ gửi lên khi gán cụm cho một công trình (`clusterId`).
 */
export interface ClusterView {
  publicId: string;
  code: string;
  name: string;
  orgUnitId: string;
  orgUnitName: string | null;
  description: string | null;
  sortOrder: number | null;
  active: boolean;
}

/** Một dòng lịch sử tình hình vận hành của công trình. */
export interface OperationStatusRow {
  publicId: string;
  operationCode: string;
  operationName: string;
  colorHex: string;
  parameterValue: string | null;
  parameterUnit: string | null;
  note: string | null;
  effectiveAt: string;
}

/**
 * Một dòng nhập nhanh tình hình vận hành.
 *
 * ⚠ `operationCode` là mã trong danh mục `operation_status_codes` (MT / ĐK / …), **không phải**
 * `OperationalStatus`. Trạng thái công trình là giá trị dẫn xuất — quy tắc 4 cấm mọi đường cho người
 * dùng đặt thẳng nó, và bản trước của màn hình nhập nhanh làm đúng điều bị cấm đó.
 */
export interface OperationStatusBatchItem {
  constructionPublicId: string;
  operationCode: string;
  parameterValue?: string;
  note?: string;
  effectiveAt: string;
}

export interface OperationStatusCodeCreateRequest {
  code: string;
  name: string;
  hasParameter: boolean;
  parameterUnit?: string;
  colorHex: string;
  mappedStatus?: OperationalStatus;
  sortOrder: number;
  active: boolean;
}

export type OperationStatusCodeUpdateRequest = OperationStatusCodeCreateRequest;

// =============================================================================
// Nhập danh mục (T17.9)
// =============================================================================

export interface RowError {
  rowNumber: number;
  column: string | null;
  message: string;
}

export interface ImportReport {
  applied: boolean;
  totalRows: number;
  toCreate: number;
  toUpdate: number;
  errors: RowError[];
}

// =============================================================================
// MOD-03 Thuỷ văn — danh mục (WS-28)
// =============================================================================

export type PositionRole = 'THUONG_LUU' | 'HA_LUU' | 'BE_HUT' | 'MN_SONG' | 'MUA';
export type AdapterType = 'BHH40' | 'MOCK';
export type ApiSourceStatus = 'HOAT_DONG' | 'TAM_DUNG';

export interface MeasurementType {
  id: string;
  code: string;
  name: string;
  /** Đơn vị ĐÃ CHUẨN HOÁ trong CSDL — không phải đơn vị nguồn trả về (nguồn trả cm, DB lưu m). */
  unit: string;
  valueScale: number;
  sortOrder: number;
  active: boolean;
  description: string | null;
}

export interface MeasurementTypeRequest {
  code: string;
  name: string;
  unit: string;
  valueScale?: number;
  sortOrder?: number;
  active?: boolean;
  description?: string;
}

/**
 * ⛔ Không có trường mã số. Backend không bao giờ trả credential, kể cả cho SUPER_ADMIN
 * (`conventions.md` §4.7) — giao diện chỉ biết `credentialDaCauHinh`.
 *
 * Bốn trường `*HieuLuc` là giá trị **đã giải**; `*DungChung` cho biết nó đến từ bảng
 * `settings` hay từ cột riêng của nguồn. Hiện ô nhập rỗng mà không nói nguồn đang chạy
 * theo tham số chung là để người vận hành kết luận "chưa cấu hình" trong khi poller vẫn chạy.
 */
export interface ApiSource {
  id: string;
  code: string;
  name: string;
  adapterType: AdapterType;
  baseUrl: string;
  credentialDaCauHinh: boolean;
  status: ApiSourceStatus;
  cron: string | null;
  frameMinutes: number | null;
  timeoutSeconds: number | null;
  maxRetry: number | null;
  cronHieuLuc: string;
  cronDungChung: boolean;
  khungNguonPhutHieuLuc: number;
  khungDungChung: boolean;
  timeoutGiayHieuLuc: number;
  timeoutDungChung: boolean;
  soLanThuLaiHieuLuc: number;
  thuLaiDungChung: boolean;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastFailureReason: string | null;
  consecutiveFailures: number;
  soDiemDo: number;
  description: string | null;
}

export interface ApiSourceCreateRequest {
  code: string;
  name: string;
  adapterType: AdapterType;
  baseUrl: string;
  description?: string;
}

/** Vì sao một lượt gọi nguồn hỏng — năm giá trị, và chúng đòi năm việc phải làm khác nhau. */
export type SyncFailureKind =
  'THIEU_MA_SO' | 'NOT_WORKING' | 'TIMEOUT' | 'HTTP_ERROR' | 'EMPTY_BODY';

/**
 * Kết cục một lượt đồng bộ — bốn giá trị **phân biệt được**.
 *
 * ⚠ `SKIPPED_UP_TO_DATE` là kết cục **bình thường và mong muốn** của 4/5 lượt chạy (poll 2 phút
 * trên nguồn cập nhật 10 phút). ⛔ Đừng vẽ nó màu đỏ — trộn nó vào `FAILED` là dạy người vận
 * hành bỏ qua màu đỏ.
 */
export type SyncStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'SKIPPED_UP_TO_DATE';

/**
 * Kết quả một lượt đồng bộ — `POST /hyd/api-sources/{id}/goi-thu`, và cũng là hình dạng mà
 * poller ghi vào `sync_logs`.
 *
 * ⛔ **Không có trường nào mang thân phản hồi của nguồn**, và đó là chủ ý: thân thật của
 * `bhh40` chứa chính mã số (`<form action="…?key=…%3b">`, đo 01/09/2026). Muốn đối chiếu
 * nguyên văn thì tra `hydro_raw_logs` — nơi có phân quyền, có hạn lưu và có bộ che.
 *
 * ⚠ Envelope của dự án bỏ hẳn trường `null` khỏi JSON, nên đọc `rawLogId == null`,
 * ⛔ không `'rawLogId' in kq`.
 */
export interface KetQuaDongBo {
  trangThai: SyncStatus;
  httpStatus: number | null;
  durationMs: number;
  loi: SyncFailureKind | null;
  lyDo: string | null;
  soByteThan: number;
  /** Mốc đầu khung 10' mà lượt này nhắm tới. */
  khungNhamToi: string | null;
  soBanGhi: number;
  /** ⚠ 0 là bình thường ở 4/5 lượt — nguồn cập nhật 10' còn poller gọi 2'. */
  soGhiMoi: number;
  soTrungBoQua: number;
  soMaLa: number;
  soDongRac: number;
  soDongTrung: number;
  /** ⛔ Chỉ LIỆT KÊ. Tuyệt đối không tự tạo điểm đo từ mã lạ — đó là G8, thuộc Công ty. */
  maChuaKhai: string[];
  soDiemDoDangHoatDong: number;
  /** ⚠ Điểm đo có hồ sơ nhưng chưa tích loại chỉ số — số đo VẪN được ghi, đây là lỗi danh mục. */
  soThieuLoaiChiSo: number;
  soKhacNguon: number;
  mocDoGanNhat: string | null;
  /** `null` = đã gọi nhưng KHÔNG lưu được nguyên văn — một sự cố CSDL đáng biết ngay. */
  rawLogId: number | null;
  syncLogId: number | null;
}

export interface ApiSourceRequest {
  name: string;
  baseUrl: string;
  frameMinutes?: number | null;
  timeoutSeconds?: number | null;
  maxRetry?: number | null;
  cron?: string | null;
  status: ApiSourceStatus;
  description?: string;
}

export interface StationConstructionView {
  id: string;
  constructionId: string;
  /**
   * ⚠ `null` khi công trình đã bị xoá mềm SAU lúc liên kết được khai — giao diện phải nói ra
   * điều đó, ⛔ không giấu cả dòng đi: một liên kết trỏ vào công trình đã xoá là thứ người vận
   * hành cần thấy để dọn.
   */
  constructionCode: string | null;
  constructionName: string | null;
  role: PositionRole;
  primary: boolean;
}

/** Khai một liên kết điểm đo ↔ công trình — T28.19. */
export interface StationLinkRequest {
  constructionId: string;
  role: PositionRole;
  primary?: boolean;
}

export interface Station {
  id: string;
  code: string;
  name: string;
  /** ⛔ Bất biến sau khi seed — đổi là gán số liệu của trạm này sang trạm khác. */
  apiCode: string;
  apiSourceId: string | null;
  apiSourceCode: string | null;
  positionRole: PositionRole;
  orgUnitId: string | null;
  orgUnitName: string | null;
  riverName: string | null;
  chainage: string | null;
  chainageM: number | null;
  latitude: string | null;
  longitude: string | null;
  interpolated: boolean;
  active: boolean;
  description: string | null;
  measurementTypes: MeasurementType[];
  constructions: StationConstructionView[];
  /** ⚠ Điểm đo `MN_SONG` không liên kết công trình nào là HỢP LỆ — cờ này đã trừ trường hợp đó. */
  thieuLienKetCongTrinh: boolean;
  chuaGanDonVi: boolean;
}

export interface StationRequest {
  code: string;
  name: string;
  apiCode: string;
  apiSourceId: string;
  positionRole: PositionRole;
  orgUnitId?: string | null;
  riverName?: string | null;
  chainage?: string | null;
  latitude?: string | null;
  longitude?: string | null;
  interpolated?: boolean;
  active?: boolean;
  description?: string;
  measurementTypeIds?: string[];
}

// =============================================================================
// MOD-03 Thuỷ văn — chẩn đoán đường ingest (WS-31 / T31.13)
// =============================================================================

/**
 * Một lượt polling — `GET /hyd/sync-logs`.
 *
 * ⚠ **Bốn bộ đếm đi riêng, ⛔ đừng cộng lại thành một cột "kết quả".** `soGhiMoi = 0` là kết
 * cục **bình thường của 4/5 lượt chạy**: poller gọi 2 phút một lần trên một nguồn cập nhật 10
 * phút một lần. Gộp chúng là biến trạng thái bình thường nhất của hệ thống thành một dòng
 * trông như lỗi.
 *
 * ⛔ Không có trường nào mang thân phản hồi của nguồn — thân thật chứa chính mã số.
 * `rawLogId` là con trỏ tới `hydro_raw_logs`, ⛔ không phải nội dung; `null` nghĩa là **chưa
 * hề mở kết nối**, ⛔ không phải "ghi hỏng".
 */
export interface SyncLogRow {
  id: number;
  nguonId: string;
  nguonCode: string;
  nguonName: string;
  batDau: string;
  ketThuc: string | null;
  durationMs: number | null;
  /** Mốc đầu khung 10' mà lượt này nhắm tới — ⛔ khác giờ gọi. */
  khungNhamToi: string | null;
  trangThai: SyncStatus;
  loi: SyncFailureKind | null;
  lyDo: string | null;
  soNhan: number;
  soGhiMoi: number;
  soTrungBoQua: number;
  soMaLa: number;
  rawLogId: number | null;
}

/**
 * Dải tóm tắt sức khoẻ — `GET /hyd/sync-logs/tong-hop`.
 *
 * ⭐ Hai bản đồ luôn mang **đủ mọi khoá, kể cả khoá bằng 0** (backend ép ở hàm dựng): "24 giờ
 * qua có **0** lượt NOT_WORKING" là một điều đã đo được, còn thiếu khoá thì đọc giống hệt
 * "chưa ai đo".
 *
 * ⭐ `soLuotGoiHong` do backend tính — ⛔ đừng cộng lại ở đây: luật "lượt gọi đã thật sự xảy
 * ra chưa" nằm ở `SyncFailureKind.duocGhiVaoRawLog()` và cộng lại là mở nơi thứ tư phải nhớ.
 */
export interface SyncSummary {
  tuMoc: string;
  /** ⚠ Số giờ **đã kẹp** ở backend — dùng nó để đặt nhãn, ⛔ không dùng lại con số đã gửi đi. */
  soGio: number;
  soLuot: number;
  theoTrangThai: Record<SyncStatus, number>;
  theoLoi: Record<SyncFailureKind, number>;
  soLuotGoiHong: number;
  /** `null` = **không có lượt nào trong cửa sổ** — triệu chứng nặng hơn mọi con số lỗi. */
  mocGanNhat: string | null;
}

/** Bộ từ vựng của hai ô lọc — `GET /hyd/sync-logs/tu-vung`, ⛔ đừng chép cứng vào .tsx. */
export interface SyncVocabulary {
  trangThai: SyncStatus[];
  lyDoHong: SyncFailureKind[];
  /** Lý do hỏng xảy ra **trước khi** mở kết nối — hiện "chưa hề gọi" thay vì "gọi hỏng". */
  loiChuaGoi: SyncFailureKind[];
}

/**
 * Một mã nguồn **chưa khai thành điểm đo** — `GET /hyd/ma-la`.
 *
 * ⚠⚠ `giaTriGanNhat` là số **nguyên văn nguồn, CHƯA quy đổi**, và `donViNguon` là đơn vị
 * nguồn khai (nguồn trả **cm**, hệ thống lưu **m**). Hiện con số mà không kèm đơn vị là để
 * người đọc hiểu `213` thành *213 mét*.
 */
export interface UnmappedCodeRow {
  apiCode: string;
  nguonId: string;
  nguonCode: string;
  soBanGhi: number;
  lanDau: string | null;
  lanGanNhat: string | null;
  /** Chuỗi, ⛔ không phải số — `2.30` tuần tự hoá thành số sẽ mất chữ số cuối (T28.27). */
  giaTriGanNhat: string | null;
  donViNguon: string | null;
  /**
   * ⚠ `true` **không có nghĩa là xong**: số đo *mới* từ nay đi thẳng vào `hydro_readings`,
   * nhưng `soBanGhi` bản ghi *lịch sử* vẫn nằm lại cho tới khi có job chuyển.
   */
  daKhaiThanhDiemDo: boolean;
  maDiemDo: string | null;
}

// =============================================================================
// MOD-03 Thuỷ văn — chất lượng số đo (WS-32)
// =============================================================================

/**
 * Trạng thái một bản ghi số đo — **hai mức chất lượng + một bia mộ**.
 *
 * ⚠⚠ Bản ghi `NGHI_NGO` và `XOA` **nằm chung bảng chính**, nên mọi truy vấn báo cáo/cảnh
 * báo/tổng hợp phải lọc `HOP_LE` (quy tắc 14). Ở phía giao diện, hệ quả là: đừng bao giờ
 * cộng dồn một danh sách trả về từ màn hình *Dữ liệu nghi ngờ* vào một con số thống kê —
 * nó cố ý chứa đúng những dòng mà báo cáo loại ra.
 *
 * ⛔ `XOA` **không phải** mức chất lượng thứ ba: nó là trạng thái cuối của bước xoá mềm.
 */
export type ReadingQuality = 'HOP_LE' | 'NGHI_NGO' | 'XOA';

/** Bản ghi này do đâu mà có. `MANUAL` là đường nhập tay khi API gián đoạn (CN-03.2). */
export type ReadingSource = 'API' | 'MANUAL';

/**
 * Một dòng của màn hình *Dữ liệu nghi ngờ* — `GET /hyd/so-do/nghi-ngo`.
 *
 * ⛔⛔ **Không có trường `id`.** Khoá tự tăng của `hydro_readings` ⛔ không ra tới dây: địa chỉ của
 * một bản ghi là bộ ba `(diemDoId, loaiChiSoCode, mocDo)` — cùng bộ khoá mà `POST /thao-tac` và
 * `POST /nhap-tay` dùng. Lấy đúng bộ ba ấy làm `rowKey`.
 */
export interface SuspectReadingRow {
  mocDo: string;
  diemDoId: string;
  diemDoCode: string;
  diemDoName: string;
  loaiChiSoCode: string;
  loaiChiSoName: string;
  donVi: string;
  /**
   * ⭐ Chuỗi, ⛔ **không phải số**. `2.300` tuần tự hoá thành số JSON cho ra `2.3`, và với
   * mực nước thì chữ số thập phân thứ ba là **milimét** — thứ mà toàn bộ ngưỡng cảnh báo
   * treo lên. Đã trả giá hai lần: T28.27 ở cổng công khai, rồi V2 ở đường quản trị.
   */
  giaTri: string;
  trangThai: ReadingQuality;
  /** MÁY nói: vì sao bộ phân loại đánh dấu dòng này lúc ingest. */
  lyDoMay: string | null;
  /** NGƯỜI nói: lý do người duyệt loại bỏ. `null` khi chưa ai xử lý. */
  lyDoNguoi: string | null;
  nguon: ReadingSource;
  mocGhi: string | null;
  /** ⚠ `null` nghĩa là dòng do NGƯỜI nhập — ⛔ không phải "raw số 0". */
  rawLogId: number | null;
}

/**
 * ⚠ Câu trả lời cho *"bảng rỗng nghĩa là gì"* — `GET /hyd/so-do/nghi-ngo/tinh-trang`.
 *
 * Ba trạng thái **phân biệt được**, và cả ba đều cho ra một bảng rỗng:
 * bộ phân loại đang chạy mà không có gì đáng ngờ (`dangKiem = true`) · chưa ai cấu hình
 * quy tắc (`dangKiem = false`, `loiCauHinh` vắng) · cấu hình có mà **hỏng** (`loiCauHinh`).
 * ⛔ Giao diện phải nói ra cái nào — quy tắc 16: *số 0 là một câu khẳng định*.
 */
export interface QualityRuleStatus {
  dangKiem: boolean;
  loiCauHinh?: string | null;
}

/** Kết quả một bước chuyển — `POST /hyd/so-do/thao-tac`. */
export interface ReviewResult {
  mocDo: string;
  trangThai: ReadingQuality;
  lyDoNguoi: string | null;
}

/** Địa chỉ một bản ghi — khoá tự nhiên, ⛔ không phải khoá tự tăng. */
export interface ReviewRequest {
  diemDoId: string;
  maLoaiChiSo: string;
  mocDo: string;
  action: string;
  reason?: string;
}

/** Ô nhập tay — `POST /hyd/so-do/nhap-tay`. ⚠ `giaTri` gửi lên là **chuỗi**. */
export interface ManualEntryRequest {
  diemDoId: string;
  maLoaiChiSo: string;
  mocDo: string;
  giaTri: string;
  ghiChu?: string;
}

// =============================================================================
// WS-33 — Máy cảnh báo ngưỡng
// =============================================================================

/**
 * Loại điều kiện của một ngưỡng cảnh báo.
 *
 * ⛔ **Không phải** quy tắc "nghi ngờ" của WS-32. Hai thứ dễ lẫn vì cùng là một con số so
 * với một giá trị đo:
 *
 * - `QuyTacNghiNgo` hỏi *"cảm biến có đang hỏng không"* — khoảng vật lý, kết quả ghi vào
 *   `quality` của chính dòng số đo.
 * - Cái này hỏi *"tình hình có đáng báo động không"* — ngưỡng nghiệp vụ, và nó **chỉ chạy
 *   trên số đo đã `HOP_LE`**.
 *
 * ⚠ `OUT_OF_RANGE` là loại **duy nhất** dùng `thresholdValueHigh`; `RATE_OF_CHANGE` đo độ
 * lớn thay đổi trên **một giờ**, ⛔ không phải chênh lệch giữa hai lượt đo.
 */
export type AlertConditionType = 'GT' | 'LT' | 'OUT_OF_RANGE' | 'RATE_OF_CHANGE';

/**
 * Trạng thái một lần vượt ngưỡng.
 *
 * ⚠⚠ `DANG_XAY_RA` **chưa chắc là một cảnh báo thật** — phải xem thêm `daXacNhan`. Một điều
 * kiện vừa vượt nhưng chưa giữ đủ `delayMinutes` là một điều kiện *đang được theo dõi*:
 * chưa ai nhận thông báo nào, và nó ⛔ không lật trạng thái công trình sang `CANH_BAO`.
 *
 * ⚠ `DA_XU_LY` có **hai** người sinh ra, phân biệt bằng `dongBoiNguoi`: máy tự đóng vì giá
 * trị về dưới ngưỡng (`false`), hay người trực bấm đóng (`true`).
 */
export type AlertEventStatus = 'DANG_XAY_RA' | 'DA_XU_LY' | 'FALSE_ALARM';

/**
 * Một mức cảnh báo trong danh mục (G9-a).
 *
 * ⛔ `colorToken` là **khoá `design-tokens`**, ⛔ không phải mã hex — ràng buộc CSDL chặn
 * `#RRGGBB` ở tầng dưới. ⛔ Đừng đổ thẳng vào `style={{ color }}`.
 */
export interface AlertLevelRow {
  id: string;
  code: string;
  name: string;
  colorToken: string;
  severityRank: number;
  active: boolean;
  description?: string | null;
}

export interface AlertLevelRequest {
  code: string;
  name: string;
  colorToken: string;
  severityRank: number;
  active?: boolean;
  description?: string | null;
}

/** ⚠ Mọi số đo ra dây là **chuỗi** (`@JsonFormat(STRING)`) — xem `parameterValue`, cùng lý do. */
export interface AlertRuleRow {
  id: string;
  stationId: string;
  stationCode: string;
  stationName: string;
  measurementTypeCode: string;
  measurementTypeName: string;
  unit: string;
  alertLevelId: string;
  alertLevelCode: string;
  alertLevelName: string;
  colorToken: string;
  conditionType: AlertConditionType;
  thresholdValue: string;
  thresholdValueHigh?: string | null;
  delayMinutes: number;
  active: boolean;
  note?: string | null;
}

export interface AlertRuleRequest {
  stationId: string;
  measurementTypeCode: string;
  alertLevelId: string;
  conditionType: AlertConditionType;
  thresholdValue: string;
  thresholdValueHigh?: string | null;
  delayMinutes?: number;
  active?: boolean;
  note?: string | null;
}

export interface AlertRuleUpdateRequest {
  conditionType: AlertConditionType;
  thresholdValue: string;
  thresholdValueHigh?: string | null;
  delayMinutes?: number;
  active?: boolean;
  note?: string | null;
}

export interface AlertEventRow {
  id: string;
  stationId: string;
  stationCode: string;
  stationName: string;
  measurementTypeName: string;
  unit: string;
  alertLevelCode: string;
  alertLevelName: string;
  colorToken: string;
  conditionType: AlertConditionType;
  status: AlertEventStatus;
  startedAt: string;
  confirmedAt?: string | null;
  endedAt?: string | null;
  triggerValue: string;
  peakValue: string;
  peakAt: string;
  reason: string;
  daXacNhan: boolean;
  dongBoiNguoi: boolean;
  note?: string | null;
}

/** Điểm đo chưa cấu hình ngưỡng nào — nửa **đọc** của `HYD-2003`. */
export interface StationWithoutThresholdRow {
  id: string;
  code: string;
  name: string;
  orgUnitName?: string | null;
}

// ---------------------------------------------------------------------------
// WS-34 — Báo cáo thuỷ văn
// ---------------------------------------------------------------------------

/**
 * BC-13 — một hàng chất lượng dữ liệu của (điểm đo × chỉ số × ngày).
 *
 * ⛔⛔ `soKhungBoSot` là `null` khi CHƯA ĐO ĐƯỢC, và khi ấy `lyDoTrong` nói vì sao.
 *    ⛔ Đừng `?? 0` ở tầng hiển thị: **0 là một câu khẳng định** (*"hôm ấy poller chạy hoàn hảo"*),
 *    còn rỗng là một câu khác hẳn. Đây là cột dùng để nghiệm thu NFR-03 — một số 0 bịa ra ở đây đi
 *    thẳng vào một cam kết với Công ty.
 *
 * ⚠ `tyLeDayDu` là **chuỗi** (`@JsonFormat STRING` ở backend) — quy tắc 2 + bài học T28.27.
 */
export interface SyncQualityRow {
  ngay: string;
  stationCode: string;
  stationName: string;
  stationActive: boolean;
  measurementTypeCode: string;
  measurementTypeName: string;
  soHopLe: number;
  soNghiNgo: number;
  soDaXoa: number;
  soKhungMongDoi: number | null;
  soKhungBoSot: number | null;
  tyLeDayDu: string | null;
  lyDoTrong: string | null;
  tinhLuc: string | null;
}

/**
 * BC-13 — một hàng nhật ký đồng bộ, gộp theo (nguồn × ngày).
 *
 * ⭐ `soBoQua` (lượt bỏ vì mọi điểm đo đã có bản ghi của khung hiện tại) ⛔ KHÔNG được cộng vào
 *    "thành công": con số ấy cao là **tốt**. Gộp lại thì một ngày 720 lượt thành công với
 *    `soGhiMoi = 0` — tức một ngày mất trắng — cho cùng tỷ lệ với một ngày hoàn hảo.
 */
export interface SyncDailyRow {
  ngay: string;
  sourceCode: string;
  sourceName: string;
  soLuot: number;
  soThanhCong: number;
  soMotPhan: number;
  soHong: number;
  soBoQua: number;
  soNhan: number;
  soGhiMoi: number;
  soTrung: number;
  soMaLa: number;
  hongGanNhat: string | null;
}

/**
 * BC-05 — một hàng tổng hợp kỳ.
 *
 * ⛔⛔ `giaTriMin`/`giaTriMax`/`giaTriTb` là `null` khi kỳ ⛔ KHÔNG có bản ghi hợp lệ nào, và khi ấy
 *    `lyDoTrong` nói vì sao. ⛔ Đừng `?? 0`: mực nước trung bình `0.000` là một câu **sai và đáng
 *    tin** — đúng định dạng, vẽ được biểu đồ, nằm gọn giữa các con số thật. Backend ép ràng buộc ấy
 *    ở hàm dựng (`TongHopKyView`); đây là vế hiển thị của cùng một luật.
 *
 * ⭐ `giaTriTb` là trung bình **theo trọng số**, ⛔ không phải trung bình của các trung bình ngày.
 *    `soBanGhi` + `soNgayCoDuLieu` là hai con số cho người đọc biết nó dựa trên bao nhiêu quan sát.
 */
export interface PeriodSummaryRow {
  stationCode: string;
  stationName: string;
  riverName: string | null;
  positionRole: string;
  measurementTypeCode: string;
  measurementTypeName: string;
  unit: string;
  soBanGhi: number;
  soNgayCoDuLieu: number;
  giaTriMin: string | null;
  mocMin: string | null;
  giaTriMax: string | null;
  mocMax: string | null;
  giaTriTb: string | null;
  lyDoTrong: string | null;
}

export interface PeriodSummaryReport {
  tuNgay: string;
  denNgay: string;
  soNgayTrongKy: number;
  hang: PeriodSummaryRow[];
}

/**
 * BC-12 — một bản ghi chi tiết.
 *
 * ⭐⭐ `quality` và `source` là **hai cột chịu lực**, ⛔ không phải siêu dữ liệu phụ trợ: chúng là
 *    thứ được đánh đổi lấy quyền không lọc chất lượng. Đây là màn hình **duy nhất** hiện bản ghi
 *    `NGHI_NGO`/`XOA` cạnh bản ghi hợp lệ — ẩn hai cột ấy đi là biến ngoại lệ hợp lệ thành đúng cái
 *    lỗi mà quy tắc 14 sinh ra để chặn.
 */
export interface ReadingDetailRow {
  mocDo: string;
  giaTri: string;
  quality: ReadingQuality;
  qualityReason: string | null;
  source: string;
  note: string | null;
  reviewNote: string | null;
}

export interface SyncQualityReport {
  tuNgay: string;
  denNgay: string;
  /** Kích thước khung của nguồn, phút — mẫu số của tỷ lệ đầy đủ. ⛔ Đừng ghi cứng 10 ở FE. */
  khungPhut: number;
  chatLuong: SyncQualityRow[];
  dongBo: SyncDailyRow[];
}
