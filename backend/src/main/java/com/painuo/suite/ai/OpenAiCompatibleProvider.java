package com.painuo.suite.ai;

import com.painuo.suite.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容提供方（适用于 OpenAI / DeepSeek / 通义 / vLLM / Ollama 的 /v1 接口）。
 *
 * <p>只依赖 {@code POST {baseUrl}/chat/completions} 这一个稳定契约，
 * 换供应商只改 baseUrl + model，不需要改代码——这也是 JD 里"第三方服务对接"最典型的实践。</p>
 *
 * <p>没有配置 apiKey 时 {@link #available()} 返回 false，{@code AiService} 会自动降级到
 * {@link MockAiProvider}，保证 clone 下来就能跑。</p>
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final AppProperties.Ai props;
    private final RestClient client;

    public OpenAiCompatibleProvider(AppProperties.Ai props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(3, props.timeoutSeconds()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + (props.apiKey() == null ? "" : props.apiKey()))
                .build();
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public boolean available() {
        return props.apiKey() != null
                && !props.apiKey().isBlank()
                && props.baseUrl() != null
                && !props.baseUrl().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiCompletion complete(AiCompletionRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())));
        try {
            Map<String, Object> response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new AiProviderException("AI 返回空响应");
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiProviderException("AI 返回格式异常：缺少 choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message == null ? null : String.valueOf(message.get("content"));
            if (content == null || content.isBlank()) {
                throw new AiProviderException("AI 返回内容为空");
            }
            Map<String, Object> usage = (Map<String, Object>) response.getOrDefault("usage", Map.of());
            return new AiCompletion(content, name(), String.valueOf(props.model()),
                    toInt(usage.get("prompt_tokens")), toInt(usage.get("completion_tokens")),
                    System.currentTimeMillis() - start);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("AI 调用失败: {}", ex.getMessage());
            throw new AiProviderException("调用大模型失败：" + ex.getMessage(), ex);
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
