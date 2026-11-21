package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 内容标签（多对多，便于小程序"标签筛选"）。 */
@Data
@TableName("cms_tag")
public class CmsTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private LocalDateTime createdAt;
}
