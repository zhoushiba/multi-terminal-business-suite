package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单 / 按钮权限。
 *
 * <p>type 三态：0 目录、1 菜单（有路由）、2 按钮（只有 perm，用来做细粒度鉴权）。
 * 前端动态路由只读 0/1，按钮权限读 2 的 perm 列表。</p>
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    /** 前端路由 path，如 /content/list。 */
    private String path;

    /** 前端组件路径，如 content/list/index。 */
    private String component;

    private String icon;

    /** 0 目录 / 1 菜单 / 2 按钮。 */
    private Integer type;

    /** 权限标识，如 cms:content:create；目录/菜单可为空。 */
    private String perm;

    private Integer sort;

    /** 1 隐藏（有路由但不在侧边栏展示）。 */
    private Integer hidden;

    private LocalDateTime createdAt;
}
