package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.painuo.suite.cache.CacheKeys;
import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.dto.AuthDtos;
import com.painuo.suite.entity.SysMenu;
import com.painuo.suite.entity.SysRole;
import com.painuo.suite.entity.SysUser;
import com.painuo.suite.entity.SysUserRole;
import com.painuo.suite.mapper.SysMenuMapper;
import com.painuo.suite.mapper.SysRoleMapper;
import com.painuo.suite.mapper.SysUserMapper;
import com.painuo.suite.mapper.SysUserRoleMapper;
import com.painuo.suite.security.AuthContext;
import com.painuo.suite.security.AuthPrincipal;
import com.painuo.suite.security.JwtTokenProvider;
import com.painuo.suite.security.PasswordHasher;
import com.painuo.suite.wechat.WechatClient;
import com.painuo.suite.wechat.WechatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证与授权入口。
 *
 * <p>三件事：</p>
 * <ol>
 *   <li><b>登录</b>：账号密码（运营后台）与微信 code（小程序）双通道，最终都落到 {@link #issue}；</li>
 *   <li><b>刷新</b>：refreshToken 一次性轮换——旧的 jti 从白名单删除，被重放的旧 token 立即失效；</li>
 *   <li><b>动态路由</b>：按用户的角色查 {@code sys_menu}，前端据此生成侧边栏与路由表。</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 超级管理员角色编码：跳过按钮级权限校验（见 PermAspect）。 */
    public static final String ROLE_ADMIN = "admin";

    /** 小程序用户默认角色，只用于标识来源，不含后台菜单。 */
    private static final String ROLE_MEMBER = "member";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final CacheService cache;
    private final PasswordHasher passwordHasher;
    private final WechatClient wechatClient;

    public AuthService(SysUserMapper userMapper,
                       SysUserRoleMapper userRoleMapper,
                       SysRoleMapper roleMapper,
                       SysMenuMapper menuMapper,
                       JwtTokenProvider jwtTokenProvider,
                       CacheService cache,
                       PasswordHasher passwordHasher,
                       WechatClient wechatClient) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cache = cache;
        this.passwordHasher = passwordHasher;
        this.wechatClient = wechatClient;
    }

    /**
     * 账号密码登录。
     *
     * <p>用户名不存在与密码错误返回同一提示，避免账号枚举。</p>
     */
    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (user == null || !passwordHasher.matches(request.password(), user.getPassword())) {
            log.warn("登录失败: username={}", request.username());
            throw BizException.of(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        ensureEnabled(user);
        return issue(user);
    }

    /**
     * 刷新令牌（一次性轮换）。
     *
     * <p>如果攻击者偷到旧 refreshToken 但在合法用户刷新之后才使用，会因白名单已删除而失败，
     * 这正是"轮换"相对"长期有效令牌"的核心收益。</p>
     */
    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        JwtTokenProvider.ParsedToken parsed =
                jwtTokenProvider.parse(refreshToken, JwtTokenProvider.TYPE_REFRESH);
        String whitelistKey = CacheKeys.refreshToken(parsed.jti());
        if (!cache.has(whitelistKey)) {
            log.warn("刷新令牌不在白名单（已轮换或已登出）: userId={}", parsed.userId());
            throw BizException.of(ResultCode.REFRESH_TOKEN_INVALID);
        }
        cache.delete(whitelistKey);
        SysUser user = userMapper.selectById(parsed.userId());
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        ensureEnabled(user);
        return issue(user);
    }

    /**
     * 小程序登录：code 换 openid，首次登录自动建号。
     *
     * <p>{@code app.wechat.mock=true} 时 openid 由 code 的哈希派生，
     * 因此同一个 code 反复登录会命中同一个用户，本地可以完整演示"登录一次、后续免登"。</p>
     */
    @Transactional
    public AuthDtos.TokenResponse wxLogin(AuthDtos.WxLoginRequest request) {
        WechatSession session = wechatClient.code2Session(request.code());
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getOpenid, session.openId())
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUser();
            user.setUsername("wx_" + suffix(session.openId(), 12));
            user.setNickname(hasText(request.nickname()) ? request.nickname() : "微信用户");
            user.setAvatar(request.avatar());
            user.setStatus(1);
            user.setSource("miniapp");
            user.setOpenid(session.openId());
            userMapper.insert(user);
            bindMemberRole(user.getId());
            log.info("小程序首次登录已建档: userId={}, openid={}, mock={}",
                    user.getId(), session.openId(), session.mock());
        } else {
            ensureEnabled(user);
            boolean changed = false;
            if (hasText(request.nickname()) && !request.nickname().equals(user.getNickname())) {
                user.setNickname(request.nickname());
                changed = true;
            }
            if (hasText(request.avatar()) && !request.avatar().equals(user.getAvatar())) {
                user.setAvatar(request.avatar());
                changed = true;
            }
            if (changed) {
                userMapper.updateById(user);
            }
        }
        return issue(user);
    }

    /**
     * 退出登录：把 accessToken 的 jti 加入黑名单，TTL 与令牌剩余有效期一致，
     * 黑名单不会无限增长。
     */
    public void logout(String authorization) {
        String token = extractBearer(authorization);
        if (token == null) {
            return;
        }
        try {
            JwtTokenProvider.ParsedToken parsed =
                    jwtTokenProvider.parse(token, JwtTokenProvider.TYPE_ACCESS);
            long ttl = jwtTokenProvider.remainingSeconds(parsed.expiresAt());
            cache.set(CacheKeys.blacklist(parsed.jti()), "1", ttl);
        } catch (BizException ex) {
            // 令牌本就过期/非法，无需拉黑
            log.debug("登出时令牌不可解析: {}", ex.getMessage());
        }
    }

    /** 当前登录用户资料（权限实时查库，角色调整后无需重新登录即可生效）。 */
    public AuthDtos.Profile profile() {
        AuthPrincipal principal = AuthContext.require();
        SysUser user = userMapper.selectById(principal.userId());
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        return new AuthDtos.Profile(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getPhone(), userMapper.selectRoleCodes(user.getId()),
                userMapper.selectPermissions(user.getId()));
    }

    /** 当前用户的动态路由树（目录/菜单，按 sort 升序）。 */
    public List<AuthDtos.RouteVO> routes() {
        AuthPrincipal principal = AuthContext.require();
        boolean isAdmin = principal.roles() != null && principal.roles().contains(ROLE_ADMIN);
        List<SysMenu> menus = menuMapper.selectRoutableMenus(principal.userId(), isAdmin);
        Map<Long, List<SysMenu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return toRoutes(byParent, 0L);
    }

    private List<AuthDtos.RouteVO> toRoutes(Map<Long, List<SysMenu>> byParent, Long parentId) {
        List<SysMenu> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        return children.stream()
                .map(menu -> new AuthDtos.RouteVO(menu.getId(), menu.getParentId(), menu.getName(),
                        menu.getPath(), menu.getComponent(), menu.getIcon(), menu.getPerm(),
                        menu.getType(), menu.getSort(), toRoutes(byParent, menu.getId())))
                .toList();
    }

    // ------------------------------------------------------------------ 内部方法

    private AuthDtos.TokenResponse issue(SysUser user) {
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        List<String> permissions = userMapper.selectPermissions(user.getId());

        JwtTokenProvider.IssuedToken access = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getUsername(), user.getNickname(), roles, permissions);
        JwtTokenProvider.IssuedToken refresh = jwtTokenProvider.issueRefreshToken(
                user.getId(), user.getUsername());

        // refreshToken 白名单：jti -> userId，过期时间与令牌一致
        cache.set(CacheKeys.refreshToken(refresh.jti()), String.valueOf(user.getId()), refresh.expiresIn());

        touchLoginTime(user.getId());

        AuthDtos.Profile profile = new AuthDtos.Profile(user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), user.getPhone(), roles, permissions);
        return new AuthDtos.TokenResponse(access.token(), refresh.token(), access.expiresIn(), profile);
    }

    private void touchLoginTime(Long userId) {
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    private void bindMemberRole(Long userId) {
        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, ROLE_MEMBER).last("LIMIT 1"));
        if (role == null) {
            log.debug("未找到 {} 角色，跳过绑定", ROLE_MEMBER);
            return;
        }
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        userRoleMapper.insert(relation);
    }

    private void ensureEnabled(SysUser user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.of(ResultCode.FORBIDDEN, "账号已被停用，请联系管理员");
        }
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String suffix(String value, int length) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= length ? value : value.substring(value.length() - length);
    }
}
