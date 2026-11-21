<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">AI 助手</h2>
        <p class="page-header__desc">
          内容摘要 / 标题候选 / 知识库问答 · 后端 AI provider 为 <code>mock</code> 时走离线抽取式策略，
          配置 <code>APP_AI_PROVIDER=openai</code> 即可切换真实模型（带降级）
        </p>
      </div>
    </div>

    <div class="ai-grid">
      <section class="panel">
        <div class="panel__header">内容摘要与标题</div>
        <div class="panel__body">
          <el-form label-position="top">
            <el-form-item label="标题（可选）">
              <el-input v-model="draft.title" placeholder="待处理内容标题" />
            </el-form-item>
            <el-form-item label="正文">
              <el-input
                v-model="draft.body"
                type="textarea"
                :rows="5"
                placeholder="粘贴正文，AI 会生成 120 字以内摘要"
              />
            </el-form-item>
          </el-form>
          <div class="ai-actions">
            <el-button type="primary" :disabled="!auth.can('ai:use')" :loading="summaryLoading" @click="onSummary">
              生成摘要
            </el-button>
            <el-button :disabled="!auth.can('ai:use')" :loading="titlesLoading" @click="onTitles">
              生成标题候选
            </el-button>
          </div>

          <div v-if="result" class="ai-result">
            <div class="ai-result__meta">
              <el-tag size="small" effect="plain">{{ result.provider }} / {{ result.model }}</el-tag>
              <el-tag v-if="result.fallback" size="small" type="warning">降级结果</el-tag>
              <span class="text-muted">耗时 {{ result.latencyMs ?? '-' }} ms</span>
              <span v-if="result.promptTokens != null" class="text-muted">
                tokens {{ result.promptTokens }} → {{ result.completionTokens }}
              </span>
            </div>
            <div class="ai-result__content">{{ result.content }}</div>
            <div v-if="result.references?.length" class="ai-result__refs">
              <div class="ai-result__refs-title">引用内容</div>
              <div v-for="ref in result.references" :key="String(ref.contentId ?? ref.title ?? '')" class="ai-result__ref">
                <el-tag size="small" type="info" effect="plain">#{{ ref.contentId }}</el-tag>
                <span>{{ ref.title }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel ai-chat">
        <div class="panel__header">内容库问答</div>
        <div class="panel__body ai-chat__body">
          <div class="ai-chat__messages">
            <div v-if="!messages.length" class="ai-chat__empty">
              <el-empty description="试着问：贵州秋季有哪些文旅线路？" :image-size="72" />
            </div>
            <div v-for="(item, index) in messages" :key="index" class="message" :class="`message--${item.role}`">
              <div class="message__bubble">{{ item.content }}</div>
              <div v-if="item.meta" class="message__meta">{{ item.meta }}</div>
            </div>
          </div>
          <div class="ai-chat__input">
            <el-input
              v-model="question"
              placeholder="输入问题，回车发送"
              :disabled="!auth.can('ai:use')"
              @keyup.enter="onChat"
            />
            <el-button type="primary" :disabled="!auth.can('ai:use')" :loading="chatLoading" @click="onChat">
              发送
            </el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiChat, aiSummary, aiTitles } from '@/api/ai'
import type { AiResult } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const draft = reactive({ title: '', body: '' })
const result = ref<AiResult | null>(null)
const summaryLoading = ref(false)
const titlesLoading = ref(false)

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  meta?: string
}

const question = ref('')
const chatLoading = ref(false)
const messages = ref<ChatMessage[]>([])

async function onSummary(): Promise<void> {
  if (!draft.body.trim()) {
    ElMessage.warning('请先粘贴正文')
    return
  }
  summaryLoading.value = true
  try {
    result.value = await aiSummary({ title: draft.title || undefined, body: draft.body })
  } finally {
    summaryLoading.value = false
  }
}

async function onTitles(): Promise<void> {
  if (!draft.body.trim()) {
    ElMessage.warning('请先粘贴正文')
    return
  }
  titlesLoading.value = true
  try {
    result.value = await aiTitles({ title: draft.title || undefined, body: draft.body })
  } finally {
    titlesLoading.value = false
  }
}

async function onChat(): Promise<void> {
  const text = question.value.trim()
  if (!text) return
  messages.value.push({ role: 'user', content: text })
  question.value = ''
  chatLoading.value = true
  try {
    const answer = await aiChat(text)
    messages.value.push({
      role: 'assistant',
      content: answer.content,
      meta: `${answer.provider}/${answer.model} · ${answer.latencyMs ?? '-'} ms${answer.fallback ? ' · 降级' : ''}`
    })
  } catch {
    messages.value.push({ role: 'assistant', content: '抱歉，AI 服务暂时不可用，请稍后再试。' })
  } finally {
    chatLoading.value = false
  }
}
</script>

<style scoped>
.ai-grid {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: 14px;
  align-items: start;
}

.ai-actions {
  display: flex;
  gap: 10px;
}

.ai-result {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #f7f9ff;
  border: 1px solid rgba(47, 107, 255, 0.14);
}

.ai-result__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  margin-bottom: 10px;
}

.ai-result__content {
  font-size: 13.5px;
  line-height: 1.9;
  white-space: pre-wrap;
  color: #2b3040;
}

.ai-result__refs {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(47, 107, 255, 0.18);
}

.ai-result__refs-title {
  font-size: 12px;
  color: var(--suite-text-secondary);
  margin-bottom: 6px;
}

.ai-result__ref {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  line-height: 1.9;
}

.ai-chat__body {
  display: flex;
  flex-direction: column;
  min-height: 430px;
}

.ai-chat__messages {
  flex: 1;
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}

.ai-chat__empty {
  padding: 24px 0;
}

.message {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.message--user {
  align-items: flex-end;
}

.message__bubble {
  max-width: 86%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  background: #f2f4f9;
  color: #2b3040;
}

.message--user .message__bubble {
  background: var(--suite-primary);
  color: #fff;
}

.message__meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--suite-text-secondary);
}

.ai-chat__input {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--suite-border);
}

code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(47, 107, 255, 0.07);
  color: #2f6bff;
}
</style>
