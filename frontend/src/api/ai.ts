import { post } from './request'
import type { AiResult } from '@/types/api'

export interface AiSummaryPayload {
  title?: string
  body?: string
  contentId?: number | null
}

/** 生成摘要（provider=mock 时走离线抽取式摘要，无网络也能演示） */
export const aiSummary = (payload: AiSummaryPayload) => post<AiResult>('/ai/summary', payload)

/** 生成标题候选 */
export const aiTitles = (payload: AiSummaryPayload) => post<AiResult>('/ai/titles', payload)

/** 基于内容库的问答 */
export const aiChat = (question: string) => post<AiResult>('/ai/chat', { question })
