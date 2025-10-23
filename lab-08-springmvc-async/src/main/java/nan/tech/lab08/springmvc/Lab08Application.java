package nan.tech.lab08.springmvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Lab-08: Spring MVC 异步编程主应用类
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>演示 Spring MVC 异步请求处理：Callable、WebAsyncTask、DeferredResult</li>
 *   <li>展示超时控制与容错机制：三层超时配置、降级策略</li>
 *   <li>量化性能提升：同步 vs 异步的 TPS 和 P99 延迟对比</li>
 *   <li>生产级线程池配置：AsyncTaskExecutor 参数优化</li>
 * </ul>
 *
 * <p><b>@快速开始</b>
 * <pre>
 * mvn spring-boot:run
 *
 * # 测试端点
 * curl http://localhost:8080/api/async/sync
 * curl http://localhost:8080/api/async/callable
 * curl http://localhost:8080/api/async/deferred?userId=user123
 * </pre>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@SpringBootApplication
public class Lab08Application {

    private static final Logger log = LoggerFactory.getLogger(Lab08Application.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Lab08Application.class, args);

        log.info("=".repeat(80));
        log.info("Lab-08: Spring MVC 异步编程 - 应用启动成功");
        log.info("=".repeat(80));
        log.info("访问地址: http://localhost:8080");
        log.info("");
        log.info("核心端点:");
        log.info("  - GET  /api/async/sync               同步阻塞端点（对比基准）");
        log.info("  - GET  /api/async/callable           Callable 异步端点");
        log.info("  - GET  /api/async/webasynctask       WebAsyncTask 端点");
        log.info("  - GET  /api/async/deferred           DeferredResult 端点");
        log.info("  - POST /api/async/deferred/complete  触发 DeferredResult 完成");
        log.info("");
        log.info("监控端点:");
        log.info("  - GET  /actuator/health              健康检查");
        log.info("  - GET  /actuator/metrics             指标数据");
        log.info("  - GET  /actuator/threaddump          线程转储");
        log.info("=".repeat(80));
    }
}
