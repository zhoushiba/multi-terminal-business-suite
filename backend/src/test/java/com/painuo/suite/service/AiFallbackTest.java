package com.painuo.suite.service;

import com.painuo.suite.dto.AiDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Provider 不可用时的降级测试。
 *
 * <p>把 {@code app.ai.provider} 改成 {@code openai} 但不给 api-key，
 * 模拟"线上配了 OpenAI、但密钥过期/网络不通"的场景。期望：
 * 业务不报错、返回 mock 结果、并且 {@code fallback=true} 让调用方知道这是兜底内容。</p>
 *
 * <p>这里单独指定了一个内存库名，避免与其它测试上下文共享 H2 时序列互相干扰。</p>
 */
@SpringBootTest(properties = {
        "app.ai.provider=openai",
        "app.ai.api-key=",
        "spring.datasource.url=jdbc:h2:mem:suite_ai_fallback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@DisplayName("AI 服务（真实 Provider 不可用时降级）")
class AiFallbackTest {

    @Autowired
    private AiService aiService;

    @Test
    @DisplayName("OpenAI 未配置密钥时自动降级到 mock，并标记 fallback=true")
    void fallsBackToMockWhenProviderUnavailable() {
        AiDtos.AiResultVO result = aiService.summarize(new AiDtos.AiAssistRequest(
                "遥感影像变化检测季报",
                "本季使用高分二号影像对都柳江流域开展地块级变化检测，识别新增耕地与建设占用共 126 处。",
                null));

        assertThat(result.content()).isNotBlank();
        assertThat(result.fallback()).isTrue();
        assertThat(result.provider()).isEqualTo("mock");
    }
}
