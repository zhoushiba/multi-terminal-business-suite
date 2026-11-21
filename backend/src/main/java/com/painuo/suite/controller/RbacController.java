package com.painuo.suite.controller;

import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.Result;
import com.painuo.suite.dto.RbacDtos;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.RbacService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户 / 角色 / 菜单。
 *
 * <p>权限串约定：{@code 资源:动作}，例如 {@code content:write}、{@code signup:verify}。
 * 菜单表里 type=2 的记录就是这些按钮权限，角色授权时勾选后即可生效——
 * 后端用 {@code @RequirePerm} 校验，前端用 {@code /api/auth/routes} 返回的 perm 控制按钮显隐，
 * 两端读的是同一份数据。</p>
 */
@RestController
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ------------------------------------------------------------------ 用户

    @GetMapping("/api/users")
    @RequirePerm("user:list")
    public Result<PageResult<RbacDtos.UserVO>> pageUsers(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return Result.ok(rbacService.pageUsers(keyword, status, PageQuery.of(page, size, null)));
    }

    @PostMapping("/api/users")
    @RequirePerm("user:write")
    public Result<RbacDtos.UserVO> createUser(@Valid @RequestBody RbacDtos.UserSaveRequest request) {
        return Result.ok(rbacService.createUser(request), "用户已创建");
    }

    @PutMapping("/api/users/{id}")
    @RequirePerm("user:write")
    public Result<RbacDtos.UserVO> updateUser(@PathVariable Long id,
                                              @Valid @RequestBody RbacDtos.UserSaveRequest request) {
        return Result.ok(rbacService.updateUser(id, request), "用户已更新");
    }

    @DeleteMapping("/api/users/{id}")
    @RequirePerm("user:write")
    public Result<Void> deleteUser(@PathVariable Long id) {
        rbacService.deleteUser(id);
        return Result.ok(null, "用户已删除");
    }

    @PostMapping("/api/users/{id}/reset-password")
    @RequirePerm("user:write")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        rbacService.resetPassword(id, body.get("password"));
        return Result.ok(null, "密码已重置");
    }

    // ------------------------------------------------------------------ 角色

    @GetMapping("/api/roles")
    @RequirePerm("role:list")
    public Result<List<RbacDtos.RoleVO>> listRoles() {
        return Result.ok(rbacService.listRoles());
    }

    @PostMapping("/api/roles")
    @RequirePerm("role:write")
    public Result<RbacDtos.RoleVO> createRole(@Valid @RequestBody RbacDtos.RoleSaveRequest request) {
        return Result.ok(rbacService.createRole(request), "角色已创建");
    }

    @PutMapping("/api/roles/{id}")
    @RequirePerm("role:write")
    public Result<RbacDtos.RoleVO> updateRole(@PathVariable Long id,
                                              @Valid @RequestBody RbacDtos.RoleSaveRequest request) {
        return Result.ok(rbacService.updateRole(id, request), "角色已更新");
    }

    @DeleteMapping("/api/roles/{id}")
    @RequirePerm("role:write")
    public Result<Void> deleteRole(@PathVariable Long id) {
        rbacService.deleteRole(id);
        return Result.ok(null, "角色已删除");
    }

    // ------------------------------------------------------------------ 菜单

    /** 完整菜单树（含按钮），角色授权弹窗用。 */
    @GetMapping("/api/menus")
    @RequirePerm("role:list")
    public Result<List<RbacDtos.MenuVO>> menuTree() {
        return Result.ok(rbacService.menuTree());
    }
}
