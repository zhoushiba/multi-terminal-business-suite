package com.painuo.suite.service;

import com.painuo.suite.dto.AiDtos;
import com.painuo.suite.security.AuthContext;
import com.painuo.suite.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 能力的离线兜底测试。
 *
 * <p>项目刻意不依赖任何外部 API Key：没有 Key 时 {@link com.painuo.suite.ai.MockAiProvider}
 * 用抽取式摘要与模板化回答顶上，调用方拿到的永远是可用结果，只多一个
 * {@code fallback=true} 标记。这样 CI、评审的机器、没有网络的场合都能跑。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AI 服务（mock 兜底）")
class AiServiceTest {

    @Autowired
    private AiService aiService;

    @BeforeEach
    void setUp() {
        AuthContext.set(new AuthPrincipal(1L, "admin", "系统管理员",
                List.of("admin"), List.of("ai:use"), null));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("默认配置下摘要由 mock provider 产出，不标记降级")
    void summarizeUsesMockProvider() {
        AiDtos.AiResultVO result = aiService.summarize(new AiDtos.AiAssistRequest(
                "黄果树瀑布水量最佳的三个时段",
                "结合上游电站放水规律，9:00-10:30 顺光且水量稳定，是出片率最高的时段。午后逆光但水量回升。",
                null));

        assertThat(result.content()).isNotBlank();
        assertThat(result.provider()).isEqualTo("mock");
        assertThat(result.model()).startsWith("mock");
        assertThat(result.fallback()).isFalse();
        assertThat(result.references()).isEmpty();
    }

    @Test
    @DisplayName("标题优化返回多行候选标题")
    void suggestTitlesReturnsCandidates() {
        AiDtos.AiResultVO result = aiService.suggestTitles(new AiDtos.AiAssistRequest(
                "梵净山预约须知",
                "每日限流 8000 人，需提前一日在小程序预约；索道 7:30 开始运行。",
                null));

        assertThat(result.content()).isNotBlank();
        assertThat(result.content().lines().filter(line -> !line.isBlank()).count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("正文为空时给出参数错误，而不是调用模型")
    void blankBodyRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> aiService.summarize(new AiDtos.AiAssistRequest("标题", "   ", null)))
                .isInstanceOf(com.painuo.suite.common.BizException.class);
    }

    @Test
    @DisplayName("智能问答能召回站内内容作为参考资料（简易 RAG）")
    void chatRecallsReferences() {
        AiDtos.AiResultVO result = aiService.chat(new AiDtos.ChatRequest("黄果树瀑布水量最佳的三个时段"));

        assertThat(result.content()).isNotBlank();
        assertThat(result.references()).isNotEmpty();
        assertThat(result.references()).allSatisfy(ref -> {
            assertThat(ref.contentId()).isNotNull();
            assertThat(ref.title()).isNotBlank();
        });
    }
}
