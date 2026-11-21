package com.painuo.suite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级配置（{@code app.*}）。
 *
 * <p>刻意集中到一个 Properties 类，而不是散落 {@code @Value}，
 * 便于在 {@code application.yml} 里一眼看全「可调项」。</p>
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cache cache,
        Ai ai,
        Storage storage,
        Wechat wechat,
        Cors cors
) {

    /** JWT 相关。secret 至少 32 字节，生产必须走环境变量。 */
    public record Jwt(
            String secret,
            long accessTokenSeconds,
            long refreshTokenSeconds,
            String issuer
    ) {
    }

    /** 缓存相关。type=memory 时用进程内实现，type=redis 时用 Lettuce 直连。 */
    public record Cache(
            String type,
            String keyPrefix,
            long defaultTtlSeconds,
            long nullValueTtlSeconds,
            double ttlJitterRatio,
            String redisUri
    ) {
    }

    /** AI 相关。provider=mock 时完全离线可用。 */
    public record Ai(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds,
            int maxContextItems,
            double temperature
    ) {
    }

    /** 对象存储。type=local 落本地磁盘，type=oss 走 S3/OSS 兼容协议（此处给出可切换骨架）。 */
    public record Storage(
            String type,
            String localRoot,
            String publicBaseUrl,
            String endpoint,
            String bucket,
            String accessKeyId,
            String accessKeySecret
    ) {
    }

    /** 微信小程序。mock=true 时不请求微信服务器，直接返回可预测的 openid。 */
    public record Wechat(
            String appId,
            String appSecret,
            boolean mock
    ) {
    }

    /** CORS 白名单，逗号分隔。 */
    public record Cors(
            String allowedOrigins
    ) {
    }
}
