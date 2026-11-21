package com.painuo.suite.common;

import java.util.List;

/**
 * 统一分页结果。
 *
 * <p>与 MyBatis-Plus 的 {@code IPage} 解耦，Controller 只返回这个结构，
 * 前端不必关心 ORM 细节。</p>
 *
 * @param list     当前页数据
 * @param page     页码，从 1 开始
 * @param size     每页条数
 * @param total    总条数
 * @param pages    总页数
 * @param hasNext  是否还有下一页（无限滚动/上拉加载直接用）
 */
public record PageResult<T>(
        List<T> list,
        long page,
        long size,
        long total,
        long pages,
        boolean hasNext
) {

    public static <T> PageResult<T> of(List<T> list, long page, long size, long total) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(list, page, size, total, pages, page < pages);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), page, size, 0, 0, false);
    }
}
