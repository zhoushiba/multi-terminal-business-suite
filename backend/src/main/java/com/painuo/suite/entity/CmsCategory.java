package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 内容分类（两级：parentId=0 为一级）。 */
@Data
@TableName("cms_category")
public class CmsCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private Long parentId;

    private Integer sort;

    /** 1 启用 / 0 停用。 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
