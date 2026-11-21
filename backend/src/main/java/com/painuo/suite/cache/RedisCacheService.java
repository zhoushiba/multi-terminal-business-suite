package com.painuo.suite.cache;

import com.painuo.suite.config.AppProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Redis 缓存实现（Lettuce 直连）。
 *
 * <p>为什么不用 {@code spring-boot-starter-data-redis}：官方 starter 会无条件装配
 * {@code RedisConnectionFactory}，本机没有 Redis 时容易在启动/健康检查阶段拖慢甚至报错。
 * 这里直接使用 Lettuce，由 {@code app.cache.type} 决定是否创建，<b>懒连接</b>：
 * 启动时不连、首次访问才连，断了自动重连。</p>
 *
 * <p>空值标记用 {@code \x00NULL}，与业务值不会冲突；TTL 抖动与内存实现保持一致。</p>
 */
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String NULL_MARK = "\u0000NULL";

    private final AppProperties.Cache props;
    private final RedisClient client;
    private volatile StatefulRedisConnection<String, String> connection;

    public RedisCacheService(AppProperties.Cache props) {
        this.props = props;
        this.client = RedisClient.create(props.redisUri());
        this.client.setDefaultTimeout(Duration.ofSeconds(2));
        log.info("缓存实现: redis(uri={})", mask(props.redisUri()));
    }

    private RedisCommands<String, String> commands() {
        StatefulRedisConnection<String, String> conn = connection;
        if (conn == null || !conn.isOpen()) {
            synchronized (this) {
                if (connection == null || !connection.isOpen()) {
                    connection = client.connect();
                }
                conn = connection;
            }
        }
        return conn.sync();
    }

    @Override
    public String get(String key) {
        try {
            String value = commands().get(key);
            if (value == null || NULL_MARK.equals(value)) {
                return null;
            }
            return value;
        } catch (RuntimeException ex) {
            // 缓存不可用时绝不阻断主流程，退化为"未命中"
            log.warn("Redis GET 失败，降级为未命中: key={}, err={}", key, ex.getMessage());
            return null;
        }
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        try {
            commands().setex(key, jitter(ttlSeconds), value);
        } catch (RuntimeException ex) {
            log.warn("Redis SET 失败: key={}, err={}", key, ex.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            commands().del(key);
        } catch (RuntimeException ex) {
            log.warn("Redis DEL 失败: key={}, err={}", key, ex.getMessage());
        }
    }

    @Override
    public boolean has(String key) {
        try {
            return Boolean.TRUE.equals(commands().exists(key) > 0);
        } catch (RuntimeException ex) {
            log.warn("Redis EXISTS 失败: key={}, err={}", key, ex.getMessage());
            return false;
        }
    }

    @Override
    public void setNull(String key, long ttlSeconds) {
        try {
            commands().setex(key, jitter(ttlSeconds), NULL_MARK);
        } catch (RuntimeException ex) {
            log.warn("Redis SETNULL 失败: key={}, err={}", key, ex.getMessage());
        }
    }

    @Override
    public boolean isNullMarked(String key) {
        try {
            return NULL_MARK.equals(commands().get(key));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        try {
            RedisCommands<String, String> cmd = commands();
            long value = cmd.incr(key);
            if (value == 1) {
                cmd.expire(key, ttlSeconds);
            }
            return value;
        } catch (RuntimeException ex) {
            log.warn("Redis INCR 失败，退化为不加锁: key={}, err={}", key, ex.getMessage());
            return 1;
        }
    }

    @Override
    public String backend() {
        return "redis";
    }

    private long jitter(long ttlSeconds) {
        double ratio = props.ttlJitterRatio();
        if (ratio <= 0) {
            return Math.max(1, ttlSeconds);
        }
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        return Math.max(1, Math.round(ttlSeconds * (1 + random.nextDouble(-ratio, ratio))));
    }

    private String mask(String uri) {
        if (uri == null) {
            return "null";
        }
        return uri.replaceAll("//([^:]+):[^@]+@", "//$1:***@");
    }

    @PreDestroy
    public void close() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            client.shutdown();
        } catch (RuntimeException ignored) {
            // 关闭阶段的异常无需影响进程退出
        }
    }
}
