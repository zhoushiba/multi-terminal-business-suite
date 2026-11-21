/**
 * 与后端 DTO 一一对应的前端类型。
 *
 * 后端统一响应：{ code, message, data, traceId, timestamp }，成功时 code = 0；
 * 分页结构：{ list, page, size, total, pages, hasNext }（见 com.painuo.suite.common）。
 */

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
  timestamp: number
}

export interface PageData<T> {
  list: T[]
  page: number
  size: number
  total: number
  pages: number
  hasNext: boolean
}

/* ------------------------------- 认证 ------------------------------- */

export interface LoginPayload {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string | null
  phone: string | null
  roles: string[]
  permissions: string[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
}

/** /api/auth/routes 下发的是「菜单 / 目录」树，按钮（type=2）不下发 */
export interface RouteNode {
  id: number
  parentId: number
  name: string
  path: string
  component: string | null
  icon: string | null
  perm: string | null
  type: number
  sort: number
  children: RouteNode[]
}

/* ------------------------------- 内容 ------------------------------- */

export interface CategoryVO {
  id: number
  parentId: number
  name: string
  code: string
  sort: number
  status: number
  children: CategoryVO[]
}

export interface TagVO {
  id: number
  name: string
  code: string
}

export interface ContentItem {
  id: number
  title: string
  summary: string | null
  body: string | null
  coverUrl: string | null
  categoryId: number | null
  categoryName: string | null
  contentType: string
  status: number
  statusText: string
  topFlag: number
  sort: number
  authorId: number | null
  authorName: string | null
  publishTime: string | null
  viewCount: number
  tags: string[]
  createdAt: string | null
  updatedAt: string | null
}

export interface ContentSaveRequest {
  title: string
  summary?: string
  body?: string
  coverUrl?: string
  categoryId?: number | null
  contentType: string
  topFlag?: boolean
  sort?: number
  tags?: string[]
}

/* ------------------------------- 点位 ------------------------------- */

export interface RegionVO {
  id: number
  name: string
  code: string
  level: number
  centerLng: number | null
  centerLat: number | null
  children: RegionVO[]
}

export interface GeoPointItem {
  id: number
  name: string
  regionId: number | null
  regionName: string | null
  type: string
  typeText: string
  lng: number
  lat: number
  address: string | null
  coverUrl: string | null
  summary: string | null
  status: number
  distanceMeters?: number | null
  distanceText?: string | null
  createdAt: string | null
}

export interface GeoPointSaveRequest {
  name: string
  regionId: number | null
  type: string
  lng: number
  lat: number
  address?: string
  coverUrl?: string
  summary?: string
  status: number
}

/* ------------------------------- 活动 ------------------------------- */

export interface ActivitySessionItem {
  id: number
  activityId: number
  name: string
  startTime: string
  endTime: string
  capacity: number
  remain: number
  price: number
  soldOut: boolean
}

export interface ActivityItem {
  id: number
  title: string
  coverUrl: string | null
  regionId: number | null
  description: string | null
  status: number
  signupStartTime: string | null
  signupEndTime: string | null
  startTime: string | null
  endTime: string | null
  signupOpen: boolean
  sessions: ActivitySessionItem[]
}

export interface ActivitySaveRequest {
  title: string
  coverUrl?: string
  regionId: number | null
  description?: string
  status: number
  signupStartTime?: string | null
  signupEndTime?: string | null
  startTime?: string | null
  endTime?: string | null
}

export interface SessionSaveRequest {
  name: string
  startTime: string
  endTime: string
  capacity: number
  price: number
}

/* ------------------------------- 报名 ------------------------------- */

export interface SignupItem {
  id: number
  activityId: number
  activityTitle: string
  sessionId: number
  sessionName: string
  sessionStartTime: string | null
  code: string
  status: number
  statusText: string
  contactName: string
  contactPhone: string
  signupTime: string | null
  verifyTime: string | null
}

export interface VerifyResult {
  signupId: number
  activityTitle: string
  contactName: string
  contactPhone: string
  status: number
  statusText: string
  verifyTime: string | null
}

/* ------------------------------- RBAC ------------------------------- */

export interface SysUserItem {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  phone: string | null
  status: number
  source: string
  roles: string[]
  roleIds: number[]
  lastLoginAt: string | null
  createdAt: string | null
}

export interface UserSaveRequest {
  username: string
  password?: string
  nickname?: string
  phone?: string
  status: number
  roleIds: number[]
}

export interface SysRoleItem {
  id: number
  code: string
  name: string
  remark: string | null
  sort: number
  menuIds: number[]
}

export interface RoleSaveRequest {
  code: string
  name: string
  remark?: string
  sort: number
  menuIds: number[]
}

export interface MenuVO {
  id: number
  parentId: number
  name: string
  path: string | null
  component: string | null
  icon: string | null
  type: number
  perm: string | null
  sort: number
  hidden: number
  children: MenuVO[]
}

/* ------------------------------- 统计 ------------------------------- */

export interface TotalsVO {
  contentTotal: number
  publishedTotal: number
  pendingTotal: number
  activityTotal: number
  signupTotal: number
  verifiedTotal: number
  pointTotal: number
  userTotal: number
  viewTotal: number
}

export interface TrendPoint {
  date: string
  count: number
}

export interface RankItem {
  name: string
  value: number
}

export interface ConversionItem {
  activityTitle: string
  sessionName: string
  capacity: number
  signed: number
  conversionRate: number
}

export interface DashboardData {
  totals: TotalsVO
  contentTrend: TrendPoint[]
  categoryRank: RankItem[]
  regionDistribution: RankItem[]
  activityConversion: ConversionItem[]
}

/* ------------------------------- 系统 ------------------------------- */

export interface OperationLogItem {
  id: number
  traceId: string | null
  userId: number | null
  username: string | null
  action: string | null
  method: string | null
  path: string | null
  ip: string | null
  costMs: number | null
  success: number
  detail: string | null
  createdAt: string | null
}

export interface SystemInfo {
  app: string
  version: string
  profiles: string
  javaVersion: string
  os: string
  uptimeSeconds: number
  serverTime: string
  cacheBackend: string
  storageBackend: string
  storageMaxMb: number
  aiProvider: string
  aiModel: string
  aiMock: boolean
  wechatMock: boolean
  jwtAccessTokenSeconds: number
  corsAllowedOrigins: string
}

/* ------------------------------- AI ------------------------------- */

export interface AiReference {
  contentId: number | null
  title: string | null
  snippet: string | null
}

export interface AiResult {
  content: string
  provider: string
  model: string
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  fallback: boolean
  references: AiReference[]
}
