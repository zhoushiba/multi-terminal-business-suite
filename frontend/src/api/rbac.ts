import { del, get, post, put } from './request'
import type {
  MenuVO,
  PageData,
  RoleSaveRequest,
  SysRoleItem,
  SysUserItem,
  UserSaveRequest
} from '@/types/api'

export interface UserQuery {
  keyword?: string
  status?: number
  page?: number
  size?: number
}

export const listUsers = (params: UserQuery) =>
  get<PageData<SysUserItem>>('/users', params as Record<string, unknown>)

export const createUser = (data: UserSaveRequest) => post<SysUserItem>('/users', data)

export const updateUser = (id: number, data: UserSaveRequest) => put<SysUserItem>(`/users/${id}`, data)

export const deleteUser = (id: number) => del<null>(`/users/${id}`)

export const resetPassword = (id: number, password: string) =>
  post<null>(`/users/${id}/reset-password`, { password })

export const listRoles = () => get<SysRoleItem[]>('/roles')

export const createRole = (data: RoleSaveRequest) => post<SysRoleItem>('/roles', data)

export const updateRole = (id: number, data: RoleSaveRequest) => put<SysRoleItem>(`/roles/${id}`, data)

export const deleteRole = (id: number) => del<null>(`/roles/${id}`)

/** 菜单树（含按钮），角色授权弹窗里勾选用 */
export const listMenus = () => get<MenuVO[]>('/menus')
