package nan.tech.lab04.completablefuture.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Lab-04 应用程序配置类。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>为 CompletableFuture 异步编排配置专用线程池</li>
 *   <li>展示如何为异步任务选择合适的线程池配置</li>
 *   <li>演示线程池命名规范，便于监控和调试</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li><b>默认 ForkJoinPool 的风险</b>: CompletableFuture 默认使用 ForkJoinPool.commonPool()，
 *       线程数量固定为 (CPU-1)，不适合 IO 密集型任务</li>
 *   <li><b>线程池大小不足</b>: 如果线程池太小，CompletableFuture 链式调用可能阻塞</li>
 *   <li><b>资源泄漏</b>: 务必在应用关闭时优雅停止线程池，避免任务丢失</li>
 * </ul>
 *
 * <p><b>@参考</b>
 * <ul>
 *   <li>Java Concurrency in Practice: ThreadPoolExecutor 配置指南</li>
 *   <li>CompletableFuture JavaDoc: 关于默认线程池的说明</li>
 * </ul>
 *
 * @author Nan
 * @since 1.0.0
 */
@SpringBootConfiguration
public class ApplicationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfiguration.class);

    /**
     * 配置 CompletableFuture 专用异步线程池。
     *
     * <p><b>@教学</b>
     * <p>CompletableFuture 的异步方法（如 thenApplyAsync, supplyAsync）可以接收自定义 Executor。
     * 本线程池为混合型任务设计（既有计算也有 IO 等待）。
     *
     * <p><b>@配置参数</b>
     * <ul>
     *   <li><b>corePoolSize</b>: CPU 核数 * 2，适合混合型任务</li>
     *   <li><b>maxPoolSize</b>: CPU 核数 * 4，允许一定的扩容能力</li>
     *   <li><b>queueCapacity</b>: 2000，较大的队列容量应对突发流量</li>
     *   <li><b>keepAliveSeconds</b>: 60秒，空闲线程快速回收</li>
     *   <li><b>threadNamePrefix</b>: "cf-async-"，便于日志追踪</li>
     * </ul>
     *
     * <p><b>@公式</b>
     * <pre>
     * 混合型任务线程数 = CPU核数 * (1 + 平均等待时间 / 平均计算时间)
     *
     * 假设:
     *   平均等待时间 = 100ms (HTTP调用, 数据库查询等)
     *   平均计算时间 = 100ms (数据处理)
     *   等待/计算比 = 100/100 = 1
     *
     * 则:
     *   核心线程数 = CPU核数 * (1 + 1) = CPU核数 * 2
     *   最大线程数 = CPU核数 * 2 * 2 = CPU核数 * 4 (允许2倍扩容)
     * </pre>
     *
     * <p><b>@使用示例</b>
     * <pre>{@code
     * // 注入线程池
     * @Autowired
     * @Qualifier("asyncExecutor")
     * private Executor asyncExecutor;
     *
     * // 使用自定义线程池
     * CompletableFuture.supplyAsync(() -> "Hello", asyncExecutor)
     *     .thenApplyAsync(s -> s + " World", asyncExecutor)
     *     .thenAccept(System.out::println);
     * }</pre>
     *
     * @return CompletableFuture 专用线程池
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 根据 CPU 核数配置
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int coreSize = cpuCores * 2;  // 混合型任务：CPU核数 * 2
        int maxSize = cpuCores * 4;   // 最大线程数：CPU核数 * 4

        // 核心参数配置
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("cf-async-");
        executor.setKeepAliveSeconds(60);

        // 优雅关闭配置
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 等待队列任务完成
        executor.setAwaitTerminationSeconds(30);             // 最多等待30秒

        // 拒绝策略：CallerRunsPolicy（在调用线程执行，避免任务丢失）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化线程池
        executor.initialize();

        log.info("✅ CompletableFuture 异步线程池已配置: core={}, max={}, queue={}, prefix={}",
                coreSize, maxSize, 2000, "cf-async-");

        return executor;
    }

    /**
     * 配置 IO 密集型线程池（用于模拟外部服务调用）。
     *
     * <p><b>@教学</b>
     * <p>本线程池用于演示 IO 密集型任务，例如模拟 HTTP 调用、数据库查询等。
     * 线程数量显著高于 CPU 核数，因为大部分时间线程都在等待 IO 完成。
     *
     * <p><b>@配置参数</b>
     * <ul>
     *   <li><b>corePoolSize</b>: CPU 核数，基础线程数</li>
     *   <li><b>maxPoolSize</b>: CPU 核数 * 10，IO密集型任务允许更多线程</li>
     *   <li><b>queueCapacity</b>: 5000，大队列应对突发请求</li>
     * </ul>
     *
     * <p><b>@公式</b>
     * <pre>
     * IO密集型任务线程数 = CPU核数 * (1 + 平均等待时间 / 平均计算时间)
     *
     * 假设:
     *   平均等待时间 = 900ms (外部服务调用)
     *   平均计算时间 = 100ms (数据序列化等)
     *   等待/计算比 = 900/100 = 9
     *
     * 则:
     *   核心线程数 = CPU核数
     *   最大线程数 = CPU核数 * (1 + 9) = CPU核数 * 10
     * </pre>
     *
     * @return IO 密集型线程池
     */
    @Bean(name = "ioExecutor")
    public Executor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int cpuCores = Runtime.getRuntime().availableProcessors();
        int coreSize = cpuCores;        // IO密集：核心线程 = CPU核数
        int maxSize = cpuCores * 10;    // 最大线程：CPU核数 * 10

        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("cf-io-");
        executor.setKeepAliveSeconds(120);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("✅ IO 密集型线程池已配置: core={}, max={}, queue={}, prefix={}",
                coreSize, maxSize, 5000, "cf-io-");

        return executor;
    }
}
