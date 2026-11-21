import { del, get, post, put } from './request'
import type { GeoPointItem, GeoPointSaveRequest, PageData, RegionVO } from '@/types/api'

export interface PointQuery {
  keyword?: string
  regionId?: number
  type?: string
  page?: number
  size?: number
}

export const listPoints = (params: PointQuery) =>
  get<PageData<GeoPointItem>>('/points', params as Record<string, unknown>)

export const getPoint = (id: number) => get<GeoPointItem>(`/points/${id}`)

export const createPoint = (data: GeoPointSaveRequest) => post<GeoPointItem>('/points', data)

export const updatePoint = (id: number, data: GeoPointSaveRequest) =>
  put<GeoPointItem>(`/points/${id}`, data)

export const deletePoint = (id: number) => del<null>(`/points/${id}`)

/** 区域树：省 - 市 两级，用于筛选器与表单下拉 */
export const listRegions = () => get<RegionVO[]>('/regions')
