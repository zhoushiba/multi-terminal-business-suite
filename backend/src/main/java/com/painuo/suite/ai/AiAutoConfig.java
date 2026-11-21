package com.painuo.suite.ai;

import com.painuo.suite.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 提供方装配。
 *
 * <p>始终注册两个 Bean：真实提供方（可能不可用）+ Mock 兜底。
 * {@code AiService} 在运行时按 {@code available()} 选择，因此**切换无需重启逻辑**，
 * 也不会因为某天 Key 过期导致接口 500。</p>
 */
@Configuration
public class AiAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(AiAutoConfig.class);

    @Bean
    public MockAiProvider mockAiProvider() {
        return new MockAiProvider();
    }

    @Bean
    public OpenAiCompatibleProvider openAiCompatibleProvider(AppProperties appProperties) {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(appProperties.ai());
        log.info("AI 配置: provider={}, model={}, realAvailable={}",
                appProperties.ai().provider(), appProperties.ai().model(), provider.available());
        return provider;
    }
}
