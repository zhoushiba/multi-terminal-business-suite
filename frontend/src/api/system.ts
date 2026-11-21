import { get } from './request'
import type { OperationLogItem, PageData, SystemInfo } from '@/types/api'

/** 运行时自述（脱敏）：缓存后端、AI provider、存储后端、JWT 有效期等 */
export const getSystemInfo = () => get<SystemInfo>('/system/info')

/** 操作日志分页（审计） */
export const listOperationLogs = (params: {
  keyword?: string
  page?: number
  size?: number
}) => get<PageData<OperationLogItem>>('/system/logs', params as Record<string, unknown>)

/** 健康检查：不需要 token，登录页右上角可以显示后端连通性 */
export const ping = () => get<Record<string, string>>('/ping')
