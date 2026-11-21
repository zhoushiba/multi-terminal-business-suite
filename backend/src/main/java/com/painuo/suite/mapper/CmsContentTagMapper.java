package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.CmsContentTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CmsContentTagMapper extends BaseMapper<CmsContentTag> {

    /** 某内容的标签名列表（详情页展示）。 */
    @Select("""
            SELECT t.name FROM cms_content_tag ct
              JOIN cms_tag t ON t.id = ct.tag_id
             WHERE ct.content_id = #{contentId}
             ORDER BY t.id ASC
            """)
    List<String> selectTagNames(@Param("contentId") Long contentId);
}
