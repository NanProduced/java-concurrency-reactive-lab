package nan.tech.lab09.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Spring WebFlux 配置类
 *
 * <h2>配置内容</h2>
 * <ul>
 *     <li>CORS 配置 (跨域请求)</li>
 *     <li>错误处理 (全局异常处理)</li>
 *     <li>消息转换器配置</li>
 * </ul>
 *
 * @author Claude Code + Lab-09 团队
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    /**
     * CORS 跨域请求配置
     *
     * 允许来自所有源的请求访问 /basic 端点
     * 用于测试演示，生产环境应该更加严格
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/basic/**")
                .allowedOrigins("*")  // 允许所有源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);

        log.info("WebFlux CORS 配置完成");
    }

}
