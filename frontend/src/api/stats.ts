import { get } from './request'
import type { DashboardData, TotalsVO } from '@/types/api'

/** 数据看板：totals + 趋势 + 排行 + 报名转化，days 默认 7 天 */
export const getDashboard = (days = 7) => get<DashboardData>('/stats/dashboard', { days })

export const getTotals = () => get<TotalsVO>('/stats/totals')
