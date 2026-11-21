package com.painuo.suite.controller;

import com.painuo.suite.cache.CacheService;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.Result;
import com.painuo.suite.config.AppProperties;
import com.painuo.suite.entity.OperationLog;
import com.painuo.suite.service.OperationLogService;
import com.painuo.suite.storage.StorageService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查与系统自述。
 *
 * <p>{@code /api/ping} 不鉴权、不查库、不查缓存，只返回进程内信息，
 * 用于 Docker healthcheck、Nginx 探活和部署后的第一眼确认。</p>
 *
 * <p>{@code /api/system/info} 是运营后台「系统配置」页的运行时快照，
 * 刻意只暴露**非敏感**字段：密钥、数据库口令、AI api-key 一律不出现在响应里。</p>
 *
 * <p>{@code /api/system/logs} 是操作日志只读分页接口，登录即可访问
 * （菜单项只挂给超级管理员，权限收在菜单层）。</p>
 */
@RestController
public class SystemController {

    /** 与 pom.xml 的 project.version 保持一致；打包后 manifest 不一定带版本，故显式声明。 */
    private static final String APP_VERSION = "1.0.0";

    private final CacheService cacheService;
    private final StorageService storageService;
    private final OperationLogService operationLogService;
    private final AppProperties appProperties;
    private final Environment environment;

    public SystemController(CacheService cacheService,
                            StorageService storageService,
                            OperationLogService operationLogService,
                            AppProperties appProperties,
                            Environment environment) {
        this.cacheService = cacheService;
        this.storageService = storageService;
        this.operationLogService = operationLogService;
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @GetMapping("/api/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("app", environment.getProperty("spring.application.name", "business-suite-backend"));
        info.put("profiles", String.join(",", environment.getActiveProfiles()));
        info.put("cacheBackend", cacheService.backend());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("serverTime", LocalDateTime.now().toString());
        return Result.ok(info);
    }

    @GetMapping("/api/system/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", environment.getProperty("spring.application.name", "business-suite-backend"));
        info.put("version", APP_VERSION);
        info.put("profiles", String.join(",", environment.getActiveProfiles()));
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        info.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000L);
        info.put("serverTime", LocalDateTime.now().toString());

        info.put("cacheBackend", cacheService.backend());
        info.put("storageBackend", storageService.backend());
        info.put("storageMaxMb", storageService.maxSize() / 1024 / 1024);

        AppProperties.Ai ai = appProperties.ai();
        info.put("aiProvider", ai == null ? "unknown" : ai.provider());
        info.put("aiModel", ai == null ? "unknown" : ai.model());
        // mock 模式 = 完全离线可演示；接真实大模型时这里会变成 openai
        info.put("aiMock", ai == null || "mock".equalsIgnoreCase(ai.provider()));

        AppProperties.Wechat wechat = appProperties.wechat();
        info.put("wechatMock", wechat == null || wechat.mock());
        info.put("jwtAccessTokenSeconds", appProperties.jwt() == null ? 0L : appProperties.jwt().accessTokenSeconds());
        info.put("corsAllowedOrigins",
                appProperties.cors() == null ? "" : appProperties.cors().allowedOrigins());
        return Result.ok(info);
    }

    @GetMapping("/api/system/logs")
    public Result<PageResult<OperationLog>> logs(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return Result.ok(operationLogService.page(keyword, PageQuery.of(page, size, null)));
    }
}
