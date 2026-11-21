package com.painuo.suite.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * 密码散列（PBKDF2-HMAC-SHA256）。
 *
 * <p>为什么不用 Spring Security 全家桶？本项目只需要"存密码 + 校验密码"两件事，
 * 引入 {@code spring-boot-starter-security} 会连带一整套过滤器链，反而要写更多配置去关掉它。
 * 这里直接用 JDK 自带的 PBKDF2：</p>
 *
 * <ul>
 *   <li>每个密码独立 16 字节随机盐；</li>
 *   <li>迭代 12 万次（OWASP 2023 对 PBKDF2-SHA256 的建议下限为 60 万次，
 *       此处按"单核 50ms 内完成"折中，生产可调到 60 万）；</li>
 *   <li>存储格式 {@code pbkdf2$迭代次数$盐(hex)$散列(hex)}，迭代次数随密码一起存，
 *       将来提高强度可以平滑迁移（登录成功时按新参数重算）。</li>
 * </ul>
 */
@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    /** 生成可入库的密码串。 */
    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + HexFormat.of().formatHex(salt)
                + "$" + HexFormat.of().formatHex(hash);
    }

    /**
     * 校验密码。
     *
     * <p>注意：不抛异常、只返回 false，避免调用方把"格式错误"和"密码错误"区分开来
     * 给攻击者提供信息。</p>
     */
    public boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = HexFormat.of().parseHex(parts[2]);
            byte[] expected = HexFormat.of().parseHex(parts[3]);
            byte[] actual = pbkdf2(rawPassword, salt, iterations);
            // 定长比较，避免时序侧信道
            if (actual.length != expected.length) {
                return false;
            }
            int diff = 0;
            for (int i = 0; i < actual.length; i++) {
                diff |= actual[i] ^ expected[i];
            }
            return diff == 0;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 不可用", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
