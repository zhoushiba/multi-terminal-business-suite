package com.painuo.suite.ai;

/**
 * 提示词模板集中管理。
 *
 * <p>把 prompt 从业务代码里抽出来，是为了能像代码一样 review 和迭代：
 * 一个 prompt 的改动会直接影响所有调用方，必须可 diff、可回滚。</p>
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String SUMMARY_SYSTEM = """
            你是「数海观澜」文旅内容平台的内容编辑。请为给定正文生成一段 80~120 字的中文摘要。
            要求：
            1. 客观陈述，不加入原文没有的信息；
            2. 保留关键实体（地名、时间、数据）；
            3. 只输出摘要正文，不要任何前缀、标题或解释。
            """;

    public static final String TITLE_SYSTEM = """
            你是新媒体运营。请基于给定内容生成 5 个中文标题候选。
            要求：每个标题 12~24 字，覆盖信息型/悬念型/数字型三种风格，逐行输出，形如 "1. xxx"。
            """;

    public static final String QA_SYSTEM = """
            你是「数海观澜」平台的内容助手。请仅依据【参考资料】回答用户问题。
            规则：
            1. 参考资料中没有的信息，明确说明"站内暂无相关内容"，不要编造；
            2. 回答控制在 200 字内，必要时分点；
            3. 结尾用一行注明引用了哪几条参考资料（如：参考：资料1、资料3）。
            """;

    public static String summaryUser(String title, String body) {
        return "标题：" + title + "\n【正文】\n" + body;
    }

    public static String titleUser(String title, String body) {
        return "原标题：" + title + "\n请基于以下正文生成标题：\n" + body;
    }

    /**
     * 组装 RAG 用户提示词。
     *
     * <p>格式刻意固定为 {@code 【参考资料N】...【问题】...}，
     * 这样 {@link MockAiProvider} 无需理解自然语言也能正确切分。</p>
     */
    public static String qaUser(java.util.List<String> references, String question) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            sb.append("【参考资料").append(i + 1).append("】").append(references.get(i)).append('\n');
        }
        sb.append("【问题】").append(question);
        return sb.toString();
    }
}
