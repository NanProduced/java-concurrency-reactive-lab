package nan.tech.lab08.springmvc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务执行器配置
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>生产级线程池配置：核心线程数、最大线程数、队列容量优化</li>
 *   <li>拒绝策略选择：CallerRunsPolicy 提供背压机制</li>
 *   <li>优雅关闭：等待任务完成后再关闭线程池</li>
 *   <li>线程命名：便于调试和监控</li>
 * </ul>
 *
 * <p><b>@参数计算</b>
 * <pre>
 * IO 密集型任务（如远程调用、数据库查询）:
 *   核心线程数 = CPU 核心数 × 2
 *   最大线程数 = CPU 核心数 × 4
 *   队列容量 = 核心线程数 × 10
 *
 * CPU 密集型任务（如计算、加密）:
 *   核心线程数 = CPU 核心数 + 1
 *   最大线程数 = CPU 核心数 × 2
 * </pre>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li>不要使用默认的 SimpleAsyncTaskExecutor（每次请求创建新线程）</li>
 *   <li>队列容量不要设置为 Integer.MAX_VALUE（永远不会扩展到 maxPoolSize）</li>
 *   <li>拒绝策略慎用 AbortPolicy（生产环境推荐 CallerRunsPolicy）</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2025-10-20
 */
@Configuration
public class AsyncExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutorConfig.class);

    /**
     * 异步任务执行器 Bean
     *
     * <p><b>@教学</b>
     * <p>用于 Callable 和 WebAsyncTask 的默认执行器。DeferredResult 通常使用自定义线程池。
     *
     * <p><b>@配置参数</b>
     * <ul>
     *   <li>CorePoolSize: 10（IO 密集型，假设 4 核 CPU × 2）</li>
     *   <li>MaxPoolSize: 50（CPU × 4，峰值处理能力）</li>
     *   <li>QueueCapacity: 100（缓冲队列，避免频繁创建线程）</li>
     *   <li>KeepAliveSeconds: 60（空闲线程存活时间）</li>
     * </ul>
     *
     * @return ThreadPoolTaskExecutor 线程池执行器
     */
    @Bean(name = "asyncTaskExecutor")
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 计算线程池参数（基于 CPU 核心数）
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int corePoolSize = cpuCount * 2;    // IO 密集型
        int maxPoolSize = cpuCount * 4;
        int queueCapacity = corePoolSize * 10;

        log.info("初始化异步任务执行器: CPU核心数={}, 核心线程数={}, 最大线程数={}, 队列容量={}",
                cpuCount, corePoolSize, maxPoolSize, queueCapacity);

        // 核心参数
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);

        // 线程命名（便于调试）
        executor.setThreadNamePrefix("async-mvc-");

        // 拒绝策略：CallerRunsPolicy（调用者线程执行，提供背压机制）
        // ⚠️ 生产环境推荐此策略，避免直接抛异常
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 优雅关闭（等待任务完成）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 允许核心线程超时（节省资源）
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("异步任务执行器初始化完成: {}", executor);
        return executor;
    }

    /**
     * 自定义异步执行器（可选）
     *
     * <p><b>@教学</b>
     * <p>用于特定场景的异步任务，例如慢速任务（大文件处理、批量导出）。
     *
     * @return ThreadPoolTaskExecutor 自定义执行器
     */
    @Bean(name = "customAsyncExecutor")
    public ThreadPoolTaskExecutor customAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 慢速任务：较小的线程池
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(120);

        executor.setThreadNamePrefix("custom-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("自定义异步执行器初始化完成: {}", executor);
        return executor;
    }
}
