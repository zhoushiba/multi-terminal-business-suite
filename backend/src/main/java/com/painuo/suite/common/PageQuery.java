package com.painuo.suite.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 统一的查询分页参数。
 *
 * <p>把"缺省值 + 上限保护"收敛到一个地方：{@code size} 最大 100，
 * 防止调用方传 {@code size=1000000} 把库拖垮（也称为分页放大攻击）。
 * 排序字段只接受白名单内的列名，避免 ORDER BY 注入。</p>
 */
public record PageQuery(int page, int size, String sort) {

    public static final int MAX_SIZE = 100;

    /** 从 query string 构造，非法值降级为安全默认值而不是抛 400。 */
    public static PageQuery of(Integer page, Integer size, String sort) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null) ? 10 : Math.min(Math.max(size, 1), MAX_SIZE);
        return new PageQuery(p, s, sort);
    }

    /** 生成 MyBatis-Plus 分页对象。 */
    public <T> Page<T> toPage() {
        return new Page<>(page, size);
    }

    /** 把 MyBatis-Plus 的分页结果翻译成本项目的统一响应结构。 */
    public <T> PageResult<T> wrap(IPage<T> result) {
        return PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 排序白名单校验：不在白名单内则回退到默认排序。 */
    public String orderBy(List<String> allowed, String fallback) {
        if (sort == null || sort.isBlank()) {
            return fallback;
        }
        // 支持 "field,asc" / "field desc" 两种写法
        String normalized = sort.trim().replace(',', ' ').replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");
        String column = parts[0];
        if (!allowed.contains(column)) {
            return fallback;
        }
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
        return column + (desc ? " DESC" : " ASC");
    }
}
