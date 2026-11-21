import dayjs from 'dayjs'

export interface StatusMeta {
  text: string
  type: 'primary' | 'success' | 'info' | 'warning' | 'danger'
}

/** 内容状态：0 草稿 / 1 待审核 / 2 已发布 / 3 已下架（与后端 ContentStatus 对齐） */
export const CONTENT_STATUS: Record<number, StatusMeta> = {
  0: { text: '草稿', type: 'info' },
  1: { text: '待审核', type: 'warning' },
  2: { text: '已发布', type: 'success' },
  3: { text: '已下架', type: 'danger' }
}

/** 内容形态 */
export const CONTENT_TYPE: Record<string, StatusMeta> = {
  article: { text: '图文', type: 'primary' },
  gallery: { text: '图集', type: 'success' },
  video: { text: '视频', type: 'warning' }
}

/** 点位类型 */
export const POINT_TYPE: Record<string, StatusMeta> = {
  scenic: { text: '景区', type: 'success' },
  heritage: { text: '人文古迹', type: 'warning' },
  plot: { text: '遥感地块', type: 'primary' },
  service: { text: '服务设施', type: 'info' }
}

/** 活动状态：0 草稿 / 1 进行中 / 2 已结束 / 3 已取消 */
export const ACTIVITY_STATUS: Record<number, StatusMeta> = {
  0: { text: '草稿', type: 'info' },
  1: { text: '进行中', type: 'success' },
  2: { text: '已结束', type: 'info' },
  3: { text: '已取消', type: 'danger' }
}

/** 报名状态：1 待核销 / 2 已核销 / 3 已取消 */
export const SIGNUP_STATUS: Record<number, StatusMeta> = {
  1: { text: '待核销', type: 'warning' },
  2: { text: '已核销', type: 'success' },
  3: { text: '已取消', type: 'info' }
}

export function statusMeta(
  map: Record<string | number, StatusMeta>,
  value: number | string | null | undefined
): StatusMeta {
  if (value === null || value === undefined || value === '') return { text: '未知', type: 'info' }
  return map[value] ?? { text: `未知(${value})`, type: 'info' }
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value
}

export function formatDate(value?: string | null): string {
  if (!value) return '—'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : value
}

export function formatNumber(value?: number | null): string {
  return (value ?? 0).toLocaleString('zh-CN')
}

export function formatMoney(value?: number | null): string {
  return `¥${(value ?? 0).toFixed(2)}`
}

/** 将毫秒/秒的进程运行时长格式化为「x 天 x 小时 x 分钟」 */
export function formatUptime(seconds?: number | null): string {
  const total = Math.max(0, Math.floor(seconds ?? 0))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时 ${minutes} 分钟`
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  return `${minutes} 分钟`
}

/** 活动报名窗口是否开放（前端兜底判断，后端也有 signupOpen 字段） */
export function isSignupOpen(start?: string | null, end?: string | null): boolean {
  const now = dayjs()
  if (start && now.isBefore(dayjs(start))) return false
  if (end && now.isAfter(dayjs(end))) return false
  return true
}
