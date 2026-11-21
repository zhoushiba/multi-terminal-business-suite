package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.CmsContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CmsContentMapper extends BaseMapper<CmsContent> {

    /**
     * 浏览数 +1（不更新 updated_at，避免"看了就算编辑过"污染内容排序）。
     *
     * <p>高并发下浏览量其实应该走 Redis 计数后批量回写，这里保留直连作为基线实现，
     * 文档 {@code docs/architecture.md} 里写了演进路径。</p>
     */
    @Update("UPDATE cms_content SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementViewCount(@Param("id") Long id);

    /** 顶部最大 sort，新增内容时排在最后。 */
    @org.apache.ibatis.annotations.Select(
            "SELECT COALESCE(MAX(sort), 0) FROM cms_content WHERE category_id = #{categoryId} AND deleted = 0")
    Integer selectMaxSort(@Param("categoryId") Long categoryId);
}
