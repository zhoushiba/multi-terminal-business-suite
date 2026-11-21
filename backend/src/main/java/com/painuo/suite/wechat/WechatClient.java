package com.painuo.suite.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 微信小程序登录对接（{@code code2session}）。
 *
 * <p>真实链路：小程序 {@code uni.login()} 拿 code → 后端带 appid/secret 请求
 * {@code https://api.weixin.qq.com/sns/jscode2session} → 换 openid + session_key。</p>
 *
 * <p>本机没有小程序 AppID，因此 {@code app.wechat.mock=true} 时走
 * {@link #mockSession(String)}：用 code 的 SHA-256 前缀生成<b>稳定但不真实</b>的 openid，
 * 这样同一 code 多次登录会命中同一用户，演示"登录 → 拿 token → 查数据"完整闭环，
 * 且绝不会把 mock 数据误认成真实用户（返回体里带 {@code mock:true}）。</p>
 */
@Component
public class WechatClient {

    private static final Logger log = LoggerFactory.getLogger(WechatClient.class);
    private static final String CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";

    private final AppProperties.Wechat props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WechatClient(AppProperties appProperties, ObjectMapper objectMapper) {
        // 嵌套配置不是独立 Bean，统一从顶层 AppProperties 取，避免出现
        // "No qualifying bean of type AppProperties$Wechat" 的启动失败
        this.props = appProperties.wechat();
        this.objectMapper = objectMapper;
    }

    public WechatSession code2Session(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code 不能为空");
        }
        if (props.mock() || props.appId() == null || props.appId().isBlank()) {
            return mockSession(code);
        }
        String url = CODE2SESSION
                + "?appid=" + encode(props.appId())
                + "&secret=" + encode(props.appSecret())
                + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = objectMapper.readTree(response.body());
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("code2session 失败: errcode={}, errmsg={}", errcode, node.path("errmsg").asText());
                throw BizException.of(ResultCode.WECHAT_ERROR,
                        "微信登录失败(" + errcode + ")：" + node.path("errmsg").asText());
            }
            return new WechatSession(
                    node.path("openid").asText(),
                    node.path("session_key").asText(),
                    node.path("unionid").asText(null),
                    false);
        } catch (IOException ex) {
            throw BizException.of(ResultCode.WECHAT_ERROR, "请求微信接口异常：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.WECHAT_ERROR, "请求微信接口被中断");
        }
    }

    /** 本地 mock：openid = "mock_" + sha256(code)[0..15]，保证幂等。 */
    public WechatSession mockSession(String code) {
        return new WechatSession("mock_" + sha256Prefix(code), "mock_session_key", null, true);
    }

    public boolean mockEnabled() {
        return props.mock() || props.appId() == null || props.appId().isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String sha256Prefix(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
