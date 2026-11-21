package com.painuo.suite.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页参数与排序白名单单元测试。
 *
 * <p>分页放大（size=1000000）与 ORDER BY 注入是列表接口最常见的两个坑，
 * 这里把它们收敛成一个可测的组件。</p>
 */
@DisplayName("分页参数")
class PageQueryTest {

    @Test
    @DisplayName("缺省值：第 1 页、每页 10 条")
    void defaultsWhenMissing() {
        PageQuery query = PageQuery.of(null, null, null);
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("页码非法时降级为第 1 页")
    void invalidPageFallsBack() {
        assertThat(PageQuery.of(0, 20, null).page()).isEqualTo(1);
        assertThat(PageQuery.of(-5, 20, null).page()).isEqualTo(1);
    }

    @Test
    @DisplayName("每页条数上限 100，防止分页放大")
    void sizeIsCapped() {
        assertThat(PageQuery.of(1, 1_000_000, null).size()).isEqualTo(PageQuery.MAX_SIZE);
        assertThat(PageQuery.of(1, 0, null).size()).isEqualTo(1);
        assertThat(PageQuery.of(1, 20, null).size()).isEqualTo(20);
    }

    @Test
    @DisplayName("排序字段只接受白名单，其余回退默认排序")
    void sortUsesWhitelist() {
        List<String> allowed = List.of("created_at", "view_count");
        assertThat(PageQuery.of(1, 10, "created_at,desc").orderBy(allowed, "id DESC"))
                .isEqualTo("created_at DESC");
        assertThat(PageQuery.of(1, 10, "view_count asc").orderBy(allowed, "id DESC"))
                .isEqualTo("view_count ASC");
        // 注入尝试必须被白名单拦下
        assertThat(PageQuery.of(1, 10, "id; DROP TABLE cms_content").orderBy(allowed, "id DESC"))
                .isEqualTo("id DESC");
        assertThat(PageQuery.of(1, 10, null).orderBy(allowed, "id DESC")).isEqualTo("id DESC");
    }
}
