package com.painuo.suite.cache;

/**
 * 缓存 key 命名中心。
 *
 * <p>统一前缀 + 冒号分层，好处：Redis 里 {@code SCAN match app:cache:cms:content:*} 能一次看清
 * 某类热点是否真的在命中，也便于按业务整体清理。</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static final String PREFIX = "app:cache:";

    /** 内容详情（热点数据）。 */
    public static String content(Long id) {
        return PREFIX + "cms:content:" + id;
    }

    /** 内容详情重建锁（防击穿）。 */
    public static String contentLock(Long id) {
        return PREFIX + "cms:content:lock:" + id;
    }

    /** 内容列表（按筛选条件 md5 后拼接）。 */
    public static String contentList(String conditionHash) {
        return PREFIX + "cms:content:list:" + conditionHash;
    }

    /** 点位列表（按边界框/区域）。 */
    public static String pointList(String conditionHash) {
        return PREFIX + "geo:point:list:" + conditionHash;
    }

    /** 看板聚合（短 TTL，允许秒级延迟）。 */
    public static String stats(String name) {
        return PREFIX + "stats:" + name;
    }

    /** 刷新令牌白名单：{@code auth:refresh:{jti} -> userId}。 */
    public static String refreshToken(String jti) {
        return "auth:refresh:" + jti;
    }

    /** 访问令牌黑名单（退出登录后剩余时间内的令牌）。 */
    public static String blacklist(String jti) {
        return "auth:blacklist:" + jti;
    }

    /** 极简接口限流计数。 */
    public static String rateLimit(String scene, String identity) {
        return "rate:" + scene + ":" + identity;
    }
}
