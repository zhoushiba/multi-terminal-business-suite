import { del, get, post, put } from './request'
import type {
  CategoryVO,
  ContentItem,
  ContentSaveRequest,
  PageData,
  TagVO
} from '@/types/api'

export interface ContentQuery {
  keyword?: string
  categoryId?: number
  status?: number
  contentType?: string
  page?: number
  size?: number
}

export const listContents = (params: ContentQuery) =>
  get<PageData<ContentItem>>('/contents', params as Record<string, unknown>)

export const getContent = (id: number) => get<ContentItem>(`/contents/${id}`)

export const createContent = (data: ContentSaveRequest) => post<ContentItem>('/contents', data)

export const updateContent = (id: number, data: ContentSaveRequest) =>
  put<ContentItem>(`/contents/${id}`, data)

export const deleteContent = (id: number) => del<null>(`/contents/${id}`)

/** 状态流转：提审 / 发布 / 驳回 / 下架 */
export const submitContent = (id: number) => post<null>(`/contents/${id}/submit`)
export const publishContent = (id: number) => post<null>(`/contents/${id}/publish`)
export const rejectContent = (id: number, remark: string) =>
  post<null>(`/contents/${id}/reject`, { remark })
export const offlineContent = (id: number) => post<null>(`/contents/${id}/offline`)

export const listCategories = () => get<CategoryVO[]>('/categories')

export const createCategory = (data: Partial<CategoryVO>) => post<CategoryVO>('/categories', data)

export const updateCategory = (id: number, data: Partial<CategoryVO>) =>
  put<CategoryVO>(`/categories/${id}`, data)

export const deleteCategory = (id: number) => del<null>(`/categories/${id}`)

export const listTags = () => get<TagVO[]>('/tags')
