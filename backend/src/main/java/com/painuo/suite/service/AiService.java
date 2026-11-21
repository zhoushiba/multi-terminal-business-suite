package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.painuo.suite.ai.AiCompletion;
import com.painuo.suite.ai.AiCompletionRequest;
import com.painuo.suite.ai.AiProvider;
import com.painuo.suite.ai.AiProviderException;
import com.painuo.suite.ai.PromptTemplates;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ContentStatus;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.config.AppProperties;
import com.painuo.suite.dto.AiDtos;
import com.painuo.suite.entity.AiConversation;
import com.painuo.suite.entity.AiMessage;
import com.painuo.suite.entity.CmsContent;
import com.painuo.suite.mapper.AiConversationMapper;
import com.painuo.suite.mapper.AiMessageMapper;
import com.painuo.suite.mapper.CmsContentMapper;
import com.painuo.suite.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 能力门面：摘要 / 标题优化 / 基于内容库的问答（简易 RAG）。
 *
 * <p><b>离线可跑</b>是这个模块的第一原则：没有 API Key 时自动落到
 * {@link com.painuo.suite.ai.MockAiProvider}（抽取式摘要 + 模板化回答），
 * 调用方完全无感，只在返回值里多一个 {@code fallback=true} 的标记。
 * 这样 CI、单测、演示机都不依赖外网和密钥。</p>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String SCENE_ASSISTANT = "assistant";
    /** 问答召回条件：正文里按关键字命中的已发布内容数量上限。 */
    private static final int RECALL_LIMIT = 5;
    /** 喂给模型的正文截断长度，避免超长 prompt 烧钱。 */
    private static final int SNIPPET_LENGTH = 600;

    private final AiProvider mockProvider;
    private final AiProvider openAiProvider;
    private final AppProperties.Ai aiProps;
    private final CmsContentMapper contentMapper;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    public AiService(@Qualifier("mockAiProvider") AiProvider mockProvider,
                     @Qualifier("openAiCompatibleProvider") AiProvider openAiProvider,
                     AppProperties appProperties,
                     CmsContentMapper contentMapper,
                     AiConversationMapper conversationMapper,
                     AiMessageMapper messageMapper) {
        this.mockProvider = mockProvider;
        this.openAiProvider = openAiProvider;
        this.aiProps = appProperties.ai();
        this.contentMapper = contentMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /** 内容摘要（运营后台编辑器里一键生成）。 */
    public AiDtos.AiResultVO summarize(AiDtos.AiAssistRequest request) {
        requireBody(request.body());
        AiCompletionRequest completionRequest = AiCompletionRequest.of(
                PromptTemplates.SUMMARY_SYSTEM,
                PromptTemplates.summaryUser(nullToEmpty(request.title()), request.body()));
        return invoke(completionRequest, List.of());
    }

    /** 标题优化：返回 5 个候选标题，一行一个。 */
    public AiDtos.AiResultVO suggestTitles(AiDtos.AiAssistRequest request) {
        requireBody(request.body());
        AiCompletionRequest completionRequest = AiCompletionRequest.of(
                PromptTemplates.TITLE_SYSTEM,
                PromptTemplates.titleUser(nullToEmpty(request.title()), request.body()));
        return invoke(completionRequest, List.of());
    }

    /** 智能问答：先从内容库检索参考资料，再让模型基于资料回答（简易 RAG）。 */
    @Transactional
    public AiDtos.AiResultVO chat(AiDtos.ChatRequest request) {
        String question = request.question() == null ? "" : request.question().trim();
        if (question.isEmpty()) {
            throw BizException.param("问题不能为空");
        }
        Long userId = AuthContext.requireUserId();

        List<AiDtos.Reference> references = recall(question);
        List<String> documents = references.stream()
                .map(ref -> "标题：" + ref.title() + "\n正文：" + ref.snippet())
                .toList();
        AiCompletionRequest completionRequest = AiCompletionRequest.of(
                PromptTemplates.QA_SYSTEM,
                PromptTemplates.qaUser(documents, question));
        AiDtos.AiResultVO result = invoke(completionRequest, references);

        persist(userId, question, result);
        return result;
    }

    // ------------------------------------------------------------------ 内部方法

    /**
     * 调用模型，失败自动降级到 mock。
     *
     * <p>降级顺序：配置指定的 Provider → mock。任何网络/鉴权/超时异常都吞在
     * {@link AiProviderException} 里，业务侧永远拿到一个可用的回答。</p>
     */
    private AiDtos.AiResultVO invoke(AiCompletionRequest request, List<AiDtos.Reference> references) {
        AiProvider primary = "openai".equalsIgnoreCase(aiProps.provider()) ? openAiProvider : mockProvider;
        boolean fallback = false;
        AiCompletion completion;
        try {
            if (!primary.available()) {
                throw new AiProviderException("Provider " + primary.name() + " 未配置密钥");
            }
            completion = primary.complete(request);
        } catch (RuntimeException ex) {
            if (primary == mockProvider) {
                // mock 都挂了说明代码有 bug，直接暴露
                throw BizException.of(ResultCode.AI_PROVIDER_ERROR, "AI 服务不可用: " + ex.getMessage());
            }
            log.warn("AI Provider {} 调用失败，降级到 mock: {}", primary.name(), ex.getMessage());
            fallback = true;
            completion = mockProvider.complete(request);
        }
        return new AiDtos.AiResultVO(completion.content(), completion.provider(), completion.model(),
                completion.promptTokens(), completion.completionTokens(), completion.latencyMs(),
                fallback, references);
    }

    /**
     * 极简召回：用问题的头 2 个词组在标题/正文里 LIKE。
     *
     * <p>真实项目这里应该是向量检索或 ES，但作品集要控制依赖，
     * 用 LIKE 也能演示"回答有出处"的产品形态；不足会写进 docs/ai-coding.md。</p>
     */
    private List<AiDtos.Reference> recall(String question) {
        int configured = aiProps.maxContextItems();
        int limit = configured <= 0 ? RECALL_LIMIT : Math.min(configured, 20);
        String keyword = extractKeyword(question);
        if (keyword.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<CmsContent> wrapper = Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getStatus, ContentStatus.PUBLISHED.code())
                .and(w -> w.like(CmsContent::getTitle, keyword).or().like(CmsContent::getBody, keyword))
                .orderByDesc(CmsContent::getViewCount)
                .last("LIMIT " + limit);
        List<CmsContent> contents = contentMapper.selectList(wrapper);
        List<AiDtos.Reference> references = new ArrayList<>(contents.size());
        for (CmsContent content : contents) {
            String snippet = content.getSummary();
            if (snippet == null || snippet.isBlank()) {
                snippet = truncate(stripHtml(content.getBody()));
            }
            references.add(new AiDtos.Reference(content.getId(), content.getTitle(), snippet));
        }
        return references;
    }

    /** 会话与消息落库，便于运营回看提问记录（也是 ai_conversation 表存在的意义）。 */
    private void persist(Long userId, String question, AiDtos.AiResultVO result) {
        AiConversation conversation = conversationMapper.selectOne(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getScene, SCENE_ASSISTANT)
                .orderByDesc(AiConversation::getId)
                .last("LIMIT 1"));
        if (conversation == null) {
            conversation = new AiConversation();
            conversation.setUserId(userId);
            conversation.setTitle(truncate(question));
            conversation.setScene(SCENE_ASSISTANT);
            conversation.setMessageCount(0);
            conversationMapper.insert(conversation);
        }

        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setMsgRole("user");
        userMessage.setContent(question);
        messageMapper.insert(userMessage);

        AiMessage answer = new AiMessage();
        answer.setConversationId(conversation.getId());
        answer.setMsgRole("assistant");
        answer.setContent(result.content());
        answer.setProvider(result.provider());
        answer.setModel(result.model());
        answer.setPromptTokens(result.promptTokens());
        answer.setCompletionTokens(result.completionTokens());
        answer.setLatencyMs(result.latencyMs());
        messageMapper.insert(answer);

        AiConversation patch = new AiConversation();
        patch.setId(conversation.getId());
        patch.setMessageCount((conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 2);
        conversationMapper.updateById(patch);
    }

    /** 取问题里第一个非停用词的片段作为检索关键字（长度 ≥2 才算有效）。 */
    private static String extractKeyword(String question) {
        String cleaned = question.replaceAll("[？?！!。，,；;：:\\s]+", " ").trim();
        for (String token : cleaned.split(" ")) {
            if (token.length() >= 2 && !"什么".equals(token) && !"怎么".equals(token)) {
                return token.length() > 12 ? token.substring(0, 12) : token;
            }
        }
        return "";
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw BizException.param("正文不能为空，AI 需要内容才能工作");
        }
    }
}
