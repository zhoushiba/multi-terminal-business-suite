package com.painuo.suite.cache;

import com.painuo.suite.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存实现的选择器：{@code app.cache.type=memory|redis}。
 *
 * <p>用普通 {@code @Bean} 而非 {@code @ConditionalOnProperty}，是为了在"配了 redis 但连不上"时
 * 明确记录日志，也方便单元测试直接 new。</p>
 */
@Configuration
public class CacheAutoConfig {

    @Bean
    public CacheService cacheService(AppProperties appProperties) {
        AppProperties.Cache cache = appProperties.cache();
        if ("redis".equalsIgnoreCase(cache.type())) {
            return new RedisCacheService(cache);
        }
        return new MemoryCacheService(cache);
    }
}
