package com.painuo.suite.config;

import com.painuo.suite.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：拦截器注册、CORS、本地上传目录静态映射。
 *
 * <p>白名单集中在此，新增开放接口只改这一处，评审时一眼可见攻击面。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 无需登录即可访问的路径（精确前缀）。 */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/captcha",
            "/api/wx/login",
            "/api/public/**",
            "/api/ping",
            "/actuator/health",
            "/uploads/**",
            "/error"
    };

    private final AuthInterceptor authInterceptor;
    private final AppProperties appProperties;

    public WebMvcConfig(AuthInterceptor authInterceptor, AppProperties appProperties) {
        this.authInterceptor = authInterceptor;
        this.appProperties = appProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS)
                .order(0);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = appProperties.cors() == null ? null : appProperties.cors().allowedOrigins();
        String[] patterns = (origins == null || origins.isBlank())
                ? new String[]{"http://localhost:*", "http://127.0.0.1:*"}
                : origins.split(",");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String root = appProperties.storage().localRoot();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + (root.endsWith("/") ? root : root + "/"));
    }
}
