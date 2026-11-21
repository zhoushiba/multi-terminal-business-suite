import { del, get, post, put } from './request'
import type {
  ActivityItem,
  ActivitySaveRequest,
  PageData,
  SessionSaveRequest,
  SignupItem,
  VerifyResult
} from '@/types/api'

export interface ActivityQuery {
  keyword?: string
  status?: number
  page?: number
  size?: number
}

export const listActivities = (params: ActivityQuery) =>
  get<PageData<ActivityItem>>('/activities', params as Record<string, unknown>)

export const getActivity = (id: number) => get<ActivityItem>(`/activities/${id}`)

export const createActivity = (data: ActivitySaveRequest) => post<ActivityItem>('/activities', data)

export const updateActivity = (id: number, data: ActivitySaveRequest) =>
  put<ActivityItem>(`/activities/${id}`, data)

export const deleteActivity = (id: number) => del<null>(`/activities/${id}`)

export const createSession = (activityId: number, data: SessionSaveRequest) =>
  post<null>(`/activities/${activityId}/sessions`, data)

export const updateSession = (sessionId: number, data: SessionSaveRequest) =>
  put<null>(`/sessions/${sessionId}`, data)

export const deleteSession = (sessionId: number) => del<null>(`/sessions/${sessionId}`)

/* ----------------------------- 报名 / 核销 ----------------------------- */

export interface SignupQuery {
  activityId?: number
  sessionId?: number
  status?: number
  keyword?: string
  page?: number
  size?: number
}

export const listSignups = (params: SignupQuery) =>
  get<PageData<SignupItem>>('/signups', params as Record<string, unknown>)

/** 现场核销：凭核销码把报名状态从「待核销」推进到「已核销」 */
export const verifySignup = (code: string) => post<VerifyResult>('/signups/verify', { code })
