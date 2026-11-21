package com.painuo.suite.cache;

/**
 * 缓存适配层。
 *
 * <p>业务只依赖这个接口，具体是 {@link MemoryCacheService}（无 Redis 时的 fallback）
 * 还是 {@link RedisCacheService}（生产）由 {@code app.cache.type} 决定，
 * 保证「本机零依赖可跑、上线换 Redis 只改配置」。</p>
 *
 * <p>约定：{@link #get} 返回 {@code null} 表示未命中；命中「空值缓存」时返回
 * {@link CacheValue#EMPTY} 语义的值，由调用方转为业务层的 notFound，从而挡住缓存穿透。</p>
 */
public interface CacheService {

    /** 未命中返回 null。 */
    String get(String key);

    void set(String key, String value, long ttlSeconds);

    void delete(String key);

    boolean has(String key);

    /**
     * 写空值标记，用于防缓存穿透。
     *
     * @param ttlSeconds 建议远小于正常值（默认 60 秒），避免脏数据长期占位
     */
    void setNull(String key, long ttlSeconds);

    /** 该 key 是否是「空值标记」。 */
    boolean isNullMarked(String key);

    /**
     * 原子自增，用于「重建缓存互斥锁」和简单限流。
     *
     * @return 自增后的值；实现不可用时返回 1（退化为不加锁）
     */
    long increment(String key, long ttlSeconds);

    /** 当前实现名，便于 /api/ping 暴露健康信息。 */
    String backend();
}
