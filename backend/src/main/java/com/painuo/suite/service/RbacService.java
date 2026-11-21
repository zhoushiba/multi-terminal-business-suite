package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.dto.RbacDtos;
import com.painuo.suite.entity.SysMenu;
import com.painuo.suite.entity.SysRole;
import com.painuo.suite.entity.SysRoleMenu;
import com.painuo.suite.entity.SysUser;
import com.painuo.suite.entity.SysUserRole;
import com.painuo.suite.mapper.SysMenuMapper;
import com.painuo.suite.mapper.SysRoleMapper;
import com.painuo.suite.mapper.SysRoleMenuMapper;
import com.painuo.suite.mapper.SysUserMapper;
import com.painuo.suite.mapper.SysUserRoleMapper;
import com.painuo.suite.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户 / 角色 / 菜单（RBAC）。
 *
 * <p>权限模型是标准的 用户 → 角色 → 菜单/按钮 三层：
 * 用户能看到的菜单由 {@code sys_menu.type IN (0,1)} 决定，能点的按钮由 {@code perm} 决定。
 * 角色授权是"全量重写"语义：先删旧关联再批量插入，天然幂等。</p>
 */
@Service
public class RbacService {

    /** 超级管理员角色编码，拥有全部菜单（不查关联表）。 */
    private static final String SUPER_ADMIN = "admin";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordHasher passwordHasher;

    public RbacService(SysUserMapper userMapper,
                       SysUserRoleMapper userRoleMapper,
                       SysRoleMapper roleMapper,
                       SysRoleMenuMapper roleMenuMapper,
                       SysMenuMapper menuMapper,
                       PasswordHasher passwordHasher) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.passwordHasher = passwordHasher;
    }

    // ------------------------------------------------------------------ 用户

    public PageResult<RbacDtos.UserVO> pageUsers(String keyword, Integer status, PageQuery pageQuery) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .and(hasText(keyword), w -> w.like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getNickname, keyword)
                        .or().like(SysUser::getPhone, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(pageQuery.toPage(), wrapper);
        List<SysUser> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getCurrent(), page.getSize(), page.getTotal());
        }

        // 一次查出所有用户的角色关联，再按 userId 分组，避免逐行查询
        List<Long> userIds = records.stream().map(SysUser::getId).toList();
        List<SysUserRole> relations = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .in(SysUserRole::getUserId, userIds));
        Map<Long, List<Long>> roleIdsByUser = new HashMap<>();
        for (SysUserRole relation : relations) {
            roleIdsByUser.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>())
                    .add(relation.getRoleId());
        }
        Map<Long, SysRole> roleCache = loadRoles(relations.stream().map(SysUserRole::getRoleId).toList());

        List<RbacDtos.UserVO> items = records.stream().map(user -> {
            List<Long> roleIds = roleIdsByUser.getOrDefault(user.getId(), List.of());
            List<String> roleCodes = roleIds.stream()
                    .map(roleCache::get).filter(Objects::nonNull)
                    .map(SysRole::getCode).toList();
            return new RbacDtos.UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                    user.getPhone(), user.getStatus(), user.getSource(), roleCodes, roleIds,
                    user.getLastLoginAt(), user.getCreatedAt());
        }).toList();
        return PageResult.of(items, page.getCurrent(), page.getSize(), page.getTotal());
    }

    @Transactional
    public RbacDtos.UserVO createUser(RbacDtos.UserSaveRequest request) {
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw BizException.of(ResultCode.CONFLICT, "用户名已存在");
        }
        if (!hasText(request.password())) {
            throw BizException.param("新增用户必须设置密码");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordHasher.encode(request.password()));
        user.setNickname(hasText(request.nickname()) ? request.nickname() : request.username());
        user.setPhone(request.phone());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setSource("web");
        userMapper.insert(user);
        bindRoles(user.getId(), request.roleIds());
        return toUserVO(user);
    }

    @Transactional
    public RbacDtos.UserVO updateUser(Long id, RbacDtos.UserSaveRequest request) {
        SysUser existing = requireUser(id);
        SysUser patch = new SysUser();
        patch.setId(id);
        if (hasText(request.password())) {
            patch.setPassword(passwordHasher.encode(request.password()));
        }
        patch.setNickname(request.nickname());
        patch.setPhone(request.phone());
        patch.setStatus(request.status());
        userMapper.updateById(patch);
        if (request.roleIds() != null) {
            bindRoles(id, request.roleIds());
        }
        return toUserVO(userMapper.selectById(existing.getId()));
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser user = requireUser(id);
        if (SUPER_ADMIN.equals(user.getUsername())) {
            throw BizException.forbidden("内置超级管理员不允许删除");
        }
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
    }

    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        if (!hasText(rawPassword) || rawPassword.length() < 6) {
            throw BizException.param("密码至少 6 位");
        }
        requireUser(id);
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(passwordHasher.encode(rawPassword));
        userMapper.updateById(patch);
    }

    // ------------------------------------------------------------------ 角色

    public List<RbacDtos.RoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getSort).orderByAsc(SysRole::getId));
        if (roles.isEmpty()) {
            return List.of();
        }
        List<SysRoleMenu> relations = roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                .in(SysRoleMenu::getRoleId, roles.stream().map(SysRole::getId).toList()));
        Map<Long, List<Long>> menuIdsByRole = new HashMap<>();
        for (SysRoleMenu relation : relations) {
            menuIdsByRole.computeIfAbsent(relation.getRoleId(), key -> new ArrayList<>())
                    .add(relation.getMenuId());
        }
        return roles.stream().map(role -> new RbacDtos.RoleVO(role.getId(), role.getCode(), role.getName(),
                role.getRemark(), role.getSort(), menuIdsByRole.getOrDefault(role.getId(), List.of()))).toList();
    }

    @Transactional
    public RbacDtos.RoleVO createRole(RbacDtos.RoleSaveRequest request) {
        Long exists = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, request.code()));
        if (exists != null && exists > 0) {
            throw BizException.of(ResultCode.CONFLICT, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(request.code());
        role.setName(request.name());
        role.setRemark(request.remark());
        role.setSort(request.sort() == null ? 0 : request.sort());
        roleMapper.insert(role);
        bindMenus(role.getId(), request.menuIds());
        return new RbacDtos.RoleVO(role.getId(), role.getCode(), role.getName(), role.getRemark(),
                role.getSort(), request.menuIds() == null ? List.of() : request.menuIds());
    }

    @Transactional
    public RbacDtos.RoleVO updateRole(Long id, RbacDtos.RoleSaveRequest request) {
        SysRole existing = requireRole(id);
        if (SUPER_ADMIN.equals(existing.getCode()) && request.menuIds() != null) {
            throw BizException.forbidden("超级管理员角色拥有全部权限，无需也不允许调整菜单");
        }
        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setCode(request.code());
        patch.setName(request.name());
        patch.setRemark(request.remark());
        patch.setSort(request.sort());
        roleMapper.updateById(patch);
        if (request.menuIds() != null) {
            bindMenus(id, request.menuIds());
        }
        return new RbacDtos.RoleVO(id, request.code(), request.name(), request.remark(), request.sort(),
                request.menuIds() == null ? List.of() : request.menuIds());
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole role = requireRole(id);
        if (SUPER_ADMIN.equals(role.getCode())) {
            throw BizException.forbidden("内置超级管理员角色不允许删除");
        }
        Long bound = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, id));
        if (bound != null && bound > 0) {
            throw BizException.of(ResultCode.CONFLICT, "该角色下还有用户，无法删除");
        }
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
        roleMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 菜单

    /** 完整菜单树（含按钮），角色授权界面用。 */
    public List<RbacDtos.MenuVO> menuTree() {
        List<SysMenu> all = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getSort).orderByAsc(SysMenu::getId));
        Map<Long, List<SysMenu>> byParent = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            byParent.computeIfAbsent(menu.getParentId() == null ? 0L : menu.getParentId(),
                    key -> new ArrayList<>()).add(menu);
        }
        return buildMenuTree(byParent, 0L);
    }

    // ------------------------------------------------------------------ 内部方法

    private Map<Long, SysRole> loadRoles(List<Long> roleIds) {
        Map<Long, SysRole> map = new HashMap<>();
        List<Long> distinct = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return map;
        }
        for (SysRole role : roleMapper.selectBatchIds(distinct)) {
            map.put(role.getId(), role);
        }
        return map;
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    private void bindMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        }
    }

    private RbacDtos.UserVO toUserVO(SysUser user) {
        List<String> roleCodes = userMapper.selectRoleCodes(user.getId());
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).toList();
        return new RbacDtos.UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getPhone(), user.getStatus(), user.getSource(), roleCodes, roleIds,
                user.getLastLoginAt(), user.getCreatedAt());
    }

    private List<RbacDtos.MenuVO> buildMenuTree(Map<Long, List<SysMenu>> byParent, Long parentId) {
        List<SysMenu> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        return children.stream().map(menu -> new RbacDtos.MenuVO(menu.getId(), menu.getParentId(),
                menu.getName(), menu.getPath(), menu.getComponent(), menu.getIcon(), menu.getType(),
                menu.getPerm(), menu.getSort(), menu.getHidden(),
                buildMenuTree(byParent, menu.getId()))).toList();
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户");
        }
        return user;
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色");
        }
        return role;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
