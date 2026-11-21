package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 内容-标签关联。 */
@Data
@TableName("cms_content_tag")
public class CmsContentTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contentId;

    private Long tagId;
}
