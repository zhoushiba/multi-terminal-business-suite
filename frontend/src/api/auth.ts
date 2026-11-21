import { get, post } from './request'
import type { LoginPayload, RouteNode, TokenResponse, UserInfo } from '@/types/api'

export const loginApi = (payload: LoginPayload) => post<TokenResponse>('/auth/login', payload)

export const refreshApi = (refreshToken: string) =>
  post<TokenResponse>('/auth/refresh', { refreshToken })

export const logoutApi = () => post<null>('/auth/logout')

export const profileApi = () => get<UserInfo>('/auth/profile')

export const routesApi = () => get<RouteNode[]>('/auth/routes')
