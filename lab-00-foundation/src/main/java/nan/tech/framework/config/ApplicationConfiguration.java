package nan.tech.framework.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * 应用程序的全局配置类。
 *
 * <p><b>@教学</b>
 * <ul>
 *   <li>应用程序的全局配置类</li>
 *   <li>展示如何使用 Spring Boot 的 @Configuration 进行 Bean 配置</li>
 *   <li>演示线程池的标准化配置模式</li>
 * </ul>
 *
 * <p><b>@陷阱</b>
 * <ul>
 *   <li>不要在线程池中执行阻塞操作过长</li>
 *   <li>线程池大小应根据实际任务特性调整</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
public class ApplicationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfiguration.class);

    /**
     * 配置异步线程池。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li><b>corePoolSize</b>: 核心线程数，始终保活</li>
     *   <li><b>maxPoolSize</b>: 最大线程数，任务堆积时创建新线程</li>
     *   <li><b>queueCapacity</b>: 队列容量，核心线程满了先入队，队列满再创建新线程</li>
     *   <li><b>keepAliveSeconds</b>: 线程存活时间，非核心线程空闲时回收</li>
     * </ul>
     *
     * <p><b>@参考</b>
     * <ul>
     *   <li>Java Concurrency in Practice: ThreadPoolExecutor 配置指南</li>
     *   <li>Spring Boot: TaskExecutor 配置最佳实践</li>
     * </ul>
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 根据 CPU 核数配置
        int coreSize = Runtime.getRuntime().availableProcessors();
        int maxSize = coreSize * 2;

        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-task-");
        executor.setKeepAliveSeconds(300);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("异步线程池已配置: core={}, max={}, queue={}", coreSize, maxSize, 1000);
        return executor;
    }

    /**
     * 配置 IO 密集型线程池。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>IO 密集型任务需要更多线程（等待时间长）</li>
     *   <li>使用公式: threads = cpus * (1 + I/O等待比)</li>
     * </ul>
     */
    @Bean(name = "ioExecutor")
    public Executor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int cpus = Runtime.getRuntime().availableProcessors();
        // IO密集: 计算比例通常为 1:10
        int coreSize = cpus;
        int maxSize = cpus * 10;

        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("io-task-");
        executor.setKeepAliveSeconds(300);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("IO 线程池已配置: core={}, max={}, queue={}", coreSize, maxSize, 5000);
        return executor;
    }

    /**
     * 配置 CPU 密集型线程池。
     *
     * <p><b>@教学</b>
     * <ul>
     *   <li>CPU 密集型任务线程数应接近 CPU 核数</li>
     *   <li>过多线程会导致频繁的上下文切换，反而降低性能</li>
     * </ul>
     */
    @Bean(name = "cpuExecutor")
    public Executor cpuExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int cpus = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(cpus);
        executor.setMaxPoolSize(cpus);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cpu-task-");
        executor.setKeepAliveSeconds(300);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("CPU 线程池已配置: core={}, max={}, queue={}", cpus, cpus, 100);
        return executor;
    }

    /**
     * 配置指标收集器。
     *
     * @param registry Micrometer 指标注册表
     * @return MetricsCollector 实例
     */
    @Bean
    public MetricsCollector metricsCollector(MeterRegistry registry) {
        return new MetricsCollector(registry);
    }
}
