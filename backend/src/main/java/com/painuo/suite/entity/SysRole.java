package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色。code 是代码里判断的稳定标识（admin/editor/operator），name 是给人看的。 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String remark;

    private Integer sort;

    private LocalDateTime createdAt;
}
