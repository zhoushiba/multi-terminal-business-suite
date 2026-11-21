package com.painuo.suite.security;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 签发与校验。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>accessToken 短（默认 30 分钟），refreshToken 长（默认 7 天）且带 {@code jti}；</li>
 *   <li>refreshToken 的 {@code jti} 会登记到缓存（内存或 Redis），刷新时做「一次性轮换」：
 *       旧的 jti 立刻删除，被重放的旧 token 直接 401；</li>
 *   <li>角色/权限直接写进 accessToken 的 claims，避免每个请求都查库；</li>
 *   <li>退出登录 = 把 accessToken 的 jti 放进黑名单，TTL 与 token 剩余时间一致。</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";
    private static final String CLAIM_USERNAME = "usr";
    private static final String CLAIM_NICKNAME = "nick";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final AppProperties.Jwt props;

    public JwtTokenProvider(AppProperties appProperties) {
        this.props = appProperties.jwt();
        String secret = props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 字节，请通过环境变量 APP_JWT_SECRET 配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发访问令牌。
     *
     * @return 令牌串 + jti + 过期秒数
     */
    public IssuedToken issueAccessToken(Long userId, String username, String nickname,
                                        List<String> roles, List<String> permissions) {
        return build(userId, username, nickname, roles, permissions, TYPE_ACCESS, props.accessTokenSeconds());
    }

    /** 签发刷新令牌（只带 userId，权限变更后必须重新登录换新 refreshToken）。 */
    public IssuedToken issueRefreshToken(Long userId, String username) {
        return build(userId, username, null, List.of(), List.of(), TYPE_REFRESH, props.refreshTokenSeconds());
    }

    private IssuedToken build(Long userId, String username, String nickname,
                              List<String> roles, List<String> permissions,
                              String type, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .issuer(props.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        CLAIM_TYPE, type,
                        CLAIM_USERNAME, username,
                        CLAIM_NICKNAME, nickname == null ? "" : nickname,
                        CLAIM_ROLES, roles,
                        CLAIM_PERMS, permissions))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, ttlSeconds, expiry.toEpochMilli());
    }

    /**
     * 解析并校验令牌。
     *
     * @throws BizException 40102/40103
     */
    public ParsedToken parse(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).requireIssuer(props.issuer())
                    .build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException ex) {
            throw BizException.of(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw BizException.of(ResultCode.TOKEN_INVALID);
        }
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw BizException.of(ResultCode.TOKEN_INVALID, "令牌类型不匹配");
        }
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.getOrDefault(CLAIM_ROLES, List.of());
        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) claims.getOrDefault(CLAIM_PERMS, List.of());
        return new ParsedToken(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_NICKNAME, String.class),
                roles, perms, claims.getId(), claims.getExpiration().getTime());
    }

    /** 令牌剩余有效期（秒），用于黑名单 TTL，避免黑名单无限膨胀。 */
    public long remainingSeconds(long expiryMillis) {
        return Math.max(1, (expiryMillis - System.currentTimeMillis()) / 1000);
    }

    /** 供缓存 key 使用的哈希，避免把 token 原文写进 Redis key。 */
    public String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public record IssuedToken(String token, String jti, long expiresIn, long expiresAt) {
    }

    public record ParsedToken(Long userId, String username, String nickname,
                              List<String> roles, List<String> permissions,
                              String jti, long expiresAt) {
    }
}
