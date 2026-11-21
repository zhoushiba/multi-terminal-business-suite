package com.painuo.suite.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与 RBAC 的接口级集成测试。
 *
 * <p>用 MockMvc 走完整的 Spring MVC 链路（Filter → Interceptor → AOP → Controller），
 * 覆盖：登录、traceId 透传、参数校验、401/403、refresh token 一次性轮换、动态菜单。
 * 这几条是"权限系统到底有没有生效"的最小可信证据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("认证与权限（接口集成）")
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("账号密码登录返回双 token 与权限集合")
    void loginReturnsTokens() throws Exception {
        JsonNode body = login("admin", "admin123");
        assertThat(body.path("code").asInt()).isZero();
        JsonNode data = body.path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("expiresIn").asLong()).isPositive();
        assertThat(data.path("user").path("username").asText()).isEqualTo("admin");
        assertThat(textOf(data.path("user").path("roles"))).contains("admin");
        assertThat(textOf(data.path("user").path("permissions"))).contains("content:list");
    }

    @Test
    @DisplayName("密码错误返回 401，且不暴露账号是否存在")
    void wrongPasswordIsUnauthorized() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(codeIs(40101))
                .andReturn();
        assertThat(read(result).path("message").asText()).contains("用户名或密码错误");
    }

    @Test
    @DisplayName("未携带 token 访问受保护接口返回 401")
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/contents"))
                .andExpect(status().isUnauthorized())
                .andExpect(codeIs(40101));
    }

    @Test
    @DisplayName("内容运营访问用户管理返回 403（RBAC 生效）")
    void editorCannotManageUsers() throws Exception {
        String token = accessToken("editor", "editor123");
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(codeIs(40301));
    }

    @Test
    @DisplayName("内容运营可以访问内容列表（越权判定不是一刀切）")
    void editorCanReadContents() throws Exception {
        String token = accessToken("editor", "editor123");
        mockMvc.perform(get("/api/contents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(codeIs(0))
                .andExpect(result -> {
                    JsonNode body = read(result);
                    assertThat(body.path("data").path("total").asLong()).isPositive();
                });
    }

    @Test
    @DisplayName("refresh token 只能使用一次（一次性轮换）")
    void refreshTokenIsSingleUse() throws Exception {
        String refreshToken = login("admin", "admin123").path("data").path("refreshToken").asText();

        MvcResult first = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(read(first).path("data").path("accessToken").asText()).isNotBlank();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(codeIs(40104));
    }

    @Test
    @DisplayName("动态路由只下发当前角色可见的菜单")
    void routesFilteredByRole() throws Exception {
        String token = accessToken("operator", "operator123");
        MvcResult result = mockMvc.perform(get("/api/auth/routes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String routes = read(result).path("data").toString();
        assertThat(routes).contains("活动运营").contains("报名核销").contains("资源点位");
        assertThat(routes).doesNotContain("用户管理").doesNotContain("内容管理");
    }

    @Test
    @DisplayName("参数校验失败返回 400 且提示字段名")
    void validationFailureReturns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(codeIs(40001))
                .andReturn();
        assertThat(read(result).path("message").asText()).contains("username");
    }

    @Test
    @DisplayName("traceId 贯穿响应头与响应体，便于全链路排查")
    void traceIdIsPropagated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andReturn();
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(headerTraceId).isNotBlank();
        assertThat(read(result).path("traceId").asText()).isEqualTo(headerTraceId);
    }

    // ------------------------------------------------------------------ 测试辅助

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private String accessToken(String username, String password) throws Exception {
        return login(username, password).path("data").path("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultMatcher codeIs(int expected) {
        return result -> assertThat(read(result).path("code").asInt()).isEqualTo(expected);
    }

    private String textOf(JsonNode array) {
        StringBuilder builder = new StringBuilder();
        array.forEach(node -> builder.append(node.asText()).append(','));
        return builder.toString();
    }
}
