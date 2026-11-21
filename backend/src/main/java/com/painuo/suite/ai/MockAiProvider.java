package com.painuo.suite.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线 Mock 提供方：用「抽取式摘要 + 关键词改写」模拟大模型，保证无 Key 可用。
 *
 * <p>它不是随便返回一句 "这是摘要"，而是真的读了输入：</p>
 * <ul>
 *   <li>摘要：按中文标点切句 → 计算句内关键词密度 → 取前 N 句并按得分排序后还原语序；</li>
 *   <li>标题：从摘要里抽 3~5 个高频词，套 5 种运营模板生成候选；</li>
 *   <li>问答：从 RAG 拼进来的参考片段里按关键词命中数排序，截取最相关段落作答。</li>
 * </ul>
 *
 * <p>因此单元测试可以断言"输出确实来自输入"，而不是断言一句固定文案。</p>
 */
public class MockAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(MockAiProvider.class);

    private static final Pattern SENTENCE_SPLIT =
            Pattern.compile("[。！？!?；;\\n]+");
    private static final Pattern TOKEN_SPLIT =
            Pattern.compile("[^\\u4e00-\\u9fa5A-Za-z0-9]+");
    private static final Pattern REFERENCE_BLOCK =
            Pattern.compile("【参考资料\\d+】([\\s\\S]*?)(?=【参考资料\\d+】|$)");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AiCompletion complete(AiCompletionRequest request) {
        long start = System.currentTimeMillis();
        String userPrompt = request.userPrompt() == null ? "" : request.userPrompt();
        String content;
        if (userPrompt.contains("【参考资料")) {
            content = answerFromReferences(userPrompt);
        } else if (userPrompt.contains("生成标题")) {
            content = suggestTitles(userPrompt);
        } else {
            content = summarize(userPrompt);
        }
        long cost = System.currentTimeMillis() - start;
        log.debug("[mock-ai] prompt={} chars, output={} chars, cost={}ms",
                userPrompt.length(), content.length(), cost);
        return new AiCompletion(content, name(), "mock-extractive-v1",
                estimateTokens(userPrompt.length()), estimateTokens(content.length()), cost);
    }

    // ------------------------------------------------------------------ 内部实现

    /** 抽取式摘要：句子打分后取前 3 句。 */
    String summarize(String text) {
        String body = stripInstruction(text);
        List<String> sentences = new ArrayList<>();
        for (String s : SENTENCE_SPLIT.split(body)) {
            String trimmed = s.trim();
            if (trimmed.length() >= 8) {
                sentences.add(trimmed);
            }
        }
        if (sentences.isEmpty()) {
            return body.isBlank() ? "内容为空，无法生成摘要。" : truncate(body, 120);
        }
        Set<String> keywords = topKeywords(body, 12);
        record Scored(int index, String sentence, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            int score = 0;
            for (String kw : keywords) {
                if (s.contains(kw)) {
                    score += 2;
                }
            }
            // 开头的句子通常含主旨，给一点权重
            score += Math.max(0, 3 - i);
            scored.add(new Scored(i, s, score));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<Scored> picked = scored.subList(0, Math.min(3, scored.size()));
        picked.sort((a, b) -> Integer.compare(a.index(), b.index()));
        StringBuilder sb = new StringBuilder();
        for (Scored s : picked) {
            sb.append(s.sentence()).append("。");
        }
        return truncate(sb.toString(), 200);
    }

    /** 标题建议：输出 5 条，用 "1. xxx" 换行分隔，便于前端直接 split 渲染。 */
    String suggestTitles(String text) {
        String summary = summarize(text);
        List<String> keywords = new ArrayList<>(topKeywords(summary, 8));
        if (keywords.isEmpty()) {
            keywords = List.of("数字文旅", "遥感数据", "产业观察");
        }
        String kw1 = keywords.get(0);
        String kw2 = keywords.size() > 1 ? keywords.get(1) : "数据服务";
        String core = truncate(summary.replace("。", "").trim(), 24);
        List<String> titles = List.of(
                kw1 + "观察：" + core,
                "从" + kw1 + "看" + kw2 + "的落地路径",
                core + "——" + kw2 + "实践复盘",
                "一文读懂" + kw1 + "与" + kw2,
                kw1 + "专题｜" + core);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < titles.size(); i++) {
            sb.append(i + 1).append(". ").append(truncate(titles.get(i), 40));
            if (i < titles.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** 简易 RAG：按关键词命中数挑最相关的参考资料片段。 */
    String answerFromReferences(String prompt) {
        String question = extractQuestion(prompt);
        List<String> blocks = new ArrayList<>();
        Matcher matcher = REFERENCE_BLOCK.matcher(prompt);
        while (matcher.find()) {
            String block = matcher.group(1).trim();
            if (!block.isEmpty()) {
                blocks.add(block);
            }
        }
        Set<String> questionKeywords = topKeywords(question, 8);
        String best = null;
        int bestScore = -1;
        for (String block : blocks) {
            int score = 0;
            for (String kw : questionKeywords) {
                if (block.contains(kw)) {
                    score += 3;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = block;
            }
        }
        if (best == null) {
            return "知识库中暂未检索到与该问题直接相关的内容。建议补充关键词后重试。";
        }
        String digest = summarize(best);
        return "根据站内内容库：" + digest
                + "\n\n（当前为离线 Mock 模式，配置 APP_AI_PROVIDER=openai 并填入 APP_AI_API_KEY 后由真实大模型作答。）";
    }

    private String extractQuestion(String prompt) {
        int idx = prompt.indexOf("【问题】");
        if (idx >= 0) {
            return prompt.substring(idx + 4);
        }
        return prompt;
    }

    /** 去掉指令性前缀（"请为下面内容生成摘要：" 之类），避免污染摘要。 */
    private String stripInstruction(String text) {
        int idx = text.indexOf("【正文】");
        if (idx >= 0) {
            return text.substring(idx + 4);
        }
        int colon = text.indexOf('：');
        if (colon > 0 && colon < 40) {
            return text.substring(colon + 1);
        }
        return text;
    }

    private Set<String> topKeywords(String text, int limit) {
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(text == null ? "" : text)) {
            String t = token.trim();
            if (t.length() >= 2 && t.length() <= 8) {
                tokens.add(t);
            }
        }
        java.util.Map<String, Integer> freq = new java.util.HashMap<>();
        for (String t : tokens) {
            freq.merge(t, 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .filter(e -> e.getValue() >= 1)
                .sorted((a, b) -> b.getValue().equals(a.getValue())
                        ? a.getKey().compareTo(b.getKey())
                        : Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private int estimateTokens(int chars) {
        // 中文约 1 字 ≈ 1 token，粗估即可
        return Math.max(1, chars / 2);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
