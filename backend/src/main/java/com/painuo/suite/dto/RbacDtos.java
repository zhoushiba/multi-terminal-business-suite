package com.painuo.suite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 用户、角色、菜单（RBAC）出入参。 */
public final class RbacDtos {

    private RbacDtos() {
    }

    /** 新增/编辑用户。编辑时 password 留空表示不改密码。 */
    public record UserSaveRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,29}$", message = "用户名需字母开头，3~30 位字母数字下划线")
            String username,

            @Size(min = 6, max = 64, message = "密码长度 6~64 位")
            String password,

            @Size(max = 50, message = "昵称最长 50 字")
            String nickname,

            @Size(max = 20, message = "手机号过长")
            String phone,

            Integer status,

            List<Long> roleIds
    ) {
    }

    /** 用户视图（含角色编码，前端表格直接展示）。 */
    public record UserVO(
            Long id,
            String username,
            String nickname,
            String avatar,
            String phone,
            Integer status,
            String source,
            List<String> roles,
            List<Long> roleIds,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
    }

    /** 角色新增/编辑（menuIds 为勾选的菜单/按钮）。 */
    public record RoleSaveRequest(
            @NotBlank(message = "角色编码不能为空")
            @Pattern(regexp = "^[a-z][a-z0-9_]{1,29}$", message = "角色编码需小写字母开头，2~30 位")
            String code,

            @NotBlank(message = "角色名称不能为空")
            @Size(max = 50, message = "角色名称最长 50 字")
            String name,

            @Size(max = 200, message = "备注最长 200 字")
            String remark,

            Integer sort,

            List<Long> menuIds
    ) {
    }

    /** 角色视图。 */
    public record RoleVO(
            Long id,
            String code,
            String name,
            String remark,
            Integer sort,
            List<Long> menuIds
    ) {
    }

    /** 菜单树视图（角色授权界面用，含按钮）。 */
    public record MenuVO(
            Long id,
            Long parentId,
            String name,
            String path,
            String component,
            String icon,
            Integer type,
            String perm,
            Integer sort,
            Integer hidden,
            List<MenuVO> children
    ) {
    }
}
