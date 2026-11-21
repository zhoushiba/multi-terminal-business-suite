package com.painuo.suite.cache;

import com.painuo.suite.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内缓存实现（无 Redis 时的 fallback）。
 *
 * <p>虽然是"降级方案"，但把三个缓存工程问题都做掉了：</p>
 * <ol>
 *   <li><b>穿透</b>：{@link #setNull} 写入短 TTL 的空值标记，重复查同一个不存在的 ID 不再打库；</li>
 *   <li><b>雪崩</b>：{@link #jitter} 给所有 TTL 加 ±10% 随机抖动，避免整点批量过期；</li>
 *   <li><b>击穿</b>：{@link #increment} 提供原子自增，业务侧可据此实现"只让一个线程回源"的互斥锁。</li>
 * </ol>
 *
 * <p>限制：仅单机有效，多实例部署必须切 {@code app.cache.type=redis}。</p>
 */
public class MemoryCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCacheService.class);

    private static final String NULL_MARK = "\u0000NULL";
    private static final int HARD_CAPACITY = 100_000;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AppProperties.Cache props;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public MemoryCacheService(AppProperties.Cache props) {
        this.props = props;
        log.info("缓存实现: memory(prefixed={}, defaultTtl={}s, jitter={})",
                props.keyPrefix(), props.defaultTtlSeconds(), props.ttlJitterRatio());
    }

    @Override
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            store.remove(key, entry);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return NULL_MARK.equals(entry.value) ? null : entry.value;
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        if (value == null) {
            setNull(key, props.nullValueTtlSeconds());
            return;
        }
        evictIfNeeded();
        store.put(key, new Entry(value, System.currentTimeMillis() + jitter(ttlSeconds) * 1000));
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean has(String key) {
        return get(key) != null || isNullMarked(key);
    }

    @Override
    public void setNull(String key, long ttlSeconds) {
        evictIfNeeded();
        store.put(key, new Entry(NULL_MARK, System.currentTimeMillis() + jitter(ttlSeconds) * 1000));
    }

    @Override
    public boolean isNullMarked(String key) {
        Entry entry = store.get(key);
        return entry != null
                && entry.expireAt >= System.currentTimeMillis()
                && NULL_MARK.equals(entry.value);
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        long now = System.currentTimeMillis();
        Entry updated = store.compute(key, (k, old) -> {
            if (old == null || old.expireAt < now) {
                return new Entry("1", now + ttlSeconds * 1000);
            }
            long count = Long.parseLong(old.value) + 1;
            return new Entry(String.valueOf(count), old.expireAt);
        });
        return Long.parseLong(updated.value);
    }

    @Override
    public String backend() {
        return "memory";
    }

    /** 命中率，供 /api/ping 展示，方便压测时观察缓存效果。 */
    public String stats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? "0/0" : h + "/" + total + " (" + (h * 100 / total) + "%)";
    }

    private long jitter(long ttlSeconds) {
        double ratio = props.ttlJitterRatio();
        if (ratio <= 0) {
            return Math.max(1, ttlSeconds);
        }
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-ratio, ratio);
        return Math.max(1, Math.round(ttlSeconds * factor));
    }

    private void evictIfNeeded() {
        if (store.size() < HARD_CAPACITY) {
            return;
        }
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
        if (store.size() >= HARD_CAPACITY) {
            store.clear();
            log.warn("内存缓存达到上限 {}，已整体清空（生产环境请切换 Redis）", HARD_CAPACITY);
        }
    }

    private record Entry(String value, long expireAt) {
    }
}
